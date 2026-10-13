# 开发指南

Voidkit 用 **Kotlin 2.1** 编写，构建工具是 Gradle 8 + JDK 21。

## 仓库

| 仓库 | 内容 |
|---|---|
| [`KokuuWork/voidkit`](https://github.com/KokuuWork/voidkit) | 内核 + 插件 jar（共 5 jar）+ Go 管理平台 |
| [`KokuuWork/voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector) | 操作审计 |
| [`KokuuWork/voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss) | 反作弊 |

## 构建

```bash
./build.sh build --continue      # 构建全部模块
```

产物在各模块 `build/libs/` 下。`build.sh` 内置了 JDK 21 与国内镜像，
在没有额外配置的机器上也能直接跑。

## 验证

除了编译，项目提供端到端冒烟测试：

```bash
tools/smoke-test.sh              # 起服务端 + 打 API 断言
```

它覆盖「起服 + 模块注册 + API 断言 + 配置落盘」，能抓到一批
「编译通过、一启动就崩」的问题。

> ⚠️ **只编译通过不算完成**。本项目踩过的坑里好几个是
> 「编译 100% 通过、一启动就崩」（modId 连字符、Kotlin 运行时缺失、
> 把没有事件监听的对象注册进总线……）。**最低要求是把 jar 丢进真实服务端启动一次。**

## 铁律

1. **低耦合**：每个组件只依赖 `core`，`core + 任意一个组件` 都能单独运行。
2. **插件之间零编译依赖**：不允许 `import` 另一个插件的类，跨插件通信只走 `VoidkitServices`。
3. **构造期零 IO**：配置读取推迟到 `ServerAboutToStartEvent`。
4. **零 mixin**：全走事件监听。
5. **不使用连字符作为 modId**：NeoForge 校验 `^[a-z][a-z0-9_]{1,63}$`。
6. **中文注释讲「为什么」**，不是「做了什么」。

## 文档

- [开发一个插件](./adding-a-feature.md) —— 从零写一个插件（骨架、Feature、配置、自检）
- [跨平台与跨版本移植](./porting.md) —— `platform` 适配层怎么工作

## 参考

- [架构](../guide/architecture.md) —— 分层与关键决策
- `core/src/main/kotlin/org/voidkit/core/api/` —— 全部可用契约
- `ARCHITECTURE.md` / `ROADMAP.md`（仓库根）—— 完整设计与任务清单
