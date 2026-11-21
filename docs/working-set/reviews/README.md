# Reviews

**状态：Draft（目录用途；不接受任何具体评审结论）**

评审记录保留异议、证据和当时结论；“Reviewed”不等于“Accepted”，业务规则以 [BASELINE](../BASELINE.md) 为准。

## 当前决策与裁定

- [2026-11-15 Compare 不持久化](2026-11-15-compare-not-persisted-decision.md)：compare 只读瞬时；MergeRound 保存决策与所选证据。
- [2026-11-15 QUESTION-010 关系身份](2026-11-15-question-010-relation-identity-decision.md)：Reference 用逻辑 Family，Association 用具体 Variant。
- [2026-11-16 Merge Checkpoint 对齐](2026-11-16-merge-checkpoint-legacy-alignment.md)：Tracker 对 + 家族 + 双侧版次定位。
- [2026-11-16 T6-F 谱系约束](2026-11-16-t6-f-lineage-constraints.md)：Shared 悬挂、同槽多 Tracker、`sourceId` 不可变、派生链 1:1。
- [2026-11-17 T7-E 关系事件](2026-11-17-t7-e-relation-guards-decision.md)：SK 只发布关系请求，不操作关系聚合。
- [2026-11-18 移除 COW Recovery](2026-11-18-remove-cow-recovery.md)：失败即终态，不重投、不补切换。
- [2026-11-19 TC-002 反模式审查](2026-11-19-ddd-anti-pattern-scan.md)：单聚合写事务、跨聚合可靠事件。
- [2026-11-19 代码演进后的文档对齐](2026-11-19-doc-alignment-after-code-evolution.md)：模块边界、直接只读、事件写入。
- [2026-11-20 产品空间删除](2026-11-20-product-space-deletion.md)：存在绑定时拒绝删除。
- [2026-11-21 Merge 分发 BC 边界](2026-11-21-merge-dispatch-bc-boundary.md)：批准时冻结值分发。

## 当前 ADR 依据

ADR 列表见 [ADR README](../adr/README.md) 与 [BASELINE](../BASELINE.md)。ADR 的状态独立于本目录评审记录。

## 历史评审

以下记录保留阶段过程、旧实现和已关闭裁定，不作为当前实现依据：

- 阶段 2/3：[领域发现](2026-11-09-stage-2-domain-discovery-review.md)、[Shared Kernel 边界](2026-11-09-stage-3-0-shared-kernel-boundary-review.md)、[正式架构](2026-11-09-stage-3-architecture-review.md)、[规则收敛](2026-11-09-stage-3-1-rule-convergence.md)
- 阶段 4/5：[阶段 4 评审](2026-11-10-stage-4-productspace-classification-review.md)、[基线与不变量审核](2026-11-10-baseline-v1-audit.md)、[不变量证据](2026-11-10-invariant-evidence-review.md)、[契约计划](2026-11-10-stage-5-contract-plan.md)
- T1～T4：[T1](2026-11-11-t1-contract-review.md) · [T2](2026-11-11-t2-binding-deletion-review.md) · [T3](2026-11-11-t3-materialization-review.md) · [T4](2026-11-11-t4-cross-bc-review.md) · [一致性修正](2026-11-11-consistency-reconciliation.md)
- T5：[S0 审核](2026-11-11-t5-s0-design-review.md)、[S7 实施裁定](2026-11-12-s7-merge-implementation-decisions.md)、[谱系评审](2026-11-12-working-set-lineage-review.md)
- T6/T7 过程：[T6-D 回并](2026-11-13-t6-d-merge-back-review.md)、[T7-A LRO](2026-11-13-t7-a-creation-lro-review.md)、[文档收敛](2026-11-16-doc-consolidation.md)、[仓库降噪](2026-11-18-cleanup-and-alignment.md)

新评审从 [REVIEW-TEMPLATE.md](REVIEW-TEMPLATE.md) 开始。
