package org.voidkit.core.api

import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * 经济服务接口。
 *
 * 和权限一样，内核**只定义接口**，不实现。装 `voidkit_economy` 才会换上真正的实现；
 * 没装时 [VoidkitServices.economy] 返回 [UnavailableEconomy] —— 它永远说「余额 0、
 * 扣款失败」，于是商店、称号售价这类功能会安静地不可用，而**不会崩**。
 *
 * 为什么放内核而不是经济插件里：商店、任务、称号扩展都要花钱，如果接口定义在
 * `voidkit_economy` 里，它们就都得硬依赖那个插件。（这也是 LuckPerms 的做法 ——
 * API 独立于实现。）
 *
 * ## 金额语义
 *
 * 统一用 `Double`，但**所有实现都必须在写入前把值规整到两位小数**
 * （见 [round]）。否则反复加减会累积浮点误差，出现 `10.000000000000002` 这种余额。
 */
interface EconomyProvider {

    /** 这个实现是否真正可用。[UnavailableEconomy] 返回 false。 */
    val available: Boolean

    /** 余额。玩家不存在时返回 0。 */
    fun balance(uuid: UUID): Double

    /** 余额。 */
    fun balance(player: ServerPlayer): Double = balance(player.uuid)

    /** 余额是否够 [amount]。 */
    fun has(player: ServerPlayer, amount: Double): Boolean = balance(player) >= amount

    /**
     * 存入（发钱），返回**存后**的余额。
     *
     * @param reason 变动原因，写进交易日志；不参与计算。
     */
    fun deposit(player: ServerPlayer, amount: Double, reason: String = ""): Double

    /**
     * 扣款，返回**扣后**的余额。
     *
     * ⚠️ 余额不足时**必须**返回 `-1` 且不做任何修改 —— 调用方靠这个判断是否允许交易。
     */
    fun withdraw(player: ServerPlayer, amount: Double, reason: String = ""): Double

    /** 直接设成某个值（管理用）。 */
    fun setBalance(player: ServerPlayer, amount: Double, reason: String = "")

    /** 玩家之间转账。余额不足返回 false。 */
    fun transfer(from: ServerPlayer, to: ServerPlayer, amount: Double): Boolean

    /** 财富排行，从高到低。 */
    fun top(limit: Int): List<EconomyEntry>

    /** 格式化成显示文本（含货币符号），例如 `1,234.50 金币`。 */
    fun format(amount: Double): String

    /** 规整到两位小数，所有实现写库前都要过一遍它。 */
    fun round(amount: Double): Double = Math.round(amount * 100.0) / 100.0
}

/** 财富排行里的一行。 */
class EconomyEntry(val name: String, val uuid: String, val balance: Double)

/**
 * 兜底实现：没有经济插件时用它。
 *
 * 刻意**不抛异常** —— 商店类插件调用 [`withdraw`] 会拿到 `-1`（视为扣款失败），
 * 于是表现为「暂时不能购买」，而不是整个命令报错。
 */
object UnavailableEconomy : EconomyProvider {

    override val available: Boolean = false

    override fun balance(uuid: UUID): Double = 0.0

    override fun deposit(player: ServerPlayer, amount: Double, reason: String): Double = 0.0

    override fun withdraw(player: ServerPlayer, amount: Double, reason: String): Double = -1.0

    override fun setBalance(player: ServerPlayer, amount: Double, reason: String) = Unit

    override fun transfer(from: ServerPlayer, to: ServerPlayer, amount: Double): Boolean = false

    override fun top(limit: Int): List<EconomyEntry> = emptyList()

    override fun format(amount: Double): String = String.format(java.util.Locale.ROOT, "%.2f", amount)
}
