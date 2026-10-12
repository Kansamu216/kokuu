package org.kokuu.core.util

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

/**
 * 带中文注释的 JSON 读写（JSONC 风格）。
 *
 * 标准 JSON 不允许注释，但一份没人看得懂的配置文件等于没有配置 —— 这里做一层薄封装：
 *
 * - **写**：按 [comments] 表在每个字段上方插入 `// 说明`
 * - **读**：先把注释剥掉再交给 Gson，所以带注释的文件照样能正常解析
 *
 * 注释表用「字段路径 → 说明」表示，路径以 `.` 分隔（如 `"api.port"`、`"clearLag.intervalSeconds"`）。
 * 数组的注释挂在**数组字段本身**的路径上；元素内部的字段无法单独写注释（会让格式碎成一地）。
 *
 * 之所以不换成 TOML/YAML：这两者虽然原生支持注释，但要么得让使用者重学一套语法，
 * 要么得引入新依赖。保持 `.json` 后缀 + 注释，用现成的编辑器就能改。
 */
object CommentedJson {

    /** 把一个 JsonElement 渲染成带注释的文本。 */
    fun write(root: JsonElement, comments: Map<String, String>): String {
        val sb = StringBuilder(2048)
        render(sb, root, "", comments, 0)
        sb.append('\n')
        return sb.toString()
    }

    /**
     * 剥掉两种注释：行注释与块注释。
     *
     * 用状态机而不是正则 —— 字符串字面量里的双斜杠（比如 `"https://kokuu.org"`）必须原样保留，
     * 拿正则去替换一定会把 URL 啃掉半截。
     */
    fun stripComments(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                sb.append(c)
                when {
                    c == '\\' && i + 1 < text.length -> {
                        sb.append(text[i + 1])
                        i += 2
                        continue
                    }
                    c == '"' -> inString = false
                }
                i++
                continue
            }
            when {
                c == '"' -> {
                    inString = true
                    sb.append(c)
                    i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    i += 2
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i += 2
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString()
    }

    // ---------- 内部 ----------

    private fun render(
        sb: StringBuilder,
        element: JsonElement,
        path: String,
        comments: Map<String, String>,
        indent: Int,
    ) {
        val pad = "  ".repeat(indent)
        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                if (obj.size() == 0) {
                    sb.append("{}")
                    return
                }
                sb.append("{\n")
                val entries = obj.entrySet().toList()
                entries.forEachIndexed { index, entry ->
                    val key = entry.key
                    val childPath = if (path.isEmpty()) key else "$path.$key"
                    comments[childPath]?.let { note -> writeComment(sb, pad + "  ", note) }
                    sb.append(pad).append("  ").append('"').append(escape(key)).append("\": ")
                    render(sb, entry.value, childPath, comments, indent + 1)
                    if (index < entries.size - 1) sb.append(',')
                    sb.append('\n')
                }
                sb.append(pad).append('}')
            }

            element.isJsonArray -> {
                val arr = element.asJsonArray
                if (arr.size() == 0) {
                    sb.append("[]")
                    return
                }
                sb.append("[\n")
                arr.forEachIndexed { index, item ->
                    sb.append(pad).append("  ")
                    // 只有**第一个**元素带注释（父路径 + "[]"）；其余用不会命中注释表的路径，
                    // 否则同一段说明会在每个元素前重复一遍
                    render(sb, item, if (index == 0) "$path[]" else "$path[#]", comments, indent + 1)
                    if (index < arr.size() - 1) sb.append(',')
                    sb.append('\n')
                }
                sb.append(pad).append(']')
            }

            element.isJsonPrimitive -> sb.append(primitive(element.asJsonPrimitive))

            else -> sb.append("null")
        }
    }

    private fun writeComment(sb: StringBuilder, pad: String, note: String) {
        note.split('\n').forEach { line -> sb.append(pad).append("// ").append(line).append('\n') }
    }

    private fun primitive(value: JsonPrimitive): String =
        if (value.isString) "\"${escape(value.asString)}\"" else value.asString

    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        text.forEach { c ->
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
