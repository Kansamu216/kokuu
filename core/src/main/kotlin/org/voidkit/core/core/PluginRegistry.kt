package org.voidkit.core.core

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.config.VoidkitConfig

/**
 * 功能模块注册表 —— 插件系统的唯一入口。
 *
 * ## 与旧版的关键区别
 *
 * 内核**不再硬编码**模块清单。每个插件在自己的 `@Mod` 构造里调 [register]，
 * 把自己的模块交进来。内核完全不知道有哪些插件存在，也不知道它们装了几个模块。
 *
 * 这让「装满所有模块」和「只装核心」跑的是同一条代码路径 —— 没有 if 判断某个来源
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

    @Volatile
    private var booted = false

    /** RegisterCommandsEvent 先于 boot 触发时暂存的 dispatcher（见 registerCommands）。 */
    @Volatile
    private var pendingCommands: CommandDispatcher<CommandSourceStack>? = null

    /**
     * 启动钩子：各插件登记「内核 boot 完成后、命令注册之前」要做的事（通常是读自己的配置）。
     *
     * ## 为什么不让插件直接监听加载器的「服务端即将启动」事件
     *
     * 插件读配置的前提是「内核已经建好配置根目录、初始化了
     * [org.voidkit.core.util.ConfigFiles]」—— 这是个**顺序要求**。而加载器事件回调的顺序
     * **不由我们控制**：
     *  - 本分支（NeoForge 1.21.1）历史上靠给内核的监听标 `EventPriority.HIGHEST`
     *    把内核排到最前（见 `NeoForgeLifecycleBridge`）。那是**靠优先级硬撑**出来的顺序，
     *    并没有写进任何契约 —— 只要以后有别的注册路径插入，或者换到没有优先级的加载器，就会破。
     *  - **Fabric 就没有事件优先级**。2026-10-13 的 Fabric 分支实测：四个插件的 entrypoint
     *    跑在内核**前面**，插件回调里的 `ConfigFiles.resolve` 直接抛
     *    `IllegalStateException: ConfigFiles 尚未初始化（rootDir 未设置）`，服务端起不来。
     *
     * 所以把时机收回内核自己手里：插件登记钩子，内核在 [boot] 里按登记顺序调用。
     * 顺序由**内核的代码顺序**保证，而不是由加载器的派发语义保证 —— 这套机制
     * **与加载器无关**，NeoForge / Fabric 两条线用同一套，不再需要优先级。
     */
    private val bootHooks = ArrayList<Pair<String, () -> Unit>>()

    /**
     * 配置重读钩子：`/vk reload` 时调用，用来把**配置文件**重新读进内存。
     *
     * 🚨 为什么不直接重跑 [bootHooks]：那些钩子里除了读配置，还夹着
     *   * **数据加载** —— economy 的 `accounts.json`（玩家余额）、perm 的 users/groups，
     *     重读会把内存里尚未落盘的改动丢掉；
     *   * **运行时状态发布** —— web 会 `ServerEndpoint.publish(running = false)`，
     *     重跑会把「管理接口正在运行」误报成没在跑。
     * 所以配置文件与数据文件在这里是**分开**的两套钩子。
     */
    private val reloadHooks = ArrayList<Pair<String, () -> Unit>>()

    /** 登记一个启动钩子。`owner` 只用于失败时的日志归属。 */
    fun onBoot(owner: String, block: () -> Unit) {
        bootHooks += owner to block
    }

    /** 登记一个「重读配置文件」的钩子。`owner` 只用于失败时的日志归属。 */
    fun onReloadConfig(owner: String, block: () -> Unit) {
        reloadHooks += owner to block
    }

    /**
     * 重跑各插件的「读配置」动作。单个插件失败不影响其它插件，
     * 也**不会**让整次重载失败 —— 失败的插件沿用上一次的配置（日志里有 ERROR）。
     */
    fun runReloadHooks() {
        reloadHooks.forEach { (owner, block) ->
            runCatching(block).onFailure {
                LOG.error("{} 重读配置失败（沿用上一次的配置）", owner, it)
            }
        }
    }

    fun isBooted(): Boolean = booted

    /**
     * 登记一个功能模块。由各插件的 `@Mod` 构造调用。
     *
     * @param owner 该模块的来源标签（如 `voidkit_essentials`），仅用于面板分组显示，
     *              不要求它对应一个独立插件（合并后一个 jar 可提供多个来源标签）。
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

        // 插件的配置加载：放在 aboutToStart 之后、命令注册之前。
        // 顺序由**本方法**保证，不再依赖加载器事件的派发顺序（见 onBoot 的说明）。
        runBootHooks()

        booted = true

        // 若 RegisterCommandsEvent 已先于 boot 触发（NeoForge 1.21.1 的时序），
        // 现在 active 已填好，补注册命令。
        pendingCommands?.let { doRegisterCommands(it) }
        pendingCommands = null

        val byOwner = owners.values.groupingBy { it }.eachCount()
        LOG.info(
            "Voidkit 模块 {} / {} 已启用，按来源分组：{}",
            active.size, all.size,
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
     * 事件挂载接入点（历史保留位）。
     *
     * VEvent 抽象（见 [dispatch]）落地后，Feature 的业务事件统一由框架翻译后经 [dispatch]
     * 逐个通知，**没有任何 Feature 再挂到加载器原生事件总线**，本方法因此是无害空实现。
     *
     * 保留它（而非删掉调用点）是为了不改变 [boot] / [reload] 的调用时序与可读性。
     */
    fun attachEventHandlers() {
        // 空实现：不再有模块挂到加载器事件总线（VEvent 已取代）。
    }

    /** 通知已启用的模块：服务端即将启动，可以加载自己的配置了。 */
    fun aboutToStart(server: MinecraftServer) = forEach("启动准备") { it.onServerAboutToStart(server) }

    fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        if (!booted) {
            // 🚨 NeoForge 1.21.1 实测：RegisterCommandsEvent 在 ServerAboutToStartEvent
            // （boot 填 active）**之前**触发。此时 active 还是空的，直接注册会一个命令都不注册。
            // 所以先暂存 dispatcher，等 boot 完成后再补注册（见 boot()）。
            pendingCommands = dispatcher
            return
        }
        doRegisterCommands(dispatcher)
    }

    private fun doRegisterCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        LOG.info("[CMD] 注册命令，active 模块 = {}", active.map { it.id })
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
        // 顺序要紧：先让各插件把配置文件重新读进内存，再让它们「重新应用」。
        // 少了这一步，onReload 只是拿旧对象再刷一遍 —— 表现就是「提示重载成功、
        // 但改了配置文件没有任何效果」（2026-10-14 用户报的记分牌 / MOTD 不生效）。
        runReloadHooks()
        forEach("重载") { it.onReload(server) }
    }

    private inline fun forEach(action: String, block: (Feature) -> Unit) {
        active.forEach { feature ->
            runCatching { block(feature) }
                .onFailure { LOG.error("模块 {} {}失败", feature.id, action, it) }
        }
    }

    /** 按登记顺序调用启动钩子；单个插件抛异常不影响其它插件。 */
    private fun runBootHooks() {
        bootHooks.forEach { (owner, block) ->
            runCatching(block).onFailure { LOG.error("{} 的启动钩子失败（配置可能未加载）", owner, it) }
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

    /** 来源标签 -> 该来源提供的模块数。面板用。 */
    fun pluginSummary(): Map<String, Int> = owners.values.groupingBy { it }.eachCount()

    private val LOG get() = VoidkitCore.LOG
}
