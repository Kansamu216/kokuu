package org.voidkit.core.util

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

/**
 * 消息播报通道。
 *
 * 之前清扫直接把东西塞进 `broadcastSystemMessage(comp, false)`，结果服主反馈
 * 「只在后台看到、聊天框里没有」—— 通道不可选、失败还被静默吞掉。
 * 这里把「发到哪」做成显式配置，并把每个玩家的发送包在容错里、失败记日志。
 *
 * 用逐玩家 [net.minecraft.server.level.ServerPlayer.sendSystemMessage] 而不是
 * 整包广播：这是最基础、最不会被别的 mod 拦下的路径，而且能顺手跳过某些玩家
 * （将来做「不看清扫提示」的开关时不用改结构）。
 */
object Broadcast {

    /** 播报通道。配置里写字符串，见 [parse]。 */
    enum class Channel {
        /** 只发聊天框。 */
        CHAT,

        /** 只发动作栏（物品栏上方那行）。 */
        ACTIONBAR,

        /** 两边都发。 */
        BOTH,

        /** 都不发（只写控制台日志）。 */
        OFF,
    }

    /**
     * 解析配置里的通道字符串。
     *
     * 宽容处理常见写法，认不出来就退回 [Channel.CHAT] —— 服主写错拼写时
     * 应该「退回默认行为」，而不是「消息凭空消失」。
     */
    fun parse(raw: String?): Channel = when (raw?.trim()?.lowercase()) {
        "actionbar", "action_bar", "bar", "action" -> Channel.ACTIONBAR
        "both", "all" -> Channel.BOTH
        "off", "none", "disable", "disabled", "silent" -> Channel.OFF
        else -> Channel.CHAT
    }

    /**
     * 播报一条消息。
     *
     * @param chat     聊天框内容
     * @param actionbar 动作栏内容；不传则复用 [chat]
     */
    fun send(
        server: MinecraftServer,
        chat: Component,
        channel: Channel,
        actionbar: Component? = null,
    ) {
        if (channel == Channel.OFF) return
        val bar = actionbar ?: chat
        server.playerList.players.forEach { player ->
            // 单个玩家发失败不该影响其他人
            runCatching {
                when (channel) {
                    Channel.CHAT -> player.sendSystemMessage(chat)
                    Channel.ACTIONBAR -> player.displayClientMessage(bar, true)
                    Channel.BOTH -> {
                        player.sendSystemMessage(chat)
                        player.displayClientMessage(bar, true)
                    }
                    Channel.OFF -> Unit
                }
            }
        }
    }

    /**
     * 给命令源用的版本：命令源可能是控制台（没有玩家），那就只在控制台打印。
     * 这条路径的结果直接告诉调用者「发出去了几个玩家」，方便排错。
     */
    fun sendToPlayers(server: MinecraftServer, chat: Component, channel: Channel): Int {
        if (channel == Channel.OFF) return 0
        var sent = 0
        server.playerList.players.forEach { player ->
            runCatching {
                if (channel == Channel.CHAT || channel == Channel.BOTH) player.sendSystemMessage(chat)
                if (channel == Channel.ACTIONBAR || channel == Channel.BOTH) player.displayClientMessage(chat, true)
                sent++
            }
        }
        return sent
    }
}
