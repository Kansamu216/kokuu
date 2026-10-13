package org.voidkit.perm

import org.voidkit.core.VoidkitCore
import org.voidkit.core.data.DataStore
import org.voidkit.core.util.ConfigFiles
import java.util.UUID

/** `permissions/groups.json` 的顶层结构。包一层是为了有地方放注释，也省掉 Gson 泛型 Type 的麻烦。 */
class PermissionGroupFile {
    /** key = 组名。 */
    var groups: MutableMap<String, PermissionGroupData> = linkedMapOf()

    fun normalize() {
        if (groups == null) groups = linkedMapOf()
    }
}

/** `permissions/users.json` 的顶层结构。 */
class PermissionUserFile {
    /** key = 玩家 UUID 字符串。 */
    var users: MutableMap<String, UserPermissionData> = linkedMapOf()

    fun normalize() {
        if (users == null) users = linkedMapOf()
    }
}

/**
 * 权限数据的读写入口。三份文件都在 `voidkit/perm/` 下。
 *
 * 两处刻意的设计：
 *  - **懒迁移**：旧版把玩家所属组存在玩家档案（`players/<uuid>.json`）里，
 *    这里不遍历所有档案，而是**读到某个玩家时才把他的旧数据搬过来** —— 服务器有几千个
 *    历史玩家时，遍历一次的代价没必要付。
 *  - **写盘按需**：权限检查会被高频调用（每次 `/home`、每条聊天），
 *    所以查询路径**绝不创建也不落盘**；只有真正修改时才置脏标记，由周期任务统一写。
 */
object PermissionStore {

    const val GROUPS_FILE = "perm/groups.json"
    const val USERS_FILE = "perm/users.json"
    const val SETTINGS_FILE = "perm/settings.json"

    private lateinit var groupFile: PermissionGroupFile
    private lateinit var userFile: PermissionUserFile

    @Volatile
    private var usersDirty = false

    lateinit var settings: PermissionSettings
        private set

    val groups: MutableMap<String, PermissionGroupData> get() = groupFile.groups

    val users: MutableMap<String, UserPermissionData> get() = userFile.users

    // ---------- 加载 ----------

    fun load() {
        settings = ConfigFiles.load(
            SETTINGS_FILE,
            PermissionSettings::class.java,
            PermComments.PERM_SETTINGS,
        ) { PermissionSettings() }
        settings.normalize()

        groupFile = ConfigFiles.load(
            GROUPS_FILE,
            PermissionGroupFile::class.java,
            PermComments.PERM_GROUPS,
        ) { PermissionGroupFile().apply { groups = builtinGroups() } }
        groupFile.normalize()
        groupFile.groups.forEach { (name, group) ->
            group.normalize()
            if (group.name.isBlank()) group.name = name
        }
        ensureBuiltins()

        userFile = ConfigFiles.load(
            USERS_FILE,
            PermissionUserFile::class.java,
            PermComments.PERM_USERS,
        ) { PermissionUserFile() }
        userFile.normalize()
        userFile.users.values.forEach { it.normalize() }
        // 上次异常退出可能留下过期的临时权限
        val now = System.currentTimeMillis()
        userFile.users.values.forEach { it.pruneExpired(now) }

        saveAll()
        VoidkitCore.LOG.info(
            "权限数据就绪：{} 个组 / {} 个玩家覆盖 / OP 绕过={}",
            groups.size,
            users.size,
            settings.opBypass,
        )
    }

    private fun builtinGroups(): MutableMap<String, PermissionGroupData> = linkedMapOf(
        "default" to PermissionGroupData(
            name = "default",
            prefix = "&7",
            weight = 0,
            permissions = mutableListOf(
                "voidkit.command.spawn",
                "voidkit.command.home",
                "voidkit.command.tpa",
                "voidkit.command.back",
            ),
        ),
        "vip" to PermissionGroupData(
            name = "vip",
            prefix = "&6[VIP]&r",
            weight = 10,
            permissions = mutableListOf("voidkit.homes.limit.10"),
            parents = mutableListOf("default"),
        ),
        "admin" to PermissionGroupData(
            name = "admin",
            prefix = "&c[管理]&r",
            weight = 100,
            permissions = mutableListOf("*"),
        ),
    )

    /** 保证 default / admin 这两个“系统级”组一定存在（配置被写坏时也能起来）。 */
    private fun ensureBuiltins() {
        if (!groups.containsKey(settings.defaultGroup)) {
            groups[settings.defaultGroup] = PermissionGroupData(name = settings.defaultGroup, prefix = "&7")
        }
        if (!groups.containsKey("default")) {
            groups["default"] = PermissionGroupData(name = "default", prefix = "&7")
        }
        if (!groups.containsKey("admin")) {
            groups["admin"] = PermissionGroupData(
                name = "admin",
                prefix = "&c[管理]&r",
                weight = 100,
                permissions = mutableListOf("*"),
            )
        }
    }

    // ---------- 查询 ----------

    /** **只读**查询：没有条目就返回 null，绝不创建、绝不落盘。权限检查走这条。 */
    fun find(uuid: UUID): UserPermissionData? = users[uuid.toString()]

    /** 取（必要时创建）玩家权限数据。会把旧档案里的组/节点懒迁移过来。 */
    fun user(uuid: UUID, name: String? = null): UserPermissionData {
        val key = uuid.toString()
        users[key]?.let { existing ->
            if (!name.isNullOrBlank() && existing.name != name) existing.name = name
            return existing
        }
        val created = UserPermissionData().apply {
            this.name = name ?: ""
            migrateFromProfile(uuid, this)
            normalize()
        }
        users[key] = created
        markUsersDirty()
        return created
    }

    /** 旧版把 group 与 permissions 存在玩家档案里，读到就搬过来。 */
    private fun migrateFromProfile(uuid: UUID, target: UserPermissionData) {
        val profile = DataStore.get()?.profile(uuid, null) ?: return
        val group = profile.group
        if (!group.isNullOrBlank() && group != settings.defaultGroup) target.group = group
        profile.permissions?.takeIf { it.isNotEmpty() }?.let { target.permissions = it.toMutableList() }
    }

    fun group(name: String?): PermissionGroupData? {
        if (name.isNullOrBlank()) return null
        return groups[name]
    }

    /** 解析玩家的组继承链：该组 + 其所有祖先。用于收集节点、判断前缀。 */
    fun groupChain(name: String?): List<PermissionGroupData> {
        if (name.isNullOrBlank()) return emptyList()
        val visited = LinkedHashSet<String>()
        val result = ArrayList<PermissionGroupData>()
        fun walk(current: String?) {
            if (current.isNullOrBlank() || !visited.add(current)) return
            val group = groups[current] ?: return
            group.normalize()
            result += group
            group.parents.forEach { walk(it) }
        }
        walk(name)
        return result
    }

    // ---------- 修改 ----------

    fun markUsersDirty() {
        usersDirty = true
    }

    /** 有改动才落盘。由周期任务调用。 */
    fun flushIfDirty() {
        if (!usersDirty) return
        usersDirty = false
        saveUsers()
    }

    fun saveGroups() = ConfigFiles.save(GROUPS_FILE, groupFile, PermComments.PERM_GROUPS)

    fun saveUsers() = ConfigFiles.save(USERS_FILE, userFile, PermComments.PERM_USERS)

    fun saveSettings() = ConfigFiles.save(SETTINGS_FILE, settings, PermComments.PERM_SETTINGS)

    fun saveAll() {
        saveGroups()
        saveUsers()
        saveSettings()
    }
}
