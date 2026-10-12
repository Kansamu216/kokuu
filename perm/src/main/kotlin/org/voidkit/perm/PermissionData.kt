package org.voidkit.perm

/**
 * 权限系统的数据模型，落在 `voidkit/perm/` 下：
 *
 * ```
 * voidkit/perm/
 * ├── groups.json    权限组（继承树 / 权重 / 前缀后缀 / 节点）
 * ├── users.json     玩家级覆盖（主组 / 附加组 / 节点 / 临时权限）
 * └── settings.json  全局设置
 * ```
 *
 * 刻意跟 [org.voidkit.core.data.PlayerProfile] 分开存：权限是要单独备份、
 * 单独交接、单独审计的东西，混在游戏档案里（家的坐标、IP 历史、游玩时长）不合适。
 */

// ============================================================ 玩家权限

/**
 * 单个玩家的权限数据，存在 `users.json`，key 是玩家的 UUID 字符串。
 *
 * 判断优先级：**玩家自己的节点 > 组的节点**（LuckPerms 同款规则）。
 * 节点前缀 `-` 表示否定，例如 `-voidkit.command.fly` 就是“明确禁止”。
 */
class UserPermissionData {

    var name: String = ""

    /** 主组。留空则用 [PermissionSettings.defaultGroup]。 */
    var group: String = ""

    /** 附加组，可以有多个；权限取并集。主组只用来决定“显示哪个前缀”。 */
    var groups: MutableList<String> = mutableListOf()

    /** 玩家级节点。前缀 `-` 表示否定，优先级高于所有组的肯定节点。 */
    var permissions: MutableList<String> = mutableListOf()

    /** 临时权限，到期自动失效。 */
    var tempPermissions: MutableList<TempPermission> = mutableListOf()

    fun normalize() {
        if (name == null) name = ""
        if (group == null) group = ""
        if (groups == null) groups = mutableListOf()
        if (permissions == null) permissions = mutableListOf()
        if (tempPermissions == null) tempPermissions = mutableListOf()
        tempPermissions.forEach { it.normalize() }
    }

    /** 清掉已过期的临时权限；返回是否真的删掉了东西。 */
    fun pruneExpired(now: Long): Boolean {
        normalize()
        // until == 0 表示永不过期；到期（until <= now）即删，
        // 与 activeTempNodes 的「until > now 才生效」严格互补（含毫秒边界）。
        return tempPermissions.removeIf { it.until > 0L && it.until <= now }
    }

    /** 当前生效的临时节点。 */
    fun activeTempNodes(now: Long): List<String> {
        normalize()
        return tempPermissions
            .filter { it.node.isNotBlank() && (it.until <= 0L || it.until > now) }
            .map { it.node }
    }

    /** 玩家自己的全部节点 = 常驻 + 有效的临时。 */
    fun ownNodes(now: Long): List<String> = permissions + activeTempNodes(now)

    /** 该玩家参与的所有组（主组 + 附加组），保持顺序并去重。 */
    fun allGroups(): List<String> {
        normalize()
        val result = LinkedHashSet<String>()
        if (group.isNotBlank()) result += group
        result += groups.filter { it.isNotBlank() }
        return result.toList()
    }
}

/** 一条临时权限。 */
class TempPermission {
    var node: String = ""

    /** 到期时间戳（毫秒）；0 = 永不过期。 */
    var until: Long = 0L

    fun normalize() {
        if (node == null) node = ""
        if (until < 0L) until = 0L
    }
}

// ============================================================ 全局设置

/** 权限系统的全局设置，存在 `settings.json`。 */
class PermissionSettings {

    /** 没有显式分组的玩家归到这个组。 */
    var defaultGroup: String = "default"

    /** 原版 OP（等级 ≥ 2）是否直接放行所有权限检查。 */
    var opBypass: Boolean = true

    /** 谁都没命中时的默认结果。一般保持 false，否则等于人人都有权限。 */
    var defaultValue: Boolean = false

    /** 是否启用 `*` 与 `xxx.*` 通配（关掉就只能写完整节点）。 */
    var wildcardEnabled: Boolean = true

    /** 前缀取哪个组：true = 权重最高的那个组；false = 主组。 */
    var prefixFromHighestWeight: Boolean = true

    fun normalize() {
        if (defaultGroup == null || defaultGroup.isBlank()) defaultGroup = "default"
    }
}
