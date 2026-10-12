package org.voidkit.core.teleport

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.StoredLocation

/** 传送与 /back 的统一实现，所有模块都走这里，保证 back 行为一致。 */
object Teleports {

    /**
     * 传送到目标坐标。
     *
     * @param recordBack 是否把“传送前的位置”记为 /back 目标
     */
    fun to(player: ServerPlayer, location: StoredLocation?, recordBack: Boolean = true): Boolean {
        if (location == null) return false
        val level = location.resolve(player.server) ?: return false
        if (recordBack) rememberBack(player)
        player.teleportTo(level, location.x, location.y, location.z, location.yaw, location.pitch)
        player.deltaMovement = Vec3.ZERO
        runCatching { player.connection.resetPosition() }
        return true
    }

    fun rememberBack(player: ServerPlayer) {
        val store = DataStore.get() ?: return
        store.profile(player.uuid, player.gameProfile.name).back = StoredLocation.of(player)
        store.markDirty()
    }

    /** /back：回到上次记录的位置（死亡点或传送前位置），并把当前位置记为新的 back。 */
    fun back(player: ServerPlayer): Boolean {
        val store = DataStore.get() ?: return false
        val profile = store.profile(player.uuid, player.gameProfile.name)
        val target = profile.back ?: return false
        // 先传送，成功后才把当前位置记成新的 back 点 ——
        // 传送失败时不能覆盖原 back 点，否则玩家的回退点凭空丢了。
        if (!to(player, target, recordBack = false)) return false
        profile.back = StoredLocation.of(player)
        store.markDirty()
        return true
    }

    fun hasBack(player: ServerPlayer): Boolean =
        DataStore.get()?.profile(player.uuid, null)?.back != null
}
