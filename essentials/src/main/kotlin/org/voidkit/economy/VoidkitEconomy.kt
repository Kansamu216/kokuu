package org.voidkit.economy

import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.placeholder.PlaceholderRegistry

/**
 * voidkit_economy —— 经济实现。
 *
 * ## 合并说明（8 jar -> 5 jar，2026-10-13）
 *
 * 本模块的代码已并入 **voidkit_essentials** 的 jar。为满足「**一个 jar 只声明一个 mod id**」
 * （Fabric 线的 `fabric.mod.json` 一个 jar 只能有一个 mod id），它**不再是独立的 `@Mod`** ——
 * 初始化改由 [org.voidkit.essentials.VoidkitEssentials] 在构造期显式触发。
 *
 * 合并**不影响**：数据路径仍是 `voidkit/economy/`（字面量常量，与 modId 无关）；
 * owner 标签仍用 [MODID]，展示 / 归组不变。
 *
 * ## 分工
 *
 * 不是每个服务器都要经济系统，所以分工是：
 *
 *  - **内核只定义接口**（[org.voidkit.core.api.EconomyProvider]）+ 一个“不可用”兜底实现，
 *    它不知道货币叫什么、余额存在哪、有没有转账税。
 *  - **这里提供默认实现**，通过 [VoidkitServices.installEconomy] 把实现换进去。
 *
 * 结果：**没有经济实现时，其它插件一行都不用改** —— 商店类调用会拿到“扣款失败”，
 * 表现为“暂时不能购买”，而不是报错崩服。
 */
class VoidkitEconomy {

    init {
        // 先把实现换进去。这样即使其它插件在构造期就查询经济服务，也能拿到真实现
        // （换了之后 VoidkitServices.economy() 才不是 UnavailableEconomy）。
        VoidkitServices.installEconomy(EconomyStore)

        // %balance% 变量由本模块提供
        PlaceholderRegistry.register("balance") { ctx ->
            ctx.player?.let { EconomyStore.plain(EconomyStore.balance(it.uuid)) }
        }

        PluginRegistry.register(EconomyFeature(), MODID)

        // 公开配置快照给面板（web 插件不需要 import 本模块）
        org.voidkit.core.api.ConfigSnapshots.register("economy") {
            val c = EconomyConfigStore.config
            mapOf(
                "currencyName" to c.currencyName,
                "startingBalance" to c.startingBalance,
                "maxBalance" to c.maxBalance,
                "payEnabled" to c.payEnabled,
                "payCooldownSeconds" to c.payCooldownSeconds,
                "taxPercent" to c.taxPercent,
            )
        }

        // 配置与账户数据在服务端启动阶段读 —— 构造期保持零 IO
        // （大型整合包加载很慢，别往这条路径上加磁盘操作）
        // ⚠️ 用内核钩子而非加载器事件：事件回调顺序不由我们控制。见 [PluginRegistry.onBoot]。
        PluginRegistry.onBoot(MODID) {
            EconomyConfigStore.load()
            EconomyStore.load()
            Values.load()
        }

        // 只重读**配置**：价格表与开关。`EconomyStore`（accounts.json）是玩家余额，
        // 属运行数据，重读会丢掉内存里尚未落盘的改动 —— 那一份不在这里重载。
        PluginRegistry.onReloadConfig(MODID) {
            EconomyConfigStore.load()
            Values.load()
        }
    }

    companion object {
        /** 保留原 modId 字符串，仅用作 PluginRegistry 的 owner 归组标签（不再是 FML 的 mod id）。 */
        const val MODID = "voidkit_economy"
    }
}
