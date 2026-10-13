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
     * ⚠️⚠️ 必须 `HIGHEST` —— 这是**硬性顺序要求**，不是优化。
     *
     * 各插件在自己的构造期就 `EVENT_BUS.addListener` 挂了一个 `ServerAboutToStartEvent`
     * 监听，用来读自己的配置；而它们全都依赖内核在这之前完成：
     *
     * ```
     * 建目录 → ConfigFiles.init(root) → 迁移旧数据 → 读内核配置 → DataStore.init → PluginRegistry.boot
     * ```
     *
     * 顺序错了会直接崩在 `ConfigFiles.resolve`：
     * `UninitializedPropertyAccessException: lateinit property rootDir has not been initialized`。
     *
     * 注册先后**不能**作为保证：1.20.1（Forge）是**并行构造 mod** 的，而且
     * `addListener(Consumer)` 与 `register(obj)` 两条注册路径混在一起时，我方的注册未必排在
     * 插件前面（1.20.1 分支上实测插件全部排在内核前面，启动即崩）。
     * **本分支（1.21.1）目前只是碰巧顺序正确** —— 用优先级把顺序写死，才不依赖加载顺序这种偶然因素。
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
