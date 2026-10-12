package org.voidkit.inspector

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.api.ConfigSnapshots
import org.voidkit.core.core.PluginRegistry

/**
 * voidkit_inspector —— 操作审计查询（COI）。
 *
 * 记录方块、容器、实体的玩家操作，落到嵌入式 SQLite；支持模组方块 / 物品的 NBT；
 * 内置对科技整合包（机械动力 / Mek / AE2）自动化产线的过滤，避免日志被机器刷爆。
 *
 * 一个模块：[InspectorFeature]。
 */
@Mod(VoidkitInspector.MODID)
class VoidkitInspector(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(InspectorFeature(), MODID)

        // 公开配置快照给面板（web 插件不需要 import 本插件）
        ConfigSnapshots.register("inspector") {
            val c = InspectorStore.config
            mapOf(
                "inspector.logPlayer" to c.logPlayer,
                "inspector.logAutomation" to c.logAutomation,
                "inspector.logNatural" to c.logNatural,
                "inspector.logContainers" to c.logContainers,
                "inspector.logKills" to c.logKills,
                "inspector.retentionDays" to c.retentionDays,
            )
        }

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            InspectorStore.load()
        }
    }

    companion object {
        const val MODID = "voidkit_inspector"
    }
}
