package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.api.PlayerViews
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.data.DataStore
import org.voidkit.core.api.CommandPerms
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Placeholders
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 信息查询：/who /seen /playtime /whois /tps
 *
 * ## 权限
 *
 * | 命令 | 节点 | 默认 |
 * |---|---|---|
 * | `/who` `/seen` `/playtime` `/tps` | `voidkit.command.info` | **公开** |
 * | `/whois <玩家>` | `voidkit.command.whois` | 仅管理（**会打印该玩家的 IP 记录**） |
 *
 * ⚠️ 这里**刻意不注册 `/list`** —— 原版已经有这个命令了。重定义不仅会抛异常，
 * 还会把本该人人可用的原版 `/list` 变成需要权限。要看在线列表用 `/who`（本套件）
 * 或原版 `/list`。
 */
class InfoFeature : Feature() {

    companion object {
        /** 玩家日常查询命令的节点（公开）。 */
        const val NODE = "voidkit.command.info"

        /** 含敏感信息（IP）的查询命令节点（不公开）。 */
        const val WHOIS_NODE = "voidkit.command.whois"
    }

    override val id = "info"
    override val displayName = "信息查询"
    override val description = "在线列表、最后在线时间、游戏时长、玩家详情、TPS"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        PublicNodes.register(NODE)

        // 每条命令**各自容错**：内核只按「模块」容错，一旦这里抛异常，
        // 本模块后面几条命令就全都注册不上了（整合包里同名命令很多，很容易踩）。
        registerCommand("who") {
            dispatcher.register(
                Commands.literal("who")
                    .requires { VoidkitServices.permission().has(it, NODE) }
                    .executes { who(it.source) }
            )
        }

        registerCommand("seen") {
            dispatcher.register(
                Commands.literal("seen")
                    .requires { VoidkitServices.permission().has(it, NODE) }
                    .then(
                        Commands.argument("name", StringArgumentType.word())
                            .executes { seen(it.source, StringArgumentType.getString(it, "name")) }
                    )
            )
        }

        registerCommand("playtime") {
            dispatcher.register(
                Commands.literal("playtime")
                    .requires { it.player != null && VoidkitServices.permission().has(it, NODE) }
                    .executes { playtime(it.source, it.source.playerOrException) }
                    .then(
                        Commands.argument("target", EntityArgument.player())
                            .executes { playtime(it.source, EntityArgument.getPlayer(it, "target")) }
                    )
            )
        }

        registerCommand("tps") {
            dispatcher.register(
                Commands.literal("tps")
                    .requires { VoidkitServices.permission().has(it, NODE) }
                    .executes { tps(it.source) }
            )
        }

        registerCommand("whois") {
            dispatcher.register(
                Commands.literal("whois")
                    .requires { CommandPerms.admin(it, WHOIS_NODE) }
                    .then(
                        Commands.argument("target", EntityArgument.player())
                            .executes { whois(it.source, EntityArgument.getPlayer(it, "target")) }
                    )
            )
        }
    }

    // ---------- 实现 ----------

    private fun who(source: CommandSourceStack): Int {
        val players = source.server.playerList.players
        if (players.isEmpty()) {
            Msg.info(source, "当前没有玩家在线")
            return 0
        }
        val names = players.joinToString(", ") { it.gameProfile.name }
        Msg.info(source, "在线 ${players.size}/${source.server.playerList.maxPlayers}：$names")
        return players.size
    }

    private fun seen(source: CommandSourceStack, name: String): Int {
        val profile = DataStore.get()?.byName(name)
        if (profile == null) {
            Msg.err(source, "找不到玩家 $name")
            return 0
        }
        if (profile.online) {
            Msg.info(source, "${profile.lastName} 当前在线")
        } else {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(profile.lastSeen))
            val ago = Msg.duration((System.currentTimeMillis() - profile.lastSeen) / 1000)
            Msg.info(source, "${profile.lastName} 最后在线于 $date（${ago}前）")
        }
        return 1
    }

    private fun playtime(source: CommandSourceStack, target: ServerPlayer): Int {
        Msg.info(
            source,
            "${target.gameProfile.name} 累计在线 ${Placeholders.playtime(target)}" +
                "（第 ${DataStore.get()?.profile(target.uuid, null)?.joinCount ?: 0} 次登录）"
        )
        return 1
    }

    private fun whois(source: CommandSourceStack, target: ServerPlayer): Int {
        val profile = DataStore.get()?.profile(target.uuid, target.gameProfile.name) ?: return 0
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm")
        Msg.info(source, "===== ${target.gameProfile.name} =====")
        Msg.info(source, "UUID: ${profile.uuid}")
        Msg.info(source, "首次进入: ${date.format(Date(profile.firstJoin))}")
        Msg.info(source, "登录次数: ${profile.joinCount}")
        Msg.info(source, "权限组: ${profile.group}")
        Msg.info(source, "当前 IP: ${profile.lastIp.ifBlank { "未知" }}")
        Msg.info(source, "历史 IP 数: ${profile.ips.size}")
        profile.ips.sortedByDescending { it.lastSeen }.take(10).forEach { record ->
            source.sendSuccess(
                {
                    Component.literal("  ${record.ip} ×${record.count}  ${date.format(Date(record.lastSeen))}")
                        .withStyle(ChatFormatting.GRAY)
                },
                false
            )
        }
        return 1
    }

    private fun tps(source: CommandSourceStack): Int {
        val server = source.server
        Msg.info(source, "TPS ${Placeholders.tps(server)} | 运行 ${Msg.duration(server.tickCount / 20L)}")
        return 1
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/info/player/{key}") { request ->
            val profile = PlayerViews.resolve(request.param("key"))
                ?: throw IllegalArgumentException("找不到玩家")
            ApiJson.obj("ok", true, "player", PlayerViews.detail(profile))
        }
    }
}
