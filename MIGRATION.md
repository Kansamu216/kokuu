# 迁移记录：合并为 5 个 jar + 版本线分支

> 状态：**已完成**（2026-10-13，分支 `feat/5jar-merge`，基于 `main` = 1.21.1 线）。
> 本文既是决策记录，也是实施记录 —— 第二节写「实际怎么做 + 踩到的坑」。
>
> 决策人：项目所有者。分组口径：`core` / `web` **原样不动**，其余 6 个模块合并成 3 个。

---

## 一、目标形态

### 1.1 主仓：8 jar → **5 jar**

合并前 8 个 Gradle 子项目：`core` `essentials` `display` `perm` `title` `economy` `utility` `web`。

合并后：

| jar（目录） | 内含的 modId | 内容 |
|---|---|---|
| **`voidkit_core`**（`core/`） | `voidkit_core` | 内核（契约 / 服务注册表 / 数据 / 文本 / 变量 / 多语言）—— **不动** |
| **`voidkit_essentials`**（`essentials/`） | `voidkit_essentials`<br>`voidkit_perm`<br>`voidkit_economy` | 指令 + 权限 + 经济 |
| **`voidkit_display`**（`display/`） | `voidkit_display`<br>`voidkit_title` | 展示 + 称号 |
| **`voidkit_utility`**（`utility/`） | `voidkit_utility` | 清扫 + 性能监测 —— **不动** |
| **`voidkit_web`**（`web/`） | `voidkit_web` | 管理 API —— **不动** |

**为什么 `web` 单独留？** 它是**唯一监听端口**的模块。不是所有人都需要管理平台，
不该被绑进任何一个游戏功能 jar 里 —— 不想开端口的人直接不装这个 jar。

**为什么 `utility` 不拆？** 清扫（clearlag）与性能（perf）**在同一个 Gradle 模块里、
共用一个 modId `voidkit_utility`**。若把清扫划给 essentials、性能划给 utility，
就等于把同一个 modId 拆到两个 jar —— FML 会判定重复 modId 而拒绝加载。
要那样分必须**把 `ClearLagFeature` 搬进 essentials 模块**（改归属，而非改打包），
代价与风险都更高。按整模块合并最干净。

**为什么用这个分组？** 按服主视角分：内核（必需）→ 玩法基础 → 展示 → 工具 → 管理。
所有插件都只依赖 `core`，插件之间零编译依赖，**怎么分在技术上都不影响运行**。

> jar 文件名沿用目录名（`voidkit_<目录名>`），**不引入任何新名字**：
> `voidkit_core / voidkit_essentials / voidkit_display / voidkit_utility / voidkit_web`
> 各 `-1.0.0+mc1.21.1-neoforge.jar`。

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

## 二、实际怎么做的

### 2.1 关键设计：**保留各自的 modId**，一个 jar 内放多个 mod（✅ 实测成立）

**没有**把合并进来的 modId 合成一个。一个 jar 里声明多个 `[[mods]]` +
多个 `@Mod` 类，NeoForge 正常加载（本分支已真机启动验证，见 2.7）。

这样做的好处：

- 语义零变化 —— mods.toml 里的依赖声明、文档里的 `voidkit_perm`、Mod List 保持原样；
- 风险最低 —— 不用改任何一个包名或 import；
- 外部若写了 `[[dependencies.voidkit_perm]]` 之类的引用，**不受影响**。

> 落地方式：**宿主 modId 取目录名**（essentials/display/utility），被合并模块的
> modId 原样并入同一 jar —— 既不改名字，也不丢名字。

### 2.2 源码搬迁（纯增量，无跨模块引用要改）

已确认：**各插件之间零编译依赖**（都只 `implementation project(':core')`），
各模块包名互不相同（`org.voidkit.perm` / `org.voidkit.title` / …），
所以搬迁只是「把目录挪进去」，**没有改任何 package / import**。

本次用 `git mv` 保留文件历史：

```
perm/src/main/kotlin/org/voidkit/perm/     → essentials/src/main/kotlin/org/voidkit/perm/
economy/src/main/kotlin/org/voidkit/economy/ → essentials/src/main/kotlin/org/voidkit/economy/
perm/src/test/                             → essentials/src/test/     （含 PermissionMatcherTest）
title/src/main/kotlin/org/voidkit/title/   → display/src/main/kotlin/org/voidkit/title/

utility/ web/ core/                        不动
```

搬迁后删除空的 `perm/` `title/` `economy/` 目录（连同其 build.gradle / resources）。

> ⚠️ 测试目录也一起搬了，否则单测会丢：`PermissionMatcherTest` 现在跑在
> `:essentials:test` 下（构建日志确认 27 个任务里 `:essentials:test` 已执行）。

### 2.3 入口类：**没有合并**

每个模块的 `@Mod` 入口类保持独立，直接共存于同一 jar：

- essentials jar：`org.voidkit.essentials.VoidkitEssentials` + `org.voidkit.perm.VoidkitPerm` + `org.voidkit.economy.VoidkitEconomy`
- display jar：`org.voidkit.display.VoidkitDisplay` + `org.voidkit.title.VoidkitTitle`

它们各自在 `init {}` 里 `PluginRegistry.register(...)` + 挂启动事件读配置，
互不干扰，**搬过去即可，一行未改**。

### 2.4 mods.toml 合并

5 个目标 jar 各自只保留**一份** `META-INF/neoforge.mods.toml`，
把被合并模块的 `[[mods]]` 与 `[[dependencies.<该 modId>]]` 块**原样拼接**进去：

```
头部（只留一份，来自宿主模块）：
  modLoader / loaderVersion / license / issueTrackerURL

然后依次拼接（每个被合并模块）：
  [[mods]]
  modId = "voidkit_perm" / "voidkit_economy" / "voidkit_title"
  ...
  [[dependencies.<该 modId>]]   × 3（neoforge / minecraft / voidkit_core）
```

- 图标：8 份 `icon.png` 字节完全相同（sha1 一致），故每个 jar 只留宿主那一份，
  所有 `[[mods]]` 的 `logoFile = "icon.png"` 都指向它 —— 不会出现重复条目。
- 占位符：根 `build.gradle` 的 `processResources` 用 `expand` 展开，作用在**整个文件**上，
  多个 `[[mods]]` 块里的 `${mod_version}` 等全部照常展开（已核对产物中无残留 `${...}`）。
- ⚠️ 文件名随版本线变：1.20.5+ / NeoForge 是 `neoforge.mods.toml`；1.20.1 及更早（Forge）
  是 `mods.toml`。本次在 1.21.1 线，用前者。

### 2.5 构建脚本改动

**a) `settings.gradle`**：只保留 5 个 include

```groovy
include 'core'
include 'essentials'
include 'display'
include 'utility'
include 'web'
```

**b) 根 `build.gradle` —— `neoForge.mods {}` 块要登记全部 modId**

这是最容易漏的一步。ModDevGradle 靠这个块建立「modId → sourceSet」的映射，
**dev 运行时用它识别 mod**；只声明宿主 modId 的话，另外几个 mod 在开发环境里会「找不到」。

实际做法：在根 `ext` 里用一张表做**单一事实来源**：

```groovy
ext.extraModIdsByProject = [
        essentials: ['voidkit_perm', 'voidkit_economy'],
        display   : ['voidkit_title'],
]
```

`subprojects {}` 里据此展开：

```groovy
def modId = "voidkit_${p.name}"
def allModIds = [modId] + (rootProject.ext.extraModIdsByProject[p.name] ?: [])
...
neoForge { mods { allModIds.each { mid -> "${mid}" { sourceSet(sourceSets.main) } } } }
```

> 🚨 **求值顺序坑**：不能在子项目 `build.gradle` 里写 `ext.extraModIds = [...]`
> 再回根里读 —— 根 `build.gradle` 的 `subprojects {}` 闭包**先于**子项目脚本执行，
> 那时 `findProperty('extraModIds')` 拿到的是 null。放在根 `ext` 里最稳。
>
> （`jar` 名与 `base.archivesName` 仍用宿主 `modId`，故文件名不变。
> `utility` / `web` / `core` 是单 mod，表里不出现即可。）

**c) jar 命名与 `bundleZip` 无需改动** —— 都遍历 `subprojects`，
合并后自然变成「5 个 jar + 1 个 zip」（已实测，见 2.7）。

### 2.6 向后兼容保证（硬约束逐条核对）

1. **数据/配置路径不变** —— 所有落盘路径都是**字面量字符串**，与 modId / jar 划分无关：
   `core/config.json`、`display/motd.json`、`perm/groups.json`、`title/titles.json`、
   `economy/accounts.json`、`utility/clearlag.json` …（见各处 `ConfigFiles.load/save` 的
   常量）。perm 的代码虽被编进 essentials 的 jar，运行时仍读写 `voidkit/perm/`。
   已有服主升级后**不会**出现「配置/数据凭空消失」。
2. **模块粒度开关不丢** —— 仍是 23 个 `Feature`（`core/api/Feature.kt`、
   `core/core/PluginRegistry.kt`、`/api/features`）。合并只改打包，不改功能粒度，
   每个 feature 仍可单独 `features` 开关。`/api/features` 里 23 项一个不少（已断言）。
3. **没删功能、没改行为** —— 纯打包重构。入口类、feature 类、命令、事件监听均未改。
4. **权限节点不变** —— `voidkit.command.*` / `voidkit.admin` 等未动。
5. **modId 沿用组内已有的名字** —— 宿主取 `voidkit_essentials` / `voidkit_display` /
   `voidkit_utility`，无新名字。
6. **jar 命名规范不变** —— `voidkit_<mod>-<版本>+mc<MC版本>-<loader>.jar`；
   每个 jar 一份正确的 `META-INF/neoforge.mods.toml`；组内模块之间零依赖（只依赖 `voidkit_core`）。
7. **`bundleZip` 正常** —— 打出 5 个 jar 的 zip。

### 2.7 验证结果（真跑，非只看编译）

```
./build.sh build -PmcVersion=1.21.1     → BUILD SUCCESSFUL，5 个 jar
  core/build/libs/voidkit_core-1.0.0+mc1.21.1-neoforge.jar
  essentials/build/libs/voidkit_essentials-1.0.0+mc1.21.1-neoforge.jar
  display/build/libs/voidkit_display-1.0.0+mc1.21.1-neoforge.jar
  utility/build/libs/voidkit_utility-1.0.0+mc1.21.1-neoforge.jar
  web/build/libs/voidkit_web-1.0.0+mc1.21.1-neoforge.jar

./build.sh bundleZip -PmcVersion=1.21.1  → zip 内 5 个 jar

tools/smoke-test.sh                     → 起开发服 + 打管理 API + 结构断言
```

关键判据（见 `tools/smoke-test.sh`）：

- `Done (...)! For help`
- `[CMD] 注册命令，active 模块 = [...]` —— **23 个 feature 一个不少**
- **没有** `[CMD] /xxx 注册失败`；**没有** `FATAL` / mod 依赖解析失败
- mods/ 里 4 个插件 jar（essentials / display / utility / web；core 由 classes 提供）
- 管理 API 全 200，19 项结构断言全 ✓
- 启动后 `core/run/voidkit/` 仍是分目录结构：`display/motd.json`、`perm/groups.json`、
  `title/titles.json`、`economy/accounts.json` …（不是被合并成一个大文件）

> 命令实测：Gradle 的 `runServer` **不转发 stdin**，`smoke-test.sh` 靠 HTTP API 断言，
> 无法端到端验证「优雅关服」。

---

## 三、坑与取舍

| 坑 | 现象 | 处理 |
|---|---|---|
| 多 modId 单 jar 是否被加载 | MIGRATION 原 §2.6 标为「必须先验证」 | 本分支实际合并 + 起服验证，**成立**，无需退到「方案 B」 |
| `mods {}` 漏登记 modId | dev 运行找不到被合并的 mod | 用 `extraModIdsByProject` 逐个登记 |
| 根/子项目脚本求值顺序 | 子项目 `ext.extraModIds` 在根 `subprojects{}` 里读不到 | 额外 modId 表放**根 `ext`** |
| 图标重名冲突 | 每个模块一份 `icon.png`，合并后同路径 | 8 份字节相同 → 每个 jar 只留一份，`logoFile` 都指向它 |
| 构建日志重定向 | `./build.sh build > build/xx.log` 在 `build/` 不存在时直接失败 | 先 `mkdir -p build` |
| smoke 插件 jar 期望数 | 脚本写死「7 个」 | 改为 **4 个**（不是 3）：essentials/display/utility 是合并产物，web 原样但同样进 mods/；并新增「23 个 feature」断言 |

**取舍**：坚持「保留各自 modId」而非合并成单一 modId —— 兼容最好、改动最小，
代价是单个 jar 的 mods.toml 稍长、Mod List 里 mod 数不变（8 个 mod，5 个 jar）。

---

## 四、与版本线分支的关系

合并会大范围移动文件，而移植要改 platform 层与入口类 —— 两件事叠在一起，
出问题很难定位是「合并搞坏的」还是「移植不对」。

本次先在 `main`（1.21.1）线完成合并并验证。**每条版本线分支都要各自做一遍**，
或先把合并 forward-port 过去。`mc/1.20.1` 线尤其注意 toml 文件名
（1.20.1 是 `mods.toml`，不是 `neoforge.mods.toml`）。

---

## 五、未做 / 存疑

- **面向使用者的文档站（`README.md` / `ROADMAP.md` / `docs/`）尚未同步**。它们仍把
  `perm/` `title/` `economy/` 当成「单独安装的 jar」来写（例如 `docs/modules/perm.md`
  写 `mods/voidkit_perm-1.0.0+…jar`）。合并后应改为「装 `voidkit_essentials` jar（内含
  perm/economy）」。本次只更新了 `ARCHITECTURE.md` 与 `MIGRATION.md`（结构说明），
  文档站留作后续单独一遍 —— 避免与其它并行改动冲突。
- **真人进服交互未验证**（GUI 点击、`/sell` 交易、称号设置）—— 沿用既有未覆盖项，
  smoke-test 覆盖不到。
- **`-PmcVersion=1.20.4` 版本线未在本分支跑过**。`extraModIdsByProject` 与 toml 合并
  逻辑是版本线无关的，但 1.20.4 的 toml 文件名/占位符未在本次一并验证。
- 合并后单测只覆盖了原有纯逻辑测试（PermissionMatcherTest / TrashBinTest / core 的三个），
  未新增针对「合并」本身的新测试。

---

## 六、相关文档

- [`PORTING-1.20.1.md`](./PORTING-1.20.1.md) —— 1.20.1 移植施工单（分支 `mc/1.20.1`）
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) —— 分层架构与关键决策
- [`ROADMAP.md`](./ROADMAP.md) —— 后续任务清单
- `docs/guide/versions.md` —— 面向使用者的版本与加载器说明（文档站）
