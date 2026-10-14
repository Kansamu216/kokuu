package org.voidkit.core.api

import java.util.concurrent.ConcurrentHashMap

/**
 * 公开节点登记表 —— 「玩家本来就能用」的命令节点。
 *
 * ## 解决什么问题
 *
 * 权限系统的默认值一般是 **false**（谁都没命中就拒绝），这对管理命令是对的，
 * 但对 `/warp` `/spawn` `/home` `/tpa` 这类**玩家日常命令**就反了：
 * 服主装上一套套件，结果普通玩家连回城都用不了，还得先去配权限。
 *
 * 这里让插件在注册命令时**顺手声明**哪些节点是「公开」的：
 *
 * ```kotlin
 * PublicNodes.register("voidkit.command.warp", "voidkit.command.spawn")
 * ```
 *
 * 于是权限判定链变成：
 *
 * ```
 * 玩家专属节点 → 各权限组（按权重） → 公开节点？→ 放行 → 否则 defaultValue
 * ```
 *
 * 语义正好是想要的那个：
 * - **默认对所有玩家开放** —— 装上就能用，零配置；
 * - **可以用 `-节点` 显式关闭** —— 因为「玩家专属节点 / 组」这两步在前，
 *   显式否定一旦命中就提前返回 false，轮不到公开节点兜底。
 *
 * ⚠️ 公开节点**只影响「未配置时」的默认值**，不是「绕过权限」。想让某组失去
 * `/warp`，在该组加一条 `-voidkit.command.warp` 即可。
 *
 * ⚠️ 内核自己不认识任何业务节点 —— 这份名单**完全由各插件登记**，
 * 内核只负责「存起来 + 查询」。卸载插件后它登记的节点自然不再公开。
 */
object PublicNodes {

    private val nodes = ConcurrentHashMap.newKeySet<String>()

    /**
     * 按**来源**登记的公开节点：同一个 owner 再次登记会**替换**上一次的集合。
     *
     * 为什么需要它：节点名可能来自配置文件（例如聊天的 `chat.colorPermission`）。
     * `/vk reload` 之后配置变了，旧节点名必须从公开名单里消失 —— 否则服主把节点
     * 改成别的名字，老名字依然是公开的，等于悄悄留了个口子。
     */
    private val byOwner = ConcurrentHashMap<String, Set<String>>()

    /** 登记一个或多个公开节点（大小写不敏感）。 */
    fun register(vararg node: String) {
        node.forEach { if (it.isNotBlank()) nodes.add(it.lowercase()) }
    }

    /** 按来源登记；同一 owner 重复调用会**替换**该来源此前的节点（配置重载用）。 */
    fun registerFor(owner: String, vararg node: String) {
        byOwner[owner] = node.filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
    }

    /** 该节点是否被登记为公开（大小写不敏感）。 */
    fun isPublic(node: String): Boolean {
        if (node.isBlank()) return false
        val key = node.lowercase()
        return nodes.contains(key) || byOwner.values.any { key in it }
    }

    /** 当前全部公开节点（按字母序，供 `/voidkit status`、面板展示）。 */
    fun all(): Set<String> = (nodes + byOwner.values.flatten()).toSortedSet()
}
