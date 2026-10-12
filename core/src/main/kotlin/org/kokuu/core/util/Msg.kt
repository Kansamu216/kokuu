package org.kokuu.core.util

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer

/** 统一的消息样式，避免各处硬编码颜色。 */
object Msg {

    private val PREFIX: MutableComponent = Component.literal("[")
        .withStyle(ChatFormatting.DARK_AQUA)
        .append(Component.literal("KokuuCore").withStyle(ChatFormatting.AQUA))
        .append(Component.literal("] ").withStyle(ChatFormatting.DARK_AQUA))

    fun prefix(): MutableComponent = PREFIX.copy()

    fun colored(text: String, color: ChatFormatting): MutableComponent =
        Component.literal(text).withStyle(color)

    fun prefixed(text: String, color: ChatFormatting): MutableComponent =
        prefix().append(colored(text, color))

    // ---------- 玩家 ----------

    fun ok(player: ServerPlayer, text: String) =
        player.sendSystemMessage(prefixed(text, ChatFormatting.GREEN))

    fun err(player: ServerPlayer, text: String) =
        player.sendSystemMessage(prefixed(text, ChatFormatting.RED))

    fun info(player: ServerPlayer, text: String) =
        player.sendSystemMessage(prefixed(text, ChatFormatting.GRAY))

    fun raw(player: ServerPlayer, component: Component) =
        player.sendSystemMessage(component)

    /** 支持 `&` 颜色的消息（用于配置文本）。 */
    fun text(player: ServerPlayer, rawText: String) =
        player.sendSystemMessage(prefix().append(Text.legacy(rawText)))

    // ---------- 命令源 ----------

    fun ok(source: CommandSourceStack, text: String) =
        source.sendSuccess({ prefixed(text, ChatFormatting.GREEN) }, false)

    fun err(source: CommandSourceStack, text: String) =
        source.sendSuccess({ prefixed(text, ChatFormatting.RED) }, false)

    fun info(source: CommandSourceStack, text: String) =
        source.sendSuccess({ prefixed(text, ChatFormatting.GRAY) }, false)

    /** 支持 `&` 颜色的消息（用于配置文本）。 */
    fun text(source: CommandSourceStack, rawText: String) =
        source.sendSuccess({ prefix().append(Text.legacy(rawText)) }, false)

    // ---------- 格式 ----------

    /** 把秒数格式化成"3天4小时"这种中文可读形式。 */
    fun duration(seconds: Long): String {
        if (seconds < 60) return "${seconds}秒"
        val minutes = seconds / 60
        if (minutes < 60) return "${minutes}分${seconds % 60}秒"
        val hours = minutes / 60
        if (hours < 24) return "${hours}小时${minutes % 60}分"
        return "${hours / 24}天${hours % 24}小时"
    }
}
