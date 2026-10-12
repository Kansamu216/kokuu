package org.voidkit.core.feature

import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.JoinLog

/**
 * 玩家追踪：上下线、IP、累计时长、改名历史。
 *
 * 这是管理平台的数据地基，其它模块通过 `CoreServices.profile(...)` 复用它。
 */
class PlayerTrackingFeature : Feature() {

    override val id = "player-tracking"
    override val displayName = "玩家追踪"
    override val description = "记录玩家上下线、IP、累计在线时长与改名历史"

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val store = DataStore.get() ?: return
        val now = System.currentTimeMillis()
        val name = player.gameProfile.name
        val ip = player.ipAddress

        val profile = store.profile(player.uuid, name)
        profile.online = true
        profile.sessionStart = now
        profile.joinCount++
        profile.lastJoin = now
        profile.lastSeen = now
        if (profile.firstJoin == 0L) profile.firstJoin = now
        if (!ip.isNullOrBlank()) {
            profile.lastIp = ip
            if (VoidkitCore.config.recordIps) profile.recordIp(ip, name, now)
        }
        store.joinLog.append(JoinLog.Entry(now, profile.uuid, name, ip ?: "", "join"))
        store.markDirty()
    }

    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val store = DataStore.get() ?: return
        val now = System.currentTimeMillis()

        val profile = store.profile(player.uuid, player.gameProfile.name)
        profile.online = false
        profile.lastQuit = now
        profile.lastSeen = now
        settleSession(profile, now)
        store.joinLog.append(JoinLog.Entry(now, profile.uuid, profile.lastName, profile.lastIp, "quit"))
        store.markDirty()
        store.save(profile)
    }

    override fun onServerStop(server: net.minecraft.server.MinecraftServer) {
        val store = DataStore.get() ?: return
        val now = System.currentTimeMillis()
        store.online().forEach { profile ->
            settleSession(profile, now)
            profile.online = false
        }
    }

    override fun onSecond(server: net.minecraft.server.MinecraftServer) {
        val store = DataStore.get() ?: return
        val now = System.currentTimeMillis()
        store.online().forEach { it.lastSeen = now }
    }

    private fun settleSession(profile: org.voidkit.core.data.PlayerProfile, now: Long) {
        if (profile.sessionStart > 0L) {
            profile.playtimeSeconds += maxOf(0L, (now - profile.sessionStart) / 1000L)
            profile.sessionStart = 0L
        }
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/tracking/summary") { _ ->
            val store = DataStore.get()
            ApiJson.obj(
                "ok", true,
                "online", store?.onlineCount() ?: 0,
                "tracked", store?.all()?.size ?: 0,
                "at", System.currentTimeMillis()
            )
        }
    }
}
