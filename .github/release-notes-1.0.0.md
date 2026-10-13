# Voidkit 1.0.0

**支持 NeoForge / Forge / Fabric 的高版本服务器模组套件** —— 内核 + 4 个插件，共 **5 个 jar**。
纯服务端，玩家用原版客户端即可进服。

> 📖 文档站：<https://kokuuwork.github.io/voidkit/>
>
> ⚠️ **本版为正式 1.0**。此前发布的 1.0–1.2 版本存在严重缺陷
> （命令注册时序错误导致命令与补全全部失效、记分牌不显示 / 行无限繁殖、聊天双名字等），
> **均已删除，请勿再使用**。版本号由此重新起算。

---

## 下载

### 一、模组套件 · 按版本线各取一个整版 zip

| 你的服务端 | 下载 |
|---|---|
| Minecraft **1.21.1** · NeoForge 21.1.x | `voidkit-1.0.0+mc1.21.1-neoforge.zip` |
| Minecraft **1.20.1** · Forge 47.x（NeoForge 1.20.1 同样可用，**同一份 jar**） | `voidkit-1.0.0+mc1.20.1-forge.zip` |

每个 zip 内含该版本线的全部 5 个 jar。**也可以单独下载某个模块的 jar**
（下方资产列表里逐条列出）。**最少只需要 `voidkit_core`。**

### 二、管理平台 VoidkitHub（Go 单二进制，与 JVM 无关）

把多台游戏服的 `voidkit_web` API 聚合成一个后台；单服也能当普通面板用。
纯 Go 编译、CGO 关闭，**无任何运行时依赖**。

| 平台 | 文件 |
|---|---|
| Linux x86-64 | `voidkithub-linux-amd64` |
| Linux ARM64 | `voidkithub-linux-arm64` |
| Linux ARMv7 | `voidkithub-linux-arm` |
| Windows x86-64 | `voidkithub-windows-amd64.exe` |
| macOS Intel | `voidkithub-darwin-amd64` |
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

> ⚠️ **从合并前的 8-jar 旧版升级？** 第一次进**升级前就在用的老存档**时，
> 控制台会打印 `voidkit_perm / voidkit_economy / voidkit_title -> MISSING` 三条告警。
> 这是**一次性、无害**的（不影响启动、不丢数据 —— 那些数据在 `voidkit/` 目录里，不在存档里），
> 换个新档或换档后即消失。详见 [安装与部署](https://kokuuwork.github.io/voidkit/guide/install.html)。

---

## 打包规格

- **5 个 jar**：`core` / `essentials`（吸收 perm + economy）/ `display`（吸收 title）/
  `utility` / `web`。
  2026-10-13 起把原来的 8 个 jar 做了「一 jar 一 modId」的**真源码合并** —— 合并只改打包形态，
  **功能、权限节点、数据目录全部不变**（运行时仍读写 `voidkit/perm/`、`voidkit/economy/`、
  `voidkit/title/`，老服数据不会丢）。
- 产物命名：`模组名-模组版本+mc版本-加载器`，
  例：`voidkit_core-1.0.0+mc1.21.1-neoforge.jar`
- 每个版本线一个 zip，内含当版全部 jar。

---

## 本版重点

**修复（相对此前版本）**

- 命令注册时序：命令与 Tab 补全在服务端启动早期即可用
- 记分牌：侧边栏正常显示；修复 `%tps%` 等行无限繁殖、越刷越长
- 聊天栏：修复「双名字」（接管原版 ChatType 装饰）
- 名字悬停 / 点击：修复样式静默丢失，悬停彩色化并提示「点击直接传送」
- **权限**：`/enderchest <玩家>` 的越权（任意玩家可开他人末影箱）已修；
  `/vk group` 与 `/vk perm add|del` 现在**真正写入权限系统**（此前只改档案、不生效）；
  `/speed <值> <玩家>` 的对他人分支补上管理权限判定

**新增**

- 聊天格式独立配置文件（内置教程注释），默认展示群组 / 等级 / 称号
- `%world%` 世界名可配置（`worldNames`），支持 `&` 颜色码，模组维度也能自定义
- 管理平台（`hub/`，Go 单二进制）：多服聚合，含反作弊与审计页面
- **1.20.1 版本线**（分支 `mc/1.20.1`）：同一份 jar 兼容 Forge 与 NeoForge 1.20.1

**工程**

- 契约层 `core.api` 不再依赖任何加载器类（Fabric 移植的前置条件已就位）
- Fabric 移植施工单就绪（`PORTING-FABRIC.md`），尚未实施

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
| **1.21.1** | ✅ 主线 | — | ⏳ 规划中 |
| **1.20.1** | ✅ 同一份 jar | ✅ 同一份 jar | ⏳ 规划中 |

- 已在 165 个 mod 的 SkyExplorer 整合包上实测
- 零 mixin、构造期零 IO，面向大型科技整合包

## 许可证

MIT
