package org.voidkit.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.voidkit.core.VoidkitCore

/**
 * 配置写回器 —— 供 Web 面板修改配置。
 *
 * 只接受**白名单字段**：未知键直接忽略，避免面板被人塞进奇怪的东西。
 * 数值统一做区间钳制（例如清扫间隔不能小于 30 秒），文本做长度与行数上限，
 * 保证面板写入的配置永远处于“运行得起来”的范围。
 *
 * 用法：`ConfigEditor.apply(json)` → 返回被改动的键名列表（用于回显给面板）。
 */
object ConfigEditor {

    private const val MAX_LINES = 20
    private const val MAX_LINE_LENGTH = 512
    private const val MAX_FORMAT_LENGTH = 512

    /** 远程管理 API 的模块 id，需与 [org.voidkit.core.feature.RemoteApiFeature.id] 保持一致。 */
    private const val REMOTE_API_ID = "remote-api"

    /** 应用面板提交的配置；返回实际生效的键。调用方负责 [VoidkitCore.saveConfig] 与重载。 */
    fun apply(json: JsonObject): List<String> {
        val cfg = VoidkitCore.config
        val changed = ArrayList<String>()

        // ---------- 模块开关 ----------
        json.getAsJsonObject("features")?.let { obj ->
            obj.entrySet().forEach { (key, value) ->
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return@forEach
                val id = key.trim()
                if (id.isEmpty() || id.length > 64) return@forEach
                cfg.features[id] = value.asBoolean
                changed += "features.$id"
            }
        }

        // ---------- 展示层 ----------
        // MOTD / TAB / 记分牌 / 聊天栏已经拆到 display 目录下的独立文件，各自有专用端点写回：
        //   POST /api/motd · POST /api/tab · POST /api/scoreboard · POST /api/chat/format
        // 这里**刻意不碰它们** —— 一个入口对应一块配置，免得两边都能改、互相打架。


        // ---------- 追踪与持久化 ----------
        bool(json, "recordIps")?.let { cfg.recordIps = it; changed += "recordIps" }
        int(json, "maxJoinLogEntries", 1000, 1_000_000)?.let {
            cfg.maxJoinLogEntries = it
            changed += "maxJoinLogEntries"
        }
        int(json, "autoSaveSeconds", 30, 3600)?.let { cfg.autoSaveSeconds = it; changed += "autoSaveSeconds" }

        cfg.normalize()
        return changed
    }

    // ---------- 字段读取（类型不对一律忽略） ----------

    private fun bool(json: JsonObject, key: String): Boolean? {
        val element = json.get(key) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) return null
        return element.asBoolean
    }

    private fun int(json: JsonObject, key: String, min: Int, max: Int): Int? {
        val element = json.get(key) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isNumber) return null
        return element.asInt.coerceIn(min, max)
    }

    private fun text(json: JsonObject, key: String, maxLength: Int): String? {
        val element = json.get(key) ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) return null
        return element.asString.take(maxLength)
    }

    /** 多行文本：接受字符串数组，也接受用 `\n` 分隔的单字符串。 */
    private fun lines(json: JsonObject, key: String, maxLines: Int): MutableList<String>? {
        val element = json.get(key) ?: return null
        val raw: List<String> = when {
            element.isJsonArray -> (element as JsonArray).mapNotNull { if (it.isJsonPrimitive) it.asString else null }
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.split('\n')
            else -> return null
        }
        return raw.take(maxLines).map { it.take(MAX_LINE_LENGTH) }.toMutableList()
    }
}
