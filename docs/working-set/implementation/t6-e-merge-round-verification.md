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
| 撤销 Checkpoint | `POST /api/worksets/{target}/merge-checkpoints/{source}/{family}/revoke` |

## 语义

- 请求校验前置：`decisions` 1–50 条、`requestId` 非空且 ≤128；不满足直接 400，不存在空轮次或分批登记入口。
- 同 `requestId` 返回原轮次；同键换方向 409 `Conflict`；登记阶段证据过期 409 `StaleEvidence`，整轮回滚，不留半成品轮次。
- 提交后由 `MergeRoundSubmitted` 异步逐条执行；`apply` 仅作幂等触发器与恢复入口。
- Checkpoint 的业务身份是方向 + 家族（`source_id, target_id, common_id`）；左右 `local_id` 只作为确认时的变体证据。

## 验证

`mvn -B -Dspring.profiles.active=default test`：110 项通过，其中 `MergeAcceptanceTest` 17 项覆盖：

- `submittedDecisionsRunAsAsyncRoundAndPollToTerminal`、`duplicateSubmittedDecisionsReturnTheSameRound`：异步轮询与同键幂等。
- `rejectsMissingDecisionsMissingRequestIdAndOversizedPage`、`requestIdCannotBeReusedForAnotherDirection`：前置拦截与跨方向冲突。
- `secondRoundWithRacedEvidenceCannotCommitTheSameFamilyTwice`：同家族旧证据第二次提交被拒。
- `partialRoundRetainsKeepTargetCheckpointAndNewRoundOnlyProcessesSelectedItems`：KEEP_TARGET 确认、部分成功与重做边界。
- `competingApplyIsRejectedAndResumeOnlyRecordsTheCommittedWrite`、`replayCompletesMergeTargetAfterListenerFailureWithoutApplyingBusinessWriteAgain`：并发执行、恢复与事件重投。

## 前端

`src/main/resources/static/js/merge.js`：默认「当前工作集 → 其父工作集」方向（默认工作集取第一个子工作集），compare 分页、逐条选择采纳源/保留目标/暂缓并勾选标记已处理，整页决策以 `crypto.randomUUID()` 作为 `requestId` 提交，随后轮询轮次终态并展示摘要与条目结果。
