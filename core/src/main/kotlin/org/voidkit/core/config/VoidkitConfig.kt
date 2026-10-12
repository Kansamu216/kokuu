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

    // ---------- 玩家追踪（内核自带模块）----------

    /** 是否记录玩家 IP。关掉后不写入 IP 历史，只保留上下线时间。 */
    var recordIps: Boolean = true

    /** 上下线日志最多保留多少条（JSONL 滚动）。 */
    var maxJoinLogEntries: Int = 20000

    // ---------- 持久化（内核数据层）----------

    /** 玩家档案自动落盘间隔（秒）。 */
    var autoSaveSeconds: Int = 300

    fun featureEnabled(id: String, fallback: Boolean): Boolean = features[id] ?: fallback

    /** 反序列化后补齐可能缺失的字段。每加一个字段都要在这里同步加一行。 */
    fun normalize() {
        if (features == null) features = linkedMapOf()
        if (language == null || language.isBlank()) language = VoidkitLang.ZH_CN
        if (maxJoinLogEntries <= 0) maxJoinLogEntries = 20000
        if (autoSaveSeconds <= 0) autoSaveSeconds = 300
    }
}
