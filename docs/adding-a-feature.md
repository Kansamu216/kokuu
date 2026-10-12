# 开发一个 Kokuu 插件

插件 = 一个独立 jar = 一个子项目。它可以装、可以不装，装了也不会让其它插件编译失败。

---

## 一、最少三步

### 1. 建目录

```
kokuucore/
└── myplugin/
    ├── build.gradle
    └── src/main/
        ├── kotlin/org/kokuu/myplugin/
        │   ├── KokuuMyPlugin.kt
        │   └── MyFeature.kt
        └── resources/META-INF/neoforge.mods.toml
```

### 2. `build.gradle`

```groovy
// 其余配置由根 build.gradle 的 subprojects{} 统一提供
dependencies {
    implementation project(':core')
}
```

### 3. `neoforge.mods.toml`

```toml
modLoader = "javafml"
loaderVersion = "${loader_range}"
license = "MIT"

[[mods]]
modId = "kokuu_myplugin"      # ⚠️ 只能用小写字母/数字/下划线，绝不能有连字符
version = "${mod_version}"
displayName = "KokuuMyPlugin"
authors = "你的名字"
description = '''
一句话说明这个插件做什么。
'''

[[dependencies.kokuu_myplugin]]
modId = "neoforge"
type = "required"
versionRange = "${neo_range}"
ordering = "NONE"
side = "BOTH"

[[dependencies.kokuu_myplugin]]
modId = "minecraft"
type = "required"
versionRange = "${mc_range}"
ordering = "NONE"
side = "BOTH"

[[dependencies.kokuu_myplugin]]
modId = "kokuu_core"
type = "required"
versionRange = "[${mod_version},)"
ordering = "AFTER"            # 必须 — 保证内核先初始化
side = "SERVER"
```

最后在根 `settings.gradle` 里加一行 `include 'myplugin'`。

---

## 二、插件入口

```kotlin
package org.kokuu.myplugin

@Mod(KokuuMyPlugin.MODID)
class KokuuMyPlugin(bus: IEventBus, container: ModContainer) {

    init {
        // ⚠️ 构造期只做纯内存登记
        PluginRegistry.register(MyFeature(), MODID)

        // 需要读配置的话：注册服务端启动监听，不要在这里读文件
        NeoForge.EVENT_BUS.addListener { _: ServerAboutToStartEvent ->
            MyConfig.load()
        }
    }

    companion object {
        const val MODID = "kokuu_myplugin"
    }
}
```

**为什么构造期不能读文件**：目标环境是几百个 mod 的整合包，FML 是**并行**加载 mod 的。
你在构造器里读一次文件，那几毫秒就直接加在启动关键路径上。

---

## 三、功能单元：`Feature`

一个插件可以包含多个 `Feature`（例如 `kokuu_essentials` 有 8 个）。

```kotlin
class MyFeature : Feature() {

    override val id = "my-feature"              // 写进配置的开关名
    override val displayName = "我的功能"
    override val description = "一句话说明。"
    override val defaultEnabled = true          // 可被配置文件覆盖

    /** 注册命令。服务端启动早期调用。 */
    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("hello")
                .requires { KokuuServices.permission().has(it, "kokuu.command.hello") }
                .executes {
                    Msg.ok(it.source, "你好")
                    Command.SINGLE_SUCCESS
                }
        )
    }

    /** 注册管理 API 路由。纯数据登记，不涉及 HTTP。 */
    override fun registerApi(router: ApiRouter) {
        router.get("/api/myplugin/status") { _ ->
            KokuuServer.onServer {              // 切回主线程
                ApiJson.obj("ok", true, "value", 42)
            }
        }
    }

    /** 配置在这里读 —— 已被启用、目录已就绪、命令还没注册。 */
    override fun onServerAboutToStart(server: MinecraftServer) {
        MyConfig.load()
    }

    /** 服务端完全启动。 */
    override fun onServerStart(server: MinecraftServer) {}

    /** 服务端正在关闭，应在此落盘。 */
    override fun onServerStop(server: MinecraftServer) {}

    /** 每秒一次（不是每 tick）。周期性逻辑写这里，别用 ServerTickEvent。 */
    override fun onSecond(server: MinecraftServer) {}

    /** `/kokuu reload` 时调用。 */
    override fun onReload(server: MinecraftServer) {}

    /** 需要监听 NeoForge 事件就在类里写 @SubscribeEvent 方法，框架会自动挂载。 */
}
```

### 关于 `@SubscribeEvent`

只有声明了 `@SubscribeEvent` 方法的 `Feature` 才会被注册到事件总线。

NeoForge 的事件总线对**没有任何监听方法的对象**会直接抛异常并**中断整个 mod 加载**，
所以框架会先反射检查一遍。纯命令 / 纯 API 的模块本来就不该进总线。

---

## 四、内核给的能力

全部在 `org.kokuu.core.api` 包里。

| 类 | 用途 |
|---|---|
| `Feature` | 功能单元：开关、命令、API 路由、生命周期 |
| `KokuuServices` | **跨插件通信的唯一方式** |
| `PermissionProvider` | 权限判定（不装权限插件时用 OP 兜底） |
| `KokuuServer` | 服务端实例 + `onServer {}` 切主线程 |
| `PlayerViews` | 玩家档案转 JSON 的公共视图 |
| `ApiRouter` / `ApiJson` | 路由登记表、JSON 构造工具 |

内核还提供工具（在 `org.kokuu.core.*`，非 `api` 包，属于内部实现，
但定稿后一般不会破坏性变更）：

- `ConfigFiles` — 带中文注释的 JSONC 读写
- `Text` — legacy 颜色码 + 变量 + `[文字](动作 "悬停")` 可点击语法
- `Msg` — 统一前缀的成功/失败/信息消息
- `PlaceholderRegistry` — 变量注册与解析
- `DataStore` / `PlayerProfile` — 玩家档案、IP 历史、上下线日志
- `EntityTools` — 实体遍历与过滤（清扫类插件用）
- `Teleports` — 传送与返回点记录

---

## 五、跨插件通信

**不要** import 其它插件的类 —— 那会让你的插件无法独立卸载。

正确做法是通过服务注册表：

```kotlin
// 提供方（在自己的 @Mod 构造里）
KokuuServices.register(MyService::class.java, MyServiceImpl())

// 消费方
val svc = KokuuServices.getOrNull(MyService::class.java)
if (svc != null) {
    // 用了这个服务
} else {
    // 插件没装，走降级逻辑 —— 这个分支必须有
}
```

权限是特例，它有内置兜底，所以可以直接调：

```kotlin
if (KokuuServices.permission().has(player, "kokuu.command.home")) { ... }
```

没装 `kokuu_perm` 时自动退化为「只认原版 OP」，永远不返回 null。

---

## 六、配置与数据

```kotlin
object MyConfig {
    private const val FILE = "myplugin/config.json"

    var enabled: Boolean = true
    var greeting: String = "你好"

    fun load() {
        val v = ConfigFiles.load(FILE, MyConfig::class.java, COMMENTS) { MyConfig() }
        enabled = v.enabled
        greeting = v.greeting
    }

    fun save() = ConfigFiles.save(FILE, this, COMMENTS)

    private val COMMENTS = mapOf(
        "enabled" to "是否启用。",
        "greeting" to "问候语，支持 & 颜色码与 %变量%。",
    )
}
```

路径是**相对 `kokuu/` 根目录**的。你的插件写 `myplugin/config.json`，
文件就落在 `<服务端根>/kokuu/myplugin/config.json`。

> `ConfigFiles` 支持 **JSONC**：写入时按 `COMMENTS` 表插 `// 中文说明`，
> 读取时用状态机剥掉注释再解析。字符串里的 `//`（如 URL）不受影响。

---

## 七、自检纪律

改完插件**不要只看「编译通过」**。本项目踩过的坑里有好几个是
「编译 100% 通过、一启动就崩」：

- modId 里带连字符 → 七个 jar 全被判非法，服务端拒绝启动
- Kotlin 运行时没进 classpath → `NoClassDefFoundError: kotlin/jvm/internal/Intrinsics`
- 把没有 `@SubscribeEvent` 方法的对象注册到事件总线 → 中断整个 mod 加载

**最低要求**：把 jar 丢进真实服务端（最好是目标整合包）启动一次，看日志。
