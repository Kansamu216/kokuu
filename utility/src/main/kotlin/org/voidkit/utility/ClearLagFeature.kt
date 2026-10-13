package org.voidkit.utility

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.Projectile
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.api.VItems
import org.voidkit.core.api.VoidkitServer
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.entity.EntityTools
import org.voidkit.core.util.Broadcast
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 清扫（清道夫）+ 垃圾桶。
 *
 * ## 清扫做了什么
 *
 * 掉落物、经验球、箭矢是长期运行服务器最大的实体负担：数量无上限，又几乎不会被
 * AI 剔除。这里按固定间隔批量处理。
 *
 * ## 和「垃圾桶」的分工
 *
 * 清扫到的东西默认**不是删除，而是收进 [TrashBin]** —— 玩家可以在下一轮清扫前
 * 自己取回。这样既不会因为掉落物堆积拖垮服务器，也不会让玩家”眼睁睁看着东西没了
 * 又拿不回来”。
 *
 * 三类东西的处理：
 *
 * | 类别 | 处理 |
 * |---|---|
 * | 配置 `protectedItems` 里的贵重物 | **完全不动**（连垃圾桶都不进） |
 * | 有自定义名称的物品（`keepNamedItems`） | **完全不动** |
 * | 其它掉落物 | 收进垃圾桶，可点开取回 |
 * | 经验球 / 箭 / 投掷物 | 直接删除（不是“物品”，没法原样还给你） |
 *
 * ## 不刷屏
 *
 * 之前是「提前 15 秒提醒 + 最后 3 秒逐秒倒数」共 4 条，大服上很吵。现在：
 *  - 提前提醒**只发一次**（[ClearLagConfig.warnSeconds]）
 *  - 逐秒倒数默认**关闭**（[ClearLagConfig.countdownSeconds] = 0）
 *  - 播报通道可选（聊天框 / 动作栏 / 两者 / 关闭）
 *
 * ## 防雪崩
 *
 * 单轮处理实体数有上限（[ClearLagConfig.maxPerSweep]）。万一异常刷出几百万个实体，
 * 一次性删完会让主线程卡十几秒（客户端看就是“服务器卡死”）—— 现在分轮慢慢处理。
 */
class ClearLagFeature : Feature() {

    override val id = "clearlag"
    override val displayName = "清扫（清道夫）"
    override val description = "定期回收掉落物到垃圾桶，降低实体开销"

    override val defaultEnabled: Boolean = true

    /** 垃圾桶。跟着服务端生命周期走。 */
    val bin = TrashBin()

    private var seconds = 0

    /** 上一轮实际移除的实体数。 */
    var lastRemoved: Int = 0
        private set

    /** 上一轮因超出限额而被直接丢弃的数量（垃圾保留上限 + 格数淘汰）。 */
    var lastDropped: Long = 0
        private set

    var lastRunAt: Long = 0L
        private set

    var totalRemoved: Long = 0L
        private set

    // ---------- 生命周期 ----------

    override fun onServerStart(server: MinecraftServer) {
        ClearLagStore.load()
        seconds = 0
    }

    override fun onReload(server: MinecraftServer) {
        ClearLagStore.load()
        seconds = 0
    }

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) return
        val cfg = ClearLagStore.config
        val interval = cfg.intervalSeconds.coerceAtLeast(30)
        seconds++

        if (seconds >= interval) {
            sweep(server)
            seconds = 0
            return
        }

        val until = interval - seconds
        if (until <= 0) return
        val channel = Broadcast.parse(cfg.announceChannel)

        // 提前提醒：只发一次（until 恰好等于配置值的那一刻）
        if (cfg.warnSeconds > 0 && until == cfg.warnSeconds) {
            Broadcast.send(
                server,
                Msg.prefix().append(Text.legacy("&e服务器将在 &c$until &e秒后清理掉落物")),
                channel,
                Text.legacy("&e$until 秒后清理")
            )
        }

        // 逐秒倒数：默认关闭，开了才喊
        if (cfg.countdownSeconds > 0 && until in 1..cfg.countdownSeconds) {
            Broadcast.send(
                server,
                Msg.prefix().append(Text.legacy("&c$until")),
                channel,
                Text.legacy("&c$until")
            )
        }
    }

    // ---------- 清扫 ----------

    /** 一次完整清扫，返回移除的实体数。 */
    fun sweep(server: MinecraftServer): Int {
        val cfg = ClearLagStore.config

        // 上一轮的作废 —— 垃圾桶的语义就是”保留到下一次清扫前"
        if (cfg.trashEnabled && !bin.isEmpty()) bin.clear()

        var removed = 0
        var binned = 0L
        var dropped = 0L
        var budget = cfg.maxPerSweep

        server.allLevels.forEach { level ->
            if (budget <= 0) return@forEach
            val targets = EntityTools.allEntities(level).filter { entity ->
                // 预算扣减放在判定里，避免先把整张实体表过一遍（那本身就要遍历一次）
                budget-- > 0 && shouldRemove(entity, cfg) && !keepProtected(entity)
            }
            if (targets.isEmpty()) return@forEach

            if (!cfg.trashEnabled) {
                removed += EntityTools.remove(targets)
                return@forEach
            }

            // 收集进垃圾桶；非物品类（经验球/箭/投掷物）直接删
            val toDiscard = ArrayList<Entity>(targets.size)
            targets.forEach { entity ->
                if (entity is ItemEntity) {
                    val stack = entity.item
                    val accepted = bin.add(stack, stack.count.toLong(), cfg)
                    dropped += stack.count - accepted
                    binned += accepted
                }
                // 不管收没收下，实体本身都要消失，否则会一直躺在地上被下一轮重复处理
                toDiscard += entity
            }
            removed += EntityTools.remove(toDiscard)
        }

        val trimmed = if (cfg.trashEnabled) bin.trim(cfg.maxTypes) else TrimResult(0, 0)
        lastRemoved = removed
        lastDropped = dropped + trimmed.droppedItems
        lastRunAt = System.currentTimeMillis()
        totalRemoved += removed

        if (removed > 0) {
            VoidkitCore.LOG.info(
                "清扫完成：移除 {} 个实体，垃圾桶收到 {} 个物品（{} 种）{}",
                removed, binned, bin.size(),
                if (lastDropped > 0) "，另丢弃 $lastDropped 个（超出限额）" else ""
            )
            announceResult(server, removed, binned)
        }
        return removed
    }

    /** 清扫完成的全服播报。带一个可点击的垃圾桶入口。 */
    private fun announceResult(server: MinecraftServer, removed: Int, binned: Long) {
        val cfg = ClearLagStore.config
        val channel = Broadcast.parse(cfg.announceChannel)
        if (channel == Broadcast.Channel.OFF) return

        val plain = Component.empty().apply {
            append(Msg.prefix())
            append(Text.legacy("&7已清理 &a$removed &7个掉落物"))
            if (cfg.trashEnabled && binned > 0) {
                append(Text.legacy("（&b${bin.size()} &7种已存入垃圾桶）"))
            }
        }

        // 可点击版本 —— 聊天框里点一下就打开垃圾桶界面
        val clickable = if (cfg.trashEnabled && cfg.trashLinkInChat && binned > 0) {
            Component.empty().apply {
                append(plain)
                append(Text.legacy("  &8[&b&l打开垃圾桶&8](run:/trash \"&7点击查看并取回你的掉落物\")"))
            }
        } else {
            plain
        }

        // 动作栏不支持点击事件，所以另给一份纯文本
        Broadcast.send(server, clickable, channel, plain)
    }

    /** 经验球 / 箭 / 投掷物 —— 不是“物品”，垃圾桶装不了，只能直接删。 */
    private fun shouldRemove(entity: Entity, cfg: ClearLagConfig): Boolean = when (entity) {
        is ItemEntity -> cfg.removeItems
        is ExperienceOrb -> cfg.removeXpOrbs
        // 箭矢先判：AbstractArrow 本身也是 Projectile，顺序不能反
        is AbstractArrow -> cfg.removeArrows
        is Projectile -> cfg.removeProjectiles
        else -> false
    }

    /** 受保护的物品：完全不动。 */
    private fun keepProtected(entity: Entity): Boolean {
        if (entity !is ItemEntity) return false
        if (TrashValue.idOf(entity.item) in ClearLagStore.protectedSet) return true
        return ClearLagStore.config.keepNamedItems && VItems.hasCustomName(entity.item)
    }

    private fun nextInSeconds(): Int {
        val interval = ClearLagStore.config.intervalSeconds.coerceAtLeast(30)
        return (interval - seconds).coerceAtLeast(0)
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // /trash 给所有玩家用（取回自己被清掉的东西），登记为公开
        PublicNodes.register("voidkit.clearlag.trash")

        dispatcher.register(
            Commands.literal("clearlag")
                .requires { VoidkitServices.permission().has(it, "voidkit.clearlag") }
                .executes { runNow(it.source) }
                .then(Commands.literal("status").executes { status(it.source) })
                .then(Commands.literal("test").executes { test(it.source) })
        )

        // /trash 单独注册 —— 它**给所有玩家**用，而 /clearlag 是管理命令，
        // 两者权限模型完全不同，不该混在一个节点下。
        dispatcher.register(
            Commands.literal("trash")
                .requires { it.player != null && VoidkitServices.permission().has(it, "voidkit.clearlag.trash") }
                .executes { openTrash(it.source.playerOrException) }
        )
    }

    private fun openTrash(player: ServerPlayer): Int {
        if (bin.isEmpty()) {
            Msg.info(player, "垃圾桶现在是空的 —— 等下一轮清扫后再来看看")
            return 1
        }
        TrashMenu.open(player, bin)
        return 1
    }

    private fun runNow(source: CommandSourceStack): Int {
        val removed = sweep(source.server)
        Msg.ok(source, "清扫完成，移除 $removed 个实体")
        if (!bin.isEmpty()) {
            Msg.info(source, "垃圾桶现有 ${bin.size()} 种、共 ${bin.totalItems()} 个物品，用 /trash 查看")
        }
        return removed
    }

    /** 测试播报 —— 服主用它一眼确认「通道配置到底生效没有」。 */
    private fun test(source: CommandSourceStack): Int {
        val cfg = ClearLagStore.config
        val channel = Broadcast.parse(cfg.announceChannel)
        val sent = Broadcast.sendToPlayers(
            source.server,
            Msg.prefix().append(Text.legacy("&e这是一条测试播报（通道：&f${channel.name.lowercase()}&e）")),
            channel
        )
        Msg.info(source, "已按通道「${cfg.announceChannel}」发送测试消息给 $sent 名在线玩家")
        if (channel == Broadcast.Channel.OFF) {
            Msg.err(source, "当前通道是 off —— 清扫播报不会出现在游戏里，只会写控制台")
        } else if (sent == 0) {
            Msg.err(source, "没有在线玩家可发；进服后再试一次")
        }
        return 1
    }

    private fun status(source: CommandSourceStack): Int {
        val cfg = ClearLagStore.config
        val counts = HashMap<String, Int>()
        source.server.allLevels.forEach { level ->
            EntityTools.countByCategory(level).forEach { (category, value) -> counts.merge(category, value, Int::plus) }
        }
        Msg.info(source, "===== 清扫状态 =====")
        Msg.info(source, "间隔 ${cfg.intervalSeconds}s | 下次在 ${Msg.duration(nextInSeconds().toLong())}后")
        Msg.info(source, "播报通道：${cfg.announceChannel} | 提前提醒 ${cfg.warnSeconds}s | 倒数 ${cfg.countdownSeconds}s")
        Msg.info(
            source,
            "清理项：掉落物=${cfg.removeItems} 经验球=${cfg.removeXpOrbs}" +
                " 箭矢=${cfg.removeArrows} 投掷物=${cfg.removeProjectiles}"
        )
        Msg.info(
            source,
            "垃圾桶：${if (cfg.trashEnabled) "开" else "关"} | ${bin.size()} 种 / ${bin.totalItems()} 个" +
                " | 上限 ${cfg.maxTypes} 种，垃圾每类留 ${cfg.junkKeepLimit}"
        )
        Msg.info(source, "上一轮移除 $lastRemoved 个（另丢弃 $lastDropped 个）| 累计 $totalRemoved 个")
        Msg.info(
            source,
            "当前实体：掉落物=${counts["item"] ?: 0} 经验球=${counts["xp_orb"] ?: 0}" +
                " 箭矢=${counts["arrow"] ?: 0} 投掷物=${counts["projectile"] ?: 0} 怪物=${counts["mob"] ?: 0}"
        )
        if (lastRunAt > 0L) {
            Msg.info(source, "上次执行于 ${SimpleDateFormat("MM-dd HH:mm:ss").format(Date(lastRunAt))}")
        }
        return 1
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/clearlag") { _ ->
            val cfg = ClearLagStore.config
            ApiJson.obj(
                "ok", true,
                "enabled", enabled,
                "intervalSeconds", cfg.intervalSeconds,
                "warnSeconds", cfg.warnSeconds,
                "countdownSeconds", cfg.countdownSeconds,
                "announceChannel", cfg.announceChannel,
                "nextInSeconds", nextInSeconds(),
                "lastRemoved", lastRemoved,
                "lastDropped", lastDropped,
                "totalRemoved", totalRemoved,
                "lastRunAt", lastRunAt,
                "trash", ApiJson.obj(
                    "enabled", cfg.trashEnabled,
                    "types", bin.size(),
                    "items", bin.totalItems(),
                    "maxTypes", cfg.maxTypes,
                    "junkKeepLimit", cfg.junkKeepLimit
                ),
                "rules", ApiJson.obj(
                    "items", cfg.removeItems,
                    "xpOrbs", cfg.removeXpOrbs,
                    "arrows", cfg.removeArrows,
                    "projectiles", cfg.removeProjectiles,
                    "keepNamedItems", cfg.keepNamedItems,
                    "protectedItems", ApiJson.arr(cfg.protectedItems),
                    "trashItems", ApiJson.arr(cfg.trashItems)
                )
            )
        }

        /** 垃圾桶内容 —— 面板上直接看，不用进服。 */
        router.get("/api/clearlag/trash") { _ ->
            ApiJson.obj(
                "ok", true,
                "types", bin.size(),
                "items", bin.totalItems(),
                "entries", bin.snapshot().map { entry ->
                    ApiJson.obj(
                        "id", TrashValue.idOf(entry.sample),
                        "name", entry.sample.hoverName.string,
                        "count", entry.count,
                        "score", entry.score,
                        "valuable", entry.valuable
                    )
                }
            )
        }

        router.post("/api/clearlag/run") { _ ->
            val removed = VoidkitServer.onServer { sweep(VoidkitServer.require()) }
            ApiJson.obj(
                "ok", true,
                "removed", removed,
                "trashTypes", bin.size(),
                "trashItems", bin.totalItems(),
                "message", "已清理 $removed 个实体"
            )
        }

        router.post("/api/clearlag/trash/clear") { _ ->
            val before = bin.totalItems()
            VoidkitServer.onServer { bin.clear() }
            ApiJson.obj("ok", true, "message", "已清空垃圾桶（丢弃 $before 个物品）")
        }
    }
}
