# 工作集创建 LRO 设计（T7）

**状态：Reviewed（T7-A；§2 保留已确认决策，接口与运行细节见 [T7-A 评审](../reviews/2026-11-13-t7-a-creation-lro-review.md)
，未发布）**

-

依据：[FACT-028](../domain/facts.md)、[多级 WorkingSet 谱系](working-set-lineage-design.md)、[T2 绑定与删除](binding-deletion-contract-v1.md)、[T3 物化](lighttrackeritem-materialization-contract-v1.md)、[T4 跨 BC](cross-bc-contract-v1.md)、[T1 Merge 执行](merge-execution-contract-v1.md)、[ADR-0005](../adr/0005-merge-execution-model.md)

- 范围：创建工作集的异步长流程（LRO）；区别于现有的原子操作入口
-

追溯：[状态图](../diagrams/working-set-creation-lro.md)、[SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)

- 不包含：权限（D11 延后）、附件/关系参与 Merge（D6）、停用（D5）

## 1. 背景与定位

创建 LRO 按所选范围初始化子工作集，扩展 LRO 给已有子工作集纳入范围。创建空本体、绑定空间和复用共享 Tracker 仍有独立入口。ALM
Requirement 是业务 BC 参考实现。

## 2. 用户决策（2026-11-13）

1. **异步 LRO**：创建工作集是异步长流程，提供 REST 接口供前端轮询。
2. **非共享 Tracker copy 的 Item 范围**：父 Tracker 下**全部 Item**；**软删除的剔除**。
3. **共享 Tracker 范围**：由用户自定义选择；选择了哪些，就在新工作集下绑定哪些。
4. **批量 LTI 失败语义**：任务作为整体**暂不开放重试**；一次失败或成功；成功允许**部分成功**
   ；结果记录在创建任务结果中。由于子工作集不影响父级工作集、对外部无影响，**失败后不需要补偿**；但任务明细必须显性标记
   **失败原因与具体位置**。
5. **关系/引用构建**：在 LRO 内，异步事务交互；LightTrackerItem 创建完成后发送事件，触发引用/关联关系构建。
6. **复用 MergeRound 的 LRO 模式与事件框架**（状态机、进度、结果、Modulith 事件）。

## 3. 术语

| 术语                             | 含义                                                                           |
|----------------------------------|--------------------------------------------------------------------------------|
| 创建任务（`WorkingSetCreation`） | 一次“按范围创建完整工作集”的 LRO 载体，独立聚合；不拥有 WorkingSet/Tracker/LTI |
| 步骤（Step）                     | LRO 内的一个阶段，如创建本体、复制非共享 Tracker、批量索引                     |
| 任务明细                         | 每步/每项的成败、失败原因与位置（TrackerId / LocalId）                         |
| 部分成功                         | 任务完成但存在失败项；已成功项保留，失败项在明细标记                           |
| 范围选择                         | 用户选定的产品空间、共享 Tracker、非共享父 Tracker 集合                        |

## 4. 模型

### 4.1 LRO 聚合 `WorkingSetCreation`

- 身份：任务 `creationId` 和新工作集 ID 均由服务端分配，遵循 [T4 身份约定](cross-bc-contract-v1.md#4-六个用例的契约边界)。
- 参数：父/目标工作集 ID、创建或扩展模式、`requestId`、名称、所选范围与发起人。公开请求与持久化参数的对应见 §7.3。
- 状态：`Pending → Running → Succeeded / PartiallySucceeded / Failed`。
- 进度：当前步骤、已完成/总数（Tracker 数、Item 数）。
- 结果：步骤明细与失败明细（JSONB）；`completedAt`。
- 不拥有 `WorkingSet`、`Tracker`、`LightTrackerItem`、`Reference` 聚合；只记录编排事实。

### 4.2 步骤与状态机

| 步骤                                     | 内容                                                                                                                                                  | 依赖                                                 | 失败处理                                                              |
|------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|-----------------------------------------------------------------------|
| `CREATE_WORKING_SET`                     | 创建子 WS 本体（`parentWorkingSetId`）                                                                                                                | —                                                    | 失败 → 任务 `Failed`，停止                                            |
| `COPY_UNSHARED_TRACKERS`                 | 受理时按所选空间补齐必带分类的来源 Tracker（当前为 Folder），与显式选择合并去重。逐项派生，保留直属来源、空间和分类                                   | 本体已创建；Tracker 所属空间已选                     | 单项失败记录明细，继续其他项                                          |
| `BIND_PRODUCT_AND_TRACKER`               | 在目标 WorkingSet 聚合中按空间纳入合法范围，同时绑定已复制的非共享 Tracker 和选定的共享 Tracker。空间成员关系由 TrackerBinding 投影，合法绑定一次保存 | 复制阶段已结束；新空间须有必带副本，且在直属父范围内 | 阶段按工作集计一项，各空间与 Tracker 的失败逐项记录，保留其余合法绑定 |
| `INDEX_UNSHARED_TRACKERS`（T7-D 已实现） | 对每个绑定成功的非共享子 Tracker，按父 Tracker 的有效 Item 创建 LightTrackerItem                                                                      | 复制+绑定                                            | 逐项记录；允许部分成功                                                |
| `PUBLISH_RELATION_EVENTS`（T7-E 已实现） | 索引完成后发布 `ReferenceCreationRequestedEvent`、`DanglingReferenceRepairRequestedEvent`、`AssociationCreationRequestedEvent`；不操作关系聚合        | 索引                                                 | 记录事件发布失败；不阻断主流程，不自动重试                            |

- 步骤内逐项失败**不中断**同步骤其他项；步骤间按依赖继续可执行步骤。
- 复制与绑定分别提交。复制失败时跳过该 Tracker 的绑定，复制成功而绑定失败时保留副本并记录绑定阶段的失败。轮询的 `progress`
  是任务汇总，`stageProgress` 分阶段记录成功、失败、跳过和总数。
- 最终状态：本体创建失败时为 `Failed`；本体成功后有失败明细（包括带原因的跳过）为 `PartiallySucceeded`，无失败明细为
  `Succeeded`。
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

- 每个非共享父 Tracker 复制为**一个**子 Tracker（`sourceId=父 Tracker`）。所选 Tracker 的空间必须在请求的空间范围内，复制前检查该前置；实际建立
  TrackerBinding 时仍按 T2 复核。
- 对每个有效父 LTI 创建一条 LightTrackerItem，原生 Item 和继承变体均以已登记的 LTI 为候选：
    - `CommonId` 继承父 Item；`LocalId` 由服务端雪花生成；
    - 自身 Revision 固定为父变体 Revision。父变体已有本地业务内容时以父 LocalId 和 Revision 为有效来源。父变体仍为
      Source-backed 时沿用其实际 `SourceLocalId` 和 `SourceRevision`
      ，见 [T3 §4](lighttrackeritem-materialization-contract-v1.md#4-双态契约与状态判定)。
    - 普通业务字段按 T3 读取，附件与关系的派生基准仍按 T3 §2.1。
- 受理只记录选择及各 Tracker 的基线时间。索引按其时间上界和 LocalId 游标尽力扫描当前可用候选，终态后不补扫。已发现的来源缺失、软删或版次不可读时，该
  Item 计为跳过并保留明细。[ADR-0010](../adr/0010-working-set-creation-tracker-baseline-time.md)
- SK 只扫描未软删且登记时间不晚于基线的 LTI。业务对象先提交而索引晚于基线才登记时，本轮允许漏项。缺 Folder
  适配器且没有已登记业务索引时，默认 Folder 容器按空目录处理。其他类型缺适配器记单 Tracker 失败。
- 子工作集已有同族 LTI 计为跳过。其余候选在业务来源锁下核对删除状态和版次，不读取普通业务字段。
- 索引由系统任务执行，当前不校验用户级授权；权限接入后另行裁定。

### 4.6 关系事件发布

索引阶段结束时登记 `TrackerItemsIndexedEvent`，提交后交付。扫描完成的 Tracker 可以含单项失败或带明细的跳过。业务 BC
可消费该事件投影公共查询视图（如 `air_inst_common`）；SK 不写该表，投影当前为待实现的空监听器，最终一致。关系请求由后续独立步骤
`PUBLISH_RELATION_EVENTS` 发布。事件字段、Merge
修复时点与失败边界统一见 [T7-E 事件契约](../reviews/2026-11-17-t7-e-relation-guards-decision.md)。

## 5. 事务、事件与一致性

**事务边界（TC-002）**：批次仅用于调度和分页，不是跨聚合写事务。每个 LTI、Tracker、WorkingSet
与任务进度分别提交；目标为副作用与其发布记录同事务保存，进度由结果事件独立推进并幂等去重。当前实现与恢复缺口见 [实现边界](../implementation/current-boundaries.md)。

- LRO 只推进状态与结果，不保存其他聚合。
- 批量 LTI 按批提交（批大小可配置），逐项记录成功/失败；部分成功保留。
- 事件沿用 Modulith 未完成事件重投（与 T6-B/D13 一致）。重投仅接续中断前尚无持久化结论的执行；已记录的业务失败保持终态。对应进度由结果事件消费推进。
- 不补偿：失败不回滚已创建的子 WS/Tracker/LTI；子 WS 与父 WS 独立，失败不影响父级。用户可自行按 T2 K3 删除叶子子 WS。
- 可见性（T7-A 用户确认）：从任务受理到终态前，普通入口（工作集详情、子级、祖先、绑定、Item 读取/写入）对目标 WS 返回 `Preparing`；
  `findChildren`/`findAncestors` 过滤 Pending/Running 的目标；任务上下文通过任务专用只读接口读取目标
  WS。轮询入口继续开放，终态后按实际提交结果访问工作集。

## 6. 幂等与并发

- 创建和扩展请求以 `requestId` 判重。相同请求复用已有任务、目标 ID 和各 Tracker 的基线时间。同键改变请求范围或显式基线时间返回
  HTTP 409。持久化约束见 [schema.sql](../../../deploys/src/main/resources/schema.sql)；扩展并发见 §12A。
- 步骤内已存在的 Tracker/LTI（重入/重复执行）视为已完成并记录“skipped”，不重复创建；唯一索引兜底。
- 不开放任务重试接口；失败后由人工查看明细并决定后续（删除子 WS 或手工补齐）。

## 7. 数据模型与端口

### 7.1 持久化

`working_set_creations.result` 保存规范化请求、各 Tracker 解析后的 `baselineAtByTracker`、发起人、进度与明细。DDL
见 [schema.sql](../../../deploys/src/main/resources/schema.sql)。

### 7.2 端口

- `LightTrackerItemQueryProvider` 提供按登记时间与 LocalId 分页，`LightTrackerItemRepository` 提供按工作集和家族认领。
- `TrackerItemSourceLock` 返回受业务来源行锁保护的版次和删除状态。
- 关系事件：WorkingSet 不定义 Reference/Association 仓储或写入
  Port；只定义关系事件契约与发布器。字段、事件类、失败明细见 [T7-E 事件契约](../reviews/2026-11-17-t7-e-relation-guards-decision.md)。

### 7.3 REST

- `POST /api/working-set-creations` 接收 `parentWorkingSetId`、`requestId`、`name`、`productSpaceIds`、`sharedTrackerIds`、
  `unsharedParentTrackerIds`。范围按集合比较，空范围合法；服务端分配目标 ID，返回 HTTP 202、任务视图和轮询 `Location`。
- 创建和扩展均接收可选 `baselineAtByTracker`，形如 `{"123":1738368000000}`，值为 epoch 毫秒。未指定的 Tracker 使用
  HEAD，即首次受理时间。只允许所选 Tracker，拒绝未来时间。任务视图返回各 Tracker 的解析结果。
- `GET /api/working-set-creations/{id}` 返回 `creationId`、`targetWorkingSetId`、`mode`、状态、步骤、进度、明细和时间；不存在返回
  HTTP 404。
- `GET /api/working-set-creations/{id}/working-set` 任务专用只读：按任务 id 读取创建中的目标工作集；普通
  `/api/worksets/{id}` 仍返回 `Preparing`。任务不存在或目标尚未创建 → 404。
- `failures` 项使用 `stage`、`productSpaceId`、`trackerId`、`localId`、`reason`、`occurredAt`。位置字段按适用性填写。`reason`
  使用对应 T2 或 T4 失败语义，未分类内部错误不暴露异常文本。
- 轮询由前端按状态终止（`Succeeded/PartiallySucceeded/Failed`）。

## 9. 规模与分页

- 主进度按父 Tracker 计数，`indexProgress` 提供各 Tracker 的 Item 成功、失败和跳过数。批大小配置为
  `workingset.creation.index-batch-size`，默认 100。游标表示已确认结果的进度；逐条提交后的乱序、游标和重放规则仍待设计。
- `result` JSONB 可能随失败项增长；T7-F 需验证失败明细规模与保留/清理。
- 并发创建多个工作集时，任务表与批量写入的连接池压力需压测（QUESTION-012）。

## 10. 与现有能力的关系

- 单 Item 派生的 HTTP 入口已退役。对已有工作集批量派生见 §12A。
- **不重开** S6 的“空范围子 WS 合法”“创建不自动复制绑定”：T7 是显式范围创建入口。
- 与 T6-D（回并）无关：回并是已有子 WS 的 Merge CREATE；T7 是新建工作集的初始化。
- 复用 MergeRound 的 LRO 模式：状态机、结果、Modulith 事件、独立事务；不复用 MergeRound 的 Checkpoint/决定语义。

## 11. 开放问题（T7 内需确认）

- 批大小、分页大小与结果保留在 T7-F 验证。取消和基于已有子工作集发起补齐任务仍待单独决策。

## 12. 实施切片（T7-A～F）

| 切片 | 内容                                                                                                                                                         | 依赖                           | 验收                                                                              |
|------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|-----------------------------------------------------------------------------------|
| T7-A | 设计评审与契约：LRO 聚合、状态机、API、失败明细、幂等                                                                                                        | 本文档                         | 评审通过、T4/场景更新                                                             |
| T7-B | LRO 骨架：任务表、创建/轮询 REST、状态推进、幂等                                                                                                             | T7-A                           | HTTP 创建返回任务；轮询状态；重复提交返回原任务                                   |
| T7-C | 范围复制与绑定：子 WS 本体、非共享 Tracker copy、共享/空间绑定                                                                                               | T7-B、S6                       | 副本/绑定正确；非法范围记录失败明细                                               |
| T7-D | 批量 LTI 索引：分页端口、软删剔除、分批创建、部分成功明细                                                                                                    | T7-C、T3                       | 批量索引成功/部分成功；软删剔除；重复执行幂等                                     |
| T7-E | 关系事件发布：索引完成后发 `ReferenceCreationRequestedEvent`/`DanglingReferenceRepairRequestedEvent`/`AssociationCreationRequestedEvent`；Merge 后发修复事件 | T7-D、QUESTION-010（已裁定 C） | 事件触发；WorkingSet 不操作关系聚合；失败不阻塞、不自动重试；不参与 Compare/Merge |
| T7-F | 规模与恢复：批大小、分页、结果保留、压测                                                                                                                     | T7-E                           | 规模数据、失败明细可读、连接池可承受                                              |

## 12A. T7-G 扩展已有工作集（Implemented，2026-11-17）

- 入口：`POST /api/worksets/{target}/extensions`，请求 `{requestId, trackerIds, baselineAtByTracker?}`；返回 202，任务复用
  `GET /api/working-set-creations/{id}` 轮询。
- 目标：对已有子工作集扩展范围。约定保留已有范围的可见性；当前查询门禁偏差见 [开放问题](open-questions.md)。
- 选择：按直接父工作集的 Tracker 选择一批 Tracker；只允许直属父级来源。
- 重复过滤：共享 Tracker 按目标的绑定 ID 判断；非共享 Tracker 按已绑定副本的 `sourceId` 匹配父 TrackerId。同一分类下其他
  Tracker 有不同身份，可继续纳入。界面保留这些已纳入项并禁用勾选。提交过滤后为空返回 `NoDerivableTracker`，不创建任务。
- 扩展范围：共享 Tracker 复用原 ID 并建立绑定。非共享 Tracker 创建可显示的副本、绑定并索引父 Item。Tracker 创建时即可显示；正在创建的目标
  WorkingSet 由工作集就绪校验挡住普通查询与业务命令。已有 WorkingSet 在扩展期间继续可用。
- 已有 Tracker 的 Item：不在扩展任务内直接覆盖；由普通 Merge 合并加入（扩展只负责新 Tracker）。
- 原子性：创建新子工作集与扩展已有子工作集均允许部分成功；T6-F i 的“批量派生整批原子”不再适用于 T7 创建/扩展 LRO。
- 并发：同一目标工作集同时只允许一个未终态扩展任务（`uq_active_extension_target`）；不同请求返回 `Preparing`。
- 事件与事务：复用 T7 LRO 的单聚合事务约束、事件与失败明细。
- 单 Item `/api/worksets/{ws}/derive` 与按空间/分类的单 Item 派生入口已移除；批量派生统一走扩展任务。

## 13. 状态与下一步

- 实施证据见 [T7-D 验收](../implementation/t7-d-index-verification.md)
  ，剩余工作见 [T7 后续计划](../implementation/t7-implementation-prompt.md)。
