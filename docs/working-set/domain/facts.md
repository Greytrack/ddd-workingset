# Working Set 候选事实、政策、假设与技术约束

**状态：Draft（混合知识登记）；已接受条目保持 Accepted，权威归档见 [事实与政策主题基线 v1](facts-policy-baseline-v1.md)。**

来源代号见 [证据登记](evidence-register.md)。已有逐条用户确认的事实、政策及组织架构约束；其余历史陈述仍待确认。文档保持 Draft，限定确认范围及候选首版清单见 [阶段 3.1 收敛](../reviews/2026-11-09-stage-3-1-rule-convergence.md)。

## Organization / Architecture Constraint（Confirmed）

| 编号 | 约束 | 状态 | 直接依据 | 固定范围与仍可设计范围 |
| --- | --- | --- | --- | --- |
| `AC-001` | Working Set 是企业 Shared Kernel 的组成能力，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 同时包含 Privilege、Workflow、Class Hierarchy 等共享能力。 | Confirmed | `USER-ARCH-2026-11-09`：用户明确称其为外部集体确定、当前无法动摇的大基调；两张附件图与文字一致 | 固定：Working Set 不移出 Shared Kernel。开放：内部领域模型、聚合边界、模块、事务、对外契约、与业务 BC 及其他共享能力的局部协作。 |

`AC-001` 不是 Accepted ADR，也不证明 Working Set 是单一聚合、单一 BC 或微服务。

## Business Fact（逐条标注确认范围）

| 编号 | 陈述 | 确认状态 | 来源 | 关联／验证方式 |
| --- | --- | --- | --- | --- |
| `FACT-001` | 历史资料把 Working Set 描述为选择 Tracker 并形成有效工作范围，而非拥有全部 Item。 | Reported / Pending | `EXT-ARCH §0`、`V4-UL:10` | 访谈工作集创建、发布、归档的业务负责人；关联 `QUESTION-001` |
| `FACT-002` | SHARED Tracker 及其 Item 的新增、修改实际归属默认工作集，所有已绑定工作集同时可见，不产生子工作集独有副本。 | 用户已确认 | 本对话用户对共享修改归属的明确说明 | [SCN-002](../scenarios/core-scenarios.md#scn-002-引用-shared-tracker)；权限与可见性实现继续设计 |
| `FACT-003` | 非共享 Tracker 派生产生新的独立业务身份，源与派生 Tracker 是两个不同领域对象；Tracker 间追溯层级由 parentId、sourceId 表达。 | 用户已确认 D-002（仅非共享派生） | 用户 2026-11-10 明确裁定；历史 `EXT-ARCH §0/3.1`、`V4-DOMAIN:409` 保留为背景 | 字段准确指向、多级派生、谱系可变性及同谱系共存仍待确认；Item 边界不由本条推导；SCN-013、QUESTION-002 |
| `FACT-004` | VIRTUAL Item 在没有本地业务内容行时仍被描述为已存在的逻辑条目。 | Reported / Pending | `V4-DOMAIN:214/547`、`EXT-ARCH §1` | 以查询、权限、删除和引用场景验证；`QUESTION-004` |
| `FACT-005` | 历史材料使用 CommonId 关联同族变体、LocalId 标识具体变体，并要求实例化不更换 LocalId。 | 部分 Accepted：R05 身份关系；实例化稳定性待验证 | `V4-OVERVIEW:5`、`V4-DOMAIN:19/30` | 派生与回并的等值关系已由用户确认，见 [身份规则](glossary.md#item-身份规则用户已确认)；CommonId 家族唯一性及雪花 ID 已由用户确认，实例化身份稳定性继续验证；`QUESTION-004` |
| `FACT-006` | 派生内容读取被要求固定到可回读的 Source Cut，而不是跟随来源最新值。 | Accepted：R07/R19 固定派生内容含附件关系；保留协议待设计 | `V4-DOMAIN:438/463`、`EXT-ARCH §3.1` | 证明历史、附件、关系及删除态均可按切点回读；`QUESTION-006` |
| `FACT-007` | 直接父子工作集支持双向比对、合并；任一方向的新增、删除、修改均可经用户选择应用到接收方。source/target 是本轮角色，不固定为父或子。 | 用户已确认 | 本对话用户明确说明父子双向新增、删除、修改的接收效果 | [双向合并场景](../scenarios/core-scenarios.md#直接父子双向合并新增删除修改)；目标业务 BC 校验并提交 |
| `FACT-008` | Merge 数据选择、实际 Apply Result 与处理确认被描述为不同事实。 | 部分 Accepted：R08—R11；DEFER 完整条件仍候选 | `MERGE §4.2-4.4`、`EXT-ARCH §1/10` | 用 `KEEP_TARGET/DEFER` 四组合验收；`QUESTION-008` |
| `FACT-009` | Reference 可在逻辑目标有效但物理端点暂不可解析时保持有效悬空状态。 | Reported / Pending | `REFERENCE CONTEXT:14/25`、`REFERENCE 05 §4` | 访谈用户可见语义及删除守卫；`QUESTION-010` |
| `FACT-010` | 创建、拓展、替换和合并被设计为可恢复长流程，受理成功不等于业务结果已可用。 | 部分 Accepted：R16/R17 GateFlow 替换；其他长流程仍候选 | `EXT-ARCH §7.3`、`V4-LRT §3-9` | 用重启、取消、半批失败场景验证；关联 `INV-011` |
| `FACT-011` | 当前架构示意把 `create / extend / resolve / diff / merge` 画作业务 BC 调用 Working Set 的统一 API 面。 | Reported / Pending | `USER-ARCH-2026-11-09` 附件 `image(1).png` | 确认这些是稳定业务能力、现有 API 名称还是示意占位；关联 `QUESTION-013` |

## Business Policy（逐条标注确认范围）

| 编号 | 当前历史主张 | 状态 | 来源 | 为什么不是不变量／如何验证 |
| --- | --- | --- | --- | --- |
| `POL-001` | 每个治理范围只有一个默认根且普通用户不能删除。 | Pending | `V4-OVERVIEW:7`、`EXT-ARCH §1` | 业务治理选择；物理 ID=1 另列 `TC-001` |
| `POL-002` | 子工作集不允许新建 Tracker。历史材料另主张 Tracker 只能由默认根创建。 | 子工作集禁止创建已由用户确认；其他创建入口范围待定 | 本对话用户明确说明；历史 `V4-DOMAIN:394-398` | 区分创建 Tracker 与在已有 Tracker 下创建 Item |
| `POL-003` | 分类配置的共享特性为固定出厂设定，不允许修改；Tracker 的共享特性在创建时确定，后续不允许修改。分类配置为共享时只能创建共享 Tracker；分类配置为非共享时，创建时可选择共享或非共享。 | 用户已确认 | 本对话用户明确确认创建选择规则、创建后不可变及分类共享特性为固定出厂设定 | [创建验收场景](../scenarios/core-scenarios.md#tracker-共享特性的创建约束)；分类共享特性变更问题已关闭，不以是否已创建 Tracker 为条件；不推导其他分类属性的变更规则或允许迁移 |
| `POL-004` | 比对、合并只允许直接父子工作集之间双向进行，支持新增、删除、修改，作用于支持这些能力的对应非共享 Tracker。 | 用户已确认直接父子准入范围 | 本对话用户明确答复；历史 `MERGE:42/841` | 兄弟及跨代不允许直接比对/合并；GateFlow 历史内容替换适用独立规则 |
| `POL-005` | 同一个 Working Set 有效范围内 CommonId 唯一，同一家族只允许一个有效变体。 | Accepted（阶段 3.1 用户裁定） | 用户 2026-11-09 明确确认；历史 `V4-DOMAIN:425-427` | SCN-011；QUESTION-004 仅保留分配、去重及并发协议 |
| `POL-006` | Tracker 一旦加入某个子工作集，无论 SHARED 或 UNSHARED，后续均不允许移除；不以是否已有 Item 或本地修改为条件。 | 用户已确认的当前业务政策 | 本对话用户明确确认“不论共享还是非共享，后续不允许移除” | 限定工作集绑定的移除；不推导 Tracker 销毁、Item 删除或整个工作集删除规则；`QUESTION-009` |
| `POL-007` | 子工作集可在已有非共享 Tracker 下新建 Item；共享 Tracker 下通过子工作集入口新建的 Item 实际在默认工作集创建。 | 用户已确认 | 本对话用户明确答复，细节见 [创建场景](../scenarios/core-scenarios.md#子工作集新建-item-与-tracker-边界) | 回并父工作集创建对应 Item 已获用户确认，见 [回并场景](../scenarios/core-scenarios.md#子工作集新增-item-回并父工作集)。CommonId/LocalId 等值关系已确认，标识分配机制待定；`QUESTION-005` |
| `POL-008` | SHARED 修改对所有已绑定工作集同时可见；具体读取面的实现与验收指标待设计。 | 业务要求已确认；技术指标待定 | 本对话用户明确确认“所有工作集同时可见” | 不得自行改为允许业务可见延迟；`QUESTION-003` |

## Hypothesis（Stage 2 inference）

| 编号 | 假设 | 依据 | 关联与验证 |
| --- | --- | --- | --- |
| `HYP-001` | Working Set 更像版本化 Manifest／工作视图，而非 Git Branch。 | `FACT-001`、SHARED/UNSHARED 可混合 | 用 10 个核心场景验证其解释力；`AP-003` |
| `HYP-002` | 历史假设混合了单个 Tracker 独立性与整个谱系的独立生命周期。 | D-001/D-002 已确认单个 Tracker 独立聚合及非共享派生新身份；不确认整个谱系是聚合 | 谱系按身份引用、Lineage 不单独设根仍是设计推论；AP-002/003 不自动获批 |
| `HYP-003` | ItemVariant 可能是承载身份、固定来源和物化状态的领域根，而非纯索引。 | `FACT-004/005/006` | 判断这些状态是否具有业务生命周期；`QUESTION-004/006` |
| `HYP-004` | 历史候选曾因担心跨类型重号而提出 namespace/type/CommonId 复合家族键。 | 已被用户确认的全局家族唯一性排除其重号前提 | CommonId 足以标识逻辑家族，类型可用于路由或校验；见 [身份规则](glossary.md#item-身份规则用户已确认) |
| `HYP-005` | History/Snapshot 是 COW、Compare 和审计的共同上游能力。 | `FACT-006` | 做历史可读性 spike；`QUESTION-006` |
| `HYP-006` | Compare & Merge 应是独立模型边界，通过业务对象公开能力写目标。 | `FACT-007/008` | 验证各业务对象字段规则与失败语义；`QUESTION-008` |
| `HYP-007` | Reference 与 Association 需要不同生命周期和缺端点语义。 | `FACT-009` | 业务访谈关系方向、删除、恢复；`QUESTION-010` |
| `HYP-008` | 超大 Working Set 需要 Manifest revision／分片准备，而不是加载全部 Binding 的单聚合。 | `EXT-ARCH §5.1/11.18` | 收集 Binding 规模和并发指标；`QUESTION-001` |

## Technical Constraint／现有实现事实

| 编号 | 陈述 | 状态 | 来源 | 处理 |
| --- | --- | --- | --- | --- |
| `TC-001` | 默认根物理 ID 被 V4 固定为 `1`。 | Reported implementation convention | `V4-OVERVIEW:7` | 不进入业务不变量；确认部署兼容需求 |
| `TC-002` | V4 偏好单聚合提交加可靠事件，不使用跨聚合事务。 | Architecture constraint/proposal | `V4-CONSISTENCY:3-13` | 需由可见性与恢复要求验证，而非反向定义业务 |
| `TC-003` | 现有业务内容可能采用覆盖式表结构，历史读取能力未知。 | Pending technical fact | `EXT-ARCH §6 B/12 P0` | 做历史、附件、关系、墓碑能力盘点 |
| `TC-004` | 旧实现存在 Long 主键、旧 branch 字段、SDK/Biz 分层等结构。 | Reported legacy constraint | `EXT-ARCH §3.3`、`V4-OVERVIEW:38` | 只作为迁移证据，不定义领域身份 |
| `TC-005` | 事件可能至少一次投递，投影存在延迟。 | Architecture assumption | `REFERENCE 05 §1/5` | 明确幂等键、因果序列和最大延迟 |
| `TC-006` | `pre-design/` 内存在大量失效相对链接。 | Observed repository condition | 阶段 1/2 链接检查 | 不影响语义，但降低历史证据可导航性 |

## 合并决策优先于接收方变动（已确认业务政策）

用户明确确认：选择合并后，无论接收方发生什么变动，均以用户的合并决策为准，不因接收方变化提示合并冲突。该政策适用于直接父子双向合并的新增、删除、修改。例如子侧删除 Requirement 后父侧又修改它，用户选择向父侧合并删除，仍执行删除，不要求因父侧新修改重新确认。

本政策决定变化竞争中的业务优先级，沿用此前已确认的目标业务 BC 整体校验及提交职责；业务规则拒绝仍保留未完成结果。执行协议应兑现该优先级，不把内部乐观锁竞争直接升级为要求用户重新裁定的合并冲突。实际结果与失败仍须如实记录，不能把未提交操作记为成功。源字段值继续按已确认政策在执行时读取最新值。

验证见 [合并删除覆盖接收方修改](../scenarios/core-scenarios.md#合并删除覆盖接收方修改)。

## Tracker 加入子工作集后禁止移除（已确认业务政策）

用户先确认共享 Tracker 绑定后禁止移除，随后明确统一当前业务限定：Tracker 一旦加入某个子工作集，无论共享还是非共享，后续均不允许移除。统一规则见 `POL-006`；验证见 [子工作集 Tracker 禁止移除](../scenarios/core-scenarios.md#子工作集-tracker-禁止移除)。历史输入中“有内容时禁止解绑或销毁”的说法拆开处理：禁止移除不再以内容为条件，销毁语义仍待确认。

阶段 3.1 用户已批准 R01—R17 及限定术语，接受范围以 [BASELINE](../BASELINE.md) 为准；本页其余候选内容仍 Draft。FACT-006 的派生历史覆盖现已明确包含派生时的附件与关系状态（用户 2026-11-09 直接裁定，SCN-012）；保留期限、读取和删除守卫协议仍待设计，不推导永久保留。

## D-002 的确认范围

用户 2026-11-10 明确确认：非共享派生建立新的独立 Tracker，源与派生是不同领域对象，追溯层级通过 `parentId`、`sourceId` 表达。Tracker 拥有自己的状态、生命周期及管理行为，Working Set 不拥有其完整生命周期；D-001 的独立聚合及停用 Tracker Branch 规则继续有效。Shared Tracker 的 FACT-002/POL-008 完全不变。

`parentId` 不先解释为直接父，`sourceId` 不先解释为最初来源；不自动确认多级派生、谱系可修改、同谱系共存或 Item 归属。FACT-003 原历史陈述中的 Item 变体部分不借本次 Tracker 裁定整体升级，既有 Item 身份裁定仍按原范围有效。来源和影响见 [阶段 3 D-002](../reviews/2026-11-09-stage-3-architecture-review.md#d-002)。

POL-002 的禁止子工作集新建 Tracker 与本次派生产生新身份分别保留：前者是创建入口政策，后者是派生结果；不从派生结果推导开放任意原生新建入口。具体用例分类与权限协议待设计。
