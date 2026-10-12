package org.voidkit.utility

import org.voidkit.core.util.ConfigFiles

/**
 * 性能监测配置，落盘到 `voidkit/utility/perf.json`。
 *
 * ## 设计前提：稳定优先
 *
 * 服主明确要求「不希望为了一点优化性能而去崩掉整个服务端」。所以这里的默认值是：
 *
 *  - **只观测，不干预**（[interveneEnabled] = false）
 *  - 干预即使打开，也**只动掉落物**，永远不碰方块、机器、生物
 *  - 所有干预都有冷却与单次上限，不会连续卸载同一个区块
 *
 * 换句话说，装上去就是安全的：只看、只记、只提醒，不改世界。
 *
 * ⚠️ 同样由 Gson 反序列化，**缺字段会读成零值** —— [normalize] 必须逐项兜底。
 */
class PerfConfig {

    var enabled: Boolean = true

    /** 采样间隔（秒）。 */
    var sampleSeconds: Int = 60

    // ---------- 提醒 ----------

    /** TPS 低于此值提醒。原版满值 20.0。 */
    var warnTps: Double = 15.0

    /** 单 tick 耗时（MSPT）高于此值提醒。原版上限 50ms。 */
    var warnMspt: Double = 45.0

    /** 两次提醒之间的最短间隔（秒），防止持续卡顿时疯狂刷屏。 */
    var warnCooldownSeconds: Int = 300

    /** 提醒通道：`chat` / `actionbar` / `both` / `off`。默认 off（只写控制台）。 */
    var warnChannel: String = "off"

    /** 提醒只发给有 `voidkit.perf.notify` 权限的玩家。 */
    var warnToAdminsOnly: Boolean = true

    // ---------- 区块热点 ----------

    /** 是否统计“实体最多的区块”排名。 */
    var chunkTopEnabled: Boolean = true

    /** 排名保留多少个区块。 */
    var chunkTopSize: Int = 10

    /** 单区块实体数超过此值时列为热点。 */
    var entityWarnPerChunk: Int = 300

    // ---------- 温和干预 ----------

    /**
     * 是否启用温和干预。**默认 false**。
     *
     * 打开后，当某区块实体数超过 [interveneThreshold] 时，会卸载该区块里的**掉落物**。
     * 永远不会删除方块、机器或生物 —— 这是刻意的边界。
     */
    var interveneEnabled: Boolean = false

    /** 触发干预的单区块实体数阈值。 */
    var interveneThreshold: Int = 800

    /** 同一区块两次干预之间的最短间隔（秒）。 */
    var interveneCooldownSeconds: Int = 600

    /** 单次干预最多处理多少个掉落物（防止一次删太多卡主线程）。 */
    var interveneMaxPerRun: Int = 5000

    fun normalize() {
        if (sampleSeconds < 5) sampleSeconds = 60
        if (warnTps <= 0) warnTps = 15.0
        if (warnTps > 20) warnTps = 20.0
        if (warnMspt <= 0) warnMspt = 45.0
        if (warnCooldownSeconds < 0) warnCooldownSeconds = 300
        if (warnChannel == null || warnChannel.isBlank()) warnChannel = "off"
        if (chunkTopSize < 1) chunkTopSize = 10
        if (chunkTopSize > 50) chunkTopSize = 50
        if (entityWarnPerChunk < 1) entityWarnPerChunk = 300
        if (interveneThreshold < 1) interveneThreshold = 800
        // 干预阈值必须高于热点阈值，否则会把“正常偏多”的区块也当病态处理
        if (interveneThreshold <= entityWarnPerChunk) interveneThreshold = entityWarnPerChunk * 2
        if (interveneCooldownSeconds < 30) interveneCooldownSeconds = 600
        if (interveneMaxPerRun <= 0) interveneMaxPerRun = 5000
    }
}

/** 加载 / 保存 [PerfConfig]。 */
object PerfStore {

    private const val FILE = "utility/perf.json"

    @Volatile
    var config: PerfConfig = PerfConfig()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, UtilityComments.PERF) { PerfConfig() }
        config.normalize()
    }

    fun save() = ConfigFiles.save(FILE, config, UtilityComments.PERF)
}
