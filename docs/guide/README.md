# 快速开始

Voidkit 是一套 **NeoForge 1.21.1 纯服务端模组套件**。玩家用原版客户端即可进服，
所有功能都在服务端完成。

一句话概括产品取向：**装上就能用、用着舒服、不会因为几百个 mod 的环境而出问题。**

## 三步跑起来

### 1. 构建（或下载 Release）

源码构建需要 **JDK 21**（Minecraft 1.21.1 的硬性要求）：

```bash
git clone https://github.com/KokuuWork/voidkit.git
cd voidkit
./build.sh build --continue
```

产物在各模块的 `build/libs/` 下。也可以直接从
[Releases](https://github.com/KokuuWork/voidkit/releases) 下载。

### 发布包命名规格

每个版本线（MC 版本 × 加载器）打一个 zip，里面是这一版全部 jar：

```
voidkit-1.0.0+mc1.21.1-neoforge.zip
├── voidkit_core-1.0.0+mc1.21.1-neoforge.jar
├── voidkit_essentials-1.0.0+mc1.21.1-neoforge.jar
├── voidkit_display-1.0.0+mc1.21.1-neoforge.jar
└── …（共 8 个）
```

文件名格式为 **`模组名-模组版本+mc版本-加载器`** ——
多版本多加载器并存时，一眼就能看出一个 jar 属于哪条版本线。

构建发布包：

```bash
./build.sh bundleZip      # → build/dist/voidkit-1.0.0+mc1.21.1-neoforge.zip
```

### 2. 安装

把要用到的 jar 丢进服务端 `mods/` 目录。

**最少只需要 `voidkit_core`**，其余按需添加。例如只要基础指令：

```
mods/
├── voidkit_core-1.0.0+mc1.21.1-neoforge.jar        ← 必需
├── voidkit_essentials-1.0.0+mc1.21.1-neoforge.jar  ← 家 / 传送 / 管理工具
└── voidkit_display-1.0.0+mc1.21.1-neoforge.jar     ← MOTD / TAB / 记分牌 / 聊天栏
```

> 只有 `voidkit_core` 打包了 Kotlin 运行时，其余插件共享它，插件本体都只有几十 KB。

### 3. 配置

首次启动会在**服务端根目录**下生成 `voidkit/` 配置目录（不是存档目录里 ——
存档可以随便删掉重开，玩家档案和 IP 记录不该跟着没）。

**所有配置文件都带中文注释**（JSONC：写入时按字段插 `// 说明`，读取时先剥注释再解析），
打开就能看懂每个字段是干什么的。

详细说明见 [安装与部署](./install.md) 与 [配置文件](./config.md)。

## 下一步

- [安装与部署](./install.md) —— 目录结构、可选模块、卸载行为
- [配置文件](./config.md) —— 每个文件的字段说明
- [命令速查](./commands.md) —— 全部命令与权限节点
- [文本语法](./text.md) —— 颜色码 / 变量 / 可点击
- [架构](./architecture.md) —— 内核/插件怎么分层，为什么这么设计
- [常见问题](./faq.md)
