# Working Set Design Baseline

- **Status**: Accepted（事实与政策主题基线 + 首版领域模型候选基线 + ADR-0001～0004；未正式发布）
- **Baseline Version**: 首版领域模型候选基线 v1（阶段 4，基于 D-001～D-019 及阶段 4 补充）
- **Release Status**: Unreleased
- **Released Git Commit**: None（仓库已有 Git 提交，但尚无正式发布的设计基线提交）
- **Released Git Tag**: None
- **Last Updated**: 2026-11-10

## 当前基线状态

事实与政策主题已有 Accepted 基线；首版领域模型候选基线已接受（含 ProductSpace/Classification/绑定）；正式发布仍为 Unreleased。

用户已于 2026-11-09 明确批准阶段 3.1 首版清单，并确认单工作集 CommonId 唯一及派生附件、关系固定。

## 候选基线（用户已接受，未正式发布）

用户于 2026-11-10 整体接受 [首版领域模型基线候选 v1](domain/model-baseline-candidate-v1.md) 的「建议批准」清单为**候选基线**，供后续实施引用。范围：三类核心聚合（Working Set/Tracker/LightTrackerItem）+ ProductSpace 独立聚合 + 业务 BC 聚合 + TrackerItem 契约，以及 D-001～D-019 已确认规则、ProductSpace/Classification/绑定与删除规则。

- 状态：候选基线（Accepted 候选），**未正式发布**，未创建 Git Commit/Tag。
- 阶段 4 补充：ProductSpace 独立聚合（Shared Kernel）、Classification 投影、默认 WorkingSet（Id=1 不可删除/隐藏）、SpaceBinding/TrackerBinding 为 WorkingSet 聚合内两个 Entity、Tracker 删除/移除语义、Diff/Merge 聚合定位（MergeRound 载体 + MergeCheckpoint 独立；未物化可参与 Diff；Merge 修改未物化目标时构建业务聚合并 save）（详见 [阶段 4 集中评审](reviews/2026-11-10-stage-4-productspace-classification-review.md)）。
- **D-019（Diff/Merge 衔接）**：未物化变体可参与 Diff；未物化目标被 Merge 修改时在 merge 中构建业务聚合正常 save（物化与修改一体）；合并决策为用户决策、command 入参，作为 ValueObject 归属 MergeRound 聚合；MergeCheckpoint 是独立聚合；MergeRound 是合并载体（LRO 发起后记录参数、进度、成功/失败）；共享 Tracker 不参与 Merge，非共享只允许直接父子准入。
- 未接受部分：Working Set 聚合内部结构细节、LightTrackerItem 完整属性/物化标志/内容指针、绑定 Entity 内部结构、软删/彻底删除机制、Diff/Merge 的差异冻结/令牌/幂等执行协议。
- 相关 ADR：[ADR-0001](adr/0001-tracker-independent-aggregate.md)、[ADR-0002](adr/0002-lighttrackeritem-authoritative-aggregate.md)、[ADR-0003](adr/0003-cow-event-driven-final-consistency.md)、[ADR-0004](adr/0004-productspace-independent-aggregate.md)（ADR-0004 仍 Proposed）。

## 当前已确认领域文档

正式领域归档为 [事实与政策主题基线 v1](domain/facts-policy-baseline-v1.md)（Accepted）。原始范围与确认依据保留于 [阶段 3.1 确认矩阵与批准记录](reviews/2026-11-09-stage-3-1-rule-convergence.md)：

- R01—R17 的限定陈述及报告中列明的 CONCEPT-003/007/009、身份规则、KEEP_TARGET/Checkpoint、CONCEPT-014/015 的限定含义。
- R18 / POL-005：同一工作集 CommonId 唯一，同族只允许一个有效变体。
- R19 / FACT-006 补充：派生附件、关系均固定为派生时状态。

确认人：用户；直接依据见报告“用户批准记录与生效范围”。报告的冲突分析、候选 INV 推导和未解决协议不因本清单而被接受。[facts](domain/facts.md) 与 [glossary](domain/glossary.md) 是混合状态文档，仅上述限定内容属于本清单。全部 INV、候选聚合、图表、API 及未确认 FACT/POL 子句仍不在清单内。承载此次接受内容的 Git Commit 尚未创建或登记。

## 已确认的组织架构约束

- `AC-001`：Working Set 属于企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。来源为用户于 2026-11-09 对既定企业架构的明确确认，详见 [Shared Kernel 边界](domain/shared-kernel-boundary.md) 和 [Context Map](diagrams/shared-kernel-context-map.md)。

`AC-001` 缩小了设计方案空间，但不接受任何 Working Set 内部聚合、限界上下文、API、事务或部署方案；本次仅接受上述规则与术语，不接受完整领域设计。

## 当前有效 ADR

- [ADR-0001：Tracker 独立聚合](adr/0001-tracker-independent-aggregate.md)：Accepted（D-001，2026-11-10）。
- [ADR-0002：LightTrackerItem 作为 Working Set 权威聚合](adr/0002-lighttrackeritem-authoritative-aggregate.md)：Accepted（D-007/D-008/D-012，2026-11-10）。
- [ADR-0003：COW 采用事件驱动最终一致与首次快照优先](adr/0003-cow-event-driven-final-consistency.md)：Accepted（D-009～D-018，2026-11-10）。

## 相关验收标准

已接受规则关联的业务结果见阶段 3.1 矩阵所列 SCN/B 场景。新增 [SCN-011/012](scenarios/core-scenarios.md) 分别验证单工作集唯一性及派生附件/关系固定；其验收表述仍 Draft，未运行测试。场景文档内其余候选预期和协议不整体接受；[implementation](implementation/README.md) 尚无实现证据。

## 已整理但未确认的候选知识

阶段 2 已形成以下 `Draft` 文档，供后续业务确认和正式架构评审使用；除上文明确纳入的限定子句外，其余内容不属于 Accepted 清单：

- [证据登记](domain/evidence-register.md)
- [候选统一领域语言](domain/glossary.md)
- [候选事实、政策、假设与技术约束](domain/facts.md)
- [候选业务不变量](domain/invariants.md)
- [开放问题](domain/open-questions.md)
- [候选 Context Map](domain/context-map.md)
- [Shared Kernel 边界](domain/shared-kernel-boundary.md)
- [候选领域设计图表](diagrams/README.md)
- [候选聚合边界](domain/aggregate-model.md)
- [竞争性聚合方案](proposals/aggregate-boundary-options.md)
- [核心候选场景](scenarios/core-scenarios.md)
- [阶段 2 设计审查](reviews/2026-11-09-stage-2-domain-discovery-review.md)

## 尚未确认的关键问题

以下主题的部分内容已由 D-001～D-019 与阶段 4 补充确认；仅未确认部分继续待设计：

- Working Set、Tracker、ProductSpace、业务对象及其关系的职责和边界 —— 大部分已由 D-001～D-019 + 阶段 4 覆盖；Working Set 聚合内部结构细节仍待。
- SHARED 与 UNSHARED Tracker 的准确业务语义、权限和一致性承诺 —— 共享/非共享语义与一致性已确认；权限可见性实现指标待定。
- Item Family／CommonId、业务 revision、并发 version 与事件顺序 —— 身份与版本语义已确认（D-012/D-018）；ItemVariant 已停用。
- 派生、历史读取、删除、引用修复、字段级比较与合并的正式规则 —— 派生/删除/合并（含 Diff/Merge 衔接，D-019）已确认；历史读取与引用修复的保留协议、Diff/Merge 差异冻结/令牌/幂等执行协议待实施。
- 对应业务场景、反例、验收标准及可追溯测试 seam —— 场景已建；验收表述多为 Draft、未运行测试。

这些主题仅其未被上文清单接受的剩余部分继续待设计；不得重新开放已确认的身份、共享、历史覆盖或合并业务规则。

## 最近基线变更

- **2026-11-10 阶段 4（D-019）**：用户确认 Diff/Merge 衔接——未物化变体可参与 Diff；未物化目标被 Merge 修改时在 merge 中构建业务聚合正常 save；合并决策作为 VO 归属 MergeRound；MergeCheckpoint 独立聚合；MergeRound 记录参数/进度/成功失败；共享 Tracker 不参与 Merge、非共享只允许直接父子准入。写入 [候选基线](domain/model-baseline-candidate-v1.md) 第 5/7/10 节、FACT-027、INV-061～063、SCN-055/056。发布仍 Unreleased。

- **2026-11-09 阶段 3.1**：用户接受 R01—R17 及限定术语，补充确认 R18/POL-005 与 R19/FACT-006；登记首版规则清单，发布仍 Unreleased，Commit/Tag 为 None。

- **2026-11-08**：创建基线入口，记录仓库尚无正式发布的 Accepted 设计基线；未新增或接受任何领域模型、ADR 或验收标准。
- **2026-11-09**：登记阶段 2 候选知识与审查材料；所有新增内容保持 `Draft / Reported / Candidate`，正式 Accepted 设计基线仍为空。
- **2026-11-09**：将 Mermaid 图表纳入设计治理，登记候选概念关系图与候选 Context Map；两图均为 `Draft / Candidate`，未进入正式基线。
- **2026-11-09**：记录 `AC-001` Confirmed 组织架构约束，新增 Shared Kernel 与四个业务 BC 外框；内部领域模型和跨 BC 契约继续保持 Draft。

## 按主题接受的治理规则

用户于 2026-11-10 确认：基线允许按主题分批接受，无需等待整个模型 Accepted。本次归档主题为事实与政策，包含既有 R01—R19 和限定术语；聚合、候选不变量及协议继续各自评审。已接受条目可直接提供限定业务行为的测试期望，未定技术细节不得补造。详见 [逐条 INV 审查](reviews/2026-11-10-invariant-evidence-review.md)。本次未发布或创建 Git Commit/Tag。

## 发布与变更规则

基线更新必须遵循根目录 [AGENTS.md](../../AGENTS.md) 中的 `Baseline Change Protocol`。获得明确确认后，才可将具体领域文档或 ADR 纳入本清单；正式发布时记录对应 Git Commit，并仅在获得授权后创建 Git Tag。

## 阶段 3 候选评审登记

[阶段 3 决策包](reviews/2026-11-09-stage-3-architecture-review.md) 已整理责任 A/B/C、三套聚合图、B01—B12 与八项裁定，并记录 D-001～D-018 裁定。领域模型见 [首版基线候选](domain/model-baseline-candidate-v1.md)；ADR-0001～0003 已 Accepted，ADR-0004 仍 Proposed。发布仍 Unreleased。
