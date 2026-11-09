# Working Set 核心候选场景

**状态：Draft**

这些场景把历史行为描述转成可讨论的业务例子，不是已确认验收标准。`Then` 中凡写“候选预期”均需业务确认；关联规则见 [候选不变量](../domain/invariants.md)，问题见 [开放问题](../domain/open-questions.md)。

## `SCN-001` 创建 Working Set

**来源**：`EXT-ARCH §7.3`、`V4-LRT §1-9`；**状态**：Reported / Pending。

- **Given** 父 Working Set 已有可选择 Tracker，创建请求固定了输入、来源切点和幂等键。
- **When** 用户创建子 Working Set。
- **Then（候选预期）** 系统先准备身份、Binding、来源和必要关系，只有封闭清单完成后才发布；重试复用已分配身份，受理不等于可用。
- **待确认**：用户看到的中间态、取消后资源保留、默认根的业务含义。

## `SCN-002` 引用 Shared Tracker

**来源**：`FACT-002`；**状态**：Reported / Pending。

- **Given** 父工作范围包含 SHARED Tracker。
- **When** 子 Working Set 纳入它并在任一上下文合法修改 Item。
- **Then（候选预期）** 两个 Working Set 的权威读取解析到同一 Tracker、同一 Item Variant 和内容；不创建副本、不通过 Merge 同步。
- **反例**：两个工作集权限不同；列表投影延迟但点查已更新。需回答 `QUESTION-003`。

## `SCN-003` 扩展 Non-shared Tracker

**来源**：`FACT-003/006`；**状态**：Reported / Pending。

- **Given** 来源 Tracker 可按固定切点读取，目标 Working Set 尚未纳入同一来源。
- **When** 用户扩展该 Tracker。
- **Then（候选预期）** 建立独立 Tracker 派生节点和稳定 Item Variant 身份，目标可独立演进，初始内容可保持 VIRTUAL。
- **反例**：重复请求、并发新请求、多级派生来源仍为 VIRTUAL。

## `SCN-004` 首次修改 VIRTUAL Item 触发 COW

**来源**：`INV-003/004/005`；**状态**：Candidate。

- **Given** 目标 Item Variant 已存在、无本地内容，并有可回读 Source Cut。
- **When** 两个命令并发首次修改它。
- **Then（候选预期）** 最多一个本地内容提交获胜；LocalId 和 parent identity 不变；失败方重读后重新校验，不覆盖赢家。
- **待确认**：内容提交与变体状态的可见窗口、冲突反馈。

## `SCN-005` 多次扩展 Working Set

**来源**：`V4-LRT §10`、`EXT-ARCH §8`；**状态**：Reported / Pending。

- **Given** Working Set 已发布一部分范围，并存在合法悬空关系。
- **When** 用户增量纳入新的 Tracker 或 Item。
- **Then（候选预期）** 新范围以新 revision 发布；既有身份不重分配；关系修复独立推进，失败不回滚已发布业务范围。
- **反例**：最后一个 Tracker 移出但 ProductSpace Binding 仍存在；重复扩展占用准备态。

## `SCN-006` 跨 Working Set 引用

**来源**：`FACT-009`、`REFERENCE 02/05`；**状态**：Reported / Pending。

- **Given** 引用保存逻辑目标，但当前工作范围尚不能解析物理 Variant。
- **When** 目标后续被纳入、合并或仍不可读。
- **Then（候选预期）** Reference 保持同一身份；可解析时补齐物理端点，不可解析时保持有原因的悬空；无权不持久化为不存在。
- **反例**：用户已删除或换目标后迟到修复事件到达。

## `SCN-007` 比较两个 Non-shared 演进线

**来源**：`FACT-007`、`INV-006`；**状态**：Candidate。

- **Given** 双侧成员与内容可按冻结证据读取。
- **When** 用户选择 source 和 target 发起 Compare。
- **Then（候选预期）** 先按 Item Family 对齐存在性，再按字段规则比较；不存在、删除、无权和历史不可用分别呈现；LocalId 不同本身不是差异。
- **待确认**：仅父子还是允许兄弟／跨代，Merge Base 如何获得。

## `SCN-008` 执行字段级合并

**来源**：`FACT-008`、`INV-007/008/009`；**状态**：Reported / Pending。

- **Given** 标题选择 ACCEPT，描述选择 KEEP_TARGET，所有适用差异已决定。
- **When** 用户提交 `markAsMerged=true`。
- **Then（候选预期）** 目标业务能力整体校验并应用标题，保留描述，记录真实 Apply Result 与处理确认；不得声称内容物理相等。
- **反例**：任一适用字段为 DEFER 时必须拒绝 true；两个分别合法字段组合后整体非法。

## `SCN-009` 合并后 source 再次修改

**来源**：`MERGE §4.3/4.4`、`INV-010`；**状态**：Reported / Pending。

- **Given** 某有向差异已 `KEEP_TARGET + true`，Checkpoint 绑定最终双侧证据。
- **When** source 或 target 的相关业务证据变化。
- **Then（候选预期）** 重新判断原确认是否仍有效；新差异可再次出现，旧确认事件不能覆盖更新的撤销或决定。
- **待确认**：无关字段变化是否使整个 token 失效。

## `SCN-010` 并发修改与并发合并

**来源**：`EXT-ARCH §8`；**状态**：Candidate。

- **Given** 用户审阅完差异后，target 被另一命令修改，同时有重复 Merge 请求。
- **When** 两个执行者提交相同或不同决定。
- **Then（候选预期）** 对于已选 ACCEPT 的字段，按 [B09 已确认政策](shared-kernel-boundary-scenarios.md#b09-接受字段时取源端最新值) 取源端最新值。审阅后、提交前目标同字段已被编辑时，只要目标整体校验通过，就覆盖该字段且不提示冲突。候选执行协议仍需保护提交过程中的写入竞争、Checkpoint 顺序和幂等，同请求不重复应用，已成功部分保留实际结果。
- **待确认**：部分成功的用户体验和依赖组顺序。

## 阶段 3 seam 建议

在规则获得确认后，优先选择三个公共 seam：工作范围发布结果、Item Variant 首次本地写入、单条 Merge Decision 应用结果。当前只记录建议，不创建测试或接口合同。共享 Tracker、COW、Merge 和并发场景进入行为设计时，再按 [图表渐进交付清单](../diagrams/README.md) 增加状态图、时序图或事件流程图。
