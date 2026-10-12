package org.kokuu.utility

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import org.kokuu.core.api.Feature
import org.kokuu.core.api.KokuuServices
import org.kokuu.core.util.Msg

/**
 * 世界工具：把原版那几个又长又难记的命令包一层短别名。
 *
 * 原版要打 `/time set night`、`/weather thunder`、`/gamerule keepInventory true`，
 * 这里简化成：
 *
 * | 简写 | 等价于 |
 * |---|---|
 * | `/day` `/night` `/noon` `/midnight` | `/time set <对应时间>` |
 * | `/freeze` `/unfreeze` | 开关 `doDaylightCycle` |
 * | `/sun` `/rain` `/thunder` | `/weather …` |
 * | `/gr <规则> [值]` | `/gamerule …`（不带值就是查询当前值） |
 * | `/diff <级别>` | `/difficulty …`，且接受 `0/1/2/3` |
 *
 * ## 为什么是"转发"而不是重写
 *
 * 时间/天气/难度这些逻辑原版已经做得很完整（含边界处理、多人同步、消息本地化），
 * 重写一遍只会引入新 bug。所以这里只做**参数翻译 + 权限提升**，真正的执行交给原版。
 *
 * ## 为什么要提权
 *
 * 我们的 `requires` 认 `kokuucore.admin` 权限节点 —— 可能有玩家是靠权限节点（而非原版 OP）
 * 拿到这个命令的，转发给原版时他权限不够会被再拒一次。所以转发前统一提到 4 级。
 * 权限本身已经由 [KokuuServices.permission().admin] 把关，提权只影响"原版那道二次检查"。
 */
class WorldToolsFeature : Feature() {

    override val id = "world-tools"
    override val displayName = "世界工具"
    override val description = "时间、天气、游戏规则、难度的简写命令"

    override val defaultEnabled: Boolean = true

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // ---------- 时间 ----------
        alias(dispatcher, "day", "time set day")
        alias(dispatcher, "night", "time set night")
        alias(dispatcher, "noon", "time set noon")
        alias(dispatcher, "midnight", "time set midnight")
        alias(dispatcher, "freeze", "gamerule doDaylightCycle false")
        alias(dispatcher, "unfreeze", "gamerule doDaylightCycle true")

        // ---------- 天气 ----------
        alias(dispatcher, "sun", "weather clear")
        alias(dispatcher, "rain", "weather rain")
        alias(dispatcher, "thunder", "weather thunder")

        // ---------- 游戏规则 ----------
        dispatcher.register(
            Commands.literal("gr")
                .requires { KokuuServices.permission().admin(it) }
                .then(
                    Commands.argument("rule", StringArgumentType.word())
                        .suggests { _, builder ->
                            SharedSuggestionProvider.suggest(COMMON_RULES, builder)
                            builder.buildFuture()
                        }
                        // 不带值 = 查询（原版 /gamerule <规则> 会回报当前值）
                        .executes { runAsOp(it.source, "gamerule ${StringArgumentType.getString(it, "rule")}") }
                        .then(
                            Commands.argument("value", StringArgumentType.word())
                                .suggests { _, builder ->
                                    SharedSuggestionProvider.suggest(listOf("true", "false"), builder)
                                    builder.buildFuture()
                                }
                                .executes {
                                    runAsOp(
                                        it.source,
                                        "gamerule ${StringArgumentType.getString(it, "rule")} " +
                                            StringArgumentType.getString(it, "value")
                                    )
                                }
                        )
                )
        )

        // ---------- 难度 ----------
        dispatcher.register(
            Commands.literal("diff")
                .requires { KokuuServices.permission().admin(it) }
                .then(
                    Commands.argument("level", StringArgumentType.word())
                        .suggests { _, builder ->
                            SharedSuggestionProvider.suggest(
                                listOf("0", "1", "2", "3", "peaceful", "easy", "normal", "hard"),
                                builder
                            )
                            builder.buildFuture()
                        }
                        .executes { runAsOp(it.source, "difficulty ${difficulty(StringArgumentType.getString(it, "level"))}") }
                )
        )
    }

    // ---------- 内部 ----------

    /** 注册一个"无参数、直接转发到原版命令"的简写。 */
    private fun alias(dispatcher: CommandDispatcher<CommandSourceStack>, name: String, command: String) {
        dispatcher.register(
            Commands.literal(name)
                .requires { KokuuServices.permission().admin(it) }
                .executes { runAsOp(it.source, command) }
        )
    }

    /** 以 OP 权限执行原版命令，见类注释里"为什么要提权"。 */
    private fun runAsOp(source: CommandSourceStack, command: String): Int {
        runCatching {
            source.server.commands.performPrefixedCommand(source.withPermission(4), command)
        }.onFailure {
            Msg.err(source, "执行失败：${it.message}")
            return 0
        }
        return 1
    }

    /** 难度同时接受数字与原版名字。 */
    private fun difficulty(raw: String): String = when (raw.lowercase()) {
        "0" -> "peaceful"
        "1" -> "easy"
        "2" -> "normal"
        "3" -> "hard"
        else -> raw
    }

    private companion object {
        /** 常用游戏规则，给 `/gr` 做补全用。 */
        val COMMON_RULES = listOf(
            "keepInventory", "doDaylightCycle", "doWeatherCycle", "doMobSpawning",
            "mobGriefing", "doFireTick", "naturalRegeneration", "fallDamage",
            "pvp", "showDeathMessages", "randomTickSpeed", "spawnRadius",
            "doImmediateRespawn", "playersSleepingPercentage", "commandBlockOutput",
        )
    }
}
