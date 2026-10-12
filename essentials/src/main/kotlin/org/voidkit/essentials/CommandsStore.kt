package org.voidkit.essentials

import org.voidkit.core.util.ConfigFiles

/**
 * 命令简写的配置，落盘到 `voidkit/essentials/commands.json`。
 *
 * （随 voidkit_essentials 一起分发 —— 命令简写本质上也是命令。）
 *
 * 只有一个用途：**关掉个别简写**。
 *
 * 大型整合包里经常有别的 mod 注册了同名命令（比如某个 mod 抢了 `/day`），
 * Brigadier 遇到重复注册会抛异常并打断整个注册流程。与其让你去翻日志找是谁，
 * 不如在这里填一行把它让出去。
 */
class CommandsConfig {

    /** 要停用的简写名（不含斜杠），例如 `["day", "sun"]`。 */
    var disabled: MutableList<String> = mutableListOf()

    fun normalize() {
        if (disabled == null) disabled = mutableListOf()
        // 统一小写 + 去重，避免 ["Day", "day"] 这种写法绕过判断
        disabled = disabled.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct().toMutableList()
    }

    fun isDisabled(name: String): Boolean = name.lowercase() in disabled
}

/** 加载 / 保存 [CommandsConfig]。 */
object CommandsStore {

    private const val FILE = "essentials/commands.json"

    @Volatile
    var config: CommandsConfig = CommandsConfig()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, CommandsComments.COMMANDS) { CommandsConfig() }
        config.normalize()
    }

    fun save() = ConfigFiles.save(FILE, config, CommandsComments.COMMANDS)
}
