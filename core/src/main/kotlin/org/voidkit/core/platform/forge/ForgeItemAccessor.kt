package org.voidkit.core.platform.forge

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.voidkit.core.api.ItemAccessor

/**
 * Forge 1.21.1 的物品数据实现（DataComponent 版，与 NeoForge 版等价）。
 *
 * ⚠️ 与 NeoForge 版的**唯一**差异：[hasExtraComponents]。
 * NeoForge 提供 `ItemStack#isComponentsPatchEmpty` 这个便捷方法，
 * Forge / 原版没有 —— 用原版的 `getComponentsPatch().isEmpty()` 替代。
 */
object ForgeItemAccessor : ItemAccessor {

    override fun hasCustomName(stack: ItemStack): Boolean = stack.has(DataComponents.CUSTOM_NAME)

    override fun setCustomName(stack: ItemStack, name: Component) {
        stack.set(DataComponents.CUSTOM_NAME, name)
    }

    override fun setLore(stack: ItemStack, lore: List<Component>) {
        stack.set(DataComponents.LORE, ItemLore(lore))
    }

    override fun hasExtraComponents(stack: ItemStack): Boolean = !stack.componentsPatch.isEmpty()
}
