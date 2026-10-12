package org.voidkit.core.data

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 单个玩家的全部持久化数据，一个玩家一个文件：`voidkit/players/<uuid>.json`。
 *
 * 集合字段刻意用并发容器：主线程（命令）与 HTTP API 线程（面板查询）会并发读写
 * 同一个 profile —— 普通集合在「一边遍历一边改」时抛 ConcurrentModificationException
 * （表现为面板查玩家详情时玩家恰好 /sethome，API 直接 500）。
 * Gson 对这两种容器都有无参构造器，序列化行为不变。
 */
class PlayerProfile {

    var uuid: String = ""
    var lastName: String = ""

    /** 曾用名列表（改名追踪）。 */
    var knownNames: MutableList<String> = CopyOnWriteArrayList()

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
    var ips: MutableList<IpRecord> = CopyOnWriteArrayList()

    /** 家。key 为名称，默认 "home"。 */
    var homes: MutableMap<String, StoredLocation> = ConcurrentHashMap()

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
        if (knownNames == null) knownNames = CopyOnWriteArrayList()
        if (ips == null) ips = CopyOnWriteArrayList()
        if (homes == null) homes = ConcurrentHashMap()
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
