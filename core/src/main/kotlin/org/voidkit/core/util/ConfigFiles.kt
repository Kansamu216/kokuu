package org.voidkit.core.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.voidkit.core.VoidkitCore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * 多文件配置存储。
 *
 * 早期所有配置挤在一个 `config.json` 里，加一个功能就得在几百行里翻 —— 现在按用途拆开：
 *
 * ```
 * config/voidkit/
 * ├── config.json          核心（模块开关 / API / 传送 / 追踪 / 清扫 / 权限组）
 * ├── display/
 * │   ├── motd.json        MOTD
 * │   ├── tab.json         TAB 玩家列表
 * │   ├── scoreboard.json  侧边栏记分牌
 * │   └── chat.json        聊天栏
 * └── lang/
 *     ├── zh_cn.json        语言包
 *     └── en_us.json
 * ```
 *
 * 两个刻意的设计：
 *  - **文件带中文注释**（JSONC 风格）：写入时按 [CommentedJson] 插入说明，读取时先剥注释。
 *    纯 JSON 没法写注释，但配置是给人看的。
 *  - **原子写入**：先写 `.tmp` 再整体替换，服务端被强杀也不会留下半个文件。
 *
 * 旧版把 display 相关字段放在 `config.json` 里的存量配置，由 [VoidkitCore.loadConfig] 一次性搬过来。
 */
object ConfigFiles {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private lateinit var rootDir: Path

    /** 是否已 [init]。用于在初始化前做校验。 */
    val initialized: Boolean get() = ::rootDir.isInitialized

    fun init(dir: Path) {
        rootDir = dir
        Files.createDirectories(rootDir)
    }

    fun root(): Path = rootDir

    /**
     * 解析到配置根下的相对路径。
     *
     * ⚠️ 未 [init] 时**主动抛清晰错误**，而不是放任 `lateinit` 抛出
     * `UninitializedPropertyAccessException: lateinit property rootDir has not been initialized`
     * —— 后者不带任何线索，排查时只能靠翻源码（1.20.1 移植时就这么踩过一次：
     * 插件的事件监听器排到了内核初始化前面，报错信息完全看不出是这个原因）。
     */
    fun resolve(relative: String): Path {
        check(::rootDir.isInitialized) {
            "ConfigFiles 尚未初始化（rootDir 未设置）。配置读写必须发生在 " +
                "ServerLifecycle.onServerAboutToStart 建好目录之后；" +
                "同理，依赖配置的插件不要在模组构造期直接读文件。" +
                "若确有必要挂 ServerAboutToStartEvent，请确认内核的 " +
                "NeoForgeLifecycleBridge.onServerAboutToStart（EventPriority.HIGHEST）" +
                "先于你的监听器执行。"
        }
        return rootDir.resolve(relative)
    }

    fun exists(relative: String): Boolean = Files.isRegularFile(resolve(relative))

    inline fun <reified T : Any> load(
        relative: String,
        comments: Map<String, String> = emptyMap(),
        noinline fallback: () -> T,
    ): T = load(relative, T::class.java, comments, fallback)

    /**
     * 读取 [relative]；文件不存在或解析失败时用 [fallback] 生成默认值并**立刻落盘**，
     * 这样使用者总能打开文件看到全部可配置项（以及每一项的中文说明）。
     */
    fun <T : Any> load(
        relative: String,
        type: Class<T>,
        comments: Map<String, String> = emptyMap(),
        fallback: () -> T,
    ): T {
        val file = resolve(relative)
        if (Files.isRegularFile(file)) {
            runCatching {
                val text = Files.readString(file, Charsets.UTF_8)
                // 文件是带注释的 —— 先剥掉，否则 Gson 直接解析失败并静默退回默认值
                gson.fromJson(CommentedJson.stripComments(text), type)?.let { return it }
            }.onFailure { VoidkitCore.LOG.warn("读取 {} 失败，改用默认值：{}", relative, it.message) }
        }
        val created = fallback()
        save(relative, created, comments)
        return created
    }

    fun save(relative: String, value: Any, comments: Map<String, String> = emptyMap()) {
        val file = resolve(relative)
        runCatching {
            file.parent?.let { Files.createDirectories(it) }
            val text = CommentedJson.write(gson.toJsonTree(value), comments)
            val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
            Files.writeString(tmp, text, Charsets.UTF_8)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { VoidkitCore.LOG.error("写入配置 {} 失败：{}", relative, it.message) }
    }

    /** 供迁移使用：把一个 JSON 文件解析成原始 map（忽略注释，用于探测旧字段是否存在）。 */
    fun readRaw(relative: String): Map<String, Any?>? {
        val file = resolve(relative)
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            val text = CommentedJson.stripComments(Files.readString(file, Charsets.UTF_8))
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(text, Map::class.java) as? Map<String, Any?>
        }.getOrNull()
    }

    /** 列出某个子目录下的文件名（不含路径）。 */
    fun listFiles(subDir: String, suffix: String = ".json"): List<String> {
        val dir = resolve(subDir)
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(suffix) }
                .map { it.fileName.toString() }
                .sorted()
                .toList()
        }
    }

    /** 读取 jar 内资源并原样写到磁盘（已存在则不动，不覆盖用户改过的版本）。 */
    fun extractResource(resourcePath: String, target: Path) {
        if (Files.exists(target)) return
        runCatching {
            val stream = ConfigFiles::class.java.getResourceAsStream(resourcePath)
                ?: error("资源不存在: $resourcePath")
            target.parent?.let { Files.createDirectories(it) }
            stream.use { Files.copy(it, target) }
        }.onFailure { VoidkitCore.LOG.warn("释放内置资源 {} 失败：{}", resourcePath, it.message) }
    }
}
