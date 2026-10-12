package org.voidkit.display

/**
 * 展示层配置的中文注释表。
 *
 * 这些说明会在写配置时按字段插进 JSON 里（见 `CommentedJson`）——
 * 纯 JSON 不能写注释，但配置是给人看的。
 *
 * 注释留在这里而不是 core：MOTD 的字段含义只有 display 插件清楚，
 * 放在内核里会导致「加一个字段要改两个模块」。
 */
object DisplayComments {

    val MOTD: Map<String, String> = mapOf(
        "enabled" to "是否启用自定义 MOTD。关闭后使用 server.properties 里的默认值。",
        "updateSeconds" to "重新推送 MOTD 的间隔（秒），改完配置等这么久就会生效。",
        "rotation" to "多套 MOTD 的挑选方式：\n" +
            "  fixed  = 固定用第一套\n" +
            "  order  = 按顺序轮换\n" +
            "  random = 按 weight 加权随机",
        "writeServerProperties" to "是否把当前 MOTD 也写回 server.properties（默认 false）。\n" +
            "整合包与多数服主自己维护那个文件，模组默认不碰它；\n" +
            "运行时服务器列表里显示的 MOTD 不受此开关影响。",
        "variants" to "MOTD 内容，可以准备多套。每套最多两行（原版客户端限制）。",
        "variants[].lines" to "这套 MOTD 的文本，最多两行。支持 & 颜色码与 %变量%。\n" +
            "⚠️ 服务器列表里不支持点击事件，所以 [文字](run:/命令) 这类写法在这里只是普通文字。",
        "variants[].weight" to "random 模式下的权重，越大越容易出现。",
    )

    val TAB: Map<String, String> = mapOf(
        "enabled" to "是否启用自定义 TAB 玩家列表（顶栏 / 底栏）。",
        "updateSeconds" to "刷新间隔（秒）。",
        "header" to "列表顶部的文字，可以写多行。支持 & 颜色码、%变量% 与 [文字](动作) 可点击语法。",
        "footer" to "列表底部的文字，可以写多行。语法同 header。",
        "useGroupPrefix" to "是否把权限组的前缀/后缀显示到 TAB 玩家名上（例如 [管理] 玩家名）。\n" +
            "用原版队伍机制实现，所以只能按组统一，没法给每个玩家写不同格式。",
    )

    val SCOREBOARD: Map<String, String> = mapOf(
        "enabled" to "是否启用侧边栏记分牌。",
        "updateSeconds" to "刷新间隔（秒）。每个玩家的侧边栏是独立的，所以 %ping% 这类变量互不干扰。",
        "boards" to "可以配多套看板。玩家看到优先级最高的、且他有权限的那一套。",
        "boards[].id" to "这套看板的标识，仅用于日志和面板识别。",
        "boards[].title" to "侧边栏顶部的标题。",
        "boards[].lines" to "侧边栏内容，从上到下排列，最多 15 行（原版限制）。\n" +
            "支持 %变量%。注意有些行是空行占位也要写 \"\" —— 留空会被客户端折叠掉。\n" +
            "⚠️ 原版记分牌不支持点击事件，所以这里写 [文字](run:/命令) 不会生效。",
        "boards[].permission" to "需要这个权限节点才能看到这套看板；留空 = 所有玩家都能用。",
        "boards[].priority" to "优先级，越大越优先。玩家会看到「有权限的里面优先级最高」的那套。",
    )

    val CHAT: Map<String, String> = mapOf(
        "enabled" to "是否启用聊天栏美化。",
        "format" to "聊天格式模板。可用 {name} {message} {world} 以及 %prefix% %group% 等变量。\n" +
            "支持 [文字](动作) 可点击语法 —— 想让玩家名可点（点击自动填私聊）就这么写：\n" +
            "  \"%prefix%[&f{name}&r](suggest:/msg {name} \"&7点击私聊\") &8» &f{message}\"",
        "colorEnabled" to "是否允许有 colorPermission 权限的玩家在聊天里打 & 颜色代码。",
        "colorPermission" to "允许使用彩色字的权限节点。",
        "placeholderPermission" to "允许在聊天里使用 %变量% 的权限节点（普通玩家只会看到原始文字）。",
        "blockMuted" to "被禁言的玩家发言是否直接拦下。false = 只在档案里标记，不真的阻止发言。",
        "nameHover" to "鼠标悬停到**玩家名**上时弹出的浮层，每项一行。支持 & 颜色与 %变量%。\n" +
            "常用变量：%player% %group% %prefix% %x% %y% %z% %world% %level%（经验等级）%playtime% %ping% %health%\n" +
            "⚠️ 原版协议里浮层只能是**文字**，做不到显示玩家皮肤头像（那需要客户端 mod）。\n" +
            "想更醒目可以用 ▎ ● 这类符号做装饰。",
        "nameHoverEnabled" to "关掉就不给玩家名挂这个浮层。",
        "nameClick" to "点击**玩家名**时执行什么。用 {name} 代表该玩家名。\n" +
            "  run:/tpa {name}        点一下给对方发传送请求（默认）\n" +
            "  suggest:/msg {name}    点一下把私聊命令填进聊天框，自己回车再发\n" +
            "  url:https://…           打开链接\n" +
            "留空 = 名字不可点。\n" +
            "⚠️ run: 是**直接执行**，点一下就发出去；怕误触就改成 suggest: 前缀。",
    )

}
