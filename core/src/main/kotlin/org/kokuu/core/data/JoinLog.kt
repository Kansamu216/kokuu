package org.kokuu.core.data

import com.google.gson.GsonBuilder
import org.kokuu.core.KokuuCore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.ArrayDeque

/**
 * 上下线流水日志（JSONL 追加写，读时只 tail 最后 N 行）。
 *
 * 玩家档案里存的是 IP 汇总，这里是"原始事件流"，面板可以按时间轴回放。
 */
class JoinLog(private val file: Path) {

    class Entry(
        var t: Long = 0L,
        var uuid: String = "",
        var name: String = "",
        var ip: String = "",
        /** join / quit */
        var action: String = ""
    )

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private var writer: java.io.BufferedWriter? = null

    @Synchronized
    fun append(entry: Entry) {
        try {
            if (writer == null) {
                Files.createDirectories(file.parent)
                writer = Files.newBufferedWriter(
                    file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
                )
            }
            writer!!.write(gson.toJson(entry))
            writer!!.newLine()
            writer!!.flush()
        } catch (t: Throwable) {
            KokuuCore.LOG.warn("写入上线日志失败: {}", t.message)
        }
    }

    /** 读取最后 [limit] 条，按时间倒序。 */
    @Synchronized
    fun tail(limit: Int): List<Entry> {
        if (!Files.exists(file)) return emptyList()
        val lines = ArrayDeque<String>(limit.coerceAtLeast(16))
        try {
            Files.newBufferedReader(file, StandardCharsets.UTF_8).useLines { seq ->
                seq.forEach { line ->
                    if (lines.size >= limit) lines.pollFirst()
                    lines.addLast(line)
                }
            }
        } catch (t: Throwable) {
            KokuuCore.LOG.warn("读取上线日志失败: {}", t.message)
            return emptyList()
        }

        val out = ArrayList<Entry>(lines.size)
        lines.reversed().forEach { line ->
            if (line.isBlank()) return@forEach
            runCatching { gson.fromJson(line, Entry::class.java) }
                .getOrNull()
                ?.let { out.add(it) }
        }
        return out
    }

    /** 读取某个玩家的最近记录。 */
    @Synchronized
    fun tailFor(uuid: String, limit: Int): List<Entry> =
        tail(limit * 20).filter { it.uuid.equals(uuid, ignoreCase = true) }.take(limit)

    /** 超过上限时截断，返回被移除的行数。 */
    @Synchronized
    fun trim(maxLines: Int): Int {
        if (maxLines <= 0 || !Files.exists(file)) return 0
        return try {
            val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
            if (lines.size <= maxLines) {
                0
            } else {
                val kept = lines.subList(lines.size - maxLines, lines.size)
                close()
                Files.write(file, kept, StandardCharsets.UTF_8)
                lines.size - maxLines
            }
        } catch (t: Throwable) {
            KokuuCore.LOG.warn("裁剪上线日志失败: {}", t.message)
            0
        }
    }

    @Synchronized
    fun close() {
        runCatching { writer?.close() }
        writer = null
    }
}
