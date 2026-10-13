package org.voidkit.economy

import org.voidkit.core.util.ConfigFiles

/**
 * 经济插件的配置，落盘到 `voidkit/economy/config.json`。
 *
 * ⚠️ 由 Gson 反序列化，**Kotlin 默认参数不生效**（Gson 用 Unsafe 分配对象，
 * 缺字段会落成 0 / false / null）—— 所以 [normalize] 必须把每一项都兜住。
 * 这个坑在本项目里已经咬过一次（清扫公告的开关），别重蹈。
 */
class EconomyConfig {

    /** 货币名，用于显示。 */
    var currencyName: String = "金币"

    /**
     * 金额显示模板。可用占位：
     *  - `{amount}`   已格式化的金额（带千分位、两位小数）
     *  - `{currency}` [currencyName]
     *
     * 例：`"{amount} {currency}"` → `1,234.50 金币`
     *     `"${'{'}currency{'}'} {amount}"` → `金币 1,234.50`
     */
    var format: String = "{amount} {currency}"

    /** 新玩家初始余额。 */
    var startingBalance: Double = 0.0

    /** 单账户余额上限，防止溢出和刷钱。 */
    var maxBalance: Double = 1_000_000_000.0

    /** 是否允许玩家之间转账。 */
    var payEnabled: Boolean = true

    /** 单笔转账金额下限。 */
    var payMin: Double = 0.01

    /** 转账冷却（秒），0 = 不限制。 */
    var payCooldownSeconds: Int = 0

    /** 转账手续费百分比（0~100），从转出方额外扣除。 */
    var taxPercent: Double = 0.0

    fun normalize() {
        if (currencyName == null || currencyName.isBlank()) currencyName = "金币"
        if (format == null || format.isBlank()) format = "{amount} {currency}"
        if (startingBalance < 0) startingBalance = 0.0
        if (maxBalance <= 0) maxBalance = 1_000_000_000.0
        if (startingBalance > maxBalance) startingBalance = maxBalance
        if (payMin < 0) payMin = 0.01
        if (payCooldownSeconds < 0) payCooldownSeconds = 0
        if (taxPercent < 0) taxPercent = 0.0
        if (taxPercent > 100) taxPercent = 100.0
    }
}

/** 经济配置的中文注释表（写盘时按字段插入 JSON）。 */
object EconomyComments {

    val CONFIG: Map<String, String> = mapOf(
        "currencyName" to "货币名，显示在余额后面。",
        "format" to "金额显示模板。可用占位：\n" +
            "  {amount}    已格式化的金额（千分位 + 两位小数）\n" +
            "  {currency}  上面那个 currencyName\n" +
            "例：\"{amount} {currency}\" -> 1,234.50 金币\n" +
            "    \"{currency} {amount}\" -> 金币 1,234.50",
        "startingBalance" to "新玩家初始余额。0 = 从零开始。",
        "maxBalance" to "单账户余额上限。设一个有意义的数字可以防止刷钱 bug 造成天文数字。",
        "payEnabled" to "是否允许玩家之间 /pay 转账。",
        "payMin" to "单笔转账的最小金额。",
        "payCooldownSeconds" to "两次转账之间的冷却（秒），0 = 不限制。\n" +
            "服务器人多时建议设 1~3 秒，防止有人写脚本刷转账。",
        "taxPercent" to "转账手续费百分比（0~100），从转出方额外扣除。\n" +
            "例如 5 表示转 100 实际扣 105。手续费直接消失（作为货币回收手段）。",
    )
}

/** 加载 / 保存 [EconomyConfig]。 */
object EconomyConfigStore {

    private const val FILE = "economy/config.json"

    @Volatile
    var config: EconomyConfig = EconomyConfig()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, EconomyComments.CONFIG) { EconomyConfig() }
        config.normalize()
    }

    fun save() = ConfigFiles.save(FILE, config, EconomyComments.CONFIG)
}
