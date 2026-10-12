package org.kokuu.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.level.ServerPlayer
import org.kokuu.core.api.Feature
import org.kokuu.core.data.StoredLocation
import org.kokuu.core.api.KokuuServices
import org.kokuu.core.teleport.Teleports
import org.kokuu.core.util.Msg

/**
 * 管理传送：/tphere /tpall /tpo
 *
 * 刻意不重定义原版 `/tp`，避免与 vanilla 命令冲突；vanilla 的 `/tp` 在 OP 等级 2 下本来就能用。
 */
class TeleportFeature : Feature() {

    override val id = "teleport"
    override val displayName = "管理传送"
    override val description = "把玩家拉到自己身边 / 全服拉人 / 传送到指定玩家"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("tphere")
                .requires { KokuuServices.permission().admin(it) }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { tpHere(it.source.playerOrException, EntityArgument.getPlayer(it, "target")) }
                )
        )

        dispatcher.register(
            Commands.literal("tpall")
                .requires { KokuuServices.permission().admin(it) }
                .executes { tpAll(it.source.playerOrException) }
        )

        dispatcher.register(
            Commands.literal("tpo")
                .requires { KokuuServices.permission().admin(it) }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { tpo(it.source.playerOrException, EntityArgument.getPlayer(it, "target")) }
                )
        )
    }

    @Throws(CommandSyntaxException::class)
    private fun tpHere(actor: ServerPlayer, target: ServerPlayer): Int {
        if (target.uuid == actor.uuid) {
            Msg.err(actor, "不能把自己传送到自己身边")
            return 0
        }
        if (!Teleports.to(target, StoredLocation.of(actor))) {
            Msg.err(actor, "传送失败")
            return 0
        }
        Msg.ok(target, "你被传送到 ${actor.gameProfile.name} 身边")
        Msg.ok(actor, "已把 ${target.gameProfile.name} 传送到你身边")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun tpAll(actor: ServerPlayer): Int {
        var count = 0
        actor.server.playerList.players.forEach { player ->
            if (player.uuid == actor.uuid) return@forEach
            if (Teleports.to(player, StoredLocation.of(actor))) count++
        }
        Msg.ok(actor, "已把 $count 名玩家传送到你身边")
        return count
    }

    @Throws(CommandSyntaxException::class)
    private fun tpo(actor: ServerPlayer, target: ServerPlayer): Int {
        if (!Teleports.to(actor, StoredLocation.of(target))) {
            Msg.err(actor, "传送失败")
            return 0
        }
        Msg.ok(actor, "已传送到 ${target.gameProfile.name} 身边")
        return 1
    }
}
