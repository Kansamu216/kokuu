package org.voidkit.core.config

/**
 * [ConfigEditor] 的「白名单写入」无法在纯 JVM 单元测试里覆盖，跳过。
 *
 * 原因：
 *  - 入口 [ConfigEditor.apply] 直接读写 [org.voidkit.core.VoidkitCore.config]，
 *    而 [org.voidkit.core.VoidkitCore] 是 `@Mod` 注解的 NeoForge 入口类，加载它需要
 *    net.minecraftforge.fml.* / com.mojang.logging.* 等 Minecraft/NeoForge 运行时；
 *  - 白名单判断本身（未知键忽略、类型校验、`int` 区间钳制）拆在 `bool` / `int` /
 *    `text` / `lines` 几个 private 辅助函数里，入参是 Gson 的 JsonObject，且逻辑与
 *    [VoidkitCore.config] 的实时状态强耦合，抽取成可单测的纯函数需要重构主源码，
 *    超出本次「只补纯逻辑测试」的范围。
 */
class ConfigEditorTest
