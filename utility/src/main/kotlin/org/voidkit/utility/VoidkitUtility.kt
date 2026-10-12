package org.voidkit.utility

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.core.PluginRegistry

/**
 * voidkit_utility —— 服务器维护。
 *
 * 两块内容：
 *  - **清扫**（[ClearLagFeature]）：把堆积的掉落物收进垃圾桶，玩家能自己取回
 *  - **性能监测**（[PerfFeature]）：TPS / MSPT 采样、区块实体热点排名、可选温和干预
 *
 * ## 为什么世界命令搬走了
 *
 * 原来这里还兼着 `/day` `/gr` 这类原版命令简写。那属于”命令接管“，和”服务器维护"
 * 是两件不同的事 —— 混在一个插件里，服主想只装清扫就得连带装一堆命令别名。
 * 现在拆到 `voidkit_commands`，各管各的。
 *
 * 这个插件对大型整合包尤其重要：实体一多，未被拾取的掉落物会持续吃 tick 预算。
 * 所有参数都可配，默认值刻意保守（性能模块默认只观测、不动世界）。
 */
@Mod(VoidkitUtility.MODID)
class VoidkitUtility(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(ClearLagFeature(), MODID)
        PluginRegistry.register(PerfFeature(), MODID)

        // 公开配置快照给面板（web 插件不需要 import 本插件）
        org.voidkit.core.api.ConfigSnapshots.register("utility") {
            val c = ClearLagStore.config
            val p = PerfStore.config
            mapOf(
                "clearlag.intervalSeconds" to c.intervalSeconds,
                "clearlag.warnSeconds" to c.warnSeconds,
                "clearlag.announceChannel" to c.announceChannel,
                "clearlag.trashEnabled" to c.trashEnabled,
                "clearlag.maxTypes" to c.maxTypes,
                "clearlag.junkKeepLimit" to c.junkKeepLimit,
                "clearlag.maxPerSweep" to c.maxPerSweep,
                "perf.sampleSeconds" to p.sampleSeconds,
                "perf.warnTps" to p.warnTps,
                "perf.warnMspt" to p.warnMspt,
                "perf.interveneEnabled" to p.interveneEnabled,
            )
        }

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            ClearLagStore.load()
            PerfStore.load()
        }
    }

    companion object {
        const val MODID = "voidkit_utility"
    }
}
