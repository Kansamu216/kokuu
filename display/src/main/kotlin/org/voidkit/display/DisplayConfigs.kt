package org.voidkit.display

/**
 * 展示层配置：MOTD / TAB / 记分牌 / 聊天栏，各自独立成文件。
 *
 * 之所以从 [VoidkitConfig] 里拆出来 —— 这四块的自定义需求最多，全挤在一个文件里既难找也难改。
 * 旧配置里的对应字段由 [org.voidkit.core.VoidkitCore.loadConfig] 一次性迁移过来。
 *
 * 所有文本字段都支持：
 *  - `&a` `&l` 等 legacy 颜色与样式
 *  - `%变量%`（见 `PlaceholderRegistry`）
 *  - `[文字](run:/命令 "悬停提示")` 可点击语法，详见 [org.voidkit.core.util.Text]
 *
 * ⚠️ 这些类由 Gson 反序列化，**Kotlin 的默认参数不会生效**（Gson 走 Unsafe 分配对象），
 * 所以每个类都必须有 `normalize()` 把 null / 越界值补回来，加载后立刻调用。
 */

// ============================================================ MOTD

/** `display/motd.json` */
class MotdConfig {
    var enabled: Boolean = true

    /** 重新推送 MOTD 的间隔（秒）。 */
    var updateSeconds: Int = 5

    /** 多套 MOTD 的挑选方式：`fixed` 固定第一套 / `order` 依次轮换 / `random` 加权随机。 */
    var rotation: String = "fixed"

    var variants: MutableList<MotdVariant> = mutableListOf(MotdVariant())

    /**
     * 是否把当前 MOTD 也写回 `server.properties`。
     *
     * **默认 false** —— 整合包与很多服主自己维护着那个文件里的 MOTD，模组不声不响地
     * 改掉它属于越界（而且会被整合包下次更新覆盖回去，白改）。运行时的服务器列表
     * MOTD 照常生效，只是不再动文件。确实想让它接管，再显式打开。
     */
    var writeServerProperties: Boolean = false

    fun normalize() {
        if (variants == null || variants.isEmpty()) variants = mutableListOf(MotdVariant())
        variants.forEach { it.normalize() }
        if (rotation !in ROTATIONS) rotation = "fixed"
        if (updateSeconds < 1) updateSeconds = 5
    }

    private companion object {
        val ROTATIONS = setOf("fixed", "order", "random")
    }
}

/** 一套 MOTD 文本。 */
class MotdVariant {
    var lines: MutableList<String> = mutableListOf(
        "&b&lVoidkit&f&l 服务器",
        "&7欢迎回来，祝你玩得开心"
    )

    /** `random` 模式下的权重，越大越容易被选中。 */
    var weight: Int = 1

    fun normalize() {
        if (lines == null || lines.isEmpty()) {
            lines = mutableListOf("&b&lVoidkit&f&l 服务器")
        }
        if (weight < 1) weight = 1
    }
}

// ============================================================ TAB

/** `display/tab.json` */
class TabConfig {
    var enabled: Boolean = true
    var updateSeconds: Int = 5

    var header: MutableList<String> = mutableListOf(
        "&b&lKOKUU&f&l 服务器",
        "&7在线 &a%online%&7/&a%max%   &7TPS &a%tps%"
    )
    var footer: MutableList<String> = mutableListOf(
        "&7延迟 &a%ping%ms   &7世界 &a%world%",
        "&7%date% %time%"
    )

    /**
     * 是否把权限组的前缀/后缀显示到 TAB 玩家名上（例如 `&c[管理]&r 玩家名`）。
     *
     * 用原版**队伍（Team）机制**实现：1.21.1 没有直接改列表名的公开 API，
     * 列表里显示的就是「队伍前缀 + 玩家名 + 队伍后缀」。
     * 因此只能做到**按组统一**，不能给每个玩家写各自的格式。
     */
    var useGroupPrefix: Boolean = true

    fun normalize() {
        if (header == null) header = mutableListOf()
        if (footer == null) footer = mutableListOf()
        if (updateSeconds < 1) updateSeconds = 5
    }
}

// ============================================================ 记分牌

/** `display/scoreboard.json` */
class ScoreboardConfig {
    var enabled: Boolean = true
    var updateSeconds: Int = 2

    /** 多套看板：按 [BoardDefinition.priority] 从高到低，取第一套该玩家有权限的。 */
    var boards: MutableList<BoardDefinition> = mutableListOf(BoardDefinition())

    fun normalize() {
        if (boards == null || boards.isEmpty()) boards = mutableListOf(BoardDefinition())
        boards.forEach { it.normalize() }
        if (updateSeconds < 1) updateSeconds = 2
    }

    /** 选出该玩家应该看到的那一套；[hasPermission] 由调用方传入（通常是 PermissionManager）。 */
    fun selectFor(hasPermission: (String) -> Boolean): BoardDefinition? {
        normalize()
        return boards
            .filter { it.permission.isBlank() || hasPermission(it.permission) }
            .maxByOrNull { it.priority }
    }
}

/** 一套侧边栏看板。 */
class BoardDefinition {
    /** 仅用于日志与面板识别。 */
    var id: String = "default"
    var title: String = "&b&lKOKUU"
    var lines: MutableList<String> = mutableListOf(
        "&7&m────────────",
        "&f玩家 &a%player%",
        "&f在线 &a%online%&7/&a%max%",
        "&f时长 &a%playtime%",
        "&f延迟 &a%ping%ms",
        "&fTPS &a%tps%",
        "&7&m────────────"
    )

    /** 需要这个权限节点才显示这套；留空表示所有玩家都能用。 */
    var permission: String = ""

    /** 越大越优先。 */
    var priority: Int = 0

    fun normalize() {
        if (id == null || id.isBlank()) id = "default"
        if (title == null) title = "&b&lKOKUU"
        if (lines == null) lines = mutableListOf()
        if (permission == null) permission = ""
    }
}

// ============================================================ 聊天栏

/** `display/chat.json` */
class ChatConfig {
    var enabled: Boolean = true

    /**
     * 聊天格式模板。
     *
     * 可用 `{name}` `{message}` `{world}`，`%prefix%` `%group%` 等变量，
     * 以及 `[文字](动作)` 可点击语法 —— 想让玩家名可点就这么写：
     *
     * ```
     * "%prefix%[&f{name}&r](suggest:/msg {name} "&7点击私聊") &8» &f{message}"
     * ```
     */
    var format: String = "%prefix%&f{name} &8» &f{message}"

    /** 是否允许有权限的玩家在聊天里打 `&` 颜色代码。 */
    var colorEnabled: Boolean = true

    var colorPermission: String = "voidkit.chat.color"

    var placeholderPermission: String = "voidkit.chat.placeholder"

    /** 被禁言的玩家是否直接拦下（false = 只在档案里标记，不阻止发言）。 */
    var blockMuted: Boolean = true

    /**
     * 鼠标悬停在**玩家名**上时弹出的浮层，每项一行。支持 `&` 颜色与 `%变量%`。
     *
     * ⚠️ 原版协议里浮层只能是**文字** —— 做不到显示玩家皮肤头像（那需要客户端 mod）。
     * 想更醒目可以用 `&7▎` 这类符号做侧边条。
     */
    var nameHover: MutableList<String> = mutableListOf(
        "&7玩家：&f%player%",
        "&7组别：%prefix%&f%group%",
        "&7位置：&f%x% &7/&f %y% &7/&f %z%",
        "&7世界：&f%world%",
        "&7等级：&f%level%",
        "&7在线时长：&f%playtime%"
    )

    /** 关掉就不给玩家名挂这个浮层。 */
    var nameHoverEnabled: Boolean = true

    /**
     * 点击**玩家名**时执行什么。默认直接发一条 `/tpa` 传送请求。
     *
     * 用 `{name}` 代表该玩家名，动作前缀同富文本语法（`run:` / `suggest:` / `url:` / `copy:`）。
     * 留空 = 名字不可点击。想改成“点击私聊”就写 `suggest:/msg {name} `。
     *
     * ⚠️ 默认这个动作会**直接执行**命令（`run:`），点一下就发请求 —— 因为需求就是
     * "点名字发 tpa"。如果担心误触，改成 `suggest:` 前缀即可变成“填进聊天框、玩家自己回车”。
     */
    var nameClick: String = "run:/tpa {name}"

    fun normalize() {
        if (format == null || format.isBlank()) format = "%prefix%&f{name} &8» &f{message}"
        if (colorPermission == null) colorPermission = "voidkit.chat.color"
        if (placeholderPermission == null) placeholderPermission = "voidkit.chat.placeholder"
        if (nameHover == null) nameHover = mutableListOf()
        if (nameClick == null) nameClick = ""
    }
}
