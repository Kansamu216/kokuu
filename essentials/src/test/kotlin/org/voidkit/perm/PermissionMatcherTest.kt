package org.voidkit.perm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [matchesPermissionNode] 的纯字符串匹配逻辑测试（不依赖 PermissionStore / Minecraft）。
 */
class PermissionMatcherTest {

    private val wildcard = true

    @Test
    fun `prefix wildcard matches descendant node`() {
        assertTrue(matchesPermissionNode("a.b.*", "a.b.c", wildcard))
    }

    @Test
    fun `prefix wildcard does not match a sibling prefix`() {
        assertFalse(matchesPermissionNode("a.b.*", "a.bc", wildcard))
    }

    @Test
    fun `prefix wildcard does not match the parent itself`() {
        assertFalse(matchesPermissionNode("a.b.*", "a.b", wildcard))
    }

    @Test
    fun `exact node matches itself`() {
        assertTrue(matchesPermissionNode("a.b", "a.b", wildcard))
    }

    @Test
    fun `exact node does not match a longer node`() {
        assertFalse(matchesPermissionNode("a.b", "a.bc", wildcard))
    }

    @Test
    fun `star matches everything`() {
        assertTrue(matchesPermissionNode("*", "a.b.c", wildcard))
        assertTrue(matchesPermissionNode("*", "x", wildcard))
    }

    @Test
    fun `bare prefix matches descendant node`() {
        // 裸前缀写法：voidkit.home 命中 voidkit.home.set
        assertTrue(matchesPermissionNode("a.b", "a.b.c", wildcard))
    }

    @Test
    fun `negation prefix is stripped before matching`() {
        assertTrue(matchesPermissionNode("-a.b", "a.b", wildcard))
        assertTrue(matchesPermissionNode("-a.b.*", "a.b.c", wildcard))
    }

    @Test
    fun `blank pattern never matches`() {
        assertFalse(matchesPermissionNode("", "a.b", wildcard))
        assertFalse(matchesPermissionNode("-", "a.b", wildcard))
    }

    @Test
    fun `wildcard disabled falls back to exact match only`() {
        assertTrue(matchesPermissionNode("a.b", "a.b", false))
        assertFalse(matchesPermissionNode("a.b.*", "a.b.c", false))
        assertFalse(matchesPermissionNode("*", "a.b.c", false))
        assertFalse(matchesPermissionNode("a.b", "a.b.c", false))
    }
}
