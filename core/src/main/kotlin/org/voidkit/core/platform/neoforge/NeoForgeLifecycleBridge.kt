package org.voidkit.core.platform.neoforge

import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.server.ServerAboutToStartEvent
import net.minecraftforge.event.server.ServerStartedEvent
import net.minecraftforge.event.server.ServerStoppingEvent
import org.voidkit.core.api.ServerLifecycleListener

/**
 * NeoForge 生命周期事件 → [ServerLifecycleListener] 回调的翻译桥。
 *
 * 这里是加载器生命周期事件类**唯一**应该出现的地方：把 `net.minecraftforge.event.*`
 * 翻译成 core.api 的平台无关回调，业务层永远看不到这些事件类。
 *
 * 移植 Forge / Fabric 时，只需按同样的接口写一份 `ForgeLifecycleBridge` /
 * `FabricLifecycleBridge`，`ServerLifecycle` 与全部业务代码零改动。
 */
class NeoForgeLifecycleBridge(private val listener: ServerLifecycleListener) {

    /**
     * ⚠️⚠️ 必须 `HIGHEST` —— 这是**硬性顺序要求**，不是优化。
     *
     * 各插件在自己的构造期就 `MinecraftForge.EVENT_BUS.addListener` 挂了一个
     * `ServerAboutToStartEvent` 监听，用来读自己的配置；而它们全都依赖内核在这之前完成：
     *
     * ```
     * 建目录 → ConfigFiles.init(root) → 迁移旧数据 → 读内核配置 → DataStore.init → PluginRegistry.boot
     * ```
     *
     * 顺序错了会直接崩在 `ConfigFiles.resolve`：
     * `UninitializedPropertyAccessException: lateinit property rootDir has not been initialized`。
     *
     * 注册先后**不能**作为保证：1.20.1（Forge）是**并行构造 mod** 的，而且
     * `EVENT_BUS.addListener(Consumer)` 与 `EVENT_BUS.register(obj)` 两条注册路径
     * 混在一起时，我方的注册未必排在插件前面 —— 实测在 1.20.1 上插件全部排在前面。
     * （1.21.1 上碰巧顺序正确，所以这个隐患一直没暴露。）
     *
     * 用优先级把顺序写死，才不依赖加载顺序这种偶然因素。
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

    // ⚠️ 1.20.1（Forge）的 tick 事件每 tick 触发**两次**（START / END），
    //    只取 END，语义才与 1.21 的 `ServerTickEvent.Post` 对齐；
    //    否则 onServerTick 会被调用两遍（每 2 秒一次的业务逻辑会变成每秒两次）。
    @SubscribeEvent
    fun onTick(event: TickEvent.ServerTickEvent) {
        if (event.phase == TickEvent.Phase.END) listener.onServerTick(event.server)
    }
}
