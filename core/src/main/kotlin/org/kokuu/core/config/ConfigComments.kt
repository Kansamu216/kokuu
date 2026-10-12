package org.kokuu.core.config

/**
 * 各配置文件的中文注释表。
 *
 * 键是「字段路径」（以 `.` 分隔），值是写在该字段上方的说明；支持 `\n` 写成多行。
 * 数组元素的字段用 `数组名[].字段名` 表示，并且只会写在**第一个**元素上方。
 *
 * 与 [org.kokuu.core.util.CommentedJson] 配合：写文件时插注释，读文件时剥掉。
 *
 * 这几张表是配置的「说明书」，改动字段时记得一起改 —— 否则使用者看到的注释会对不上。
 */
object ConfigComments {

    // ============================================================ 核心配置

    val CORE: Map<String, String> = mapOf(
        "features" to "模块开关。键是模块 id，值 false 表示停用该模块；没写的模块用自身默认状态。\n" +
            "游戏内 /kcore features 可查看全部 id 与当前状态。",
        "apiEnabled" to "是否启用管理 API（供 Web 面板 / Hub 调用）。关掉后不影响游戏功能。",
        "apiBind" to "API 监听地址。默认 127.0.0.1 只允许本机访问；\n" +
            "面板在另一台机器上时改成 0.0.0.0，并务必同时更换端口与令牌。",
        "apiPort" to "API 端口。",
        "apiToken" to "API 访问令牌，首次启动自动生成，用 /kcore token 查看。\n" +
            "⚠️ 不要外泄：拿到它就等于拿到整台服务器的管理权。",
        "apiRateLimitPerSecond" to "单个 IP 每秒允许的请求数，防止面板被刷。",
        "recordIps" to "是否记录玩家 IP 历史（面板上可看某个玩家用过哪些 IP）。",
        "maxJoinLogEntries" to "上下线日志最多保留多少条，超出后从最旧的开始丢。",
        "defaultHomeLimit" to "默认组玩家能设置多少个家。单个权限组可以用 kokuucore.home.<数量> 覆盖。",
        "maxHomes" to "家数量的硬上限，防止权限配置写错时被刷爆。",
        "tpaEnabled" to "是否允许玩家之间互相传送（/tpa /tpahere /tpaccept）。",
        "tpaTimeoutSeconds" to "传送请求的有效期（秒），过期自动作废。",
        "backEnabled" to "是否启用 /back（回到上一次死亡或传送前的位置）。",
        "teleportCooldownSeconds" to "传送冷却（秒），0 = 不限制。",
        "autoSaveSeconds" to "自动将玩家档案落盘的间隔（秒）。注意：数据本身是「写时即落」的，\n" +
            "这个只是兜底的周期性整体保存。",
        "clearLagEnabled" to "是否启用定时清扫（清道夫）。",
        "clearLagIntervalSeconds" to "清扫间隔（秒）。",
        "clearLagWarnSeconds" to "清扫前多少秒开始倒计时提醒，0 = 不提醒。",
        "clearLagRemoveItems" to "清扫时是否删除掉落物。",
        "clearLagRemoveXpOrbs" to "清扫时是否删除经验球。",
        "clearLagRemoveArrows" to "清扫时是否删除地上的箭。",
        "clearLagRemoveProjectiles" to "清扫时是否删除其他投掷物（雪球、鸡蛋等）。",
        "clearLagKeepNamedItems" to "是否保留有自定义名称的物品（一般是玩家命名过的珍视物）。",
        "clearLagAnnounce" to "清扫完成后是否全服提示清理了多少个实体。",
    )

    // ============================================================ MOTD

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

    // ============================================================ TAB

    val TAB: Map<String, String> = mapOf(
        "enabled" to "是否启用自定义 TAB 玩家列表（顶栏 / 底栏）。",
        "updateSeconds" to "刷新间隔（秒）。",
        "header" to "列表顶部的文字，可以写多行。支持 & 颜色码、%变量% 与 [文字](动作) 可点击语法。",
        "footer" to "列表底部的文字，可以写多行。语法同 header。",
        "useGroupPrefix" to "是否把权限组的前缀/后缀显示到 TAB 玩家名上（例如 [管理] 玩家名）。\n" +
            "用原版队伍机制实现，所以只能按组统一，没法给每个玩家写不同格式。",
    )

    // ============================================================ 记分牌

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

    // ============================================================ 聊天栏

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

    // ============================================================ 权限

    val PERM_SETTINGS: Map<String, String> = mapOf(
        "defaultGroup" to "没有显式分组的玩家归到这个组。",
        "opBypass" to "原版 OP（等级 ≥ 2）是否直接放行所有权限检查。\n" +
            "关掉后 OP 也得靠权限节点说话，适合把管理权完全交给权限系统。",
        "defaultValue" to "谁都没命中时的默认结果。\n" +
            "⚠️ 一般保持 false。设成 true 等于人人都有权限（除非显式用 -节点 否定）。",
        "wildcardEnabled" to "是否启用通配：星号命中一切，kokuucore.home.星号 命中 kokuucore.home.set 这类前缀匹配。",
        "prefixFromHighestWeight" to "聊天与 TAB 的前缀取哪个组：\n" +
            "  true  = 权重最高的那个组（附加组也可能顶掉主组的前缀）\n" +
            "  false = 始终用主组",
    )

    val PERM_GROUPS: Map<String, String> = mapOf(
        "groups" to "权限组定义。key 就是组名，会原样出现在 /perm 命令与网页面板里。\n" +
            "\n" +
            "每个组的字段：\n" +
            "  prefix        聊天/TAB 显示的前缀，支持 & 颜色码\n" +
            "  suffix        后缀，通常留空\n" +
            "  weight        权重，越大越优先（决定前缀取谁、节点冲突时谁说了算）\n" +
            "  permissions   权限节点列表\n" +
            "  parents       父组，其节点与权重都会被继承（可多级，不怕写环）\n" +
            "\n" +
            "节点写法：\n" +
            "  kokuucore.command.fly     精确匹配\n" +
            "  kokuucore.home.星号        前缀通配\n" +
            "  星号                       一切权限\n" +
            "  -kokuucore.command.fly    否定：明确禁止，优先级高于同组里的肯定",
        "groups.default" to "默认组：所有新玩家的起点。",
        "groups.vip" to "示例 VIP 组：通过 parents 继承 default，再多给一点配额。",
        "groups.admin" to "示例管理员组：用星号拿到全部权限。",
    )

    val PERM_USERS: Map<String, String> = mapOf(
        "users" to "玩家级权限覆盖。key 是玩家 UUID。\n" +
            "\n" +
            "  name             最近一次看到的玩家名（只为了方便人工查看）\n" +
            "  group            主组\n" +
            "  groups           附加组，可以有多个，权限取并集\n" +
            "  permissions      玩家专属节点，支持 - 否定，**优先级高于任何组**\n" +
            "  tempPermissions  临时权限，到期自动失效（until 是毫秒时间戳，0 = 永久）\n" +
            "\n" +
            "⚠️ 一般不需要手写这个文件 —— 用游戏内 /perm 或网页面板改更安全。",
    )

    // ============================================================ 称号

    val TITLES: Map<String, String> = mapOf(
        "titles" to "称号定义。key 是称号 id，用在 /title set <id> 里。\n" +
            "\n" +
            "每个称号的字段：\n" +
            "  display      显示文本，支持 & 颜色码，例如 \"&6[VIP]\"\n" +
            "  description  说明文字，鼠标悬停在 GUI 图标上时显示\n" +
            "  permission   拥有条件：玩家有这个权限节点才会出现在可选列表；留空 = 人人可用\n" +
            "  weight       排序权重，越大越靠前\n" +
            "  icon         GUI 图标，物品 id，例如 minecraft:gold_ingot\n" +
            "\n" +
            "⚠️「谁佩戴了哪个称号」不在这里 —— 那存在玩家档案里。这个文件只描述\"有哪些称号\"。\n" +
            "⚠️ 拥有关系直接复用权限系统（permission 字段），不另建一份名单 ——\n" +
            "   两份数据最容易对不上。给某个玩家发称号就用 /perm user <玩家> add kokuucore.title.vip。",
        "titles.newbie" to "示例：默认称号。permission 留空 = 所有玩家都能用。",
        "titles.vip" to "示例：需要 kokuucore.title.vip 权限节点才能佩戴。",
        "titles.builder" to "示例：建筑师。",
        "titles.admin" to "示例：管理团队。",
    )
}
