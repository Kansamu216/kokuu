package org.voidkit.web

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.PlayerViews
import org.voidkit.core.config.ConfigEditor
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.IpRecord
import org.voidkit.core.data.JoinLog
import org.voidkit.core.data.PlayerProfile
import java.util.UUID

/**
 * 内核通用 API：健康检查、服务器状态、玩家档案、上下线日志、广播、重载。
 * 各模块自己的路由在 `Feature.registerApi` 里追加。
 */
object ApiDefaults {

    fun register(router: ApiRouter) {

        // ---------- 健康检查（无需令牌） ----------
        router.get("/api/health") { _ ->
            ApiJson.obj("ok", true, "mod", VoidkitCore.MODID, "version", VoidkitCore.VERSION, "api", "v1")
        }

        // ---------- 服务器概览 ----------
        router.get("/api/stats") { _ ->
            val server = ApiServer.get().server()
            // 运行时长取自 ServerLifecycle，与 API 模块本身的启停解耦
            val uptime = org.voidkit.core.event.ServerLifecycle.uptimeSeconds() * 1000L
            val avgTps = if (uptime > 2000) minOf(20.0, server.tickCount / (uptime / 1000.0)) else 20.0
            val runtime = Runtime.getRuntime()
            ApiJson.obj(
                "online", server.playerCount,
                "maxPlayers", server.playerList.maxPlayers,
                "totalProfiles", DataStore.get()?.all()?.size ?: 0,
                "uptimeMs", uptime,
                "avgTps", Math.round(avgTps * 100) / 100.0,
                "tickCount", server.tickCount,
                "memoryUsedMb", (runtime.totalMemory() - runtime.freeMemory()) / 1048576L,
                "memoryMaxMb", runtime.maxMemory() / 1048576L,
                "motd", server.motd,
                "version", server.serverVersion,
                "apiRunning", ApiServer.get().running()
            )
        }

        // ---------- 模块清单 ----------
        router.get("/api/features") { _ ->
            val array = JsonArray()
            PluginRegistry.all().forEach { feature ->
                array.add(
                    ApiJson.obj(
                        "id", feature.id,
                        "name", feature.displayName,
                        "description", feature.description,
                        "enabled", feature.enabled,
                        "defaultEnabled", feature.defaultEnabled
                    )
                )
            }
            ApiJson.obj("ok", true, "features", array)
        }

        // ---------- 变量清单（面板可做变量提示） ----------
        router.get("/api/placeholders") { _ ->
            ApiJson.obj(
                "ok", true,
                "keys", org.voidkit.core.placeholder.PlaceholderRegistry.keys().sorted()
            )
        }

        // ---------- 玩家列表 ----------
        router.get("/api/players") { request ->
            val store = DataStore.get() ?: return@get ApiJson.obj("ok", true, "total", 0, "players", JsonArray())
            val onlyOnline = request.queryBool("online", false)
            val keyword = request.query("q", "")!!.lowercase()
            val limit = request.queryInt("limit", 100).coerceIn(1, 1000)
            val offset = request.queryInt("offset", 0).coerceAtLeast(0)

            val list = (if (onlyOnline) store.online() else store.all()).toMutableList()
            if (keyword.isNotBlank()) {
                list.removeIf { profile ->
                    !profile.lastName.lowercase().contains(keyword) &&
                        profile.knownNames.none { it.lowercase().contains(keyword) } &&
                        !profile.uuid.contains(keyword)
                }
            }
            when (request.query("sort", "lastSeen")) {
                "name" -> list.sortBy { it.lastName.lowercase() }
                "playtime" -> list.sortByDescending { it.playtimeSeconds }
                "joinCount" -> list.sortByDescending { it.joinCount }
                "firstJoin" -> list.sortBy { it.firstJoin }
                else -> list.sortByDescending { it.lastSeen }
            }

            val total = list.size
            val page = list.subList(minOf(offset, total), minOf(offset + limit, total))
            ApiJson.obj(
                "ok", true,
                "total", total,
                "offset", offset,
                "limit", limit,
                "players", page.map { summary(it) }
            )
        }

        // ---------- 玩家详情 ----------
        router.get("/api/players/{key}") { request ->
            val profile = resolve(request.param("key"))
                ?: throw IllegalArgumentException("找不到玩家: ${request.param("key")}")
            ApiJson.obj("ok", true, "player", detail(profile))
        }

        // ---------- 玩家 IP 历史 ----------
        router.get("/api/players/{key}/ips") { request ->
            val profile = resolve(request.param("key"))
                ?: throw IllegalArgumentException("找不到玩家: ${request.param("key")}")
            val ips = profile.ips.sortedByDescending { it.lastSeen }
            ApiJson.obj("ok", true, "player", profile.lastName, "ips", ips.map { PlayerViews.ipJson(it) })
        }

        // ---------- 上下线事件流 ----------
        router.get("/api/logs/joins") { request ->
            val store = DataStore.get()
                ?: return@get ApiJson.obj("ok", true, "entries", JsonArray())
            val limit = request.queryInt("limit", 100).coerceIn(1, 2000)
            val uuid = request.query("uuid")
            val action = request.query("action")
            val entries = store.joinLog.tail(if (uuid == null) limit * 3 else limit * 20)
                .asSequence()
                .filter { uuid == null || it.uuid.equals(uuid, ignoreCase = true) }
                .filter { action == null || it.action == action }
                .take(limit)
                .map { PlayerViews.entryJson(it) }
                .toList()
            ApiJson.obj("ok", true, "entries", entries)
        }

        // ---------- 踢出 ----------
        router.post("/api/players/{key}/kick") { request ->
            val body = request.bodyJson()
            val reason = body.get("reason")?.takeIf { !it.isJsonNull }?.asString ?: "被管理员移出服务器"
            val key = request.param("key")!!
            val done = ApiServer.get().onServer {
                val player = findOnline(key) ?: return@onServer false
                player.connection.disconnect(Component.literal(reason))
                true
            }
            if (!done) throw IllegalArgumentException("玩家不在线")
            ApiJson.ok("已踢出 $key")
        }

        // ---------- 私信给玩家 ----------
        router.post("/api/players/{key}/message") { request ->
            val body = request.requireField("message")
            val message = body.get("message").asString
            val prefix = body.get("prefix")?.asString ?: "[面板] "
            val key = request.param("key")!!
            val done = ApiServer.get().onServer {
                val player = findOnline(key) ?: return@onServer false
                player.sendSystemMessage(Component.literal(prefix + message))
                true
            }
            if (!done) throw IllegalArgumentException("玩家不在线")
            ApiJson.ok("已发送")
        }

        // ---------- 全服广播 ----------
        router.post("/api/broadcast") { request ->
            val message = request.requireField("message").get("message").asString
            ApiServer.get().onServer {
                ApiServer.get().server().playerList.broadcastSystemMessage(Component.literal(message), false)
                null
            }
            ApiJson.ok("已广播")
        }

        // ---------- 玩家管理：禁言 / 解禁 ----------
        router.post("/api/players/{key}/mute") { request ->
            val body = request.bodyJson()
            val minutes = body.get("minutes")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val reason = body.get("reason")?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString ?: ""
            val until = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L

            ApiServer.get().onServer {
                val profile = resolve(request.param("key")) ?: throw IllegalArgumentException("找不到玩家")
                profile.muted = true
                profile.mutedUntil = until
                profile.muteReason = reason
                DataStore.get()?.markDirty()
                DataStore.get()?.save(profile)
                findOnline(profile.uuid)
                    ?.sendSystemMessage(Component.literal("你已被管理员禁言"))
                ApiJson.obj(
                    "ok", true,
                    "player", profile.lastName,
                    "muted", true,
                    "until", until,
                    "minutes", minutes
                )
            }
        }

        router.post("/api/players/{key}/unmute") { request ->
            ApiServer.get().onServer {
                val profile = resolve(request.param("key")) ?: throw IllegalArgumentException("找不到玩家")
                profile.muted = false
                profile.mutedUntil = 0L
                profile.muteReason = ""
                DataStore.get()?.markDirty()
                DataStore.get()?.save(profile)
                findOnline(profile.uuid)?.sendSystemMessage(Component.literal("你的禁言已被解除"))
                ApiJson.obj("ok", true, "player", profile.lastName, "muted", false)
            }
        }

        // ---------- 权限组相关的端点在这里**没有** ----------
        // 它们属于 voidkit_perm 插件，由该插件自己注册（/api/permissions/*）。
        // web 插件不认识权限系统的数据结构 —— 这正是这次拆分要达到的效果。

        // ---------- 配置写回（白名单字段，见 ConfigEditor） ----------
        router.post("/api/config") { request ->
            val body = request.bodyJson()
            if (body.entrySet().isEmpty()) {
                throw IllegalArgumentException("请求体为空")
            }
            val changed = ApiServer.get().onServer {
                // 内核自己的字段走 ConfigEditor；管理 API 的字段归本插件管
                val keys = ArrayList(ConfigEditor.apply(body))
                keys += WebStore.applyFrom(body)
                VoidkitCore.saveConfig()
                VoidkitCore.loadConfig()
                PluginRegistry.reload(ApiServer.get().server())
                keys
            }
            ApiJson.obj("ok", true, "changed", changed, "message", "已应用 ${changed.size} 项配置")
        }

        // ---------- 重载 ----------
        router.post("/api/reload") { _ ->
            ApiServer.get().onServer {
                VoidkitCore.loadConfig()
                // reload 内部会 applyConfig + 重新挂载事件总线，再逐个通知模块
                PluginRegistry.reload(ApiServer.get().server())
                null
            }
            ApiJson.ok("配置已重载")
        }

        // ---------- 当前配置（脱敏） ----------
        router.get("/api/config") { _ ->
            val c = VoidkitCore.config
            val web = WebStore.config
            ApiJson.obj(
                "ok", true,
                "config", ApiJson.obj(
                    // 管理 API 自己的配置 —— 从 WebStore 读，不再从内核配置里借
                    "apiEnabled", web.enabled,
                    "apiBind", web.bind,
                    "apiPort", web.port,
                    "apiTokenMasked", mask(web.token),
                    "apiRateLimitPerSecond", web.rateLimitPerSecond,
                    // 内核自己的配置
                    "language", c.language,
                    "recordIps", c.recordIps,
                    "maxJoinLogEntries", c.maxJoinLogEntries,
                    "autoSaveSeconds", c.autoSaveSeconds
                )
                // 家上限 / TPA 超时等属于 essentials 插件，见 /api/config/plugins
                // 权限组清单由 voidkit_perm 的 /api/permissions/groups 提供
            )
        }

        /**
         * 各插件自己公开的配置快照。
         *
         * 用的是内核的 [org.voidkit.core.api.ConfigSnapshots] 登记处 —— 每个插件
         * 主动上报自己愿意公开的字段，**web 不 import 任何插件**。
         * 所以新装一个插件、哪怕它叫什么都行，这里会自动多出一块。
         */
        router.get("/api/config/plugins") { _ ->
            // 逐插件手工拼 JsonObject —— ApiJson.obj 是 (key, value) 交替的可变参数，
            // 用 flatMap + 展开构造容易把键值顺序搞错，这里显式写更稳妥
            val plugins = JsonObject()
            org.voidkit.core.api.ConfigSnapshots.all().forEach { (owner, values) ->
                val one = JsonObject()
                values.forEach { (key, value) -> one.add(key, ApiJson.toElement(value)) }
                plugins.add(owner, one)
            }
            ApiJson.obj(
                "ok", true,
                "owners", ApiJson.arr(org.voidkit.core.api.ConfigSnapshots.owners()),
                "plugins", plugins
            )
        }
    }

    // ---------- 辅助 ----------

    private fun mask(token: String): String =
        if (token.length <= 8) "********" else token.take(4) + "…" + token.takeLast(4)

    // ---------- 视图转发 ----------
    // 这几个方法已上移到内核的 PlayerViews：插件们直接调它，不必依赖 web 插件。
    // 这里保留同名转发，只是为了让本文件内部的调用少改几处。

    fun resolve(key: String?): PlayerProfile? = PlayerViews.resolve(key)

    fun findOnline(key: String?): ServerPlayer? = PlayerViews.findOnline(key)

    fun summary(profile: PlayerProfile): JsonObject = PlayerViews.summary(profile)

    fun detail(profile: PlayerProfile): JsonObject = PlayerViews.detail(profile)
}
