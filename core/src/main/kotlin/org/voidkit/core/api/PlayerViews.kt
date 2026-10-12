package org.voidkit.core.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.IpRecord
import org.voidkit.core.data.JoinLog
import org.voidkit.core.data.PlayerProfile
import java.util.UUID

/**
 * 玩家档案的对外 JSON 视图。
 *
 * 原先这些方法长在 web 插件的 `ApiDefaults` 上，于是每个要开管理端点的插件
 * 都得依赖 web 插件才能把数据转成 JSON。它们其实只用到内核自己的
 * [DataStore] / [PlayerProfile]，所以上移到内核。
 *
 * 这样即使不装 web 插件，插件也能用同一套 JSON 结构 —— 将来换个管理端
 * （比如换成 gRPC、换成邮件日报）不用动业务代码。
 */
object PlayerViews {

    /** 按 UUID 或玩家名查档案。找不到返回 null。 */
    fun resolve(key: String?): PlayerProfile? {
        val store = DataStore.get() ?: return null
        if (key.isNullOrBlank()) return null
        val trimmed = key.trim()

        val uuid = runCatching { UUID.fromString(trimmed) }.getOrNull()
        if (uuid != null) {
            return store.all().firstOrNull { it.uuid.equals(uuid.toString(), ignoreCase = true) }
        }
        return store.byName(trimmed)
    }

    /** 按 UUID 或玩家名找在线玩家。 */
    fun findOnline(key: String?): ServerPlayer? {
        if (key.isNullOrBlank()) return null
        val server = VoidkitServer.get() ?: return null
        return server.playerList.players.firstOrNull {
            it.uuid.toString().equals(key, ignoreCase = true) ||
                it.gameProfile.name.equals(key, ignoreCase = true)
        }
    }

    /** 列表用的精简视图。 */
    fun summary(profile: PlayerProfile): JsonObject = ApiJson.obj(
        "uuid", profile.uuid,
        "name", profile.lastName,
        "online", profile.online,
        "lastIp", profile.lastIp,
        "firstJoin", profile.firstJoin,
        "lastJoin", profile.lastJoin,
        "lastQuit", profile.lastQuit,
        "lastSeen", profile.lastSeen,
        "playtimeSeconds", profile.playtimeSeconds,
        "joinCount", profile.joinCount,
        "homeCount", profile.homes?.size ?: 0,
        "group", profile.group,
        "muted", profile.muted,
        "knownNames", ApiJson.arr(profile.knownNames),
    )

    /** 详情视图：在 [summary] 之上补 IP 历史、家、昵称等。 */
    fun detail(profile: PlayerProfile): JsonObject {
        val json = summary(profile)

        ApiJson.put(json, "ips", profile.ips.sortedByDescending { it.lastSeen }.map { ipJson(it) })

        val homes = JsonArray()
        profile.homes?.forEach { (name, location) ->
            homes.add(
                ApiJson.obj(
                    "name", name,
                    "dim", location.dim,
                    "x", location.x, "y", location.y, "z", location.z,
                    "display", location.display(),
                )
            )
        }
        ApiJson.put(json, "homes", homes)

        ApiJson.put(json, "nickname", profile.nickname)
        ApiJson.put(json, "vanished", profile.vanished)
        ApiJson.put(json, "godMode", profile.godMode)
        ApiJson.put(json, "flying", profile.flying)
        ApiJson.put(json, "muteReason", profile.muteReason)
        return json
    }

    fun ipJson(record: IpRecord): JsonObject = ApiJson.obj(
        "ip", record.ip,
        "firstSeen", record.firstSeen,
        "lastSeen", record.lastSeen,
        "count", record.count,
        "names", ApiJson.arr(record.names),
    )

    fun entryJson(entry: JoinLog.Entry): JsonObject = ApiJson.obj(
        "t", entry.t,
        "uuid", entry.uuid,
        "name", entry.name,
        "ip", entry.ip,
        "action", entry.action,
    )
}
