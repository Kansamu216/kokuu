package org.voidkit.core.util

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * 物品 id 与 [ItemStack] 的互转。
 *
 * 配置里写 `minecraft:diamond` 这种字符串，比让服主去查数字 id 友好得多。
 * 解析失败**不抛异常** —— 配置写错一个字母不该让菜单打不开，
 * 而是退化成屏障方块（原版表示“这里有问题”的图标），服主一眼就能看出是哪格。
 */
object Icons {

    /** 取物品；找不到返回 null。 */
    fun itemOf(id: String?): net.minecraft.world.item.Item? {
        if (id.isNullOrBlank()) return null
        val location = ResourceLocation.tryParse(id.trim()) ?: return null
        return BuiltInRegistries.ITEM.getOptional(location).orElse(null)
    }

    /**
     * 造一个图标物品。
     *
     * @param fallbackId 解析失败时用哪个物品，默认屏障
     */
    fun of(id: String?, fallbackId: String = "minecraft:barrier"): ItemStack {
        val item = itemOf(id) ?: itemOf(fallbackId) ?: Items.BARRIER
        return ItemStack(item)
    }

    /** 物品 id 是否真实存在。菜单加载时用它校验并给出警告。 */
    fun exists(id: String?): Boolean = itemOf(id) != null
}
