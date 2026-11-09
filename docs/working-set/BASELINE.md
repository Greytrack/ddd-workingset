# Working Set Design Baseline

- **Status**: Draft
- **Baseline Version**: Unreleased
- **Released Git Commit**: None（仓库已有 Git 提交，但尚无正式发布的设计基线提交）
- **Released Git Tag**: None
- **Last Updated**: 2026-11-09

## 当前基线状态

当前无正式发布的 Accepted 设计基线。

本文件目前只建立设计基线的清单和版本入口，不批准任何 Working Set 领域结论。阶段 1 的目录初始化、治理规则、模板和本文件本身均不等于领域设计获批。

## 当前已确认领域文档

无。

[domain/](domain/README.md) 保留 Draft 候选模型。阶段 3 已有用户逐项确认的场景规则及版次定义，确认范围与依据见 [裁定进度](reviews/2026-11-09-stage-3-architecture-review.md#裁定进度)。这些逐项记录等待汇总评审和基线纳入，正式发布状态为 Unreleased。

## 已确认的组织架构约束

- `AC-001`：Working Set 属于企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。来源为用户于 2026-11-09 对既定企业架构的明确确认，详见 [Shared Kernel 边界](domain/shared-kernel-boundary.md) 和 [Context Map](diagrams/shared-kernel-context-map.md)。

`AC-001` 缩小了设计方案空间，但不接受任何 Working Set 内部聚合、限界上下文、API、事务或部署方案；正式 Accepted 领域设计基线仍为空。

## 当前有效 ADR

无。

[adr/](adr/README.md) 当前只有状态为 `Draft` 的目录用途说明和 [ADR-TEMPLATE.md](adr/ADR-TEMPLATE.md)，不存在状态为 `Accepted` 且未被 `Superseded` 的实际 ADR。

## 相关验收标准

无。

[scenarios/](scenarios/README.md) 已有阶段 2 的 `Draft` 候选场景，但尚未成为已确认验收标准；[implementation/](implementation/README.md) 仍只有目录用途说明，也没有测试证据。

## 已整理但未确认的候选知识

阶段 2 已形成以下 `Draft` 文档，供后续业务确认和正式架构评审使用；它们不属于 Accepted 设计基线：

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

以下主题在根目录 [AGENTS.md](../../AGENTS.md) 和 [`pre-design/`](../../pre-design/) 中存在候选分析，但尚未整理为正式基线：

- Working Set、Tracker、ProductSpace、业务对象及其关系的职责和边界；
- SHARED 与 UNSHARED Tracker 的准确业务语义、权限和一致性承诺；
- Item Family／CommonId、Item Variant／LocalId、业务 revision、并发 version 与事件顺序的身份和版本语义；
- 派生、历史读取、删除、引用修复、字段级比较与合并的正式规则；
- 对应业务场景、反例、验收标准及可追溯测试 seam。

这些条目是待确认范围，不是已认可事实，也不表示其优先级已经正式决定。

## 最近基线变更

- **2026-11-08**：创建基线入口，记录仓库尚无正式发布的 Accepted 设计基线；未新增或接受任何领域模型、ADR 或验收标准。
- **2026-11-09**：登记阶段 2 候选知识与审查材料；所有新增内容保持 `Draft / Reported / Candidate`，正式 Accepted 设计基线仍为空。
- **2026-11-09**：将 Mermaid 图表纳入设计治理，登记候选概念关系图与候选 Context Map；两图均为 `Draft / Candidate`，未进入正式基线。
- **2026-11-09**：记录 `AC-001` Confirmed 组织架构约束，新增 Shared Kernel 与四个业务 BC 外框；内部领域模型和跨 BC 契约继续保持 Draft。

## 发布与变更规则

基线更新必须遵循根目录 [AGENTS.md](../../AGENTS.md) 中的 `Baseline Change Protocol`。获得明确确认后，才可将具体领域文档或 ADR 纳入本清单；正式发布时记录对应 Git Commit，并仅在获得授权后创建 Git Tag。

## 阶段 3 候选评审登记

[阶段 3 决策包](reviews/2026-11-09-stage-3-architecture-review.md) 已整理责任 A/B/C、三套聚合图、B01—B12 与八项裁定。均为 Draft / Candidate，不属于 Accepted 基线。状态继续 Draft / Unreleased；无实际 ADR、已接受模型或正式验收标准。
