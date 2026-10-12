package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// Server 是一台被 Hub 管理的游戏服（Agent）。
//
// Token 是那台服 kokuucore 的 apiToken，**只保存在 Hub 上、永远不下发给浏览器** ——
// 这是把面板和口令解耦的关键：面板使用者只需要知道 Hub 的一个令牌，
// 而各服的令牌留在 Hub 里，换服、加服都不用重新分发口令。
type Server struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	API     string   `json:"api"`   // 例：http://10.0.0.5:8765 或 http://mc.example.com:8765
	Token   string   `json:"token"` // 对应服的 apiToken
	Tags    []string `json:"tags"`
	Note    string   `json:"note"`
	Enabled bool     `json:"enabled"` // 关掉就不再轮询，但仍保留在清单里
}

// AgentState 是一台服的最近一次探测结果。属于"可丢的缓存"，
// 持久化只是为了让 Hub 重启后立刻有数据，不必等第一轮轮询。
type AgentState struct {
	Online      bool    `json:"online"`
	Error       string  `json:"error"`
	LatencyMs   int64   `json:"latencyMs"`
	CheckedAt   int64   `json:"checkedAt"`
	ModVersion  string  `json:"modVersion"`
	Minecraft   string  `json:"minecraft"`
	Players     int     `json:"players"`
	MaxPlayers  int     `json:"maxPlayers"`
	Profiles    int     `json:"profiles"`
	AvgTps      float64 `json:"avgTps"`
	MemUsedMb   int64   `json:"memUsedMb"`
	MemMaxMb    int64   `json:"memMaxMb"`
	UptimeMs    int64   `json:"uptimeMs"`
	FeaturesOn  int     `json:"featuresOn"`
	FeaturesAll int     `json:"featuresAll"`
}

// Config 同时是 Hub 自身的设置和受管服务器清单。
// 合成一份是刻意的：小内存机器上运维只想管一个文件。
type Config struct {
	Listen string `json:"listen"`
	// User / Password 是面板登录用的账号密码（浏览器走 HTTP Basic）。
	User     string `json:"user"`
	Password string `json:"password"`
	// Token 是给脚本 / 监控用的机器令牌；面板登录用不上它，但接口同样接受。
	Token          string    `json:"token"`
	PollSeconds    int       `json:"pollSeconds"`
	TimeoutSeconds int       `json:"timeoutSeconds"`
	Servers        []*Server `json:"servers"`
}

// Store 负责配置与状态的读写。所有落盘都是"写临时文件再原子替换"，
// 避免 hub 被 kill 时留下半截 JSON。
type Store struct {
	mu        sync.RWMutex
	dir       string
	cfg       *Config
	state     map[string]*AgentState
	cfgPath   string
	statePath string
}

func OpenStore(dir string) (*Store, error) {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, fmt.Errorf("创建数据目录失败: %w", err)
	}
	s := &Store{
		dir:       dir,
		cfgPath:   filepath.Join(dir, "config.json"),
		statePath: filepath.Join(dir, "state.json"),
		state:     map[string]*AgentState{},
	}

	s.cfg = &Config{}
	if err := readJSON(s.cfgPath, s.cfg); err != nil && !errors.Is(err, os.ErrNotExist) {
		return nil, fmt.Errorf("读取配置失败: %w", err)
	}
	s.normalize()

	// state.json 缺失或坏掉都不算致命，重新探测即可
	_ = readJSON(s.statePath, &s.state)
	if s.state == nil {
		s.state = map[string]*AgentState{}
	}
	return s, nil
}

func (s *Store) normalize() {
	if s.cfg.Listen == "" {
		s.cfg.Listen = "0.0.0.0:9000"
	}
	if s.cfg.PollSeconds <= 0 {
		s.cfg.PollSeconds = 15
	}
	if s.cfg.TimeoutSeconds <= 0 {
		s.cfg.TimeoutSeconds = 6
	}
	// 面板登录账号：首次运行时自动给一个，省得用户手工编
	if strings.TrimSpace(s.cfg.User) == "" {
		s.cfg.User = "admin"
	}
	if strings.TrimSpace(s.cfg.Password) == "" {
		s.cfg.Password = randomID(12)
	}
	if s.cfg.Token == "" {
		s.cfg.Token = randomID(24)
	}
	if s.cfg.Servers == nil {
		s.cfg.Servers = []*Server{}
	}
	for _, srv := range s.cfg.Servers {
		if srv.ID == "" {
			srv.ID = randomID(8)
		}
		srv.API = strings.TrimRight(strings.TrimSpace(srv.API), "/")
		if srv.Tags == nil {
			srv.Tags = []string{}
		}
	}
}

func (s *Store) Config() Config {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return *s.cfg
}

// UpdateSettings 用命令行参数覆盖配置并落盘。
// 只有非空/非零值才会覆盖 —— 这样"不带参数启动"不会把上次的设置抹掉。
func (s *Store) UpdateSettings(listen, user, password, token string, pollSeconds, timeoutSeconds int) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	if v := strings.TrimSpace(listen); v != "" {
		s.cfg.Listen = v
	}
	if v := strings.TrimSpace(user); v != "" {
		s.cfg.User = v
	}
	if v := strings.TrimSpace(password); v != "" {
		s.cfg.Password = v
	}
	if v := strings.TrimSpace(token); v != "" {
		s.cfg.Token = v
	}
	if pollSeconds > 0 {
		s.cfg.PollSeconds = pollSeconds
	}
	if timeoutSeconds > 0 {
		s.cfg.TimeoutSeconds = timeoutSeconds
	}
	s.normalize()
	return s.saveLocked()
}

func (s *Store) Token() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.Token
}

// Credentials 返回面板登录用的账号密码（仅给鉴权中间件与启动日志用，不下发给浏览器）。
func (s *Store) Credentials() (string, string) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.User, s.cfg.Password
}

func (s *Store) PollSeconds() int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.PollSeconds
}

func (s *Store) TimeoutSeconds() int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.cfg.TimeoutSeconds
}

// Servers 返回清单副本（含 Token，仅供 Hub 内部与代理使用）。
func (s *Store) Servers() []*Server {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]*Server, 0, len(s.cfg.Servers))
	for _, srv := range s.cfg.Servers {
		cp := *srv
		out = append(out, &cp)
	}
	return out
}

// PublicServers 返回给浏览器的版本：**抹掉 Token**。
func (s *Store) PublicServers() []map[string]any {
	s.mu.RLock()
	defer s.mu.RUnlock()
	out := make([]map[string]any, 0, len(s.cfg.Servers))
	for _, srv := range s.cfg.Servers {
		out = append(out, map[string]any{
			"id":       srv.ID,
			"name":     srv.Name,
			"api":      srv.API,
			"tags":     srv.Tags,
			"note":     srv.Note,
			"enabled":  srv.Enabled,
			"hasToken": srv.Token != "", // 只告诉面板"有没有配令牌"，不给真值
		})
	}
	return out
}

func (s *Store) Server(id string) (*Server, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, srv := range s.cfg.Servers {
		if srv.ID == id {
			cp := *srv
			return &cp, true
		}
	}
	return nil, false
}

// Upsert 新增或更新一台服。ID 为空视为新增。
func (s *Store) Upsert(in *Server) (*Server, error) {
	s.mu.Lock()
	defer s.mu.Unlock()

	name := strings.TrimSpace(in.Name)
	api := strings.TrimRight(strings.TrimSpace(in.API), "/")
	if name == "" {
		return nil, errors.New("服务器名称不能为空")
	}
	if api == "" {
		return nil, errors.New("接口地址不能为空")
	}
	if !strings.HasPrefix(api, "http://") && !strings.HasPrefix(api, "https://") {
		api = "http://" + api
	}

	if in.ID == "" {
		srv := &Server{
			ID:      randomID(8),
			Name:    name,
			API:     api,
			Token:   strings.TrimSpace(in.Token),
			Tags:    cleanTags(in.Tags),
			Note:    strings.TrimSpace(in.Note),
			Enabled: true,
		}
		s.cfg.Servers = append(s.cfg.Servers, srv)
		return srv, s.saveLocked()
	}

	for _, srv := range s.cfg.Servers {
		if srv.ID != in.ID {
			continue
		}
		srv.Name = name
		srv.API = api
		srv.Tags = cleanTags(in.Tags)
		srv.Note = strings.TrimSpace(in.Note)
		srv.Enabled = in.Enabled
		// 令牌留空表示"不改动"，避免面板回显掩码后再保存把真令牌覆盖成空
		if t := strings.TrimSpace(in.Token); t != "" {
			srv.Token = t
		}
		return srv, s.saveLocked()
	}
	return nil, errors.New("找不到该服务器")
}

func (s *Store) Delete(id string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	for i, srv := range s.cfg.Servers {
		if srv.ID == id {
			s.cfg.Servers = append(s.cfg.Servers[:i], s.cfg.Servers[i+1:]...)
			delete(s.state, id)
			return s.saveLocked()
		}
	}
	return errors.New("找不到该服务器")
}

// ---------- 状态缓存 ----------

func (s *Store) SetState(id string, st *AgentState) {
	s.SetStates(map[string]*AgentState{id: st})
}

// SetStates 批量写入探测结果。
//
// 刻意做成批量的：一轮轮询可能有几十台服，逐台落盘会把磁盘和 CPU 都浪费在
// 同一个 JSON 上（小内存 VPS 上尤其明显）。
func (s *Store) SetStates(states map[string]*AgentState) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for id, st := range states {
		s.state[id] = st
	}
	s.saveStateLocked()
}

func (s *Store) State(id string) *AgentState {
	s.mu.RLock()
	defer s.mu.RUnlock()
	if st, ok := s.state[id]; ok {
		cp := *st
		return &cp
	}
	return nil
}

// ---------- 落盘 ----------

func (s *Store) save() error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.saveLocked()
}

func (s *Store) saveLocked() error {
	return writeJSONAtomic(s.cfgPath, s.cfg)
}

func (s *Store) saveStateLocked() error {
	return writeJSONAtomic(s.statePath, s.state)
}

func readJSON(path string, out any) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, out)
}

func writeJSONAtomic(path string, value any) error {
	data, err := json.MarshalIndent(value, "", "  ")
	if err != nil {
		return err
	}
	data = append(data, '\n')

	tmp := path + ".tmp"
	if err := os.WriteFile(tmp, data, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, path)
}

// ---------- 小工具 ----------

func randomID(n int) string {
	buf := make([]byte, (n+1)/2)
	if _, err := rand.Read(buf); err != nil {
		// 极端情况下退回时间戳，至少保证唯一性不重复
		return fmt.Sprintf("%x", time.Now().UnixNano())[:n]
	}
	return hex.EncodeToString(buf)[:n]
}

func cleanTags(in []string) []string {
	out := make([]string, 0, len(in))
	for _, t := range in {
		t = strings.TrimSpace(t)
		if t != "" {
			out = append(out, t)
		}
	}
	return out
}
