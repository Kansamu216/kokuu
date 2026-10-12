package org.voidkit.display

import org.voidkit.core.util.ConfigFiles
import org.voidkit.core.util.Icons
import org.voidkit.core.VoidkitCore

/** 菜单里的一个按钮。 */
class MenuItemDef {
    /** 图标，物品 id，例如 `minecraft:clock`。 */
    var icon: String = "minecraft:stone"

    /** 显示名称，支持 `&` 颜色码与 `%变量%`。留空则用物品原名。 */
    var name: String = ""

    /** 说明文字，鼠标悬停时显示。 */
    var lore: MutableList<String> = mutableListOf()

    /**
     * 点击动作。见 [MenuGui] 的说明：
     * `run:` / `console:` / `menu:` / `back` / `close` / `url:` / `copy:` / `suggest:`
     */
    var action: String = ""

    /** 需要这个权限节点才能点。留空 = 人人可点。 */
    var permission: String = ""

    /** 图标显示的数量（纯视觉）。 */
    var amount: Int = 1

    fun normalize() {
        if (icon == null || icon.isBlank()) icon = "minecraft:stone"
        if (name == null) name = ""
        if (action == null) action = ""
        if (permission == null) permission = ""
        if (lore == null) lore = mutableListOf()
        if (amount < 1) amount = 1
        if (amount > 64) amount = 64
    }
}

/** 一个菜单。 */
class MenuDef {
    /** 界面标题，支持 `&` 颜色码。 */
    var title: String = "&8菜单"

    /** 行数 1~6。 */
    var rows: Int = 3

    /** 槽位号（"0"~"53"）-> 按钮。 */
    var items: MutableMap<String, MenuItemDef> = linkedMapOf()

    fun normalize() {
        if (title == null || title.isBlank()) title = "&8菜单"
        if (rows < 1) rows = 3
        if (rows > 6) rows = 6
        if (items == null) items = linkedMapOf()
        items.values.forEach { it?.normalize() }
        // 行数与槽位数不匹配时按行数裁掉越界的项，避免打开时静默丢失
        val capacity = rows * 9
        items.entries.removeIf { (key, _) ->
            val slot = key.toIntOrNull()
            slot == null || slot < 0 || slot >= capacity
        }
    }

    fun capacity(): Int = rows * 9
}

/** 绑定到物品上的菜单开启方式（钟表菜单）。 */
class OpenerDef {
    /** 要打开的菜单名。 */
    var menu: String = ""

    /** 该物品的显示名（留空 = 用物品原名）。 */
    var name: String = ""

    /** 说明文字。 */
    var lore: MutableList<String> = mutableListOf()

    /** 需要这个权限节点才能用。 */
    var permission: String = ""

    fun normalize() {
        if (menu == null) menu = ""
        if (name == null) name = ""
        if (permission == null) permission = ""
        if (lore == null) lore = mutableListOf()
    }
}

/** 菜单全局设置。 */
class MenuSettings {
    /** 执行完动作后自动关闭界面。关掉的话适合“连点多个按钮”的菜单。 */
    var closeOnAction: Boolean = true

    /** 打开菜单时播放的音效 id（留空不播）。 */
    var openSound: String = "minecraft:block.note_block.bell"

    fun normalize() {
        if (openSound == null) openSound = ""
    }
}

/** `display/menu.json` 的整体结构。 */
class MenuFile {
    var settings: MenuSettings = MenuSettings()
    var menus: MutableMap<String, MenuDef> = linkedMapOf()
    var openers: MutableMap<String, OpenerDef> = linkedMapOf()

    fun normalize() {
        if (settings == null) settings = MenuSettings()
        settings.normalize()
        if (menus == null) menus = linkedMapOf()
        if (openers == null) openers = linkedMapOf()
        menus.values.forEach { it?.normalize() }
        openers.values.forEach { it?.normalize() }
        // 指向不存在菜单的绑定直接丢掉，免得玩家右键后什么都不发生还不报错
        openers.entries.removeIf { (_, def) -> def == null || !menus.containsKey(def.menu) }
    }
}

/** 菜单配置的中文注释表。 */
object MenuComments {

    val MENU: Map<String, String> = mapOf(
        "settings" to "菜单全局设置。",
        "settings.closeOnAction" to "点完按钮是否自动关闭界面。\n" +
            "false = 保持打开，适合“连续点几个按钮”的菜单（但命令的反馈消息会被界面挡住）。",
        "settings.openSound" to "打开菜单时播放的音效 id，留空不播。",
        "menus" to "菜单定义。key 是菜单名（用在 /menu <名字> 和 action 的 menu: 里）。",
        "menus.*.title" to "界面标题，支持 & 颜色码与 %变量%。",
        "menus.*.rows" to "界面行数 1~6。行数 x 9 = 可用槽位数。",
        "menus.*.items" to "槽位号（0~53，从左到右、从上到下）-> 按钮定义。\n" +
            "\n" +
            "每个按钮的字段：\n" +
            "  icon        图标物品 id，例如 minecraft:clock\n" +
            "  name        显示名称，支持 & 颜色码与 %变量%\n" +
            "  lore        说明文字（字符串数组），悬停时显示\n" +
            "  action      点击动作，见下\n" +
            "  permission  需要这个权限节点才能点；留空 = 人人可点\n" +
            "  amount      图标显示数量（纯视觉，1~64）\n" +
            "\n" +
            "action 支持的动作：\n" +
            "  run:/spawn                以玩家身份执行命令（走他真实权限）\n" +
            "  console:/time set day     以控制台身份执行（绕过权限，只用于可信菜单）\n" +
            "  menu:shop                 打开另一个菜单（可用 back 返回）\n" +
            "  back                      返回上一个菜单\n" +
            "  close                     关闭界面\n" +
            "  url:https://…             发一条可点击链接\n" +
            "  copy:文本                 发一条可复制文本\n" +
            "  suggest:/msg              发一条“点击填入聊天框”的提示\n" +
            "\n" +
            "⚠️ url / copy / suggest 无法在点击格子时立即生效 —— 打开链接、写剪贴板、\n" +
            "   填聊天框都必须由客户端自己发起。它们会退化成“发一条可点击的聊天提示”。",
        "openers" to "把菜单绑定到物品上（经典“钟表菜单”）。key 是物品 id。\n" +
            "玩家手持该物品右键即打开对应菜单。\n" +
            "\n" +
            "  menu        要打开的菜单名（必须在 menus 里存在）\n" +
            "  name        该物品的显示名（留空 = 用物品原名）\n" +
            "  lore        说明文字\n" +
            "  permission  需要这个权限节点才能用；留空 = 人人可用",
    )
}

/**
 * 菜单配置的读写。
 *
 * 打开菜单很频繁，所以 [menuOf] 走内存缓存；只有 [load] / [save] 碰磁盘。
 */
object MenuStore {

    private const val FILE = "display/menu.json"

    @Volatile
    var file: MenuFile = MenuFile()
        private set

    fun load() {
        file = ConfigFiles.load(FILE, MenuComments.MENU) { defaultFile() }
        file.normalize()
        warnBadIcons()
    }

    fun save() = ConfigFiles.save(FILE, file, MenuComments.MENU)

    fun menuOf(name: String): MenuDef? = file.menus[name]

    fun openerOf(itemId: String): OpenerDef? = file.openers[itemId]

    /**
     * 校验图标 id。写错一个字母不该让菜单打不开（[Icons.of] 会退化成屏障），
     * 但应该让服主在日志里看到 —— 否则他会以为是“菜单坏了”。
     */
    private fun warnBadIcons() {
        file.menus.forEach { (menuName, def) ->
            def.items.forEach { (slot, item) ->
                if (!Icons.exists(item.icon)) {
                    VoidkitCore.LOG.warn("菜单 {} 的槽位 {} 图标 id 无效：{}（将显示为屏障方块）", menuName, slot, item.icon)
                }
            }
        }
        file.openers.forEach { (id, _) ->
            if (!Icons.exists(id)) {
                VoidkitCore.LOG.warn("菜单绑定引用了无效的物品 id：{}（这个绑定不会生效）", id)
            }
        }
    }

    /** 首次生成的示例菜单 —— 让服主一眼看懂结构，改起来有参照。 */
    private fun defaultFile(): MenuFile {
        val f = MenuFile()

        val main = MenuDef()
        main.title = "&8&l服务器菜单"
        main.rows = 3

        main.items["11"] = MenuItemDef().apply {
            icon = "minecraft:compass"
            name = "&a&l回城"
            lore = mutableListOf("&7回到出生点", "&8点一下就走")
            action = "run:/spawn"
        }
        main.items["13"] = MenuItemDef().apply {
            icon = "minecraft:clock"
            name = "&b&l传送请求"
            lore = mutableListOf("&7给别人发传送请求", "&8点一下填入聊天框")
            action = "suggest:/tpa "
        }
        main.items["15"] = MenuItemDef().apply {
            icon = "minecraft:name_tag"
            name = "&e&l我的称号"
            lore = mutableListOf("&7打开称号界面")
            action = "run:/title"
        }
        main.items["26"] = MenuItemDef().apply {
            icon = "minecraft:barrier"
            name = "&7关闭"
            action = "close"
        }

        f.menus["main"] = main

        val info = MenuDef()
        info.title = "&8服务器信息"
        info.rows = 3
        info.items["13"] = MenuItemDef().apply {
            icon = "minecraft:book"
            name = "&f在线：&a%online% &7/ &a%max%"
            lore = mutableListOf("&7TPS：&f%tps%", "&7时间：&f%time%&7  &f%date%")
            action = "close"
        }
        f.menus["info"] = info

        // 示例：手持时钟右键打开主菜单（经典钟表菜单）
        f.openers["minecraft:clock"] = OpenerDef().apply {
            menu = "main"
            name = "&6&l服务器菜单"
            lore = mutableListOf("&7拿在手里右键打开")
        }

        return f
    }
}
