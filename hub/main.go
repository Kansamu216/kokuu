// Command voidkithub —— VoidkitCore 分布式管理平台的中心节点。
//
// 解决的是"一台面板管 N 台游戏服"这件事：
//
//	┌────────────┐   轮询 + 代理透传   ┌──────────────┐
//	│ 浏览器面板  │ ─────────────────▶ │  VoidkitHub    │
//	└────────────┘                    │ (小内存机器) │
//	                                  └──────┬───────┘
//	                          ┌──────────────┼──────────────┐
//	                     ┌────▼───┐    ┌─────▼──┐     ┌─────▼──┐
//	                     │ 服 A   │    │ 服 B   │     │ 服 C   │
//	                     │Agent   │    │Agent   │     │Agent   │
//	                     └────────┘    └────────┘     └────────┘
//
// Agent 就是各服里 voidkit 的 `remote-api` 模块（可在配置里关掉）。
// Hub 把各服的 apiToken 收在自己这里，浏览器只认 Hub 的一个令牌。
//
// 资源占用：单二进制、无数据库、状态就是一个 JSON 文件，常驻内存约 20MB 量级。
//
// 构建与运行：
//
//	go build -o voidkithub .
//	./voidkithub -listen 0.0.0.0:9000 -data ./hubdata
package main

import (
	"context"
	"embed"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
)

//go:embed web
var webAssets embed.FS

func main() {
	var (
		listen    = flag.String("listen", "", "Hub 监听地址（留空用配置文件，默认 0.0.0.0:9000）")
		dataDir   = flag.String("data", "./hubdata", "数据目录（存 config.json / state.json）")
		user      = flag.String("user", "", "面板登录用户名（留空用配置文件，首次运行默认 admin）")
		password  = flag.String("pass", "", "面板登录密码（留空则首次运行自动生成并打印）")
		token     = flag.String("token", "", "机器访问令牌，给脚本/监控用（留空则自动生成）")
		pollSecs  = flag.Int("poll", 0, "轮询间隔秒数（0 = 用配置文件的值）")
		timeout   = flag.Int("timeout", 0, "单台服务器探测超时秒数（0 = 用配置文件的值）")
		showToken = flag.Bool("show-token", false, "只打印当前面板令牌然后退出")
	)
	flag.Parse()

	store, err := OpenStore(*dataDir)
	if err != nil {
		log.Fatalf("打开数据目录失败：%v", err)
	}
	if err := store.UpdateSettings(*listen, *user, *password, *token, *pollSecs, *timeout); err != nil {
		log.Fatalf("写入配置失败：%v", err)
	}
	if *showToken {
		fmt.Println(store.Token())
		return
	}

	cfg := store.Config()

	// 日志同时写控制台和 <数据目录>/hub.log。
	// 既是运维习惯（服务该有日志文件），也解决了"用 start 拉起的窗口一闪而过、看不到错"的问题。
	logPath := filepath.Join(*dataDir, "hub.log")
	if logFile, err := os.OpenFile(logPath, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644); err == nil {
		log.SetOutput(io.MultiWriter(os.Stdout, logFile))
		defer logFile.Close()
	} else {
		log.Printf("无法写入日志文件 %s：%v", logPath, err)
	}

	agent := NewAgentClient(time.Duration(cfg.TimeoutSeconds) * time.Second)
	poller := NewPoller(
		store,
		agent,
		time.Duration(cfg.PollSeconds)*time.Second,
		time.Duration(cfg.TimeoutSeconds)*time.Second,
	)

	web, err := fs.Sub(webAssets, "web")
	if err != nil {
		log.Fatalf("内嵌面板资源缺失：%v", err)
	}
	hub := NewHub(store, agent, poller, web)

	// Ctrl+C / SIGTERM（systemd、docker stop）都能优雅退出
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	go poller.Run(ctx)

	server := &http.Server{
		Addr:              cfg.Listen,
		Handler:           hub.Handler(),
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	hubUser, hubPassword := store.Credentials()

	log.Printf("VoidkitHub 启动：http://%s", cfg.Listen)
	log.Printf("数据目录：%s", *dataDir)
	log.Printf("日志文件：%s", logPath)
	log.Printf("轮询：每 %d 秒一次，单台超时 %d 秒", cfg.PollSeconds, cfg.TimeoutSeconds)
	log.Printf("面板登录：用户名 %s  密码 %s", hubUser, hubPassword)
	log.Printf("机器令牌：%s（给脚本/监控用，面板登录用不上）", store.Token())
	if n := len(store.Servers()); n == 0 {
		log.Print("还没有受管服务器 —— 打开面板「服务器」页添加第一台")
	} else {
		log.Printf("已配置 %d 台受管服务器", n)
	}

	go func() {
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("监听失败：%v", err)
		}
	}()

	<-ctx.Done()
	log.Print("正在关闭…")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = server.Shutdown(shutdownCtx)
	log.Print("已停止")
}
