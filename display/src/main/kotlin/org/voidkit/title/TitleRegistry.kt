package org.voidkit.title

import org.voidkit.core.VoidkitCore
import org.voidkit.core.util.ConfigFiles

/** `titles.json` 的顶层结构。包一层是为了有地方放注释，也省掉 Gson 泛型 Type 的麻烦。 */
class TitleFile {
    /** key = 称号 id，会用在 `/title set <id>` 里。 */
    var titles: MutableMap<String, TitleDefinition> = linkedMapOf()

    /** 自定义称号（玩家自己写文本）的配置。 */
    var custom: CustomTitleConfig = CustomTitleConfig()

    fun normalize() {
        if (titles == null) titles = linkedMapOf()
        if (custom == null) custom = CustomTitleConfig()
        custom.normalize()
    }
}

/**
 * 自定义称号配置：玩家用 `/title custom <文本>` 自己写称号时的规则。
 *
 * 两个防滥用点：显示宽度上限（中文 2 格 / ASCII 1 格），以及字符白名单 + 默认禁止颜色码
 * —— 不让玩家把自己刷成管理员同款颜色。
 */
class CustomTitleConfig(
    /** 是否启用自定义称号功能。 */
    var enabled: Boolean = true,
    /** 使用自定义称号需要的权限节点。 */
    var permission: String = "voidkit.title.custom",
    /** 显示宽度上限（中文/全角/emoji 算 2 格，ASCII 算 1 格）。 */
    var maxLength: Int = 8,
    /** 纯文本允许的字符正则（去掉颜色码后再校验）。 */
    var allowedPattern: String = "^[\\p{L}\\p{N}\\p{So} _-]+$",
    /** 是否允许玩家在称号里带颜色码；默认禁止，防伪装管理员颜色。 */
    var allowColors: Boolean = false,
    /** 展示时统一套的颜色码（玩家自己的颜色会被它包裹）。 */
    var colorCode: String = "&f",
) {
    fun normalize() {
        if (permission == null || permission.isBlank()) permission = "voidkit.title.custom"
        if (maxLength <= 0) maxLength = 8
        if (allowedPattern == null || allowedPattern.isBlank()) allowedPattern = "^[\\p{L}\\p{N}\\p{So} _-]+$"
        if (colorCode == null || colorCode.isBlank()) colorCode = "&f"
    }
}

/**
 * 一个称号的定义。
 *
 * 用带默认值的构造参数（而不是“只有 var 字段”）—— 内置示例与面板构造时写得简洁，
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
 * 称号注册表。定义在 `voidkit/title/titles.json`。
 *
 * "谁拥有哪个称号"**不单独存** —— 直接复用权限节点：
 * 称号定义里写 `permission`，玩家有那个节点就能用。这样一套权限系统管到底，
 * 不用再维护第二份“某某拥有哪些称号”的数据（那种数据最容易和权限对不上）。
 *
 * 玩家**当前佩戴**的称号存在自己的档案里（`PlayerProfile.activeTitle`）。
 */
object TitleRegistry {

    const val FILE = "title/titles.json"

    private lateinit var file: TitleFile

    val titles: MutableMap<String, TitleDefinition> get() = file.titles

    /** 自定义称号配置。 */
    val custom: CustomTitleConfig get() = file.custom

    fun load() {
        file = ConfigFiles.load(FILE, TitleFile::class.java, TitleComments.TITLES) {
            TitleFile().apply { titles = builtin() }
        }
        file.normalize()
        file.titles.forEach { (id, title) ->
            title.normalize()
            if (title.display.isBlank()) title.display = "&7[$id]"
        }
        save()
        VoidkitCore.LOG.info("称号数据就绪：{} 个称号", titles.size)
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
            permission = "voidkit.title.vip",
            weight = 10,
            icon = "minecraft:gold_ingot",
        ),
        "builder" to TitleDefinition(
            display = "&b[建筑师]",
            description = "参与过服务器建设",
            permission = "voidkit.title.builder",
            weight = 20,
            icon = "minecraft:bricks",
        ),
        "admin" to TitleDefinition(
            display = "&c[管理]",
            description = "服务器管理团队",
            permission = "voidkit.title.admin",
            weight = 100,
            icon = "minecraft:netherite_ingot",
        ),
    )

    fun save() = ConfigFiles.save(FILE, file, TitleComments.TITLES)

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
