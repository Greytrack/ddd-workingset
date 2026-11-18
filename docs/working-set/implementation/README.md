# 实施与验收索引

当前设计见 [BASELINE](../BASELINE.md)。本页索引验收证据、当前阶段和下一步。

## 当前阶段

| 主题 | 已完成 | 进行中/后续 |
| --- | --- | --- |
| T6 Merge 与身份 | 直属派生 Tracker 对、瞬时 Compare、整页 Merge LRO、回并、轮次恢复和谱系约束 | Merge 两项语义待裁定；业务 Baseline 与 SK 治理见 [T6 收尾](t6-next-stage-outline.md) |
| T7 创建与扩展 | 范围绑定、受理快照、批量 LTI、任务中心，以及 T7-E 工作集侧关系事件发布 | 真实关系 Adapter/消费服务接入；T7-F 规模、保留和恢复见 [后续计划](t7-implementation-prompt.md) |
| 当前运行版本 | Maven 多模块；业务 ID 使用 BIGINT，Requirement 同族共享只读编号；默认工作集支持创建 Tracker | 部署与 Demo 见 [README](../../../README.md)，身份/API 见 [T4 §4](../domain/cross-bc-contract-v1.md) |

2026-11-18 全量回归 `mvn -B test` 共 165 项通过。CQRS 查询侧实施见 [CQRS 记录](cqrs-query-side.md)。当前实现偏差和待验证项见 [开放问题](../domain/open-questions.md)。

## 验收记录

| 范围 | 验收记录 |
| --- | --- |
| T7 工作集异步创建/扩展 | [A 契约评审](../reviews/2026-11-13-t7-a-creation-lro-review.md)、[B 任务骨架](t7-b-creation-task-verification.md)、[C 范围绑定](t7-c-binding-verification.md)、[D 批量索引](t7-d-index-verification.md)、[E 关系事件](../reviews/2026-11-17-t7-e-relation-guards-decision.md)、[G 扩展工作集](t7-g-extension-verification.md) |
| T6 Merge 与身份 | [B 并发与恢复](t6-b-merge-recovery-verification.md)、[C 身份分配](t6-c-identity-verification.md)、[D 回并](t6-d-merge-back-verification.md)、[E 一页决策](t6-e-merge-round-verification.md)、[F 谱系](../reviews/2026-11-16-t6-f-lineage-constraints.md) |
| T5 历史验收（保留证据，不再作为当前入口） | [S1 查询](t5-s1-verification-result.md)、[S2 保存与事件](t5-s2-verification-result.md)、[S3 首次物化](t5-s3-verification-result.md)、[S4 恢复](t5-s4-verification-result.md)、[S5 绑定与删除](t5-s5-verification-result.md)、[S6 谱系](t5-s6-verification-result.md)、[S7 Merge](t5-s7-verification-result.md) |
| T5 历史支撑证据 | [运行时](t5-runtime-verification.md)、[D2 家族作用域](t5-d2-family-scope-verification.md)、[D4 SK 边界](t5-d4-sk-boundary-verification.md) |

跟踪分类创建和数字身份见 [Tracker 验收](../../../deploys/src/test/java/bootstrap/TrackerDefaultAcceptanceTest.java) 与 [Snowflake 存储验收](../../../deploys/src/test/java/bootstrap/SnowflakeStorageAcceptanceTest.java)。
