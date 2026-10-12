package org.voidkit.perm

import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import org.voidkit.core.api.VoidkitServices
import org.voidkit.core.core.PluginRegistry

/**
 * voidkit_perm —— 权限插件。
 *
 * ## 它是可选插件
 *
 * 内核只定义 [org.voidkit.core.api.PermissionProvider] 接口，并自带一个「只认原版 OP」的
 * 兜底实现。本插件做两件事：把权限数据读进来，然后把自己的实现**装进内核**
 * （[VoidkitServices.installPermission]）。在那之后，所有插件的权限判断都会走到这里。
 *
 * 也就是说：卸载这个 jar，服务器照常启动，只是权限退化成「OP 全通、其他人全挡」。
 * 这正是插件化想要的效果 —— 少一个插件不该让整套崩掉。
 */
@Mod(VoidkitPerm.MODID)
class VoidkitPerm(bus: IEventBus, container: ModContainer) {

    init {
        PluginRegistry.register(PermissionFeature(), MODID)

        // 先装上实现：即使数据还没读，判断也不会 NPE（PermissionManager 内部有默认值）
        VoidkitServices.installPermission(PermissionManager)

        // 配置在服务端启动阶段读 —— 构造期保持零 IO
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            PermissionStore.load()
        }
    }

    companion object {
        const val MODID = "voidkit_perm"
    }
}
