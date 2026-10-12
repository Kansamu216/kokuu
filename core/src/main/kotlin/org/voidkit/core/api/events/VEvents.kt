package org.voidkit.core.api.events

import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.item.ItemStack

/**
 * 平台无关的事件（VEvent）。
 *
 * 这是「事件抽象」的核心：Feature 不再直接监听 `net.neoforged` 的事件类，
 * 而是覆写 [org.voidkit.core.api.Feature] 上的 `onXxx(e: VXxx)` 回调。
 * platform 层（`NeoForgeEventBridge`）负责把加载器原生事件翻译成这里的对象。
 *
 * 字段只用 `net.minecraft.*`（三平台同名）和基本类型 —— 移植 Forge/Fabric 时
 * 只需重写 EventBridge，这些定义与全部业务代码零改动。
 *
 * ⚠️ 带 `isCanceled` 语义的事件（聊天、右键）用 `var canceled` 字段桥接：
 * 业务代码设 `canceled = true` 后，EventBridge 在事件结束时把它写回原生事件的
 * `isCanceled`。
 */

/** 玩家登录。 */
data class VPlayerJoin(val player: ServerPlayer)

/** 玩家登出。 */
data class VPlayerQuit(val player: ServerPlayer)

/** 玩家死亡（source 为伤害来源）。 */
data class VPlayerDeath(val player: ServerPlayer, val source: DamageSource)

/** 玩家聊天。canceled=true 表示取消广播；message 可被改写（聊天格式化用）。 */
class VChat(val player: ServerPlayer, val rawText: String, var message: MutableComponent) {
    var canceled: Boolean = false
}

/** 玩家右键物品（手持菜单物品等）。canceled=true 阻止物品被使用。 */
class VRightClickItem(val player: ServerPlayer, val stack: ItemStack, val hand: InteractionHand) {
    var canceled: Boolean = false
}
