/* Folder instances have a separate navigation surface from Tracker containers. */
let foldersScope = '';
let foldersAvailable = false;

function foldersSetExpanded(expanded) {
    const open = expanded && foldersAvailable;
    $('#trackersPanel').classList.toggle('folder-expanded', open);
    $('#itemsPanel').classList.toggle('folder-view', open);
    $('#folderDrawerToggle').setAttribute('aria-expanded', String(open));
    $('#generalTrackerDrawer').setAttribute('aria-expanded', String(!open));
    $('#folderInstanceTree').classList.toggle('hidden', !open);
    $('#folderContentPlaceholder').classList.toggle('hidden', !open);
}

function foldersRenderScope(space) {
    const key = state.selectedWs + ':' + (space?.productSpaceId || '');
    const tracker = state.trackers.find((candidate) => candidate.productSpaceId === space?.productSpaceId
        && candidate.classificationId === FOLDER_CLASSIFICATION_ID);
    foldersAvailable = Boolean(tracker);
    const toggle = $('#folderDrawerToggle');
    toggle.disabled = !foldersAvailable;
    toggle.title = tracker ? '展开文件夹实例树' : '当前范围未纳入文件夹跟踪分类';
    $('#generalTrackerDrawer').classList.toggle('hidden', !foldersAvailable);
    $('#folderInstanceTree').innerHTML = `<div class="folder-scope-label">${escapeHtml(space?.name || '文件夹')}</div>
        <div class="empty item-empty"><strong>文件夹实例树待接入</strong><span>当前仅提供导航布局；实例读取及维护将在后续接入。</span></div>`;
    if (key !== foldersScope || !foldersAvailable) foldersSetExpanded(false);
    foldersScope = key;
}

document.addEventListener('DOMContentLoaded', () => {
    $('#folderDrawerToggle').addEventListener('click', () => foldersSetExpanded(
        $('#folderDrawerToggle').getAttribute('aria-expanded') !== 'true'));
    $('#generalTrackerDrawer').addEventListener('click', () => foldersSetExpanded(false));
});
