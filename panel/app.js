/* =======================================================================
   VoidkitCore 管理台 —— 轻量前端
   与模组完全分离：这里只消费 HTTP API，可部署在任意静态服务器上。
   无框架、无构建、无外部依赖。
   ======================================================================= */

'use strict';

const LS_KEY = 'voidkit.panel';
const COLORS = {
  0: '#000000', 1: '#0000AA', 2: '#00AA00', 3: '#00AAAA', 4: '#AA0000', 5: '#AA00AA',
  6: '#FFAA00', 7: '#AAAAAA', 8: '#555555', 9: '#5555FF', a: '#55FF55', b: '#55FFFF',
  c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF',
};

const state = {
  base: '', token: '', view: 'overview', timer: null, drawer: null, groups: [],
  features: [], players: { items: [], total: 0, offset: 0, limit: 20, q: '', online: false, sort: 'lastSeen' },
  logs: { entries: [], action: '', q: '', limit: 100 },
};

const $ = (id) => document.getElementById(id);
const el = () => $('view');

/* ------------------------------------------------------------------ 工具 */

const esc = (v) => String(v === null || v === undefined ? '' : v)
  .replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

/** 把 &a / §a 颜色码渲染成带颜色的 HTML。 */
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

/** 用名字/UUID 稳定生成头像底色，不依赖任何外部头像服务。 */
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
  setTimeout(() => { box.style.opacity = '0'; box.style.transition = 'opacity .3s'; setTimeout(() => box.remove(), 320); },
    kind === 'error' ? 5000 : 2800);
}

/* ------------------------------------------------------------------ 请求 */

async function api(path, options) {
  const opts = options || {};
  const headers = { 'X-Auth-Token': state.token };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(state.base + path, {
      method: opts.method || 'GET',
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    });
  } catch (e) {
    throw new Error('无法连接服务端，请确认服务器已启动且接口地址正确');
  }
  if (res.status === 401) { logout(); throw new Error('令牌无效，请重新连接'); }

  const text = await res.text();
  let data = null;
  if (text) { try { data = JSON.parse(text); } catch (e) { data = null; } }
  if (!res.ok) throw new Error((data && (data.message || data.error)) || `请求失败（HTTP ${res.status}）`);
  return data || {};
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

function promptBox(title, fields, okText, danger) {
  return new Promise((resolve) => {
    $('modal-card').innerHTML = `
      <h3>${esc(title)}</h3>
      ${fields.map((f, i) => `<label class="field"><span>${esc(f.label)}</span>
        <input id="mf-${i}" type="${esc(f.type || 'text')}" value="${esc(f.value === undefined ? '' : f.value)}"
          placeholder="${esc(f.placeholder || '')}"></label>`).join('')}
      <div class="row" style="justify-content:flex-end">
        <button class="btn" id="m-no">取消</button>
        <button class="btn ${danger ? 'danger' : 'primary'}" id="m-yes">${esc(okText || '确定')}</button>
      </div>`;
    $('modal').classList.add('open');
    if (fields[0]) $('mf-0').focus();
    $('m-no').onclick = () => { closeModal(); resolve(null); };
    $('m-yes').onclick = () => {
      const values = {};
      fields.forEach((f, i) => { values[f.name] = $('mf-' + i).value; });
      closeModal();
      resolve(values);
    };
  });
}

/* ------------------------------------------------------------------ 视图：概览 */

async function viewOverview() {
  setHead('概览', '服务器实时状态与快捷运维');
  el().innerHTML = '<div class="card"><div class="empty">加载中…</div></div>';

  const [stats, features, logs] = await Promise.all([
    api('/api/stats'), api('/api/features'), api('/api/logs/joins?limit=10'),
  ]);
  state.features = features.features || [];
  $('foot-online').textContent = `在线 ${stats.online}/${stats.maxPlayers}`;
  $('foot-tps').textContent = `TPS ${stats.avgTps}`;

  const enabled = state.features.filter((f) => f.enabled).length;

  el().innerHTML = `
    <div class="grid g4">
      <div class="stat a"><div class="k">在线玩家</div>
        <div class="v">${stats.online}<small> / ${stats.maxPlayers}</small></div>
        <div class="n">历史档案 ${stats.totalProfiles} 份</div></div>
      <div class="stat b"><div class="k">平均 TPS</div><div class="v">${stats.avgTps}</div>
        <div class="n">tick ${stats.tickCount}</div></div>
      <div class="stat"><div class="k">内存占用</div>
        <div class="v">${fmtMb(stats.memoryUsedMb)}<small> / ${fmtMb(stats.memoryMaxMb)}</small></div>
        <div class="n">使用率 ${stats.memoryMaxMb ? Math.round(stats.memoryUsedMb / stats.memoryMaxMb * 100) : 0}%</div></div>
      <div class="stat"><div class="k">运行时长</div><div class="v">${fmtDuration(stats.uptimeMs / 1000)}</div>
        <div class="n">${esc(stats.version)}</div></div>
    </div>

    <div class="grid g2">
      <div class="card">
        <div class="card-head"><h3>快捷操作</h3>
          <span class="badge ${stats.apiRunning ? 'on' : 'dg'}">API ${stats.apiRunning ? '运行中' : '未启动'}</span></div>
        <div class="card-body">
          <label class="field"><span>全服公告（支持 &amp;a 颜色代码）</span>
            <input id="broadcast" placeholder="输入公告内容"></label>
          <div class="row">
            <button class="btn primary" id="do-broadcast">发送公告</button>
            <button class="btn" id="do-reload">重载配置</button>
            <button class="btn" id="do-clear">立即清扫</button>
          </div>
          <hr style="border:0;border-top:1px solid var(--line);margin:15px 0">
          <dl class="kv">
            <dt>MOTD</dt><dd>${mc(stats.motd)}</dd>
            <dt>功能模块</dt><dd>${enabled} / ${state.features.length} 已启用</dd>
            <dt>接口地址</dt><dd class="mono wrap">${esc(state.base)}</dd>
          </dl>
        </div>
      </div>

      <div class="card">
        <div class="card-head"><h3>最近动态</h3><button class="btn sm" id="go-logs">全部日志</button></div>
        <div class="card-body flush">
          ${(logs.entries || []).length ? `<div class="tw"><table>
            <thead><tr><th>时间</th><th>动作</th><th>玩家</th><th>IP</th></tr></thead><tbody>
            ${logs.entries.map((e) => `<tr class="click" data-player="${esc(e.name)}">
              <td class="tiny nowrap">${fmtTime(e.t)}</td>
              <td>${e.action === 'join' ? '<span class="badge on">上线</span>' : '<span class="badge off">下线</span>'}</td>
              <td><div class="pcell">${avatar(e.name, e.uuid, 24)}<span>${esc(e.name)}</span></div></td>
              <td class="mono tiny">${esc(e.ip)}</td></tr>`).join('')}
            </tbody></table></div>` : '<div class="empty">暂无日志</div>'}
        </div>
      </div>
    </div>`;

  $('do-broadcast').onclick = async () => {
    const message = $('broadcast').value.trim();
    if (!message) return toast('公告内容不能为空', 'warn');
    try { await api('/api/broadcast', { method: 'POST', body: { message } }); $('broadcast').value = ''; toast('公告已发送'); }
    catch (e) { toast(e.message, 'error'); }
  };
  $('do-reload').onclick = reloadServer;
  $('do-clear').onclick = clearLag;
  $('go-logs').onclick = () => setView('logs');
  bindPlayerRows();
}

/* ------------------------------------------------------------------ 视图：玩家 */

async function viewPlayers() {
  setHead('玩家管理', '查询档案、IP 历史、权限与实时操作');
  el().innerHTML = '<div class="card"><div class="empty">加载中…</div></div>';

  const p = state.players;
  const query = new URLSearchParams({
    q: p.q, online: String(p.online), sort: p.sort, limit: String(p.limit), offset: String(p.offset),
  });
  const data = await api('/api/players?' + query);
  p.items = data.players || [];
  p.total = data.total || 0;

  const from = p.total ? p.offset + 1 : 0;
  const to = Math.min(p.offset + p.limit, p.total);
  const pages = Math.max(1, Math.ceil(p.total / p.limit));

  el().innerHTML = `
    <div class="card">
      <div class="bar">
        <input id="q" type="text" placeholder="搜索名字 / 曾用名 / UUID" value="${esc(p.q)}">
        <label class="check"><input type="checkbox" id="only-online" ${p.online ? 'checked' : ''}> 只看在线</label>
        <select id="sort">
          ${[['lastSeen', '最后在线'], ['name', '名字'], ['playtime', '游戏时长'], ['joinCount', '登录次数'], ['firstJoin', '首次进入']]
            .map(([k, t]) => `<option value="${k}" ${p.sort === k ? 'selected' : ''}>${t}</option>`).join('')}
        </select>
        <select id="limit">${[20, 50, 100].map((n) => `<option ${p.limit === n ? 'selected' : ''}>${n}</option>`).join('')}</select>
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

/* ------------------------------------------------------------------ 玩家详情 */

async function openPlayer(key) {
  state.drawer = key;
  $('drawer').classList.add('open');
  const panel = $('drawer-panel');
  panel.innerHTML = '<div class="empty">加载中…</div>';

  let d, ips;
  try {
    d = (await api('/api/players/' + encodeURIComponent(key))).player;
    ips = (await api(`/api/players/${encodeURIComponent(key)}/ips`)).ips || [];
  } catch (e) {
    panel.innerHTML = `<div class="dhead"><h3>加载失败</h3><button class="btn sm" onclick="closeDrawer()">关闭</button></div>
      <div class="empty">${esc(e.message)}</div>`;
    return;
  }
  if (state.drawer !== key) return;

  const homes = d.homes || [];
  const perms = d.permissions || [];

  panel.innerHTML = `
    <div class="dhead">
      ${avatar(d.name, d.uuid, 40)}
      <div><h3>${esc(d.name)}</h3><div class="uuid mono">${esc(d.uuid)}</div></div>
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
      <h4>权限</h4>
      <div class="row">
        <select id="a-group" style="width:auto;flex:1">
          ${state.groups.map((g) => `<option value="${esc(g.name)}" ${g.name === d.group ? 'selected' : ''}>${esc(g.name)}${g.prefix ? '（' + esc(plain(g.prefix)) + '）' : ''}</option>`).join('')}
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

  if ($('a-msg')) {
    $('a-msg').onclick = async () => {
      const v = await promptBox(`私信 ${d.name}`, [{ name: 'message', label: '内容', placeholder: '支持 &a 颜色代码' }], '发送');
      if (!v || !v.message.trim()) return;
      try { await api(`/api/players/${encodeURIComponent(key)}/message`, { method: 'POST', body: v }); toast('已发送'); }
      catch (e) { toast(e.message, 'error'); }
    };
  }
  if ($('a-kick')) {
    $('a-kick').onclick = async () => {
      const v = await promptBox(`踢出 ${d.name}`, [{ name: 'reason', label: '原因', value: '被管理员移出服务器' }], '踢出', true);
      if (!v) return;
      try { await api(`/api/players/${encodeURIComponent(key)}/kick`, { method: 'POST', body: v }); toast('已踢出'); refreshDrawer(); }
      catch (e) { toast(e.message, 'error'); }
    };
  }
  if ($('a-mute')) {
    $('a-mute').onclick = async () => {
      const v = await promptBox(`禁言 ${d.name}`, [
        { name: 'minutes', label: '时长（分钟，0 = 永久）', type: 'number', value: '60' },
        { name: 'reason', label: '原因', value: '' },
      ], '禁言', true);
      if (!v) return;
      try {
        await api(`/api/players/${encodeURIComponent(key)}/mute`, {
          method: 'POST', body: { minutes: Number(v.minutes) || 0, reason: v.reason },
        });
        toast('已禁言'); refreshDrawer();
      } catch (e) { toast(e.message, 'error'); }
    };
  }
  if ($('a-unmute')) {
    $('a-unmute').onclick = async () => {
      try { await api(`/api/players/${encodeURIComponent(key)}/unmute`, { method: 'POST', body: {} }); toast('已解除禁言'); refreshDrawer(); }
      catch (e) { toast(e.message, 'error'); }
    };
  }
  $('a-group-set').onclick = async () => {
    const group = $('a-group').value;
    try { await api(`/api/players/${encodeURIComponent(key)}/group`, { method: 'POST', body: { group } }); toast('权限组已更新'); refreshDrawer(); }
    catch (e) { toast(e.message, 'error'); }
  };

  panel.querySelectorAll('[data-home]').forEach((btn) => {
    btn.onclick = async () => {
      if (!await confirmBox('删除家', `确定删除 ${d.name} 的家「${btn.dataset.home}」？`, '删除', true)) return;
      try {
        await api(`/api/players/${encodeURIComponent(key)}/homes/${encodeURIComponent(btn.dataset.home)}/delete`, { method: 'POST', body: {} });
        toast('已删除'); refreshDrawer();
      } catch (e) { toast(e.message, 'error'); }
    };
  });
}

function closeDrawer() {
  state.drawer = null;
  $('drawer').classList.remove('open');
  $('drawer-panel').innerHTML = '';
}

function refreshDrawer() {
  if (state.drawer) openPlayer(state.drawer);
  if (state.view === 'players') draw();
}

/* ------------------------------------------------------------------ 视图：日志 */

async function viewLogs() {
  setHead('上下线日志', '来自 logs/joins.jsonl 的原始事件流');
  el().innerHTML = '<div class="card"><div class="empty">加载中…</div></div>';

  const l = state.logs;
  const isUuid = /^[0-9a-f-]{32,36}$/i.test(l.q);
  const query = new URLSearchParams({ limit: String(l.q ? l.limit * 4 : l.limit) });
  if (l.action) query.set('action', l.action);
  if (isUuid) query.set('uuid', l.q);

  const data = await api('/api/logs/joins?' + query);
  let entries = data.entries || [];
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

  bindPlayerRows();
}

/* ------------------------------------------------------------------ 视图：模块 */

async function viewModules() {
  setHead('功能模块', '开关任意模块，配置立即生效');
  el().innerHTML = '<div class="card"><div class="empty">加载中…</div></div>';

  const data = await api('/api/features');
  state.features = data.features || [];

  el().innerHTML = `
    <div class="card">
      <div class="card-head">
        <h3>模块清单（${state.features.filter((f) => f.enabled).length}/${state.features.length} 启用）</h3>
        <button class="btn primary" id="save-modules">保存并重载</button>
      </div>
      <div class="card-body flush">
        <div class="tw"><table>
          <thead><tr><th style="width:56px">开关</th><th>模块</th><th>id</th><th>说明</th></tr></thead><tbody>
          ${state.features.map((f) => `<tr>
            <td><label class="check"><input type="checkbox" data-f="${esc(f.id)}" ${f.enabled ? 'checked' : ''}></label></td>
            <td>${esc(f.name)}</td>
            <td class="mono tiny">${esc(f.id)}</td>
            <td class="tiny wrap">${esc(f.description || '—')}</td></tr>`).join('')}
          </tbody></table></div>
      </div>
    </div>`;

  $('save-modules').onclick = async () => {
    const features = {};
    el().querySelectorAll('[data-f]').forEach((c) => { features[c.dataset.f] = c.checked; });
    try {
      const r = await api('/api/config', { method: 'POST', body: { features } });
      toast(`已应用 ${(r.changed || []).length} 个模块开关`);
      draw();
    } catch (e) { toast(e.message, 'error'); }
  };
}

/* ------------------------------------------------------------------ 通用动作 */

async function reloadServer() {
  try { await api('/api/reload', { method: 'POST', body: {} }); toast('配置已重载'); draw(); }
  catch (e) { toast(e.message, 'error'); }
}

async function clearLag() {
  try {
    const r = await api('/api/clearlag/run', { method: 'POST', body: {} });
    toast(`清扫完成，移除 ${r.removed} 个实体`);
  } catch (e) { toast(e.message, 'error'); }
}

function bindPlayerRows() {
  el().querySelectorAll('[data-player]').forEach((row) => {
    row.onclick = () => openPlayer(row.dataset.player);
  });
}

/* ------------------------------------------------------------------ 路由 */

const VIEWS = { overview: viewOverview, players: viewPlayers, logs: viewLogs, modules: viewModules };

function setHead(title, sub) {
  $('title').textContent = title;
  $('subtitle').textContent = sub || '';
}

function markNav() {
  document.querySelectorAll('#nav button').forEach((b) => b.classList.toggle('on', b.dataset.view === state.view));
}

async function draw() {
  markNav();
  try {
    await VIEWS[state.view]();
    $('refreshed').textContent = '更新于 ' + new Date().toLocaleTimeString('zh-CN');
  } catch (e) {
    toast(e.message, 'error');
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
  state.token = '';
  localStorage.removeItem(LS_KEY);
  closeDrawer();
  showLogin('已断开连接');
}

async function connect(base, token) {
  state.base = String(base).replace(/\/+$/, '');
  state.token = token;
  const stats = await api('/api/stats');
  state.groups = (await api('/api/groups')).groups || [];
  localStorage.setItem(LS_KEY, JSON.stringify({ base: state.base, token }));

  $('login').classList.add('hidden');
  $('app').classList.remove('hidden');
  $('foot-tps').textContent = `TPS ${stats.avgTps}`;
  state.view = 'overview';
  await draw();

  if (state.timer) clearInterval(state.timer);
  state.timer = setInterval(() => {
    if (document.hidden) return;
    if (state.view === 'overview' || state.view === 'logs') draw();
  }, 10000);
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
    const token = $('login-token').value.trim();
    if (!token) { $('login-error').textContent = '请填写访问令牌'; return; }
    try {
      await connect($('login-base').value.trim(), token);
      $('login-error').textContent = '';
    } catch (err) { $('login-error').textContent = err.message; }
  };

  const guess = location.protocol.startsWith('http') ? location.origin : 'http://127.0.0.1:8765';
  let saved = null;
  try { saved = JSON.parse(localStorage.getItem(LS_KEY) || 'null'); } catch (e) { saved = null; }

  $('login-base').value = (saved && saved.base) || guess;
  $('login-token').value = (saved && saved.token) || '';
  if (saved && saved.token) {
    connect(saved.base || guess, saved.token).catch(() => showLogin('保存的连接已失效，请重新连接'));
  }
}

document.addEventListener('DOMContentLoaded', boot);
