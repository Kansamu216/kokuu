package main

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"io/fs"
	"net/http"
	"net/url"
	"path"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	apiPrefix   = "/hub/api/"
	proxyPrefix = "/hub/proxy/"
	maxBodySize = 1 << 20
	maxRespSize = 8 << 20
)

// Hub 把 HTTP 处理收在一起。它本身不存状态，状态都在 Store 里。
type Hub struct {
	store    *Store
	agent    *AgentClient
	poller   *Poller
	user     string
	password string
	token    string
	web      fs.FS
}

func NewHub(store *Store, agent *AgentClient, poller *Poller, web fs.FS) *Hub {
	user, password := store.Credentials()
	return &Hub{
		store:    store,
		agent:    agent,
		poller:   poller,
		user:     user,
		password: password,
		token:    store.Token(),
		web:      web,
	}
}

func (h *Hub) Handler() http.Handler {
	mux := http.NewServeMux()
	// 健康检查不鉴权，方便外部 uptime 监控直接探
	mux.HandleFunc("/hub/api/health", h.handleHealth)
	// /hub/api/* 与 /hub/proxy/* 都要 Hub 令牌
	mux.Handle(apiPrefix, h.auth(http.HandlerFunc(h.handleAPI)))
	mux.Handle(proxyPrefix, h.auth(http.HandlerFunc(h.handleProxy)))
	// 面板本体：页面里不含任何密钥（令牌由使用者填写），所以不鉴权
	mux.Handle("/", h.static())
	return cors(mux)
}

// ---------- 中间件 ----------

func cors(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		header := w.Header()
		header.Set("Access-Control-Allow-Origin", "*")
		header.Set("Access-Control-Allow-Headers", "authorization,content-type,x-hub-token,x-auth-token")
		header.Set("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// auth 接受两种凭据：
//  1. 面板登录：HTTP Basic（用户名 + 密码），这是给人用的
//  2. 机器令牌：X-Hub-Token / Bearer，这是给脚本、监控、CI 用的
//
// 两者都是恒定时间比较，避免通过响应时间猜口令。
func (h *Hub) auth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if h.credentialsOK(r) {
			next.ServeHTTP(w, r)
			return
		}
		// 带上 WWW-Authenticate，浏览器会弹原生登录框（用 Basic 时体验更顺）
		w.Header().Set("WWW-Authenticate", `Basic realm="KokuuHub", charset="UTF-8"`)
		writeError(w, http.StatusUnauthorized, "unauthorized", "用户名或密码不正确（也可用 Hub 令牌访问）")
	})
}

func (h *Hub) credentialsOK(r *http.Request) bool {
	// ---- Basic：面板登录 ----
	if user, pass, ok := r.BasicAuth(); ok {
		userOK := subtle.ConstantTimeCompare([]byte(user), []byte(h.user)) == 1
		passOK := subtle.ConstantTimeCompare([]byte(pass), []byte(h.password)) == 1
		if userOK && passOK {
			return true
		}
	}

	// ---- 令牌：脚本 / 监控 ----
	token := strings.TrimSpace(r.Header.Get("X-Hub-Token"))
	if token == "" {
		if auth := r.Header.Get("Authorization"); len(auth) > 7 && strings.EqualFold(auth[:7], "bearer ") {
			token = strings.TrimSpace(auth[7:])
		}
	}
	return token != "" && subtle.ConstantTimeCompare([]byte(token), []byte(h.token)) == 1
}

// ---------- 静态资源 ----------

func (h *Hub) static() http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet && r.Method != http.MethodHead {
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}
		name := strings.TrimPrefix(path.Clean(r.URL.Path), "/")
		if name == "" || name == "." {
			name = "index.html"
		}
		data, err := fs.ReadFile(h.web, name)
		if err != nil {
			http.NotFound(w, r)
			return
		}
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

// ---------- 基础接口 ----------

func (h *Hub) handleHealth(w http.ResponseWriter, r *http.Request) {
	cfg := h.store.Config()
	servers := h.store.Servers()
	online := 0
	for _, srv := range servers {
		if st := h.store.State(srv.ID); st != nil && st.Online {
			online++
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":            true,
		"service":       "kokuuhub",
		"servers":       len(servers),
		"serversOnline": online,
		"pollSeconds":   cfg.PollSeconds,
	})
}

func (h *Hub) handleAPI(w http.ResponseWriter, r *http.Request) {
	rest := strings.Trim(strings.TrimPrefix(r.URL.Path, apiPrefix), "/")
	var parts []string
	if rest != "" {
		parts = strings.Split(rest, "/")
	}

	switch {
	case len(parts) == 1 && parts[0] == "servers":
		switch r.Method {
		case http.MethodGet:
			h.listServers(w)
		case http.MethodPost:
			h.saveServer(w, r, "")
		default:
			methodNotAllowed(w)
		}
	case len(parts) == 2 && parts[0] == "servers":
		switch r.Method {
		case http.MethodPost:
			h.saveServer(w, r, parts[1])
		case http.MethodDelete:
			h.deleteServer(w, parts[1])
		default:
			methodNotAllowed(w)
		}
	case len(parts) == 3 && parts[0] == "servers" && parts[2] == "delete":
		h.deleteServer(w, parts[1])
	case len(parts) == 3 && parts[0] == "servers" && parts[2] == "probe":
		h.probeServer(w, r, parts[1])
	case len(parts) == 1 && parts[0] == "fleet":
		h.fleet(w)
	case len(parts) == 1 && parts[0] == "players":
		h.searchPlayers(w, r)
	default:
		writeError(w, http.StatusNotFound, "not_found", "未知接口: "+r.URL.Path)
	}
}

// ---------- 受管服务器 CRUD ----------

func (h *Hub) listServers(w http.ResponseWriter) {
	servers := h.store.PublicServers()
	list := make([]map[string]any, 0, len(servers))
	for _, srv := range servers {
		id, _ := srv["id"].(string)
		entry := map[string]any{
			"id":       id,
			"name":     srv["name"],
			"api":      srv["api"],
			"tags":     srv["tags"],
			"note":     srv["note"],
			"enabled":  srv["enabled"],
			"hasToken": srv["hasToken"],
		}
		if st := h.store.State(id); st != nil {
			entry["state"] = st
		}
		list = append(list, entry)
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":          true,
		"pollSeconds": h.store.PollSeconds(),
		"servers":     list,
	})
}

type serverPayload struct {
	Name    string   `json:"name"`
	API     string   `json:"api"`
	Token   string   `json:"token"`
	Tags    []string `json:"tags"`
	Note    string   `json:"note"`
	Enabled *bool    `json:"enabled"`
}

func (h *Hub) saveServer(w http.ResponseWriter, r *http.Request, id string) {
	body, err := readBody(r)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	var payload serverPayload
	if err := json.Unmarshal(body, &payload); err != nil {
		writeError(w, http.StatusBadRequest, "bad_json", "请求体不是合法 JSON")
		return
	}

	enabled := true
	if id != "" {
		// 更新：没传 enabled 就沿用原值，避免面板漏传字段把服务器"改成停用"
		if existing, ok := h.store.Server(id); ok {
			enabled = existing.Enabled
		}
	}
	if payload.Enabled != nil {
		enabled = *payload.Enabled
	}

	saved, err := h.store.Upsert(&Server{
		ID:      id,
		Name:    payload.Name,
		API:     payload.API,
		Token:   payload.Token,
		Tags:    payload.Tags,
		Note:    payload.Note,
		Enabled: enabled,
	})
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}

	// 保存完立刻探一次，面板上马上就能看到"通/不通"，不用等下一轮轮询
	ctx, cancel := context.WithTimeout(r.Context(), h.agent.timeout+2*time.Second)
	defer cancel()
	state, _ := h.poller.PollOne(ctx, saved.ID)

	writeJSON(w, http.StatusOK, map[string]any{
		"ok":     true,
		"server": map[string]any{"id": saved.ID, "name": saved.Name, "api": saved.API},
		"state":  state,
	})
}

func (h *Hub) deleteServer(w http.ResponseWriter, id string) {
	if err := h.store.Delete(id); err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true})
}

func (h *Hub) probeServer(w http.ResponseWriter, r *http.Request, id string) {
	ctx, cancel := context.WithTimeout(r.Context(), h.agent.timeout+2*time.Second)
	defer cancel()

	state, err := h.poller.PollOne(ctx, id)
	if err != nil {
		writeError(w, http.StatusBadRequest, "bad_request", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "state": state})
}

// ---------- 舰队总览 ----------

type FleetItem struct {
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Tags        []string `json:"tags"`
	Enabled     bool     `json:"enabled"`
	Online      bool     `json:"online"`
	Error       string   `json:"error"`
	LatencyMs   int64    `json:"latencyMs"`
	CheckedAt   int64    `json:"checkedAt"`
	Players     int      `json:"players"`
	MaxPlayers  int      `json:"maxPlayers"`
	AvgTps      float64  `json:"avgTps"`
	MemUsedMb   int64    `json:"memUsedMb"`
	MemMaxMb    int64    `json:"memMaxMb"`
	UptimeMs    int64    `json:"uptimeMs"`
	Minecraft   string   `json:"minecraft"`
	ModVersion  string   `json:"modVersion"`
	FeaturesOn  int      `json:"featuresOn"`
	FeaturesAll int      `json:"featuresAll"`
	Profiles    int      `json:"profiles"`
}

func (h *Hub) fleet(w http.ResponseWriter) {
	servers := h.store.Servers()

	items := make([]FleetItem, 0, len(servers))
	onlineServers, offlineServers, disabled := 0, 0, 0
	totalPlayers, totalMax, profiles := 0, 0, 0
	var tpsSum float64
	tpsCount := 0

	for _, srv := range servers {
		item := FleetItem{ID: srv.ID, Name: srv.Name, Tags: srv.Tags, Enabled: srv.Enabled}
		if !srv.Enabled {
			disabled++
			item.Error = "已停用（不参与轮询）"
			items = append(items, item)
			continue
		}

		st := h.store.State(srv.ID)
		if st == nil {
			item.Error = "尚未探测"
			items = append(items, item)
			continue
		}

		item.Online = st.Online
		item.Error = st.Error
		item.LatencyMs = st.LatencyMs
		item.CheckedAt = st.CheckedAt
		item.Players = st.Players
		item.MaxPlayers = st.MaxPlayers
		item.AvgTps = st.AvgTps
		item.MemUsedMb = st.MemUsedMb
		item.MemMaxMb = st.MemMaxMb
		item.UptimeMs = st.UptimeMs
		item.Minecraft = st.Minecraft
		item.ModVersion = st.ModVersion
		item.FeaturesOn = st.FeaturesOn
		item.FeaturesAll = st.FeaturesAll
		item.Profiles = st.Profiles

		if st.Online {
			onlineServers++
			totalPlayers += st.Players
			totalMax += st.MaxPlayers
			profiles += st.Profiles
			if st.AvgTps > 0 {
				tpsSum += st.AvgTps
				tpsCount++
			}
		} else {
			offlineServers++
		}
		items = append(items, item)
	}

	// 在线的排前面，其次按人数多的排
	sort.SliceStable(items, func(i, j int) bool {
		if items[i].Enabled != items[j].Enabled {
			return items[i].Enabled
		}
		if items[i].Online != items[j].Online {
			return items[i].Online
		}
		return items[i].Players > items[j].Players
	})

	avgTps := 0.0
	if tpsCount > 0 {
		avgTps = tpsSum / float64(tpsCount)
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"ok":              true,
		"servers":         len(servers),
		"onlineServers":   onlineServers,
		"offlineServers":  offlineServers,
		"disabledServers": disabled,
		"players":         totalPlayers,
		"maxPlayers":      totalMax,
		"profiles":        profiles,
		"avgTps":          avgTps,
		"pollSeconds":     h.store.PollSeconds(),
		"items":           items,
	})
}

// ---------- 跨服玩家搜索 ----------

type PlayerOccurrence struct {
	ServerID        string `json:"serverId"`
	Server          string `json:"server"`
	Online          bool   `json:"online"`
	LastIp          string `json:"lastIp"`
	Group           string `json:"group"`
	PlaytimeSeconds int64  `json:"playtimeSeconds"`
	JoinCount       int    `json:"joinCount"`
	LastSeen        int64  `json:"lastSeen"`
}

type MergedPlayer struct {
	UUID                 string             `json:"uuid"`
	Name                 string             `json:"name"`
	Online               bool               `json:"online"`
	OnlineOn             []string           `json:"onlineOn"`
	TotalPlaytimeSeconds int64              `json:"totalPlaytimeSeconds"`
	TotalJoinCount       int                `json:"totalJoinCount"`
	LastSeen             int64              `json:"lastSeen"`
	Occurrences          []PlayerOccurrence `json:"occurrences"`
}

// agentPlayer 对应模组 /api/players 返回的单条记录（字段名见 ApiDefaults.summary）。
type agentPlayer struct {
	UUID            string `json:"uuid"`
	Name            string `json:"name"`
	Online          bool   `json:"online"`
	LastIp          string `json:"lastIp"`
	LastSeen        int64  `json:"lastSeen"`
	PlaytimeSeconds int64  `json:"playtimeSeconds"`
	JoinCount       int    `json:"joinCount"`
	Group           string `json:"group"`
}

// searchPlayers 向所有【在线】的服并发查询，再按 uuid 合并。
//
// 只查在线服是刻意的：一个玩家的存档可能散在 5 台服上，但离线服的请求会白等一个超时，
// 面板上体验很差。离线服的玩家可以通过先选中那台服再查（走代理由前端完成）。
func (h *Hub) searchPlayers(w http.ResponseWriter, r *http.Request) {
	keyword := strings.TrimSpace(r.URL.Query().Get("q"))
	onlineOnly := r.URL.Query().Get("online") == "true"
	limit := 50
	if n, err := strconv.Atoi(r.URL.Query().Get("limit")); err == nil && n > 0 && n <= 500 {
		limit = n
	}

	type failure struct {
		Server  string `json:"server"`
		Message string `json:"message"`
	}

	var mu sync.Mutex
	var wg sync.WaitGroup
	occurrences := map[string]*MergedPlayer{}
	failures := make([]failure, 0)

	for _, srv := range h.store.Servers() {
		if !srv.Enabled {
			continue
		}
		st := h.store.State(srv.ID)
		if st == nil || !st.Online {
			continue
		}

		wg.Add(1)
		go func(s *Server) {
			defer wg.Done()

			params := url.Values{}
			params.Set("limit", strconv.Itoa(limit))
			if keyword != "" {
				params.Set("q", keyword)
			}
			if onlineOnly {
				params.Set("online", "true")
			}

			ctx, cancel := context.WithTimeout(r.Context(), h.agent.timeout+2*time.Second)
			defer cancel()

			resp, err := h.agent.call(ctx, s, http.MethodGet, "/api/players?"+params.Encode(), "", nil)
			if err != nil {
				mu.Lock()
				failures = append(failures, failure{Server: s.Name, Message: friendlyError(err)})
				mu.Unlock()
				return
			}
			defer resp.Body.Close()

			if resp.StatusCode != http.StatusOK {
				mu.Lock()
				failures = append(failures, failure{Server: s.Name, Message: "HTTP " + strconv.Itoa(resp.StatusCode)})
				mu.Unlock()
				return
			}

			var payload struct {
				Players []agentPlayer `json:"players"`
			}
			if err := json.NewDecoder(io.LimitReader(resp.Body, maxRespSize)).Decode(&payload); err != nil {
				mu.Lock()
				failures = append(failures, failure{Server: s.Name, Message: "返回内容不是合法 JSON"})
				mu.Unlock()
				return
			}

			mu.Lock()
			defer mu.Unlock()
			for _, p := range payload.Players {
				merged, ok := occurrences[p.UUID]
				if !ok {
					merged = &MergedPlayer{UUID: p.UUID, Name: p.Name}
					occurrences[p.UUID] = merged
				}
				if p.Online {
					merged.Online = true
					merged.OnlineOn = append(merged.OnlineOn, s.Name)
				}
				if p.LastSeen > merged.LastSeen {
					merged.LastSeen = p.LastSeen
				}
				merged.TotalPlaytimeSeconds += p.PlaytimeSeconds
				merged.TotalJoinCount += p.JoinCount
				merged.Occurrences = append(merged.Occurrences, PlayerOccurrence{
					ServerID:        s.ID,
					Server:          s.Name,
					Online:          p.Online,
					LastIp:          p.LastIp,
					Group:           p.Group,
					PlaytimeSeconds: p.PlaytimeSeconds,
					JoinCount:       p.JoinCount,
					LastSeen:        p.LastSeen,
				})
			}
		}(srv)
	}
	wg.Wait()

	players := make([]*MergedPlayer, 0, len(occurrences))
	for _, p := range occurrences {
		players = append(players, p)
	}
	sort.SliceStable(players, func(i, j int) bool {
		if players[i].Online != players[j].Online {
			return players[i].Online
		}
		return players[i].LastSeen > players[j].LastSeen
	})

	writeJSON(w, http.StatusOK, map[string]any{
		"ok":       true,
		"total":    len(players),
		"players":  players,
		"failures": failures,
	})
}

// ---------- 代理透传 ----------

// handleProxy 把面板请求转发给某台服的模组 API。
//
// 这是"分布式"的关键一步：浏览器只跟 Hub 说话，各服的令牌留在 Hub 里，
// 于是面板不需要（也不应该）知道任何一台服的 apiToken。
func (h *Hub) handleProxy(w http.ResponseWriter, r *http.Request) {
	rest := strings.TrimPrefix(r.URL.Path, proxyPrefix)
	idx := strings.IndexByte(rest, '/')
	if idx <= 0 {
		writeError(w, http.StatusBadRequest, "bad_path", "代理路径要形如 /hub/proxy/{id}/api/...")
		return
	}
	id, tail := rest[:idx], rest[idx:]

	srv, ok := h.store.Server(id)
	if !ok {
		writeError(w, http.StatusNotFound, "not_found", "找不到服务器 "+id)
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), h.agent.timeout+5*time.Second)
	defer cancel()

	// 不转发浏览器带来的任何鉴权头：agent.call 只会带上这台服自己的令牌
	resp, err := h.agent.call(ctx, srv, r.Method, tail+querySuffix(r.URL.RawQuery), r.Header.Get("Content-Type"), r.Body)
	if err != nil {
		writeError(w, http.StatusBadGateway, "upstream_unreachable", friendlyError(err))
		return
	}
	defer resp.Body.Close()

	if ct := resp.Header.Get("Content-Type"); ct != "" {
		w.Header().Set("Content-Type", ct)
	}
	w.WriteHeader(resp.StatusCode)
	if r.Method != http.MethodHead {
		_, _ = io.Copy(w, io.LimitReader(resp.Body, maxRespSize))
	}
}

func querySuffix(raw string) string {
	if raw == "" {
		return ""
	}
	return "?" + raw
}

// ---------- 小工具 ----------

func methodNotAllowed(w http.ResponseWriter) {
	writeError(w, http.StatusMethodNotAllowed, "method_not_allowed", "该接口不支持这个 HTTP 方法")
}

func readBody(r *http.Request) ([]byte, error) {
	if r.Body == nil {
		return nil, errors.New("请求体为空")
	}
	defer r.Body.Close()
	data, err := io.ReadAll(io.LimitReader(r.Body, maxBodySize))
	if err != nil {
		return nil, err
	}
	if len(data) == 0 {
		return nil, errors.New("请求体为空")
	}
	return data, nil
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, map[string]any{"ok": false, "error": code, "message": message})
}
