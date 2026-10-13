# perm（权限）

> 📦 **已包含在 `voidkit_essentials` 里** —— 装
> `mods/voidkit_essentials-1.0.1+mc1.21.1-neoforge.jar` 即获得本模块，
> **不需要**（也没有）单独的 `voidkit_perm` jar。本页保留，因为权限的 feature、
> 权限节点与配置都没变；数据目录也仍是 `voidkit/perm/`（见下）。

LuckPerms 风格的**权限组树**：组继承、前缀/后缀、权重、通配、临时权限。
游戏内 `/perm` 与 [管理平台](../panel/) 都能改，也可以直接编辑 JSON。

> **没装/关掉它也能用**：所有权限检查退化成「只认原版 OP」，其余功能照常。
> 开了它，才由权限节点说话。

## 判断顺序（重要）

```
① 原版 OP（等级 ≥2）且 opBypass = true   → 放行
② 玩家专属节点（含临时权限）             → 以它为准（- 前缀 = 拒绝）
③ 玩家所属各组，按权重从高到低           → 先命中的为准
④ 公开节点？（见下）                     → 放行
⑤ 都没命中                               → settings.defaultValue（默认 false）
```

### 「公开节点」是什么

套件里有一批**玩家日常命令**（`/warp` `/spawn` `/home` `/tpa` `/menu` `/balance` …）。
如果它们的默认值也是「拒绝」，服主装完套件还得先把权限配一遍，普通玩家才能回城 ——
这与「装上就能用」冲突。

所以这些节点被各插件**登记为公开**，判定到第 ④ 步时默认放行：

- **零配置下对所有玩家生效**（不装/关掉权限模块也一样）；
- 想关掉，加一条**否定节点**即可 —— 因为 ②③ 两步在前，
  显式否定一旦命中就提前返回，轮不到 ④ 兜底：

```jsonc
// 禁止 vip 组使用传送点
{ "groups": { "vip": { "permissions": ["-voidkit.command.warp"] } } }
```

> 也就是说：**公开节点只是「没配置时的默认值」，不是「绕过权限」。**

完整名单见 [权限节点参考](../guide/permissions.md)。

## 功能模块

| Feature id | 说明 | 默认 |
|---|---|---|
| `permissions` | 权限系统本体 | 开 |

## 命令

| 命令 | 说明 |
|---|---|
| `/perm` | 权限管理主命令，游戏内完成组与节点的增删改查 |

常见用法（示意）：

```
/perm group vip add voidkit.command.fly     # 给组加节点
/perm group vip setprefix &6[VIP]           # 设前缀
/perm user <玩家> group vip                 # 设主组
/perm user <玩家> add voidkit.title.vip     # 玩家级节点（优先级高于任何组）
/perm user <玩家> add -voidkit.command.back # 否定节点
```

## 配置（三个文件）

### `voidkit/perm/settings.json`

| 字段 | 说明 |
|---|---|
| `defaultGroup` | 没有显式分组的玩家归到这个组 |
| `opBypass` | 原版 OP（等级 ≥ 2）是否直接放行所有权限检查 |
| `defaultValue` | 都没命中时的默认结果（**一般保持 false**）。⚠️ **公开节点不受它影响** —— `/warp` `/spawn` 这类玩家命令在它判定之前就已放行，见上文「判断顺序」 |
| `wildcardEnabled` | 是否启用通配：`*` 命中一切，`voidkit.home.*` 命中前缀匹配 |
| `prefixFromHighestWeight` | `true` = 权重最高的组（附加组也可能顶掉主组）；`false` = 始终用主组 |

### `voidkit/perm/groups.json`

```jsonc
"groups": {
  "default": { "prefix": "&7", "weight": 0, "permissions": [] },
  "vip":     { "prefix": "&6[VIP]", "weight": 10, "parents": ["default"],
               "permissions": ["voidkit.homes.limit.10"] },
  "admin":   { "prefix": "&c[管理]", "weight": 100, "parents": ["vip"],
               "permissions": ["*"] }
}
```

每个组的字段：

| 字段 | 说明 |
|---|---|
| `prefix` / `suffix` | 聊天与 TAB 显示的前缀 / 后缀，支持 `&` 颜色码 |
| `weight` | 权重，越大越优先（决定前缀取谁、节点冲突时谁说了算） |
| `permissions` | 权限节点列表 |
| `parents` | 父组，节点与权重都会被继承（可多级，不怕写环） |

**节点写法**：

```
voidkit.command.fly     精确匹配
voidkit.home.*          前缀通配
*                       一切权限
-voidkit.command.fly    否定：明确禁止，优先级高于同组里的肯定
```

### `voidkit/perm/users.json`

玩家级覆盖，按 UUID 索引：

| 字段 | 说明 |
|---|---|
| `name` | 最近一次看到的玩家名（只方便人工查看） |
| `group` | 主组 |
| `groups` | 附加组（可多个，权限取并集） |
| `permissions` | 玩家专属节点，**优先级高于任何组**，支持 `-` 否定 |
| `tempPermissions` | 临时权限，到期自动失效 |

> ⚠️ 一般不需要手写这个文件 —— 用游戏内 `/perm` 或面板改更安全。

## 与其它模块的关系

- **称号的「谁有资格用」直接复用权限节点**（不另建名单）；
- **前缀/后缀**被聊天格式（`%prefix%`）和 TAB（`useGroupPrefix`）读取；
- 管理类命令（`voidkit.admin`）在开了权限模块后走权限判定，否则退化为 OP。
