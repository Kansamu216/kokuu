package org.voidkit.inspector

import org.voidkit.core.util.ConfigFiles

/**
 * 操作审计模块配置，落盘到 `voidkit/inspector/config.json`。
 *
 * ## 设计主轴：先「筛」，再「记」
 *
 * 和 CoreProtect / Ledger 最大的不同：这套配置的第一件事是**决定什么不记**。
 * 目标环境是科技整合包（机械动力 / Mek / AE2），一条产线一分钟能产生成千上万次
 * 方块与容器变化，如果照单全收，数据库会被机械臂、管道、自动合成淹没，
 * 真正违规行为的那一下反而查不出来。
 *
 * 所以记录被拆成两扇门：
 *
 *  1. **来源闸门**（[logPlayer] / [logAutomation] / [logNatural]）——按「是谁干的」分类。
 *     真人玩家 = 记；机械臂 / 管道 / 假玩家 = 默认不记；树叶腐烂 / 流体 = 默认不记。
 *  2. **动作细分**（[logBlockBreak] 等）——按「干了什么」再筛一层。
 *
 * 外加一张 [alwaysLogSources] 白名单：即便来源类别被关掉，爆炸、火焰、苦力怕这些
 * 「违规行为相关」的自然事件也照记不误（它们往往正是需要追责的场景）。
 */
class InspectorConfig {

    // ---------- 数据库 ----------

    /** 数据库文件路径，相对 `voidkit/` 根目录。 */
    var databaseFile: String = "inspector/inspector.db"

    /** 记录保留天数。0 = 永久保留。超过的数据会在后台分批清除。 */
    var retentionDays: Int = 30

    // ---------- 来源闸门 ----------

    /** 记录真人玩家的操作。几乎总是应该开着。 */
    var logPlayer: Boolean = true

    /**
     * 记录「自动化」来源（机械臂、管道、假玩家驱动的方块/容器操作）。
     *
     * 🚨 默认 **关**。科技整合包里这一项是数据量的头号来源 —— 一条 Create 产线
     * 一分钟刷几万次方块变化，全记进去既拖慢写入，又让真正的违规行为记录被稀释。
     * 需要审计产线时再临时打开。
     */
    var logAutomation: Boolean = false

    /**
     * 记录「自然」来源（树叶腐烂、流体流动、方块更新、重力方块下落）。
     *
     * 默认 **关** —— 这些几乎全是噪音，而且和违规行为无关。
     * 真正需要追责的自然事件（爆炸、火、岩浆）走 [alwaysLogSources]。
     */
    var logNatural: Boolean = false

    /**
     * 即便来源类别被上面的开关关掉，也**强制记录**的来源名单。
     *
     * 填 `#` 前缀的来源名：`#creeper` `#tnt` `#fire` `#lava` `#enderman`
     * `#wither` `#ghast` `#lightning`。这些是「没有真人玩家、但需要追责」的事件
     * —— 苦力怕炸了仓库、火把房子烧了，都该能查到是谁点的。
     */
    var alwaysLogSources: MutableList<String> = mutableListOf(
        "#creeper", "#tnt", "#fire", "#lava", "#enderman",
        "#wither", "#ghast", "#lightning",
    )

    // ---------- 动作细分 ----------

    /** 记录玩家破坏方块。 */
    var logBlockBreak: Boolean = true

    /** 记录玩家放置方块。 */
    var logBlockPlace: Boolean = true

    /** 记录容器（箱子/熔炉/机器）拿取、放入。 */
    var logContainers: Boolean = true

    /** 记录玩家击杀实体（含其它玩家）。 */
    var logKills: Boolean = true

    /** 记录玩家丢出 / 捡起物品。默认关 —— 太吵，且不是追责重点。 */
    var logItemDropPickup: Boolean = false

    // ---------- 黑名单 ----------

    /**
     * 这些方块**绝对不记**，即便真人玩家操作也跳过。填方块 id（如 `minecraft:grass_block`）。
     *
     * 典型用途：某些整合包的「装饰性方块」会被建筑辅助模组大量放置，
     * 或者服主明确表示不在乎某种方块的审计。
     */
    var blockBlacklist: MutableList<String> = mutableListOf()

    /** 这些物品不记（容器交互里涉及它们时跳过）。填物品 id。 */
    var itemBlacklist: MutableList<String> = mutableListOf()

    /** 这些维度不记。填维度 id（如 `minecraft:the_nether`）。 */
    var worldBlacklist: MutableList<String> = mutableListOf()

    // ---------- 性能保护 ----------

    /**
     * 单 tick 最多入队的事件数。达到后本 tick 剩余事件直接丢弃（计入统计）。
     *
     * 这是防雪崩的闸门：即便某次异常刷出几十万次事件，也只会入队这么多，
     * 主线程不会被写库拖垮（写库在独立线程，主线程只做 O(1) 入队）。
     */
    var maxEventsPerTick: Int = 2000

    /**
     * 单来源每秒最多入队的事件数。超过部分丢弃（计入统计）。
     *
     * 兜底用：万一某个模组把它的机器操作「冒充」成真人玩家（把玩家当假玩家传给事件），
     * 这一层也能把写入量压住。真人正常操作几乎不会触顶。
     */
    var maxPerSourcePerSecond: Int = 500

    /** 写队列容量上限。写线程来不及消费时，超出丢弃（计入统计），绝不阻塞主线程。 */
    var queueCapacity: Int = 100000

    /** 写线程每批最多合并多少条事件做一次事务提交。 */
    var batchSize: Int = 500

    /** 写线程最长攒批时间（毫秒）。队列不满也会到点落库，控制延迟。 */
    var flushIntervalMs: Int = 200

    /** 查询线程池大小。查询在独立线程跑，不占游戏主线程、不卡 TPS。 */
    var queryThreads: Int = 2

    /** 清理过期记录时，每批删除多少条（分批删避免长时间锁库）。 */
    var purgeBatchSize: Int = 5000

    fun normalize() {
        // ⚠️ Gson 用 Unsafe 分配对象，缺字段会落成零值 —— 每加一个字段这里都要兜底
        if (databaseFile == null || databaseFile.isBlank()) databaseFile = "inspector/inspector.db"
        if (retentionDays < 0) retentionDays = 30
        if (alwaysLogSources == null) alwaysLogSources = mutableListOf(
            "#creeper", "#tnt", "#fire", "#lava", "#enderman",
            "#wither", "#ghast", "#lightning",
        )
        if (blockBlacklist == null) blockBlacklist = mutableListOf()
        if (itemBlacklist == null) itemBlacklist = mutableListOf()
        if (worldBlacklist == null) worldBlacklist = mutableListOf()
        if (maxEventsPerTick <= 0) maxEventsPerTick = 2000
        if (maxPerSourcePerSecond <= 0) maxPerSourcePerSecond = 500
        if (queueCapacity < 1000) queueCapacity = 1000
        if (batchSize <= 0) batchSize = 500
        if (flushIntervalMs <= 0) flushIntervalMs = 200
        if (queryThreads < 1) queryThreads = 2
        if (queryThreads > 8) queryThreads = 8
        if (purgeBatchSize <= 0) purgeBatchSize = 5000
    }

    /** 统一小写 + 去空白后的集合，避免 `Minecraft:Diamond ` 这种写法漏判。 */
    fun idSet(list: List<String>): Set<String> =
        list.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** alwaysLogSources 的规范化集合。 */
    fun alwaysLogSet(): Set<String> = idSet(alwaysLogSources)
}

/** 配置的中文注释表。 */
object InspectorComments {

    val CONFIG: Map<String, String> = mapOf(
        "databaseFile" to "数据库文件路径，相对 voidkit/ 根目录。\n" +
            "嵌入式 SQLite，零配置、单文件、随服务端走，不额外起数据库进程。",
        "retentionDays" to "记录保留天数，超过的数据会在后台分批清除。0 = 永久保留。\n" +
            "数字越大占用磁盘越多；默认 30 天足够追责大多数违规行为。",
        "logPlayer" to "记录真人玩家的操作。几乎总是应该开着。",
        "logAutomation" to "记录「自动化」来源（机械臂、管道、假玩家）。\n" +
            "⚠️ 默认关：科技整合包里这是数据量头号来源，机械动力产线一分钟几万次方块变化，\n" +
            "全记进去会稀释真正的违规行为记录。需要审计产线时再临时打开。",
        "logNatural" to "记录「自然」来源（树叶腐烂、流体、方块更新、重力方块）。\n" +
            "默认关 —— 几乎全是噪音。真正要追责的自然事件（爆炸/火/岩浆）走 alwaysLogSources。",
        "alwaysLogSources" to "即便来源类别被关，也强制记录来源名单（# 前缀）。\n" +
            "苦力怕爆炸、火蔓延、岩浆流动、末影人搬方块这些「没有真人玩家但要追责」的事件都在这。",
        "logBlockBreak" to "记录玩家破坏方块。",
        "logBlockPlace" to "记录玩家放置方块。",
        "logContainers" to "记录容器（箱子/熔炉/机器）拿取、放入。",
        "logKills" to "记录玩家击杀实体。",
        "logItemDropPickup" to "记录玩家丢出 / 捡起物品。默认关 —— 太吵，且不是追责重点。",
        "blockBlacklist" to "这些方块绝对不记（即便真人操作）。填方块 id。",
        "itemBlacklist" to "这些物品不记（容器交互涉及它们时跳过）。填物品 id。",
        "worldBlacklist" to "这些维度不记。填维度 id（如 minecraft:the_nether）。",
        "maxEventsPerTick" to "单 tick 最多入队事件数，达到后剩余丢弃。防雪崩闸门。",
        "maxPerSourcePerSecond" to "单来源每秒最多入队事件数。兜底防「模组冒充真人玩家」刷爆库。",
        "queueCapacity" to "写队列容量上限。写线程来不及消费时超出丢弃，绝不阻塞主线程。",
        "batchSize" to "写线程每批最多合并多少条事件做一次事务提交。",
        "flushIntervalMs" to "写线程最长攒批时间（毫秒）。队列不满也到点落库，控制延迟。",
        "queryThreads" to "查询线程池大小。查询在独立线程跑，不占游戏主线程、不卡 TPS。",
        "purgeBatchSize" to "清理过期记录时每批删除条数（分批删避免长时间锁库）。",
    )
}

/** 加载 / 保存 [InspectorConfig]，并缓存解析好的集合。 */
object InspectorStore {

    private const val FILE = "inspector/config.json"

    @Volatile
    var config: InspectorConfig = InspectorConfig()
        private set

    @Volatile
    var blockBlacklist: Set<String> = emptySet()
        private set

    @Volatile
    var itemBlacklist: Set<String> = emptySet()
        private set

    @Volatile
    var worldBlacklist: Set<String> = emptySet()
        private set

    @Volatile
    var alwaysLog: Set<String> = emptySet()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, InspectorComments.CONFIG) { InspectorConfig() }
        config.normalize()
        blockBlacklist = config.idSet(config.blockBlacklist)
        itemBlacklist = config.idSet(config.itemBlacklist)
        worldBlacklist = config.idSet(config.worldBlacklist)
        alwaysLog = config.alwaysLogSet()
    }

    fun save() = ConfigFiles.save(FILE, config, InspectorComments.CONFIG)
}
