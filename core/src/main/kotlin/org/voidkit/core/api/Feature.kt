package org.voidkit.core.api

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.neoforged.bus.api.SubscribeEvent
import org.voidkit.core.api.ApiRouter

/**
 * 一个“功能模块”。
 *
 * 所有服务器玩法（家、传送点、返回、TPA、管理工具、MOTD、TAB、记分牌、清扫…）都继承本类，
 * 并在 [PluginRegistry] 里登记一行即可接入：
 * 配置开关、事件挂载、命令注册、API 路由、启停与重载全部由框架统一驱动。
 *
 * 需要监听 NeoForge 事件的模块，直接在子类里写带 `@SubscribeEvent` 的方法，
 * 框架会把它注册到事件总线（只有被启用的模块才会注册）。
 */
abstract class Feature {

    /** 由 [PluginRegistry] 按配置写入。 */
    var enabled: Boolean = false

    /**
     * 本模块是否声明了 `@SubscribeEvent` 监听方法。
     *
     * NeoForge 的事件总线对“没有任何监听方法的对象”会直接抛
     * `IllegalArgumentException: ... has no @SubscribeEvent methods, but register was called anyway`，
     * 所以挂载前必须先判断。纯命令 / 纯 API 的模块（如 `/kcore`、清扫）本来就不该进总线。
     *
     * 用 `declaredMethods` 只扫本类，不递归父类 —— `Feature` 基类自身不定义任何监听器。
     */
    private val cachedListensToEvents: Boolean by lazy {
        javaClass.declaredMethods.any { it.isAnnotationPresent(SubscribeEvent::class.java) }
    }

    open fun listensToEvents(): Boolean = cachedListensToEvents

    /** 稳定唯一 id，例如 `homes` / `scoreboard`。会写进配置文件作为开关键。 */
    abstract val id: String

    /** 展示名，用于日志与面板。 */
    open val displayName: String get() = id

    /** 一句话说明，出现在 `/kcore features` 与面板上。 */
    open val description: String get() = ""

    /** 默认是否启用（可被配置文件覆盖）。 */
    open val defaultEnabled: Boolean get() = true

    /** 注册命令。服务端启动早期调用，勿在此访问世界。 */
    open fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {}

    /** 注册 API 路由。启动早期调用。 */
    open fun registerApi(router: ApiRouter) {}

    /**
     * 服务端即将启动。
     *
     * 这是**加载本模块配置的正确时机** —— 此时配置根目录已就绪、模块开关已按配置定好，
     * 而命令注册（[registerCommands]）还没发生，所以命令里能安全地引用刚读到的配置。
     *
     * 不要把这个逻辑塞进 `@Mod` 构造：那会让构造期产生 IO，在几百个 mod 的整合包里
     * 直接体现为拖长启动时间。只有被启用的模块才会收到本回调。
     */
    open fun onServerAboutToStart(server: MinecraftServer) {}

    /** 服务端启动完成，数据目录已就绪。 */
    open fun onServerStart(server: MinecraftServer) {}

    /** 服务端正在关闭，应在此落盘。 */
    open fun onServerStop(server: MinecraftServer) {}

    /** 每秒回调一次（不是每 tick）。周期性逻辑写这里，别写进 ServerTickEvent。 */
    open fun onSecond(server: MinecraftServer) {}

    /** 执行 `/kcore reload` 时调用。 */
    open fun onReload(server: MinecraftServer) {}
}
