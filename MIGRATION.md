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

| jar（目录） | 声明的 modId | 内容 |
|---|---|---|
| **`voidkit_core`**（`core/`） | `voidkit_core` | 内核（契约 / 服务注册表 / 数据 / 文本 / 变量 / 多语言）—— **不动** |
| **`voidkit_essentials`**（`essentials/`） | `voidkit_essentials` | 指令 + 权限 + 经济（吸收原 `perm`、`economy` 的源码） |
| **`voidkit_display`**（`display/`） | `voidkit_display` | 展示 + 称号（吸收原 `title` 的源码） |
| **`voidkit_utility`**（`utility/`） | `voidkit_utility` | 清扫 + 性能监测 —— **不动** |
| **`voidkit_web`**（`web/`） | `voidkit_web` | 管理 API —— **不动** |

> **一个 jar 只声明一个 mod id —— 真源码合并**（不是「一个 jar 塞多个 modId」）。
> 原因：Fabric 线的 `fabric.mod.json` **一个 jar 只能有一个 mod id**，而用户要的是
> 「同一套 5-jar 分组在 NeoForge / Fabric 上复用」。所以 `perm` / `economy` / `title`
> 的**源码**被编进幸存 jar，但**不再各自作为独立 mod** 被加载（见 2.1 / 2.3）。

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
> 各 `-0.1.0-beta.1+mc1.21.1-neoforge.jar`。

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

### 2.1 关键设计：**一个 jar 只声明一个 modId**（真源码合并）

被吸收模块的 `@Mod` 注解被移除，它们的类被编进幸存 jar，初始化改由幸存者的入口类
**在构造期显式触发**。理由：

- **Fabric 线要求**：`fabric.mod.json` 一个 jar 只能声明一个 mod id。若在 NeoForge 上用
  「一个 jar 多 `[[mods]]`」（那是 NeoForge 独有特性），Fabric 线就没法复用同一套分组 ——
  两边形态必须一致。
- 用户要的是「5 jar 的分组」在多加载器上**复用同一套心智模型**，而不是每种加载器一个分法。

代价与取舍：

- `voidkit_perm` / `voidkit_economy` / `voidkit_title` 这三个 **mod id 消失**（不再出现在 Mod List）；
  若外部曾写 `[[dependencies.voidkit_perm]]`，需改成依赖 `voidkit_essentials`。
- 换来的是「一套分组、两边通用」，且**功能与数据完全不受影响**（见 2.6）。

> 注：**曾先做成「一 jar 多 modId」并验证通过**（NeoForge 确实支持），但为满足
> 「多加载器复用同一套分组」而**返工**为现在的一 jar 一 modId。

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

### 2.3 入口类：被吸收模块**去掉 `@Mod`**，由幸存者显式初始化

幸存者（仍是 `@Mod`）：`VoidkitEssentials`、`VoidkitDisplay`（以及不动的 core/utility/web）。

被吸收模块的入口类**保留类与全部初始化逻辑**，只是去掉 `@Mod` 注解、改成普通类：

| 类 | 变化 |
|---|---|
| `org.voidkit.perm.VoidkitPerm` | 去掉 `@Mod` 与 `(bus, container)` 构造参数 → `class VoidkitPerm { init { … } }` |
| `org.voidkit.economy.VoidkitEconomy` | 同上 |
| `org.voidkit.title.VoidkitTitle` | 同上 |

然后在幸存者的 `init` 里**显式跑它们的初始化**：

```kotlin
// VoidkitEssentials.init 里
VoidkitPerm()       // installPermission + register(PermissionFeature) + 挂启动事件
VoidkitEconomy()    // installEconomy + register(EconomyFeature) + %balance% + 快照 + 事件

// VoidkitDisplay.init 里
VoidkitTitle()      // register(TitleFeature) + %title% + 事件
```

时机与原来一致 —— 都是 **mod 构造期**（`PluginRegistry.boot` 在 `ServerAboutToStartEvent`
才跑，晚于构造期），所以 `VoidkitServices.installPermission(...)` 之类依旧先于 boot 完成。
构造期仍是**纯内存登记、零 IO**。

**owner 标签保持不变**：`PluginRegistry.register(feature, owner)` 仍传各自的 `MODID`
常量（`"voidkit_perm"` / `"voidkit_economy"` / `"voidkit_title"`），因此启动日志里
`Voidkit 模块 X / Y 已启用，按来源分组：…voidkit_perm(1)…` 的**归组明细与合并前完全一致**
（这些常量现在只作 owner 标签用，不再是 FML 的 mod id）。

> 注：日志措辞已从早期的「来自 N 个插件」改为「**按来源分组**」—— 合并后一个 jar 可提供
> 多个 owner 标签（如 `voidkit_essentials` 的 jar 里还有 `voidkit_perm` / `voidkit_economy`），
> 再把这些标签叫「插件」会与 `mods/` 里真实的 jar 数（4 个）自相矛盾。

### 2.4 mods.toml：只留一个 `[[mods]]`

5 个目标 jar 各自**只保留一份** `META-INF/neoforge.mods.toml`，且里面**只有一个
`[[mods]]`**（幸存者自己），外加它自己的 `[[dependencies.<幸存 modId>]]` 三块
（neoforge / minecraft / voidkit_core）。被吸收模块的 `[[mods]]` 与依赖块**删掉**。

- 在文件末尾留了一条 `#` 注释，说明「原 voidkit_perm/economy（或 title）的代码已并入本 jar，
  但不再是独立 mod；数据目录仍在 `voidkit/perm/`…」—— 方便日后有人翻 jar 时理解。
- 图标：每个 jar 一份 `icon.png`（8 份原本字节相同，只留幸存者那一份）。
- 占位符：根 `build.gradle` 的 `processResources` 用 `expand` 展开，产物中无残留 `${...}`。
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

**b) 根 `build.gradle` —— `neoForge.mods {}` 保持「一个项目一个 modId」**

因为一 jar 一 modId，这里**不需要**任何多 modId 登记，沿用原来的写法即可：

```groovy
def modId = "voidkit_${p.name}"
neoForge { mods { "${modId}" { sourceSet(sourceSets.main) } } }
```

> 反例（本次返工前一度用过、现已移除）：若一个 jar 装多个 modId，就必须在
> `neoForge.mods {}` 里逐个登记，否则 **dev 运行（runServer）会找不到那些 mod**。
> 既然改成一 jar 一 modId，这段逻辑连同 `ext.extraModIdsByProject` 一并删掉了。

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
5. **modId 取幸存者已有的名字** —— `voidkit_essentials` / `voidkit_display` / `voidkit_utility`，
   无新名字；被吸收模块的 mod id 不再作为独立 mod（取舍见 2.1）。
6. **jar 命名规范不变** —— `voidkit_<mod>-<版本>+mc<MC版本>-<loader>.jar`；
   每个 jar 一份正确的 `META-INF/neoforge.mods.toml`（只有一个 `[[mods]]`）；
   组内模块之间零依赖（都只依赖 `voidkit_core`）。
7. **`bundleZip` 正常** —— 打出 5 个 jar 的 zip。
8. **唯一的行为差异：老存档首次进服会有一次 `ModMismatchEvent` 告警**
   （`voidkit_perm/economy/title -> MISSING`），一次性、无害、不丢数据 —— 详见第三节的说明。

### 2.7 验证结果（真跑，非只看编译）

```
./build.sh build -PmcVersion=1.21.1     → BUILD SUCCESSFUL，5 个 jar
  core/build/libs/voidkit_core-0.1.0-beta.1+mc1.21.1-neoforge.jar
  essentials/build/libs/voidkit_essentials-0.1.0-beta.1+mc1.21.1-neoforge.jar
  display/build/libs/voidkit_display-0.1.0-beta.1+mc1.21.1-neoforge.jar
  utility/build/libs/voidkit_utility-0.1.0-beta.1+mc1.21.1-neoforge.jar
  web/build/libs/voidkit_web-0.1.0-beta.1+mc1.21.1-neoforge.jar

./build.sh bundleZip -PmcVersion=1.21.1  → zip 内 5 个 jar

tools/smoke-test.sh                     → 起开发服 + 打管理 API + 结构断言
```

关键判据（见 `tools/smoke-test.sh`）：

- `Done (...)! For help`
- `[CMD] 注册命令，active 模块 = [...]` —— **23 个 feature 一个不少**
- **没有** `[CMD] /xxx 注册失败`；**没有** `FATAL` / mod 依赖解析失败
- **Mod List 里只有 5 个 mod**（`voidkit_core` / `voidkit_essentials` / `voidkit_display` /
  `voidkit_utility` / `voidkit_web`）—— 不再有 `voidkit_perm` / `voidkit_economy` / `voidkit_title`
- mods/ 里 4 个插件 jar（essentials / display / utility / web；core 由 classes 提供）
- 管理 API 全 200，所有结构断言 ✓
- 启动后 `core/run/voidkit/` 仍是分目录结构：`display/motd.json`、`perm/groups.json`、
  `title/titles.json`、`economy/accounts.json` …（不是被合并成一个大文件）

实证（本分支实跑输出）：

```
# FML Mod List（只有 5 个 voidkit mod，无 perm/economy/title）
     Mod List:
		Name Version (Mod Id)
		Minecraft 1.21.1 (minecraft)
		NeoForge 21.1.209 (neoforge)
		VoidkitCore 1.0.0 (voidkit_core)
		VoidkitDisplay 1.0.0 (voidkit_display)
		VoidkitEssentials 1.0.0 (voidkit_essentials)
		VoidkitUtility 1.0.0 (voidkit_utility)
		VoidkitWeb 1.0.0 (voidkit_web)

# 模块与启动
[smoke] 已放入 4 个插件 jar（期望 4 个；内核由开发环境提供，不放 jar）
[CMD] 注册命令，active 模块 = [ … 23 个 … ]
Voidkit 模块 23 / 23 已启用，按来源分组：voidkit_display(6), voidkit_essentials(9),
        voidkit_title(1), voidkit_economy(1), voidkit_web(1), voidkit_core(2),
        voidkit_perm(1), voidkit_utility(2)
Done (7.282s)! For help, type "help"
结构断言全部通过（19 项）

# 数据路径未变（关键回归点）
$ find core/run/voidkit -type f | sed 's#core/run/voidkit/##' | sort
core/config.json
display/announce.json   display/chat.json    display/menu.json
display/motd.json       display/scoreboard.json  display/tab.json
economy/accounts.json   economy/config.json  economy/values.json
essentials/commands.json  essentials/config.json
lang/en_us.json
perm/groups.json        perm/settings.json   perm/users.json
title/titles.json
utility/clearlag.json   utility/perf.json
web/config.json
```

> 注意 `perm/` `economy/` `title/` 三个目录仍在 —— 它们的代码已被编进
> `voidkit_essentials` / `voidkit_display`，但落盘路径是代码里的字面量常量，与 modId 无关。

> 命令实测：Gradle 的 `runServer` **不转发 stdin**，`smoke-test.sh` 靠 HTTP API 断言，
> 无法端到端验证「优雅关服」。

---

## 三、坑与取舍

| 坑 | 现象 | 处理 |
|---|---|---|
| 「一 jar 多 modId」在 Fabric 线不成立 | `fabric.mod.json` 一个 jar 只能一个 mod id | 返工为**真源码合并**：移除被吸收模块的 `@Mod`，改由幸存者构造期初始化 |
| `mods {}` 漏登记 modId | （返工前）dev 运行找不到被合并的 mod | 一 jar 一 modId 后不再需要；多 modId 登记逻辑已删 |
| 根/子项目脚本求值顺序 | （返工前）子项目 `ext.extraModIds` 在根 `subprojects{}` 里读不到 | 那条路径已废弃 |
| 图标重名冲突 | 每个模块一份 `icon.png`，合并后同路径 | 8 份字节相同 → 每个 jar 只留幸存者一份 |
| 构建日志重定向 | `./build.sh build > build/xx.log` 在 `build/` 不存在时直接失败 | 先 `mkdir -p build` |
| smoke 插件 jar 期望数 | 脚本写死「7 个」 | 改为 **4 个**（不是 3）：essentials/display/utility 是合并产物，web 原样但同样进 mods/；并新增「23 个 feature」断言 |
| 旧存档里的 mod 清单比对 | 升级后启动出现 `ModMismatchEvent` 告警：`voidkit_perm (version 1.0.0 -> MISSING)` 等 | **预期内、无害**（详见下方说明） |

> **关于 `ModMismatchEvent` 告警（`voidkit_* -> MISSING`）**
>
> NeoForge 会把「加载过该存档的 mod 清单」写进**世界存档**（`level.dat` 的
> `fml.LoadingModList`，见 `CommonHooks.writeAdditionalLevelSaveData`）。下次进同一个
> 存档时，它拿存档里的清单与当前加载的 mod 比对，**缺失的 modId 会以
> `xxx (version x -> MISSING)` 的形式出现在 `ModMismatchEvent` 里并打一条 WARN**。
>
> 因此：**已有服务器升级到合并版后，第一次进老存档会看到
> `voidkit_perm` / `voidkit_economy` / `voidkit_title` 三条 MISSING 告警** —— 这正是
> 「一 jar 一 modId」移除这三个 modId 的必然后果。它是**一次性的**：
>
> - 不影响启动（服务端照常 `Done`）；
> - 不丢数据（这三个模块的数据都在 `voidkit/` 目录，不在世界 NBT 里）；
> - 换新存档 / 用一次新档进服后，存档里的清单被更新为 5 个 mod，告警消失。
>
> 本次返工验证时一度看到该告警，就是因为它来自**第一次（多 modId 版）冒烟留下的老世界存档**；
> 删掉 `core/run/world` 重跑后即不再出现（见 2.7 结论）。

**取舍**：采用**真源码合并 / 一 jar 一 modId**，而非「一 jar 多 modId」—— 后者是 NeoForge
独有，Fabric 线无法复用同一套分组。代价是 `voidkit_perm` / `voidkit_economy` / `voidkit_title`
三个 mod id 从 Mod List 消失（功能、权限节点、数据目录均不受影响）。

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
