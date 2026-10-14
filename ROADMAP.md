# Voidkit 开发路线图与执行手册

> **本文档写给执行开发的 AI 或开发者，假设读者对项目零背景。**
> 每个任务自包含：涉及文件、具体步骤、验收标准都写好了，照做即可，不需要自己查上下文。
> 按「第 6 节任务清单」的顺序执行。做完一个任务 = 一个分支 + 一个 PR。

---

## 0. 执行规则（先读这节）

**工作流循环（每个任务都走这个流程）**：

```
1. git checkout main && git pull origin main        # 确保最新
2. git checkout -b feature/<任务名>                   # 开新分支
3. 改代码（只改任务里列出的文件，别的不动）
4. ./build.sh build --continue                       # 必须编译通过
5. tools/smoke-test.sh                               # 改了 core/配置/命令 必须跑
6. git add <精确的文件> && git commit                 # 不要 git add -A
7. git push -u origin feature/<任务名>
8. gh pr create --repo KokuuWork/voidkit --title "..." --body "..."
9. gh pr merge <PR号> --repo KokuuWork/voidkit --squash --delete-branch
```

**硬性要求**：
- 全程 **Kotlin**，禁止写 Java。
- 注释用**中文**，讲「为什么」不复述代码。
- 中文注释里禁止 ASCII 直引号 `"..."` 包中文（用「」）；只有字符串字面量里允许 ASCII 引号。
- 编译通过 ≠ 能用。**改了 modId / mods.toml / 依赖声明 / core 启动路径，必须跑 smoke-test**。
- 完成任务后在 PR body 里写「Closes #<issue号>」（issue 全部做完才写；只做一部分写「Part of #<issue号>」）。

---

## 1. 项目 60 秒速览

| 项 | 值 |
|---|---|
| 是什么 | **纯服务端**套件；多加载器（NeoForge / Forge / Fabric）· 多版本（1.20.x–1.21.x），面向几百 mod 的大型科技整合包 |
| 结构 | 内核 `voidkit_core` + 4 个插件 jar（共 5 jar），另有审计模块独立仓库 |
| 语言 | Kotlin 2.1 + Gradle 8 + JDK 21；管理平台是 Go |
| 仓库 | `KokuuWork/voidkit`（本仓库）、`KokuuWork/voidkit-inspector`（审计，依赖 core 前置） |
| 版本 | 当前 `0.1.0-beta.1`（beta0.1，测试版；四条版本线共用同一版本号） |
| 目标 | 「智能 AI 基础模组」：零配置、开箱即用、不出问题。**多版本 · 多加载器已落地**（2026-10-14）：1.21.1 的 NeoForge / Forge / Fabric 三条线 + 1.20.1 的 Forge/NeoForge 均已真机验证并发布 |

**5 个 jar**（`core` `essentials` `display` `utility` `web`），每个 = `settings.gradle` 里一行
include + 一个子目录。功能粒度是 `Feature`（配置里逐个开关）。

> ⚠️ **合并记录（2026-10-13）**：原 8 个 jar 做了「一 jar 一 modId」的真源码合并 ——
> `perm` + `economy` 的源码并入 `essentials/`，`title` 的源码并入 `display/`
> （`voidkit_perm` / `voidkit_economy` / `voidkit_title` 三个 modId 消失）。**包名不变**
> （仍是 `org.voidkit.perm.*` 等），只是换了子项目：下文任务清单里出现的
> `perm/src/...`、`economy/src/...` 现应读作 `essentials/src/main/kotlin/org/voidkit/perm/...`
> 一类路径，Gradle 任务名 `:perm:test` 也改成 `:essentials:test`。详见根目录 `MIGRATION.md`。

**分层架构**（改代码前必须理解）：

```
插件 ──只依赖──▶ core.api（契约，稳定）──▶ core.platform（适配层，唯一按加载器重写）──▶ 加载器
```

- 插件**绝不** import `net.neoforged.*`（加载器 API），也**绝不** import 其它插件的类。
- `net.minecraft.*`（原版类）三平台同名，**可以**直接 import，不用抽象。
- 跨插件通信只走 `VoidkitServices`（core/api/ 里）。

---

## 2. 铁律（违反 = 返工或崩服）

1. **每个组件只依赖 core**：core + 任意一个插件必须能单独跑。插件之间零编译依赖。
2. **`org.voidkit.core.api` 是唯一契约包**。内核里**没有一行 HTTP 代码**（只挂 `ApiRouter` 路由表）。
3. **构造期零 IO**：读配置一律放 `Feature.onServerAboutToStart(server)` 回调里，不在 `@Mod` 构造里读文件。
4. **零 mixin**：只用事件监听。整合包里已有 30+ mixin mod，再插一脚会出问题。
5. **modId 只能用 `[a-z0-9_]`**，绝不能有连字符！NeoForge 校验 `^[a-z][a-z0-9_]{1,63}$`，
   违反 = 服务端拒绝启动全部 jar（`InvalidModFileException`）。
6. **jarJar 版本区间只锁下界**：写 `[2.1.0,)` 不写 `[2.1.0, 2.2.0)`（整合包里 KotlinForForge
   声明 `[2.3.0,)`，写死上界会没有交集 → FML 仲裁失败）。
7. **构造 JSON 一律走 `ApiJson.obj/put/arr`**，永远不要手写 `toString()` 塞值（历史教训：
   list 被序列化成字符串，接口返回 200 但前端 `.map()` 全崩）。
8. **周期性逻辑写 `Feature.onSecond`**，不要用 `ServerTickEvent`。
9. **玩家发言只走 `Text.legacyColor`**（仅颜色），不能走完整解析——否则玩家能发出
   可点击的钓鱼链接。玩家名要 `Text.strip` 过，否则改名 `&c假管理` 能冒充管理员。
10. **Gson 不走 Kotlin 默认参数**（用 Unsafe 分配，字段全是 null/0/false）——
    每个配置类必须写 `normalize()` 补默认值，加载后立刻调用。

---

## 3. 环境与命令

```bash
./build.sh build --continue        # 构建全部子项目（build.sh 已内置 JDK21 + 国内镜像）
./build.sh :core:compileKotlin     # 只编译 core（快速检查）
tools/smoke-test.sh                # 起真服务端 + 打全部 API + 断言（改完必跑）
cd hub && go build -o voidkithub . # 构建 Go 管理平台
```

- **不要自己设 JAVA_HOME**（build.sh 已处理；本机默认 java 是 25，Gradle 8 不认）。
- `maven.neoforged.net` 国内被污染，已由 `mirror.init.gradle` 重定向到官方 CDN，**不要改回 BMCLAPI**。
- 网络注意：git push 若报 `github.com:443` 不通，已配置 SSH over 443 自动重写，正常 push 即可。

---

## 4. 代码模式模板（新功能照抄这个骨架）

### 4.1 最小 Feature（含命令 + 配置 + API）

```kotlin
package org.voidkit.<插件名>

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.MinecraftServer
import org.voidkit.core.api.*

class MyFeature : Feature() {
    override val id = "my-feature"                      // 配置开关键，稳定唯一
    override val displayName = "我的功能"
    override val description = "一句话说明"
    override val defaultEnabled = true

    override fun onServerAboutToStart(server: MinecraftServer) {
        MyConfigStore.load()                            // 配置在这里读，不在构造器
    }

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("mycmd")
                .requires { VoidkitServices.permission().has(it, "voidkit.myplugin.mycmd") }
                .executes {
                    Msg.ok(it.source, "你好")
                    Command.SINGLE_SUCCESS
                }
        )
    }

    override fun registerApi(router: ApiRouter) {
        router.get("/api/my-feature") { _ ->
            ApiJson.obj("ok", true, "value", 42)
        }
    }
}
```

### 4.2 配置类（normalize 必写）

```kotlin
class MyConfig(
    var enabled: Boolean = true,
    var maxItems: Int = 10,
) {
    fun normalize() {                    // Gson 用 Unsafe 分配，缺字段全是零值
        // Kotlin 里 var Int 不会是 null，但 Gson 绕过构造器时字段可能是平台默认值
        // Boolean 是 false、Int 是 0 —— 按语义补回
    }
}

object MyConfigStore {
    private const val FILE = "myplugin/config.json"   // 相对 voidkit/ 数据根目录
    var config: MyConfig = MyConfig()
        private set

    fun load() {
        config = ConfigFiles.load(FILE, MyComments.CONFIG) { MyConfig() }
        config.normalize()
    }
}
```

### 4.3 插件入口（@Mod 类）

```kotlin
@Mod(VoidkitMyPlugin.MODID)
class VoidkitMyPlugin(bus: IEventBus, container: ModContainer) {
    init {
        PluginRegistry.register(MyFeature(), MODID)    // 纯内存登记，零 IO
    }
    companion object { const val MODID = "voidkit_myplugin" }
}
```

### 4.4 监听 NeoForge 事件（现有模式，VEvent 抽象前仍这样写）

```kotlin
// 在 Feature 子类里：
@SubscribeEvent
fun onPlayerQuit(event: PlayerEvent.PlayerLoggedOutEvent) {
    // 事件类是 net.neoforged 的 —— 在 issue #1 事件抽象完成前暂时允许
}
```

⚠️ 只有**有** `@SubscribeEvent` 方法的 Feature 才会进事件总线（框架自动判断）；
纯命令/纯 API 的 Feature 不要加事件方法，加了空方法会崩（见 5.1）。

### 4.5 常用内核 API 速查

| 类 | 用途 |
|---|---|
| `Msg.ok / err / info` | 给玩家/控制台发消息 |
| `Text.legacy(str)` | 富文本解析（颜色 + `[文字](动作)` 点击） |
| `Text.strip(str)` | 去颜色码 + 动作 → 纯文本 |
| `VoidkitServer.onServer { ... }` | **API handler 碰世界/实体必须包这个**（切回主线程） |
| `VoidkitServices.permission()` | 权限判定（没装/关掉权限模块时退化为只认 OP） |
| `VoidkitServices.economy()` | 经济接口（没装 economy 时 withdraw 恒失败） |
| `ConfigFiles.load/save` | 配置读写（带中文注释 + 原子写入） |
| `Icons.of("minecraft:stone")` | 按物品 id 造 ItemStack 图标 |
| `PlaceholderRegistry` | `%变量%` 系统 |

---

## 5. 已知地雷（编译器不会告诉你，全是真踩过的）

### 5.1 两个「编译通过、一启动就崩」的坑

1. **Kotlin 运行时不在开发 classpath**：开发运行从 classes 目录加载，jarJar 不生效
   → `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics`。已在 build.gradle 用
   `additionalRuntimeClasspath` 修好，**不要删那行**。
2. **空事件对象不能注册总线**：NeoForge 对「没有任何 @SubscribeEvent 方法的对象」
   调 register 直接抛 IllegalArgumentException 并**中断整个 mod 加载**。
   框架已用 `Feature.listensToEvents()` 自动判断，**不要绕过它手动 register**。

### 5.2 MC 1.21.1 映射差异（写错就编译失败或行为不对）

| 错误写法 | 正确写法 |
|---|---|
| `level.getEntitiesOfClass(type)` 单参 | `allEntities.filterIsInstance(type)` |
| `stack.hasCustomHoverName()` | `stack.has(DataComponents.CUSTOM_NAME)`（1.21 走数据组件） |
| `itemEntity.pickUpDelay` 直接访问 | 只有 `hasPickUpDelay()`（字段 private） |
| `getOrCreatePlayerScore(...)` 当 Score 用 | 返回 `ScoreAccess`，用 `.set(int)` / `.reset()` |
| 记分牌行直接 setText | 1.20.3 起用 `ScoreHolder.forNameOnly(text)`；文本相同的行**互相覆盖**，第 i 行追加 i 个 `§r` |
| MOTD 反射改 | 1.21.1 有 `setMotd()` + `invalidateStatus()`，直接用 |
| `"（$ago前）"` | `"（${ago}前）"`（`ago前` 会被当标识符） |

### 5.3 其它坑

- **Kotlin 块注释支持嵌套**：注释里写 `display/*.json` 会在编译时报
  `Syntax error: Unclosed comment`（行号指向文件末尾，极具误导性）。
  注释里要表达通配就写「display 目录下的 json」。
- **点击事件支持范围**：聊天栏 ✓、TAB 顶/底栏 ✓、**记分牌 ✗、MOTD ✗**（原版协议限制）。
  MOTD 用 `Text.toSection`（剥动作留颜色码），别和 `Text.strip`（连颜色也去）搞混。
- **权限 API 是高频调用**（每条命令/聊天都走），查询路径绝不做 IO、绝不创建数据。
- **bat 文件**：如需写 `.bat` 必须 GBK 编码 + CRLF + `chcp 936 >nul`（UTF-8 会满屏乱码报错）。

---

## 6. 任务清单（按顺序执行）

> 每个任务标注：issue 号、难度（★入门 ★★常规 ★★★需架构判断）、预计 PR 数。

### 任务 1：数据去重（issue #5）｜难度 ★★

**问题**：`PlayerProfile.group / permissions`（core/data/PlayerProfile.kt）与权限模块的
`users.json` 重复存了一份。服主用 `/perm user X group Y` 改组后，
面板显示的组名是过期的（读的 PlayerProfile 那份）。

**涉及文件**：
- `core/src/main/kotlin/org/voidkit/core/api/PermissionProvider.kt`
- `core/src/main/kotlin/org/voidkit/core/api/PlayerViews.kt`
- `core/src/main/kotlin/org/voidkit/core/data/PlayerProfile.kt`
- `essentials/src/main/kotlin/org/voidkit/perm/PermissionManager.kt`
- `essentials/src/main/kotlin/org/voidkit/perm/PermissionStore.kt`

**步骤**：
1. `PermissionProvider` 接口加方法：`fun groupOf(uuid: java.util.UUID): String`，
   `VanillaPermissionProvider` 实现返回 `""`。
2. perm 的 `PermissionManager` 实现 `groupOf(uuid)`：内部调已有的 `groupName(uuid)`。
3. `PlayerViews.summary()` 里 `"group", profile.group` 改成
   `"group", VoidkitServices.permission().groupOf(UUID.fromString(profile.uuid))`。
4. `PlayerProfile` 删掉 `group` 和 `permissions` 两个字段及 normalize 里对应行。
5. 全仓库 `grep -rn "profile.group\|profile.permissions\|\.group\b"` 找出所有引用点逐一改掉。
6. **迁移**：在 `ServerLifecycle.onServerAboutToStart` 的 `migrateLegacy` 之后加一步
   「把 PlayerProfile 里已有的非默认 group/permissions 灌进 perm/users.json」——
   只在 `perm/users.json` 里还没有该玩家记录时灌（不覆盖新数据）。注意这一步发生在
   权限模块自己的配置加载**之前**还是之后，先读
   `essentials/src/main/kotlin/org/voidkit/perm/PermissionStore.kt` 的加载时机再定。
7. 跑 smoke-test + 检查 `/api/players` 的 group 字段。

**验收**：
- `/perm user X group Y` 后，`GET /api/players` 里 X 的 group 立刻是 Y
- 老存档升级后组归属不丢（迁移生效）
- 关掉权限模块不崩（groupOf 返回 ""）

---

### 任务 2：单元测试（issue #6）｜难度 ★★

**目标**：给 core 的纯逻辑补 JUnit 测试。这些逻辑不依赖 Minecraft 类，可直接测。

**要测的（按价值排序）**：
1. `core/util/Text.kt`：颜色解析、`[文字](动作)` 解析、strip、toSection、嵌套括号
2. `utility/.../TrashBin.kt` 的价值评分（protectedItems > 自定义名 > trashItems > 稀有度）
3. `perm/.../PermissionManager` 的节点匹配（精确 / `xxx.*` 前缀 / `*` / `-否定` / 临时权限过期）
4. `core/config/ConfigEditor` 白名单写入
5. `core/util/CommentedJson` 的注释剥离（字符串里的 `//` 如 URL 不受影响）

**步骤**：
1. 根 `build.gradle` 的 `subprojects` 里确认有 `test` 任务支持（Kotlin JVM 插件自带），
   需要加依赖：`testImplementation(kotlin("test"))`（在根 build.gradle 的公共 dependencies 块加）。
2. 每个测试文件放 `<模块>/src/test/kotlin/` 对应包下。
3. 跑 `./build.sh :core:test :essentials:test :utility:test`。
4. **注意**：依赖 Minecraft 类的逻辑（ItemStack 等）**不测**，跳过——mock 成本大于收益。

**验收**：`./build.sh build` 里 test 任务全绿；覆盖上述 5 块逻辑。

---

### 任务 3：事件抽象 VEvent（issue #1 第二步）｜难度 ★★★（需按蓝图执行）

**背景**：Feature 子类现在直接写 `@SubscribeEvent fun onX(e: net.neoforged 的类)`。
移植 Fabric 时每个都要改。目标：Feature 只用平台无关的回调。

**先摸清现状**：`grep -rln "@SubscribeEvent" --include=*.kt core essentials display utility web`
（当前 12 个文件有事件监听）。

**设计蓝图（照此实现）**：

1. 新建 `core/src/main/kotlin/org/voidkit/core/api/events/VEvents.kt`，定义平台无关事件
   （只定义现有 Feature 用到的，不要多造）：

```kotlin
/** 平台无关事件。字段只用 net.minecraft.*（三平台同名）和基本类型。 */
data class VBlockBreak(val player: ServerPlayer, val pos: BlockPos, val level: ServerLevel)
data class VEntityPlace(val entity: Entity, val pos: BlockPos, val level: ServerLevel)
data class VPlayerJoin(val player: ServerPlayer)
data class VPlayerQuit(val player: ServerPlayer)
data class VPlayerDeath(val player: ServerPlayer, val source: DamageSource)
data class VContainerOpen(val player: ServerPlayer, val menu: AbstractContainerMenu)
data class VContainerClose(val player: ServerPlayer, val menu: AbstractContainerMenu)
// ... 按 grep 结果补全
```

2. `Feature` 基类加 open 回调（全部带默认空实现，子类按需覆写）：

```kotlin
open fun onBlockBreak(e: VBlockBreak) {}
open fun onPlayerQuit(e: VPlayerQuit) {}
// ... 每个 VEvent 一个
```

3. 新建 `core/platform/neoforge/NeoForgeEventBridge.kt`：一个带 `@SubscribeEvent` 的类，
   监听 NeoForge 原生事件 → 构造 VEvent → 调 `PluginRegistry.dispatch { it.onBlockBreak(ve) }`。
   在 `PluginRegistry` 加一个 `dispatch(block: (Feature) -> Unit)`：遍历 active 逐个 try-catch 调用。

4. `NeoForgePlatform.registerLifecycle` 处同时注册 `NeoForgeEventBridge`。

5. **逐插件迁移**（每个插件一个 PR，降低风险）：把 Feature 子类里的
   `@SubscribeEvent fun onX(e: NeoForge类)` 改成 `override fun onX(e: VXxx)`，
   方法体逻辑不变。迁移完一个插件，grep 确认该插件 0 处 `net.neoforged` import
   （除了 @Mod 入口类）。

**验收**：
- 全部 Feature 迁移后，`grep -rn "import net.neoforged" --include=*.kt essentials display utility web`
  只剩 @Mod 入口类（每个插件的 VoidkitXxx.kt）
- smoke-test 通过（23 模块全注册、API 断言全过）
- 有事件的功能（如称号佩戴、back 记录、经济登出清理）行为不变

**⚠️ 风险提示**：事件参数有 `isCanceled`（取消事件）、`setCanceled` 这类**可变**语义的
（如 inspector 的检查模式取消左键），VEvent 里要带 `var canceled: Boolean` 字段桥接。

---

### 任务 4：版本抽象 VItem/VNbt（issue #1 第三步）｜难度 ★★★

**背景**：1.20.1 的 ItemStack 用 NBT `getTag()`，1.21.1 用 DataComponent。
跨版本时这部分必须收口。

**蓝图**：
1. `core/api/VItem.kt` 定义读写抽象：
```kotlin
object VItems {
    fun customName(stack: ItemStack): String?      // 读自定义名
    fun hasNbtData(stack: ItemStack): Boolean     // 判断有无数据
    fun nbtBytes(stack: ItemStack): ByteArray?    // 序列化（审计用）
}
```
2. `core/platform/` 里按版本实现（当前只有 1.21.1 版本实现，读 DataComponents）。
3. 把 `utility/TrashBin.kt`、`inspector 仓的 Nbt.kt`、`title 模块的 icon` 里
   直接读 `DataComponents.*` 的调用换成 `VItems.xxx`。
4. NeoForge 1.20.1 多版本构建（Gradle 多 sourceSet 或分支）**放到最后做**，
   这一步前先确认 1.20.1 的 API 差异清单。

**验收**：`grep -rn "DataComponents" utility title economy essentials` 全部走 VItems。

---

### 任务 5：智能回滚（issue #8，在 voidkit-inspector 仓库）｜难度 ★★★

**仓库**：`KokuuWork/voidkit-inspector`（独立仓库，依赖 core 前置，开发前先 `./fetch-core.sh`）。

**v1 范围（按此做，不要扩大）**：
1. `/inspect rollback u:玩家 t:1h r:半径 [--preview]`：先实现 `--preview`
   （只统计「将回滚 N 方块 / M 容器 / K 台机器（跳过）」不实际执行）。
2. 分类判定：物品 id 的 namespace 是 `minecraft:` 的普通方块直接回滚；
   容器（箱子/熔炉）回滚物品 + NBT；**非 minecraft 命名空间（create:/mekanism:/ae2: 等
   模组机器）默认跳过并列出警告**——这是「智能」的核心，宁可少回滚不能回滚坏机器。
3. 实际执行分帧：复用清扫的分轮预算思路（每 tick 限量，不卡主线程）。
4. 回滚本身写审计记录（来源 `#rollback`）。

**涉及文件**（voidkit-inspector 仓库）：
- `src/main/kotlin/org/voidkit/inspector/InspectorFeature.kt`（加 rollback 命令）
- 新建 `Rollback.kt`
- `InspectorDb.kt`（查询待回滚记录）

**验收**：preview 数字正确；机器方块被跳过且警告列出；回滚后 `/inspect lookup` 能看到 `#rollback` 记录。

---

### 任务 6：商店 GUI（issue #2）｜难度 ★★

**前置**：估值表已做完（`economy/values.json` + `/sell` 已上线）。

**方案**：复用 display 的菜单系统思路，但**不依赖 display 插件**——
在 economy 模块内写一个只读箱子 GUI（参考 inspector 仓库的 `AuditMenu.kt` 的写法：
Container + ChestMenu 接管 clicked 不调 super）。

**步骤**：
1. `essentials/src/main/kotlin/org/voidkit/economy/ShopMenu.kt`：
   商品列表从估值表生成（`Values.file.values` 的 key 去掉 `#` 前缀），
   每格 = 商品图标 + 名称 + lore（单价）。
2. 点击 = 买入一格（扣钱 `EconomyStore.withdraw` + `player.inventory.add`）。
3. `/shop` 命令打开，权限 `voidkit.economy.shop`。

**验收**：买入扣钱、钱不够提示、背包满提示（不扣钱）。

---

### 任务 7：性能深化（issue #7）｜难度 ★★

按 issue 顺序做三件事（utility 插件）：
1. **区块耗时归因**：`/perf chunks` 已有热点排名，补「该区块里实体类型 Top5」
   （遍历该区块实体按 EntityType 分组计数）。
2. **掉落物自动合并**：同一 5x5x5 范围内同类掉落物合并成一个实体（数量累加）。
   **保守默认关闭**，配置 `perf.json` 加 `itemMerge: false` 开关。
   注意：只合并 `ItemEntity`，` age` 相近的；不动经验球。
3. **刷怪上限**：`perf.json` 加每区块生物上限配置（只统计非宠物、非命名生物），
   超限时阻止新生物自然生成（不是杀现有的）。

**验收**：三项都能在配置里关掉；默认值全部保守；smoke-test 通过。

---

### 任务 8：火焰/流体日志（issue #9，voidkit-inspector 仓库）｜难度 ★★

**v1 只做「记玩家点火/放岩浆」**（把纵火追溯到人，不逐方块记火焰蔓延）：
- 监听 `BlockEvent.EntityPlaceEvent`，方块是 `minecraft:fire` / `minecraft:lava` 时记录
  （来源 = 放置的玩家），复用现有 LogEvent 管道。
- 火焰蔓延的逐方块日志留到后续（无 mixin 拿不到干净事件，先不碰）。

---

### 任务 9：技术栈现代化 + CI（issue #11）｜难度 ★★

1. **Detekt + Ktlint**：根 build.gradle 加插件，`./build.sh lint` 跑静态检查。
2. **GitHub Actions CI**：`.github/workflows/ci.yml`——push 时跑 build + test。
   注意 workflow 文件推送需要 `gh auth refresh -h github.com -s workflow`（用户执行）。
3. **Prometheus /metrics**：web 插件加 `GET /metrics`（文本格式，TPS/MSPT/玩家数/实体数）。
4. **CI 自动发布**：打 tag 时自动构建 + 上传 Release 附件。

---

### 任务 10：后台可视化（issue #15 第二部分）｜难度 ★★

`hub/web/`（Go 管理平台前端）：
1. 「智能审计」页加**聚合条形图**：按玩家聚合操作次数（调 `/api/inspector/lookup`，
   前端 group by actor）。
2. **时间线视图**：沿时间轴展示操作序列（一条横轴，每个操作一个点，hover 显示详情）。
3. 前端约束：**无 CDN 依赖、离线可用**；风格遵守已定稿的 Go 工具风
   （浅色、单一靛蓝 `#185FA5` 强调色、0.5px 细边框、克制圆角、丝滑动效、
   文案去游戏隐喻、不要矿物色/斜面按钮/斜纹）。

---

### 任务 11：/sell all 跳过快捷栏（二次审计发现）｜难度 ★

**问题**：`/sell all` 会把快捷栏（0-8 格）里正在用的工具/武器一起卖掉
（如果服主给工具配了价）。玩家误卖手持装备的体验很差。

**涉及文件**：`essentials/src/main/kotlin/org/voidkit/economy/EconomyFeature.kt`（sellAll 函数）、
`essentials/src/main/kotlin/org/voidkit/economy/EconomyConfig.kt`（加配置）。

**步骤**：
1. `EconomyConfig` 加字段 `var sellAllSkipHotbar: Boolean = true`（normalize 兜底）。
2. `sellAll` 的 for 循环改成：
   `for (i in items.indices) { if (EssentialsStore 不存在——直接读 EconomyConfigStore.config.sellAllSkipHotbar && i < 9) continue; ... }`
   （`player.inventory.items` 的 0-8 格就是快捷栏。）
3. 卖出播报里如果因跳过快捷栏没卖任何东西，提示语说明「快捷栏已跳过（配置可关）」。

**验收**：手持钻石剑 + 背包有圆石时 `/sell all` 只卖圆石；配置改 false 后剑也卖。

---

### 任务 12：审计补完——精读剩余盲区（二次审计发现）｜难度 ★★

**背景**：已有两轮审计覆盖了 core 数据层 / TPA / economy / title / inspector，
但以下区域只做过机械扫描、没逐行精读。按顺序逐个读，发现问题按「任务格式」记录并修复。

**精读清单（按风险排序）**：

1. **`essentials/src/main/kotlin/org/voidkit/perm/PermissionManager.kt`**（最高风险）：
   节点匹配逻辑（精确 / `xxx.*` 通配 / `*` 全部 / `-xxx` 否定 / 临时权限过期），
   重点查：通配符边界（`a.b.*` 是否误匹配 `a.bc`）、否定优先级（玩家级 `-x` 是否
   真能压过组的 `+x`）、临时权限的过期判断边界（正好等于过期时刻算不算过期）。
2. **`display/src/main/kotlin/org/voidkit/display/ChatFormatFeature.kt`**：已验证玩家
   正文走 `Text.legacyColor`（安全 ✅），但精读**悬停浮层**里拼玩家数据的部分
   （坐标/等级来自 profile，确认都过了 `Text.strip`）。
3. **`display/.../ScoreboardFeature.kt` + `TabFeature.kt`**：每秒推送给全体玩家——
   查有没有在推送循环里做 IO / 昂贵计算（应该只做字符串拼接）。
4. **`essentials/.../TeleportFeature.kt` + `BackFeature.kt`**：跨维度传送的
   异常路径（目标维度不存在时的表现）、back 记录的写入时机（死亡瞬间 vs 传送前）。
5. **`utility/.../TrashBin.kt`**：价值评分的整数溢出（几个 long 相乘会不会溢出）、
   聚合键的构造（同 id 不同 NBT 的物品是否正确区分）。
6. **`web/src/main/kotlin/org/voidkit/web/ApiServer.kt`**：CORS 的
   `Access-Control-Allow-Origin` 值是什么（若为 `*` 记录低风险说明）；
   HTTP 请求体的读取有没有大小上限（防超大 body 打爆内存）。

**做法**：每个文件通读一遍，对照上面列的具体怀疑点检查。发现真 bug 就修（走标准
工作流），没发现就在 PR/commit 里写「已精读，X 处怀疑点均验证无问题」。

**验收**：6 个区域全部精读并有记录；发现的 bug 各自修复 + smoke-test 通过。

---

### 二次审计已修复清单（2026-10-12，勿重复修）

以下 5 处已修复并验证，执行新任务时**不要改动这些逻辑**，但可以参考它们的写法：

1. inspector `openContainers` 异常退出泄漏 → `onLogout`（PlayerLoggedOutEvent）清理
2. inspector `writeBatch` rollback 后字典缓存悬空 → catch 里清全部字典缓存
3. inspector `writeBatch` Statement 异常路径泄漏 → 改 `use{}`
4. essentials TPA 退出不清 pending → `onLogout` 立刻作废
5. **core `PlayerProfile` 集合并发化**：`homes`→`ConcurrentHashMap`、`ips`/`knownNames`
   →`CopyOnWriteArrayList`。原因：API 线程遍历 + 主线程 `/sethome` 并发修改会抛
   `ConcurrentModificationException`（面板查详情时 API 500）。
   **新代码里所有「会被多线程读写的集合」都必须用并发容器**——这是新铁律。

### 已知低风险项（已评估，不要修）

- web token 比较是非常量时间（timing attack 理论面；本地管理 API，实际不可利用）
- token 支持 query param 传递（会进代理日志；保留是为了 curl 调试方便）
- InspectorDb 读连接 ThreadLocal 在 close 后不 remove（关服场景，无后续使用）
- title `custom clear` 后回落显示 id 称号（覆盖层语义，合理）
- `DataStore.profile()` 的 name check-then-act 竞态（后果仅 knownNames 重复入列，
  `addKnownName` 有去重，无害）
- （第三轮）web CORS `Access-Control-Allow-Origin: *` + token 支持 query 传递的组合：
  若 token 经 query 泄漏，任意跨域页面可调 API。建议后续收紧 CORS、去掉 query 传 token
- （第三轮）web readBody 超 1MB 静默截断（无 413）——建议返回 413 而非静默截断
- （第三轮）权限「-否定」靠列表顺序生效（后写覆盖先写）——脆弱但非 bug，重构权限模型时再处理

---

## 7. 进阶任务（需要架构判断，做之前先出设计稿评审）

| 任务 | 说明 | 为什么不能直接做 |
|---|---|---|
| **NeoForge 1.20.4 多版本构建**（优先） | 同一份代码编译到 1.20.4 + 1.21.1 | 事件/假玩家与 21.1 同包同名，只差 NBT 版 ItemAccessor + Java 17 toolchain——**移植成本最低的目标** |
| **1.20.1 移植**（后置） | NeoForge 47.1.x | ⚠️ 它的 API 包名还是 `net.minecraftforge.*`（Forge 式），要额外做包名适配层 + Forge 式 mods.toml，成本远高于 1.20.2-1.20.4 |
| Gradle 多版本构建结构 | platform 拆成每版本一个子项目（LuckPerms 模式）或 `-PmcVersion` 选源集；双 toolchain（Java 17 / 21） | NBT 版 ItemAccessor 在 1.21.1 下编译不过（1.21.1 已移除 ItemStack.tag），必须等这个结构就位 |
| ~~Fabric 移植~~ ✅ **已完成**（2026-10-14） | `platform/fabric` 实现 | 真机验证通过（Fabric Loader 0.19.5 + Fabric API 0.116.17）：23/23 模块、API 全 200 |
| AI 层（自然语言查审计） | LLM 可选增强 | 原则：AI 是可选增强不是依赖——不接 LLM 时功能照常，只是少一层「聪明」 |

**进度**：Gradle 多版本结构 ✅ → 1.20.1（Forge 适配层）✅ → 1.21.1 Forge ✅ → Fabric ✅。
**剩余**：1.20.4 / 1.20.2（NBT accessor + 对应事件桥）。

---

## 8. 附录：改完代码的自检清单

- [ ] `./build.sh build --continue` 通过（零 error）
- [ ] 改了 core / 配置 / 命令 / 事件 → `tools/smoke-test.sh` 通过
- [ ] 新配置类有 `normalize()`
- [ ] 新命令有权限节点 + 有 `.requires` 判定
- [ ] API handler 碰世界/实体包了 `VoidkitServer.onServer { }`
- [ ] JSON 构造走 `ApiJson`，没有手写 toString
- [ ] 注释中文、无 ASCII 引号包中文、无 `/*` 嵌套注释陷阱
- [ ] modId 无连字符；jarJar 版本区间只锁下界
- [ ] 新增的「会被多线程读写的集合」用了并发容器（ConcurrentHashMap / CopyOnWriteArrayList）
- [ ] git add 的是精确文件列表（不是 `-A`）
