package org.voidkit.core.platform.fabric

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore
import org.voidkit.core.api.ItemAccessor

/**
 * Fabric 1.21.1 的物品数据实现（DataComponent 版）。
 *
 * ⚠️ 与 NeoForge 线的那份**代码完全相同** —— 因为这里用到的
 * `DataComponents` / `ItemLore` / `ItemStack` 全是 Mojang 原版类，
 * 三平台同包同名，跟加载器一点关系都没有。
 *
 * 这也侧面验证了平台分层的判断：真正需要按加载器重写的只有
 * 事件总线 / 假玩家 / 游戏目录 / mod 入口这四样，物品组件不是。
 *
 * 移植到 Fabric 1.20.1 时，这一份要换成 NBT 实现（`display.name` / `display.Lore`）。
 */
object FabricItemAccessor : ItemAccessor {

    override fun hasCustomName(stack: ItemStack): Boolean = stack.has(DataComponents.CUSTOM_NAME)

    override fun setCustomName(stack: ItemStack, name: Component) {
        stack.set(DataComponents.CUSTOM_NAME, name)
    }

    override fun setLore(stack: ItemStack, lore: List<Component>) {
        stack.set(DataComponents.LORE, ItemLore(lore))
    }

    /**
     * 除了自定义名/Lore 之外，物品上还挂着别的组件吗？
     *
     * ⚠️ NeoForge 线上这里用的是 `!stack.isComponentsPatchEmpty` —— 那是 **NeoForge
     * 给 ItemStack 打的补丁方法**，纯原版/官方映射里没有。Fabric 下用等价的
     * `componentsPatch.isEmpty`（`getComponentsPatch()` 返回的 `DataComponentPatch`
     * 自带 `isEmpty()`）。
     */
    override fun hasExtraComponents(stack: ItemStack): Boolean = !stack.componentsPatch.isEmpty
}
