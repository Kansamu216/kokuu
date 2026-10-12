package org.voidkit.utility

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Rarity

/**
 * 垃圾桶里的一种物品（**按类型聚合**）。
 *
 * [sample] 只用来渲染图标和判定价值，它的数量恒为 1；
 * 真实数量在 [count] 里，用 `Long` 是因为产线跑一小时凑出几百万个圆石很常见。
 */
class BinEntry(
    /** 聚合键，见 [TrashValue.keyOf]。 */
    val key: String,
    /** 样本物品（数量 1），渲染与判定都用它。 */
    val sample: ItemStack,
    /** 这个类型被回收的总数量。 */
    var count: Long,
    /** 价值分，越大越「值得留」。超容量时按它排序。 */
    val score: Long,
) {
    /** 这个类型是不是配置里显式保护的。 */
    val valuable: Boolean get() = score >= TrashValue.PROTECTED_THRESHOLD
}

/** 一次超容量裁剪的结果，用于日志和播报。 */
class TrimResult(
    /** 因为超出格数上限而被直接丢弃的类型数。 */
    val droppedTypes: Int,
    /** 被丢弃的物品总数。 */
    val droppedItems: Long,
) {
    val trimmed: Boolean get() = droppedItems > 0 || droppedTypes > 0
}

/**
 * 清扫垃圾桶。
 *
 * ## 为什么需要它
 *
 * 原本清扫是「遍历 → 直接 `discard()`」——干净利落，但有两个问题：
 *
 *  1. **误伤**：玩家刚想捡的一箱矿、机器刚产出的成品，一并消失，找不回来；
 *  2. **无法解释**：服主只看到「已清理 3 万个实体」，不知道清掉了什么。
 *
 * 现在改成「收进垃圾桶」，玩家可以在下一次清扫前自己取回。
 *
 * ## 两个关键设计
 *
 * ### 一、按类型聚合，而不是按堆栈
 *
 * 掉落物数量是无上限的，**类型数**却不是 —— 一个跑满的产线吐出几十种物品顶天了。
 * 所以这里用 `Map<物品键, 数量>`：1 万个圆石是**一个条目**（显示成 1 格、数量 10000），
 * 而不是 1 万格。这是「不会因为掉落物太多崩掉」的根本原因 ——
 * 内存占用跟着**类型数**走，与掉落物总量无关。
 *
 * ### 二、超容量时按价值淘汰，而不是先到先得
 *
 * 类型数仍然可能超过格数上限（整合包尤其如此：几十种矿物碎块、几十种模组零件）。
 * 这时**不能**简单丢掉新来的 —— 那会变成「垃圾把钻石挤出去」，正好和垃圾桶的目的相反。
 * 所以满了以后按 [score] 排序，把**分数最低的**赶出去（通常是圆石、泥土、砂砾），
 * 分数高的（带附魔的装备、改名过的物品、稀有度高的）永远留得住。
 *
 * 另外对垃圾清单里的物品单独设了「每类保留上限」（[ClearLagConfig.junkKeepLimit]）：
 * 圆石留 512 个够了，多余的连进垃圾桶都不进，直接在清扫阶段丢掉。
 *
 * ## 线程
 *
 * 只在服务端主线程访问（清扫、命令、GUI 点击都在主线程），所以用普通
 * `LinkedHashMap` 即可，不需要并发容器。
 */
class TrashBin {

    /** 聚合条目。`LinkedHashMap` 保证 GUI 里的顺序稳定（不会每次打开都跳）。 */
    private val entries = LinkedHashMap<String, BinEntry>()

    /** 上一次清空的时间戳；0 表示还没清过。 */
    var clearedAt: Long = 0L
        private set

    /** 累计清空过几轮，用于面板显示。 */
    var rounds: Long = 0L
        private set

    // ---------- 写入 ----------

    /**
     * 收一个物品进桶。
     *
     * @return 实际收进来的数量（可能因为垃圾保留上限被截断）
     */
    fun add(stack: ItemStack, amount: Long, cfg: ClearLagConfig): Long {
        if (amount <= 0 || stack.isEmpty) return 0

        val key = TrashValue.keyOf(stack)
        val score = TrashValue.score(stack, cfg)
        val existing = entries[key]

        // 垃圾物品有每类保留上限 —— 圆石这种东西留一点意思一下就够了
        val limit = if (score < 0) cfg.junkKeepLimit.toLong() else Long.MAX_VALUE
        val current = existing?.count ?: 0L
        val accepted = if (limit == Long.MAX_VALUE) amount else (limit - current).coerceAtLeast(0L)
        if (accepted <= 0) return 0

        if (existing != null) {
            existing.count += accepted
            return accepted
        }

        val sample = stack.copy().also { it.setCount(1) }
        entries[key] = BinEntry(key, sample, accepted, score)
        return accepted
    }

    /**
     * 裁到 [maxTypes] 个条目以内。
     *
     * 淘汰顺序：价值分低的先走；同分时**数量多的先走**（数量多说明是产线垃圾，
     * 而不是稀有物）。
     */
    fun trim(maxTypes: Int): TrimResult {
        if (entries.size <= maxTypes) return TrimResult(0, 0)

        val doomed = entries.values
            .sortedWith(compareBy({ it.score }, { -it.count }))
            .take(entries.size - maxTypes)

        var droppedItems = 0L
        doomed.forEach {
            droppedItems += it.count
            entries.remove(it.key)
        }
        return TrimResult(doomed.size, droppedItems)
    }

    // ---------- 读取 ----------

    /** 当前全部条目，已按价值从高到低、同分按数量从多到少排好，供 GUI 渲染。 */
    fun snapshot(): List<BinEntry> =
        entries.values.sortedWith(compareByDescending<BinEntry> { it.score }.thenByDescending { it.count })

    fun size(): Int = entries.size

    fun totalItems(): Long = entries.values.sumOf { it.count }

    fun isEmpty(): Boolean = entries.isEmpty()

    /** 取回某个条目的全部物品，并从桶里移除。找不到返回 null。 */
    fun take(key: String): BinEntry? = entries.remove(key)

    /** 清空（下一轮清扫开始时调用，或管理员手动清）。 */
    fun clear() {
        entries.clear()
        clearedAt = System.currentTimeMillis()
        rounds++
    }

    fun byKey(key: String): BinEntry? = entries[key]
}

/**
 * 物品价值判定。
 *
 * 目标是「机器产出的垃圾」和「玩家真正在乎的东西」能分开。没有引入任何外部
 * 数据源（不查价格表、不联网），纯靠物品自身属性推断 —— 因为在整合包里
 * 「物品值多少钱」根本无从得知，但「它是不是一把附魔过的剑」是确定的。
 */
object TrashValue {

    /** 分数高于这个值就视为「受保护」，永不淘汰。 */
    const val PROTECTED_THRESHOLD = 10_000L

    /** 显式垃圾的基线分（负数），保证它们排在最前面被淘汰。 */
    private const val TRASH_BASE = -1_000_000L

    /** 显式保护的分数，远远高于任何启发式推断。 */
    private const val PROTECTED_SCORE = 100_000_000L

    /**
     * 聚合键。
     *
     * 大部分物品直接用 id（`minecraft:cobblestone`）。
     * 但**带额外数据的物品必须单独成键** —— 否则一把附魔钻石剑会和一堆普通钻石剑
     * 聚合到同一格，取回时只能拿到其中一种，等于把玩家的装备换成了一把新的。
     * 所以有自定义名 / 附魔 / 损耗时，把组件表的哈希拼进去。
     */
    fun keyOf(stack: ItemStack): String {
        val base = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        // 有「非默认组件」就按组件内容区分（药水 / 成书 / 地图 / 旗帜 / 潜影盒 / 附魔 /
        // 自定义名 / 损坏…），否则同类聚合。原来只认 3 类（名 / 附魔 / 损坏），
        // 会把不同药水、不同成书错误合并成一条，取回时变体丢失。
        return if (!stack.isComponentsPatchEmpty) "$base#${stack.components.hashCode().toString(16)}" else base
    }

    /** 物品 id（不含聚合后缀）。 */
    fun idOf(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

    /**
     * 价值分。判定顺序（从上到下，先命中先返回）：
     *
     *  1. 在配置的 `protectedItems` 里 → 极高（永不淘汰）
     *  2. 有自定义名称 → 高（玩家改过名，几乎肯定是珍视物）
     *  3. 在配置的 `trashItems` 里 → 负分（最先被淘汰）
     *  4. 其它 → 按稀有度 / 附魔 / 是否装备 / 堆叠上限累加
     */
    fun score(stack: ItemStack, cfg: ClearLagConfig): Long {
        val id = idOf(stack)

        if (id in cfg.protectedItems) return PROTECTED_SCORE
        if (stack.getComponents().get(DataComponents.CUSTOM_NAME) != null) return PROTECTED_SCORE - 1

        if (id in cfg.trashItems) return TRASH_BASE + rarityBonus(stack)

        var score = rarityBonus(stack)
        if (stack.isEnchanted) score += 3_000
        // 可损耗 = 工具 / 装备 / 武器，玩家辛苦做出来的
        if (stack.isDamageableItem) score += 2_000
        // 堆叠上限小的通常更稀有（工具 1 个、药水 16 个）
        if (stack.maxStackSize <= 16) score += 500
        // 用过的物品：说明是玩家在用的，别动
        if (stack.isDamageableItem && stack.damageValue > 0) score += 500
        return score
    }

    private fun rarityBonus(stack: ItemStack): Long = when (stack.rarity) {
        Rarity.UNCOMMON -> 100L
        Rarity.RARE -> 1_000L
        Rarity.EPIC -> 5_000L
        else -> 0L
    }
}
