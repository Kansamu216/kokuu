package org.voidkit.title

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
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
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.PlayerProfile
import org.voidkit.core.api.VoidkitServices
import org.voidkit.title.TitleDefinition
import org.voidkit.title.TitleRegistry
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text

/**
 * 称号系统。定义在 `voidkit/title/titles.json`。
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
 * 称号定义里写 `permission`，玩家有那个节点就能用 —— **不另建一份“谁拥有哪些称号”的名单**。
 * 那种名单最容易和权限对不上（发了权限忘发称号、撤了权限忘撤称号）。
 * 要给人发称号就用：`/perm user <玩家> add voidkit.title.vip`。
 *
 * ## GUI 是只读的
 *
 * [TitleMenu] 覆写了 `clicked` 且**不调用 super** —— 图标拿不出来、也拖不动，
 * 点一下就是“选它”，选完自动关界面。否则玩家能把菜单里的展示物品塞进背包。
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
                .then(
                    Commands.literal("custom")
                        .requires { canUseCustom(it.player) }
                        .then(
                            Commands.literal("clear")
                                .executes { setCustom(it.source.playerOrException, "") }
                        )
                        .then(
                            Commands.argument("text", StringArgumentType.greedyString())
                                .executes {
                                    setCustom(
                                        it.source.playerOrException,
                                        StringArgumentType.getString(it, "text"),
                                    )
                                }
                        )
                )
        )
    }

    // ---------- 交互 ----------

    private fun openGui(player: ServerPlayer): Int {
        val mine = usableTitles(player)
        if (mine.isEmpty()) {
            Msg.err(player, "你还没有任何可用的称号")
            return 0
        }
        val wearing = wearingTitleId(player)
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
        val current = wearingTitleId(player)
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

    // ---------- 自定义称号 ----------

    /** 该玩家是否能用自定义称号（功能开关 + 权限节点）。 */
    private fun canUseCustom(player: ServerPlayer?): Boolean {
        if (player == null) return false
        val cfg = TitleRegistry.custom
        return cfg.enabled && VoidkitServices.permission().has(player, cfg.permission)
    }

    /** 设置（或清除）自定义称号。空串 = 清除。 */
    private fun setCustom(player: ServerPlayer, rawText: String): Int {
        val cfg = TitleRegistry.custom
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            profile.customTitle = ""
            DataStore.get()?.markDirty()
            Msg.ok(player, "已清除自定义称号")
            return 1
        }
        val error = validateCustom(trimmed)
        if (error != null) {
            Msg.err(player, error)
            return 0
        }
        val plain = stripColors(trimmed)
        profile.customTitle = if (cfg.allowColors) trimmed else plain
        DataStore.get()?.markDirty()
        Msg.text(player, "已设置自定义称号 ${cfg.colorCode}${profile.customTitle}")
        return 1
    }

    /** 校验自定义称号文本，返回错误信息；通过则返回 null。 */
    private fun validateCustom(text: String): String? {
        val cfg = TitleRegistry.custom
        val plain = stripColors(text)
        val width = displayWidth(plain)
        if (width > cfg.maxLength) {
            return "自定义称号最长 ${cfg.maxLength} 格，当前 $width 格"
        }
        val regex = runCatching { Regex(cfg.allowedPattern) }.getOrNull()
        if (regex != null && !regex.matches(plain)) {
            return "自定义称号含有非法字符"
        }
        return null
    }

    /** 去掉 `&x` / `§x` 颜色码，得到纯文本。 */
    private fun stripColors(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '&' || c == '§') && i + 1 < text.length && ChatFormatting.getByCode(text[i + 1]) != null) {
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** 显示宽度：中文/全角/emoji 等算 2 格，其余（ASCII 等）算 1 格。 */
    private fun displayWidth(text: String): Int {
        var width = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            width += if (isWide(cp)) 2 else 1
            i += Character.charCount(cp)
        }
        return width
    }

    /** 该码点是否按 2 格计宽（CJK、全角形式、emoji 等宽字符）。 */
    private fun isWide(cp: Int): Boolean = when {
        cp >= 0x1F000 -> true                    // emoji 与补充平面符号
        cp in 0x2600..0x27BF -> true             // 杂项/装饰符号（含一部分 emoji）
        cp in 0x1100..0x115F -> true             // 谚文字母
        cp in 0x2E80..0x303F -> true             // CJK 部首、标点、注音
        cp in 0x3040..0x30FF -> true             // 平假名、片假名
        cp in 0x3130..0x318F -> true             // 谚文兼容字母
        cp in 0x31A0..0x31BF -> true             // 注音扩展
        cp in 0x31F0..0x31FF -> true             // 片假名语音扩展
        cp in 0x3200..0x33FF -> true             // 带圈 CJK 与兼容
        cp in 0x3400..0x4DBF -> true             // CJK 扩展 A
        cp in 0x4E00..0x9FFF -> true             // CJK 统一表意文字
        cp in 0xA000..0xA4CF -> true             // 彝文
        cp in 0xA960..0xA97F -> true             // 谚文扩展 A
        cp in 0xAC00..0xD7AF -> true             // 谚文音节
        cp in 0xF900..0xFAFF -> true             // CJK 兼容表意文字
        cp in 0xFE10..0xFE19 -> true             // 竖排形式
        cp in 0xFE30..0xFE52 -> true             // CJK 兼容形式
        cp in 0xFE54..0xFE66 -> true             // 小形式变体
        cp in 0xFE68..0xFE6B -> true             // 小形式变体
        cp in 0xFF00..0xFFEF -> true             // 全角形式
        else -> false
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
            val server = org.voidkit.core.api.VoidkitServer.require()
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
            def.permission.isBlank() || VoidkitServices.permission().has(player, def.permission)

        /**
         * 玩家当前佩戴的称号；没戴、或那个称号已被删掉时返回 null。
         *
         * 自定义称号（[PlayerProfile.customTitle] 非空）优先于 id 称号 —— 返回一个合成的
         * [TitleDefinition]，display 统一套上配置里的 colorCode。
         */
        fun activeTitle(player: ServerPlayer): TitleDefinition? {
            val profile = DataStore.get()?.profile(player.uuid, null) ?: return null
            if (profile.customTitle.isNotBlank()) {
                return TitleDefinition(
                    display = TitleRegistry.custom.colorCode + profile.customTitle,
                    description = "自定义称号",
                )
            }
            return TitleRegistry.byId(profile.activeTitle)
        }

        /** 当前佩戴的 id 称号；自定义称号生效时返回空（自定义不走 id 体系，不参与列表标记）。 */
        fun wearingTitleId(player: ServerPlayer): String {
            val profile = DataStore.get()?.profile(player.uuid, null) ?: return ""
            if (profile.customTitle.isNotBlank()) return ""
            return profile.activeTitle
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
            // 走预定义称号体系时清掉自定义文本，避免两者并存、显示混乱
            profile.customTitle = ""
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
