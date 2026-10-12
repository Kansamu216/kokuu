package org.kokuu.title

import org.kokuu.core.KokuuCore
import org.kokuu.core.config.ConfigComments
import org.kokuu.core.util.ConfigFiles

/** `titles.json` 的顶层结构。包一层是为了有地方放注释，也省掉 Gson 泛型 Type 的麻烦。 */
class TitleFile {
    /** key = 称号 id，会用在 `/title set <id>` 里。 */
    var titles: MutableMap<String, TitleDefinition> = linkedMapOf()

    fun normalize() {
        if (titles == null) titles = linkedMapOf()
    }
}

/**
 * 一个称号的定义。
 *
 * 用带默认值的构造参数（而不是"只有 var 字段"）—— 内置示例与面板构造时写得简洁，
 * Gson 反序列化时也能正常工作。
 */
class TitleDefinition(
    /** 显示文本，支持 `&` 颜色码。例如 `&6[VIP]`。 */
    var display: String = "",
    /** 说明，鼠标悬停/列表里显示。 */
    var description: String = "",
    /**
     * 拥有条件：需要这个权限节点才会出现在玩家的可选列表里。
     * 留空 = 所有玩家都能用。
     */
    var permission: String = "",
    /** 排序权重，越大越靠前。 */
    var weight: Int = 0,
    /** GUI 里的图标，物品 id，例如 `minecraft:gold_ingot`。 */
    var icon: String = "minecraft:name_tag",
) {
    fun normalize() {
        if (display == null) display = ""
        if (description == null) description = ""
        if (permission == null) permission = ""
        if (icon == null || icon.isBlank()) icon = "minecraft:name_tag"
    }
}

/**
 * 称号注册表。定义在 `kokuu/title/titles.json`。
 *
 * "谁拥有哪个称号"**不单独存** —— 直接复用权限节点：
 * 称号定义里写 `permission`，玩家有那个节点就能用。这样一套权限系统管到底，
 * 不用再维护第二份"某某拥有哪些称号"的数据（那种数据最容易和权限对不上）。
 *
 * 玩家**当前佩戴**的称号存在自己的档案里（`PlayerProfile.activeTitle`）。
 */
object TitleRegistry {

    const val FILE = "title/titles.json"

    private lateinit var file: TitleFile

    val titles: MutableMap<String, TitleDefinition> get() = file.titles

    fun load() {
        file = ConfigFiles.load(FILE, TitleFile::class.java, ConfigComments.TITLES) {
            TitleFile().apply { titles = builtin() }
        }
        file.normalize()
        file.titles.forEach { (id, title) ->
            title.normalize()
            if (title.display.isBlank()) title.display = "&7[$id]"
        }
        save()
        KokuuCore.LOG.info("称号数据就绪：{} 个称号", titles.size)
    }

    /** 内置几个示例，让使用者打开文件就知道怎么写。 */
    private fun builtin(): MutableMap<String, TitleDefinition> = linkedMapOf(
        "newbie" to TitleDefinition(
            display = "&a[萌新]",
            description = "初来乍到",
            permission = "",
            weight = 0,
            icon = "minecraft:wheat_seeds",
        ),
        "vip" to TitleDefinition(
            display = "&6[VIP]",
            description = "尊贵的 VIP 会员",
            permission = "kokuucore.title.vip",
            weight = 10,
            icon = "minecraft:gold_ingot",
        ),
        "builder" to TitleDefinition(
            display = "&b[建筑师]",
            description = "参与过服务器建设",
            permission = "kokuucore.title.builder",
            weight = 20,
            icon = "minecraft:bricks",
        ),
        "admin" to TitleDefinition(
            display = "&c[管理]",
            description = "服务器管理团队",
            permission = "kokuucore.title.admin",
            weight = 100,
            icon = "minecraft:netherite_ingot",
        ),
    )

    fun save() = ConfigFiles.save(FILE, file, ConfigComments.TITLES)

    fun byId(id: String?): TitleDefinition? {
        if (id.isNullOrBlank()) return null
        return titles[id]
    }

    /** 全部称号，按权重从高到低。 */
    fun sorted(): List<Pair<String, TitleDefinition>> =
        titles.entries
            .sortedByDescending { it.value.weight }
            .map { it.key to it.value }
}
