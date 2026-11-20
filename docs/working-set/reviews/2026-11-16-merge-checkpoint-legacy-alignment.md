# Merge Checkpoint 身份修正与旧表参考（2026-11-16）

**状态：Accepted（用户 2026-11-16 裁定）**。范围：`merge_checkpoints` 的身份键、方向语义、命中规则、并发合并闸门。参考旧设计 `air_ws_items_merge_index`。**历史取舍提示**：此处以下内容保留原裁定；物理表、决定版本与用户撤销入口已由[现行契约 §6](../domain/merge-execution-contract-v1.md#6-合并确认与跨轮次查询)替换。

## 1. 旧设计的定位（澄清）

`air_ws_items_merge_index` **不是合并明细**，而是“下一次 diff 的参考坐标系”：

- 一条记录 = 一次**接收**（`merge_action` 恒为 `ACCEPT`、`merge_status` 恒为 `MERGED`），对应 `markAsMerged`；
- 命中的 Item 对代表“已接收、无差异”，compare 不再下发；
- 未命中才进入逐字段比较与决策。

它的 `branch_a_id` / `branch_b_id` 是**Tracker 分支 ID**（派生分支），不是 Git branch，也不是 WorkingSet。

## 2. 用户裁定

| # | 裁定 |
| --- | --- |
| 1 | **WorkingSet 的 ID 不进 Checkpoint**：合并以 Tracker 为单位，身份用两侧 **Tracker 分支** + 家族 |
| 2 | **方向不进唯一键**：A→B 与 B→A 的结果一致，方向只是属性 |
| 3 | **同一 Tracker 对禁止并发合并**：存在未终态轮次时，下一对必须等终态（成功或失败）并抛异常 |
| 4 | **同 CommonId 重建不会发生**：只能沿派生链经合并传递，命中规则不需要 `local_id` 防御 |
| 5 | **只参考、不全盘复刻**：旧列按 V4 领域语言重命名；旧 `batch_id` 的“派生组”在 V4 无对应概念，只有 `MergeRound` |

## 3. 方案

### 3.1 命名取舍

| 旧列 | V4 列 | 取舍理由 |
| --- | --- | --- |
| `id` | `checkpoint_id` | 表内主键按表名命名 |
| `batch_id` | `round_id` | V4 无“派生组”，只有 `MergeRound`；语义改为轮次 ID |
| `branch_a_id` / `branch_b_id` | `tracker_a_id` / `tracker_b_id` | 它们是 **Tracker 分支**，不是工作集；A/B 只表达规范化 |
| `merge_action`（恒 `ACCEPT`） | 删除 | 常量列；语义由“每行即一条已接收锚点”承载 |
| `merge_status`（恒 `MERGED`） | 删除 | 常量列；撤销用 `revoked` 表达 |
| `diff_type` | `change_kind` | 对齐 `Comparison.ChangeKind` |
| `merge_direction` | `merge_direction` | 保留：方向是属性，不是身份 |
| `merge_time` | `confirmed_at` | 语义是“确认时间” |
| `lock_version` | 删除 | `decision_version` 已覆盖晚到与并发保护 |
| `created_*` / `updated_*` | `operator` / `confirmed_at` / `updated_at` | 简化审计 |
| `local_id_a/b`、`revision_a/b`、`common_id` | 保留 | A/B 侧证据与命中锚点 |

### 3.2 唯一键与命中

- 唯一键：`(tracker_a_id, tracker_b_id, common_id)`，A ≤ B（`TrackerId` 字符串排序）；
- 命中：`(tracker_a_id, tracker_b_id, common_id, revision_a, revision_b)` 且 `revoked = false`；
- 不比 `local_id`（依据裁定 4），`local_id_a/b` 仅作证据；
- 方向无关：A→B 确认后，B→A 的 compare 命中同一行。

### 3.3 并发合并闸门

- **一页决策必须同属一个 Tracker 对**：提交时校验 `decisions` 的 `evidence.trackers()` 全部相同，否则 400；
- `merge_rounds` 增加轮次级 `tracker_a_id` / `tracker_b_id`，并建部分唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uq_merge_rounds_active_tracker_pair
    ON merge_rounds (tracker_a_id, tracker_b_id)
    WHERE (state->>'state') IN ('Preparing', 'Running');
```

- 应用层先预检，命中返回 `PairMergeInProgress`（409）；数据库索引兜住并发窗口；
- 轮次终态后索引条目消失，下一轮立即可提交；
- 插入路径区分 `request_id` 冲突（幂等返回原轮次）与 Tracker 对冲突（`PairMergeInProgress`）。

## 4. 影响面

| 层 | 改动 |
| --- | --- |
| schema | 重建 `merge_checkpoints`（`tracker_a_id`/`tracker_b_id` + `round_id` + `change_kind` + `merge_direction` + `revoked` + 审计列）；`merge_rounds` 增加 `tracker_a_id`/`tracker_b_id` 与并发闸门索引 |
| `MergeCheckpoint` | `Key(TrackerId a, TrackerId b, CommonId family)`，构造时规范化；`mergeDirection` 作为属性 |
| `JdbcMergeStore` | 按新列读写；upsert 冲突目标 `(tracker_a_id, tracker_b_id, common_id)`；`revokeCheckpoint` 按 Tracker 对+家族；新增未终态轮次查询 |
| `MergeComparisonService.handled` | 命中比较改为 Tracker 对 + `common_id` + `revision_a/b` |
| `MergeExecutionService` | `confirm` 写方向/`change_kind`/审计；`submit` 校验同一 Tracker 对并预检未终态轮次 |
| `MergeFailure.Reason` | 新增 `PairMergeInProgress`，映射 409 |
| API | `POST /merge-rounds` 新增 400（跨 Tracker 对）与 409（同对正在合并）；`revoke` 路径改为两侧 Tracker |
| 前端 | `core.js` 的 `REASON_TEXT` 增加 `PairMergeInProgress` 文案 |
| 测试 | 反向命中、方向无关撤销、同对并发提交被拒、终态后可再提交、字段落库断言；回归 Merge 20 项 |

## 5. 已确认（2026-11-16）

1. **并发提示**：新增 `PairMergeInProgress`（409）。
2. **撤销方向无关**：撤销后两个方向都重新视为未处理。
3. **旧数据**：开发基线直接重构，不迁移既有 Checkpoint。

## 6. 目标表结构（`merge_checkpoints`）

目标列：`checkpoint_id`（代理主键）、`round_id`（确认时的 MergeRound）、`tracker_a_id`/`tracker_b_id`（规范化 Tracker 对，A≤B）、`common_id`（家族）、`local_id_a/b` 与 `revision_a/b`（双侧证据，`revision` 为命中锚点）、`change_kind`、`merge_direction`（属性）、`decision_version`（晚到保护）、`revoked`、`operator`/`remark`、`confirmed_at`/`updated_at`。

唯一键 `(tracker_a_id, tracker_b_id, common_id)`；命中条件为唯一键 + `revision_a`/`revision_b` 且 `revoked = false`；`local_id` 只作证据，不参与命中。

现行 DDL 与索引见 [schema.sql](../../../deploys/src/main/resources/schema.sql)，当时的实施与旧数据处置见 [重构记录](../implementation/merge-checkpoint-rework-plan.md)。

## 7. 关联

- [Merge 执行契约 v1 §5/§6](../domain/merge-execution-contract-v1.md)、[聚合模型](../domain/aggregate-model.md)、[ADR-0005](../adr/0005-merge-execution-model.md)
- [T6-E 验收](../implementation/t6-e-merge-round-verification.md)、[T6-F 裁定](2026-11-16-t6-f-lineage-constraints.md)
