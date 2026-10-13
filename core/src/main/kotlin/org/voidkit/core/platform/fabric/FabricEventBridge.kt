package org.voidkit.core.platform.fabric

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.ItemStack
import org.voidkit.core.api.events.VChat
import org.voidkit.core.api.events.VPlayerDeath
import org.voidkit.core.api.events.VPlayerJoin
import org.voidkit.core.api.events.VPlayerQuit
import org.voidkit.core.api.events.VRightClickItem
import org.voidkit.core.core.PluginRegistry

/**
 * Fabric 事件 → VEvent 的翻译桥（事件抽象的核心）。
 *
 * 与 NeoForge 线的 `NeoForgeEventBridge` 一一对应：
 * 监听 Fabric API 的回调，翻译成平台无关的 VEvent，再经 [PluginRegistry.dispatch]
 * 分发给所有已启用模块。业务代码永远看不到 `net.fabricmc.*`。
 *
 * 带取消语义的事件（聊天 / 右键）把业务侧的 `canceled` 写回 Fabric 的返回约定。
 */
class FabricEventBridge {

    init {
        // ---- 玩家上下线 ----
        // 用 ServerPlayConnectionEvents 而不是 ServerPlayerEvents.JOIN/LEAVE：
        // 后者在 **1.20.1 的 fabric-api 里不存在**（1.21.x 才加），而前者两版都有。
        // 所以这一份桥代码可以原样移植到 1.20.1 的 Fabric 分支，不必按版本分叉。
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            PluginRegistry.dispatch { it.onPlayerJoin(VPlayerJoin(handler.player)) }
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            PluginRegistry.dispatch { it.onPlayerQuit(VPlayerQuit(handler.player)) }
        }

        // ---- 玩家死亡 ----
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val p = entity as? ServerPlayer ?: return@register
            PluginRegistry.dispatch { it.onPlayerDeath(VPlayerDeath(p, source)) }
        }

        // ---- 聊天 ----
        // Fabric 1.21+ 是**签名聊天**，不允许随意改写正文。
        // 好在现有实现（`ChatFormatFeature`）本来就是「取消原版广播 + 自己广播整条」，
        // 并不真的改 `VChat.message` —— 所以只需要用「是否放行」这一个开关，
        // 天然绕开签名限制，不需要 `ServerMessageDecoratorEvent`。
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register { message, sender, _ ->
            val ve = VChat(sender, message.signedContent(), Component.empty())
            PluginRegistry.dispatch { it.onChat(ve) }
            !ve.canceled      // 返回 false = 取消原版广播（我们的实现自己播）
        }

        // ---- 右键物品（钟表菜单）----
        // ⚠️ UseItemCallback 是**双端**事件：客户端也会触发。但那边 `player` 是
        //    LocalPlayer，`as? ServerPlayer` 直接得到 null → 走 pass，天然安全；
        //    而且本套件 environment = "server"，客户端 mixin 根本不加载。
        // ⚠️ 它返回 `InteractionResultHolder`（不是布尔），取消用 fail、放行用 pass。
        //    ⚠️ 这个类在官方映射里叫 `InteractionResultHolder` —— Yarn 映射里叫
        //    `TypedActionResult`，网上 Fabric 教程多用后者，别被带偏。
        UseItemCallback.EVENT.register { player, _, hand ->
            val p = player as? ServerPlayer
                ?: return@register InteractionResultHolder.pass(ItemStack.EMPTY)
            val stack = p.getItemInHand(hand)
            val ve = VRightClickItem(p, stack, hand)
            PluginRegistry.dispatch { it.onRightClickItem(ve) }
            if (ve.canceled) InteractionResultHolder.fail(stack)
            else InteractionResultHolder.pass(stack)
        }
    }
}
