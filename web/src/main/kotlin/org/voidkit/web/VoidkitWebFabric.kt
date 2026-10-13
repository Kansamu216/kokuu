package org.voidkit.web

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import org.voidkit.core.core.PluginRegistry

/**
 * voidkit_web —— 管理 API 桥（**Fabric 入口**）。
 *
 * 这是整套里**唯一**会监听端口的模块，而且必须显式安装才会。内核与其它插件
 * 里一行 HTTP 代码都没有 —— 它们只往 [org.voidkit.core.api.ApiRouter] 这个
 * 纯数据登记表里挂自己的路由，由本插件负责把表挂到真正的 HTTP 服务器上。
 *
 * 配套的 hub 与 panel 两个 Go 程序是独立产物，不依赖 JVM —— 所以 Fabric 线上
 * 它们照常可用（HTTP 那一层与加载器无关）。
 *
 * ⚠️ 与 NeoForge 线的唯一差别：多挂了一个 `SERVER_STOPPED`（关服后清状态）。
 */
class VoidkitWebFabric : ModInitializer {

    override fun onInitialize() {
        PluginRegistry.register(RemoteApiFeature(), MODID)

        // 配置在服务端启动阶段读 —— 初始化期保持零 IO。
        // ⚠️ 用内核钩子而非加载器事件：事件回调顺序不由我们控制（Fabric 无优先级）。
        PluginRegistry.onBoot(MODID) {
            WebStore.load()
            // 把"管理接口现在是什么状态"告诉内核 —— /vk 命令要显示它，
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

        // 关服后把状态清掉，避免热重载时显示陈旧的"运行中"。
        // 这一条不涉及时序要求（关服后才跑），保留直接用加载器事件。
        ServerLifecycleEvents.SERVER_STOPPED.register {
            org.voidkit.core.api.ServerEndpoint.publish(null)
        }
    }

    companion object {
        const val MODID = "voidkit_web"
    }
}
