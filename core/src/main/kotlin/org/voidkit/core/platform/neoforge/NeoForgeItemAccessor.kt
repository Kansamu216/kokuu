package org.voidkit.core.platform.neoforge

import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.voidkit.core.api.ItemAccessor

/**
 * 1.20.1 的物品数据实现（**NBT 版**）。
 *
 * DataComponent 系统是 1.20.5 才引入的；1.20.1 里物品的自定义名与 Lore 都存在
 * NBT 的 `display.name` / `display.Lore` 下。这里就是 [ItemAccessor] 抽象
 * **唯一**需要为版本差异改动的实现 —— 插件侧一行都不用动
 * （这正是当初立 `VItems` 抽象的目的，见 [org.voidkit.core.api.VItem]）。
 *
 * 与 1.21 版（`main` 分支）的对照：
 * ```
 * 1.21 : stack.has(DataComponents.CUSTOM_NAME)  / stack.set(DataComponents.LORE, ItemLore(...))
 * 1.20 : stack.hasCustomHoverName()             / tag.display.Lore（ListTag<StringTag(JSON)>）
 * ```
 */
object NeoForgeItemAccessor : ItemAccessor {

    private const val DISPLAY = "display"
    private const val LORE = "Lore"

    override fun hasCustomName(stack: ItemStack): Boolean = stack.hasCustomHoverName()

    override fun setCustomName(stack: ItemStack, name: Component) {
        stack.setHoverName(name)
    }

    override fun setLore(stack: ItemStack, lore: List<Component>) {
        val list = ListTag()
        lore.forEach { list.add(StringTag.valueOf(Component.Serializer.toJson(it))) }
        stack.getOrCreateTagElement(DISPLAY).put(LORE, list)
    }

    override fun hasExtraComponents(stack: ItemStack): Boolean = stack.tag?.isEmpty == false
}
