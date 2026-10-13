# 迁移方案：合并为 5 个 jar + 版本线分支

> 状态：**方案已定，代码未动**（截至 2026-10-13）。本文是执行手册，接手按第二节逐步做即可。
>
> 决策人：项目所有者。执行前请先读完第一节的「为什么这么分」。

---

## 一、目标形态

### 1.1 主仓：8 jar → **5 jar**

当前 8 个 Gradle 子项目：`core` `essentials` `display` `perm` `title` `economy` `utility` `web`。

合并后：

| jar | 内含的 modId | 内容 |
|---|---|---|
| **`voidkit_core`** | `voidkit_core` | 内核（契约 / 服务注册表 / 数据 / 文本 / 变量 / 多语言） |
| **`voidkit_essentials`** | `voidkit_essentials`<br>`voidkit_perm`<br>`voidkit_economy` | 指令 + 权限 + 经济 |
| **`voidkit_display`** | `voidkit_display`<br>`voidkit_title` | 展示 + 称号 |
| **`voidkit_utility`** | `voidkit_utility` | 清扫 + 性能监测（**原样不动**） |
| **`voidkit_web`** | `voidkit_web` | 管理 API（**刻意单独**） |

**为什么 `web` 单独留？** 它是**唯一监听端口**的模块。不是所有人都需要管理平台，
不该被绑进任何一个游戏功能 jar 里 —— 不想开端口的人直接不装这个 jar。

**为什么 `utility` 不拆？** 清扫（clearlag）与性能（perf）**在同一个 Gradle 模块里、
共用一个 modId `voidkit_utility`**。若把清扫划给 essentials、性能划给 utility，
就等于把同一个 modId 拆到两个 jar —— FML 会判定重复 modId 而拒绝加载。
要那样分必须**把 `ClearLagFeature` 搬进 essentials 模块**（改归属，而非改打包），
代价与风险都更高。按整模块合并最干净。

**为什么用这个分组？** 按服主视角分：内核（必需）→ 玩法基础 → 展示 → 工具 → 管理。
所有插件都只依赖 `core`，插件之间零编译依赖，**怎么分在技术上都不影响运行**。

### 1.2 加载器与版本：**一个仓库 + 版本线分支**（不按加载器分仓）

```
KokuuWork/voidkit
├── main              1.21.1 · NeoForge      ← 主线
├── mc/1.20.1         1.20.1 · Forge + NeoForge（同一份 jar）
└── …                 后续按版本线继续加分支（含 Fabric）
```

> **早期曾打算给 Fabric 单开仓库，已作废**：代码只有一份，差异集中在 `core.platform`
> 适配层；分仓会让「同一处 bug 改三遍」。
>
> **1.20.1 的 Forge 与 NeoForge 共用一份 jar**：NeoForge 1.20.1 就是 Forge 47.1.x 的分支，
> API 包名完全相同（`net.minecraftforge.*`），Forge 的 mod 能直接在 NeoForge 1.20.1 上加载。
> 详见 `PORTING-1.20.1.md`。

---

## 二、合并怎么做

### 2.1 关键设计：**保留各自的 modId**，一个 jar 内放多个 mod

**不**把合并进来的 modId 合成一个。理由：

- 语义零变化 —— mods.toml 里的 `[[dependencies.*]]`、文档里的 `voidkit_perm`、
  用户看到的 mod 列表全都保持原样；
- 风险最低 —— 不用改任何一个包名或 import；
- 一个 jar 声明多个 `[[mods]]` 是支持的（**但必须实测确认，见 2.6**）。

### 2.2 源码搬迁（纯增量，无跨模块引用要改）

已确认：**各插件之间零编译依赖**（都只 `implementation project(':core')`），
各模块包名互不相同（`org.voidkit.perm` / `org.voidkit.title` / …），
所以搬迁只是「把目录挪进去」，**不需要改任何 package / import**。

```
perm/src/          → essentials/src/          （含 perm/src/test/）
economy/src/       → essentials/src/
title/src/         → display/src/

utility/           不动
web/               不动
core/              不动
```

搬迁后删除空的 `perm/` `title/` `economy/` 目录。

> ⚠️ 测试目录也要一起搬，否则单测会丢：
> `perm/src/test/kotlin/org/voidkit/perm/PermissionMatcherTest.kt`

### 2.3 入口类：**不用合并**

每个模块的 `@Mod` 入口类保持独立，直接共存于同一 jar：

- `org.voidkit.essentials.VoidkitEssentials`（`@Mod("voidkit_essentials")`）
- `org.voidkit.perm.VoidkitPerm`（`@Mod("voidkit_perm")`）
- `org.voidkit.economy.VoidkitEconomy`
- `org.voidkit.display.VoidkitDisplay`
- `org.voidkit.title.VoidkitTitle`

它们各自在 `init {}` 里 `PluginRegistry.register(...)` + 挂启动事件读配置，
互不干扰，**搬过去即可**。

### 2.4 mods.toml 合并

5 个目标 jar 各自只保留**一份** `META-INF/<toml>`，
把被合并模块的 `[[mods]]` 与 `[[dependencies.<该 modId>]]` 块**原样拼接**进去。

```
头部（只留一份，来自宿主模块）：
  modLoader / loaderVersion / license / issueTrackerURL

然后依次拼接（每个被合并模块）：
  [[mods]]
  modId = "voidkit_perm"
  ...
  [[dependencies.voidkit_perm]]   × 3（加载器 / minecraft / voidkit_core）
```

> ⚠️ **文件名随版本线变**：1.20.5+ / NeoForge 是 `neoforge.mods.toml`；
> 1.20.1 及更早（Forge）是 `mods.toml`。合并时按当前版本线的名字走。

占位符由根 `build.gradle` 的 `processResources` 统一展开，**不用改**。

### 2.5 构建脚本改动

**a) `settings.gradle`**：只保留 5 个 include

```groovy
include 'core'
include 'essentials'
include 'display'
include 'utility'
include 'web'
```

**b) 根 `build.gradle` — `mods {}` 块要声明合并进来的全部 modId**

这是最容易漏的一步。ModDevGradle 靠这个块建立「modId → sourceSet」的映射，
**dev 运行时用它识别 mod**；只声明宿主 modId 的话，另外几个 mod 在开发环境里会「找不到」。

```groovy
def modId = "voidkit_${p.name}"
ext.modId = modId
// 合并进来的额外 modId（在子项目的 build.gradle 里用 ext.extraModIds 声明）
def allModIds = [modId] + (p.findProperty('extraModIds') ?: [])
```

（`allModIds` 逐个塞进 `neoForge { mods { … } }` / `legacyForge { mods { … } }`。）

**c) 子项目 `build.gradle` 声明额外 modId**

```groovy
// essentials/build.gradle
ext.extraModIds = ['voidkit_perm', 'voidkit_economy']
```

```groovy
// display/build.gradle
ext.extraModIds = ['voidkit_title']
```

`utility` / `web` / `core` 不需要（单 mod）。

**d) jar 命名与 `bundleZip` 无需改动** —— 都遍历 `subprojects`，
合并后自然变成「5 个 jar + 1 个 zip」。

### 2.6 🚨 必须先验证的前提

**「一个 jar 内声明多个 `[[mods]]` + 多个 `@Mod` 类」是否被目标加载器正确加载。**

不要跳过。验证方法：

1. 先只合并**一对**（`title` → `display`），构建；
2. 把 `display` jar 放进 `core/run/mods/`（**不要放 `core`**，core 从 classes 加载）启动；
3. 看日志：`VoidkitTitle` 是否出现在 Mod List、`titles` feature 是否注册
   （对照 `[CMD] 注册命令，active 模块 = [...]`）；
4. 通过后再做其余合并。

若多 modId 不被支持 → 退路见第五节。

### 2.7 验证清单

```bash
./build.sh build --continue        # 应产出 5 个 jar
./build.sh bundleZip               # zip 内应为 5 个 jar
```

真实启动（**关键**，编译通过不等于能跑）：把合并后的 jar 放进 `core/run/mods/`
（⚠️ 不要放 `voidkit_core`）后 `./build.sh runServer`。

**合格标准**：

- 出现 `Done (...)! For help`
- `[CMD] 注册命令，active 模块 = [...]` —— **23 个 feature 一个不少**
- **没有** `[CMD] /xxx 注册失败`
- **没有** `FATAL` / mod 依赖解析失败
- Mod List 里 5 个 jar 对应的 8 个 modId 都在

命令实测：Gradle 的 `runServer` **不转发 stdin**，要开 RCON
（`core/run/server.properties`：`enable-rcon=true` + 密码），用极简 Python RCON 客户端发
`/vk status`；验证完改回 `false`。

---

## 三、与版本线分支的关系

**建议顺序：先把 1.20.1 移植做完并验证，再做合并。**

合并会大范围移动文件，而移植要改 platform 层与入口类 —— 两件事叠在一起，
出问题很难定位是「合并搞坏的」还是「移植不对」。

若先做了合并，则**每条版本线分支都要各自做一遍**（或先把合并做进 `main`，
再 forward-port）。

---

## 四、现状与未做

**已确认（调研结论，可直接用）**

- 各插件之间**零编译依赖**，只依赖 core → 合并/搬迁不会产生 import 问题
- 各模块包名不同（`org.voidkit.<模块名>`）→ 合并后无同类冲突
- 8 份 mods.toml 结构完全一致；各模块 `build.gradle` 除 core 外一致
- 入口类互不依赖：各自 `PluginRegistry.register(...)` + 启动事件读配置
- feature id（`features` 表键）与配置目录都是**字面量**，与 modId / jar 划分**无关** →
  合并**不会**影响任何现有服务器的配置文件

**未做**

- 源码搬迁、toml 合并、构建脚本改动 —— **一行都没改**
- 多 modId 单 jar 的实测验证

---

## 五、备选方案（若多 modId 单 jar 不被支持）

**方案 B：一个 jar 一个 modId。** 把合并模块的入口类也合成一个
（例如 `VoidkitEssentials` 里同时 `register` 权限/经济的 feature），
mods.toml 只留一份 `[[mods]]`。

- 代价：`voidkit_perm` / `voidkit_economy` / `voidkit_title` 这些 modId 会从 Mod List 消失；
  若外部有 `[[dependencies.voidkit_perm]]` 之类的引用需一并改。
- 影响面：因为 feature id 与配置目录都是字面量，**功能与配置不受影响**，
  只是「mod 名字」变少。

**方案 C（不推荐）：保留 8 个 Gradle 模块，构建期合并 jar。**
开发期与生产期形态不一致，长期维护成本更高。

---

## 六、相关文档

- [`PORTING-1.20.1.md`](./PORTING-1.20.1.md) —— 1.20.1 移植施工单（分支 `mc/1.20.1`）
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) —— 分层架构与关键决策
- [`ROADMAP.md`](./ROADMAP.md) —— 后续任务清单
- `docs/guide/versions.md` —— 面向使用者的版本与加载器说明（文档站）
