package org.voidkit.web

import net.minecraft.server.MinecraftServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature

/**
 * 远程管理 API —— 把对内提供的 HTTP 管理接口做成一个**可开关的模块**。
 *
 * 之前这块是硬接在 [org.voidkit.core.event.ServerLifecycle] 上的：服务端一启动就起 HTTP，
 * 只有 `apiEnabled` 一个配置项能关，且和其它模块的开关不在一处、不受 `/kcore reload` 管。
 * 现在它就是一个普通的 [Feature]，于是自动获得：
 *
 *  - `config.json` 里 `features["remote-api"]` 的开关（`apiEnabled` 作为默认值继续兼容）
 *  - `/kcore reload` 热启停，不必重启服务端
 *  - 面板「模块」页的一个开关，和 MOTD / 记分牌并列
 *  - `/kcore features` 清单里可见
 *
 * 什么时候该关掉它：纯单机测试、或者你根本不用面板/监控。关掉后**不会**监听任何端口，
 * 少一个暴露面 —— 这也是把它模块化的主要理由。
 *
 * ⚠️ 本模块不监听任何 NeoForge 事件（没有 `@SubscribeEvent`），
 * 所以 [Feature.listensToEvents] 为 false，不会被注册到事件总线。
 */
class RemoteApiFeature : Feature() {

    override val id = "remote-api"
    override val displayName = "远程管理 API"
    override val description = "对外提供 HTTP 管理接口（面板 / Hub / 监控调用）；不需要远程管理时关掉即可"

    /** 兼容早期的 `apiEnabled` 开关；显式写 `features["remote-api"]` 时以后者为准。 */
    override val defaultEnabled: Boolean get() = WebStore.config.enabled

    /**
     * 挂载内核通用接口（健康检查、玩家、日志、配置、广播…）。
     *
     * 各模块自己的路由不在这里挂 —— [org.voidkit.core.core.PluginRegistry] 会遍历所有
     * 启用中的模块调用它们的 `registerApi`，顺序无关，互不依赖。
     */
    override fun registerApi(router: ApiRouter) {
        ApiDefaults.register(router)
    }

    override fun onServerStart(server: MinecraftServer) {
        ApiServer.get().start(server)
    }

    override fun onServerStop(server: MinecraftServer) {
        ApiServer.get().stop()
    }

    /** 开关被 reload 改动时跟着起停。 */
    override fun onReload(server: MinecraftServer) {
        if (enabled) {
            ApiServer.get().start(server)
        } else {
            ApiServer.get().stop()
        }
    }
}
