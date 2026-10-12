package org.voidkit.core.platform.neoforge

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.ServerChatEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import org.voidkit.core.api.events.VChat
import org.voidkit.core.api.events.VPlayerDeath
import org.voidkit.core.api.events.VPlayerJoin
import org.voidkit.core.api.events.VPlayerQuit
import org.voidkit.core.api.events.VRightClickItem
import org.voidkit.core.core.PluginRegistry

/**
 * NeoForge 事件 → VEvent 的翻译桥（事件抽象的核心）。
 *
 * 监听加载器原生事件，翻译成平台无关的 VEvent，再通过 [PluginRegistry.dispatch]
 * 分发给所有已启用模块的对应回调。业务代码永远看不到 `net.neoforged` 的事件类。
 *
 * 带取消语义的事件（聊天 / 右键），在分发结束后把 `canceled` 写回原生事件的
 * `isCanceled`，保证业务代码的取消动作真实生效。
 */
class NeoForgeEventBridge {

    @SubscribeEvent
    fun onLogin(e: PlayerEvent.PlayerLoggedInEvent) {
        val p = e.entity as? ServerPlayer ?: return
        PluginRegistry.dispatch { it.onPlayerJoin(VPlayerJoin(p)) }
    }

    @SubscribeEvent
    fun onLogout(e: PlayerEvent.PlayerLoggedOutEvent) {
        val p = e.entity as? ServerPlayer ?: return
        PluginRegistry.dispatch { it.onPlayerQuit(VPlayerQuit(p)) }
    }

    @SubscribeEvent
    fun onDeath(e: LivingDeathEvent) {
        val p = e.entity as? ServerPlayer ?: return
        PluginRegistry.dispatch { it.onPlayerDeath(VPlayerDeath(p, e.source)) }
    }

    @SubscribeEvent
    fun onChat(e: ServerChatEvent) {
        val ve = VChat(e.player, e.rawText, e.message.copy())
        PluginRegistry.dispatch { it.onChat(ve) }
        e.isCanceled = ve.canceled
        e.message = ve.message
    }

    @SubscribeEvent
    fun onRightClick(e: PlayerInteractEvent.RightClickItem) {
        val p = e.entity as? ServerPlayer ?: return
        val ve = VRightClickItem(p, e.itemStack, e.hand)
        PluginRegistry.dispatch { it.onRightClickItem(ve) }
        e.isCanceled = ve.canceled
    }
}
