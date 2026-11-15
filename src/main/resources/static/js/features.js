/* ---------- modal shell ---------- */
function modalHeader(title, subtitle = '') {
  return `<div class="modal-head">
    <div><h3>${escapeHtml(title)}</h3>${subtitle ? `<p>${escapeHtml(subtitle)}</p>` : ''}</div>
    <button type="button" class="modal-close" data-close-modal aria-label="关闭">×</button>
  </div>`;
}

function openModal(html, wide = false) {
  $('#modalContent').innerHTML = html;
  $('#modalPanel').classList.toggle('wide', wide);
  $('#modalOverlay').classList.remove('hidden');
}

function closeModal() {
  $('#modalOverlay').classList.add('hidden');
  $('#modalContent').innerHTML = '';
  taskCenterOpen = false;
  requirementContext = null;
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
  let completed = false;
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
        if (!wasTerminal && isTerminal(view.status)) completed = true;
      } catch (error) {
        task.lastError = reasonText(error.body) || error.message;
        if (error.status === 404) task.status = 'Failed';
      }
    }));
    saveTasks();
    updateTaskBadge();
    if (taskCenterOpen) renderTaskCenter();
    if (completed) {
      await loadWorksetTree();
      setStatus('创建任务已结束，可刷新左侧工作集查看结果', 'ok');
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
    : '<div class="empty">暂无任务。点击“创建工作集”发起一个。</div>';
  openModal(`${modalHeader('任务中心', '创建工作集 LRO 的阶段与进度')}
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

  const stages = STAGES.map((stage) => {
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
        <div class="task-title">${escapeHtml(task.targetName || task.targetId || '工作集任务')}</div>
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
  const defaultTarget = 'WS-' + Date.now().toString(36).toUpperCase();
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
        <div class="grid-2">
          <div class="field"><label for="createTarget">新工作集 ID</label><input id="createTarget" name="newWorkingSetId" value="${escapeHtml(defaultTarget)}" required></div>
          <div class="field"><label for="createRequest">请求 ID</label><input id="createRequest" name="requestId" value="${escapeHtml('ui-' + Date.now())}" required></div>
        </div>
        <div id="createScope"><div class="empty small">加载父工作集范围…</div></div>
        <div id="createFormError" class="form-error"></div>
      </div>
      <div class="modal-foot">
        <button type="button" class="btn" data-close-modal>取消</button>
        <button type="submit" class="btn primary">创建并轮询</button>
      </div>
    </form>`, true);
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
      <div class="scope-hint">三级范围：工作集 → 产品空间 → Tracker。每个空间可以只选部分 Tracker，也可以一个都不选；不选任何空间即可创建空工作集。</div>
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
      <button type="button" class="scope-toggle" data-scope-toggle="${escapeHtml(space.productSpaceId)}" aria-label="展开 Tracker" aria-expanded="false">›</button>
    </div>
    <div class="scope-trackers">
      <div class="scope-tracker-group">
        <div class="scope-group-title">共享 Tracker · ${shared.length}</div>
        ${trackerRows(shared, 'sharedTrackerIds', 'shared')}
      </div>
      <div class="scope-tracker-group">
        <div class="scope-group-title">非共享 Tracker · ${unshared.length}</div>
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
  meta.textContent = `${selected}/${trackers.length} 个 Tracker`;
  spaceElement.classList.toggle('no-space', !spaceElement.querySelector('input[name="productSpaceIds"]')?.checked);
}

async function submitCreateWorkset(form) {
  const errorBox = $('#createFormError');
  if (errorBox) errorBox.textContent = '';
  const parentId = form.parentWorkingSetId.value.trim();
  const targetId = form.newWorkingSetId.value.trim();
  const requestId = form.requestId.value.trim();
  const nameInput = form.querySelector('input[name="name"]');
  const workspaceName = nameInput ? nameInput.value.trim() : '';
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
        newWorkingSetId: targetId,
        requestId,
        name: workspaceName,
        productSpaceIds,
        sharedTrackerIds: trackerValues('sharedTrackerIds'),
        unsharedParentTrackerIds: trackerValues('unsharedParentTrackerIds')
      })
    });
    const parentName = findNode(state.worksets, parentId)?.ws?.name || shortId(parentId);
    addTask(creation, { targetId, targetName: workspaceName || targetId, parentId, parentName });
    closeModal();
    openTaskCenter();
    setStatus('创建任务已提交：' + shortId(creation.creationId), 'ok');
  } catch (error) {
    if (errorBox) errorBox.textContent = reasonText(error.body) || error.message;
  }
}

/* ---------- single requirement CRUD ---------- */
function openRequirementCreate() {
  if (!state.selectedTracker) {
    setStatus('请先选择一个 Tracker', 'error');
    return;
  }
  requirementContext = { mode: 'create' };
  const tracker = state.selectedTracker;
  openModal(`${modalHeader('新建需求', `工作集 ${shortId(state.selectedWs)} · Tracker ${tracker.name || shortId(tracker.trackerId)}`)}
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
  const localId = item.sourceLocalId || item.localId;
  try {
    const requirement = await api('/api/requirements/' + encodeURIComponent(localId));
    requirementContext = { mode: 'edit', localId, lockVersion: requirement.lockVersion };
    openModal(`${modalHeader('编辑需求', `来源需求 ${shortId(localId)}`)}
      <form id="requirementForm">
        <div class="modal-body">
          <div class="field"><label for="reqTitle">标题</label><input id="reqTitle" name="title" value="${escapeHtml(requirement.title)}" required></div>
          <div class="field"><label for="reqDescription">描述</label><textarea id="reqDescription" name="description" required>${escapeHtml(requirement.description)}</textarea></div>
          <div class="field"><span class="hint">编辑的是 Item 的实际内容来源；乐观锁版本 ${escapeHtml(requirement.lockVersion)}。</span></div>
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
  const current = state.selectedWs;
  state.selectedWs = null;
  state.selectedTracker = null;
  state.selectedItem = null;
  state.items = [];
  state.content = null;
  await loadWorksetTree();
  const root = state.worksets[0];
  const target = current && findNode(state.worksets, current) ? current : root?.id;
  if (target) await selectWorkset(target);
}

function bindEvents() {
  $('#worksetTree').addEventListener('click', (event) => {
    const row = event.target.closest('[data-ws-id]');
    if (row) selectWorkset(row.dataset.wsId);
  });

  $('#spaceList').addEventListener('click', (event) => {
    const toggle = event.target.closest('[data-space-toggle]');
    if (toggle) {
      const spaceId = toggle.dataset.spaceToggle;
      state.selectedSpace = spaceId;
      if (state.expandedSpaces.has(spaceId)) state.expandedSpaces.delete(spaceId);
      else state.expandedSpaces.add(spaceId);
      renderSpaces();
      return;
    }
    const tracker = event.target.closest('[data-tracker-id]');
    if (tracker) selectTracker(tracker.dataset.trackerId);
  });

  $('#itemArea').addEventListener('click', (event) => {
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

  $('#selectedTracker').addEventListener('click', (event) => {
    if (event.target.closest('[data-action="new-requirement"]')) openRequirementCreate();
  });

  $('#createWorksetBtn').addEventListener('click', openCreateWorkset);
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
      toggle.setAttribute('aria-label', expanded ? '收起 Tracker' : '展开 Tracker');
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
    if (event.target.id === 'createWorksetForm') {
      event.preventDefault();
      submitCreateWorkset(event.target);
    }
    if (event.target.id === 'requirementForm') {
      event.preventDefault();
      submitRequirement(event.target);
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeModal();
  });
}

document.addEventListener('DOMContentLoaded', () => {
  state.tasks = loadStoredTasks();
  updateTaskBadge();
  if (state.tasks.some((task) => !isTerminal(task.status))) startTaskPolling();
  bindEvents();
  refresh();
});
