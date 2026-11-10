# Working Set 核心候选场景

**状态：Draft**

这些场景把历史行为描述转成可讨论的业务例子，不是已确认验收标准。`Then` 中凡写“候选预期”均需业务确认；关联规则见 [候选不变量](../domain/invariants.md)，问题见 [开放问题](../domain/open-questions.md)。

## `SCN-001` 创建 Working Set

**来源**：`EXT-ARCH §7.3`、`V4-LRT §1-9`；**状态**：Reported / Pending。

- **Given** 父 Working Set 已有可选择 Tracker，创建请求固定了输入、来源切点和幂等键。
- **When** 用户创建子 Working Set。
- **Then（候选预期）** 系统先准备身份、Binding、来源和必要关系，只有封闭清单完成后才发布（D-004：封闭不意味着自动补派生未选的结构父）；重试复用已分配身份，受理不等于可用。
- **待确认**：用户看到的中间态、取消后资源保留、默认根的业务含义。

## `SCN-002` 引用 Shared Tracker

**来源**：`FACT-002`、本对话用户对共享修改归属与禁止移除的明确确认；**状态**：业务规则已确认，技术实现待设计。

- **Given** 多个工作集已绑定同一个 SHARED Tracker。
- **When** 用户从任一工作集入口合法修改该 Tracker，或新增、修改其中 Item。
- **Then** 修改实际发生在默认工作集，所有绑定该共享 Tracker 的工作集同时可见；不存在只在特定子工作集生效的新增或修改，不派生副本、不通过 Merge 同步。
- **When** 用户尝试从工作集中移除已绑定的共享 Tracker。
- **Then** 拒绝移除，保留绑定。
- **边界**：业务对象的校验及提交仍由所属 BC 负责。权限差异、各读取面的可见性实现与验收指标继续设计，不能以异步投影为由擅自放宽“同时可见”的业务要求。此处禁止移除指解除工作集绑定，不推导 Tracker 或 Item 的删除规则。

## Tracker 共享特性的创建约束

**状态**：业务规则已获用户确认，技术实现待设计。**来源**：本对话用户明确说明及 [POL-003](../domain/facts.md)。

| Given 分类配置 | When 创建时指定共享特性 | Then |
| --- | --- | --- |
| 共享 | 共享 | 共享特性校验通过 |
| 共享 | 非共享 | 拒绝创建 |
| 非共享 | 共享 | 共享特性校验通过 |
| 非共享 | 非共享 | 共享特性校验通过 |

“校验通过”仅指共享特性约束满足，创建仍需满足其余适用业务规则。Tracker 创建成功后，任何将共享改为非共享或将非共享改为共享的请求均被拒绝，保持原共享特性。未指定共享特性时的默认值没有确认，不自行补充。分类配置的共享特性为固定出厂设定，不允许修改。

- **Given** 分类出厂配置为共享或非共享，分别覆盖尚未创建 Tracker 与已经创建 Tracker。
- **When** 尝试修改分类配置的共享特性。
- **Then** 不允许修改，分类保持原出厂共享设定，既有 Tracker 的共享特性也保持不变。

## 子工作集 Tracker 禁止移除

**状态**：业务政策已获用户确认，技术实现待设计。**来源**：本对话用户明确确认及 [POL-006](../domain/facts.md)。

- **Given** 某 Tracker 已加入子工作集，分别覆盖 SHARED 与 UNSHARED。
- **When** 用户尝试将该 Tracker 从此子工作集移除。
- **Then** 拒绝移除，原绑定及工作范围保留。
- **验收边界**：空 Tracker、已有 Item、已有本地修改均执行同一禁止规则。此政策不等于禁止 Item 删除，也不裁定整个工作集删除。

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
- **反例**：重复扩展占用准备态。历史“最后一个 Tracker 移出”反例不再适用于子工作集，因 `POL-006` 已确认加入后禁止移除。

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
- **准入范围（用户已确认）**：比对与合并只允许直接父子工作集之间进行。兄弟和跨代工作集不允许直接比对或合并。
- **验证例子**：V1 → V2 → V3 中，V1/V2 与 V2/V3 满足关系条件，V1/V3 不满足。相同父工作集下的两个子工作集也不满足。GateFlow 用 V1 内容替换 V3 草稿遵循已确认的历史内容替换合同。

## `SCN-008` 执行字段级合并

**来源**：`FACT-008`、`INV-007/008/009`；**状态**：Reported / Pending。

- **Given** 标题选择 ACCEPT，描述选择 KEEP_TARGET，所有适用差异已决定。
- **When** 用户提交 `markAsMerged=true`。
- **Then（候选预期）** 目标业务能力整体校验并应用标题，保留描述，记录真实 Apply Result 与处理确认；不得声称内容物理相等。
- **反例**：任一适用字段为 DEFER 时必须拒绝 true；两个分别合法字段组合后整体非法。

## KEEP_TARGET 的已处理确认

**用户已确认。** 内容未合并、双侧仍然不同，也允许以 `markAsMerged=true` 标记已处理。决策结果记入 MergeCheckpoint。下一次比对时，只要两边版次都没有发生变化，就不再显示该差异。

例如源标题为「定稿」，目标为「初稿」。用户选择保留目标并标记已处理，目标仍为「初稿」。MergeCheckpoint 保存该决定及对应双侧版次，后续同版次组合比对抑制该差异。

确认依据为本对话中用户对 KEEP_TARGET 的明确答复。用户进一步明确「版次」为显式升级的业务版次。普通修改增加乐观锁版本，业务版次不增加，也不产生新版次历史数据。显式升级才产生版次历史数据，并将版次号向前推进 +1。即使普通编辑改变了标题，只要双侧业务版次未变，已处理差异仍继续隐藏。关联 [阶段 3 裁定进度](../reviews/2026-11-09-stage-3-architecture-review.md#裁定进度)。

## `SCN-009` 合并后 source 再次修改

**来源**：用户在本对话的明确确认；关联 `INV-010`。**状态**：普通修改及显式升级后由用户发起比对的行为均已确认。

- **Given** 某有向差异已通过 `markAsMerged=true` 标记已处理，MergeCheckpoint 保存对应双侧业务版次。
- **When** source 或 target 普通修改标题，乐观锁版本增加，但业务版次均未变化。
- **Then（已确认）** 下次比对继续隐藏这条已处理差异。
- **When** 任一侧显式升级业务版次，用户随后再次主动发起比对。
- **Then（已确认）** 旧 MergeCheckpoint 对新双侧版次组合过时。重新比较所有适用字段属性，包含之前合并过和没有合并过的字段，显示仍然存在的差异。之前的处理标记不再抑制本次差异。
- **触发方式（已确认）** 比对由用户主动发起。版次升级后，在下一次用户发起比对时应用上述规则。
- **待设计**：乱序确认与撤销协议、过时 Checkpoint 的物理保留方式。

## `SCN-010` 并发修改与并发合并

**来源**：`EXT-ARCH §8`；**状态**：Candidate。

- **Given** 用户审阅完差异后，target 被另一命令修改，同时有重复 Merge 请求。
- **When** 两个执行者提交相同或不同决定。
- **Then（候选预期）** 对于已选 ACCEPT 的字段，按 [B09 已确认政策](shared-kernel-boundary-scenarios.md#b09-接受字段时取源端最新值) 取源端最新值。审阅后、提交前目标同字段已被编辑时，只要目标整体校验通过，就覆盖该字段且不提示冲突。候选执行协议仍需保护提交过程中的写入竞争、Checkpoint 顺序和幂等，同请求不重复应用，已成功部分保留实际结果。
- **待确认**：部分成功及仅重试未完成项已由 B08 确认；剩余为结果呈现、依赖组顺序和恢复协议。

## 子工作集新建 Item 与 Tracker 边界

**用户已确认。** 本对话中用户明确允许子工作集在任意已有 Tracker 下新建 Item，例如 Requirement，并按共享模式区分实际归属。

- 非共享 Tracker 已被纳入子工作集时，可在其中原生新建 Item，创建归属该子工作集。
- 从子工作集入口在共享 Tracker 下新建 Item，视觉上在子工作集操作，实际在默认工作集创建，并通过共享 Tracker 可见。
- 子工作集不允许新建 Tracker。

这些规则区分操作入口、对象创建归属与集合创建权限。具体对象的创建合法性与权限由所属 BC 裁决。PLM/DQA 已确认的共享查看接入范围继续按各 BC 合同处理，通用创建归属规则不自动增加其界面的创建入口。用户已确认新增 Item 可以回并父工作集，派生及回并的 CommonId/LocalId 关系见 [身份规则](../domain/glossary.md#item-身份规则用户已确认)，标识分配与具体创建协议继续设计。

来源为本对话用户的明确答复。关联 [POL-002/007](../domain/facts.md)、[创建边界](../domain/shared-kernel-boundary.md#item-创建入口与归属用户已确认)。

## 子工作集新增 Item 回并父工作集

**用户已确认。** 确认依据为本对话中用户对新增 Requirement 回并问题的明确答复「允许」。

- **Given** 子工作集在自己的非共享 Tracker 中新建了一条 Requirement，父工作集的对应 Tracker 下没有该对象。
- **When** 用户通过合并选择将这条新增需求带回父工作集。
- **Then** 允许在父工作集创建对应 Item，目标业务 BC 负责校验并提交。父侧对象由用户的合并决定创建，属于存在性差异的显式处理。

用户已确认父子对应 Item 使用相同 CommonId、各自不同的 LocalId。默认工作集 Item 的 CommonId=LocalId，非默认工作集 Item 的 CommonId!=LocalId。标识分配及创建去重凭据待细化，见 [身份规则](../domain/glossary.md#item-身份规则用户已确认)。对象创建不会改变工作集父子链；Item 的来源或谱系如何记录继续设计。

关联 [创建边界](../domain/shared-kernel-boundary.md#item-创建入口与归属用户已确认) 与 [QUESTION-005](../domain/open-questions.md)。

## 直接父子双向合并新增、删除、修改

**状态**：业务能力已获用户确认，执行协议待设计。**来源**：本对话用户明确说明及 [FACT-007 / POL-004](../domain/facts.md)。

前提为直接父子工作集、支持比对和合并的对应非共享 Tracker，用户主动比对并选择应用差异。下列接收效果均以目标业务 BC 校验通过并提交成功为前提。

| 方向 | 源侧变化 | 接收方效果 |
| --- | --- | --- |
| 子 → 父 | 新增 Item | 父侧新增对应 Item |
| 子 → 父 | 删除已派生的 Item | 父侧删除对应 Item |
| 子 → 父 | 修改 Item | 父侧应用选定修改 |
| 父 → 子 | 新增 Item | 子侧新增对应 Item |
| 父 → 子 | 删除已派生的 Item | 子侧删除对应 Item |
| 父 → 子 | 修改 Item | 子侧应用选定修改 |

被目标 BC 拒绝时，不执行该业务变化，本条合并保持未完成，保留用户选择与拒绝结果。新增沿用已确认的 CommonId/LocalId 身份规则；修改沿用执行时取源最新值的政策。删除是用户明确选择的存在性操作，不因源侧删除自动删除其他工作集内容；既有派生内容在未应用删除合并时仍按已确认规则独立可编辑。

此能力不允许移除 Tracker 绑定，不扩展到共享 Tracker、兄弟或跨代工作集。删除的软删/彻底删除机制、删除证据保留、目标已删除等执行边界继续设计，不由此场景自动裁定。

行为协作见 [合并时序](../diagrams/collaboration-behavior.md)。

## 合并删除覆盖接收方修改

**状态**：业务政策已获用户确认，执行协议待设计。**来源**：本对话用户明确答复“一切以用户决策为准”，见 [决策优先政策](../domain/facts.md#合并决策优先于接收方变动已确认业务政策)。

- **Given** 直接父子工作集有对应 Requirement，子侧已删除该对象，父侧随后又修改它。
- **When** 用户选择将子侧删除合并到父侧。
- **Then** 不因父侧新修改提示合并冲突，仍按删除决定交由 ALM 校验并执行；校验通过并提交成功后，父侧对应对象被删除。
- **反向验收**：父侧删除、子侧随后修改，用户选择向子侧合并删除时，执行相同政策。
- **通用政策**：用户已将决策优先规则扩展到接收方的各种变动，不限于本例。新增、删除、修改的执行都应遵守用户选择，内部竞争处理不能另行要求用户作冲突决策。对象合法性仍由目标 BC 判断，未提交或被业务拒绝的操作不得标记成功。

## 阶段 3 seam 建议

在规则获得确认后，优先选择三个公共 seam：工作范围发布结果、Item Variant 首次本地写入、单条 Merge Decision 应用结果。当前只记录建议，不创建测试或接口合同。共享 Tracker、COW、Merge 和并发场景进入行为设计时，再按 [图表渐进交付清单](../diagrams/README.md) 增加状态图、时序图或事件流程图。

## SCN-011 单工作集内同族有效变体唯一

**状态：业务规则 Accepted；验收表述 Draft，未运行测试。** 来源：阶段 3.1 用户裁定，POL-005 / QUESTION-004。

- Given 工作集有效范围已有 CommonId=C 的变体 A。
- When 创建、纳入或合并试图让同族另一变体 B 同时生效。
- Then 有效范围不能同时存在 A、B；同一工作集内 CommonId 唯一。
- 同族变体在不同工作集共存不违反此规则。重复请求如何返回、竞争操作如何裁决仍待协议设计。

## SCN-012 派生附件与关系固定

**状态：业务规则 Accepted；验收表述 Draft，未运行测试。** 来源：阶段 3.1 用户裁定，FACT-006 / QUESTION-006；补充 B04。

- Given 派生时附件状态为 A0、关系状态为 R0。
- When 来源随后修改附件或关系，目标读取其派生依据或首次编辑。
- Then 派生依据仍为 A0、R0，不替换为来源最新状态。来源删除也不得破坏已确认的首次编辑保证。
- 不由此裁定关系目标内容递归快照、权限冻结、永久保留或目标后续主动修改规则。

批准证据见 [阶段 3.1 用户批准记录](../reviews/2026-11-09-stage-3-1-rule-convergence.md#用户批准记录与生效范围)。

## SCN-013 非共享 Tracker 派生独立身份

**业务事实：用户确认 D-002；验收表述：Draft，未执行测试。** 关联 FACT-003、QUESTION-002；[直接裁定依据](../reviews/2026-11-09-stage-3-architecture-review.md#d-002)。

- Given 一个非共享源 Tracker A。
- When 对 A 执行一次合法派生。
- Then 产生具有不同业务身份的 Tracker B，A 与 B 是两个独立领域对象；Tracker 具有自己的状态、生命周期与管理行为，Working Set 不拥有其完整生命周期。
- Then Tracker 追溯层级通过 parentId/sourceId 表达；在字段语义裁定前，不写 parentId=A 或 sourceId=A 的测试期望。
- 范围：仅一次非共享派生；不推导 B 能继续派生、不允许或允许改谱系、同工作集能同时纳入 A/B、Item 所属聚合或删除级联。SCN-002 的 Shared 规则保持原样。

## SCN-014 Tracker 双维关系

**业务规则：用户确认 D-003；验收表述 Draft，未运行测试。** 依据：[D-003](../reviews/2026-11-09-stage-3-architecture-review.md#d-003)，[完整图](../diagrams/tracker-dual-relations.md)。

- Given WS0→WS1→WS2 是直接逐级工作集链，非共享 T0→T1→T2 逐级派生。
- Then T1.sourceId=T0，T2.sourceId=T1；T2.sourceId=T0 不符合禁止跳级规则。
- Given WS1 内 B1 的直属结构父为 A1，B1 的派生来源 B0 位于 WS0。
- Then B1.parentId=A1，B1.sourceId=B0。parentId 不指向 B0，不用于跨工作集复制。
- 结构父需在本工作集有效组织范围；Shared 不能仅由物理归属断言有效或无效。跨工作集父级是否一致待确认。
- 自环/结构环作为 INV-015 候选反例，尚不批准具体拒绝协议。字段可变性、树继承、同源数量、Item 归属不由本场景补全。

SCN-013 的字段未定描述为 D-002 当时状态，当前字段期望以本场景为准；其非共享派生新身份规则不变。

## SCN-015 非共享 Tracker 派生组织裁剪

**业务规则：用户确认 D-004；验收表述 Draft，未运行测试。** 关联 POL-009、INV-019—021；[裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-004)、[图](../diagrams/tracker-dual-relations.md#d-004-clipping)。

- Given WS0 中非共享 Tracker B 的直属结构父为 A；WS1 直接派生自 WS0。
- When 派生 WS1 时只选择 B，没有选择 A，采用允许的根节点结果。
- Then WS1 产生新身份 Tracker B1，B1.parentId=null、B1.sourceId=B；不自动创建 A1。B1 是 WS1 中的合法根，源 A/B 的组织关系不因目标裁剪被改写。
- 反例：不能以“必须完整继承树”为由拒绝这一结果或自动补建 A1；不能将 B1.sourceId 清空或改为 A 来修复组织父。
- 未决分支：Shared 混合树、同源多派生及整批失败/原子性均不在本场景补全。父也选择时的默认继承与创建前显式重组已由 D-005 确认（见 SCN-016），派生后移动已由 D-006 禁止（见 SCN-017）。源关系不被回写是本例派生含义的推论，不规定其并发编辑协议。

## SCN-016 创建前显式重组

**业务规则：用户确认 D-005；验收表述 Draft，未运行测试。** 关联 POL-010、INV-023；[裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-005)。

- Given WS0 中非共享 Tracker B 的结构父为 A；WS1 派生时同时选择了 A 与 B。
- When 创建 WS1 的派生 Tracker，且未在创建完成前做显式重组。
- Then 默认继承已选节点间的组织关系：B1.parentId=A1（A1 为 A 在 WS1 的对应身份）。
- When 用户在创建完成前显式指定新的合法组织结构，包括使 B1 成为根。
- Then 以用户指定结构为准，B1.parentId 可为 null 或其他合法父；无论默认继承还是显式重组，B1.sourceId 仍为 B（直接来源不变）。
- 反例：不得把显式重组解释为创建完成后再修改 parentId；不得以默认继承为由拒绝用户在创建完成前指定其他合法结构。

## SCN-017 创建后组织位置不可变

**业务规则：用户确认 D-006；验收表述 Draft，未运行测试。** 关联 POL-011、INV-022；[裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-006)。

- Given Tracker T 已完成创建，组织位置为 parentId=P。
- When 请求将 T 移动（更改 parentId）、更换父节点或调整为根（parentId=null）。
- Then 全部被拒绝，parentId 保持创建完成时确定的值；不存在创建后移动的合法路径。
- 边界：此冻结仅针对组织位置，不影响 T 的其他业务状态或生命周期；不封闭该 Working Set 继续加入新的 Tracker，也不扩大 POL-002 的原生创建入口。sourceId 不因移动请求而变化。

## SCN-018 创建时父缺失与组织环反例

**业务规则：合法性约束与 D-006 相关；验收表述 Draft，未运行测试。** 关联 INV-014/015/023；[裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-006)。

- Given 创建 Tracker 时指定 parentId 指向一个在当前 Working Set 有效组织范围内不存在的父节点。
- Then 创建不能以该非法父完成；必须改用合法父或 null 根。父缺失是创建期反例，不是创建后移动的借口。
- Given 创建候选结构形成组织环（如 A.parent=B 且 B.parent=A，或自环 A.parent=A）。
- Then 该候选结构非法，创建不能完成。无环检查集中于创建候选结构（INV-015），不批准创建后并发移动。
- 待确认（QUESTION-009.a）：父 Tracker 后续删除或停用时，仍存在的子 Tracker 如何处理。候选包括拒绝该父生命周期操作、保留有明确历史/停用语义的引用，或另行明确业务处置；本轮不选择。不得自动清空/重挂子 parentId 或级联删除，也不以本问题重新开放普通移动权限。

<a id="scn-019"></a>
## SCN-019 首次 COW 业务对象与索引身份一致

**候选；D-007 提出，未确认事务协议。** 关联 INV-024。

- Given 非共享 Tracker 派生后的 VIRTUAL 业务对象（如 Requirement）首次本地写入，触发 COW。
- When 目标业务 BC 创建本地业务对象并提交，同时 Working Set 维护 LightTrackerItem 公共身份索引。
- Then（候选预期）本地业务对象与 LightTrackerItem 的公共身份（CommonId/LocalId）保持一致，二者指向同一逻辑条目。
- 待确认：LightTrackerItem 是否为有效身份及版本定位的权威来源；新业务对象与索引更新的提交顺序与一致性协议；业务对象成功但索引失败时的恢复。

<a id="scn-020"></a>
## SCN-020 业务提交成功而索引失败

**候选；D-007 提出，未确认收敛协议。** 关联 INV-025。

- Given 业务对象修改已由业务 BC 校验并提交成功。
- When 后续 LightTrackerItem 索引更新失败（投递丢失、投影失败等）。
- Then（候选预期）系统不伪造成功，也不丢失已提交业务内容；索引通过可靠重试或回读业务事实恢复一致。
- 待确认：业务提交与索引是双事实，权威回读、幂等键、失败可见性与恢复协议；不得用索引状态反向否定业务提交成功。

<a id="scn-021"></a>
## SCN-021 派生创建 LightTrackerItem

**业务规则：用户确认 D-008；验收表述 Draft。** 关联 INV-026/027、FACT-013。

- Given 已创建 Working Set 聚合与 Tracker 聚合。
- When 执行 TrackerItem 派生（Copy-on-Write，不立即物化完整业务对象副本）。
- Then 创建 LightTrackerItem，分配当前变体 LocalId、保留 CommonId，记录 SourceLocalId 与 SourceRevision，经 TrackerId/WorkingSetId 指向。
- 反例：不得把派生等同立即物化；未物化不代表变体不存在。

<a id="scn-022"></a>
## SCN-022 LightTrackerItem 引用范围约束

**候选；D-008 指向确认，范围约束规则未定。** 关联 INV-028。

- Given LightTrackerItem 经 TrackerId/WorkingSetId 指向所属 Tracker 与 Working Set。
- Then（候选预期）该 Tracker 属于该 Working Set 的有效工作范围，来源可追溯。
- 待确认：范围约束的精确规则与校验协议。

<a id="scn-023"></a>
## SCN-023 未物化经 Query 与工厂重建

**业务规则：用户确认 D-008；验收表述 Draft。** 关联 INV-029/030。

- Given 派生后的 LightTrackerItem 处于未物化（VIRTUAL）状态，无本地完整业务内容。
- When 业务调用方经 Working Set 解析身份后，通过业务 BC 的 CQRS Query 获取业务对象。
- Then 业务 BC 借助工厂以派生来源（Source Cut）重建完整业务聚合并返回；调用方不感知底层是否已物化。
- 边界：Working Set 不拥有或重建业务 BC 完整状态；业务对象内部不变量由业务 BC 负责。

<a id="scn-024"></a>
## SCN-024 首次 COW 并发反例

**候选；D-008 提出，物化协议未定。** 关联 INV-031/032。

- Given 同一 LightTrackerItem 未物化，两个命令并发首次本地写入。
- When 双方都尝试物化并提交完整业务对象。
- Then（候选预期）最多一个业务对象物化获胜，身份（LocalId 候选）保持不变，失败方重读后重新校验。
- 待确认：首次物化完成标志与有效内容指针、LocalId 是否等同业务聚合变体身份、业务内容与 LightTrackerItem 更新是否同步原子。

<a id="scn-025"></a>
## SCN-025 来源变更触发 COW 物化并切换有效内容

**业务规则：用户确认 D-009；验收表述 Draft。** 关联 FACT-014、INV-033。

- Given WS0 的 R0 被 WS1 的 R1（未物化，Source-backed）引用为派生来源。
- When WS0 对 R0 做普通修改（Revision 不升级）：先 Query 修改前事实 A，再修改保存为 B，发送来源变更事件。
- Then COW 物化 Handler 据 A 形成 R1 自身完整业务聚合并保存，发物化完成事件；LightTrackerItem 索引 Handler 标记 R1 已物化，有效内容来源切换到自身 LocalId 及自身有效版次（Local-backed）。
- 反例：普通修改不得自动升级 Revision；物化前 R1 不依赖源端最新头版次作为永久内容。

<a id="scn-026"></a>
## SCN-026 修改前事实的可靠取得（反例二，D-010 已确认）

**业务规则：用户确认 D-010；验收表述 Draft。** 关联 INV-034。

- Given WS0 修改前 Query 已得到 A，随后修改保存为 B。
- When 异步物化 Handler 执行时，只能读取当前数据库内容 B。
- Then（已确认）修改前事实 A 作为事件关联的不可变内容快照与来源变更事件一起传递，物化 Handler 据此形成 R1 内容 A，而非 B。
- 边界：事件序列化、可靠交付机制与持久化策略留待实施设计；不得将可变的头 Revision 或仅有 SourceVersion 的事件当作不可变快照。

<a id="scn-027"></a>
## SCN-027 物化切换后保留派生历史来源（反例三）

**候选；D-009 提出，不未经确认新增字段。** 关联 INV-036。

- Given WS1 的 LightTrackerItem 原 sourceLocalId 为 R0。
- When 物化后有效来源切换为 R1 自身。
- Then（候选预期）原 sourceLocalId（Derivation Origin）不被覆盖，仍可追溯 R1 来源于 R0。
- 待确认：派生历史来源与当前有效内容来源是否分别建模、如何表达；不得未经确认增加数据库字段。

<a id="scn-028"></a>
## SCN-028 连续修改的多事件乱序（反例四）

**候选；D-009 提出，未确认幂等与并发。** 关联 INV-035。

- Given 同一来源在短时间内连续修改多次，多个来源变更事件先后到达（可能乱序）。
- When 各事件的 COW 物化 Handler 处理已物化变体。
- Then（候选预期）已物化变体不被重复或错误覆盖；物化唯一性与事件幂等保证最多一次有效物化。
- 待确认：事件顺序、幂等键、有效内容指针的 CAS 收敛。

<a id="scn-029"></a>
## SCN-029 物化完成前的查询可见性（反例一，D-010 已确认）

**业务规则：用户确认 D-010；验收表述 Draft。**

- Given WS0 的 R0 Revision 3 内容为 A；WS1 的 R1 未物化，引用 R0 头版次；R0 普通修改为 B 但 Revision 仍为 3；来源变更事件尚未处理。
- When WS1 此时执行 Query。
- Then（已确认）来源修改已保存、COW 尚未完成期间，允许派生工作集暂时读取到来源修改后的内容 B；物化完成后，R1 以需要保护的修改前事实 A 形成独立内容。
- 语义：这是最终一致性，物化完成前允许暂时读到新内容，物化完成后以修改前快照 A 形成独立内容。

<a id="scn-030"></a>
## SCN-030 连续修改与事件乱序（D-011 首次快照优先）

**业务规则：用户确认 D-011；验收表述 Draft。** 关联 INV-037/038、FACT-016。

- Given 来源对象初始内容 A、Revision 3；子工作集派生变体 R1 尚未物化。
- When 来源第一次修改 A → B，事件 E1 携带快照 A；来源第二次修改 B → C，事件 E2 携带快照 B；E2 先于 E1 到达消费者。
- Then（已确认）R1 最终必须物化为 A（第一次修改前快照），而非 B 或 C；快照选择按来源修改的实际先后顺序而非事件到达顺序。
- 反例：不得采用「最先收到的事件直接物化」；同一 R1 不得因重复/乱序事件产生多个有效物化结果。

<a id="scn-031"></a>
## SCN-031 首次事件重复投递

**候选；D-011 提出，幂等协议未定。** 关联 INV-038。

- Given R1 已因 E1 完成物化（内容 A，Local-backed）。
- When E1 因重试或重投再次到达。
- Then（候选预期）重复 E1 不生成新的有效业务对象，不改变已物化内容 A；物化幂等保证同一 LocalId 只物化一次。
- 待确认：幂等键与物化声明的收敛协议；不得用数据库锁/消息队列偏移量替代业务裁定。

<a id="scn-032"></a>
## SCN-032 Local-backed 后收到旧来源事件

**业务规则：用户确认 D-011 第 6 条；验收表述 Draft。** 关联 INV-039。

- Given R1 已物化并切换为 Local-backed，有效来源指向自身。
- When 收到来源后续修改事件（或迟到的旧事件）。
- Then（已确认）来源事件不覆盖 R1 的本地业务内容；R1 保持已物化内容。
- 边界：历史派生来源保留追溯，但当前有效内容来源不再由来源事件改变。

<a id="scn-033"></a>
## SCN-033 同一业务 Revision 下多次普通修改

**候选；D-011 提出，来源变更顺序表达未定。** 关联 Source Change Order。

- Given 来源 R0 保持业务 Revision 3，期间发生多次普通内容修改（A→B→C），每次普通修改只推并发版本、不升级 Revision。
- When 各次修改分别产生来源变更事件。
- Then（候选预期）首次快照仍按来源修改的实际先后顺序确定，业务 Revision 不参与排序；需 Source Change Order 机制反映成功修改顺序。
- 待确认：复用已有 Concurrent Version 还是独立变更序列表达来源修改顺序。

<a id="scn-034"></a>
## SCN-034 来源修改与目标派生并发

**候选；D-011 提出，派生基准识别未定。** 关联 Derivation Baseline。

- Given 目标工作集正在派生 R0 为 R1，同时来源 R0 发生修改。
- When 派生与来源修改并发，来源变更事件可能在派生基准建立前后到达。
- Then（候选预期）R1 的 Derivation Baseline 决定「首次相关修改」的判定基准；派生基准之前的事件不触发 R1 物化。
- 待确认：多级派生中各层需要保护的目标变体如何识别。

<a id="scn-035"></a>
## SCN-035 COW 前后 SourceLocalId/SourceRevision 字段对比

**业务规则：用户确认 D-012；验收表述 Draft。** 关联 INV-040/041/042、FACT-017。

- Given WS0 的 R0（CommonId=C100、LocalId=R0、Revision=3）；WS1 派生 R1（CommonId=C100、LocalId=R1、Revision=3）。
- When COW 前（Source-backed）：R1.SourceLocalId=R0、R1.SourceRevision=3，通过来源解析完整业务内容。
- When COW 完成后（Local-backed）：R1.LocalId=R1、R1.CommonId=C100、R1.SourceLocalId=R1、R1.SourceRevision=R1.Revision，真实业务内容已物化。
- Then COW 不改变 LocalId、CommonId；SourceLocalId/SourceRevision 从指向来源切换为指向自身。
- 边界：Revision=3 仅为示例，不推断物化时业务 Revision 必然不变；R0→R1 历史派生关系不要求由 LightTrackerItem 保存。

<a id="scn-036"></a>
## SCN-036 多级派生与有效内容解析（D-012 历史，逐级解析假设被 D-013 排除）

**历史候选；D-012 提出「逐级递归解析」，现由 D-013 排除。** 关联 FACT-017/FACT-018。

- 原假设：WS0/R0 → WS1/R1 → WS2/R2；R1.SourceLocalId=R0、R2.SourceLocalId=R1，R1/R2 均未物化，R2 经 R1 逐级定位。
- D-013 修正：R2.SourceLocalId 直接指向实际拥有完整内容的变体（若 R1 未物化则直接指向 R0），不逐级递归解析。
- 现见 SCN-037（场景 A）与 SCN-038（场景 B）。

<a id="scn-037"></a>
## SCN-037 仅根物化的直接定位与扇出（场景 A）

**业务规则：用户确认 D-013；验收表述 Draft。** 关联 FACT-018、INV-043/044。

- Given WS0/R0 → WS1/R1 → WS2/R2；仅 R0 已物化，R1、R2 未物化。
- Then R1.SourceLocalId=R0、R2.SourceLocalId=R0（R2 跳过未物化的 R1 直接指向 R0）。
- When R0 修改前形成快照 A、修改后为 B，发来源变更事件。
- Then COW 依据 SourceLocalId 发现依赖 R0 的 R1、R2，二者均以快照 A 物化（扇出，不限派生深度）。
- 反例：不得采用逐级递归解析；R2 不因 R1 未物化而无法定位。

<a id="scn-038"></a>
## SCN-038 中间层物化后的定位与依赖（场景 B）

**业务规则：用户确认 D-013；验收表述 Draft。** 关联 FACT-018、INV-044。

- Given R0、R1 已物化，R2 未物化。
- Then R2.SourceLocalId=R1（R1 已物化，是最近实际拥有完整内容的上游）。
- When R0 修改：不应因此直接物化 R2（R2 不依赖 R0）。
- When R1 修改：R2 才依据 R1 修改前内容进行 COW。
- 反例：R0 修改不得触发 R2 物化；依赖者集合由 SourceLocalId 指向的实际来源决定。

<a id="scn-039"></a>
## SCN-039 中间上游物化不重定向已有下游（场景 A）

**业务规则：用户确认 D-014；验收表述 Draft。** 关联 FACT-019、INV-045。

- Given WS0/R0 → WS1/R1 → WS2/R2；R0 已物化，R1/R2 未物化，均 SourceLocalId=R0。
- When R1 在 R0 未修改的前提下独立完成 COW：R1.SourceLocalId=R1、R1.SourceRevision=R1.Revision。
- Then R2 仍保持 SourceLocalId=R0，不自动改为 R1。
- 反例：不得因 R1 物化而自动更新 R2 的 SourceLocalId；SourceLocalId 不需始终动态指向最近已物化祖先。

<a id="scn-040"></a>
## SCN-040 有效来源依赖而非派生层级触发 COW（场景 B/C）

**业务规则：用户确认 D-014；验收表述 Draft。** 关联 FACT-019。

- Given R1 已物化（SourceLocalId=R1），R2 未物化且仍 SourceLocalId=R0。
- When R0 修改：R2 是 R0 的直接有效内容依赖者，据 R0 的首次修改前快照执行 COW。
- When R1 修改：不应因 R2 在派生层级上位于 R1 之下就自动将 R2 视为 R1 的有效内容依赖者，R2 不参与 R1 的 COW。
- 反例：识别需物化变体基于当前有效来源依赖关系，而非递归遍历工作集后代。

<a id="scn-041"></a>
## SCN-041 多目标并行物化互不影响（场景 D）

**候选；D-014 提出，快照选择独立性确认。** 关联 INV-037/038。

- Given R0 修改导致 R1、R2 等多个目标并行 COW。
- When 部分目标先完成物化。
- Then 不应改变其他目标的快照选择与有效来源；各目标独立按各自首次快照物化。
- 反例：一个目标的完成不得触发其他目标的来源重定向或快照替换。

<a id="scn-042"></a>
## SCN-042 新派生按 D-013 选来源与已有下游对照（场景 E）

**业务规则：用户确认 D-014；验收表述 Draft。** 关联 FACT-018/FACT-019。

- Given R1 已物化（SourceLocalId=R1），R2 已存在且 SourceLocalId=R0（不重定向）。
- When 从已物化的 R1 派生新变体 R3。
- Then R3 按 D-013 选择 SourceLocalId=R1（派生当时的实际内容来源），与已有下游 R2 保持 SourceLocalId=R0 形成对照。
- 反例：新派生选来源与已有下游不重定向是两条不同规则，不得混用。

<a id="scn-043"></a>
## SCN-043 派生按时间基准选 Revision 且不复制内容

**业务规则：用户确认 D-015；验收表述 Draft。** 关联 FACT-020、INV-046。

- Given 用户发起 Working Set 派生，选择命名 Snapshot 或 Head 作为时间基准 T。
- When 系统按 T 选择之前适用的最大业务 Revision，定位实际拥有内容的来源 LocalId，创建 LightTrackerItem 保存 LocalId/CommonId/SourceLocalId/SourceRevision。
- Then 派生完成，不复制完整业务聚合；SourceLocalId 遵循 D-013 直接指向实际拥有内容的变体。
- 反例：Head 不是动态查询时刻，而是派生请求确定的时间点；派生不等待来源停止变化，不因并发修改先完成而改变已选时间基准。

<a id="scn-044"></a>
## SCN-044 同一 Revision 内时间基准与内容状态（D-015 反例，D-016 部分裁定）

**D-015 提出，D-016 部分裁定：派生仅固定版次、COW 保护从派生完成开始。** 关联 INV-047/048/049。

- Given R0 Revision 3 在 10:00 已存在；10:20 内容 A；10:20 普通修改 A→B（Revision 仍 3）；10:30 用户以 Head 派生 R1 选 Revision 3；10:40 普通修改 B→C（Revision 仍 3）。
- D-016 裁定：SourceRevision=3 只表达业务版次，不表达内容；派生不冻结 Revision 3 的普通内容。10:40 修改是否触发 R1 COW，取决于 R1 完整派生是否已在 10:40 前完成（见 SCN-045/046）。
- 仍待裁定：完整派生完成与来源修改实际先后的判定协议（场景 C 竞态）。

<a id="scn-045"></a>
## SCN-045 派生完成后来源修改触发 COW（场景 A，D-017 后判定简化为消费时）

**业务规则：用户确认 D-016/D-017；验收表述 Draft。** 关联 FACT-021/022、INV-049/050。

- Given R0 处于 Revision 3；R1 已完整派生完成（R1.SourceLocalId=R0、SourceRevision=3、未物化）。
- When R0 普通修改 B→C（Revision 不变），事件携带不可变修改前快照 B。
- Then（D-017）Handler 消费时查询到 R1 满足条件，R1 参与异步 COW，最终物化为 B（修改前快照）。
- 反例：判定依据是消费时是否满足条件，而非追溯修改开始时间。

<a id="scn-046"></a>
## SCN-046 消费时未满足条件则不物化（场景 B，D-017 修订）

**业务规则：用户确认 D-017；验收表述 Draft。** 关联 FACT-022、INV-050。

- Given R1 尚在完整派生操作执行过程中（LightTrackerItem 尚未创建）；R0 普通修改 B→C（Revision 仍 3）。
- When Handler 消费来源变更事件时，查询不到 R1（尚未派生完成）。
- Then 此次消费不物化 R1；随后 R1 派生完成，仍 Source-backed，Query 得到 R0 Revision 3 当前内容 C。
- 反例：判定依据是消费时是否查询到满足条件的 LightTrackerItem，而非修改发生时派生是否完成。

<a id="scn-047"></a>
## SCN-047 来源修改与派生完成竞态（D-016 场景 C，D-017 关闭）

**D-016 提出，D-017 关闭：不再比较修改开始与派生完成时间。** 关联 FACT-022。

- Given 来源修改已开始 → R1 派生完成 → 来源修改成功提交。
- D-017 裁定：不追溯比较修改开始时间与派生完成时间；Handler 消费时查询到 R1 满足条件即物化，查询不到则不物化。
- 反例：不再以开始时间还是提交时间判断，改为消费时确定。

<a id="scn-048"></a>
## SCN-048 COW 目标集合在消费时确定

**业务规则：用户确认 D-017；验收表述 Draft。** 关联 FACT-022、INV-050。

- Given 来源 R0 修改并保存，发布携带不可变快照的变更事件。
- When Handler 异步消费事件，查询当前 SourceLocalId=R0、SourceRevision=匹配、尚未物化的 LightTrackerItem。
- Then 查询命中的所有 LightTrackerItem（不限工作集派生深度、不追溯修改开始时间）均参与本次 COW 物化。
- 反例：不在修改开始时提前固定目标集合；即使派生在修改开始后才完成，只要消费时满足条件也参与。

<a id="scn-049"></a>
## SCN-049 Revision 显式升级触发 COW 与 LightTrackerItem Revision

**业务规则：用户确认 D-018；验收表述 Draft。** 关联 FACT-023、INV-051/052。

- Given R0 处于 Revision 3；R1 未物化，SourceLocalId=R0、SourceRevision=3、自身 Revision=1。
- When R0 显式升级业务 Revision 3→4（内容可能不变），发布来源变更事件。
- Then（已确认）Revision 升级触发 R1 的 COW（与普通修改一样受 COW 保护）。
- When R1 物化后，SourceRevision 指向来源，但 LightTrackerItem 的 Revision 与 SourceRevision 不是始终同步（派生初期 SourceRevision=3、Revision=1）。
- 边界：LightTrackerItem 自身 Revision 与业务 BC 的 TrackerItem Revision 通过事件同步（细节不细化）。

<a id="scn-050"></a>
## SCN-050 空间绑定前置与槽位唯一

**业务规则：用户确认（2026-11-10 补充）；验收表述 Draft。** 关联 POL-012、INV-053/054/055。

- Given 目标 WorkingSet WS1 尚未关联 ProductSpace P。
- When 尝试为 P 下的 Tracker 建立 TrackerBinding。
- Then 必须先有 WS1 对 P 的 SpaceBinding；Tracker.productSpaceId 必须等于 P；同一 WS1/ProductSpace 最多一个有效 SpaceBinding；同一 WS1/ProductSpace/Classification 最多一个有效 TrackerBinding。
- 反例：不得在没有 SpaceBinding 时建立 TrackerBinding；不得在同一 WorkingSet 重复纳入同一 Tracker。

<a id="scn-051"></a>
## SCN-051 Classification 投影生成 Tracker

**业务规则：用户确认（2026-11-10 补充）；验收表述 Draft。** 关联 FACT-025、INV-056。

- Given 平台元数据已初始化 Classification 集合，常驻内存。
- When 创建 ProductSpace P。
- Then 系统读取适用 Classification 集合，按分类逐个创建 Tracker 聚合并持久化，归属于 P，并建立在默认 WorkingSet 的 TrackerBinding。
- 边界：Tracker 不随元数据初始化自动全量创建；同一 Classification 在不同 ProductSpace 可对应不同 Tracker；同一 ProductSpace/WorkingSet/Classification 最多一个有效 Tracker。

<a id="scn-052"></a>
## SCN-052 子 WorkingSet 纳入与移除（Shared/非共享）

**业务规则：用户确认（2026-11-10，修订 POL-006）；验收表述 Draft。** 关联 POL-013/014、INV-057/058/060。

- Given 子 WorkingSet WS1 纳入直接上游 Tracker。
- When Shared Tracker 纳入：复用原 TrackerId，仅建立新 TrackerBinding。
- When 非共享 Tracker 纳入：创建新 Tracker 聚合，建立直接 sourceId 来源。
- When 移除 Shared Tracker：仅解除 TrackerBinding（不删除聚合；不受「有 Item 不允许删除」约束，因为那是针对聚合删除）。
- When 移除非共享 Tracker：无 TrackerItem 数据且无派生子 Tracker → 删除该 Tracker 并解除绑定；已有派生子 Tracker → 不允许删除（保留来源链）。
- When 任意 Tracker 存在 TrackerItem 数据 → 不允许移除（避免遗留业务数据）。
- 边界：非共享成功移除后可重新纳入、重新派生新身份；移除空间内最后一个 Tracker 不触发空间移除（SpaceBinding 保留）。

<a id="scn-053"></a>
## SCN-053 Tracker 删除约束

**业务规则：用户确认（2026-11-10）；验收表述 Draft。** 关联 POL-014、INV-058。

- Given 一个非共享 Tracker T。
- When 尝试删除 T。
- Then 必须满足：无 TrackerItem 数据（空 Tracker）、无派生子 Tracker、无组织子节点（parentId 指向 T）；否则不允许删除，校验提示含有子节点。
- 反例：有 Item 的 Tracker 不能删除；有派生子 Tracker 的非共享 Tracker 不能删除；有组织子节点但无派生子节点的也不能删除（避免 parentId 悬空，D-006 禁止重挂）。
- 例外：默认 WorkingSet 下的 Shared Tracker 在空且无子时可以删除；Shared Tracker 从子 WorkingSet 移除仅解除 TrackerBinding。

<a id="scn-054"></a>
## SCN-054 默认 WorkingSet 与 ProductSpace 创建初始化

**业务规则：用户确认（2026-11-10 补充）；验收表述 Draft。** 关联 FACT-024/026、INV-059。

- Given 系统初始化：默认 WorkingSet（固定 Id=1）由平台元数据预初始化，无 ProductSpace。
- When 用户创建 ProductSpace P。
- Then 读取适用 Classification 集合，按分类逐个创建 Tracker 聚合并持久化，归属 P，建立在默认 WorkingSet 的绑定关系。
- 边界：默认 WorkingSet 不可删除/隐藏；Tracker 只能在默认 WorkingSet 创建原始聚合；子 WorkingSet 不能独立创建原始 Tracker，只能纳入直接上游。

<a id="scn-055"></a>
## SCN-055 未物化变体参与 Diff 与 Merge 修改

**业务规则：用户确认（2026-11-10）；验收表述 Draft。** 关联 FACT-027、INV-061/062。

- Given 目标变体 R1 尚未物化（Source-backed）。
- When 用户对 R1 与来源变体 R0 发起 Diff。
- Then Diff 可比较（未物化不阻止 Diff）。
- When 用户选择对 R1 应用 Merge 修改（ACCEPT 字段）。
- Then 在 merge 过程中构建 R1 的完整业务聚合正常 save（物化与修改一体），不要求先单独物化。
- 边界：Merge 修改仍需目标业务 BC 整体校验并提交；Merge 不绕过 COW/物化语义。

<a id="scn-056"></a>
## SCN-056 MergeRound 载体与 MergeCheckpoint

**业务规则：用户确认（2026-11-10）；验收表述 Draft。** 关联 FACT-027、INV-063。

- Given 用户发起 Diff→Merge（LRO）。
- When MergeRound 受理：记录参数（方向、范围、决策）、进度、成功/失败信息。
- Then 合并决策（用户 command 入参）作为 ValueObject 归属 MergeRound；MergeCheckpoint 独立聚合，跨轮次保存最近处理状态。
- 边界：MergeRound/MergeCheckpoint 属合并模型区域，不是三类核心聚合之一；共享 Tracker 不参与，非共享只允许直接父子准入。
