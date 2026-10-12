package org.kokuu.core.config

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.kokuu.core.KokuuCore

/**
 * 配置写回器 —— 供 Web 面板修改配置。
 *
 * 只接受**白名单字段**：未知键直接忽略，避免面板被人塞进奇怪的东西。
 * 数值统一做区间钳制（例如清扫间隔不能小于 30 秒），文本做长度与行数上限，
 * 保证面板写入的配置永远处于"运行得起来"的范围。
 *
 * 用法：`ConfigEditor.apply(json)` → 返回被改动的键名列表（用于回显给面板）。
 */
object ConfigEditor {

    private const val MAX_LINES = 20
    private const val MAX_LINE_LENGTH = 512
    private const val MAX_FORMAT_LENGTH = 512

    /** 远程管理 API 的模块 id，需与 [org.kokuu.core.feature.RemoteApiFeature.id] 保持一致。 */
    private const val REMOTE_API_ID = "remote-api"

    /** 应用面板提交的配置；返回实际生效的键。调用方负责 [KokuuCore.saveConfig] 与重载。 */
    fun apply(json: JsonObject): List<String> {
        val cfg = KokuuCore.config
        val changed = ArrayList<String>()

        // ---------- 模块开关 ----------
        var remoteApiToggle: Boolean? = null
        json.getAsJsonObject("features")?.let { obj ->
            obj.entrySet().forEach { (key, value) ->
                if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) return@forEach
                val id = key.trim()
                if (id.isEmpty() || id.length > 64) return@forEach
                val on = value.asBoolean
                cfg.features[id] = on
                changed += "features.$id"
                if (id == REMOTE_API_ID) remoteApiToggle = on
            }
        }

        // ---------- 展示层 ----------
        // MOTD / TAB / 记分牌 / 聊天栏已经拆到 display 目录下的独立文件，各自有专用端点写回：
        //   POST /api/motd · POST /api/tab · POST /api/scoreboard · POST /api/chat/format
        // 这里**刻意不碰它们** —— 一个入口对应一块配置，免得两边都能改、互相打架。

        // ---------- 清扫 ----------
        bool(json, "clearLagEnabled")?.let { cfg.clearLagEnabled = it; changed += "clearLagEnabled" }
        int(json, "clearLagIntervalSeconds", 30, 86400)?.let {
            cfg.clearLagIntervalSeconds = it
            changed += "clearLagIntervalSeconds"
        }
        int(json, "clearLagWarnSeconds", 0, 120)?.let { cfg.clearLagWarnSeconds = it; changed += "clearLagWarnSeconds" }
        bool(json, "clearLagRemoveItems")?.let { cfg.clearLagRemoveItems = it; changed += "clearLagRemoveItems" }
        bool(json, "clearLagRemoveXpOrbs")?.let { cfg.clearLagRemoveXpOrbs = it; changed += "clearLagRemoveXpOrbs" }
        bool(json, "clearLagRemoveArrows")?.let { cfg.clearLagRemoveArrows = it; changed += "clearLagRemoveArrows" }
        bool(json, "clearLagRemoveProjectiles")?.let {
            cfg.clearLagRemoveProjectiles = it
            changed += "clearLagRemoveProjectiles"
        }
        bool(json, "clearLagKeepNamedItems")?.let { cfg.clearLagKeepNamedItems = it; changed += "clearLagKeepNamedItems" }
        bool(json, "clearLagAnnounce")?.let { cfg.clearLagAnnounce = it; changed += "clearLagAnnounce" }

        // ---------- 传送与配额 ----------
        int(json, "defaultHomeLimit", 1, 512)?.let { cfg.defaultHomeLimit = it; changed += "defaultHomeLimit" }
        int(json, "maxHomes", 1, 512)?.let { cfg.maxHomes = it; changed += "maxHomes" }
        bool(json, "tpaEnabled")?.let { cfg.tpaEnabled = it; changed += "tpaEnabled" }
        int(json, "tpaTimeoutSeconds", 5, 3600)?.let { cfg.tpaTimeoutSeconds = it; changed += "tpaTimeoutSeconds" }
        bool(json, "backEnabled")?.let { cfg.backEnabled = it; changed += "backEnabled" }
        int(json, "teleportCooldownSeconds", 0, 3600)?.let {
            cfg.teleportCooldownSeconds = it
            changed += "teleportCooldownSeconds"
        }

        // ---------- 追踪与持久化 ----------
        bool(json, "recordIps")?.let { cfg.recordIps = it; changed += "recordIps" }
        int(json, "maxJoinLogEntries", 1000, 1_000_000)?.let {
            cfg.maxJoinLogEntries = it
            changed += "maxJoinLogEntries"
        }
        int(json, "autoSaveSeconds", 30, 3600)?.let { cfg.autoSaveSeconds = it; changed += "autoSaveSeconds" }

        // ---------- 管理 API（令牌不在此修改，避免面板顺手泄露） ----------
        // apiEnabled 是 remote-api 模块的"默认开关"。两者写同一个值，防止出现
        // "apiEnabled=true 但 features.remote-api=false" 这种自相矛盾的配置。
        bool(json, "apiEnabled")?.let {
            cfg.apiEnabled = it
            cfg.features[REMOTE_API_ID] = it
            changed += "apiEnabled"
        }
        // 反过来也一样：面板直接切模块开关时，同步回 apiEnabled
        remoteApiToggle?.let {
            cfg.apiEnabled = it
            if ("apiEnabled" !in changed) changed += "apiEnabled"
        }
        int(json, "apiPort", 1, 65535)?.let { cfg.apiPort = it; changed += "apiPort" }
        text(json, "apiBind", 64)?.let { cfg.apiBind = it; changed += "apiBind" }
        int(json, "apiRateLimitPerSecond", 1, 10000)?.let {
            cfg.apiRateLimitPerSecond = it
            changed += "apiRateLimitPerSecond"
        }

        if (cfg.defaultHomeLimit > cfg.maxHomes) cfg.defaultHomeLimit = cfg.maxHomes
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
