package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"syscall"
	"time"
)

// AgentClient 用统一的超时与错误策略调用各游戏服里 kokuucore 的 HTTP API。
//
// 只做两件事：探活抓指标（probe），以及把面板请求原样转发过去（call）。
type AgentClient struct {
	client  *http.Client
	timeout time.Duration
}

func NewAgentClient(timeout time.Duration) *AgentClient {
	transport := &http.Transport{
		DialContext:         (&net.Dialer{Timeout: 3 * time.Second, KeepAlive: 30 * time.Second}).DialContext,
		MaxIdleConns:        64,
		MaxIdleConnsPerHost: 4,
		IdleConnTimeout:     90 * time.Second,
	}
	return &AgentClient{
		timeout: timeout,
		client: &http.Client{
			Timeout:   timeout,
			Transport: transport,
			// 不跟随重定向：agent 地址就是最终地址，跟着跳有可能把令牌带到别的域
			CheckRedirect: func(*http.Request, []*http.Request) error {
				return http.ErrUseLastResponse
			},
		},
	}
}

// call 对某台服发一次请求，path 形如 "/api/stats"。
func (c *AgentClient) call(
	ctx context.Context,
	srv *Server,
	method, path, contentType string,
	body io.Reader,
) (*http.Response, error) {
	url := strings.TrimRight(srv.API, "/") + path
	req, err := http.NewRequestWithContext(ctx, method, url, body)
	if err != nil {
		return nil, err
	}
	if srv.Token != "" {
		req.Header.Set("X-Auth-Token", srv.Token)
	}
	req.Header.Set("Accept", "application/json")
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	return c.client.Do(req)
}

// probe 探活一台服并抓取总览需要的指标。
// 任何一步失败都返回一个 Online=false 且带可读原因的 AgentState，而不是 error ——
// 调用方（轮询器）不需要区分"成功/失败"，只要把结果存下来给面板看。
func (c *AgentClient) probe(ctx context.Context, srv *Server) *AgentState {
	st := &AgentState{CheckedAt: time.Now().UnixMilli()}

	start := time.Now()
	resp, err := c.call(ctx, srv, http.MethodGet, "/api/stats", "", nil)
	st.LatencyMs = time.Since(start).Milliseconds()
	if err != nil {
		st.Error = friendlyError(err)
		return st
	}
	defer resp.Body.Close()

	data, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode != http.StatusOK {
		st.Error = fmt.Sprintf("HTTP %d %s", resp.StatusCode, snippet(data))
		return st
	}

	var stats struct {
		Online        int     `json:"online"`
		MaxPlayers    int     `json:"maxPlayers"`
		TotalProfiles int     `json:"totalProfiles"`
		UptimeMs      int64   `json:"uptimeMs"`
		AvgTps        float64 `json:"avgTps"`
		MemUsedMb     int64   `json:"memoryUsedMb"`
		MemMaxMb      int64   `json:"memoryMaxMb"`
		Version       string  `json:"version"`
	}
	if err := json.Unmarshal(data, &stats); err != nil {
		st.Error = "返回内容不是合法 JSON（是不是把端口指到别的东西上了？）"
		return st
	}

	st.Online = true
	st.Error = ""
	st.Players = stats.Online
	st.MaxPlayers = stats.MaxPlayers
	st.Profiles = stats.TotalProfiles
	st.UptimeMs = stats.UptimeMs
	st.AvgTps = stats.AvgTps
	st.MemUsedMb = stats.MemUsedMb
	st.MemMaxMb = stats.MemMaxMb
	st.Minecraft = stats.Version

	// 模块数失败不影响"在线"判定，静默忽略
	if on, all, err := c.countFeatures(ctx, srv); err == nil {
		st.FeaturesOn, st.FeaturesAll = on, all
	}
	if ver, err := c.modVersion(ctx, srv); err == nil {
		st.ModVersion = ver
	}
	return st
}

func (c *AgentClient) countFeatures(ctx context.Context, srv *Server) (int, int, error) {
	resp, err := c.call(ctx, srv, http.MethodGet, "/api/features", "", nil)
	if err != nil {
		return 0, 0, err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if resp.StatusCode != http.StatusOK {
		return 0, 0, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	var payload struct {
		Features []struct {
			Enabled bool `json:"enabled"`
		} `json:"features"`
	}
	if err := json.Unmarshal(data, &payload); err != nil {
		return 0, 0, err
	}
	on := 0
	for _, f := range payload.Features {
		if f.Enabled {
			on++
		}
	}
	return on, len(payload.Features), nil
}

func (c *AgentClient) modVersion(ctx context.Context, srv *Server) (string, error) {
	resp, err := c.call(ctx, srv, http.MethodGet, "/api/health", "", nil)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	var payload struct {
		Version string `json:"version"`
	}
	if err := json.Unmarshal(data, &payload); err != nil {
		return "", err
	}
	return payload.Version, nil
}

// friendlyError 把常见的网络错误翻译成人能看懂的原因，
// 因为面板上"connection refused"对开服的人没什么帮助。
func friendlyError(err error) string {
	if err == nil {
		return ""
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return "连接超时（检查防火墙 / 端口是否放行）"
	}
	if errors.Is(err, syscall.ECONNREFUSED) {
		return "连接被拒绝（服务端没启动，或端口不对）"
	}
	var dnsErr *net.DNSError
	if errors.As(err, &dnsErr) {
		return "域名解析失败：" + dnsErr.Name
	}
	msg := err.Error()
	switch {
	case strings.Contains(msg, "connection refused"):
		return "连接被拒绝（服务端没启动，或端口不对）"
	case strings.Contains(msg, "i/o timeout"):
		return "连接超时（检查防火墙 / 端口是否放行）"
	case strings.Contains(msg, "no such host"):
		return "域名解析失败"
	case strings.Contains(msg, "certificate"):
		return "TLS 证书校验失败（HTTPS 地址配错？）"
	}
	return msg
}

func snippet(data []byte) string {
	s := strings.TrimSpace(string(data))
	if len(s) > 120 {
		return s[:120] + "…"
	}
	return s
}
