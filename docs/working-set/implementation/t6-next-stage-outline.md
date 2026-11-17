# T6 收尾与后续

**状态：T6 收尾（2026-11-17）**。已完成切片见[验收索引](README.md)，实施约束见 [AGENTS.md](../../../AGENTS.md) 与 [BASELINE](../BASELINE.md)。

## 1. 状态

| 切片 | 状态 |
| --- | --- |
| T6-B 并发与恢复 | 完成（[验收](t6-b-merge-recovery-verification.md)） |
| T6-C 身份分配 | 完成（[验收](t6-c-identity-verification.md)） |
| T6-D 子工作集回并 | 完成（[验收](t6-d-merge-back-verification.md)） |
| T6-E 瞬时 compare + 一页决策 | 完成（[验收](t6-e-merge-round-verification.md)） |
| T6-F 谱系约束 | 裁定并实施 `is_default`/多 Tracker/名称唯一/产品空间默认 Tracker（[裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md)）；`Difference.key` 已废弃，条目身份由 `Entry.id` + evidence 承担；**剩余**：`QUESTION-002.j` 配对、Shared 默认边界；i（批量派生整批原子）实现转 T7-G |
| T6-G 关系、Baseline 与治理 | 未开始；待 QUESTION-011／014 裁定 |
| 规模/保留 | 转[规模压测与保留专题](performance-testing.md)，不阻塞 |

## 2. Merge 轮次边界（保留的最小约定）

- compare 只读瞬时，一页 20–50 条；`POST /worksets/{target}/merge-rounds` 是唯一提交入口，`decisions` 1–50 条、`requestId` 必填幂等；空决策/超限 400。
- `GET /merge-rounds/{id}` 完整返回，不分页；`apply` 是幂等触发器。
- 同一 Tracker 对禁止并发合并（`PairMergeInProgress`）；Checkpoint 身份 = 两侧 Tracker 分支 + 家族 + 双侧版次，WorkingSet 不参与。

## 3. 转出项

- **i 批量派生整批原子**：规则见 T6-F 裁定，实现归 **T7-G 扩展工作集 LRO**。
- **`/api/worksets/{ws}/derive` 公开入口**：与“Item 只经合并传递”冲突，待 T7-G 就位后删除；内部派生能力保留给创建/扩展任务。
- **规模、保留、清理**：[规模压测与保留专题](performance-testing.md)。
- **Baseline 与版本治理**：T6-G / QUESTION-011、014。

## 4. 关联

- [T6-F 裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md)
- [Checkpoint 对齐裁定](../reviews/2026-11-16-merge-checkpoint-legacy-alignment.md)
- [T7 后续计划](t7-implementation-prompt.md)
