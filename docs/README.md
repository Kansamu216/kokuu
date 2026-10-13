---
home: true
heroImage: /images/logo.svg
heroText: Voidkit
tagline: NeoForge 1.21.1 纯服务端模组套件 —— 装着就能用，不用担心几百个 mod 的整合包把服搞崩
actions:
  - text: 快速开始
    link: /guide/
    type: primary
  - text: 模块总览
    link: /modules/
    type: secondary
footer: MIT Licensed · 文档站由 VuePress 构建并部署于 GitHub Pages
---

<div class="vk-home">

<p class="vk-lead">
  给 NeoForge 服务器补齐 Bukkit / Essentials / CMI 那一类通用功能，外加
  <strong>独立发布的反作弊</strong>与<strong>操作审计</strong>组件，以及一个轻量管理平台。<br>
  玩家用原版客户端即可进服，零 mixin、构造期零 IO、周期任务默认保守。
</p>

<h2 class="vk-sec"><span class="vk-ico vk-ico-rocket"></span>三步跑起来</h2>

<div class="vk-steps">
  <div class="vk-step">
    <h4>构建</h4>
    <p>需要 JDK 21。也可以直接从 Releases 下载整版 zip。</p>
    <p><code>./build.sh build --continue</code></p>
  </div>
  <div class="vk-step">
    <h4>安装</h4>
    <p>把 jar 丢进服务端 <code>mods/</code>，<strong>最少只需要 voidkit_core</strong>，其余按需。</p>
    <p><code>mods/voidkit_core-1.0.0+mc1.21.1-neoforge.jar</code></p>
  </div>
  <div class="vk-step">
    <h4>配置</h4>
    <p>首次启动生成 <code>voidkit/</code>，<strong>所有配置文件都带中文注释</strong>，打开即可改。</p>
    <p><code>/voidkit reload</code> 生效，不用重启。</p>
  </div>
</div>

<h2 class="vk-sec"><span class="vk-ico vk-ico-spark"></span>五个能力</h2>

<div class="vk-grid">
  <div class="vk-card">
    <span class="vk-ico vk-ico-essentials"></span>
    <div>
      <h4>基础服务器功能</h4>
      <p>家 / 传送点 / TPA / 出生点 / 管理工具 / 信息查询，以及原版命令简写。缺哪个插件都退化成保守行为，不会崩。</p>
    </div>
  </div>
  <div class="vk-card">
    <span class="vk-ico vk-ico-display"></span>
    <div>
      <h4>展示层</h4>
      <p>MOTD、TAB、侧边栏记分牌、聊天栏格式、公告、多级菜单 —— 全部配置驱动，改完即生效。</p>
    </div>
  </div>
  <div class="vk-card">
    <span class="vk-ico vk-ico-anticheat"></span>
    <div>
      <h4>反作弊 · VoidkitGuard</h4>
      <p>客户端 Agent + 服务端校验。哈希锁、随机中途复检、蜜罐协议、静默取证，换号不换机照样被抓。</p>
    </div>
  </div>
  <div class="vk-card">
    <span class="vk-ico vk-ico-audit"></span>
    <div>
      <h4>操作审计 · VoidkitInspector</h4>
      <p>CoreProtect 风格查破坏，<strong>专为科技包设计</strong>：自动化产线默认不记，真正那次破坏才查得出来。</p>
    </div>
  </div>
  <div class="vk-card">
    <span class="vk-ico vk-ico-panel"></span>
    <div>
      <h4>管理平台 · VoidkitHub</h4>
      <p>单个 Go 二进制，把多台服务器的数据聚合成一个后台：总览、玩家、审计、反作弊、模块开关一键切换。</p>
    </div>
  </div>
  <div class="vk-card">
    <span class="vk-ico vk-ico-gear"></span>
    <div>
      <h4>大整合包兼容</h4>
      <p>零 mixin、构造期零 IO、命令简写逐条容错。已在 165 个 mod 的 SkyExplorer 实测通过。</p>
    </div>
  </div>
</div>

<h2 class="vk-sec"><span class="vk-ico vk-ico-core"></span>模块一览<small>每个都是独立 jar，可单独安装 / 卸载 / 发布</small></h2>

<div class="vk-mods">
  <a class="vk-mod" href="/voidkit/modules/core.html"><span class="vk-ico vk-ico-core"></span><span class="t"><strong>内核</strong><span>voidkit_core</span></span><span class="tag">必需</span></a>
  <a class="vk-mod" href="/voidkit/modules/essentials.html"><span class="vk-ico vk-ico-essentials"></span><span class="t"><strong>基础指令</strong><span>voidkit_essentials</span></span></a>
  <a class="vk-mod" href="/voidkit/modules/display.html"><span class="vk-ico vk-ico-display"></span><span class="t"><strong>展示层</strong><span>voidkit_display</span></span></a>
  <a class="vk-mod" href="/voidkit/modules/perm.html"><span class="vk-ico vk-ico-perm"></span><span class="t"><strong>权限</strong><span>voidkit_perm</span></span></a>
  <a class="vk-mod" href="/voidkit/modules/title.html"><span class="vk-ico vk-ico-title"></span><span class="t"><strong>称号</strong><span>voidkit_title</span></span></a>
  <a class="vk-mod" href="/voidkit/modules/economy.html"><span class="vk-ico vk-ico-economy"></span><span class="t"><strong>经济</strong><span>voidkit_economy</span></span></a>
  <a class="vk-mod" href="/voidkit/modules/utility.html"><span class="vk-ico vk-ico-utility"></span><span class="t"><strong>清扫 / 性能</strong><span>voidkit_utility</span></span></a>
  <a class="vk-mod" href="/voidkit/modules/web.html"><span class="vk-ico vk-ico-web"></span><span class="t"><strong>管理 API 桥</strong><span>voidkit_web</span></span></a>
  <a class="vk-mod" href="/voidkit/anticheat/"><span class="vk-ico vk-ico-anticheat"></span><span class="t"><strong>反作弊</strong><span>voidkit_guard</span></span><span class="tag">独立</span></a>
  <a class="vk-mod" href="/voidkit/audit/"><span class="vk-ico vk-ico-audit"></span><span class="t"><strong>操作审计</strong><span>voidkit_inspector</span></span><span class="tag">独立</span></a>
</div>

<h2 class="vk-sec"><span class="vk-ico vk-ico-panel"></span>管理平台<small>多服聚合成一个后台</small></h2>

<p class="vk-lead" style="text-align:left;max-width:none;margin-bottom:0.2rem">
  单个 Go 二进制，部署在小内存机器上即可。各服的 apiToken 只存在 Hub 上，
  浏览器只认 Hub 一个凭据 —— 配一台服就是普通面板，配多台就跨服汇总。
</p>

<figure class="vk-shot">
  <img src="./images/panel/panel-fleet.png" alt="VoidkitHub 总览页：在线服务器、玩家数、TPS 与智能提示">
  <figcaption>总览 —— 实时状态 + 主动指出需要关注的问题</figcaption>
</figure>

<div class="vk-shots">
  <figure class="vk-shot">
    <img src="./images/panel/panel-inspect.png" alt="智能审计页：按玩家聚合的条形图、破坏与放置占比、动作分布">
    <figcaption>智能审计 —— 聚合图 / 时间线 / 表格三种视图</figcaption>
  </figure>
  <figure class="vk-shot">
    <img src="./images/panel/panel-guard-console.png" alt="反作弊会话控制台：校验状态、可疑信号、机器码与一键处置">
    <figcaption>反作弊会话控制台 —— 一键截图 / 深扫 / 封禁</figcaption>
  </figure>
</div>

<p style="text-align:center;margin-top:1rem">
  <a href="/voidkit/panel/">查看管理平台完整说明 →</a>
</p>

<h2 class="vk-sec"><span class="vk-ico vk-ico-guide"></span>生态仓库</h2>

<div class="vk-repos">
  <div class="vk-repo">
    <strong><span class="vk-ico vk-ico-core"></span>voidkit</strong>
    <p>本仓库：内核 + 7 个插件 + Go 管理平台。</p>
    <span class="dep">KokuuWork/voidkit</span>
  </div>
  <div class="vk-repo">
    <strong><span class="vk-ico vk-ico-anticheat"></span>voidkit-guard-oss</strong>
    <p>反作弊：客户端 Agent + 服务端校验，哈希锁 / 中途复检 / 蜜罐协议。</p>
    <span class="dep">依赖 voidkit_core</span>
  </div>
  <div class="vk-repo">
    <strong><span class="vk-ico vk-ico-audit"></span>voidkit-inspector</strong>
    <p>操作审计：科技包友好的查破坏与智能回滚（预览 + 撤销）。</p>
    <span class="dep">依赖 voidkit_core</span>
  </div>
</div>

<hr>

<p style="text-align:center;color:var(--vp-c-text-3);font-size:0.88rem;margin-top:1.4rem">
  找不到想要的？看 <a href="/voidkit/guide/faq.html">常见问题</a>，或去
  <a href="https://github.com/KokuuWork/voidkit/issues" target="_blank" rel="noopener">GitHub Issues</a> 提一个。
</p>

</div>
