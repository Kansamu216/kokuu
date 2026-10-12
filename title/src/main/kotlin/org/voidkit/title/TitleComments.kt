package org.voidkit.title

/**
 * 称号配置的中文注释表。
 *
 * 重点说明一件事：「谁拥有哪个称号」**不在这个文件里** ——
 * 拥有关系直接复用权限节点，避免两份名单对不上。
 */
object TitleComments {

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
            "   两份数据最容易对不上。给某个玩家发称号就用 /perm user <玩家> add voidkit.title.vip。",
        "titles.newbie" to "示例：默认称号。permission 留空 = 所有玩家都能用。",
        "titles.vip" to "示例：需要 voidkit.title.vip 权限节点才能佩戴。",
        "titles.builder" to "示例：建筑师。",
        "titles.admin" to "示例：管理团队。",
    )

}
