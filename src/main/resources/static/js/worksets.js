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
  renderWorksetTree();
  setStatus(root ? '工作集已加载' : '无法加载默认工作集', root ? 'ok' : 'error');
}

function renderWorksetTree() {
  const container = $('#worksetTree');
  container.innerHTML = '';
  let count = 0;
  const walk = (node, depth) => {
    count += 1;
    const row = document.createElement('div');
    row.className = 'tree-node' + (state.selectedWs === node.id ? ' selected' : '');
    row.dataset.wsId = node.id;
    row.title = node.id;
    row.style.paddingLeft = (9 + Math.min(depth, 10) * 14) + 'px';
    const label = node.ws?.name || (node.ws?.defaultWorkingSet ? '默认工作集 (1)' : shortId(node.id));
    row.innerHTML = `<span class="tree-dot"></span><span class="tree-label">${escapeHtml(label)}</span>` +
      (node.children.length ? `<span class="badge count">${node.children.length}</span>` : '') +
      (node.error ? '<span class="badge unbound">不可用</span>' : '');
    container.appendChild(row);
    node.children.forEach((child) => walk(child, depth + 1));
  };
  state.worksets.forEach((node) => walk(node, 0));
  $('#worksetCount').textContent = count ? count + ' 个' : '';
  if (!count) container.innerHTML = '<div class="empty">暂无工作集</div>';
}

async function selectWorkset(id) {
  state.selectedWs = id;
  state.selectedSpace = null;
  state.selectedTracker = null;
  state.selectedItem = null;
  state.items = [];
  state.itemsLoading = false;
  state.itemsError = null;
  state.content = null;
  state.contentError = null;
  state.bindings = { spaces: [], trackers: [] };
  state.trackers = [];
  state.expandedSpaces.clear();
  renderWorksetTree();
  renderSpaces();
  renderTracker();
  renderItems();
  setStatus('加载 ' + shortId(id) + ' 的绑定范围…');

  try {
    const base = '/api/worksets/' + encodeURIComponent(id);
    const [bindings, trackers] = await Promise.all([api(base + '/bindings'), api(base + '/trackers')]);
    state.bindings = bindings || { spaces: [], trackers: [] };
    state.trackers = trackers || [];
    renderSpaces();
    const bound = (state.bindings.trackers || []).length;
    setStatus('已加载 ' + (state.bindings.spaces || []).length + ' 个空间 · ' + state.trackers.length + ' 个 Tracker（绑定 ' + bound + '）', 'ok');
  } catch (error) {
    renderSpaces();
    setStatus(reasonText(error.body) || error.message, 'error');
  }
}

function renderSpaces() {
  const info = $('#selectedWorkset');
  const list = $('#spaceList');

  if (!state.selectedWs) {
    info.innerHTML = '<div class="empty">从左侧选择工作集</div>';
    list.innerHTML = '';
    $('#spaceCount').textContent = '';
    return;
  }

  const node = findNode(state.worksets, state.selectedWs);
  const ws = node?.ws;
  info.innerHTML = `
    <div class="ws-title">${escapeHtml(ws?.name || (ws?.defaultWorkingSet ? '默认工作集' : shortId(state.selectedWs)))} <span class="mono">${escapeHtml(state.selectedWs)}</span></div>
    <div class="ws-meta">父级：${ws?.parentWorkingSetId ? `<span class="mono">${escapeHtml(ws.parentWorkingSetId)}</span>` : '无（根）'}</div>`;

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

  if (!spaces.size) {
    list.innerHTML = '<div class="empty">该工作集还没有绑定产品空间或 Tracker</div>';
    $('#spaceCount').textContent = '';
    return;
  }

  const boundIds = new Set((state.bindings?.trackers || []).map((item) => item.trackerId));
  let html = '';
  spaces.forEach((space, spaceId) => {
    const trackers = state.trackers.filter((tracker) => tracker.productSpaceId === spaceId);
    const boundCount = trackers.filter((tracker) => boundIds.has(tracker.trackerId)).length;
    const expanded = state.expandedSpaces.has(spaceId);
    const origin = space.origin || (space.bound ? '' : '未绑定空间');
    const spaceName = space.name || (space.bound ? shortId(spaceId) : '未绑定空间');
    html += `
      <div class="space-card ${expanded ? 'expanded' : ''}">
        <button type="button" class="space-head" data-space-toggle="${escapeHtml(spaceId)}" title="${escapeHtml(spaceId)}">
          <span class="space-main">
            <span class="space-id">${escapeHtml(spaceName)}</span>
            <span class="space-meta"><span class="mono">${escapeHtml(spaceId)}</span> · ${escapeHtml(origin)}${space.state ? ' · ' + escapeHtml(space.state) : ''} · ${trackers.length} 个 Tracker${boundCount !== trackers.length ? '（已绑定 ' + boundCount + '）' : ''}</span>
          </span>
          <span class="chevron">${expanded ? '⌄' : '›'}</span>
        </button>
        ${expanded ? `<div class="tracker-tree">${renderTrackerTree(trackers, boundIds)}</div>` : ''}
      </div>`;
  });
  list.innerHTML = html;
  $('#spaceCount').textContent = spaces.size + ' 个空间';
}

function renderTrackerTree(trackers, boundIds) {
  if (!trackers.length) return '<div class="empty small">该空间下没有 Tracker</div>';

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
      <div class="tracker-node${selected}" data-tracker-id="${escapeHtml(tracker.trackerId)}"
           style="padding-left:${8 + Math.min(depth, 8) * 14}px" title="${escapeHtml(tracker.trackerId)}">
        <span class="tracker-label">${escapeHtml(trackerName)}</span>
        <span class="badge ${tracker.sharingMode === 'SHARED' ? 'shared' : 'unshared'}">${escapeHtml(tracker.sharingMode)}</span>
        ${tracker.sourceId ? `<span class="badge source">源 ${escapeHtml(shortId(tracker.sourceId))}</span>` : ''}
        ${bound ? '' : '<span class="badge unbound">未绑定</span>'}
      </div>` + kids.map((child) => walk(child, depth + 1)).join('');
  };

  let html = sort(roots).map((tracker) => walk(tracker, 0)).join('');
  if (!html) {
    html = sort(trackers).map((tracker) => walk(tracker, 0)).join('');
  }
  return html || '<div class="empty small">该空间下没有可显示的 Tracker</div>';
}

async function selectTracker(trackerId) {
  const tracker = state.trackers.find((item) => item.trackerId === trackerId);
  if (!tracker) return;

  state.selectedTracker = tracker;
  state.selectedSpace = tracker.productSpaceId;
  state.expandedSpaces.add(tracker.productSpaceId);
  state.selectedItem = null;
  state.content = null;
  state.contentError = null;
  state.items = [];
  state.itemsError = null;

  const bound = (state.bindings?.trackers || []).some((item) => item.trackerId === trackerId);
  renderSpaces();
  renderTracker();

  if (!bound) {
    state.itemsLoading = false;
    state.itemsError = '该 Tracker 未绑定到当前工作集，无法读取 Item';
    renderItems();
    setStatus(state.itemsError, 'error');
    return;
  }

  state.itemsLoading = true;
  renderItems();
  setStatus('加载 Tracker Item…');
  try {
    state.items = await api('/api/worksets/' + encodeURIComponent(state.selectedWs) +
      '/trackers/' + encodeURIComponent(trackerId) + '/items') || [];
    setStatus('已加载 ' + state.items.length + ' 个 Item', 'ok');
  } catch (error) {
    state.itemsError = reasonText(error.body) || error.message;
    setStatus(state.itemsError, 'error');
  } finally {
    state.itemsLoading = false;
    renderItems();
  }
}

function renderTracker() {
  const area = $('#selectedTracker');
  if (!state.selectedTracker) {
    area.innerHTML = '<div class="empty">从中间选择一个 Tracker</div>';
    return;
  }
  const tracker = state.selectedTracker;
  const space = (state.bindings?.spaces || []).find((item) => item.productSpaceId === tracker.productSpaceId);
  const trackerName = tracker.name || shortId(tracker.trackerId);
  const spaceName = space?.name || tracker.productSpaceId;
  const bound = (state.bindings?.trackers || []).some((item) => item.trackerId === tracker.trackerId);
  area.innerHTML = `
    <div class="ws-title">${escapeHtml(trackerName)} <span class="mono">${escapeHtml(tracker.trackerId)}</span></div>
    <div class="ws-meta">空间 ${escapeHtml(spaceName)} · ${escapeHtml(tracker.sharingMode)} · 分类 <span class="mono">${escapeHtml(tracker.classificationId)}</span>${tracker.sourceId ? ` · 源 <span class="mono">${escapeHtml(tracker.sourceId)}</span>` : ''}</div>
    <div class="tracker-actions">
      ${bound
        ? '<button type="button" class="btn small primary" data-action="new-requirement">＋ 新建需求</button>'
        : '<span class="muted">该 Tracker 未绑定，不能新建需求</span>'}
    </div>`;
}

function renderItems() {
  const area = $('#itemArea');
  if (!state.selectedTracker) {
    area.innerHTML = '<div class="empty">选择 Tracker 后显示 Item 列表</div>';
    $('#itemCount').textContent = '';
    return;
  }
  if (state.itemsLoading) {
    area.innerHTML = '<div class="empty">加载 Item…</div>';
    $('#itemCount').textContent = '';
    return;
  }
  if (state.itemsError) {
    area.innerHTML = `<div class="empty error-text">${escapeHtml(state.itemsError)}</div>`;
    $('#itemCount').textContent = '';
    return;
  }
  if (!state.items.length) {
    area.innerHTML = '<div class="empty">该 Tracker 下没有 Item</div>';
    $('#itemCount').textContent = '0 个';
    return;
  }

  const rows = state.items.map((item) => {
    const selected = state.selectedItem?.localId === item.localId ? ' selected' : '';
    return `
      <tr class="${selected}" data-local-id="${escapeHtml(item.localId)}" data-item-ws="${escapeHtml(item.workingSetId)}">
        <td class="item-name" title="${escapeHtml(item.name || item.commonId)}">${escapeHtml(item.name || shortId(item.commonId))}</td>
        <td class="mono" title="${escapeHtml(item.localId)}">${escapeHtml(shortId(item.localId))}</td>
        <td class="mono" title="${escapeHtml(item.commonId)}">${escapeHtml(shortId(item.commonId))}</td>
        <td>${escapeHtml(item.revision)}</td>
        <td class="mono" title="${escapeHtml(item.sourceLocalId)}">${escapeHtml(shortId(item.sourceLocalId))}</td>
        <td>${escapeHtml(item.sourceRevision)}</td>
        <td>${item.materialized ? '<span class="badge shared">是</span>' : '<span class="badge unshared">否</span>'}</td>
        <td class="row-actions">
          <button type="button" class="btn small" data-edit-requirement="${escapeHtml(item.localId)}">编辑</button>
          <button type="button" class="btn small danger" data-delete-requirement="${escapeHtml(item.localId)}">删除</button>
        </td>
      </tr>`;
  }).join('');

  area.innerHTML = `
    <table>
      <thead>
        <tr><th>名称</th><th>Local ID</th><th>Common ID</th><th>Rev</th><th>来源 Local</th><th>来源 Rev</th><th>本地物化</th><th>操作</th></tr>
      </thead>
      <tbody>${rows}</tbody>
    </table>
    ${state.selectedItem ? renderItemDetail() : '<div class="hint">点击一行查看 Item 内容</div>'}`;
  $('#itemCount').textContent = state.items.length + ' 个';
}

function renderItemDetail() {
  if (state.contentLoading) {
    return '<div class="detail"><div class="empty small">加载内容…</div></div>';
  }
  if (state.contentError) {
    return `<div class="detail"><div class="empty small error-text">${escapeHtml(state.contentError)}</div></div>`;
  }
  const content = state.content;
  const fields = content?.fields || {};
  const rows = Object.entries(fields).map(([key, value]) =>
    `<dt>${escapeHtml(key)}</dt><dd>${escapeHtml(formatValue(value))}</dd>`).join('');
  return `
    <div class="detail">
      <div class="detail-title">${escapeHtml(state.selectedItem?.name || 'Item 内容')} <span class="mono">${escapeHtml(shortId(state.selectedItem?.localId))}</span></div>
      <div class="detail-meta">内容来源 <span class="mono">${escapeHtml(content?.contentLocalId || '—')}</span> · 版本 ${escapeHtml(content?.contentRevision ?? '—')} · ${content?.materialized ? '本地物化' : '来源继承'}</div>
      <dl class="fields">${rows || '<dt>内容</dt><dd>—</dd>'}</dl>
    </div>`;
}

async function loadContent(localId, workingSetId) {
  state.selectedItem = state.items.find((item) => item.localId === localId) || { localId, workingSetId };
  state.content = null;
  state.contentError = null;
  state.contentLoading = true;
  renderItems();
  try {
    state.content = await api('/api/worksets/' + encodeURIComponent(workingSetId) +
      '/items/' + encodeURIComponent(localId) + '/content');
  } catch (error) {
    state.contentError = reasonText(error.body) || error.message;
  } finally {
    state.contentLoading = false;
    renderItems();
  }
}
