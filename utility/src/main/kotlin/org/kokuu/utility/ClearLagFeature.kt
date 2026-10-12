package org.kokuu.utility

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.AbstractArrow
import net.minecraft.world.entity.projectile.Projectile
import org.kokuu.core.KokuuCore
import org.kokuu.core.api.ApiJson
import org.kokuu.core.api.ApiRouter
import org.kokuu.core.api.KokuuServer
import org.kokuu.core.api.Feature
import org.kokuu.core.entity.EntityTools
import org.kokuu.core.api.KokuuServices
import org.kokuu.core.util.Msg
import org.kokuu.core.util.Text
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 清扫（清道夫）—— 服务器性能优化模块。
 *
 * 掉落物、经验球、箭矢是长期运行服务器里最大的实体负担：它们数量无上限、
 * 又几乎不会被 AI 剔除。这里按固定间隔批量 [EntityTools.remove] 掉。
 *
 * 与其它模块的关系：
 *  - 实体筛选全部走 [EntityTools]，不自己写遍历（后续要做实体统计/刷怪控制也复用它）；
 *  - 倒计时提醒复用 [Msg] 与 [Text]，全服广播走 `PlayerList.broadcastSystemMessage`。
 *
 * 安全阀：
 *  - 刚扔出 / 刚由方块产出的掉落物有拾取延迟，[EntityTools.isProtected] 会放过它们；
 *  - 带自定义名称的物品（玩家命名的装备、商店货币）默认保留；
 *  - 无敌实体与坐骑上的实体不动。
 */
class ClearLagFeature : Feature() {

    private companion object {
        /** 间隔下限，防止有人在配置里写 1 秒把服务器刷爆。 */
        const val MIN_INTERVAL_SECONDS = 30
    }

    override val id = "clearlag"
    override val displayName = "清扫（清道夫）"
    override val description = "定期清理掉落物、经验球、箭矢与投掷物，降低实体开销"

    /** 尊重早期的 `clearLagEnabled` 开关。 */
    override val defaultEnabled: Boolean get() = KokuuCore.config.clearLagEnabled

    private var seconds = 0
    private var lastRemoved = 0
    private var lastRunAt = 0L
    private var totalRemoved = 0L

    // ---------- 生命周期 ----------

    override fun onServerStart(server: MinecraftServer) = reset()

    override fun onReload(server: MinecraftServer) = reset()

    private fun reset() {
        seconds = 0
        lastRemoved = 0
    }

    override fun onSecond(server: MinecraftServer) {
        if (!enabled) {
            return
        }
        val cfg = KokuuCore.config
        val interval = cfg.clearLagIntervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
        seconds++

        if (seconds >= interval) {
            val removed = runClear(server)
            lastRemoved = removed
            lastRunAt = System.currentTimeMillis()
            totalRemoved += removed
            seconds = 0
            if (removed > 0 && cfg.clearLagAnnounce) {
                announce(server, "&7已清理 &a$removed &7个掉落物/经验球")
            }
            if (removed > 0) {
                KokuuCore.LOG.info("清扫完成，移除 {} 个实体", removed)
            }
            return
        }

        val until = interval - seconds
        if (until in warnPoints(interval, cfg.clearLagWarnSeconds)) {
            announce(server, "&e服务器将在 &c$until &e秒后清理掉落物")
        }
    }

    /** 提醒时间点：配置的提前量 + 最后 3 秒倒数（0 表示不提醒）。 */
    private fun warnPoints(interval: Int, configured: Int): Set<Int> {
        if (configured <= 0) return emptySet()
        return setOf(configured, 3, 2, 1).filter { it in 1 until interval }.toSet()
    }

    // ---------- 清扫 ----------

    /** 遍历所有维度做一次清扫，返回移除的实体总数。 */
    private fun runClear(server: MinecraftServer): Int {
        val cfg = KokuuCore.config
        var removed = 0
        server.allLevels.forEach { level ->
            val targets = EntityTools.allEntities(level).filter { entity ->
                shouldRemove(entity) && !EntityTools.isProtected(entity, cfg.clearLagKeepNamedItems)
            }
            if (targets.isNotEmpty()) {
                removed += EntityTools.remove(targets)
            }
        }
        return removed
    }

    private fun shouldRemove(entity: Entity): Boolean {
        val cfg = KokuuCore.config
        return when (entity) {
            is ItemEntity -> cfg.clearLagRemoveItems
            is ExperienceOrb -> cfg.clearLagRemoveXpOrbs
            // 箭矢先判：AbstractArrow 本身也是 Projectile，顺序不能反
            is AbstractArrow -> cfg.clearLagRemoveArrows
            is Projectile -> cfg.clearLagRemoveProjectiles
            else -> false
        }
    }

    private fun announce(server: MinecraftServer, text: String) {
        runCatching {
            server.playerList.broadcastSystemMessage(Msg.prefix().append(Text.legacy(text)), false)
        }
    }

    private fun nextInSeconds(): Int {
        val interval = KokuuCore.config.clearLagIntervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
        return (interval - seconds).coerceAtLeast(0)
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("clearlag")
                .requires { KokuuServices.permission().admin(it) }
                .executes { runNow(it.source) }
                .then(Commands.literal("status").executes { status(it.source) })
        )
    }

    private fun runNow(source: CommandSourceStack): Int {
        val server = source.server
        val removed = runClear(server)
        lastRemoved = removed
        lastRunAt = System.currentTimeMillis()
        totalRemoved += removed
        seconds = 0
        Msg.ok(source, "清扫完成，移除 $removed 个实体")
        if (removed > 0 && KokuuCore.config.clearLagAnnounce) {
            announce(server, "&7管理员手动清扫，已清理 &a$removed &7个掉落物/经验球")
        }
        return removed
    }

    private fun status(source: CommandSourceStack): Int {
        val cfg = KokuuCore.config
        val counts = HashMap<String, Int>()
        source.server.allLevels.forEach { level ->
            EntityTools.countByCategory(level).forEach { (category, value) ->
                counts.merge(category, value, Int::plus)
            }
        }
        Msg.info(source, "===== 清扫状态 =====")
        Msg.info(source, "间隔 ${cfg.clearLagIntervalSeconds}s | 下次在 ${Msg.duration(nextInSeconds().toLong())}后")
        Msg.info(
            source,
            "清理项：掉落物=${cfg.clearLagRemoveItems} 经验球=${cfg.clearLagRemoveXpOrbs}" +
                " 箭矢=${cfg.clearLagRemoveArrows} 投掷物=${cfg.clearLagRemoveProjectiles}" +
                " 保留命名物品=${cfg.clearLagKeepNamedItems}"
        )
        Msg.info(source, "上次清理 $lastRemoved 个 | 累计 $totalRemoved 个")
        Msg.info(
            source,
            "当前实体：掉落物=${counts["item"] ?: 0} 经验球=${counts["xp_orb"] ?: 0}" +
                " 箭矢=${counts["arrow"] ?: 0} 投掷物=${counts["projectile"] ?: 0}" +
                " 怪物=${counts["mob"] ?: 0}"
        )
        if (lastRunAt > 0L) {
            Msg.info(source, "上次执行于 ${SimpleDateFormat("MM-dd HH:mm:ss").format(Date(lastRunAt))}")
        }
        return 1
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/clearlag") { _ ->
            val cfg = KokuuCore.config
            ApiJson.obj(
                "ok", true,
                "enabled", enabled,
                "intervalSeconds", cfg.clearLagIntervalSeconds,
                "warnSeconds", cfg.clearLagWarnSeconds,
                "nextInSeconds", nextInSeconds(),
                "lastRemoved", lastRemoved,
                "totalRemoved", totalRemoved,
                "lastRunAt", lastRunAt,
                "rules", ApiJson.obj(
                    "items", cfg.clearLagRemoveItems,
                    "xpOrbs", cfg.clearLagRemoveXpOrbs,
                    "arrows", cfg.clearLagRemoveArrows,
                    "projectiles", cfg.clearLagRemoveProjectiles,
                    "keepNamedItems", cfg.clearLagKeepNamedItems,
                    "announce", cfg.clearLagAnnounce
                )
            )
        }

        router.post("/api/clearlag/run") { _ ->
            val removed = KokuuServer.onServer { runClear(KokuuServer.require()) }
            lastRemoved = removed
            lastRunAt = System.currentTimeMillis()
            totalRemoved += removed
            ApiJson.obj("ok", true, "removed", removed, "message", "已清理 $removed 个实体")
        }
    }
}
