package org.voidkit.core.config

import org.voidkit.core.api.VoidkitLang

/**
 * 内核配置，落盘到 `voidkit/core/config.json`。
 *
 * ## 这里**只**放内核自己的东西
 *
 * 经过一轮归属整理后，内核配置只保留"内核真的在用"的字段：
 *
 * | 字段 | 谁在用 |
 * |---|---|
 * | [language] | 内核（[VoidkitLang]） |
 * | [features] | 内核（[org.voidkit.core.core.PluginRegistry]） |
 * | [worldNames] | 内核的 `%world%` 变量（聊天/TAB/记分牌/面板共用） |
 * | [recordIps] / [maxJoinLogEntries] | 内核的玩家追踪模块 |
 * | [autoSaveSeconds] | 内核的数据落盘 |
 *
 * 其它一律下放到对应插件的文件（各有中文注释）：
 *
 * ```
 * voidkit/
 * ├── core/config.json          这个文件
 * ├── display/                 MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单
 * ├── perm/                     权限组 / 玩家覆盖 / 全局设置
 * ├── title/titles.json         称号定义
 * ├── essentials/            家上限 / TPA / back / 传送冷却 / 命令简写开关
 * ├── economy/config.json      货币
 * ├── utility/                  清扫 / 性能
 * └── web/config.json          管理 API 地址与令牌
 * ```
 *
 * 这样一份插件的配置可以整体备份、整体交接；卸载插件时也不会在内核配置里留下
 * 一堆没人读的死字段。存量服务器的旧值由各插件首次生成配置时自动搬走
 * （见 [LegacyConfig]）。
 *
 * 文本字段支持 `&` 颜色代码与 `%占位符%`，见 [org.voidkit.core.placeholder.PlaceholderRegistry]。
 *
 * ⚠️ 这个类由 Gson 反序列化，**Kotlin 默认参数不生效**（Gson 用 Unsafe 分配对象，
 * 缺字段会落成 null / 0 / false），所以 [normalize] 必须把每一项都补回来。
 */
class VoidkitConfig {

    /**
     * 界面语言：`zh_cn`（默认，中文原文直出）/ `en_us`。
     *
     * 词表在 `voidkit/lang/en_us.json`，可以直接改；改完 `/voidkit reload` 生效。
     * 缺词条时自动回退中文，不会出现空白或裸 key。
     */
    var language: String = VoidkitLang.ZH_CN

    /** 按 [org.voidkit.core.api.Feature.id] 开关模块；未出现的用其 defaultEnabled。 */
    var features: MutableMap<String, Boolean> = linkedMapOf()

    // ---------- 世界显示名 ----------

    /**
     * 维度显示名映射，供 `%world%` 变量使用（聊天 / TAB / 记分牌 / 面板都读它）。
     *
     * - **key**：优先写维度完整 ID（`命名空间:路径`，如 `minecraft:overworld`）；
     *   也支持只写路径简写（如 `overworld`）。模组维度写它自己的 ID，例如
     *   `twilightforest:twilight_forest`、`twilightforest:skylight_forest`。
     * - **value**：显示名，**支持 `&` 颜色代码**（如 `&a主世界`、`&c下界`）。
     * - 没配到的维度，回退显示维度路径原文（如 `twilight_forest`），不会留空。
     *
     * ⚠️ 空的 map（`{}`）是**合法**的，表示「全部按路径原文显示」；只有字段整个缺失才会补默认值。
     * 改完 `/voidkit reload` 即生效，不用重启。
     */
    var worldNames: MutableMap<String, String> = LinkedHashMap(DEFAULT_WORLD_NAMES)

    // ---------- 玩家追踪（内核自带模块）----------

    /**
     * 是否记录玩家 IP。关掉后不写入 IP 历史，只保留上下线时间。
     *
     * ⚠️ 本字段**刻意不在 [normalize] 里兜底**，且**缺 key 时读成 `false`** ——
     * Gson 走 Unsafe 分配，配置里没有这一行就等于 `false`（与声明处的 `= true` 不一致）。
     *
     * 为什么不能像 [maxJoinLogEntries] 那样用零值哨兵补回 `true`：布尔零值 `false` 同时是
     * 「用户有意关闭」的合法值，二者在反序列化后**无法区分**；若在 [normalize] 里把 `false`
     * 改回 `true`，服主将**永远无法关掉** IP 记录，比静默关掉更糟。
     *
     * 结论：要让 IP 记录生效，配置里必须**显式**写 `"recordIps": true`。
     * 真正意义上的「缺 key 兜底」需要把本字段改为可空 backing field 或自定义反序列化
     * （即改动配置读取方式），当前刻意不做。
     */
    var recordIps: Boolean = true

    /** 上下线日志最多保留多少条（JSONL 滚动）。 */
    var maxJoinLogEntries: Int = 20000

    // ---------- 持久化（内核数据层）----------

    /** 玩家档案自动落盘间隔（秒）。 */
    var autoSaveSeconds: Int = 300

    fun featureEnabled(id: String, fallback: Boolean): Boolean = features[id] ?: fallback

    /**
     * 把维度 ID 解析成显示名（`%world%` 用的就是它）。
     *
     * 纯字符串逻辑，不碰任何 Minecraft 类型，方便单测。匹配顺序：
     * 1. 完整 ID 命中 [worldNames]（如 `minecraft:overworld`）
     * 2. 路径简写命中（如 `overworld`）
     * 3. 都没有 → 原样返回维度 ID
     */
    fun worldDisplayName(dimensionId: String): String {
        // 与 normalize 同款写法：Gson 走 Unsafe，字段可能是 null，不能省这个判断
        val names = worldNames
        if (names == null) return dimensionId
        return names[dimensionId]
            ?: names[dimensionId.substringAfter(':', dimensionId)]
            ?: dimensionId
    }

    /**
     * 反序列化后补齐可能缺失的字段。每加一个字段都要在这里同步加一行。
     *
     * **唯一例外：[recordIps]** —— 它的零值 `false` 与「用户有意关闭」无法区分，无法在此兜底，
     * 缺 key 即 `false`，详见该字段注释。
     */
    fun normalize() {
        if (features == null) features = linkedMapOf()
        if (language == null || language.isBlank()) language = VoidkitLang.ZH_CN
        // 只用 null 判定：**空 map 是用户的有意选择**（全部显示路径原文），不能覆盖
        if (worldNames == null) worldNames = LinkedHashMap(DEFAULT_WORLD_NAMES)
        if (maxJoinLogEntries <= 0) maxJoinLogEntries = 20000
        if (autoSaveSeconds <= 0) autoSaveSeconds = 300
    }

    companion object {
        /**
         * [worldNames] 的出厂默认值：原版三个维度给中文名。
         *
         * 刻意**不带颜色码** —— 保持与原行为一致，模板里已有的 `&e%world%` 之类照旧生效；
         * 想要彩色命名由服主自己在配置里加 `&` 码。
         */
        val DEFAULT_WORLD_NAMES: Map<String, String> = linkedMapOf(
            "minecraft:overworld" to "主世界",
            "minecraft:the_nether" to "下界",
            "minecraft:the_end" to "末地",
        )
    }
}
