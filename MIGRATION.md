# 迁移方案：合并为 4 个 jar + Fabric 独立仓库

> 状态：**方案已定，代码未动**（截至 2026-10-13）。本文是执行手册，接手时按「第二节」逐步做即可。
>
> 决策人：项目所有者。执行前请先读完第一节的「为什么这么分」。

---

## 一、目标形态

### 1.1 NeoForge 主仓：8 jar → **4 jar**

当前 8 个 Gradle 子项目：`core` `essentials` `display` `perm` `title` `economy` `utility` `web`。

合并后：

| jar | 内含的 modId | 内容 |
|---|---|---|
| **`voidkit_core`** | `voidkit_core` | 内核（契约 / 服务注册表 / 数据 / 文本 / 变量 / 多语言） |
| **`voidkit_essentials`** | `voidkit_essentials`<br>`voidkit_perm`<br>`voidkit_economy`<br>`voidkit_utility` | 指令 + 权限 + 经济 + 清扫性能 |
| **`voidkit_display`** | `voidkit_display`<br>`voidkit_title` | 展示 + 称号 |
| **`voidkit_web`** | `voidkit_web` | 管理 API（**刻意单独**） |

**为什么 `web` 单独留？** 它是**唯一监听端口**的模块。不是所有人都需要管理平台，
不该被绑进任何一个游戏功能 jar 里 —— 不想开端口的人直接不装这个 jar。

**为什么用这个分组？** 按服主视角分：内核（必需）→ 玩法基础 → 展示 → 管理。
所有插件都只依赖 `core`，插件之间零编译依赖，所以**怎么分在技术上都不影响运行**。

### 1.2 Fabric：**新建一个仓库**，装整套插件（不含反作弊/审计）

- 仓库建议名：`KokuuWork/voidkit-fabric`
- 内容：`core` + `essentials` + `display` + `web` 的 Fabric 版（同样是 4 个 jar）
- **反作弊（voidkit-guard-oss）与审计（voidkit-inspector）始终是独立仓库**，
  各自在自己的仓里加 Fabric 支持，**不并入**主仓，也不并入 Fabric 仓。
- 新仓与主仓的关系：镜像结构（同样的目录名、同样的 `core`/`platform` 分层），
  但 `platform/` 下是 Fabric 实现。见第三节。

---

## 二、NeoForge 侧合并：具体怎么做

### 2.1 关键设计：**保留各自的 modId**，一个 jar 内放多个 mod

**不**把 4 个 modId 合成 1 个。理由：

- 语义零变化 —— `neoforge.mods.toml` 里的 `[[dependencies.voidkit_core]]`、
  文档里的 `voidkit_perm`、用户看到的 mod 列表全都保持原样；
- 风险最低 —— 不用改任何一个包名或 import；
- NeoForge 支持一个 jar 声明多个 `[[mods]]`（**但必须实测确认，见 2.6**）。

### 2.2 源码搬迁（纯增量，无跨模块引用要改）

已确认：**各插件之间零编译依赖**（都只 `implementation project(':core')`），
各模块包名互不相同（`org.voidkit.perm` / `org.voidkit.title` / …），
所以搬迁只是「把目录挪进去」，**不需要改任何 package / import**。

```
perm/src/          → essentials/src/          （含 perm/src/test/）
economy/src/       → essentials/src/
utility/src/       → essentials/src/          （含 utility/src/test/）
title/src/         → display/src/
web/               不动
core/              不动
```

搬迁后删除空的 `perm/` `title/` `economy/` `utility/` 目录。

> ⚠️ 测试目录也要一起搬，否则单测会丢：
> `perm/src/test/kotlin/org/voidkit/perm/PermissionMatcherTest.kt`
> `utility/src/test/kotlin/org/voidkit/utility/TrashBinTest.kt`

### 2.3 入口类：**不用合并**

每个模块的 `@Mod` 入口类保持独立，直接共存于同一 jar：

- `org.voidkit.essentials.VoidkitEssentials`（`@Mod("voidkit_essentials")`）
- `org.voidkit.perm.VoidkitPerm`
- `org.voidkit.economy.VoidkitEconomy`
- `org.voidkit.utility.VoidkitUtility`

它们各自在 `init {}` 里 `PluginRegistry.register(...)` + 挂 `ServerAboutToStartEvent` 读配置，
互不干扰，**搬过去即可**。

### 2.4 `neoforge.mods.toml` 合并

4 个目标 jar 各自只保留**一份** `META-INF/neoforge.mods.toml`，
把被合并模块的 `[[mods]]` 与 `[[dependencies.<该 modId>]]` 块**原样拼接**进去。

```
头部（只留一份，来自宿主模块）：
  modLoader / loaderVersion / license / issueTrackerURL

然后依次拼接（每个被合并模块）：
  [[mods]]
  modId = "voidkit_perm"
  ...
  [[dependencies.voidkit_perm]]   × 3（neoforge / minecraft / voidkit_core）
```

占位符 `${loader_range}` `${mod_version}` `${neo_range}` `${mc_range}`
由根 `build.gradle` 的 `processResources` 统一展开，**不用改**。

例：`essentials/src/main/resources/META-INF/neoforge.mods.toml`
= essentials 原有内容 + perm 的块 + economy 的块 + utility 的块。

### 2.5 构建脚本改动

**a) `settings.gradle`**：只保留 4 个 include

```groovy
include 'core'
include 'essentials'
include 'display'
include 'web'
```

**b) 根 `build.gradle` — `neoForge.mods {}` 要声明合并进来的全部 modId**

这是最容易漏的一步。ModDevGradle 靠这个块建立「modId → sourceSet」的映射，
**dev 运行时（runServer）用它识别 mod**；只声明宿主 modId 的话，
另外三个 mod 在开发环境里会「找不到」。

```groovy
def modId = "voidkit_${p.name}"
ext.modId = modId
// 合并进来的额外 modId（在子项目的 build.gradle 里用 ext.extraModIds 声明）
def allModIds = [modId] + (p.findProperty('extraModIds') ?: [])

neoForge {
    version = rootProject.ext.neoVersion
    mods {
        allModIds.each { id ->
            "${id}" {
                sourceSet(sourceSets.main)
            }
        }
    }
}
```

**c) 子项目 `build.gradle` 声明额外 modId**

```groovy
// essentials/build.gradle
ext.extraModIds = ['voidkit_perm', 'voidkit_economy', 'voidkit_utility']
```

```groovy
// display/build.gradle
ext.extraModIds = ['voidkit_title']
```

`web/build.gradle`、`core/build.gradle` 不需要（单 mod）。

**d) jar 命名与 `bundleZip` 无需改动** —— 两者都遍历 `subprojects`，
合并后自然变成「4 个 jar + 1 个 zip（`voidkit-<版本>+mc<MC版本>-<加载器>.zip`）」。

### 2.6 🚨 必须先验证的前提

**「一个 jar 内声明多个 `[[mods]]` + 多个 `@Mod` 类」是否被 NeoForge 1.21.1 正确加载。**

不要跳过这一步。验证方法：

1. 先只合并**一对**（`title` → `display`），构建；
2. 把 `display` jar 放进 `core/run/mods/`（**不要放 `core`**，core 从 classes 加载）启动；
3. 看日志：`VoidkitTitle` 是否出现在 Mod List、`titles` feature 是否注册
   （对照 `[CMD] 注册命令，active 模块 = [...]`）；
4. 通过后再做其余合并。

若多 modId 不被支持 → 退路方案见「第五节 备选方案」。

### 2.7 验证清单（每步都要跑）

```bash
./build.sh build --continue        # 应产出 4 个 jar
./build.sh bundleZip               # zip 内应为 4 个 jar
```

真实启动验证（**关键**，编译通过不等于能跑）：

```bash
# 1) core/run/mods/ 放合并后的 jar（⚠️ 不要放 voidkit_core，避免与 classes 重复）
# 2) 启动
./build.sh runServer
```

**合格标准**：

- 日志出现 `Done (...)! For help`
- `[CMD] 注册命令，active 模块 = [...]` —— 应为 **23 个** feature，一个不少
- **没有** `[CMD] /xxx 注册失败` 警告
- **没有** `FATAL` / `Mod voidkit_xxx requires ...` 之类的错误
- Mod List 里 4 个 jar 对应的 8 个 modId 都在

命令行为实测（Gradle 的 `runServer` **不转发 stdin**，管道喂命令无效）：

```properties
# core/run/server.properties
enable-rcon=true
rcon.password=<临时密码>
```

用极简 Python RCON 客户端发 `/vk status`、`/vk placeholders` 等；
验证完记得把 `enable-rcon` 改回 `false`。

---

## 三、Fabric 仓库：形态与实现

### 3.1 仓库定位

| 项 | 决定 |
|---|---|
| 仓库 | **新建**，建议 `KokuuWork/voidkit-fabric` |
| 内容 | `core` + `essentials` + `display` + `web`（4 个 jar，与 NeoForge 侧同构） |
| **不含** | `voidkit-guard`（反作弊）、`voidkit_inspector`（审计） |
| 反作弊 / 审计 | **始终独立仓库**，各自在自己的仓里加 Fabric 支持 |

### 3.2 实现路径（复用主仓已铺好的适配层）

主仓的分层已经把「跨平台」这件事收口到一处：

```
插件业务代码（零改动）
   ↓ 只用 core.api
core.api      契约层：Feature / VoidkitServices / VEvents / VItem / PermissionProvider …
   ↓
core.platform 适配层：Platform 接口 + Platforms 单例 + neoforge/ 实现
   ↓
net.minecraft.*（三平台同名，不用抽象） / net.neoforged.*（要收口）
```

**移植 = 只重写 `platform/fabric/`，`core.api` 与全部插件业务代码零改动。**

已有基础（主仓现状）：

- `core/api/events/VEvents.kt` —— 平台无关事件（`VPlayerJoin` / `VQuit` / `VDeath` / `VChat` / `VRightClickItem`）
- `core/api/VItem.kt` —— 物品访问抽象（`ItemAccessor` / `VItems`），抹平 NBT ↔ DataComponent
- `core/platform/` —— `Platform` 接口 + `Platforms` 单例
- `core/api/ServerLifecycleListener.kt` —— 生命周期收口

### 3.3 Fabric 侧要新增/替换的东西

| 项 | NeoForge 现状 | Fabric 需要 |
|---|---|---|
| mod 入口 | `@Mod(VoidkitXxx.MODID)` + `neoforge.mods.toml` | `fabric.mod.json` + `ModInitializer` |
| 事件总线 | `NeoForge.EVENT_BUS` / `@SubscribeEvent` | Fabric 的 `ServerLifecycleEvents` / `PlayerEvents` / `ServerMessageEvents` … |
| 命令注册 | `RegisterCommandsEvent` | `CommandRegistrationCallback` |
| 游戏目录 | FML 的 `FMLPaths`（已收口到 `Platform.gameDir()`） | `FabricLoader.getInstance().getGameDir()` |
| 假玩家判定 | NeoForge helper（已收口） | 自建判定 |
| 物品组件 | 1.21.1 DataComponent（`VItems` 已抽象） | 同版本 Fabric 用同一套原版类，差异主要在加载器 API |
| 依赖注入 | `ModContainer` / `IEventBus` 构造参数 | `ModInitializer.onInitialize()` |

**纪律**：主仓立的规矩要带过去 —— **零 mixin**、构造期零 IO、
不使用连字符 modId、周期任务默认保守。Fabric 生态里 mixin 很常见，
但本项目面向「几百个 mod 的大型整合包」，不抢类改写是核心兼容性策略。

### 3.4 落地顺序建议

1. 建仓 + `core` 的 Fabric 版跑通（能被 Fabric 加载、`/vk status` 可用）
2. 逐个搬插件（业务代码直接拷，只改入口与事件挂载）
3. `web` 最后（HTTP 用 JDK 自带，与加载器无关，基本是纯拷贝）
4. 反作弊 / 审计：在**各自仓库**里按同一套 `platform/` 思路加 Fabric 支持

---

## 四、现状与未做

**已确认（调研结论，可直接用）**

- 各插件之间**零编译依赖**，只依赖 core → 合并/搬迁不会产生 import 问题
- 各模块包名不同（`org.voidkit.<模块名>`）→ 合并后无同类冲突
- 8 份 `neoforge.mods.toml` 结构完全一致（同样的头部 + 一组 `[[mods]]` + 3 条 `[[dependencies.*]]`）
- 各模块 `build.gradle` 除 `core/build.gradle`（dev runServer 配置）外完全一致
- 入口类互不依赖：各自 `PluginRegistry.register(...)` + `ServerAboutToStartEvent` 读配置
- feature id（`features` 表键）与配置目录都是**字面量**，与 modId / jar 划分**无关** →
  合并**不会**影响任何现有服务器的配置文件

**未做**

- 源码搬迁、toml 合并、构建脚本改动 —— **一行都没改**（保持干净，便于新会话开始）
- 多 modId 单 jar 的实测验证

---

## 五、备选方案（若多 modId 单 jar 不被支持）

**方案 B：一个 jar 一个 modId。** 把合并模块的入口类也合成一个
（例如 `VoidkitEssentials` 里同时 `register` 权限/经济/清扫的 feature），
`neoforge.mods.toml` 只留一份 `[[mods]]`。

- 代价：`voidkit_perm` / `voidkit_economy` / `voidkit_utility` / `voidkit_title`
  这些 modId 会从 Mod List 消失；若外部有 `[[dependencies.voidkit_perm]]` 之类引用需一并改。
- 影响面：因为 feature id 与配置目录都是字面量，**功能与配置不受影响**，
  只是「mod 名字」变少。

**方案 C（不推荐）：保留 8 个 Gradle 模块，构建期合并 jar。**
开发期与生产期形态不一致，长期维护成本更高。

---

## 六、相关文档

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) —— 分层架构与关键决策
- [`ROADMAP.md`](./ROADMAP.md) —— 后续任务清单
- `docs/dev/porting.md` —— 跨平台/跨版本移植（文档站内容）
- `.workbuddy/memory/` —— 每个工作日的决策与踩坑记录
