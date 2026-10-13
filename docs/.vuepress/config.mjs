import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'

/**
 * Voidkit 文档站配置（VuePress 2 + Vite）。
 *
 * ⚠️ 为什么是 `.mjs` 而不是 `.js`：
 *   本文件是 ESM，但**不能**靠根 package.json 的 `"type": "module"` 来声明 ——
 *   那个字段是全局的，会让仓库里**所有** `.js` 都变成 ES Module，
 *   从而砸掉 `hub/tools/fake-agent.js`（CommonJS，`require` 直接报错）和调用它的
 *   `hub/tools/smoke-test.sh`。VuePress 支持 `config.mjs`，用它显式声明 ESM 即可，
 *   仓库其余 `.js` 保持默认的 CommonJS。
 *
 * 部署形态：GitHub Pages 项目站点 —— https://kokuuwork.github.io/voidkit/
 * 由此 base 必须是 '/voidkit/'（少了这个前缀，所有静态资源 404）。
 */
export default defineUserConfig({
  // ⚠️ 项目站点的子路径，改仓库名时这里要一起改
  base: '/voidkit/',
  lang: 'zh-CN',
  title: 'Voidkit',
  description:
    '支持 NeoForge / Forge / Fabric 的高版本服务器模组套件（Minecraft 1.20.x–1.21.x），面向几百个 mod 的大型科技整合包',

  head: [
    // ⚠️ head 里的路径**不会**自动加 base，必须自己写全
    // 品牌规范：网站 favicon 用 icon.ico（视觉包 icons/ 下）
    ['link', { rel: 'icon', type: 'image/png', href: '/voidkit/images/icon.png' }],
    ['link', { rel: 'alternate icon', href: '/voidkit/images/favicon.ico' }],
    ['link', { rel: 'apple-touch-icon', href: '/voidkit/images/apple-touch-icon.png' }],
    // 品牌强调色 Indigo 500
    ['meta', { name: 'theme-color', content: '#4f46e5' }],
  ],

  bundler: viteBundler(),

  theme: defaultTheme({
    // 品牌规范：小于 96px 一律用图标版（导航栏就是这么小）
    logo: '/images/icon-128.png',
    // 右上角 "在 GitHub 上编辑此页"
    repo: 'KokuuWork/voidkit',
    docsDir: 'docs',
    editLink: true,
    editLinkText: '在 GitHub 上编辑此页',
    lastUpdated: true,
    lastUpdatedText: '最后更新',
    contributors: false,
    // 深浅色跟随系统
    colorMode: 'auto',
    colorModeSwitch: true,
    // 404 页
    notFound: ['页面不存在', '这个地址没有对应的文档'],

    navbar: [
      { text: '指南', link: '/guide/' },
      { text: '模块', link: '/modules/' },
      { text: '反作弊', link: '/anticheat/' },
      { text: '审计', link: '/audit/' },
      { text: '管理平台', link: '/panel/' },
      { text: '开发', link: '/dev/' },
    ],

    sidebar: {
      '/guide/': [
        {
          text: '指南',
          children: [
            '/guide/README.md',
            '/guide/install.md',
            '/guide/config.md',
            '/guide/commands.md',
            '/guide/permissions.md',
            '/guide/versions.md',
            '/guide/text.md',
            '/guide/architecture.md',
            '/guide/faq.md',
          ],
        },
      ],
      '/modules/': [
        {
          text: '模块总览',
          children: ['/modules/README.md'],
        },
        {
          text: '内核',
          children: ['/modules/core.md'],
        },
        {
          text: '插件',
          children: [
            '/modules/essentials.md',
            '/modules/display.md',
            '/modules/perm.md',
            '/modules/title.md',
            '/modules/economy.md',
            '/modules/utility.md',
            '/modules/web.md',
          ],
        },
      ],
      '/anticheat/': [
        {
          text: '反作弊（VoidkitGuard）',
          children: ['/anticheat/README.md'],
        },
      ],
      '/audit/': [
        {
          text: '审计（VoidkitInspector）',
          children: ['/audit/README.md'],
        },
      ],
      '/panel/': [
        {
          text: '管理平台（VoidkitHub）',
          children: ['/panel/README.md'],
        },
      ],
      '/dev/': [
        {
          text: '开发',
          children: ['/dev/README.md', '/dev/adding-a-feature.md', '/dev/porting.md'],
        },
      ],
    },
  }),
})
