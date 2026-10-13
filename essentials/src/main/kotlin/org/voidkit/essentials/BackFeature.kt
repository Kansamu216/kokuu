package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.StoredLocation
import org.voidkit.core.teleport.Teleports
import org.voidkit.core.util.Msg

/**
 * /back 与 /return —— 回到死亡点或上一次传送前的位置。
 *
 * 两者是**同一个功能**（`/return` 只是更好记的别名），所以共用节点
 * `voidkit.command.back`，并登记为公开。是否启用另有 `backEnabled` 配置。
 */
class BackFeature : Feature() {

    companion object {
        /** 玩家日常命令的节点（公开）。 */
        const val NODE = "voidkit.command.back"
    }

    override val id = "back"
    override val displayName = "返回"
    override val description = "/back 回到死亡地点或上一次传送前的位置"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        PublicNodes.register(NODE)
        val requires = { source: CommandSourceStack ->
            source.player != null && VoidkitServices.permission().has(source, NODE)
        }
        dispatcher.register(Commands.literal("back").requires(requires).executes { back(it.source) })
        dispatcher.register(Commands.literal("return").requires(requires).executes { back(it.source) })
    }

    @Throws(CommandSyntaxException::class)
    private fun back(source: CommandSourceStack): Int {
        val player = source.playerOrException
        if (!EssentialsStore.config.backEnabled) {
            Msg.err(player, "/back 已被服务器关闭")
            return 0
        }
        if (!Teleports.hasBack(player)) {
            Msg.err(player, "没有可返回的位置")
            return 0
        }
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name)
        val target = profile?.back
        if (!Teleports.back(player)) {
            Msg.err(player, "返回失败：目标维度 ${target?.dim ?: "?"} 不存在")
            return 0
        }
        Msg.ok(player, "已返回 ${target?.display() ?: ""}")
        return 1
    }

    /** 死亡时记录死亡点，/back 即可回到死亡处。 */
    override fun onPlayerDeath(e: org.voidkit.core.api.events.VPlayerDeath) {
        val player = e.player
        val store = DataStore.get() ?: return
        store.profile(player.uuid, player.gameProfile.name).back = StoredLocation.of(player)
        store.markDirty()

        player.sendSystemMessage(
            Msg.prefix().append(
                Component.literal("死亡点已记录，输入 /back 可返回").withStyle(ChatFormatting.GRAY)
            )
        )
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/players/{key}/back") { request ->
            val profile = org.voidkit.core.api.PlayerViews.resolve(request.param("key"))
                ?: throw IllegalArgumentException("找不到玩家")
            val location = profile.back
            ApiJson.obj(
                "ok", true,
                "hasBack", location != null,
                "location", location?.let {
                    ApiJson.obj("dim", it.dim, "x", it.x, "y", it.y, "z", it.z, "display", it.display())
                }
            )
        }
    }
}
