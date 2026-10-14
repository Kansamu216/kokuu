# 贡献指南 / Contributing

## 提交信息 / Commit messages

**双语：英文在前，中文在后；用语规范、写清楚。**

```
type(scope): English subject

中文正文：为什么这么改、根因是什么、证据在哪、影响面与边界条件。
有验证就写明验证方式与结果；只做过静态推理的必须写明，不要写成"已验证"。
```

- `type`：`feat` / `fix` / `refactor` / `perf` / `docs` / `test` / `chore` / `build` / `merge`
- `scope`：`core` / `essentials` / `display` / `utility` / `web` / `build` / `docs`
- 一个提交只做一件事；不相关的改动不要塞进同一个提交（`merge` 提交除外）

## 产品文案用词纪律

**禁止**口语比喻：`查熊` / `熊孩子` / `反熊孩子`。
一律用专业术语：`审计` / `Audit`、`违规行为`、`反作弊`（英文 `inspector` 可保留）。

范围包括：文档、代码注释、日志文案、配置文件注释、命令描述、issue / PR 正文标题、站点文案。
自检：

```bash
git grep -nE "查熊|熊孩子|反熊孩子"
```

## 分支与版本线

| 分支 | Minecraft | 加载器 | 必需 JDK |
|---|---|---|---|
| `main` | 1.21.1 | NeoForge | 21 |
| `mc/1.20.1` | 1.20.1 | Forge（同一份 jar 也兼容 NeoForge 1.20.1） | 17 |

源码只在**加载器相关**的部分分叉（`core/platform/*`、8 个入口类、`mods.toml` / `neoforge.mods.toml`）；
业务代码两条线保持一致。

## 构建与验证

```bash
./build.sh build -PmcVersion=1.21.1        # 或 1.20.1
tools/smoke-test.sh -PmcVersion=1.21.1     # 开发服自检：起服 + 28 个管理 API + 19 项结构断言
tools/e2e-forge-server.sh                  # 正式服复核：把 reobf 产物装到真正的 Forge 1.20.1 上
tools/verify-installed-server.sh <目录>     # 一键验收：起服 + 模块 + API + 全命令树 + MOTD
```

⚠️ **两套产物用途不能混**（1.20.1 线特有）：

| 目录 | 方法名 | 用途 |
|---|---|---|
| `build/devlibs/` | 官方映射名 | 只给 MDG 的 `runServer` / IDE 调试 |
| `build/libs/` | SRG（`m_xxxxx_`） | 只给正式服 / 整合包 |

装反了不是"装不上"，而是能加载、能注册命令、**一部分功能一调用才炸**（`NoSuchMethodError: m_xxxxx_()`）。
不确定时用 `tools/detect-jar-mappings.py <jar>` 判定。

## 数据与配置路径

**永不随 modId 变化**：始终是 `<服务端根>/voidkit/<模块名>/...`
（如 `voidkit/perm/groups.json`、`voidkit/display/motd.json`）。
改动这块前请先确认路径是代码里的字面量常量，而不是由 modId 推导 —— 否则老服主升级会丢数据。
