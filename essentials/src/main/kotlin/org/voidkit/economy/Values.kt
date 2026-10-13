package org.voidkit.economy

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity
import org.voidkit.core.util.ConfigFiles

/**
 * 物品估值表 `economy/values.json` 的结构。
 *
 * 键有两种写法：
 *  - 精确物品 id：`minecraft:diamond`
 *  - 物品标签：`#minecraft:logs`、`#c:ingots`（以 `#` 开头）
 *
 * ⚠️ 由 Gson 反序列化，Kotlin 默认参数不生效 —— 缺字段会落成 null，所以
 * [normalize] 必须兜住。负价、null 价一律丢弃，避免卖出倒贴钱。
 */
class ValueFile {

    /** 估值表：物品 id 或 `#标签` → 单价（不含数量）。 */
    var values: MutableMap<String, Double> = linkedMapOf()

    /**
     * 没命中任何条目时的兜底定价：
     *  - `none`   不估价（单价 0，无法出售）
     *  - `rarity` 按稀有度估价（COMMON=1、UNCOMMON=5、RARE=20、EPIC=100）
     */
    var fallback: String = "none"

    fun normalize() {
        // 用 src 临时接收 Gson 产物，再重建成真正的可变 LinkedHashMap ——
        // 这样后续 removeAll / put 都不会踩到 Gson 内部 Map 的坑。
        val src = values
        values = linkedMapOf()
        src?.forEach { (k, v) ->
            if (k != null && v != null && v >= 0) values[k] = v
        }
        if (fallback == null || fallback !in setOf("none", "rarity")) fallback = "none"
    }
}

/** 估值表的注释（写盘时插入 JSON）。 */
object ValueComments {

    val VALUES: Map<String, String> = mapOf(
        "values" to "物品估值表：键为物品 id（minecraft:diamond）或物品标签（#minecraft:logs，以 # 开头）。\n" +
            "查找优先级：精确 id > 标签 > fallback。单价不含数量，卖出时按堆叠数量累乘。",
        "fallback" to "没命中任何条目时的兜底定价：\n" +
            "  none   不估价（单价 0，无法出售）\n" +
            "  rarity 按稀有度估价（COMMON=1、UNCOMMON=5、RARE=20、EPIC=100）",
    )
}

/**
 * 物品估值服务。
 *
 * 只做「给定物品 → 单价」，不含数量。卖出时的总价由调用方自己 `单价 × 数量` 并过
 * [EconomyStore.round] 规整。文件读写复用 [ConfigFiles]，落盘带中文注释。
 */
object Values {

    const val FILE = "economy/values.json"

    @Volatile
    var file: ValueFile = ValueFile()
        private set

    /** 读取（不存在或解析失败时用内置默认并落盘）。 */
    fun load() {
        file = ConfigFiles.load(FILE, ValueComments.VALUES) { defaultFile() }
        file.normalize()
    }

    fun save() = ConfigFiles.save(FILE, file, ValueComments.VALUES)

    /** 内置默认：给几个示例，方便服主照葫芦画瓢。 */
    private fun defaultFile(): ValueFile = ValueFile().apply {
        values = linkedMapOf(
            "minecraft:diamond" to 100.0,
            "minecraft:iron_ingot" to 8.0,
            "minecraft:gold_ingot" to 12.0,
            "minecraft:emerald" to 150.0,
            "#minecraft:logs" to 1.5,
            "#minecraft:planks" to 0.5,
        )
        fallback = "none"
    }

    /**
     * 查某件物品的**单价**（不含数量）。
     *
     * 优先级：
     *  1. 精确物品 id 命中
     *  2. 遍历物品标签，`#标签` 命中
     *  3. fallback：`none` → 0；`rarity` → 按稀有度映射
     */
    fun valueOf(stack: ItemStack): Double {
        val values = file.values
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        values[id]?.let { return it }

        // getTags() 返回的是 Stream，forEach 是非内联的，非局部 return 会被禁止，
        // 所以这里用迭代器手动遍历，命中即返回。
        val tags = stack.getTags().iterator()
        while (tags.hasNext()) {
            val v = values["#" + tags.next().location()]
            if (v != null) return v
        }

        return when (file.fallback) {
            "rarity" -> when (stack.rarity) {
                Rarity.COMMON -> 1.0
                Rarity.UNCOMMON -> 5.0
                Rarity.RARE -> 20.0
                Rarity.EPIC -> 100.0
            }
            else -> 0.0
        }
    }

    /**
     * 按字符串键查单价，供 `/value` 命令用。
     *
     * 先按原样查（支持直接写 `#minecraft:logs`），没命中再补 `#` 前缀查一次，
     * 这样玩家输入 `minecraft:logs` 也能命中标签条目。
     */
    fun valueOf(id: String): Double {
        val values = file.values
        values[id]?.let { return it }
        if (!id.startsWith("#")) values["#$id"]?.let { return it }
        return 0.0
    }

    /** 供 `/value` 补全：估值表 key 去掉标签前缀 `#`。 */
    fun suggestKeys(): List<String> = file.values.keys.map { it.removePrefix("#") }
}
