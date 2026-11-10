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
