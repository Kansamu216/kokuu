package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.data.StoredLocation
import org.voidkit.core.teleport.Teleports
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 玩家互传：/tpa /tpahere /tpaccept /tpdeny /tpacancel
 *
 * 节点 `voidkit.command.tpa` 公开（玩家之间自己传，属日常功能）；
 * 服主可关闭整组或单独关闭某人。开关本身另有 `tpaEnabled` 配置。
 */
class TpaFeature : Feature() {

    companion object {
        /** 玩家日常命令的节点（公开）。 */
        const val NODE = "voidkit.command.tpa"
    }

    private data class Request(val from: UUID, val to: UUID, val here: Boolean, val expiresAt: Long)

    /** key = 被请求者 UUID。同一时刻每位玩家只保留一个待处理请求。 */
    private val pending = ConcurrentHashMap<UUID, Request>()

    override val id = "tpa"
    override val displayName = "玩家互传"
    override val description = "/tpa、/tpahere 请求传送，带超时和接受/拒绝"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        PublicNodes.register(NODE)
        val requires = { source: CommandSourceStack ->
            source.player != null && VoidkitServices.permission().has(source, NODE)
        }

        dispatcher.register(
            Commands.literal("tpa").requires(requires)
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { request(it.source, EntityArgument.getPlayer(it, "target"), false) }
                )
        )
        dispatcher.register(
            Commands.literal("tpahere").requires(requires)
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { request(it.source, EntityArgument.getPlayer(it, "target"), true) }
                )
        )
        dispatcher.register(Commands.literal("tpaccept").requires(requires).executes { accept(it.source) })
        dispatcher.register(Commands.literal("tpdeny").requires(requires).executes { deny(it.source) })
        dispatcher.register(Commands.literal("tpacancel").requires(requires).executes { cancel(it.source) })
    }

    // ---------- 实现 ----------

    @Throws(CommandSyntaxException::class)
    private fun request(source: CommandSourceStack, target: ServerPlayer, here: Boolean): Int {
        if (!EssentialsStore.config.tpaEnabled) {
            Msg.err(source, "传送请求功能已被服务器关闭")
            return 0
        }
        val from = source.playerOrException
        if (from.uuid == target.uuid) {
            Msg.err(from, "不能给自己发请求")
            return 0
        }
        val now = System.currentTimeMillis()
        val ttlSeconds = EssentialsStore.config.tpaTimeoutSeconds.coerceAtLeast(10)
        val ttl = ttlSeconds * 1000L

        val existing = pending[target.uuid]
        if (existing != null && existing.expiresAt > now) {
            Msg.err(from, "${target.gameProfile.name} 还有未处理的传送请求")
            return 0
        }
        pending[target.uuid] = Request(from.uuid, target.uuid, here, now + ttl)

        Msg.ok(from, "已向 ${target.gameProfile.name} 发送${if (here) "召唤" else "传送"}请求，等待回应…")
        // 用**可点击按钮**代替”请输入 /tpaccept"这种要记命令的提示。
        // 玩家名剥过颜色码，防止有人改名成 "&c系统" 之类冒充提示。
        target.sendSystemMessage(
            Text.legacy(
                "&8&m──────&r &e传送请求 &8&m──────\n" +
                    "  &f${Text.strip(from.gameProfile.name)} &7" +
                    (if (here) "请求你传送到他那里" else "请求传送到你这里") + "\n" +
                    "  &8[&a&l接受&8](run:/tpaccept \"&7点一下同意\")  " +
                    "&8[&c&l拒绝&8](run:/tpdeny \"&7点一下拒绝\")  " +
                    "&8&o（${ttlSeconds} 秒内有效）"
            )
        )
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun accept(source: CommandSourceStack): Int {
        val me = source.playerOrException
        val request = take(me.uuid)
        if (request == null) {
            Msg.err(me, "没有待处理的传送请求")
            return 0
        }
        val from = source.server.playerList.getPlayer(request.from)
        if (from == null) {
            Msg.err(me, "对方已离线，请求取消")
            return 0
        }
        val mover = if (request.here) from else me
        val anchor = if (request.here) me else from
        if (!Teleports.to(mover, StoredLocation.of(anchor))) {
            Msg.err(me, "传送失败：目标维度不可用")
            return 0
        }
        Msg.ok(me, "已接受，正在传送 ${mover.gameProfile.name}")
        Msg.ok(from, "${me.gameProfile.name} 接受了你的请求")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun deny(source: CommandSourceStack): Int {
        val me = source.playerOrException
        val request = take(me.uuid)
        if (request == null) {
            Msg.err(me, "没有待处理的传送请求")
            return 0
        }
        source.server.playerList.getPlayer(request.from)?.let {
            Msg.info(it, "${me.gameProfile.name} 拒绝了你的传送请求")
        }
        Msg.ok(me, "已拒绝该请求")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun cancel(source: CommandSourceStack): Int {
        val me = source.playerOrException
        if (pending.entries.removeIf { it.value.from == me.uuid }) {
            Msg.ok(me, "已撤回你发出的传送请求")
            return 1
        }
        Msg.info(me, "你没有发出的传送请求")
        return 0
    }

    private fun take(target: UUID): Request? {
        val request = pending.remove(target) ?: return null
        return if (request.expiresAt < System.currentTimeMillis()) null else request
    }

    override fun onSecond(server: MinecraftServer) {
        val now = System.currentTimeMillis()
        val expired = pending.filterValues { it.expiresAt < now }.keys.toList()
        expired.forEach { key ->
            val request = pending.remove(key) ?: return@forEach
            server.playerList.getPlayer(request.from)?.sendSystemMessage(
                Msg.prefix().append(Component.literal("传送请求已超时").withStyle(ChatFormatting.GRAY))
            )
        }
    }

    /**
     * 玩家退出时立刻作废与他相关的请求 —— 比等 60 秒超时清理快一拍，
     * 也避免对方接受一个「发起者已离线」的僵尸请求。
     */
    override fun onPlayerQuit(e: org.voidkit.core.api.events.VPlayerQuit) {
        val uuid = e.player.uuid
        pending.entries.removeIf { it.value.from == uuid || it.value.to == uuid }
    }

    override fun onServerStop(server: MinecraftServer) {
        pending.clear()
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/tpa/pending") { _ ->
            ApiJson.obj("ok", true, "count", pending.size)
        }
    }
}
