package org.voidkit.display

import org.voidkit.core.VoidkitCore
import org.voidkit.core.util.ConfigFiles

/**
 * 展示层的四份配置（MOTD / TAB / 记分牌 / 聊天栏）。
 *
 * 它们原先挂在 `VoidkitCore` 的静态字段上，属于内核。现在整块搬到 display 插件 ——
 * 不装这个插件，内核里就没有 `MotdConfig` 这些类型，也不会有对应的读写逻辑。
 *
 * 文件都落在 `voidkit/display/` 下（由内核的 [ConfigFiles] 解析相对路径）。
 */
object DisplayStore {

    const val MOTD_FILE = "display/motd.json"
    const val TAB_FILE = "display/tab.json"
    const val SCOREBOARD_FILE = "display/scoreboard.json"
    const val CHAT_FILE = "display/chat.json"

    var motd: MotdConfig = MotdConfig()
        private set

    var tab: TabConfig = TabConfig()
        private set

    var scoreboard: ScoreboardConfig = ScoreboardConfig()
        private set

    var chat: ChatConfig = ChatConfig()
        private set

    /** 由 [VoidkitDisplay] 在服务端启动阶段调用。 */
    fun load() {
        motd = loadOne(MOTD_FILE, DisplayComments.MOTD, { MotdConfig() }, MotdConfig::normalize)
        tab = loadOne(TAB_FILE, DisplayComments.TAB, { TabConfig() }, TabConfig::normalize)
        scoreboard = loadOne(
            SCOREBOARD_FILE, DisplayComments.SCOREBOARD,
            { ScoreboardConfig() }, ScoreboardConfig::normalize,
        )
        chat = loadOne(CHAT_FILE, DisplayComments.CHAT, { ChatConfig() }, ChatConfig::normalize)

        VoidkitCore.LOG.info(
            "展示层配置就绪：MOTD {} 套 / 记分牌 {} 套 / TAB {} / 聊天 {}",
            motd.variants.size,
            scoreboard.boards.size,
            if (tab.enabled) "开" else "关",
            if (chat.enabled) "开" else "关",
        )
    }

    fun saveMotd() = ConfigFiles.save(MOTD_FILE, motd, DisplayComments.MOTD)

    fun saveTab() = ConfigFiles.save(TAB_FILE, tab, DisplayComments.TAB)

    fun saveScoreboard() = ConfigFiles.save(SCOREBOARD_FILE, scoreboard, DisplayComments.SCOREBOARD)

    fun saveChat() = ConfigFiles.save(CHAT_FILE, chat, DisplayComments.CHAT)

    fun saveAll() {
        saveMotd()
        saveTab()
        saveScoreboard()
        saveChat()
    }

    private inline fun <reified T : Any> loadOne(
        relative: String,
        comments: Map<String, String>,
        noinline fallback: () -> T,
        noinline normalize: (T) -> Unit,
    ): T {
        val value = ConfigFiles.load(relative, T::class.java, comments, fallback)
        // Gson 走 Unsafe 分配对象，Kotlin 的默认参数不生效 —— 加载后必须补一遍
        normalize(value)
        ConfigFiles.save(relative, value, comments)
        return value
    }
}
