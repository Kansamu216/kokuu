package org.voidkit.core.api

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

/**
 * 物品自定义数据的跨版本读写契约。
 *
 * 1.20.1 与 1.21.1 的物品系统完全不同：1.20.1 用 NBT（自定义名在 `display.name`），
 * 1.21.1 用 DataComponent。业务代码一律走 [VItems]，由 platform 层按目标版本实现
 * —— 这样同一份业务代码能编译到多个 Minecraft 版本。
 *
 * ⚠️ 本文件只允许用 `net.minecraft.*`（三版本同包同名），**不许**引用任何平台相关
 * API（DataComponents / NBT 标签类等），否则跨版本编译时直接失败。
 */
interface ItemAccessor {

    /** 是否有自定义名称（玩家改过名的物品）。 */
    fun hasCustomName(stack: ItemStack): Boolean

    /** 设置自定义名称。 */
    fun setCustomName(stack: ItemStack, name: Component)

    /** 设置 lore（鼠标悬停说明）。 */
    fun setLore(stack: ItemStack, lore: List<Component>)

    /**
     * 是否有非默认组件（影响物品实质：药水 / 附魔 / 成书 / 地图等）。
     * 垃圾桶按这个判断物品是否该按变体区分聚合。
     */
    fun hasExtraComponents(stack: ItemStack): Boolean
}

/**
 * 物品数据入口。启动时由 platform 层 `install` 具体实现。
 */
object VItems {

    @Volatile
    private var impl: ItemAccessor? = null

    /** 由内核启动时调用（platform 层实现注册进来）。 */
    fun install(accessor: ItemAccessor) {
        impl = accessor
    }

    private val accessor: ItemAccessor
        get() = impl ?: error("VItems 未初始化（platform 层未 install）")

    fun hasCustomName(stack: ItemStack): Boolean = accessor.hasCustomName(stack)

    fun setCustomName(stack: ItemStack, name: Component) = accessor.setCustomName(stack, name)

    fun setLore(stack: ItemStack, lore: List<Component>) = accessor.setLore(stack, lore)

    fun hasExtraComponents(stack: ItemStack): Boolean = accessor.hasExtraComponents(stack)
}
