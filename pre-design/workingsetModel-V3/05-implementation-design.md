# Shared Kernel 工作集 — 实现设计与迁移

> 本页保留旧版实现与迁移思路，不约束 V4。最新[访问与装配](./workingsetModel/02-use-cases.md#loading-and-proxy)及[一致性协议](./workingsetModel/03-consistency.md)明确：轻量来源索引是持久化事实；代理缓存和内存依赖登记均不是一致性依据。

> **文档版本**: v2.21
> **最后更新**: 2026-10-27
> **模块归属**: `sk-workingset`

本文是当前领域模型的实现视图，不是独立的“第二版设计”。统一语言见 [08-UL.md](./08-UL.md)，领域规则、公开契约与事务矩阵以 [01-domain-model.md](./01-domain-model.md) 为准，用例编排见 [04-sequence-diagrams.md](./04-sequence-diagrams.md)。这里只描述存储约束、批量算法、历史读取、并发防线、恢复和迁移；不替代领域规则或 Java SPI。

## 1. 逻辑存储模型与约束

领域约束以 [01 §3](./01-domain-model.md) 为准；本节将其映射为持久化防线，不重新定义领域语义。Tracker 更新语句排除 sharing_mode；必要时增加数据库权限或触发器防止误写。Binding 不存第二份独立 mode。

目标逻辑模型不要求一次性改名现网表，但必须满足以下职责。`light_tracker_item` 是 LightTrackerItem 的逻辑名称，现网 `air_ws_items_index` 可以继续承担其持久化，不据此直接执行表重命名。

| 逻辑存储 | 关键字段 | 关键约束 |
|----------|----------|----------|
| `tracker` | tracker_id, parent_tracker_id, source_tracker_id, sharing_mode, snapshot_at, status | tracker_id 主键；sharing_mode NOT NULL 且创建后禁止更新；两类关系分别无环 |
| `working_set` | id, project_id, product_space_id, parent_id, status | 同范围名称唯一；父子不能成环 |
| `working_set_tracker_binding` | working_set_id, tracker_id, visible, status | 每个工作集/Tracker 至多一个 ACTIVE；终止记录保留历史 |
| `light_tracker_item` | tracker_id, common_id, local_id, item_type, source_local_id, source_revision, instantiation_state, deletion_state, row_version | 内部存储键 UNIQUE(tracker_id, common_id)；UNIQUE(local_id)；INDEX(common_id, tracker_id)；INDEX(source_local_id, source_revision)；local_id/source_local_id/source_revision NOT NULL |
| `lineage_write_guard` | common_id, owner, expires_at, fencing_token, lineage_version | common_id 唯一；token 单调递增；派生与修改共用版本协议 |
| `domain_outbox` | event_id, aggregate_id, aggregate_version, payload | event_id 唯一 |

额外约束：

1. SHARED Tracker 可被多个不同 WorkingSet 绑定；UNSHARED 派生 Tracker 至多一个有效 Binding；
2. 同一 `(workingSetId, trackerId)` 组合至多一个有效 Binding；同一工作集在一个 SourceTrackerId 派生族中只能选择一个 Tracker；
3. ParentTrackerId 只表达业务父子层级，SourceTrackerId 只表达派生来源，两种关系分别不得成环；
4. 派生 Tracker 必须满足 `source_tracker_id IS NOT NULL`、`snapshot_at IS NOT NULL`，并继承来源 Tracker 的 sharing_mode；`isDerived()` 由 sourceTrackerId 计算；
5. 原生实例满足 `common_id=local_id`；处于 INSTANTIATED 状态时还必须满足 `source_local_id=local_id`；
6. 派生 VIRTUAL 实例满足 `common_id<>local_id`，且 `source_local_id + source_revision` 必须精确指向快照选中的同 CommonId 来源版次；
7. 任一 INSTANTIATED 实例满足 `source_local_id=local_id`，`source_revision` 等于当前本地版次；后续 check-in 产生新本地版次时须同步更新；
8. `source_revision>=1`；非版次控制对象固定为 1，表示该来源实例唯一的物理行；
9. Tracker 的 SourceTrackerId 来源图不得成环，LocalId 和 CommonId 均不得复用；
10. instantiation_state 只能是 VIRTUAL 或 INSTANTIATED；deletion_state 只能是 ACTIVE 或 DELETED；delete2 后索引被删除，不产生第三种状态；
11. TrackerSharingMode 的不可变性同时由领域层和持久化层守护。


> 引用与比对合并存储、现网表映射见 [09 存储与演进](./09-merge-domain-design.md#merge-storage)。

## 2. 历史快照读取与来源保留

### 2.1 捕获与历史读取

创建输入允许 `HEAD` 或 `snapshotId`：

- `HEAD` 表示用户点击创建的一瞬间；服务端必须在受理请求的短事务内用数据库时钟记录权威 capturedAt，不能信任浏览器时钟，也不能等到异步 Job 执行；
- 同一次创建命令所选的全部 Tracker 共用一个 capturedAt，避免跨 Tracker 出现不同的快照时刻；
- 已有 `snapshotId` 解析为其中各 Tracker 的 capturedAt；
- 每个 `TrackerSnapshot` 只需持久化 trackerId 与 capturedAt，Item 集合通过时态查询确定；
- 多版次 Item 按 capturedAt 解析当时已提交且不可变的 Revision，并把确定的 `sourceLocalId + sourceRevision` 写入派生身份索引；未签入草稿不能冒充快照内容；
- 非版次控制 Item 统一写入 `sourceRevision=1`，它不是内容版本号。读取旧时刻必须由来源读取请求携带确定的 capturedAt，并由时态存储解析；只有当前可变行、没有历史/冻结能力的类型不能承诺历史快照；
- 不能只保存 sourceLocalId 后在读取时动态取最新内容，也不能在查询时重新按 capturedAt 漂移解析来源版次；
- TrackerSnapshot 创建后禁止修改。

请求受理与异步索引生成之间也可能发生来源变更。Snapshot/历史保留协议必须在 Job 入队前保护 capturedAt 时刻的集合、内容及删除状态，直到派生索引建立完成；不能等到索引出现后才开始保护。若某类型只有可变当前行且没有历史/冻结能力，当前轻量异步派生契约应拒绝该类型；若以后增加受理阶段同步冻结的兼容策略，需单独明确代价和事务边界。

从 VIRTUAL 来源条目再次派生时，先解析出可读取的物理来源与确定版次，再写入子条目的 ItemSourceRef；不得把尚无物理行的父 LocalId 当作内容来源。逐跳派生关系由 SourceTrackerId 保留。

创建/扩展的受理、幂等步骤和 READY 发布由 [04 SQ1](./04-sequence-diagrams.md) 定义，本节只落实其数据保留条件。

## 3. 批量读写实现

解析语义以 [01 §4.1](./01-domain-model.md) 为准，调用过程见 [04 SQ2](./04-sequence-diagrams.md)。已完成派生 Tracker 中索引缺失表示该条目不存在，不向来源隐式回退；每次加载都校验生命周期、权限、目标身份和归属。

### 3.1 批量读取与读一致性

1. 一次解析所有 CommonId 的 Binding 和 READY Tracker，再批量读取轻量条目；
2. 按 itemType 分组，分为本地 LocalId 集合与来源读取请求集合，去重后调用 `loadLocalBatch/loadSourceBatch`；
3. 来源请求包含 sourceLocalId、sourceRevision 与需要时态读取时的 capturedAt，后者由 SK 根据快照上下文确定；
4. TrackerItemContentPort.resolve 仅组装内存对象，不改变来源对象、不分配新 ID、不调用 save；目标 LocalId 必须使用索引已经分配的值；
5. 严格 resolveAll 先验证全部目标可见且类型兼容，按请求顺序恢复重复项；缺失、权限不足或类型错误使整个查询失败。过滤式搜索/分页另行定义；
6. 获取轻量条目的版本、加载内容、复核版本属于一个读一致性协议。并发实例化或 delete2 改变来源后，丢弃旧结果并有限重试；不能返回旧身份与新内容拼接的对象；
7. 缓存键包含 TrackerId/LocalId 与内容切点/并发版本，不能只按 CommonId 缓存不同变体。

### 3.2 写入口与扩展限制

业务代码通过 `IWorkingSetItemService.mutate/mutateAll` 获得 T/List<T> 执行业务行为。SK 先完成类型校验和闭包准备，再加载实际可写对象；调用方不用检查 VIRTUAL 或主动实例化。查询返回的对象可以用于计算和内存业务行为，但其改动不自动持久化，业务 BC 的物理 Repository 不对外暴露绕过 SK 的受管对象保存入口。

当前 demo 使用显式回调命令，不实现自动脏检测 Unit of Work。若后续提供 load→修改→save 的普通 Repository 体验，必须用 SK 管理的 Unit of Work 追踪原始版本、在物理写入前保护后代并对提交执行 CAS；不能仅在 save 中无条件 upsert。

## 4. COW 执行与长闭包恢复

### 4.1 闭包计算

对 LocalId=L 的 update/delete，系统先读取 L 的 CommonId 和所在 Tracker，再按 CommonId 查询全部变体，并利用 `Tracker.sourceTrackerId` 图筛出当前 Tracker 子树：

```text
family = findAllByCommonId(commonId)
closure = variantInCurrentTracker(L) + variantsInDescendantTrackers(family)
order = topologicalSortBySourceTracker(closure) // 来源 Tracker 在前，派生 Tracker 在后
```

- 变更原生根实例时，closure 覆盖该 CommonId 的完整派生体系；
- 变更中间实例时，只覆盖该实例及其全部下游派生实例；
- 变更叶子实例时，closure 只有自身；
- INSTANTIATED 节点无需重复复制，但仍参与拓扑和一致性校验；
- 已被 delete2 的节点因索引不存在，不进入 closure，也不能再作为内容来源。

### 4.2 物理实例化与恢复

系统以 CommonId 获取 `LineageMutationLease` 和 fencingToken，然后按拓扑顺序处理 closure 中的 VIRTUAL 节点：

1. 按节点索引中的 `sourceLocalId + sourceRevision` 读取确定的来源物理行，不再动态读取来源最新版次；
2. 完整复制业务字段和必要的版本内容；
3. 保留 CommonId，使用该索引预分配的 LocalId；
4. 把 TrackerId、ProductSpaceId、组织/项目等归属字段改为目标节点的值；
5. 执行对象类型注册的复制后处理，重建本地 Revision、派生引用或其他必须本地化的数据；版次控制对象记录实际生成的本地版次，非版次控制对象使用 1；
6. 同一事务写入目标物理行，将 `sourceLocalId` 重置为自身 LocalId、`sourceRevision` 重置为本地版次，CAS `VIRTUAL → INSTANTIATED` 并追加 `ItemInstantiated` Outbox 事件。

TrackerItemContentPort.save 必须返回实际本地版次和删除状态。原始变更提交时，业务内容与 LightTrackerItem.sourceRevision/deletionState/乐观锁版本同事务同步；只保存业务行而不刷新轻量条目会破坏解析和 Diff。

实例化操作按 LocalId 幂等。闭包可以分批提交，但 lineage lease 未释放前禁止同一 CommonId 的其他变更。WorkingSet Provisioning 为既有 CommonId 插入新派生索引时也必须参与同一 lease/version 协议；原始变更提交前再次 CAS 校验 lineageVersion，防止闭包计算后又出现未实例化后代。只有整个 closure 成功且版本校验通过后，应用服务才能提交最初的 update/delete。中途失败时已经完成的实例化可以保留，重试从首个 VIRTUAL 节点继续，原始业务变更不得执行。INSTANTIATED 对象后续 check-in 产生新本地版次时，内容写入与 `sourceRevision` 更新必须处于同一事务；非版次控制对象始终保持 1。

写保护必须持续到原始业务变更事务完成，不能在 sdkImpl 方法返回时提前释放。所有实际写入复核 fencingToken/lineageVersion，提交或回滚后再释放；租约过期的执行者不得继续写入。批量命令按固定 CommonId 顺序取锁，避免互锁。

闭包分批提交是可恢复准备阶段，需由流程记录协调多次独立的 Management 事务；不能嵌套在一个外层 @Transactional 中却宣称已经分批提交。一个闭包失败时可以保留已完成实例化，但原始业务变更尚未生效。批量业务跨 CommonId 是否原子提交应由业务用例明确，小批量 demo 采用同一事务，长任务需返回逐项结果与幂等状态。

显式 TrackerItemInstantiator 使用与写前准备相同的派生闭包，直接返回请求目标，不暴露闭包对象；类型内容端口内部复制物理数据，批量读取实际本地版次后同步索引。isInstantiated() 只投影加载事实，不增加持久化列或赋予写许可。领域契约见 [01 §4.2](./01-domain-model.md)，实现见 [06 §7.4–7.5](./06-tracker-item-java-demo.md)。

## 5. delete2 清理事务

删除状态与关系范围以 [01 §4.3–4.4](./01-domain-model.md) 为准；普通 Part 不进入血缘保护，按自身 BC 的生命周期协调器执行清理。

`BusinessObjectDeletionService.delete2(localId)` 是彻底删除的唯一入口，具体对象 Repository 不得暴露绕过该用例的公开物理删除能力。对于同一数据库内的数据，按以下顺序在一个事务中执行：

1. 获取 CommonId 的 LineageMutationLease，加载 INSTANTIATED + DELETED 对象并完成 Delete2Specification；
2. 在删除前冻结事件和缓存失效所需的最小身份信息；
3. 删除所有以该对象为端点的领域关系，以及对象拥有的版次、附件和类型特有从属数据；
4. 删除业务实体物理行；
5. 最后删除 LightTrackerItem 索引，并写入用于清理外部投影的 Outbox 事件；
6. 任一步失败则整体回滚，对象继续留在回收站。

## 6. Merge Apply 执行（见 09 §6.1）

详细内容已迁入 [09 执行保护、幂等、租约、恢复与事务](./09-merge-domain-design.md#merge-execution)，此处仅保留跳转入口。

## 7. 与现网实现的映射

| 现网实现 | 目标角色 |
|----------|----------|
| `infrastructure/biz/.../workingset/rest/WorkingsetRest`、`WorkingsetManagement` | REST 接入与应用编排；Management 持有写事务 |
| `infrastructure/sdk/.../workingset`（IWorkingsetInner、DTO、enums） | 共享模型、能力契约与 inner；不要求新增平行 domain 包 |
| `infrastructure/sdkImpl/.../service/workingset` | Loader、类型注册与执行协调、Job；SK 实现 Instantiator，BC 在自身 sdkImpl 实现内容端口；不声明写事务 |
| `air_inst_workingset` | WorkingSet |
| `air_ws_workingset_branch_relation` | WorkingSetTrackerBinding 的持久化基础；移除 BranchId 和 mode 的领域含义 |
| `air_ws_tracker` 中 branch/tracker 混合字段 | 统一为 Tracker 聚合；保留 TrackerId，增加 parentTrackerId/sourceTrackerId 和不可变 sharingMode |
| `air_ws_items_index` | LightTrackerItem 身份索引；承载 commonId/localId/sourceLocalId/sourceRevision、instantiationState/deletionState；仅 VIRTUAL 没有本地内容行，delete2 时索引随对象彻底删除 |
| `air_ws_items_revision_index` | 多版次 Item 的 Tracker 修订索引 |
| `air_inst_view_ws_*` | TrackerItem Query 实现 |
| `RequestThreadHolder.WORKINGSET` | 请求级 WorkingSetContext；不得成为领域实体归属 |
| `AbstractWorkingsetJob` | WorkingSetProvisioning 执行器 |

## 8. 演进顺序

1. 给 Tracker 增加非空 sharingMode，并从现网 shared 事实一次性回填；上线后禁止更新；
2. 明确 `air_ws_workingset_branch_relation` 为 WorkingSetTrackerBinding，对有效记录增加 `(workingSetId, trackerId)` 唯一约束；
3. 合并 Tracker 与 TrackerBranch：以 TrackerId 作为唯一主键，增加 ParentTrackerId 与 SourceTrackerId，迁移后移除 BranchId；
4. 把 CommonId/LocalId 提升为 Shared Kernel 的 InstanceIdentifiable 能力；回填原生实例 `commonId=localId`，并校验全局唯一性；
5. 引入 TrackerSnapshot(trackerId, capturedAt)，确保 HEAD 在用户请求受理时捕获；为派生 Tracker 批量生成带 `sourceLocalId + sourceRevision` 的 VIRTUAL 身份索引，多版次对象固化快照选中的来源版次，非版次控制对象使用 revision=1；
6. 引入 `CommonId + SourceTrackerId` 派生查询、LineageMutationLease 和 InstantiationClosure；将 update/delete 改为闭包实例化完成后再执行，并在实例化后把来源指针重置为自身 LocalId 和本地版次；
7. 引入统一 InstantiationState、DeletionState 与 delete/undelete/delete2；InstantiationState 仅保留 VIRTUAL/INSTANTIATED，delete2 删除实体、索引、版次和关系，不保留删除占位状态；
8. 比对合并迁移见 [09 存储与演进](./09-merge-domain-design.md#merge-storage)；
9. 引用迁移见 [09 引用兼容与迁移](./09-merge-domain-design.md#reference-design)；
10. 引入 Outbox/Inbox，最后移除 afterCommit-only 缓存一致性路径。

每一步应提供数据校验、双读比对、回滚开关和契约测试；禁止在同一版本中同时改变身份、解析和合并三条主链而没有过渡期。

引用兼容协议见 [09 规范引用](./09-merge-domain-design.md#reference-design)。
