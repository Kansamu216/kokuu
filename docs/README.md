---
home: true
title: 首页
heroImage: /images/logo.svg
heroText: Voidkit
tagline: NeoForge 1.21.1 纯服务端模组套件 —— 装着就能用，不用担心几百个 mod 的整合包会把服搞崩
actions:
  - text: 快速开始
    link: /guide/
    type: primary
  - text: 模块总览
    link: /modules/
    type: secondary
features:
  - title: 纯服务端
    details: 玩家用原版客户端即可进服，不需要装任何东西。所有功能都在服务端完成。
  - title: 内核 + 插件
    details: 内核只提供契约，功能拆成一个个独立 jar，可单独安装、单独卸载、单独关闭。
  - title: 大整合包友好
    details: 零 mixin、构造期零 IO、周期任务保守。已在 165 个 mod 的 SkyExplorer 上实测。
  - title: 反作弊
    details: VoidkitGuard 提供客户端 Agent + 服务端校验：哈希锁、中途复检、蜜罐协议、静默取证。
  - title: 操作审计
    details: VoidkitInspector 是 CoreProtect 风格的审计，专为科技包设计，自动化产线不污染日志。
  - title: 管理平台
    details: VoidkitHub 是单个 Go 二进制，把多台服务器的数据聚合成一个后台，浏览器直接访问。
footer: MIT Licensed · 文档站由 VuePress 构建并部署于 GitHub Pages
---

## 它解决什么问题

大型科技整合包（机械动力 / Mekanism / AE2 那一类）里，服务器运营的痛点很具体：

- 想装个**家 / TPA / 记分牌 / 清扫**，却发现 Bukkit 生态的插件在 NeoForge 上用不了；
- 装了**查错/查破坏**（COI）插件，结果一条自动化产线刷几百万圆石，全进了数据库，
  真正的那次破坏被淹没在噪音里，根本查不出来；
- 反作弊要么完全没有，要么需要玩家装一堆客户端 mod，还容易被绕过。

Voidkit 就是针对这些场景做的：**内核 + 插件**的纯服务端套件，外加**独立的反作弊与审计组件**，
配套一个轻量管理平台。

## 从这里开始

| 我是… | 去哪 |
|---|---|
| 服主，想装上跑起来 | [快速开始](/guide/) → [安装部署](/guide/install.md) |
| 服主，想查命令和权限 | [命令速查](/guide/commands.md) |
| 想在后台管服务器 | [管理平台](/panel/) |
| 关心反作弊 | [反作弊 VoidkitGuard](/anticheat/) |
| 关心查破坏 / 回滚 | [审计 VoidkitInspector](/audit/) |
| 开发者，想写自己的插件 | [开发指南](/dev/) |
