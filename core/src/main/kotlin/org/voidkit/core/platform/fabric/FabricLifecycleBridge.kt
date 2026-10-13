package org.voidkit.core.platform.fabric

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import org.voidkit.core.api.ServerLifecycleListener

/**
 * Fabric 生命周期回调 → [ServerLifecycleListener] 的平台无关回调。
 *
 * 与 NeoForge 线的 `NeoForgeLifecycleBridge` 一一对应，
 * 区别只有「注册方式」：NeoForge 用事件总线 + 注解，Fabric 用 `Event.register`。
 *
 * | NeoForge 事件 | Fabric 回调 |
 * |---|---|
 * | `ServerAboutToStartEvent` | `ServerLifecycleEvents.SERVER_STARTING` |
 * | `RegisterCommandsEvent` | `CommandRegistrationCallback.EVENT` |
 * | `ServerStartedEvent` | `ServerLifecycleEvents.SERVER_STARTED` |
 * | `ServerStoppingEvent` | `ServerLifecycleEvents.SERVER_STOPPING` |
 * | `ServerTickEvent.Post` | `ServerTickEvents.END_SERVER_TICK` |
 *
 * ## 关于「顺序」这回事（与 NeoForge 版的最大不同）
 *
 * NeoForge 版必须给 `onServerAboutToStart` 标 `EventPriority.HIGHEST`，因为那边
 * 各插件的监听器与内核的监听器竞争同一个事件、注册先后不保证。
 *
 * Fabric **没有优先级概念**，但也不需要：`SERVER_STARTING` 只有这一个注册点
 * （内核自己的桥），所有插件读配置都发生在内核 `PluginRegistry.boot(...)` 主动
 * 派发的 `Feature.onServerAboutToStart` 回调里 —— 顺序由内核代码顺序保证，
 * 而不是由加载器的派发顺序保证。这一点比 NeoForge 线更稳。
 */
class FabricLifecycleBridge(private val listener: ServerLifecycleListener) {

    init {
        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            listener.onServerAboutToStart(server)
        }

        // ⚠️ 命令注册**先于** SERVER_STARTING 触发（Fabric 的 CommandManager 在
        //    MinecraftServer 构造期就建好了，而 SERVER_STARTING 在 runServer() 里才发）。
        //    所以此时 `PluginRegistry.active()` 还是空的 —— 内核用 `pendingCommands`
        //    暂存 dispatcher，等 boot() 定好开关后再补注册。这套机制是加载器无关的。
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            listener.onRegisterCommands(dispatcher)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            listener.onServerStarted(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            listener.onServerStopping(server)
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            listener.onServerTick(server)
        }
    }
}
