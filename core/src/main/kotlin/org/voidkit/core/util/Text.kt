package org.voidkit.core.util

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

/**
 * 文本工具：把配置里的富文本语法转成原版 [Component]。
 *
 * 自己解析而不是引入 Adventure：服务端 mod 不想再加依赖，原版 [ChatFormatting.getByCode] 已提供完整码表。
 *
 * ## 支持的语法
 *
 * ```
 * &a &l &o &n &m &r &k          legacy 颜色与样式（& 与 § 都可以）
 * %变量%                        占位符，交给 PlaceholderRegistry 解析
 * [显示文字](动作)               可点击单元，动作可多个用 ; 分隔
 * ```
 *
 * `[文字](...)` 支持的动作：
 *
 * | 写法 | 点击后 |
 * |---|---|
 * | `run:/spawn` | 以玩家身份执行命令（会自动补前导 `/`） |
 * | `suggest:/home ` | 把命令填进聊天框，由玩家确认后发送 |
 * | `url:https://…` | 打开链接 |
 * | `copy:文本` | 复制到剪贴板 |
 *
 * 写在 `(...)` 最后的一对引号是**悬停提示**，支持 `&` 颜色与 `\n` 换行：
 *
 * ```
 * &7[&a回城&7](run:/spawn "&f点击回到出生点\n&7冷却 5 秒")
 * &7[&b官网&7](url:https://voidkit.org "&7在新标签页打开")
 * ```
 *
 * 只有 `[` 后面没跟 `(动作)` 时才当普通方括号处理，所以 `[在线] 5 人` 这种正常文本不受影响。
 */
object Text {

    /** 解析富文本：legacy 颜色 + `[文字](动作)` 可点击单元。 */
    fun legacy(text: String): MutableComponent {
        val root = Component.empty()
        var i = 0
        while (i < text.length) {
            val open = text.indexOf('[', i)
            if (open < 0) {
                root.append(colored(text.substring(i)))
                break
            }

            val close = matchBracket(text, open, '[', ']')
            if (close < 0 || close + 1 >= text.length || text[close + 1] != '(') {
                // 不是可点击结构 —— 把这个 '[' 当普通字符，继续往后找
                root.append(colored(text.substring(i, open + 1)))
                i = open + 1
                continue
            }

            val paren = matchBracket(text, close + 1, '(', ')')
            if (paren < 0) {
                root.append(colored(text.substring(i)))
                break
            }

            if (open > i) root.append(colored(text.substring(i, open)))
            root.append(clickable(colored(text.substring(open + 1, close)), text.substring(close + 2, paren)))
            i = paren + 1
        }
        return root
    }

    /** 多行解析，行间以换行符连接。 */
    fun legacyLines(lines: List<String>): MutableComponent {
        val root = Component.empty()
        lines.forEachIndexed { index, line ->
            if (index > 0) root.append(Component.literal("\n"))
            root.append(legacy(line))
        }
        return root
    }

    /** 去掉所有颜色代码与动作标记，得到纯文本（用于控制台日志、面板预览）。 */
    fun strip(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '&' || c == '§') && i + 1 < text.length && ChatFormatting.getByCode(text[i + 1]) != null) {
                i += 2
                continue
            }
            if (c == '[') {
                val close = matchBracket(text, i, '[', ']')
                if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                    val paren = matchBracket(text, close + 1, '(', ')')
                    if (paren > 0) {
                        sb.append(strip(text.substring(i + 1, close)))
                        i = paren + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * 转成原版 `§` 格式字符串，供 server.properties 这类只认格式码的场合使用。
     *
     * ⚠️ 是「去掉动作标记、**保留**颜色码」—— 和 [strip] 正好相反。
     * MOTD 走的就是这条路：服务器列表不支持点击事件，所以 `(run:/...)` 必须剥掉，
     * 但 `&b` 这类颜色必须保住。
     */
    fun toSection(text: String): String = dropActions(text).replace('&', '§')

    /** 只剥掉 `(动作)` 部分，保留显示文字与颜色码。 */
    private fun dropActions(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '[') {
                val close = matchBracket(text, i, '[', ']')
                if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                    val paren = matchBracket(text, close + 1, '(', ')')
                    if (paren > 0) {
                        sb.append(text, i + 1, close)
                        i = paren + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /**
     * 只解析颜色与样式，**不**处理 `[文字](动作)`。
     *
     * 给玩家自己输入的文本用 —— 玩家的发言必须走这条，否则他能发出可点击的链接/命令，
     * 变成钓鱼或骗点指令的入口。
     */
    fun legacyColor(text: String): MutableComponent = colored(text)

    /** 解析单条动作串（`run:xxx` / `suggest:xxx` / `url:xxx` / `copy:xxx`），无法识别返回 null。 */
    fun clickEvent(spec: String): ClickEvent? = parseClick(spec.trim())

    /** 构造悬停浮层（多行文本，支持 `&` 颜色与 `\n`）。 */
    fun hoverEvent(text: String): HoverEvent =
        HoverEvent(HoverEvent.Action.SHOW_TEXT, colored(text.replace("\\n", "\n")))

    // ---------- 内部 ----------

    /** 找与 [start] 处括号配对的收尾位置，支持嵌套；找不到返回 -1。 */
    private fun matchBracket(text: String, start: Int, open: Char, close: Char): Int {
        var depth = 0
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    /** 纯 legacy 颜色/样式解析（不含点击语法）。 */
    private fun colored(text: String): MutableComponent {
        val root = Component.empty()
        val formats = ArrayList<ChatFormatting>()
        val buffer = StringBuilder()
        var index = 0

        fun flush() {
            if (buffer.isEmpty()) return
            val part = Component.literal(buffer.toString())
            formats.forEach { part.withStyle(it) }
            root.append(part)
            buffer.setLength(0)
        }

        while (index < text.length) {
            val c = text[index]
            if ((c == '&' || c == '§') && index + 1 < text.length) {
                val code = ChatFormatting.getByCode(text[index + 1])
                if (code != null) {
                    flush()
                    if (code == ChatFormatting.RESET) formats.clear() else formats.add(code)
                    index += 2
                    continue
                }
            }
            buffer.append(c)
            index++
        }
        flush()
        return root
    }

    /** 把 `动作说明` 应用到 [base] 上。解析失败就原样返回，不会丢文字。 */
    private fun clickable(base: MutableComponent, spec: String): MutableComponent {
        var rest = spec.trim()
        var hover: String? = null

        // 末尾成对的引号 = 悬停提示
        if (rest.endsWith("\"")) {
            val quote = rest.lastIndexOf('"', rest.length - 2)
            if (quote >= 0) {
                hover = rest.substring(quote + 1, rest.length - 1)
                rest = rest.substring(0, quote).trim()
            }
        }

        val style = Style.EMPTY
            .withClickEvent(parseClick(rest))
            .withHoverEvent(hover?.let { hoverEvent(it) })
        return if (style.isEmpty) base else base.withStyle(style)
    }

    /** 取第一条能识别的动作；都不认识返回 null。 */
    private fun parseClick(spec: String): ClickEvent? {
        for (raw in spec.split(';')) {
            val part = raw.trim()
            val colon = part.indexOf(':')
            if (colon <= 0) continue
            val value = part.substring(colon + 1)
            when (part.substring(0, colon).lowercase()) {
                "run", "command", "cmd" -> return ClickEvent(ClickEvent.Action.RUN_COMMAND, withSlash(value))
                "suggest" -> return ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, withSlash(value))
                "url", "open" -> return ClickEvent(ClickEvent.Action.OPEN_URL, value)
                "copy" -> return ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value)
            }
        }
        return null
    }

    private fun withSlash(value: String): String = if (value.startsWith("/")) value else "/$value"
}
