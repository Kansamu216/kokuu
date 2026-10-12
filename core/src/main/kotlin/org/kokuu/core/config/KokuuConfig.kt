package org.kokuu.core.config

/**
 * 核心配置，落盘到 `config/kokuucore/config.json`。
 *
 * ⚠️ 下面这些**不在这里**，各自有独立文件：
 *  - 展示层（MOTD / TAB / 记分牌 / 聊天栏）→ `display/` 目录
 *  - 权限（组、玩家覆盖、全局设置）→ `permissions/` 目录，见 [org.kokuu.core.perm.PermissionStore]
 *
 * 文本字段支持 `&` 颜色代码与 `%占位符%`，见 [org.kokuu.core.placeholder.PlaceholderRegistry]。
 *
 * ⚠️ 这个类由 Gson 反序列化，**Kotlin 默认参数不生效**（Gson 用 Unsafe 分配对象，
 * 字段会是 null / 0 / false），所以 [normalize] 必须把缺的补回来。
 */
class KokuuConfig {

    // ---------- 模块开关 ----------
    /** 按 [org.kokuu.core.api.Feature.id] 开关模块；未出现的用其 defaultEnabled。 */
    var features: MutableMap<String, Boolean> = linkedMapOf()

    // ---------- 管理 API ----------
    var apiEnabled: Boolean = true
    /** 监听地址。默认仅本机；面板不在同一台机器时改 0.0.0.0，并务必换掉端口与令牌。 */
    var apiBind: String = "127.0.0.1"
    var apiPort: Int = 8765
    var apiToken: String = ""
    var apiRateLimitPerSecond: Int = 30

    // ---------- 玩家追踪 ----------
    var recordIps: Boolean = true
    var maxJoinLogEntries: Int = 20000

    // ---------- 传送 ----------
    var defaultHomeLimit: Int = 3
    var maxHomes: Int = 32
    var tpaEnabled: Boolean = true
    var tpaTimeoutSeconds: Int = 60
    var backEnabled: Boolean = true
    /** 传送冷却（秒），0 = 关闭。 */
    var teleportCooldownSeconds: Int = 0

    // ---------- 持久化 ----------
    var autoSaveSeconds: Int = 300

    // ---------- 清扫（清道夫） ----------
    var clearLagEnabled: Boolean = true
    /** 清扫间隔（秒）。 */
    var clearLagIntervalSeconds: Int = 300
    /** 清扫前的倒计时提醒（秒），0 = 不提醒。 */
    var clearLagWarnSeconds: Int = 15
    var clearLagRemoveItems: Boolean = true
    var clearLagRemoveXpOrbs: Boolean = true
    var clearLagRemoveArrows: Boolean = true
    var clearLagRemoveProjectiles: Boolean = false
    /** 保留有自定义名称的物品（一般是玩家珍视的物品）。 */
    var clearLagKeepNamedItems: Boolean = true
    /** 单次清扫后若清理数量大于 0 是否全服提示。 */
    var clearLagAnnounce: Boolean = true

    fun featureEnabled(id: String, fallback: Boolean): Boolean = features[id] ?: fallback

    /** 反序列化后补齐可能缺失的字段。 */
    fun normalize() {
        if (features == null) features = linkedMapOf()
        if (apiToken == null) apiToken = ""
        if (apiBind == null || apiBind.isBlank()) apiBind = "127.0.0.1"
        if (apiRateLimitPerSecond <= 0) apiRateLimitPerSecond = 30
        if (maxJoinLogEntries <= 0) maxJoinLogEntries = 20000
        if (defaultHomeLimit < 0) defaultHomeLimit = 0
        if (maxHomes < 1) maxHomes = 32
        if (defaultHomeLimit > maxHomes) defaultHomeLimit = maxHomes
        if (tpaTimeoutSeconds <= 0) tpaTimeoutSeconds = 60
        if (teleportCooldownSeconds < 0) teleportCooldownSeconds = 0
        if (autoSaveSeconds <= 0) autoSaveSeconds = 300
        if (clearLagIntervalSeconds <= 0) clearLagIntervalSeconds = 300
        if (clearLagWarnSeconds < 0) clearLagWarnSeconds = 0
    }
}
