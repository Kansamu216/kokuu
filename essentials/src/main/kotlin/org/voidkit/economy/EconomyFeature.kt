package org.voidkit.economy

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import org.voidkit.core.api.ApiJson
import org.voidkit.core.api.ApiRouter
import org.voidkit.core.api.Feature
import org.voidkit.core.api.PlayerViews
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.api.VoidkitServer
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Msg
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 经济功能：命令 + `%balance%` 变量 + 管理 API。
 *
 * 数据与实现都在 [EconomyStore]，这里只负责“对外接口”。
 *
 * 命令全部围绕 `voidkit.economy.*` 权限节点设计 —— 玩家默认只能看自己的余额、
 * 给别人转账；查别人、发钱、扣钱要 `voidkit.economy.admin`。
 */
class EconomyFeature : Feature() {

    override val id = "economy"
    override val displayName = "经济"
    override val description = "货币余额、转账、财富排行"

    override val defaultEnabled: Boolean = true

    /** 上次转账时间，用于冷却。 */
    private val lastPay = ConcurrentHashMap<String, Long>()

    // ---------- 生命周期 ----------

    /**
     * 玩家退出时清掉他的转账冷却记录。
     *
     * 这个表是按 UUID 存的 —— 不清的话每来过一个玩家就留一条，长期运行的服会
     * 悄悄涨到几万条。冷却本来就是会话级的，退出即失效合情合理。
     */
    override fun onPlayerQuit(e: org.voidkit.core.api.events.VPlayerQuit) {
        lastPay.remove(e.player.uuid.toString())
    }

    override fun onSecond(server: MinecraftServer) = EconomyStore.flushIfDirty()

    override fun onServerStop(server: MinecraftServer) {
        // 关服必须强写一次 —— 否则最后 30 秒的交易会丢
        EconomyStore.flush()
        lastPay.clear()
    }

    override fun onReload(server: MinecraftServer) {
        EconomyConfigStore.load()
        Values.load()
    }

    // ---------- 命令 ----------

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        // 玩家日常经济操作登记为公开：零配置下普通玩家也能查余额 / 转账 / 回收。
        // 管理端的 `voidkit.economy.admin` 保持不公开。
        PublicNodes.register(
            "voidkit.economy.balance",
            "voidkit.economy.pay",
            "voidkit.economy.value",
            "voidkit.economy.sell",
            "voidkit.economy.shop"
        )

        val balance = Commands.literal("balance")
            .requires { VoidkitServices.permission().has(it, "voidkit.economy.balance") }
            .executes { showBalance(it.source, it.source.playerOrException) }
            .then(
                Commands.argument("target", EntityArgument.player())
                    .requires { VoidkitServices.permission().has(it, "voidkit.economy.balance.others") }
                    .executes { showBalance(it.source, EntityArgument.getPlayer(it, "target")) }
            )

        dispatcher.register(balance)
        // /bal 是所有人的习惯叫法，注册成别名而不是独立实现
        dispatcher.register(
            Commands.literal("bal")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.balance") }
                .redirect(balance.build())
        )

        dispatcher.register(
            Commands.literal("pay")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.pay") }
                .then(
                    Commands.argument("target", EntityArgument.player())
                        .then(
                            Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                                .executes {
                                    pay(
                                        it.source.playerOrException,
                                        EntityArgument.getPlayer(it, "target"),
                                        DoubleArgumentType.getDouble(it, "amount")
                                    )
                                }
                        )
                )
        )

        dispatcher.register(
            Commands.literal("baltop")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.balance") }
                .executes { top(it.source, 1) }
                .then(
                    Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes { top(it.source, IntegerArgumentType.getInteger(it, "page")) }
                )
        )

        // ---------- 估值 / 出售 ----------

        dispatcher.register(
            Commands.literal("value")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.value") }
                .then(
                    Commands.argument("id", StringArgumentType.string())
                        .suggests { _, builder -> SharedSuggestionProvider.suggest(Values.suggestKeys(), builder) }
                        .executes { showValue(it.source, StringArgumentType.getString(it, "id")) }
                )
        )

        dispatcher.register(
            Commands.literal("worth")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.value") }
                .executes { showWorth(it.source.playerOrException) }
        )

        dispatcher.register(
            Commands.literal("sell")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.sell") }
                .then(
                    Commands.literal("hand")
                        .executes { sellHand(it.source.playerOrException) }
                )
                .then(
                    Commands.literal("all")
                        .executes { sellAll(it.source.playerOrException) }
                )
        )

        // ---------- 商店 ----------

        dispatcher.register(
            Commands.literal("shop")
                .requires { VoidkitServices.permission().has(it, "voidkit.economy.shop") }
                .executes { openShop(it.source.playerOrException) }
        )

        // ---------- 管理 ----------
        val eco = Commands.literal("eco").requires { VoidkitServices.permission().has(it, "voidkit.economy.admin") }
        eco.then(
            Commands.literal("give").then(
                Commands.argument("target", EntityArgument.player()).then(
                    Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes { adjust(it.source, EntityArgument.getPlayer(it, "target"), DoubleArgumentType.getDouble(it, "amount"), ACT_GIVE) }
                )
            )
        )
        eco.then(
            Commands.literal("take").then(
                Commands.argument("target", EntityArgument.player()).then(
                    Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes { adjust(it.source, EntityArgument.getPlayer(it, "target"), DoubleArgumentType.getDouble(it, "amount"), ACT_TAKE) }
                )
            )
        )
        eco.then(
            Commands.literal("set").then(
                Commands.argument("target", EntityArgument.player()).then(
                    Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes { adjust(it.source, EntityArgument.getPlayer(it, "target"), DoubleArgumentType.getDouble(it, "amount"), ACT_SET) }
                )
            )
        )
        dispatcher.register(eco)
    }

    // ---------- 命令实现 ----------

    private fun showBalance(source: CommandSourceStack, target: ServerPlayer): Int {
        val amount = EconomyStore.balance(target.uuid)
        if (target.uuid == source.player?.uuid) {
            Msg.ok(source, "你的余额：${EconomyStore.format(amount)}")
        } else {
            Msg.info(source, "${target.gameProfile.name} 的余额：${EconomyStore.format(amount)}")
        }
        return 1
    }

    private fun pay(from: ServerPlayer, to: ServerPlayer, amount: Double): Int {
        val cfg = EconomyConfigStore.config
        if (!cfg.payEnabled) {
            Msg.err(from, "服务器已关闭转账功能")
            return 0
        }
        if (from.uuid == to.uuid) {
            Msg.err(from, "不能给自己转账")
            return 0
        }
        if (amount < cfg.payMin) {
            Msg.err(from, "单笔转账不能少于 ${EconomyStore.format(cfg.payMin)}")
            return 0
        }

        val cooldown = cfg.payCooldownSeconds
        if (cooldown > 0) {
            val now = System.currentTimeMillis()
            val last = lastPay[from.uuid.toString()] ?: 0L
            val wait = cooldown * 1000L - (now - last)
            if (wait > 0) {
                Msg.err(from, "转账太快了，请等 ${(wait / 1000) + 1} 秒")
                return 0
            }
        }

        val tax = EconomyStore.round(amount * cfg.taxPercent / 100.0)
        val total = EconomyStore.round(amount + tax)
        if (!EconomyStore.has(from, total)) {
            Msg.err(
                from,
                if (tax > 0) "余额不足，需要 ${EconomyStore.format(total)}（含手续费 ${EconomyStore.format(tax)}）"
                else "余额不足，需要 ${EconomyStore.format(total)}"
            )
            return 0
        }

        if (!EconomyStore.transfer(from, to, amount)) {
            Msg.err(from, "转账失败，请稍后再试")
            return 0
        }
        lastPay[from.uuid.toString()] = System.currentTimeMillis()

        if (tax > 0) {
            Msg.ok(from, "已向 ${to.gameProfile.name} 转账 ${EconomyStore.format(amount)}（手续费 ${EconomyStore.format(tax)}）")
        } else {
            Msg.ok(from, "已向 ${to.gameProfile.name} 转账 ${EconomyStore.format(amount)}")
        }
        Msg.ok(to, "${from.gameProfile.name} 向你转账 ${EconomyStore.format(amount)}，当前余额 ${EconomyStore.format(EconomyStore.balance(to.uuid))}")
        return 1
    }

    private fun adjust(source: CommandSourceStack, target: ServerPlayer, amount: Double, action: String): Int {
        val before = EconomyStore.balance(target.uuid)
        when (action) {
            ACT_GIVE -> EconomyStore.deposit(target, amount, "admin-give")
            ACT_TAKE -> {
                val left = EconomyStore.withdraw(target, amount, "admin-take")
                if (left < 0) {
                    Msg.err(source, "${target.gameProfile.name} 只有 ${EconomyStore.format(before)}，扣不掉 ${EconomyStore.format(amount)}")
                    return 0
                }
            }
            ACT_SET -> EconomyStore.setBalance(target, amount, "admin-set")
        }
        val after = EconomyStore.balance(target.uuid)
        Msg.ok(source, "${target.gameProfile.name}：${EconomyStore.format(before)} -> ${EconomyStore.format(after)}")
        Msg.info(target, "你的余额已被调整为 ${EconomyStore.format(after)}")
        return 1
    }

    private fun top(source: CommandSourceStack, page: Int): Int {
        val perPage = 10
        val all = EconomyStore.top(page * perPage)
        val from = (page - 1) * perPage
        if (all.size <= from) {
            Msg.err(source, "没有第 $page 页的数据")
            return 0
        }
        Msg.info(source, "===== 财富排行 第 $page 页 =====")
        all.drop(from).forEachIndexed { index, entry ->
            Msg.info(source, " ${from + index + 1}. ${entry.name}  ${EconomyStore.format(entry.balance)}")
        }
        return 1
    }

    // ---------- 估值 / 出售 ----------

    private fun showValue(source: CommandSourceStack, id: String): Int {
        val v = Values.valueOf(id)
        if (v <= 0) {
            Msg.err(source, "估值表里没有「$id」，或它的单价为 0")
            return 0
        }
        Msg.ok(source, "物品 $id 单价 ${EconomyStore.format(v)}")
        return 1
    }

    private fun showWorth(player: ServerPlayer): Int {
        val stack = player.mainHandItem
        if (stack.isEmpty) {
            Msg.err(player, "你手上没有物品")
            return 0
        }
        val unit = Values.valueOf(stack)
        if (unit <= 0) {
            Msg.err(player, "「${stack.hoverName.string}」没有价值")
            return 0
        }
        val total = EconomyStore.round(unit * stack.count)
        Msg.ok(player, "「${stack.hoverName.string}」单价 ${EconomyStore.format(unit)}，这一叠共 ${EconomyStore.format(total)}")
        return 1
    }

    private fun sellHand(player: ServerPlayer): Int {
        val stack = player.mainHandItem
        if (stack.isEmpty) {
            Msg.err(player, "你手上没有物品")
            return 0
        }
        val unit = Values.valueOf(stack)
        if (unit <= 0) {
            Msg.err(player, "「${stack.hoverName.string}」没有价值，卖不掉")
            return 0
        }
        val total = EconomyStore.round(unit * stack.count)
        EconomyStore.deposit(player, total, "sell")
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)
        Msg.ok(player, "卖出「${stack.hoverName.string}」×${stack.count}，收入 ${EconomyStore.format(total)}")
        return 1
    }

    private fun sellAll(player: ServerPlayer): Int {
        val items = player.inventory.items
        var total = 0.0
        var types = 0
        for (i in items.indices) {
            val stack = items[i]
            if (stack.isEmpty) continue
            val unit = Values.valueOf(stack)
            // 只卖有价的：none 兜底时未列出的物品单价为 0，天然被排除
            if (unit <= 0) continue
            total += unit * stack.count
            types++
            player.inventory.setItem(i, ItemStack.EMPTY)
        }
        if (types == 0) {
            Msg.err(player, "背包里没有可出售的物品")
            return 0
        }
        val rounded = EconomyStore.round(total)
        EconomyStore.deposit(player, rounded, "sell")
        Msg.ok(player, "共卖出 $types 种物品，收入 ${EconomyStore.format(rounded)}")
        return 1
    }

    // ---------- 商店 ----------

    private fun openShop(player: ServerPlayer): Int {
        ShopMenu.open(player)
        return 1
    }

    // ---------- API ----------

    override fun registerApi(router: ApiRouter) {
        router.get("/api/economy/top") { request ->
            val limit = (request.query("limit")?.toIntOrNull() ?: 20).coerceIn(1, 200)
            ApiJson.obj(
                "ok", true,
                "currency", EconomyConfigStore.config.currencyName,
                "entries", EconomyStore.top(limit).map {
                    ApiJson.obj("name", it.name, "uuid", it.uuid, "balance", it.balance)
                }
            )
        }

        router.get("/api/economy/{key}") { request ->
            // 注意：/api/economy/top 必须注册在这条之前 —— 两者段数相同，
            // 路由是顺序匹配的，先注册的字面路径才有机会命中。
            val profile = PlayerViews.resolve(request.param("key")) ?: throw IllegalArgumentException("找不到玩家")
            val uuid = runCatching { UUID.fromString(profile.uuid) }.getOrNull()
            val amount = uuid?.let { EconomyStore.balance(it) } ?: 0.0
            ApiJson.obj(
                "ok", true,
                "name", profile.lastName,
                "uuid", profile.uuid,
                "online", profile.online,
                "balance", amount,
                "formatted", EconomyStore.format(amount)
            )
        }

        router.post("/api/economy/{key}/give") { request ->
            val amount = request.requireField("amount").get("amount").asDouble
            val name = VoidkitServer.onServer {
                val player = PlayerViews.findOnline(request.param("key"))
                    ?: throw IllegalArgumentException("该玩家不在线（只能给在线玩家发钱）")
                EconomyStore.deposit(player, amount, "api-give")
                player.gameProfile.name
            }
            ApiJson.obj("ok", true, "message", "已发放 ${EconomyStore.format(amount)} 给 $name")
        }

        router.post("/api/economy/{key}/set") { request ->
            val amount = request.requireField("amount").get("amount").asDouble
            val name = VoidkitServer.onServer {
                val player = PlayerViews.findOnline(request.param("key"))
                    ?: throw IllegalArgumentException("该玩家不在线")
                EconomyStore.setBalance(player, amount, "api-set")
                player.gameProfile.name
            }
            ApiJson.obj("ok", true, "message", "已把 $name 的余额设为 ${EconomyStore.format(amount)}")
        }
    }

    private companion object {
        const val ACT_GIVE = "give"
        const val ACT_TAKE = "take"
        const val ACT_SET = "set"
    }
}
