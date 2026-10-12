/* =======================================================================
   VoidkitHub 管理平台 —— 前端
   与单服面板（panel/）的区别：这里管的是**一组**服务器。
   浏览器只跟 Hub 说话；各台服的 apiToken 留在 Hub 上，前端永远拿不到。
   ======================================================================= */

'use strict';

const LS_KEY = 'voidkithub.panel.v1';
const COLORS = {
  0: '#000000', 1: '#0000AA', 2: '#00AA00', 3: '#00AAAA', 4: '#AA0000', 5: '#AA00AA',
  6: '#FFAA00', 7: '#AAAAAA', 8: '#555555', 9: '#5555FF', a: '#55FF55', b: '#55FFFF',
  c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF',
};

const state = {
  base: '', user: '', pass: '', view: 'fleet', timer: null, drawer: null,
  servers: [], fleet: null, current: '',
  players: { items: [], total: 0, offset: 0, limit: 20, q: '', online: false, sort: 'lastSeen' },
  xsearch: { hits: [], failures: [], q: '', online: false, done: false },
  logs: { entries: [], q: '', action: '', limit: 100 },
  modules: [],
  guard: {
    sessions: { items: [], failures: [] },
    bans: { items: [], total: 0, q: '', type: '' },
    report: { player: '' },
    audit: { events: [], failures: [], type: '', limit: 100 },
  },
};

/** UTF-8 安全的 base64（中文用户名/密码也能正确编码）。 */
function b64(str) {
  const bytes = new TextEncoder().encode(str);
  let bin = '';
  bytes.forEach((b) => { bin += String.fromCharCode(b); });
  return btoa(bin);
}

/** 面板登录用的 Authorization 头。 */
const authHeader = () => 'Basic ' + b64(state.user + ':' + state.pass);

const $ = (id) => document.getElementById(id);
const el = () => $('view');

/* ------------------------------------------------------------------ 工具 */

const esc = (v) => String(v === null || v === undefined ? '' : v)
  .replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function mc(text) {
  let color = null, bold = false, out = '', buf = '';
  const src = String(text === null || text === undefined ? '' : text);
  const flush = () => {
    if (!buf) return;
    const style = (color ? 'color:' + color + ';' : '') + (bold ? 'font-weight:700;' : '');
    out += style ? `<span style="${style}">${esc(buf)}</span>` : esc(buf);
    buf = '';
  };
  for (let i = 0; i < src.length; i++) {
    const c = src[i];
    if ((c === '&' || c === '§') && i + 1 < src.length) {
      const code = src[i + 1].toLowerCase();
      if (COLORS[code]) { flush(); color = COLORS[code]; bold = false; i++; continue; }
      if (code === 'l') { flush(); bold = true; i++; continue; }
      if (code === 'r') { flush(); color = null; bold = false; i++; continue; }
      if ('mnok'.includes(code)) { i++; continue; }
    }
    buf += c;
  }
  flush();
  return out || '&nbsp;';
}

const plain = (t) => String(t || '').replace(/[&§][0-9a-fk-orA-FK-OR]/g, '');

function fmtTime(ms) {
  if (!ms) return '—';
  const d = new Date(Number(ms));
  if (isNaN(d)) return '—';
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function fmtDuration(seconds) {
  const s = Math.max(0, Math.floor(Number(seconds) || 0));
  if (s < 60) return `${s}秒`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}分${s % 60}秒`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}小时${m % 60}分`;
  return `${Math.floor(h / 24)}天${h % 24}小时`;
}

const fmtAgo = (ms) => (ms ? fmtDuration((Date.now() - Number(ms)) / 1000) + '前' : '—');
const fmtMb = (mb) => (Number(mb) >= 1024 ? (Number(mb) / 1024).toFixed(2) + 'GB' : Math.round(Number(mb) || 0) + 'MB');

function avatar(name, uuid, size) {
  let hash = 0;
  const seed = String(uuid || name || '?');
  for (let i = 0; i < seed.length; i++) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  const palette = ['#4cc2a0', '#4a9eff', '#e3b341', '#f2616b', '#a78bfa', '#f0883e', '#3fb950', '#38bdf8'];
  const px = size || 30;
  const ch = (String(name || '?').trim().charAt(0) || '?').toUpperCase();
  return `<span class="avatar" style="width:${px}px;height:${px}px;font-size:${Math.round(px * 0.46)}px;background:${palette[hash % palette.length]}">${esc(ch)}</span>`;
}

function toast(message, kind) {
  const box = document.createElement('div');
  box.className = 'toast' + (kind ? ' ' + kind : '');
  box.textContent = message;
  $('toasts').appendChild(box);
  setTimeout(() => {
    box.style.opacity = '0'; box.style.transition = 'opacity .3s';
    setTimeout(() => box.remove(), 320);
  }, kind === 'error' ? 5200 : 3000);
}

const serverName = (id) => {
  const s = state.servers.find((x) => x.id === id);
  return s ? s.name : id;
};
const serverById = (id) => state.servers.find((x) => x.id === id) || null;

/* ------------------------------------------------------------------ 请求 */

async function hub(path, options) {
  const opts = options || {};
  const headers = { Authorization: authHeader() };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(state.base + path, {
      method: opts.method || 'GET',
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    });
  } catch (e) {
    throw new Error('无法连接 Hub，请确认 Hub 已启动且地址正确');
  }
  if (res.status === 401) { logout(); throw new Error('用户名或密码不正确'); }

  const text = await res.text();
  let data = null;
  if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
  if (!res.ok) throw new Error((data && (data.message || data.error)) || `请求失败（HTTP ${res.status}）`);
  return data || {};
}

/** 通过 Hub 代理访问某台游戏服的模组 API。 */
function proxy(path, options) {
  const opts = Object.assign({}, options || {});
  const server = opts.server || state.current;
  delete opts.server;
  if (!server) throw new Error('请先选择一台服务器');
  return hub('/hub/proxy/' + encodeURIComponent(server) + path, opts);
}

/**
 * 从接口响应里安全地取数组。
 *
 * 模组的 ApiJson 曾经把 List 序列化成字符串，接口照样返回 200，
 * 前端只能报 "xxx.map is not a function" —— 完全看不出是后端的问题。
 * 这里把这种情况转成一条能指明原因的报错，而不是让它在下游炸开。
 */
function asArray(value, field) {
  if (value === undefined || value === null) return [];
  if (Array.isArray(value)) return value;
  throw new Error(`接口返回的 ${field} 不是数组（实际是 ${typeof value}）—— 后端序列化有问题，请确认模组版本`);
}

/* ------------------------------------------------------------------ 弹窗 */

function closeModal() { $('modal').classList.remove('open'); $('modal-card').innerHTML = ''; }

function confirmBox(title, desc, okText, danger) {
  return new Promise((resolve) => {
    $('modal-card').innerHTML = `
      <h3>${esc(title)}</h3>${desc ? `<p>${esc(desc)}</p>` : ''}
      <div class="row" style="justify-content:flex-end">
        <button class="btn" id="m-no">取消</button>
        <button class="btn ${danger ? 'danger' : 'primary'}" id="m-yes">${esc(okText || '确定')}</button>
      </div>`;
    $('modal').classList.add('open');
    $('m-no').onclick = () => { closeModal(); resolve(false); };
    $('m-yes').onclick = () => { closeModal(); resolve(true); };
  });
}

/** fields: {name,label,type:text|password|number|textarea|checkbox,value,placeholder,hint} */
function formModal(title, desc, fields, okText, danger) {
  return new Promise((resolve) => {
    const body = fields.map((f, i) => {
      if (f.type === 'checkbox') {
        return `<label class="check" style="margin-bottom:13px">
          <input type="checkbox" data-idx="${i}" ${f.value ? 'checked' : ''}> ${esc(f.label)}</label>`;
      }
      if (f.type === 'textarea') {
        return `<label class="field"><span>${esc(f.label)}</span>
          <textarea class="textarea" data-idx="${i}" placeholder="${esc(f.placeholder || '')}">${esc(f.value || '')}</textarea></label>`;
      }
      return `<label class="field"><span>${esc(f.label)}</span>
        <input class="input" data-idx="${i}" type="${esc(f.type || 'text')}"
          value="${esc(f.value === undefined ? '' : f.value)}" placeholder="${esc(f.placeholder || '')}">
        ${f.hint ? `<span class="tiny" style="display:block;margin-top:4px">${esc(f.hint)}</span>` : ''}</label>`;
    }).join('');

    $('modal-card').innerHTML = `
      <h3>${esc(title)}</h3>${desc ? `<p>${esc(desc)}</p>` : ''}
      ${body}
      <div class="row" style="justify-content:flex-end">
        <button class="btn" id="m-no">取消</button>
        <button class="btn ${danger ? 'danger' : 'primary'}" id="m-yes">${esc(okText || '保存')}</button>
      </div>`;
    $('modal').classList.add('open');

    const first = $('modal-card').querySelector('[data-idx]');
    if (first && first.type !== 'checkbox') first.focus();

    $('m-no').onclick = () => { closeModal(); resolve(null); };
    $('m-yes').onclick = () => {
      const values = {};
      $('modal-card').querySelectorAll('[data-idx]').forEach((node) => {
        const field = fields[Number(node.dataset.idx)];
        values[field.name] = field.type === 'checkbox' ? node.checked : node.value;
      });
      closeModal();
      resolve(values);
    };
  });
}

/* ------------------------------------------------------------------ 骨架 */

function setHead(title, sub) { $('title').textContent = title; $('subtitle').textContent = sub || ''; }

function markNav() {
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.view === state.view));
}

function loading() { el().innerHTML = '<div class="card"><div class="empty">加载中…</div></div>'; }

function updateFooter() {
  const f = state.fleet;
  if (!f) return;
  $('foot-fleet').textContent = `${f.onlineServers}/${f.servers} 台在线`;
  $('foot-players').textContent = `在线 ${f.players}`;
}

/** 顶部服务器选择器：只在「对单台服务器操作」的页面出现；单服时自动隐藏。 */
function renderSelector() {
  const wrap = $('selector-wrap');
  const singleServerViews = ['logs', 'modules', 'inspect', 'guard-bans', 'guard-report'];
  const needSelect = singleServerViews.includes(state.view);
  // 单服（≤1 台）时无需选择器 —— 逻辑直接落在唯一那台上
  wrap.classList.toggle('hidden', !needSelect || state.servers.length <= 1);
  if (!needSelect) return;

  const options = ['<option value="">全部服务器</option>']
    .concat(state.servers.map((s) => {
      const st = s.state || {};
      const mark = !s.enabled ? '（停用）' : (st.online ? '' : '（离线）');
      return `<option value="${esc(s.id)}" ${state.current === s.id ? 'selected' : ''}>${esc(s.name)}${mark}</option>`;
    }));
  $('selector').innerHTML = options.join('');
  $('selector').onchange = () => {
    state.current = $('selector').value;
    draw();
  };
}

/* ------------------------------------------------------------------ 视图：总览（仪表盘） */

async function viewFleet() {
  setHead('总览', '所有服务器的实时状态与智能提示');
  markNav();
  loading();

  state.fleet = await hub('/hub/api/fleet');
  await refreshServers();
  updateFooter();

  const f = state.fleet;
  const health = [f.onlineServers, f.offlineServers, f.disabledServers];

  // 智能提示：主动指出需要关注的问题，而不是让服主自己去翻
  const alerts = [];
  for (const s of (f.items || [])) {
    if (s.online && (s.avgTps || 0) > 0 && (s.avgTps || 0) < 17) {
      alerts.push({ txt: `${esc(s.name)} TPS 偏低（${(s.avgTps || 0).toFixed(1)}）`, kind: 'warn' });
    }
    if (s.online && (s.memUsedMb || 0) > 8192) {
      alerts.push({ txt: `${esc(s.name)} 内存占用高（${fmtMb(s.memUsedMb)}）`, kind: 'warn' });
    }
  }
  if (f.offlineServers > 0) {
    alerts.push({ txt: `${f.offlineServers} 台服务器离线`, kind: 'danger' });
  }

  el().innerHTML = `
    <div class="grid g4">
      <div class="stat a"><div class="k">在线服务器</div>
        <div class="v">${f.onlineServers}<small> / ${f.servers}</small></div>
        <div class="n">离线 ${health[1]} · 停用 ${health[2]}</div></div>
      <div class="stat b"><div class="k">在线玩家</div>
        <div class="v">${f.players}<small> / ${f.maxPlayers}</small></div>
        <div class="n">全部服的容量合计</div></div>
      <div class="stat"><div class="k">平均 TPS</div>
        <div class="v">${(f.avgTps || 0).toFixed(1)}</div>
        <div class="n">仅在线的服务器</div></div>
      <div class="stat"><div class="k">玩家档案</div>
        <div class="v">${f.profiles}</div>
        <div class="n">各服合计（含重复）</div></div>
    </div>

    ${alerts.length ? `
      <div class="ai-card">
        <div class="ai-head">智能提示</div>
        <div class="ai-list">
          ${alerts.map((a) => `<div class="ai-item ${a.kind}"><span class="dot-state ${a.kind === 'danger' ? 'down' : 'up'}"></span>${a.txt}</div>`).join('')}
        </div>
      </div>` : (f.servers > 0 ? `
      <div class="ai-card ok">
        <div class="ai-head">智能提示</div>
        <div class="ai-list"><div class="ai-item"><span class="dot-state up"></span>所有服务器运行正常</div></div>
      </div>` : '')}

    ${f.servers === 0 ? `
      <div class="hint-card">
        还没有添加任何服务器。先去「服务器」页把各台游戏服加进来 —— 每台服需要
        <code>remote-api</code> 模块开着，以及它的 <code>apiToken</code>。
        <div class="chips"><span class="chip" id="goto-servers">去添加服务器</span></div>
      </div>` : `
      <div class="fleet">
        ${f.items.map((item) => srvCard(item)).join('')}
      </div>`}
  `;

  if ($('goto-servers')) $('goto-servers').onclick = () => setView('servers');

  el().querySelectorAll('[data-enter]').forEach((btn) => {
    btn.onclick = () => {
      state.current = btn.dataset.enter;
      setView('players');
    };
  });
  el().querySelectorAll('[data-probe]').forEach((btn) => {
    btn.onclick = async () => {
      btn.disabled = true;
      btn.textContent = '探测中…';
      try {
        await hub('/hub/api/servers/' + encodeURIComponent(btn.dataset.probe) + '/probe', { method: 'POST', body: {} });
        await draw();
      } catch (e) { toast(e.message, 'error'); btn.disabled = false; btn.textContent = '探活'; }
    };
  });
}

function srvCard(item) {
  const cls = !item.enabled ? 'off' : (item.online ? '' : 'down');
  const stateBadge = !item.enabled
    ? '<span class="badge off">已停用</span>'
    : (item.online ? '<span class="badge on">在线</span>' : '<span class="badge dg">离线</span>');

  const metrics = item.online ? `
    <div class="srv-metrics">
      <div class="m"><div class="k">在线</div><div class="v">${item.players}<small>/${item.maxPlayers}</small></div></div>
      <div class="m"><div class="k">TPS</div><div class="v">${(item.avgTps || 0).toFixed(1)}</div></div>
      <div class="m"><div class="k">内存</div><div class="v">${fmtMb(item.memUsedMb)}</div></div>
    </div>` : '';

  return `
    <div class="srv ${cls}">
      <div class="srv-top">
        <span class="dot-state ${!item.enabled ? 'off' : (item.online ? 'up' : 'down')}"></span>
        <div style="min-width:0">
          <div class="name">${esc(item.name)}</div>
          <div class="api">${esc(item.api || '—')}</div>
        </div>
        <div class="state">
          ${stateBadge}
          ${item.online ? `<span class="tiny">${item.latencyMs}ms</span>` : ''}
        </div>
      </div>

      ${metrics}

      ${item.error ? `<div class="srv-err">${esc(item.error)}</div>` : ''}

      ${item.online ? `<div class="tiny">
        运行 ${fmtDuration(item.uptimeMs / 1000)} · MC ${esc(item.minecraft || '?')}
        · 模块 ${item.featuresOn}/${item.featuresAll}
        ${item.modVersion ? '· VoidkitCore ' + esc(item.modVersion) : ''}
      </div>` : ''}

      ${(item.tags && item.tags.length) ? `<div class="srv-tags">${item.tags.map((t) => `<span class="badge">${esc(t)}</span>`).join('')}</div>` : ''}

      <div class="srv-actions">
        <button class="btn sm" data-probe="${esc(item.id)}">探活</button>
        ${item.enabled ? `<button class="btn sm primary" data-enter="${esc(item.id)}">进入管理</button>` : ''}
      </div>
    </div>`;
}

/* ------------------------------------------------------------------ 视图：玩家 */

async function viewPlayers() {
  markNav();
  if (state.current) {
    return viewPlayersSingle();
  }
  return viewPlayersCross();
}

/** 未选服务器时：跨服搜索，看一个玩家在哪几台服上有号。 */
async function viewPlayersCross() {
  setHead('玩家（跨服）', '在所有在线的服务器上搜索同一个玩家');
  const x = state.xsearch;

  el().innerHTML = `
    <div class="card">
      <div class="bar">
        <input id="xq" type="text" placeholder="输入玩家名 / UUID（留空则列出各服最近的玩家）" value="${esc(x.q)}">
        <label class="check"><input type="checkbox" id="xonline" ${x.online ? 'checked' : ''}> 只看在线</label>
        <button class="btn primary" id="xsearch">搜索</button>
      </div>
      <div class="card-body flush" id="xresult">
        ${x.done ? '' : '<div class="empty">输入关键字后回车，或直接点搜索</div>'}
      </div>
    </div>`;

  const run = async () => {
    x.q = $('xq').value.trim();
    x.online = $('xonline').checked;
    $('xresult').innerHTML = '<div class="empty">正在向各服并发查询…</div>';
    try {
      const params = new URLSearchParams({ limit: '50' });
      if (x.q) params.set('q', x.q);
      if (x.online) params.set('online', 'true');
      const data = await hub('/hub/api/players?' + params);
      x.hits = asArray(data.players, 'players');
      x.failures = asArray(data.failures, 'failures');
      x.done = true;
      renderCrossResult();
    } catch (e) {
      $('xresult').innerHTML = `<div class="empty">${esc(e.message)}</div>`;
    }
  };

  $('xq').onkeydown = (e) => { if (e.key === 'Enter') run(); };
  $('xsearch').onclick = run;
  if (x.done) renderCrossResult();
}

function renderCrossResult() {
  const x = state.xsearch;
  const box = $('xresult');
  if (!box) return;

  const failNote = x.failures.length
    ? `<div class="fleet-note" style="padding:10px 15px">有 ${x.failures.length} 台服查询失败：${x.failures.map((f) => esc(f.server + '：' + f.message)).join('；')}</div>`
    : '';

  if (!x.hits.length) {
    box.innerHTML = failNote + '<div class="empty">没有找到匹配的玩家</div>';
    return;
  }

  box.innerHTML = failNote + `<div class="tw"><table>
    <thead><tr><th>玩家</th><th>状态</th><th>所在服务器</th><th>累计时长</th><th>登录</th><th>最后在线</th></tr></thead>
    <tbody>
      ${x.hits.map((p) => `<tr class="click" data-xplayer="${esc(p.uuid)}">
        <td><div class="pcell">${avatar(p.name, p.uuid, 30)}
          <div><div>${esc(p.name)}</div><div class="uuid mono">${esc(String(p.uuid).slice(0, 8))}…</div></div></div></td>
        <td>${p.online ? '<span class="badge on">在线</span>' : '<span class="badge off">离线</span>'}</td>
        <td>${p.occurrences.map((o) => `<span class="badge ${o.online ? 'ac' : ''}">${esc(o.server)}</span>`).join(' ')}</td>
        <td class="nowrap">${fmtDuration(p.totalPlaytimeSeconds)}</td>
        <td class="tiny">${p.totalJoinCount} 次</td>
        <td class="tiny nowrap">${fmtAgo(p.lastSeen)}</td>
      </tr>`).join('')}
    </tbody></table></div>`;

  box.querySelectorAll('[data-xplayer]').forEach((tr) => {
    tr.onclick = () => {
      const p = x.hits.find((hit) => hit.uuid === tr.dataset.xplayer);
      if (p) openCrossPlayer(p);
    };
  });
}

function openCrossPlayer(p) {
  state.drawer = 'x:' + p.uuid;
  $('drawer').classList.add('open');
  const panel = $('drawer-panel');

  panel.innerHTML = `
    <div class="dhead">
      ${avatar(p.name, p.uuid, 40)}
      <div><h3>${esc(p.name)}</h3><div class="uuid mono">${esc(p.uuid)}</div></div>
      <button class="btn sm" id="d-close">关闭</button>
    </div>

    <div class="dsec">
      <h4>总览</h4>
      <div class="row">
        ${p.online ? '<span class="badge on">在线</span>' : '<span class="badge off">离线</span>'}
        <span class="badge ac">出现在 ${p.occurrences.length} 台服</span>
      </div>
      <dl class="kv" style="margin-top:12px">
        <dt>累计时长</dt><dd>${fmtDuration(p.totalPlaytimeSeconds)}（各服相加）</dd>
        <dt>累计登录</dt><dd>${p.totalJoinCount} 次</dd>
        <dt>最后在线</dt><dd>${fmtTime(p.lastSeen)}（${fmtAgo(p.lastSeen)}）</dd>
        ${p.onlineOn.length ? `<dt>此刻在线</dt><dd>${p.onlineOn.map(esc).join('、')}</dd>` : ''}
      </dl>
    </div>

    <div class="dsec">
      <h4>按服务器</h4>
      <div class="tw"><table>
        <thead><tr><th>服务器</th><th>状态</th><th>权限组</th><th>时长</th><th>最后 IP</th><th></th></tr></thead>
        <tbody>
          ${p.occurrences.map((o) => `<tr>
            <td>${esc(o.server)}</td>
            <td>${o.online ? '<span class="badge on">在线</span>' : '<span class="badge off">离线</span>'}</td>
            <td><span class="badge ac">${esc(o.group || 'default')}</span></td>
            <td class="tiny nowrap">${fmtDuration(o.playtimeSeconds)}</td>
            <td class="mono tiny">${esc(o.lastIp || '—')}</td>
            <td><button class="btn sm" data-goto="${esc(o.serverId)}">打开</button></td>
          </tr>`).join('')}
        </tbody>
      </table></div>
    </div>`;

  $('d-close').onclick = closeDrawer;
  panel.querySelectorAll('[data-goto]').forEach((btn) => {
    btn.onclick = () => {
      state.current = btn.dataset.goto;
      state.drawer = null;
      $('drawer').classList.remove('open');
      setView('players');
    };
  });
}

/** 选中某台服后：完整的单服玩家管理（走 Hub 代理）。 */
async function viewPlayersSingle() {
  const srv = serverById(state.current);
  setHead('玩家 · ' + (srv ? srv.name : state.current), '档案、IP 历史、权限与实时操作');
  loading();

  const p = state.players;
  const params = new URLSearchParams({
    q: p.q, online: String(p.online), sort: p.sort, limit: String(p.limit), offset: String(p.offset),
  });
  const data = await proxy('/api/players?' + params);
  p.items = asArray(data.players, 'players');
  p.total = data.total || 0;

  const from = p.total ? p.offset + 1 : 0;
  const to = Math.min(p.offset + p.limit, p.total);
  const pages = Math.max(1, Math.ceil(p.total / p.limit));
  const st = (srv && srv.state) || {};

  el().innerHTML = `
    ${srv && srv.state && !srv.state.online ? `
      <div class="hint-card">
        <strong>${esc(srv.name)} 当前离线</strong><br>
        ${esc(srv.state.error || '无法连接')}
        <div class="chips">
          <span class="chip" id="back-fleet">回总览</span>
          <span class="chip" id="pick-other">换个服务器</span>
        </div>
      </div>` : ''}

    <div class="card">
      <div class="bar">
        <input id="q" type="text" placeholder="搜索名字 / 曾用名 / UUID" value="${esc(p.q)}">
        <label class="check"><input type="checkbox" id="only-online" ${p.online ? 'checked' : ''}> 只看在线</label>
        <select id="sort">
          ${[['lastSeen', '最后在线'], ['name', '名字'], ['playtime', '游戏时长'], ['joinCount', '登录次数'], ['firstJoin', '首次进入']]
            .map(([k, t]) => `<option value="${k}" ${p.sort === k ? 'selected' : ''}>${t}</option>`).join('')}
        </select>
        <select id="limit">${[20, 50, 100].map((n) => `<option ${p.limit === n ? 'selected' : ''}>${n}</option>`).join('')}</select>
        ${st.online ? `<span class="tiny">${st.players}/${st.maxPlayers} 在线 · TPS ${(st.avgTps || 0).toFixed(1)}</span>` : ''}
      </div>

      ${p.items.length ? `<div class="tw"><table>
        <thead><tr><th>玩家</th><th>状态</th><th>权限组</th><th>最后 IP</th>
          <th>游戏时长</th><th>登录</th><th>最后在线</th><th></th></tr></thead><tbody>
        ${p.items.map((r) => `<tr class="click" data-key="${esc(r.uuid)}">
          <td><div class="pcell">${avatar(r.name, r.uuid, 30)}
            <div><div>${esc(r.name)}</div><div class="uuid mono">${esc(String(r.uuid).slice(0, 8))}…</div></div></div></td>
          <td>${r.online ? '<span class="badge on">在线</span>' : '<span class="badge off">离线</span>'}
            ${r.muted ? ' <span class="badge dg">禁言</span>' : ''}</td>
          <td><span class="badge ac">${esc(r.group || 'default')}</span></td>
          <td class="mono tiny">${esc(r.lastIp || '—')}</td>
          <td class="nowrap">${fmtDuration(r.playtimeSeconds)}</td>
          <td class="tiny">${r.joinCount} 次</td>
          <td class="tiny nowrap">${r.online ? '现在' : fmtAgo(r.lastSeen)}</td>
          <td class="nowrap"><button class="btn sm" data-open="${esc(r.uuid)}">详情</button></td>
        </tr>`).join('')}
        </tbody></table></div>
        <div class="pager">
          <span>共 ${p.total} 位 · 第 ${Math.floor(p.offset / p.limit) + 1}/${pages} 页（${from}-${to}）</span>
          <div class="row">
            <button class="btn sm" id="prev" ${p.offset <= 0 ? 'disabled' : ''}>上一页</button>
            <button class="btn sm" id="next" ${to >= p.total ? 'disabled' : ''}>下一页</button>
          </div>
        </div>` : '<div class="empty">没有匹配的玩家</div>'}
    </div>`;

  if ($('back-fleet')) $('back-fleet').onclick = () => setView('fleet');
  if ($('pick-other')) $('pick-other').onclick = () => { state.current = ''; renderSelector(); setView('players'); };

  const search = () => {
    p.q = $('q').value.trim(); p.online = $('only-online').checked;
    p.sort = $('sort').value; p.limit = Number($('limit').value); p.offset = 0;
    draw();
  };
  $('q').onkeydown = (e) => { if (e.key === 'Enter') search(); };
  $('only-online').onchange = search;
  $('sort').onchange = search;
  $('limit').onchange = search;
  if ($('prev')) $('prev').onclick = () => { p.offset = Math.max(0, p.offset - p.limit); draw(); };
  if ($('next')) $('next').onclick = () => { p.offset += p.limit; draw(); };

  el().querySelectorAll('tr[data-key]').forEach((tr) => { tr.onclick = () => openPlayer(tr.dataset.key); });
  el().querySelectorAll('[data-open]').forEach((b) => {
    b.onclick = (e) => { e.stopPropagation(); openPlayer(b.dataset.open); };
  });
}

/* ------------------------------------------------------------------ 玩家详情（单服） */

async function openPlayer(key) {
  const server = state.current;
  state.drawer = server + ':' + key;
  $('drawer').classList.add('open');
  const panel = $('drawer-panel');
  panel.innerHTML = '<div class="empty">加载中…</div>';

  let d, ips, groups = [], homes = [];
  try {
    d = (await proxy('/api/players/' + encodeURIComponent(key), { server })).player;
    ips = (await proxy('/api/players/' + encodeURIComponent(key) + '/ips', { server })).ips || [];
    groups = ((await proxy('/api/groups', { server })).groups || []).map((g) => g.name);
    homes = d.homes || [];
  } catch (e) {
    panel.innerHTML = `<div class="dhead"><h3>加载失败</h3><button class="btn sm" id="d-close">关闭</button></div>
      <div class="empty">${esc(e.message)}</div>`;
    $('d-close').onclick = closeDrawer;
    return;
  }
  if (state.drawer !== server + ':' + key) return;

  const call = (path, body, method) => proxy(path, { server, method: method || 'POST', body: body || {} });

  panel.innerHTML = `
    <div class="dhead">
      ${avatar(d.name, d.uuid, 40)}
      <div><h3>${esc(d.name)}</h3><div class="uuid mono">${esc(serverName(server))} · ${esc(String(d.uuid).slice(0, 18))}…</div></div>
      <button class="btn sm" id="d-close">关闭</button>
    </div>

    <div class="dsec">
      <h4>状态</h4>
      <div class="row">
        ${d.online ? '<span class="badge on">在线</span>' : '<span class="badge off">离线</span>'}
        <span class="badge ac">${esc(d.group)}</span>
        ${d.muted ? '<span class="badge dg">已禁言</span>' : ''}
        ${d.vanished ? '<span class="badge">隐身中</span>' : ''}
      </div>
      <dl class="kv" style="margin-top:12px">
        <dt>首次进入</dt><dd>${fmtTime(d.firstJoin)}</dd>
        <dt>最后在线</dt><dd>${fmtTime(d.lastSeen)}（${fmtAgo(d.lastSeen)}）</dd>
        <dt>累计时长</dt><dd>${fmtDuration(d.playtimeSeconds)}</dd>
        <dt>登录次数</dt><dd>${d.joinCount} 次</dd>
        <dt>最后 IP</dt><dd class="mono">${esc(d.lastIp || '—')}</dd>
        ${d.muteReason ? `<dt>禁言原因</dt><dd>${esc(d.muteReason)}</dd>` : ''}
      </dl>
    </div>

    <div class="dsec">
      <h4>操作</h4>
      <div class="row">
        ${d.online ? '<button class="btn" id="a-msg">私信</button><button class="btn danger" id="a-kick">踢出</button>' : ''}
        ${d.muted ? '<button class="btn" id="a-unmute">解除禁言</button>' : '<button class="btn danger" id="a-mute">禁言</button>'}
      </div>
    </div>

    <div class="dsec">
      <h4>权限组</h4>
      <div class="row">
        <select id="a-group" style="width:auto;flex:1">
          ${groups.map((g) => `<option ${g === d.group ? 'selected' : ''}>${esc(g)}</option>`).join('')}
        </select>
        <button class="btn sm" id="a-group-set">应用</button>
      </div>
    </div>

    <div class="dsec">
      <h4>IP 历史（${ips.length}）</h4>
      ${ips.length ? `<div class="tw"><table><thead><tr><th>IP</th><th>次数</th><th>最后使用</th></tr></thead><tbody>
        ${ips.map((i) => `<tr><td class="mono">${esc(i.ip)}</td><td>${i.count}</td>
          <td class="tiny">${fmtTime(i.lastSeen)}</td></tr>`).join('')}
        </tbody></table></div>` : '<div class="empty">没有记录</div>'}
    </div>

    <div class="dsec">
      <h4>家（${homes.length}）</h4>
      ${homes.length ? `<div class="tw"><table><thead><tr><th>名称</th><th>位置</th><th></th></tr></thead><tbody>
        ${homes.map((h) => `<tr><td>${esc(h.name)}</td><td class="mono tiny">${esc(h.display)}</td>
          <td><button class="btn sm danger" data-home="${esc(h.name)}">删除</button></td></tr>`).join('')}
        </tbody></table></div>` : '<div class="empty">没有设置家</div>'}
    </div>`;

  $('d-close').onclick = closeDrawer;
  const refresh = () => { openPlayer(key); };

  if ($('a-msg')) $('a-msg').onclick = async () => {
    const v = await formModal('私信 ' + d.name, null, [{ name: 'message', label: '内容', placeholder: '支持 &a 颜色代码' }], '发送');
    if (!v || !v.message.trim()) return;
    try { await call('/api/players/' + encodeURIComponent(key) + '/message', { message: v.message }); toast('已发送'); }
    catch (e) { toast(e.message, 'error'); }
  };

  if ($('a-kick')) $('a-kick').onclick = async () => {
    const v = await formModal('踢出 ' + d.name, null, [{ name: 'reason', label: '原因', value: '被管理员移出服务器' }], '踢出', true);
    if (!v) return;
    try { await call('/api/players/' + encodeURIComponent(key) + '/kick', v); toast('已踢出'); refresh(); }
    catch (e) { toast(e.message, 'error'); }
  };

  if ($('a-mute')) $('a-mute').onclick = async () => {
    const v = await formModal('禁言 ' + d.name, '分钟填 0 表示永久禁言。', [
      { name: 'minutes', label: '时长（分钟）', type: 'number', value: '60' },
      { name: 'reason', label: '原因', value: '' },
    ], '禁言', true);
    if (!v) return;
    try {
      await call('/api/players/' + encodeURIComponent(key) + '/mute', { minutes: Number(v.minutes) || 0, reason: v.reason });
      toast('已禁言'); refresh();
    } catch (e) { toast(e.message, 'error'); }
  };

  if ($('a-unmute')) $('a-unmute').onclick = async () => {
    try { await call('/api/players/' + encodeURIComponent(key) + '/unmute'); toast('已解除禁言'); refresh(); }
    catch (e) { toast(e.message, 'error'); }
  };

  $('a-group-set').onclick = async () => {
    const group = $('a-group').value;
    try { await call('/api/players/' + encodeURIComponent(key) + '/group', { group }); toast('权限组已更新'); refresh(); }
    catch (e) { toast(e.message, 'error'); }
  };

  panel.querySelectorAll('[data-home]').forEach((btn) => {
    btn.onclick = async () => {
      if (!await confirmBox('删除家', `确定删除 ${d.name} 的家「${btn.dataset.home}」？`, '删除', true)) return;
      try {
        await call('/api/players/' + encodeURIComponent(key) + '/homes/' + encodeURIComponent(btn.dataset.home) + '/delete');
        toast('已删除'); refresh();
      } catch (e) { toast(e.message, 'error'); }
    };
  });
}

/* ------------------------------------------------------------------ 视图：日志 */

async function viewLogs() {
  markNav();
  setHead('上下线日志', state.current ? '来自 ' + serverName(state.current) : '请先选择一台服务器');
  loading();

  if (!state.current) return needServerHint();

  const l = state.logs;
  const isUuid = /^[0-9a-f-]{32,36}$/i.test(l.q);
  const params = new URLSearchParams({ limit: String(l.q ? l.limit * 4 : l.limit) });
  if (l.action) params.set('action', l.action);
  if (isUuid) params.set('uuid', l.q);

  const data = await proxy('/api/logs/joins?' + params);
  let entries = asArray(data.entries, 'entries');
  if (l.q && !isUuid) entries = entries.filter((e) => String(e.name).toLowerCase().includes(l.q.toLowerCase()));
  entries = entries.slice(0, l.limit);

  el().innerHTML = `
    <div class="card">
      <div class="bar">
        <input id="lq" type="text" placeholder="按玩家名或 UUID 过滤" value="${esc(l.q)}">
        <select id="laction">
          <option value="">全部动作</option>
          <option value="join" ${l.action === 'join' ? 'selected' : ''}>仅上线</option>
          <option value="quit" ${l.action === 'quit' ? 'selected' : ''}>仅下线</option>
        </select>
        <select id="llimit">${[50, 100, 300, 1000].map((n) => `<option ${l.limit === n ? 'selected' : ''}>${n}</option>`).join('')}</select>
        <span class="tiny">共 ${entries.length} 条</span>
      </div>
      ${entries.length ? `<div class="tw"><table>
        <thead><tr><th>时间</th><th>动作</th><th>玩家</th><th>UUID</th><th>IP</th></tr></thead><tbody>
        ${entries.map((e) => `<tr class="click" data-player="${esc(e.name)}">
          <td class="tiny nowrap">${fmtTime(e.t)}</td>
          <td>${e.action === 'join' ? '<span class="badge on">上线</span>' : '<span class="badge off">下线</span>'}</td>
          <td><div class="pcell">${avatar(e.name, e.uuid, 24)}<span>${esc(e.name)}</span></div></td>
          <td class="mono tiny">${esc(String(e.uuid).slice(0, 8))}…</td>
          <td class="mono tiny">${esc(e.ip)}</td></tr>`).join('')}
        </tbody></table></div>` : '<div class="empty">没有匹配的记录</div>'}
    </div>`;

  const search = () => {
    l.q = $('lq').value.trim(); l.action = $('laction').value; l.limit = Number($('llimit').value);
    draw();
  };
  $('lq').onkeydown = (e) => { if (e.key === 'Enter') search(); };
  $('laction').onchange = search;
  $('llimit').onchange = search;

  el().querySelectorAll('[data-player]').forEach((row) => { row.onclick = () => openPlayer(row.dataset.player); });
}

/* ------------------------------------------------------------------ 视图：模块 */

async function viewModules() {
  markNav();
  setHead('功能模块', state.current ? '开关 ' + serverName(state.current) + ' 的模块' : '请先选择一台服务器');
  loading();

  if (!state.current) return needServerHint();

  const data = await proxy('/api/features');
  state.modules = asArray(data.features, 'features');

  el().innerHTML = `
    <div class="card">
      <div class="card-head">
        <h3>${esc(serverName(state.current))}：${state.modules.filter((f) => f.enabled).length}/${state.modules.length} 启用</h3>
        <button class="btn primary" id="save-modules">保存并重载</button>
      </div>
      <div class="card-body flush">
        <div class="tw"><table>
          <thead><tr><th style="width:56px">开关</th><th>模块</th><th>id</th><th>说明</th></tr></thead><tbody>
          ${state.modules.map((f) => `<tr>
            <td><label class="check"><input type="checkbox" data-f="${esc(f.id)}" ${f.enabled ? 'checked' : ''}></label></td>
            <td>${esc(f.name)}</td>
            <td class="mono tiny">${esc(f.id)}</td>
            <td class="tiny wrap">${esc(f.description || '—')}</td></tr>`).join('')}
          </tbody></table></div>
      </div>
    </div>
    <div class="hint-card tiny">
      关掉 <code>remote-api</code> 会让这台服从 Hub 上"失联"（Hub 再也连不上它的管理接口），
      面板里会显示为离线 —— 这是预期行为，适合你想彻底关掉远程管理的时候。
    </div>`;

  $('save-modules').onclick = async () => {
    const features = {};
    el().querySelectorAll('[data-f]').forEach((c) => { features[c.dataset.f] = c.checked; });
    try {
      const r = await proxy('/api/config', { method: 'POST', body: { features } });
      toast(`已应用 ${(r.changed || []).length} 个模块开关`);
      draw();
    } catch (e) { toast(e.message, 'error'); }
  };
}

/* ------------------------------------------------------------------ 视图：智能审计 */

const ACTION_NAMES = { 0: '放置', 1: '破坏', 2: '放入', 3: '取出', 4: '击杀', 5: '丢出', 6: '捡起' };

async function viewInspect() {
  markNav();
  setHead('智能审计', state.current ? '审计 ' + serverName(state.current) + ' 的操作记录' : '请先选择一台服务器');
  loading();

  if (!state.current) return needServerHint();

  const st = await proxy('/api/inspector');

  el().innerHTML = `
    <div class="grid g4">
      <div class="stat b"><div class="k">已记录</div><div class="v">${st.written || 0}</div><div class="n">累计落库的操作</div></div>
      <div class="stat"><div class="k">待写入</div><div class="v">${st.pending || 0}</div><div class="n">写队列中</div></div>
      <div class="stat"><div class="k">自动丢弃</div><div class="v">${st.dropped || 0}</div><div class="n">超限丢弃（防刷屏）</div></div>
      <div class="stat"><div class="k">保留</div><div class="v">${st.retentionDays || 0}<small> 天</small></div><div class="n">过期自动清理</div></div>
    </div>

    <div class="card">
      <div class="card-head">
        <h3>查询操作记录</h3>
        <span class="tiny">玩家=${st.logPlayer ? '开' : '关'} · 自动化=${st.logAutomation ? '开' : '关'} · 容器=${st.logContainers ? '开' : '关'} · 击杀=${st.logKills ? '开' : '关'}</span>
      </div>
      <div class="card-body">
        <div class="bar" style="padding:0 0 12px;border:0;background:transparent">
          <input id="iq" type="text" placeholder="玩家名（留空 = 全部）" style="flex:1 1 180px">
          <select id="iaction">
            <option value="">全部动作</option>
            <option value="0">放置</option><option value="1">破坏</option>
            <option value="2">放入</option><option value="3">取出</option>
            <option value="4">击杀</option>
          </select>
          <button class="btn primary" id="iquery">查询</button>
        </div>
        <div id="iresult" class="tiny">输入玩家名（或留空）查询最近的操作记录</div>
      </div>
    </div>
  `;

  $('iquery').onclick = () => runInspectQuery();
  $('iq').onkeydown = (e) => { if (e.key === 'Enter') runInspectQuery(); };
}

async function runInspectQuery() {
  const box = $('iresult');
  const actor = $('iq').value.trim();
  const action = $('iaction').value;
  const params = new URLSearchParams();
  if (actor) params.set('actor', actor);
  if (action) params.set('action', action);
  params.set('limit', '30');

  box.innerHTML = '<span class="dim">查询中…</span>';
  try {
    const data = await proxy('/api/inspector/lookup?' + params.toString());
    const rows = asArray(data.rows, 'rows');
    if (!rows.length) {
      box.innerHTML = '<div class="empty">没有匹配的记录</div>';
      return;
    }
    box.innerHTML = `<div class="tw"><table>
      <thead><tr><th>时间</th><th>来源</th><th>动作</th><th>对象</th><th>数量</th><th>坐标</th></tr></thead><tbody>
      ${rows.map((r) => `<tr>
        <td class="tiny nowrap">${fmtTime(r.time)}</td>
        <td>${esc(r.actor)}${r.source !== 0 ? ' <span class="badge">自动</span>' : ''}</td>
        <td>${ACTION_NAMES[r.action] || r.action}</td>
        <td class="mono tiny">${esc(r.material)}${r.hasNbt ? ' <span class="badge ac">NBT</span>' : ''}</td>
        <td class="tiny">${r.amount > 1 ? r.amount : ''}</td>
        <td class="mono tiny">${r.x}, ${r.y}, ${r.z}</td></tr>`).join('')}
      </tbody></table></div>`;
  } catch (e) {
    box.innerHTML = `<div class="hint-card">查询失败：${esc(e.message)}</div>`;
  }
}

/* ------------------------------------------------------------------ 反作弊：会话控制台 */

// guard 会话状态 → 面板文案与徽章（字段见 guard 模块 IntegrityFeature.registerApi）
const GUARD_STATES = {
  CHALLENGE_SENT: { txt: '握手中', cls: 'ac' },
  VERIFIED: { txt: '已通过', cls: 'on' },
  FAILED: { txt: '未通过', cls: 'dg' },
  CLOSED: { txt: '已断开', cls: 'off' },
};

async function viewGuardConsole() {
  setHead('反作弊 · 会话控制台', '所有在线服务器的实时校验会话');
  markNav();
  loading();

  const data = await hub('/hub/api/guard/sessions');
  const g = state.guard.sessions;
  g.items = asArray(data.items, 'items');
  g.failures = asArray(data.failures, 'failures');

  const counts = { VERIFIED: 0, CHALLENGE_SENT: 0, FAILED: 0 };
  let flagged = 0;
  for (const it of g.items) {
    const s = it.session || {};
    if (counts[s.state] !== undefined) counts[s.state]++;
    if ((s.signals || 0) > 0) flagged++;
  }

  el().innerHTML = `
    <div class="grid g4">
      <div class="stat a"><div class="k">已通过校验</div><div class="v">${counts.VERIFIED}</div><div class="n">全服合计</div></div>
      <div class="stat b"><div class="k">握手中</div><div class="v">${counts.CHALLENGE_SENT}</div><div class="n">正在完成挑战</div></div>
      <div class="stat d"><div class="k">未通过</div><div class="v">${counts.FAILED}</div><div class="n">校验失败（将被处置）</div></div>
      <div class="stat"><div class="k">携带信号</div><div class="v">${flagged}</div><div class="n">报告过可疑信号的玩家</div></div>
    </div>

    ${g.failures.length ? `
      <div class="fleet-note" style="padding:10px 15px">有 ${g.failures.length} 台服查询失败：${g.failures.map((f) => esc(f.server + '：' + f.message)).join('；')}</div>` : ''}

    <div class="card">
      <div class="card-head">
        <h3>在线会话（${g.items.length}）</h3>
        <span class="tiny">每 15 秒自动刷新 · 点击行打开玩家报告</span>
      </div>
      ${g.items.length ? `<div class="tw"><table>
        <thead><tr><th>玩家</th><th>校验状态</th><th>服务器</th><th>Mod</th><th>信号</th>
          <th>机器码</th><th>网卡</th><th>QQ</th><th></th></tr></thead><tbody>
        ${g.items.map((it, i) => {
          const s = it.session || {};
          const st = GUARD_STATES[s.state] || { txt: s.state || '—', cls: 'off' };
          const signals = Number(s.signals) || 0;
          return `<tr class="click" data-gidx="${i}">
            <td><div class="pcell">${avatar(s.name, s.uuid, 28)}
              <div><div>${esc(s.name || '—')}</div><div class="uuid mono">${esc(String(s.uuid || '').slice(0, 8))}…</div></div></div></td>
            <td><span class="badge ${st.cls}">${st.txt}</span>${s.fails ? ` <span class="badge dg">失败 ${s.fails} 次</span>` : ''}</td>
            <td><span class="badge">${esc(it.server)}</span></td>
            <td class="tiny">${s.mods ?? '—'}</td>
            <td>${signals ? `<span class="badge dg">${signals}</span>` : '<span class="tiny">0</span>'}</td>
            <td class="mono tiny">${esc(s.machine || '未采集')}</td>
            <td class="tiny">${s.macs ?? '—'}</td>
            <td class="tiny">${s.qq ?? '—'}</td>
            <td class="nowrap">
              <button class="btn sm" data-gshot="${i}">截图</button>
              <button class="btn sm" data-gscan="${i}">深度扫描</button>
              <button class="btn sm danger" data-gban="${i}">封禁</button>
            </td>
          </tr>`;
        }).join('')}
        </tbody></table></div>` : `
        <div class="empty">
          ${g.failures.length ? '没有任何在线会话（部分服务器查询失败，见上方说明）' : '当前没有玩家在线，或各服务器未安装 voidkit_guard'}
        </div>`}
    </div>`;

  const byIdx = (i) => {
    const it = g.items[i];
    return it ? { it, s: it.session || {} } : null;
  };

  el().querySelectorAll('tr[data-gidx]').forEach((tr) => {
    tr.onclick = () => {
      const found = byIdx(Number(tr.dataset.gidx));
      if (!found || !found.s.name) return;
      state.current = found.it.serverId;
      state.guard.report.player = found.s.name;
      renderSelector();
      setView('guard-report');
    };
  });
  el().querySelectorAll('[data-gshot]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const found = byIdx(Number(b.dataset.gshot));
      if (found) requestScreenshot(found.it.serverId, found.s.name);
    };
  });
  el().querySelectorAll('[data-gscan]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const found = byIdx(Number(b.dataset.gscan));
      if (found) requestDeepScan(found.it.serverId, found.s.name);
    };
  });
  el().querySelectorAll('[data-gban]').forEach((b) => {
    b.onclick = (e) => {
      e.stopPropagation();
      const found = byIdx(Number(b.dataset.gban));
      if (found) banPlayerForm(found.it.serverId, found.s.name);
    };
  });
}

/* ------------------------------------------------------------------ 反作弊：操作 */

/** 请求截屏（静默），随后轮询截屏记录，回传后直接在抽屉里打开看图。 */
async function requestScreenshot(serverId, name) {
  if (!await confirmBox('请求截屏', `对 ${name} 发起截屏？截屏在客户端无提示完成，完成后自动在面板中打开。`, '请求截屏')) return;
  const since = Date.now();
  try {
    await proxy('/api/guard/screenshots', { server: serverId, method: 'POST', body: { player: name, reason: '面板截屏' } });
  } catch (e) { toast(e.message, 'error'); return; }
  toast('已请求截屏，等待客户端回传…');

  let tries = 0;
  const timer = setInterval(async () => {
    if (++tries > 12) { clearInterval(timer); toast('等待截屏回传超时，可稍后在该玩家的截屏记录中查看', 'warn'); return; }
    let fresh = null;
    try {
      const data = await proxy('/api/guard/screenshots?limit=5&player=' + encodeURIComponent(name), { server: serverId });
      fresh = asArray(data.screenshots, 'screenshots').find((r) => Number(r.t) >= since) || null;
    } catch (e) { return; /* 轮询失败静默重试 */ }
    if (fresh) { clearInterval(timer); openScreenshot(serverId, fresh); }
  }, 2500);
}

/** 某玩家的截屏记录 + 看图抽屉。 */
async function openScreenshotList(serverId, name) {
  state.drawer = 'shots:' + name;
  $('drawer').classList.add('open');
  const panel = $('drawer-panel');
  panel.innerHTML = `
    <div class="dhead"><div><h3>截屏记录 · ${esc(name)}</h3><div class="uuid mono">${esc(serverName(serverId))}</div></div>
      <button class="btn sm" id="d-close">关闭</button></div>
    <div class="dsec"><div class="empty">加载中…</div></div>`;
  $('d-close').onclick = closeDrawer;

  let rows = [];
  try {
    const data = await proxy('/api/guard/screenshots?limit=20&player=' + encodeURIComponent(name), { server: serverId });
    rows = asArray(data.screenshots, 'screenshots');
  } catch (e) {
    panel.querySelector('.dsec').innerHTML = `<div class="empty">${esc(e.message)}</div>`;
    return;
  }
  if (state.drawer !== 'shots:' + name) return;

  panel.querySelector('.dsec').innerHTML = rows.length ? `<div class="tw"><table>
    <thead><tr><th>时间</th><th>尺寸</th><th>大小</th><th>事由</th><th></th></tr></thead><tbody>
    ${rows.map((r) => `<tr>
      <td class="tiny nowrap">${fmtTime(r.t)}</td>
      <td class="tiny mono">${r.width}x${r.height}</td>
      <td class="tiny">${Math.round((r.bytes || 0) / 1024)}KB</td>
      <td class="tiny">${esc(r.reason || '—')}</td>
      <td><button class="btn sm" data-idx="${rows.indexOf(r)}">查看</button></td>
    </tr>`).join('')}
    </tbody></table></div>` : '<div class="empty">没有截屏记录</div>';

  panel.querySelectorAll('[data-idx]').forEach((btn) => {
    btn.onclick = () => openScreenshot(serverId, rows[Number(btn.dataset.idx)]);
  });
}

/** 单张截屏：base64 渲染进抽屉，点图放大。 */
async function openScreenshot(serverId, row) {
  state.drawer = 'shot:' + row.id;
  $('drawer').classList.add('open');
  const panel = $('drawer-panel');
  panel.innerHTML = `
    <div class="dhead"><div><h3>截屏 · ${esc(row.player)}</h3>
      <div class="uuid mono">${fmtTime(row.t)} · ${row.width}x${row.height} · ${esc(serverName(serverId))}</div></div>
      <button class="btn sm" id="d-close">关闭</button></div>
    <div class="dsec"><div class="empty">加载中…</div></div>`;
  $('d-close').onclick = closeDrawer;

  let data;
  try {
    data = await proxy('/api/guard/screenshots/' + encodeURIComponent(row.id), { server: serverId });
  } catch (e) {
    panel.querySelector('.dsec').innerHTML = `<div class="empty">${esc(e.message)}</div>`;
    return;
  }
  if (state.drawer !== 'shot:' + String(row.id) || !data.base64) return;

  const src = 'data:image/png;base64,' + data.base64;
  panel.querySelector('.dsec').innerHTML = `<img class="shot" src="${src}" alt="玩家截屏">`;
  panel.querySelector('img.shot').onclick = () => showImageModal(src, '截屏 · ' + row.player);
}

function showImageModal(src, title) {
  $('modal-card').innerHTML = `
    <h3>${esc(title)}</h3>
    <img class="shot-full" src="${src}" alt="截屏大图">
    <div class="row" style="justify-content:flex-end;margin-top:12px"><button class="btn" id="m-no">关闭</button></div>`;
  $('modal').classList.add('open');
  $('m-no').onclick = closeModal;
}

/** 下发深度扫描。 */
async function requestDeepScan(serverId, name) {
  if (!await confirmBox('深度扫描', `对 ${name} 下发深度扫描？客户端将回传本机 IP、文件夹清单与心跳 RTT。`, '下发扫描')) return;
  try {
    await proxy('/api/guard/scan', { server: serverId, method: 'POST', body: { player: name, reason: '面板扫描' } });
    toast('已下发深度扫描，结果稍后见「玩家报告」');
    state.guard.report.player = name;
  } catch (e) { toast(e.message, 'error'); }
}

/** 封禁表单（target 支持玩家名 / UUID / IP / 机器码 / MAC / QQ，guard 自动识别）。 */
async function banPlayerForm(serverId, target) {
  const v = await formModal('封禁', `目标：${target}。guard 会自动封禁该玩家的全部关联身份（IP / 机器码 / 网卡 / QQ）。`, [
    { name: 'reason', label: '原因', value: '面板封禁' },
    { name: 'days', label: '时长（天，0 = 永久）', type: 'number', value: '0' },
  ], '确认封禁', true);
  if (!v) return;
  try {
    const r = await proxy('/api/guard/bans', {
      server: serverId, method: 'POST',
      body: { target, reason: v.reason || '面板封禁', days: Number(v.days) || 0, admin: 'panel' },
    });
    toast(r.message || '已封禁');
  } catch (e) { toast(e.message, 'error'); }
}

/* ------------------------------------------------------------------ 反作弊：封禁管理 */

async function viewGuardBans() {
  markNav();
  const srv = serverById(state.current);
  setHead('反作弊 · 封禁管理', state.current ? '封禁记录来自 ' + srv.name : '请先选择一台服务器');
  if (!state.current) { el().innerHTML = ''; return needServerHint(); }

  const b = state.guard.bans;
  const data = await proxy('/api/guard/bans?limit=200');
  b.items = asArray(data.bans, 'bans');
  b.total = data.total || b.items.length;

  el().innerHTML = `
    <div class="card">
      <div class="card-head">
        <h3>封禁记录（${b.items.length}${b.total > b.items.length ? ' / 共 ' + b.total : ''}）</h3>
        <button class="btn primary" id="g-add-ban">手动封禁</button>
      </div>
      <div class="bar">
        <input id="g-bq" type="text" placeholder="按关键字过滤（值 / 原因 / 管理员）" value="${esc(b.q)}">
        <select id="g-btype">
          ${[['', '全部类型'], ['name', '玩家名'], ['uuid', 'UUID'], ['ip', 'IP'], ['machine', '机器码'], ['mac', '网卡 MAC'], ['qq', 'QQ']]
            .map(([k, t]) => `<option value="${k}" ${b.type === k ? 'selected' : ''}>${t}</option>`).join('')}
        </select>
        <span class="tiny">过滤在本页记录上进行</span>
      </div>
      <div id="g-bans-result"></div>
    </div>
    <div class="hint-card tiny">
      guard 的封禁是「身份级」的：命中任意一条（IP / 主板机器码 / 网卡 MAC / QQ / UUID）都会被拒之门外。
      封禁数据在各服自己的数据库里 —— 换一台服务器请在右上角切换。
    </div>`;

  const renderBans = () => {
    const box = $('g-bans-result');
    const kw = b.q.toLowerCase();
    const list = b.items.filter((r) => {
      const kt = String(r.keyType || '');
      if (b.type === 'mac' && !kt.startsWith('mac')) return false;
      if (b.type === 'qq' && !kt.startsWith('qq')) return false;
      if (b.type && b.type !== 'mac' && b.type !== 'qq' && kt !== b.type) return false;
      if (kw && ![r.keyValue, r.reason, r.admin].some((v) => String(v || '').toLowerCase().includes(kw))) return false;
      return true;
    });

    box.innerHTML = list.length ? `<div class="tw"><table>
      <thead><tr><th>#</th><th>类型</th><th>值</th><th>原因</th><th>管理员</th><th>创建</th><th>到期</th><th></th></tr></thead><tbody>
      ${list.map((r) => {
        const kt = String(r.keyType || '');
        const typeName = kt.startsWith('mac') ? '网卡' : kt.startsWith('qq') ? 'QQ' : ({ name: '玩家名', uuid: 'UUID', ip: 'IP', machine: '机器码' }[kt] || kt || '—');
        return `<tr>
          <td class="tiny mono">${esc(r.id)}</td>
          <td><span class="badge">${esc(typeName)}</span></td>
          <td class="mono tiny wrap">${esc(r.keyValue || '—')}</td>
          <td>${esc(r.reason || '—')}</td>
          <td class="tiny">${esc(r.admin || '—')}</td>
          <td class="tiny nowrap">${fmtTime(r.created)}</td>
          <td class="tiny nowrap">${Number(r.expires) > 0 ? fmtTime(r.expires) : '永久'}</td>
          <td><button class="btn sm danger" data-lift="${esc(r.id)}">解封</button></td>
        </tr>`;
      }).join('')}
      </tbody></table></div>` : '<div class="empty">没有匹配的封禁记录</div>';

    box.querySelectorAll('[data-lift]').forEach((btn) => {
      btn.onclick = async () => {
        if (!await confirmBox('解除封禁', `确定解除封禁 #${btn.dataset.lift}？该身份将可以重新进服。`, '解封', true)) return;
        try {
          await proxy('/api/guard/bans/lift', { method: 'POST', body: { id: Number(btn.dataset.lift) } });
          toast('已解封');
          draw();
        } catch (e) { toast(e.message, 'error'); }
      };
    });
  };

  const search = () => {
    b.q = $('g-bq').value.trim();
    b.type = $('g-btype').value;
    renderBans();
  };
  $('g-bq').onkeydown = (e) => { if (e.key === 'Enter') search(); };
  $('g-btype').onchange = search;
  $('g-add-ban').onclick = async () => {
    const v = await formModal('手动封禁', 'target 支持玩家名 / UUID / IP / 机器码 / MAC / QQ，guard 自动识别。', [
      { name: 'target', label: '封禁目标', placeholder: '玩家名、UUID、1.2.3.4 …' },
      { name: 'reason', label: '原因', value: '面板封禁' },
      { name: 'days', label: '时长（天，0 = 永久）', type: 'number', value: '0' },
    ], '确认封禁', true);
    if (!v || !v.target.trim()) return;
    try {
      const r = await proxy('/api/guard/bans', {
        method: 'POST',
        body: { target: v.target.trim(), reason: v.reason || '面板封禁', days: Number(v.days) || 0, admin: 'panel' },
      });
      toast(r.message || '已封禁');
      draw();
    } catch (e) { toast(e.message, 'error'); }
  };

  renderBans();
}

/* ------------------------------------------------------------------ 反作弊：玩家报告 */

const GUARD_LEVELS = {
  CLEAN: { cls: 'lv-clean', txt: '正常' },
  SUSPICIOUS: { cls: 'lv-susp', txt: '可疑' },
  LIKELY: { cls: 'lv-likely', txt: '大概率作弊' },
  CONFIRMED: { cls: 'lv-conf', txt: '确证作弊' },
};

async function viewGuardReport() {
  markNav();
  setHead('反作弊 · 玩家报告', '综合判定：结论、评分与判定依据全部可见');
  const g = state.guard.report;

  el().innerHTML = `
    <div class="card">
      <div class="bar">
        <input id="g-rq" type="text" placeholder="玩家名（报告来自右上角选中的服务器）" value="${esc(g.player)}">
        <button class="btn primary" id="g-run">查看报告</button>
        ${g.player ? '<button class="btn" id="g-rescan">深度扫描</button><button class="btn" id="g-shots">截屏记录</button>' : ''}
      </div>
      <div id="g-report-body">${g.player ? '<div class="empty">加载中…</div>' : '<div class="empty">输入玩家名查看综合判定报告</div>'}</div>
    </div>`;

  $('g-run').onclick = () => { g.player = $('g-rq').value.trim(); draw(); };
  $('g-rq').onkeydown = (e) => { if (e.key === 'Enter') { g.player = $('g-rq').value.trim(); draw(); } };
  const rescan = $('g-rescan');
  if (rescan) rescan.onclick = async () => {
    await requestDeepScan(state.current, g.player);
    if (g.player) draw();
  };
  const shots = $('g-shots');
  if (shots) shots.onclick = () => openScreenshotList(state.current, g.player);

  if (!g.player) return;
  const box = $('g-report-body');

  let r;
  try {
    r = await proxy('/api/guard/report/' + encodeURIComponent(g.player));
  } catch (e) {
    box.innerHTML = `<div class="empty">${esc(e.message)}<br><span class="tiny">没有记录的常见原因：玩家从未进过这台服，或未安装 voidkit_guard</span></div>`;
    return;
  }

  const lv = GUARD_LEVELS[String(r.level || '').toUpperCase()] || { cls: '', txt: r.levelDisplay || r.level || '未知' };
  const lines = (r.lines || []).map(plain).filter(Boolean);
  const reasons = (r.reasons || []).map(plain);

  // 环境画像：报告的 lines 里有一条「环境：OS｜CPU｜GPU｜内存｜时区」，拆出来单独展示
  const envLine = lines.find((l) => l.startsWith('环境：'));
  const envParts = envLine ? envLine.replace(/^环境：/, '').split('｜').map((s) => s.trim()) : [];
  const envNames = ['操作系统', 'CPU', 'GPU', '内存', '时区'];
  const otherLines = lines.filter((l) => l !== envLine);

  const deep = r.deep || null;

  box.innerHTML = `
    <div class="card-body">
      <div class="verdict">
        <span class="badge lg ${lv.cls}">${esc(lv.txt)}</span>
        <span class="score">${Number(r.score) || 0}<small> 分</small></span>
        <span class="tiny">评分与依据全部来自 guard 的加权判定，不做黑盒</span>
      </div>

      <h4 class="sect">判定依据（${reasons.length}）</h4>
      ${reasons.length ? `<div class="reasons">
        ${reasons.map((x) => `<div class="reason"><span class="dot-state down"></span>${esc(x)}</div>`).join('')}
      </div>` : '<div class="tiny" style="padding:4px 0 2px">没有发现可疑线索</div>'}

      <h4 class="sect">档案与环境</h4>
      ${envParts.length ? `<dl class="kv" style="grid-template-columns:96px 1fr">
        ${envParts.map((v, i) => `<dt>${envNames[i] || '其他'}</dt><dd>${esc(v)}</dd>`).join('')}
      </dl>` : ''}
      <div class="reasons" style="margin-top:10px">
        ${otherLines.map((l) => `<div class="reason plain">${esc(l)}</div>`).join('')}
      </div>

      <h4 class="sect">深度扫描</h4>
      ${deep ? `
        <dl class="kv" style="grid-template-columns:96px 1fr">
          <dt>心跳 RTT</dt><dd>${deep.rttMs ?? '—'} ms</dd>
          <dt>IP 列表</dt><dd>${(deep.ips || []).length ? (deep.ips || []).map((ip) => `<span class="badge">${esc(ip)}</span>`).join(' ') : '—'}</dd>
          <dt>文件条目</dt><dd>${(deep.files || []).length} 条</dd>
        </dl>
        ${(deep.files || []).length ? `<details class="files"><summary>展开 ${deep.files.length} 条文件记录</summary>
          <div class="mono tiny wrap" style="line-height:1.9">${deep.files.map((f) => esc(f)).join('<br>')}</div>
        </details>` : ''}
        <div class="tiny" style="margin-top:8px">进程 / 窗口黑名单命中情况体现在上方「判定依据」里（确证信号 +50/条）。</div>
      ` : '<div class="tiny" style="padding:4px 0 2px">没有深度扫描记录 —— 用顶部「深度扫描」按钮下发一次。</div>'}
    </div>`;
}

/* ------------------------------------------------------------------ 反作弊：审计日志 */

// guard 审计事件类型 → 面板徽章（type 取值见 guard 模块各 Feature 里的 hub.audit 调用）
const GUARD_AUDIT_TYPES = {
  guard_join: { txt: '进服', cls: 'ac' },
  guard_quit: { txt: '退服', cls: 'off' },
  guard_pass: { txt: '校验通过', cls: 'on' },
  guard_fail: { txt: '校验失败', cls: 'dg' },
  guard_suspect: { txt: '可疑信号', cls: 'dg' },
  guard_rechallenge: { txt: '复检', cls: 'ac' },
  ban_add: { txt: '封禁', cls: 'dg' },
  ban_block: { txt: '拦截', cls: 'dg' },
  ban_lift: { txt: '解封', cls: 'on' },
  screen_req: { txt: '截屏请求', cls: 'ac' },
  screen_done: { txt: '截屏完成', cls: 'on' },
  screen_timeout: { txt: '截屏超时', cls: 'off' },
  deep_req: { txt: '深扫下发', cls: 'ac' },
  deep_done: { txt: '深扫回传', cls: 'on' },
  key_rotate: { txt: '令牌轮换', cls: '' },
};

/** 详情是 JSON 字符串，表格里截断展示，悬浮 title 看全文。 */
const brief = (s, n) => {
  const t = String(s || '');
  return t.length > n ? t.slice(0, n) + '…' : t;
};

async function viewGuardAudit() {
  setHead('反作弊 · 审计日志', '所有在线服务器的审计事件流水（进服/校验/封禁/截屏等）');
  markNav();
  loading();

  const a = state.guard.audit;
  const params = new URLSearchParams({ limit: String(a.limit) });
  if (a.type) params.set('type', a.type);
  const data = await hub('/hub/api/guard/audit?' + params);
  a.events = asArray(data.events, 'events');
  a.failures = asArray(data.failures, 'failures');
  // Hub 已按时间倒序汇总；这里再排一次，防上游顺序变化
  a.events.sort((x, y) => (Number(y.t) || 0) - (Number(x.t) || 0));

  el().innerHTML = `
    ${a.failures.length ? `
      <div class="fleet-note" style="padding:10px 15px">有 ${a.failures.length} 台服查询失败：${a.failures.map((f) => esc(f.server + '：' + f.message)).join('；')}</div>` : ''}

    <div class="card">
      <div class="bar">
        <select id="g-atype">
          <option value="">全部类型</option>
          ${Object.entries(GUARD_AUDIT_TYPES)
            .map(([k, v]) => `<option value="${k}" ${a.type === k ? 'selected' : ''}>${v.txt}</option>`).join('')}
        </select>
        <select id="g-alimit">${[50, 100, 200, 500].map((n) => `<option ${a.limit === n ? 'selected' : ''}>${n}</option>`).join('')}</select>
        <span class="tiny">共 ${a.events.length} 条 · 按时间倒序</span>
      </div>
      ${a.events.length ? `<div class="tw"><table>
        <thead><tr><th>时间</th><th>服务器</th><th>类型</th><th>操作者</th><th>目标</th><th>详情</th></tr></thead><tbody>
        ${a.events.map((e) => {
          const ty = GUARD_AUDIT_TYPES[e.type] || { txt: e.type || '—', cls: '' };
          return `<tr>
            <td class="tiny nowrap">${fmtTime(e.t)}</td>
            <td><span class="badge">${esc(e.server || '—')}</span></td>
            <td><span class="badge ${ty.cls}">${esc(ty.txt)}</span></td>
            <td class="tiny">${esc(e.actor || '—')}</td>
            <td class="tiny">${esc(e.target || '—')}</td>
            <td class="mono tiny wrap" title="${esc(e.detail || '')}">${esc(brief(e.detail, 120)) || '—'}</td>
          </tr>`;
        }).join('')}
        </tbody></table></div>` : `
        <div class="empty">
          ${a.failures.length ? '没有任何审计事件（部分服务器查询失败，见上方说明）' : '还没有审计事件 —— 各服可能未安装 voidkit_guard 或审计模块未启用'}
        </div>`}
    </div>`;

  $('g-atype').onchange = () => { a.type = $('g-atype').value; draw(); };
  $('g-alimit').onchange = () => { a.limit = Number($('g-alimit').value); draw(); };
}

/* ------------------------------------------------------------------ 视图：服务器管理 */

async function refreshServers() {
  const data = await hub('/hub/api/servers');
  state.servers = asArray(data.servers, 'servers');
  // 单服优先：默认选中唯一/第一台服务器。大多数服只有一台，玩家/日志/模块页
  // 不该还要用户先「选服务器」。选中项被删了也自动回落到第一台。
  if (!state.servers.some((s) => s.id === state.current)) {
    state.current = state.servers.length ? state.servers[0].id : '';
  }
  renderSelector();
}

async function viewServers() {
  markNav();
  setHead('服务器', 'Hub 管理的游戏服清单');
  loading();

  await refreshServers();

  el().innerHTML = `
    <div class="card">
      <div class="card-head">
        <h3>受管服务器（${state.servers.length}）</h3>
        <button class="btn primary" id="add-server">添加服务器</button>
      </div>
      <div class="card-body flush">
        ${state.servers.length ? `<div class="tw"><table>
          <thead><tr><th>名称</th><th>接口地址</th><th>状态</th><th>玩家</th><th>延迟</th><th>标签</th><th></th></tr></thead><tbody>
          ${state.servers.map((s) => {
            const st = s.state || {};
            const badge = !s.enabled
              ? '<span class="badge off">已停用</span>'
              : (st.online ? '<span class="badge on">在线</span>' : '<span class="badge dg">离线</span>');
            return `<tr>
              <td><strong>${esc(s.name)}</strong>${s.note ? `<div class="tiny">${esc(s.note)}</div>` : ''}</td>
              <td class="mono tiny">${esc(s.api)}</td>
              <td>${badge}${st.error ? `<div class="tiny" style="color:#ffb3b9">${esc(st.error)}</div>` : ''}</td>
              <td class="tiny">${st.online ? st.players + '/' + st.maxPlayers : '—'}</td>
              <td class="tiny">${st.online ? st.latencyMs + 'ms' : '—'}</td>
              <td>${(s.tags || []).map((t) => `<span class="badge">${esc(t)}</span>`).join(' ') || '<span class="tiny">—</span>'}</td>
              <td class="nowrap">
                <button class="btn sm" data-probe="${esc(s.id)}">探活</button>
                <button class="btn sm" data-edit="${esc(s.id)}">编辑</button>
                <button class="btn sm danger" data-del="${esc(s.id)}">删除</button>
              </td>
            </tr>`;
          }).join('')}
          </tbody></table></div>` : `
          <div class="empty">
            还没有服务器。点右上角「添加服务器」，把每台游戏服的<br>
            <code>http://地址:端口</code> 和它的 <code>apiToken</code> 填进来。
          </div>`}
      </div>
    </div>

    <div class="hint-card">
      <strong>每台游戏服要做的准备</strong><br>
      1. 装好 <code>voidkit</code> 模组，并确保 <code>remote-api</code> 模块是<b>开启</b>的<br>
      2. 把 <code>apiBind</code> 改成 <code>0.0.0.0</code>（默认只监听 127.0.0.1，Hub 连不上）<br>
      3. 在游戏里执行 <code>/kcore token</code> 拿到令牌，填到上面<br>
      4. 放行对应端口；跨公网建议用 HTTPS 反代 —— 令牌是明文传输的
    </div>`;

  $('add-server').onclick = () => editServer(null);

  el().querySelectorAll('[data-probe]').forEach((btn) => {
    btn.onclick = async () => {
      btn.disabled = true; btn.textContent = '…';
      try { await hub('/hub/api/servers/' + encodeURIComponent(btn.dataset.probe) + '/probe', { method: 'POST', body: {} }); await draw(); }
      catch (e) { toast(e.message, 'error'); btn.disabled = false; btn.textContent = '探活'; }
    };
  });
  el().querySelectorAll('[data-edit]').forEach((btn) => {
    btn.onclick = () => editServer(serverById(btn.dataset.edit));
  });
  el().querySelectorAll('[data-del]').forEach((btn) => {
    btn.onclick = async () => {
      const s = serverById(btn.dataset.del);
      if (!await confirmBox('删除服务器', `确定把「${s ? s.name : btn.dataset.del}」从 Hub 移除？不会影响那台服本身。`, '删除', true)) return;
      try { await hub('/hub/api/servers/' + encodeURIComponent(btn.dataset.del) + '/delete', { method: 'POST', body: {} }); toast('已移除'); draw(); }
      catch (e) { toast(e.message, 'error'); }
    };
  });
}

async function editServer(srv) {
  const isNew = !srv;
  const values = await formModal(
    isNew ? '添加服务器' : '编辑 ' + srv.name,
    isNew ? null : '令牌留空表示不修改。',
    [
      { name: 'name', label: '名称', value: srv ? srv.name : '', placeholder: '生存一区' },
      { name: 'api', label: '模组接口地址', value: srv ? srv.api : '', placeholder: 'http://10.0.0.5:8765', hint: '填游戏服上 voidkit 的 API 地址（不是游戏端口）' },
      { name: 'token', label: '访问令牌', type: 'password', value: '', placeholder: srv && srv.hasToken ? '已配置，留空不改' : '游戏内 /kcore token' },
      { name: 'tags', label: '标签（逗号分隔）', value: srv ? (srv.tags || []).join(', ') : '', placeholder: '生存, 主城' },
      { name: 'note', label: '备注', value: srv ? (srv.note || '') : '' },
      { name: 'enabled', label: '参与轮询（关掉后 Hub 不再主动连它）', type: 'checkbox', value: srv ? srv.enabled : true },
    ],
    isNew ? '添加' : '保存',
  );
  if (!values) return;

  const body = {
    name: values.name.trim(),
    api: values.api.trim(),
    token: values.token.trim(),
    tags: String(values.tags || '').split(',').map((t) => t.trim()).filter(Boolean),
    note: values.note.trim(),
    enabled: values.enabled,
  };
  if (!body.name || !body.api) { toast('名称和接口地址都要填', 'warn'); return; }

  try {
    const path = isNew ? '/hub/api/servers' : '/hub/api/servers/' + encodeURIComponent(srv.id);
    const result = await hub(path, { method: 'POST', body });
    const st = result.state;
    toast(st && st.online ? `已保存，连接正常（${st.latencyMs}ms）` : `已保存，但连不上：${(st && st.error) || '未知原因'}`, st && st.online ? '' : 'warn');
    await draw();
  } catch (e) { toast(e.message, 'error'); }
}

/* ------------------------------------------------------------------ 通用 */

function needServerHint() {
  const online = state.servers.filter((s) => s.enabled && s.state && s.state.online);
  el().innerHTML = `
    <div class="hint-card">
      <strong>这个页面需要先选定一台服务器</strong><br>
      用右上角的「当前服务器」下拉框选一台，或者直接点下面的快捷入口。
      <div class="chips">
        ${online.length
          ? online.map((s) => `<span class="chip" data-pick="${esc(s.id)}">${esc(s.name)}</span>`).join('')
          : '<span class="tiny">当前没有在线的服务器</span>'}
        <span class="chip" data-goto-servers>管理服务器清单</span>
      </div>
    </div>`;
  el().querySelectorAll('[data-pick]').forEach((chip) => {
    chip.onclick = () => { state.current = chip.dataset.pick; renderSelector(); draw(); };
  });
  const goto = el().querySelector('[data-goto-servers]');
  if (goto) goto.onclick = () => setView('servers');
}

function closeDrawer() {
  state.drawer = null;
  $('drawer').classList.remove('open');
  $('drawer-panel').innerHTML = '';
}

const VIEWS = {
  fleet: viewFleet,
  players: viewPlayers,
  logs: viewLogs,
  modules: viewModules,
  servers: viewServers,
  inspect: viewInspect,
  'guard-console': viewGuardConsole,
  'guard-bans': viewGuardBans,
  'guard-report': viewGuardReport,
  'guard-audit': viewGuardAudit,
};

async function draw() {
  markNav();
  try {
    await VIEWS[state.view]();
    $('refreshed').textContent = '更新于 ' + new Date().toLocaleTimeString('zh-CN');
  } catch (e) {
    toast(e.message, 'error');
    el().innerHTML = `<div class="hint-card">加载失败：${esc(e.message)}</div>`;
  }
}

function setView(name) {
  state.view = name;
  draw();
}

/* ------------------------------------------------------------------ 登录 */

function showLogin(message) {
  if (state.timer) clearInterval(state.timer);
  state.timer = null;
  $('app').classList.add('hidden');
  $('login').classList.remove('hidden');
  $('login-error').textContent = message || '';
}

function logout() {
  state.pass = '';
  localStorage.removeItem(LS_KEY);
  closeDrawer();
  showLogin('已退出');
}

async function connect(base, user, pass) {
  state.base = String(base).replace(/\/+$/, '');
  state.user = user;
  state.pass = pass;

  const health = await hub('/hub/api/health');
  localStorage.setItem(LS_KEY, JSON.stringify({ base: state.base, user, pass }));

  $('login').classList.add('hidden');
  $('app').classList.remove('hidden');

  await refreshServers();
  state.view = 'fleet';
  await draw();

  if (state.timer) clearInterval(state.timer);
  // 总览页与反作弊控制台自动刷新；Hub 自己也在后台轮询，这里只是把缓存的新值读回来
  state.timer = setInterval(() => {
    if (document.hidden) return;
    if (state.view === 'fleet' || state.view === 'guard-console') draw();
  }, 15000);

  if (health.servers === 0) toast('还没有受管服务器，去「服务器」页添加第一台', 'warn');
}

/* ------------------------------------------------------------------ 启动 */

function boot() {
  document.querySelectorAll('#nav button').forEach((b) => { b.onclick = () => setView(b.dataset.view); });
  $('refresh').onclick = () => draw();
  $('logout').onclick = () => logout();
  $('drawer').onclick = (e) => { if (e.target === $('drawer')) closeDrawer(); };
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    if ($('modal').classList.contains('open')) closeModal();
    else if (state.drawer) closeDrawer();
  });

  $('login-form').onsubmit = async (e) => {
    e.preventDefault();
    const user = $('login-user').value.trim();
    const pass = $('login-pass').value;
    if (!user || !pass) { $('login-error').textContent = '用户名和密码都要填'; return; }
    try {
      await connect($('login-base').value.trim(), user, pass);
      $('login-error').textContent = '';
    } catch (err) { $('login-error').textContent = err.message; }
  };

  const guess = location.protocol.startsWith('http') ? location.origin : 'http://127.0.0.1:9000';
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(LS_KEY) || 'null'); } catch (e) { saved = null; }

  $('login-base').value = (saved && saved.base) || guess;
  $('login-user').value = (saved && saved.user) || 'admin';
  $('login-pass').value = (saved && saved.pass) || '';
  if (saved && saved.pass) {
    connect(saved.base || guess, saved.user || 'admin', saved.pass).catch(() => showLogin('保存的登录已失效，请重新登录'));
  }
}

document.addEventListener('DOMContentLoaded', boot);
