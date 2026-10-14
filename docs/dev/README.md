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

**命令行为**是冒烟覆盖不到的另一半 —— 起一台开了 RCON 的服务端，把**全部根命令**
用「无参数 / 给一个不存在的参数」两种形态跑一遍，再比对日志有没有新异常：

```bash
python tools/command-audit.py 127.0.0.1 25575 <rcon密码> --log <服务端日志>
```

`tools/rcon-exec.py` 是最小 RCON 客户端（Gradle `runServer` 不转发 stdin，
控制台喂不了命令，RCON 是唯一能脚本化驱动命令的通道）。两个已踩过的坑写在它的注释里：
**RCON 响应会被切成多个 4096 字节分包**（只读一个包会把长输出截断，且剩余分包会
错位成下一条命令的响应）；`/help <页码>` 在 1.21.1 不成立，裸 `help` 一次给全表。

**一键验收**（对任意「已装好 jar 的服务端目录」都适用 —— 把上面几件串成一条命令）：

```bash
tools/verify-installed-server.sh <服务端目录> [--jdk <JDK目录>] [--api-port 8766]
```

依次跑：起服 → 加载期致命错误 → 模块注册 23/23 → 管理 API → 全命令树 → MOTD，
最后给一份 ✅/❌ 结论。它会**硬性拦下** mods 里同一模块存在多份 jar 的情况 ——
FML 遇到同名 modId 会自己挑一个（通常是版本最高的）、只打一条 warning，
于是「验收」可能验的是你并不想测的那个 jar；**这种假绿比直接失败危险得多**。

MOTD 那一环用 `tools/server-ping.py`（Server List Ping，1.7+ 协议）直接问服务端要状态：
拿到的是**客户端真正会看到的那段文本**，不是配置文件里的原文 —— 所以它能验出
「配置改了但没推给客户端」这类问题。MOTD 也是**唯一不需要在线玩家**的显示类功能。

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
