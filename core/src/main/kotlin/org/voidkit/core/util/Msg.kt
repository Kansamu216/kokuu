package org.voidkit.core.util

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.api.VoidkitLang

/**
 * 统一的消息样式，避免各处硬编码颜色。
 *
 * 所有文本都会经过 [VoidkitLang.tr] —— 所以**整个项目的消息自动支持多语言**，
 * 调用点一行都不用改。中文环境下 [VoidkitLang.tr] 直接返回原文，没有任何查表开销。
 *
 * 需要翻译的消息用 `%s` 占位（见 [VoidkitLang] 的说明）：
 * ```kotlin
 * Msg.ok(source, VoidkitLang.tr("已向 %s 发送传送请求", targetName))
 * ```
 */
object Msg {

    private val PREFIX: MutableComponent = Component.literal("[")
        .withStyle(ChatFormatting.DARK_AQUA)
        .append(Component.literal("Voidkit").withStyle(ChatFormatting.AQUA))
        .append(Component.literal("] ").withStyle(ChatFormatting.DARK_AQUA))

    fun prefix(): MutableComponent = PREFIX.copy()

    fun colored(text: String, color: ChatFormatting): MutableComponent =
        Component.literal(text).withStyle(color)

    /** 加统一前缀 + 上色。文本在这里过一遍翻译。 */
    fun prefixed(text: String, color: ChatFormatting): MutableComponent =
        prefix().append(colored(VoidkitLang.tr(text), color))

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
        player.sendSystemMessage(prefix().append(Text.legacy(VoidkitLang.tr(rawText))))

    // ---------- 命令源 ----------

    fun ok(source: CommandSourceStack, text: String) =
        source.sendSuccess({ prefixed(text, ChatFormatting.GREEN) }, false)

    fun err(source: CommandSourceStack, text: String) =
        source.sendSuccess({ prefixed(text, ChatFormatting.RED) }, false)

    fun info(source: CommandSourceStack, text: String) =
        source.sendSuccess({ prefixed(text, ChatFormatting.GRAY) }, false)

    /** 支持 `&` 颜色的消息（用于配置文本）。 */
    fun text(source: CommandSourceStack, rawText: String) =
        source.sendSuccess({ prefix().append(Text.legacy(VoidkitLang.tr(rawText))) }, false)

    // ---------- 格式 ----------

    /**
     * 把秒数格式化成可读形式。
     *
     * 中文 `3天4小时` / 英文 `3d 4h` —— 单位也要跟着语言走，所以整条拼装
     * 都交给语言表，不在代码里硬编码单位。
     */
    fun duration(seconds: Long): String {
        // 拆开写而不是 listOf 解构 —— 这个方法会被记分牌每秒调用，不必要地建对象
        val s = seconds % 60
        val m = (seconds % 3600) / 60
        val h = (seconds % 86400) / 3600
        val d = seconds / 86400
        return when {
            seconds < 60 -> VoidkitLang.tr("%s秒", s)
            seconds < 3600 -> VoidkitLang.tr("%s分%s秒", m, s)
            seconds < 86400 -> VoidkitLang.tr("%s小时%s分", h, m)
            else -> VoidkitLang.tr("%s天%s小时", d, h)
        }
    }
}
