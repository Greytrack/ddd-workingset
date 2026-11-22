# Merge Checkpoint 重构实施记录（2026-11-16）

**状态：Historical（已实施）**。设计裁定见 [Checkpoint 对齐裁定](../reviews/2026-11-16-merge-checkpoint-legacy-alignment.md)
，现行实现见[合并执行契约 §6](../../05-contracts/t1-merge-execution.md#6-合并确认与跨轮次查询)，下文只记录当期实现。

> **表名提示**：下文 `merge_checkpoints` 是当时 V4 初版设计提出的物理表名，**从未成为当前表**。当前实际存储是 V3 旧表
> `air_ws_items_merge_index`，`MergeCheckpoint` 只是 V4 领域模型，由仓储适配到该表；列不足时走增量 DDL，不新建第二张表。

## 实施步骤

1. Checkpoint 身份重建为 Tracker 分支对（`tracker_a_id`/`tracker_b_id` + `round_id` + `change_kind` +
   `merge_direction` + `revoked` + 审计列），唯一键 `(tracker_a_id, tracker_b_id, common_id)`；
2. `merge_rounds` 增加 `tracker_a_id`/`tracker_b_id` 与并发闸门索引；
3. `MergeCheckpoint`、`MergeRepository`、`JdbcMergeRepository`、`MergeComparisonService`、`MergeExecutionService`、
   `MergeController`、`MergeFailure` 同步；
4. `MergeCheckpointCleanupJob` 定时清理被超越锚点（`workingset.merge.checkpoint-cleanup-cron`，默认每天 03:00）；
   **2026-11-20 后该清理任务已移除，确认由轮次保存、跨轮次改为只读查询，见 ADR-0006**；
5. 前端 `core.js` 补 `PairMergeInProgress` 文案。

## 验收

- 全量测试 165 项通过；
- 新增用例：反向命中、同 Tracker 对并发 409、终态后可再提交、跨 Tracker 对 400、被超越锚点清理；
- 开发库按 `schema.sql` 重建，承载表 `air_ws_items_merge_index` 无 `source_id`/`target_id`。

## 旧数据处置记录（2026-11-17）

当时移除 `Difference.key` / `entryKey` 后，旧轮次 JSON 未迁移，验收使用重建后的开发数据。数值 ID 版本的库初始化与 Demo
重建统一见 [仓库 README](../../../../README.md)。

## 风险（已接受）

- `merge_rounds` 部分唯一索引依赖 `state->>'state'`；状态由服务端单一入口维护。
- 一页决策跨 Tracker 对 → 400；UI 按 Tracker 比较。
