package org.voidkit.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [CommentedJson.stripComments] 的纯字符串逻辑测试。
 *
 * 该函数不碰 Gson、不碰 Minecraft 类，只做状态机剥注释，可以脱离运行时单测。
 */
class CommentedJsonTest {

    @Test
    fun `strips line comment at end of line`() {
        val input = "{\"a\":1 // 注释\n}"
        assertEquals("{\"a\":1 \n}", CommentedJson.stripComments(input))
    }

    @Test
    fun `keeps double slash inside string literal`() {
        val input = "{\"url\":\"https://x\",\"b\":2}"
        assertEquals(input, CommentedJson.stripComments(input))
    }

    @Test
    fun `strips comment after a url value`() {
        val input = "{\"url\":\"https://x\" // 这是注释\n}"
        assertEquals("{\"url\":\"https://x\" \n}", CommentedJson.stripComments(input))
    }

    @Test
    fun `strips block comment`() {
        assertEquals("{}", CommentedJson.stripComments("/* 块注释 */{}"))
    }

    @Test
    fun `keeps double slash after escaped quote inside string`() {
        val input = "{\"a\":\"\\\" // not comment\",\"b\":1}"
        assertEquals(input, CommentedJson.stripComments(input))
    }

    @Test
    fun `input without comments is unchanged`() {
        assertEquals("{\"a\":1}", CommentedJson.stripComments("{\"a\":1}"))
    }
}
