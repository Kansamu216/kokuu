package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.GameType
import org.voidkit.core.api.Feature
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Msg
import java.util.Locale

/**
 * 原版命令简写。
 *
 * | 简写 | 等价于 |
 * |---|---|
 * | `/day` `/night` `/noon` `/midnight` `/dawn` `/dusk` | `/time set …` |
 * | `/freeze` `/unfreeze` | 开关 `doDaylightCycle` |
 * | `/sun` `/rain` `/thunder` | `/weather …` |
 * | `/gr <规则> [值]` | `/gamerule …`（不带值 = 查当前值） |
 * | `/diff <级别>` | `/difficulty …`，接受 `0/1/2/3` 与原版名字 |
 * | `/gm <模式> [玩家]` | `/gamemode …`，接受 `0/1/2/3/s/c/a/sp` 与中文 |
 *
 * ## 为什么是「转发」而不是「重写」
 *
 * 时间 / 天气 / 难度 / 游戏模式这些逻辑原版已经做得完整 —— 含边界处理、多人同步、
 * 消息本地化（中文客户端会显示中文反馈）。重写一遍只会引入新 bug 并丢掉这些。
 * 所以这里只做**参数翻译 + 提权**，真正的执行交给原版。
 *
 * ## 为什么要提权
 *
 * 这些命令的准入由权限节点把关（例如 `voidkit.command.day`）—— 玩家可能是靠
 * 权限节点而非原版 OP 拿到命令的。转发给原版时他权限不够，会被**再拒一次**。
 * 所以转发前统一提到 4 级：准入已经检查过了，这道二次检查对我们没意义。
 *
 * ## 冲突处理（大型整合包的关键）
 *
 * 165 个 mod 的包里，`/day` 这种短名字很容易被别的 mod 抢注。Brigadier 遇到重复
 * 注册会抛异常并**打断整个注册流程** —— 连后面没冲突的命令一起完蛋。
 * 所以每条命令单独 `runCatching`，冲突时只跳过这一条并给出提示，其余照常。
 */
class CommandAliasFeature : Feature() {

    override val id = "command-alias"
    override val displayName = "原版命令简写"
    override val description = "时间、天气、游戏规则、难度、游戏模式的短别名"

    override val defaultEnabled: Boolean = true

    /** 注册失败的简写，`/voidkit status` 会把它列出来。 */
    val failed = linkedMapOf<String, String>()

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        failed.clear()

        // ---------- 时间 ----------
        alias(dispatcher, "day", "time set day")
        alias(dispatcher, "night", "time set night")
        alias(dispatcher, "noon", "time set noon")
        alias(dispatcher, "midnight", "time set midnight")
        alias(dispatcher, "dawn", "time set sunrise")
        alias(dispatcher, "dusk", "time set sunset")
        alias(dispatcher, "freeze", "gamerule doDaylightCycle false")
        alias(dispatcher, "unfreeze", "gamerule doDaylightCycle true")

        // ---------- 天气 ----------
        alias(dispatcher, "sun", "weather clear")
        alias(dispatcher, "rain", "weather rain")
        alias(dispatcher, "thunder", "weather thunder")

        // ---------- 游戏规则 ----------
        guarded("gr") {
            dispatcher.register(
                Commands.literal("gr")
                    .requires { VoidkitServices.permission().has(it, "voidkit.command.gamerule") }
                    .then(
                        Commands.argument("rule", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(COMMON_RULES, builder)
                                builder.buildFuture()
                            }
                            // 不带值 = 查询（原版 `/gamerule <规则>` 会回报当前值）
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
        }

        // ---------- 难度 ----------
        guarded("diff") {
            dispatcher.register(
                Commands.literal("diff")
                    .requires { VoidkitServices.permission().has(it, "voidkit.command.difficulty") }
                    .then(
                        Commands.argument("level", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(
                                    listOf("0", "1", "2", "3", "peaceful", "easy", "normal", "hard"),
                                    builder
                                )
                                builder.buildFuture()
                            }
                            .executes {
                                runAsOp(
                                    it.source,
                                    "difficulty ${difficulty(StringArgumentType.getString(it, "level"))}"
                                )
                            }
                    )
            )
        }

        // ---------- 游戏模式 ----------
        guarded("gm") {
            dispatcher.register(
                Commands.literal("gm")
                    .requires { VoidkitServices.permission().has(it, "voidkit.command.gamemode") }
                    .then(
                        Commands.argument("mode", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(
                                    listOf("0", "1", "2", "3", "s", "c", "a", "sp", "survival", "creative", "adventure", "spectator"),
                                    builder
                                )
                                builder.buildFuture()
                            }
                            .executes { gm(it.source.playerOrException, it.source.playerOrException, StringArgumentType.getString(it, "mode")) }
                            .then(
                                Commands.argument("target", EntityArgument.player())
                                    .requires { VoidkitServices.permission().admin(it) }
                                    .executes {
                                        gm(
                                            it.source.playerOrException,
                                            EntityArgument.getPlayer(it, "target"),
                                            StringArgumentType.getString(it, "mode")
                                        )
                                    }
                            )
                    )
            )
        }
    }

    // ---------- 内部 ----------

    /** 注册一个「无参数、直接转发到原版」的简写。 */
    private fun alias(dispatcher: CommandDispatcher<CommandSourceStack>, name: String, command: String) {
        if (CommandsStore.config.isDisabled(name)) return
        guarded(name) {
            dispatcher.register(
                Commands.literal(name)
                    .requires { VoidkitServices.permission().has(it, "voidkit.command.$name") }
                    .executes { runAsOp(it.source, command) }
            )
        }
    }

    /**
     * 单条命令的注册保护。
     *
     * 注册失败（多半是名字被别的 mod 占了）只记下来并跳过，不往上抛 ——
     * 否则一条命令冲突会把后面所有命令的注册一起打断。
     */
    private inline fun guarded(name: String, register: () -> Unit) {
        runCatching(register).onFailure {
            failed[name] = it.message ?: it.javaClass.simpleName
            org.voidkit.core.VoidkitCore.LOG.warn(
                "命令 /{} 注册失败（多半与其它 mod 冲突），已跳过：{}", name, it.message
            )
        }
    }

    /** 以 OP 权限执行原版命令，见类注释里「为什么要提权」。 */
    private fun runAsOp(source: CommandSourceStack, command: String): Int {
        runCatching {
            source.server.commands.performPrefixedCommand(source.withPermission(4), command)
        }.onFailure {
            Msg.err(source, "执行失败：${it.message}")
            return 0
        }
        return 1
    }

    /** 切换游戏模式。自己给自己切不需要管理员，切别人需要。 */
    private fun gm(actor: ServerPlayer, target: ServerPlayer, raw: String): Int {
        val mode = parseGameMode(raw)
        if (mode == null) {
            Msg.err(actor, "未知的游戏模式：$raw（可用 0/1/2/3、s/c/a/sp，或 survival/creative/adventure/spectator）")
            return 0
        }
        target.setGameMode(mode)
        if (target.uuid == actor.uuid) {
            Msg.ok(actor, "游戏模式已切换为 ${mode.name.lowercase(Locale.ROOT)}")
        } else {
            Msg.ok(actor, "已把 ${target.gameProfile.name} 设为 ${mode.name.lowercase(Locale.ROOT)}")
            Msg.info(target, "${actor.gameProfile.name} 把你的游戏模式设为 ${mode.name.lowercase(Locale.ROOT)}")
        }
        return 1
    }

    /** 难度同时接受数字与原版名字。 */
    private fun difficulty(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
        "0" -> "peaceful"
        "1" -> "easy"
        "2" -> "normal"
        "3" -> "hard"
        else -> raw
    }

    /** 游戏模式接受数字、单字母、全名，以及中文。 */
    private fun parseGameMode(raw: String): GameType? = when (raw.lowercase(Locale.ROOT)) {
        "0", "s", "survival", "生存" -> GameType.SURVIVAL
        "1", "c", "creative", "创造" -> GameType.CREATIVE
        "2", "a", "adventure", "冒险" -> GameType.ADVENTURE
        "3", "sp", "spectator", "旁观" -> GameType.SPECTATOR
        else -> null
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
