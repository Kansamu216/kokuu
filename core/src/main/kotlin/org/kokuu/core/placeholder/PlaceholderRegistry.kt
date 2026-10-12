package org.kokuu.core.placeholder

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.kokuu.core.KokuuCore

/**
 * 变量（占位符）模块。
 *
 * 设计目标是**可复用**：任何需要展示变量文字的功能（TAB、记分牌、聊天、面板）
 * 都只需要调用 [resolve]，而不关心变量由谁提供。
 *
 * 两种注册形式：
 * - [register]：全局 key，模板里写 `%key%`
 * - [registerExpansion]：带命名空间，模板里写 `%expansion_key%`（给第三方模块/插件复用，避免撞名）
 *
 * 未注册的占位符会**原样保留**，方便在配置里一眼看出写错了哪个变量。
 */
object PlaceholderRegistry {

    /** 解析上下文：玩家可能为空（例如服务端控制台或纯全服变量）。 */
    class Context(val server: MinecraftServer, val player: ServerPlayer?)

    fun interface Resolver {
        fun resolve(ctx: Context): String?
    }

    private val resolvers = LinkedHashMap<String, Resolver>()

    /** 注册全局变量：模板里写 `%key%`。 */
    fun register(key: String, resolver: Resolver) {
        resolvers[key.lowercase()] = resolver
    }

    /** 注册带命名空间的变量：模板里写 `%expansion_key%`。 */
    fun registerExpansion(expansion: String, key: String, resolver: Resolver) {
        resolvers["${expansion.lowercase()}_${key.lowercase()}"] = resolver
    }

    fun unregister(key: String) {
        resolvers.remove(key.lowercase())
    }

    fun keys(): Set<String> = resolvers.keys.toSet()

    /** 解析模板里的全部 `%...%`。 */
    fun resolve(template: String, ctx: Context): String {
        if (template.indexOf('%') < 0) return template
        val out = StringBuilder(template.length + 32)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '%') {
                val end = template.indexOf('%', i + 1)
                if (end > i + 1) {
                    val key = template.substring(i + 1, end).lowercase()
                    val value = resolvers[key]?.resolve(ctx)
                    if (value != null) {
                        out.append(value)
                        i = end + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    fun resolve(lines: List<String>, ctx: Context): List<String> = lines.map { resolve(it, ctx) }

    // ---------- 内置变量 ----------

    /** 由 [KokuuCore] 在 mod 构造期调用。 */
    fun bootstrap() {
        register("online") { ctx -> ctx.server.playerCount.toString() }
        register("max") { ctx -> ctx.server.playerList.maxPlayers.toString() }
        register("tps") { ctx -> org.kokuu.core.util.Placeholders.tps(ctx.server) }
        register("time") { _ -> java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()) }
        register("date") { _ -> java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()) }
        register("uptime") { ctx ->
            val started = ctx.server.tickCount / 20L
            org.kokuu.core.util.Msg.duration(started)
        }

        register("player") { ctx -> ctx.player?.gameProfile?.name }
        register("uuid") { ctx -> ctx.player?.uuid?.toString() }
        register("world") { ctx -> ctx.player?.level()?.dimension()?.location()?.path }
        register("ping") { ctx -> ctx.player?.connection?.latency()?.toString() }
        register("health") { ctx -> ctx.player?.health?.toInt()?.toString() }
        register("max_health") { ctx -> ctx.player?.maxHealth?.toInt()?.toString() }
        register("food") { ctx -> ctx.player?.foodData?.foodLevel?.toString() }
        register("level") { ctx -> ctx.player?.experienceLevel?.toString() }
        register("x") { ctx -> ctx.player?.blockX?.toString() }
        register("y") { ctx -> ctx.player?.blockY?.toString() }
        register("z") { ctx -> ctx.player?.blockZ?.toString() }
        register("group") { ctx -> ctx.player?.let { org.kokuu.core.api.KokuuServices.permission().groupOf(it) } }
        register("prefix") { ctx -> ctx.player?.let { org.kokuu.core.api.KokuuServices.permission().prefixOf(it) } }
        // 注意：这里**没有** %title% —— 它由 kokuu_title 插件自己注册（见 KokuuTitle）。
        // 内核不认识「称号」这个概念，就像它不认识「家」和「权限组」一样。
        register("playtime") { ctx -> ctx.player?.let { org.kokuu.core.util.Placeholders.playtime(it) } }
        register("homes") { ctx ->
            ctx.player?.let {
                org.kokuu.core.data.DataStore.get()?.profile(it.uuid, null)?.homes?.size?.toString()
            }
        }

        KokuuCore.LOG.info("已注册 {} 个内置变量", resolvers.size)
    }
}
