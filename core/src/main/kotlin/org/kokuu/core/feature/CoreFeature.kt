package org.kokuu.core.feature

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import org.kokuu.core.KokuuCore
import org.kokuu.core.api.Feature
import org.kokuu.core.core.PluginRegistry
import org.kokuu.core.data.DataStore
import org.kokuu.core.api.KokuuServices
import org.kokuu.core.placeholder.PlaceholderRegistry
import org.kokuu.core.util.Msg
import org.kokuu.core.util.Placeholders
import java.util.Locale

/**
 * 内核管理：`/kcore`。
 *
 * 这是运维入口，集中处理"配置 / 数据 / 权限"三类操作，避免每个模块各写一套管理命令：
 *  - `status`        服务器与内核概况
 *  - `reload`        重载配置并热切换模块开关（含事件总线的注册/注销）
 *  - `save`          立刻落盘全部玩家档案与全局数据
 *  - `token`         查看管理 API 访问令牌
 *  - `api`           管理 API 监听状态
 *  - `features`      模块清单与开关状态
 *  - `placeholders`  全部可用变量
 *  - `group`         设置玩家权限组
 *  - `perm`          增删玩家附加权限节点
 */
class CoreFeature : Feature() {

    override val id = "core"
    override val displayName = "内核管理"
    override val description = "/kcore 状态、重载、保存、令牌、模块、变量与分组权限"

    /** 内核模块不允许被关掉，否则就失去了运维入口。 */
    override val defaultEnabled = true

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("kcore")
                .requires { KokuuServices.permission().has(it, ADMIN_NODE) }
                .executes { help(it.source) }
                .then(Commands.literal("status").executes { status(it.source) })
                .then(Commands.literal("reload").executes { reload(it.source) })
                .then(Commands.literal("save").executes { save(it.source) })
                .then(Commands.literal("token").executes { token(it.source) })
                .then(Commands.literal("api").executes { api(it.source) })
                .then(Commands.literal("features").executes { features(it.source) })
                .then(Commands.literal("placeholders").executes { placeholders(it.source) })
                .then(
                    Commands.literal("group")
                        .then(
                            Commands.argument("target", EntityArgument.player())
                                .then(
                                    Commands.argument("group", StringArgumentType.word())
                                        .suggests { _, builder ->
                                            SharedSuggestionProvider.suggest(KokuuServices.permission().groups(), builder)
                                        }
                                        .executes {
                                            setGroup(
                                                it.source,
                                                EntityArgument.getPlayer(it, "target"),
                                                StringArgumentType.getString(it, "group")
                                            )
                                        }
                                )
                        )
                )
                .then(
                    Commands.literal("perm")
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("target", EntityArgument.player())
                                        .then(
                                            Commands.argument("node", StringArgumentType.word())
                                                .executes {
                                                    editPermission(
                                                        it.source,
                                                        EntityArgument.getPlayer(it, "target"),
                                                        StringArgumentType.getString(it, "node"),
                                                        add = true
                                                    )
                                                }
                                        )
                                )
                        )
                        .then(
                            Commands.literal("del")
                                .then(
                                    Commands.argument("target", EntityArgument.player())
                                        .then(
                                            Commands.argument("node", StringArgumentType.word())
                                                .executes {
                                                    editPermission(
                                                        it.source,
                                                        EntityArgument.getPlayer(it, "target"),
                                                        StringArgumentType.getString(it, "node"),
                                                        add = false
                                                    )
                                                }
                                        )
                                )
                        )
                )
        )
    }

    // ---------- 子命令 ----------

    private fun help(source: CommandSourceStack): Int {
        Msg.info(source, "===== KokuuCore ${KokuuCore.VERSION} =====")
        listOf(
            "status" to "服务器与内核概况",
            "reload" to "重载配置并热切换模块",
            "save" to "立刻落盘玩家数据",
            "token" to "查看管理 API 令牌",
            "api" to "管理 API 监听状态",
            "features" to "模块清单",
            "placeholders" to "可用变量清单",
            "group <玩家> <组>" to "设置玩家权限组",
            "perm add|del <玩家> <节点>" to "增删玩家权限节点"
        ).forEach { (name, desc) -> Msg.info(source, "/kcore $name - $desc") }
        return 1
    }

    private fun status(source: CommandSourceStack): Int {
        val server = source.server
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        Msg.info(source, "===== 服务器状态 =====")
        Msg.info(source, "在线 ${server.playerCount}/${server.playerList.maxPlayers} | TPS ${Placeholders.tps(server)}")
        Msg.info(source, "运行时长 ${Msg.duration(server.tickCount / 20L)} | 内存 ${usedMb}MB / ${runtime.maxMemory() / 1048576L}MB")
        Msg.info(
            source,
            "模块 ${PluginRegistry.active().size}/${PluginRegistry.all().size} 已启用" +
                " | 玩家档案 ${DataStore.get()?.all()?.size ?: 0} 份"
        )
        return 1
    }

    private fun reload(source: CommandSourceStack): Int {
        KokuuCore.loadConfig()
        PluginRegistry.reload(source.server)
        Msg.ok(
            source,
            "配置已重载，当前启用 ${PluginRegistry.active().size}/${PluginRegistry.all().size} 个模块"
        )
        return 1
    }

    private fun save(source: CommandSourceStack): Int {
        val store = DataStore.get()
        if (store == null) {
            Msg.err(source, "数据存储尚未就绪")
            return 0
        }
        store.saveAll()
        store.saveGlobal()
        Msg.ok(source, "已保存 ${store.all().size} 份玩家档案与全局数据")
        return 1
    }

    private fun token(source: CommandSourceStack): Int {
        val token = KokuuCore.config.apiToken
        if (token.isBlank()) {
            Msg.err(source, "尚未生成 API 令牌")
            return 0
        }
        // 控制台看不到玩家消息，直接写日志
        if (source.player == null) {
            KokuuCore.LOG.info("管理 API 令牌: {}", token)
            Msg.ok(source, "令牌已写入服务器日志")
        } else {
            Msg.info(source, "管理 API 令牌：$token")
            Msg.info(source, "请勿在公开聊天里泄露；面板请求需带 Authorization: Bearer <token>")
        }
        return 1
    }

    private fun api(source: CommandSourceStack): Int {
        val cfg = KokuuCore.config
        val feature = PluginRegistry.byId("remote-api")
        // 内核不认识 ApiServer —— 那是 kokuu_web 插件里的东西。
        // 这里只能问注册表「那个模块在不在、开着没」，这正是插件化想要的边界。
        val running = feature?.enabled == true

        Msg.info(source, "===== 远程管理 API =====")
        val module = when {
            feature == null -> "未注册"
            feature.enabled -> "已启用"
            else -> "已关闭"
        }
        Msg.info(source, "模块：$module（id=remote-api；改 config.json 的 features 或用面板「模块」页）")
        Msg.info(source, "运行中：$running | 监听 ${cfg.apiBind}:${cfg.apiPort} | 限流 ${cfg.apiRateLimitPerSecond} 次/秒/IP")
        if (running) {
            Msg.info(source, "自检地址：http://${cfg.apiBind}:${cfg.apiPort}/api/health（免令牌）")
        }
        if (cfg.apiBind == "0.0.0.0") {
            Msg.err(source, "当前监听所有网卡，请确认防火墙与令牌安全")
        }
        if (!running) {
            Msg.info(source, "关闭状态不会监听任何端口；用 /kcore reload 或重启服务端生效")
        }
        return 1
    }

    private fun features(source: CommandSourceStack): Int {
        Msg.info(source, "===== 功能模块（${PluginRegistry.active().size}/${PluginRegistry.all().size}） =====")
        PluginRegistry.all().forEach { feature ->
            val mark = if (feature.enabled) "&a[开]" else "&7[关]"
            val line = "$mark &f${feature.id} &7- ${feature.displayName}"
            Msg.text(source, line)
        }
        return 1
    }

    private fun placeholders(source: CommandSourceStack): Int {
        val keys = PlaceholderRegistry.keys().sorted()
        Msg.info(source, "===== 可用变量（${keys.size}） =====")
        // 控制台/聊天一行一行发，每行 6 个，避免刷屏
        keys.chunked(6).forEach { group ->
            Msg.info(source, group.joinToString("  ") { "%$it%" })
        }
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun setGroup(source: CommandSourceStack, target: ServerPlayer, group: String): Int {
        val key = group.lowercase(Locale.ROOT)
        if (key !in KokuuServices.permission().groups()) {
            Msg.err(source, "没有名为 $key 的权限组，可用：${KokuuServices.permission().groups().joinToString(", ")}")
            return 0
        }
        val profile = DataStore.get()?.profile(target.uuid, target.gameProfile.name)
        if (profile == null) {
            Msg.err(source, "找不到 ${target.gameProfile.name} 的档案")
            return 0
        }
        val previous = profile.group
        profile.group = key
        DataStore.get()?.markDirty()
        DataStore.get()?.save(profile)
        Msg.ok(source, "已把 ${target.gameProfile.name} 的权限组从 $previous 改为 $key")
        Msg.info(target, "你的权限组已被设为 $key")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun editPermission(source: CommandSourceStack, target: ServerPlayer, node: String, add: Boolean): Int {
        val profile = DataStore.get()?.profile(target.uuid, target.gameProfile.name) ?: return 0
        val permissions = profile.permissions
        val name = target.gameProfile.name
        if (add) {
            if (permissions.contains(node)) {
                Msg.info(source, "$name 已经有节点 $node")
                return 0
            }
            permissions.add(node)
        } else {
            if (!permissions.remove(node)) {
                Msg.info(source, "$name 没有节点 $node")
                return 0
            }
        }
        DataStore.get()?.markDirty()
        DataStore.get()?.save(profile)
        Msg.ok(source, (if (add) "已授予 " else "已移除 ") + "$name 的节点 $node（共 ${permissions.size} 个）")
        return 1
    }

    private companion object {
        const val ADMIN_NODE = "kokuucore.admin"
    }
}
