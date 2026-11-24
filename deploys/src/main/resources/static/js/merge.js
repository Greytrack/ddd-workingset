// Compare / Merge 面板：瞬时比较分页 → 一页决策提交 → 轮询轮次终态。
// 依赖 core.js 的 api()、state、escapeHtml()、setStatus()。
const MERGE_ACTIONS = {
    ACCEPT: '采纳源',
    IGNORED: '忽略变更'
};

const mergeView = {
    open: false,
    anchor: null,
    peers: [],
    sourceTracker: null,
    targetTracker: null,
    target: null,
    source: null,
    pageToken: null,
    page: null,
    rows: [],
    round: null,
    submitting: false,
    poller: null
};

function mergeOverlay() {
    return $('#mergeOverlay');
}

let mergeTrigger = null;

function closeMerge(refresh = true) {
    const wasOpen = mergeView.open;
    if (mergeView.poller) clearInterval(mergeView.poller);
    mergeView.poller = null;
    mergeView.open = false;
    mergeView.round = null;
    mergeView.page = null;
    mergeView.rows = [];
    mergeOverlay().classList.add('hidden');
    $('#mergeContent').innerHTML = '';
    syncModalBackground();
    mergeTrigger?.focus();
    mergeTrigger = null;
    if (wasOpen && refresh) {
        refreshSelectedRange().catch((error) => setStatus('刷新失败：' + error.message, 'error'));
    }
}

async function openMerge() {
    const trigger = document.activeElement;
    const selected = state.selectedTracker;
    const workingSetId = state.selectedWs;
    const node = findNode(state.worksets, workingSetId);
    if (!selected || !node?.ws) return;

    const anchor = {workingSetId, trackerId: selected.trackerId, name: selected.name};
    const parent = node.ws.parentWorkingSetId;
    const adjacent = parent && selected.sourceId ? [parent] : [];
    const ranges = await Promise.all(adjacent.map(async (id) => {
        const base = '/v1/workingsets/' + encodeURIComponent(id);
        const [trackers, bindings] = await Promise.all([
            api(base + '/trackers'),
            id === '1' ? null : api(base + '/bindings')
        ]);
        const boundIds = new Set(bindings?.trackers.map((binding) => binding.trackerId));
        return trackers.filter((tracker) => !tracker.shared
            && (id === '1' || boundIds.has(tracker.trackerId))
            && tracker.trackerId === selected.sourceId)
            .map((tracker) => ({workingSetId: id, trackerId: tracker.trackerId, name: tracker.name}));
    }));
    if (state.selectedTracker !== selected || state.selectedWs !== workingSetId) return;

    closeMerge(false);
    mergeTrigger = trigger;
    mergeView.open = true;
    mergeView.anchor = anchor;
    mergeView.peers = ranges.flat();
    mergeOverlay().classList.remove('hidden');
    syncModalBackground();
    renderMergeShell();
    $('#mergeDirection').focus();
    if (mergeView.peers.length) await mergeStartComparison();
}

function renderMergeShell() {
    const peers = mergeView.peers;
    $('#mergeContent').innerHTML = `
    <div class="modal-head">
      <div>
        <h3 id="mergeTitle">比较/合并跟踪分类 <span class="merge-title-name">${escapeHtml(mergeView.anchor.name || '未命名分类')}</span></h3>
        <p id="mergeSubtitle"></p>
      <div class="merge-picker">
        <div class="merge-workset-path" aria-label="工作集合并方向">
          <div class="merge-endpoint"><span class="merge-endpoint-role">来源工作集</span><strong id="mergeSourceWorkset"></strong></div>
        <button type="button" id="mergeDirection" class="merge-direction-toggle" value="target"
          aria-label="切换比对方向" aria-pressed="true" onclick="mergeFlipDirection()" ${peers.length ? '' : 'disabled'}>
          <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><path d="M4 8h16m-4-4 4 4-4 4M20 16H4m4-4-4 4 4 4"/></svg>
        </button>
          <div class="merge-endpoint"><span class="merge-endpoint-role">接收工作集</span><strong id="mergeTargetWorkset"></strong></div>
        </div>
        <button type="button" class="btn" id="mergeCompare" onclick="mergeStartComparison()" ${peers.length ? '' : 'disabled'}>重新比较</button>
      </div>
      </div>
      <button type="button" class="modal-close" aria-label="关闭" onclick="closeMerge()">✕</button>
    </div>
    <div class="modal-body">
      <div id="mergeBody"><div class="empty">${peers.length ? '正在比较…' : '没有可比较的直属父级跟踪分类'}</div></div>
    </div>
    <div class="modal-foot">
      <span id="mergeHint" class="muted"></span>
      <span class="spacer"></span>
      <button type="button" class="btn" onclick="closeMerge()">关闭</button>
      <button type="button" class="btn primary" id="mergeSubmit" onclick="submitMerge()" disabled>提交本页决策</button>
    </div>`;
    mergeUpdateDirectionLabel();
}

function mergeUpdateDirectionLabel() {
    const button = $('#mergeDirection');
    const peer = mergeView.peers[0];
    const current = worksetLabel(mergeView.anchor.workingSetId, '当前工作集');
    const other = peer ? worksetLabel(peer.workingSetId, '父工作集') : '无可比较父级';
    const direction = button.value === 'source' ? `${current} → ${other}` : `${other} → ${current}`;
    $('#mergeSourceWorkset').textContent = button.value === 'source' ? current : other;
    $('#mergeTargetWorkset').textContent = button.value === 'source' ? other : current;
    button.title = direction + '；点击切换方向，再重新比较';
    button.setAttribute('aria-label', direction + '，点击切换方向');
    $('#mergeSubtitle').textContent = '';
}

function mergeFlipDirection() {
    const button = $('#mergeDirection');
    if (button.disabled) return;
    button.value = button.value === 'source' ? 'target' : 'source';
    button.setAttribute('aria-pressed', String(button.value === 'target'));
    mergeResetComparison();
    $('#mergeBody').innerHTML = '<div class="empty" role="status">方向已切换，点击“重新比较”查看此方向的差异</div>';
}

function mergeResetComparison() {
    mergeView.page = null;
    mergeView.rows = [];
    $('#mergeSubmit').disabled = true;
    $('#mergeCompare').disabled = !mergeView.peers.length;
    $('#mergeHint').textContent = '';
    mergeUpdateDirectionLabel();
    $('#mergeBody').innerHTML = '<div class="empty">点击“重新比较”查看当前方向的差异</div>';
}

async function mergeStartComparison() {
    if (!mergeView.peers.length || mergeView.submitting || mergeView.poller) return;
    const peer = mergeView.peers[0];
    const anchorIsSource = $('#mergeDirection').value === 'source';
    const source = anchorIsSource ? mergeView.anchor : peer;
    const target = anchorIsSource ? peer : mergeView.anchor;
    mergeView.source = source.workingSetId;
    mergeView.target = target.workingSetId;
    mergeView.sourceTracker = source.trackerId;
    mergeView.targetTracker = target.trackerId;
    mergeView.pageToken = null;
    mergeView.round = null;
    await loadMergePage();
}

async function loadMergePage() {
    const body = $('#mergeBody');
    const submit = $('#mergeSubmit');
    const compareButton = $('#mergeCompare');
    const directionSelect = $('#mergeDirection');
    compareButton.disabled = true;
    directionSelect.disabled = true;
    submit.disabled = true;
    mergeView.rows = [];
    mergeView.page = null;
    $('#mergeHint').textContent = '';
    body.innerHTML = '<div class="empty">正在比较…</div>';
    const query = new URLSearchParams({
        sourceTrackerId: mergeView.sourceTracker,
        targetTrackerId: mergeView.targetTracker,
        sourceWorkingSetId: mergeView.source,
        pageSize: 50
    });
    if (mergeView.pageToken) query.set('pageToken', mergeView.pageToken);
    try {
        const comparison = await api('/v1/workingsets/' + encodeURIComponent(mergeView.target)
            + ':compare?' + query, {method: 'POST'});
        const page = comparison.page;
        if (body !== $('#mergeBody')) return;
        mergeView.page = page;
        mergeView.rows = page.items.map((difference) => ({
            difference, action: 'IGNORED', mark: true,
            fields: Object.fromEntries((difference.fields || []).map((field) => [field, 'IGNORED']))
        }));
        $('#mergeSubtitle').textContent = `本页 ${page.items.length} 条差异`;
        renderMergeRows();
    } catch (error) {
        body.innerHTML = `<div class="empty">比较失败：${escapeHtml(error.message)}</div>`;
    } finally {
        compareButton.disabled = false;
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
    const next = mergeView.page && mergeView.page.nextPageToken
        ? `<button type="button" class="btn" onclick="nextMergePage()">下一页</button>` : '';
    body.innerHTML = `
    <table class="merge-table merge-comparison-table">
      <thead><tr><th>条目</th><th>差异</th><th>来源内容</th><th>接收方内容</th><th>接收决策</th><th>标记已处理</th></tr></thead>
      <tbody>${rows.map((row, index) => mergeRowHtml(row, index)).join('')}</tbody>
    </table>
    <div class="merge-foot">${next}</div>`;
    submit.disabled = !rows.some(isMergeRowDecidable);
    $('#mergeHint').textContent = '默认忽略变更，打开开关接收源变更；标记已处理独立生效，会隐藏当前版次的整行差异。';
}

function isMergeRowDecidable(row) {
    return row.difference.source.presence !== 'UNREADABLE'
        && row.difference.target.presence !== 'UNREADABLE';
}

function mergeDecisionSwitch(index, fieldIndex, action, label) {
    return `<button type="button" class="merge-decision-switch" role="switch"
        aria-checked="${action === 'ACCEPT'}" aria-label="${escapeHtml(label)}：接收源变更"
        onclick="mergeToggleDecision(this, ${index}, ${fieldIndex})">
        <span class="decision-ignore">忽略变更</span>
        <span class="decision-motion" aria-hidden="true"><span>→</span></span>
        <span class="decision-accept">接收变更</span>
    </button>`;
}

function mergeToggleDecision(button, index, fieldIndex) {
    const accept = button.getAttribute('aria-checked') !== 'true';
    const action = accept ? 'ACCEPT' : 'IGNORED';
    if (fieldIndex < 0) setMergeAction(index, action);
    else setMergeFieldAction(index, fieldIndex, action);
    button.setAttribute('aria-checked', String(accept));
}

function mergePresenceInfo(evidence, role) {
    if (evidence.presence === 'ABSENT' || evidence.presence === 'DELETED') return '';
    const labels = {PRESENT: '查看条目', ABSENT: '无对应条目', UNREADABLE: '内容不可读', DELETED: '条目已删除'};
    return infoDisclosure(labels[evidence.presence] || evidence.presence || '状态未知',
        [['角色', role], ['所属工作集', worksetLabel(role === '源' ? mergeView.source : mergeView.target, '未命名工作集')],
            ['对象 ID', evidence.localId], ['状态', labels[evidence.presence] || evidence.presence]]);
}

function mergeItemName(difference) {
    for (const side of [difference.source, difference.target]) {
        if (side?.presence !== 'PRESENT') continue;
        const title = side.fields?.title;
        if (typeof title === 'string' && title.trim()) return title;
        const item = state.items.find((candidate) => candidate.localId === side.localId);
        if (item?.name) return item.name;
    }
    return '名称不可用';
}

function mergeRowHtml(row, index) {
    const difference = row.difference;
    const target = difference.target || {};
    const source = difference.source || {};
    const updated = difference.kind === 'MODIFIED';
    const decidable = isMergeRowDecidable(row);
    const decisions = !decidable ? '证据不可读' : updated
        ? (difference.fields || []).map((field, fieldIndex) => `
        <div class="merge-field-decision"><div class="merge-decision-line"><span title="${escapeHtml(field)}">${escapeHtml(({
            title: '标题',
            description: '描述'
        })[field] || field)}：</span>
          ${mergeDecisionSwitch(index, fieldIndex, row.fields[field], ({
            title: '标题',
            description: '描述'
        })[field] || field)}
        </div></div>`).join('')
        : `<div class="merge-field-decision"><div class="merge-decision-line"><span>${difference.kind === 'DELETED' ? '删除' : '新增'}：</span>${mergeDecisionSwitch(index, -1, row.action, difference.kind === 'DELETED' ? '删除对象' : '新增对象')}</div></div>`;
    const mark = !decidable ? '—' : updated
        ? `<input type="checkbox" aria-label="标记整条差异已处理" ${row.mark ? 'checked' : ''} onchange="setMergeMark(${index}, this.checked)">`
        : difference.kind === 'ADDED' ? '接收成功后自动标记' : '不标记';
    const kind = {ADDED: '新增', MODIFIED: '更新', DELETED: '删除'}[difference.kind] || difference.kind;
    return `
    <tr>
      <td>${infoDisclosure(mergeItemName(difference), [['家族 ID', difference.family]])}</td>
      <td><span class="merge-kind merge-kind-${difference.kind === 'MODIFIED' ? 'updated' : difference.kind === 'ADDED' ? 'added' : difference.kind === 'DELETED' ? 'deleted' : 'other'}">${escapeHtml(kind)}</span></td>
      <td>${mergePresenceInfo(source, '源')}</td>
      <td>${mergePresenceInfo(target, '目标')}</td>
      <td>${decisions}</td>
      <td>${mark}</td>
    </tr>`;
}

function setMergeAction(index, action) {
    mergeView.rows[index].action = action;
}

function setMergeFieldAction(index, fieldIndex, action) {
    const row = mergeView.rows[index];
    row.fields[row.difference.fields[fieldIndex]] = action;
}

function setMergeMark(index, mark) {
    mergeView.rows[index].mark = mark;
}

async function nextMergePage() {
    mergeView.pageToken = mergeView.page.nextPageToken;
    mergeView.rows = [];
    await loadMergePage();
}

function mergeDecisions() {
    return mergeView.rows.filter(isMergeRowDecidable).map((row) => ({
        evidence: row.difference,
        decision: row.difference.kind === 'MODIFIED'
            ? {fields: row.fields, markAsMerged: row.mark}
            : {existence: row.action}
    }));
}

async function submitMerge() {
    if (mergeView.submitting) return;
    mergeView.submitting = true;
    const submit = $('#mergeSubmit');
    const body = $('#mergeBody');
    submit.disabled = true;
    setMergePickerDisabled(true);
    try {
        const round = await api('/v1/workingsets/' + encodeURIComponent(mergeView.target) + '/mergeRounds', {
            method: 'POST',
            headers: {'Content-Type': 'application/json', 'Idempotency-Key': newRequestId()},
            body: JSON.stringify({
                sourceWorkingSetId: mergeView.source,
                decisions: mergeDecisions()
            })
        });
        addMergeTask(round);
        if (body !== $('#mergeBody')) return;
        mergeView.round = round;
        $('#mergeHint').textContent = '决策已受理，正在执行…';
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
    for (const id of ['mergeDirection', 'mergeCompare']) {
        $('#' + id).disabled = disabled;
    }
}

function pollMergeRound(roundId) {
    if (mergeView.poller) clearInterval(mergeView.poller);
    const body = $('#mergeBody');
    const poller = setInterval(async () => {
        try {
            const round = await api('/v1/mergeRounds/' + encodeURIComponent(roundId));
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
    const state = result?.state || round.state;
    const states = {
        Preparing: '正在准备合并',
        Running: '正在执行合并',
        Succeeded: '本轮合并已完成',
        PartiallySucceeded: '本轮合并部分完成',
        Failed: '本轮合并未成功'
    };
    const tone = !result ? 'running' : state === 'Succeeded' ? 'success' : state === 'Failed' ? 'error' : 'warning';
    const stats = result ? [['已执行', result.summary.applied], ['已确认', result.summary.confirmed], ['已拒绝', result.summary.rejected], ['有冲突', result.summary.conflict], ['证据过期', result.summary.staleEvidence]] : [];
    const summary = `<section class="merge-result-banner ${tone}" role="status">
      <span class="merge-result-icon" aria-hidden="true">${!result ? '⋯' : state === 'Succeeded' ? '✓' : '!'}</span>
      <div><h4>${escapeHtml(states[state] || '合并状态待确认')}</h4><p>${result ? '请查看下方逐项执行结果。' : '决策已受理，结果将自动更新。'}</p></div>
      ${infoDisclosure('执行详情', [['轮次 ID', round.id], ['状态代码', state]])}
    </section>
    ${stats.length ? `<div class="merge-result-stats">${stats.map(([label, count]) => `<div><strong>${escapeHtml(String(count ?? 0))}</strong><span>${label}</span></div>`).join('')}</div>` : ''}`;
    const outcomes = {
        Applied: ['已执行', 'success'],
        Confirmed: ['已确认', 'success'],
        Rejected: ['已拒绝', 'error'],
        Conflict: ['有冲突', 'warning'],
        StaleEvidence: ['证据过期', 'warning'],
        Unknown: ['结果待核实', 'warning']
    };
    const entries = round.entries.map((entry) => {
        const evidence = entry.evidence || {};
        const decision = entry.decision || {};
        const outcome = entry.result ? outcomes[entry.result.outcome] || ['结果待核实', 'warning'] : ['等待执行', 'running'];
        const operation = decision.existence ? ({
            ACCEPT: '接收变更',
            IGNORED: '忽略变更'
        })[decision.existence] || '存在性决策'
            : Object.entries(decision.fields || {}).map(([field, action]) => `${({
            title: '标题',
            description: '描述'
        })[field] || field}：${action === 'ACCEPT' ? '接收' : action === 'IGNORED' ? '忽略' : action}`).join(' · ') || '字段决策';
        return `
    <tr>
      <td>${infoDisclosure(mergeItemName(evidence), [['家族 ID', evidence.family]])}</td>
      <td><div>${escapeHtml(operation)}</div>${decision.markAsMerged ? '<span class="muted">标记已处理</span>' : ''}</td>
      <td><span class="merge-outcome ${outcome[1]}">${outcome[0]}</span></td>
      <td>${entry.result?.failure ? escapeHtml(reasonText(entry.result.failure) || String(entry.result.failure)) : ''}</td>
    </tr>`;
    }).join('');
    body.innerHTML = `${summary}
    <div class="merge-result-heading"><h4>条目执行明细</h4><span class="muted">${round.entries.length} 个条目</span></div>
    <table class="merge-table merge-result-table">
      <thead><tr><th>条目</th><th>提交的决策</th><th>执行结果</th><th>原因说明</th></tr></thead>
      <tbody>${entries}</tbody>
    </table>`;
    $('#mergeSubtitle').textContent = result ? '合并结果' : '执行中';
    $('#mergeHint').textContent = result ? '本轮执行已结束；可重新比较，查看接收方的最新差异。' : '正在执行，本页结果会自动更新。';
}

document.addEventListener('DOMContentLoaded', () => {
    $('#itemActions').addEventListener('click', (event) => {
        if (event.target.closest('[data-action="compare-tracker"]')) {
            openMerge().catch((error) => setStatus('无法加载可比较的 跟踪分类：' + error.message, 'error'));
        }
    });
});
