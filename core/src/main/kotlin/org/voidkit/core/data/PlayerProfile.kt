package org.voidkit.core.data

/**
 * 单个玩家的全部持久化数据，一个玩家一个文件：`voidkit/players/<uuid>.json`。
 */
class PlayerProfile {

    var uuid: String = ""
    var lastName: String = ""

    /** 曾用名列表（改名追踪）。 */
    var knownNames: MutableList<String> = mutableListOf()

    var firstJoin: Long = 0L
    var lastJoin: Long = 0L
    var lastQuit: Long = 0L
    var lastSeen: Long = 0L

    /** 累计在线时长（秒）。 */
    var playtimeSeconds: Long = 0L
    var joinCount: Int = 0

    /** 是否在线，仅内存有效。 */
    @Transient
    var online: Boolean = false

    /** 本次上线时间戳，仅内存。 */
    @Transient
    var sessionStart: Long = 0L

    var lastIp: String = ""

    /** 该玩家用过的所有 IP。 */
    var ips: MutableList<IpRecord> = mutableListOf()

    /** 家。key 为名称，默认 "home"。 */
    var homes: MutableMap<String, StoredLocation> = linkedMapOf()

    /** /back 目标。 */
    var back: StoredLocation? = null

    // ---------- 权限 / 状态 ----------
    var group: String = "default"

    /** 附加权限节点，独立于分组。 */
    var permissions: MutableList<String> = mutableListOf()

    var nickname: String = ""

    /** 当前佩戴的称号 id（对应 `titles.json` 里的 key）；空 = 不显示称号。 */
    var activeTitle: String = ""

    /** 玩家自定义称号文本（/title custom 设置）；非空时优先于 [activeTitle] 显示。 */
    var customTitle: String = ""

    var vanished: Boolean = false
    var godMode: Boolean = false
    var flying: Boolean = false

    var muted: Boolean = false
    var mutedUntil: Long = 0L
    var muteReason: String = ""

    // ---------- 辅助 ----------

    fun normalize() {
        if (knownNames == null) knownNames = mutableListOf()
        if (ips == null) ips = mutableListOf()
        if (homes == null) homes = linkedMapOf()
        if (permissions == null) permissions = mutableListOf()
        if (group.isBlank()) group = "default"
        if (activeTitle == null) activeTitle = ""
        if (customTitle == null) customTitle = ""
        ips.forEach { it.normalize() }
    }

    /** 记录一次 IP 访问，返回对应记录。 */
    fun recordIp(ip: String?, name: String?, now: Long): IpRecord? {
        if (ip.isNullOrBlank()) return null
        val existing = ips.firstOrNull { it.ip == ip }
        if (existing != null) {
            existing.touch(name, now)
            return existing
        }
        val record = IpRecord(ip, now, now, 1)
        if (!name.isNullOrBlank()) record.names.add(name)
        ips.add(record)
        return record
    }

    fun isMuted(now: Long): Boolean = muted && (mutedUntil <= 0L || mutedUntil > now)

    fun displayName(): String = nickname.ifBlank { lastName }

    fun addKnownName(name: String?) {
        if (name.isNullOrBlank() || name == lastName) return
        if (!knownNames.contains(name)) knownNames.add(name)
    }
}
