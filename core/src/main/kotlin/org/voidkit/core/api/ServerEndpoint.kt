package org.voidkit.core.api

/**
 * 管理端点的状态快照。
 *
 * 内核**不实现** HTTP，但 `/voidkit status` 需要显示"管理接口在哪个地址、开没开"。
 * 直接让内核去读 web 插件的配置是不行的 —— 那就变成内核反向依赖插件了
 * （正是这次重构要拆掉的东西）。
 *
 * 所以反过来：**web 插件启动时把自己的状态写进来**，内核只读这份快照。
 * 没装 web 插件时 [ServerEndpoint] 里什么都没有，`/voidkit` 就显示"未启用"。
 *
 * 这也顺手解决了另一个问题：面板插件想显示"API 状态"时，不必去猜是谁实现的。
 */
object ServerEndpoint {

    /** 一次快照。字段都是基本类型，内核不需要知道 web 插件的数据类。 */
    class Info(
        val bind: String,
        val port: Int,
        val running: Boolean,
        val token: String,
        val rateLimitPerSecond: Int,
    )

    @Volatile
    private var info: Info? = null

    /** 由提供管理接口的插件（`voidkit_web`）在启动 / 关闭时调用。 */
    fun publish(info: Info?) {
        this.info = info
    }

    /** 当前快照；没有插件提供管理接口时为 null。 */
    fun current(): Info? = info

    /** 是否已经有插件提供了管理接口。 */
    fun available(): Boolean = info != null
}
