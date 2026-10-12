#!/usr/bin/env node
/* =======================================================================
   假 Agent —— 模拟一台装了 voidkit 的游戏服的 HTTP API。
   用途：验证 Hub 时不需要真开一台 Minecraft 服务端。

   它会严格校验 X-Auth-Token：
     - 头里的令牌不对 → 401
   这样就能证明"Hub 确实把每台服自己的令牌注入到了请求里"，
   而不是碰巧连上了一个不校验令牌的服务。

   用法：
     node tools/fake-agent.js             # 监听 8787，令牌 fake-token
     node tools/fake-agent.js 9001 abc    # 指定端口与令牌
   ======================================================================= */

'use strict';

const http = require('http');

const PORT = Number(process.argv[2] || 8787);
const TOKEN = process.argv[3] || 'fake-token';
const NAME = process.argv[4] || '假服一区';

const STARTED_AT = Date.now();

const PLAYERS = [
  { uuid: '11111111-1111-1111-1111-111111111111', name: 'Steve', online: true, lastIp: '10.0.0.11', lastSeen: Date.now() - 60_000, playtimeSeconds: 36000, joinCount: 12, group: 'admin' },
  { uuid: '22222222-2222-2222-2222-222222222222', name: 'Alex', online: false, lastIp: '10.0.0.12', lastSeen: Date.now() - 7200_000, playtimeSeconds: 9000, joinCount: 5, group: 'default' },
  { uuid: '33333333-3333-3333-3333-333333333333', name: 'Notch', online: true, lastIp: '10.0.0.13', lastSeen: Date.now() - 5_000, playtimeSeconds: 900000, joinCount: 88, group: 'vip' },
];

const FEATURES = [
  { id: 'remote-api', name: '远程管理 API', enabled: true, description: 'fake' },
  { id: 'homes', name: '家', enabled: true, description: 'fake' },
  { id: 'motd', name: '服务器 MOTD', enabled: true, description: 'fake' },
  { id: 'scoreboard', name: '侧边栏记分牌', enabled: false, description: 'fake' },
];

function json(res, status, payload) {
  const body = Buffer.from(JSON.stringify(payload), 'utf8');
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': body.length });
  res.end(body);
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const path = url.pathname;

  // 健康检查免令牌，其余一律校验 —— 和真实模组保持一致
  if (path === '/api/health') {
    return json(res, 200, { ok: true, mod: 'voidkit', version: '1.0.0-fake', api: 'v1' });
  }
  if (req.headers['x-auth-token'] !== TOKEN) {
    return json(res, 401, { ok: false, error: 'unauthorized', message: '缺少或错误的访问令牌' });
  }

  switch (path) {
    case '/api/stats': {
      const online = PLAYERS.filter((p) => p.online).length;
      return json(res, 200, {
        online,
        maxPlayers: 20,
        totalProfiles: PLAYERS.length,
        uptimeMs: Date.now() - STARTED_AT,
        avgTps: 19.87,
        tickCount: Math.floor((Date.now() - STARTED_AT) / 50),
        memoryUsedMb: 1214,
        memoryMaxMb: 4096,
        motd: `§b§l${NAME} §f§l测试`,
        version: '1.21.1',
        apiRunning: true,
      });
    }
    case '/api/features':
      return json(res, 200, { ok: true, features: FEATURES });

    case '/api/players': {
      const q = (url.searchParams.get('q') || '').toLowerCase();
      const onlineOnly = url.searchParams.get('online') === 'true';
      let list = PLAYERS.slice();
      if (onlineOnly) list = list.filter((p) => p.online);
      if (q) list = list.filter((p) => p.name.toLowerCase().includes(q) || p.uuid.includes(q));
      const limit = Number(url.searchParams.get('limit') || 100);
      return json(res, 200, { ok: true, total: list.length, players: list.slice(0, limit) });
    }

    case '/api/logs/joins':
      return json(res, 200, {
        ok: true,
        entries: [
          { t: Date.now() - 120_000, uuid: PLAYERS[0].uuid, name: PLAYERS[0].name, ip: PLAYERS[0].lastIp, action: 'join' },
          { t: Date.now() - 600_000, uuid: PLAYERS[1].uuid, name: PLAYERS[1].name, ip: PLAYERS[1].lastIp, action: 'quit' },
        ],
      });

    case '/api/clearlag':
      return json(res, 200, { ok: true, enabled: true, intervalSeconds: 300, nextInSeconds: 120, lastRemoved: 7, totalRemoved: 99, rules: {} });

    default:
      return json(res, 404, { ok: false, error: 'not_found', message: 'fake agent: ' + path });
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`[fake-agent] ${NAME} 监听 http://127.0.0.1:${PORT}，令牌 ${TOKEN}`);
});
