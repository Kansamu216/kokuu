package org.voidkit.utility

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
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Icons
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text

/**
 * 垃圾桶的只读容器视图。
 *
 * ## 为什么要在打开时“冻结”布局
 *
 * [TrashBin] 的排序是按价值动态排的。如果每次 [getItem] 都现算，玩家取回一件后
 * 所有格子会整体前移 —— 光标下的物品直接变成另一样，非常容易误取。
 *
 * 所以在这里把 `槽位 -> 聚合键` 的映射**在菜单打开时固定下来**（[keys]），
 * 取回某格只是把那一格置空，其余格子位置不动。
 *
 * ## 按钮格
 *
 * 底部一行（45~53）是固定按钮，不参与物品映射：
 *  - 45  全部取回（按顺序把能塞进背包的都塞进去）
 *  - 49  清空垃圾桶（需要 `voidkit.clearlag.clear`）
 *  - 53  关闭
 */
class TrashContainer(
    private val bin: TrashBin,
    /** 打开者是否有「清空垃圾桶」权限；无权限时该按钮**不显示**（而不是显示了再报错）。 */
    private val canClear: Boolean,
) : Container {

    /** 固定映射：槽位 -> 聚合键。null = 该格为空。 */
    private val keys: Array<String?> = arrayOfNulls(TrashMenu.ITEM_SLOTS)

    /** 预渲染好的显示物品（避免每次同步都重建 ItemStack）。 */
    private val displays: Array<ItemStack?> = arrayOfNulls(TrashMenu.TOTAL_SLOTS)

    init {
        bin.snapshot().take(TrashMenu.ITEM_SLOTS).forEachIndexed { index, entry ->
            keys[index] = entry.key
            displays[index] = render(entry)
        }
        refreshButtons()
    }

    fun keyAt(slot: Int): String? = keys.getOrNull(slot)

    /** 某格取空后调用：置空并刷新。 */
    fun forget(slot: Int) {
        if (slot !in keys.indices) return
        keys[slot] = null
        displays[slot] = null
        refreshButtons()
    }

    /** 取回一部分后仍有剩余：刷新那一格的显示数量。 */
    fun refresh(slot: Int) {
        val key = keys.getOrNull(slot) ?: return
        displays[slot] = bin.byKey(key)?.let { render(it) }
        refreshButtons()
    }

    // ---------- Container ----------

    override fun getContainerSize(): Int = TrashMenu.TOTAL_SLOTS

    override fun isEmpty(): Boolean = bin.isEmpty()

    override fun getItem(slot: Int): ItemStack = displays.getOrNull(slot) ?: ItemStack.EMPTY

    /** 下面的写入路径全部禁用 —— 垃圾桶是只读的。 */
    override fun removeItem(slot: Int, amount: Int): ItemStack = ItemStack.EMPTY

    override fun removeItemNoUpdate(slot: Int): ItemStack = ItemStack.EMPTY

    override fun setItem(slot: Int, stack: ItemStack) = Unit

    override fun setChanged() = Unit

    /** [Container] 继承自 `Clearable`，原版在容器被丢弃时会调它。垃圾桶是只读视图，不清。 */
    override fun clearContent() = Unit

    override fun stillValid(player: Player): Boolean = true

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = false

    // ---------- 渲染 ----------

    /**
     * 把一条聚合记录渲染成显示物品。
     *
     * 数量超过 64 时图标右上角只能显示 64（原版上限），所以**真实数量写在名称里** ——
     * 不能让玩家以为那一格只有 64 个。
     */
    private fun render(entry: BinEntry): ItemStack {
        val display = entry.sample.copy()
        display.setCount(entry.count.coerceIn(1L, 64L).toInt())
        VItems.setCustomName(display, Text.legacy("&f${nameOf(entry.sample)} &8× &e${formatCount(entry.count)}"))
        val lore = mutableListOf<Component>()
        lore += Text.legacy("&7点击 &f全部取回 &7（${formatCount(entry.count)} 个）")
        if (entry.valuable) lore += Text.legacy("&a◆ 高价值物品，不会被自动丢弃")
        if (entry.score < 0) lore += Text.legacy("&8◆ 常见产物")
        VItems.setLore(display, lore)
        return display
    }

    /** 底部按钮的图标与文案。会随垃圾桶状态变化（空/非空）。 */
    private fun refreshButtons() {
        val hasContent = !bin.isEmpty()

        displays[TrashMenu.SLOT_TAKE_ALL] = if (hasContent) {
            button(
                "minecraft:chest",
                "&a&l全部取回",
                listOf("&7把垃圾桶里能装下的都塞进背包", "&8装不下的会留在这里")
            )
        } else {
            button("minecraft:barrier", "&8垃圾桶是空的", listOf("&7等下一轮清扫再看看"))
        }

        // 无权限时**不显示**按钮 —— 开箱即用前提下，玩家不该看到点了必然报错的东西。
        // 判定与 clear() 内完全一致（同一个常量），不会出现「按钮能点但报错」的错位。
        displays[TrashMenu.SLOT_CLEAR] = if (canClear) {
            button(
                "minecraft:lava_bucket",
                "&c&l清空垃圾桶",
                listOf("&7丢弃里面全部物品")
            )
        } else {
            null
        }

        displays[TrashMenu.SLOT_CLOSE] = button("minecraft:barrier", "&7关闭", emptyList())
    }

    private fun button(icon: String, name: String, lore: List<String>): ItemStack {
        val stack = Icons.of(icon)
        VItems.setCustomName(stack, Text.legacy(name))
        if (lore.isNotEmpty()) {
            VItems.setLore(stack, lore.map { Text.legacy(it) })
        }
        return stack
    }

    private fun nameOf(stack: ItemStack): String = stack.hoverName.string

    private fun formatCount(count: Long): String = COUNT_FORMAT.format(count)

    private companion object {
        val COUNT_FORMAT = java.text.DecimalFormat("#,##0")
    }
}

/**
 * 垃圾桶界面：6 行箱子，底部一行是操作按钮。
 *
 * 继承 [ChestMenu] 并**完全接管** [clicked]（刻意不调用 super）——
 * 这样物品既不能被拖出去、也不能被放进来，点一下格子就等于“取回这一格”。
 * 这是把原版容器当“按钮面板”用的标准做法。
 */
class TrashMenu(
    containerId: Int,
    inventory: Inventory,
    private val trash: TrashContainer,
    private val bin: TrashBin,
) : ChestMenu(MenuType.GENERIC_9x6, containerId, inventory, trash, 6) {

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        // ⚠️ 不调用 super —— 一旦调用，原版就会按 PICKUP/QUICK_MOVE 处理这个槽位，
        //    玩家可以把垃圾桶里的东西拖到光标上带走，绕过我们的“取回”逻辑。
        if (player !is ServerPlayer) return
        if (slotId < 0) return

        when (slotId) {
            in 0 until ITEM_SLOTS -> takeOne(player, slotId)
            SLOT_TAKE_ALL -> takeAll(player)
            // 按钮在无权限时已不显示；这里仍由 clear() 内部做权威校验（防客户端伪造点击）
            SLOT_CLEAR -> clear(player)
            SLOT_CLOSE -> player.closeContainer()
            // 玩家背包区（54~89）：什么都不做，防止往里塞东西
            else -> Unit
        }
    }

    /** 屏蔽 Shift 点击 —— 否则它会试图把容器内容“快速移动”进玩家背包。 */
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    // ---------- 操作 ----------

    private fun takeOne(player: ServerPlayer, slot: Int) {
        val key = trash.keyAt(slot) ?: return
        val entry = bin.take(key) ?: return

        val remainder = give(player, entry.sample, entry.count)
        if (remainder > 0) {
            // 背包塞不下 —— 剩下的放回垃圾桶，别凭空消失
            bin.add(entry.sample, remainder, ClearLagStore.config)
            Msg.err(player, "背包放不下，还有 ${format(remainder)} 个留在垃圾桶里")
            trash.refresh(slot)
        } else {
            Msg.ok(player, "已取回 ${format(entry.count)} 个 ${entry.sample.hoverName.string}")
            trash.forget(slot)
        }
    }

    private fun takeAll(player: ServerPlayer) {
        val keys = (0 until ITEM_SLOTS).mapNotNull { trash.keyAt(it) }
        if (keys.isEmpty()) {
            Msg.info(player, "垃圾桶是空的")
            return
        }
        var taken = 0L
        var kinds = 0
        keys.forEach { key ->
            val entry = bin.take(key) ?: return@forEach
            val remainder = give(player, entry.sample, entry.count)
            if (remainder > 0) {
                bin.add(entry.sample, remainder, ClearLagStore.config)
            } else {
                kinds++
            }
            taken += entry.count - remainder
        }
        if (kinds == 0) {
            Msg.err(player, "背包空间不足，没能取回任何物品")
        } else {
            Msg.ok(player, "已取回 $kinds 种、共 ${format(taken)} 个物品" + if (taken < keys.size) "（背包满了，剩余的仍留在桶里）" else "")
        }
        player.closeContainer()
    }

    private fun clear(player: ServerPlayer) {
        if (!VoidkitServices.permission().has(player, CLEAR_NODE)) {
            Msg.err(player, "你没有清空垃圾桶的权限")
            return
        }
        val count = bin.totalItems()
        bin.clear()
        player.closeContainer()
        Msg.ok(player, "已清空垃圾桶（丢弃 ${format(count)} 个物品）")
    }

    // ---------- 给物品 ----------

    /**
     * 把 [count] 个 [sample] 尽量塞进玩家背包。
     *
     * @return 没塞进去的数量（0 = 全部装下）
     */
    private fun give(player: ServerPlayer, sample: ItemStack, count: Long): Long {
        var remaining = count
        val perStack = sample.maxStackSize.coerceAtLeast(1)
        while (remaining > 0) {
            val chunk = minOf(remaining, perStack.toLong()).toInt()
            val stack = sample.copy()
            stack.setCount(chunk)
            if (!player.inventory.add(stack)) break
            remaining -= chunk
        }
        return remaining
    }

    private fun format(count: Long): String = java.text.DecimalFormat("#,##0").format(count)

    companion object {
        /** 物品区槽位数。 */
        const val ITEM_SLOTS = 45

        /** 总槽位（6 行 × 9 列）。 */
        const val TOTAL_SLOTS = 54

        const val SLOT_TAKE_ALL = 45
        const val SLOT_CLEAR = 49

        /** 「清空垃圾桶」所需节点。按钮显示与动作校验共用，避免两处漂移。 */
        const val CLEAR_NODE = "voidkit.clearlag.clear"
        const val SLOT_CLOSE = 53

        /** 打开垃圾桶界面。 */
        fun open(player: ServerPlayer, bin: TrashBin) {
            // 权限在**打开时**判定一次即可：菜单生命周期很短，且 clear() 里还有二次校验。
            val container = TrashContainer(
                bin,
                org.voidkit.core.api.VoidkitServices.permission().has(player, CLEAR_NODE),
            )
            player.openMenu(
                SimpleMenuProvider(
                    { id, inventory, _ -> TrashMenu(id, inventory, container, bin) },
                    Text.legacy("&8垃圾桶 &7— 点击物品取回")
                )
            )
        }
    }
}
