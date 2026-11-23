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
