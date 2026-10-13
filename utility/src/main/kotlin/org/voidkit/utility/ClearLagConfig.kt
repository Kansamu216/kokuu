package org.voidkit.utility

import org.voidkit.core.util.ConfigFiles

/**
 * 清扫与垃圾桶的配置，落盘到 `voidkit/utility/clearlag.json`。
 *
 * ## 关于 [normalize]
 *
 * ⚠️ 这个类由 Gson 反序列化，**Kotlin 的默认值不生效** —— Gson 用 `Unsafe`
 * 绕过构造器直接分配对象，所以文件里**没写到的字段会保持 JVM 的零值**
 * （Boolean = false、Int = 0、对象 = null），而不是这里的默认值。
 *
 * 这正是之前那个 bug 的成因：老版本写出的配置里没有 `clearLagAnnounce`，
 * 于是它被读成 `false`，清扫播报就再也不进聊天框了（只剩控制台那行日志）。
 *
 * 所以规矩是：**每加一个字段，[normalize] 里就必须同步加一条兜底**。
 * 宁可啰嗦，也不要再出现“配置里没有某一行就功能静默失效”。
 */
class ClearLagConfig {

    // ---------- 基本 ----------

    /** 是否启用定时清扫。 */
    var enabled: Boolean = true

    /** 清扫间隔（秒）。 */
    var intervalSeconds: Int = 300

    /**
     * 清扫前多少秒只提醒**一次**。0 = 完全不提醒。
     *
     * 刻意只提醒一次 —— 之前是「提前 15 秒 + 最后 3 秒逐秒倒数」共 4 条消息，
     * 在大服上相当刷屏。需要更热闹的倒数就把 [countdownSeconds] 调大。
     */
    var warnSeconds: Int = 30

    /** 最后 N 秒逐秒倒数（3 = 只在 3、2、1 各喊一声）。0 = 不倒计时。 */
    var countdownSeconds: Int = 0

    /** 播报通道：`chat` / `actionbar` / `both` / `off`。 */
    var announceChannel: String = "chat"

    /** 清扫完成后，是否在聊天框给一个可点击的「打开垃圾桶」入口。 */
    var trashLinkInChat: Boolean = true

    // ---------- 清理项 ----------

    var removeItems: Boolean = true
    var removeXpOrbs: Boolean = true
    var removeArrows: Boolean = true
    var removeProjectiles: Boolean = false

    /**
     * 保留有自定义名称的物品 —— 这类物品不经垃圾桶，**原地不动**。
     *
     * 和垃圾桶的分工：这条是“绝对不碰”，垃圾桶是“先收起来，你自己来拿”。
     * 玩家命名的箱子钥匙、商店货币、任务道具放这里更省心。
     */
    var keepNamedItems: Boolean = true

    // ---------- 垃圾桶 ----------

    /** 是否把清扫到的东西收进垃圾桶（false = 老行为，直接删除）。 */
    var trashEnabled: Boolean = true

    /**
     * 垃圾桶格数上限（最多保留多少**种**物品）。
     *
     * 因为按类型聚合，一个产线撑死几十种物品，45 格足够。
     * 超过时按价值淘汰 — 见 [TrashBin.trim]。
     */
    var maxTypes: Int = 45

    /**
     * 垃圾物品（[trashItems] 里那些）每类最多保留多少个，多余的直接丢弃。
     *
     * 这是对付「产线狂吐圆石」的闸门：留 512 个给玩家看看是什么，
     * 剩下几百万个连进桶的机会都没有。设 0 = 垃圾物品完全不进桶（直接丢）。
     */
    var junkKeepLimit: Int = 512

    /**
     * 显式保护的物品：**永远不会**被清扫，也不会进垃圾桶。
     *
     * 填物品 id。整合包里加了新模组的贵重材料时，往这里加一行即可。
     */
    var protectedItems: MutableList<String> = mutableListOf(
        "minecraft:netherite_ingot",
        "minecraft:diamond",
        "minecraft:diamond_block",
        "minecraft:emerald",
        "minecraft:emerald_block",
        "minecraft:nether_star",
        "minecraft:elytra",
        "minecraft:shulker_box",
        "minecraft:enchanted_golden_apple",
        "minecraft:ancient_debris",
        "minecraft:echo_shard",
    )

    /**
     * 显式标记为垃圾的物品：会被**优先淘汰**，并受 [junkKeepLimit] 限制。
     *
     * 默认放的是「产线一定会大量产出、且几乎没人捡」的那几样。
     * 注意这只是“优先级最低”，不等于一定会被删 —— 桶没满时它们照样留着。
     */
    var trashItems: MutableList<String> = mutableListOf(
        "minecraft:cobblestone",
        "minecraft:cobbled_deepslate",
        "minecraft:gravel",
        "minecraft:dirt",
        "minecraft:coarse_dirt",
        "minecraft:rooted_dirt",
        "minecraft:sand",
        "minecraft:red_sand",
        "minecraft:andesite",
        "minecraft:diorite",
        "minecraft:granite",
        "minecraft:tuff",
        "minecraft:calcite",
        "minecraft:netherrack",
        "minecraft:end_stone",
        "minecraft:basalt",
        "minecraft:blackstone",
        "minecraft:stone",
        "minecraft:snowball",
        "minecraft:stick",
        "minecraft:flint",
        "minecraft:rotten_flesh",
        "minecraft:poisonous_potato",
        "minecraft:wheat_seeds",
        "minecraft:kelp",
        "minecraft:sugar_cane",
    )

    // ---------- 性能保护 ----------

    /**
     * 单轮清扫处理实体的上限。达到就停止，剩下的留到下一轮。
     *
     * 这是防雪崩的闸门：万一某次异常刷出 500 万个实体，一次性删完会让主线程
     * 卡住十几秒（客户端看就是“服务器卡死”）。分轮处理，每轮都在可接受的耗时内。
     */
    var maxPerSweep: Int = 20000

    fun normalize() {
        // ⚠️ 每一行都必要 —— Gson 缺字段读成零值，不兜底就会静默失效
        if (intervalSeconds <= 0) intervalSeconds = 300
        if (intervalSeconds < 30) intervalSeconds = 30
        if (warnSeconds < 0) warnSeconds = 0
        if (warnSeconds >= intervalSeconds) warnSeconds = intervalSeconds - 1
        if (countdownSeconds < 0) countdownSeconds = 0
        if (countdownSeconds > 10) countdownSeconds = 10
        if (announceChannel == null || announceChannel.isBlank()) announceChannel = "chat"
        if (maxTypes < 1) maxTypes = 45
        // 界面固定是 6 行箱子，物品区 45 格（底部一行留给按钮），所以上限就是 45
        if (maxTypes > TrashMenu.ITEM_SLOTS) maxTypes = TrashMenu.ITEM_SLOTS
        if (junkKeepLimit < 0) junkKeepLimit = 512
        if (maxPerSweep <= 0) maxPerSweep = 20000
        if (protectedItems == null) protectedItems = mutableListOf()
        if (trashItems == null) trashItems = mutableListOf()
    }

    /** 配置里的物品 id 统一小写 + 去空白，避免 "Minecraft:Diamond " 这种写法漏判。 */
    fun normalizedIdSet(list: List<String>): Set<String> =
        list.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}

/** 清扫配置的中文注释表。 */
object UtilityComments {

    val CLEARLAG: Map<String, String> = mapOf(
        "enabled" to "是否启用定时清扫。",
        "intervalSeconds" to "清扫间隔（秒）。下限 30 —— 设太小会让主线程一直在删实体。",
        "warnSeconds" to "清扫前多少秒提醒一次。0 = 不提醒。\n" +
            "刻意只提醒一次：之前是「提前 15 秒 + 最后 3 秒逐秒倒数」共 4 条，\n" +
            "大服上很刷屏。想要倒数感就把下面的 countdownSeconds 调大。",
        "countdownSeconds" to "最后 N 秒逐秒倒数（3 = 只在 3、2、1 各喊一声）。0 = 不倒计时。\n" +
            "上限 10，防止有人填 60 把聊天框刷爆。",
        "announceChannel" to "播报发到哪：\n" +
            "  chat       聊天框（默认）\n" +
            "  actionbar  动作栏（物品栏上方那行，不占聊天记录）\n" +
            "  both       两边都发\n" +
            "  off        都不发，只写控制台日志\n" +
            "⚠️ 这台服的清扫播报曾经“只在后台看到”，就是这个字段缺省导致的 —— 现在缺省会被兜成 chat。",
        "trashLinkInChat" to "清扫播报里是否带一个可点击的「打开垃圾桶」入口。",
        "removeItems" to "清扫时是否收走掉落物。",
        "removeXpOrbs" to "清扫时是否收走经验球。",
        "removeArrows" to "清扫时是否收走地上的箭。",
        "removeProjectiles" to "清扫时是否收走其他投掷物（雪球、鸡蛋等）。",
        "keepNamedItems" to "是否让「有自定义名称的物品」完全不受清扫影响（留在原地，也不进垃圾桶）。\n" +
            "玩家命名过的箱子钥匙 / 商店货币 / 任务道具建议保持 true。",
        "trashEnabled" to "是否把清扫到的东西收进垃圾桶。\n" +
            "true  = 玩家可以在下一次清扫前自己取回（推荐）\n" +
            "false = 老行为：直接删除，不保留",
        "maxTypes" to "垃圾桶格数上限 —— 注意是“多少种物品”，不是“多少个物品”。\n" +
            "因为按类型聚合，1 万个圆石只占 1 格。超过上限时按价值淘汰，\n" +
            "分数低的（圆石、泥土）先走，带附魔 / 改过名的永远留得住。",
        "junkKeepLimit" to "垃圾物品每类最多保留多少个，多出来的直接丢弃。\n" +
            "这是对付产线狂吐圆石的闸门 —— 留一点给玩家看看是什么就够了。\n" +
            "设 0 = 垃圾物品完全不进垃圾桶。",
        "protectedItems" to "显式保护的物品：永不被清扫，也不进垃圾桶。填物品 id。\n" +
            "整合包里加了贵重材料就往这里加一行。",
        "trashItems" to "显式标记为垃圾的物品：优先淘汰，并受 junkKeepLimit 限制。\n" +
            "默认放的是产线必然大量产出、且几乎没人捡的那几样。\n" +
            "注意这只是“优先级最低”，桶没满时它们照样留着。",
        "maxPerSweep" to "单轮清扫处理实体的上限，达到就停下、剩下的留到下一轮。\n" +
            "这是防雪崩的闸门：万一刷出几百万个实体，一次性删完会让主线程卡十几秒\n" +
            "（客户端看就是“服务器卡死”）。分轮处理每轮都在可接受耗时内。",
    )

    val PERF: Map<String, String> = mapOf(
        "enabled" to "是否启用性能监测。",
        "sampleSeconds" to "采样间隔（秒）。多久统计一次 TPS / 实体数。",
        "warnTps" to "TPS 低于这个值时提醒管理员。默认 15.0（原版满值 20.0）。",
        "warnMspt" to "单 tick 耗时高于这个毫秒数时提醒。默认 45ms（原版上限 50ms）。",
        "warnCooldownSeconds" to "两次性能提醒之间的最短间隔（秒），防止持续卡顿时疯狂刷屏。",
        "warnChannel" to "提醒发到哪：chat / actionbar / both / off。默认只写控制台。",
        "warnToAdminsOnly" to "性能提醒只发给有 voidkit.perf.notify 权限的玩家。\n" +
            "true = 只有管理员看得到（推荐）；false = 全服可见。",
        "chunkTopEnabled" to "是否统计“实体最多的区块”排名。\n" +
            "用来找产线 / 刷怪塔造成的热点区块。",
        "chunkTopSize" to "排名保留多少个区块。",
        "entityWarnPerChunk" to "单区块实体数超过这个值时，把它列为热点。默认 300。",
        "interveneEnabled" to "是否启用温和干预（默认 false —— 只观测，不动世界）。\n" +
            "开启后会在区块实体数超过阈值时，卸载该区块里的掉落物。\n" +
            "⚠️ 永远不会删除方块、机器或生物。",
        "interveneThreshold" to "触发干预的单区块实体数阈值。默认 800，请慎重调低。",
        "interveneCooldownSeconds" to "同一区块两次干预之间的最短间隔（秒），防止反复卸载。",
        "interveneMaxPerRun" to "单次干预最多处理多少个掉落物，防止一次删太多卡主线程。",
    )
}

/** 加载 / 保存 [ClearLagConfig]。 */
object ClearLagStore {

    private const val FILE = "utility/clearlag.json"

    @Volatile
    var config: ClearLagConfig = ClearLagConfig()
        private set

    /** 配置里解析好的物品 id 集合，避免每次判定都重新 trim/lowercase。 */
    @Volatile
    var protectedSet: Set<String> = emptySet()
        private set

    @Volatile
    var trashSet: Set<String> = emptySet()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, UtilityComments.CLEARLAG) { ClearLagConfig() }
        config.normalize()
        protectedSet = config.normalizedIdSet(config.protectedItems)
        trashSet = config.normalizedIdSet(config.trashItems)
    }

    fun save() = ConfigFiles.save(FILE, config, UtilityComments.CLEARLAG)
}
