package org.voidkit.web

import org.voidkit.core.config.LegacyConfig
import org.voidkit.core.util.ConfigFiles

/**
 * 管理 API 的配置，落盘到 `voidkit/web/config.json`。
 *
 * 这些字段原本长在内核的 `config.json` 里 —— 那是归属错位：内核连 HTTP 都不碰，
 * 却要替 web 插件保存监听地址和令牌。现在归位了。
 *
 * 存量服务器不受影响：首次生成这个文件时会从旧 `core/config.json` 里把值搬过来
 * （见 [WebStore.load] 的 fallback）。
 *
 * ⚠️ 由 Gson 反序列化，**缺字段读成零值** —— [normalize] 必须逐项兜底。
 */
class WebConfig {

    /** 是否启用管理 API（默认值；显式配置 `features["remote-api"]` 时以后者为准）。 */
    var enabled: Boolean = true

    /** 监听地址。默认仅本机；面板在别的机器上时改成 `0.0.0.0`。 */
    var bind: String = "127.0.0.1"

    var port: Int = 8765

    /**
     * 访问令牌。**拿到它就等于拿到整台服务器的管理权。**
     *
     * 首次启动自动生成；用 `/voidkit token` 查看。
     */
    var token: String = ""

    /** 单 IP 每秒请求上限。 */
    var rateLimitPerSecond: Int = 30

    fun normalize() {
        if (bind == null || bind.isBlank()) bind = "127.0.0.1"
        if (port <= 0 || port > 65535) port = 8765
        if (token == null) token = ""
        if (rateLimitPerSecond <= 0) rateLimitPerSecond = 30
        if (rateLimitPerSecond > 100_000) rateLimitPerSecond = 100_000
    }
}

/** 管理 API 配置的中文注释表。 */
object WebComments {

    val CONFIG: Map<String, String> = mapOf(
        "enabled" to "是否启用管理 API（供 Web 面板 / Hub 调用）。关掉后不影响游戏功能。\n" +
            "这只是\"默认开关\" —— 如果 config.json 的 features 里显式写了 remote-api，以那个为准。",
        "bind" to "API 监听地址。\n" +
            "  127.0.0.1  只允许本机访问（默认，最安全）\n" +
            "  0.0.0.0    所有网卡都能连 —— 面板在另一台机器上时必须这么设，\n" +
            "             同时务必换掉端口并保管好下面的 token。",
        "port" to "API 端口。",
        "token" to "访问令牌，首次启动自动生成。用 /voidkit token 查看。\n" +
            "⚠️ 不要外泄：拿到它就等于拿到整台服务器的管理权。\n" +
            "⚠️ 各游戏服的 token 只存在中心 Hub 上，永远不下发给浏览器。",
        "rateLimitPerSecond" to "单 IP 每秒请求上限。面板正常使用远低于这个值；\n" +
            "调小可以挡住暴力尝试令牌的流量。",
    )
}

/** 加载 / 保存 [WebConfig]。 */
object WebStore {

    private const val FILE = "web/config.json"

    @Volatile
    var config: WebConfig = WebConfig()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, WebComments.CONFIG) {
            // 文件不存在 —— 从旧 core/config.json 搬一次，别让服主的端口和令牌白设
            WebConfig().also { fresh ->
                LegacyConfig.string("apiBind")?.let { fresh.bind = it }
                LegacyConfig.int("apiPort")?.let { fresh.port = it }
                LegacyConfig.string("apiToken")?.let { fresh.token = it }
                LegacyConfig.int("apiRateLimitPerSecond")?.let { fresh.rateLimitPerSecond = it }
                LegacyConfig.bool("apiEnabled")?.let { fresh.enabled = it }
            }
        }
        config.normalize()

        if (config.token.isBlank()) {
            config.token = org.voidkit.core.VoidkitCore.randomToken()
            org.voidkit.core.VoidkitCore.LOG.info("已生成新的 API 访问令牌，用 /voidkit token 查看")
            save()
        }
    }

    fun save() = ConfigFiles.save(FILE, config, WebComments.CONFIG)

    /**
     * 从面板提交的 JSON 里应用管理 API 自己的字段。
     *
     * 这几个字段原本由内核的 `ConfigEditor` 处理 —— 那时它们长在内核配置里。
     * 现在归位了，所以由本插件自己接。
     *
     * ⚠️ **`token` 故意不接受写回** —— 面板能改令牌的话，一次误操作（或者一个
     * 被偷走的面板会话）就能把服主锁在门外，而他还得进服务器翻文件才能恢复。
     * 令牌只由服务端首次启动时生成。
     */
    fun applyFrom(json: com.google.gson.JsonObject): List<String> {
        val changed = ArrayList<String>(4)

        json.get("apiEnabled")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isBoolean) {
                config.enabled = it.asBoolean
                changed += "apiEnabled"
            }
        }
        json.get("apiBind")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isString) {
                config.bind = it.asString.trim()
                changed += "apiBind"
            }
        }
        json.get("apiPort")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) {
                config.port = it.asInt
                changed += "apiPort"
            }
        }
        json.get("apiRateLimitPerSecond")?.let {
            if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) {
                config.rateLimitPerSecond = it.asInt
                changed += "apiRateLimitPerSecond"
            }
        }

        if (changed.isNotEmpty()) {
            config.normalize()
            save()
        }
        return changed
    }
}
