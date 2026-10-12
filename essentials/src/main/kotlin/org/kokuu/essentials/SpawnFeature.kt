package org.kokuu.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import org.kokuu.core.api.ApiJson
import org.kokuu.core.api.ApiRouter
import org.kokuu.core.api.Feature
import org.kokuu.core.data.DataStore
import org.kokuu.core.data.StoredLocation
import org.kokuu.core.api.KokuuServices
import org.kokuu.core.teleport.Teleports
import org.kokuu.core.util.Msg

/** 出生点：/spawn /setspawn */
class SpawnFeature : Feature() {

    companion object {
        /** 出生点解析：优先自定义，其次原版主世界出生点。 */
        fun spawnLocation(server: MinecraftServer): StoredLocation {
            DataStore.get()?.global?.spawn?.let { return it }
            val overworld = server.overworld()
            val pos = overworld.sharedSpawnPos
            return StoredLocation.of(overworld, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        }
    }

    override val id = "spawn"
    override val displayName = "出生点"
    override val description = "全服出生点与 /spawn 回城"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(Commands.literal("spawn").executes { spawn(it.source) })
        dispatcher.register(
            Commands.literal("setspawn")
                .requires { KokuuServices.permission().admin(it) }
                .executes { setSpawn(it.source) }
        )
    }

    @Throws(CommandSyntaxException::class)
    private fun spawn(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val target = spawnLocation(source.server)
        if (!Teleports.to(player, target)) {
            Msg.err(player, "传送失败：目标维度 ${target.dim} 不存在")
            return 0
        }
        Msg.ok(player, "已回到出生点")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun setSpawn(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val location = StoredLocation.of(player)
        DataStore.get()?.global?.spawn = location
        DataStore.get()?.saveGlobal()

        // 同步到原版出生点，保证新玩家首次进服也落在同一位置
        location.resolve(source.server)?.setDefaultSpawnPos(
            BlockPos(
                Math.floor(location.x).toInt(),
                Math.floor(location.y).toInt(),
                Math.floor(location.z).toInt()
            ),
            location.yaw
        )
        Msg.ok(source, "已设置出生点 → ${location.display()}")
        return 1
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/spawn") { _ ->
            val location = spawnLocation(org.kokuu.core.api.KokuuServer.require())
            ApiJson.obj(
                "ok", true,
                "spawn", ApiJson.obj(
                    "dim", location.dim,
                    "x", location.x, "y", location.y, "z", location.z,
                    "yaw", location.yaw, "pitch", location.pitch,
                    "display", location.display()
                )
            )
        }
    }
}
