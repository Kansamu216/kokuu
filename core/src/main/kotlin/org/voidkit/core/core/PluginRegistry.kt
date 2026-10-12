package org.voidkit.core.core

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.config.VoidkitConfig
import org.voidkit.core.platform.Platforms
import java.util.concurrent.ConcurrentHashMap

/**
 * 功能模块注册表 —— 插件系统的唯一入口。
 *
 * ## 与旧版的关键区别
 *
 * 内核**不再硬编码**模块清单。每个插件在自己的 `@Mod` 构造里调 [register]，
 * 把自己的模块交进来。内核完全不知道有哪些插件存在，也不知道它们装了几个模块。
 *
 * 这让「装满 7 个插件」和「只装核心」跑的是同一条代码路径 —— 没有 if 判断某个插件
 * 在不在，只有「注册表里有几个模块」这一个事实。
 *
 * ## 注册时机
 *
 * [register] 是纯内存操作，可以在任何插件的构造期随时调用，**不需要等内核先初始化**。
 * 真正的启动动作（读配置、挂事件、注册命令）统一定在服务端启动阶段，见 [boot].
 *
 * 线程安全：注册发生在 mod 构造期（FML 单线程），读取分散在多线程，故用并发容器。
 */
object PluginRegistry {

    /** 没标明来源时的默认归属。 */
    const val CORE_OWNER = "voidkit_core"

    private val all = LinkedHashMap<String, Feature>()
    private val owners = HashMap<String, String>()
    private val active = ArrayList<Feature>()

    /** 已经挂到事件总线上的模块。保证「注册一次、注销干净」，避免重复注册导致事件触发两次。 */
    private val attached: MutableSet<Feature> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var booted = false

    fun isBooted(): Boolean = booted

    /**
     * 登记一个功能模块。由各插件的 `@Mod` 构造调用。
     *
     * @param owner 提供该模块的插件 id（如 `voidkit_essentials`），用于面板分组显示。
     */
    fun register(feature: Feature, owner: String = CORE_OWNER) {
        if (all.containsKey(feature.id)) {
            LOG.error("重复的功能模块 id: {}（来自 {}），已忽略后者", feature.id, owner)
            return
        }
        all[feature.id] = feature
        owners[feature.id] = owner
    }

    /**
     * 服务端即将启动时由内核调用：读配置 → 决定开关 → 挂事件总线。
     *
     * 放在这里而不是 mod 构造期，是为了让**构造期保持零 IO** —— 大型整合包里
     * 上百个 mod 并行加载，在构造期读文件会把启动时间拉长好几秒。
     */
    fun boot(config: VoidkitConfig, server: MinecraftServer) {
        if (booted) {
            LOG.warn("PluginRegistry 已经启动过一次，忽略重复调用")
            return
        }
        applyConfig(config)
        attachEventHandlers()
        aboutToStart(server)
        booted = true

        val byOwner = owners.values.groupingBy { it }.eachCount()
        LOG.info(
            "Voidkit 模块 {} / {} 已启用，来自 {} 个插件：{}",
            active.size, all.size, byOwner.size,
            byOwner.entries.joinToString(", ") { "${it.key}(${it.value})" },
        )
    }

    /** 依据配置刷新启用状态（reload 复用）。 */
    fun applyConfig(config: VoidkitConfig) {
        active.clear()
        all.values.forEach { feature ->
            val on = config.featureEnabled(feature.id, feature.defaultEnabled)
            feature.enabled = on
            if (on) active.add(feature)
        }
    }

    /**
     * 让事件总线上的模块集合与 [active] 对齐。
     *
     * 🚨 只有 [Feature.listensToEvents] 为 true 的模块才会被 `register` —— NeoForge 的事件总线
     * 对「没有任何 @SubscribeEvent 方法的对象」会直接抛 IllegalArgumentException 并
     * **中断整个 mod 加载**。纯命令 / 纯 API 模块（如 `/koku`、清扫）因此不进总线。
     */
    fun attachEventHandlers() {
        var added = 0
        var removed = 0
        var skipped = 0

        all.values.forEach { feature ->
            if (!feature.enabled) {
                if (attached.remove(feature)) {
                    runCatching { Platforms.current.unregisterListener(feature) }
                        .onFailure { LOG.error("模块 {} 注销事件失败", feature.id, it) }
                    removed++
                }
                return@forEach
            }

            if (!feature.listensToEvents()) {
                skipped++
                return@forEach
            }

            if (attached.add(feature)) {
                runCatching { Platforms.current.registerListener(feature) }
                    .onFailure { LOG.error("模块 {} 注册事件失败", feature.id, it) }
                added++
            }
        }

        LOG.info(
            "事件总线：挂载 {}，摘除 {}，{} 个模块无事件监听（不进总线）",
            added, removed, skipped,
        )
    }

    /** 通知已启用的模块：服务端即将启动，可以加载自己的配置了。 */
    fun aboutToStart(server: MinecraftServer) = forEach("启动准备") { it.onServerAboutToStart(server) }

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        active.forEach { feature ->
            runCatching { feature.registerCommands(dispatcher) }
                .onFailure { LOG.error("模块 {} 注册命令失败", feature.id, it) }
        }
    }

    fun registerApi(router: ApiRouter) {
        active.forEach { feature ->
            runCatching { feature.registerApi(router) }
                .onFailure { LOG.error("模块 {} 注册 API 失败", feature.id, it) }
        }
    }

    fun startAll(server: MinecraftServer) = forEach("启动") { it.onServerStart(server) }

    fun stopAll(server: MinecraftServer) = forEach("停止") { it.onServerStop(server) }

    fun tickSecond(server: MinecraftServer) = forEach("tick") { it.onSecond(server) }

    /** 重载：先按新配置刷新开关与事件挂载，再通知各模块做自己的热更新。 */
    fun reload(server: MinecraftServer) {
        applyConfig(VoidkitCore.config)
        attachEventHandlers()
        forEach("重载") { it.onReload(server) }
    }

    private inline fun forEach(action: String, block: (Feature) -> Unit) {
        active.forEach { feature ->
            runCatching { block(feature) }
                .onFailure { LOG.error("模块 {} {}失败", feature.id, action, it) }
        }
    }

    /**
     * 把事件分发给所有已启用模块（VEvent 抽象的核心）。
     *
     * 由 platform 层的 EventBridge 调用：它把加载器原生事件翻译成 VEvent 后，
     * 通过本方法逐个通知 active 模块的对应回调。单个模块抛异常不影响其它模块。
     */
    fun dispatch(block: (Feature) -> Unit) {
        active.forEach { feature ->
            runCatching { block(feature) }
                .onFailure { LOG.error("模块 {} 事件处理失败", feature.id, it) }
        }
    }

    fun all(): List<Feature> = all.values.toList()

    fun active(): List<Feature> = active.toList()

    fun byId(id: String): Feature? = all[id]

    fun ownerOf(featureId: String): String = owners[featureId] ?: CORE_OWNER

    /** 插件 id -> 该插件提供的模块数。面板用。 */
    fun pluginSummary(): Map<String, Int> = owners.values.groupingBy { it }.eachCount()

    private val LOG get() = VoidkitCore.LOG
}
