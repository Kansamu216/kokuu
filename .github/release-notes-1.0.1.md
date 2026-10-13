# Voidkit 1.0.1

**支持 NeoForge / Forge 的高版本服务器模组套件** —— 内核 + 4 个插件，共 **5 个 jar**。
纯服务端，玩家用原版客户端即可进服。

> 📖 文档站：<https://kokuuwork.github.io/voidkit/>
>
> ✅ **从 1.0.0 升级**：直接覆盖 `mods/` 里的 jar 即可，`voidkit/` 数据目录完全兼容、
> 无需任何迁移。至少替换 `voidkit_core` 与 `voidkit_essentials` 两个 jar
> （偷懒就直接用整版 zip 全换）。

---

## 本版内容（相对 1.0.0）

### 修复 · /tpa 与 /tpahere 传送方向写反

- **现象**：`/tpa <玩家>` 对方点接受后，实际执行的是「**对方传送到我**」；
  `/tpahere <玩家>` 则相反，实际是「我传送到对方」。
- **根因**：`TpaFeature.accept()` 里「移动方 / 锚点」的判定写反了。请求端的提示文案
  （"请求传送到你这里" / "请求你传送到他那里"）一直是对的，只有接受端的执行是反的 ——
  所以问题相当隐蔽，不实际接受一次发现不了。
- **影响面**：仅 `voidkit_essentials` 的 `/tpa`、`/tpahere`；
  `/tpaccept` `/tpdeny` `/tpacancel`、超时清理、退出清理均不受影响。
- **升级后请实测一次**：`/tpa` 对方接受后，应该是**发起方**移动到对方身边。

---

## 下载

### 一、模组套件 · 按版本线各取一个整版 zip

| 你的服务端 | 下载 |
|---|---|
| Minecraft **1.21.1** · NeoForge 21.1.x | `voidkit-1.0.1+mc1.21.1-neoforge.zip` |
| Minecraft **1.20.1** · Forge 47.x（NeoForge 1.20.1 同样可用，**同一份 jar**） | `voidkit-1.0.1+mc1.20.1-forge.zip` |

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
- 产物命名：`模组名-模组版本+mc版本-加载器`，
  例：`voidkit_core-1.0.1+mc1.21.1-neoforge.jar`
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

- 已在 165 个 mod 的 SkyExplorer 整合包上实测
- 零 mixin、构造期零 IO，面向大型科技整合包

## 验证

- 三条版本线（1.21.1 NeoForge / 1.20.1 Forge / 1.21.1 Fabric）编译通过
- 两条发布线冒烟自检通过：23 个模块注册、28 个管理 API 全部 200、19 项结构断言
- /tpa 命令行为需要两个在线玩家实测，冒烟覆盖不到，本次为代码级核对

## 许可证

MIT
