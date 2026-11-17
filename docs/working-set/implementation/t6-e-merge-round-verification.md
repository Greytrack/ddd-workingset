# T6-E MergeRound 一页决策与幂等提交验收

**状态：Accepted（2026-11-16 收敛）**。设计依据 [T1 契约 §3/§4](../domain/merge-execution-contract-v1.md) 与 [Compare 不持久化裁定](../reviews/2026-11-15-compare-not-persisted-decision.md)；规模与保留见[规模压测与保留专题](performance-testing.md)。

## 范围

- 瞬时 compare 分页；一页用户决策形成一个 MergeRound；`requestId` 幂等；异步 LRO 轮询；前端接入。
- 不含：规模压测、保留清理、历史数据回填（开发基线不迁移旧数据）。

## 接口

| 能力 | 接口 |
| --- | --- |
| 比较（只读、瞬时、分页） | `POST /api/worksets/{target}/compare/{source}?offset&limit` |
| 提交一页决策 | `POST /api/worksets/{target}/merge-rounds`，body `{sourceWorkingSetId, requestId, decisions}` |
| 轮询轮次 | `GET /api/merge-rounds/{id}`（完整 entries/result，不分页） |
| 执行触发器 | `POST /api/merge-rounds/{id}/apply`（幂等，重复调用返回原结果） |
| 撤销 Checkpoint | `POST /api/merge-checkpoints/{trackerA}/{trackerB}/{family}/revoke` |

## 语义

- 请求校验前置：`decisions` 1–50 条、`requestId` 非空且 ≤128；不满足直接 400，不存在空轮次或分批登记入口。
- 每个 decision 只含 `evidence` + `decision`；`entryKey` 已于 2026-11-17 废弃。轮次内条目身份由服务端生成的 `Entry.id` 承担，跨轮次审计使用完整 evidence。
- 同 `requestId` 返回原轮次；同键换方向 409 `Conflict`；登记阶段证据过期 409 `StaleEvidence`，整轮回滚，不留半成品轮次。
- 并发同键：`insertOrFindRound` 以 `ON CONFLICT (request_id) DO UPDATE … RETURNING state` 单条语句返回已提交轮次，不依赖"插入失败后再查询"的可见性。
- 提交后由 `MergeRoundSubmitted` 异步逐条执行；`apply` 仅作幂等触发器与恢复入口。
- Checkpoint 的业务身份是**两侧 Tracker 分支 + 家族**（`tracker_a_id, tracker_b_id, common_id`）；**WorkingSet 不参与**；左右 `local_id` 只作为确认时的变体证据。

## 验证

`mvn -B -Dspring.profiles.active=default test`：113 项通过（22 个测试类；T6-F、错误码契约、TrackerPair 四元组与 sourceId 配对测试后全量为 133 项），其中 `MergeAcceptanceTest` 26 项覆盖：

- `submittedDecisionsRunAsAsyncRoundAndPollToTerminal`、`duplicateSubmittedDecisionsReturnTheSameRound`：异步轮询与串行同键重放。
- `concurrentSubmissionsWithSameRequestIdReturnOneRound`：两线程同键并发提交返回同一轮次，库中只有一行。
- `failedSubmissionLeavesNoRoundBehind`：证据过期 409 后事务回滚，不残留轮次。
- `rejectsMissingDecisionsMissingRequestIdAndOversizedPage`、`rejectsRequestIdLongerThanStorageColumn`、`requestIdCannotBeReusedForAnotherDirection`：空决策、缺键、超限、超长与跨方向冲突。
- `secondRoundWithRacedEvidenceCannotCommitTheSameFamilyTwice`：同家族旧证据第二次提交被拒。
- `partialRoundRetainsKeepTargetCheckpointAndNewRoundOnlyProcessesSelectedItems`：KEEP_TARGET 确认、部分成功与重做边界。
- `competingApplyIsRejectedAndResumeOnlyRecordsTheCommittedWrite`、`replayCompletesMergeTargetAfterListenerFailureWithoutApplyingBusinessWriteAgain`：并发执行、恢复与事件重投。

## 前端

`src/main/resources/static/js/merge.js`：面板顶部「源 / 目标」下拉（默认当前工作集 → 其父；默认工作集取第一个子），前端预判直接父子并禁用非法组合；compare 分页、按差异类型给默认决策（`ADDED`/`MODIFIED` 采纳源，`DELETED` 保留目标），整页决策以 `crypto.randomUUID()` 作为 `requestId` 提交，随后轮询轮次终态并展示摘要与条目结果。全局函数带 `merge` 前缀（避免覆盖 `features.js` 的 `flattenWorksets`），静态资源按 `?v=N` 版本化；约定见 [前端脚本约定](../../../src/main/resources/static/js/README.md)。
