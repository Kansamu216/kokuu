package org.voidkit.core.platform.forge

import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.event.ServerChatEvent
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.events.VChat
import org.voidkit.core.api.events.VPlayerDeath
import org.voidkit.core.api.events.VPlayerJoin
import org.voidkit.core.api.events.VPlayerQuit
import org.voidkit.core.api.events.VRightClickItem
import org.voidkit.core.core.PluginRegistry

/**
 * Forge 事件 → VEvent 的翻译桥（与 NeoForge 版逐行对应，只换包名）。
 *
 * 加载器原生事件类**只能**出现在 platform 层；业务代码永远看不到它们。
 */
class ForgeEventBridge {

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
        VoidkitCore.LOG.debug("[CHAT] {} -> [{}] canceled={}", e.rawText, ve.message.string, ve.canceled)
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
