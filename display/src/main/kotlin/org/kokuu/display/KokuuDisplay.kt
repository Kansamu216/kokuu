package org.kokuu.display

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.kokuu.core.core.PluginRegistry

/**
 * kokuu_display —— 展示层插件。
 *
 * MOTD、TAB 顶底栏、侧边栏记分牌、聊天栏格式。四者共用同一套富文本能力：
 *
 *  - 传统颜色码 `&a` `&l`
 *  - 变量 `%player%` `%online%` `%tps%` …
 *  - 内联动作 `[文字](run:/spawn "悬停提示")` —— 点击执行、悬停提示
 *
 * ⚠️ 服务端能做的最大范围到此为止：**MOTD 与记分牌不支持点击/悬停**，这是原版协议限制
 * （前者是服务器列表文本，后者是纯字符串行）。聊天栏与 TAB 顶底栏支持。
 */
@Mod(KokuuDisplay.MODID)
class KokuuDisplay(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(MotdFeature(), MODID)
        PluginRegistry.register(TabFeature(), MODID)
        PluginRegistry.register(ScoreboardFeature(), MODID)
        PluginRegistry.register(ChatFormatFeature(), MODID)

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            DisplayStore.load()
        }
    }

    companion object {
        const val MODID = "kokuu_display"
        val LOG = LogUtils.getLogger()
    }
}
