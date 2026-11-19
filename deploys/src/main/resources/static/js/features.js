/* ---------- modal shell ---------- */
let modalTrigger = null;

function modalHeader(title, subtitle = '') {
  return `<div class="modal-head">
    <div><h3 id="modalTitle">${escapeHtml(title)}</h3>${subtitle ? `<p>${escapeHtml(subtitle)}</p>` : ''}</div>
    <button type="button" class="modal-close" data-close-modal aria-label="关闭">×</button>
  </div>`;
}

function openModal(html, wide = false) {
  const overlay = $('#modalOverlay');
  const opening = overlay.classList.contains('hidden');
  const restoreCloseFocus = !opening && overlay.contains(document.activeElement);
  if (opening) modalTrigger = document.activeElement;
  $('#modalContent').innerHTML = html;
  $('#modalPanel').classList.toggle('wide', wide);
  overlay.classList.remove('hidden');
  if (opening || restoreCloseFocus) {
    (overlay.querySelector('input:not([type="checkbox"]):not([readonly]), select:enabled') || overlay.querySelector('[data-close-modal]')).focus();
  }
}

function closeModal() {
  if ($('#modalOverlay').classList.contains('hidden')) return;
  $('#modalOverlay').classList.add('hidden');
  $('#modalContent').innerHTML = '';
  taskCenterOpen = false;
  requirementContext = null;
  modalTrigger?.focus();
  modalTrigger = null;
}

/* ---------- LRO task centre ---------- */
function isTerminal(status) {
  return TERMINAL_STATUSES.has(status);
}

function loadStoredTasks() {
  try {
    const raw = localStorage.getItem(TASK_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveTasks() {
  try {
    localStorage.setItem(TASK_KEY, JSON.stringify(state.tasks));
  } catch {
    /* storage may be unavailable; polling still works in-memory */
  }
}

function updateTaskBadge() {
  const count = state.tasks.filter((task) => !isTerminal(task.status)).length;
  const badge = $('#taskBadge');
  badge.textContent = String(count);
  badge.classList.toggle('zero', count === 0);
}

function addTask(creation, meta) {
  const task = {
    ...meta,
    ...creation,
    id: creation.creationId,
    targetId: meta.targetId,
    lastUpdated: Date.now()
  };
  const index = state.tasks.findIndex((item) => item.id === task.id);
  if (index >= 0) state.tasks[index] = { ...state.tasks[index], ...task };
  else state.tasks.unshift(task);
  saveTasks();
  updateTaskBadge();
  startTaskPolling();
}

function startTaskPolling() {
  if (taskTimer) return;
  taskTimer = setInterval(pollTasks, 1000);
}

async function pollTasks() {
  if (state.taskPolling) return;
  const active = state.tasks.filter((task) => !isTerminal(task.status));
  if (!active.length) {
    if (taskTimer) {
      clearInterval(taskTimer);
      taskTimer = null;
    }
    updateTaskBadge();
    return;
  }
  state.taskPolling = true;
  const completed = [];
  try {
    await Promise.all(active.map(async (task) => {
      try {
        const view = await api('/api/working-set-creations/' + encodeURIComponent(task.id));
        const wasTerminal = isTerminal(task.status);
        task.status = view.status;
        task.stage = view.stage;
        task.progress = view.progress;
        task.stageProgress = view.stageProgress;
        task.indexProgress = view.indexProgress;
        task.failures = view.failures;
        task.completedAt = view.completedAt;
        task.lastError = null;
        task.lastUpdated = Date.now();
        if (!wasTerminal && isTerminal(view.status)) completed.push(task);
      } catch (error) {
        task.lastError = reasonText(error.body) || error.message;
        if (error.status === 404) task.status = 'Failed';
      }
    }));
    saveTasks();
    updateTaskBadge();
    if (taskCenterOpen) renderTaskCenter();
    if (completed.some((task) => task.mode !== 'EXTEND')) await loadWorksetTree();
    if (completed.some((task) => task.mode === 'EXTEND' && task.targetId === state.selectedWs)) {
      await refreshSelectedRange();
    }
    if (completed.length) {
      const warning = completed.some((task) => task.status !== 'Succeeded');
      setStatus(warning ? '任务已结束，请在任务中心查看结果' : '工作集任务已完成', warning ? 'warn' : 'ok');
    }
  } finally {
    state.taskPolling = false;
  }
}

function openTaskCenter() {
  taskCenterOpen = true;
  renderTaskCenter();
  startTaskPolling();
}

function renderTaskCenter() {
  const body = state.tasks.length
    ? `<div class="task-list">${state.tasks.map(renderTaskCard).join('')}</div>`
    : '<div class="empty">暂无工作集任务。</div>';
  openModal(`${modalHeader('任务中心')}
    <div class="modal-body">${body}</div>
    <div class="modal-foot"><button type="button" class="btn" data-close-modal>关闭</button></div>`, true);
  taskCenterOpen = true;
}

function renderTaskCard(task) {
  const status = task.status || 'Pending';
  const statusClass = isTerminal(status)
    ? ((status === 'Succeeded' || status === 'PartiallySucceeded') ? 'succeeded' : 'failed')
    : 'running';
  const progress = task.progress || {};
  const total = progress.total == null ? 0 : progress.total;
  const processed = (progress.succeeded || 0) + (progress.failed || 0) + (progress.skipped || 0);
  const percent = total ? Math.min(100, Math.round(processed * 100 / total)) : (isTerminal(status) ? 100 : 6);

  const stages = STAGES.filter((stage) => task.mode !== 'EXTEND' || stage !== 'CREATE_WORKING_SET').map((stage) => {
    const p = task.stageProgress?.[stage];
    const stageProcessed = p ? (p.succeeded || 0) + (p.failed || 0) + (p.skipped || 0) : 0;
    const stageTotal = p && p.total != null ? p.total : null;
    const done = stageTotal !== null && stageProcessed >= stageTotal;
    const current = !isTerminal(status) && task.stage === stage;
    const cls = done ? 'done' : current ? 'current' : '';
    const counts = p
      ? `${stageProcessed}/${stageTotal == null ? '?' : stageTotal} · ✓${p.succeeded || 0} ✗${p.failed || 0} ↷${p.skipped || 0}`
      : '未开始';
    return `<div class="stage-row ${cls}"><span class="stage-dot"></span><span>${escapeHtml(STAGE_LABELS[stage] || stage)}</span><span class="stage-counts">${escapeHtml(counts)}</span></div>`;
  }).join('');

  const indexRows = Object.entries(task.indexProgress || {}).map(([trackerId, counts]) =>
    `<div class="stage-row"><span>LTI · ${escapeHtml(shortId(trackerId))}</span><span class="stage-counts">✓${counts.succeeded || 0} ✗${counts.failed || 0} ↷${counts.skipped || 0}</span></div>`).join('');

  const failures = (task.failures || []).slice(0, 8).map((failure) =>
    `<li>${escapeHtml(failure.stage || '')} · ${escapeHtml(failure.trackerId || '')}${failure.localId ? ' · ' + escapeHtml(failure.localId) : ''} · ${escapeHtml(failure.reason || '')}</li>`).join('');

  return `<div class="task-card ${statusClass}">
    <div class="task-top">
      <div>
        <div class="task-title">${task.mode === 'EXTEND' ? '扩展' : '新建'} · ${escapeHtml(task.targetName || task.targetId || '工作集')}</div>
        <div class="task-sub">父级 ${escapeHtml(task.parentName || task.parentId || '—')} · 任务 ${escapeHtml(shortId(task.id))}</div>
      </div>
      <span class="task-status ${statusClass}">${escapeHtml(STATUS_LABELS[status] || status)}</span>
    </div>
    <div class="task-progress"><span style="width:${percent}%"></span></div>
    <div class="task-sub">当前阶段：${escapeHtml(isTerminal(status) ? '收尾 / 完成' : (STAGE_LABELS[task.stage] || task.stage || '等待开始'))} · 进度 ${processed}/${total || '?'}</div>
    <div class="stage-list">${stages}${indexRows}</div>
    ${failures ? `<ul class="failure-list">${failures}</ul>` : ''}
    ${task.lastError ? `<div class="failure-list">${escapeHtml(task.lastError)}</div>` : ''}
  </div>`;
}

/* ---------- create working set ---------- */
const WORKSET_COLOR_PRESETS = [
  ['白色', '#FFFF'], ['蓝色', '#3478F6'], ['青色', '#0D9488'], ['绿色', '#2E8B57'],
  ['橙色', '#E88434'], ['玫红', '#CA4B7C'], ['紫色', '#8054BC'], ['深灰', '#475569']
];

function wireWorksetColor(form, prefix = 'create') {
  const picker = form.querySelector('#' + prefix + 'ColorPicker');
  const input = form.querySelector('#' + prefix + 'Color');
  const preview = form.querySelector('#' + prefix + 'ColorPreview');
  const buttons = [...form.querySelectorAll('[data-workset-color]')];
  const sync = () => {
    const color = parseWorksetColor(input.value);
    preview.style.setProperty('--swatch', color?.hex || 'transparent');
    for (const button of buttons) {
      button.setAttribute('aria-pressed', String(button.dataset.worksetColor === color?.hex));
    }
    if (color) picker.value = color.rgb;
  };
  for (const button of buttons) {
    button.addEventListener('click', () => {
      input.value = button.dataset.worksetColor;
      sync();
    });
  }
  input.addEventListener('input', sync);
  const pick = () => {
    input.value = picker.value.toUpperCase();
    sync();
  };
  picker.addEventListener('input', pick);
  picker.addEventListener('change', pick);
  sync();
}

function worksetColorField(prefix, value = '#FFFF') {
  return `<div class="field">
    <span id="${prefix}ColorLabel" class="field-label">工作集颜色</span>
    <div class="color-palette" role="group" aria-labelledby="${prefix}ColorLabel">
      ${WORKSET_COLOR_PRESETS.map(([label, hex]) =>
        `<button type="button" class="color-swatch" data-workset-color="${hex}" style="--swatch:${hex}"
          aria-label="${label} ${hex}" title="${label}" aria-pressed="false"></button>`).join('')}
    </div>
    <div class="color-field">
      <label class="color-custom" for="${prefix}ColorPicker">
        <span id="${prefix}ColorPreview" class="color-preview" aria-hidden="true"></span>
        <span>自定义颜色</span>
        <input id="${prefix}ColorPicker" type="color" value="#ffffff" aria-label="打开自定义颜色选择器">
      </label>
      <input id="${prefix}Color" name="color" value="${escapeHtml(value)}" maxlength="9" spellcheck="false"
        aria-label="工作集颜色十六进制值"
        pattern="#(?:[0-9A-Fa-f]{3,4}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})" required>
    </div>
  </div>`;
}

function flattenWorksets(nodes, out = [], depth = 0) {
  for (const node of nodes || []) {
    out.push({ node, depth });
    flattenWorksets(node.children || [], out, depth + 1);
  }
  return out;
}

async function openCreateWorkset() {
  if (!state.worksets.length) await loadWorksetTree();
  const flat = flattenWorksets(state.worksets);
  const selected = state.selectedWs || '1';
  const options = flat.map(({ node, depth }) => {
    const label = node.ws?.name || (node.id === '1' ? '默认工作集' : shortId(node.id));
    return `<option value="${escapeHtml(node.id)}"${node.id === selected ? ' selected' : ''}>${'　'.repeat(Math.min(depth, 6))}${escapeHtml(label)} (${escapeHtml(shortId(node.id))})</option>`;
  }).join('');
  openModal(`${modalHeader('创建工作集', '选择父工作集范围，提交后进入 LRO 任务中心')}
    <form id="createWorksetForm">
      <div class="modal-body">
        <div class="field">
          <label for="createParent">父工作集</label>
          <select id="createParent" name="parentWorkingSetId">${options}</select>
        </div>
        <div class="field">
          <label for="createName">工作集名称</label>
          <input id="createName" name="name" placeholder="例如：动力系统 V2 需求工作集" maxlength="255" required>
        </div>
        ${worksetColorField('create')}
        <input type="hidden" name="requestId" value="${escapeHtml(newRequestId())}">
        <div id="createScope"><div class="empty small">加载父工作集范围…</div></div>
        <div id="createFormError" class="form-error"></div>
      </div>
      <div class="modal-foot">
        <button type="button" class="btn" data-close-modal>取消</button>
        <button type="submit" class="btn primary">创建并轮询</button>
      </div>
    </form>`, true);
  wireWorksetColor($('#createWorksetForm'));
  const parentSelect = $('#createParent');
  if (parentSelect) await loadCreateScope(parentSelect.value);
}

async function loadCreateScope(parentId) {
  const target = $('#createScope');
  if (!target) return;
  target.innerHTML = '<div class="empty small">加载父工作集范围…</div>';
  try {
    const base = '/api/worksets/' + encodeURIComponent(parentId);
    const [bindings, trackers] = await Promise.all([api(base + '/bindings'), api(base + '/trackers')]);
    const trackerById = new Map((trackers || []).map((tracker) => [tracker.trackerId, tracker]));
    const bySpace = new Map();
    (bindings.trackers || []).forEach((binding) => {
      if (binding.shown === false || binding.classificationId === FOLDER_CLASSIFICATION_ID) return;
      if (!bySpace.has(binding.productSpaceId)) bySpace.set(binding.productSpaceId, []);
      const tracker = trackerById.get(binding.trackerId) || {
        trackerId: binding.trackerId,
        name: binding.trackerId,
        sharingMode: binding.sharingMode
      };
      bySpace.get(binding.productSpaceId).push(tracker);
    });
    const spaces = (bindings.spaces || []).map((space) => ({
      ...space,
      trackers: bySpace.get(space.productSpaceId) || []
    }));
    bySpace.forEach((spaceTrackers, spaceId) => {
      if (!spaces.some((space) => space.productSpaceId === spaceId)) {
        spaces.push({ productSpaceId: spaceId, name: spaceId, trackers: spaceTrackers });
      }
    });
    if (!spaces.length) {
      target.innerHTML = '<div class="scope-hint">父工作集没有绑定产品空间；不选任何内容即可创建空工作集。</div>';
      return;
    }
    target.innerHTML = `
      <div class="scope-hint">三级范围：工作集 → 产品空间 → 跟踪分类。每个空间可以只选部分 跟踪分类，也可以一个都不选；不选任何空间即可创建空工作集。</div>
      <div class="scope-tree">${spaces.map((space) => renderScopeSpace(space)).join('')}</div>`;
    target.querySelectorAll('.scope-space').forEach((space) => updateScopeMeta(space));
  } catch (error) {
    target.innerHTML = `<div class="empty small error-text">${escapeHtml(reasonText(error.body) || error.message)}</div>`;
  }
}

function renderScopeSpace(space) {
  const shared = space.trackers.filter((tracker) => tracker.sharingMode === 'SHARED');
  const unshared = space.trackers.filter((tracker) => tracker.sharingMode !== 'SHARED');
  const trackerRows = (list, name, badgeClass) => list.length
    ? list.map((tracker) => `
      <label class="scope-tracker">
        <input type="checkbox" name="${name}" value="${escapeHtml(tracker.trackerId)}" data-space-id="${escapeHtml(space.productSpaceId)}" checked>
        <span class="scope-tracker-name">${escapeHtml(tracker.name || shortId(tracker.trackerId))}<span class="check-id">${escapeHtml(tracker.trackerId)}</span></span>
        <span class="badge ${badgeClass}">${escapeHtml(tracker.sharingMode)}</span>
      </label>`).join('')
    : '<div class="scope-empty">无</div>';
  return `<div class="scope-space" data-scope-space="${escapeHtml(space.productSpaceId)}">
    <div class="scope-space-head">
      <label class="scope-space-main">
        <input type="checkbox" name="productSpaceIds" value="${escapeHtml(space.productSpaceId)}" data-space-id="${escapeHtml(space.productSpaceId)}" checked>
        <span class="scope-space-name">${escapeHtml(space.name || space.productSpaceId)}<span class="check-id">${escapeHtml(space.productSpaceId)}</span></span>
      </label>
      <span class="scope-space-meta" data-scope-meta></span>
      <button type="button" class="scope-toggle" data-scope-toggle="${escapeHtml(space.productSpaceId)}" aria-label="展开 跟踪分类" aria-expanded="false">›</button>
    </div>
    <div class="scope-trackers">
      <div class="scope-tracker-group">
        <div class="scope-group-title">共享 跟踪分类 · ${shared.length}</div>
        ${trackerRows(shared, 'sharedTrackerIds', 'shared')}
      </div>
      <div class="scope-tracker-group">
        <div class="scope-group-title">非共享 跟踪分类 · ${unshared.length}</div>
        ${trackerRows(unshared, 'unsharedParentTrackerIds', 'unshared')}
      </div>
    </div>
  </div>`;
}

function updateScopeMeta(spaceElement) {
  const meta = spaceElement.querySelector('[data-scope-meta]');
  if (!meta) return;
  const trackers = [...spaceElement.querySelectorAll('.scope-tracker input')];
  const selected = trackers.filter((input) => input.checked).length;
  meta.textContent = `${selected}/${trackers.length} 个 跟踪分类`;
  spaceElement.classList.toggle('no-space', !spaceElement.querySelector('input[name="productSpaceIds"]')?.checked);
}

async function submitCreateWorkset(form) {
  const errorBox = $('#createFormError');
  if (errorBox) errorBox.textContent = '';
  const parentId = form.parentWorkingSetId.value.trim();
  const requestId = form.requestId.value.trim();
  const nameInput = form.querySelector('input[name="name"]');
  const workspaceName = nameInput ? nameInput.value.trim() : '';
  const color = parseWorksetColor(form.elements.namedItem('color').value);
  if (!color) {
    if (errorBox) errorBox.textContent = '请选择或输入有效的十六进制颜色。';
    return;
  }
  const values = (name) => [...form.querySelectorAll(`input[name="${name}"]:checked`)].map((input) => input.value);
  const productSpaceIds = values('productSpaceIds');
  const selectedSpaces = new Set(productSpaceIds);
  const trackerValues = (name) => [...form.querySelectorAll(`input[name="${name}"]:checked`)]
    .filter((input) => selectedSpaces.has(input.dataset.spaceId))
    .map((input) => input.value);
  try {
    const creation = await api('/api/working-set-creations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        parentWorkingSetId: parentId,
        requestId,
        name: workspaceName,
        color: color.hex,
        productSpaceIds,
        sharedTrackerIds: trackerValues('sharedTrackerIds'),
        unsharedParentTrackerIds: trackerValues('unsharedParentTrackerIds')
      })
    });
    const parentName = findNode(state.worksets, parentId)?.ws?.name || shortId(parentId);
    const targetId = creation.targetWorkingSetId;
    addTask(creation, { targetId, targetName: workspaceName || targetId, parentId, parentName });
    closeModal();
    openTaskCenter();
    setStatus('创建任务已提交：' + shortId(creation.creationId), 'ok');
  } catch (error) {
    if (errorBox) errorBox.textContent = reasonText(error.body) || error.message;
  }
}

/* ---------- edit working set ---------- */
function openWorksetEdit() {
  const id = state.selectedWs;
  const node = findNode(state.worksets, id);
  if (!id || !node) return;
  const ws = node.ws || {};
  const color = parseWorksetColor(ws.color)?.hex || '#FFFF';
  openModal(`${modalHeader('编辑工作集', '修改名称与主题色，不影响已绑定的范围')}
    <form id="editWorksetForm">
      <div class="modal-body">
        <div class="field">
          <label for="editName">工作集名称</label>
          <input id="editName" name="name" maxlength="255" required
            value="${escapeHtml(ws.name || '')}" placeholder="例如：动力系统 V2 需求工作集">
        </div>
        ${worksetColorField('edit', color)}
        <div class="form-error" role="alert"></div>
      </div>
      <div class="modal-foot">
        <button type="button" class="btn" data-close-modal>取消</button>
        <button type="submit" class="btn primary">保存</button>
      </div>
    </form>`);
  const form = $('#editWorksetForm');
  form.dataset.workingSetId = id;
  wireWorksetColor(form, 'edit');
  form.querySelector('#editName').focus();
}

async function submitWorksetEdit(form) {
  const errorBox = form.querySelector('.form-error');
  const nameInput = form.querySelector('#editName');
  const name = nameInput.value.trim();
  const color = parseWorksetColor(form.querySelector('#editColor').value);
  if (!name) {
    errorBox.textContent = '请输入工作集名称';
    nameInput.focus();
    return;
  }
  if (!color) {
    errorBox.textContent = '请选择或输入有效的十六进制颜色。';
    return;
  }
  const workingSetId = form.dataset.workingSetId;
  const submit = form.querySelector('[type="submit"]');
  errorBox.textContent = '';
  submit.disabled = true;
  submit.textContent = '保存中…';
  try {
    const updated = await api('/api/worksets/' + encodeURIComponent(workingSetId), {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, color: color.hex })
    });
    applyWorksetEdit(updated);
    if (form.isConnected) closeModal();
    setStatus('工作集已更新', 'ok');
  } catch (error) {
    errorBox.textContent = reasonText(error.body) || error.message;
  } finally {
    submit.disabled = false;
    submit.textContent = '保存';
  }
}

/* ---------- extend working set ---------- */
async function openExtendWorkset() {
  const targetId = state.selectedWs;
  const parentId = findNode(state.worksets, targetId)?.ws?.parentWorkingSetId;
  if (!parentId) return;
  const targetName = worksetLabel(targetId);
  const parentName = worksetLabel(parentId);
  openModal(`${modalHeader('扩展工作集', targetName + ' · 父工作集 ' + parentName)}
    <form id="extendWorksetForm">
      <div class="modal-body">
        <div id="extendScope" role="group" aria-label="父工作集的跟踪分类"><div class="empty small" role="status">正在读取范围…</div></div>
        <div class="form-error" role="alert"></div>
      </div>
      <div class="modal-foot">
        <span id="extensionCount" class="muted" aria-live="polite"></span>
        <button type="button" class="btn" data-close-modal>取消</button>
        <button type="submit" class="btn primary" disabled>提交扩展</button>
      </div>
    </form>`, true);
  const form = $('#extendWorksetForm');
  const scope = $('#extendScope');
  let submittedSelection = '';
  let requestId;
  form.addEventListener('change', () => updateExtensionSelection(form));
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const submit = form.querySelector('[type="submit"]');
    if (submit.disabled) return;
    const trackerIds = extensionSelection(form);
    const key = JSON.stringify(trackerIds);
    if (key !== submittedSelection) {
      requestId = newRequestId();
      submittedSelection = key;
    }
    const errorBox = form.querySelector('.form-error');
    errorBox.textContent = '';
    submit.disabled = true;
    submit.textContent = '提交中…';
    const selected = [...form.querySelectorAll('input[name="trackerIds"]:enabled')];
    selected.forEach((input) => { input.disabled = true; });
    try {
      const task = await api('/api/worksets/' + encodeURIComponent(targetId) + '/extensions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ requestId, trackerIds })
      });
      addTask(task, { mode: 'EXTEND', targetId, targetName, parentId, parentName });
      if (form.isConnected) {
        closeModal();
        openTaskCenter();
      }
      if (isTerminal(task.status) && state.selectedWs === targetId) await refreshSelectedRange();
      setStatus('扩展任务已提交：' + shortId(task.creationId), 'ok');
    } catch (error) {
      errorBox.textContent = error.body?.reason === 'PREPARING' ? '当前已有扩展任务，请在完成后重试。'
        : error.body?.reason === 'NoDerivableTracker' ? '所选跟踪分类已纳入，请重新打开列表。'
        : reasonText(error.body) || error.message;
    } finally {
      selected.forEach((input) => { input.disabled = false; });
      submit.textContent = '提交扩展';
      updateExtensionSelection(form);
    }
  });
  try {
    const parent = '/api/worksets/' + encodeURIComponent(parentId);
    const target = '/api/worksets/' + encodeURIComponent(targetId);
    const [parentBindings, parentTrackers, targetBindings, targetTrackers] = await Promise.all([
      api(parent + '/bindings'), api(parent + '/trackers'), api(target + '/bindings'), api(target + '/trackers')
    ]);
    if (!form.isConnected) return;
    const parentBound = new Set(parentBindings.trackers.map((binding) => binding.trackerId));
    const targetBound = new Set(targetBindings.trackers.map((binding) => binding.trackerId));
    const derivedSources = new Set(targetTrackers
      .filter((tracker) => tracker.sourceId && targetBound.has(tracker.trackerId))
      .map((tracker) => tracker.sourceId));
    const groups = new Map();
    const spaceNames = new Map(parentBindings.spaces.map((space) => [space.productSpaceId, space.name]));
    const mounted = new Set(targetBindings.spaces.map((space) => space.productSpaceId));
    for (const root of parentBindings.trackers.filter((binding) => binding.rootFolder)) {
      if (!mounted.has(root.productSpaceId)) {
        groups.set(root.productSpaceId, [{ ...root, name: '仅挂载产品空间', mountOnly: true }]);
      }
    }
    for (const tracker of parentTrackers) {
      if (tracker.classificationId === FOLDER_CLASSIFICATION_ID) continue;
      if (parentId !== '1' && !parentBound.has(tracker.trackerId)) continue;
      const space = tracker.productSpaceId;
      if (!groups.has(space)) groups.set(space, []);
      groups.get(space).push(tracker);
    }
    scope.innerHTML = groups.size ? `<div class="scope-tree">${[...groups].map(([spaceId, trackers]) =>
      `<fieldset class="extension-group"><legend>${escapeHtml(spaceNames.get(spaceId) || spaceId)}</legend>
        ${trackers.map((tracker) => {
          const shared = tracker.sharingMode === 'SHARED';
          const included = (shared ? targetBound : derivedSources).has(tracker.trackerId);
          return `<label class="scope-tracker${included ? ' already-included' : ''}">
            <input type="checkbox" name="trackerIds" value="${escapeHtml(tracker.trackerId)}"${included ? ' disabled' : ''}>
            <span class="scope-tracker-name">${escapeHtml(tracker.name || shortId(tracker.trackerId))}<span class="check-id">${escapeHtml(tracker.trackerId)}</span></span>
            <span class="badge ${shared ? 'shared' : 'unshared'}">${tracker.mountOnly ? '自动创建根文件夹' : shared ? '共享' : '非共享'}</span>
            ${included ? '<span class="badge">已纳入</span>' : ''}
          </label>`;
        }).join('')}</fieldset>`).join('')}</div>` : '<div class="empty small">父工作集没有可展示的跟踪分类。</div>';
    updateExtensionSelection(form);
  } catch (error) {
    scope.innerHTML = `<div class="empty small error-text">${escapeHtml(reasonText(error.body) || error.message)}</div>`;
  }
}

function extensionSelection(form) {
  return [...form.querySelectorAll('input[name="trackerIds"]:checked:enabled')].map((input) => input.value).sort();
}

function updateExtensionSelection(form) {
  const count = extensionSelection(form).length;
  const available = form.querySelectorAll('input[name="trackerIds"]:enabled').length;
  form.querySelector('#extensionCount').textContent = available ? '已选 ' + count + ' 项' : '暂无可扩展项';
  form.querySelector('[type="submit"]').disabled = count === 0;
}

/* ---------- Tracker and requirement CRUD ---------- */
async function openTrackerCreate() {
  const space = currentSpaces().get(state.selectedSpace);
  if (state.selectedWs !== '1' || !space?.bound) return;
  const workingSetId = state.selectedWs;
  openModal(`${modalHeader('新建跟踪分类')}
    <form id="trackerForm">
      <div class="modal-body">
        <div class="grid-2">
          <div class="field"><label for="trackerWorkset">所属工作集</label><input id="trackerWorkset" value="${escapeHtml(worksetLabel(workingSetId))}" readonly></div>
          <div class="field"><label for="trackerSpace">所属产品空间</label><input id="trackerSpace" value="${escapeHtml(space.name || space.productSpaceId)}" readonly></div>
        </div>
        <div class="field"><label for="trackerName">名称</label><input id="trackerName" name="trackerName" maxlength="255" required></div>
        <div class="grid-2">
          <div class="field"><label for="trackerClassification">所属分类</label>
            <select id="trackerClassification" name="classification" required disabled><option value="">正在加载分类…</option></select></div>
          <div class="field"><label for="trackerSharing">共享方式</label>
            <select id="trackerSharing" name="sharing" required disabled><option value="">请先选择所属分类</option></select></div>
        </div>
        <div class="form-error" role="alert"></div>
      </div>
      <div class="modal-foot"><button type="button" class="btn" data-close-modal>取消</button>
        <button type="submit" class="btn primary" disabled>创建</button></div>
    </form>`);
  const form = $('#trackerForm');
  form.dataset.workingSetId = workingSetId;
  form.dataset.productSpaceId = space.productSpaceId;
  form.trackerName.focus();
  form.classification.addEventListener('change', () => {
    const mode = form.classification.selectedOptions[0]?.dataset.mode;
    form.sharing.replaceChildren();
    if (mode) {
      if (mode === 'UNSHARED') form.sharing.add(new Option('非共享', 'UNSHARED'));
      form.sharing.add(new Option('共享', 'SHARED'));
    } else {
      form.sharing.add(new Option('请先选择所属分类', ''));
    }
    form.sharing.disabled = !mode;
    form.querySelector('[type="submit"]').disabled = !mode;
  });
  try {
    const classifications = (await api('/api/worksets/metadata/classifications'))
      .filter((classification) => classification.classificationId !== FOLDER_CLASSIFICATION_ID);
    if (!form.isConnected) return;
    form.classification.replaceChildren(new Option(classifications.length ? '选择所属分类' : '暂无可用分类', ''));
    for (const classification of classifications) {
      const option = new Option(classification.name || classification.classificationId, classification.classificationId);
      option.dataset.mode = classification.sharingMode;
      form.classification.add(option);
    }
    form.classification.disabled = !classifications.length;
  } catch (error) {
    form.classification.replaceChildren(new Option('分类加载失败', ''));
    form.querySelector('.form-error').textContent = reasonText(error.body) || error.message;
  }
}

async function submitTracker(form) {
  const submit = form.querySelector('[type="submit"]');
  if (submit.disabled) return;
  const errorBox = form.querySelector('.form-error');
  const name = form.trackerName.value.trim();
  if (!name) {
    errorBox.textContent = '请输入名称';
    form.trackerName.focus();
    return;
  }
  const { workingSetId, productSpaceId } = form.dataset;
  errorBox.textContent = '';
  submit.disabled = true;
  submit.textContent = '创建中…';
  form.classification.disabled = true;
  form.sharing.disabled = true;
  try {
    const created = await api('/api/worksets/' + encodeURIComponent(workingSetId) + '/trackers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ productSpaceId, name, classificationId: form.classification.value, sharingMode: form.sharing.value })
    });
    if (form.isConnected) closeModal();
    if (state.selectedWs === workingSetId) {
      if (!state.trackers.some((tracker) => tracker.trackerId === created.trackerId)) state.trackers.push(created);
      if (!state.bindings.trackers.some((binding) => binding.trackerId === created.trackerId)) {
        state.bindings.trackers.push({ trackerId: created.trackerId });
      }
      if (state.selectedSpace === productSpaceId) await selectTracker(created.trackerId);
      else renderSpaces();
    }
    setStatus('跟踪分类已创建', 'ok');
  } catch (error) {
    errorBox.textContent = error.body?.reason === 'DATA_INTEGRITY_CONFLICT'
      ? '创建发生冲突，请检查该产品空间和分类下是否已有同名跟踪分类。'
      : reasonText(error.body) || error.message;
  } finally {
    submit.disabled = false;
    submit.textContent = '创建';
    form.classification.disabled = false;
    form.sharing.disabled = false;
  }
}

function openRequirementCreate() {
  if (!state.selectedTracker) {
    setStatus('请先选择一个 跟踪分类', 'error');
    return;
  }
  requirementContext = { mode: 'create' };
  const tracker = state.selectedTracker;
  openModal(`${modalHeader('新建需求', `工作集 ${shortId(state.selectedWs)} · 跟踪分类 ${tracker.name || shortId(tracker.trackerId)}`)}
    <form id="requirementForm">
      <div class="modal-body">
        <div class="field"><label for="reqTitle">标题</label><input id="reqTitle" name="title" required></div>
        <div class="field"><label for="reqDescription">描述</label><textarea id="reqDescription" name="description" required></textarea></div>
        <div class="field"><label for="reqRevision">初始 Revision</label><input id="reqRevision" name="revision" type="number" min="0" value="1"></div>
        <div id="requirementFormError" class="form-error"></div>
      </div>
      <div class="modal-foot"><button type="button" class="btn" data-close-modal>取消</button><button type="submit" class="btn primary">创建</button></div>
    </form>`);
}

async function openRequirementEdit(item) {
  const localId = item.localId;
  try {
    const requirement = await api('/api/requirements/' + encodeURIComponent(localId));
    requirementContext = { mode: 'edit', localId, lockVersion: requirement.lockVersion };
    openModal(`${modalHeader('编辑需求', requirement.number || shortId(localId))}
      <form id="requirementForm">
        <div class="modal-body">
          <div class="field"><label for="reqTitle">标题</label><input id="reqTitle" name="title" value="${escapeHtml(requirement.title)}" required></div>
          <div class="field"><label for="reqDescription">描述</label><textarea id="reqDescription" name="description" required>${escapeHtml(requirement.description)}</textarea></div>
          <div id="requirementFormError" class="form-error"></div>
        </div>
        <div class="modal-foot"><button type="button" class="btn" data-close-modal>取消</button><button type="submit" class="btn primary">保存</button></div>
      </form>`);
  } catch (error) {
    setStatus(reasonText(error.body) || error.message, 'error');
  }
}

async function submitRequirement(form) {
  const errorBox = $('#requirementFormError');
  const submit = form.querySelector('[type="submit"]');
  if (submit.disabled) return;
  submit.disabled = true;
  submit.textContent = '保存中…';
  if (errorBox) errorBox.textContent = '';
  const title = form.title.value.trim();
  const description = form.description.value;
  const trackerId = state.selectedTracker?.trackerId;
  try {
    if (requirementContext?.mode === 'edit') {
      await api('/api/requirements/' + encodeURIComponent(requirementContext.localId), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title, description, expectedLockVersion: requirementContext.lockVersion })
      });
      setStatus('需求已更新', 'ok');
    } else {
      await api('/api/requirements', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          workingSetId: state.selectedWs,
          trackerId,
          title,
          description,
          revision: Number(form.revision.value || 1)
        })
      });
      setStatus('需求已创建', 'ok');
    }
    closeModal();
    if (trackerId) await selectTracker(trackerId);
  } catch (error) {
    if (errorBox) errorBox.textContent = reasonText(error.body) || error.message;
  } finally {
    submit.disabled = false;
    submit.textContent = '保存';
  }
}

async function deleteRequirement(item) {
  const localId = item.sourceLocalId || item.localId;
  if (!window.confirm('确定删除需求 ' + (item.name || localId) + '？')) return;
  try {
    await api('/api/requirements/' + encodeURIComponent(localId), { method: 'DELETE' });
    setStatus('需求已删除', 'ok');
    if (state.selectedTracker) await selectTracker(state.selectedTracker.trackerId);
  } catch (error) {
    setStatus(reasonText(error.body) || error.message, 'error');
  }
}

async function refresh() {
  const button = $('#refresh');
  button.disabled = true;
  try {
    await loadWorksetTree();
    await refreshSelectedRange();
  } finally {
    button.disabled = false;
  }
}

async function refreshSelectedRange() {
  const current = state.selectedWs;
  const space = state.selectedSpace;
  const tracker = state.selectedTracker?.trackerId;
  const item = state.selectedItem?.localId;
  const filter = $('#itemFilter').value;
  const target = current && findNode(state.worksets, current) ? current : state.worksets[0]?.id;
  if (!target) return;
  const keepSelection = current === target;
  await selectWorkset(target, { autoSelect: false });
  if (state.selectedWs !== target) return;

  if (keepSelection && currentSpaces().has(space)) {
    selectSpace(space);
    if (tracker && state.trackers.some((candidate) => candidate.trackerId === tracker)) {
      $('#itemFilter').value = filter;
      await selectTracker(tracker, { keepFilter: true });
      const selected = state.items.find((candidate) => candidate.localId === item);
      if (selected && !state.selectedItem) await loadContent(selected.localId, selected.workingSetId);
      return;
    }
    const fallback = defaultTrackerFor(space);
    if (fallback) await selectTracker(fallback.trackerId);
    return;
  }
  await selectDefaultRange();
}

function bindEvents() {
  const openPicker = () => $('.context-picker[open]');
  $('.pane-nav').addEventListener('click', (event) => {
    const tab = event.target.closest('[data-pane]');
    if (!tab) return;
    if (tab.dataset.pane === 'trackers') setTrackerPanelCollapsed(false);
    showPane(tab.dataset.pane);
  });
  $('#toggleTrackerPanel').addEventListener('click', (event) => {
    const collapsed = event.currentTarget.getAttribute('aria-expanded') === 'true';
    setTrackerPanelCollapsed(collapsed);
    showPane(collapsed ? 'items' : 'trackers');
  });
  $('#worksetTree').addEventListener('click', (event) => {
    const row = event.target.closest('[data-ws-id]');
    if (!row) return;
    $('#worksetPicker').open = false;
    $('#worksetPicker summary').focus();
    if (row.dataset.wsId !== state.selectedWs) {
      selectWorkset(row.dataset.wsId);
      if ($('#toggleTrackerPanel').getAttribute('aria-expanded') === 'true') showPane('trackers');
    }
  });

  $('#spaceList').addEventListener('click', (event) => {
    const space = event.target.closest('[data-space-id]');
    if (!space) return;
    selectSpace(space.dataset.spaceId);
    $('#spacePicker').open = false;
    $('#spacePicker summary').focus();
    setTrackerPanelCollapsed(false);
    showPane('trackers');
  });
  $('#trackerList').addEventListener('click', (event) => {
    const tracker = event.target.closest('[data-tracker-id]');
    if (tracker) {
      selectTracker(tracker.dataset.trackerId);
      $('#trackerList .tracker-node.selected')?.focus({ preventScroll: true });
      showPane('items', true);
    }
  });
  document.addEventListener('click', (event) => {
    const picker = openPicker();
    if (picker && !picker.contains(event.target)) picker.open = false;
  });

  $('#itemFilter').addEventListener('input', renderItems);
  $('#itemDetail').addEventListener('click', (event) => {
    if (!event.target.closest('[data-close-detail]')) return;
    const item = state.selectedItem;
    ++contentRequest;
    state.selectedItem = null;
    state.contentLoading = false;
    renderItemDetail();
    $('#itemArea').querySelector(`[data-local-id="${CSS.escape(item.localId)}"] .item-open`)?.focus();
  });

  $('#itemArea').addEventListener('click', (event) => {
    if (event.target.closest('[data-clear-item-filter]')) {
      const filter = $('#itemFilter');
      filter.value = '';
      renderItems();
      filter.focus();
      return;
    }
    const edit = event.target.closest('[data-edit-requirement]');
    if (edit) {
      const item = state.items.find((candidate) => candidate.localId === edit.dataset.editRequirement);
      if (item) openRequirementEdit(item);
      return;
    }
    const remove = event.target.closest('[data-delete-requirement]');
    if (remove) {
      const item = state.items.find((candidate) => candidate.localId === remove.dataset.deleteRequirement);
      if (item) deleteRequirement(item);
      return;
    }
    const row = event.target.closest('[data-local-id]');
    if (row) loadContent(row.dataset.localId, row.dataset.itemWs);
  });

  $('#itemActions').addEventListener('click', (event) => {
    if (event.target.closest('[data-action="new-requirement"]')) openRequirementCreate();
  });

  $('#createWorksetBtn').addEventListener('click', openCreateWorkset);
  $('#editWorksetBtn').addEventListener('click', openWorksetEdit);
  $('#extendWorksetBtn').addEventListener('click', openExtendWorkset);
  $('#createTrackerBtn').addEventListener('click', openTrackerCreate);
  $('#taskCenterBtn').addEventListener('click', openTaskCenter);
  $('#refresh').addEventListener('click', refresh);

  $('#modalOverlay').addEventListener('click', (event) => {
    if (event.target.id === 'modalOverlay') closeModal();
  });

  $('#modalContent').addEventListener('click', (event) => {
    if (event.target.closest('[data-close-modal]')) {
      closeModal();
      return;
    }
    const toggle = event.target.closest('[data-scope-toggle]');
    if (toggle) {
      const space = toggle.closest('.scope-space');
      if (!space) return;
      const expanded = space.classList.toggle('expanded');
      toggle.textContent = expanded ? '⌄' : '›';
      toggle.setAttribute('aria-expanded', String(expanded));
      toggle.setAttribute('aria-label', expanded ? '收起 跟踪分类' : '展开 跟踪分类');
    }
  });

  $('#modalContent').addEventListener('change', (event) => {
    if (event.target.id === 'createParent') {
      loadCreateScope(event.target.value);
      return;
    }
    const scopeSpace = event.target.closest('.scope-space');
    if (!scopeSpace) return;
    const spaceCheckbox = scopeSpace.querySelector('input[name="productSpaceIds"]');
    if (event.target === spaceCheckbox) {
      const enabled = event.target.checked;
      scopeSpace.querySelectorAll('.scope-tracker input').forEach((input) => {
        input.disabled = !enabled;
        input.checked = enabled;
      });
    } else if (event.target.closest('.scope-tracker')) {
      if (event.target.checked && spaceCheckbox && !spaceCheckbox.checked) {
        spaceCheckbox.checked = true;
        scopeSpace.querySelectorAll('.scope-tracker input').forEach((input) => { input.disabled = false; });
      }
    }
    updateScopeMeta(scopeSpace);
  });

  $('#modalContent').addEventListener('submit', (event) => {
    if (event.target.id === 'trackerForm') {
      event.preventDefault();
      submitTracker(event.target);
    }
    if (event.target.id === 'createWorksetForm') {
      event.preventDefault();
      submitCreateWorkset(event.target);
    }
    if (event.target.id === 'editWorksetForm') {
      event.preventDefault();
      submitWorksetEdit(event.target);
    }
    if (event.target.id === 'requirementForm') {
      event.preventDefault();
      submitRequirement(event.target);
    }
  });

  document.addEventListener('keydown', (event) => {
    const picker = openPicker();
    if (event.key === 'Escape' && picker) {
      picker.open = false;
      picker.querySelector('summary').focus();
    }
    const overlay = document.querySelector('.modal-overlay:not(.hidden)');
    if (!overlay) return;
    if (event.key === 'Escape') {
      if (overlay.id === 'mergeOverlay') closeMerge();
      else closeModal();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = [...overlay.querySelectorAll('button, input, select, textarea, a[href]')]
      .filter((element) => !element.disabled && element.getClientRects().length);
    const first = focusable[0];
    const last = focusable.at(-1);
    const edge = event.shiftKey ? first : last;
    if (document.activeElement === edge || !overlay.contains(document.activeElement)) {
      event.preventDefault();
      (event.shiftKey ? last : first)?.focus();
    }
  });
}

document.addEventListener('DOMContentLoaded', () => {
  state.tasks = loadStoredTasks();
  updateTaskBadge();
  if (state.tasks.some((task) => !isTerminal(task.status))) startTaskPolling();
  bindEvents();
  refresh();
});
