const $ = (selector) => document.querySelector(selector);

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
