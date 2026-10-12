package org.kokuu.title

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import org.kokuu.core.api.ApiJson
import org.kokuu.core.api.ApiRouter
import org.kokuu.core.api.Feature
import org.kokuu.core.data.DataStore
import org.kokuu.core.api.KokuuServices
import org.kokuu.title.TitleDefinition
import org.kokuu.title.TitleRegistry
import org.kokuu.core.util.Msg
import org.kokuu.core.util.Text

/**
 * 称号系统。定义在 `kokuu/title/titles.json`。
 *
 * ```
 * /title            打开图形界面，点格子即佩戴
 * /title list       聊天列表（每个称号后面跟可点的「佩戴」）
 * /title set <id>   直接佩戴
 * /title clear      摘掉称号
 * ```
 *
 * ## 拥有关系直接复用权限系统
 *
 * 称号定义里写 `permission`，玩家有那个节点就能用 —— **不另建一份"谁拥有哪些称号"的名单**。
 * 那种名单最容易和权限对不上（发了权限忘发称号、撤了权限忘撤称号）。
 * 要给人发称号就用：`/perm user <玩家> add kokuucore.title.vip`。
 *
 * ## GUI 是只读的
 *
 * [TitleMenu] 覆写了 `clicked` 且**不调用 super** —— 图标拿不出来、也拖不动，
 * 点一下就是"选它"，选完自动关界面。否则玩家能把菜单里的展示物品塞进背包。
 */
class TitleFeature : Feature() {

    override val id = "titles"
    override val displayName = "称号系统"
    override val description = "可佩戴的称号，带图形界面切换，可在聊天/TAB/记分牌里用 %title% 显示"

    override val defaultEnabled: Boolean = true

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("title")
                .requires { it.player != null }
                .executes { openGui(it.source.playerOrException) }
                .then(Commands.literal("list").executes { listTitles(it.source) })
                .then(
                    Commands.literal("set")
                        .then(
                            Commands.argument("id", StringArgumentType.word())
                                .suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(TitleRegistry.titles.keys, builder)
                                    builder.buildFuture()
                                }
                                .executes {
                                    set(it.source.playerOrException, StringArgumentType.getString(it, "id"))
                                }
                        )
                )
                .then(Commands.literal("clear").executes { set(it.source.playerOrException, "") })
        )
    }

    // ---------- 交互 ----------

    private fun openGui(player: ServerPlayer): Int {
        val mine = usableTitles(player)
        if (mine.isEmpty()) {
            Msg.err(player, "你还没有任何可用的称号")
            return 0
        }
        val wearing = DataStore.get()?.profile(player.uuid, null)?.activeTitle ?: ""
        player.openMenu(
            SimpleMenuProvider(
                { id, inventory, _ -> TitleMenu(id, inventory, player, mine, wearing) },
                Component.literal("称号 · 点一下佩戴")
            )
        )
        return 1
    }

    private fun listTitles(source: CommandSourceStack): Int {
        val player = source.player
        if (player == null) {
            Msg.err(source, "这个子命令只能由玩家执行")
            return 0
        }
        val mine = usableTitles(player)
        val current = DataStore.get()?.profile(player.uuid, null)?.activeTitle ?: ""
        val lines = ArrayList<String>()
        lines += "&8&m─────&r &b我的称号 &8(${mine.size}) &m─────"
        lines += "&7当前：${activeTitle(player)?.display ?: "&7（未佩戴）"}"
        lines += ""
        if (mine.isEmpty()) {
            lines += "&7（还没有可用称号 —— 让管理员给你加权限节点）"
        }
        mine.forEach { (id, def) ->
            val mark = if (id == current) "&a●" else "&8○"
            lines += "$mark &f${Text.strip(def.display)} &8($id)  &8[&e佩戴&8](run:/title set $id)"
        }
        lines += ""
        lines += "&8[&7摘掉称号&8](run:/title clear)  &8[&a图形界面&8](run:/title \"&7打开箱子界面\")"
        lines.forEach { source.sendSuccess({ Text.legacy(it) }, false) }
        return 1
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/titles") { _ ->
            ApiJson.obj(
                "ok", true,
                "titles", ApiJson.arr(
                    TitleRegistry.sorted().map { (id, def) -> ApiJson.obj("id", id, "title", titleJson(def)) }
                )
            )
        }
        router.post("/api/titles/set") { request ->
            val body = request.requireField("player", "title")
            val name = body.get("player").asString
            val titleId = body.get("title").asString
            val server = org.kokuu.core.api.KokuuServer.require()
            val target = server.playerList.getPlayerByName(name)
                ?: throw IllegalArgumentException("玩家 $name 不在线")
            if (set(target, titleId) == 0) {
                throw IllegalArgumentException("设置失败（称号不存在，或该玩家未解锁这个称号）")
            }
            ApiJson.obj("ok", true, "player", name, "title", titleId)
        }
    }

    private fun titleJson(def: TitleDefinition): com.google.gson.JsonObject {
        def.normalize()
        return ApiJson.obj(
            "display", def.display,
            "plain", Text.strip(def.display),
            "description", def.description,
            "permission", def.permission,
            "weight", def.weight,
            "icon", def.icon
        )
    }

    companion object {

        /** 该玩家能用的称号：permission 留空的，或者他有那个节点的。 */
        fun usableTitles(player: ServerPlayer): List<Pair<String, TitleDefinition>> =
            TitleRegistry.sorted().filter { canUse(player, it.second) }

        fun canUse(player: ServerPlayer, def: TitleDefinition): Boolean =
            def.permission.isBlank() || KokuuServices.permission().has(player, def.permission)

        /** 玩家当前佩戴的称号；没戴、或那个称号已被删掉时返回 null。 */
        fun activeTitle(player: ServerPlayer): TitleDefinition? {
            val profile = DataStore.get()?.profile(player.uuid, null) ?: return null
            return TitleRegistry.byId(profile.activeTitle)
        }

        /**
         * 佩戴称号。空串 = 摘掉。
         *
         * 返回 1/0 而不是 Boolean —— 它同时被 Brigadier 的 `executes` 使用，
         * 那里要的就是 Int（返回 0 会让命令算作失败）。
         */
        fun set(player: ServerPlayer, id: String): Int {
            if (id.isNotBlank()) {
                val def = TitleRegistry.byId(id)
                if (def == null) {
                    Msg.err(player, "没有名为 $id 的称号")
                    return 0
                }
                if (!canUse(player, def)) {
                    Msg.err(player, "你还没有解锁称号「${Text.strip(def.display)}」")
                    return 0
                }
            }
            val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
            profile.activeTitle = id
            DataStore.get()?.markDirty()
            if (id.isBlank()) {
                Msg.ok(player, "已摘掉称号")
            } else {
                Msg.text(player, "已佩戴称号 ${TitleRegistry.byId(id)?.display ?: id}")
            }
            return 1
        }
    }

    // ---------------------------------------------------------------- GUI

    /**
     * 称号选择界面（3 行箱子）。
     *
     * 图标取自称号定义的 `icon` 字段（物品 id）；`&` 颜色会应用在物品名与说明上。
     */
    private class TitleMenu(
        id: Int,
        inventory: Inventory,
        private val owner: ServerPlayer,
        private val entries: List<Pair<String, TitleDefinition>>,
        wearing: String,
    ) : ChestMenu(MenuType.GENERIC_9x3, id, inventory, SimpleContainer(ROWS * 9), ROWS) {

        init {
            entries.forEachIndexed { index, (titleId, def) ->
                if (index >= ROWS * 9) return@forEachIndexed
                container.setItem(index, icon(titleId, def, titleId == wearing))
            }
        }

        override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
            // ⚠️ 刻意**不调用 super** —— 这是个只读界面；不调 super 就移不动物品
            val entry = entries.getOrNull(slotId) ?: return
            if (player !is ServerPlayer) return
            set(player, entry.first)
            player.closeContainer()
        }

        override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

        override fun stillValid(player: Player): Boolean = player === owner

        private fun icon(titleId: String, def: TitleDefinition, isWearing: Boolean): ItemStack {
            val parsed = ResourceLocation.tryParse(def.icon)
            val item = parsed?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) } ?: Items.NAME_TAG
            val stack = ItemStack(item)
            stack.set(
                DataComponents.CUSTOM_NAME,
                Text.legacy(def.display + if (isWearing) " &a◀ 佩戴中" else "")
            )
            val lore = ArrayList<Component>()
            if (def.description.isNotBlank()) lore += Text.legacy("&7${def.description}")
            lore += Text.legacy("&8id: &7$titleId")
            lore += Text.legacy(if (isWearing) "&a当前佩戴" else "&e点击佩戴")
            stack.set(DataComponents.LORE, ItemLore(lore))
            return stack
        }

        private companion object {
            const val ROWS = 3
        }
    }
}
