# T7-D 实施提示词（批量 LTI 索引）

**状态：Historical**。本文件保留 T7-D 实施前的任务与备选项。当前裁定和实施结果见 [T7-D 验收](t7-d-index-verification.md)。

## 0. 角色与目标

你是本仓库的实施者。T7-D 的目标是：在已完成的异步创建工作集 LRO（T7-B/T7-C）之后，为每个复制出的非共享子 Tracker 批量建立 LightTrackerItem（LTI）索引。交付物属于 `sk.workingset` 的契约与机制；`alm.requirement` 只提供分页身份适配。

T7-D 只做索引。关系/引用构建属于 T7-E，不在本切片实现。

## 1. 开始前必读

1. `AGENTS.md`、`docs/working-set/BASELINE.md`；
2. `docs/working-set/domain/working-set-creation-lro-design.md`，尤其 §4.2、§4.5、§5、§7.2、§9；
3. `docs/working-set/domain/lighttrackeritem-materialization-contract-v1.md`（T3 §2.1/§4，身份、来源与双态）；
4. `docs/working-set/domain/cross-bc-contract-v1.md` §5（T4 九分类失败语义）、§7（端口责任）；
5. `docs/working-set/implementation/t7-implementation-prompt.md`、`t7-c-binding-verification.md`；
6. 代码：`WorkingSetCreation`、`WorkingSetCreationService`、`WorkingSetApplicationService`、`LightTrackerItem`、`LightTrackerItemRepository`、`RequirementCatalog`、`TrackerRepository`。

## 2. 当前状态（不要重做）

- T7-B/T7-C 已交付任务表、状态机、REST、幂等与可见性：`Stage` 目前只有 `CREATE_WORKING_SET`、`BIND_PRODUCT_SPACES`、`COPY_UNSHARED_TRACKERS`、`BIND_UNSHARED_TRACKERS`、`BIND_SHARED_TRACKERS`。
- `WorkingSetCreationService.execute` 以任务行锁串行推进；`currentItem()` 由 `Request.items(stage)` + `stageProgress` 计算；每项写入与进度同事务提交；事件重投从已提交进度继续。
- `WorkingSetCreation.Progress` 当前为 `(succeeded, failed, skipped, Integer total)`，`total` 在受理时固化，计数单位是 `Request.items(stage)` 的元素。
- `LightTrackerItem` 已提供 `derive(...)`（Source-backed，要求 `localId != sourceLocalId`）、`local(...)`、`restore(...)`；`LightTrackerItemRepository.save` 按 `local_id` UPSERT；`light_tracker_items` 有唯一索引 `(working_set_id, common_id)`。
- `RequirementCatalog.identities(TrackerId)` 只返回 `localId/commonId`，ALM 实现查 `requirements` 且过滤 `delete_flag=FALSE`，没有 revision、来源和分页。
- `TrackerRepository.findDerivedTracker(target, source)` 可按子 WS + 父 Tracker 找到复制出的子 Tracker。
- `WorkingSetApplicationService.resolveContentSource` 已给出既有来源规则：派生父变体用 `findById` 校验后取 `effectiveContentSource()`/`effectiveContentRevision()`；业务原生 Item 的来源是自身 `localId` + `revision`。
- 普通查询/任务专用读取/`Preparing` 可见性已完成，不要重开。

## 3. 已确认的业务语义（不得重开）

- 索引范围：每个选定的非共享父 Tracker，复制并绑定成功后，对其**全部 Item 剔除软删**建立 LTI。父范围包括业务原生 Item 与已纳入的 LTI，重合身份只计一次（设计 §4.5）。
- 身份：`CommonId` 继承父 Item；`LocalId` 由服务端生成且**必须不等于** `CommonId`；自身 `Revision` 固定为父变体的 `Revision`。
- 内容来源：父变体已有本地业务内容（Local-backed）时，以父 `LocalId`/`Revision` 为有效来源；父变体仍为 Source-backed 时，沿用其实际 `sourceLocalId`/`sourceRevision`。
- 失败语义：沿用 T4 §5 九分类；软删 `DELETED`、永久删除/不存在 `NOT_FOUND`、依赖不可用 `DEPENDENCY_UNAVAILABLE`、历史不可读 `HISTORY_UNAVAILABLE`。内部异常不把异常文本写入明细。
- 一次执行、不重试、不补偿；允许部分成功；失败明细必须带 `stage`、`trackerId`、`localId`（适用时）、`reason`。
- 批量按批提交；每批与对应任务进度同事务提交；崩溃/事件重投从最后已提交位置继续。
- 已存在 LTI 视为已完成并记录 `skipped`，不重复创建；唯一索引兜底。
- 索引完成后发布事件，供 T7-E 异步构建关系；关系失败不阻断索引与工作集本体。
- 目标 WS 在任务终态前对普通入口仍为 `Preparing`；任务专用读取接口保持可用。

## 4. 必须先裁定的实现开放点（不擅自决定）

1. **进度粒度**：现有聚合只能按“阶段项”计数，且 `total` 受理时固化。推荐 INDEX 阶段以**父 Tracker** 为阶段项（`stageProgress` 计 Tracker 的 success/failed/skipped），Item 级结果只写入 `failures` 与批次结果；不在本切片重写状态机。若产品要求 Item 级进度，需扩展 `Progress`（允许动态 `total`）、增加批次游标并说明恢复语义——先向用户确认，不得静默改变任务视图契约。
2. **分页端口形态**：推荐新增 `TrackerItemCatalog`（SK 定义、ALM 适配），不要给 `RequirementCatalog` 增加可选方法或把 ALM 查询细节塞进 Merge 端口。若选择扩展旧端口，必须保持 Merge 现有行为不变。
3. **父 LTI 的软删判定**：业务原生 Item 直接按 `delete_flag` 过滤；父 LTI `materialized=true` 时对应业务内容按 `requirements.delete_flag` 判定；Source-backed 的父 LTI 是否检查来源内容软删，按 T3 §4 现行读取语义裁决并在验证记录中写明。不得自行扩大“软删”定义。
4. **批次大小与恢复粒度**：T7-F 才最终确定配置与压测；T7-D 可先引入可配置批大小（默认值写入文档）。若按 Tracker 为阶段项，崩溃后重做该 Tracker 并从已提交批次继续，需要明确批次游标存放位置（任务 `result` JSONB 或重新扫描 + 幂等跳过）。
5. **索引完成事件契约**：建议 `TrackerItemsIndexed(creationId, workingSetId, trackerIds, occurredAt)`，只定义与发布，不实现关系监听。事件字段不得引入 ALM 类型。
6. **失败原因映射**：父 Item 读取失败、来源缺失、内容已删分别使用哪一分类，先列映射表再实现；未知内部错误统一 `UNKNOWN`，不泄露 SQL/异常文本。

以上任一项无法从现有设计直接推出时，停止实现并向用户确认；不要把推荐方案直接记成 Accepted。

## 5. 任务分解

### 5.1 状态机接入

- 在 `WorkingSetCreation.Stage` 末尾增加 `INDEX_UNSHARED_TRACKERS`，顺序位于 `BIND_SHARED_TRACKERS` 之后；本切片不新增 `LINK_RELATIONS`，由 T7-E 决定。
- `Request.items(INDEX_UNSHARED_TRACKERS)` 返回 `unsharedParentTrackerIds`；`submit` 的阶段总数自然包含这些项。
- `WorkingSetCreationService.executeItem` 增加分支：
  - 通过 `trackers.findDerivedTracker(target, parentTrackerId)` 找复制出的子 Tracker；
  - 复制/绑定阶段已失败、子 Tracker 不存在或无有效绑定时记 `Skipped`，不构造 LTI；
  - 否则分页读取并索引。
- 确认旧任务 JSONB（缺少新阶段）仍可恢复：进入新阶段时初始化 `stageProgress`，不得让 `currentItem()` 读到 `null`。

### 5.2 分页身份端口

- 在 `sk.workingset.application.port` 新增 `TrackerItemCatalog`，按父 `TrackerId` 做 keyset 分页，返回：
  - `localId`、`commonId`、`revision`；
  - 有效内容来源 `sourceLocalId`、`sourceRevision`、`materialized`；
  - 软删项不返回；同一 `CommonId` 只返回一次（跨业务原生与 LTI 来源去重）。
- 游标使用 `localId > lastLocalId`，禁止 `OFFSET`；页大小为批大小的输入。
- ALM 适配器：
  - `requirements`：`tracker_id = ? AND delete_flag = FALSE AND local_id > ? ORDER BY local_id`，来源为自身；
  - `light_tracker_items`：`tracker_id = ? AND local_id > ? ORDER BY local_id`，来源按 `materialized` 选择自身或 `source_local_id/source_revision`；
  - 两源合并时按 `commonId` 去重；若同族同时出现两种来源，优先有效业务变体并在验证记录中说明。
- SK 只依赖端口；ALM 类型不得进入 `sk.workingset`（ArchUnit）。

### 5.3 身份、来源与软删

- 对每条身份构造 `LightTrackerItem.derive(nextDistinctLocalId(commonId), commonId, childTrackerId, targetWorkingSetId, parentRevision, contentSourceLocalId, contentSourceRevision)`。
- 业务原生父 Item：`parentRevision = requirements.revision`，来源为自身 `localId`/`revision`。
- 父 LTI：`parentRevision = lti.revision`，来源为 `materialized ? (lti.localId, lti.revision) : (lti.sourceLocalId, lti.sourceRevision)`。
- 子 Tracker 必须是复制后的 `UNSHARED` 子 Tracker；共享 Tracker 不索引、不复制。
- 软删：按 §4.3 的裁定过滤；永久删除 Item 不返回，天然不参与。
- 重合身份只建一条；已有 LTI（同 `working_set_id + common_id`）记 `Skipped`。

### 5.4 分批提交与进度

- 每批在一个事务内完成：读取一页 → 逐项构造/保存 LTI → 更新任务进度或批次游标。不要一次加载整个 Tracker 的 Item。
- 同批单项失败不中断其他项；失败项写入 `failures`，成功项保留。
- 批大小可配置；默认值与配置键写入设计 §9 或验证记录。
- 任务行锁仍串行化每个推进；不要把整批拆成无进度记录的多事务，否则事件重投会重复扫描且无法判断已提交范围。
- 若采用 Tracker 粒度进度，Tracker 内批次中断后重做该 Tracker：已存在 LTI 跳过、已记录失败保持终态，不重复追加明细。

### 5.5 幂等与重入

- 事件重投从 `stageProgress.processed()` 继续；已完成阶段不重跑。
- 已存在 LTI 按 `(working_set_id, common_id)` 判定并记 `Skipped`；唯一索引只作兜底，不得用 UPSERT 静默覆盖已有变体。
- 复制阶段失败的 Tracker 在 INDEX 阶段记 `Skipped`，与 T7-C 的 Copy→Bind 跳过逻辑一致。
- 同一 `CommonId` 的重复扫描结果必须稳定；不得因为分页顺序变化生成第二条 LTI。

### 5.6 索引完成事件

- 定义 SK 事件（如 `TrackerItemsIndexed`），在索引阶段完成且事务提交后发布；使用现有 Modulith 未完成事件机制。
- 字段只包含任务/工作集/已索引 Tracker 与时间；不包含 ALM 实体或内容。
- 本切片不实现关系监听、不新增关系表；T7-E 接入后若增加 `LINK_RELATIONS` 阶段，再评估任务视图兼容。
- 事件重投不得重复创建 LTI 或重复追加成功计数。

### 5.7 验收测试

至少覆盖：

1. **正常路径**：父 Tracker 下 2～3 个 Requirement，创建子 WS 后任务 `Succeeded`；子 LTI 数量、`commonId` 继承、`localId != commonId`、`revision`、`sourceLocalId/sourceRevision` 全部正确。
2. **多 Tracker**：两个非共享父 Tracker 各自索引，互不串数据。
3. **软删剔除**：`delete_flag=TRUE` 的 Requirement 不生成 LTI；任务仍成功或按实际失败计数。
4. **重合身份**：父 Tracker 为派生非共享 Tracker（先建父 WS），其已有 LTI 与实际内容来源在孙 WS 中正确延续，且同一 `CommonId` 只有一条。
5. **部分失败**：构造一条无法读取/来源缺失的 Item，任务 `PartiallySucceeded`，明细含 `INDEX_UNSHARED_TRACKERS`、`trackerId`、`localId`、`reason`，其他 Item 保留。
6. **重入幂等**：重复发布 `WorkingSetCreationSubmitted` 后 LTI 数量不变，已存在项计入 `skipped`，任务结果稳定。
7. **复制失败跳过**：父 Tracker 未选空间导致复制失败时，INDEX 项为 `Skipped`，任务按既有规则终态。
8. **分批**：用小批大小配置验证多批提交；事件重投后不重复。
9. **可见性回归**：索引运行中普通查询仍返回 `Preparing`；任务专用接口可读目标 WS；终态后可正常读取已索引 Item。

测试只通过公共 seam 与 REST/任务视图观察；事件驱动处使用 `await`；不修改既有测试期望来掩盖冲突。

### 5.8 文档

- 更新 `working-set-creation-lro-design.md` §4.2（INDEX 已实现）、§4.5（最终身份/来源/去重裁定）、§7.2（端口最终形态）、§9（分页/批大小）、§13（状态与下一步）。
- 更新 `t7-c-binding-verification.md` 中“LTI 索引由 T7-D 接入”的表述。
- 新增 `t7-d-index-verification.md`（Reviewed）：观察结果、失败原因映射、批大小/分页参数、测试项数。
- 同步 `scenarios/core-scenarios.md` SCN-075、`implementation/README.md`；如进入基线，更新 `BASELINE.md`。
- 不重开 T7-A 已确认的可见性、幂等与失败语义。

## 6. 全局约束

- SK 优先：索引机制、端口、事件属于 `sk.workingset`；ALM 只做分页身份适配，不把 ALM Guard 升格为 SK 契约。
- 不重开已关闭裁定（D1～D17、C3～C6/C11/C12、QUESTION-001/003/008/009/015）。
- 失败语义只用 T4 §5 九分类；不暴露异常文本。
- ArchUnit：`sk.workingset` 不得依赖 `alm`。
- 测试只测公共 seam；事件驱动处使用 `await`；不修改测试期望掩盖冲突。
- 未经用户明确授权，不提交、不发布、不创建 Tag。

## 7. 完成定义

- INDEX 阶段可执行，正常/部分失败/重入/软删/重合/复制失败均有测试证据；
- 身份、来源、软删、分页、批次与进度语义在文档中有明确裁定；
- 索引完成事件已定义并发布，T7-E 可直接消费；
- `mvn -B clean test` 全绿（当前 101 项 + 新增），含 ArchUnit、真实 PostgreSQL/Redis/Tomcat；
- 文档相对链接可解析，`git diff --check` 干净；
- 新增 `t7-d-index-verification.md` 并完成 `BASELINE.md`/README 登记（如适用）。

## 8. 建议的第一个动作

先读 `WorkingSetCreation`、`WorkingSetCreationService.executeItem`、`LightTrackerItem`、`RequirementCatalog` 与 `WorkingSetApplicationService.resolveContentSource`。写一个最小的“单 Tracker、3 个 Requirement”端到端测试（Red）：创建子 WS、等待任务终态、断言 LTI 身份与来源。确认测试向量后，再就 §4 的进度粒度、端口形态、软删判定与批大小向用户确认，最后实现分页与批次框架。不要先写通用批处理抽象。
