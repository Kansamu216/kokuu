package org.kokuu.perm

/**
 * 一个权限分组的定义（可序列化到 config.json 的 `groups` 字段）。
 *
 * 权限节点支持末尾通配：`kokuucore.home.*` 匹配 `kokuucore.home.set`；`*` 表示全部权限。
 */
class PermissionGroupData(
    var name: String = "default",
    /** 聊天/TAB 前缀，支持 `&` 颜色代码。 */
    var prefix: String = "",
    var suffix: String = "",
    /** 数值越大优先级越高，用于继承冲突与显示排序。 */
    var weight: Int = 0,
    var permissions: MutableList<String> = mutableListOf(),
    /** 父组，权限与前缀会被继承。 */
    var parents: MutableList<String> = mutableListOf()
) {
    fun normalize() {
        if (permissions == null) permissions = mutableListOf()
        if (parents == null) parents = mutableListOf()
    }
}
