package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import org.voidkit.core.VoidkitCore
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.StoredLocation
import org.voidkit.core.teleport.Teleports
import org.voidkit.core.util.Msg

/** /back —— 回到死亡点或上一次传送前的位置。 */
class BackFeature : Feature() {

    override val id = "back"
    override val displayName = "返回"
    override val description = "/back 回到死亡地点或上一次传送前的位置"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val requires = { source: CommandSourceStack -> source.player != null }
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
    @SubscribeEvent
    fun onDeath(event: LivingDeathEvent) {
        val player = event.entity as? ServerPlayer ?: return
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
