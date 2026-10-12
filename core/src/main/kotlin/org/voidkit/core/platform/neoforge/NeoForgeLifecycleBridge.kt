package org.voidkit.core.platform.neoforge

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.voidkit.core.api.ServerLifecycleListener

/**
 * NeoForge 生命周期事件 → [ServerLifecycleListener] 回调的翻译桥。
 *
 * 这里是加载器生命周期事件类**唯一**应该出现的地方：把 `net.neoforged.neoforge.event.*`
 * 翻译成 core.api 的平台无关回调，业务层永远看不到这些事件类。
 *
 * 移植 Forge / Fabric 时，只需按同样的接口写一份 `ForgeLifecycleBridge` /
 * `FabricLifecycleBridge`，`ServerLifecycle` 与全部业务代码零改动。
 */
class NeoForgeLifecycleBridge(private val listener: ServerLifecycleListener) {

    @SubscribeEvent
    fun onServerAboutToStart(event: ServerAboutToStartEvent) =
        listener.onServerAboutToStart(event.server)

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) =
        listener.onRegisterCommands(event.dispatcher)

    @SubscribeEvent
    fun onServerStarted(event: ServerStartedEvent) =
        listener.onServerStarted(event.server)

    @SubscribeEvent
    fun onServerStopping(event: ServerStoppingEvent) =
        listener.onServerStopping(event.server)

    @SubscribeEvent
    fun onTick(event: ServerTickEvent.Post) =
        listener.onServerTick(event.server)
}
