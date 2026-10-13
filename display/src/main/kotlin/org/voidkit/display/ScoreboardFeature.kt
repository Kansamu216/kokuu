package org.voidkit.display

import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.ScoreHolder
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.placeholder.PlaceholderRegistry
import org.voidkit.core.util.Text
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 侧边栏记分牌。配置在 `display/scoreboard.json`，可以配**多套**。
 *
 * 每个玩家看到的是「他有权限的看板里优先级最高的那套」—— 比如默认玩家看基础信息，
 * VIP 看带额外信息的版本，管理员看带调试数据的版本。
 *
 * **每个玩家一个独立 objective**（`kc_<uuid前8位>`），再把
 * [ClientboundSetDisplayObjectivePacket] 单独推给他 —— 这是实现「每人侧边栏内容不同」的标准做法：
 * 行里可以直接写 `%ping%` `%playtime%` `%homes%` 这类只对该玩家成立的变量。
 *
 * 两个实现细节：
 *  - 行文本是 ScoreHolder 的名字（1.20.3 起记分牌用 `ScoreHolder` 而非 [net.minecraft.network.chat.Component]），
 *    所以**重复的行**会互相覆盖。这里给第 i 行追加 i 个不可见的 `§r`，既保证唯一又不影响观感。
 *  - 因为行是**纯字符串**，所以要显式把 `&a` 转成 `§a`（[Text.toSection]）；
 *    同理记分牌**不支持点击事件**，行里的 `[文字](run:/...)` 会被剥成普通文字。
 *  - 记分牌数据会被存档持久化，所以玩家退出时必须显式删掉自己的 objective，否则会越攒越多。
 */
class ScoreboardFeature : Feature() {

    private var seconds = 0

    /** objective 名 → 上一轮的行（用于删掉这轮不再出现的行）。 */
    private val lastLines = HashMap<String, List<String>>()

    /** 刚上线的玩家，下一次 tick 立刻渲染，不等刷新周期。 */
    private val pending = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    /** 已经打过“推送成功”日志的玩家（进服时清空）—— 用于排查“看不到记分牌”。 */
    private val logged = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private companion object {
        /** 追加在行尾的不可见重置码，用来把「文本相同的行」区分成不同的 ScoreHolder。 */
        const val INVISIBLE = "\u00a7r"

        const val OBJECTIVE_PREFIX = "kc_"
    }

    override val id = "scoreboard"
    override val displayName = "侧边栏记分牌"
    override val description = "每个玩家独立的侧边栏信息板，支持多套按权限切换"

    override val defaultEnabled: Boolean get() = DisplayStore.scoreboard.enabled

    // ---------- 生命周期 ----------

    override fun onServerStart(server: MinecraftServer) {
        seconds = 0
        clearAll(server)
        refresh(server)
    }

    override fun onServerStop(server: MinecraftServer) = clearAll(server)

    override fun onReload(server: MinecraftServer) {
        seconds = 0
        lastLines.clear()
        clearAll(server)
        refresh(server)
    }

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) return
        val interval = max(1, DisplayStore.scoreboard.updateSeconds)
        seconds++
        val due = seconds % interval == 0
        if (!due && pending.isEmpty()) return

        val scoreboard = server.scoreboard
        for (player in server.playerList.players) {
            if (!due && !pending.remove(player.uuid.toString())) continue
            runCatching { render(scoreboard, server, player) }
                .onFailure { VoidkitCore.LOG.warn("渲染 {} 的记分牌失败：{}", player.gameProfile.name, it.message) }
        }
    }

    // ---------- 事件 ----------

    override fun onPlayerJoin(e: org.voidkit.core.api.events.VPlayerJoin) {
        if (!enabled) return
        pending.add(e.player.uuid.toString())
    }

    override fun onPlayerQuit(e: org.voidkit.core.api.events.VPlayerQuit) {
        val player = e.player
        pending.remove(player.uuid.toString())
        logged.remove(player.uuid.toString())
        // 玩家已经下线，把它的 objective 从存档里摘掉
        runCatching {
            val server = player.server
            val name = objectiveName(player)
            server.scoreboard.getObjective(name)?.let { server.scoreboard.removeObjective(it) }
            lastLines.remove(name)
        }
    }

    // ---------- 渲染 ----------

    private fun refresh(server: MinecraftServer) {
        if (!enabled) return
        val scoreboard = server.scoreboard
        server.playerList.players.forEach { player ->
            runCatching { render(scoreboard, server, player) }
                .onFailure { VoidkitCore.LOG.warn("渲染 {} 的记分牌失败：{}", player.gameProfile.name, it.message) }
        }
    }

    /** 按权限挑出该玩家该看的那套；没有就把他侧边栏清掉。 */
    private fun selectBoard(player: ServerPlayer): BoardDefinition? =
        DisplayStore.scoreboard.selectFor { node -> VoidkitServices.permission().has(player, node) }

    private fun render(scoreboard: Scoreboard, server: MinecraftServer, player: ServerPlayer) {
        val board = selectBoard(player)
        if (board == null) {
            clearFor(scoreboard, player)
            return
        }

        val name = objectiveName(player)
        val title = Text.legacy(board.title)
        val ctx = PlaceholderRegistry.Context(server, player)

        // 行是纯字符串 —— 必须显式转成 § 格式码，否则客户端会把 "&a" 原样显示出来。
        // 顺带把 [文字](动作) 的动作剥掉：记分牌不支持点击事件。
        val rendered = PlaceholderRegistry.resolve(board.lines, ctx)
            .map { Text.toSection(it) }
            // 空行会让客户端把整行折叠掉，占位成一个空格保住排版
            .map { if (it.isBlank()) " " else it }
        val holders = rendered.mapIndexed { index, line -> line + INVISIBLE.repeat(index) }

        var objective: Objective? = scoreboard.getObjective(name)
        val existing = objective
        if (existing == null || existing.displayName != title) {
            existing?.let { scoreboard.removeObjective(it) }
            objective = scoreboard.addObjective(
                name,
                ObjectiveCriteria.DUMMY,
                title,
                ObjectiveCriteria.RenderType.INTEGER,
                false,
                null
            )
            // 🚨 关键：NeoForge 1.21.1 的 ServerScoreboard 只有「被 track 的 objective」的
            // 分数变化才会广播给玩家（onScoreChanged 里 Set.contains(trackedObjectives) 才广播）。
            // 原版只在 setDisplayObjective 时自动 track —— 但我们用「直接发显示包」实现
            // 「每人独立记分牌」（绕过了 setDisplayObjective），所以必须手动 startTracking，
            // 否则分数压根不发给客户端，客户端侧边栏是空的（只有标题都看不到）。
            (scoreboard as? net.minecraft.server.ServerScoreboard)?.startTrackingObjective(objective!!)
            lastLines.remove(name)
        }
        val target = objective ?: return

        val sb = scoreboard as? net.minecraft.server.ServerScoreboard

        // 清掉上一轮的全部行。刻意**不用「差集」**——差集依赖轮间逐字比较，像 %tps% 这种
        // 每轮都变的行容易漏删；漏删的行会残留在客户端，表现为「TPS 行无限繁殖、记分牌越长越大」。
        // 全删全设最稳（最多 15 行，开销可忽略）。
        lastLines[name]?.forEach { old ->
            val h = ScoreHolder.forNameOnly(old)
            runCatching {
                scoreboard.getOrCreatePlayerScore(h, target).reset()
                // 🚨 reset 只改服务端状态，**不会广播「删除分数」给客户端** —— 必须显式调
                // onPlayerScoreRemoved，否则客户端那一行永远留在侧边栏上。
                sb?.onPlayerScoreRemoved(h, target)
            }
        }

        // 分数从下往上递减，保证显示顺序与配置一致
        var score = holders.size
        holders.forEach { holder ->
            scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), target).set(score--)
        }
        lastLines[name] = holders

        player.connection.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, target))

        // 每个玩家每次上线只打一次。进服后看不到记分牌时，靠这行区分
        // 「是我们压根没推」还是「推了但客户端没显示」。
        if (logged.add(player.uuid.toString())) {
            VoidkitCore.LOG.info(
                "已向 {} 推送记分牌「{}」：{} 行，objective={}",
                player.gameProfile.name,
                board.id,
                holders.size,
                name,
            )
        }
    }

    private fun clearFor(scoreboard: Scoreboard, player: ServerPlayer) {
        val name = objectiveName(player)
        scoreboard.getObjective(name)?.let { scoreboard.removeObjective(it) }
        lastLines.remove(name)
        runCatching {
            player.connection.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null))
        }
    }

    private fun clearAll(server: MinecraftServer) {
        val scoreboard = server.scoreboard
        // 先按在线玩家清，再兜底扫一遍自己的 objective（覆盖异常退出的残留）
        server.playerList.players.forEach { clearFor(scoreboard, it) }
        scoreboard.objectiveNames
            .filter { it.startsWith(OBJECTIVE_PREFIX) }
            .forEach { stale -> scoreboard.getObjective(stale)?.let { scoreboard.removeObjective(it) } }
    }

    private fun objectiveName(player: ServerPlayer): String =
        OBJECTIVE_PREFIX + player.uuid.toString().substring(0, 8)

    // ---------- 面板写回 ----------

    fun updateFrom(json: com.google.gson.JsonObject): Boolean {
        val cfg = DisplayStore.scoreboard
        var changed = false

        json.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { cfg.enabled = it; changed = true }
        json.get("updateSeconds")?.takeIf { it.isJsonPrimitive }?.asInt?.let {
            cfg.updateSeconds = it.coerceIn(1, 3600); changed = true
        }
        json.getAsJsonArray("boards")?.let { array ->
            val boards = mutableListOf<BoardDefinition>()
            array.forEach { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val board = BoardDefinition()
                obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.let { board.id = it }
                obj.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.let { board.title = it }
                obj.getAsJsonArray("lines")?.let { lines ->
                    board.lines = lines.map { it.asString }.take(15).toMutableList()
                }
                obj.get("permission")?.takeIf { it.isJsonPrimitive }?.asString?.let { board.permission = it }
                obj.get("priority")?.takeIf { it.isJsonPrimitive }?.asInt?.let { board.priority = it }
                board.normalize()
                boards += board
            }
            if (boards.isNotEmpty()) {
                cfg.boards = boards
                changed = true
            }
        }

        if (changed) {
            cfg.normalize()
            DisplayStore.saveScoreboard()
            onReload(org.voidkit.core.api.VoidkitServer.require())
        }
        return changed
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        // 排查用：进服后看不到记分牌时打这个接口，能直接看到
        //「objective 建了没 / 挂上侧边栏没 / 推了几行 / 选的是哪套看板」
        router.get("/api/scoreboard/debug") { _ ->
            val server = org.voidkit.core.api.VoidkitServer.require()
            val scoreboard = server.scoreboard
            ApiJson.obj(
                "ok", true,
                "featureEnabled", enabled,
                "objectivesOnScoreboard", ApiJson.arr(
                    scoreboard.objectiveNames.filter { it.startsWith(OBJECTIVE_PREFIX) }
                ),
                "online", ApiJson.arr(
                    server.playerList.players.map { player ->
                        val name = objectiveName(player)
                        val objective = scoreboard.getObjective(name)
                        ApiJson.obj(
                            "player", player.gameProfile.name,
                            "objectiveName", name,
                            "objectiveExists", objective != null,
                            "title", objective?.displayName?.string ?: "",
                            "pushedLines", lastLines[name]?.size ?: 0,
                            "board", selectBoard(player)?.id ?: "-"
                        )
                    }
                )
            )
        }

        router.get("/api/scoreboard") { _ ->
            val cfg = DisplayStore.scoreboard
            ApiJson.obj(
                "ok", true,
                "enabled", enabled,
                "updateSeconds", cfg.updateSeconds,
                "boards", ApiJson.arr(
                    cfg.boards.map { board ->
                        ApiJson.obj(
                            "id", board.id,
                            "title", board.title,
                            "lines", ApiJson.arr(board.lines),
                            "permission", board.permission,
                            "priority", board.priority
                        )
                    }
                ),
                // 记分牌行是纯文本，客户端不支持点击事件 —— 面板据此禁用”插入点击按钮"
                "clickable", false
            )
        }
        router.get("/api/scoreboard/preview") { _ ->
            val server = org.voidkit.core.api.VoidkitServer.require()
            val sample = server.playerList.players.firstOrNull()
            val ctx = PlaceholderRegistry.Context(server, sample)
            val board = sample?.let { selectBoard(it) } ?: DisplayStore.scoreboard.boards.firstOrNull()
            ApiJson.obj(
                "ok", true,
                "player", sample?.gameProfile?.name ?: "-",
                "boardId", board?.id ?: "-",
                "title", Text.strip(board?.title ?: ""),
                "lines", ApiJson.arr(
                    PlaceholderRegistry.resolve(board?.lines ?: emptyList(), ctx).map { Text.strip(it) }
                )
            )
        }
        router.post("/api/scoreboard") { request ->
            val changed = updateFrom(request.bodyJson())
            ApiJson.obj("ok", true, "changed", changed)
        }
    }
}
