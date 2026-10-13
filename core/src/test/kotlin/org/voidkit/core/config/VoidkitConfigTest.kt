package org.voidkit.core.config

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [VoidkitConfig.worldDisplayName] 的纯字符串逻辑测试。
 *
 * 该函数不碰任何 Minecraft/NeoForge 类型（入参就是维度 ID 字符串），
 * 可以脱离运行时单测 —— 这也是把它从 [org.voidkit.core.placeholder.PlaceholderRegistry]
 * 里抽出来的原因。
 */
class VoidkitConfigTest {

    private fun cfg(names: Map<String, String>) = VoidkitConfig().apply {
        worldNames = LinkedHashMap(names)
    }

    @Test
    fun `default config ships the three vanilla dimensions`() {
        val c = VoidkitConfig()
        assertEquals("主世界", c.worldDisplayName("minecraft:overworld"))
        assertEquals("下界", c.worldDisplayName("minecraft:the_nether"))
        assertEquals("末地", c.worldDisplayName("minecraft:the_end"))
    }

    @Test
    fun `matches by full dimension id`() {
        val c = cfg(mapOf("twilightforest:twilight_forest" to "&d暮色森林"))
        assertEquals("&d暮色森林", c.worldDisplayName("twilightforest:twilight_forest"))
    }

    @Test
    fun `matches by path shorthand when full id is absent`() {
        // 服主偷懒只写路径 —— 也应命中
        val c = cfg(mapOf("overworld" to "主城"))
        assertEquals("主城", c.worldDisplayName("minecraft:overworld"))
    }

    @Test
    fun `full id takes priority over path shorthand`() {
        val c = cfg(
            linkedMapOf(
                "minecraft:overworld" to "主世界",
                "overworld" to "兜底简写",
            )
        )
        assertEquals("主世界", c.worldDisplayName("minecraft:overworld"))
    }

    @Test
    fun `passes color codes through untouched`() {
        // 颜色码必须原样带出，由调用方的 Text.legacy / toSection 去解析
        val c = cfg(mapOf("minecraft:overworld" to "&a&l主世界"))
        assertEquals("&a&l主世界", c.worldDisplayName("minecraft:overworld"))
    }

    @Test
    fun `unknown dimension falls back to raw id`() {
        val c = cfg(mapOf("minecraft:overworld" to "主世界"))
        assertEquals("aether:the_aether", c.worldDisplayName("aether:the_aether"))
    }

    @Test
    fun `empty map means show raw id for everything`() {
        val c = cfg(emptyMap())
        assertEquals("minecraft:overworld", c.worldDisplayName("minecraft:overworld"))
    }

    @Test
    fun `normalize keeps an intentionally empty map`() {
        // 空 map 是用户的有意选择（全部按路径原文显示）→ normalize 不能把它填回默认值
        val c = cfg(emptyMap())
        c.normalize()
        assertEquals("minecraft:overworld", c.worldDisplayName("minecraft:overworld"))
        assertEquals(0, c.worldNames.size)
    }

    @Test
    fun `normalize leaves a populated map untouched`() {
        val c = cfg(mapOf("minecraft:overworld" to "&a主城"))
        c.normalize()
        assertEquals("&a主城", c.worldDisplayName("minecraft:overworld"))
    }
}
