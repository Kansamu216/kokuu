package org.kokuu.web

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.kokuu.core.core.PluginRegistry

/**
 * kokuu_web —— 管理 API 桥。
 *
 * 这是整套里**唯一**会监听端口的模块，而且必须显式安装才会。内核与其它插件
 * 里一行 HTTP 代码都没有 —— 它们只往 [org.kokuu.core.api.ApiRouter] 这个
 * 纯数据登记表里挂自己的路由，由本插件负责把表挂到真正的 HTTP 服务器上。
 *
 * 配套的 [hub] 与 [panel] 两个 Go 程序是独立仓库产物，不依赖 JVM。
 */
@Mod(KokuuWeb.MODID)
class KokuuWeb(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(RemoteApiFeature(), MODID)
    }

    companion object {
        const val MODID = "kokuu_web"
    }
}
