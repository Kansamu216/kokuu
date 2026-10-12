package org.voidkit.core.api

/**
 * 「家」的数量上限。
 *
 * ## 为什么单独抽一个接口
 *
 * 家上限有两个来源：**服务器级默认值**（属于 essentials 插件的配置）和
 * **按权限组的覆盖**（属于 perm 插件的逻辑，走 `voidkit.home.<数量>` 节点）。
 *
 * 原本 perm 插件直接去读内核配置里的 `defaultHomeLimit` / `maxHomes` ——
 * 那两个字段语义上属于 essentials，放在内核里是错位的。现在反过来：
 * essentials 把默认值通过这个接口暴露出来，perm 需要时来拿。
 *
 * 结果是 perm 不再关心"默认值存在哪"，essentials 也能单独卸载
 * （卸载后退化成 [DefaultHomeLimits] 的保守值）。
 */
interface HomeLimits {

    /** 默认组玩家能设几个家。 */
    fun defaultLimit(): Int

    /** 任何玩家都不能超过的硬上限。 */
    fun maxPerPlayer(): Int
}

/** 没有 essentials 插件时用的保守兜底。 */
object DefaultHomeLimits : HomeLimits {
    override fun defaultLimit(): Int = 3
    override fun maxPerPlayer(): Int = 32
}
