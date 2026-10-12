package org.voidkit.core.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 极简路由器：支持 `/api/players/{uuid}/ips` 这类路径占位符。
 * 模块通过 `Feature.registerApi(router)` 挂自己的路由。
 */
class ApiRouter {

    /** 一次请求的上下文。 */
    class Request {
        var method: String = ""
        var path: String = ""
        val pathParams: MutableMap<String, String> = mutableMapOf()
        val query: MutableMap<String, String> = mutableMapOf()
        var body: String = ""
        var remoteIp: String = ""
        var authToken: String = ""

        fun param(key: String): String? = pathParams[key]

        fun query(key: String, default: String? = null): String? = query[key] ?: default

        fun queryInt(key: String, default: Int): Int = query[key]?.toIntOrNull() ?: default

        fun queryBool(key: String, default: Boolean): Boolean =
            query[key]?.let { it == "true" || it == "1" } ?: default

        fun bodyJson(): JsonObject {
            if (body.isBlank()) return JsonObject()
            return runCatching { JsonParser.parseString(body) }
                .getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?: JsonObject()
        }

        fun requireField(vararg keys: String): JsonObject {
            val json = bodyJson()
            keys.forEach { key ->
                require(json.has(key)) { "缺少字段 $key" }
            }
            return json
        }
    }

    fun interface Handler {
        fun handle(request: Request): Any?
    }

    private class Route(val method: String, val segments: List<String>, val handler: Handler)

    private val routes = mutableListOf<Route>()

    fun get(path: String, handler: Handler) = route("GET", path, handler)

    fun post(path: String, handler: Handler) = route("POST", path, handler)

    fun route(method: String, path: String, handler: Handler) {
        val clean = path.removePrefix("/")
        routes += Route(
            method.uppercase(),
            if (clean.isEmpty()) emptyList() else clean.split("/"),
            handler
        )
    }

    fun match(request: Request): Handler? {
        val clean = request.path.removePrefix("/")
        val parts = if (clean.isEmpty()) emptyList() else clean.split("/")
        for (route in routes) {
            if (!route.method.equals(request.method, ignoreCase = true)) continue
            if (route.segments.size != parts.size) continue
            val params = HashMap<String, String>()
            var matched = true
            for (i in parts.indices) {
                val segment = route.segments[i]
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    params[segment.substring(1, segment.length - 1)] = parts[i]
                } else if (segment != parts[i]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                request.pathParams.putAll(params)
                return route.handler
            }
        }
        return null
    }

    fun listing(): List<String> = routes.map { "${it.method} /${it.segments.joinToString("/")}" }
}
