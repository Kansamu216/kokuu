package org.voidkit.core.data

/** 某个 IP 的累计记录。 */
class IpRecord(
    var ip: String = "",
    var firstSeen: Long = 0L,
    var lastSeen: Long = 0L,
    var count: Int = 0,
    /** 该 IP 用过的游戏名（用于识别小号）。 */
    var names: MutableList<String> = mutableListOf()
) {
    fun touch(name: String?, now: Long) {
        lastSeen = now
        count++
        if (!name.isNullOrBlank() && !names.contains(name)) {
            names.add(name)
        }
    }

    fun normalize() {
        if (names == null) names = mutableListOf()
    }
}
