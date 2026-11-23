const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function render(checkedOut, busy = false) {
    const page = {innerHTML: ''};
    const context = vm.createContext({
        document: {addEventListener() {}},
        $: () => page,
        escapeHtml: (value) => String(value),
        infoDisclosure: () => '',
        formatAuditTime: () => '—',
        worksetLabel: () => '工作集',
        state: {selectedWs: '1', selectedTracker: {name: '需求'}}
    });
    vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/requirement-detail.js'), 'utf8'), context);
    vm.runInContext(`requirementPage = {item:{name:'需求'},data:{title:'标题', description:'描述', checkedOut:${checkedOut}, revision:1},busy:${busy},error:''}; renderRequirementPage();`, context);
    return page.innerHTML;
}

test('签入态标题与描述真正禁用，保存不可用', () => {
    const html = render(false);
    assert.match(html, /id="detailReqTitle"[^>]*disabled/);
    assert.match(html, /id="detailReqDescription"[^>]*disabled/);
    assert.match(html, /type="submit" disabled/);
});

test('服务端已签出状态启用标题、描述和保存', () => {
    const html = render(true);
    assert.doesNotMatch(html.match(/<input id="detailReqTitle"[^>]*>/)[0], /disabled|readonly/);
    assert.doesNotMatch(html.match(/<textarea id="detailReqDescription"[^>]*>/)[0], /disabled|readonly/);
    assert.doesNotMatch(html, /type="submit" disabled/);
});

test('生命周期操作处理中不可编辑', () => {
    const html = render(true, true);
    assert.match(html, /id="detailReqTitle"[^>]*disabled/);
    assert.match(html, /data-req-command="checkin" disabled/);
});

test('审计信息以不可编辑的定义列表展示', () => {
    const html = render(true);
    const audit = html.match(/<dl class="requirement-audit">[\s\S]*?<\/dl>/)[0];
    for (const label of ['版次', '签入签出状态', '创建人', '创建时间', '更新人', '更新时间']) {
        assert.ok(audit.includes(`<dt>${label}</dt>`));
    }
    assert.doesNotMatch(audit, /<input|<textarea|<select/);
});

test('缺失或无效审计时间不编造时间', () => {
    const context = vm.createContext({document: {querySelector() {}}});
    vm.runInContext(fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/js/core.js'), 'utf8'), context);
    for (const value of [null, undefined, '', 'invalid']) {
        assert.equal(context.formatAuditTime(value), '—');
    }
    assert.notEqual(context.formatAuditTime(1730000000000), '—');
});
