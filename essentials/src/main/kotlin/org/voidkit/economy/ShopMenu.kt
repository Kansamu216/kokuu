package org.voidkit.economy

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import org.voidkit.core.api.VItems
import org.voidkit.core.util.Icons
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text

/** 商店里的一件商品：物品 id + 单价。 */
class ShopEntry(val id: String, val price: Double)

/**
 * 商店的只读容器视图。
 *
 * 打开菜单时把估值表里「精确物品 id」的条目拍平成商品列表（过滤掉 `#标签`），
 * 并对每个 id 做一次物品解析 —— 解析失败（id 写错、物品不存在）直接跳过，
 * 不占格子，也不会在玩家点它时冒出个屏障方块。
 */
class ShopContainer(entries: List<ShopEntry>) : Container {

    /** 前 45 格：槽位 -> 商品。null 表示该格没有商品。 */
    private val items: Array<ShopEntry?> = arrayOfNulls(ShopMenu.ITEM_SLOTS)

    /** 预渲染好的显示物品（避免每次同步都重建 ItemStack）。 */
    private val displays: Array<ItemStack?> = arrayOfNulls(ShopMenu.TOTAL_SLOTS)

    init {
        entries.take(ShopMenu.ITEM_SLOTS).forEachIndexed { index, entry ->
            items[index] = entry
            displays[index] = render(entry)
        }
        displays[ShopMenu.SLOT_CLOSE] = button("minecraft:barrier", "&c&l关闭", emptyList())
    }

    /** 取某格的商品；非商品格返回 null。 */
    fun entryAt(slot: Int): ShopEntry? = items.getOrNull(slot)

    // ---------- Container ----------

    override fun getContainerSize(): Int = ShopMenu.TOTAL_SLOTS

    override fun isEmpty(): Boolean = items.all { it == null }

    override fun getItem(slot: Int): ItemStack = displays.getOrNull(slot) ?: ItemStack.EMPTY

    // 写入路径全部禁用 —— 商店是只读的
    override fun removeItem(slot: Int, amount: Int): ItemStack = ItemStack.EMPTY

    override fun removeItemNoUpdate(slot: Int): ItemStack = ItemStack.EMPTY

    override fun setItem(slot: Int, stack: ItemStack) = Unit

    override fun setChanged() = Unit

    override fun clearContent() = Unit

    override fun stillValid(player: Player): Boolean = true

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = false

    // ---------- 渲染 ----------

    private fun render(entry: ShopEntry): ItemStack {
        val stack = Icons.of(entry.id)
        VItems.setCustomName(stack, Text.legacy("&f${entry.id}"))
        VItems.setLore(stack, listOf(Text.legacy("&7单价 &e${EconomyStore.format(entry.price)}")))
        return stack
    }

    private fun button(icon: String, name: String, lore: List<String>): ItemStack {
        val stack = Icons.of(icon)
        VItems.setCustomName(stack, Text.legacy(name))
        if (lore.isNotEmpty()) {
            VItems.setLore(stack, lore.map { Text.legacy(it) })
        }
        return stack
    }
}

/**
 * 商店界面：6 行箱子，前 45 格是商品，右下角（53）是关闭。
 *
 * 继承 [ChestMenu] 并**完全接管** [clicked]（刻意不调用 super）——
 * 这样商品图标既不能被拖出去、也不能被放进来；点商品格等于「买入一件」。
 *
 * ## 买入的扣钱 / 加物品顺序
 *
 * 1. 先只读判断余额（[EconomyStore.has]）——不够直接拒绝，别等加了物品才想起买不起；
 * 2. 再 `inventory.add` 加物品——背包满了就拒绝，**一分钱都不扣**；
 * 3. 最后才 [EconomyStore.withdraw] 扣钱——上一步 `has` 已确认余额足够，正常不会失败。
 *
 * 之所以「先加物品后扣钱」，是因为背包满是最常见的中断点，让它先发生就能天然
 * 做到「满背包不扣钱」，无需回滚货币。兜底分支里 withdraw 失败时把刚加的物品退掉，
 * 防止极端情况下玩家白拿。
 */
class ShopMenu(
    containerId: Int,
    inventory: Inventory,
    private val container: ShopContainer,
) : ChestMenu(MenuType.GENERIC_9x6, containerId, inventory, container, 6) {

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        // ⚠️ 不调用 super —— 一旦调用，原版就会按 PICKUP/QUICK_MOVE 处理这个槽位，
        //    玩家可以把商品图标拖到光标上带走，破坏「点击即购买」的语义。
        if (player !is ServerPlayer) return
        if (slotId < 0) return

        when {
            slotId in 0 until ITEM_SLOTS -> buy(player, slotId)
            slotId == SLOT_CLOSE -> player.closeContainer()
            // 玩家背包区（54~89）：什么都不做，防止往里塞东西
            else -> Unit
        }
    }

    /** 屏蔽 Shift 点击 —— 否则它会试图把容器内容「快速移动」进玩家背包。 */
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    // ---------- 买入 ----------

    private fun buy(player: ServerPlayer, slot: Int) {
        val entry = container.entryAt(slot) ?: return

        // 1. 先只读看钱够不够
        if (!EconomyStore.has(player, entry.price)) {
            Msg.err(player, "余额不足")
            return
        }

        val item = Icons.of(entry.id)

        // 2. 先加物品：背包满了就拒绝，不扣钱
        if (!player.inventory.add(item)) {
            Msg.err(player, "背包已满")
            return
        }

        // 3. 最后扣钱。上面 has 已确认余额够，这一步正常不会失败
        val left = EconomyStore.withdraw(player, entry.price, "shop")
        if (left < 0) {
            // 兜底：极端情况下 withdraw 仍失败（例如余额被并发改动），
            // 把刚加进去的物品退掉，不让玩家白拿。
            player.inventory.removeItem(item)
            Msg.err(player, "余额不足")
            return
        }

        Msg.ok(player, "已购买「${item.hoverName.string}」×1，花费 ${EconomyStore.format(entry.price)}")
    }

    companion object {
        /** 商品区槽位数（前 45 格放商品）。 */
        const val ITEM_SLOTS = 45

        /** 总槽位（6 行 × 9 列）。 */
        const val TOTAL_SLOTS = 54

        const val SLOT_CLOSE = 53

        /** 打开商店界面。 */
        fun open(player: ServerPlayer) {
            val entries = Values.file.values
                .asSequence()
                // 只卖精确物品 id：过滤掉 `#标签` 条目
                .filter { (id, _) -> !id.startsWith("#") }
                // 解析物品，解析失败（id 写错 / 物品不存在）跳过
                .mapNotNull { (id, price) -> if (Icons.exists(id)) ShopEntry(id, price) else null }
                .toList()

            val container = ShopContainer(entries)
            player.openMenu(
                SimpleMenuProvider(
                    { id, inventory, _ -> ShopMenu(id, inventory, container) },
                    Text.legacy("&8商店 &7— 点击商品购买")
                )
            )
        }
    }
}
