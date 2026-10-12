package org.voidkit.inspector

import net.minecraft.core.component.DataComponents
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
import net.minecraft.world.item.component.ItemLore
import org.voidkit.core.util.Icons
import org.voidkit.core.util.Text
import java.text.SimpleDateFormat
import java.util.Date

/** 时间格式（`MM-dd HH:mm:ss`），只在游戏主线程使用，无需加锁。 */
private val TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm:ss")

/**
 * 审计结果的只读容器视图。
 *
 * 在打开时把「槽位 → 记录」一次性拍平到 [displays]，之后菜单不再访问 [InspectorDb]，
 * 也不用关心分页 —— 下一页的重新查询交给菜单层的 [AuditMenu.onNext] 回调。
 */
private class AuditContainer(rows: List<InspectorDb.QueryRow>, hasNext: Boolean) : Container {

    private val displays: Array<ItemStack?> = arrayOfNulls(AuditMenu.TOTAL_SLOTS)

    init {
        rows.take(AuditMenu.ITEM_SLOTS).forEachIndexed { index, row ->
            displays[index] = render(row)
        }
        displays[AuditMenu.SLOT_NEXT] = if (hasNext) {
            button("minecraft:arrow", "&a&l下一页", listOf("&7点击加载下一页记录"))
        } else {
            button("minecraft:barrier", "&8没有下一页了", emptyList())
        }
        displays[AuditMenu.SLOT_CLOSE] = button("minecraft:barrier", "&c&l关闭", emptyList())
    }

    // ---------- Container ----------

    override fun getContainerSize(): Int = AuditMenu.TOTAL_SLOTS

    override fun isEmpty(): Boolean = displays.all { it == null || it.isEmpty }

    override fun getItem(slot: Int): ItemStack = displays.getOrNull(slot) ?: ItemStack.EMPTY

    /** 写入路径全部禁用 —— 审计结果是只读的。 */
    override fun removeItem(slot: Int, amount: Int): ItemStack = ItemStack.EMPTY

    override fun removeItemNoUpdate(slot: Int): ItemStack = ItemStack.EMPTY

    override fun setItem(slot: Int, stack: ItemStack) = Unit

    override fun setChanged() = Unit

    override fun clearContent() = Unit

    override fun stillValid(player: Player): Boolean = true

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = false

    // ---------- 渲染 ----------

    /**
     * 把一条记录渲染成图标：名称显示「动作 + 物品」，lore 展开详情。
     *
     * 数量超过 64 时图标右上角只能显示 64（原版上限），所以**真实数量写在 lore 里**。
     */
    private fun render(row: InspectorDb.QueryRow): ItemStack {
        val stack = Icons.of(row.material)
        stack.setCount(row.amount.coerceIn(1, 64))
        val action = Actions.NAMES[row.action] ?: "?"
        stack.set(DataComponents.CUSTOM_NAME, Text.legacy("&e$action &f${row.material}"))

        val time = TIME_FORMAT.format(Date(row.time))
        val isPlayer = row.source == SourceKind.PLAYER.id
        val actorColor = if (isPlayer) "&a" else "&7"
        val lore = mutableListOf<Component>()
        lore += Text.legacy("&7操作者：$actorColor${row.actor}")
        lore += Text.legacy("&7时间：&f$time")
        lore += Text.legacy("&7动作：&e$action")
        lore += Text.legacy("&7坐标：&f${row.x}, ${row.y}, ${row.z}")
        lore += Text.legacy("&7数量：&f${row.amount}")
        lore += if (row.nbt != null) Text.legacy("&6◆ 含 NBT 数据") else Text.legacy("&8无 NBT")
        lore += Text.legacy("&7点击在聊天中查看完整信息")
        stack.set(DataComponents.LORE, ItemLore(lore))
        return stack
    }

    private fun button(icon: String, name: String, lore: List<String>): ItemStack {
        val stack = Icons.of(icon)
        stack.set(DataComponents.CUSTOM_NAME, Text.legacy(name))
        if (lore.isNotEmpty()) {
            stack.set(DataComponents.LORE, ItemLore(lore.map { Text.legacy(it) }))
        }
        return stack
    }
}

/**
 * 审计结果界面：6 行箱子，前 45 格是记录，底部一行是「下一页 / 关闭」按钮。
 *
 * 继承 [ChestMenu] 并**完全接管** [clicked]（刻意不调用 super）——
 * 这样物品既不能被拖出去、也不能被放进来；点记录格只在聊天里回显完整信息，
 * 点「下一页」交给 [onNext] 重新查询并重开界面。
 */
class AuditMenu(
    containerId: Int,
    inventory: Inventory,
    private val owner: ServerPlayer,
    private val rows: List<InspectorDb.QueryRow>,
    private val hasNext: Boolean,
    private val onNext: () -> Unit,
) : ChestMenu(MenuType.GENERIC_9x6, containerId, inventory, AuditContainer(rows, hasNext), 6) {

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        // ⚠️ 不调用 super —— 一旦调用，原版就会按 PICKUP/QUICK_MOVE 处理这个槽位，
        //    玩家可以把记录图标拖到光标上带走，破坏只读语义。
        if (player !is ServerPlayer) return
        if (slotId < 0) return

        when (slotId) {
            in 0 until ITEM_SLOTS -> rows.getOrNull(slotId)?.let { showDetail(it) }
            SLOT_NEXT -> if (hasNext) { onNext(); owner.closeContainer() }
            SLOT_CLOSE -> owner.closeContainer()
            // 玩家背包区（54~89）：什么都不做，防止往里塞东西
            else -> Unit
        }
    }

    /** 屏蔽 Shift 点击 —— 否则它会试图把容器内容「快速移动」进玩家背包。 */
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    private fun showDetail(row: InspectorDb.QueryRow) {
        owner.sendSystemMessage(formatRow(row))
    }

    companion object {
        /** 物品区槽位数（前 45 格放记录）。 */
        const val ITEM_SLOTS = 45

        /** 总槽位（6 行 × 9 列）。 */
        const val TOTAL_SLOTS = 54

        const val SLOT_NEXT = 49
        const val SLOT_CLOSE = 53

        /** 打开审计结果界面。 */
        fun open(player: ServerPlayer, rows: List<InspectorDb.QueryRow>, hasNext: Boolean, onNext: () -> Unit) {
            player.openMenu(
                SimpleMenuProvider(
                    { id, inventory, _ -> AuditMenu(id, inventory, player, rows, hasNext, onNext) },
                    Text.legacy("&8审计查询结果")
                )
            )
        }

        /** 把一条记录拼成聊天里的一行完整信息（文字模式与「点击回显」共用）。 */
        fun formatRow(row: InspectorDb.QueryRow): Component {
            val time = TIME_FORMAT.format(Date(row.time))
            val action = Actions.NAMES[row.action] ?: "?"
            val isPlayer = row.source == SourceKind.PLAYER.id
            val actorColor = if (isPlayer) "&a" else "&7"
            val count = if (row.amount > 1) " ×${row.amount}" else ""
            val nbt = if (row.nbt != null) " &6[NBT]" else ""
            return Text.legacy(
                "&7$time $actorColor${row.actor} &e$action &f${row.material}$count&7 @ ${row.x},${row.y},${row.z}$nbt"
            )
        }
    }
}
