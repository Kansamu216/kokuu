package org.voidkit.core.config

/**
 * 配置中文注释表。
 *
 * 键是「字段路径」（以 `.` 分隔），值是写在该字段上方的说明；支持 `\n` 写成多行。
 * 数组元素的字段用 `数组名[].字段名` 表示，并且只会写在**第一个**元素上方。
 *
 * 与 [org.voidkit.core.util.CommentedJson] 配合：写文件时插注释，读文件时剥掉。
 *
 * ⚠️ 这里**只有内核自己**的配置注释。各插件的注释表已随配置一起下放
 * （`DisplayComments` / `PermComments` / `TitleComments` / `UtilityComments` /
 * `EconomyComments` / `AnnounceComments` / `MenuComments` / `CommandsComments` /
 * `WebComments` / `EssentialsComments`）—— 加一个字段只改一个模块。
 */
object ConfigComments {

    // ============================================================ 内核配置

    val CORE: Map<String, String> = mapOf(
        "language" to "界面语言：zh_cn（默认）/ en_us。\n" +
            "词表在 voidkit/lang/ 下，可以直接改；缺词条自动回退中文。",
        "features" to "模块开关。键是模块 id，值 false 表示停用该模块；没写的模块用自身默认状态。\n" +
            "游戏内 /voidkit features 可查看全部 id 与当前状态。",
        "recordIps" to "是否记录玩家 IP 历史（面板上可看某个玩家用过哪些 IP）。",
        "maxJoinLogEntries" to "上下线日志最多保留多少条，超出后从最旧的开始丢。",
        "autoSaveSeconds" to "自动将玩家档案落盘的间隔（秒）。\n" +
            "注意：数据本身是\"写时即落\"的，这个只是兜底的周期性整体保存。",

        "_note" to "本文件只保留内核自己的配置。\n" +
            "各插件的配置已下放到 voidkit/ 下各自的子目录，且都带中文注释：\n" +
            "  display/      MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单\n" +
            "  perm/         权限组 / 玩家覆盖 / 全局设置\n" +
            "  title/        称号定义\n" +
            "  essentials/   家上限 / TPA / back / 传送冷却 / 命令简写开关\n" +
            "  economy/      货币\n" +
            "  utility/      清扫 / 性能\n" +
            "  web/          管理 API 地址与令牌",
    )
}
