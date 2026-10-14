package org.voidkit.core.platform.forge

import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.voidkit.core.api.ServerLifecycleListener

/**
 * Forge 生命周期事件 → [ServerLifecycleListener] 回调的翻译桥。
 *
 * 与 NeoForge 版逐行对应，差异只有三处：
 *   1. 事件包：`net.neoforged.neoforge.event.*` → `net.minecraftforge.event.*`
 *   2. tick 事件：`ServerTickEvent.Post` → `TickEvent.ServerTickEvent.Post`
 *      （Forge 52 起 tick 事件拆成 Pre/Post 内部类，已 javap 核实）
 *   3. `EventPriority` / `SubscribeEvent` 来自 `net.minecraftforge.eventbus.api`
 *
 * 关于 `HIGHEST`：插件读配置已统一走 `PluginRegistry.onBoot`，本事件只剩内核
 * 一个监听者，优先级不再影响正确性 —— 保留它纯属防御性（详见 NeoForge 版注释）。
 */
class ForgeLifecycleBridge(private val listener: ServerLifecycleListener) {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
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
    fun onTick(event: TickEvent.ServerTickEvent.Post) =
        listener.onServerTick(event.server)
}
