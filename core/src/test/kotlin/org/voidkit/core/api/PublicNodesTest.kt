package org.voidkit.core.api

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PublicNodes] 的纯逻辑测试。
 *
 * 它决定「玩家日常命令默认能不能用」，判错方向就是两种事故：
 * - 误判为公开 → 管理命令对所有人开放；
 * - 漏判 → 零配置下玩家连 `/warp` 都用不了。
 */
class PublicNodesTest {

    @AfterTest
    fun cleanup() {
        // 这个对象是全局单例，测试之间要清干净（用反射拿到内部集合直接清）
        val field = PublicNodes::class.java.getDeclaredField("nodes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(PublicNodes) as MutableSet<String>).clear()
    }

    @Test
    fun `registered node is public`() {
        PublicNodes.register("voidkit.command.warp")
        assertTrue(PublicNodes.isPublic("voidkit.command.warp"))
    }

    @Test
    fun `unregistered node is not public`() {
        PublicNodes.register("voidkit.command.warp")
        assertFalse(PublicNodes.isPublic("voidkit.command.setwarp"))
    }

    @Test
    fun `matching is case insensitive`() {
        PublicNodes.register("voidkit.command.warp")
        assertTrue(PublicNodes.isPublic("VOIDKIT.COMMAND.WARP"))
    }

    @Test
    fun `blank node is never public`() {
        PublicNodes.register("voidkit.command.warp")
        assertFalse(PublicNodes.isPublic(""))
        assertFalse(PublicNodes.isPublic("   "))
    }

    @Test
    fun `register ignores blank and supports vararg`() {
        PublicNodes.register("", "  ", "voidkit.command.hat", "voidkit.command.enderchest")
        assertEquals(
            setOf("voidkit.command.enderchest", "voidkit.command.hat"),
            PublicNodes.all()
        )
    }

    @Test
    fun `all returns sorted snapshot`() {
        PublicNodes.register("voidkit.command.spawn", "voidkit.command.back")
        assertEquals(
            listOf("voidkit.command.back", "voidkit.command.spawn"),
            PublicNodes.all().toList()
        )
    }
}
