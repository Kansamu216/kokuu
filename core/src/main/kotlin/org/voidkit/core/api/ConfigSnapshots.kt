package org.voidkit.core.api

import java.util.concurrent.ConcurrentHashMap

/**
 * 插件配置快照登记处。
 *
 * ## 解决什么问题
 *
 * 网页面板想显示"每台服的清扫间隔是多少、家上限是多少、轮播开着没"。但那些配置
 * 分散在各插件里，而 **web 插件不能 import 其它插件** —— 那会让 web 变成"必须知道
 * 所有插件"的中心节点，任何一个插件改名都会把 web 编译搞挂。
 *
 * 所以反过来：**各插件主动把自己想公开的配置项报上来**，web 只管把登记处里的东西
 * 原样吐成 JSON。加一个新插件不需要动 web 一行代码。
 *
 * ## 用法
 *
 * ```kotlin
 * // 插件构造期（零 IO，只是登记一个 lambda）
 * ConfigSnapshots.register("essentials") {
 *     mapOf("tpaEnabled" to EssentialsStore.config.tpaEnabled, ...)
 * }
 * ```
 *
 * lambda 在**每次请求时**才执行，所以读到的一定是最新的值，
 * 不需要在 reload 时重新登记。
 */
object ConfigSnapshots {

    /** 插件名 -> 取快照的函数。 */
    private val providers = ConcurrentHashMap<String, () -> Map<String, Any?>>()

    fun register(owner: String, provider: () -> Map<String, Any?>) {
        providers[owner] = provider
    }

    fun unregister(owner: String) {
        providers.remove(owner)
    }

    /** 全部插件的快照。单个插件抛异常不会影响其它插件。 */
    fun all(): Map<String, Map<String, Any?>> =
        providers.entries
            .sortedBy { it.key }
            .associate { (owner, provider) ->
                owner to runCatching { provider() }.getOrDefault(emptyMap())
            }

    /** 已登记的插件名。 */
    fun owners(): List<String> = providers.keys.sorted()
}
