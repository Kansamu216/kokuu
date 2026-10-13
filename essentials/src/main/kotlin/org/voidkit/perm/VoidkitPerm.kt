package org.voidkit.perm

import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.core.PluginRegistry

/**
 * voidkit_perm —— 权限实现。
 *
 * ## 合并说明（8 jar -> 5 jar，2026-10-13）
 *
 * 本模块的代码已并入 **voidkit_essentials** 的 jar。为满足「**一个 jar 只声明一个 mod id**」
 * （Fabric 线的 `fabric.mod.json` 一个 jar 只能有一个 mod id，而两条加载器要复用同一套分组），
 * 它**不再是独立的 `@Mod`** —— 初始化改由
 * [org.voidkit.essentials.VoidkitEssentials] 在构造期显式触发。
 *
 * 合并**不影响**下面两件事：
 *  - 数据路径仍是 `voidkit/perm/`（路径是各 Store 里的字面量常量，与 modId 无关）；
 *  - [PluginRegistry] 的 owner 标签仍用 [MODID]，面板 / 日志的归组展示不变。
 *
 * ## 它依然提供「权限实现」
 *
 * 内核只定义 [org.voidkit.core.api.PermissionProvider] 接口，并自带一个「只认原版 OP」的
 * 兜底实现。这里做两件事：把权限数据读进来，然后把自己的实现**装进内核**
 * （[VoidkitServices.installPermission]）。在那之后，所有插件的权限判断都会走到这里。
 *
 * 也就是说：不装 voidkit_essentials 这颗插件，服务器照常启动，只是权限退化成
 * 「OP 全通、其他人全挡」—— 少一个插件不该让整套崩掉。
 */
class VoidkitPerm {

    init {
        PluginRegistry.register(PermissionFeature(), MODID)

        // 先装上实现：即使数据还没读，判断也不会 NPE（PermissionManager 内部有默认值）
        VoidkitServices.installPermission(PermissionManager)

        // 配置在服务端启动阶段读 —— 构造期保持零 IO。
        // ⚠️ 用内核钩子而非加载器事件：回调顺序不由我们控制。见 PluginRegistry.onBoot。
        PluginRegistry.onBoot(MODID) {
            PermissionStore.load()
        }
    }

    companion object {
        /** 保留原 modId 字符串，仅用作 PluginRegistry 的 owner 归组标签（不再是 FML 的 mod id）。 */
        const val MODID = "voidkit_perm"
    }
}
