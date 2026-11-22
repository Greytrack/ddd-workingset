/* Workspace view preferences and tree navigation. */
function layoutReadPreference(key) {
    try {
        return localStorage.getItem(key);
    } catch {
        return null;
    }
}

function layoutSavePreference(key, value) {
    try {
        localStorage.setItem(key, String(value));
    } catch { /* Preferences are optional. */
    }
}

function layoutBindResize(handle, {key, initial, limits, position, apply}) {
    let value = Number(layoutReadPreference(key)) || initial;
    const update = (next) => {
        const [min, max] = limits();
        value = Math.round(Math.max(min, Math.min(max, next)));
        apply(value);
        handle.setAttribute('aria-valuemin', min);
        handle.setAttribute('aria-valuemax', max);
        handle.setAttribute('aria-valuenow', value);
    };
    update(value);
    handle.addEventListener('keydown', (event) => {
        const step = {ArrowLeft: -16, ArrowRight: 16, ArrowDown: -16, ArrowUp: 16}[event.key];
        if (!step) return;
        event.preventDefault();
        update(value + step);
        layoutSavePreference(key, value);
    });
    handle.addEventListener('pointerdown', (event) => {
        if (event.button !== 0) return;
        event.preventDefault();
        handle.setPointerCapture(event.pointerId);
        document.body.classList.add('layout-resizing');
        const move = (moveEvent) => update(position(moveEvent));
        const stop = () => {
            handle.removeEventListener('pointermove', move);
            document.body.classList.remove('layout-resizing');
            layoutSavePreference(key, value);
        };
        handle.addEventListener('pointermove', move);
        handle.addEventListener('lostpointercapture', stop, {once: true});
    });
    window.addEventListener('resize', () => update(value));
}

function layoutInitTree() {
    const list = $('#trackerList');
    const filter = $('#trackerFilter');
    const collapsed = new Set();
    const render = () => {
        const rows = [...list.querySelectorAll('[data-tracker-id]')];
        const query = filter.value.trim().toLocaleLowerCase();
        const matches = rows.map((row) => (row.textContent + row.dataset.trackerId).toLocaleLowerCase().includes(query));
        const ancestors = [];
        rows.forEach((row, index) => {
            const depth = Number(row.dataset.depth);
            while (ancestors.length && Number(ancestors.at(-1).dataset.depth) >= depth) ancestors.pop();
            const hasChildren = Number(rows[index + 1]?.dataset.depth) > depth;
            let toggle = row.querySelector('[data-tree-toggle]');
            if (hasChildren && !toggle) {
                toggle = document.createElement('span');
                toggle.dataset.treeToggle = '';
                toggle.setAttribute('role', 'button');
                toggle.tabIndex = 0;
                row.prepend(toggle);
            }
            const descendantMatches = rows.slice(index + 1).findIndex((candidate) => Number(candidate.dataset.depth) <= depth);
            const end = descendantMatches < 0 ? rows.length : index + 1 + descendantMatches;
            row.hidden = query ? !matches.slice(index, end).some(Boolean)
                : ancestors.some((parent) => collapsed.has(parent.dataset.trackerId));
            if (toggle) {
                const expanded = Boolean(query) || !collapsed.has(row.dataset.trackerId);
                toggle.textContent = expanded ? '⌄' : '›';
                toggle.setAttribute('aria-expanded', String(expanded));
                toggle.setAttribute('aria-label', (expanded ? '收起 ' : '展开 ') + row.querySelector('.tracker-label').textContent);
            }
            ancestors.push(row);
        });
        $('#trackerFilterStatus').textContent = query ? `匹配 ${matches.filter(Boolean).length} 个分类` : '';
    };
    const toggle = (event) => {
        const control = event.target.closest('[data-tree-toggle]');
        if (!control) return;
        if (event.type === 'keydown' && !['Enter', ' '].includes(event.key)) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        const id = control.closest('[data-tracker-id]').dataset.trackerId;
        if (collapsed.has(id)) collapsed.delete(id); else collapsed.add(id);
        filter.value = '';
        render();
    };
    list.addEventListener('click', toggle, true);
    list.addEventListener('keydown', toggle, true);
    filter.addEventListener('input', render);
    $('#expandTrackers').addEventListener('click', () => {
        collapsed.clear();
        filter.value = '';
        render();
    });
    $('#collapseTrackers').addEventListener('click', () => {
        list.querySelectorAll('[data-tracker-id]').forEach((row) => collapsed.add(row.dataset.trackerId));
        filter.value = '';
        render();
    });
    new MutationObserver(render).observe(list, {childList: true});
    render();
}

document.addEventListener('DOMContentLoaded', () => {
    const layout = $('.layout');
    const sidebar = $('#trackersPanel');
    const sidebarHandle = document.createElement('div');
    sidebarHandle.className = 'sidebar-resizer';
    sidebarHandle.tabIndex = 0;
    sidebarHandle.setAttribute('role', 'separator');
    sidebarHandle.setAttribute('aria-label', '调整分类侧栏宽度');
    sidebarHandle.setAttribute('aria-orientation', 'vertical');
    sidebar.append(sidebarHandle);
    layoutBindResize(sidebarHandle, {
        key: 'ws-sidebar-width', initial: 280,
        limits: () => [220, Math.max(220, Math.min(480, window.innerWidth * .45))],
        position: (event) => event.clientX - sidebar.getBoundingClientRect().left,
        apply: (value) => layout.style.setProperty('--sidebar-width', value + 'px')
    });
    const detailHandle = document.createElement('div');
    detailHandle.className = 'detail-resizer';
    detailHandle.tabIndex = 0;
    detailHandle.setAttribute('role', 'separator');
    detailHandle.setAttribute('aria-label', '调整详情高度');
    detailHandle.setAttribute('aria-orientation', 'horizontal');
    $('#itemsPanel').insertBefore(detailHandle, $('#itemDetail'));
    layoutBindResize(detailHandle, {
        key: 'ws-detail-height', initial: 260,
        limits: () => [120, Math.max(120, $('#itemsPanel').clientHeight * .65)],
        position: (event) => $('#itemsPanel').getBoundingClientRect().bottom - event.clientY,
        apply: (value) => $('#itemsPanel').style.setProperty('--detail-height', value + 'px')
    });
    setTrackerPanelCollapsed(layoutReadPreference('ws-sidebar-collapsed') === 'true');
    layoutInitTree();
});
