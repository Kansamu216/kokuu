package org.voidkit.display

import org.voidkit.core.util.ConfigFiles

/** 定时轮播公告。 */
class AutoBroadcast {

    /** 是否启用轮播。 */
    var enabled: Boolean = false

    /** 间隔（秒）。 */
    var intervalSeconds: Int = 600

    /** true = 随机挑一条；false = 按顺序循环。 */
    var random: Boolean = true

    /** 播报通道：chat / actionbar / both / off。 */
    var channel: String = "chat"

    /**
     * 公告内容。一条里可以用 `\n` 分行，也可以用 `&` 颜色码和 `%变量%`。
     *
     * 已经在播过的那条不会立刻重复（顺序模式下就是简单地往后走）。
     */
    var messages: MutableList<String> = mutableListOf(
        "&7欢迎来到本服务器，有问题可以 &f/help",
        "&7想快速回城就用 &f/spawn&7；回不去了用 &f/back",
        "&7别忘了 &f/title &7设一个自己的称号",
    )

    fun normalize() {
        if (intervalSeconds < 30) intervalSeconds = 600
        if (channel == null || channel.isBlank()) channel = "chat"
        if (messages == null) messages = mutableListOf()
    }
}

/**
 * 公告配置，落盘到 `voidkit/display/announce.json`。
 *
 * ⚠️ 同样由 Gson 反序列化，**缺字段读成零值** —— [normalize] 逐项兜底。
 */
class AnnounceConfig {

    /** 是否启用 `/announce` 命令。 */
    var enabled: Boolean = true

    /** 播报通道：chat / actionbar / both / off。 */
    var channel: String = "chat"

    /** 正文前缀（显示在每条公告前面）。 */
    var prefix: String = "&6[公告] &r"

    /** 正文上方的框线，可多行。留空则不显示。 */
    var header: MutableList<String> = mutableListOf("&8&m                                        ")

    /** 正文下方的框线。 */
    var footer: MutableList<String> = mutableListOf("&8&m                                        ")

    /** 播报时播放的音效 id，留空不播。 */
    var sound: String = "minecraft:block.note_block.bell"

    /** 定时轮播。 */
    var auto: AutoBroadcast = AutoBroadcast()

    fun normalize() {
        if (channel == null || channel.isBlank()) channel = "chat"
        if (prefix == null) prefix = ""
        if (header == null) header = mutableListOf()
        if (footer == null) footer = mutableListOf()
        if (sound == null) sound = ""
        if (auto == null) auto = AutoBroadcast()
        auto.normalize()
    }
}

/** 公告配置的中文注释表。 */
object AnnounceComments {

    val ANNOUNCE: Map<String, String> = mapOf(
        "enabled" to "是否启用 /announce 命令。",
        "channel" to "播报通道：\n" +
            "  chat       聊天框（默认）\n" +
            "  actionbar  动作栏，不占聊天记录\n" +
            "  both       两边都发\n" +
            "  off        都不发（只写控制台）",
        "prefix" to "正文前缀，显示在每条公告前面。支持 & 颜色码。",
        "header" to "正文上方的框线，可以写多行。留空数组则不显示框。\n" +
            "&m 是删除线 —— 用它画横线最省事，不用数破折号。",
        "footer" to "正文下方的框线。",
        "sound" to "播报时播放的音效 id，留空不播。\n" +
            "调低音量或设成空串可以避免大服上一起响很吵。",
        "auto" to "定时轮播公告。",
        "auto.enabled" to "是否启用轮播。",
        "auto.intervalSeconds" to "轮播间隔（秒），下限 30。",
        "auto.random" to "true = 每次随机挑一条；false = 按顺序循环。",
        "auto.channel" to "轮播走哪个通道（可以和手动公告不同，比如轮播只走 actionbar）。",
        "auto.messages" to "轮播内容列表。一条里可以用 \\n 分行，支持 & 颜色码与 %变量%。",
    )
}

/** 加载 / 保存 [AnnounceConfig]。 */
object AnnounceStore {

    private const val FILE = "display/announce.json"

    @Volatile
    var config: AnnounceConfig = AnnounceConfig()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, AnnounceComments.ANNOUNCE) { AnnounceConfig() }
        config.normalize()
    }

    fun save() = ConfigFiles.save(FILE, config, AnnounceComments.ANNOUNCE)
}
