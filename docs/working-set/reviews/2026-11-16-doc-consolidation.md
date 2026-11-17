# 2026-11-16 文档收敛与降噪记录

**状态：Reviewed（文档整理，不改变业务语义；用户要求执行）**

## 范围

对 `docs/working-set/` 做一次去重、去历史套话和状态修正，保留有实质价值的证据、反例、备选方案与历史裁定。未删除 `pre-design/` 历史材料；新增 [pre-design/README.md](../../../pre-design/README.md) 标明其 Historical 定位。

## 主要改动

| 文档 | 改动 |
| --- | --- |
| [open-questions.md](../domain/open-questions.md) | 248→70 行：只保留当前开放问题；D-010～D-019 的历史论证移回阶段 3 评审链接；新增 `QUESTION-002.j/k/l`、`QUESTION-008.b/c`、`QUESTION-017/018`；已关闭问题压缩为索引 |
| [glossary.md](../domain/glossary.md) | 去重 D-008～D-018 的逐段重复；保留现行术语、身份规则、COW 语义、历史术语表与示例对话 |
| [facts.md](../domain/facts.md) | 压缩逐 D 历史评论；保留 FACT/POL/HYP/TC 与当前政策；更新已关闭问题状态 |
| [invariants.md](../domain/invariants.md) | 按主题合并为一张登记表；INV-036/049 标 Superseded；INV-056 标注需按 T6-F 重述 |
| [aggregate-model.md](../domain/aggregate-model.md) | 221→62 行：只保留当前聚合结论、一致性边界、未决项；历史 AP 比较移至 proposals |
| [shared-kernel-boundary.md](../domain/shared-kernel-boundary.md) | 移除已被 T4 关闭的候选责任模式与长表；保留已确认 BC 边界与 T4 K1″/K2″/K3″ 结论 |
| [merge-entry-lifecycle.md](../diagrams/merge-entry-lifecycle.md) | 移除 `ComparisonToken`、`registerEntries`、`Completed` 等已取消/不存在的交互，改为整页提交 + 异步执行 |
| [merge-checkpoint-legacy-alignment.md](2026-11-16-merge-checkpoint-legacy-alignment.md) | 目标表结构 DDL 只保留在 [重构方案](../implementation/merge-checkpoint-rework-plan.md)，裁定文档不再复制第二份 |
| proposals / diagrams | `aggregate-boundary-options`、`aggregate-candidates`、`diff-merge-knowledge-extraction` 标 Historical；失效候选不再呈现为当前方案 |
| 其他 | 修正 T6-F“方向+家族”Checkpoint 旧说法、reviews README“工作集对”笔误、T5-S7 旧接口未标废弃、T6-F 名称唯一作用域矛盾、T4“只读”与 `BusinessObject` 的表述冲突 |

## 未改变

- 未改变任何已接受业务规则、ADR 状态、契约裁定或测试期望。
- 未删除历史评审、反例、被替代方案；未删除 `pre-design/`。
- 当时未实现 T6-F/T7-E；T6-F 的多 Tracker/默认/名称唯一已于 2026-11-17 实现，配对、复合条目身份、Shared 默认语义与 T7-E 仍在[开放问题](../domain/open-questions.md)与[实施索引](../implementation/README.md)中登记。

## 代码清理

- `JdbcBindingRepositories`：去除重复 `requireReady` 与重复 Tracker 行映射。
- `WorkingSetApplicationService.read`：授权拒绝改为 `ApplicationFailure(DENIED)`，不再抛无映射的 `UnsupportedOperationException`。
- `JdbcMergeStore.recordCheckpoint`：重新确认时刷新 `confirmed_at`。
- `MergeRoundSubmittedListener`：忽略已不存在轮次的提交事件，避免清理/删除后无限重投。

## 验证

- 当时 `mvn -B -q test`：113 项通过，0 失败、0 错误、0 跳过；Merge 异步监听器不再因测试清理产生 `RoundNotFound` 噪音。2026-11-17 T6-F 实施后全量为 122 项通过；错误码契约、默认 Tracker 边界、TrackerPair 四元组与直接 `sourceId` 配对测试补充后为 133 项。
- 文档本地链接检查：`docs/` 无断链；`pre-design/` 仍有已知旧相对链接（`TC-006`），不影响语义。
