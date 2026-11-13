# T6-D 子工作集新建与回并验收

**状态：Reviewed**。用户裁定已登记于 [T1 §8](../domain/merge-execution-contract-v1.md#8-轮次终态与结果表达)，创建协作见 [T4 §4](../domain/cross-bc-contract-v1.md#4-六个用例的契约边界)。

[裁定评审](../reviews/2026-11-13-t6-d-merge-back-review.md)记录了方案权衡。

## 公开入口验收

| 场景 | 可观察结果 |
| --- | --- |
| 子工作集原生新建 → Compare → CREATE → Apply | 默认与非默认父工作集均创建同 CommonId、不同 LocalId 的变体，遵守目标身份规则。子侧身份及父子链保持原样。 |
| 父侧已有有效同族 | Compare 显示 PRESENT，登记 CREATE 被拒绝。 |
| 父侧原变体软删 | Compare 显示 ABSENT，独立 RESTORE 被拒绝。Apply 恢复原 LocalId 并更新内容，保留目标版次；重复 Apply 返回原结果。 |
| 共享 Tracker 从子工作集入口创建 | 实际归默认工作集，无回并差异。 |
| 两个已登记轮次竞争同族 | 一次业务写入成功，另一条目因目标出现返回 StaleEvidence；Apply 仍按 T1 返回逐条结果。同轮执行权冲突仍是 HTTP 409。 |
| 非默认父侧新建 | 新变体依 D15-B 通过事件纳入 LTI。事件失败后重投、业务提交后轮次恢复沿用 T6-B 验收。 |

实现证据见 [MergeAcceptanceTest](../../../src/test/java/bootstrap/MergeAcceptanceTest.java) 与 [MergeExecutionService](../../../src/main/java/sk/workingset/application/MergeExecutionService.java)。ALM 参考适配器在条目事务内锁定家族席位并返回实际身份。沿用已有数据库约束与 Merge 审计，无 schema 变更。

建模方法采用 Lattice `domain-driven-design`（`D:\coding\IdeaProjects\union\airedge\.agents\skills\domain-driven-design`），恢复与内容更新由目标业务聚合保证同一提交边界。

`mvn -B clean test` 使用 PostgreSQL、Redis 和内嵌 Tomcat，包含 ArchUnit。输出为 `Tests run: 91, Failures: 0, Errors: 0, Skipped: 0`。
