package org.kokuu.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

/**
 * 构造 JSON 响应的小工具。
 *
 * 🚨 这里曾经有个**系统性缺陷**：`put` 只有「是 JsonElement / 基本类型 / 其余一律 toString()」
 * 三条分支，于是 `listOf(JsonObject)` 被序列化成 `"[{...},{...}]"` 这样的**字符串**，
 * 而不是 JSON 数组。表现是面板报 `data.players.map is not a function`、
 * `entries.map is not a function` —— 前端拿到的是字符串。
 *
 * 现在统一走 [toElement]，集合 → JsonArray、Map → JsonObject，
 * 所有接口一起受益，不用在每个调用点记得包一层 `ApiJson.arr(...)`。
 */
object ApiJson {

    fun obj(vararg kv: Any?): JsonObject {
        val json = JsonObject()
        var i = 0
        while (i + 1 < kv.size) {
            put(json, kv[i]?.toString() ?: "", kv[i + 1])
            i += 2
        }
        return json
    }

    fun put(json: JsonObject, key: String, value: Any?) {
        json.add(key, toElement(value))
    }

    /** 把任意 Kotlin 值转成 Gson 的 [JsonElement]，集合/Map 会变成真正的 JSON 数组/对象。 */
    fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull.INSTANCE
        is JsonElement -> value
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Char -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject().apply {
            value.forEach { (k, v) -> add(k?.toString() ?: "", toElement(v)) }
        }
        is Iterable<*> -> JsonArray().apply { value.forEach { add(toElement(it)) } }
        is Array<*> -> JsonArray().apply { value.forEach { add(toElement(it)) } }
        else -> JsonPrimitive(value.toString())
    }

    fun arr(items: Collection<*>): JsonArray = toElement(items) as JsonArray

    fun ok(message: String): JsonObject = obj("ok", true, "message", message)

    fun error(code: String, message: String): JsonObject = obj("ok", false, "error", code, "message", message)
}
