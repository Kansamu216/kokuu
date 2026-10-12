package org.kokuu.essentials

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.kokuu.core.core.PluginRegistry

/**
 * kokuu_essentials —— 基础指令插件。
 *
 * 家、传送点、出生点、返回、TPA、传送与管理工具。属于「绝大多数服都需要」的那一类，
 * 所以单独打成一个包，服主可以只装它 + 内核就有一套完整的基础指令。
 *
 * 构造期只做纯内存登记（见 [PluginRegistry] 的说明），不读任何文件。
 */
@Mod(KokuuEssentials.MODID)
class KokuuEssentials(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(HomeFeature(), MODID)
        PluginRegistry.register(WarpFeature(), MODID)
        PluginRegistry.register(SpawnFeature(), MODID)
        PluginRegistry.register(BackFeature(), MODID)
        PluginRegistry.register(TpaFeature(), MODID)
        PluginRegistry.register(TeleportFeature(), MODID)
        PluginRegistry.register(AdminToolsFeature(), MODID)
        PluginRegistry.register(InfoFeature(), MODID)
    }

    companion object {
        const val MODID = "kokuu_essentials"
    }
}
