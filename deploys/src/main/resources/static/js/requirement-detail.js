let requirementPage = null;
let requirementPageRequest = 0;

async function openRequirementPage(item) {
    const request = ++requirementPageRequest;
    requirementPage = {item, data: null, busy: true, error: ''};
    document.body.classList.add('requirement-page-open');
    renderRequirementPage();
    try {
        const data = await api('/api/requirements/' + encodeURIComponent(item.localId));
        if (request !== requirementPageRequest) return;
        requirementPage.data = data;
    } catch (error) {
        if (request === requirementPageRequest) requirementPage.error = reasonText(error.body) || error.message;
    } finally {
        if (request === requirementPageRequest) {
            requirementPage.busy = false;
            renderRequirementPage();
            $('#requirementBack').focus();
        }
    }
}

function renderRequirementPage() {
    const page = requirementPage;
    const data = page.data;
    const editable = data?.checkedOut === true && !page.busy;
    $('#requirementPage').innerHTML = `<header class="requirement-page-head">
      <button type="button" class="btn" id="requirementBack" ${page.busy ? 'disabled' : ''}>← 返回列表</button>
      <div><p class="muted">${escapeHtml(worksetLabel(state.selectedWs))} / ${escapeHtml(state.selectedTracker?.name || '需求')}</p><h1>${escapeHtml(data?.title || page.item.name || '需求详情')}</h1></div>
      <span class="requirement-state ${editable ? 'editing' : ''}">${data?.checkedOut ? '已签出 · 可编辑' : '已签入 · 只读'}</span>
    </header>
    <main class="requirement-page-content">
      <div class="requirement-lifecycle-actions" aria-label="需求生命周期操作">
        <button class="btn primary" type="button" data-req-command="checkout" ${!data || data.checkedOut || page.busy ? 'disabled' : ''}>签出</button>
        <button class="btn primary" type="button" data-req-command="checkin" ${!editable ? 'disabled' : ''}>签入</button>
        <button class="btn" type="button" data-req-command="cancel-checkout" ${!editable ? 'disabled' : ''}>取消签出</button>
        <span class="muted">${page.busy ? '正在处理…' : data?.checkedOut ? '保存修改后签入；取消签出会恢复签出前内容。' : '签出后才可编辑需求。'}</span>
      </div>
      <p class="form-error" role="alert">${escapeHtml(page.error)}</p>
      ${data ? `<div class="requirement-page-meta"><span>编号 ${escapeHtml(data.number || '未编号')}</span>${infoDisclosure('技术信息', [['对象 ID', data.localId], ['家族 ID', data.commonId], ['并发版本', data.lockVersion]])}</div>
      <dl class="requirement-audit"><div><dt>版次</dt><dd>${escapeHtml(String(data.revision))}</dd></div><div><dt>签入签出状态</dt><dd>${data.checkedOut ? '已签出' : '已签入'}</dd></div><div><dt>创建人</dt><dd>${escapeHtml(data.createdBy || '—')}</dd></div><div><dt>创建时间</dt><dd>${escapeHtml(formatAuditTime(data.createdTime))}</dd></div><div><dt>更新人</dt><dd>${escapeHtml(data.updatedBy || '—')}</dd></div><div><dt>更新时间</dt><dd>${escapeHtml(formatAuditTime(data.updatedTime))}</dd></div></dl>
      <form id="requirementDetailForm"><div class="field"><label for="detailReqTitle">标题</label><input id="detailReqTitle" name="title" value="${escapeHtml(data.title)}" required ${!editable ? 'disabled' : ''}></div>
      <div class="field"><label for="detailReqDescription">描述</label><textarea id="detailReqDescription" name="description" ${!editable ? 'disabled' : ''}>${escapeHtml(data.description || '')}</textarea></div>
      <div class="requirement-page-save"><button class="btn primary" type="submit" ${!editable ? 'disabled' : ''}>保存修改</button><span class="muted">保存仅更新草稿，签入后恢复只读。</span></div></form>` : `<div class="empty">${page.busy ? '正在加载需求…' : '需求加载失败，请返回列表重试。'}</div>`}
    </main>`;
}

async function requirementPageMutate(command) {
    const page = requirementPage;
    if (!page?.data || page.busy) return;
    const form = $('#requirementDetailForm');
    const isSave = command === 'save';
    if (command !== 'checkout' && !page.data.checkedOut) return;
    if (command === 'checkout' && page.data.checkedOut) return;
    if (command === 'checkin' && (form.elements.namedItem('title').value !== page.data.title || form.elements.namedItem('description').value !== (page.data.description || ''))) {
        $('#requirementPage .form-error').textContent = '请先保存修改，再签入。';
        return;
    }
    const payload = isSave ? {title: form.elements.namedItem('title').value.trim(), description: form.elements.namedItem('description').value, expectedLockVersion: page.data.lockVersion}
        : {expectedLockVersion: page.data.lockVersion};
    page.busy = true;
    page.error = '';
    // Keep input values and focus stable while an operation is in flight.
    $('#requirementPage').querySelectorAll('button').forEach((button) => { button.disabled = true; });
    try {
        const data = await api('/api/requirements/' + encodeURIComponent(page.item.localId) + (isSave ? '' : '/' + command), {
            method: isSave ? 'PUT' : 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
        });
        if (requirementPage !== page) return;
        page.data = data;
        try { page.data = await api('/api/requirements/' + encodeURIComponent(page.item.localId)); }
        catch { page.error = '操作已完成，审计信息刷新失败，请返回列表后重新进入。'; }
    } catch (error) {
        page.error = reasonText(error.body) || error.message;
        page.busy = false;
        $('#requirementPage .form-error').textContent = page.error;
        $('#requirementBack').disabled = false;
        $('#requirementDetailForm button').disabled = !page.data.checkedOut;
        $('#requirementPage').querySelectorAll('[data-req-command]').forEach((button) => { button.disabled = button.dataset.reqCommand === 'checkout' ? page.data.checkedOut : !page.data.checkedOut; });
        return;
    }
    page.busy = false;
    renderRequirementPage();
}

document.addEventListener('DOMContentLoaded', () => {
    $('#requirementPage').addEventListener('submit', (event) => {
        event.preventDefault();
        requirementPageMutate('save');
    });
    $('#requirementPage').addEventListener('click', (event) => {
        if (event.target.closest('#requirementBack')) {
            if (requirementPage?.busy) return;
            ++requirementPageRequest;
            requirementPage = null;
            document.body.classList.remove('requirement-page-open');
            $('#requirementPage').innerHTML = '';
            refreshSelectedRange().catch((error) => setStatus(error.message, 'error'));
        }
        const action = event.target.closest('[data-req-command]');
        if (action) requirementPageMutate(action.dataset.reqCommand);
    });
});
