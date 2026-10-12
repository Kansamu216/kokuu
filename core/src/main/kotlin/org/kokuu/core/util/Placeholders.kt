package org.kokuu.core.util

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.kokuu.core.data.DataStore
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** 少数需要服务端内部数据的变量实现（其余见 [org.kokuu.core.placeholder.PlaceholderRegistry]）。 */
object Placeholders {

    /** 平均 TPS：用总 tick 数 / 运行秒数，比反射读内部 tickTimes 稳妥。 */
    fun tps(server: MinecraftServer): String {
        // 运行时长取自 ServerLifecycle，而不是 API —— 关掉远程管理模块时 TPS 也要能正常显示
        val uptimeSeconds = org.kokuu.core.event.ServerLifecycle.uptimeSeconds().toDouble()
        if (uptimeSeconds < 2.0) return "20.0"
        val tps = min(20.0, server.tickCount / uptimeSeconds)
        return String.format(Locale.ROOT, "%.1f", tps)
    }

    fun playtime(player: ServerPlayer): String {
        val store = DataStore.get() ?: return "-"
        val profile = store.profile(player.uuid, null)
        var seconds = profile.playtimeSeconds
        if (profile.sessionStart > 0L) {
            seconds += max(0L, (System.currentTimeMillis() - profile.sessionStart) / 1000L)
        }
        return Msg.duration(seconds)
    }
}
