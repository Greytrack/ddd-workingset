---
feature: io-tab-iframe-fullscreen
created: 2026-10-22
status: implemented-pending-browser-validation
assignee: codex
reviewer: Claude
supersedes: io-tab-component-modal（组件化方案，改动面过大，不采用）
---

# 工单：输入输出「参考知识包 / 实际交付物」弹窗 —— iframe 铺满视口方案

## 1. 背景与需求

页面示例：`/frontend/workingset/1/workspace/628239570753920/subclass/12010103/tracker/628239571198362/object/628239736832384/tab/420212`

输入输出页签（`IODynamicTab`）点「参考知识包」「实际交付物」单元格，会打开 `KnowledgePacks` 弹窗，里面用 iframe 渲染目标对象的附件/关联关系页签。测试反馈两点：

1. **弹窗太宽**：现在是 1360，改为 **1200**。
2. **弹窗里点「添加」等按钮，二级弹窗被困在 iframe 内**（iframe 只有 1200×640，「添加」弹窗本身也是 1200 宽），看上去像弹窗嵌弹窗。**期望二级弹窗像正常弹窗一样覆盖整个页面。**

## 2. 方案（已定，不要改方向）

**iframe 不再放在外层 Modal 里，而是铺满整个视口（`position: fixed; inset: 0`，背景透明）；1200 宽的"外层弹窗"改由 iframe 内的页面自己渲染。** 这样 iframe 内所有二级弹窗天然拥有整个视口。

- iframe 内页面通过一个**新 query 参数**（建议 `ioModalShell=1`，常量放 `@/common/enum/system`，与 `SHOW_TAB_ONLY` 并列）进入"弹窗外壳"模式。**所有新行为只在带该参数时生效**，其它页面、`showTabOnly` 的既有用法完全不变。
- 外层与 iframe 之间用 `postMessage` 通信（同源）。
- 对象参数仍由 iframe 自己的 URL（cell 的 `smartUrl`）决定，**不改任何共享组件的取参方式**。

### 明确不做

- 不改 `Attachments`、`RelationShips`、`DynamicTabPanel`、`EditorTable`、`TableCommon`、`useInstanceParams`、`DynamicCreateFormItemList` 等任何共享组件/Hook。
- 不改 `IODynamicTab` 关闭后的刷新逻辑（保持原来的 `reload(true)` + `cancelEdit()`）。
- 不改后端、OpenAPI、依赖。
- 不新增"关闭前未保存确认"等原来没有的行为。

## 3. 第 0 步：先另存现有工作区改动（必须先做）

当前分支 `dev-3.8-wlc-search-process-1009` 工作区里有上一轮"组件化方案"的未提交改动（约 31 个修改文件 + 9 个新增文件 + `.lattice/context/io-tab-component-modal.md`）。**不要丢弃，另存到本地分支**：

1. `git status --short` 确认改动只涉及上一轮组件化方案的文件。如有无关文件，**停下来问用户**。
2. `git switch -c wip/io-tab-component-modal`
3. 提交全部改动，但**排除本工单文件**：
   `git add -A -- . ':!.lattice/context/io-tab-iframe-fullscreen.md'`，然后 `git commit -m "wip: io tab component modal (shelved)"`
4. `git switch dev-3.8-wlc-search-process-1009`，确认 `git status` 只剩本工单文件这一个未跟踪文件，且 `git diff` 为空。
5. **不要 push。**

## 4. 实现

预计改 4～5 个文件，全部在 `web/src` 下。

### 4.1 `frontend/components/IODynamicTab/index.tsx`

- `getObjUrl` 在原有 `?showTabOnly=1` 基础上追加 `&ioModalShell=1&ioModalTitle=<encodeURIComponent(标题)>`。标题沿用现有的 `I18n.sys.tips.00000338`（参考知识包）/ `00000339`（实际交付物）。
- 其它逻辑不动（`onTrigger`、`handleKnowledgePackClose` 保持原样）。

### 4.2 `frontend/components/KnowledgePacks/index.tsx`、`index.less`

外层不再使用 `AirModal`：

- `open` 时通过 `createPortal` 到 `document.body` 渲染一个容器：`position: fixed; inset: 0; z-index` 与 antd Modal 默认层级一致（1000）。
- 容器内是 iframe：`width/height: 100%`、`border: 0`、`background: transparent`，`src = tabUrl`。注意：父页面与 iframe 页面的 `color-scheme` 不一致时浏览器会给 iframe 画不透明底色，需要保证两边一致（或在 iframe 上显式设置 `color-scheme`）。
- **加载态**：收到 iframe 的 `ready` 消息前，外层自己显示半透明遮罩和居中 Spin，避免白屏或闪烁；收到 `ready` 后隐藏外层遮罩（由 iframe 内的 Modal 遮罩接管）。iframe 加载失败或超时（建议 15s）时，外层遮罩上给出关闭按钮，不能把用户卡死。
- **消息监听**：`window.addEventListener('message', ...)`，必须校验 `event.origin === window.location.origin` 且 `event.source === iframeRef.current?.contentWindow`，数据格式校验 `{ source: 'io-modal-shell', type: 'ready' | 'close' }`，其它消息一律忽略。收到 `close` 时调用 `props.onClose()`。卸载时移除监听。
- `open=false` 时卸载 iframe（等价于原来的 `destroyOnClose`）。
- 删除原来往 iframe 注入 `.sub-navbar { display: none }` 的逻辑，改为在 iframe 内按外壳模式隐藏（见 4.3）。
- 打开期间父页面 body 禁止滚动，关闭后恢复原值。
- props 保持 `open / tabUrl / onClose / modalTitle` 不变（`modalTitle` 可不再使用，但不要删，避免调用方改动）。

### 4.3 iframe 内的"弹窗外壳"：`frontend/layout/index.tsx`（及对应 less）

- 读取 `ioModalShell`。为真时（且 `showTabOnly=1`）：
  - 给 `document.documentElement` 加一个 class（如 `io-modal-shell`），卸载时移除。在 less 中让 `html, body, #root, .layout, .layout-right-container, .layout-content-wrapper, .layout-main-content` 背景透明，并隐藏 `.sub-navbar`。所有样式都必须挂在该 class 下。
  - 把原本的 `<div className="layout ...">…</div>` 内容放进一个常开的 `AirModal`：`width={1200}`、`title` 取 `ioModalTitle`、`footer={null}`、`maskClosable`、`keyboard`（Esc）。内容区高度与原来体验一致（约 640，小屏用 `min(640px, calc(100vh - 180px))` 并允许滚动），`max-width: calc(100vw - 32px)`。
  - `onCancel`（点 X、Esc、点遮罩）时执行 `window.parent.postMessage({ source: 'io-modal-shell', type: 'close' }, window.location.origin)`。
  - 外壳 Modal 挂载后发一次 `{ source: 'io-modal-shell', type: 'ready' }`。
  - `window.parent === window` 时（直接在浏览器打开带参数的地址）不要报错；关闭按钮可以什么都不做，或回退为 `history.back()`，二选一，在执行记录里写明。
- 不带 `ioModalShell` 时，`LayoutApp` 渲染结果必须与改动前**逐字等价**。

> 如果发现外壳放在 `LayoutApp` 不合适（例如 `UnsavedChangesGuard` / `TailoringProvider` 的层级问题），可以下沉到详情页的 `showTabOnly` 渲染处，但同样只能由 `ioModalShell` 参数开启，并在执行记录里说明原因。

## 5. 验收

### 功能（在 `localhost` 开发环境，用上面的示例页面）

| 编号 | 场景 | 期望 |
|---|---|---|
| A1 | 点输出页签某行「实际交付物」 | 出现 1200 宽弹窗，标题为"实际交付物"，内容为该行目标对象的关联关系页签，无导航栏 |
| A2 | A1 中点「添加」 | 添加弹窗覆盖**整个页面**（遮罩铺满视口，弹窗不被 1200×640 区域裁剪，无横向滚动条） |
| A3 | 关闭添加弹窗 | 回到实际交付物弹窗，内容保留 |
| A4 | 点 X / 按 Esc / 点遮罩 | 整个弹窗关闭，父表格刷新（与原行为一致），页面可正常滚动与操作 |
| A5 | 点「参考知识包」（输入、输出页签都测） | 同 A1，标题为"参考知识包"，显示附件页签；上传等按钮弹出的二级弹窗同样覆盖全屏 |
| A6 | 不同行连续打开/关闭 | 每次都是对应行的对象，不残留上一次内容 |
| A7 | 窄屏（宽度 < 1200） | 弹窗不超出视口，可滚动 |
| A8 | 首次打开慢加载 | 外层有遮罩 + loading，无白屏闪烁 |

### 不影响现有功能

| 编号 | 场景 | 期望 |
|---|---|---|
| B1 | 正常打开任意对象详情页、附件页签、关联关系页签 | 与改动前一致（不带新参数，代码路径不变） |
| B2 | 已有的 `showTabOnly=1` 用法（如 Electron、智能链接新窗口打开） | 与改动前一致 |
| B3 | 输入输出页签的普通编辑、保存、对象链接跳转 | 与改动前一致 |

### 工程检查

- `cd web && pnpm tsc` 通过。
- 改动文件 ESLint、Prettier 通过；`git diff --check` 通过。
- `pnpm test:unit`：已知 HEAD 基线有 6 个失败（`ReqIFImportModal/utils.test.ts` 5 个、`TopInfo/utils/filterInitiateProcessTree.test.ts` 1 个），不能新增失败。
- `git diff --stat` 只包含第 4 节列出的文件（及新增常量所在的 `common/enum/system.ts`）。超出范围先在执行记录里说明原因。
- `web/` 下修改代码后执行 `graphify update .`。

## 6. 交付要求

- **不 commit 本次实现、不 push**，留在工作区等 Claude 审查。
- 在本文件末尾追加"执行记录"：实际改动文件、A/B 表每项的验证结果与证据（截图路径/请求记录），以及没能验证的项和原因。
- 不要把"命令执行过"写成"验证通过"。
- 浏览器验证时不要提交真实业务写操作（上传、添加、删除）；如必须验证写操作，先问用户。

## 执行记录

### 2026-10-22：执行结果

#### 第 0 步：旧方案保留

- 开始时处于 `dev-3.8-wlc-search-process-1009`；核对文件列表均为上一轮组件化方案，以及本工单，未发现无关改动。
- 已创建本地分支 `wip/io-tab-component-modal`，存档提交 `c15f74289d`，标题严格采用工单指定的 `wip: io tab component modal (shelved)`。包含旧方案 41 个文件，未包含本工单。
- 原样存档首次被提交钩子的 SPA 边界检查拦截：旧方案 `src/hooks/instanceParamsContext.ts` 引用 `@/frontend/router/modules`，构成共享层到业务端的新依赖。没有为存档修改旧方案；仅对该次 WIP 存档使用命令级 `core.hooksPath=/dev/null`，未改仓库 hooks 配置。拦截日志 `/tmp/io-component-shelve-commit.log`。此存档不代表旧方案通过检查。
- 已切回开发分支；切回后 `git diff` 为空，`git status --short` 只剩本工单未跟踪，满足干净起点。未 push。

#### 实际改动文件与实现

| 文件 | 改动 |
|---|---|
| `web/src/common/enum/system.ts` | 新增 ioModalShell/ioModalTitle query 常量及 ready/close 消息协议常量 |
| `web/src/frontend/components/IODynamicTab/index.tsx` | getObjUrl 在原 showTabOnly 参数后追加新参数和编码后的原国际化标题；仅另有 import 排序 |
| `web/src/frontend/components/KnowledgePacks/index.tsx` | body Portal 全屏 iframe；同源且同 iframe Window 的消息校验；ready 前 loading，15s 超时错误和关闭入口；打开锁 body 滚动，卸载恢复原值；关闭/目标更换时销毁 iframe |
| `web/src/frontend/components/KnowledgePacks/index.less` | 全视口容器；iframe 透明、与子文档一致的 normal color-scheme；loading 遮罩。ready 前用 opacity 隐藏而非 visibility，使 iframe 内 Modal 自动聚焦不因不可见布局被阻断，Esc 的实际表现仍待浏览器验证 |
| `web/src/frontend/layout/index.tsx` | 仅 showTabOnly=1 且 ioModalShell=1 时渲染 1200px AirModal；保留原三个 Provider 的层级；Modal 打开完成后发一次 ready；X/Esc/遮罩通过 onCancel 发 close |
| `web/src/frontend/layout/index.less` | 所有新样式限定 html.io-modal-shell；透明背景、隐藏 sub-navbar、内容高度 min(640px, calc(100vh - 180px))、窄屏最大宽度与滚动 |

实际为 6 个源码文件（估算 4～5 个之外的一份是第 4.3 节已允许的对应 layout less，透明/高度样式必须局部隔离）；另更新本工单执行记录，没有超出列明的模块。未修改任何共享组件/Hook、后端、生成接口或依赖。

直接打开带 shell 参数的地址、`window.parent === window` 时选择**关闭不操作**，不调用 history.back、不向自身发送消息。

保留的行为边界：

- `handleKnowledgePackClose` 与 HEAD 文本完全相同，仍为 setIsModalOpen(false) → reload(true) → cancelEdit()；onTrigger 未改。
- 未增加关闭前未保存确认、上传完成联动、延迟刷新等行为。
- 不带新参数时渲染同一个原 layout 子树；用去除格式空白后的文本比对确认子树内容一致，原 ElectronNavigationListener / UnsavedChangesGuard / TailoringProvider 层级保留。已有 showTabOnly 独立使用不进入新分支。
- 参数仍由 iframe 的 smartUrl 决定，不注入对象 Context，不改变取参优先级。
- 外层 ready/close 校验 origin、event.source、协议 source 与 type；其它消息忽略。加载失败/15s 超时后仍有关闭按钮，晚到的合法 ready 可以恢复展示。浏览器异常场景尚未实测。

#### 验收表：静态结果与运行时结果分开记录

本地前端原先未运行，已用现有 Vite 启动（4456）；没有运行会刷新语言资源的启动前置脚本，也没有改环境配置。新浏览器标签页可以载入前端，DOM 显示 LayoutSpin 初始化加载态；网络记录显示 `GET /plm/api/login/get_current_user` 发出后持续无响应，直接请求该接口 5s 超时、HTTP 状态 000。用户明确回复“暂不可用，记录未验证项”，因此停止重试后端，并如实保留如下状态。没有通过 mock 接口把业务验收标为通过。

| 编号 | 结果 | 证据 / 待恢复后验证内容 |
|---|---|---|
| A1 | **未验证** | 后端初始化阻塞，未进入输出单元格；1200、原标题和隐藏导航仅有代码依据 |
| A2 | **未验证** | 未能打开实际添加层；需验证遮罩铺满主视口、无裁剪/横向滚动 |
| A3 | **未验证** | 需确认关闭添加保留外壳与内容，Esc 只关闭最上层 |
| A4 | **未验证** | 需实测 X/Esc/遮罩三条路径及滚动恢复；父刷新处理函数已静态确认与 HEAD 一致 |
| A5 | **未验证** | 输入/输出参考知识包均待真实页面验证；不会选择文件或提交上传 |
| A6 | **未验证** | key=tabUrl 和关闭卸载保证新实例的设计已检查；仍需换行连续打开的运行时证据 |
| A7 | **未验证** | max-width/内容高度/滚动 CSS 已检查；需窄屏验证外壳和二级弹窗实际尺寸与可操作性 |
| A8 | **未验证** | ready 前 loading、15s 超时关闭入口已实现；本次阻塞发生在父业务页面初始化之前，不能冒充弹窗慢加载验收 |
| B1 | **浏览器未验证；静态路径确认** | 原详情 layout 子树内容比对一致，普通页面不进入新外壳；附件、关联关系源码未修改 |
| B2 | **浏览器未验证；静态门控确认** | showTabOnly 单独使用不创建外壳、不添加 html class、不启用样式 |
| B3 | **浏览器未验证；静态范围确认** | onTrigger、关闭刷新处理函数和表格编辑/保存/链接业务未改。真实保存按工单限制未提交 |

#### 工程检查

- `pnpm tsc`：通过，exit 0，日志 `/tmp/io-iframe-tsc.log`。
- `pnpm test:unit`：658 项，652 通过、6 失败，日志 `/tmp/io-iframe-unit.log`。失败为工单已知 ReqIFImportModal/utils.test.ts 的 5 项与 TopInfo/utils/filterInitiateProcessTree.test.ts 的 1 项；未出现其它失败文件。本分支不含封存方案新增测试，数量因此与上轮不同。
- Import 排序脚本已针对 import 改动文件执行；采用限定文件的 ESLint --fix，避免全站 lint:fix 修改无关文件。
- `graphify update .`：通过，exit 0，更新 6560 节点、11287 边、798 社区；图过大跳过 graph.html，graph.json/GRAPH_REPORT.md 已更新，属于 git 忽略的生成物，没有新增工作区文件。日志 `/tmp/io-iframe-graphify.log`。
- 最终 ESLint/Prettier/Stylelint、SPA 边界审计、diff 检查结果在最后复核补录。

#### 复查与交付边界

- 已检查调用链、状态销毁、消息来源、加载失败/超时兜底、样式门控和原刷新逻辑。
- 未验证的重点是透明背景实际合成、内外 Modal 的焦点/Esc、子层遮罩与窄屏；不能以类型检查通过替代。
- 本次实现不 commit、不 push，留开发分支工作区供 Claude 审查。浏览器没有执行真实上传、添加、删除或保存。
- Cycle 回顾：需求/范围直接采用已定工单；本次在 iframe 保留 URL 取参，避免共享参数改造。工程核对已完成，业务运行时验证受后端不可用阻塞。未修改技能/规则文件。



#### 最后复核

- 最终 ESLint `--max-warnings 0`：通过，exit 0，日志 `/tmp/io-iframe-eslint-final.log`。
- 改动 6 个源码文件 Prettier check：通过，日志 `/tmp/io-iframe-prettier.log`。
- 两份 Less 的 Stylelint：通过，exit 0，日志 `/tmp/io-iframe-stylelint.log`。
- 新方案 SPA 边界审计：通过，新增越界 import 0；日志 `/tmp/io-iframe-spa-audit.log`。
- `git diff --check`：通过。最终 tracked diff 仅工单列明 6 个源码文件；本工单仍未跟踪，暂存区为空，无本次实现提交。
- 后端阻塞的脱敏请求记录 `/tmp/io-iframe-init-network.json`：只有 get_current_user 发出事件，没有响应事件。本次不保留或导出认证凭据。
- 临时浏览器验证标签页已关闭；没有继续重试用户确认暂不可用的后端。
- 当前分支 `dev-3.8-wlc-search-process-1009`；旧方案仅存在本地 WIP 分支，未推送；新方案与工单执行记录留工作区等待审查。


### 2026-10-22：用户登录后的浏览器续验

用户协助登录后，内置浏览器 tab 2 已显示正常项目页和输出表格，登录用户 wlc；最初还观察到用户打开的实际交付物框（iframe 标题 `22(IO000026)`、页面标题实际交付物、关联关系按钮），随后该框被关闭。因此本轮不再把登录作为阻塞原因，也不把最初短暂观察冒充完整 A1 尺寸验收。

重新打开第一行实际交付物后，实际测得：

- 目标 URL 为 `.../subclass/12070101/tracker/628239571202456/object/630388717117760/tab/420352?showTabOnly=1&ioModalShell=1&ioModalTitle=...`，对应第一行输入输出对象，父项目仍是分类 12010103 / 实例 628239736832384 / 页签 420212。
- iframe rect `(0,0,1746,1506)`，当时父视口也是 `1746×1506`，确认外框铺满视口；父 body overflow 从空值变为 hidden。
- ready 前 iframe opacity 为 0，color-scheme 为 normal，外层有 loading 和关闭按钮。初始化超时后显示已有国际化错误文案，关闭按钮保留。加载期截图 `/tmp/io-iframe-loading.png`，超时截图 `/tmp/io-iframe-timeout.png`。
- 页面已登录，但该 iframe 的 get_current_user 请求仍失败：Vite 代理日志为 `connect ETIMEDOUT 106.75.210.232:443`，浏览器最终收到 HTTP 500。直接同接口请求 6 秒无响应。这是后端代理连接失败，不能用“需要重新登录”解释。
- 运行三种无效 close 消息测试：同源父 Window 发送、错误 origin（即使 source 指向 iframe Window）、错误协议 source，均未关闭当前 overlay（之后仍为 1）。测试没有修改生产源码或提交后端写操作。
- 点击超时界面的关闭按钮后，overlay=0、iframe=0、父 body overflow 恢复空值；父表格发出 query_tab_data_object_list，参数仍为父项目 `instanceType=12010103, instanceId=628239736832384, tabId=420212, workingsetId=1`。请求已发出不等于刷新响应成功。
- 脱敏网络证据 `/tmp/io-iframe-login-resume-network.json`；关闭后截图 `/tmp/io-iframe-timeout-closed.png`。没有导出认证信息。

验收状态补充（不覆盖上轮未验证记录的历史）：A8 的加载遮罩、15s 超时仍可关闭已取得部分运行时证据；A4 的异常页关闭、iframe 销毁和滚动恢复已通过，正常 X/Esc/遮罩仍未验证；消息校验上述三种拒绝路径通过。A1 的完整外壳尺寸、A2/A3 添加层、A5 输入输出知识包、A6 连续换行、A7 窄屏及 B 类完整回归仍待后端稳定后完成。当前没有新增源码改动。

### 2026-10-22：按截图隐藏加载中的关闭按钮

- 用户指出 loading 下方不需要“关闭”按钮。本轮只改 KnowledgePacks 的按钮渲染条件：loading 阶段仅显示 Spin；加载失败或 15s 超时进入 error 后才显示关闭按钮，保留异常退出入口。
- ready 阶段的 iframe 弹窗关闭方式、消息校验、目标 URL、关闭后刷新逻辑均不变；此前截图和“加载期有关闭按钮”的记录属于本次调整前状态。
- 复查 loading / ready / error 三态边界：按钮仅在 error 渲染；没有增加 API、参数或共享组件改动。
- 验证：pnpm tsc、该文件 ESLint（--max-warnings 0）、Prettier check、git diff --check 均通过。类型检查日志 `/tmp/io-iframe-hide-loading-close-tsc.log`。未新增测试；本轮未进行浏览器复验，业务交互未验证项仍保留。
- Cycle 回顾：本轮范围为一个 JSX 条件及本执行记录；将正常加载展示与异常退出入口分开，避免改变原有弹窗业务行为。未 commit、未 push。
