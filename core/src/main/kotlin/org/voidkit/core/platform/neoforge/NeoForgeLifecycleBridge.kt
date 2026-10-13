package org.voidkit.core.platform.neoforge

import net.neoforged.bus.api.EventPriority
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

    /**
     * 顺序要求：本回调必须在**任何会读配置的东西**之前跑完 ——
     * 它负责「建目录 → `ConfigFiles.init(root)` → 迁移旧数据 → 读内核配置 →
     * `DataStore.init` → `PluginRegistry.boot`」这一串，之后才有配置可读。
     * 顺序错了会直接崩在 `ConfigFiles.resolve`（`rootDir` 未初始化）。
     *
     * ## 关于 `HIGHEST`（2026-10-13 架构调整后：保留，但已**不再是必需的**）
     *
     * 历史上各插件是**自己**监听这个事件读配置的，所以内核必须靠 `EventPriority.HIGHEST`
     * 把自己排到最前 —— 那是**靠优先级硬撑**出来的顺序：它没写进任何契约，
     * 且换到没有优先级的加载器就立刻失效（Fabric 线上实测插件跑在内核**前面**，
     * 读配置直接崩，服务端起不来）。
     *
     * 现在插件读配置统一走 `PluginRegistry.onBoot`（内核在 `boot()` 里按序调用），
     * 所以**本事件只剩内核一个监听者**，优先级不再影响正确性。
     *
     * 保留 `HIGHEST` 是**防御性**的：万一将来又有人往这个事件上挂监听，
     * 内核仍会排在最前，不至于悄悄退回「靠注册顺序碰运气」。
     */
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
    fun onTick(event: ServerTickEvent.Post) =
        listener.onServerTick(event.server)
}
