# Merge Checkpoint 重构实施方案（2026-11-16）

**目标**：把 `merge_checkpoints` 的身份从“两侧 WorkingSet”改为“**两侧 Tracker 分支 + 家族**”，与“合并以 Tracker 为单位”的模型一致；表名保持 `merge_checkpoints`。

**依据**：[Checkpoint 身份修正裁定](../reviews/2026-11-16-merge-checkpoint-legacy-alignment.md)、[Merge 契约 §5/§6](../domain/merge-execution-contract-v1.md)、[聚合模型](../domain/aggregate-model.md)。

## 1. 目标结构

### 1.1 `merge_checkpoints`

```sql
CREATE TABLE IF NOT EXISTS merge_checkpoints (
    checkpoint_id    BIGSERIAL PRIMARY KEY,
    round_id         VARCHAR(64) NOT NULL,
    tracker_a_id     VARCHAR(64) NOT NULL,
    tracker_b_id     VARCHAR(64) NOT NULL,
    common_id        VARCHAR(64) NOT NULL,
    local_id_a       VARCHAR(64),
    local_id_b       VARCHAR(64),
    revision_a       INTEGER,
    revision_b       INTEGER,
    change_kind      VARCHAR(16),
    merge_direction  VARCHAR(8)  NOT NULL,
    decision_version BIGINT      NOT NULL,
    revoked          BOOLEAN     NOT NULL DEFAULT FALSE,
    remark           TEXT,
    operator         VARCHAR(64),
    confirmed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_merge_checkpoints_tracker_pair_family
    ON merge_checkpoints (tracker_a_id, tracker_b_id, common_id);
CREATE INDEX idx_merge_checkpoints_round  ON merge_checkpoints (round_id);
CREATE INDEX idx_merge_checkpoints_family ON merge_checkpoints (common_id);
```

- `tracker_a_id` / `tracker_b_id`：Tracker 分支对，按 `TrackerId` 字符串排序（A ≤ B）；
- `round_id`：确认时的 `MergeRound.id`；
- `merge_direction`：`A_TO_B` / `B_TO_A`，属性而非身份；
- 命中键：`(tracker_a_id, tracker_b_id, common_id, revision_a, revision_b)`，且 `revoked = false`；
- 不比较 `local_id`（同 CommonId 重建不会发生）。

### 1.2 `merge_rounds` 增量

```sql
ALTER TABLE merge_rounds ADD COLUMN IF NOT EXISTS tracker_a_id VARCHAR(64);
ALTER TABLE merge_rounds ADD COLUMN IF NOT EXISTS tracker_b_id VARCHAR(64);

CREATE UNIQUE INDEX uq_merge_rounds_active_tracker_pair
    ON merge_rounds (tracker_a_id, tracker_b_id)
    WHERE (state->>'state') IN ('Preparing', 'Running');
```

- 一页决策必须同属一个 Tracker 对；轮次记录该对，作为并发闸门键；
- 终态后索引条目消失，下一轮可提交。

## 2. 代码改动清单

| 文件 | 改动 |
| --- | --- |
| `domain/merge/MergeCheckpoint.java` | `Key(TrackerId a, TrackerId b, CommonId family)`，紧凑构造器内按 TrackerId 排序；字段改为 `roundId / trackerA / trackerB / localIdA / localIdB / revisionA / revisionB / changeKind / direction / decisionVersion / revoked`；新增 `Direction` 枚举 |
| `domain/merge/MergeFailure.java` | `Reason` 新增 `PairMergeInProgress` |
| `application/port/MergeStore.java` | `recordCheckpoint`、`findCheckpoint(Key)`、`revokeCheckpoint(Key, …)`；新增 `hasActiveRoundForTrackerPair(TrackerId a, TrackerId b)`；`insertOrFindRound(round, trackerA, trackerB)` |
| `infrastructure/JdbcMergeStore.java` | 新列读写；upsert `ON CONFLICT (tracker_a_id, tracker_b_id, common_id) DO UPDATE … WHERE decision_version < EXCLUDED.decision_version`；撤销按 Tracker 对+家族；活跃轮次查询；插入轮次写 `tracker_a_id/b` 并区分 `request_id` 冲突与 Tracker 对冲突 |
| `application/MergeComparisonService.java` | `handled` 用 `difference.trackers()` 规范化 A/B + `common_id` + 双侧版次 |
| `application/MergeExecutionService.java` | `confirm` 用 `entry.evidence().trackers()` 构造 Key，推导方向/`change_kind`/`round_id`/`operator`；`revokeCheckpoint` 用 Tracker 对；`submit` 校验同一 Tracker 对 + 预检活跃轮次 |
| `bootstrap/web/MergeController.java` | 撤销端点改为 `/api/merge-checkpoints/{trackerA}/{trackerB}/{family}/revoke` |
| `bootstrap/web/ApiExceptionHandler.java` | `PairMergeInProgress` → 409 |
| `static/js/core.js` | `REASON_TEXT` 增加 `PairMergeInProgress` |
| `schema.sql` | 见 §1；开发库需一次性 `DROP TABLE merge_checkpoints` 后重跑 |
| `MergeAcceptanceTest` | `MergeCheckpoint.Key` 改 TrackerId；撤销路径改 Tracker；新增并发闸门与反向命中用例 |

## 3. 数据迁移

- 开发基线：`DROP TABLE merge_checkpoints` 后由 `schema.sql` 重建；不迁移旧数据。
- `merge_rounds` 用 `ADD COLUMN IF NOT EXISTS`，旧轮次 `tracker_a_id/b` 为空，不参与新闸门。

## 4. 测试清单

| 用例 | 断言 |
| --- | --- |
| `checkpointHitIsDirectionAgnostic` | A→B 确认后，B→A 的 compare 不再出现该家族 |
| `revokeIsDirectionAgnostic` | 撤销后两个方向都重新出现差异 |
| `concurrentPairSubmissionIsRejected` | 同 Tracker 对第二个提交 → 409 `PairMergeInProgress` |
| `pairCanMergeAgainAfterTerminal` | 上一轮终态后可提交同对的新轮次 |
| `mixedTrackerPairsAreRejected` | 一页决策跨两个 Tracker 对 → 400 |
| `checkpointPersistsTrackerPair` | 落库 `tracker_a_id/b`、`round_id`、`change_kind`、`merge_direction` 正确 |
| 回归 | `MergeAcceptanceTest` 现有用例、T5-S5 删除判定、T6-F 的默认 Tracker 改动 |

## 5. 验收标准

- `mvn -B -Dspring.profiles.active=default test` 全绿；
- `schema.sql` 在空库可执行；开发库重建后 `merge_checkpoints` 无 `source_id/target_id`；
- demo profile 实跑：A→B 确认后 B→A compare 0 差异；同对并发提交 409；撤销后双向重现。

## 6. 风险

| 风险 | 处理 |
| --- | --- |
| `merge_rounds` 部分唯一索引依赖 `state->>'state'` | 状态由服务端维护；写入/更新路径保持单一入口 |
| 一页决策跨 Tracker 对 | `submit` 显式拒绝（400），UI 按 Tracker 比较 |
| 旧 Checkpoint 数据 | 开发基线直接重建，不迁移 |
| TrackerId 排序 | 统一用字符串比较；索引与 Java 规范化一致 |

## 7. 实施状态（2026-11-16）

已完成：

- `merge_checkpoints` 重建为 Tracker 分支对身份；`merge_rounds` 增加 `tracker_a_id`/`tracker_b_id` 与并发闸门索引；
- 代码：`MergeCheckpoint`、`MergeStore`、`JdbcMergeStore`、`MergeComparisonService`、`MergeExecutionService`、`MergeController`、`MergeFailure`、`core.js`；
- 定时清理 `MergeCheckpointCleanupJob`（`@Scheduled`，默认每天 03:00，可用 `workingset.merge.checkpoint-cleanup-cron` 配置），清理两侧版次都已被超越的未撤销锚点；
- 新增验收用例：反向命中、同 Tracker 对并发 409、终态后可再提交、跨 Tracker 对 400、被超越锚点清理；
- 全量测试 118 项通过。

T6-F e（2026-11-16 已完成）：

- `trackers.is_default` + 部分唯一索引：每个（工作集，空间，分类）恰好一个默认 Tracker；
- 名称唯一：同一（工作集，空间，分类）内业务原生 Tracker 名称唯一，派生 Tracker 继承来源名称不参与；
- 去掉 `tracker_bindings` 的 `(工作集, 空间, 分类)` 三元唯一键，同槽位允许多个 Tracker；
- `POST /api/worksets/1/product-spaces`：创建产品空间并为每个分类同步创建默认 Tracker；
- 派生时按“目标工作集是否已有默认”设置 `is_default`；
- 新增 `TrackerDefaultAcceptanceTest`（4 项）覆盖默认唯一、名称唯一、产品空间创建、绑定约束移除。
