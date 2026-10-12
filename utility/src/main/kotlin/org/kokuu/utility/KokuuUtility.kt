package org.kokuu.utility

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.kokuu.core.core.PluginRegistry

/**
 * kokuu_utility —— 服务器维护工具。
 *
 * 定期清扫掉落物（带倒计时提醒）+ 原版世界命令的短别名。
 *
 * 这个插件对大型整合包尤其重要：实体一多，未被拾取的掉落物会持续吃 tick 预算。
 * 清扫的相关参数全部可配，默认值刻意保守。
 */
@Mod(KokuuUtility.MODID)
class KokuuUtility(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(ClearLagFeature(), MODID)
        PluginRegistry.register(WorldToolsFeature(), MODID)
    }

    companion object {
        const val MODID = "kokuu_utility"
    }
}
