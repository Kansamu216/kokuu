package org.voidkit.core.event

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ServerLifecycleListener
import org.voidkit.core.api.VoidkitLang
import org.voidkit.core.api.VoidkitServer
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.data.DataStore
import org.voidkit.core.platform.Platforms
import org.voidkit.core.util.ConfigFiles
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 驱动所有模块的服务器生命周期。
 *
 * 模块本身不监听这些事件，只实现 [org.voidkit.core.api.Feature] 上的回调。
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
object ServerLifecycle : ServerLifecycleListener {

    /** 数据根目录名：服务端根目录下的 `voidkit/`。 */
    private const val ROOT_DIR_NAME = "voidkit"

    /**
     * 历史上出现过的数据目录位置，按"越早越靠前"排列。
     *
     * 这个项目的数据目录换过三次位置，这里是完整的一份账：
     *
     * | 版本 | 位置 | 说明 |
     * |---|---|---|
     * | 最早 | `<服务端>/world/kokuucore/` | 放在世界目录里 —— 删存档会连玩家数据一起删 |
     * | 之后 | `<服务端>/config/kokuucore/` | 移出世界目录，但仍在 `config/` 下 |
     * | 改名后 | `<服务端>/config/voidkit/` | 套件改名 |
     * | 现在 | `<服务端>/voidkit/` | 配置与数据合成一个目录树 |
     *
     * ⚠️ 漏掉任何一代，那批服主升级后就会"数据凭空消失"（其实是去读了新位置的空目录）。
     */
    private val LEGACY_ROOTS = listOf(
        "world/kokuucore",
        "config/kokuucore",
        "config/voidkit",
        "world/voidkit",
    )

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

    override fun onServerAboutToStart(server: MinecraftServer) {
        // 1. 配置与数据根目录：<服务端根>/voidkit
        //    放在服务端根目录而不是存档里 —— 存档可以随便删掉重开，玩家档案和 IP 记录不该跟着没。
        val root = Platforms.current.gameDir().resolve(ROOT_DIR_NAME)
        Files.createDirectories(root)
        ConfigFiles.init(root)

        // 服务端实例交给内核持有：各插件靠它把 API 请求切回主线程
        VoidkitServer.attach(server)

        // 2. 老结构（world/voidkit）自动搬一次
        migrateLegacy(server, root)

        // 3. 语言表：创建目录并铺出内置词表，之后 loadConfig 会按配置切语言
        VoidkitLang.init(root)

        // 4. 内核配置
        VoidkitCore.loadConfig()

        // 4. 数据存储（玩家档案 / 上下线日志）挂在 core 名下
        DataStore.init(root.resolve("core").resolve("data"))
        DataStore.get()?.joinLog?.trim(VoidkitCore.config.maxJoinLogEntries)

        // 5. 启动注册表：定开关 → 挂事件总线 → 通知各插件加载自己的配置
        PluginRegistry.boot(VoidkitCore.config, server)

        VoidkitCore.LOG.info("数据根目录: {}", root)
    }

    override fun onRegisterCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        VoidkitCore.LOG.info("[CMD] 收到 RegisterCommandsEvent，active 模块数 = {}", PluginRegistry.active().size)
        PluginRegistry.registerCommands(dispatcher)
    }

    override fun onServerStarted(server: MinecraftServer) {
        startedAtMs = System.currentTimeMillis()
        PluginRegistry.startAll(server)
    }

    override fun onServerStopping(server: MinecraftServer) {
        PluginRegistry.stopAll(server)
        DataStore.shutdown()
        VoidkitServer.detach()
        startedAtMs = 0L
        VoidkitCore.LOG.info("Voidkit 已保存并关闭")
    }

    override fun onServerTick(server: MinecraftServer) {
        ticks++
        if (ticks % 20L != 0L) return
        if (DataStore.ready()) {
            PluginRegistry.tickSecond(server)
        }
        val autoSave = VoidkitCore.config.autoSaveSeconds.coerceAtLeast(30)
        if (ticks % (20L * autoSave) == 0L) {
            DataStore.get()?.let {
                it.saveAll()
                it.saveGlobal()
            }
        }
    }

    /**
     * 把旧版本的数据搬到新的 `voidkit/` 结构下。
     *
     * 覆盖 [LEGACY_ROOTS] 里全部历史位置。只在**新位置还没有内核配置**时执行，
     * 避免覆盖用户已经改过的新配置；旧目录只读不删 —— 迁移出错时用户手上还有原始数据。
     *
     * 搬迁分两步：
     *  1. **结构改名** —— 老版本一个 `config.json` 装所有插件配置、`permissions/` 装权限，
     *     现在拆成 `core/config.json` + `perm/`。这里做目录/文件级的重命名映射。
     *  2. **原样搬** —— 已经是新结构的子目录（`display` / `economy` / `utility` …）直接复制。
     */
    private fun migrateLegacy(server: MinecraftServer, newRoot: Path) {
        runCatching {
            if (Files.exists(newRoot.resolve(VoidkitCore.CORE_FILE))) {
                VoidkitCore.LOG.debug("新目录已有内核配置，跳过旧数据迁移")
                return
            }

            val gameDir = Platforms.current.gameDir()
            var source: Path? = null
            for (relative in LEGACY_ROOTS) {
                // 相对服务端根目录的先查，再查世界目录（老目录名可能出现在任意一处）
                val candidates = listOf(
                    gameDir.resolve(relative),
                    server.getWorldPath(LevelResource.ROOT).resolve(relative.substringAfterLast('/')),
                )
                val hit = candidates.firstOrNull { Files.isDirectory(it) }
                if (hit != null) {
                    source = hit
                    break
                }
            }
            val legacy = source ?: return

            // 老结构 -> 新结构 的重命名。键支持两种写法（老布局直接放，新布局带一层目录）。
            val renames = listOf(
                listOf("config.json", "core/config.json"),
                listOf("players", "core/data/players"),
                listOf("global.json", "core/data/global.json"),
                listOf("logs", "core/data/logs"),
                listOf("permissions", "perm"),
                listOf("titles.json", "title/titles.json"),
            )
            var moved = 0
            renames.forEach { (from, to) ->
                val direct = legacy.resolve(from)
                val nested = legacy.resolve(to)
                val src = if (Files.exists(direct)) direct else if (Files.exists(nested)) nested else null
                if (src == null) return@forEach
                val dst = newRoot.resolve(to)
                if (Files.exists(dst)) return@forEach // 已经有更新的数据，不覆盖
                Files.createDirectories(dst.parent ?: newRoot)
                copyRecursively(src, dst)
                moved++
            }

            // 已经是新结构的子目录：原样搬过去
            val passthrough = listOf(
                "core", "display", "perm", "title", "essentials",
                "commands", "interact", "economy", "utility", "web", "lang",
            )
            passthrough.forEach { name ->
                val src = legacy.resolve(name)
                if (!Files.isDirectory(src)) return@forEach
                val dst = newRoot.resolve(name)
                if (Files.exists(dst)) return@forEach
                copyRecursively(src, dst)
                moved++
            }

            if (moved > 0) {
                VoidkitCore.LOG.info("已从旧目录 {} 迁移 {} 项数据到 {}（旧目录保留未删）", legacy, moved, newRoot)
            }
        }.onFailure { VoidkitCore.LOG.warn("旧数据迁移跳过：{}", it.message) }
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
