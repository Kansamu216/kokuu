package org.kokuu.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import org.kokuu.core.api.PlayerViews
import org.kokuu.core.api.ApiJson
import org.kokuu.core.api.ApiRouter
import org.kokuu.core.api.Feature
import org.kokuu.core.data.DataStore
import org.kokuu.core.data.StoredLocation
import org.kokuu.core.api.KokuuServices
import org.kokuu.core.teleport.Teleports
import org.kokuu.core.util.Msg
import java.util.Locale

/** 家：/sethome /home /delhome /homes */
class HomeFeature : Feature() {

    companion object {
        const val DEFAULT_HOME = "home"
    }

    override val id = "homes"
    override val displayName = "家"
    override val description = "每个玩家可设置多个家，配额由分组权限决定"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("sethome")
                .requires { it.player != null }
                .executes { setHome(it.source, DEFAULT_HOME) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .executes { setHome(it.source, StringArgumentType.getString(it, "name")) }
                )
        )

        dispatcher.register(
            Commands.literal("home")
                .requires { it.player != null }
                .executes { goHome(it.source, DEFAULT_HOME) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests { ctx, builder -> SharedSuggestionProvider.suggest(homeNames(ctx.source), builder) }
                        .executes { goHome(it.source, StringArgumentType.getString(it, "name")) }
                )
        )

        dispatcher.register(
            Commands.literal("delhome")
                .requires { it.player != null }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests { ctx, builder -> SharedSuggestionProvider.suggest(homeNames(ctx.source), builder) }
                        .executes { deleteHome(it.source, StringArgumentType.getString(it, "name")) }
                )
        )

        dispatcher.register(
            Commands.literal("homes")
                .requires { it.player != null }
                .executes { listHomes(it.source) }
        )
    }

    // ---------- 命令实现 ----------

    @Throws(CommandSyntaxException::class)
    private fun setHome(source: CommandSourceStack, name: String): Int {
        val player = source.playerOrException
        val key = name.lowercase(Locale.ROOT)
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
        val homes = profile.homes ?: linkedMapOf<String, StoredLocation>().also { profile.homes = it }
        val existing = homes.containsKey(key)
        val limit = KokuuServices.permission().homeLimit(player)
        if (!existing && homes.size >= limit) {
            Msg.err(player, "家的数量已达上限（$limit 个），请先删除一个或用 /delhome")
            return 0
        }
        homes[key] = StoredLocation.of(player)
        DataStore.get()?.markDirty()
        DataStore.get()?.save(profile)
        Msg.ok(player, (if (existing) "已更新家 " else "已设置家 ") + key + " → " + homes[key]!!.display())
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun goHome(source: CommandSourceStack, name: String): Int {
        val player = source.playerOrException
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
        val target = profile.homes?.get(name.lowercase(Locale.ROOT))
        if (target == null) {
            Msg.err(player, "没有名为 $name 的家，用 /sethome $name 先设置一个")
            return 0
        }
        if (!Teleports.to(player, target)) {
            Msg.err(player, "传送失败：目标维度 ${target.dim} 不存在")
            return 0
        }
        Msg.ok(player, "已回到 $name")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun deleteHome(source: CommandSourceStack, name: String): Int {
        val player = source.playerOrException
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
        val key = name.lowercase(Locale.ROOT)
        if (profile.homes?.remove(key) == null) {
            Msg.err(player, "没有名为 $name 的家")
            return 0
        }
        DataStore.get()?.markDirty()
        DataStore.get()?.save(profile)
        Msg.ok(player, "已删除家 $key")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun listHomes(source: CommandSourceStack): Int {
        val player = source.playerOrException
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
        val homes = profile.homes
        if (homes.isNullOrEmpty()) {
            Msg.info(player, "你还没有设置任何家，用 /sethome [名称] 创建")
            return 0
        }
        Msg.info(player, "你的家（${homes.size}/${KokuuServices.permission().homeLimit(player)}）：")
        homes.forEach { (key, location) ->
            player.sendSystemMessage(Msg.colored("  $key - ${location.display()}", ChatFormatting.GRAY))
        }
        return homes.size
    }

    private fun homeNames(source: CommandSourceStack): List<String> {
        val player = source.player ?: return emptyList()
        return DataStore.get()?.profile(player.uuid, null)?.homes?.keys?.toList() ?: emptyList()
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/players/{key}/homes") { request ->
            val profile = PlayerViews.resolve(request.param("key"))
                ?: throw IllegalArgumentException("找不到玩家: ${request.param("key")}")
            val homes = profile.homes.orEmpty().map { (name, location) ->
                ApiJson.obj(
                    "name", name, "dim", location.dim,
                    "x", location.x, "y", location.y, "z", location.z,
                    "display", location.display()
                )
            }
            ApiJson.obj("ok", true, "player", profile.lastName, "homes", homes)
        }

        router.post("/api/players/{key}/homes/{name}/delete") { request ->
            val profile = PlayerViews.resolve(request.param("key"))
                ?: throw IllegalArgumentException("找不到玩家")
            val name = request.param("name")!!.lowercase(Locale.ROOT)
            if (profile.homes?.remove(name) == null) throw IllegalArgumentException("找不到该家")
            DataStore.get()?.markDirty()
            ApiJson.ok("已删除")
        }
    }
}
