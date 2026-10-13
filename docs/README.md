---
home: true
heroImage: /images/icon.png
heroText: Voidkit
tagline: NeoForge 1.21.1 纯服务端模组套件
actions:
  - text: 快速开始
    link: /guide/
    type: primary
  - text: 查看文档
    link: /modules/
    type: secondary
footer: MIT Licensed · 文档站由 VuePress 构建并部署于 GitHub Pages
---

<div class="vk-home">

<div class="vk-intro">
  给 NeoForge 服务器补齐 Bukkit / Essentials / CMI 那一类通用功能，
  外加独立的<strong>反作弊</strong>与<strong>操作审计</strong>组件，和一个轻量管理平台。
  <div class="sub">
    玩家用原版客户端即可进服，零 mixin、构造期零 IO。
    已在 165 个 mod 的整合包上实测 —— 装上就能用，用着舒服。
  </div>
</div>

<div class="vk-features">
  <div class="vk-feature">
    <span class="vk-ico vk-ico-essentials"></span>
    <h3>基础服务器功能</h3>
    <p>家、传送、TPA、记分牌、聊天栏、清扫、经济、权限、称号。每个都是独立 jar，缺哪个都退化成保守行为，不会崩。</p>
  </div>
  <div class="vk-feature">
    <span class="vk-ico vk-ico-anticheat"></span>
    <h3>反作弊</h3>
    <p>客户端 Agent + 服务端校验。哈希锁、随机中途复检、蜜罐协议、静默取证 —— 主刃在服务端，改客户端也没用。</p>
  </div>
  <div class="vk-feature">
    <span class="vk-ico vk-ico-audit"></span>
    <h3>操作审计</h3>
    <p>CoreProtect 风格查破坏，专为科技整合包设计。自动化产线的噪音不进库，智能回滚带预览与撤销。</p>
  </div>
</div>

<h2 class="vk-title">管理平台<small>单个 Go 二进制，把多台服务器聚合成一个后台</small></h2>

<figure class="vk-show">
  <img src="./images/panel/panel-fleet.png" alt="VoidkitHub 总览页">
  <figcaption>总览 —— 实时状态与智能提示，浏览器打开即用</figcaption>
</figure>

<h2 class="vk-title">三步跑起来</h2>

<div class="vk-start">
  <ol class="vk-startsteps">
    <li>
      <h3>构建</h3>
      <p>需要 JDK 21，也可以直接下载 Releases 整版 zip。</p>
      <code>./build.sh build --continue</code>
    </li>
    <li>
      <h3>安装</h3>
      <p>把 jar 丢进服务端 <code>mods/</code>。最少只需 <code>voidkit_core</code>，其余按需。</p>
      <code>voidkit_core-1.0.0+mc1.21.1-neoforge.jar</code>
    </li>
    <li>
      <h3>配置</h3>
      <p>首次启动生成 <code>voidkit/</code>，配置文件全带中文注释。</p>
      <code>/vk reload</code>
    </li>
  </ol>
</div>

<div class="vk-explore">
  <a href="/voidkit/modules/"><span class="vk-ico vk-ico-core"></span>模块</a>
  <a href="/voidkit/guide/permissions.html"><span class="vk-ico vk-ico-perm"></span>权限</a>
  <a href="/voidkit/anticheat/"><span class="vk-ico vk-ico-anticheat"></span>反作弊</a>
  <a href="/voidkit/audit/"><span class="vk-ico vk-ico-audit"></span>操作审计</a>
  <a href="/voidkit/panel/"><span class="vk-ico vk-ico-panel"></span>管理平台</a>
  <a href="/voidkit/dev/"><span class="vk-ico vk-ico-guide"></span>开发</a>
  <a href="/voidkit/guide/faq.html"><span class="vk-ico vk-ico-spark"></span>常见问题</a>
</div>

<div class="vk-end">
  MIT Licensed · 反馈与建议去 <a href="https://github.com/KokuuWork/voidkit/issues" target="_blank" rel="noopener">GitHub Issues</a>
</div>

</div>
