package org.voidkit.economy

import net.minecraftforge.fml.common.Mod
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.placeholder.PlaceholderRegistry

/**
 * voidkit_economy —— 独立的经济插件。
 *
 * ## 为什么独立
 *
 * 不是每个服务器都要经济系统。做成内核的一部分，等于逼所有服主接受一套货币规则。
 * 所以分工是：
 *
 *  - **内核只定义接口**（[org.voidkit.core.api.EconomyProvider]）+ 一个“不可用”兜底实现
 *    它不知道货币叫什么、余额存在哪、有没有转账税。
 *  - **本插件提供默认实现**，构造时通过 [VoidkitServices.installEconomy] 把自己的实现换进去。
 *
 * 结果：**不装本插件时，其它插件一行都不用改** —— 商店类调用会拿到“扣款失败”，
 * 表现为“暂时不能购买”，而不是报错崩服。
 *
 * ## 没有多余的东西
 *
 * 不装它，就不会有 `economy/` 目录、不会有 `%balance%` 变量、不会有 `/bal` 命令、
 * 更不会有任何账户文件。默认状态是“不存在”。
 */
// ⚠️ 1.20.1（Forge）的 mod 构造器**只认两种签名**：`()` 和 `(FMLJavaModLoadingContext)`
//    （见 FMLModContainer.constructMod）；`(IEventBus, ModContainer)` 是 NeoForge 1.21 的写法，
//    在 1.20.1 上会直接报 NoSuchMethodException 拒绝加载。
//    本套件不使用 mod 事件总线（游戏事件统一走 MinecraftForge.EVENT_BUS，见 Platforms.registerListener），
//    因此这里用无参构造 —— 两条版本线语义一致。
@Mod(VoidkitEconomy.MODID)
class VoidkitEconomy {

    init {
        // 先把实现换进去。这样即使其它插件在构造期就查询经济服务，也能拿到真实现
        // （换了之后 VoidkitServices.economy() 才不是 UnavailableEconomy）。
        VoidkitServices.installEconomy(EconomyStore)

        // %balance% 变量由本插件提供 —— 卸载它，这个变量自然就不存在了
        PlaceholderRegistry.register("balance") { ctx ->
            ctx.player?.let { EconomyStore.plain(EconomyStore.balance(it.uuid)) }
        }

        PluginRegistry.register(EconomyFeature(), MODID)

        // 公开配置快照给面板（web 插件不需要 import 本插件）
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
        MinecraftForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            EconomyConfigStore.load()
            EconomyStore.load()
            Values.load()
        }
    }

    companion object {
        const val MODID = "voidkit_economy"
    }
}
