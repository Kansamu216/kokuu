package org.voidkit.perm

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.data.DataStore
import org.voidkit.perm.PermissionGroupData
import org.voidkit.perm.PermissionManager
import org.voidkit.perm.PermissionStore
import org.voidkit.perm.TempPermission
import org.voidkit.perm.UserPermissionData
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.util.UUID

/**
 * 权限管理（LuckPerms 风格）。
 *
 * 数据在 `voidkit/perm/` 下三份文件里，见 [PermissionStore]。
 *
 * ## 命令
 *
 * ```
 * /perm                                  我的权限概览
 * /perm check <节点>                      检查某个节点（附带“为什么”）
 * /perm nodes                            列出我生效的全部节点
 * /perm user <玩家>                       查看某玩家
 * /perm user <玩家> group <组>            设主组
 * /perm user <玩家> addgroup <组>         加附加组
 * /perm user <玩家> delgroup <组>         移除附加组
 * /perm user <玩家> add <节点>            加节点（前面加 - 就是否定）
 * /perm user <玩家> del <节点>            删节点
 * /perm user <玩家> grant <节点> <分钟>    临时授权
 * /perm group                            列出全部组
 * /perm group <组>                        查看某组
 * /perm group <组> add|del <节点>         增删节点
 * /perm group <组> parent <父组>          设父组（继承）
 * /perm group <组> prefix|suffix <文本>   设前后缀（支持 & 颜色码）
 * /perm group <组> weight <数值>          设权重
 * /perm create <组> / /perm delete <组>   新建 / 删除组
 * /perm reload                           从磁盘重载
 * ```
 *
 * 输出里的 `[xxx](run:/perm ...)` 是**可点击按钮**（用 [Text] 的富文本语法），
 * 点一下就直接执行或把命令填进聊天框 —— 这就是“游戏内可视化编辑”。
 */
class PermissionFeature : Feature() {

    override val id = "permissions"
    override val displayName = "权限管理"
    override val description = "LuckPerms 风格的权限节点：组继承、临时权限、游戏内点击式编辑与面板管理"
    override val defaultEnabled: Boolean = true

    // ---------- 生命周期 ----------

    override fun onSecond(server: MinecraftServer) {
        // 权限查询走只读路径、不落盘；这里统一把脏数据写出去
        PermissionStore.flushIfDirty()
    }

    override fun onServerStop(server: MinecraftServer) = PermissionStore.saveAll()

    override fun onReload(server: MinecraftServer) {
        PermissionStore.load()
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("perm")
                .executes { handle(it.source, "") }
                .then(
                    Commands.argument("args", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            listOf("check ", "nodes", "user ", "group", "create ", "delete ", "reload", "help")
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes { handle(it.source, StringArgumentType.getString(it, "args")) }
                )
        )
    }

    private fun handle(source: CommandSourceStack, args: String): Int {
        val parts = args.trim().split(' ').filter { it.isNotBlank() }
        val sub = parts.firstOrNull()?.lowercase() ?: ""
        val rest = parts.drop(1)
        return when (sub) {
            "" -> overview(source)
            "check" -> check(source, rest.joinToString(" "))
            "nodes" -> ownNodes(source)
            "user" -> adminOnly(source) { userCmd(source, rest) }
            "group" -> adminOnly(source) { groupCmd(source, rest) }
            "create" -> adminOnly(source) { createGroup(source, rest.firstOrNull()) }
            "delete", "del" -> adminOnly(source) { deleteGroup(source, rest.firstOrNull()) }
            "reload" -> adminOnly(source) { reloadCmd(source) }
            "help" -> { help(source); 1 }
            else -> {
                Msg.err(source, "未知子命令「$sub」")
                help(source)
                0
            }
        }
    }

    private inline fun adminOnly(source: CommandSourceStack, block: () -> Int): Int {
        if (!PermissionManager.admin(source)) {
            Msg.err(source, "你需要管理员权限才能执行这个操作")
            return 0
        }
        return block()
    }

    // ---------- 概览 / 自查 ----------

    private fun overview(source: CommandSourceStack): Int {
        val player = source.player
        if (player == null) {
            send(
                source,
                listOf(
                    "&8&m──────────&r &b权限管理 &8&m──────────",
                    "&7控制台用法：&f/perm user <玩家> &7查看某玩家",
                    "&7或直接用网页面板的「权限」页。",
                ),
            )
            return 1
        }
        val group = PermissionManager.resolvedGroup(player)
        val data = PermissionStore.find(player.uuid)
        val own = data?.ownNodes(System.currentTimeMillis())?.size ?: 0
        val effective = PermissionManager.effectiveNodes(player)
        val extras = data?.groups?.filter { it.isNotBlank() }?.joinToString("、")?.ifBlank { "无" } ?: "无"

        send(
            source,
            listOf(
                "&8&m──────────&r &b权限概览 &8&m──────────",
                "&7玩家：&f${player.gameProfile.name}",
                "&7主组：${group.prefix}&f${group.name} &8(权重 ${group.weight})",
                "&7附加组：&f$extras",
                "&7玩家专属节点：&f$own &7个",
                "&7生效节点合计：&f${effective.size} &7个",
                "",
                menu(
                    listOf(
                        "&a查看全部组" to "run:/perm group",
                        "&b检查某节点" to "suggest:/perm check ",
                        "&e我的节点" to "run:/perm nodes",
                    )
                ),
            ),
        )
        return 1
    }

    private fun check(source: CommandSourceStack, node: String): Int {
        if (node.isBlank()) {
            Msg.err(source, "用法：/perm check <权限节点>")
            return 0
        }
        val player = source.player
        if (player == null) {
            Msg.err(source, "这个子命令只能由玩家执行")
            return 0
        }
        val result = PermissionManager.has(player, node)
        source.sendSuccess(
            { Text.legacy("&7节点 &f$node &7→ " + if (result) "&a有权限" else "&c无权限") },
            false,
        )
        PermissionManager.explain(player, node).forEach { line ->
            source.sendSuccess({ Text.legacy("  &8$line") }, false)
        }
        return 1
    }

    private fun ownNodes(source: CommandSourceStack): Int {
        val player = source.player ?: run {
            Msg.err(source, "这个子命令只能由玩家执行")
            return 0
        }
        val nodes = PermissionManager.effectiveNodes(player)
        send(
            source,
            listOf("&8&m─────&r &b我生效的权限节点 &8(${nodes.size}) &m─────") +
                nodes.map { "&7· &f$it" } +
                listOf("", menu(listOf("&a返回概览" to "run:/perm")))
        )
        return 1
    }

    private fun help(source: CommandSourceStack): Unit = send(
        source,
        listOf(
            "&8&m──────────&r &b/perm 用法 &8&m──────────",
            "&f/perm &7我的概览",
            "&f/perm check <节点> &7检查节点（附原因）",
            "&f/perm nodes &7我生效的节点",
            "&f/perm user <玩家> &7查看玩家",
            "&f/perm user <玩家> group <组> &7设主组",
            "&f/perm user <玩家> add|del <节点> &7增删节点（- 前缀为否定）",
            "&f/perm user <玩家> grant <节点> <分钟> &7临时授权",
            "&f/perm group &7列出全部组",
            "&f/perm group <组> &7查看某组",
            "&f/perm group <组> add|del <节点>",
            "&f/perm group <组> parent <父组>",
            "&f/perm group <组> prefix|suffix <文本>",
            "&f/perm group <组> weight <数值>",
            "&f/perm create|delete <组>",
            "&f/perm reload",
        ),
    )

    // ---------- /perm user ----------

    private fun userCmd(source: CommandSourceStack, rest: List<String>): Int {
        val name = rest.firstOrNull()
        if (name.isNullOrBlank()) {
            Msg.err(source, "用法：/perm user <玩家> [group|addgroup|delgroup|add|del|grant]")
            return 0
        }
        val target = source.server.playerList.getPlayerByName(name)
        val uuid = target?.uuid ?: resolveOfflineUuid(name)
        if (uuid == null) {
            Msg.err(source, "找不到玩家 $name（本地没有他的档案）")
            return 0
        }
        val data = PermissionStore.user(uuid, target?.gameProfile?.name ?: name)
        val action = rest.getOrNull(1)?.lowercase() ?: "info"
        val arg = rest.getOrNull(2)?.trim().orEmpty()
        val now = System.currentTimeMillis()

        when (action) {
            "info" -> return userInfo(source, uuid, data)

            "group" -> {
                if (arg.isBlank()) {
                    Msg.err(source, "用法：/perm user $name group <组名>")
                    return 0
                }
                if (!PermissionStore.groups.containsKey(arg)) {
                    Msg.err(source, "没有名为 $arg 的组，可用：${PermissionStore.groups.keys.joinToString("、")}")
                    return 0
                }
                data.group = arg
                PermissionStore.markUsersDirty()
                Msg.ok(source, "已把 $name 的主组设为 $arg")
                return 1
            }

            "addgroup", "delgroup" -> {
                if (arg.isBlank()) {
                    Msg.err(source, "用法：/perm user $name $action <组名>")
                    return 0
                }
                if (action == "addgroup") {
                    if (!PermissionStore.groups.containsKey(arg)) {
                        Msg.err(source, "没有名为 $arg 的组")
                        return 0
                    }
                    if (data.groups.contains(arg)) {
                        Msg.info(source, "$name 已经在 $arg 组里了")
                        return 1
                    }
                    data.groups.add(arg)
                } else {
                    if (!data.groups.remove(arg)) {
                        Msg.info(source, "$name 不在 $arg 组里")
                        return 1
                    }
                }
                PermissionStore.markUsersDirty()
                Msg.ok(source, "已更新 $name 的附加组")
                return 1
            }

            "add" -> {
                if (arg.isBlank()) {
                    Msg.err(source, "用法：/perm user $name add <节点>（节点前加 - 表示否定）")
                    return 0
                }
                if (data.permissions.contains(arg)) {
                    Msg.info(source, "$name 已经有这个节点了")
                    return 1
                }
                data.permissions.add(arg)
                PermissionStore.markUsersDirty()
                Msg.ok(source, "已给 $name 添加节点 $arg")
                return 1
            }

            "del", "remove" -> {
                if (!data.permissions.remove(arg)) {
                    Msg.info(source, "$name 没有节点 $arg")
                    return 1
                }
                PermissionStore.markUsersDirty()
                Msg.ok(source, "已移除 $name 的节点 $arg")
                return 1
            }

            "grant" -> {
                val minutes = rest.getOrNull(3)?.toIntOrNull() ?: 0
                if (arg.isBlank() || minutes <= 0) {
                    Msg.err(source, "用法：/perm user $name grant <节点> <分钟>")
                    return 0
                }
                val until = now + minutes * 60_000L
                data.tempPermissions.removeIf { it.node == arg }
                data.tempPermissions.add(TempPermission().apply { node = arg; this.until = until })
                PermissionStore.markUsersDirty()
                Msg.ok(source, "已给 $name 临时授权 $arg，${Msg.duration(minutes * 60L)}后失效")
                return 1
            }

            else -> {
                Msg.err(source, "未知动作「$action」")
                return 0
            }
        }
    }

    private fun userInfo(source: CommandSourceStack, uuid: UUID, data: UserPermissionData): Int {
        val group = PermissionStore.group(data.group.ifBlank { PermissionStore.settings.defaultGroup })
        val now = System.currentTimeMillis()
        val tempLines = data.tempPermissions
            .filter { it.node.isNotBlank() }
            .map { temp ->
                val state = if (temp.until in 1 until now) "已过期" else "生效中"
                "&7· &f${temp.node} &8($state${if (temp.until > 0) "，至 ${java.text.SimpleDateFormat("MM-dd HH:mm").format(java.util.Date(temp.until))}" else ""})"
            }

        send(
            source,
            listOf(
                "&8&m──────&r &b玩家权限 &8${data.name.ifBlank { uuid.toString().take(8) }} &m──────",
                "&7主组：${group?.prefix ?: ""}&f${group?.name ?: data.group.ifBlank { PermissionStore.settings.defaultGroup }}",
                "&7附加组：&f${data.groups.filter { it.isNotBlank() }.joinToString("、").ifBlank { "无" }}",
                "&7玩家专属节点 (${data.permissions.size})：" + (data.permissions.joinToString("&7, &f").ifBlank { "&7无" }),
                "&7临时权限 (${data.tempPermissions.size})：",
            ) + tempLines + listOf(
                "",
                menu(
                    listOf(
                        "&a设主组" to "suggest:/perm user ${data.name} group ",
                        "&b加节点" to "suggest:/perm user ${data.name} add ",
                        "&c删节点" to "suggest:/perm user ${data.name} del ",
                        "&e临时授权" to "suggest:/perm user ${data.name} grant ",
                    )
                ),
            ),
        )
        return 1
    }

    private fun resolveOfflineUuid(name: String): UUID? =
        DataStore.get()?.all()
            ?.firstOrNull { profile ->
                profile.lastName.equals(name, true) || profile.knownNames.any { it.equals(name, true) }
            }
            ?.uuid
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    // ---------- /perm group ----------

    private fun groupCmd(source: CommandSourceStack, rest: List<String>): Int {
        val name = rest.firstOrNull()
        if (name.isNullOrBlank()) return groupList(source)

        val group = PermissionStore.group(name)
        if (group == null) {
            Msg.err(source, "没有名为 $name 的组，可用：${PermissionStore.groups.keys.joinToString("、")}")
            return 0
        }
        group.normalize()

        val action = rest.getOrNull(1)?.lowercase()
        if (action == null) return groupInfo(source, group)

        val arg = rest.drop(2).joinToString(" ").trim()

        when (action) {
            "add" -> {
                if (arg.isBlank()) { Msg.err(source, "用法：/perm group $name add <节点>"); return 0 }
                if (group.permissions.contains(arg)) { Msg.info(source, "节点已存在"); return 1 }
                group.permissions.add(arg)
            }

            "del", "remove" -> {
                if (!group.permissions.remove(arg)) { Msg.info(source, "没有节点 $arg"); return 1 }
            }

            "parent" -> {
                if (arg.isBlank()) { Msg.err(source, "用法：/perm group $name parent <父组>"); return 0 }
                if (arg == name) { Msg.err(source, "组不能把自己设为父组"); return 0 }
                if (!PermissionStore.groups.containsKey(arg)) { Msg.err(source, "没有名为 $arg 的组"); return 0 }
                if (group.parents.contains(arg)) { Msg.info(source, "已经是父组了"); return 1 }
                group.parents.add(arg)
            }

            "unparent" -> {
                if (!group.parents.remove(arg)) { Msg.info(source, "不是父组"); return 1 }
            }

            "prefix" -> {
                group.prefix = arg
                Msg.ok(source, "已设置 $name 的前缀")
                PermissionStore.saveGroups()
                return 1
            }

            "suffix" -> {
                group.suffix = arg
                Msg.ok(source, "已设置 $name 的后缀")
                PermissionStore.saveGroups()
                return 1
            }

            "weight" -> {
                val weight = arg.toIntOrNull()
                if (weight == null) { Msg.err(source, "权重要是整数"); return 0 }
                group.weight = weight
                Msg.ok(source, "已把 $name 的权重设为 $weight")
                PermissionStore.saveGroups()
                return 1
            }

            else -> {
                Msg.err(source, "未知动作「$action」")
                return 0
            }
        }

        PermissionStore.saveGroups()
        Msg.ok(source, "已更新组 $name")
        // 组一变，所有人的前缀/权限都可能变，立刻重推一次展示层
        refreshDisplay(source.server)
        return 1
    }

    private fun groupList(source: CommandSourceStack): Int {
        val groups = PermissionStore.groups.values.sortedByDescending { it.weight }
        val buttons = groups.map { "&f${it.name}" to "run:/perm group ${it.name}" }
        send(
            source,
            listOf("&8&m─────&r &b权限组 &8(${groups.size}) &m─────") +
                groups.flatMap { group ->
                    group.normalize()
                    listOf(
                        "${group.prefix}&f${group.name} &8权重 ${group.weight} &7节点 ${group.permissions.size} " +
                            "&7父组 ${group.parents.joinToString("、").ifBlank { "无" }}"
                    )
                } +
                listOf("", menu(buttons))
        )
        return 1
    }

    private fun groupInfo(source: CommandSourceStack, group: PermissionGroupData): Int {
        send(
            source,
            listOf(
                "&8&m──────&r &b权限组 ${group.name} &8&m──────",
                "&7前缀：${group.prefix}&r",
                "&7后缀：${group.suffix.ifBlank { "（空）" }}",
                "&7权重：&f${group.weight}",
                "&7父组：&f${group.parents.joinToString("、").ifBlank { "无" }}",
                "&7继承链：&f${PermissionStore.groupChain(group.name).joinToString(" → ") { it.name }}",
                "&7节点 (${group.permissions.size})：",
            ) + group.permissions.map { "&7· &f$it" } + listOf(
                "",
                menu(
                    listOf(
                        "&a加节点" to "suggest:/perm group ${group.name} add ",
                        "&b改前缀" to "suggest:/perm group ${group.name} prefix ",
                        "&e改权重" to "suggest:/perm group ${group.name} weight ",
                        "&6加父组" to "suggest:/perm group ${group.name} parent ",
                        "&c删组" to "run:/perm delete ${group.name}",
                    )
                ),
            ),
        )
        return 1
    }

    private fun createGroup(source: CommandSourceStack, name: String?): Int {
        if (name.isNullOrBlank()) { Msg.err(source, "用法：/perm create <组名>"); return 0 }
        if (PermissionStore.groups.containsKey(name)) { Msg.info(source, "组 $name 已存在"); return 1 }
        PermissionStore.groups[name] = PermissionGroupData(name = name, prefix = "&7")
        PermissionStore.saveGroups()
        Msg.ok(source, "已创建组 $name（记得给它加节点或父组）")
        return 1
    }

    private fun deleteGroup(source: CommandSourceStack, name: String?): Int {
        if (name.isNullOrBlank()) { Msg.err(source, "用法：/perm delete <组名>"); return 0 }
        if (name == PermissionStore.settings.defaultGroup) {
            Msg.err(source, "不能删除默认组 ${PermissionStore.settings.defaultGroup}")
            return 0
        }
        if (PermissionStore.groups.remove(name) == null) { Msg.err(source, "没有名为 $name 的组"); return 0 }
        // 把还在用这个组的玩家挪回默认组，免得他们变成“无组”状态
        val fallback = PermissionStore.settings.defaultGroup
        PermissionStore.users.values.forEach { data ->
            if (data.group == name) { data.group = fallback; PermissionStore.markUsersDirty() }
            data.groups.remove(name)
        }
        PermissionStore.saveGroups()
        Msg.ok(source, "已删除组 $name，原属该组的玩家已挪到 $fallback")
        refreshDisplay(source.server)
        return 1
    }

    private fun reloadCmd(source: CommandSourceStack): Int {
        PermissionStore.load()
        Msg.ok(source, "权限数据已从磁盘重载")
        refreshDisplay(source.server)
        return 1
    }

    /** 组变了以后，TAB 前缀/聊天前缀/记分牌都可能要重算 —— 让相关模块重跑一遍。 */
    private fun refreshDisplay(server: MinecraftServer) {
        runCatching {
            org.voidkit.core.core.PluginRegistry.reload(server)
        }.onFailure { VoidkitCore.LOG.warn("刷新展示层失败：{}", it.message) }
    }

    // ---------- 输出辅助 ----------

    private fun send(source: CommandSourceStack, lines: List<String>) {
        lines.forEach { line ->
            source.sendSuccess({ if (line.isEmpty()) Component.empty() else Text.legacy(line) }, false)
        }
    }

    /** 把「文字 -> 动作」列表拼成一排可点击按钮。 */
    private fun menu(items: List<Pair<String, String>>): String =
        items.joinToString("  ") { (label, action) -> "&8[${label}&8]($action)" }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/permissions/groups") { _ ->
            ApiJson.obj(
                "ok", true,
                "groups", ApiJson.arr(PermissionStore.groups.values.sortedByDescending { it.weight }.map { groupJson(it) })
            )
        }

        router.get("/api/permissions/settings") { _ ->
            val s = PermissionStore.settings
            ApiJson.obj(
                "ok", true,
                "settings", ApiJson.obj(
                    "defaultGroup", s.defaultGroup,
                    "opBypass", s.opBypass,
                    "defaultValue", s.defaultValue,
                    "wildcardEnabled", s.wildcardEnabled,
                    "prefixFromHighestWeight", s.prefixFromHighestWeight
                )
            )
        }

        router.get("/api/permissions/users") { request ->
            val keyword = request.query("q", "")!!.lowercase()
            val limit = request.queryInt("limit", 100).coerceIn(1, 500)
            val list = PermissionStore.users.values
                .filter { data ->
                    keyword.isBlank() ||
                        data.name.lowercase().contains(keyword) ||
                        data.group.lowercase().contains(keyword)
                }
                .sortedBy { it.name.lowercase() }
            ApiJson.obj(
                "ok", true,
                "total", list.size,
                "users", ApiJson.arr(list.take(limit).map { userJson(it) })
            )
        }

        router.get("/api/permissions/user/{key}") { request ->
            val key = request.param("key")!!
            val found = findUser(key)
                ?: throw IllegalArgumentException("找不到玩家: $key")
            ApiJson.obj("ok", true, "user", userJson(found.second), "uuid", found.first)
        }

        router.get("/api/permissions/explain") { request ->
            val playerName = request.query("player")
            val node = request.query("node")
            if (playerName.isNullOrBlank() || node.isNullOrBlank()) {
                throw IllegalArgumentException("需要 player 与 node 两个参数")
            }
            val server = org.voidkit.core.api.VoidkitServer.require()
            val player = server.playerList.getPlayerByName(playerName)
                ?: throw IllegalArgumentException("玩家 $playerName 不在线（解释功能需要在线玩家）")
            ApiJson.obj(
                "ok", true,
                "player", playerName,
                "node", node,
                "result", PermissionManager.has(player, node),
                "steps", ApiJson.arr(PermissionManager.explain(player, node)),
                "effectiveNodes", ApiJson.arr(PermissionManager.effectiveNodes(player))
            )
        }
    }

    private fun groupJson(group: PermissionGroupData): com.google.gson.JsonObject {
        group.normalize()
        return ApiJson.obj(
            "name", group.name,
            "prefix", group.prefix,
            "suffix", group.suffix,
            "weight", group.weight,
            "permissions", ApiJson.arr(group.permissions),
            "parents", ApiJson.arr(group.parents),
            "chain", ApiJson.arr(PermissionStore.groupChain(group.name).map { it.name })
        )
    }

    private fun userJson(data: UserPermissionData): com.google.gson.JsonObject {
        data.normalize()
        return ApiJson.obj(
            "name", data.name,
            "group", data.group,
            "groups", ApiJson.arr(data.groups),
            "permissions", ApiJson.arr(data.permissions),
            "tempPermissions", ApiJson.arr(
                data.tempPermissions.map { ApiJson.obj("node", it.node, "until", it.until) }
            ),
            "allGroups", ApiJson.arr(data.allGroups())
        )
    }

    private fun findUser(key: String): Pair<String, UserPermissionData>? {
        // 先按 UUID 精确找，再按名字兜底
        PermissionStore.users[key]?.let { return key to it }
        val lower = key.lowercase()
        return PermissionStore.users.entries
            .firstOrNull { it.value.name.lowercase() == lower }
            ?.let { it.key to it.value }
    }
}
