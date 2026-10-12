// Command voidkitpanel —— VoidkitCore 管理面板的独立服务器。
//
// 和 Python 版 serve.py 的目标一样：把 panel/ 这堆静态文件对外提供，并顺手
// 把模组的 /api 反向代理进来。区别在于它编译成**单个可执行文件**：
//
//   - 前端资源用 go:embed 打进去，分发只有一个文件，目标机不需要装任何运行时
//   - 同源反代解决跨域（浏览器视角只有面板这一个源），也能顺带注入 API 令牌，
//     让面板使用者不必知道令牌
//   - 可选内置 HTTP Basic 登录，避免面板直接裸奔在公网
//
// 构建：
//
//	cd panel && go build -o voidkitpanel .
//
// 运行：
//
//	./voidkitpanel                                     # 面板 :8766 → API 127.0.0.1:8765
//	./voidkitpanel -listen 0.0.0.0:8080 -user admin -pass '***' -token '<apiToken>'
package main

import (
	"crypto/subtle"
	"embed"
	"encoding/json"
	"flag"
	"io/fs"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"os/signal"
	"path"
	"strconv"
	"strings"
	"syscall"
	"time"
)

//go:embed index.html style.css app.js
var assets embed.FS

func main() {
	var (
		listen  = flag.String("listen", "127.0.0.1:8766", "面板监听地址")
		apiAddr = flag.String("api", "http://127.0.0.1:8765", "模组管理 API 地址")
		user    = flag.String("user", "", "面板登录用户名（留空则不启用面板登录）")
		pass    = flag.String("pass", "", "面板登录密码")
		token   = flag.String("token", "", "API 访问令牌：设置后由本进程注入，面板使用者无需知道")
	)
	flag.Parse()

	target, err := url.Parse(*apiAddr)
	if err != nil || target.Host == "" {
		log.Fatalf("无效的 -api 地址 %q：%v", *apiAddr, err)
	}

	proxy := newProxy(target, *token)

	mux := http.NewServeMux()
	mux.Handle("/api", proxy)
	mux.Handle("/api/", proxy)
	mux.Handle("/", staticHandler())

	handler := withAuth(mux, *user, *pass)
	if *user == "" {
		log.Print("警告：未启用面板登录（-user 为空），若监听公网地址请务必设置 -user/-pass")
	}

	server := &http.Server{
		Addr:    *listen,
		Handler: handler,
		// 面板是内网工具，超时给紧一点，顺手挡掉 Slowloris 类连接占用
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       30 * time.Second,
		WriteTimeout:      60 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	log.Printf("VoidkitCore 管理面板: http://%s", *listen)
	log.Printf("反向代理目标 API: %s", target)
	if *token != "" {
		log.Print("已启用令牌注入：面板端无需填写访问令牌")
	}

	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("监听失败：%v", err)
		}
	}()

	// 优雅退出，避免面板正在写配置时被硬杀
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)
	<-stop

	log.Print("正在关闭…")
	shutdownDone := make(chan struct{})
	go func() {
		_ = server.Close()
		close(shutdownDone)
	}()
	select {
	case <-shutdownDone:
	case <-time.After(5 * time.Second):
	}
	log.Print("已停止")
}

// newProxy 把 /api/* 转发给模组的管理 API。
//
// token 非空时会**覆盖**请求头里的 X-Auth-Token —— 这样面板端可以填任意占位字符串，
// 真正的令牌只留在本进程的命令行里，不必发给每个使用者。
func newProxy(target *url.URL, token string) *httputil.ReverseProxy {
	proxy := httputil.NewSingleHostReverseProxy(target)
	director := proxy.Director

	proxy.Director = func(r *http.Request) {
		director(r)
		r.Host = target.Host
		if token != "" {
			r.Header.Del("Authorization")
			r.Header.Set("X-Auth-Token", token)
		}
	}

	proxy.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
		writeJSON(w, http.StatusBadGateway, map[string]any{
			"ok":      false,
			"error":   "upstream_unreachable",
			"message": "无法连接服务端 API（" + target.String() + "）：" + err.Error(),
		})
	}
	return proxy
}

// staticHandler 提供内嵌的前端资源。
func staticHandler() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}

		// 直接用 fs.ReadFile 而不是 http.FileServer：后者对 "/index.html"
		// 会回 301 重定向到 "./"（它认为两者是同一个资源），
		// 而这里希望访问 "/" 直接拿到页面，少一次跳转。
		name := strings.TrimPrefix(path.Clean(r.URL.Path), "/")
		if name == "" || name == "." {
			name = "index.html"
		}

		data, err := fs.ReadFile(assets, name)
		if err != nil {
			http.NotFound(w, r)
			return
		}

		// 面板要能立刻反映最新配置，禁掉缓存省得使用者手动强刷
		w.Header().Set("Content-Type", contentTypeOf(name))
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Length", strconv.Itoa(len(data)))
		if r.Method == http.MethodHead {
			w.WriteHeader(http.StatusOK)
			return
		}
		_, _ = w.Write(data)
	})
}

func contentTypeOf(name string) string {
	switch strings.ToLower(path.Ext(name)) {
	case ".html", ".htm":
		return "text/html; charset=utf-8"
	case ".css":
		return "text/css; charset=utf-8"
	case ".js", ".mjs":
		return "text/javascript; charset=utf-8"
	case ".json":
		return "application/json; charset=utf-8"
	case ".svg":
		return "image/svg+xml"
	case ".png":
		return "image/png"
	case ".ico":
		return "image/x-icon"
	default:
		return "application/octet-stream"
	}
}

// withAuth 可选的 HTTP Basic 登录；user 为空则直接放行。
func withAuth(next http.Handler, user, pass string) http.Handler {
	if user == "" {
		return next
	}
	expectUser := []byte(user)
	expectPass := []byte(pass)

	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotUser, gotPass, ok := r.BasicAuth()
		// 恒定时间比较，避免通过响应时间猜密码
		userOK := subtle.ConstantTimeCompare([]byte(gotUser), expectUser) == 1
		passOK := subtle.ConstantTimeCompare([]byte(gotPass), expectPass) == 1
		if !ok || !userOK || !passOK {
			w.Header().Set("WWW-Authenticate", `Basic realm="VoidkitCore", charset="UTF-8"`)
			http.Error(w, "需要登录", http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}
