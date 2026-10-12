package org.voidkit.perm

/**
 * 权限配置的中文注释表（groups / users / settings 三份文件）。
 *
 * 注释随文件一起写盘，所以服主打开 `voidkit/perm/groups.json` 就能看懂
 * 每个字段是干什么的，不用翻文档。
 */
object PermComments {

    val PERM_SETTINGS: Map<String, String> = mapOf(
        "defaultGroup" to "没有显式分组的玩家归到这个组。",
        "opBypass" to "原版 OP（等级 ≥ 2）是否直接放行所有权限检查。\n" +
            "关掉后 OP 也得靠权限节点说话，适合把管理权完全交给权限系统。",
        "defaultValue" to "谁都没命中时的默认结果。\n" +
            "⚠️ 一般保持 false。设成 true 等于人人都有权限（除非显式用 -节点 否定）。",
        "wildcardEnabled" to "是否启用通配：星号命中一切，voidkit.home.星号 命中 voidkit.home.set 这类前缀匹配。",
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
            "  voidkit.command.fly     精确匹配\n" +
            "  voidkit.home.星号        前缀通配\n" +
            "  星号                       一切权限\n" +
            "  -voidkit.command.fly    否定：明确禁止，优先级高于同组里的肯定",
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

}
