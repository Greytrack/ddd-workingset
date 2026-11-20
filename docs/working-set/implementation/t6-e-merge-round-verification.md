> 2026-11-18 用户裁定移除 COW Recovery；本文中依赖 /api/cow/recover 的验证记录保留为历史证据，不再代表当前实现。

# T6-E MergeRound 一页决策与幂等提交验收

**状态：Accepted（2026-11-16 收敛；2026-11-20 确认归属由 ADR-0006 调整）**。设计依据 [T1 契约 §3/§4](../domain/merge-execution-contract-v1.md) 与 [Compare 不持久化裁定](../reviews/2026-11-15-compare-not-persisted-decision.md)；规模与保留见[规模压测与保留专题](performance-testing.md)。

> **2026-11-20 补充**：确认记录归 MergeEntry，随轮次结果同事务保存；跨轮次 Checkpoint 改为只读查询模型，不再运行独立清理任务。本文下述接口、验证数量和“旧确认表”描述保留当时证据，当前规则见 [ADR-0006](../adr/0006-merge-confirmation-ownership.md) 与 [T1 §6](../domain/merge-execution-contract-v1.md#6-合并确认与跨轮次查询)。

## 范围

- 瞬时 compare 分页；一页用户决策形成一个 MergeRound；`requestId` 幂等；异步 LRO 轮询；前端接入。
- 不含：规模压测、保留清理、历史数据回填（开发基线不迁移旧数据）。

## 接口

| 能力 | 接口 |
| --- | --- |
| 比较（只读、瞬时、分页） | `POST /api/worksets/{target}/compare/{source}?sourceTrackerId=…&targetTrackerId=…`，分页使用可选的 `offset` 和 `limit` |
| 提交一页决策 | `POST /api/worksets/{target}/merge-rounds`，body `{sourceWorkingSetId, requestId, decisions}` |
| 轮询轮次 | `GET /api/merge-rounds/{id}`（完整 entries/result，不分页） |
| 执行触发器 | `POST /api/merge-rounds/{id}/apply`（幂等，重复调用返回原结果） |

## 语义

- 请求校验前置：`decisions` 1–50 条、`requestId` 非空且 ≤128；不满足直接 400，不存在空轮次或分批登记入口。
- 每个 decision 只含 `evidence` + `decision`；`entryKey` 已于 2026-11-17 废弃。轮次内条目身份由服务端生成的 `Entry.id` 承担，跨轮次审计使用完整 evidence。
- **开发基线**：按现行 schema 初始化，不迁移旧 V4 合并表和轮次 JSON。旧 V3 表沿用 `air_ws_items_merge_index`；切换前收敛旧版本的在途轮次和事件。
- 同 `requestId` 返回原轮次；同键换方向 409 `Conflict`；登记阶段证据过期 409 `StaleEvidence`，整轮回滚，不留半成品轮次。
- 并发同键：`insertOrFindRound` 以 `ON CONFLICT (request_id) DO UPDATE … RETURNING state` 单条语句返回已提交轮次，不依赖"插入失败后再查询"的可见性。
- 提交后由 `MergeRoundSubmittedEvent` 异步逐条执行；`apply` 仅作幂等触发器与恢复入口。
- Checkpoint 的业务身份是**两侧 Tracker 分支 + 家族**（旧表列 `branch_a_id, branch_b_id, common_id`）；**WorkingSet 不参与**；左右 `local_id` 只作为确认时的变体证据。

## 验证

`mvn -B -Dspring.profiles.active=default test`：113 项通过（22 个测试类；T6-F、错误码契约、TrackerPair 四元组与 sourceId 配对测试后全量为 133 项；T7-G 后全量 165 项），其中 `MergeAcceptanceTest` 27 项覆盖：

- `submittedDecisionsRunAsAsyncRoundAndPollToTerminal`、`duplicateSubmittedDecisionsReturnTheSameRound`：异步轮询与串行同键重放。
- `concurrentSubmissionsWithSameRequestIdReturnOneRound`：两线程同键并发提交返回同一轮次，库中只有一行。
- `failedSubmissionLeavesNoRoundBehind`：证据过期 409 后事务回滚，不残留轮次。
- `rejectsMissingDecisionsMissingRequestIdAndOversizedPage`、`rejectsRequestIdLongerThanStorageColumn`、`requestIdCannotBeReusedForAnotherDirection`：空决策、缺键、超限、超长与跨方向冲突。
- `secondRoundWithRacedEvidenceCannotCommitTheSameFamilyTwice`：同家族旧证据第二次提交被拒。
- `partialRoundRetainsKeepTargetCheckpointAndNewRoundOnlyProcessesSelectedItems`：KEEP_TARGET 确认、部分成功与重做边界。
- `competingApplyIsRejectedAndResumeOnlyRecordsTheCommittedWrite`、`replayCompletesMergeTargetAfterListenerFailureWithoutApplyingBusinessWriteAgain`：并发执行、恢复与事件重投。

- `confirmationFailureRollsBackRoundAndOldBusinessReplayCannotReplaceNewRound`：确认写入失败回滚轮次结果，后续同对轮次被拒；重投业务回执恢复后可继续，旧重投不会覆盖新确认。

## 前端

从 Tracker 详情的「比较 / 合并」进入，选择直属派生对象和方向。比较、翻页和整页决策都围绕该 Tracker 对，提交后轮询轮次结果。入口见 [merge.js](../../../deploys/src/main/resources/static/js/merge.js)，验收见 [MergeAcceptanceTest](../../../deploys/src/test/java/bootstrap/MergeAcceptanceTest.java)。
