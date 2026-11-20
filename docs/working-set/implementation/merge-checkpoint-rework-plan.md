# Merge Checkpoint 重构实施记录（2026-11-16）

**状态：Historical（已实施）**。设计裁定见 [Checkpoint 对齐裁定](../reviews/2026-11-16-merge-checkpoint-legacy-alignment.md)，现行实现见[合并执行契约 §6](../domain/merge-execution-contract-v1.md#6-mergecheckpoint-主要领域行为与不变量)，下文只记录当期实现。

## 实施步骤

1. `merge_checkpoints` 重建为 Tracker 分支对身份（`tracker_a_id`/`tracker_b_id` + `round_id` + `change_kind` + `merge_direction` + `revoked` + 审计列），唯一键 `(tracker_a_id, tracker_b_id, common_id)`；
2. `merge_rounds` 增加 `tracker_a_id`/`tracker_b_id` 与并发闸门索引；
3. `MergeCheckpoint`、`MergeRepository`、`JdbcMergeRepository`、`MergeComparisonService`、`MergeExecutionService`、`MergeController`、`MergeFailure` 同步；
4. `MergeCheckpointCleanupJob` 定时清理被超越锚点（`workingset.merge.checkpoint-cleanup-cron`，默认每天 03:00）；
5. 前端 `core.js` 补 `PairMergeInProgress` 文案。

## 验收

- 全量测试 165 项通过；
- 新增用例：反向命中、同 Tracker 对并发 409、终态后可再提交、跨 Tracker 对 400、被超越锚点清理；
- 开发库按 `schema.sql` 重建，`merge_checkpoints` 无 `source_id`/`target_id`。

## 旧数据处置记录（2026-11-17）

当时移除 `Difference.key` / `entryKey` 后，旧轮次 JSON 未迁移，验收使用重建后的开发数据。数值 ID 版本的库初始化与 Demo 重建统一见 [仓库 README](../../../README.md)。

## 风险（已接受）

- `merge_rounds` 部分唯一索引依赖 `state->>'state'`；状态由服务端单一入口维护。
- 一页决策跨 Tracker 对 → 400；UI 按 Tracker 比较。
