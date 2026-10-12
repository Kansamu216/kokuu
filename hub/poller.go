package main

import (
	"context"
	"errors"
	"sync"
	"time"
)

// Poller 在后台定期探活所有受管服务器。
//
// 为什么不在面板打开时实时去戳：一次刷新要等 N 台服各回一次（慢），
// 而且频繁请求容易被对面的防火墙当成扫描。所以常态是读 Hub 缓存下来的最近状态，
// 只有点"探活"/"刷新"按钮时才即时打一次。
type Poller struct {
	store    *Store
	agent    *AgentClient
	interval time.Duration
	timeout  time.Duration
}

func NewPoller(store *Store, agent *AgentClient, interval, timeout time.Duration) *Poller {
	return &Poller{store: store, agent: agent, interval: interval, timeout: timeout}
}

// Run 阻塞运行直到 ctx 结束。启动时先立刻探一轮，免得面板刚打开全是"未知"。
func (p *Poller) Run(ctx context.Context) {
	p.PollAll(ctx)

	ticker := time.NewTicker(p.interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			p.PollAll(ctx)
		}
	}
}

// PollAll 并发探活全部启用的服务器。每台服独立超时，一台慢不会拖累其它台。
func (p *Poller) PollAll(ctx context.Context) {
	servers := p.store.Servers()

	var mu sync.Mutex
	var wg sync.WaitGroup
	results := make(map[string]*AgentState, len(servers))

	for _, srv := range servers {
		if !srv.Enabled {
			continue
		}
		wg.Add(1)
		go func(s *Server) {
			defer wg.Done()
			cctx, cancel := context.WithTimeout(ctx, p.timeout+2*time.Second)
			defer cancel()
			state := p.agent.probe(cctx, s)
			mu.Lock()
			results[s.ID] = state
			mu.Unlock()
		}(srv)
	}
	wg.Wait()

	if len(results) > 0 {
		p.store.SetStates(results)
	}
}

// PollOne 立刻探活指定的一台（面板上的"探活"按钮）。
func (p *Poller) PollOne(ctx context.Context, id string) (*AgentState, error) {
	srv, ok := p.store.Server(id)
	if !ok {
		return nil, errors.New("找不到该服务器")
	}
	cctx, cancel := context.WithTimeout(ctx, p.timeout+2*time.Second)
	defer cancel()

	state := p.agent.probe(cctx, srv)
	p.store.SetState(id, state)
	return state, nil
}
