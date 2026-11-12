# S7 Merge 实施裁定

**状态：Reviewed（2026-11-12；用户裁定 + S7 实施验证；不构成基线发布）**

## 比较证据与决策优先

用户选择以登记为分界。登记前，双侧内容证据变化返回 `StaleEvidence`。登记后，普通字段变化按决策优先处理，`ACCEPT` 读取源端最新值，目标 BC 基于当前聚合校验。身份、版次、存在性或可读性变化仍拒绝执行。该裁定衔接 [T1 §4、§7](../domain/merge-execution-contract-v1.md)、[R08/R09](../domain/facts-policy-baseline-v1.md) 和 [SCN-057](../scenarios/core-scenarios.md#scn-057)。

## Requirement 生命周期

用户裁定 `delete` 只写 `delete_flag`，保留业务数据并从正常读取隐藏。 `undelete` 撤销该标记。 `delete2` 从业务存储移除对象，删除后无法撤销。Merge 的显式 `DELETE` 与 `RESTORE` 分别使用 `delete` 和 `undelete`。其他生命周期限制待细化。已有来源依赖继续遵循 [FACT-006](../domain/facts.md) 的可读性约束。

入口、证据和实施边界见 [S7 验证结果](../implementation/t5-s7-verification-result.md)。

## 开放问题状态（D9，2026-11-12）

用户裁定关闭 [QUESTION-008](../domain/open-questions.md) 的**已实现部分**：登记前/后证据分界、普通字段决策优先、部分成功、新轮次与 Checkpoint 由 S7 兑现。剩余“执行中并发、重试与结果恢复协议”拆为 `QUESTION-008.a` 继续跟踪，不重开已关闭部分。
