package org.voidkit.utility

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.component.DataComponents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.ChunkPos
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.VoidkitServer
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Broadcast
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.util.concurrent.ConcurrentHashMap

/** 一个区块的实体统计。 */
class ChunkStat(
    val dim: String,
    val x: Int,
    val z: Int,
    var total: Int,
    var items: Int,
) {
    /** 给人看的坐标（区块中心的世界坐标）。 */
    val centerX: Int get() = x * 16 + 8
    val centerZ: Int get() = z * 16 + 8
}

/**
 * 性能监测。
 *
 * ## 只观测为主
 *
 * 默认行为是：采样 → 记录 → 超阈值时提醒（默认只写控制台，不打扰玩家）。
 * 世界**一个方块都不会动**。
 *
 * ## 数据来源
 *
 * 全部来自原版已维护的计数器，不做任何“为了统计而跑一遍全服”的事：
 *  - MSPT 用 [MinecraftServer.getAverageTickTimeNanos]（原版自己算好的滑动平均）
 *  - TPS 由 MSPT 反推（`1000 / mspt`）而不是去数 tick —— 数 tick 得到的
 *    "过去一分钟平均”对突发卡顿反应太慢，看不到正在发生的问题
 *  - 实体分布走 [ServerLevel.getAllEntities]（已加载实体），**绝不会为了统计加载区块**
 *
 * ## 温和干预（默认关）
 *
 * 打开后，对实体数超阈值的区块卸载其中的**掉落物**。三条硬边界：
 *
 *  1. 只删 [ItemEntity]，**永远不碰方块、机器、生物**；
 *  2. 只在实体**已加载**的情况下操作，不主动加载区块；
 *  3. 每个区块有冷却、单次有上限，不会连续反复卸载同一区块。
 *
 * 也就是说，即使配置写错，最坏情况也只是“某些掉落物被清掉了”，不会破坏存档结构。
 */
class PerfFeature : Feature() {

    override val id = "perf"
    override val displayName = "性能监测"
    override val description = "TPS / MSPT 采样、区块实体热点排名、可选温和干预"

    override val defaultEnabled: Boolean = true

    // ---------- 采样状态 ----------

    @Volatile
    var mspt: Double = 0.0
        private set

    @Volatile
    var tps: Double = 20.0
        private set

    @Volatile
    var entityTotal: Int = 0
        private set

    @Volatile
    var entityItems: Int = 0
        private set

    @Volatile
    var entityXpOrbs: Int = 0
        private set

    @Volatile
    var entityProjectiles: Int = 0
        private set

    @Volatile
    var entityMobs: Int = 0
        private set

    @Volatile
    var hotspots: List<ChunkStat> = emptyList()
        private set

    @Volatile
    var lastSampleAt: Long = 0L
        private set

    @Volatile
    var samples: Long = 0L
        private set

    /** 干预累计次数与被卸载的掉落物数。 */
    @Volatile
    var interventions: Long = 0L
        private set

    @Volatile
    var intervenedItems: Long = 0L
        private set

    private var seconds = 0

    /** 区块 -> 上次干预时间戳。 */
    private val interveneCooldown = ConcurrentHashMap<String, Long>()

    private var lastWarnAt = 0L

    /**
     * 干预冷却表的上限。
     *
     * 它是按"维度:区块X:区块Z"累积的 —— 长跑几个月下来能攒出几十万个键。
     * 超过上限就清一次（本来也只是防"反复卸载同一个区块"，丢历史没有副作用）。
     */
    private companion object {
        const val MAX_COOLDOWN_ENTRIES = 8192
    }

    // ---------- 生命周期 ----------

    override fun onServerStart(server: MinecraftServer) {
        PerfStore.load()
        seconds = 0
        interveneCooldown.clear()
        sample(server)
    }

    override fun onReload(server: MinecraftServer) {
        PerfStore.load()
        seconds = 0
    }

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) return
        val cfg = PerfStore.config
        seconds++
        if (seconds < cfg.sampleSeconds.coerceAtLeast(5)) return
        seconds = 0
        sample(server)
    }

    // ---------- 采样 ----------

    /** 采一次样：更新指标、算热点、必要时提醒与干预。 */
    fun sample(server: MinecraftServer) {
        val cfg = PerfStore.config

        // ---- 时间指标 ----
        val nanos = server.averageTickTimeNanos
        mspt = nanos / 1_000_000.0
        tps = if (mspt <= 0.0) 20.0 else minOf(20.0, 1000.0 / mspt)

        // ---- 实体分布 ----
        var total = 0
        var items = 0
        var xp = 0
        var projectiles = 0
        var mobs = 0
        val stats = HashMap<String, ChunkStat>()

        server.allLevels.forEach { level ->
            val dim = level.dimension().location().toString()
            level.allEntities.forEach { entity ->
                total++
                when {
                    entity is ItemEntity -> items++
                    entity is ExperienceOrb -> xp++
                    entity is Projectile -> projectiles++
                    entity is Mob -> mobs++
                }
                if (cfg.chunkTopEnabled) {
                    val pos = entity.chunkPosition()
                    val key = "$dim:${pos.x}:${pos.z}"
                    val stat = stats.getOrPut(key) { ChunkStat(dim, pos.x, pos.z, 0, 0) }
                    stat.total++
                    if (entity is ItemEntity) stat.items++
                }
            }
        }

        entityTotal = total
        entityItems = items
        entityXpOrbs = xp
        entityProjectiles = projectiles
        entityMobs = mobs
        lastSampleAt = System.currentTimeMillis()
        samples++

        hotspots = if (cfg.chunkTopEnabled) {
            stats.values.sortedByDescending { it.total }.take(cfg.chunkTopSize)
        } else {
            emptyList()
        }

        checkWarn(server, cfg)
        if (cfg.interveneEnabled) intervene(server, stats.values, cfg)
    }

    // ---------- 提醒 ----------

    private fun checkWarn(server: MinecraftServer, cfg: PerfConfig) {
        val slowTps = tps < cfg.warnTps
        val slowMspt = mspt > cfg.warnMspt
        if (!slowTps && !slowMspt) return

        val now = System.currentTimeMillis()
        if (now - lastWarnAt < cfg.warnCooldownSeconds * 1000L) return

        // 连续卡顿时至少留一点间隔，别把控制台刷满
        if (lastWarnAt > 0L && now - lastWarnAt < 30_000L) return
        lastWarnAt = now

        val reason = buildString {
            if (slowTps) append("TPS ${fmt(tps)} < ${cfg.warnTps}  ")
            if (slowMspt) append("MSPT ${fmt(mspt)}ms > ${cfg.warnMspt}ms")
        }
        val top = hotspots.firstOrNull()
        val detail = if (top != null && top.total >= cfg.entityWarnPerChunk) {
            "；热点区块 ${top.dim.substringAfterLast(':')} [${top.x},${top.z}] 有 ${top.total} 个实体（掉落物 ${top.items}）"
        } else {
            ""
        }

        VoidkitCore.LOG.warn("性能告警：{}{}", reason, detail)

        val channel = Broadcast.parse(cfg.warnChannel)
        if (channel == Broadcast.Channel.OFF) return

        val message = Msg.prefix().append(Text.legacy("&c性能告警：&f$reason$detail"))
        if (!cfg.warnToAdminsOnly) {
            Broadcast.send(server, message, channel)
            return
        }
        server.playerList.players.forEach { player ->
            if (!VoidkitServices.permission().has(player, "voidkit.perf.notify")) return@forEach
            runCatching {
                if (channel == Broadcast.Channel.CHAT || channel == Broadcast.Channel.BOTH) {
                    player.sendSystemMessage(message)
                }
                if (channel == Broadcast.Channel.ACTIONBAR || channel == Broadcast.Channel.BOTH) {
                    player.displayClientMessage(message, true)
                }
            }
        }
    }

    // ---------- 温和干预 ----------

    /**
     * 对超阈值区块卸载掉落物。
     *
     * 三条边界见类注释。这里额外做了两件事保证不卡主线程：
     *  - 单次总量上限（[PerfConfig.interveneMaxPerRun]）
     *  - 计算完一次性删，而不是边遍历边删（避免并发修改）
     */
    private fun intervene(server: MinecraftServer, stats: Collection<ChunkStat>, cfg: PerfConfig) {
        val now = System.currentTimeMillis()
        val over = stats.filter { it.total >= cfg.interveneThreshold }
        if (over.isEmpty()) return

        // 冷却表按区块键累积，长跑会一直涨。到上限就清空一次 ——
        // 它只是防"反复卸载同一个区块"，丢历史没有副作用。
        if (interveneCooldown.size > MAX_COOLDOWN_ENTRIES) {
            interveneCooldown.clear()
            VoidkitCore.LOG.debug("性能干预冷却表已清空（超过 {} 条）", MAX_COOLDOWN_ENTRIES)
        }

        var budget = cfg.interveneMaxPerRun
        over.forEach { stat ->
            if (budget <= 0) return@forEach

            val key = "${stat.dim}:${stat.x}:${stat.z}"
            val last = interveneCooldown[key] ?: 0L
            if (now - last < cfg.interveneCooldownSeconds * 1000L) return@forEach

            val level = server.allLevels.firstOrNull { it.dimension().location().toString() == stat.dim } ?: return@forEach
            val victims = collectItems(level, stat.x, stat.z, budget)
            if (victims.isEmpty()) return@forEach

            budget -= victims.size
            val removed = org.voidkit.core.entity.EntityTools.remove(victims)
            interveneCooldown[key] = now
            interventions++
            intervenedItems += removed

            VoidkitCore.LOG.info(
                "性能干预：区块 {} [{},{}] 实体 {} 个，卸载其中 {} 个掉落物",
                stat.dim.substringAfterLast(':'), stat.x, stat.z, stat.total, removed
            )
        }
    }

    /** 收集某区块内可安全删除的掉落物（受保护的不动）。 */
    private fun collectItems(level: ServerLevel, cx: Int, cz: Int, limit: Int): List<Entity> {
        val cfg = ClearLagStore.config
        val out = ArrayList<Entity>(minOf(limit, 256))
        level.allEntities.forEach { entity ->
            if (out.size >= limit) return@forEach
            if (entity !is ItemEntity) return@forEach
            val pos = entity.chunkPosition()
            if (pos.x != cx || pos.z != cz) return@forEach
            // 和清扫用同一套保护规则 —— 贵重物和玩家命名过的物品一律不动
            val id = TrashValue.idOf(entity.item)
            if (id in ClearLagStore.protectedSet) return@forEach
            if (cfg.keepNamedItems && entity.item.getComponents().get(DataComponents.CUSTOM_NAME) != null) return@forEach
            out += entity
        }
        return out
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("perf")
                .requires { VoidkitServices.permission().has(it, "voidkit.perf") }
                .executes { report(it.source, false) }
                .then(Commands.literal("chunks").executes { report(it.source, true) })
        )
    }

    private fun report(source: CommandSourceStack, chunks: Boolean): Int {
        // 管理员敲命令时先现采一次 —— 否则看到的是几十秒前的快照
        sample(source.server)
        val cfg = PerfStore.config

        // ⚠️ 这里用 Msg.text 而不是 Msg.info：info 走的是 Component.literal，
        //    不解析 & 颜色码，会把 "&a" 原样显示出来。text 走 Text.legacy。
        Msg.text(source, "&8===== 服务器性能 =====")
        Msg.text(
            source,
            "&7TPS ${paint(tps, 20.0, cfg.warnTps)}&7 | MSPT ${paint(mspt, 50.0, cfg.warnMspt)}&7ms" +
                " | 在线 &f${source.server.playerList.playerCount}"
        )
        Msg.text(
            source,
            "&7实体总数 &f$entityTotal &7（掉落物 &f$entityItems&7 / 经验球 &f$entityXpOrbs" +
                "&7 / 投掷物 &f$entityProjectiles&7 / 生物 &f$entityMobs&7）"
        )
        if (cfg.chunkTopEnabled && hotspots.isNotEmpty()) {
            Msg.text(source, "&8----- 实体最多的区块 -----")
            hotspots.forEachIndexed { index, stat ->
                val flag = if (stat.total >= cfg.entityWarnPerChunk) "&c⚠" else "&8·"
                Msg.text(
                    source,
                    " $flag &f${index + 1}. &b${stat.dim.substringAfterLast(':')} &7[${stat.x}, ${stat.z}]" +
                        " &8(&f${stat.centerX}&8, &f${stat.centerZ}&8) &7| &f${stat.total} &7个（掉落物 ${stat.items}）"
                )
            }
        }
        if (chunks) {
            Msg.text(source, "&8----- 干预设置 -----")
            Msg.text(
                source,
                "&7状态：${if (cfg.interveneEnabled) "&a开" else "&7关"}" +
                    " &7| 阈值 &f${cfg.interveneThreshold} &7| 冷却 &f${cfg.interveneCooldownSeconds}s" +
                    " &7| 单次上限 &f${cfg.interveneMaxPerRun}"
            )
            Msg.text(source, "&7累计干预 &f$interventions &7次，卸载 &f$intervenedItems &7个掉落物")
        }
        Msg.text(source, "&8采样 ${samples} 次 | 间隔 ${cfg.sampleSeconds}s | 上次 ${ago(lastSampleAt)}")
        return 1
    }

    /**
     * 给数值上色。
     *
     * @param best 满分值：TPS 是 20（越大越好），MSPT 传 50（越小越好）
     * @param threshold 告警阈值
     */
    private fun paint(value: Double, best: Double, threshold: Double): String {
        val higherIsBetter = best >= 20.0
        return when {
            higherIsBetter -> when {
                value >= threshold -> "&a${fmt(value)}"
                value >= threshold * 0.8 -> "&e${fmt(value)}"
                else -> "&c${fmt(value)}"
            }
            else -> when {
                value <= threshold -> "&a${fmt(value)}"
                value <= threshold * 1.2 -> "&e${fmt(value)}"
                else -> "&c${fmt(value)}"
            }
        }
    }

    private fun ago(at: Long): String {
        if (at <= 0L) return "还没采过"
        val seconds = (System.currentTimeMillis() - at) / 1000
        return if (seconds < 60) "${seconds}秒前" else "${seconds / 60}分钟前"
    }

    private fun fmt(value: Double): String = String.format(java.util.Locale.ROOT, "%.1f", value)

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/perf") { request ->
            if (request.queryBool("sample", false)) {
                VoidkitServer.onServer { sample(VoidkitServer.require()) }
            }
            val cfg = PerfStore.config
            ApiJson.obj(
                "ok", true,
                "tps", tps,
                "mspt", mspt,
                "players", VoidkitServer.get()?.playerList?.playerCount ?: 0,
                "entities", ApiJson.obj(
                    "total", entityTotal,
                    "items", entityItems,
                    "xpOrbs", entityXpOrbs,
                    "projectiles", entityProjectiles,
                    "mobs", entityMobs
                ),
                "thresholds", ApiJson.obj(
                    "tps", cfg.warnTps,
                    "mspt", cfg.warnMspt,
                    "entityPerChunk", cfg.entityWarnPerChunk
                ),
                "intervention", ApiJson.obj(
                    "enabled", cfg.interveneEnabled,
                    "threshold", cfg.interveneThreshold,
                    "runs", interventions,
                    "itemsRemoved", intervenedItems
                ),
                "samples", samples,
                "lastSampleAt", lastSampleAt,
                "chunks", hotspots.map {
                    ApiJson.obj(
                        "dim", it.dim,
                        "x", it.x, "z", it.z,
                        "centerX", it.centerX, "centerZ", it.centerZ,
                        "total", it.total,
                        "items", it.items,
                        "hot", it.total >= cfg.entityWarnPerChunk
                    )
                }
            )
        }
    }
}
