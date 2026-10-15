# Voidkit 发帖截图清单（MCP 自动采集）

> 目标：为 mcmod / MCBBS / GitHub 发帖准备实机截图。
> 采集方式：`minecraft-mod-mcp` 的 `screenshot_to_file`（AI 驱动客户端，无需手动截屏）。
> 输出目录：`docs/release/images/shots/`（最终挑图后进图集）

## 一、必须有的 6 张（核心卖点）

| # | 文件名 | 画面内容 | 怎么触发 | 对应卖点 |
|---|---|---|---|---|
| 1 | `01-tab-list.png` | TAB 玩家列表（自定义格式：组名/等级/称号） | 多人时按 Tab，单人也能开 | 显示模块 · TAB |
| 2 | `02-scoreboard.png` | 右侧记分牌（行标题、玩家名、分数/延迟） | 站在原地即可 | 显示模块 · 记分牌 |
| 3 | `03-chat-format.png` | 聊天栏：他人发言 + 自己发言（`&` 颜色 + `%变量%`） | 用 MCP `type_text` 打两行聊天 | **修过的 bug**：不再是"全是变量" |
| 4 | `04-menu-gui.png` | `/menu` 打开的箱子菜单（Voidkit 自绘） | 客户端执行 `/menu` | 菜单 GUI · 零配置 |
| 5 | `05-hud-status.png` | 无特殊 HUD 时不截；改截 `/vk status` 回执 | 执行 `/vk status` | 内核 23 模块在线 |
| 6 | `06-hub-panel.png` | hub 管理面板（modules 页 / fleet 页） | 浏览器开 hub | 管理后台（**已有现成图**，见 docs/.vuepress/dist/assets/panel-*.png） |

## 二、值得有的 4 张（功能纵深）

| # | 文件名 | 画面内容 | 怎么触发 | 对应卖点 |
|---|---|---|---|---|
| 7 | `07-home-tpa.png` | `/sethome` → `/home` 传送后的实景 + 聊天回执 | MCP 连续发两条命令 | 家 / 传送（补缺口的关键证据） |
| 8 | `08-economy.png` | `/balance` `/pay` 的回执（余额变动前后） | MCP 发命令 | 经济模块 |
| 9 | `09-perm.png` | `/perm` 列表或拒绝提示（非 OP 视角） | 用非 OP 身份跑 | 权限模块 |
| 10 | `10-mcp-overlay.png` | MCP 调试 overlay 开着的样子 | bridge 默认开 | AI 可驱动的证据（可选） |

## 三、截图规格

- 分辨率：**1280×720**（发帖够用；`--width 1280 --height 720` 启动客户端）
- 隐藏 GUI：`F1` 或关掉 HUD，避免干扰主体
- 命名：`NN-描述.png`，两位数字排序
- 每张截完**当场看一眼**——确认主体在画面里、不是黑屏/暂停菜单

## 四、执行顺序建议

1. 客户端进服（`launch_minecraft` + 自动连 localhost:25565）
2. 先截「静态展示类」（1、2）——站着不动就能拍
3. 再截「操作类」（3、4、5）——用 `type_text` 打命令，等回执出现再拍
4. 最后截「流程类」（7、8、9）——需要连续命令 + 状态流转
5. hub 面板用现成的（`docs/release/images/panel-overview.png`、`panel-modules.png`）

## 五、注意事项

- 🚨 切窗口会触发**暂停菜单**，MCP 指令失效 → 截图前用 overlay 的 **release mouse** 或 `Esc` 暂停菜单释放鼠标
- 🚨 截图必须走 `screenshot_to_file`（存盘），不要用只返回 base64 的 `screenshot` —— 视觉分析要读文件
- 服务端别用 `verify-installed-server.sh`（它收尾会杀服），要常驻
