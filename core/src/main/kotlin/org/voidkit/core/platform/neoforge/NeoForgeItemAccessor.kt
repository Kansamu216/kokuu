package org.voidkit.core.platform.neoforge

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.voidkit.core.api.ItemAccessor

/**
 * NeoForge 1.21.1 的物品数据实现（DataComponent 版）。
 *
 * 移植 1.20.1 时新增一份用 NBT 的实现（自定义名写 `display.name` 标签），
 * 业务代码零改动。
 */
object NeoForgeItemAccessor : ItemAccessor {

    override fun hasCustomName(stack: ItemStack): Boolean = stack.has(DataComponents.CUSTOM_NAME)

    override fun setCustomName(stack: ItemStack, name: Component) {
        stack.set(DataComponents.CUSTOM_NAME, name)
    }

    override fun setLore(stack: ItemStack, lore: List<Component>) {
        stack.set(DataComponents.LORE, ItemLore(lore))
    }

    override fun hasExtraComponents(stack: ItemStack): Boolean = !stack.isComponentsPatchEmpty
}
