let worksetRequest = 0;
let trackerRequest = 0;
let contentRequest = 0;

function parseWorksetColor(value) {
  const hex = typeof value === 'string' ? value.trim().toUpperCase() : '';
  if (!/^#(?:[0-9A-F]{3,4}|[0-9A-F]{6}|[0-9A-F]{8})$/.test(hex)) return null;
  let digits = hex.slice(1);
  if (digits.length <= 4) digits = [...digits].map((digit) => digit.repeat(2)).join('');
  return { hex, rgb: '#' + digits.slice(0, 6), alpha: parseInt(digits.slice(6) || 'FF', 16) / 255 };
}

function applyWorksetTheme(value) {
  const color = parseWorksetColor(value) ?? parseWorksetColor('#FFFF');
  const white = [255, 255, 255];
  const ink = [0, 0, 0];
  const mix = (from, to, amount) => from.map((channel, i) => Math.round(channel + (to[i] - channel) * amount));
  const rgb = [1, 3, 5].map((i) => parseInt(color.rgb.slice(i, i + 2), 16));
  const fill = mix(white, rgb, color.alpha);
  const soft = mix(white, fill, .17);
  const luminance = (channels) => channels.reduce((total, channel, i) => {
    const x = channel / 255;
    return total + [0.2126, 0.7152, 0.0722][i] * (x <= .04045 ? x / 12.92 : ((x + .055) / 1.055) ** 2.4);
  }, 0);
  let accent = fill;
  const softLuminance = luminance(soft);
  while ((softLuminance + .05) / (luminance(accent) + .05) < 4.5) {
    accent = mix(accent, ink, .18);
  }
  const onFill = 1.05 / (luminance(fill) + .05) >= 4.5 ? white : ink;
  const tones = {
    '--accent': accent,
    '--accent-fill': fill,
    '--accent-on-fill': onFill,
    '--accent-soft': soft,
    '--accent-edge': mix(white, accent, .37),
    '--accent-hover': mix(fill, onFill === white ? ink : white, .12),
    '--workset-bg': mix([245, 246, 248], fill, .1)
  };
  for (const [name, channels] of Object.entries(tones)) {
    document.documentElement.style.setProperty(name, `rgb(${channels.join(', ')})`);
  }
}

function applySelectedWorksetTheme() {
  applyWorksetTheme(findNode(state.worksets, state.selectedWs)?.ws?.color);
}

function showPane(pane, moveFocus = false) {
  const layout = $('.layout');
  layout.dataset.activePane = pane;
  document.querySelectorAll('[data-pane]').forEach((button) => {
    const active = button.dataset.pane === pane;
    button.setAttribute('aria-pressed', String(active));
    if (active && moveFocus && button.offsetWidth) button.focus();
  });
}

function setTrackerPanelCollapsed(collapsed) {
  $('.layout').dataset.sidebarCollapsed = String(collapsed);
  $('#trackerPaneContent').classList.toggle('hidden', collapsed);
  const button = $('#toggleTrackerPanel');
  const label = collapsed ? '展开跟踪分类' : '收起跟踪分类';
  button.setAttribute('aria-expanded', String(!collapsed));
  button.setAttribute('aria-label', label);
  button.title = label;
}

async function loadWorksetTree() {
  setStatus('加载工作集…');
  const seen = new Set();
  async function build(id) {
    if (seen.has(id) || seen.size >= 500) return null;
    seen.add(id);
    let ws;
    try {
      ws = await api('/api/worksets/' + encodeURIComponent(id));
    } catch (error) {
      return { id, error, children: [] };
    }
    let childIds = [];
    try {
      childIds = await api('/api/worksets/' + encodeURIComponent(id) + '/children') || [];
    } catch {
      childIds = [];
    }
    const children = (await Promise.all(childIds.map(build))).filter(Boolean);
    return { id, ws, children };
  }
  const root = await build('1');
  state.worksets = root ? [root] : [];
  applySelectedWorksetTheme();
  renderWorksetTree();
  setStatus(root && !root.error ? '工作集已加载' : '无法加载默认工作集', root && !root.error ? 'ok' : 'error');
}

function renderWorksetTree() {
  const container = $('#worksetTree');
  container.innerHTML = '';
  let count = 0;
  const walk = (node, depth) => {
    count += 1;
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'tree-node' + (state.selectedWs === node.id ? ' selected' : '');
    row.dataset.wsId = node.id;
    row.setAttribute('aria-current', state.selectedWs === node.id ? 'true' : 'false');
    row.title = node.id;
    row.style.paddingLeft = (9 + Math.min(depth, 10) * 14) + 'px';
    const label = node.ws?.name || (node.ws?.defaultWorkingSet ? '默认工作集 (1)' : shortId(node.id));
    row.innerHTML = `<span class="tree-dot"></span><span class="tree-label">${escapeHtml(label)}</span>` +
      (node.children.length ? `<span class="badge count">${node.children.length}</span>` : '') +
      (node.error ? '<span class="badge unbound">不可用</span>' : '');
    const color = parseWorksetColor(node.ws?.color);
    if (color) {
      row.querySelector('.tree-dot').style.setProperty('--workset-dot', color.hex);
    }
    container.appendChild(row);
    node.children.forEach((child) => walk(child, depth + 1));
  };
  state.worksets.forEach((node) => walk(node, 0));
  $('#worksetCount').textContent = count ? count + ' 个' : '';
  if (!count) container.innerHTML = '<div class="empty">暂无工作集</div>';
}

async function selectWorkset(id) {
  const request = ++worksetRequest;
  ++trackerRequest;
  ++contentRequest;
  state.selectedWs = id;
  applySelectedWorksetTheme();
  state.selectedSpace = null;
  clearTrackerSelection();
  state.bindings = { spaces: [], trackers: [] };
  state.trackers = [];
  $('#activeWorkset').textContent = worksetLabel(id);
  $('#createWorksetBtn').classList.toggle('hidden', id !== '1');
  $('#extendWorksetBtn').classList.toggle('hidden', !findNode(state.worksets, id)?.ws?.parentWorkingSetId);
  renderWorksetTree();
  renderSpaces();
  renderSpaceTrackers();
  renderTracker();
  renderItems();
  setStatus('加载 ' + shortId(id) + ' 的绑定范围…');

  try {
    const base = '/api/worksets/' + encodeURIComponent(id);
    const [bindings, trackers] = await Promise.all([api(base + '/bindings'), api(base + '/trackers')]);
    if (request !== worksetRequest) return;
    state.bindings = bindings || { spaces: [], trackers: [] };
    state.trackers = trackers || [];
    renderSpaces();
    renderSpaceTrackers();
    const bound = (state.bindings.trackers || []).length;
    setStatus('已加载 ' + (state.bindings.spaces || []).length + ' 个空间 · ' + state.trackers.length + ' 个 跟踪分类（绑定 ' + bound + '）', 'ok');
  } catch (error) {
    if (request !== worksetRequest) return;
    renderSpaces();
    setStatus(reasonText(error.body) || error.message, 'error');
  }
}

function worksetLabel(id, fallback = shortId(id)) {
  const node = findNode(state.worksets, id);
  const ws = node?.ws;
  return ws?.name || (ws?.defaultWorkingSet ? '默认工作集' : fallback);
}

function currentSpaces() {
  const spaces = new Map();
  (state.bindings?.spaces || []).forEach((space) => {
    spaces.set(space.productSpaceId, { ...space, bound: true });
  });
  state.trackers.forEach((tracker) => {
    if (!spaces.has(tracker.productSpaceId)) {
      spaces.set(tracker.productSpaceId, {
        productSpaceId: tracker.productSpaceId,
        origin: null,
        state: null,
        bound: false
      });
    }
  });
  return spaces;
}

function renderSpaces() {
  const list = $('#spaceList');
  const spaces = currentSpaces();
  const selectedSpace = spaces.get(state.selectedSpace);
  const activeSpace = $('#activeSpace');
  activeSpace.textContent = selectedSpace ? selectedSpace.name || shortId(state.selectedSpace) : '请选择';
  activeSpace.title = selectedSpace ? activeSpace.textContent : '';
  if (!state.selectedWs) {
    list.innerHTML = '<div class="empty">请先选择工作集</div>';
    $('#spaceCount').textContent = '';
    return;
  }
  if (!spaces.size) {
    list.innerHTML = '<div class="empty">该工作集还没有绑定产品空间或 跟踪分类</div>';
    $('#spaceCount').textContent = '';
    return;
  }

  const boundIds = new Set((state.bindings?.trackers || []).map((item) => item.trackerId));
  let html = '';
  spaces.forEach((space, spaceId) => {
    const trackers = state.trackers.filter((tracker) => tracker.productSpaceId === spaceId);
    const boundCount = trackers.filter((tracker) => boundIds.has(tracker.trackerId)).length;
    const selected = state.selectedSpace === spaceId;
    const origin = space.origin || (space.bound ? '' : '未绑定空间');
    const spaceName = space.name || shortId(spaceId);
    const meta = [space.name ? spaceId : '', origin, space.state].filter(Boolean).join(' · ');
    html += `
      <div class="space-card ${selected ? 'selected' : ''}">
        <button type="button" class="space-head" data-space-id="${escapeHtml(spaceId)}" aria-current="${selected}" title="${escapeHtml(spaceId)}">
          <span class="space-main">
            <span class="space-id">${escapeHtml(spaceName)}</span>
            ${meta ? `<span class="space-meta">${escapeHtml(meta)}</span>` : ''}
            <span class="space-meta">${trackers.length} 个 跟踪分类${boundCount !== trackers.length ? ' · 已绑定 ' + boundCount : ''}</span>
          </span>
          <span class="chevron" aria-hidden="true">›</span>
        </button>
      </div>`;
  });
  list.innerHTML = html;
  $('#spaceCount').textContent = spaces.size + ' 个空间';
}

function renderSpaceTrackers() {
  const space = currentSpaces().get(state.selectedSpace);
  const create = $('#createTrackerBtn');
  create.classList.toggle('hidden', state.selectedWs !== '1');
  create.disabled = !space?.bound;
  create.title = space?.bound ? '新建跟踪分类' : '请先选择已绑定的产品空间';
  const heading = $('#selectedSpace');
  const list = $('#trackerList');
  heading.classList.toggle('hidden', !space);
  if (!space) {
    list.innerHTML = '<div class="empty item-empty"><strong>尚未选择产品空间</strong><span>选择产品空间后查看当前工作集下的 跟踪分类。</span></div>';
    $('#trackerCount').textContent = '';
    return;
  }
  const trackers = state.trackers.filter((tracker) => tracker.productSpaceId === space.productSpaceId);
  const boundIds = new Set((state.bindings?.trackers || []).map((item) => item.trackerId));
  heading.innerHTML = `<div class="ws-title">${escapeHtml(space.name || space.productSpaceId)}</div>
    <div class="ws-meta">${escapeHtml(worksetLabel(state.selectedWs))}</div>`;
  list.innerHTML = renderTrackerTree(trackers, boundIds);
  $('#trackerCount').textContent = trackers.length + ' 个';
}

function clearTrackerSelection() {
  state.selectedTracker = null;
  state.selectedItem = null;
  state.items = [];
  state.itemsLoading = false;
  state.itemsError = null;
  state.content = null;
  state.contentLoading = false;
  state.contentError = null;
  $('#itemFilter').value = '';
}

function selectSpace(spaceId) {
  if (spaceId !== state.selectedSpace) {
    state.selectedSpace = spaceId;
    ++trackerRequest;
    ++contentRequest;
    clearTrackerSelection();
    renderSpaces();
    renderTracker();
    renderItems();
  }
  renderSpaceTrackers();
}

function renderTrackerTree(trackers, boundIds) {
  if (!trackers.length) return '<div class="empty small">该空间下没有 跟踪分类</div>';

  const byId = new Map(trackers.map((tracker) => [tracker.trackerId, tracker]));
  const childrenOf = new Map();
  const roots = [];
  trackers.forEach((tracker) => {
    const parent = tracker.parentId && byId.has(tracker.parentId) && tracker.parentId !== tracker.trackerId
      ? tracker.parentId : null;
    if (parent) {
      if (!childrenOf.has(parent)) childrenOf.set(parent, []);
      childrenOf.get(parent).push(tracker);
    } else {
      roots.push(tracker);
    }
  });

  const visited = new Set();
  const sort = (list) => list.sort((a, b) => String(a.trackerId).localeCompare(String(b.trackerId)));
  const walk = (tracker, depth) => {
    if (visited.has(tracker.trackerId)) return '';
    visited.add(tracker.trackerId);
    const kids = sort(childrenOf.get(tracker.trackerId) || []);
    const selected = state.selectedTracker?.trackerId === tracker.trackerId ? ' selected' : '';
    const bound = boundIds.has(tracker.trackerId);
    const trackerName = tracker.name || shortId(tracker.trackerId);
    return `
      <button type="button" class="tracker-node${selected}" data-tracker-id="${escapeHtml(tracker.trackerId)}"
           aria-current="${Boolean(selected)}"
           style="padding-left:${8 + Math.min(depth, 8) * 14}px" title="${escapeHtml(tracker.trackerId)}">
        <span class="tracker-label">${escapeHtml(trackerName)}</span>
        <span class="badge ${tracker.sharingMode === 'SHARED' ? 'shared' : 'unshared'}">${tracker.sharingMode === 'SHARED' ? '共享' : '非共享'}</span>
        ${tracker.isDefault ? '<span class="badge">默认</span>' : ''}
        ${bound ? '' : '<span class="badge unbound">未绑定</span>'}
      </button>` + kids.map((child) => walk(child, depth + 1)).join('');
  };

  let html = sort(roots).map((tracker) => walk(tracker, 0)).join('');
  if (!html) {
    html = sort(trackers).map((tracker) => walk(tracker, 0)).join('');
  }
  return html || '<div class="empty small">该空间下没有可显示的 跟踪分类</div>';
}

async function selectTracker(trackerId, { keepFilter = false } = {}) {
  const tracker = state.trackers.find((item) => item.trackerId === trackerId);
  if (!tracker) return;
  const request = ++trackerRequest;
  ++contentRequest;
  const workingSetId = state.selectedWs;
  if (!keepFilter && state.selectedTracker?.trackerId !== trackerId) $('#itemFilter').value = '';

  state.selectedTracker = tracker;
  state.selectedSpace = tracker.productSpaceId;
  state.selectedItem = null;
  state.content = null;
  state.contentLoading = false;
  state.contentError = null;
  state.items = [];
  state.itemsError = null;

  const bound = (state.bindings?.trackers || []).some((item) => item.trackerId === trackerId);
  renderSpaces();
  renderSpaceTrackers();
  renderTracker();

  if (!bound) {
    state.itemsLoading = false;
    state.itemsError = '该 跟踪分类 未绑定到当前工作集，无法读取 Item';
    renderItems();
    setStatus(state.itemsError, 'error');
    return;
  }

  state.itemsLoading = true;
  renderItems();
  setStatus('加载 跟踪分类 Item…');
  try {
    const requirements = await api('/api/requirements?trackerId=' + encodeURIComponent(trackerId) +
      '&workingSetId=' + encodeURIComponent(workingSetId)) || [];
    if (request !== trackerRequest) return;
    state.items = (requirements || []).map((requirement) => ({
      ...requirement,
      workingSetId,
      name: requirement.title,
      requirement
    }));
    setStatus('已加载 ' + state.items.length + ' 个 Item', 'ok');
  } catch (error) {
    if (request !== trackerRequest) return;
    state.itemsError = reasonText(error.body) || error.message;
    setStatus(state.itemsError, 'error');
  } finally {
    if (request === trackerRequest) {
      state.itemsLoading = false;
      renderItems();
    }
  }
}

function renderTracker() {
  const tracker = state.selectedTracker;
  const bound = tracker && (state.bindings?.trackers || []).some((item) => item.trackerId === tracker.trackerId);
  const canCompare = tracker?.sharingMode === 'UNSHARED' && (bound || state.selectedWs === '1');
  $('#itemActions').classList.toggle('hidden', !bound && !canCompare);
  $('[data-action="new-requirement"]').classList.toggle('hidden', !bound);
  $('[data-action="compare-tracker"]').classList.toggle('hidden', !canCompare);
  $('#itemContext').classList.toggle('hidden', !tracker);
  if (!tracker) return;

  const space = currentSpaces().get(tracker.productSpaceId);
  const names = {
    itemWorksetName: worksetLabel(state.selectedWs, '未命名工作集'),
    itemSpaceName: space?.name || '未命名产品空间',
    itemTrackerName: tracker.name || '未命名跟踪分类'
  };
  for (const [id, name] of Object.entries(names)) {
    const label = document.getElementById(id);
    label.textContent = name;
    label.title = name;
  }
}

function renderItems() {
  const area = $('#itemArea');
  $('#itemToolbar').classList.toggle('hidden', !state.items.length);
  area.setAttribute('aria-busy', String(state.itemsLoading));
  renderItemDetail();
  if (!state.selectedTracker) {
    area.innerHTML = '<div class="empty item-empty">选择跟踪分类后查看 Item</div>';
    $('#itemCount').textContent = '';
    return;
  }
  if (state.itemsLoading) {
    area.innerHTML = '<div class="empty item-empty" role="status"><span class="spinner" aria-hidden="true"></span>正在加载 Item…</div>';
    $('#itemCount').textContent = '';
    return;
  }
  if (state.itemsError) {
    area.innerHTML = `<div class="empty error-text">${escapeHtml(state.itemsError)}</div>`;
    $('#itemCount').textContent = '';
    return;
  }
  if (!state.items.length) {
    area.innerHTML = '<div class="empty item-empty"><strong>暂无 Item</strong><span>可从上方新建需求。</span></div>';
    $('#itemCount').textContent = '0 个';
    return;
  }

  const query = $('#itemFilter').value.trim().toLocaleLowerCase();
  const visibleItems = state.items.filter((item) => [item.name, item.number, item.localId, item.commonId]
    .some((value) => String(value ?? '').toLocaleLowerCase().includes(query)));
  $('#itemCount').textContent = query ? visibleItems.length + ' / ' + state.items.length + ' 个' : state.items.length + ' 个';
  if (!visibleItems.length) {
    area.innerHTML = '<div class="empty item-empty"><strong>没有匹配的 Item</strong><button type="button" class="btn small" data-clear-item-filter>清除筛选</button></div>';
    return;
  }
  const rows = visibleItems.map((item) => {
    const selected = state.selectedItem?.localId === item.localId ? ' selected' : '';
    return `
      <tr class="${selected}" data-local-id="${escapeHtml(item.localId)}" data-item-ws="${escapeHtml(item.workingSetId)}">
        <td><button type="button" class="item-open" aria-controls="itemDetail" aria-expanded="${Boolean(selected)}">${escapeHtml(item.name || shortId(item.commonId))}</button>
          <div class="item-identity mono" title="${escapeHtml(item.localId)}">${escapeHtml(item.number || shortId(item.localId))}</div></td>
        <td class="item-revision">${escapeHtml(item.revision)}</td>
        <td>${item.materialized ? '<span class="badge shared">本地物化</span>' : '<span class="badge source">来源继承</span>'}</td>
        <td><div class="row-actions">
          <button type="button" class="btn small" data-edit-requirement="${escapeHtml(item.localId)}">编辑</button>
          <button type="button" class="btn small danger" data-delete-requirement="${escapeHtml(item.localId)}">删除</button>
          </div>
        </td>
      </tr>`;
  }).join('');

  area.innerHTML = `
    <table class="item-table">
      <thead>
        <tr><th>名称 / 编号</th><th class="item-revision">版次</th><th>内容状态</th><th>操作</th></tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>`;
}

function renderItemDetail() {
  const detail = $('#itemDetail');
  const item = state.selectedItem;
  detail.classList.toggle('hidden', !item);
  detail.setAttribute('aria-busy', String(state.contentLoading));
  $('#itemArea').querySelectorAll('[data-local-id]').forEach((row) => {
    const active = row.dataset.localId === item?.localId;
    row.classList.toggle('selected', active);
    row.querySelector('.item-open').setAttribute('aria-expanded', String(active));
  });
  if (!item) {
    detail.innerHTML = '';
    return;
  }
  const content = state.content;
  const rows = Object.entries(content?.fields || {}).map(([key, value]) =>
    `<dt>${escapeHtml(({ title: '标题', description: '描述' })[key] ?? key)}</dt><dd>${escapeHtml(formatValue(value))}</dd>`).join('');
  const header = `<header class="detail-head">
    <h3 class="detail-title">${escapeHtml(item.name || 'Item 详情')}</h3>
    <button type="button" class="modal-close" data-close-detail aria-label="关闭 Item 详情">×</button>
  </header>`;
  const body = state.contentLoading ? '<div class="empty small" role="status">正在加载内容…</div>'
    : state.contentError ? `<div class="empty small error-text">${escapeHtml(state.contentError)}</div>`
    : `<div class="detail-meta">
      ${item.number ? `<span>编号 <span class="mono">${escapeHtml(item.number)}</span></span>` : ''}
      <span>Local ID <span class="mono">${escapeHtml(item.localId)}</span></span>
      <span>Common ID <span class="mono">${escapeHtml(item.commonId)}</span></span>
      <span>版次 ${escapeHtml(item.revision ?? '—')}</span>
      <span>内容来源 <span class="mono">${escapeHtml(content?.contentLocalId || '—')}</span> · 版次 ${escapeHtml(content?.contentRevision ?? '—')}</span>
      </div><dl class="fields">${rows || '<dt>内容</dt><dd>—</dd>'}</dl>`;
  const focused = detail.contains(document.activeElement);
  detail.innerHTML = header + body;
  if (focused) detail.querySelector('[data-close-detail]').focus();
}

async function loadContent(localId, workingSetId) {
  const request = ++contentRequest;
  state.selectedItem = state.items.find((item) => item.localId === localId) || { localId, workingSetId };
  state.content = null;
  state.contentError = null;
  state.contentLoading = true;
  renderItemDetail();
  try {
    const requirement = await api('/api/requirements/' + encodeURIComponent(localId));
    const item = state.selectedItem;
    if (request === contentRequest) {
      state.content = {
        fields: { title: requirement.title, description: requirement.description },
        materialized: requirement.materialized,
        contentLocalId: requirement.materialized ? localId : (item?.sourceLocalId || localId),
        contentRevision: requirement.materialized ? requirement.revision : (item?.sourceRevision ?? requirement.revision)
      };
    }
  } catch (error) {
    if (request === contentRequest) state.contentError = reasonText(error.body) || error.message;
  } finally {
    if (request === contentRequest) {
      state.contentLoading = false;
      renderItemDetail();
    }
  }
}
