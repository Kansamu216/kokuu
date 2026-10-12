package org.voidkit.display

import net.minecraft.network.protocol.game.ClientboundTabListPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
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
 * TAB 玩家列表：顶栏 / 底栏文字 + 玩家名前缀。配置在 `display/tab.json`。
 *
 * 用 [ClientboundTabListPacket] **逐个玩家**推送，所以 header/footer 里可以安全地放
 * 玩家自己的变量（`%ping%` `%playtime%` `%world%` `%x%`），每个玩家看到的数字各不相同。
 *
 * 顶栏底栏支持 `&` 颜色码、`%变量%`，以及 `[文字](run:/命令 "悬停提示")` 可点击语法 ——
 * 也就是说可以在 TAB 里放一排“点击回城/打开菜单”的按钮。
 *
 * 「按权限组显示前缀」走原版**队伍（Team）机制**：1.21.1 没有直接改列表显示名的公开 API，
 * 列表里显示的就是「队伍前缀 + 玩家名 + 队伍后缀」，所以只能按组统一，不能逐人定制。
 */
class TabFeature : Feature() {

    private var seconds = 0

    /** 刚进服的玩家要立刻收到一次，否则要等一整个刷新周期。 */
    private val pending = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    override val id = "tab"
    override val displayName = "TAB 玩家列表"
    override val description = "自定义 Tab 顶栏底栏，按权限组显示名字前缀，支持变量与点击"

    override val defaultEnabled: Boolean get() = DisplayStore.tab.enabled

    override fun onServerStart(server: MinecraftServer) {
        seconds = 0
        applyGroupPrefix(server)
        push(server)
    }

    override fun onReload(server: MinecraftServer) {
        seconds = 0
        applyGroupPrefix(server)
        push(server)
    }

    override fun onServerStop(server: MinecraftServer) = clearTeams(server)

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) return
        val cfg = DisplayStore.tab
        val interval = max(1, cfg.updateSeconds)
        seconds++
        val due = seconds % interval == 0
        if (!due && pending.isEmpty()) return

        if (cfg.header.isEmpty() && cfg.footer.isEmpty() && !cfg.useGroupPrefix) {
            pending.clear()
            return
        }
        for (player in server.playerList.players) {
            if (!due && !pending.remove(player.uuid.toString())) continue
            // 到点刷新时顺便重设一次组前缀 —— 玩家可能刚被换组
            if (due) applyGroupPrefix(server, player)
            send(player, cfg.header, cfg.footer)
        }
    }

    @SubscribeEvent
    fun onLogin(event: PlayerEvent.PlayerLoggedInEvent) {
        if (!enabled) return
        val player = event.entity as? ServerPlayer ?: return
        applyGroupPrefix(player.server, player)
        pending.add(player.uuid.toString())
    }

    @SubscribeEvent
    fun onLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        pending.remove(player.uuid.toString())
    }

    // ---------- 实现 ----------

    private fun push(server: MinecraftServer) {
        val cfg = DisplayStore.tab
        if (cfg.header.isEmpty() && cfg.footer.isEmpty()) return
        server.playerList.players.forEach { send(it, cfg.header, cfg.footer) }
    }

    private fun send(player: ServerPlayer, headerLines: List<String>, footerLines: List<String>) {
        val ctx = PlaceholderRegistry.Context(player.server, player)
        val header = Text.legacyLines(PlaceholderRegistry.resolve(headerLines, ctx))
        val footer = Text.legacyLines(PlaceholderRegistry.resolve(footerLines, ctx))
        runCatching { player.connection.send(ClientboundTabListPacket(header, footer)) }
            .onFailure { VoidkitCore.LOG.debug("推送 TAB 失败（{}）：{}", player.gameProfile.name, it.message) }
    }

    /**
     * 把权限组的前缀/后缀做成原版队伍，TAB 列表里就显示成 `[管理] 玩家名`。
     *
     * [only] 非空时只处理这一个玩家（进服 / 刷新时用），否则处理全部在线玩家。
     */
    private fun applyGroupPrefix(server: MinecraftServer, only: ServerPlayer? = null) {
        if (!DisplayStore.tab.useGroupPrefix) return
        val scoreboard = server.scoreboard
        val targets = if (only != null) listOf(only) else server.playerList.players
        for (player in targets) {
            runCatching {
                // 接口只给组名和前/后缀（都是 String）—— 拿不到权限系统的内部结构，
                // 这是刻意的：display 不该知道组是怎么存的。
                val group = VoidkitServices.permission().groupOf(player)
                val teamName = TEAM_PREFIX + group.take(12)
                val team = scoreboard.getPlayerTeam(teamName) ?: scoreboard.addPlayerTeam(teamName)

                val prefix = Text.legacy(VoidkitServices.permission().prefixOf(player))
                val suffix = Text.legacy(VoidkitServices.permission().suffixOf(player))
                if (team.playerPrefix?.string != prefix.string) team.playerPrefix = prefix
                if (team.playerSuffix?.string != suffix.string) team.playerSuffix = suffix

                val name = player.gameProfile.name
                if (scoreboard.getPlayersTeam(name) !== team) scoreboard.addPlayerToTeam(name, team)
            }.onFailure {
                VoidkitCore.LOG.debug("设置 {} 的 TAB 组前缀失败：{}", player.gameProfile.name, it.message)
            }
        }
    }

    /** 关服时摘掉自己建的队伍，免得在存档里越攒越多。 */
    private fun clearTeams(server: MinecraftServer) {
        runCatching {
            val scoreboard = server.scoreboard
            scoreboard.playerTeams
                .filter { it.name.startsWith(TEAM_PREFIX) }
                .forEach { scoreboard.removePlayerTeam(it) }
        }
    }

    /** 面板写回配置。 */
    fun updateFrom(json: com.google.gson.JsonObject): Boolean {
        val cfg = DisplayStore.tab
        var changed = false

        json.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let { cfg.enabled = it; changed = true }
        json.get("updateSeconds")?.takeIf { it.isJsonPrimitive }?.asInt?.let {
            cfg.updateSeconds = it.coerceIn(1, 3600); changed = true
        }
        json.get("useGroupPrefix")?.takeIf { it.isJsonPrimitive }?.asBoolean?.let {
            cfg.useGroupPrefix = it; changed = true
        }
        json.getAsJsonArray("header")?.let { cfg.header = it.map { line -> line.asString }.toMutableList(); changed = true }
        json.getAsJsonArray("footer")?.let { cfg.footer = it.map { line -> line.asString }.toMutableList(); changed = true }

        if (changed) {
            cfg.normalize()
            DisplayStore.saveTab()
            val server = org.voidkit.core.api.VoidkitServer.require()
            applyGroupPrefix(server)
            onReload(server)
        }
        return changed
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/tab") { _ ->
            val cfg = DisplayStore.tab
            ApiJson.obj(
                "ok", true,
                "enabled", enabled,
                "updateSeconds", cfg.updateSeconds,
                "useGroupPrefix", cfg.useGroupPrefix,
                "header", ApiJson.arr(cfg.header),
                "footer", ApiJson.arr(cfg.footer),
                "clickable", true
            )
        }
        router.get("/api/tab/preview") { _ ->
            val server = org.voidkit.core.api.VoidkitServer.require()
            val sample = server.playerList.players.firstOrNull()
            val ctx = PlaceholderRegistry.Context(server, sample)
            ApiJson.obj(
                "ok", true,
                "player", sample?.gameProfile?.name ?: "-",
                "header", ApiJson.arr(PlaceholderRegistry.resolve(DisplayStore.tab.header, ctx)),
                "footer", ApiJson.arr(PlaceholderRegistry.resolve(DisplayStore.tab.footer, ctx))
            )
        }
        router.post("/api/tab") { request ->
            val changed = updateFrom(request.bodyJson())
            ApiJson.obj("ok", true, "changed", changed)
        }
    }

    private companion object {
        const val TEAM_PREFIX = "kcg_"
    }
}
