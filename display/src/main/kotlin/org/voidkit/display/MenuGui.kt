package org.voidkit.display

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
import org.voidkit.core.api.VItems
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.util.Icons
import org.voidkit.core.util.Msg
import org.voidkit.core.util.Text
import java.util.concurrent.ConcurrentHashMap

/**
 * 菜单界面的只读容器。
 *
 * 和垃圾桶那种“动态内容”不同，菜单是**静态**的 —— 定义在配置里，打开时按槽位把
 * 图标渲染好，点击由 [MenuGui] 处理。
 *
 * ⚠️ 容器大小**必须等于 `行数 × 9`**：原版 [ChestMenu] 构造时会用 `checkContainerSize`
 * 校验，不匹配会直接抛异常（玩家会看到“打开界面失败”）。所以这里不能固定写 54，
 * 必须跟着 `rows` 走。
 */
class MenuContainer(private val def: MenuDef) : Container {

    private val size: Int = def.capacity()

    private val slots: Array<ItemStack?> = arrayOfNulls(size)

    init {
        def.items.forEach { (slotKey, item) ->
            val slot = slotKey.toIntOrNull() ?: return@forEach
            if (slot < 0 || slot >= size) return@forEach
            slots[slot] = render(item)
        }
    }

    fun defAt(slot: Int): MenuItemDef? = if (slot in 0 until size) def.items[slot.toString()] else null

    override fun getContainerSize(): Int = size

    override fun isEmpty(): Boolean = slots.none { it != null }

    override fun getItem(slot: Int): ItemStack = slots.getOrNull(slot) ?: ItemStack.EMPTY

    // ---------- 只读 ----------

    override fun removeItem(slot: Int, amount: Int): ItemStack = ItemStack.EMPTY

    override fun removeItemNoUpdate(slot: Int): ItemStack = ItemStack.EMPTY

    override fun setItem(slot: Int, stack: ItemStack) = Unit

    override fun setChanged() = Unit

    override fun clearContent() = Unit

    override fun stillValid(player: Player): Boolean = true

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = false

    // ---------- 渲染 ----------

    private fun render(item: MenuItemDef): ItemStack {
        val stack = Icons.of(item.icon)
        stack.setCount(item.amount.coerceIn(1, 64))
        if (item.name.isNotBlank()) {
            VItems.setCustomName(stack, Text.legacy(item.name))
        }
        val lines = ArrayList<Component>()
        item.lore.filter { it.isNotBlank() }.forEach { lines += Text.legacy(it) }
        if (lines.isNotEmpty()) {
            VItems.setLore(stack, lines)
        }
        return stack
    }

    companion object {
        /** 行数 -> 原版的箱子菜单类型。 */
        fun menuTypeFor(rows: Int): MenuType<ChestMenu> = when (rows.coerceIn(1, 6)) {
            1 -> MenuType.GENERIC_9x1
            2 -> MenuType.GENERIC_9x2
            3 -> MenuType.GENERIC_9x3
            4 -> MenuType.GENERIC_9x4
            5 -> MenuType.GENERIC_9x5
            else -> MenuType.GENERIC_9x6
        }
    }
}

/**
 * 菜单界面。
 *
 * 和垃圾桶界面一样**完全接管** [clicked]（不调用 super）—— 菜单里的图标是”按钮"
 * 而不是“物品”，不能让玩家拖进背包，也不能往格子里放东西。
 *
 * ## 动作语法
 *
 * | 写法 | 效果 |
 * |---|---|
 * | `run:/spawn` | **以玩家身份**执行命令（走他真实的权限） |
 * | `console:/time set day` | 以控制台身份执行（绕过权限，只用于管理员编写的可信菜单） |
 * | `menu:shop` | 打开另一个菜单（可 `back` 返回） |
 * | `back` | 返回上一个菜单 |
 * | `close` | 关闭界面 |
 * | `url:https://…` | 发一条可点击链接 |
 * | `copy:文本` | 发一条可复制文本 |
 * | `suggest:/msg ` | 发一条“点击填入聊天框”的提示 |
 *
 * ⚠️ `url` / `copy` / `suggest` 三类**无法在点击格子时立即生效** —— 打开链接、
 * 写剪贴板、填聊天框都必须由客户端自己发起。它们会退化成“发一条可点击的聊天提示”，
 * 玩家再点一下才生效。这是原版协议的限制，不是实现偷懒。
 */
class MenuGui(
    containerId: Int,
    inventory: Inventory,
    private val def: MenuDef,
    private val menuName: String,
    private val container: MenuContainer,
) : ChestMenu(
    MenuContainer.menuTypeFor(def.rows),
    containerId,
    inventory,
    container,
    def.rows.coerceIn(1, 6)
) {

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        // ⚠️ 不调用 super —— 一旦调用，原版会按 PICKUP/QUICK_MOVE 处理，
        //    玩家能把菜单图标拖到光标上带走。
        if (player !is ServerPlayer) return
        val item = container.defAt(slotId) ?: return

        if (item.permission.isNotBlank() && !VoidkitServices.permission().has(player, item.permission)) {
            Msg.err(player, "你没有使用这一项的权限")
            return
        }
        if (item.action.isBlank()) return

        runAction(player, item.action.trim())
    }

    /** 屏蔽 Shift 点击 —— 否则它会试图把容器内容“快速移动”进玩家背包。 */
    override fun quickMoveStack(player: Player, index: Int): ItemStack = ItemStack.EMPTY

    // ---------- 动作分发 ----------

    private fun runAction(player: ServerPlayer, action: String) {
        val lower = action.lowercase()
        when {
            lower == "close" -> player.closeContainer()

            lower == "back" -> {
                val previous = MenuNav.pop(player)
                if (previous != null) MenuFeatureOpener.open(player, previous) else player.closeContainer()
            }

            lower.startsWith("run:") -> execute(player, action.substring(4).trim(), asConsole = false)

            lower.startsWith("console:") -> execute(player, action.substring(8).trim(), asConsole = true)

            lower.startsWith("menu:") -> {
                val target = action.substring(5).trim()
                if (MenuStore.menuOf(target) == null) {
                    Msg.err(player, "菜单 $target 不存在")
                    return
                }
                MenuNav.push(player, menuName)
                MenuFeatureOpener.open(player, target)
            }

            // 下面三种客户端必须自己响应，服务端只能发提示
            lower.startsWith("url:") -> prompt(player, action.substring(4).trim(), "url")
            lower.startsWith("copy:") -> prompt(player, action.substring(5).trim(), "copy")
            lower.startsWith("suggest:") -> prompt(player, action.substring(8).trim(), "suggest")

            else -> Msg.err(player, "未知的菜单动作：$action")
        }
    }

    private fun execute(player: ServerPlayer, command: String, asConsole: Boolean) {
        if (command.isBlank()) return
        val cmd = command.removePrefix("/")
        runCatching {
            val source = if (asConsole) player.server.createCommandSourceStack() else player.createCommandSourceStack()
            player.server.commands.performPrefixedCommand(source, cmd)
        }.onFailure {
            Msg.err(player, "菜单命令执行失败：${it.message}")
        }
        // 菜单点完通常就该关掉，否则界面会挡住命令的反馈消息
        if (MenuStore.file.settings.closeOnAction) player.closeContainer()
    }

    /** 发一条可点击的聊天提示 —— 用于 url / copy / suggest 这三类必须客户端响应的动作。 */
    private fun prompt(player: ServerPlayer, value: String, kind: String) {
        val label = when (kind) {
            "copy" -> "&b点击复制：&f$value"
            "suggest" -> "&b点击填入聊天框：&f$value"
            else -> "&b点击打开链接：&f$value"
        }
        // 富文本 DSL：[显示文字](动作 "悬停提示")
        // ⚠️ 动作部分不能带颜色码 —— 解析时取 ":" 之前的内容当动作名，颜色码会让它认不出来
        player.sendSystemMessage(
            Msg.prefix().append(Text.legacy("&7$label  &8[&a点这里&8]($kind:$value \"&7点一下\")"))
        )
        if (MenuStore.file.settings.closeOnAction) player.closeContainer()
    }
}

/**
 * 菜单导航栈。
 *
 * 支撑 `menu:` 里配 `back` 返回 —— 每个玩家一条独立历史。
 * 用 [ConcurrentHashMap] 是因为玩家可能在网络线程与主线程上同时触发，
 * 虽然实际上几乎只会走主线程，但这样写的成本为零。
 */
object MenuNav {

    private const val MAX_DEPTH = 16

    private val history = ConcurrentHashMap<String, ArrayDeque<String>>()

    fun push(player: ServerPlayer, menu: String) {
        val stack = history.computeIfAbsent(player.uuid.toString()) { ArrayDeque() }
        // 防止递归菜单把内存撑爆
        if (stack.size >= MAX_DEPTH) stack.removeFirst()
        stack.addLast(menu)
    }

    fun pop(player: ServerPlayer): String? = history[player.uuid.toString()]?.removeLastOrNull()

    fun clear(player: ServerPlayer) {
        history.remove(player.uuid.toString())
    }
}

/**
 * 打开菜单的入口。
 *
 * 单独抽出来是为了让 [MenuGui] 不必反向依赖 [MenuFeature]（那会形成循环引用）。
 */
object MenuFeatureOpener {

    fun open(player: ServerPlayer, menuName: String) {
        val def = MenuStore.menuOf(menuName) ?: run {
            Msg.err(player, "菜单 $menuName 不存在")
            return
        }
        val container = MenuContainer(def)
        player.openMenu(
            SimpleMenuProvider(
                { id, inventory, _ -> MenuGui(id, inventory, def, menuName, container) },
                Text.legacy(def.title)
            )
        )
        playOpenSound(player)
    }

    private fun playOpenSound(player: ServerPlayer) {
        val id = MenuStore.file.settings.openSound
        if (id.isBlank()) return
        runCatching {
            val location = net.minecraft.resources.ResourceLocation.tryParse(id) ?: return
            val sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(location).orElse(null)
                ?: return
            player.playNotifySound(sound, net.minecraft.sounds.SoundSource.MASTER, 1f, 1f)
        }
    }
}
