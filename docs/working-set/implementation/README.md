# 实施与验收索引

当前设计以 [BASELINE](../BASELINE.md) 为准；本页只记录实现状态、验收证据和下一步。代码现状与差距统一见 [当前实现边界](current-boundaries.md)。

## 当前状态

| 主题 | 当前结论 | 入口 |
| --- | --- | --- |
| 运行基线 | Maven 多模块；BIGINT 身份；`air_inst_common` 是公共查询视图 | [当前实现边界](current-boundaries.md) |
| T6 Merge | 直属父子、瞬时 compare、整页 Merge LRO、回并、轮次恢复已关闭 | [T6 验收](#当前验收) |
| T7 创建/扩展 | 按 Tracker 基线时间尽力派生；`baselineAtByTracker` 缺省 HEAD；重放复用原任务和基线 | [T7 契约](../domain/working-set-creation-lro-design.md)、[ADR-0010](../adr/0010-working-set-creation-tracker-baseline-time.md) |
| 公共查询视图 | SK 只写 LightTrackerItem 索引；业务 BC 消费 `TrackerItemsIndexedEvent` 投影公共视图，当前 ALM 为空监听器 | [T7 §4.6](../domain/working-set-creation-lro-design.md) |
| T5 | 历史切片证据；不再作为当前设计入口 | [历史验收](#历史验收) |

## 当前验收

| 范围 | 记录 |
| --- | --- |
| T7 创建/扩展 | [A 契约](../reviews/2026-11-13-t7-a-creation-lro-review.md)、[B 任务骨架](t7-b-creation-task-verification.md)、[C 范围绑定](t7-c-binding-verification.md)、[E 关系事件](../reviews/2026-11-17-t7-e-relation-guards-decision.md)、[G 扩展](t7-g-extension-verification.md) |
| T6 Merge/身份 | [B 并发恢复](t6-b-merge-recovery-verification.md)、[C 身份](t6-c-identity-verification.md)、[D 回并](t6-d-merge-back-verification.md)、[E 轮次](t6-e-merge-round-verification.md)、[F 谱系](../reviews/2026-11-16-t6-f-lineage-constraints.md) |
| 运行时 | [T5 运行时验证](t5-runtime-verification.md)（历史执行时点，不证明当前工作区） |
| 规模/保留 | [性能测试](performance-testing.md) |

## 历史验收

T5 的 S1～S7、D2/D4、S0 设计与索引旧方案保留证据，但已被后续契约和 ADR 替代：

- [T5 S0 设计](t5-s0-minimal-slice-design.md)、[场景映射](t5-s0-scenario-test-mapping.md)、[S0 评审](../reviews/2026-11-11-t5-s0-design-review.md)
- [S1](t5-s1-verification-result.md) · [S2](t5-s2-verification-result.md) · [S3](t5-s3-verification-result.md) · [S4](t5-s4-verification-result.md) · [S5](t5-s5-verification-result.md) · [S6](t5-s6-verification-result.md) · [S7](t5-s7-verification-result.md)
- [D2 家族作用域](t5-d2-family-scope-verification.md)、[D4 SK 边界](t5-d4-sk-boundary-verification.md)
- [T7-D 批量索引旧验收](t7-d-index-verification.md)：**Superseded by ADR-0010**

## 未验证与后续

- 数据库/Redis 集成回归需在专用空库执行；当前工作区未执行全量。
- T7-F 规模、失败明细保留和关系消费仍待专题验证。
- 权限接入、SK 契约版本治理仍是开放问题，见 [开放问题](../domain/open-questions.md)。
