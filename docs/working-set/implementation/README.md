# 实施与验收索引

当前设计见 [BASELINE](../BASELINE.md)。本页索引验收证据、当前阶段和下一步。

## 当前阶段

| 主题 | 已完成 | 进行中/后续 |
| --- | --- | --- |
| T6 Merge 与身份 | T6-B 并发与恢复、T6-C 身份分配、T6-D 子工作集回并、T6-E 瞬时 compare + 一页决策 MergeRound 幂等与异步 LRO | T6-F 谱系约束、T6-G 关系/Baseline/治理；见 [T6 后续大纲](t6-next-stage-outline.md) |
| T7 创建工作集 | T7-A 契约、T7-B 任务骨架、T7-C 范围绑定、T7-D 批量索引（受理时冻结快照） | T7-E 关系构建（QUESTION-010 已裁定 C，待实现）、T7-F 规模/保留/恢复（依赖 QUESTION-012）；见 [T7 后续计划](t7-implementation-prompt.md) |

下一步建议：QUESTION-010 已裁定 C，下一步实现 T7-E 的 Reference/Association 构建与修复；规模、保留与恢复验证统一见[规模压测与保留专题](performance-testing.md)；T6-F 按 `QUESTION-002` 逐项裁定。T7-E 接入前继续保留 `TrackerItemsIndexed` 生产占位监听器。

## 验收记录

| 范围 | 验收记录 |
| --- | --- |
| T7 工作集异步创建 | [A 契约评审](../reviews/2026-11-13-t7-a-creation-lro-review.md)、[B 任务骨架](t7-b-creation-task-verification.md)、[C 范围绑定](t7-c-binding-verification.md)、[D 批量索引](t7-d-index-verification.md) |
| T6 Merge 与身份 | [B 并发与恢复](t6-b-merge-recovery-verification.md)、[C 身份分配](t6-c-identity-verification.md)、[D 回并](t6-d-merge-back-verification.md)、[E 一页决策](t6-e-merge-round-verification.md) |
| T5 首个纵向切片 | [S1 查询](t5-s1-verification-result.md)、[S2 保存与事件](t5-s2-verification-result.md)、[S3 首次物化](t5-s3-verification-result.md)、[S4 恢复](t5-s4-verification-result.md)、[S5 绑定与删除](t5-s5-verification-result.md)、[S6 谱系](t5-s6-verification-result.md)、[S7 Merge](t5-s7-verification-result.md) |
| T5 支撑证据 | [运行时](t5-runtime-verification.md)、[D2 家族作用域](t5-d2-family-scope-verification.md)、[D4 SK 边界](t5-d4-sk-boundary-verification.md) |

领域待裁定事项见[开放问题](../domain/open-questions.md)。已完成的 T6/T7 实施提示词及阶段 2 概念旧图已从当前索引移除，必要时从 Git 历史查阅。
