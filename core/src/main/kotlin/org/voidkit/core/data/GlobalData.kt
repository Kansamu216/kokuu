package org.voidkit.core.data

/** 全服共享数据，落盘到 `voidkit/global.json`。 */
class GlobalData {

    var warps: MutableMap<String, WarpPoint> = linkedMapOf()

    /** 全服出生点（/spawn 目标，/setspawn 设置）。 */
    var spawn: StoredLocation? = null

    var whitelistEnabled: Boolean = false
    var whitelist: MutableList<String> = mutableListOf()

    var bans: MutableList<BanEntry> = mutableListOf()

    fun normalize() {
        if (warps == null) warps = linkedMapOf()
        if (whitelist == null) whitelist = mutableListOf()
        if (bans == null) bans = mutableListOf()
        warps.values.forEach { warp ->
            if (warp.location == null) warp.location = StoredLocation()
        }
    }

    fun activeBan(uuid: String, now: Long): BanEntry? =
        bans.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) && !it.expired(now) }

    class BanEntry(
        var uuid: String = "",
        var name: String = "",
        var reason: String = "",
        var operator: String = "",
        var createdAt: Long = 0L,
        /** 0 表示永久。 */
        var expiresAt: Long = 0L,
        var ipBan: Boolean = false
    ) {
        fun expired(now: Long): Boolean = expiresAt in 1 until now
    }
}
