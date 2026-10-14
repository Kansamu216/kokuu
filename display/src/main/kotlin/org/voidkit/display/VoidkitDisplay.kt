package org.voidkit.display

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import org.voidkit.core.api.ConfigSnapshots
import org.voidkit.core.api.PublicNodes
import org.voidkit.core.core.PluginRegistry
import org.voidkit.title.VoidkitTitle

/**
 * voidkit_display —— 面向玩家的展示与交互。
 *
 * 六块内容，共用同一套富文本能力（颜色码 / 变量 / 可点击动作）：
 *
 * | 模块 id | 内容 |
 * |---|---|
 * | `motd` | 服务器列表的 MOTD（可配多套轮换） |
 * | `tab` | TAB 顶栏 / 底栏 |
 * | `scoreboard` | 侧边栏记分牌（可按权限切换多套） |
 * | `chat-format` | 聊天栏格式、玩家名悬停浮层、点击动作 |
 * | `announce` | 公告与定时轮播 |
 * | `menu` | JSON 驱动的箱子菜单 / 钟表菜单 |
 *
 * ⚠️ 服务端能做的最大范围到此为止：**MOTD 与记分牌不支持点击/悬停**，这是原版协议限制
 * （前者是服务器列表文本，后者是纯字符串行）。聊天栏、TAB 顶底栏、菜单支持。
 *
 * ## 为什么公告和菜单也在这里
 *
 * 它们和 MOTD / TAB 解决的是同一类问题 —— "服务器想向玩家展示点什么、并让玩家能点"。
 * 而每块内容仍是**独立可开关的 `Feature`**（见 `features` 配置），
 * 所以打包在一起不损失任何灵活性。
 */
@Mod(VoidkitDisplay.MODID)
class VoidkitDisplay(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(MotdFeature(), MODID)
        PluginRegistry.register(TabFeature(), MODID)
        PluginRegistry.register(ScoreboardFeature(), MODID)
        PluginRegistry.register(ChatFormatFeature(), MODID)
        PluginRegistry.register(AnnounceFeature(), MODID)
        PluginRegistry.register(MenuFeature(), MODID)

        // 合并进来的 title：不再是独立 mod（一 jar 一 modId），由宿主构造期显式初始化。
        // owner 标签仍用原 modId（见 VoidkitTitle），展示归组不变。
        VoidkitTitle()

        // 聊天栏的「变量 / 颜色」对玩家默认开放 —— 开箱即用，否则玩家既用不了彩色字，
        // 也无法在**自己发言里**用变量。想收紧就在某个组加 `-voidkit.chat.color` /
        // `-voidkit.chat.placeholder` 即可（显式否定在这些节点之前生效）。
        // 注意：聊天**模板**本身不受这两个节点影响，永远解析 —— 那是服主配置，不是玩家输入。
        PluginRegistry.onBoot(MODID) {
            val chat = DisplayStore.chat
            // 按来源登记：聊天节点名取自配置，重载后要能**替换**旧名字。
            PublicNodes.registerFor(MODID, chat.colorPermission, chat.placeholderPermission)
        }

        // 公开配置快照给面板（web 插件不需要 import 本插件）
        ConfigSnapshots.register("display") {
            val chat = DisplayStore.chat
            val motd = DisplayStore.motd
            val a = AnnounceStore.config
            mapOf(
                "motd.enabled" to motd.enabled,
                "motd.rotation" to motd.rotation,
                "motd.variants" to motd.variants.size,
                "tab.updateSeconds" to DisplayStore.tab.updateSeconds,
                "scoreboard.boards" to DisplayStore.scoreboard.boards.size,
                "chat.nameHoverEnabled" to chat.nameHoverEnabled,
                "announce.channel" to a.channel,
                "announce.autoEnabled" to a.auto.enabled,
                "announce.autoCount" to a.auto.messages.size,
                "menu.count" to MenuStore.file.menus.size,
                "menu.openerCount" to MenuStore.file.openers.size,
            )
        }

        // 配置在服务端启动阶段读 —— 构造期保持零 IO。
        // ⚠️ 用内核的 [PluginRegistry.onBoot] 而非加载器事件：事件回调顺序不由我们控制。
        PluginRegistry.onBoot(MODID) {
            DisplayStore.load()
            AnnounceStore.load()
            MenuStore.load()
        }

        // `/vk reload` 时重读的**只有配置文件**：显示层四份 + 公告 + 菜单。
        // 聊天那两个公开节点名来自配置，所以这里按来源重登记（会替换掉改名前的旧节点）。
        PluginRegistry.onReloadConfig(MODID) {
            DisplayStore.load()
            AnnounceStore.load()
            MenuStore.load()
            val chat = DisplayStore.chat
            PublicNodes.registerFor(MODID, chat.colorPermission, chat.placeholderPermission)
        }
    }

    companion object {
        const val MODID = "voidkit_display"
        val LOG = LogUtils.getLogger()
    }
}
