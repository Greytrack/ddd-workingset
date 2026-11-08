# Shared Kernel 工作集领域建模 — 领域模型

> V4 工作集本体已收敛至 [领域模型](./workingsetModel/01-domain-model.md)。本页保留历史设计；轻量记录、可选代理及具体业务对象的最新边界见 [§5.1](./workingsetModel/01-domain-model.md#light-record-and-proxy)。

> **文档版本**: v2.21
> **最后更新**: 2026-10-27
> **模块归属**: 实例身份能力属于共享内核基础协议；工作集规则属于 `sk-workingset`，协作模块为 `sk-tracker-catalog`、`sk-tracker-item`、`sk-item-revision`、`sk-snapshot`

通用统一语言见 [08-UL.md](./08-UL.md)，引用与比对合并专项见 [09 UL](./09-merge-domain-design.md#merge-ul)。本文负责领域概念、行为、不变量与公开契约；类图见 02，用例见 03，流程见 04，存储与执行机制见 05，不维护另一套实现方案。

## 1. 领域原始类型

下表描述标识的语义角色；按 AirEdge 当前规范，Java 主键保持 `Long`，通过具名参数、定位器和上下文防止混传。图中的 TrackerId、CommonId 等是语义名称，不要求建立独立 Java ID 包装类。

| 类型 | 含义 | 约束 |
|------|------|------|
| `ProjectId` | 项目标识 | — |
| `ProductSpaceId` | 产品空间标识 | — |
| `WorkingSetId` | 工作集标识 | 默认性由范围内根工作集规则判定，不传播裸 `ID=1` |
| `TrackerId` | Tracker 聚合标识 | 同时标识 Tracker 定义和数据版本线；系统不再定义 BranchId |
| `InstanceId` | 实例标识的统一值域 | CommonId 与 LocalId 都使用该值域，允许按值判断原生/派生 |
| `CommonId` | InstanceId 的血缘根角色 | 每条血缘全局唯一、派生时保持不变、永不复用 |
| `LocalId` | InstanceId 的当前实例角色 | 全局唯一、实例创建后不可变、不得用于跨 Tracker 稳定引用 |
| `RevisionId` | 不可变修订标识 | 对 Revisionable 对象有效 |
| `SourceRevision` | 内容来源版次号 | 正整数；非版次控制对象固定为 1 |
| `ContentVersion` | 业务内容的并发版本，与 sourceRevision 区分 | 非版次对象不能用恒为 1 的 sourceRevision 检测变化；单纯实例化不等于业务内容变化 |
| `SnapshotId` | 可复用快照标识 | 创建输入可使用 HEAD 或已有 SnapshotId；HEAD 必须先解析成确定的 capturedAt |
| `FieldId` | 动态字段标识 | 字段级 Diff/Merge 粒度 |

## 2. 值对象与枚举

| 类型 | 字段/取值 | 语义 |
|------|-----------|------|
| `InstanceIdentity` | commonId, localId | 全局实例身份；原生实例两者相等，派生实例两者不同 |
| `TrackerSharingMode` | `SHARED`、`UNSHARED` | Tracker 创建时确定，创建后永久不可变 |
| `TrackerSnapshot` | trackerId, capturedAt | Tracker 在该时间戳可见的 Item 集合；派生后不可修改 |
| `TrackerItemLocator` | workingSetId, commonId | 面向公开用例的上下文定位器；先解析 Binding，再得到当前 Tracker，不暴露 LightTrackerItem |
| `SourceReadRequest` | sourceLocalId, sourceRevision, capturedAt | 类型 SPI 的来源读取请求；capturedAt 由 SK 解析有效内容切点，非版次历史读取不能只用 revision=1 |
| `ItemSourceRef` | sourceLocalId, sourceRevision | 当前内容的精确来源；VIRTUAL 指向来源实例版次，INSTANTIATED 指向自身本地版次 |
| `InstantiationClosure` | rootLocalId, descendantLocalIds | 同一 CommonId 下，位于 root 所属 Tracker 派生子树中的实例集合 |
| `InstantiationState` | `VIRTUAL`、`INSTANTIATED` | 无本地内容、已有本地内容；delete2 后索引本身被删除，不产生第三种状态 |
| `DeletionState` | `ACTIVE`、`DELETED` | BusinessObject 的未删除或回收站状态；delete2 成功后对象及其状态记录均不存在 |
| `LineageMutationLease` | commonId, owner, expiresAt, fencingToken, lineageVersion | 串行化同一 CommonId 的索引派生、闭包实例化与业务变更 |

引用与比对合并值对象、枚举及租约见 [09 专项 UL](./09-merge-domain-design.md#merge-ul)。

## 3. 聚合边界

### 3.1 Tracker（sk-tracker-catalog）

`Tracker` 是业务对象容器、结构定义和数据版本线，也是该能力的聚合根。原先 TrackerBranch 的职责并入 Tracker。

状态：`TrackerId`、可选 `ParentTrackerId`、可选 `SourceTrackerId`、分类/字段模式、工作流绑定（工作流定义由 `sk-workflow` 提供，此处仅保存绑定引用）、`TrackerSharingMode`、可选 `TrackerSnapshot`、状态和 ApplyLease。

行为：`create()`、`derive()`、`isDerived()`、`acquireApplyLease()`、`releaseLease()`、`archive()`。

不变量：

1. 创建命令必须显式给出 `TrackerSharingMode`；
2. sharingMode 创建后永久不可变，不提供 setter、update 命令或状态转换；
3. 配置错误只能创建新的 Tracker，并通过独立迁移用例迁移数据；旧 Tracker 的身份和 mode 不变化；
4. `ParentTrackerId` 只表达业务父子层级；`SourceTrackerId` 只表达派生来源，两种关系分别校验无环；
5. `isDerived()` 等价于 `sourceTrackerId != null`，不持久化第二份布尔事实；
6. 派生 Tracker 必须继承来源 Tracker 的 sharingMode，且只有 UNSHARED Tracker 可以因工作集隔离而派生；
7. SHARED Tracker 可被多个 WorkingSet Binding 引用；UNSHARED 派生 Tracker 只能有一个有效 Binding；
8. 派生 Tracker 必须记录不可变 SourceTrackerId 和 TrackerSnapshot；HEAD 在用户点击创建的请求受理事务中以数据库时钟记录 capturedAt；
9. 捕获历史快照必须支持按 capturedAt 回读集合、内容与删除状态；多版次对象读取当时已提交的 Revision，非版次对象仍用 sourceRevision=1，但必须有时态读取或已冻结内容。仅有可覆盖的当前行不足以支持历史快照与异步派生；
10. 比对与合并边界及执行保护见 [09 比对合并不变量](./09-merge-domain-design.md#merge-rules) 与 [09 执行协议](./09-merge-domain-design.md#merge-execution)。

`TrackerCreationService` 创建原始 Tracker 后，将它直接绑定到默认 WorkingSet。后续工作集对 SHARED Tracker 复用该 Tracker；对 UNSHARED Tracker 创建新的派生 Tracker。

### 3.2 WorkingSet

状态：`WorkingSetId`、项目/产品空间、名称、父工作集、生命周期、`WorkingSetTrackerBinding` 集合。

行为：`createChild()`、`bind()`、`extend()`、`hideBinding()`、`rename()`、`reparent()`、`delete()`。

不变量：

1. 名称在项目范围内唯一；
2. 父工作集必须属于同一项目，且不能形成环；
3. 默认根工作集不可删除；
4. `(workingSetId, trackerId)` 至多一个有效 Binding；同一工作集在一个 SourceTrackerId 派生族中只能有一个有效 Binding；
5. Binding 不携带共享策略，也不能覆盖 Tracker 的 sharingMode；
6. SHARED Tracker 可以被多个 WorkingSet Binding 复用；
7. UNSHARED 派生 Tracker 只能被创建它的工作集有效绑定一次；
8. Binding 可隐藏，不物理移除其历史。

### 3.3 LightTrackerItem

Tracker 中每个条目的内部轻量表示；VIRTUAL 与 INSTANTIATED 状态都保留它，直至 delete2。它是独立维护的**索引实体（Entity）**——一致性由血缘写保护（lineage lease）与数据库约束保障，不构成聚合边界——不继承 TrackerItem，也不作为外部业务对象返回。Tracker 只拥有逻辑归属规则，不整体加载全部轻量条目。

用例定位：`TrackerItemLocator(workingSetId, commonId)`。应用服务解析当前 WorkingSet 的 Binding 后，得到 TrackerId，再以 LightTrackerItem 固有的 `(trackerId, commonId)` 唯一约束定位变体。

状态：必填 `trackerId`、`commonId`、`localId`、`itemType`、`ItemSourceRef`、`InstantiationState`、`DeletionState` 与乐观锁版本。原生和派生变体都必须具有精确 ItemSourceRef。

行为：`provisionOriginal()`、`provisionVirtual()`、`resolveContentRef()`、`markInstantiated()`、`refreshLocalRevision()`、`markDeleted()`、`markRestored()`、`synchronizeSaved()`、`requireDelete2Allowed()`。实例化、delete/undelete 和 delete2 的 I/O 与事务由 Management/命令协调器编排；状态校验留在轻量条目与具体业务对象中。

不变量：

1. 每个 `(trackerId, commonId)` 至多一个变体索引，且每个 LocalId 全局唯一；SHARED Tracker 被多个 WorkingSet 绑定时复用同一索引行；
2. 原生 Item 创建时一次分配身份值，并同时作为 CommonId 与 LocalId；其 sourceLocalId 指向自身，sourceRevision 指向当前本地版次；
3. 派生 LightTrackerItem 保持 CommonId、分配新 LocalId；VIRTUAL 的 sourceLocalId/sourceRevision 精确指向 TrackerSnapshot 时刻选中的来源实例版次；
4. 非版次控制对象 sourceRevision 固定为 1，历史内容切点由快照读取上下文保持；没有历史/冻结能力不能保证旧时刻读取。Revisionable 对象保存实际来源版次；
5. 任一实例执行 update/delete 前，必须按 CommonId 与 SourceTrackerId 派生关系构造以自身 Tracker 为根的 InstantiationClosure，并按来源到派生顺序实例化其中全部 VIRTUAL 变体；
6. 实例化严格按 sourceLocalId/sourceRevision 复制物理行，使用索引既有 LocalId，保留 CommonId，并重写 TrackerId、ProductSpaceId 及对象特有归属字段；
7. 实例化完成时 sourceLocalId 必须重置为自身 LocalId，sourceRevision 必须重置为生成的本地版次；后续 check-in 产生新本地版次时同步更新 sourceRevision，非版次对象始终为 1；
8. 单个变体的内容写入、来源指针重置、`VIRTUAL → INSTANTIATED` 和 Outbox 事件同事务；闭包全部完成前禁止提交原始变更；
9. `delete()` 在闭包实例化完成后执行 `ACTIVE → DELETED`，只软删除当前实例，后代保留实例化出的独立内容；
10. `undelete()` 只允许 `INSTANTIATED + DELETED → INSTANTIATED + ACTIVE`，并保留原身份与归属；
11. `delete2()` 只允许从 INSTANTIATED + DELETED 执行；成功时删除实体、LightTrackerItem 索引、版次以及所有以该对象为端点的领域关系记录；
12. delete2 完成后对象在领域模型中不再存在，不能 undelete；CommonId/LocalId 即使已无留存记录也不得重新分配。

原生实例可创建在默认工作集或子工作集中，不能由工作集层级判断原生/派生。LocalId 在身份索引创建时分配，不等同于业务物理内容行是否已经存在；CommonId 标识实例族，同族变体沿用同值。

### 3.4 MergeRound 与记录比对合并

详细内容已迁入 [09 比对合并模型、属性与方法](./09-merge-domain-design.md#merge-model)，此处仅保留跳转入口。

规则见 [09 比对、策略与不变量](./09-merge-domain-design.md#merge-rules)；执行见 [09 执行协议](./09-merge-domain-design.md#merge-execution)。

### 3.5 相邻聚合

- `Snapshot`：以 TrackerId 与 capturedAt 定义该时刻的 Item 集合；HEAD 只是在用户操作时创建即时 Snapshot 的命令语义。
- `RevisionItem`：不可变内容修订；签出草稿和签入由对象自身服务维护。
- `WorkingSetProvisioning`：Create/Extend/Replace 的可恢复流程管理器，记录步骤、进度和幂等状态。

## 4. 能力协议

| 接口 | 责任 | 说明 |
|------|------|------|
| `InstanceIdentifiable` | commonId、localId、isOriginal/isDerived | 所有业务实例共有；sourceLocalId/sourceRevision 属于变体索引，不属于全局实例身份能力 |
| `BusinessObject` | 实例身份、业务内容读取、业务行为与删除生命周期 | 所有业务对象底座，组合 `InstanceIdentifiable` 与 `Deletable` |
| `Deletable` | getDeletionState/delete/undelete/delete2 | 实体负责 ACTIVE/DELETED 生命周期校验；受管对象由 SK 准备 COW，普通对象直接执行；delete2 的存储清理由协调器完成 |
| `TrackerItem` | `BusinessObject + getTrackerId() + getTrackerItemType() + isInstantiated()` | Shared Kernel 的公开稳定能力；受 Tracker 管理的具体业务对象直接实现，SK 不依赖具体业务类型 |
| `TrackerItemInstantiator` | instantiate(locator, type)/instantiateAll(workingsetId, commonIds, type) | SK 定义并实现的公开协调能力；准备目标及派生子树闭包，直接返回请求目标 T/List<T>，不是 BC 类型 SPI |
| `TrackerItemContentPort<T>` | loadLocalBatch/loadSourceBatch/resolve/instantiateLocal/loadSavedStateBatch/save/deletePhysical | BC 内容适配端口；物理复制只接受已确定来源与目标，批量返回内部版次摘要，不决定实例化闭包 |
| `TrackerItemCapability` | DERIVE/INSTANTIATE/DIFF/MERGE | 类型注册声明；DERIVE/INSTANTIATE 是受管类型的接入条件，DIFF/MERGE 需相应策略，不能仅凭 implements TrackerItem 推断 |
| `Revisionable` | checkOut/checkIn/history | 可选能力，不并入 TrackerItem |
| `Routeable` | 状态流转 | 可选能力，属 `sk-workflow`：状态机语义由工作流团队定义，本规格仅声明能力接口与探测点（不定义 Workflow/State/Transition） |

业务对象使用接口 + 组合，不强制继承 SK 骨架类。所有 BusinessObject 均提供 delete/undelete/delete2。`isOriginal` 等价于 `commonId == localId`，`isDerived` 等价于 `commonId != localId`，二者均为派生判断而非可变状态。Requirement、Bug 等受 Tracker 管理的对象可以直接实现 `TrackerItem`，并建议组合 `TrackerItemIdentity` 承载共享身份。`LightTrackerItem` 不是 TrackerItem 的父类或子类；它是工作集内部对变体身份、来源和实例化状态的管理模型。

### 4.1 对外读写契约

公开查询返回 `TrackerItem`、`List<TrackerItem>` 或 `T/List<T>`（T 实现 TrackerItem），不再建立额外的有效条目结果类型。VIRTUAL 读取只组装内存对象，不改变存储状态；来源只提供内容，身份和项目/产品空间归属来自目标上下文。

公开命令以 TrackerItemLocator 或一组 CommonId 接受业务行为，由 SK 自动实例化闭包、重新加载目标对象、调用业务方法，并同事务保存内容、当前本地版次、DeletionState 与 LightTrackerItem。业务代码无需判断实例化状态；查询对象的改动也不会自动写回。完整自动脏检测 Unit of Work 属于可选后续实现，不是当前 demo 的既有承诺。

批量查询先批量解析 Binding/LightTrackerItem，再按 itemType 与来源状态分组加载，按请求顺序返回。`resolveAll` 是严格集合契约：缺失、不可见或类型不匹配则失败，不悄悄返回不完整列表；搜索/分页另设明确的可见集合查询。批量写入去重目标，按固定顺序获取 CommonId 写保护，同一 CommonId 的闭包不可任意切断。

### 4.2 显式实例化能力

> 本节是**唯一权威定义**；其他文档只做链接引用，不重复正文。

TrackerItem 不再声明实例化方法，也不持有服务或 LightTrackerItem 引用。公开 TrackerItemInstantiator 由 SK 定义并实现，以 TrackerItemLocator 或工作集中的 CommonId 集合接受请求；返回 TrackerItem/T 或 List<T>，不引入独立结果包装。

显式实例化准备目标节点及同一 CommonId 下位于其 Tracker 派生子树中的全部 VIRTUAL 条目；已实例化节点不重复复制，但不能因此跳过后代规划。来源祖先只读取和保护，不无条件实例化整族。批量请求合并重叠闭包，固定顺序获取写保护，返回请求目标并保留请求顺序和重复项，不暴露内部闭包对象。只有完整闭包准备成功才返回；重复调用幂等，失败按有界事务回滚，长闭包遵循 [05](./05-implementation-design.md) 的恢复协议。

TrackerItem.isInstantiated() 是加载时的只读事实：VIRTUAL 来源重建的对象为 false，本地物理对象为 true。权威状态来自 LightTrackerItem，不增加第二份持久化字段，不提供 setter；旧对象标记可能过时，不能据此绕过实例化、并发或写命令校验。普通业务流程无需判断它。

BC 通过 TrackerItemContentPort.instantiateLocal(target, source) 实现类型特有的物理复制；该方法不决定时机、范围、快照或锁。SK 在同一受保护事务内批量取得实际本地版次/删除摘要，并调用 LightTrackerItem.markInstantiated(localRevision) 重置来源。版次信息不进入公开 TrackerItem，非版次对象仍为 1。

实例化不执行业务编辑，不保存 detached 对象的未提交内容；update/delete 继续自动准备同一派生闭包，无须显式实例化。之后的写入仍重新校验与规划，写保护保持到 Management 提交或回滚。DIFF/MERGE 使用独立策略，不把 Instantiator 扩充为通用业务服务，也不预设可覆盖的默认算法。

### 4.3 删除生命周期与清理边界

所有 BusinessObject 都有以下生命周期；下面的实例化前置仅适用于实现 TrackerItem 的对象，普通 Part 不进入工作集协议：

- `delete()`：先执行 `LineageInstantiationService.ensureReady(scope)` 实例化当前节点及全部后代，再通过通用 BusinessObject API 对当前真实实例执行 `ACTIVE → DELETED`，使其进入回收站；后代保持 ACTIVE，且已拥有独立内容；
- `undelete()`：只允许 `INSTANTIATED + DELETED → INSTANTIATED + ACTIVE`，通过通用 BusinessObject API 从回收站恢复；
- `delete2()`：只允许处理 `INSTANTIATED + DELETED` 的回收站对象；成功时彻底删除业务实体、LightTrackerItem 索引、全部版次，以及所有以该对象为端点的领域关系记录，不写入任何删除占位状态，成功后不能 undelete。

Delete2Specification 必须确认对象已在回收站、已经实例化，并且有效 Snapshot、法定留存及关联删除规则允许彻底删除。派生后代不是 delete2 的阻塞条件，因为此前的 delete 已保证其物理行独立。删除计划应在同一全局用例中枚举并清除实体、索引、版次和关系；执行完成后任何查询都只能得到不存在，且 CommonId/LocalId 仍不得重新分配。

实体负责生命周期校验；实际清理通过统一生命周期协调器执行，具体对象 Repository 不对业务调用方暴露绕过该用例的物理删除入口。删除事务步骤见 [05 §5](./05-implementation-design.md)，调用时序见 [04 SQ5](./04-sequence-diagrams.md)。

### 4.4 规范引用语义

详细内容已迁入 [09 规范引用、解析与清理](./09-merge-domain-design.md#reference-design)，此处仅保留跳转入口。

## 5. 领域服务

| 服务 | 责任 |
|------|------|
| `TrackerBindingService` | 读取不可变 TrackerSharingMode；SHARED 复用 Tracker，UNSHARED 创建带 SourceTrackerId/TrackerSnapshot 的派生 Tracker；写 Binding |
| `TrackerCreationService` | 创建原始 Tracker、固化 sharingMode，并写入默认 WorkingSet 的首个 Binding |
| `TrackerItemTypeRegistry` | 按 itemType 装配 BC 的 ContentPort；类型、输出类和 capabilities 属于注册元数据，Shared Kernel 不依赖 Requirement、Bug 等实现类 |
| `TrackerItemResolver` | 批量解析 Binding 与 LightTrackerItem，按类型调用 ContentPort，返回目标上下文中的 TrackerItem/T；不触发实例化 |
| `LightTrackerItemProvisioningService` | 派生 Tracker 创建时批量生成索引，保留 CommonId、分配 LocalId、记录来源 LocalId 与版次，不复制内容 |
| `LineageInstantiationService` | 按 CommonId 与 SourceTrackerId 派生图构造闭包，以 Tracker 拓扑顺序复制物理行、校正归属并重置来源指针；提交前校验 lineageVersion |
| `TrackerItemMutationService` | 在实例化闭包完成后调用统一 update/delete API；以 lineage lease/fencing 防止同血缘并发写穿透 |
| `BusinessObjectDeletionService` | 统一删除生命周期的清理协调；受管 delete/undelete 由 TrackerItemMutationService 执行，delete2 协调实体、索引、版次和具体关系清理；普通对象由其 BC 按同一生命周期执行 |
| `Delete2Specification` | 校验对象已在回收站且已实例化，并确认快照历史、法定留存和关联删除规则允许彻底删除 |
| `WorkingSetDeleteService` | 终止 Binding 但保留历史，只清理无有效引用的 UNSHARED 派生 Tracker；共享 Tracker 保留 |

引用与比对合并领域服务见 [09 引用服务](./09-merge-domain-design.md#reference-design) / [09 合并执行与服务](./09-merge-domain-design.md#merge-execution)。

## 6. 仓储与查询

写侧仓储：`TrackerRepository`、`WorkingSetRepository`、`LightTrackerItemRepository`、`SnapshotRepository`。

查询侧：

- `WorkingSetQuery`
- `BindingQuery`
- `TrackerItemQuery.resolve(commonId, workingSetId)`
- `TrackerItemQuery.resolveAll(...)`
- `BadgeQuery`

单条与批量 TrackerItem 查询必须共享同一套 Binding、TrackerSnapshot、DeletionState、来源链和权限语义，统一经 `IWorkingSetItemService.resolve/resolveAll` 暴露，写入经 `mutate/mutateAll/delete/undelete/delete2` 暴露。

本节“领域服务”列出了能力名称。纯闭包计算、状态与合并判定属于领域规则；获取数据、调用实例化协调器/内容端口、事务和恢复流程属于应用协调。按项目规范由 biz Management 持有写事务，sdkImpl 完成跨实例编排；不要求新增平行 domain 包。

引用与合并的仓储/查询见 [09 仓储边界](./09-merge-domain-design.md#merge-execution)。

## 7. 领域事件与一致性

事件信封包含 `eventId, eventType, occurredAt, aggregateType, aggregateId, aggregateVersion, correlationId, causationId, payload`，与状态变更同事务写入 Outbox；消费者使用 Inbox/唯一键幂等处理。

核心事件：`WorkingSetCreated`、`TrackerBound`、`TrackerDerived`、`ItemInstantiated`、`BusinessObjectDeleted`、`BusinessObjectUndeleted`、`BusinessObjectPhysicallyDeleted`、`ItemCheckedIn`、`SnapshotCaptured`。

| 事实 | 一致性 |
|------|--------|
| TrackerSharingMode 不可变 | Tracker 聚合与数据库更新防线强一致 |
| 身份唯一性、来源链、Binding 唯一性、闭包实例化、DeletionState | lineage lease/fencing + 聚合事务 + 数据库约束强一致 |
| SourceTrackerId 与 TrackerSnapshot 不可变；ParentTrackerId 单独校验业务层级 | Tracker 聚合与层级校验协议强一致 |
| 实例视图、徽章、通知 | 可靠事件驱动的最终一致 |

---

合并事件、一致性见 [09 生命周期与执行协议](./09-merge-domain-design.md#merge-lifecycle)。

## 8. 用例 × 事务/一致性矩阵

> 说明实现期的**事务边界与并发保护**归属（可直接作为锁与 @Transactional 范围的依据）。
> 强一致 = 同一本地事务内完成；最终一致 = 经 Outbox/Inbox 投影（见 §7）。

| 用例 | 参与聚合/记录 | 强一致事务内容 | 并发保护 | 最终一致投影 |
|------|---------------|----------------|----------|--------------|
| 创建/扩展工作集（Provisioning） | WorkingSet、Tracker、Binding、LightTrackerItem | 受理事务：TrackerSnapshot 捕获 + 流程记录；幂等步骤事务：复用/派生 Tracker、写 Binding、批量生成 VIRTUAL 索引；**索引齐备后原子发布 READY** | 每步幂等键；索引批量参与 lineageVersion 写保护 | 进度、缓存、TrackerItem 投影 |
| TrackerItem 解析（resolve/resolveAll） | LightTrackerItem（只读）、来源/本地内容（只读） | 只读，无写事务 | 索引版本复核 + 有限重试（不返回旧身份+新内容） | — |
| 业务写入（mutate/mutateAll/update/delete） | LightTrackerItem、业务实体 T、Outbox | 单事务：lineage lease/fencing → 闭包实例化（含索引 CAS）→ 业务行为 → save + 索引同步 → Outbox；**闭包未完成不提交** | LineageMutationLease（同 CommonId 串行；批量按固定顺序取锁） | 实例视图、徽章、搜索 |
| 显式 instantiate（§4.2） | LightTrackerItem、业务实体 T、Outbox | 单事务：当前条目及依赖其旧内容的后代闭包实例化 + 索引 CAS + Outbox | lineage lease（INSTANTIATE 意图） | 同上 |
| undelete | 业务实体 T、LightTrackerItem、Outbox | 单事务：INSTANTIATED+DELETED → ACTIVE | 同上 | 列表/搜索/引用诊断 |
| delete2 | 业务实体、LightTrackerItem、版次、领域关系、Outbox | 单事务六步顺序（关系 → 版次/从属 → 实体 → 索引 → Outbox） + Delete2Specification | lineage lease | 删除投影 |
| 删除工作集 | WorkingSet、Binding、（无有效引用的派生 Tracker） | 单事务：终止 Binding + 软删 + Outbox；派生 Tracker 清理另起用例 | DeleteWorkingSetSpecification（非默认/无子集/无进行中 Provisioning/Merge） | 缓存/读模型 |
| 快照创建/查询 | TrackerSnapshot（不可变） | 一次性写入捕获事务；查询只读 | 不可变，无锁 | — |

引用、Diff/选择、Merge Apply 的专项规则与事务矩阵见 [09 事务与一致性](./09-merge-domain-design.md#merge-execution)；引用解析见 [09 规范引用](./09-merge-domain-design.md#reference-design)。
