package org.kokuu.core.event

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.kokuu.core.KokuuCore
import org.kokuu.core.api.KokuuLang
import org.kokuu.core.api.KokuuServer
import org.kokuu.core.core.PluginRegistry
import org.kokuu.core.data.DataStore
import org.kokuu.core.util.ConfigFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 驱动所有模块的服务器生命周期。
 *
 * 模块本身不监听这些事件，只实现 [org.kokuu.core.api.Feature] 上的回调。
 *
 * 启动顺序经过刻意安排，每一步都有依赖关系：
 *
 * ```
 * ServerAboutToStart  建目录 → 迁移旧数据 → 读内核配置 → 起数据存储 → 通知各插件读自己的配置
 * RegisterCommands    注册命令（必须在上一步之后，命令会引用刚加载的配置）
 * ServerStarted       各模块开始工作
 * ServerStopping      落盘
 * ```
 *
 * 这里**不直接碰任何具体模块的业务逻辑** —— 连清扫、记分牌这些都是模块自己在回调里做的。
 */
object ServerLifecycle {

    /** 数据根目录名：服务端根目录下的 `kokuu/`。 */
    private const val ROOT_DIR_NAME = "kokuu"

    /** 旧版把数据放在世界目录下的这个名字里。 */
    private const val LEGACY_DIR_NAME = "kokuucore"

    private var ticks: Long = 0

    @Volatile
    private var startedAtMs: Long = 0L

    /** 服务端启动时刻（毫秒）。TPS / 运行时长这类与 API 无关的指标都从这里取。 */
    fun startedAt(): Long = startedAtMs

    /** 已运行秒数；未启动时为 0。 */
    fun uptimeSeconds(): Long {
        val started = startedAtMs
        return if (started <= 0L) 0L else (System.currentTimeMillis() - started) / 1000L
    }

    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) {
        val server = event.server

        // 1. 配置与数据根目录：<服务端根>/kokuu
        //    放在服务端根目录而不是存档里 —— 存档可以随便删掉重开，玩家档案和 IP 记录不该跟着没。
        val root = FMLPaths.GAMEDIR.get().resolve(ROOT_DIR_NAME)
        Files.createDirectories(root)
        ConfigFiles.init(root)

        // 服务端实例交给内核持有：各插件靠它把 API 请求切回主线程
        KokuuServer.attach(server)

        // 2. 老结构（world/kokuucore）自动搬一次
        migrateLegacy(server, root)

        // 3. 语言表：创建目录并铺出内置词表，之后 loadConfig 会按配置切语言
        KokuuLang.init(root)

        // 4. 内核配置
        KokuuCore.loadConfig()

        // 4. 数据存储（玩家档案 / 上下线日志）挂在 core 名下
        DataStore.init(root.resolve("core").resolve("data"))
        DataStore.get()?.joinLog?.trim(KokuuCore.config.maxJoinLogEntries)

        // 5. 启动注册表：定开关 → 挂事件总线 → 通知各插件加载自己的配置
        PluginRegistry.boot(KokuuCore.config, server)

        KokuuCore.LOG.info("数据根目录: {}", root)
    }

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        PluginRegistry.registerCommands(event.dispatcher)
    }

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) {
        startedAtMs = System.currentTimeMillis()
        PluginRegistry.startAll(event.server)
    }

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) {
        PluginRegistry.stopAll(event.server)
        DataStore.shutdown()
        KokuuServer.detach()
        startedAtMs = 0L
        KokuuCore.LOG.info("Kokuu 已保存并关闭")
    }

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Post) {
        ticks++
        if (ticks % 20L != 0L) return
        val server: MinecraftServer = event.server
        if (DataStore.ready()) {
            PluginRegistry.tickSecond(server)
        }
        val autoSave = KokuuCore.config.autoSaveSeconds.coerceAtLeast(30)
        if (ticks % (20L * autoSave) == 0L) {
            DataStore.get()?.let {
                it.saveAll()
                it.saveGlobal()
            }
        }
    }

    /**
     * 把旧版的 `world/kokuucore/` 搬到新的 `kokuu/` 结构下。
     *
     * 只在**新位置还没有内核配置**时执行一次，避免覆盖用户已经改过的新配置。
     * 旧目录只读不删 —— 迁移出错时用户手上还有原始数据。
     */
    private fun migrateLegacy(server: MinecraftServer, newRoot: Path) {
        runCatching {
            if (Files.exists(newRoot.resolve(KokuuCore.CORE_FILE))) return

            val legacy = server.getWorldPath(LevelResource.ROOT).resolve(LEGACY_DIR_NAME)
            if (!Files.isDirectory(legacy)) return

            // 旧目录 -> 新目录 的对应关系。没列到的文件不搬（避免把过期结构带进新版本）。
            val map = mapOf(
                "config.json" to "core/config.json",
                "display" to "display",
                "permissions" to "perm",
                "titles.json" to "title/titles.json",
                "players" to "core/data/players",
                "global.json" to "core/data/global.json",
                "logs" to "core/data/logs",
            )

            var moved = 0
            map.forEach { (from, to) ->
                val src = legacy.resolve(from)
                if (!Files.exists(src)) return@forEach
                val dst = newRoot.resolve(to)
                Files.createDirectories(dst.parent ?: newRoot)
                copyRecursively(src, dst)
                moved++
            }

            if (moved > 0) {
                KokuuCore.LOG.info("已从旧目录 {} 迁移 {} 项数据到 {}（旧目录保留未删）", legacy, moved, newRoot)
            }
        }.onFailure { KokuuCore.LOG.warn("旧数据迁移跳过：{}", it.message) }
    }

    private fun copyRecursively(src: Path, dst: Path) {
        Files.walk(src).use { stream ->
            stream.forEach { path ->
                val target = dst.resolve(src.relativize(path).toString())
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target)
                } else {
                    target.parent?.let { Files.createDirectories(it) }
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }
}
