# Voidkit · mcmod 发布资料

> 版本：`0.1.0-beta.4`（beta0.4） · 发布时间：2026-10-14
>
> 本文件按 mcmod（minecraft.net/cn-minecraft.com，MCMOD 的发布表单）字段整理，  
> 字段名标注了「⌈mcmod 字段⌋」，可直接对应后台表单逐项填。

---

## 一、项目主页（项目级 · 只填一次）

### 1. 基本信息

| 字段         | 内容                                                               |
| ---------- | ---------------------------------------------------------------- |
| ⌈项目名⌋      | **Voidkit**                                                      |
| ⌈项目英文名⌋    | Voidkit                                                          |
| ⌈所属分类⌋     | 服务端工具 / 服务端插件（mcmod 字段叫「分类」）                                     |
| ⌈标签⌋       | 服务器辅助 · 传送 · 经济 · 权限 · 管理 · 反作弊 · 审计 · 整合包                       |
| ⌈许可证⌋      | MIT                                                              |
| ⌈作者⌋       | Kokuu Studio                                                     |
| ⌈主页链接⌋     | <https://github.com/KokuuWork/voidkit>                           |
| ⌈文档链接⌋     | <https://kokuuwork.github.io/voidkit/>                           |
| ⌈Issue 追踪⌋ | <https://github.com/KokuuWork/voidkit/issues>                    |
| ⌈QQ 维修群⌋ | **498700382** · <https://qm.qq.com/q/498700382>           |
| ⌈关键词⌋      | voidkit, neoforge, forge, fabric, 1.21.1, 1.20.1, 服务端, 整合包, 管理面板 |

### 2. 简短描述（≈ 一句话，mcmod 限制通常 80–150 字）

> Voidkit（**无界套件**）是一套面向 NeoForge / Forge / Fabric 的高版本服务端模组套件：  
> 5 个 jar、一套代码同时支撑 1.20.1 与 1.21.1；补齐「家 / 传送 / 记分牌 / 聊天 / 经济 / 权限 / 称号 / 清扫」等通用服功能，  
> 配套独立的反作弊与操作审计组件、加上一个轻量管理后台。玩家用原版客户端即可进服。

### 3. 详细介绍（支持 BBCode / Markdown，按 mcmod 实际编辑器粘贴）

```markdown
# Voidkit · 让高版本模组服也能用上「Essentials / CMI 那一套」

面向 NeoForge / Forge / Fabric 的**高版本服务端模组套件**，玩家用原版客户端即可进服。
已在 165 个 mod 的 SkyExplorer 整合包上实测 —— **零 mixin、构造期零 IO、不与已有 mod 抢类改写**。

## 一套代码 = 三加载器 = 两版本

| Minecraft | NeoForge | Forge | Fabric |
|---|---|---|---|
| 1.21.1 | ✅ 主线 | ✅（Forge 52.x） | ✅（需 Fabric API） |
| 1.20.1 | ✅ **与 Forge 共用一份 jar** | ✅ | 规划中 |

> **1.20.1 的 Forge 与 NeoForge 是同一份 jar**（NeoForge 1.20.1 就是 Forge 47.1.x 的分支），
> 按你服务端用的加载器取一份即可，**切勿同时装两个**。

## 5 个 jar，按需拿

只装 `voidkit_core` 也能跑（剩余退化成保守兜底，不崩）：

- `voidkit_core` — **内核**（插件契约、服务注册、数据、文本/变量/多语言；带 Kotlin 运行时）
- `voidkit_essentials` — 家 · 公共传送点 · TPA · 出生点 · 管理工具 · 命令简写 · 权限 · 经济
- `voidkit_display` — MOTD · TAB · 记分牌 · 聊天栏 · 公告 · 菜单 · 称号
- `voidkit_utility` — 清扫 · 垃圾桶 · 性能监测
- `voidkit_web` — 管理 API 桥（唯一监听端口的）

每个功能都是独立 `Feature`，可在配置 `features` 段或管理面板「模块」页逐个开关。
**jar 粒度 ≠ 功能粒度**。

## 主要特性

- **零配置开箱即用** —— 装上就能用，常用命令、公告、菜单、记分牌都是智能默认值
- **玩家向权限默认开放** —— `/home` `/tpa` `/warp` `/back` `/menu` `/title` 等无需任何权限节点
- **聊天模板不受玩家权限门控** —— 默认模板一定会被解析，不会出现「聊天全是变量」
- **MOTD / TAB / 记分牌 / 聊天 `&` 颜色 / `%变量%` 全部按服主模板渲染**
- **/vk reload 真的重读配置** —— 显示「重载了」，配置真的重读，且不会冲掉内存里没落盘的数据
- **周期任务默认保守** —— 清扫、监视、记分牌刷新全是可配可关

## 常用命令速览

| 命令 | 说明 |
|---|---|
| `/vk` | 内核：`reload` / `save` / `token` / `status` / `features` / `api` |
| `/sethome` `/home` `/tpa` `/tpaccept` | 家 · 玩家互传 |
| `/warp` `/warps` `/setwarp` | 公共传送点 |
| `/fly` `/god` `/heal` `/feed` `/vanish` | 管理工具 |
| `/clearlag` `/trash` `/perf` | 清扫 · 垃圾桶 · 性能 |
| `/announce` `/menu` | 公告 · 菜单 |
| `/perm` `/title` | 权限 · 称号 |
| `/balance` `/pay` `/baltop` `/sell` `/shop` | 经济 |

完整命令与权限见文档站：

> 📖 https://kokuuwork.github.io/voidkit/

## 为什么适合大型整合包

- **零 mixin** —— 不与整合包里已有的 mixin mod 抢类
- **构造期零 IO** —— 配置读取推迟到服务端启动阶段
- **不做侵入性写入** —— MOTD 默认**不会**改 `server.properties`
- **命令简写逐条容错** —— 名字被占用只跳过这一条
- **周期任务可关** —— 默认不打扰

## 配套组件（独立发布）

| 组件 | 说明 |
|---|---|
| **VoidkitGuard** | 反作弊：客户端 Agent + 服务端校验（哈希锁、随机复检、蜜罐、静默取证） |
| **VoidkitInspector** | 操作审计：CoreProtect 风格、智能回滚五层，科技整合包友好 |

## 当前状态

- 四条版本线均已**真机起服验证**（1.21.1 NeoForge / 1.21.1 Forge 52.x / 1.20.1 Forge / 1.20.1 NeoForge / 1.21.1 Fabric）
- 全部 23 个模块注册、管理 API 28 个端点全 200、全命令树无异常
- 仍在测试，详见版本发布说明

## 许可证

MIT
```

### 4. 项目图标 / 主图

| 用途                | 文件 / 链接                                                                    |
| ----------------- | -------------------------------------------------------------------------- |
| 项目图标（128×128 方形）  | <https://github.com/KokuuWork/voidkit/blob/main/.github/assets/icon.png>   |
| 主图（横幅 / 1024×256） | <https://github.com/KokuuWork/voidkit/blob/main/.github/assets/banner.png> |
| 仓库 README 直接预览    | <https://github.com/KokuuWork/voidkit#readme>                              |

> mcmod 上传图标时直接拖 PNG/JPG，单图 ≤ 2 MB（具体以 mcmod 当下规格为准）。

---

## 二、版本发布页（每发布一版填一次）

### 1. 版本基本信息

| 字段                | 内容                                  |
| ----------------- | ----------------------------------- |
| ⌈版本号⌋             | 0.1.0-beta.4                        |
| ⌈用户可视版本名⌋         | beta0.4                             |
| ⌈所属 Minecraft 版本⌋ | 1.20.1, 1.21.1                      |
| ⌈所属加载器⌋           | NeoForge, Forge, Fabric             |
| ⌈对应 JDK⌋          | 1.21.1 线 = JDK 21；1.20.1 线 = JDK 17 |
| ⌈该版本类型⌋           | 测试版 / beta                          |
| ⌈该版本状态⌋           | 公开（不勾选「仅自己可见」）                      |

### 2. 下载文件（mcmod 一次只能挂单一 zip 链接 —— **每个版本需要分行发布** 或 **在描述里贴出全部 5 个 zip**）

#### A. 5 个整版 zip（按版本线各取一个 —— 这是 mcmod 主分发物）

| 你的服务端                          | 直链                                                                                                               |
| ------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| 1.21.1 · NeoForge 21.1.x       | <https://github.com/KokuuWork/voidkit/releases/download/0.1.0-beta.4/voidkit-0.1.0-beta.4+mc1.21.1-neoforge.zip> |
| 1.21.1 · Forge 52.x            | <https://github.com/KokuuWork/voidkit/releases/download/0.1.0-beta.4/voidkit-0.1.0-beta.4+mc1.21.1-forge.zip>    |
| 1.21.1 · Fabric                | <https://github.com/KokuuWork/voidkit/releases/download/0.1.0-beta.4/voidkit-0.1.0-beta.4+mc1.21.1-fabric.zip>   |
| 1.20.1 · NeoForge / Forge 47.x | <https://github.com/KokuuWork/voidkit/releases/download/0.1.0-beta.4/voidkit-0.1.0-beta.4+mc1.20.1-neoforge.zip> |

> ⚠️ **1.20.1 · Forge 与 NeoForge 是同一份 jar**（NeoForge 1.20.1 就是 Forge 47.1.x 的分支），  
> 上面这两个 zip 内容完全一致、逐字节相同 —— 按你的加载器取一份即可。
>
> ⚠️ mcmod 不支持附多个 zip 的话，**改在版本描述里列全部下载链接**（见下「发布版描述」一节）。

#### B. 单 jar（可选列出，给二次开发者）

核心 5 个 jar × 5 版本线 = 25 个文件，全部直链见 GitHub release 页面：

> <https://github.com/KokuuWork/voidkit/releases/tag/0.1.0-beta.4>

### 3. 依赖关系表

| 关系                | modId                          | 必需   | 版本范围          |
| ----------------- | ------------------------------ | ---- | ------------- |
| 仅 1.20.1 NeoForge | forge                          | ✅ 必需 | [47.0.0,)     |
| 仅 1.20.1 Forge    | forge                          | ✅ 必需 | [47.0.0,)     |
| 1.21.1 NeoForge   | minecraft                      | ✅ 必需 | 1.21.1        |
| 1.21.1 NeoForge   | neoforge                       | ✅ 必需 | 21.1.x        |
| 1.21.1 Forge      | forge                          | ✅ 必需 | 52.x          |
| 1.21.1 Fabric     | fabricloader                   | ✅ 必需 | ≥ 0.16.0      |
| 1.21.1 Fabric     | fabric-api（外置 module）          | ✅ 必需 | 任意 1.21.1 兼容版 |
| 全部                | `voidkit_core` 是其他 4 个的前置（按需取） | 模块依赖 |               |

### 4. 发布版描述（Beta0.4 测试版完整 changelog，可粘贴到 mcmod 描述框）

```markdown
# Voidkit beta0.4（测试版）

支持 NeoForge / Forge / Fabric 的高版本服务端模组套件 —— 内核 + 4 个插件，共 5 个 jar。
纯服务端，玩家用原版客户端即可进服。

> 📖 文档站：https://kokuuwork.github.io/voidkit/
>
> 🛠 **Bug 维修群**：[`498700382`](https://qm.qq.com/q/498700382) ——
> 装不上、启动崩、模块不生效、配置改了不生效、和别的 mod 冲突，都来群里说。
> 报 bug 请附上日志里 `Voidkit 模块 x / x 已启用` 那一行、MC 与加载器版本、相关报错片段。
>
> 🚧 测试版（beta0.4）：本套件处于早期测试阶段，多数功能未经充分实测，
> **不建议用于生产环境**。请在小众测试服试用并反馈，修复随版本持续推进。
>
> 版本号 `0.1.0-beta.4`（即 beta0.4 的语义化写法 —— NeoForge / Forge 的
> mods.toml 要求语义化版本号，`beta0.4` 字面量会被加载器拒绝）。
>
> 本 Release 是当前唯一的发布版本；早期测试版（0.1.0-beta.1 等）与 tag 已下架，
> 请始终使用最新一版。

---

## 下载（5 个整版 zip + 3 个管理平台二进制 + 25 个单 jar）

### 模组套件（按版本线各取一份）

| 你的服务端 | 下载 |
|---|---|
| 1.21.1 · NeoForge 21.1.x | voidkit-0.1.0-beta.4+mc1.21.1-neoforge.zip |
| 1.21.1 · Forge 52.x | voidkit-0.1.0-beta.4+mc1.21.1-forge.zip |
| 1.21.1 · Fabric | voidkit-0.1.0-beta.4+mc1.21.1-fabric.zip |
| 1.20.1 · NeoForge（47.1.x） | voidkit-0.1.0-beta.4+mc1.20.1-neoforge.zip |
| 1.20.1 · Forge 47.x | voidkit-0.1.0-beta.4+mc1.20.1-forge.zip |

> 全部资产 + sha256 见
> https://github.com/KokuuWork/voidkit/releases/tag/0.1.0-beta.4
>
> ℹ️ 1.20.1 的 `-forge` 与 `-neoforge` 两份包**内容完全相同**（逐字节一致，文件名后缀不同）——
> 按加载器取一份即可，**切勿同时装两个**（modId 相同，会被判为重复 mod）。
>
> 🧵 1.21.1 · Fabric 还需要 Fabric API 一并放进 mods/。

---

## 本版修复（新内容）

### 聊天栏「全是变量」+ 普通玩家默认用不了彩色字

- **现象**：普通玩家（非 OP）在聊天栏看到的格式模板里，`%group%` `%level%` `%title%` 原样显示成变量名；
  玩家自己默认也没有 `&` 颜色与变量权限。
- **根因**（两条叠加）：① 聊天格式化把**服务器模板**的变量解析用玩家权限节点门控了，但模板是**服主配置**、
  不是玩家输入，本来就不该受玩家权限影响。② 两个聊天节点不在公开节点表里，默认谁都没有。
- **修复**：模板**无条件解析**（玩家的权限只作用于**自己发言里**的颜色与变量）；两个聊天节点改为**默认开放**。
- **影响面**：仅 voidkit_display 的聊天格式。

### Fabric 线启动即失败：跨模块依赖硬编码了旧版本号

- **现象**：Fabric 服务端一启动就抛 `FormattedException: Some of your mods are incompatible`，
  4 个插件全部报「需要 voidkit_core 的 1.0.0 及以上版本，但已安装 0.1.0-beta.1 不对」。
- **根因**：`fabric.mod.json` 里对 core 的依赖**硬编码** `">=1.0.0"`。版本号从 1.0.x 改为 0.1.0-beta.N 后失效。
- **修复**：改为构建期展开的 `">=${version}"`，与 `version` 字段同一套机制。
- 这类问题**编译和冒烟都测不到，只有真机起服才会暴露**。

### 垃圾桶「清空」按钮对无权限玩家仍显示

- **修复**：按钮改为按权限隐藏（打开菜单时判定一次）；动作侧仍保留二次校验，防客户端伪造。

### /vk reload 提示成功，但插件配置其实没生效

- **根因**：reload 只重读了内核配置，随后挨个调用各模块的「重载」回调，但回调没重读磁盘。
- **修复**：新增「配置重读」钩子（与启动钩子分开登记），reload 时先让各插件**重新读盘**，
  再执行「重新应用」—— 区分「读配置」与「加载数据 + 状态发布」，避免把内存里没落盘的改动冲掉。
- **顺带修**：聊天那两个「公开权限节点」改为**按来源登记** —— 改完节点名再 reload，旧名字会从公开名单里消失。
- **顺带修**：`/perm reload` 与重载路径改为**先落盘、再重读**（不会冲掉刚改的权限）。
- **顺带修**：菜单（钟表这类 opener 物品）在玩家无权限 / opener 指向不存在的菜单时会**说清原因 + 写日志**。
- **影响面**：core + 全部 6 个模块入口。

### /broadcast 在控制台执行后没有任何回执

- **修复**：现在会回「已广播给 N 名玩家」/「已广播，但当前没有玩家在线」。
- 全命令树审计自己点出来：348 条用例里唯一「零响应」的自有命令就是它。

### 玩家互传 · /tpa 与 /tpahere 传送方向写反（beta0.1 起已修）

### /back 往返切换失效（beta0.1 起已修）

- **根因**：`back()` 原先「先传送、后记录当前位置」，抓到的自然是目的地而非出发地。**状态必须在变动前抓取**。

---

## 安装（5 个 jar）

把 jar 放进服务端 mods/：

| jar | 说明 | 必需 |
|---|---|---|
| voidkit_core | 内核 | ✅ 必需 |
| voidkit_essentials | 家 / 传送点 / TPA / 管理工具 / 权限 / 经济 | 可选 |
| voidkit_display | MOTD / TAB / 记分牌 / 聊天栏 / 公告 / 菜单 / 称号 | 可选 |
| voidkit_utility | 清扫 / 垃圾桶 / 性能监测 | 可选 |
| voidkit_web | 管理 API 桥（唯一监听端口的） | 可选 |

- 1.21.1 线需 **JDK 21**；1.20.1 线需 **JDK 17**。
- **少装任何一个都不会崩** —— 内核给每个服务都准备了保守的兜底实现。
- 全部功能在 `voidkit/core/config.json` 的 `features` 段逐个可关（`/vk features` 查看）。

首次启动会在服务端根目录生成 `voidkit/` 配置目录，**所有配置文件带中文注释**。
玩家向功能**默认开放、开箱即用**；管理类命令认 `voidkit.admin` 或对应专属节点，
**OP（等级 ≥ 2）自动绕过**。

---

## 验证状态

四条版本线均已**真机起服验证**（各 23/23 模块注册 + 管理 API 全 200 + 无致命错误）：

- 1.21.1 · NeoForge 21.1.x
- 1.21.1 · Forge 52.1.19
- 1.20.1 · Forge 47.4.20；同一份 `-neoforge` 包在 NeoForge 1.20.1（47.1.106）上同样通过
- 1.21.1 · Fabric Loader 0.19.5 + Fabric API 0.116.17

- 开发服冒烟自检通过（28 个管理 API 全 200 + 19 项结构断言）
- /vk reload 机制已真机验证（运行中改 motd.json / scoreboard.json，reload 后日志确认重新读出了新配置）
- 全命令树实跑：348 条用例无异常堆栈、无崩溃
- 一键验收脚本四条线全跑通
- **客户端行为实测（本版新增）**：真实客户端连上 1.21.1 Fabric 服务端，逐项确认
  **客户端实际看到的**画面，而不只看服务端日志：
  - **聊天格式正常** —— 日志出现 `[default][Lv0] VoidkitTest » voidkit`，说明组前缀、
    权重等级与变量解析均正确（解析坏掉时会看到 `%player%` 原样输出或满屏变量，
    正是本版首条修复要解决的问题）
  - **记分牌已推送** —— 日志确认 `已向 VoidkitTest 推送记分牌「default」：6 行`，
    客户端画面同步显示「玩家 / 在线 / 延迟 / TPS」四列与 VOIDKIT 标题

**仍未做**：传送 / 家 / 经济 / 权限的**状态流转**（余额变动、传送回退、权限组切换后的
命令可见性变化）需要多人在线或分步操作，本版仅确认了单点状态正确。
本版为测试版，除加载、API 链路与上述客户端画面外，多数命令行为未经系统实测。

## 许可证

MIT
```

---

## 三、提交前的自检清单（粘贴完逐项确认）

- [ ] 项目名：Voidkit（与 mods.toml 的 `displayName="VoidkitCore"` 取个别的吗？—— 不用，本项目整体名就叫 Voidkit）
- [ ] 简短描述（≤ 150 字）已粘贴
- [ ] 详细介绍（Markdown）已粘贴
- [ ] 项目图标已上传（建议用 `voidkit_core` jar 里的 `icon.png`）
- [ ] 主图 / 横幅已上传（README 里的 `banner.png`）
- [ ] 标签已勾：服务器辅助、传送、经济、权限、管理、反作弊、审计
- [ ] 分类已选：服务端工具 / 插件
- [ ] 依赖关系已声明（mcmod 通常用 mod 自己的 metas 推断，看一眼自动识别结果是否对）
- [ ] 5 个 zip（4 个版本线 + 1.20.1 双包）已挂 / 描述里列出
- [ ] 版本号写 `0.1.0-beta.4`（不要写成 beta0.4 —— NeoForge / Forge 字面量会让加载器拒绝）
- [ ] 标注「测试版」横幅置顶
- [ ] 文档站链接已贴
- [ ] Issue 链接已贴
- [ ] 许可证：MIT

> 注：mcmod 后台对「多个 zip 附件」的支持不一致 —— 如果一次只允许 1 个主附件，  
> 则按版本线分别建多条发布版（一条对一个版本线一个 zip），主描述保持一致。

---

## 四、长期维护建议

1. **新版本发布**：每次只需要重新生成此文件中的「下载链接表」与「本版修复」两段；  
   其它段落（项目介绍、依赖、安装步骤）跨版本稳定。
2. **mcmod 的版本说明保留** GitHub release 描述同步习惯（顶部「beta0.N 测试版」横幅）。
3. 1.20.1 的 Forge / NeoForge 双包一直保持「内容相同、文件名不同」，发布时记得两条都传；  
   描述里加粗提醒「取一份即可」。
4. 标签在 `0.1.0` 正式版发布前都建议至少保留「测试版」「服务器辅助」两个，  
   不要预挂「正式可用」类标签。
