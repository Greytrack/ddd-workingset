---
feature: io-tab-iframe-fullscreen
reviewed: 2026-10-22
reviewer: Codex
status: changes-and-evidence-follow-up-required
---

# Codex 验收审查：iframe 全屏弹窗

## 结论

核心交互已有证据支持：1200px 外壳、iframe 全视口且透明、添加层超出外壳显示、关联类型下拉与关键源对象请求、Esc 只关闭顶层、外壳三条关闭路径及父项目刷新。没有从现有证据发现阻断这些交互的缺陷。

**不签署“全部验收通过”**：原实现工单 4.3 的内容高度未满足；若干“通过”包含未完成的子项；部分证据缺少可复核的执行细节。状态应为“核心交互通过，存在一项待修偏差及若干补验/证据待补项”。未运行的写入、客户端与语言场景保持未验证。

本轮只审查与记录，未修改实现，没有执行浏览器操作、业务写入、提交或推送。工程结果依据执行日志复核，本轮没有重新运行整套测试。

## 审查输入与方法

- 执行工单：同目录 `io-tab-iframe-fullscreen-qa.md`。
- 执行报告：同目录 `io-tab-iframe-fullscreen-qa-report.md`。保留原报告结论，本文件作为独立审查意见。
- 证据根目录：`/Users/wlc534/qa-evidence/io-tab-iframe-fullscreen-2026-10-22/`。
- 对原报告反引号中 `.png/.json/.log/.py` 文件去重解析，实际为 **57 个引用且全部存在**；存在性不等于每项断言已被证明。
- 读取 Q01～Q15 相关测量/网络 JSON、工程日志、Q09 延迟脚本；目视复核拖动后、下拉、输入附件、窄屏滚动到底、超时、取消确认及刷新后截图。
- 对照当前六个实现文件 diff、AirModal 本地依赖样式、原实现工单及原单测日志。当前 HEAD 与执行报告一致：`447d3343511257a6c1762d03a4fcc6ca59912723`。
- 原 `handleKnowledgePackClose` 及 `onTrigger` 区域与 HEAD 去空白比较一致。当前 tracked 修改总数为 **8**；其中 6 个属于本工单，`web/src/styles/reset.less`、`web/src/utils/theme.ts` 为另一个任务，排除审查、未清理。
- 证据未保存六个实现文件在测试开始时的内容哈希，mtime 只能作为辅助依据；不能声称已用内容哈希证明执行期间完全未变。文末记录本次审查时哈希供后续复验使用。

## 必须处理的实现偏差

### R1：外壳内容高度被基础组件限制为 495px（报告的问题编号 P1，影响程度低）

- 位置：[layout 样式](/Users/wlc534/gitlab/airedge/web/src/frontend/layout/index.less:50)。
- `q01-detail.json` 记录宽屏视口 1504×1459，body 高 495px；`q08-1024.json` 记录 1024×768 时同为 495px。基础 AirModal 的 `.ant-modal-body { max-height: 495px; }` 存在，当前外壳只设置 height，没有覆盖 max-height。
- 原工单公式在上述视口分别应为 **640px、588px**；768×600 应为 **420px**，该点现有测量符合。不能把 1024×768 的期望也写成 640px。
- 结论：接受为实现缺陷，建议修复后再完成验收；执行者不能自行把原工单的尺寸约定排除为“不否决”。这不构成高优先级 P1 事故，报告中的 P1 应明确为问题编号。
- 建议范围：只在 `html.io-modal-shell .io-modal-shell-dialog .ant-modal-body` 内覆盖 max-height，保留既定自适应 height 与 overflow。不要改基础 AirModal、共享弹窗或全局样式。
- 修复后复验：1504×1459 / 1024×768 / 768×600 下高度 640 / 588 / 420；分别打开实际交付物和参考知识包，检查二级层、滚动和 X/Esc。普通详情页及单独 showTabOnly 不应受影响。
- 本轮未实施该修复。

## 逐项验收

这里的“通过”仅限该行说明的证据范围，不能外推到所有用户、所有对象、真实写入或未运行环境。

| 编号 | Codex 判定 | 核对结果与限制 |
|---|---|---|
| Q01 | 部分通过；原 4.3 待修 | q01.json/q01-detail.json 支持 width=1200、iframe 全视口、透明、标题与目标 URL、隐藏导航。495px 高度偏差见 R1，不接受作为完整原工单通过。 |
| Q02 | 核心功能通过；数值证据需更正 | q02-after-drag.png 清楚显示添加层移到外壳边界外且完整；q03-dropdown.json 的输入框 y=417 也支持拖动后的位移。q02-drag.json 的 after 却为 x=152,y=160,height=24，不能作为文字声称的 content=(152,360,1200,723) 的测量证据。q02-open.json 记录视口高 1159，另一测量为 1459；两份数据不能当作同一时刻。需补正确的 content 矩形及视口时间对应。 |
| Q03 | 关键源对象及下拉对照通过；结论限缩 | q03-reopen-requests.json 中 query_association_data_object_list 为 12070101/630388717117760/420352；find_by_ids 带目标 tabId。下拉 7 项、默认值和按钮禁用状态与正常详情证据一致。正常详情 JSON 中 class tree 的源参数也正确，但那份记录是正常详情，不是 iframe；不要将其当作 iframe 所有接口参数的证明。保留“已采集关键读取请求未串父项目参数”，不扩展为所有按钮/所有请求。 |
| Q04 | 通过 | q04.json 分别记录 X/取消/Esc 后 add=0,shell=1,masks=1，焦点返回外壳；遮罩点击后 add 仍为 1，符合现有子层禁止遮罩关闭的规则。 |
| Q05 | 通过（传输与页面状态） | q05.json 三条关闭路径均 overlay/iframe=0、body overflow 恢复，每次父项目参数刷新并有 HTTP 200。HTTP 200 证明传输成功，不等于已检查业务响应 code/data；不能宣称所有数据内容均已独立核对。 |
| Q06 | 部分通过 | 输入附件截图与 q06-normal.json 列表/按钮对照、输出附件截图、q06-second-level.json 支持内容、宽度和拷贝层全视口。iframe src 属于目标对象。缺少附件读取接口参数的直接网络记录，未完全覆盖“读取参数一致”子项；补记录即可，不需要真实上传。 |
| Q07 | 通过（记录的连续打开路径） | q07.json 两轮六次，甲/乙与知识包目标各自正确；每次 loading 重置、iframe=1，关闭后为 0。采样支持无旧实例；不能由离散采样外推“任何时刻绝无闪帧”。 |
| Q08 | 模拟视口范围通过；保留观察项 | 两份 JSON 支持外壳宽度/关闭入口、二级 mask 全视口、无文档横向溢出；滚到底截图显示 footer/取消可达。真实移动设备未执行，不纳入通过。滚动数值 187 未出现在这两份 JSON 中，若报告保留该精确数值应补测量。 |
| Q09 | 主要场景通过；错误分类范围需明确 | slow2 支持加载中无关闭按钮，ready 后解除遮罩；timeout20b 支持 16s 错误提示和 31s 晚到 ready 恢复。但脚本在晚到 ready 后点的是外壳 X，不能单用 timeout20b 证明“超时页按钮关闭”。证据目录另有 q09-timeout20.json：最后采样 shell=0、错误按钮存在、随后 closed_overlay=0、overflow 恢复，与脚本错误态分支一致，可补充引用。该旧记录 t16.0 的实际 t_s=20.5，不可当作精确 16s 样本。未有独立 iframe onError 故障注入，保留未验证；超时分支已有证据。 |
| Q10 | 已执行普通页面路径通过 | 正常详情/关联添加、附件拷贝等证据无新壳、无 io-modal-shell class、恢复白底及无残留；静态默认 layout 子树与 provider 顺序保留。不能据此覆盖所有详情模块。 |
| Q11 | 部分通过 | 仅 showTabOnly=1 的 URL 实测通过；Electron 和真实智能链接入口未跑。按工单“含未完成子项不得整体通过”，调整总状态，不要求为本次验收搭建新客户端环境。 |
| Q12 | 部分通过；补取消确认 | 截图支持编辑和原取消确认框；刷新后显示恢复数据不能代替取消确认完成。q12-final.json 的 save/cancel 仍为 false（disabled=false），亦不是“取消完成、按钮恢复禁用”的证据。取消最终确认可在确认仅本地丢弃后补验；保存写入仍按边界不执行。行选择和链接的报告描述缺少独立状态/URL记录，建议随取消补验保留。 |
| Q13 | 部分通过 | 门控/独立打开 JSON 与代码支持原规则、无父窗口关闭不操作；文案词条有中英静态来源。运行时语言切换未测，不接受整体通过。 |
| Q14 | 静态通过；运行时拒绝路径证据待补 | 当前代码的 origin/source/协议/type 校验与清理具备。q14.json 只有 4 个计数和 valid_close=0，没有记录实际发送脚本、origin、source 身份比较、发送前/后时序；尤其“旧 iframe Window”可能仅旧 Window 发给自己，未到父窗口。需补方法/脚本及前后状态，不能仅凭 a/b/c/d=1 独立核定四种构造均正确。正常子页 ready/close 由其它用例支持。 |
| Q15 | 本工单工程检查通过；保留基线失败 | tsc/lint/格式日志支持退出码；单测 not ok 的名称和所属 suite 与原 /tmp/io-iframe-unit.log 逐项完全一致，658/652/6，无新增失败。测试整体 exit=1，不能写“全绿”。工作区为 6 个本工单源码+2 个无关修改，不能再笼统写“仅 6 个修改”。 |

## 两个范围问题的判定

1. **495px：建议修复，保留为未关闭缺陷。** 这是本次新外壳未覆盖基础样式造成的明确需求偏差；即便影响低，也不应无审批地把既定高度降为 495px。
2. **768×600 的添加 footer：本次作为体验记录，不扩大修复范围。** 滚动后 footer 可达、Esc 可退出，证据支持非外壳裁剪。尚无同尺寸普通详情添加层对照，不能把“必定是原有问题”写成实测结论；可按需补对照。此次不修改共享添加弹窗。

## 可交给 DeepSeek 的最小补验清单

仅补以下证据，不重跑全部，不改源码：

1. Q02：选取**可见的添加层 `.ant-modal-content`**，拖动前后分别量 content、外壳、视口 rect、时间和 devicePixelRatio；附截图。不可只量可拖动容器的占位高度。纠正 1159/1459 的采样时刻及 after=24 的口径。
2. Q03/Q06：补 iframe 内 class tree/关联类型候选来源及附件列表的必要读取请求 URL、源参数、状态；与普通目标详情同一入口对应。缺少源参数的通用字典请求明确说明，不硬要求它包含对象 ID。不要导出凭据。
3. Q12：先确认取消最终“确定”只本地撤销，再进入编辑、取消并确认；记录值恢复、保存/取消禁用状态及后续表格可操作。顺带记录行选择计数及对象链接真实目标。禁止保存真实修改。
4. Q14：补四种构造的可复现脚本或步骤，列发送前后状态。旧 iframe 场景保存旧 contentWindow 引用，关闭并重新打开后，构造向父窗口分发的消息并明确 event.source 为旧引用、与新 iframe 引用不等；若使用合成 MessageEvent，明确它只验证监听器拒绝逻辑，不称为真实浏览器发信。真实 X 正常关闭作为对照。
5. Q09：在报告中明确引用 timeout20（超时退出）与 timeout20b（晚到恢复）各自证据；分开记录 onError 未验证。修正 Q11/Q13 部分通过状态，保留环境与业务写入未验证清单。

R1 的代码修复应在用户授权具体修复范围后另行实施；修复后追加上述三尺寸验证及基本二级层/普通页面冒烟，不能用当前 495px 版本证据冒充修复版验收。

## 工程日志口径补充

- `q15-after-other-ai.log` 中 `ESLINT_NEW_EXIT=1` 是对另一个任务的 reset.less 使用 ESLint 导致的解析错误。它不是本工单四个 TS/TSX 文件 lint 失败，也不能从报告中隐去；该 Less 的样式检查记录是 Stylelint exit=0。
- 首次 tsc “not found”只在报告文字中出现，当前 q15-tsc.log 仅保留成功重跑。环境瞬态是执行者解释，缺原始失败输出，不能作为已证实根因；成功重跑有日志支持。
- 无需为这两处外部 hover 修改重跑本工单全部浏览器场景；它们的视觉回归由另一个任务负责。

## 本轮审查快照（SHA-256）

后续复验如文件变化，按变动范围重验。本快照不能追溯证明 DeepSeek 执行时内容相同。

```text
e20be012c8bd80c1e89da73b70e8d21ec657dd507f97025f8810a6da72f5464e  web/src/common/enum/system.ts
ee69270193c33d7cc7013debc5e4ee7e7c48390aa4da64b7d6a623bf5bb2b9b3  web/src/frontend/components/IODynamicTab/index.tsx
20135472137c25f7f16f07c00d9fee52835fda1f66ae5abce223ba96c5af299e  web/src/frontend/components/KnowledgePacks/index.tsx
c72ca6283b6c3cc507c34b2c40aea4ff8e610a5103a4f4a622573331d85fbf0e  web/src/frontend/components/KnowledgePacks/index.less
fb88c5960b73aa5eaabd121eb676af9a98b9b09e0a956fcbecf7a0e7d442c0a9  web/src/frontend/layout/index.tsx
907aa226a641a1a81e41fb899b6879303f50e1c79215f56ce95777df08a0a31c  web/src/frontend/layout/index.less
```
