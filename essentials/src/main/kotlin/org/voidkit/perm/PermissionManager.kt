package org.voidkit.perm

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.api.PermissionProvider
import org.voidkit.core.api.PublicNodes
import java.util.UUID

/**
 * 权限判断核心（LuckPerms 风格）。
 *
 * ## 判断顺序
 *
 * 1. 原版 OP 等级 ≥ [ADMIN_LEVEL]，且 `settings.opBypass` 为 true → 直接放行
 * 2. **玩家自己的节点**（常驻 + 未过期的临时）—— 有命中就用它，`-` 前缀表示拒绝
 * 3. 玩家所属各组（主组 + 附加组 + 全部祖先）**按权重从高到低**逐个找
 * 4. 都没命中 → **公开节点？放行**（`/warp` `/spawn` 这类玩家命令，见 [PublicNodes]）
 * 5. 还不命中 → `settings.defaultValue`（默认 false）
 *
 * 关键点：**先命中的说了算**，且玩家级节点永远优先于组 —— 这样才能用
 * `-voidkit.command.fly` 单独禁掉某个玩家的某个权限，而不用动整个组。
 *
 * 第 4 步为何放在第 5 步之前、第 2/3 步之后：公开节点只是「没配置时的默认值」，
 * 不是「绕过权限」。所以显式配置（含否定）永远压得过它 —— 想把某组的 `/warp`
 * 关掉，给那个组加一条 `-voidkit.command.warp` 就行。
 *
 * ## 节点写法
 *
 * | 写法 | 含义 |
 * |---|---|
 * | `voidkit.command.fly` | 精确 |
 * | `voidkit.home.*` | 前缀通配 |
 * | `*` | 一切 |
 * | `-voidkit.command.fly` | 否定 |
 */
object PermissionManager : PermissionProvider {

    /** 原版 OP 等级：2 = 管理员。 */
    const val ADMIN_LEVEL = 2

    // ---------- 判断 ----------

    override fun has(player: ServerPlayer, node: String): Boolean {
        val settings = PermissionStore.settings
        if (settings.opBypass && player.hasPermissions(ADMIN_LEVEL)) return true
        if (node.isBlank()) return settings.defaultValue

        val now = System.currentTimeMillis()
        val data = PermissionStore.find(player.uuid)

        // ---- 1. 玩家自己的节点（优先级最高）----
        if (data != null) {
            data.normalize()
            val own = data.ownNodes(now)
            if (own.isNotEmpty()) {
                // 后写的覆盖先写的：同一个人写 [a, -a] 时以 "-a" 为准
                val hit = own.lastOrNull { matches(it, node) }
                if (hit != null) return !hit.startsWith("-")
            }
        }

        // ---- 2. 各组按权重从高到低 ----
        for (group in groupsByWeightDesc(player.uuid)) {
            val hit = group.permissions.lastOrNull { matches(it, node) }
            if (hit != null) return !hit.startsWith("-")
        }

        // ---- 3. 公开节点：玩家命令默认开放（显式否定已在上面提前返回）----
        if (PublicNodes.isPublic(node)) return true

        // ---- 4. 兜底 ----
        return settings.defaultValue
    }

    /** 该玩家参与的所有组（含全部祖先），按权重从高到低排列。 */
    private fun groupsByWeightDesc(uuid: UUID): List<PermissionGroupData> {
        val names = PermissionStore.find(uuid)?.allGroups()
            ?: listOf(PermissionStore.settings.defaultGroup)
        val seen = LinkedHashSet<String>()
        val result = ArrayList<PermissionGroupData>()
        names.forEach { name ->
            PermissionStore.groupChain(name).forEach { group ->
                if (seen.add(group.name)) result += group
            }
        }
        return result.sortedByDescending { it.weight }
    }

    private fun matches(pattern: String, node: String): Boolean =
        matchesPermissionNode(pattern, node, PermissionStore.settings.wildcardEnabled)

    override fun has(source: CommandSourceStack, node: String): Boolean = has(source, node, ADMIN_LEVEL)

    /** 带自定义 OP 兜底等级的内部版本（有些命令只要求等级 1）。 */
    fun has(source: CommandSourceStack, node: String, fallbackLevel: Int): Boolean {
        // 🚨 控制台 / 命令方块没有玩家实体，**一律放行** —— 这是内核接口的约定
        //    （见 PermissionProvider.has(source, node) 的默认实现）。
        //    以前这里写成 `?: return false`，导致装了权限插件后控制台反而跑不了
        //    任何带节点的命令，与「不装权限插件时控制台能用」正好相反。
        val player = source.player ?: return true
        if (PermissionStore.settings.opBypass && player.hasPermissions(fallbackLevel)) return true
        return has(player, node)
    }

    /** 命令的 requires 判断：原版 OP 或带 `voidkit.admin` 权限。 */
    override fun admin(source: CommandSourceStack): Boolean =
        (PermissionStore.settings.opBypass && source.hasPermission(ADMIN_LEVEL)) || has(source, "voidkit.admin")

    override fun admin(player: ServerPlayer): Boolean =
        (PermissionStore.settings.opBypass && player.hasPermissions(ADMIN_LEVEL)) || has(player, "voidkit.admin")

    // ---------- 分组 ----------

    fun groupName(uuid: UUID): String {
        val explicit = PermissionStore.find(uuid)?.group
        return explicit?.takeIf { it.isNotBlank() } ?: PermissionStore.settings.defaultGroup
    }

    /**
     * 玩家“用于显示”的那个组。
     *
     * 默认取权重最高的那个（附加组可能顶掉主组），[PermissionSettings.prefixFromHighestWeight]
     * 关掉后则始终用主组。
     */
    fun resolvedGroup(player: ServerPlayer): PermissionGroupData {
        val uuid = player.uuid
        val primary = PermissionStore.group(groupName(uuid))
        if (!PermissionStore.settings.prefixFromHighestWeight) {
            return primary ?: PermissionStore.group(PermissionStore.settings.defaultGroup)
            ?: PermissionGroupData()
        }
        val best = groupsByWeightDesc(uuid).firstOrNull()
        return best ?: primary ?: PermissionStore.group(PermissionStore.settings.defaultGroup) ?: PermissionGroupData()
    }

    /**
     * 接口要求只回组名 —— 其它插件不该拿到权限系统的内部数据结构。
     * 要完整组定义（前缀/权重/父组）用 [resolvedGroup]，那是本插件内部才需要的东西。
     */
    override fun groupOf(player: ServerPlayer): String = groupName(player.uuid)

    override fun groupOf(uuid: UUID): String = groupName(uuid)

    override fun groups(): Collection<String> = PermissionStore.groups.keys

    /**
     * 设置玩家主组并标记落盘。组不存在返回 false。
     *
     * 语义与 `/perm user <玩家> group <组>` 一致（都改主组 `UserPermissionData.group`），
     * 且会经 [PermissionStore.user] 顺带触发旧档案的懒迁移。
     */
    override fun setGroup(uuid: UUID, group: String): Boolean {
        if (!PermissionStore.groups.containsKey(group)) return false
        val data = PermissionStore.user(uuid)
        data.group = group
        PermissionStore.markUsersDirty()
        return true
    }

    /**
     * 增删玩家专属节点，并标记落盘。
     *
     * 这是「修改玩家节点」的**唯一实现**：`/perm user <玩家> add|del|remove <节点>`
     * 与内核 `/vk perm add|del` 都走这里，语义完全一致（改 `UserPermissionData.permissions`）。
     * 幂等（添加已存在的节点 / 移除不存在的节点）不视为失败；空节点视为非法返回 false。
     */
    override fun setNode(uuid: UUID, node: String, granted: Boolean): Boolean {
        if (node.isBlank()) return false
        val data = PermissionStore.user(uuid)
        val changed = if (granted) {
            if (data.permissions.contains(node)) false else data.permissions.add(node)
        } else {
            data.permissions.remove(node)
        }
        if (changed) PermissionStore.markUsersDirty()
        return true
    }

    override fun prefixOf(player: ServerPlayer): String = resolvedGroup(player).prefix

    override fun suffixOf(player: ServerPlayer): String = resolvedGroup(player).suffix

    // ---------- 排查与调试 ----------

    /**
     * 解释某个节点为什么是 true/false —— 面板和 `/perm check` 用。
     *
     * 返回逐条命中的来源，格式与 LuckPerms 的 verbose 类似。
     */
    fun explain(player: ServerPlayer, node: String): List<String> {
        val settings = PermissionStore.settings
        val lines = ArrayList<String>()

        if (settings.opBypass && player.hasPermissions(ADMIN_LEVEL)) {
            lines += "✓ 你是原版 OP（等级 ${ADMIN_LEVEL}），opBypass 开着 → 直接放行"
            return lines
        }

        val now = System.currentTimeMillis()
        val data = PermissionStore.find(player.uuid)
        if (data != null) {
            data.normalize()
            val hit = data.ownNodes(now).lastOrNull { matches(it, node) }
            if (hit != null) {
                lines += (if (hit.startsWith("-")) "✗" else "✓") + " 玩家级节点「$hit」命中 → ${if (hit.startsWith("-")) "拒绝" else "允许"}"
                return lines
            }
            lines += "· 玩家级节点没有匹配"
        } else {
            lines += "· 该玩家没有权限覆盖条目"
        }

        var matched = false
        for (group in groupsByWeightDesc(player.uuid)) {
            val hit = group.permissions.lastOrNull { matches(it, node) }
            if (hit != null) {
                lines += (if (hit.startsWith("-")) "✗" else "✓") + " 组「${group.name}」(权重 ${group.weight}) 的「$hit」命中 → ${if (hit.startsWith("-")) "拒绝" else "允许"}"
                matched = true
                break
            }
            lines += "· 组「${group.name}」(权重 ${group.weight}) 没有匹配"
        }

        if (!matched) {
            lines += "· 所有组都没有匹配 → 用默认值 ${settings.defaultValue}"
        }
        return lines
    }

    /** 列出该玩家当前生效的全部节点（去重），供面板展示。 */
    fun effectiveNodes(player: ServerPlayer): List<String> {
        val now = System.currentTimeMillis()
        val result = LinkedHashSet<String>()
        PermissionStore.find(player.uuid)?.let { result += it.ownNodes(now) }
        groupsByWeightDesc(player.uuid).forEach { result += it.permissions }
        return result.toList()
    }

    // ---------- 功能配额 ----------

    /** 家里的数量上限：默认配额 + 分组权限覆盖（`voidkit.homes.limit.<n>`）。 */
    override fun homeLimit(player: ServerPlayer): Int {
        val max = org.voidkit.core.api.VoidkitServices.homes().maxPerPlayer().coerceAtLeast(1)
        if (admin(player)) return max
        for (n in max downTo 1) {
            if (has(player, "voidkit.homes.limit.$n")) return n
        }
        return org.voidkit.core.api.VoidkitServices.homes().defaultLimit().coerceIn(1, max)
    }
}

/**
 * 权限节点的纯字符串匹配（从 [PermissionManager.matches] 抽出，便于单测，不依赖
 * [PermissionStore] 与任何 Minecraft 类）。
 *
 * 规则：
 *  - 节点可带 `-` 否定前缀，匹配前先剥掉（否定语义由调用方处理）；
 *  - `*` 匹配一切；`a.b.*` 匹配 `a.b.c` 但不匹配 `a.bc` 或 `a.b`；
 *  - 精确相等直接命中；裸前缀（`a.b`）命中 `a.b.c` 这类后代节点。
 */
internal fun matchesPermissionNode(pattern: String, node: String, wildcardEnabled: Boolean): Boolean {
    val clean = pattern.removePrefix("-")
    if (clean.isBlank()) return false
    if (!wildcardEnabled) return clean == node
    return when {
        clean == "*" -> true
        clean == node -> true
        clean.endsWith(".*") -> node.startsWith(clean.dropLast(1))
        // 也支持不带 .* 的裸前缀写法：voidkit.home 命中 voidkit.home.set
        node.startsWith("$clean.") -> true
        else -> false
    }
}
