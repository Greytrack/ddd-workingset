const $ = (selector) => document.querySelector(selector);

function formatAuditTime(value) {
    if (value === null || value === undefined || value === '') return '—';
    const date = new Date(Number(value));
    return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
    }).format(date);
}

let infoCardSequence = 0;

function infoDisclosure(label, entries) {
    const id = 'info-card-' + (++infoCardSequence);
    const fields = entries.filter(([, value]) => value !== undefined && value !== null && value !== '')
        .map(([name, value]) => `<dt>${escapeHtml(name)}</dt><dd>${escapeHtml(String(value))}</dd>`).join('');
    return `<span class="info-disclosure"><button type="button" class="info-trigger" popovertarget="${id}" aria-label="查看${escapeHtml(label)}详情" onclick="infoPositionCard(this)">${escapeHtml(label)}</button><div id="${id}" popover="auto" class="info-card"><dl>${fields}</dl></div></span>`;
}

function infoPositionCard(button) {
    const card = document.getElementById(button.getAttribute('popovertarget'));
    if (card.matches(':popover-open')) return;
    card.showPopover();
    const anchor = button.getBoundingClientRect();
    const rect = card.getBoundingClientRect();
    const left = Math.max(12, Math.min(anchor.left, window.innerWidth - rect.width - 12));
    const top = anchor.bottom + 8 + rect.height <= window.innerHeight - 12
        ? anchor.bottom + 8 : Math.max(12, anchor.top - rect.height - 8);
    card.style.left = left + 'px';
    card.style.top = top + 'px';
    // The native popovertarget action owns toggling and focus restoration.
    card.hidePopover();
}

const state = {
    worksets: [],
    selectedWs: null,
    bindings: {spaces: [], trackers: []},
    trackers: [],
    selectedSpace: null,
    selectedTracker: null,
    items: [],
    itemsLoading: false,
    itemsError: null,
    selectedItem: null,
    content: null,
    contentLoading: false,
    contentError: null,
    tasks: [],
    taskPolling: false
};

const TASK_KEY = 'ws-creation-tasks-v2';
const FOLDER_CLASSIFICATION_ID = '10070101';
const TERMINAL_STATUSES = new Set(['Succeeded', 'PartiallySucceeded', 'Failed']);
const STAGES = [
    'CREATE_WORKING_SET',
    'COPY_UNSHARED_TRACKERS',
    'BIND_PRODUCT_AND_TRACKER',
    'INDEX_UNSHARED_TRACKERS',
    'PUBLISH_RELATION_EVENTS'
];
const STAGE_LABELS = {
    CREATE_WORKING_SET: '创建工作集本体',
    COPY_UNSHARED_TRACKERS: '创建 跟踪分类',
    BIND_PRODUCT_AND_TRACKER: '绑定产品空间与跟踪分类',
    INDEX_UNSHARED_TRACKERS: '创建 LTI',
    PUBLISH_RELATION_EVENTS: '发布关系事件'
};
const STATUS_LABELS = {
    Pending: '排队中',
    Running: '进行中',
    Succeeded: '已完成',
    PartiallySucceeded: '部分成功',
    Failed: '失败'
};
let taskTimer = null;
let taskCenterOpen = false;
let requirementContext = null;

const REASON_TEXT = {
    PREPARING: '工作集正在准备中，暂时不可用',
    NOT_INCLUDED: '该 跟踪分类 未绑定到当前工作集',
    NOT_FOUND: '对象不存在',
    DELETED: '对象已删除',
    HISTORY_UNAVAILABLE: '所需历史版本不可读',
    DEPENDENCY_UNAVAILABLE: '依赖服务暂不可用',
    PairMergeInProgress: '同一对 跟踪分类 正在合并，请等待本轮结束',
    UNKNOWN: '发生未知错误'
};

async function api(path, options = {}) {
    const response = await fetch(path, {
        cache: 'no-store',
        ...options,
        headers: {Accept: 'application/json', ...(options.headers || {})}
    });
    const text = await response.text();
    let body = null;
    if (text) {
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
    }
    if (!response.ok) {
        const error = new Error(reasonText(body) || response.statusText || ('HTTP ' + response.status));
        error.status = response.status;
        error.body = body;
        throw error;
    }
    return body;
}

function reasonText(body) {
    if (!body || typeof body !== 'object') return typeof body === 'string' ? body : '';
    const friendly = body.reason ? (REASON_TEXT[body.reason] || body.reason) : '';
    return [friendly, body.message].filter(Boolean).join(' · ');
}

function setStatus(text, kind = '') {
    const status = $('#status');
    status.textContent = text;
    status.className = 'status ' + kind;
}

function newRequestId() {
    return window.crypto?.randomUUID?.() || 'ui-' + Date.now() + '-' + Math.random().toString(16).slice(2);
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (ch) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
}

function shortId(value) {
    if (!value) return '—';
    const text = String(value);
    return text.length > 22 ? text.slice(0, 9) + '…' + text.slice(-7) : text;
}

function formatValue(value) {
    if (value === null || value === undefined) return '—';
    if (typeof value === 'object') return JSON.stringify(value);
    return String(value);
}

function findNode(nodes, id) {
    for (const node of nodes) {
        if (node.id === id) return node;
        const found = findNode(node.children || [], id);
        if (found) return found;
    }
    return null;
}
