# 工作集创建 LRO 设计（T7）

**状态：Reviewed（T7-A；§2 保留已确认决策，接口与运行细节见 [T7-A 评审](../reviews/2026-11-13-t7-a-creation-lro-review.md)，未发布）**

- 依据：[FACT-028](../domain/facts.md)、[多级 WorkingSet 谱系](working-set-lineage-design.md)、[T2 绑定与删除](binding-deletion-contract-v1.md)、[T3 物化](lighttrackeritem-materialization-contract-v1.md)、[T4 跨 BC](cross-bc-contract-v1.md)、[T1 Merge 执行](merge-execution-contract-v1.md)、[ADR-0005](../adr/0005-merge-execution-model.md)
- 范围：创建工作集的异步长流程（LRO）；区别于现有的原子操作入口
- 追溯：[状态图](../diagrams/working-set-creation-lro.md)、[SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)
- 不包含：权限（D11 延后）、附件/关系参与 Merge（D6）、停用（D5）

## 1. 背景与定位

现有实现把“创建工作集”拆成多个原子操作：`createChildWorkingSet`（创建空本体）、`bindSpace`、`bindTrackerShared`、`derive`/`bindTrackerDerived`（逐项派生）。S6 设计明确“允许空范围子工作集”“创建子 WS 不自动复制父 WS 绑定”，子 WS 范围由后续逐项纳入决定。

T7 新增**另一个用例**：**按用户选定的范围一次性创建完整工作集**，作为异步长流程（LRO）执行。它不替换原子操作，也不改变 S6 的“空范围子 WS 合法”；两者是不同入口：原子操作用于后续增量调整，T7 用于“新建并初始化范围”。

T7 的产物仍是 `sk.workingset` 的契约与机制；`alm.requirement` 只作为占位适配与验收样例。

## 2. 用户决策（2026-11-13）

1. **异步 LRO**：创建工作集是异步长流程，提供 REST 接口供前端轮询。
2. **非共享 Tracker copy 的 Item 范围**：父 Tracker 下**全部 Item**；**软删除的剔除**。
3. **共享 Tracker 范围**：由用户自定义选择；选择了哪些，就在新工作集下绑定哪些。
4. **批量 LTI 失败语义**：任务作为整体**暂不开放重试**；一次失败或成功；成功允许**部分成功**；结果记录在创建任务结果中。由于子工作集不影响父级工作集、对外部无影响，**失败后不需要补偿**；但任务明细必须显性标记**失败原因与具体位置**。
5. **关系/引用构建**：在 LRO 内，异步事务交互；LightTrackerItem 创建完成后发送事件，触发引用/关联关系构建。
6. **复用 MergeRound 的 LRO 模式与事件框架**（状态机、进度、结果、Modulith 事件）。

## 3. 术语

| 术语 | 含义 |
| --- | --- |
| 创建任务（`WorkingSetCreation`） | 一次“按范围创建完整工作集”的 LRO 载体，独立聚合；不拥有 WorkingSet/Tracker/LTI |
| 步骤（Step） | LRO 内的一个阶段，如创建本体、复制非共享 Tracker、批量索引 |
| 任务明细 | 每步/每项的成败、失败原因与位置（TrackerId / LocalId） |
| 部分成功 | 任务完成但存在失败项；已成功项保留，失败项在明细标记 |
| 范围选择 | 用户选定的产品空间、共享 Tracker、非共享父 Tracker 集合 |

## 4. 模型

### 4.1 LRO 聚合 `WorkingSetCreation`

- 身份：`creationId`（雪花或请求幂等键派生）。
- 参数：`parentWorkingSetId`、`newWorkingSetId`、选定的 `productSpaceIds`、`sharedTrackerIds`、`unsharedParentTrackerIds`、发起人（`Caller`，D11 暂不校验）。
- 状态：`Pending → Running → Succeeded / PartiallySucceeded / Failed`。
- 进度：当前步骤、已完成/总数（Tracker 数、Item 数）。
- 结果：步骤明细与失败明细（JSONB）；`completedAt`。
- 不拥有 `WorkingSet`、`Tracker`、`LightTrackerItem`、`Reference` 聚合；只记录编排事实。

### 4.2 步骤与状态机

| 步骤 | 内容 | 依赖 | 失败处理 |
| --- | --- | --- | --- |
| `CREATE_WORKING_SET` | 创建子 WS 本体（`parentWorkingSetId`） | — | 失败 → 任务 `Failed`，停止 |
| `BIND_PRODUCT_SPACES` | 绑定用户选定的产品空间 | 本体 | 同上 |
| `COPY_UNSHARED_TRACKERS` | 为每个选定的父 Tracker 复制一个子 Tracker（`sourceId=父`、同空间/分类、`UNSHARED`） | 对应空间已绑定 | 单项失败记录明细，继续其他项 |
| `BIND_UNSHARED_TRACKERS` | 绑定已复制的非共享 Tracker | 复制及对应空间已绑定 | 同上 |
| `BIND_SHARED_TRACKERS` | 绑定用户选定的共享 Tracker（复用父 TrackerId，不复制） | 对应空间已绑定 | 同上 |
| `INDEX_UNSHARED_TRACKERS`（T7-D 已实现） | 对每个绑定成功的非共享子 Tracker，按父 Tracker 的有效 Item 创建 LightTrackerItem | 复制+绑定 | 逐项记录；允许部分成功 |
| `LINK_RELATIONS` | 索引完成后发布事件，异步构建引用/关联（依赖 QUESTION-010） | 索引 | 记录失败项；不阻断任务整体 |

- 步骤内逐项失败**不中断**同步骤其他项；步骤间按依赖继续可执行步骤。
- 复制与绑定分别提交。复制失败时跳过该 Tracker 的绑定，复制成功而绑定失败时保留副本并记录绑定阶段的失败。轮询的 `progress` 是任务汇总，`stageProgress` 分阶段记录成功、失败、跳过和总数。
- 最终状态：全部步骤成功 → `Succeeded`；存在失败项但关键步骤（创建本体）成功 → `PartiallySucceeded`；关键步骤失败 → `Failed`。
- 不开放任务重试；重复提交返回已有任务（见 §6）。

### 4.3 失败明细

每个失败项至少记录：

- `stage`（步骤名）、`trackerId`、`localId`（若适用）、`reason`（失败分类或异常摘要）、`occurredAt`；
- 任务结果汇总：成功数、失败数、跳过数（如已存在）。
- 失败项必须可在轮询接口读取；不以日志代替任务明细。

### 4.4 范围选择

- **非共享 Tracker**：请求携带父 WS 的 TrackerId 列表；只允许直属父 WS 当前有效范围内的 Tracker（S6 来源约束）。
- **共享 Tracker**：请求携带父 WS 中已绑定的共享 TrackerId 列表；只绑定所选，不在新 WS 复制。
- **产品空间**：请求携带 ProductSpaceId 列表；只允许父 WS 有效范围内的空间。
- 父 WS 不存在的 Tracker/空间 → 该请求项失败并记录明细，不擅自扩大范围。

### 4.5 身份与 LTI 索引

- 每个非共享父 Tracker 复制为**一个**子 Tracker（`sourceId=父 Tracker`）。所选 Tracker 的空间必须在请求的空间范围内，复制前检查该前置；实际建立 TrackerBinding 时仍按 T2 复核。
- 对每个有效父 Item 创建一条 LightTrackerItem，父范围包括业务原生 Item 与已纳入的 LTI，重合身份只计一次：
  - `CommonId` 继承父 Item；`LocalId` 由服务端雪花生成；
  - 自身 Revision 固定为父变体 Revision。父变体已有本地业务内容时以父 LocalId 和 Revision 为有效来源。父变体仍为 Source-backed 时沿用其实际 `SourceLocalId` 和 `SourceRevision`，见 [T3 §4](lighttrackeritem-materialization-contract-v1.md#4-双态契约与状态判定)。
  - 普通业务字段按 T3 读取，附件与关系的派生基准仍按 T3 §2.1。
- 原生 Item、Local-backed 父 LTI 对应对象及 Source-backed 父 LTI 的实际内容来源，软删时均剔除且不计失败。扫描中发现对象或实际来源缺失时记 `NOT_FOUND`。
- SK 按 LocalId 合并业务身份页与 LTI 页。重合同族优先有效业务身份，子工作集已有同族 LTI 计为跳过。

### 4.6 关系/引用构建

- LTI 索引步骤完成后发布事件（如 `TrackerItemsIndexed` / `WorkingSetIndexCreated`），SK 侧异步监听并构建引用/关联关系。
- 关系模型依赖 QUESTION-010（Reference/Association 的身份、钉版、悬空、修复）。T7-E 先交付索引完成事实、关系协作端口和失败记录，关系规则待 QUESTION-010 裁定。评审建议轮询显式标记关系步骤暂缓。
- 关系构建失败记录在任务明细；不阻断已完成的索引与工作集本体。

## 5. 事务、事件与一致性

- 每个步骤/批次独立事务提交；LRO 只推进状态与结果，不把全流程放进一个大事务（避免长锁与跨聚合大事务）。
- 批量 LTI 按批提交（批大小可配置），逐项记录成功/失败；部分成功保留。
- 事件沿用 Modulith 未完成事件重投（与 T6-B/D13 一致）。重投仅接续中断前尚无持久化结论的执行；已记录的业务失败保持终态。成功的副作用和对应进度在同一事务提交。
- 不补偿：失败不回滚已创建的子 WS/Tracker/LTI；子 WS 与父 WS 独立，失败不影响父级。用户可自行按 T2 K3 删除叶子子 WS。
- 可见性（T7-A 用户确认）：从任务受理到终态前，普通入口（工作集详情、子级、祖先、绑定、Item 读取/写入）对目标 WS 返回 `Preparing`；`findChildren`/`findAncestors` 过滤 Pending/Running 的目标；任务上下文通过任务专用只读接口读取目标 WS。轮询入口继续开放，终态后按实际提交结果访问工作集。

## 6. 幂等与并发

- 请求以 `(parentWorkingSetId, newWorkingSetId, requestId)` 为幂等键。相同键、相同规范化范围返回已有任务；相同键但范围不同返回 HTTP 409。
- 同一 `newWorkingSetId` 只允许一个创建任务，其他任务请求返回 HTTP 409。
- 受理前强校验目标 `newWorkingSetId` 是否已存在于 `working_sets`：已存在 → 直接 409 `WorkingSetAlreadyExists`，不插入任务、不把已有 WS 标记为 `Preparing`。
- 步骤内已存在的 Tracker/LTI（重入/重复执行）视为已完成并记录“skipped”，不重复创建；唯一索引兜底。
- 不开放任务重试接口；失败后由人工查看明细并决定后续（删除子 WS 或手工补齐）。

## 7. 数据模型与端口

### 7.1 表

```sql
CREATE TABLE IF NOT EXISTS working_set_creations (
    id VARCHAR(64) PRIMARY KEY,
    parent_working_set_id VARCHAR(128) NOT NULL,
    new_working_set_id VARCHAR(128) NOT NULL UNIQUE,
    request_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);
```

- `result` 保存规范化请求、发起人、进度和失败明细。目标 ID 唯一约束负责并发受理，服务复核完整幂等键和请求范围。JSONB 的规模风险在 T7-F 验证。

### 7.2 端口

- `TrackerItemCatalog`（SK 定义、BC 实现）：按 Tracker 和 LocalId 游标提供未软删的业务身份页及有效同族查询。`RequirementCatalog` 继续服务 Merge。
- `LightTrackerItemRepository` 提供父 LTI 分页查询与按工作集/家族条件插入。内容可读性沿用 T3 读取边界。
- 关系/引用 Port：依赖 QUESTION-010；T7-E 定义。

### 7.3 REST

- `POST /api/working-set-creations` 接收 `parentWorkingSetId`、`newWorkingSetId`、`requestId`、`productSpaceIds`、`sharedTrackerIds`、`unsharedParentTrackerIds`。范围按集合比较，空集合合法。首次返回 HTTP 202、`creationId`、`status` 和轮询 `Location`。相同请求复用任务并返回当前状态。
- `GET /api/working-set-creations/{id}` 返回专用任务视图，字段包括 `creationId`、`status`、`stage`、`progress`、`failures` 和任务时间。进度按步骤记录成功、失败和跳过数，扫描结束前允许总数未知。不存在的任务返回 HTTP 404。
- `GET /api/working-set-creations/{id}/working-set` 任务专用只读：按任务 id 读取创建中的目标工作集；普通 `/api/worksets/{id}` 仍返回 `Preparing`。任务不存在或目标尚未创建 → 404。
- `failures` 项使用 `stage`、`productSpaceId`、`trackerId`、`localId`、`reason`、`occurredAt`。位置字段按适用性填写。`reason` 使用对应 T2 或 T4 失败语义，未分类内部错误不暴露异常文本。
- 轮询由前端按状态终止（`Succeeded/PartiallySucceeded/Failed`）。

## 8. 失败、部分成功与不补偿

- 任务一次性执行；失败后不自动重试、不回滚、不清理子 WS。
- 失败明细必须显性：阶段、Tracker、Item、原因；前端可据此提示用户。
- 任务整体状态与明细分离：`PartiallySucceeded` 表示“有成功项也有失败项”，不是全部失败。

## 9. 规模与分页

- 索引阶段主进度按父 Tracker 计数。轮询 `indexProgress` 按父 Tracker 提供 Item 成功、失败和跳过数。批大小配置为 `workingset.creation.index-batch-size`，默认 100。LocalId 游标、计数和批次 LTI 同事务提交，重投从已提交游标继续。
- `result` JSONB 可能随失败项增长；T7-F 需验证失败明细规模与保留/清理。
- 并发创建多个工作集时，任务表与批量写入的连接池压力需压测（QUESTION-012）。

## 10. 与现有能力的关系

- **不替换**原子操作：`createChildWorkingSet`、`bindSpace`、`bindTrackerShared`、`derive` 保留，用于后续增量。
- **不重开** S6 的“空范围子 WS 合法”“创建不自动复制绑定”：T7 是显式范围创建入口。
- 与 T6-D（回并）无关：回并是已有子 WS 的 Merge CREATE；T7 是新建工作集的初始化。
- 复用 MergeRound 的 LRO 模式：状态机、结果、Modulith 事件、独立事务；不复用 MergeRound 的 Checkpoint/决定语义。

## 11. 开放问题（T7 内需确认）

- 批大小、分页大小与结果保留在 T7-F 验证。取消和基于已有子工作集发起补齐任务仍待单独决策。

## 12. 实施切片（T7-A～F）

| 切片 | 内容 | 依赖 | 验收 |
| --- | --- | --- | --- |
| T7-A | 设计评审与契约：LRO 聚合、状态机、API、失败明细、幂等 | 本文档 | 评审通过、T4/场景更新 |
| T7-B | LRO 骨架：任务表、创建/轮询 REST、状态推进、幂等 | T7-A | HTTP 创建返回任务；轮询状态；重复提交返回原任务 |
| T7-C | 范围复制与绑定：子 WS 本体、非共享 Tracker copy、共享/空间绑定 | T7-B、S6 | 副本/绑定正确；非法范围记录失败明细 |
| T7-D | 批量 LTI 索引：分页端口、软删剔除、分批创建、部分成功明细 | T7-C、T3 | 批量索引成功/部分成功；软删剔除；重复执行幂等 |
| T7-E | 关系/引用构建：索引完成事件、异步构建、失败明细 | T7-D、QUESTION-010 | 事件触发；关系构建成功/失败可查 |
| T7-F | 规模与恢复：批大小、分页、结果保留、压测 | T7-E | 规模数据、失败明细可读、连接池可承受 |

## 13. 状态与下一步

- T7-D 已接入批量索引，并在最终索引批次提交后发布 `TrackerItemsIndexed`。事件包含所有扫描完成的子 Tracker，包括有单项失败的 Tracker。T7-E 消费该事件。见 [T7-D 验收](../implementation/t7-d-index-verification.md)。
