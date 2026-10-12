package org.voidkit.essentials

/** 命令简写配置的中文注释表（写盘时按字段插入 JSON）。 */
object CommandsComments {

    val COMMANDS: Map<String, String> = mapOf(
        "disabled" to "要停用的简写名（不含斜杠），例如 [\"day\", \"sun\"]。\n" +
            "\n" +
            "用途：大型整合包里常有别的 mod 注册了同名命令，Brigadier 遇到重复注册会抛异常。\n" +
            "虽然本项目已经逐条容错（冲突只跳过这一条，不影响其它），\n" +
            "但如果你确认某条简写被别的 mod 抢了、而且想用那个 mod 的版本，在这里写一行即可。\n" +
            "\n" +
            "可用名字：day night noon midnight dawn dusk freeze unfreeze\n" +
            "          sun rain thunder gr diff gm",
    )
}
