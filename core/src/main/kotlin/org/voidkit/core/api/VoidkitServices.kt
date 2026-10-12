package org.voidkit.core.api

import java.util.concurrent.ConcurrentHashMap

/**
 * 跨插件服务注册表。
 *
 * 这是插件之间**唯一**的通信方式 —— 插件 A 不 import 插件 B 的类，只认 [VoidkitServices]
 * 里的接口。好处是任意插件都能单独卸载：只要消费方对「服务缺席」有兜底，就不会崩。
 *
 * 典型用法：
 * ```kotlin
 * // 提供方（voidkit_perm 的 @Mod 构造里）
 * VoidkitServices.installPermission(MyPermissionProvider())
 *
 * // 消费方（任意插件）
 * if (VoidkitServices.permission().has(player, "voidkit.command.home")) { ... }
 * ```
 *
 * 线程安全：注册发生在 mod 构造期（单线程），读取分布在多个线程，故用并发容器。
 */
object VoidkitServices {

    private val registry = ConcurrentHashMap<Class<*>, Any>()

    // ---------------- 通用服务 ----------------

    /** 注册一个服务实现。重复注册会覆盖（后加载的插件优先）。 */
    fun <T : Any> register(type: Class<T>, impl: T) {
        registry[type] = impl
    }

    /** 取服务；未注册时返回 null。 */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(type: Class<T>): T? = registry[type] as? T

    /** 取服务；未注册时抛异常。只在「这个服务必须存在」的地方用。 */
    fun <T : Any> require(type: Class<T>): T =
        getOrNull(type) ?: error("服务 ${type.simpleName} 尚未注册（缺少提供它的插件？）")

    inline fun <reified T : Any> getOrNull(): T? = getOrNull(T::class.java)

    inline fun <reified T : Any> require(): T = require(T::class.java)

    // ---------------- 权限（带兜底，特殊对待） ----------------

    @Volatile
    private var permissionProvider: PermissionProvider = VanillaPermissionProvider

    /**
     * 当前生效的权限实现。
     *
     * **永远不返回 null** —— 没装权限插件时退化成 [VanillaPermissionProvider]（只认 OP）。
     * 这样调用方不用到处判空。
     */
    fun permission(): PermissionProvider = permissionProvider

    /** 换上一个权限实现。由权限插件在构造期调用。 */
    fun installPermission(provider: PermissionProvider) {
        permissionProvider = provider
    }

    /**
     * 当前跑的是不是自定义权限插件（即有没有被 `installPermission` 换过）。
     * 面板上用它显示「权限来源：VoidkitPerm / 原版 OP」。
     */
    fun hasPermissionPlugin(): Boolean = permissionProvider !== VanillaPermissionProvider

    // ---------------- 经济（同样带兜底） ----------------

    @Volatile
    private var economyProvider: EconomyProvider = UnavailableEconomy

    /**
     * 当前生效的经济实现。
     *
     * **永远不返回 null** —— 没装经济插件时退化成 [UnavailableEconomy]。
     * 消费方（商店 / 任务 / 称号扩展）不需要判空，只要在 `withdraw` 返回 -1 时
     * 当作「买不起」处理即可。
     */
    fun economy(): EconomyProvider = economyProvider

    /** 换上一个经济实现。由经济插件在构造期调用。 */
    fun installEconomy(provider: EconomyProvider) {
        economyProvider = provider
    }

    fun hasEconomyPlugin(): Boolean = economyProvider !== UnavailableEconomy

    // ---------------- 家上限（同样带兜底） ----------------

    @Volatile
    private var homeLimits: HomeLimits = DefaultHomeLimits

    /**
     * 家数量上限的来源。没装 essentials 时退化成保守默认值（3 / 32），
     * 所以 perm 插件可以放心直接调用，不用判空。
     */
    fun homes(): HomeLimits = homeLimits

    /** 由 essentials 插件在构造期调用。 */
    fun installHomeLimits(limits: HomeLimits) {
        homeLimits = limits
    }
}
