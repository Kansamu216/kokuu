# Kokuu

**NeoForge 1.21.1 的纯服务端套件** —— 用服务端 mod 实现 Bukkit / Essentials / CMI
那一类通用服务器功能，外加一套可选的分布式 Web 管理平台。

玩家用**原版客户端**即可进服，不需要安装任何东西。

```
内核 + 6 个插件，每个都是独立 jar，可单独安装、单独卸载、单独发布
```

| 模块 | modId | 内容 | 大小 |
|---|---|---|---|
| `core/` | `kokuu_core` | **内核**：插件契约、服务注册表、数据存储、文本与变量系统 | 1.7 MB |
| `essentials/` | `kokuu_essentials` | 传送 / 家 / 传送点 / 返回 / TPA / 管理工具 / 信息查询 | 68 KB |
| `display/` | `kokuu_display` | MOTD / TAB / 记分牌 / 聊天栏 | 63 KB |
| `perm/` | `kokuu_perm` | 权限组与权限节点（**可选**） | 54 KB |
| `title/` | `kokuu_title` | 称号 + 游戏内 GUI | 27 KB |
| `utility/` | `kokuu_utility` | 清扫（清道夫）/ 世界命令简写 | 20 KB |
| `web/` | `kokuu_web` | 管理 API 桥（**唯一会监听端口的**） | 37 KB |

> 只有 `kokuu_core` 打包了 Kotlin 运行时，其余插件共享它 —— 所以插件本体都只有几十 KB。

---

## 为什么拆成插件

内核**不认识**「家」「传送点」「权限组」「称号」这些概念。它只提供七样东西：

- 插件契约与生命周期（`Feature`）
- 跨插件服务注册表（`KokuuServices`）
- 数据存储、文本/颜色解析、变量系统、实体与传送工具
- 一个纯数据的 HTTP 路由登记表（`ApiRouter`）—— **内核里没有一行 HTTP 代码**

由此得到的实际好处：

- **少一个插件不会让整套崩掉。** 不装 `kokuu_perm`，权限判定自动退化成
  「只认原版 OP」；不装 `kokuu_web`，服务器不会监听任何端口。
- **插件之间零编译依赖。** 它们只依赖内核的 `org.kokuu.core.api` 包，
  通过 `KokuuServices` 互相通信。卸载任意插件不影响其它插件编译。
- **可以按需分配。** 只想要基础指令就装 `core` + `essentials`。

---

## 快速上手

### 构建

需要 **JDK 21**（Minecraft 1.21.1 的要求）。

```bash
./build.sh build --continue
```

产物在各自的 `build/libs/` 下，共七个 jar。

### 安装

把要用的 jar 丢进服务端 `mods/` 目录即可。最少装 `kokuu_core`，
其余按需。

### 配置

首次启动会在**服务端根目录**下生成 `kokuu/`（不是存档目录里 —— 存档可以
随便删掉重开，玩家档案和 IP 记录不该跟着没）：

```
kokuu/
├── core/
│   ├── config.json          模块开关 / API / 传送 / 追踪 / 清扫
│   └── data/                玩家档案、上下线日志
├── display/
│   ├── motd.json            MOTD（可配多套轮换）
│   ├── tab.json             TAB 顶栏 / 底栏
│   ├── scoreboard.json      记分牌（可按权限切换多套）
│   └── chat.json            聊天栏格式、悬停浮层、点击动作
├── perm/
│   ├── groups.json          权限组树
│   ├── users.json           玩家级覆盖
│   └── settings.json        全局设置
└── title/
    └── titles.json          称号定义
```

**所有配置文件都带中文注释**（JSONC：写入时按字段插 `// 说明`，读取时先剥注释再解析）。

### 命令

| 命令 | 说明 |
|---|---|
| `/kokuu` | 内核管理：`reload` `save` `token` `status` `features` `api` |
| `/perm` | 权限管理（需 `kokuu_perm`） |
| `/title` | 称号 GUI（需 `kokuu_title`） |
| `/sethome` `/home` `/delhome` `/homes` | 家 |
| `/setwarp` `/warp` `/warps` `/delwarp` | 公共传送点 |
| `/spawn` `/setspawn` | 出生点 |
| `/back` | 回到死亡点 / 上次传送前位置 |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | 玩家互传 |
| `/fly` `/god` `/heal` `/feed` `/gm` `/speed` `/vanish` `/hat` | 管理工具 |
| `/day` `/night` `/sun` `/rain` `/gr` `/diff` | 世界命令简写 |

> 刻意**不重定义原版已有命令**（`/tp`、`/gamemode`、`/list` …），避免覆盖原版行为。

### 文本语法（MOTD / TAB / 聊天栏通用）

```
&a &l              颜色与样式（legacy 颜色码）
%player% %online%  变量
[文字](动作 "悬停") 可点击
```

| 动作 | 点击后 |
|---|---|
| `run:/spawn` | 以玩家身份执行命令 |
| `suggest:/msg ` | 填进聊天框，自己回车 |
| `url:https://…` | 打开链接 |
| `copy:文本` | 复制 |

例：TAB 底栏放一排按钮

```jsonc
"footer": ["&8[&a回城&8](run:/spawn \"&7点一下回到出生点\")  &8[&b主菜单&8](run:/menu)"]
```

⚠️ **支持点击的范围**：聊天栏 ✓、TAB 顶/底栏 ✓、**记分牌 ✗、MOTD ✗** ——
后两者是原版协议限制（记分牌的行是纯字符串，服务器列表不接受点击事件）。

---

## 大型整合包兼容性

本项目的主要目标环境是**几百个 mod 的重度整合包**（已在 165 个 mod 的
SkyExplorer 上实测通过）。为此做了这些约束：

- **零 mixin** —— 不与整合包里已有的 mixin mod 争抢类改写
- **mod 构造期零 IO** —— 配置读取推迟到服务端启动阶段。上百个 mod 并行加载时，
  在构造器里读文件会把那几毫秒直接加在启动关键路径上
- **不使用连字符作为 modId** —— NeoForge 校验 `^[a-z][a-z0-9_]{1,63}$`，
  带连字符会被判定为非法 mod 文件并拒绝启动
- **不做侵入性写入** —— 例如 MOTD 默认**不会**改写 `server.properties`
  （见 `MotdConfig.writeServerProperties`，默认 `false`）
- **周期性任务默认保守**，全部可配可关

---

## 开发插件

一个插件 = 一个独立 jar。最小骨架：

```kotlin
@Mod(KokuuMyPlugin.MODID)
class KokuuMyPlugin(bus: IEventBus, container: ModContainer) {

    init {
        // 构造期只做纯内存登记，不读文件
        PluginRegistry.register(MyFeature(), MODID)
    }

    companion object { const val MODID = "kokuu_myplugin" }
}

class MyFeature : Feature() {
    override val id = "my-feature"
    override val displayName = "我的功能"

    override fun registerCommands(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("hello").executes {
                Msg.ok(it.source, "你好")
                Command.SINGLE_SUCCESS
            }
        )
    }

    override fun onServerAboutToStart(server: MinecraftServer) {
        // 配置在这个阶段读
    }
}
```

然后在 `mods.toml` 里声明对内核的依赖：

```toml
[[dependencies.kokuu_myplugin]]
modId = "kokuu_core"
type = "required"
versionRange = "[1.0.0,)"
ordering = "AFTER"          # 保证内核先初始化
side = "SERVER"
```

可用的内核 API 全在 `org.kokuu.core.api` 包里：

| 类 | 用途 |
|---|---|
| `Feature` | 插件内的功能单元：开关、命令、API 路由、生命周期回调 |
| `KokuuServices` | 跨插件服务注册表（唯一通信方式） |
| `PermissionProvider` | 权限判定接口（不装权限插件时用 OP 兜底） |
| `KokuuServer` | 服务端实例 + **切回主线程**的工具（API handler 必须用） |
| `PlayerViews` | 玩家档案转 JSON 的公共视图 |
| `ApiRouter` / `ApiJson` | 纯数据的路由登记表与 JSON 构造工具 |

外层的 `build.gradle` 会自动给子项目套上 ModDevGradle + Kotlin + 镜像重定向，
新增插件只需：建目录 → 写两个 build 相关文件 → 在 `settings.gradle` 里 `include`。

---

## 管理平台（可选）

`hub/` 与 `panel/` 是两个 **Go 程序**，与 JVM 无关，可部署在小内存机器上。

```
浏览器 → KokuuHub（中心节点）→ 各游戏服的 kokuu_web（Agent）
```

- 各服的 `apiToken` **只存在 Hub 上**，浏览器只认 Hub 一个凭据
- Hub 支持账号密码（HTTP Basic）+ 机器令牌两种认证方式

```bash
cd hub && go build -o kokuuhub .          # 单文件，无运行时依赖
./kokuuhub -listen 0.0.0.0:9000 -user admin -pass '改成你自己的'
```

---

## 许可证

[MIT](LICENSE)
