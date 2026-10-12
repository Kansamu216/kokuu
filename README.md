# Voidkit

**NeoForge 1.21.1 的纯服务端套件** —— 用服务端 mod 实现 Bukkit / Essentials / CMI
那一类通用服务器功能，外加一套可选的分布式 Web 管理平台。

玩家用**原版客户端**即可进服，不需要安装任何东西。

```
内核 + 8 个插件，每个都是独立 jar，可单独安装、单独卸载、单独发布
```

| 模块 | modId | 内含的功能模块（`features` 里逐个开关） |
|---|---|---|
| `core/` | `voidkit_core` | **内核**：插件契约、服务注册表、数据存储、文本与变量系统、多语言 · 含 `player-tracking` |
| `essentials/` | `voidkit_essentials` | `home` `warp` `spawn` `back` `tpa` `teleport` `admin-tools` `info` `command-alias` |
| `display/` | `voidkit_display` | `motd` `tab` `scoreboard` `chat-format` `announce` `menu` |
| `perm/` | `voidkit_perm` | `permissions` — LuckPerms 风格的组树与节点（**可选**） |
| `title/` | `voidkit_title` | `title` — 称号 + 游戏内 GUI |
| `economy/` | `voidkit_economy` | `economy` — 货币、转账、排行（**可选**，只为给商店/任务类扩展留接口） |
| `utility/` | `voidkit_utility` | `clearlag`（含垃圾桶）+ `perf`（TPS·MSPT·区块热点） |
| `web/` | `voidkit_web` | `remote-api` — 管理 API 桥（**唯一会监听端口的**，**可选**） |
| `inspector/` | `voidkit_inspector` | `inspector` — 操作审计（COI）：方块/容器/实体审计，SQLite 存储（**可选**） |

> 只有 `voidkit_core` 打包了 Kotlin 运行时，其余插件共享它；`voidkit_inspector`
> 额外内置了嵌入式 SQLite 驱动（jar-in-jar），其余插件本体都只有几十 KB。

### jar 粒度 ≠ 功能粒度

**每个功能都是一个独立的 `Feature`**，可以在 `config.json` 的 `features` 段、
`/voidkit features` 清单、或面板「模块」页里**逐个开关**。

所以把命令简写放进 `essentials`、把公告和菜单放进 `display`，**不损失任何灵活性** ——
jar 粒度只决定"发布与安装的打包单位"，不决定"功能能不能单独关"。
真正合并掉的只有：独立的 jar 文件、独立的 `mods.toml`、独立的入口类、
和一套重复的 Gradle 子项目配置。

**少装任何一个都不会崩**，因为内核只认接口、并给每个服务准备了保守的兜底实现：

| 不装 | 后果 |
|---|---|
| `voidkit_perm` | 权限判定退化成「只认原版 OP」，其余功能照常 |
| `voidkit_economy` | 扣款恒返回失败，表现为「暂时不能购买」而不是报错 |
| `voidkit_web` | 服务器不监听任何端口，一行 HTTP 都不跑 |
| `voidkit_essentials` | 家上限退化成保守默认值（3 / 32） |

---

## 为什么拆成插件

内核**不认识**「家」「传送点」「权限组」「称号」这些概念。它只提供七样东西：

- 插件契约与生命周期（`Feature`）
- 跨插件服务注册表（`VoidkitServices`）
- 数据存储、文本/颜色解析、变量系统、实体与传送工具
- 一个纯数据的 HTTP 路由登记表（`ApiRouter`）—— **内核里没有一行 HTTP 代码**

由此得到的实际好处：

- **少一个插件不会让整套崩掉。** 不装 `voidkit_perm`，权限判定自动退化成
  「只认原版 OP」；不装 `voidkit_web`，服务器不会监听任何端口。
- **插件之间零编译依赖。** 它们只依赖内核的 `org.voidkit.core.api` 包，
  通过 `VoidkitServices` 互相通信。卸载任意插件不影响其它插件编译。
- **可以按需分配。** 只想要基础指令就装 `core` + `essentials`。

---

## 快速上手

### 构建

需要 **JDK 21**（Minecraft 1.21.1 的要求）。

```bash
./build.sh build --continue
```

产物在各自的 `build/libs/` 下，共九个 jar（1 内核 + 8 插件）。

### 安装

把要用的 jar 丢进服务端 `mods/` 目录即可。最少装 `voidkit_core`，
其余按需。

### 配置

首次启动会在**服务端根目录**下生成 `voidkit/`（不是存档目录里 —— 存档可以
随便删掉重开，玩家档案和 IP 记录不该跟着没）：

```
voidkit/
├── core/
│   ├── config.json          内核：语言 / 模块开关 / 追踪 / 落盘间隔
│   └── data/                玩家档案、上下线日志
├── display/
│   ├── motd.json            MOTD（可配多套轮换）
│   ├── tab.json             TAB 顶栏 / 底栏
│   ├── scoreboard.json      记分牌（可按权限切换多套）
│   ├── chat.json            聊天栏格式、悬停浮层、点击动作
│   ├── announce.json        公告框线 / 通道 / 轮播
│   └── menu.json            菜单与物品绑定
├── perm/
│   ├── groups.json          权限组树
│   ├── users.json           玩家级覆盖
│   └── settings.json        全局设置
├── title/
│   └── titles.json          称号定义
├── essentials/
│   ├── config.json          家上限 / TPA / back / 传送冷却
│   └── commands.json        要停用的命令简写（避免与别的 mod 撞名）
├── economy/
│   ├── config.json          货币名 / 初始余额 / 转账手续费
│   └── accounts.json        账户余额
├── utility/
│   ├── clearlag.json        清扫间隔 / 通道 / 垃圾桶规则
│   └── perf.json            TPS·MSPT 阈值 / 区块热点 / 干预开关
└── web/
    └── config.json          管理 API 监听地址与令牌
```

**配置文件按插件分家**：一份插件的配置可以整体备份、整体交接；卸载插件时
内核配置里不会留下一堆没人读的死字段。旧版把所有字段塞在
`core/config.json` 里的服务器**升级时会自动搬迁**（见 `LegacyConfig`）。

**所有配置文件都带中文注释**（JSONC：写入时按字段插 `// 说明`，读取时先剥注释再解析）。

### 命令

| 命令 | 说明 |
|---|---|
| `/voidkit` | 内核管理：`reload` `save` `token` `status` `features` `api` |
| `/perm` | 权限管理（需 `voidkit_perm`） |
| `/title` | 称号 GUI（需 `voidkit_title`） |
| `/sethome` `/home` `/delhome` `/homes` | 家 |
| `/setwarp` `/warp` `/warps` `/delwarp` | 公共传送点 |
| `/spawn` `/setspawn` | 出生点 |
| `/back` | 回到死亡点 / 上次传送前位置 |
| `/tpa` `/tpahere` `/tpaccept` `/tpdeny` | 玩家互传（请求自带可点的接受/拒绝按钮） |
| `/fly` `/god` `/heal` `/feed` `/speed` `/vanish` `/hat` `/enderchest` `/workbench` `/invsee` | 管理工具 |
| `/day` `/night` `/noon` `/midnight` `/dawn` `/dusk` `/freeze` `/unfreeze` | 时间简写 |
| `/sun` `/rain` `/thunder` | 天气简写 |
| `/gr <规则> [值]` `/diff <级别>` `/gm <模式> [玩家]` | 游戏规则 / 难度 / 游戏模式 |
| `/clearlag` `/clearlag status` `/clearlag test` | 清扫（`test` 会发一条测试播报，用来确认通道配置） |
| `/trash` | **打开垃圾桶**，取回被清扫的掉落物 |
| `/perf` `/perf chunks` | 性能报告 / 区块实体热点排名 |
| `/announce <内容>` `/announce auto on\|off` `/announce list` | 公告与定时轮播 |
| `/menu [名字]` `/menu list` `/menu open <玩家> <菜单>` | 菜单系统 |
| `/balance` `/bal` `/pay` `/baltop` `/eco give\|take\|set` | 经济（需 `voidkit_economy`） |

> 命令简写是**转发**给原版执行而不是重写 —— 行为、消息本地化、边界处理都与
> Mojang 写的一致。每条命令单独做冲突保护：名字被别的 mod 占了只会跳过这一条，
> 不影响其余命令注册（165 个 mod 的整合包里 `/day` 这种短名字很容易撞）。

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

## 清扫与垃圾桶

清扫到的东西**默认不是删除，而是收进垃圾桶** —— 玩家可以在下一轮清扫前自己取回。
清扫完成的播报里带一个可点的「打开垃圾桶」入口，`/trash` 也随时能开。

要同时做到「掉落物多到不会崩服」和「不会把有价值的物品挤掉」，靠三个机制：

**① 按类型聚合，不是按堆栈。**
垃圾桶内部是 `Map<物品键, 数量>`。1 万个圆石是**一格**（显示 `圆石 × 10,000`），
不是 1 万格。所以内存占用跟着**物品种类数**走，与掉落物总量无关 —— 这是
「不会因为掉落物太多崩掉」的根本原因。

**② 超容量时按价值淘汰，不是先到先得。**
物品种类也可能超过格数上限（整合包里几十种矿物碎块、几十种模组零件）。
这时不能简单丢掉新来的 —— 那会变成「垃圾把钻石挤出去」，正好和垃圾桶的目的相反。
所以满了以后按价值分排序，把**最低分的**赶出去。价值分判定顺序：

```
① 配置 protectedItems 里的              → 永不动（连桶都不进）
② 有自定义名称的                        → 永不动（玩家改过名，几乎肯定是珍视物）
③ 配置 trashItems 里的                  → 优先淘汰 + 每类限量
④ 其它                                  → 按稀有度 / 附魔 / 是否装备 / 堆叠上限累加
```

**③ 垃圾物品有每类保留上限。**
圆石留 512 个够玩家看看是什么了，剩下几百万个连进桶的机会都没有。

三类东西的处理各不相同：

| 类别 | 处理 |
|---|---|
| `protectedItems` 的贵重物 | **完全不动** |
| 有自定义名称的物品 | **完全不动** |
| 其它掉落物 | 收进垃圾桶，可点开逐格或一键取回 |
| 经验球 / 箭 / 投掷物 | 直接删除（不是「物品」，没法原样还给你） |

另外单轮处理实体数有上限（`maxPerSweep`）：万一异常刷出几百万个实体，一次性删完
会让主线程卡十几秒（客户端看就是「服务器卡死」）—— 分轮处理，每轮都在可接受耗时内。

## 性能监测

`/perf` 与 `GET /api/perf`：

- **TPS / MSPT** —— MSPT 直接读原版自己维护的滑动平均，TPS 由它反推
  （数 tick 得到的「过去一分钟平均」对突发卡顿反应太慢）
- **实体分布** —— 总数 + 掉落物/经验球/投掷物/生物分类计数
- **区块热点排名** —— 找产线、刷怪塔造成的热点区块（含区块坐标与世界坐标）
- **超阈值提醒** —— 默认只写控制台（`warnChannel` 默认 `off`），免得刷屏

**温和干预默认关闭**。打开后对超阈值的区块卸载其中的掉落物，三条硬边界：

1. 只删掉落物，**永远不碰方块、机器、生物**
2. 只在实体**已加载**时操作，绝不为了优化去加载区块
3. 每个区块有冷却、单次有总量上限

也就是说，即使配置写错，最坏情况也只是「某些掉落物被清掉了」，不会破坏存档结构。

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
@Mod(VoidkitMyPlugin.MODID)
class VoidkitMyPlugin(bus: IEventBus, container: ModContainer) {

    init {
        // 构造期只做纯内存登记，不读文件
        PluginRegistry.register(MyFeature(), MODID)
    }

    companion object { const val MODID = "voidkit_myplugin" }
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
[[dependencies.voidkit_myplugin]]
modId = "voidkit_core"
type = "required"
versionRange = "[1.0.0,)"
ordering = "AFTER"          # 保证内核先初始化
side = "SERVER"
```

可用的内核 API 全在 `org.voidkit.core.api` 包里：

| 类 | 用途 |
|---|---|
| `Feature` | 插件内的功能单元：开关、命令、API 路由、生命周期回调 |
| `VoidkitServices` | 跨插件服务注册表（唯一通信方式） |
| `PermissionProvider` | 权限判定接口（不装权限插件时用 OP 兜底） |
| `VoidkitServer` | 服务端实例 + **切回主线程**的工具（API handler 必须用） |
| `PlayerViews` | 玩家档案转 JSON 的公共视图 |
| `ApiRouter` / `ApiJson` | 纯数据的路由登记表与 JSON 构造工具 |

外层的 `build.gradle` 会自动给子项目套上 ModDevGradle + Kotlin + 镜像重定向，
新增插件只需：建目录 → 写两个 build 相关文件 → 在 `settings.gradle` 里 `include`。

---

## 管理平台（可选）

`hub/` 是唯一的 **Go 管理程序**，与 JVM 无关，可部署在小内存机器上。
单服只需在 Hub 里配一台服（本机 `127.0.0.1:8765`）即可当面板用；多服则配多台。

```
浏览器 → VoidkitHub（中心节点）→ 各游戏服的 voidkit_web（Agent）
```

- 各服的 `apiToken` **只存在 Hub 上**，浏览器只认 Hub 一个凭据
- Hub 支持账号密码（HTTP Basic）+ 机器令牌两种认证方式

```bash
cd hub && go build -o voidkithub .          # 单文件，无运行时依赖
./voidkithub -listen 0.0.0.0:9000 -user admin -pass '改成你自己的'
```

---

## 许可证

[MIT](LICENSE)
