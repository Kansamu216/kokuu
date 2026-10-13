---
home: true
heroImage: /images/logo.svg
heroText: Voidkit
tagline: NeoForge 1.21.1 纯服务端模组套件
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

<div class="vk-facts">
  <span><span class="vk-ico vk-ico-core"></span>v1.0.0 正式发布</span>
  <span><span class="vk-ico vk-ico-display"></span>NeoForge 1.21.1</span>
  <span><span class="vk-ico vk-ico-essentials"></span>原版客户端可进服</span>
  <span><span class="vk-ico vk-ico-spark"></span>165 mod 整合包实测</span>
  <span><span class="vk-ico vk-ico-gear"></span>零 mixin · 低耦合</span>
</div>

<p class="vk-lead">
  用服务端 mod 补齐 Bukkit / Essentials / CMI 那一类通用服务器功能，外加独立的
  <strong>反作弊</strong>与<strong>操作审计</strong>组件，和一个轻量管理平台。<br>
  目标是：装上就能用，用着舒服，不会因为几百个 mod 的环境而出问题。
</p>

<h2 class="vk-sec"><span class="vk-ico vk-ico-spark"></span>四大能力</h2>

<div class="vk-pillars">
  <div class="vk-pillar">
    <span class="vk-ico vk-ico-essentials"></span>
    <h3>基础服务器功能</h3>
    <p class="one">Bukkit 生态那一套，在 NeoForge 上补齐。缺哪个插件都退化成保守行为，不会崩。</p>
    <ul>
      <li>家 / 传送点 / 出生点 / TPA，请求自带可点按钮</li>
      <li>管理工具与信息查询，原版命令简写逐条容错</li>
      <li>权限 / 称号 / 经济 / 商店 / 清扫垃圾桶</li>
    </ul>
    <a class="more" href="/voidkit/modules/">查看全部模块 →</a>
  </div>
  <div class="vk-pillar">
    <span class="vk-ico vk-ico-anticheat"></span>
    <h3>反作弊 · VoidkitGuard</h3>
    <p class="one">客户端 Agent + 服务端校验。改客户端也没用，主刃在服务端。</p>
    <ul>
      <li>哈希锁 + 随机中途复检，开局后作弊也现形</li>
      <li>蜜罐协议 + 综合判定，静默取证不暴露细节</li>
      <li>机器码封禁换号无效，多服联防同步封禁</li>
    </ul>
    <a class="more" href="/voidkit/anticheat/">反作弊详情 →</a>
  </div>
  <div class="vk-pillar">
    <span class="vk-ico vk-ico-audit"></span>
    <h3>操作审计 · VoidkitInspector</h3>
    <p class="one">CoreProtect 风格查破坏，专为科技整合包设计 —— 产线噪音不进库。</p>
    <ul>
      <li>来源分类：机械臂 / 管道 / 假玩家默认不记</li>
      <li>箱子 GUI 查询，悬停看「谁 / 何时 / 动作 / 坐标」</li>
      <li>智能回滚：分类感知 + 预览 + 可撤销</li>
    </ul>
    <a class="more" href="/voidkit/audit/">审计详情 →</a>
  </div>
  <div class="vk-pillar">
    <span class="vk-ico vk-ico-panel"></span>
    <h3>管理平台 · VoidkitHub</h3>
    <p class="one">单个 Go 二进制，把多台服务器的数据聚合成一个后台，浏览器直接访问。</p>
    <ul>
      <li>总览 / 玩家 / 日志 / 模块开关，一键操作</li>
      <li>智能审计聚合图 + 反作弊会话控制台</li>
      <li>令牌只存在 Hub 上，浏览器只认一个凭据</li>
    </ul>
    <a class="more" href="/voidkit/panel/">管理平台详情 →</a>
  </div>
</div>

<h2 class="vk-sec"><span class="vk-ico vk-ico-rocket"></span>快速上手</h2>

<div class="vk-band">
  <div class="vk-steps">
    <div class="vk-step">
      <h4>构建</h4>
      <p>需要 JDK 21；也可以直接下载 Releases 整版 zip。</p>
      <p><code>./build.sh build --continue</code></p>
    </div>
    <div class="vk-step">
      <h4>安装</h4>
      <p>把 jar 丢进服务端 <code>mods/</code>。<strong>最少只需 voidkit_core</strong>，其余按需。</p>
      <p><code>voidkit_core-1.0.0+mc1.21.1-neoforge.jar</code></p>
    </div>
    <div class="vk-step">
      <h4>配置</h4>
      <p>首次启动生成 <code>voidkit/</code>，<strong>配置文件全带中文注释</strong>。</p>
      <p><code>/voidkit reload</code> 生效，不用重启。</p>
    </div>
  </div>
</div>

<h2 class="vk-sec"><span class="vk-ico vk-ico-gear"></span>为什么适合大型整合包</h2>

<div class="vk-fits">
  <span class="vk-fit"><span class="vk-ico vk-ico-anticheat"></span>零 mixin，不与整合包抢类改写</span>
  <span class="vk-fit"><span class="vk-ico vk-ico-core"></span>构造期零 IO，不拖慢启动</span>
  <span class="vk-fit"><span class="vk-ico vk-ico-essentials"></span>命令简写逐条容错，不整批失败</span>
  <span class="vk-fit"><span class="vk-ico vk-ico-utility"></span>周期任务默认保守，全部可配可关</span>
  <span class="vk-fit"><span class="vk-ico vk-ico-spark"></span>165 mod 整合包实测通过</span>
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

<h2 class="vk-sec"><span class="vk-ico vk-ico-panel"></span>管理平台长什么样</h2>

<p class="vk-lead" style="text-align:left;max-width:none;margin-bottom:0.2rem">
  实时状态、智能提示、聚合图表、一键处置 —— 浏览器打开即用。
</p>

<figure class="vk-shot">
  <img src="./images/panel/panel-fleet.png" alt="VoidkitHub 总览页：在线服务器、玩家数、TPS 与智能提示">
  <figcaption>总览 —— 实时状态 + 主动指出需要关注的问题</figcaption>
</figure>

<p style="text-align:center">
  <a href="/voidkit/panel/">更多界面截图与部署说明 →</a>
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
    <p>反作弊：客户端 Agent + 服务端校验。</p>
    <span class="dep">依赖 voidkit_core</span>
  </div>
  <div class="vk-repo">
    <strong><span class="vk-ico vk-ico-audit"></span>voidkit-inspector</strong>
    <p>操作审计：科技包友好的查破坏与智能回滚。</p>
    <span class="dep">依赖 voidkit_core</span>
  </div>
</div>

<hr>

<p style="text-align:center;color:var(--vp-c-text-3);font-size:0.88rem;margin-top:1.4rem">
  找不到想要的？看 <a href="/voidkit/guide/faq.html">常见问题</a>，或去
  <a href="https://github.com/KokuuWork/voidkit/issues" target="_blank" rel="noopener">GitHub Issues</a> 提一个。
</p>

</div>
