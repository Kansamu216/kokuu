package org.voidkit.display

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.CommandPerms
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PlayerViews
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.api.VoidkitServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Msg

/**
 * 菜单系统。
 *
 * 两个入口：
 *  - `/menu [名字]` —— 命令打开；
 *  - **手持绑定物品右键** —— 就是老 spigot 上的“钟表菜单”，配置见 `display/menu.json`
 *    的 `openers` 段。
 *
 * ## 权限
 *
 * | 用法 | 节点 | 默认 |
 * |---|---|---|
 * | `/menu`、`/menu <名字>`、右键绑定物 | `voidkit.menu.use` | **公开**（玩家日常入口） |
 * | `/menu list`、`/menu reload`、`/menu open <玩家> <菜单>` | `voidkit.menu` | 仅管理 |
 *
 * 界面与动作语法见 [MenuGui]。这里只负责“从哪进来”和“列出有哪些菜单”。
 */
class MenuFeature : Feature() {

    override val id = "menu"
    override val displayName = "菜单"
    override val description = "JSON 驱动的箱子菜单 / 钟表菜单"

    override val defaultEnabled: Boolean = true

    override fun onServerStart(server: MinecraftServer) {
        MenuStore.load()
    }

    override fun onReload(server: MinecraftServer) {
        MenuStore.load()
    }

    // ---------- 手持物品右键打开 ----------

    /**
     * 玩家退出时清掉他的菜单导航栈。
     *
     * [MenuNav] 是按 UUID 存的，不清的话每来过一个玩家就留一条 —— 长期运行的服
     * 会慢慢涨到几万条。这类"按玩家/UUID 累积的缓存"必须配一个退出清理，
     * 否则就是缓慢内存泄漏。
     */
    override fun onPlayerQuit(e: org.voidkit.core.api.events.VPlayerQuit) {
        MenuNav.clear(e.player)
    }

    /**
     * 手持绑定的物品右键 → 打开对应菜单。
     *
     * 只认主手，且会**取消原版交互** —— 否则右键时钟的同时还会触发物品本身的动作。
     * 所以不建议把菜单绑在能吃/能放置的物品上（比如面包、方块）。
     */
    override fun onRightClickItem(e: org.voidkit.core.api.events.VRightClickItem) {
        if (!enabled) return
        val player = e.player
        if (e.hand != net.minecraft.world.InteractionHand.MAIN_HAND) return

        val stack = e.stack
        if (stack.isEmpty) return
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val opener = MenuStore.openerOf(id) ?: return
        // 拒绝要说出口。以前这两条都是静默 return —— 玩家点了没任何反应，只会以为"这东西坏了"，
        // 服主也看不到自己的配置写错了（与垃圾桶那个"显示了再报错"是同一族问题）。
        val node = opener.permission.ifBlank { "voidkit.menu.use" }
        if (!VoidkitServices.permission().has(player, node)) {
            Msg.err(player, "你没有使用这个菜单的权限（$node）")
            return
        }
        if (MenuStore.menuOf(opener.menu) == null) {
            Msg.err(player, "菜单未定义：${opener.menu}（请检查 display/menu.json）")
            VoidkitCore.LOG.warn("菜单 opener 指向了不存在的菜单：{}", opener.menu)
            return
        }

        e.canceled = true
        MenuFeatureOpener.open(player, opener.menu)
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // 玩家节点公开：菜单是给玩家用的，零配置下必须能打开（右键绑定物品同理）
        PublicNodes.register(USE_NODE)

        // 根命令本身不设门槛 —— 按分支各自判定：
        //   打开菜单 = 玩家；列出 / 重载 / 代开 = 管理
        val root = Commands.literal("menu")

        root.then(
            Commands.argument("name", StringArgumentType.word())
                .requires { VoidkitServices.permission().has(it, USE_NODE) }
                .suggests { _, builder ->
                    SharedSuggestionProvider.suggest(MenuStore.file.menus.keys, builder)
                    builder.buildFuture()
                }
                .executes { open(it.source, StringArgumentType.getString(it, "name")) }
        )
        root.then(
            Commands.literal("list")
                .requires { CommandPerms.admin(it, MANAGE_NODE) }
                .executes { list(it.source) }
        )
        root.then(
            Commands.literal("reload")
                .requires { CommandPerms.admin(it, MANAGE_NODE) }
                .executes { reload(it.source) }
        )
        root.then(
            Commands.literal("open")
                .requires { CommandPerms.admin(it, MANAGE_NODE) }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .then(
                            Commands.argument("name", StringArgumentType.word())
                                .suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(MenuStore.file.menus.keys, builder)
                                    builder.buildFuture()
                                }
                                .executes {
                                    forceOpen(
                                        it.source,
                                        EntityArgument.getPlayer(it, "target"),
                                        StringArgumentType.getString(it, "name")
                                    )
                                }
                        )
                )
        )
        // 不带参数 = 打开 main（如果存在）
        root.requires { VoidkitServices.permission().has(it, USE_NODE) }
            .executes { open(it.source, DEFAULT_MENU) }
        dispatcher.register(root)
    }

    private fun open(source: CommandSourceStack, name: String): Int {
        val player = source.player
        if (player == null) {
            Msg.err(source, "这个命令只能由玩家使用（控制台请用 /menu open <玩家> <菜单>）")
            return 0
        }
        if (MenuStore.menuOf(name) == null) {
            Msg.err(source, "没有名为 $name 的菜单，可用：${MenuStore.file.menus.keys.joinToString(", ")}")
            return 0
        }
        MenuNav.clear(player)
        MenuFeatureOpener.open(player, name)
        return 1
    }

    private fun forceOpen(source: CommandSourceStack, target: ServerPlayer, name: String): Int {
        if (MenuStore.menuOf(name) == null) {
            Msg.err(source, "没有名为 $name 的菜单")
            return 0
        }
        MenuNav.clear(target)
        MenuFeatureOpener.open(target, name)
        Msg.ok(source, "已为 ${target.gameProfile.name} 打开菜单 $name")
        return 1
    }

    private fun list(source: CommandSourceStack): Int {
        Msg.text(source, "&8===== 菜单（${MenuStore.file.menus.size} 个）=====")
        MenuStore.file.menus.forEach { (name, def) ->
            Msg.text(source, "&b$name &8| &7${def.rows} 行 &8| &7${def.items.size} 个按钮 &8| &7${def.title}")
        }
        if (MenuStore.file.openers.isNotEmpty()) {
            Msg.text(source, "&8----- 物品绑定 -----")
            MenuStore.file.openers.forEach { (item, opener) ->
                Msg.text(source, "&f$item &7→ 右键打开 &b${opener.menu}")
            }
        }
        return 1
    }

    private fun reload(source: CommandSourceStack): Int {
        MenuStore.load()
        Msg.ok(source, "菜单已重新读取（${MenuStore.file.menus.size} 个菜单 / ${MenuStore.file.openers.size} 个物品绑定）")
        Msg.text(source, "&7图标 id 写错的会在控制台日志里给出警告，退化成屏障方块显示")
        return 1
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/menus") { _ ->
            ApiJson.obj(
                "ok", true,
                "settings", ApiJson.obj(
                    "closeOnAction", MenuStore.file.settings.closeOnAction,
                    "openSound", MenuStore.file.settings.openSound
                ),
                "menus", MenuStore.file.menus.map { (name, def) ->
                    ApiJson.obj(
                        "name", name,
                        "title", def.title,
                        "rows", def.rows,
                        "items", def.items.size
                    )
                },
                "openers", MenuStore.file.openers.map { (item, opener) ->
                    ApiJson.obj(
                        "item", item,
                        "menu", opener.menu,
                        "permission", opener.permission
                    )
                }
            )
        }

        router.post("/api/menu/{name}/open/{key}") { request ->
            val name = request.param("name")!!
            val opened = VoidkitServer.onServer {
                val def = MenuStore.menuOf(name) ?: throw IllegalArgumentException("菜单 $name 不存在")
                val player = PlayerViews.findOnline(request.param("key"))
                    ?: throw IllegalArgumentException("该玩家不在线")
                MenuNav.clear(player)
                MenuFeatureOpener.open(player, name)
                player.gameProfile.name
            }
            ApiJson.obj("ok", true, "message", "已为 $opened 打开菜单 $name")
        }
    }

    private companion object {
        /** 不带参数时默认打开的菜单名。 */
        const val DEFAULT_MENU = "main"

        /** 打开菜单（玩家日常入口）—— 公开节点。 */
        const val USE_NODE = "voidkit.menu.use"

        /** 管理菜单（list / reload / 代开）—— 不公开。 */
        const val MANAGE_NODE = "voidkit.menu"
    }
}
