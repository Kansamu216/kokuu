package org.voidkit.utility

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 垃圾桶的价值评分与排序。
 *
 * 核心的价值评分（[TrashValue.score] / [TrashValue.keyOf] / [TrashValue.idOf]）与
 * 排序（[TrashBin.trim] / [TrashBin.snapshot]）全部依赖 Minecraft 的 ItemStack /
 * BuiltInRegistries / DataComponents / Rarity 等类，无法在纯 JVM 单元测试里脱离
 * Minecraft 运行时构造 ItemStack，故这些部分跳过。
 *
 * 这里只测不依赖 MC 类的纯部分：[TrimResult]。
 */
class TrashBinTest {

    @Test
    fun `trim result is not trimmed when nothing dropped`() {
        assertFalse(TrimResult(0, 0L).trimmed)
    }

    @Test
    fun `trim result is trimmed when types were dropped`() {
        assertTrue(TrimResult(1, 0L).trimmed)
    }

    @Test
    fun `trim result is trimmed when items were dropped`() {
        assertTrue(TrimResult(0, 5L).trimmed)
    }
}
