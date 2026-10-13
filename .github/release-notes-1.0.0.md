# Voidkit 1.0.0

**NeoForge 1.21.1 纯服务端模组套件** —— 内核 + 7 个可独立安装的插件。
玩家用原版客户端即可进服。

> 📖 文档站：<https://kokuuwork.github.io/voidkit/>
>
> ⚠️ **本版为正式 1.0**。此前发布的 1.0–1.2 版本存在严重缺陷
> （命令注册时序错误导致命令与补全全部失效、记分牌不显示 / 行无限繁殖、聊天双名字等），
> **均已删除，请勿再使用**。版本号由此重新起算。

## 下载

推荐直接下 **整版 zip**，解压后按需取用：

```
voidkit-1.0.0+mc1.21.1-neoforge.zip
```

也可以单独下载某个模块的 jar。**最少只需要 `voidkit_core`。**

## 安装

把 jar 放进服务端 `mods/`：

| 模块 | 说明 | 必需 |
|---|---|---|
| `voidkit_core` | 内核（插件契约 / 数据 / 文本 / 变量 / 多语言） | ✅ |
| `voidkit_essentials` | 家 / 传送点 / TPA / 出生点 / 管理工具 / 命令简写 | |
| `voidkit_display` | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 | |
| `voidkit_perm` | 权限（LuckPerms 风格组树） | |
| `voidkit_title` | 称号 + 游戏内 GUI | |
| `voidkit_economy` | 经济 / 商店 / 物品回收 | |
| `voidkit_utility` | 清扫 + 垃圾桶 + 性能监测 | |
| `voidkit_web` | 管理 API 桥（唯一监听端口的） | |

- 需要 **JDK 21**。
- **少装任何一个都不会崩** —— 内核给每个服务都准备了保守的兜底实现
  （例如不装 `voidkit_perm`，权限判定退化为「只认原版 OP」）。
- 所有功能的展示名可在 `voidkit/core/config.json` 的 `features` 段逐个开关。

首次启动会在**服务端根目录**生成 `voidkit/` 配置目录，**所有配置文件都带中文注释**。

## 本版重点

**修复（相对此前版本）**
- 命令注册时序：命令与 Tab 补全在服务端启动早期即可用
- 记分牌：侧边栏正常显示；修复 `%tps%` 等行无限繁殖、越刷越长
- 聊天栏：修复「双名字」（接管原版 ChatType 装饰）
- 名字悬停 / 点击：修复样式静默丢失，悬停彩色化并提示「点击直接传送」

**新增**
- 聊天格式独立配置文件（内置教程注释），默认展示群组 / 等级 / 称号
- `%world%` 世界名可配置（`worldNames`），支持 `&` 颜色码，模组维度也能自定义
- 管理平台（`hub/`，Go 单二进制）：多服聚合，含反作弊与审计页面

**打包规格**
- 产物命名：`模组名-模组版本+mc版本-加载器`
  例：`voidkit_core-1.0.0+mc1.21.1-neoforge.jar`
- 每个版本线一个 zip，内含当版全部 jar

## 配套组件（独立仓库）

| 组件 | 仓库 |
|---|---|
| 反作弊 VoidkitGuard（客户端 Agent + 服务端校验） | [`KokuuWork/voidkit-guard-oss`](https://github.com/KokuuWork/voidkit-guard-oss) |
| 操作审计 VoidkitInspector（COI 风格，科技包友好） | [`KokuuWork/voidkit-inspector`](https://github.com/KokuuWork/voidkit-inspector) |

两者都依赖 `voidkit_core` 作为前置。

## 环境

- Minecraft **1.21.1** / NeoForge **21.1.x**
- 已在 165 个 mod 的 SkyExplorer 整合包上实测
- 零 mixin、构造期零 IO，面向大型科技整合包

## 许可证

MIT
