package org.voidkit.web

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import org.voidkit.core.core.PluginRegistry

/**
 * voidkit_web —— 管理 API 桥。
 *
 * 这是整套里**唯一**会监听端口的模块，而且必须显式安装才会。内核与其它插件
 * 里一行 HTTP 代码都没有 —— 它们只往 [org.voidkit.core.api.ApiRouter] 这个
 * 纯数据登记表里挂自己的路由，由本插件负责把表挂到真正的 HTTP 服务器上。
 *
 * 配套的 hub 与 panel 两个 Go 程序是独立产物，不依赖 JVM。
 */
@Mod(VoidkitWeb.MODID)
class VoidkitWeb(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(RemoteApiFeature(), MODID)

        // 配置在服务端启动阶段读 —— 构造期保持零 IO。
        // ⚠️ 用内核的 [PluginRegistry.onBoot] 而非加载器事件：事件回调顺序不由我们控制。
        PluginRegistry.onBoot(MODID) {
            WebStore.load()
            // 把"管理接口现在是什么状态"告诉内核 —— /voidkit 命令要显示它，
            // 但内核不能反过来 import 本插件（见 ServerEndpoint 的说明）
            org.voidkit.core.api.ServerEndpoint.publish(
                org.voidkit.core.api.ServerEndpoint.Info(
                    bind = WebStore.config.bind,
                    port = WebStore.config.port,
                    running = false, // 真正的启动在 RemoteApiFeature.onServerStart 里，那时再更新
                    token = WebStore.config.token,
                    rateLimitPerSecond = WebStore.config.rateLimitPerSecond,
                )
            )
        }

        // 关服后把状态清掉，避免热重载时显示陈旧的"运行中"
        NeoForge.EVENT_BUS.addListener { _: ServerStoppedEvent ->
            org.voidkit.core.api.ServerEndpoint.publish(null)
        }
    }

    companion object {
        const val MODID = "voidkit_web"
    }
}
