package org.voidkit.core

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import org.voidkit.core.api.VoidkitLang
import org.voidkit.core.config.ConfigComments
import org.voidkit.core.config.LegacyConfig
import org.voidkit.core.config.VoidkitConfig
import org.voidkit.core.core.PluginRegistry
import org.voidkit.core.event.ServerLifecycle
import org.voidkit.core.feature.CoreFeature
import org.voidkit.core.feature.PlayerTrackingFeature
import org.voidkit.core.placeholder.PlaceholderRegistry
import org.voidkit.core.util.ConfigFiles
import org.slf4j.Logger
import java.nio.file.Path
import java.security.SecureRandom

/**
 * VoidkitCore —— 服务端套件的**内核**。
 *
 * 它本身不含任何玩法：家、传送点、MOTD、记分牌、清扫、权限、称号、网页面板
 * 全部是独立插件。内核只提供它们共用的地基：
 *
 *  - 插件契约（[org.voidkit.core.api.Feature]）与注册表（[PluginRegistry]）
 *  - 跨插件服务注册表（[org.voidkit.core.api.VoidkitServices]）与权限服务接口
 *  - 数据存储、文本/颜色解析、变量系统、实体与传送工具
 *
 * ## 为什么构造期什么都不读
 *
 * 目标运行环境是**几百个 mod 的大型整合包**。FML 是并**行**加载 mod 的，构造器里
 * 做一次文件 IO，就会把那几毫秒直接加在整个启动关键路径上。所以这里只做纯内存登记，
 * 配置读取统一推迟到 `ServerAboutToStartEvent`（见 [ServerLifecycle]）。
 */
@Mod(VoidkitCore.MODID)
class VoidkitCore(bus: IEventBus, container: ModContainer) {

    init {
        // 纯内存登记：把自己提供的模块交给注册表。零 IO、零异常风险。
        PluginRegistry.register(PlayerTrackingFeature(), MODID)
        PluginRegistry.register(CoreFeature(), MODID)

        // 变量系统的注册表也是纯内存的，可以在这里建好
        PlaceholderRegistry.bootstrap()

        NeoForge.EVENT_BUS.register(ServerLifecycle)

        LOG.info("VoidkitCore {} 已装载，配置将在服务端启动阶段读取", VERSION)
    }

    companion object {
        const val MODID = "voidkit_core"

        /**
         * 版本号从 FML 读，**不在代码里写死** —— 单一事实来源是
         * `build.gradle` 的 `voidkitVersion`（会写进 mods.toml 与 jar 名）。
         * 写死的话迟早出现「jar 叫 1.2.0、程序自报 1.0.0」这种不一致。
         */
        val VERSION: String by lazy {
            ModList.get().getModContainerById(MODID)
                .map { it.modInfo.version.toString() }
                .orElse("dev")
        }

        /**
         * 内核配置的路径，**相对**于 `voidkit/` 根目录。
         *
         * 每个插件各占一个子目录（`core/` `display/` `perm/` `title/` …），
         * 这样一份插件的配置和数据可以整体备份、整体交接。
         */
        const val CORE_FILE = "core/config.json"

        val LOG: Logger = LogUtils.getLogger()

        var config: VoidkitConfig = VoidkitConfig()
            private set

        lateinit var configFile: Path
            private set

        /** 由 [ServerLifecycle] 在服务端启动阶段调用（此时配置根目录已就绪）。 */
        fun loadConfig() {
            try {
                // ⚠️ 必须在改写文件**之前**留底 —— 旧版把各插件的配置都塞在这里，
                //    插件要靠这份留存把自己的初版配置从旧值生成出来（见 LegacyConfig）。
                LegacyConfig.capture()

                config = ConfigFiles.load(CORE_FILE, VoidkitConfig::class.java, ConfigComments.CORE) { VoidkitConfig() }
                // Gson 走 Unsafe 分配对象，Kotlin 的默认参数不生效 —— 加载后必须补一遍
                config.normalize()
                // 语言要在任何消息产生之前定下来
                VoidkitLang.setLocale(config.language)
                configFile = ConfigFiles.resolve(CORE_FILE)
                saveConfig()
                LOG.info("内核配置就绪：{}", configFile)
            } catch (t: Throwable) {
                LOG.error("加载配置失败，改用默认值继续", t)
            }
        }

        fun saveConfig() {
            runCatching { ConfigFiles.save(CORE_FILE, config, ConfigComments.CORE) }
                .onFailure { LOG.error("保存配置失败", it) }
        }

        fun randomToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return buildString(bytes.size * 2) {
                bytes.forEach { b ->
                    append(Character.forDigit((b.toInt() shr 4) and 0xF, 16))
                    append(Character.forDigit(b.toInt() and 0xF, 16))
                }
            }
        }
    }
}
