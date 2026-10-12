package org.voidkit.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * 多语言。
 *
 * ## 为什么用「中文原文」当 key
 *
 * 常规做法是给每条消息起个 id（`home.set.success`），代价是**所有调用点都要改**。
 * 本项目有 561 条独立中文串，逐个改造既容易漏、又让 diff 淹没在噪音里。
 *
 * 所以这里反过来：**中文原文本身就是 key**。
 *
 * ```kotlin
 * Msg.ok(source, "已设置家")      // 中文环境直接显示；英文环境显示 "Home set"
 * ```
 *
 * 中文环境**完全零开销**（直接返回原文，连查表都不做）；英文缺词条时自动回退
 * 中文，**永远不会显示空白或裸 key**。
 *
 * ## 带参数的消息：模式匹配
 *
 * 代码里原本是 Kotlin 插值：`Msg.ok(source, "已向 $name 发送请求")`。
 * 插值之后的句子每条都不同，没法查表。但**不需要为此改代码** ——
 * 语言表里写带 `%s` 的模式，加载时编译成正则，运行时反解出参数：
 *
 * ```json
 * "已向 %s 发送传送请求": "Sent a teleport request to %1$s"
 * ```
 *
 * 译文里用 `%1$s` `%2$s` 编号引用参数，所以中英文语序不同也不影响。
 *
 * 匹配顺序：先精确，再模式（长的优先，避免短模式抢先命中）。匹配结果会缓存。
 */
object VoidkitLang {

    const val ZH_CN = "zh_cn"
    const val EN_US = "en_us"

    private const val RESOURCE_DIR = "/voidkit-lang"

    /** 一条带占位符的模式。 */
    private class Placeholder(val regex: Regex, val translation: String)

    @Volatile
    private var locale: String = ZH_CN

    /** 精确表：原文 -> 译文。中文环境为空。 */
    @Volatile
    private var table: Map<String, String> = emptyMap()

    /** 模式表：按模式长度降序（长模式更具体，先试）。 */
    @Volatile
    private var patterns: List<Placeholder> = emptyList()

    /** 查过的结果，避免同一句话反复跑正则。 */
    private val cache = ConcurrentHashMap<String, String>()

    private var langDir: Path? = null

    fun locale(): String = locale

    fun isChinese(): Boolean = locale == ZH_CN

    fun available(): List<String> = listOf(ZH_CN, EN_US)

    /** 切换语言。由 [org.voidkit.core.VoidkitCore.loadConfig] 调用，`/voidkit reload` 后再次生效。 */
    fun setLocale(tag: String) {
        locale = normalize(tag)
        cache.clear()
        if (locale == ZH_CN) {
            table = emptyMap()
            patterns = emptyList()
        } else {
            apply(loadTable(locale))
        }
    }

    /** 把 `en_us` / `en-US` / `en` 之类统一成内部形式。 */
    fun normalize(tag: String): String {
        val t = tag.trim().lowercase().replace('-', '_')
        return when {
            t.startsWith("zh") -> ZH_CN
            t.startsWith("en") -> EN_US
            else -> ZH_CN
        }
    }

    /**
     * 翻译一条消息。查不到就原样返回 —— 保证界面不会出现空白或裸 key。
     *
     * 中文环境下这是个直接 return，没有任何查找开销。
     */
    fun tr(zh: String): String {
        if (locale == ZH_CN || zh.isEmpty()) return zh

        table[zh]?.let { return it }
        cache[zh]?.let { return it }

        for (p in patterns) {
            val m = p.regex.matchEntire(zh) ?: continue
            val args = m.groupValues.drop(1).toTypedArray()
            val out = runCatching { String.format(p.translation, *args) }.getOrDefault(p.translation)
            cache[zh] = out
            return out
        }
        return zh
    }

    /** 翻译一条带参数的消息，[zh] 里用 `%s` 占位。 */
    fun tr(zh: String, vararg args: Any?): String {
        val pattern = tr(zh)
        if (args.isEmpty()) return pattern
        return runCatching { String.format(pattern, *args) }.getOrDefault(pattern)
    }

    /** 由内核在配置根目录就绪后调用。 */
    fun init(root: Path) {
        val dir = root.resolve("lang")
        langDir = dir
        runCatching { Files.createDirectories(dir) }
        runCatching { extractIfMissing(dir, EN_US) }
        if (locale != ZH_CN) apply(loadTable(locale))
    }

    // ---------- 内部 ----------

    private fun apply(entries: Map<String, String>) {
        val exact = HashMap<String, String>(entries.size * 2)
        val pats = ArrayList<Placeholder>()

        entries.forEach { (key, value) ->
            if (!key.contains("%s")) {
                exact[key] = value
            } else {
                // "已向 %s 发送请求" -> 已向(.+?)发送请求
                val body = key.split("%s").joinToString("(.+?)") { Regex.escape(it) }
                pats += Placeholder(Regex(body), value)
            }
        }
        // 长模式更具体，优先匹配
        pats.sortByDescending { it.regex.pattern.length }

        table = exact
        patterns = pats
    }

    /** 磁盘优先于 jar 内置 —— 服主改过的措辞不该被升级覆盖。 */
    private fun loadTable(tag: String): Map<String, String> {
        langDir?.let { dir ->
            val file = dir.resolve("$tag.json")
            if (Files.isRegularFile(file)) {
                runCatching { parse(Files.readString(file, Charsets.UTF_8)) }
                    .onSuccess { if (it.isNotEmpty()) return it }
            }
        }
        val builtin = VoidkitLang::class.java.getResourceAsStream("$RESOURCE_DIR/$tag.json")
            ?: return emptyMap()
        return builtin.use { stream ->
            runCatching { parse(stream.readBytes().toString(Charsets.UTF_8)) }.getOrDefault(emptyMap())
        }
    }

    private fun parse(text: String): Map<String, String> {
        val root = JsonParser.parseString(text)
        if (!root.isJsonObject) return emptyMap()
        val obj: JsonObject = root.asJsonObject
        val out = HashMap<String, String>(obj.size() * 2)
        obj.entrySet().forEach { (key, value) ->
            // 以 _ 开头的是元信息（如 "_locale"），不是词条
            if (key.startsWith("_") || !value.isJsonPrimitive) return@forEach
            out[key] = value.asString
        }
        return out
    }

    private fun extractIfMissing(dir: Path, tag: String) {
        val target = dir.resolve("$tag.json")
        if (Files.exists(target)) return
        val stream = VoidkitLang::class.java.getResourceAsStream("$RESOURCE_DIR/$tag.json") ?: return
        Files.write(target, stream.use { it.readBytes() })
    }
}
