package org.voidkit.core.api

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer

/**
 * 服务器生命周期的平台无关回调。
 *
 * 这是「事件抽象」的第一步：把「启动准备 / 命令注册 / 启动完成 / 停止 / tick」这组
 * 每个加载器都有、但事件类包名不同的生命周期事件，收口成一组统一回调。
 *
 * platform 层（如 `NeoForgePlatform`）负责监听各自加载器的原生事件、翻译成这些回调；
 * 业务层（`ServerLifecycle`）只实现本接口，永不碰加载器事件类。
 *
 * 回调参数里的 `MinecraftServer` / `CommandDispatcher` 都是 `net.minecraft.*` 原版类，
 * 三平台同包同名，可以直接用、不需要再抽象。
 */
interface ServerLifecycleListener {

    fun onServerAboutToStart(server: MinecraftServer)

    fun onRegisterCommands(dispatcher: CommandDispatcher<CommandSourceStack>)

    fun onServerStarted(server: MinecraftServer)

    fun onServerStopping(server: MinecraftServer)

    /** 每个服务端 tick 回调（在方法内部自行按 20 tick 折算成每秒）。 */
    fun onServerTick(server: MinecraftServer)
}
