package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.CommandPerms
import org.voidkit.core.api.Feature
import org.voidkit.core.data.DataStore
import org.voidkit.core.data.StoredLocation
import org.voidkit.core.data.WarpPoint
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.teleport.Teleports
import org.voidkit.core.util.Msg
import java.util.Locale

/**
 * 公共传送点：/setwarp /delwarp /warp /warps
 *
 * ## 权限：使用与管理分开
 *
 * | 命令 | 节点 | 默认 |
 * |---|---|---|
 * | `/warp` `/warps` | `voidkit.command.warp` | **对所有玩家开放** |
 * | `/setwarp` `/delwarp` | `voidkit.command.setwarp` | 仅管理（或 `voidkit.admin`） |
 *
 * 这两件事的性质完全不同：**用**传送点是玩家日常操作，**建/删**是管理操作。
 * 合成一个节点就会出现「要么人人能删传送点，要么人人不能用传送点」的两难。
 */
class WarpFeature : Feature() {

    override val id = "warps"
    override val displayName = "传送点"
    override val description = "全服共用的命名传送点，命令与面板都可管理"

    /** 玩家日常命令的节点，登记为公开（零配置下普通玩家也能用）。 */
    private val useNode = "voidkit.command.warp"

    /** 管理命令的节点，不公开（默认仅管理可用）。 */
    private val manageNode = "voidkit.command.setwarp"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        PublicNodes.register(useNode)

        dispatcher.register(
            Commands.literal("setwarp")
                .requires { CommandPerms.admin(it, manageNode) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .executes { setWarp(it.source, StringArgumentType.getString(it, "name")) }
                )
        )

        dispatcher.register(
            Commands.literal("delwarp")
                .requires { CommandPerms.admin(it, manageNode) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(warpNames(), builder) }
                        .executes { deleteWarp(it.source, StringArgumentType.getString(it, "name")) }
                )
        )

        dispatcher.register(
            Commands.literal("warp")
                .requires { VoidkitServices.permission().has(it, useNode) }
                .executes { listWarps(it.source) }
                .then(
                    Commands.argument("name", StringArgumentType.word())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(warpNames(), builder) }
                        .executes { warp(it.source, StringArgumentType.getString(it, "name")) }
                )
        )

        dispatcher.register(
            Commands.literal("warps")
                .requires { VoidkitServices.permission().has(it, useNode) }
                .executes { listWarps(it.source) }
        )
    }

    // ---------- 实现 ----------

    @Throws(CommandSyntaxException::class)
    private fun setWarp(source: CommandSourceStack, name: String): Int {
        val key = name.lowercase(Locale.ROOT)
        val player = source.player
        val location = if (player != null) StoredLocation.of(player) else StoredLocation()
        val author = player?.gameProfile?.name ?: "console"
        DataStore.get()?.global?.warps?.set(key, WarpPoint(key, location, author, System.currentTimeMillis()))
        DataStore.get()?.saveGlobal()
        Msg.ok(source, "已设置传送点 $key → ${location.display()}")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun deleteWarp(source: CommandSourceStack, name: String): Int {
        val key = name.lowercase(Locale.ROOT)
        if (DataStore.get()?.global?.warps?.remove(key) == null) {
            Msg.err(source, "没有名为 $name 的传送点")
            return 0
        }
        DataStore.get()?.saveGlobal()
        Msg.ok(source, "已删除传送点 $key")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun warp(source: CommandSourceStack, name: String): Int {
        val player = source.playerOrException
        val point = DataStore.get()?.global?.warps?.get(name.lowercase(Locale.ROOT))
        if (point == null) {
            Msg.err(player, "没有名为 $name 的传送点，可用 /warps 查看全部")
            return 0
        }
        if (!Teleports.to(player, point.location)) {
            Msg.err(player, "传送失败：目标维度 ${point.location.dim} 不存在")
            return 0
        }
        Msg.ok(player, "已传送到 ${point.name}")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun listWarps(source: CommandSourceStack): Int {
        val warps = DataStore.get()?.global?.warps ?: return 0
        if (warps.isEmpty()) {
            Msg.info(source, "当前没有任何传送点")
            return 0
        }
        Msg.info(source, "可用传送点（${warps.size}）：")
        warps.values.forEach { Msg.info(source, "  ${it.name} - ${it.location.display()}") }
        return warps.size
    }

    private fun warpNames(): List<String> =
        DataStore.get()?.global?.warps?.keys?.toList() ?: emptyList()

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/warps") { _ ->
            ApiJson.obj(
                "ok", true,
                "warps", warpNames().map { name ->
                    val warp = DataStore.get()!!.global.warps[name]!!
                    ApiJson.obj(
                        "name", warp.name,
                        "dim", warp.location.dim,
                        "x", warp.location.x, "y", warp.location.y, "z", warp.location.z,
                        "display", warp.location.display(),
                        "createdBy", warp.createdBy,
                        "createdAt", warp.createdAt
                    )
                }
            )
        }

        router.post("/api/warps") { request ->
            val body = request.requireField("name", "dim", "x", "y", "z")
            val name = body.get("name").asString.lowercase(Locale.ROOT)
            val location = StoredLocation(
                body.get("dim").asString,
                body.get("x").asDouble,
                body.get("y").asDouble,
                body.get("z").asDouble,
                if (body.has("yaw")) body.get("yaw").asFloat else 0f,
                if (body.has("pitch")) body.get("pitch").asFloat else 0f
            )
            DataStore.get()?.global?.warps?.set(name, WarpPoint(name, location, "panel", System.currentTimeMillis()))
            DataStore.get()?.saveGlobal()
            ApiJson.ok("已保存传送点 $name")
        }

        router.post("/api/warps/{name}/delete") { request ->
            val key = request.param("name")!!.lowercase(Locale.ROOT)
            if (DataStore.get()?.global?.warps?.remove(key) == null) {
                throw IllegalArgumentException("没有名为 $key 的传送点")
            }
            DataStore.get()?.saveGlobal()
            ApiJson.ok("已删除传送点 $key")
        }
    }
}
