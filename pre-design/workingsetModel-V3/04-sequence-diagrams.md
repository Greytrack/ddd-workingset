# Shared Kernel 工作集领域建模 — 用例流程时序图

> V4 时序以 [用例与协作](./workingsetModel/02-use-cases.md) 为准。本页保留历史设计；加载入口决定是否组装代理，写入统一由 Management 与 TrackerItemInstantiator 协调，不由代理启动跨变体事务。

> **文档版本**: v2.21
> **最后更新**: 2026-10-27
> **模块归属**: `sharedkernal` / `sk-workingset`
> **核心前提**: `TrackerSharingMode` 在 Tracker 创建后永久不可变；以下流程只读取该属性。
>
> 说明：SQ0~SQ5 覆盖 Tracker 共享策略固化、工作集生命周期、上下文切换与变体解析、派生链实例化、字段级合并、删除校验。
> 其中 SQ3 借鉴参考图 000006（checkOut 签出）的"聚合根 + 通用能力 + 工厂"协作模式，
> SQ5 借鉴参考图 000007（删除）的"领域服务 + Specification + Repository"编排模式。

---

## SQ0 创建 Tracker 并固化共享策略

```mermaid
sequenceDiagram
    autonumber
    actor Admin as Tracker 管理员
    participant App as TrackerManagement
    participant Factory as TrackerFactory
    participant Tracker as Tracker
    participant Repo as TrackerRepository
    participant Binding as WorkingSetTrackerBindingRepository

    Admin->>App: createTracker(name, sharingMode, schema)
    App->>Factory: create(command)
    Factory->>Tracker: new Tracker(parentTrackerId?, sourceTrackerId=null, sharingMode)
    Tracker->>Tracker: validate mode is SHARED or UNSHARED
    App->>Repo: insert(Tracker)
    Repo-->>App: TrackerId
    App->>Binding: bind(defaultWorkingSetId, trackerId)
    App-->>Admin: TrackerId
    Note over App,Repo: 后续 updateTracker 不接收 sharingMode，Repository update 也不更新该列
```

**要点**：sharingMode 只在创建时写入；Tracker 直接与默认 WorkingSet 建立首个 Binding，不再创建另一类数据线对象。配置错误时创建新 Tracker 并执行独立迁移。

---

## SQ1 创建工作集（异步 Job 编排）

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 工作集管理员
    participant Rest as WorkingsetRest
    participant Mgmt as WorkingsetManagement
    participant Val as 权限校验
    participant Snapshot as TrackerSnapshotService
    participant Job as CreateWorkingsetJob（异步）
    participant WSFactory as WorkingSetFactory
    participant WSRepo as WorkingSetRepository
    participant Binding as TrackerBindingService
    participant Tracker as TrackerCatalog
    participant Light as LightTrackerItemFactory
    participant IdxRepo as LightTrackerItemRepository
    participant Notify as 通知服务

    Admin->>Rest: createWorkingset(CreateWorkingSetCommand)
    Rest->>Mgmt: createWorkingset(cmd)
    Mgmt->>Val: hasCreatePrivilege(subClassId)
    Val-->>Mgmt: ok
    Mgmt->>Mgmt: convertModelToDto（过滤父集不存在的产品空间/Tracker）
    Mgmt->>Snapshot: captureAtRequestTime(selectedTrackers)
    Snapshot-->>Mgmt: TrackerSnapshot[](trackerId, capturedAt)
    Mgmt->>Snapshot: retainReadCutsUntilProvisioningComplete(snapshots)
    Mgmt->>Job: createWorkingsetInBackground(input, snapshots)
    Job-->>Admin: 受理成功（进度 10，立即返回）
    Job->>Job: 恢复幂等检查（工作集已存在则跳过）
    Job->>WSFactory: createEmptyWorkingSet(name, color, parentId)
    WSFactory-->>Job: WorkingSet（PROVISIONING）
    Job->>WSRepo: save(WorkingSet)
    WSRepo-->>Job: WorkingSetId
    Job->>Tracker: getImmutableSharingMode(trackerId)
    alt SHARED
        Job->>Binding: bindSharedTracker(workingSetId, trackerId)
    else UNSHARED
        Job->>Binding: deriveTracker(trackerId, trackerSnapshot)
        Binding-->>Job: derivedTrackerId
        Job->>Binding: bindDerivedTracker(workingSetId, derivedTrackerId)
        Job->>Light: provisionVirtualIdentities(derivedTrackerId, trackerSnapshot)
        Light-->>Job: 批量轻量条目（确定来源、目标身份）
        Job->>IdxRepo: batchInsert（参与 lineageVersion 写保护）
    end
    Job->>WSRepo: 完整索引就绪后原子发布 READY
    Job->>Snapshot: releaseProvisioningPins（保留有效快照所需历史）
    Job->>Notify: 完成通知（进度 100，失败告警）
```

**要点**：HEAD 表示用户点击创建的一瞬间，服务端必须在异步 Job 入队前记录 capturedAt。非共享 Tracker 根据 TrackerSnapshot 批量生成轻量索引：保留 CommonId、分配新 LocalId，并以 sourceLocalId/sourceRevision 精确指向来源内容；非版次对象 sourceRevision=1。

---

### 创建/扩展的恢复与发布协议

`WorkingSetProvisioning` 记录可恢复步骤：

```text
AcceptCreateWorkingSet
→ ResolveSelectedTrackers
→ CaptureTrackerSnapshotsAtRequestTime
→ RetainReadCuts
→ EnqueueProvisioning
→ ForEach Tracker:
    ReadImmutableSharingMode
    → SHARED: ReuseSourceTracker → CreateBinding（复用已有条目，不生成派生索引）
    → UNSHARED: ResolveParentBinding → CreateDerivedTracker(sourceTrackerId, trackerSnapshot)
        → CreateBinding
        → BatchProvisionLightTrackerItems(commonId, newLocalId, sourceLocalId, sourceRevision, VIRTUAL)
→ PublishReadyAtomically
→ Complete
```

每一步具有稳定幂等键。重试时已经完成的步骤跳过，不重新创建 Tracker、Binding 或索引。WorkingSet/派生 Tracker 在索引尚未齐备时保持 PROVISIONING，正常查询只使用 READY 的 Binding 和 Tracker；完成与发布必须原子切换，不能把部分生成的集合当成完整工作集。

快照 cut 的时态读取、保留与来源扁平化见 [05 §2](./05-implementation-design.md)。

---

## SQ2 单条与批量强类型业务查询

```mermaid
sequenceDiagram
    autonumber
    actor BC as 业务调用方
    participant API as IWorkingSetItemService
    participant Resolver as TrackerItemResolver
    participant Index as LightTrackerItemRepository
    participant Registry as TrackerItemTypeRegistry
    participant Content as TrackerItemContentPort（具体 BC）

    BC->>API: resolveAll(workingSetId, commonIds, Requirement.class)
    API->>Resolver: resolveAll(query)
    Resolver->>Index: 批量解析 Binding、READY Tracker、权限与轻量条目
    Index-->>Resolver: 有序 LightTrackerItem 集合
    Resolver->>Resolver: 验证完整集合与类型，按 itemType 分组
    loop 每个类型组
        Resolver->>Registry: require(itemType, expectedClass)
        Registry-->>Resolver: 对应内容端口
        Resolver->>Content: loadLocalBatch(instantiatedLocalIds)
        Content-->>Resolver: 本地业务对象
        Resolver->>Content: loadSourceBatch(virtualSourceReadRequests)
        Content-->>Resolver: 确定来源内容
        Resolver->>Content: resolve(targetIdentityAndOwnership, sourceSnapshot)
        Content-->>Resolver: 目标上下文中的业务对象（不保存）
    end
    Resolver->>Index: 复核轻量索引版本（变化则有限重试）
    Resolver-->>API: List<Requirement>（请求顺序）
    API-->>BC: List<Requirement>
```

**要点**：业务方使用同一种业务类型处理两类存储状态。SK 内部按目标身份组装 VIRTUAL 内容，查询不创建物理行；普通查询不返回回收站对象。工作集上下文显式传入 API，不依赖 ThreadLocal 决定实体归属。单条 resolve 复用批量语义。

---

## SQ3 业务命令自动准备当前节点及全部派生后代

```mermaid
sequenceDiagram
    autonumber
    actor BC as 业务调用方
    participant App as biz Management
    participant Mutation as TrackerItemMutationService
    participant Guard as LineageWriteGuard
    participant Svc as LineageInstantiationService
    participant Planner as InstantiationPlanner
    participant Source as SourceSnapshotLoader
    participant Content as TrackerItemContentPort（具体 BC）
    participant Index as LightTrackerItemRepository

    BC->>App: 批量修改业务对象
    App->>App: 开始有界写事务
    App->>Mutation: executeAll(workingSetId, commonIds, type, businessBehavior)
    Mutation->>Guard: acquireAndEnlist（校验权限/类型，固定顺序取锁）
    Guard-->>Mutation: MutationScope（释放绑定事务完成）
    Mutation->>Svc: ensureReady(scope)
    Svc->>Planner: plan(scope 中的 CommonId + Tracker 派生图)
    Planner-->>Svc: InstantiationClosure（当前节点与后代拓扑序）
    Svc->>Source: 批量读取全部 VIRTUAL 条目的确定来源
    Source-->>Svc: 按目标 LocalId 对齐的来源快照
    loop 每个 VIRTUAL 轻量条目
        Svc->>Content: instantiateLocal(targetIdentityAndOwnership, sourceSnapshot)
        Content-->>Svc: TrackerItem（已创建本地内容）
    end
    Svc->>Content: 按类型批量 loadSavedStateBatch(createdLocalIds)
    Content-->>Svc: 实际本地版次与删除摘要
    Svc->>Index: 同事务批量同步状态/来源 CAS + ItemInstantiated Outbox
    Svc-->>Mutation: 闭包准备完成
    Mutation->>Index: 显式重载目标索引版本
    Mutation->>Content: loadLocalBatch(targetLocalIds)
    Content-->>Mutation: List<T>
    Mutation->>Mutation: 调用业务回调（实体 revise/delete 等）
    Mutation->>Content: save(item)
    Content-->>Mutation: SavedTrackerItem（本地版次 + 删除状态）
    Mutation->>Index: 同事务同步版次/DeletionState/CAS + Outbox
    Mutation-->>App: 业务行为执行完成
    App->>App: 提交事务（实际写入复核 fencing/lineageVersion）
    App->>Guard: 事务完成后释放写保护
    App-->>BC: 业务操作成功
```

**要点**：业务代码只操作 T/List<T>。CommonId 与 SourceTrackerId 决定闭包，sourceLocalId 实例化后指向自身，不用于遍历派生树。单次本地内容/索引迁移保持原子一致；原业务变更只有在整个闭包准备完成后才能提交。失败回滚走同一事务完成释放路径。undelete 由单独 RESTORE 意图校验并加载回收站对象，delete2 由后续专用清理用例执行。

---

<a id="merge-main-sequence"></a>

## SQ4 分类记录比对、数据应用与处理确认

详细内容已迁入 [09 完整主时序](./09-merge-domain-design.md#merge-main-sequence)，此处仅保留跳转入口。

## SQ5 删除工作集（Specification 校验）

> 借鉴参考图 000007 的删除编排：应用服务 → 领域服务（跨聚合编排）→ Specification → Repository 逻辑删除。

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 工作集管理员
    participant Rest as WorkingsetRest
    participant Mgmt as WorkingsetManagement
    participant Svc as WorkingSetDeleteService（命令协调器）
    participant Spec as DeleteWorkingSetSpecification
    participant Repo as WorkingSetRepository
    participant Binding as WorkingSetTrackerBindingRepository
    participant Outbox as DomainEventOutbox
    participant WS as WorkingSet

    Admin->>Rest: deleteWorkingset(workingsetId)
    Rest->>Mgmt: deleteWorkingset(DeleteWorkingSetCommand)
    Mgmt->>Mgmt: 权限校验 + 事务开始
    Mgmt->>Svc: delete(workingSetId)
    Svc->>Repo: findById(workingSetId)
    Repo-->>Svc: WorkingSet
    Svc->>Spec: isSatisfiedBy(workingSet)
    Spec->>Spec: 非默认 / 无子集 / 无进行中 Provisioning 或 Merge
    Spec-->>Svc: true（否则抛 AppException 拒绝删除）
    Svc->>Binding: deactivateBindingsAndFindUnreferencedDerivedTrackers()
    Svc->>WS: delete()
    Svc->>Repo: save(workingSet)
    Repo-->>Svc: ok
    Svc->>Outbox: append(WorkingSetDeleted)
    Mgmt-->>Admin: 删除成功
```

**要点**：删除是跨聚合操作，由 Management/命令协调器编排，Specification 负责领域判定；共享 Tracker 可能仍被其他 Binding 引用，不得级联删除。只有无有效 Binding、无在用派生后代及历史保留义务的 UNSHARED 派生 Tracker 才能进入后续清理；缓存与读模型经可靠事件刷新。

---



## SQ6 显式实例化目标及派生闭包

```mermaid
sequenceDiagram
    actor BC as 业务调用方
    participant App as InstantiationManagement
    participant API as TrackerItemInstantiator（SK）
    participant Guard as LineageWriteGuard
    participant Service as LineageInstantiationService
    participant Content as 内容适配端口（具体 BC）
    participant Light as LightTrackerItemRepository
    participant Loader as LocalTrackerItemLoader
    BC->>App: instantiateRequirements(workingSetId, commonIds)
    Note over App: 开始写事务
    App->>API: instantiateAll(workingSetId, commonIds, Requirement.class)
    API->>Guard: acquireAndEnlist(INSTANTIATE，去重、固定锁顺序)
    Guard-->>API: MutationScope
    API->>Service: ensureReady(scope)
    Service->>Service: 规划目标及派生子树，批量读取固定来源
    loop 闭包中的 VIRTUAL 节点（拓扑顺序）
        Service->>Content: instantiateLocal(target, captured source)
        Content-->>Service: TrackerItem（本地内容已创建）
    end
    Service->>Content: 按类型批量读取实际版次/删除摘要
    Content-->>Service: Map LocalId → SavedTrackerItem
    Service->>Light: 同事务 markInstantiated + CAS + Outbox
    Note over Service: 已实例化节点不重复复制，但仍检查后代
    Service-->>API: 闭包准备完成
    API->>Light: 重载请求目标索引版本
    API->>Loader: 批量重载请求目标并校验身份/标记
    Loader-->>API: T/List T（isInstantiated=true）
    API-->>App: 请求顺序的 List Requirement（不返回闭包）
    App->>App: 提交事务
    App->>Guard: 事务完成后释放保护
    App-->>BC: List Requirement
```

单条入口复用批量语义。请求不传 detached 对象，不保存业务编辑；旧查询对象的 isInstantiated() 不会自动刷新。来源祖先只读取与保护，后续 update/delete 仍重新规划闭包，不能把“已实例化”当作写许可。公开协调由 SK 实现，类型物理复制由 BC 内容端口实现。
