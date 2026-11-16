// Compare / Merge 面板：瞬时比较分页 → 一页决策提交 → 轮询轮次终态。
// 依赖 core.js 的 api()、state、escapeHtml()、setStatus()。
const MERGE_ACTIONS = {
  ACCEPT: '采纳源',
  KEEP_TARGET: '保留目标',
  DEFER: '暂缓'
};

const mergeView = {
  open: false,
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

function closeMerge() {
  if (mergeView.poller) clearInterval(mergeView.poller);
  mergeView.poller = null;
  mergeView.open = false;
  mergeView.round = null;
  mergeView.page = null;
  mergeView.rows = [];
  mergeOverlay().classList.add('hidden');
  $('#mergeContent').innerHTML = '';
}

async function openMerge() {
  const current = state.selectedWs;
  if (!current) {
    setStatus('请先选择工作集再比较合并', 'warn');
    return;
  }
  const node = await api('/api/worksets/' + encodeURIComponent(current));
  let target = node.parentWorkingSetId || current;
  let source = node.parentWorkingSetId ? current : null;
  if (!source) {
    const children = await api('/api/worksets/' + encodeURIComponent(current) + '/children');
    if (!children.length) {
      setStatus('默认工作集没有可比较的子工作集', 'warn');
      return;
    }
    source = children[0];
  }
  mergeView.open = true;
  mergeView.target = target;
  mergeView.source = source;
  mergeView.offset = 0;
  mergeView.round = null;
  mergeView.rows = [];
  mergeOverlay().classList.remove('hidden');
  renderMergeShell();
  await loadMergePage();
}

function renderMergeShell() {
  $('#mergeContent').innerHTML = `
    <div class="modal-head">
      <div>
        <h3>比较 / 合并</h3>
        <p id="mergeSubtitle"></p>
      </div>
      <button type="button" class="modal-close" onclick="closeMerge()">✕</button>
    </div>
    <div class="modal-body">
      <div id="mergeBody"><div class="empty">正在比较…</div></div>
    </div>
    <div class="modal-foot">
      <span id="mergeHint" class="muted"></span>
      <span class="spacer"></span>
      <button type="button" class="btn" onclick="closeMerge()">关闭</button>
      <button type="button" class="btn primary" id="mergeSubmit" onclick="submitMerge()" disabled>提交本页决策</button>
    </div>`;
}

async function loadMergePage() {
  const body = $('#mergeBody');
  body.innerHTML = '<div class="empty">正在比较…</div>';
  try {
    const page = await api('/api/worksets/' + encodeURIComponent(mergeView.target)
      + '/compare/' + encodeURIComponent(mergeView.source)
      + '?offset=' + mergeView.offset + '&limit=50', { method: 'POST' });
    mergeView.page = page;
    mergeView.rows = page.differences.map((difference) => ({ difference, action: 'ACCEPT', mark: true }));
    $('#mergeSubtitle').textContent = `源 ${mergeView.source} → 目标 ${mergeView.target} · 共 ${page.differences.length} 条差异`
      + (page.nextOffset === null ? '（本页即末页）' : '（还有下一页）');
    renderMergeRows();
  } catch (error) {
    body.innerHTML = `<div class="empty">比较失败：${escapeHtml(error.message)}</div>`;
  }
}

function renderMergeRows() {
  const rows = mergeView.rows;
  const body = $('#mergeBody');
  const submit = $('#mergeSubmit');
  if (!rows.length) {
    body.innerHTML = '<div class="empty">两个工作集在当前范围内没有差异</div>';
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
      return { entryKey: difference.key, evidence: difference,
        decision: { fields, markAsMerged } };
    }
    const existence = row.action === 'ACCEPT'
      ? (difference.kind === 'ADDED' ? 'CREATE' : 'DELETE')
      : row.action;
    return { entryKey: difference.key, evidence: difference,
      decision: { existence, markAsMerged } };
  });
}

async function submitMerge() {
  if (mergeView.submitting) return;
  mergeView.submitting = true;
  const submit = $('#mergeSubmit');
  submit.disabled = true;
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
    mergeView.round = round;
    $('#mergeHint').textContent = `已受理轮次 ${shortId(round.id)}，等待执行…`;
    renderMergeRound(round);
    pollMergeRound(round.id);
  } catch (error) {
    $('#mergeHint').textContent = `提交失败：${error.message}`;
    submit.disabled = false;
  } finally {
    mergeView.submitting = false;
  }
}

function pollMergeRound(roundId) {
  if (mergeView.poller) clearInterval(mergeView.poller);
  mergeView.poller = setInterval(async () => {
    try {
      const round = await api('/api/merge-rounds/' + encodeURIComponent(roundId));
      mergeView.round = round;
      renderMergeRound(round);
      if (round.result) {
        clearInterval(mergeView.poller);
        mergeView.poller = null;
      }
    } catch (error) {
      clearInterval(mergeView.poller);
      mergeView.poller = null;
      $('#mergeHint').textContent = `轮询失败：${error.message}`;
    }
  }, 800);
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

function newRequestId() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
  return 'merge-' + Date.now() + '-' + Math.random().toString(16).slice(2);
}

document.addEventListener('DOMContentLoaded', () => {
  const button = $('#mergeBtn');
  if (button) button.addEventListener('click', () => openMerge());
});
