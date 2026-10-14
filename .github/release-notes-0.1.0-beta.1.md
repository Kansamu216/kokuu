# Voidkit beta0.1

**支持 NeoForge / Forge 的高版本服务器模组套件** —— 内核 + 4 个插件，共 **5 个 jar**。
纯服务端，玩家用原版客户端即可进服。

> 📖 文档站：<https://kokuuwork.github.io/voidkit/>
>
> 🚧 **测试版（beta0.1）**：本套件处于早期测试阶段，多数功能未经充分实测，
> **不建议用于生产环境**。请在小众测试服试用并反馈问题，修复随版本持续推进。
>
> 📦 版本号 `0.1.0-beta.1`（即 beta0.1 的语义化写法 —— NeoForge / Forge 的
> mods.toml 要求版本号符合语义化规范，`beta0.1` 字面量会被加载器拒绝）。

---

## 本版包含的修复

### 玩家互传 · /tpa 与 /tpahere 传送方向写反

- **现象**：`/tpa <玩家>` 对方点接受后，实际执行的是「**对方传送到我**」；
  `/tpahere <玩家>` 则相反，实际是「我传送到对方」。
- **根因**：`TpaFeature.accept()` 里「移动方 / 锚点」的判定写反了。请求端的提示文案
  （"请求传送到你这里" / "请求你传送到他那里"）一直是对的，只有接受端的执行是反的。
- **影响面**：仅 `voidkit_essentials` 的 `/tpa`、`/tpahere`。

### /back 往返切换失效

- **现象**：第一次 `/back` 能正常回到死亡点 / 上一次传送前的位置；但紧接着再输一次
  `/back`，人已经在目标点 → 空操作，且 back 点被写成目的地本身，**玩家回不去出发地**。
- **根因**：`Teleports.back()` 原先「先传送、后记录当前位置」，而 `teleportTo` 之后
  玩家已经站在目标点，`StoredLocation.of(player)` 抓到的是目的地而非出发地。
- **影响面**：仅 `voidkit_core` 的 `/back`（`/return` 是同一功能的别名）。

---

## 下载

### 一、模组套件 · 按版本线各取一个整版 zip

| 你的服务端 | 下载 |
|---|---|
| Minecraft **1.21.1** · NeoForge 21.1.x | `voidkit-0.1.0-beta.1+mc1.21.1-neoforge.zip` |
| Minecraft **1.20.1** · NeoForge 1.20.1（47.1.x） | `voidkit-0.1.0-beta.1+mc1.20.1-neoforge.zip` |
| Minecraft **1.20.1** · Forge 47.x | `voidkit-0.1.0-beta.1+mc1.20.1-forge.zip` |

每个 zip 内含该版本线的全部 5 个 jar。**也可以单独下载某个模块的 jar**
（下方资产列表里逐条列出）。**最少只需要 `voidkit_core`。**

> ℹ️ **1.20.1 的 `-forge` 与 `-neoforge` 两份包内容完全相同**（逐字节一致，仅文件名后缀
> 不同）—— NeoForge 1.20.1 就是 Forge 47.1.x 的分支、且以 modId `forge` 注册，同一个 jar
> 在两种加载器上都能跑（**已真机实测**：Forge 47.4.20 与 NeoForge 47.1.106 均加载成功）。
> 按你服务端用的加载器取一份即可，**切勿同时装两个**（modId 相同，会被判为重复 mod）。

### 二、管理平台 VoidkitHub（Go 单二进制，与 JVM 无关）

把多台游戏服的 `voidkit_web` API 聚合成一个后台；单服也能当普通面板用。
纯 Go 编译、CGO 关闭，**无任何运行时依赖**。

| 平台 | 文件 |
|---|---|
| Windows x86-64 | `voidkithub-windows-amd64.exe` |
| Linux x86-64 | `voidkithub-linux-amd64` |
| macOS Apple Silicon | `voidkithub-darwin-arm64` |

```bash
chmod +x voidkithub-linux-amd64
./voidkithub-linux-amd64 -listen 0.0.0.0:9000 -data /var/lib/voidkithub
```

用法见 [管理平台文档](https://kokuuwork.github.io/voidkit/panel/)。

---

## 安装（模组）

把 jar 放进服务端 `mods/`：

| 模块 | 说明 | 必需 |
|---|---|---|
| `voidkit_core` | 内核（插件契约 / 数据 / 文本 / 变量 / 多语言） | ✅ |
| `voidkit_essentials` | 家 / 传送点 / TPA / 出生点 / 管理工具 / 命令简写 **+ 权限 + 经济** | |
| `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 **+ 称号** | |
| `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测 | |
| `voidkit_web` | 管理 API 桥（唯一会监听端口的） | |

- 1.21.1 线需要 **JDK 21**；1.20.1 线需要 **JDK 17**。
- **少装任何一个都不会崩** —— 内核给每个服务都准备了保守的兜底实现
  （例如不装权限模块，权限判定退化为「只认原版 OP」）。
- 所有功能都能在 `voidkit/core/config.json` 的 `features` 段逐个开关
  （jar 粒度 ≠ 功能粒度）。

首次启动会在**服务端根目录**生成 `voidkit/` 配置目录，**所有配置文件都带中文注释**。

---

## 打包规格

- **5 个 jar**：`core` / `essentials`（吸收 perm + economy）/ `display`（吸收 title）/
  `utility` / `web`。
- 产物命名：`模组名-模组版本+mc版本-加载器`，
  例：`voidkit_core-0.1.0-beta.1+mc1.21.1-neoforge.jar`
- 每个版本线一个 zip，内含当版全部 jar。

---

## 配套组件（独立仓库）

| 组件 | 仓库 |
|---|---|
| 反作弊 VoidkitGuard（客户端 Agent + 服务端校验） | [`KokuuWork/voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss) |
| 操作审计 VoidkitInspector（COI 风格，科技包友好） | [`KokuuWork/voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector) |

两者都依赖 `voidkit_core` 作为前置。

---

## 环境

| Minecraft | NeoForge | Forge | Fabric |
|---|---|---|---|
| **1.21.1** | ✅ 主线 | — | ⏳ 移植中 |
| **1.20.1** | ✅ 同一份 jar | ✅ 同一份 jar | ⏳ 移植中 |

- 已在 165 个 mod 的 SkyExplorer 整合包上实测加载兼容
- 零 mixin、构造期零 IO，面向大型科技整合包

## 验证

- 三条版本线（1.21.1 NeoForge / 1.20.1 Forge / 1.21.1 Fabric）编译通过
- 两条发布线冒烟自检通过：23 个模块注册、28 个管理 API 全部 200、19 项结构断言
- **1.20.1 发布包已在真机验证**：同一份 jar 分别在 **Forge 47.4.20** 与
  **NeoForge 1.20.1（47.1.106）** 服务端上加载成功、23 模块注册、管理 API 全部 200
- `/tpa` `/back` 命令行为需要在线玩家实测，冒烟覆盖不到，本次为代码级核对
- **本版为测试版**：除冒烟覆盖的加载 / API / 配置链路外，多数命令行为未经系统实测

## 许可证

MIT
