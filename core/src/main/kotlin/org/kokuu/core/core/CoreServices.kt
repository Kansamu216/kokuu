package org.kokuu.core.core

import net.minecraft.server.level.ServerPlayer
import org.kokuu.core.data.DataStore
import org.kokuu.core.data.PlayerProfile
import org.kokuu.core.data.StoredLocation
import org.kokuu.core.placeholder.PlaceholderRegistry
import org.kokuu.core.teleport.Teleports
import java.util.UUID

/**
 * 模块之间共享的服务入口。
 *
 * 让模块不必互相依赖 —— 记分牌要显示玩家的家数量，调 [profile] 即可，
 * 不需要 import HomeFeature。
 */
object CoreServices {

    fun store(): DataStore? = DataStore.get()

    fun profile(player: ServerPlayer): PlayerProfile? =
        DataStore.get()?.profile(player.uuid, player.gameProfile.name)

    fun profile(uuid: UUID): PlayerProfile? = DataStore.get()?.profile(uuid, null)

    /** 传送到已保存坐标，默认记 /back。 */
    fun teleport(player: ServerPlayer, location: StoredLocation, recordBack: Boolean = true): Boolean =
        Teleports.to(player, location, recordBack)

    /** 构造一个变量解析上下文（TAB / 记分牌 / 聊天共用）。 */
    fun placeholderContext(player: ServerPlayer?): PlaceholderRegistry.Context? {
        val server = player?.server ?: return null
        return PlaceholderRegistry.Context(server, player)
    }
}
