package org.voidkit.essentials

import org.voidkit.core.api.HomeLimits
import org.voidkit.core.config.LegacyConfig
import org.voidkit.core.util.ConfigFiles

/**
 * essentials 插件的配置，落盘到 `voidkit/essentials/config.json`。
 *
 * 这些字段原本长在内核的 `config.json` 里（`defaultHomeLimit` / `tpaTimeoutSeconds` …）——
 * 归属错位：内核既不提供 /home 也不提供 /tpa，却要替它们存配置。
 *
 * 存量服务器不受影响：首次生成这个文件时会从旧 `core/config.json` 搬过来。
 *
 * ⚠️ 由 Gson 反序列化，**缺字段读成零值** —— [normalize] 必须逐项兜底。
 */
class EssentialsConfig {

    /** 默认组玩家能设几个家。单个权限组可以用 `voidkit.home.<数量>` 覆盖。 */
    var defaultHomeLimit: Int = 3

    /** 任何玩家都不能超过的家的硬上限。 */
    var maxHomes: Int = 32

    /** 是否允许 /tpa 系列传送请求。 */
    var tpaEnabled: Boolean = true

    /** 传送请求的有效期（秒），过期自动作废。 */
    var tpaTimeoutSeconds: Int = 60

    /** 是否允许 /back 回到上一个位置。 */
    var backEnabled: Boolean = true

    /** 传送冷却（秒），0 = 关闭。 */
    var teleportCooldownSeconds: Int = 0

    fun normalize() {
        if (defaultHomeLimit < 0) defaultHomeLimit = 0
        if (maxHomes < 1) maxHomes = 32
        if (defaultHomeLimit > maxHomes) defaultHomeLimit = maxHomes
        if (tpaTimeoutSeconds <= 0) tpaTimeoutSeconds = 60
        if (tpaTimeoutSeconds > 3600) tpaTimeoutSeconds = 3600
        if (teleportCooldownSeconds < 0) teleportCooldownSeconds = 0
    }
}

/** essentials 配置的中文注释表。 */
object EssentialsComments {

    val CONFIG: Map<String, String> = mapOf(
        "defaultHomeLimit" to "默认组玩家能设几个家。\n" +
            "单个权限组可以用 voidkit.home.<数量> 节点覆盖，例如 voidkit.home.10。",
        "maxHomes" to "任何玩家都不能超过的家数硬上限 —— 防止有人给自己组配一个天文数字。",
        "tpaEnabled" to "是否允许 /tpa /tpahere 这类传送请求。关掉后相关命令会被拒绝。",
        "tpaTimeoutSeconds" to "传送请求的有效期（秒），过期自动作废（10~3600）。\n" +
            "太长会让人以为请求没发出去，太短则来不及点接受。",
        "backEnabled" to "是否允许 /back 回到上一个位置。",
        "teleportCooldownSeconds" to "两次传送之间的冷却（秒），0 = 不限制。\n" +
            "人多时设 1~3 秒可以挡掉 Ctrl+右键式的连点传送。",
    )
}

/**
 * 加载 / 保存 [EssentialsConfig]，并把自己作为 [HomeLimits] 暴露给内核。
 *
 * 实现 `HomeLimits` 而不是让 perm 去读配置对象 —— 这样 perm 不需要 import
 * 这个类，essentials 卸载后它也能正常工作。
 */
object EssentialsStore : HomeLimits {

    private const val FILE = "essentials/config.json"

    @Volatile
    var config: EssentialsConfig = EssentialsConfig()
        private set

    override fun defaultLimit(): Int = config.defaultHomeLimit

    override fun maxPerPlayer(): Int = config.maxHomes

    fun load() {
        config = ConfigFiles.load(FILE, EssentialsComments.CONFIG) {
            // 文件不存在 —— 从旧 core/config.json 搬一次
            EssentialsConfig().also { fresh ->
                LegacyConfig.int("defaultHomeLimit")?.let { fresh.defaultHomeLimit = it }
                LegacyConfig.int("maxHomes")?.let { fresh.maxHomes = it }
                LegacyConfig.bool("tpaEnabled")?.let { fresh.tpaEnabled = it }
                LegacyConfig.int("tpaTimeoutSeconds")?.let { fresh.tpaTimeoutSeconds = it }
                LegacyConfig.bool("backEnabled")?.let { fresh.backEnabled = it }
                LegacyConfig.int("teleportCooldownSeconds")?.let { fresh.teleportCooldownSeconds = it }
            }
        }
        config.normalize()
    }

    fun save() = ConfigFiles.save(FILE, config, EssentialsComments.CONFIG)
}
