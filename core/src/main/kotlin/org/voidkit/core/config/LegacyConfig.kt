package org.voidkit.core.config

import org.voidkit.core.VoidkitCore
import org.voidkit.core.util.ConfigFiles

/**
 * 旧版 `core/config.json` 的原始值留存。
 *
 * ## 为什么需要它
 *
 * 早期所有插件的配置都堆在内核那一份 `config.json` 里（`apiPort`、`tpaTimeoutSeconds`、
 * `clearLagIntervalSeconds` …）。现在按归属下放到各插件自己的文件，但**存量服务器
 * 的旧文件不能白扔** —— 服主改过的端口、令牌、传送超时都得跟过去。
 *
 * 做法：内核加载配置时**先把旧文件原样读一份存下来**，各插件在"自己的配置文件还不存在"
 * 时从这里取值生成初版。之后就以各自的文件为准，不再回看。
 *
 * 时序上这是安全的：[ServerLifecycle] 的监听器在插件之前注册（core 是依赖最底层，
 * 先加载），所以内核一定先跑完再轮到插件。
 */
object LegacyConfig {

    private var raw: Map<String, Any?> = emptyMap()

    /** 由 [VoidkitCore.loadConfig] 在改写文件之前调用。 */
    fun capture() {
        raw = runCatching { ConfigFiles.readRaw(VoidkitCore.CORE_FILE) }.getOrNull() ?: emptyMap()
    }

    /** 旧文件里有没有这一项（用来区分"设成了 false"和"根本没这一项"）。 */
    fun has(key: String): Boolean = raw.containsKey(key)

    fun bool(key: String): Boolean? = raw[key] as? Boolean

    fun int(key: String): Int? = (raw[key] as? Number)?.toInt()

    fun string(key: String): String? = (raw[key] as? String)?.takeIf { it.isNotBlank() }

    /** 供 `/voidkit status` 显示"这次从旧配置里搬了多少项"。 */
    fun capturedSize(): Int = raw.size
}
