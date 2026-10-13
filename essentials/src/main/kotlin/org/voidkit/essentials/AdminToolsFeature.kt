package org.voidkit.essentials

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.item.ItemStack
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.CommandPerms
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.data.DataStore
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text

/**
 * 管理工具集：/fly /god /heal /feed /speed /vanish /hat /enderchest /workbench /invsee
 * 外加聊天治理：/broadcast /mute /unmute
 *
 * 不重定义原版已有命令（/tp、/gamemode、/list…），只提供不冲突的别名。
 *
 * ## 权限分三层
 *
 * | 层 | 命令 | 节点 | 默认 |
 * |---|---|---|---|
 * | **玩家自助** | `/hat` `/enderchest` `/workbench` | `voidkit.command.hat` 等 | **公开**（人人可用） |
 * | **对自己用管理工具** | `/fly` `/god` `/heal` `/feed` `/speed` `/vanish` | `voidkit.command.fly` 等 | 仅管理，可单独授权（给 VIP 发 `/fly`） |
 * | **对别人动手** | `/fly <玩家>` `/enderchest <玩家>` `/invsee` `/broadcast` `/mute` | 必须 `voidkit.admin` | 仅管理 |
 *
 * 🚨 修复记录：以前 `/fly` `/god` `/heal` `/feed` 的「对自己」分支写的是
 * `requires { it.player != null }` —— **完全没有权限校验**，等于任何玩家都能
 * 给自己开飞行和无敌。现在必须过节点。
 */
class AdminToolsFeature : Feature() {

    override val id = "admin-tools"
    override val displayName = "管理工具"
    override val description = "飞行、无敌、治疗、喂食、游戏速度、隐身、末影箱、工作台、查看背包、公告与禁言"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // 玩家自助类（打开自己的界面 / 戴帽子）默认对所有玩家开放
        PublicNodes.register(
            "voidkit.command.hat",
            "voidkit.command.enderchest",
            "voidkit.command.workbench"
        )

        // 逐条注册、各自容错：整合包里 /fly /speed /hat 这类短名字很容易被别的 mod 抢先，
        // 撞名会让 Brigadier 抛异常。内核只按「模块」容错，不隔离的话本模块后面的命令会一起消失。
        val steps: List<Pair<String, () -> Unit>> = listOf(
            "fly" to { registerSelfOrOther(dispatcher, "fly", "voidkit.command.fly", ::toggleFly) },
            "god" to { registerSelfOrOther(dispatcher, "god", "voidkit.command.god", ::toggleGod) },
            "heal" to { registerSelfOrOther(dispatcher, "heal", "voidkit.command.heal", ::heal) },
            "feed" to { registerSelfOrOther(dispatcher, "feed", "voidkit.command.feed", ::feed) },
            "speed" to { registerSpeed(dispatcher) },
            "vanish" to { registerVanish(dispatcher, "vanish") },
            "v" to { registerVanish(dispatcher, "v") },
            "hat" to { registerHat(dispatcher) },
            "enderchest" to { registerEnderChest(dispatcher) },
            "workbench" to { registerWorkbench(dispatcher) },
            "invsee" to { registerInvSee(dispatcher) },
            "broadcast" to { registerBroadcast(dispatcher) },
            "mute" to { registerMute(dispatcher) },
            "unmute" to { registerUnmute(dispatcher) }
        )
        steps.forEach { (name, step) -> registerCommand(name) { step() } }

        // /gm 已迁到同子项目的 CommandAliasFeature —— 它是原版命令的短别名，和 fly/god 这类
        // 附加工具不同性质。放在那边可以带 target 参数，也能和「原版命令接管」一起演进。
    }

    // ---------- 注册 ----------

    /**
     * 「对自己 / 对别人」两种用法的命令（/fly /god /heal /feed）。
     *
     * - **对自己**：`voidkit.admin` 或该命令的专属节点（[node]）—— 所以可以只给某组发 `/fly`；
     * - **对别人**：**必须** `voidkit.admin` —— 只拿到 `/fly` 的人不该能把别人也变成飞行。
     */
    private fun registerSelfOrOther(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        name: String,
        node: String,
        action: (ServerPlayer, ServerPlayer) -> Int
    ) {
        dispatcher.register(
            Commands.literal(name)
                .requires { CommandPerms.admin(it, node) }
                .executes { action(it.source.playerOrException, it.source.playerOrException) }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .requires { VoidkitServices.permission().admin(it) }
                        .executes { action(it.source.playerOrException, EntityArgument.getPlayer(it, "target")) }
                )
        )
    }

    private fun registerSpeed(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("speed")
                .requires { CommandPerms.admin(it, "voidkit.command.speed") }
                .then(
                    Commands.argument("value", FloatArgumentType.floatArg(0f, 5f))
                        .executes {
                            setSpeed(it.source.playerOrException, FloatArgumentType.getFloat(it, "value"))
                        }
                        .then(
                            Commands.argument("target", EntityArgument.player())
                                // 改**别人**移速必须真管理员：父节点只要求「admin 或 voidkit.command.speed」，
                                // 只被授予细权限（非 admin）的人不该能动别人。与 /fly /gm <玩家> 一致。
                                .requires { VoidkitServices.permission().admin(it) }
                                .executes {
                                    setSpeed(
                                        EntityArgument.getPlayer(it, "target"),
                                        FloatArgumentType.getFloat(it, "value")
                                    )
                                }
                        )
                )
        )
    }

    private fun registerVanish(dispatcher: CommandDispatcher<CommandSourceStack>, name: String) {
        dispatcher.register(
            Commands.literal(name)
                .requires { CommandPerms.admin(it, "voidkit.command.vanish") }
                .executes { toggleVanish(it.source.playerOrException) }
        )
    }

    private fun registerHat(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("hat")
                .requires { it.player != null && VoidkitServices.permission().has(it, "voidkit.command.hat") }
                .executes { hat(it.source.playerOrException) }
        )
    }

    private fun registerEnderChest(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("enderchest")
                .requires { it.player != null && VoidkitServices.permission().has(it, "voidkit.command.enderchest") }
                .executes { enderChest(it.source.playerOrException, it.source.playerOrException) }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        // ⚠️ 只看 admin，**不要**传 voidkit.command.enderchest —— 它是公开节点，
                        // CommandPerms.admin 会退化成 `perm.admin() || perm.has(node)`，而 has() 对
                        // 公开节点默认放行，等于人人可开他人末影箱。开别人箱子必须真管理员。
                        .requires { VoidkitServices.permission().admin(it) }
                        .executes { enderChest(it.source.playerOrException, EntityArgument.getPlayer(it, "target")) }
                )
        )
    }

    private fun registerWorkbench(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("workbench")
                .requires { it.player != null && VoidkitServices.permission().has(it, "voidkit.command.workbench") }
                .executes { workbench(it.source.playerOrException) }
        )
    }

    private fun registerInvSee(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("invsee")
                .requires { CommandPerms.admin(it, "voidkit.command.invsee") }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { invSee(it.source.playerOrException, EntityArgument.getPlayer(it, "target")) }
                )
        )
    }

    private fun registerBroadcast(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("broadcast")
                .requires { CommandPerms.admin(it, "voidkit.command.broadcast") }
                .then(
                    Commands.argument("message", StringArgumentType.greedyString())
                        .executes { broadcast(it.source, StringArgumentType.getString(it, "message")) }
                )
        )
    }

    private fun registerMute(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("mute")
                .requires { CommandPerms.admin(it, "voidkit.command.mute") }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { mute(it.source, EntityArgument.getPlayer(it, "target"), 0L, "") }
                        .then(
                            Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes {
                                    mute(
                                        it.source,
                                        EntityArgument.getPlayer(it, "target"),
                                        IntegerArgumentType.getInteger(it, "minutes") * 60_000L,
                                        ""
                                    )
                                }
                                .then(
                                    Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes {
                                            mute(
                                                it.source,
                                                EntityArgument.getPlayer(it, "target"),
                                                IntegerArgumentType.getInteger(it, "minutes") * 60_000L,
                                                StringArgumentType.getString(it, "reason")
                                            )
                                        }
                                )
                        )
                )
        )
    }

    private fun registerUnmute(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("unmute")
                .requires { CommandPerms.admin(it, "voidkit.command.mute") }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .executes { unmute(it.source, EntityArgument.getPlayer(it, "target")) }
                )
        )
    }

    // ---------- 实现 ----------

    private fun toggleFly(actor: ServerPlayer, target: ServerPlayer): Int {
        val abilities = target.abilities
        abilities.mayfly = !abilities.mayfly
        if (!abilities.mayfly) abilities.flying = false
        target.onUpdateAbilities()

        DataStore.get()?.profile(target.uuid, target.gameProfile.name)?.flying = abilities.mayfly
        DataStore.get()?.markDirty()

        val state = if (abilities.mayfly) "开启" else "关闭"
        Msg.ok(target, "飞行已$state")
        if (target.uuid != actor.uuid) Msg.ok(actor, "已为 ${target.gameProfile.name} $state 飞行")
        return 1
    }

    private fun toggleGod(actor: ServerPlayer, target: ServerPlayer): Int {
        val enabled = !target.isInvulnerable
        target.isInvulnerable = enabled
        DataStore.get()?.profile(target.uuid, target.gameProfile.name)?.godMode = enabled
        DataStore.get()?.markDirty()
        val state = if (enabled) "开启" else "关闭"
        Msg.ok(target, "无敌模式已$state")
        if (target.uuid != actor.uuid) Msg.ok(actor, "已${state} ${target.gameProfile.name} 的无敌模式")
        return 1
    }

    private fun heal(actor: ServerPlayer, target: ServerPlayer): Int {
        target.health = target.maxHealth
        target.foodData.foodLevel = 20
        // 饱和度的 getter 是 getSaturationLevel()，setter 是 setSaturation()，不成对，只能显式调用
        target.foodData.setSaturation(20f)
        target.removeAllEffects()
        Msg.ok(target, "已恢复生命与状态")
        if (target.uuid != actor.uuid) Msg.ok(actor, "已治疗 ${target.gameProfile.name}")
        return 1
    }

    private fun feed(actor: ServerPlayer, target: ServerPlayer): Int {
        target.foodData.foodLevel = 20
        target.foodData.setSaturation(20f)
        Msg.ok(target, "饥饿值已补满")
        if (target.uuid != actor.uuid) Msg.ok(actor, "已喂饱 ${target.gameProfile.name}")
        return 1
    }

    private fun setSpeed(target: ServerPlayer, value: Float): Int {
        target.abilities.walkingSpeed = value
        target.abilities.flyingSpeed = value
        target.onUpdateAbilities()
        Msg.ok(target, "移动速度已设为 $value")
        return 1
    }

    private fun toggleVanish(player: ServerPlayer): Int {
        val profile = DataStore.get()?.profile(player.uuid, player.gameProfile.name) ?: return 0
        profile.vanished = !profile.vanished
        player.isInvisible = profile.vanished
        DataStore.get()?.markDirty()
        Msg.ok(player, if (profile.vanished) "隐身已开启" else "隐身已关闭")
        return 1
    }

    private fun hat(player: ServerPlayer): Int {
        val inventory = player.inventory
        val selected = inventory.selected
        val hand = inventory.getItem(selected)
        if (hand.isEmpty) {
            Msg.err(player, "手上没有物品")
            return 0
        }
        val oldHelmet = inventory.armor[3]
        inventory.armor[3] = hand.copy()
        inventory.setItem(selected, oldHelmet)
        Msg.ok(player, "已把手中物品戴到头上")
        return 1
    }

    private fun enderChest(viewer: ServerPlayer, target: ServerPlayer): Int {
        viewer.openMenu(
            SimpleMenuProvider(
                { id, inventory, _ -> ChestMenu.threeRows(id, inventory, target.enderChestInventory) },
                Component.literal(if (target === viewer) "末影箱" else "${target.gameProfile.name} 的末影箱")
            )
        )
        return 1
    }

    private fun workbench(player: ServerPlayer): Int {
        player.openMenu(
            SimpleMenuProvider(
                { id, inventory, _ -> CraftingMenu(id, inventory, ContainerLevelAccess.NULL) },
                Component.translatable("container.crafting")
            )
        )
        return 1
    }

    private fun invSee(viewer: ServerPlayer, target: ServerPlayer): Int {
        viewer.openMenu(
            SimpleMenuProvider(
                { id, inventory, _ -> ChestMenu.sixRows(id, inventory, PlayerInvView(target.inventory)) },
                Component.literal("${target.gameProfile.name} 的物品栏")
            )
        )
        return 1
    }

    // ---------- 聊天治理 ----------

    private fun broadcast(source: CommandSourceStack, message: String): Int {
        val line = Component.empty()
            .append(Component.literal("[公告] ").withStyle(ChatFormatting.GOLD))
            .append(Text.legacy(message))
        source.server.playerList.broadcastSystemMessage(line, false)
        return 1
    }

    /** durationMs <= 0 表示永久禁言。 */
    @Throws(CommandSyntaxException::class)
    private fun mute(source: CommandSourceStack, target: ServerPlayer, durationMs: Long, reason: String): Int {
        val store = DataStore.get() ?: return 0
        val profile = store.profile(target.uuid, target.gameProfile.name)
        profile.muted = true
        profile.mutedUntil = if (durationMs > 0L) System.currentTimeMillis() + durationMs else 0L
        profile.muteReason = reason
        store.markDirty()
        store.save(profile)

        val span = if (durationMs > 0L) Msg.duration(durationMs / 1000L) else "永久"
        val suffix = if (reason.isBlank()) "" else "：$reason"
        Msg.ok(source, "已禁言 ${target.gameProfile.name}（$span）$suffix")
        Msg.err(target, "你已被禁言（$span）$suffix")
        return 1
    }

    @Throws(CommandSyntaxException::class)
    private fun unmute(source: CommandSourceStack, target: ServerPlayer): Int {
        val store = DataStore.get() ?: return 0
        val profile = store.profile(target.uuid, target.gameProfile.name)
        if (!profile.muted) {
            Msg.info(source, "${target.gameProfile.name} 当前没有被禁言")
            return 0
        }
        profile.muted = false
        profile.mutedUntil = 0L
        profile.muteReason = ""
        store.markDirty()
        store.save(profile)
        Msg.ok(source, "已解除 ${target.gameProfile.name} 的禁言")
        Msg.ok(target, "你的禁言已被解除")
        return 1
    }

    /**
     * 把玩家背包（36 格）映射到 54 格视图上供 [ChestMenu.sixRows] 使用，
     * 余下 18 格为空位，避免 slot 索引越界。
     */
    private class PlayerInvView(private val inventory: Inventory) : Container {

        override fun getContainerSize(): Int = 54

        override fun isEmpty(): Boolean = inventory.isEmpty

        override fun getItem(slot: Int): ItemStack =
            if (slot < 36) inventory.getItem(slot) else ItemStack.EMPTY

        override fun removeItem(slot: Int, amount: Int): ItemStack =
            if (slot < 36) inventory.removeItem(slot, amount) else ItemStack.EMPTY

        override fun removeItemNoUpdate(slot: Int): ItemStack =
            if (slot < 36) inventory.removeItemNoUpdate(slot) else ItemStack.EMPTY

        override fun setItem(slot: Int, stack: ItemStack) {
            if (slot < 36) inventory.setItem(slot, stack)
        }

        override fun setChanged() = inventory.setChanged()

        override fun stillValid(player: Player): Boolean = true

        override fun clearContent() {}
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/admin/online") { _ ->
            val players = org.voidkit.core.api.VoidkitServer.require().playerList.players
            ApiJson.obj("ok", true, "players", players.map { it.gameProfile.name })
        }
    }
}
