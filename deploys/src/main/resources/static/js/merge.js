// Compare / Merge 面板：瞬时比较分页 → 一页决策提交 → 轮询轮次终态。
// 依赖 core.js 的 api()、state、escapeHtml()、setStatus()。
const MERGE_ACTIONS = {
  ACCEPT: '采纳源',
  KEEP_TARGET: '保留目标',
  DEFER: '暂缓'
};

const mergeView = {
  open: false,
  anchor: null,
  peers: [],
  sourceTracker: null,
  targetTracker: null,
  target: null,
  source: null,
  offset: 0,
  page: null,
  rows: [],
  round: null,
  submitting: false,
  poller: null
};

function mergeOverlay() { return $('#mergeOverlay'); }
let mergeTrigger = null;

function closeMerge() {
  if (mergeView.poller) clearInterval(mergeView.poller);
  mergeView.poller = null;
  mergeView.open = false;
  mergeView.round = null;
  mergeView.page = null;
  mergeView.rows = [];
  mergeOverlay().classList.add('hidden');
  $('#mergeContent').innerHTML = '';
  mergeTrigger?.focus();
  mergeTrigger = null;
}

async function openMerge() {
  const trigger = document.activeElement;
  const selected = state.selectedTracker;
  const workingSetId = state.selectedWs;
  const node = findNode(state.worksets, workingSetId);
  if (!selected || !node?.ws) return;

  const anchor = { workingSetId, trackerId: selected.trackerId, name: selected.name };
  const parent = node.ws.parentWorkingSetId;
  const adjacent = [...(parent && selected.sourceId ? [parent] : []),
    ...node.children.filter((child) => child.ws).map((child) => child.id)];
  const ranges = await Promise.all(adjacent.map(async (id) => {
    const base = '/api/worksets/' + encodeURIComponent(id);
    const [trackers, bindings] = await Promise.all([
      api(base + '/trackers'),
      id === '1' ? null : api(base + '/bindings')
    ]);
    const boundIds = new Set(bindings?.trackers.map((binding) => binding.trackerId));
    return trackers.filter((tracker) => tracker.sharingMode === 'UNSHARED'
      && (id === '1' || boundIds.has(tracker.trackerId))
      && (id === parent ? tracker.trackerId === selected.sourceId : tracker.sourceId === selected.trackerId))
      .map((tracker) => ({ workingSetId: id, trackerId: tracker.trackerId, name: tracker.name }));
  }));
  if (state.selectedTracker !== selected || state.selectedWs !== workingSetId) return;

  closeMerge();
  mergeTrigger = trigger;
  mergeView.open = true;
  mergeView.anchor = anchor;
  mergeView.peers = ranges.flat();
  mergeOverlay().classList.remove('hidden');
  renderMergeShell();
  $('#mergeTrackerSelect').focus();
}

function renderMergeShell() {
  const peers = mergeView.peers;
  const options = peers.map((peer, index) => `<option value="${index}">${escapeHtml(
    (peer.name || peer.trackerId) + ' · ' + peer.trackerId + '（' + peer.workingSetId + '）'
  )}</option>`).join('');
  $('#mergeContent').innerHTML = `
    <div class="modal-head">
      <div>
        <h3 id="mergeTitle">比较 / 合并</h3>
        <p id="mergeSubtitle"></p>
      </div>
      <button type="button" class="modal-close" aria-label="关闭" onclick="closeMerge()">✕</button>
    </div>
    <div class="modal-body">
      <div class="merge-picker">
        <label>比较对象 <select id="mergeTrackerSelect" onchange="mergeResetComparison()">
          <option value="" selected disabled>选择 跟踪分类</option>${options}
        </select></label>
        <label>方向 <select id="mergeDirection" onchange="mergeResetComparison()">
          <option value="source">当前 跟踪分类 → 比较对象</option>
          <option value="target">比较对象 → 当前 跟踪分类</option>
        </select></label>
        <button type="button" class="btn" id="mergeCompare" onclick="mergeStartComparison()" disabled>开始比较</button>
      </div>
      <div id="mergeBody"><div class="empty">${peers.length ? '请选择比较对象和方向' : '没有可比较的直属派生 跟踪分类'}</div></div>
    </div>
    <div class="modal-foot">
      <span id="mergeHint" class="muted"></span>
      <span class="spacer"></span>
      <button type="button" class="btn" onclick="closeMerge()">关闭</button>
      <button type="button" class="btn primary" id="mergeSubmit" onclick="submitMerge()" disabled>提交本页决策</button>
    </div>`;
  $('#mergeSubtitle').textContent = '当前 跟踪分类 ' + (mergeView.anchor.name || mergeView.anchor.trackerId);
}

function mergeResetComparison() {
  mergeView.page = null;
  mergeView.rows = [];
  $('#mergeSubmit').disabled = true;
  $('#mergeCompare').disabled = !$('#mergeTrackerSelect').value;
  $('#mergeHint').textContent = '';
  $('#mergeSubtitle').textContent = '当前 跟踪分类 ' + (mergeView.anchor.name || mergeView.anchor.trackerId);
  $('#mergeBody').innerHTML = '<div class="empty">请选择比较对象和方向</div>';
}

async function mergeStartComparison() {
  const selection = $('#mergeTrackerSelect').value;
  if (selection === '' || mergeView.submitting || mergeView.poller) return;
  const peer = mergeView.peers[Number(selection)];
  const anchorIsSource = $('#mergeDirection').value === 'source';
  const source = anchorIsSource ? mergeView.anchor : peer;
  const target = anchorIsSource ? peer : mergeView.anchor;
  mergeView.source = source.workingSetId;
  mergeView.target = target.workingSetId;
  mergeView.sourceTracker = source.trackerId;
  mergeView.targetTracker = target.trackerId;
  mergeView.offset = 0;
  mergeView.round = null;
  await loadMergePage();
}

async function loadMergePage() {
  const body = $('#mergeBody');
  const submit = $('#mergeSubmit');
  const compareButton = $('#mergeCompare');
  const trackerSelect = $('#mergeTrackerSelect');
  const directionSelect = $('#mergeDirection');
  compareButton.disabled = true;
  trackerSelect.disabled = true;
  directionSelect.disabled = true;
  submit.disabled = true;
  mergeView.rows = [];
  mergeView.page = null;
  $('#mergeHint').textContent = '';
  body.innerHTML = '<div class="empty">正在比较…</div>';
  const query = new URLSearchParams({
    sourceTrackerId: mergeView.sourceTracker,
    targetTrackerId: mergeView.targetTracker,
    offset: mergeView.offset,
    limit: 50
  });
  try {
    const page = await api('/api/worksets/' + encodeURIComponent(mergeView.target)
      + '/compare/' + encodeURIComponent(mergeView.source) + '?' + query, { method: 'POST' });
    if (body !== $('#mergeBody')) return;
    mergeView.page = page;
    mergeView.rows = page.differences.map((difference) => ({ difference, action: 'ACCEPT', mark: true }));
    $('#mergeSubtitle').textContent = `${mergeView.sourceTracker}（${mergeView.source}） → ${mergeView.targetTracker}（${mergeView.target}） · 本页 ${page.differences.length} 条差异`;
    renderMergeRows();
  } catch (error) {
    body.innerHTML = `<div class="empty">比较失败：${escapeHtml(error.message)}</div>`;
  } finally {
    compareButton.disabled = false;
    trackerSelect.disabled = false;
    directionSelect.disabled = false;
  }
}

function renderMergeRows() {
  const rows = mergeView.rows;
  const body = $('#mergeBody');
  const submit = $('#mergeSubmit');
  if (!rows.length) {
    body.innerHTML = '<div class="empty">这两个 跟踪分类 没有可处理的差异</div>';
    submit.disabled = true;
    $('#mergeHint').textContent = '';
    return;
  }
  const next = mergeView.page && mergeView.page.nextOffset !== null
    ? `<button type="button" class="btn" onclick="nextMergePage()">下一页</button>` : '';
  body.innerHTML = `
    <table class="merge-table">
      <thead><tr><th>家族</th><th>差异</th><th>源</th><th>目标</th><th>决策</th><th>标记已处理</th></tr></thead>
      <tbody>${rows.map((row, index) => mergeRowHtml(row, index)).join('')}</tbody>
    </table>
    <div class="merge-foot">${next}</div>`;
  submit.disabled = false;
  $('#mergeHint').textContent = '提交后按 requestId 幂等登记为一轮，执行结果由轮次轮询展示';
}

function mergeRowHtml(row, index) {
  const difference = row.difference;
  const target = difference.target || {};
  const source = difference.source || {};
  const options = Object.entries(MERGE_ACTIONS).map(([value, label]) =>
    `<option value="${value}"${row.action === value ? ' selected' : ''}>${label}</option>`).join('');
  const fields = (difference.fields || []).join(', ');
  const disable = row.action === 'DEFER' ? ' disabled' : '';
  return `
    <tr>
      <td class="mono">${escapeHtml(shortId(difference.family && difference.family.value))}</td>
      <td>${escapeHtml(difference.kind)}${fields ? `<div class="muted">${escapeHtml(fields)}</div>` : ''}</td>
      <td>${escapeHtml(source.presence || '—')}<div class="muted mono">${escapeHtml(shortId(source.localId && source.localId.value))}</div></td>
      <td>${escapeHtml(target.presence || '—')}<div class="muted mono">${escapeHtml(shortId(target.localId && target.localId.value))}</div></td>
      <td>
        <select onchange="setMergeAction(${index}, this.value)">${options}</select>
      </td>
      <td><input type="checkbox" ${row.mark ? 'checked' : ''}${disable} onchange="setMergeMark(${index}, this.checked)"></td>
    </tr>`;
}

function setMergeAction(index, action) {
  const row = mergeView.rows[index];
  row.action = action;
  if (action === 'DEFER') row.mark = false;
  renderMergeRows();
}

function setMergeMark(index, mark) {
  mergeView.rows[index].mark = mark;
}

async function nextMergePage() {
  mergeView.offset = mergeView.page.nextOffset;
  mergeView.rows = [];
  await loadMergePage();
}

function mergeDecisions() {
  return mergeView.rows.map((row) => {
    const difference = row.difference;
    const markAsMerged = row.action !== 'DEFER' && row.mark;
    if (difference.kind === 'MODIFIED') {
      const fields = {};
      (difference.fields || []).forEach((field) => { fields[field] = row.action; });
      return { evidence: difference,
        decision: { fields, markAsMerged } };
    }
    const existence = row.action === 'ACCEPT'
      ? (difference.kind === 'ADDED' ? 'CREATE' : 'DELETE')
      : row.action;
    return { evidence: difference,
      decision: { existence, markAsMerged } };
  });
}

async function submitMerge() {
  if (mergeView.submitting) return;
  mergeView.submitting = true;
  const submit = $('#mergeSubmit');
  const body = $('#mergeBody');
  submit.disabled = true;
  setMergePickerDisabled(true);
  try {
    const round = await api('/api/worksets/' + encodeURIComponent(mergeView.target) + '/merge-rounds', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        sourceWorkingSetId: mergeView.source,
        requestId: newRequestId(),
        decisions: mergeDecisions()
      })
    });
    if (body !== $('#mergeBody')) return;
    mergeView.round = round;
    $('#mergeHint').textContent = `已受理轮次 ${shortId(round.id)}，等待执行…`;
    renderMergeRound(round);
    pollMergeRound(round.id);
  } catch (error) {
    if (body !== $('#mergeBody')) return;
    $('#mergeHint').textContent = `提交失败：${error.message}`;
    submit.disabled = false;
    setMergePickerDisabled(false);
  } finally {
    mergeView.submitting = false;
  }
}

function setMergePickerDisabled(disabled) {
  for (const id of ['mergeTrackerSelect', 'mergeDirection', 'mergeCompare']) {
    $('#' + id).disabled = disabled;
  }
}

function pollMergeRound(roundId) {
  if (mergeView.poller) clearInterval(mergeView.poller);
  const body = $('#mergeBody');
  const poller = setInterval(async () => {
    try {
      const round = await api('/api/merge-rounds/' + encodeURIComponent(roundId));
      if (body !== $('#mergeBody') || mergeView.poller !== poller) return;
      mergeView.round = round;
      renderMergeRound(round);
      if (round.result) {
        clearInterval(mergeView.poller);
        mergeView.poller = null;
        setMergePickerDisabled(false);
      }
    } catch (error) {
      if (body !== $('#mergeBody') || mergeView.poller !== poller) return;
      clearInterval(mergeView.poller);
      mergeView.poller = null;
      setMergePickerDisabled(false);
      $('#mergeHint').textContent = `轮询失败：${error.message}`;
    }
  }, 800);
  mergeView.poller = poller;
}

function renderMergeRound(round) {
  const body = $('#mergeBody');
  const result = round.result;
  const summary = result
    ? `<div class="merge-summary">
         <span>轮次 ${escapeHtml(shortId(round.id))}</span>
         <span>终态 <strong>${escapeHtml(result.state)}</strong></span>
         <span>写入 ${result.summary.applied}</span>
         <span>确认 ${result.summary.confirmed}</span>
         <span>拒绝 ${result.summary.rejected}</span>
         <span>冲突 ${result.summary.conflict}</span>
         <span>暂缓 ${result.summary.deferred}</span>
         <span>证据过期 ${result.summary.staleEvidence}</span>
       </div>`
    : `<div class="merge-summary"><span>轮次 ${escapeHtml(shortId(round.id))}</span><span>状态 ${escapeHtml(round.state)}</span><span>执行中…</span></div>`;
  const entries = round.entries.map((entry) => `
    <tr>
      <td class="mono">${escapeHtml(shortId(entry.evidence && entry.evidence.family && entry.evidence.family.value))}</td>
      <td>${escapeHtml(entry.decision && entry.decision.existence ? entry.decision.existence : 'fields')}</td>
      <td>${entry.result ? escapeHtml(entry.result.outcome) : '<span class="muted">等待</span>'}</td>
      <td>${entry.result && entry.result.failure ? escapeHtml(entry.result.failure) : ''}</td>
    </tr>`).join('');
  body.innerHTML = `${summary}
    <table class="merge-table">
      <thead><tr><th>家族</th><th>操作</th><th>结果</th><th>失败</th></tr></thead>
      <tbody>${entries}</tbody>
    </table>`;
}

document.addEventListener('DOMContentLoaded', () => {
  $('#selectedTracker').addEventListener('click', (event) => {
    if (event.target.closest('[data-action="compare-tracker"]')) {
      openMerge().catch((error) => setStatus('无法加载可比较的 跟踪分类：' + error.message, 'error'));
    }
  });
});
