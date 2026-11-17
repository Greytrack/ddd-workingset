# 执行报告：输入输出 iframe 全屏弹窗及原功能回归

- 工单：`io-tab-iframe-fullscreen-qa.md`
- 执行者：DeepSeek（本次会话）
- 执行日期：2026-10-22
- 结论摘要：Q01～Q11、Q13～Q15 通过（含记录性说明）；Q12 部分通过。未发现阻断性缺陷；发现 1 项与本工单 4.3 约 640px 高度约定不一致的低优先级偏差（P1），以及 1 项窄屏体验记录（P2）。

## 执行环境

- 时间：2026-10-22 16:29～16:55（本地时间）。
- 浏览器：Chrome for Testing 151.0.7922.34，Playwright 1.62（Python），有头模式，经 CDP 连接；设备像素比 2。
- 视口：默认 1504×1459；Q08 分别为 1024×768、768×600（CDP viewport 模拟）。
- 分支 / HEAD：`dev-3.8-wlc-search-process-1009` / `447d3343511257a6c1762d03a4fcc6ca59912723`。
- 工作区：验证期间为本工单 6 个实现文件；验证开始/结束时这 6 个文件的内容未再变更（mtime 均早于验证开始）。验证结束后，另一 AI 在同一分支追加了 `web/src/styles/reset.less`、`web/src/utils/theme.ts` 两处与本功能无关的输入框 hover 边框色调整，详见 Q15。
- 后端与登录：Vite 代理指向 `test38.airedge3.airedgesoft.com`；验证前 443 端口可达，`get_current_user` 返回 401（未登录）；由用户在弹出的独立测试浏览器中手动登录后，业务接口返回 200。未读取或导出任何 token/Cookie/密码。
- 测试行与目标（脱敏，仅对象/页签 ID）：
  - 父项目：分类 `12010103`、实例 `628239736832384`、页签 `420212`。
  - 输出行甲 `data-row-key=630388717283648`（名称 22）：实际交付物目标 `object/630388717117760/tab/420352`；参考知识包目标 `object/630388717117760/tab/420351`。
  - 输出行乙 `data-row-key=630388547823936`（名称 23）：实际交付物目标 `object/630388547647808/tab/420352`。
  - 输入行 `data-row-key=630226210128224`（文件 25989011.docx）：参考知识包目标 `object/630226209773920/tab/420351`。
- 证据目录（绝对路径）：`/Users/wlc534/qa-evidence/io-tab-iframe-fullscreen-2026-10-22/`
- 测试浏览器：独立 profile `.../profile`，CDP `http://127.0.0.1:9333`，未使用用户日常 Chrome profile；验证完成后已关闭浏览器并删除临时 profile，不保留登录会话。

## 逐项结果

| 编号 | 结果 | 实际操作与测量 | 证据绝对路径 | 未完成内容/原因 |
|---|---|---|---|---|
| Q01 | 通过（含 P1 记录） | 双击输出行甲“实际交付物”。父视口 1504×1459；iframe rect (0,0,1504,1459) 铺满；`src` 为目标 `object/630388717117760/tab/420352?showTabOnly=1&ioModalShell=1&ioModalTitle=%E5%AE%9E%E9%99%85%E4%BA%A4%E4%BB%98%E7%89%A9`；外壳 content 宽 1200、rect (152,160,1200,535)；标题“实际交付物”；iframe `background: transparent`，无导航栏，父页面可见；父 body overflow 由空值变为 `hidden`；ready 后 iframe `opacity=1`、loading 已卸载。 | `q01-shell.png`、`q01-detail.json`、`q01.json` | 无 |
| Q02 | 通过 | 外壳内点“添加”。两层遮罩均覆盖 (0,0,1504,1459)，z-index 1000/1200；“添加”content 1200×723，初始 (152,160)，未被外壳 1200×495 内容区裁剪；拖动标题栏下移 200px 后 content 位于 (152,360)-(1352,1083)，超出外壳底边 719 仍完整可见，层级在 1200，外壳不可误操作；frame 文档 `scrollWidth=clientWidth=1504`，无额外横向滚动条。 | `q02-add-open.png`、`q02-after-drag.png`、`q02-drag.json`、`q02-open.json` | 无 |
| Q03 | 通过 | 关联类型下拉：默认“依赖于”，候选“依赖于/高于/从属于/关联于/派生自/违反/排除”，下拉 rect (240,451,507,188)，未被外壳裁剪。与目标对象 `object/630388717117760/tab/420352` 正常详情页同一“添加”入口对照：默认值、7 个候选项、取消可用/保存与保存并关闭禁用、列表内容均一致。读取请求 `query_association_data_object_list` 的 `instanceType=12070101, instanceId=630388717117760, tabId=420352` 均为**源对象**参数；`find_by_ids`/`query_default_tracker_class_tree` 的 `classifyId/instanceId/tabId` 亦为源对象；所有请求体中未出现父项目 `12010103/628239736832384/420212`。 | `q03-dropdown.png`、`q03-dropdown.json`、`q03-reopen-requests.json`、`q03-normal-detail.png`、`q03-normal-detail.json`、`q03-q06-final.json` | 未选择候选对象，因此候选对象 ID 未进入提交参数；符合“只查看不确认” |
| Q04 | 通过 | 二级层 X、取消、Esc 均只关闭添加层：`add=0, shell=1, masks=1`，外壳内容保留、无残留透明遮罩、焦点落到外壳按钮；点击遮罩不关闭添加层（`add=1,masks=2`），与该组件原有 `maskClosable=false` 规则一致，未要求改变。 | `q04.json`、`q04-after-mask.png` | 遮罩关闭为原规则，不适用“必须关闭” |
| Q05 | 通过 | 无二级层时，外壳 X、Esc、点击遮罩三条路径均：父页面 overlay/iframe 卸载为 0，body inline overflow 由 `hidden` 恢复为空值（与打开前一致）；父表格发出 `query_tab_data_object_list`，参数为父项目 `instanceType=12010103, instanceId=628239736832384, tabId=420212`，HTTP 200；每次均可重新打开，父表格可继续操作。 | `q05.json`、`q05-after-shell-x.png`、`q05-after-shell-mask.png` | 无 |
| Q06 | 通过 | 输入页签 `25989011.docx` 行：目标 `object/630226209773920/tab/420351`，标题“参考知识包”，外壳宽 1200，内容为附件页签，列表 AT00000149/25989011.docx，按钮与正常附件页签一致。二级“拷贝”弹窗 800×589、遮罩铺满 (0,0,1504,1459)、z-index 1200，取消后回到外壳。输出页签参考知识包：目标 `object/630388717117760/tab/420351`，标题正确，宽 1200，空附件列表与正常附件页签一致。 | `q06-input-tab.png`、`q06-input-refkp-loaded.png`、`q06-input.json`、`q06-shell-second-level.png`、`q06-second-level.json`、`q06-output-refkp.png`、`q06-normal-attachments.png`、`q06-normal-output-attachments.png`、`q06-normal.json` | 未选择文件、未触发上传 |
| Q07 | 通过 | 两轮：行甲实际交付物→行乙实际交付物→行甲参考知识包，每轮前切换输入/输出页签。每次 iframe 仅 1 个，目标分别为 `630388717117760/420352`、`630388547647808/420352`、`630388717117760/420351`；标题与入口一致；打开瞬间 loading 存在（加载态重置）；关闭后 overlay/iframe 均为 0；未出现上一条内容或重复实例。 | `q07.json` | 无 |
| Q08 | 通过 | 1024×768：外壳 content 992×535、body 495，标题与关闭按钮在视口内；“添加”992×723，bottom=763<768，完整可见；无横向滚动。768×600：外壳 content 736×460、body 420，关闭按钮 (716,44)-(748,76) 可达；“添加”736×723，底部超出视口但 modal wrap `overflow:auto`，`scrollTop` 可到 187，footer/取消按钮最终位于 (513..576) 可达；X/Esc 均可退出。 | `q08-1024-shell.png`、`q08-1024-add.png`、`q08-1024.json`、`q08-768-shell.png`、`q08-768-add.png`、`q08-768-add-scrolled-max.png`、`q08-768.json` | 未在真实移动设备验证，仅 CDP viewport |
| Q09 | 通过 | 延迟 iframe 文档 2s：t=2s 仅遮罩+居中 Spin、无关闭按钮、iframe opacity 0；t=8s ready，loading 卸载、opacity 1。延迟 20s：t=16s 显示“服务器错误，请联系管理员！”+“关闭”按钮，Spin 消失；t=31s 该 iframe 最终 ready，错误遮罩消失、内容恢复；点关闭后 overlay=0、body overflow 恢复；重新打开重置为加载态。 | `q09-slow2-t2.0.png`、`q09-slow2-t8.0.png`、`q09-slow2.json`、`q09-timeout20b-t16.0.png`、`q09-timeout20b-t31.0.png`、`q09-timeout20b.json`、`q09_async.py` | 无 |
| Q10 | 通过 | 目标对象正常详情页（无 ioModalShell）：`html` class 无 `io-modal-shell`，页面背景 `rgb(255,255,255)`，侧边栏存在，无新外壳/overlay；关联关系“添加”弹窗 1200×723、遮罩铺满、下拉正常、Esc/取消可关；“拷贝”弹窗 800×589，取消后无残留。 | `q10-normal-copy-modal.png`、`q10b.json` | 无 |
| Q11 | 通过 | 目标 URL 仅带 `showTabOnly=1`：无 `.io-modal-shell-dialog`、无 `io-modal-shell` class、无 overlay、无新 AirModal，内容按原 showTabOnly 展示。 | `q11-q13.json` | Electron/智能链接入口无环境，未运行 |
| Q12 | 部分通过 | 行选择：勾选后“已选中1条”，取消后“已选中0条”；对象链接：点击行甲名称 `attribute-smart-link` 实际跳到 `object/630388717117760/tab/420352`，返回后父表格正常；编辑：点击“必须交付”可编辑单元格后保存/取消按钮均解除禁用（进入编辑态），点击工具栏取消弹出原“确定取消修改的数据吗？”确认框；未观察到保存/更新类写请求；最后通过刷新丢弃未保存状态，表格恢复 2 行、保存/取消禁用。 | `q12-edit-probe.png`、`q12-edit-active.png`、`q12-after-cancel2.png`、`q12-after-reload.png`、`q12-cancel.json`、`q12-final.json` | 取消编辑的最终“确定”未点击完成（刷新丢弃，无写请求）；保存写入链路按工单禁止执行，**未验证** |
| Q13 | 通过 | 静态：错误文案 `api.errMsg500`（中/英均存在）、关闭 `I18n.sys.tips.00000019`、标题复用 `I18n.sys.tips.00000338/00000339`（中/英均存在）。门控：仅 `ioModalShell=1` 不进入外壳；`showTabOnly=1&ioModalShell=1` 独立标签页直接打开可正常渲染外壳，标题取 `ioModalTitle`，点 X 不关闭、无报错（`window.parent===window` 时按约定不操作）；临时标签页已关闭。 | `q11-q13.json`、`q13-direct-both.png` | 未找到不写用户偏好的语言切换方式，运行时中/英显示未验证（仅静态词条） |
| Q14 | 通过 | 静态：校验 `origin`、`event.source`、协议 `source`、`type`；卸载时移除监听、清定时器、恢复 body overflow；`open=false` 卸载 iframe。运行时四种非法消息均未关闭 overlay：父窗口自身发送（source 不符）、合成错误 origin、真实 iframe 发送错误协议 source、重新打开后由旧 iframe Window 发送；随后正常 X 关闭成功，证明监听仍生效。 | `q14.json` | 人为派发消息只证明拒绝路径；真实 ready/close 由 Q01～Q09 正常流程覆盖 |
| Q15 | 通过 | 命令与退出码见下；`pnpm test:unit` 658 项/652 通过/6 失败，失败项与历史基线一致；工单列明的 6 个实现文件 diff 与验证时一致。验证结束后工作区另有 2 个无关样式文件（`reset.less`、`theme.ts`），来源见下。 | `q15-tsc.log`、`q15-lint.log`、`q15-unit.log`、`q15-after-other-ai.log` | 首次执行 `pnpm tsc` 曾报 `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL / Command "tsc" not found`，立即重跑 exit 0；记录原样 |

## 问题列表

### P1（影响程度：低；非 15 项通过标准的否决项）

- 前置条件：宽屏（本次 1504×1459）打开外壳。
- 复现：任一行“实际交付物/参考知识包”打开外壳，量取 `.io-modal-shell-dialog .ant-modal-body`。
- 预期：按原工单 4.3，内容区高度 `min(640px, calc(100vh - 180px))`，本视口应为 640px。
- 实际：body 高度 495px；`max-height` 为 495px，来源是基础组件 `@airedge/react-base-component/dist/AirModal/index.less:77` 的 `.air-modal .ant-modal-body { max-height: 495px; }`，新样式只设置了 `height`，未覆盖 `max-height`。1024×768 下同样为 495px；768×600 下为 420px（符合预期）。
- 影响：外壳可视内容区比设计约定矮，功能不受阻；无原行为对照问题（新外壳新样式）。
- 证据：`q01-detail.json`、`q01-shell.png`、`q08-1024.json`；源码规则见上述 less 行。
- 修复建议（本工单未授权修改）：在新样式作用域内对 `.ant-modal-body` 显式 `max-height: none`（或与 `height` 相同），再确认窄屏滚动行为。

### P2（影响程度：低；记录性）

- 前置条件：768×600 CSS 视口打开二级“添加”弹窗。
- 实际：弹窗内容高 723px，底部操作区初始在视口外；modal wrap 可滚动（最大 187px），滚动后 footer/取消可见；X 与 Esc 始终可退出。
- 判断：不是被外壳裁剪，也不是遮罩导致无法退出；属于窄屏下基础弹窗的滚动体验，未作为本次引入缺陷上报。
- 证据：`q08-768-add.png`、`q08-768-add-scrolled-max.png`、`q08-768.json`。

## 工程检查

| 命令 | 退出码 | 结果 |
|---|---|---|
| `pnpm tsc`（首次） | 1 | `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL / Command "tsc" not found`（环境瞬态） |
| `pnpm tsc`（立即重跑） | 0 | 通过 |
| `pnpm exec eslint --max-warnings 0 <4 files>` | 0 | 通过 |
| `pnpm exec prettier --check <6 files>` | 0 | 通过 |
| `pnpm exec stylelint <2 less>` | 0 | 通过 |
| `git diff --check` | 0 | 通过 |
| `pnpm run test:unit` | 1 | 658 项，652 通过，6 失败（与历史基线一致） |

单元测试失败项（6 个叶子用例）：

- `ReqIFImportModal/utils.test.ts`
  - `buildReqIfMappingRows`（子用例：构造 Specifications 根行、Specification 行与其下的 Tracker/Items 固定容器行）
  - `buildTrackerMappingsFromRows`（子用例：Items 固定行下多个 Item 类型的字段合并展示，保存时按类型分别提交）
  - `历史导入明细`（子用例：buildHistoryDetailRows 把更新条目的属性变化摊平为明细行）
  - `buildRowsFromSavedMapping`（子用例：按已保存映射重建映射表，并从预览结果还原 ReqIF 名称；查看配置时还原枚举值行的逐项映射与目标候选）
- `TopInfo/utils/filterInitiateProcessTree.test.ts`
  - `filterInitiateProcessTree: 物料源对象保留 CHANGE 并叠加问题报告根（仅问题报告子分类）`

与工单历史基线（ReqIF 5 项 + TopInfo 1 项）一致，无新增失败文件或失败用例。

修改范围复核：本工单实现对应的 tracked diff 为 `web/src/common/enum/system.ts`、`web/src/frontend/components/IODynamicTab/index.tsx`、`KnowledgePacks/index.tsx`、`KnowledgePacks/index.less`、`web/src/frontend/layout/index.tsx`、`web/src/frontend/layout/index.less` 六个文件；无共享组件/Hook、后端、生成接口或依赖变更。

验证结束后工作区新增的 2 个 tracked 修改（**不属于本工单实现，未清理**）：

- `web/src/styles/reset.less`：`.air-textarea-box.show-count-inner:hover` 边框色由 `--ant-primary-color-hover` 改为 `--ant-primary-color`。
- `web/src/utils/theme.ts`：Input / InputNumber / Select / DatePicker 增加 `hoverBorderColor: var(--ant-primary-color)`。
- 来源：用户说明由另一 AI 在同一分支修改；代码注释标注“设计师要求（2026-09-22 确认）”，属于输入框 hover/focus 边框色统一，与本工单的外壳、iframe、二级弹窗、门控、刷新逻辑无关。
- 影响评估：仅影响 input/select/date-picker 的 hover 边框色，不改变布局、层级、遮罩、iframe 尺寸或参数；本次 Q01～Q14 的运行时结论仍成立。复跑 `tsc`、Prettier、Stylelint、`git diff --check` 均通过（日志 `q15-after-other-ai.log`）。

## 收尾

- 网络限制：CDP network emulation 已复位（latency 0 / throughput -1）；route 拦截已 unroute；视口已恢复 1504×1459。
- 临时标签页：Q03/Q10/Q11/Q13 打开的对照/直开标签页已关闭；仅保留用户原输入输出页签。独立测试浏览器已关闭，临时 profile 已删除。
- 证据清理：删除登录、失败重跑、重复截图等 33 个中间产物及测试 profile；保留报告引用的 57 个证据文件。
- 工作区：验证期间为 `dev-3.8-wlc-search-process-1009` / `447d334351...`，6 个实现文件修改 + 2 个未跟踪工单文件；验证结束后另一 AI 追加 2 个无关样式文件（共 8 个修改），未清理、未提交、未推送。
- 业务写入：未执行真实添加、上传、保存、删除；Q12 编辑态通过刷新丢弃，未观察到写请求；未读取/导出 token、Cookie、密码，网络证据中的 token 查询参数已脱敏为 `***`。
- 未验证项（逐条）：
  1. Q12 保存写入链路（工单禁止）。
  2. Q12 取消编辑的最终确认点击（观察到确认框，未完成“确定”即刷新丢弃）。
  3. Q11 Electron/智能链接真实入口（无运行环境）。
  4. Q13 运行时中/英语言切换（未找到不写用户偏好的切换方式，仅静态词条）。
  5. Q14 真实业务握手之外的伪造消息只能证明拒绝路径（真实 ready/close 已由正常流程覆盖）。
  6. Q08 真实移动设备窄屏（仅 CDP viewport 模拟）。
  7. 真实上传、添加、删除、保存等业务写操作（工单禁止）。
