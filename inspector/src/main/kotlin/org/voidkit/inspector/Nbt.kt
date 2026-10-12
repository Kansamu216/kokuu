package org.voidkit.inspector

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * NBT 的编解码，以及「从世界对象里提取方块 / 物品身份」的工具。
 *
 * 原则：NBT 只在**游戏主线程**提取（方块实体是世界状态，离线程读不安全），
 * 转成不可变的 [ByteArray] 后再交给写线程，写线程只碰字节流。
 */
object Nbt {

    /** 把 [CompoundTag] 编码成二进制（未压缩，快；NBT 本身够小）。 */
    fun encode(tag: CompoundTag?): ByteArray? {
        if (tag == null) return null
        return runCatching {
            val bos = ByteArrayOutputStream()
            DataOutputStream(bos).use { NbtIo.write(tag, it) }
            bos.toByteArray()
        }.getOrNull()
    }

    /** 反向解码。 */
    fun decode(bytes: ByteArray?): CompoundTag? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            DataInputStream(ByteArrayInputStream(bytes)).use { NbtIo.read(it) }
        }.getOrNull()
    }

    /** 方块注册名（`minecraft:stone`）。 */
    fun blockId(state: BlockState): String =
        BuiltInRegistries.BLOCK.getKey(state.block)?.toString() ?: "minecraft:air"

    /** 物品注册名（`mekanism:ingot_steel`）。 */
    fun itemId(stack: ItemStack): String =
        BuiltInRegistries.ITEM.getKey(stack.item)?.toString() ?: "minecraft:air"

    /**
     * 方块实体的 NBT（箱子内容、机器状态、告示牌文字、Create 装置…）。
     *
     * 没有方块实体（普通石头）就返回 null，不浪费一个字节。
     */
    fun blockEntityNbt(level: Level, pos: BlockPos): ByteArray? {
        val be = level.getBlockEntity(pos) ?: return null
        return encode(runCatching { be.saveWithFullMetadata(level.registryAccess()) }.getOrNull())
    }

    /**
     * 物品组件的 NBT（附魔、自定义名、整合包物品的私有数据）。
     *
     * 只有**带组件**的物品才存 —— 一块普通圆石没有组件，存了就是浪费。
     * 返回的是包含 `id`/`count`/`components` 的完整标签，便于将来回滚重建。
     */
    fun itemNbt(stack: ItemStack, registry: HolderLookup.Provider): ByteArray? {
        if (stack.components.isEmpty()) return null
        // save 返回 Tag（实际是 CompoundTag），先收敛成 CompoundTag 再编码
        val tag = runCatching { stack.save(registry) }.getOrNull() as? CompoundTag ?: return null
        return encode(tag)
    }
}
