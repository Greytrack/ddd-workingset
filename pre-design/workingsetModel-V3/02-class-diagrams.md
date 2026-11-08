# Shared Kernel 工作集领域建模 — 类图

> V4 类图以 [工作集领域模型](./workingsetModel/01-domain-model.md) 为准。本页为历史设计；[§5.1](./workingsetModel/01-domain-model.md#light-record-and-proxy) 区分 LightTrackerItem 持久化索引、可选 TrackerItem 代理和 Requirement。轻量索引不实现 TrackerItem，代理不是新的领域聚合。

> 代理具体实现统一为 **LightTrackerItemProxy**。完整代理、Requirement 工厂、CQRS 查询及命令类图见 [V4 04](./workingsetModel/04-query-command-demo.md)，含箭头与多重性说明。

> **文档版本**: v2.21
> **最后更新**: 2026-10-27
> **模块归属**: `sharedkernal` / `sk-workingset`

本文件只展示当前有效关系。Tracker 同时承担定义与数据版本线职责；不再定义 TrackerBranch 或 BranchId。

## 1. 共享内核对外边界

工作集作为基础能力对外提供时，外部业务模块将它视为一个整体。共享内核提供统一 API 面，内部再按领域职责分解，形成“对外统一、对内分解”的边界。

```mermaid
graph LR
  subgraph EXT["外部业务"]
    A["GF"]
    B["ALM"]
    C["PLM"]
  end

  subgraph WSF["工作集（对外单一共享内核）"]
    API["对外 API 面<br/>create / extend / resolve / diff / merge<br/>TrackerItem / List&lt;TrackerItem&gt;"]

    subgraph INT["内部领域实现"]
      BC1i["工作集上下文"]
      BC2i["Tracker 派生与轻量条目"]
      BC3i["引用与重写"]
      BC4i["差异与合并"]
    end

    API --> BC1i
    API --> BC2i
    API --> BC3i
    API --> BC4i
  end

  A -. 调用 .-> API
  B -. 调用 .-> API
  C -. 调用 .-> API
```

边界说明：

- 业务 BC 通过 Shared Kernel 的 TrackerItem、命令和查询契约使用工作集能力，可依赖公开的 Tracker/WorkingSet 模型，但不直接操纵 LightTrackerItem、来源指针和合并执行机制。
- 对外 API 负责鉴权、参数转换和用例编排；业务规则仍由内部聚合与领域服务执行，API 本身不承载领域状态。
- 内部四个区域是同一共享内核内的职责划分，不是四套独立对外服务；它们可以协作完成一个用例，但不能分别向外暴露另一套工作集契约。
- `create / extend` 管理工作集上下文，`resolve` 统一解析有效条目，`diff / merge` 提供非共享 Tracker 的差异与合并能力；引用解析和重写由统一 API 在内部协调。

## 2. 核心领域关系

```mermaid
classDiagram
  direction LR

  class Tracker {
    <<AggregateRoot>>
    +TrackerId id
    +TrackerId parentTrackerId
    +TrackerId sourceTrackerId
    +TrackerSharingMode sharingMode
    +isDerived()
  }

  class WorkingSet {
    <<AggregateRoot>>
    +WorkingSetId id
    +WorkingSetId parentId
  }

  class WorkingSetTrackerBinding {
    <<Entity>>
    +TrackerId trackerId
  }

  class TrackerSnapshot {
    <<ValueObject>>
    +TrackerId trackerId
    +Instant capturedAt
  }

  class LightTrackerItem {
    <<Entity>>
    +TrackerId trackerId
    +CommonId commonId
    +LocalId localId
    +String itemType
    +InstantiationState instantiationState
    +DeletionState deletionState
    +isVirtual() boolean
    +isInstantiated() boolean
    +markInstantiated(localRevision)
    +markDeleted()
    +markRestored()
  }

  class ItemSourceRef {
    <<ValueObject>>
    +LocalId sourceLocalId
    +SourceRevision sourceRevision
  }

  class TrackerItem {
    <<Shared Kernel interface>>
    +getTrackerId() TrackerId
    +getCommonId() CommonId
    +getLocalId() LocalId
    +getTrackerItemType() String
    +isInstantiated() boolean
  }

  WorkingSet "1" *-- "0..*" WorkingSetTrackerBinding : 包含
  WorkingSetTrackerBinding --> "1" Tracker : 绑定
  Tracker "0..*" --> "0..1" Tracker : parentTrackerId
  Tracker "0..*" --> "0..1" Tracker : sourceTrackerId
  Tracker *-- "0..1" TrackerSnapshot : 固化来源时刻
  Tracker "1" --> "0..*" LightTrackerItem : 管理轻量条目
  LightTrackerItem *-- "1" ItemSourceRef : 内容来源
  LightTrackerItem --> "0..1" TrackerItem : 本地物理实体
```

关系约束：

- `TrackerSharingMode` 只属于 Tracker，创建后不可变；Binding 不重复保存该属性。
- `parentTrackerId` 表达业务父子层级，`sourceTrackerId` 表达派生来源；`isDerived()` 等价于 `sourceTrackerId != null`。
- SHARED 模式下多个 Binding 指向同一个 Tracker；UNSHARED 模式下每个子工作集绑定一个新的派生 Tracker。
- `LightTrackerItem` 是 Tracker 下每个条目的轻量化工作集表示，不论其是否已实例化都存在；`InstantiationState` 描述它是否已有本地物理 TrackerItem。
- `trackerId + commonId` 是 LightTrackerItem 的内部唯一定位约束；`commonId + localId` 是实例身份，不再引入额外的组合键值对象。
- `LightTrackerItem` 与其本地物理 TrackerItem 的 trackerId、commonId、localId、itemType 必须一致；VIRTUAL 时没有本地物理实体，INSTANTIATED 时存在本地 TrackerItem。
- 原生变体的来源指向自身；派生 VIRTUAL 变体的来源指向快照选中的实例版次。`markInstantiated()` 同时把来源重置为自身 LocalId 和本地版次。
- `markDeleted()/markRestored()` 只同步真实 TrackerItem 的删除状态；delete2 由领域服务协调并最终删除 LightTrackerItem 本身。
- `LightTrackerItem` 是 **Entity（实体）** 而非聚合根：其唯一约束（`trackerId + commonId`）与状态迁移由血缘写保护与数据库约束保障；独立维护只为避免超大聚合与全 Tracker 写竞争。
- WorkingSetId 不属于 LightTrackerItem 或 TrackerItem。公开用例以 `WorkingSetId + CommonId` 定位，经 Binding 解析出 TrackerId 后，再以 `trackerId + commonId` 查找 LightTrackerItem；SHARED Tracker 因而可以被多个 WorkingSet 复用同一条目。

## 3. 能力组合

### 3.1 业务对象能力组合

```mermaid
classDiagram
  direction TB

  class BusinessObject {
    <<interface>>
    +getBusinessData() BusinessObjectData
  }
  class InstanceIdentifiable {
    <<interface>>
    +getCommonId()
    +getLocalId()
  }
  class Deletable {
    <<interface>>
    +getDeletionState()
    +delete()
    +undelete()
    +delete2()
  }
  class TrackerItem {
    <<Shared Kernel interface>>
    +getTrackerId()
    +getTrackerItemType()
    +isInstantiated() boolean
  }
  class TrackerItemInstantiator {
    <<Shared Kernel coordination>>
    +instantiate(locator) TrackerItem
    +instantiateAll(workingSetId, commonIds, type) List~T~
  }
  class Revisionable {
    <<interface>>
    +checkOut()
    +checkIn()
  }
  class Routeable {
    <<interface>>
    +apply(transition)
  }

  class Requirement
  class Bug
  class Part
  class Task

  BusinessObject --|> InstanceIdentifiable
  BusinessObject --|> Deletable
  TrackerItem --|> BusinessObject
  TrackerItemInstantiator --> TrackerItem : 返回请求目标
  WorkingSetTrackerItemInstantiator ..|> TrackerItemInstantiator

  Requirement ..|> TrackerItem
  Requirement ..|> Revisionable
  Bug ..|> TrackerItem
  Bug ..|> Routeable
  Part ..|> BusinessObject
  Part ..|> Revisionable
  Task ..|> BusinessObject
  Task ..|> Routeable

```

`BusinessObject` 统一具有 `InstanceIdentifiable` 和 `Deletable` 能力。`TrackerItem` 是 Shared Kernel 对外统一的稳定业务契约；Requirement、Bug 通过直接实现它声明受 Tracker/WorkingSet 管理，业务方可以使用 `TrackerItem`、`List<TrackerItem>` 或具体业务类型。`LightTrackerItem` 是 SK 内部按 Tracker 管理的轻量条目表示，不是对外业务契约，也不是 TrackerItem 的子类型。`TrackerItemInstantiator` 是 SK 的统一实例化协调契约，由 SK 实现并直接返回请求目标 TrackerItem/T 或 List<T>。具体 BC 只提供类型特有的内容适配，不承担跨聚合闭包协调。`isInstantiated()` 是只读加载事实，不是可编辑业务状态或写入许可。`Revisionable` 与 `Routeable` 仍是正交可选能力。

为保持讨论视图简洁，本文件各图暂不展开内容访问端口；加载、历史读取、内存重建、保存和物理删除的契约见 [01 领域模型](./01-domain-model.md) 与 [06 Java Demo](./06-tracker-item-java-demo.md)。图中省略不意味着删除这些职责，也不由 Instantiator 承担。

### 3.2 对外统一、对内分解（能力组合图 2）

```mermaid
flowchart TB
  subgraph BC["具体业务 BC"]
    TYPES["Requirement / Bug / 其他业务对象"]
    APP["业务 Management"]
  end
  subgraph PUB["Shared Kernel · 公开稳定契约"]
    ITEM["TrackerItem<br/>身份 / 归属 / 类型 / isInstantiated()"]
    INSTANTIATOR["TrackerItemInstantiator<br/>instantiate / instantiateAll"]
    API["WorkingSet API<br/>resolve / update / delete / undelete / delete2"]
    LOCATOR["TrackerItemLocator<br/>WorkingSetId + CommonId"]
  end
  subgraph INT["Shared Kernel · 内部机制"]
    IMPL["WorkingSetTrackerItemInstantiator<br/>校验 / 闭包准备 / 目标重载"]
    CLOSURE["LineageInstantiationService<br/>当前节点及派生子树"]
    RESOLVER["TrackerItemResolver"]
    MUTATION["Mutation / Deletion Service"]
    LIGHT["LightTrackerItem<br/>权威身份、来源与状态"]
    REGISTRY["TrackerItemTypeRegistry<br/>内部类型适配"]
  end
  TYPES -. "implements" .-> ITEM
  APP --> LOCATOR
  APP --> INSTANTIATOR
  APP --> API
  INSTANTIATOR --> IMPL
  IMPL --> CLOSURE
  API --> RESOLVER
  API --> MUTATION
  MUTATION -->|"写前自动准备"| CLOSURE
  CLOSURE --> LIGHT
  CLOSURE --> REGISTRY
  RESOLVER --> LIGHT
  RESOLVER --> REGISTRY
  IMPL -->|"只返回请求目标 T / List<T>"| ITEM
  RESOLVER -->|"直接返回业务对象"| ITEM
```

边界与关系说明：

- BC 可以依赖并实现 TrackerItem；SK 不反向依赖 Requirement、Bug 等具体类型。
- TrackerItem 不再声明 instantiate，也不持有服务/LightTrackerItem 引用。isInstantiated() 为加载时只读观察，无 setter、不独立持久化，旧值可能过时，不能据此跳过受管写入口。
- TrackerItemInstantiator 是 SK 公开协调能力，不是 BC 类型 SPI；接收工作集定位上下文，准备目标及其 Tracker 派生子树中的 VIRTUAL 条目，返回请求目标而非闭包。
- 已实例化根仍需检查未实例化后代；来源祖先只读取并保护，不自动复制整族。
- 显式实例化不保存 detached 编辑；update/delete 无须调用方先实例化，仍自动准备同一闭包并重新验证并发状态。
- 具体 BC 的类型复制、历史读取和存储由内部内容适配承担；本文件各图暂不展开内容端口，不将其职责混入公开 Instantiator。
- LightTrackerItem 与 TrackerItem 组合而非继承：前者管理权威来源/状态，后者为完整业务对象契约。VIRTUAL 查询组装对象的标记为 false，本地物理对象加载为 true。
- Tracker 管理条目不意味着聚合必须加载全部条目；索引独立维护，跨条目写保护与原子持久化仍由 SK 协调。

## 4. TrackerItem 解析关系

```mermaid
flowchart TD
    INPUT[WorkingSetId + CommonId] --> FAMILY[确定 Item 所属 Tracker 派生族]
    FAMILY --> BINDING[查询 WorkingSetTrackerBinding]
    BINDING --> TRACKER[取得当前工作集绑定的 Tracker]
    TRACKER --> DELETED{DeletionState = DELETED?}
    DELETED -->|是| ABSENT[返回不可见]
    DELETED -->|否| LOCAL
    LOCAL -->|INSTANTIATED| INSTANTIATED[以当前 LocalId 读取本地内容/Revision]
    LOCAL -->|VIRTUAL| SNAPSHOT[按 sourceLocalId + sourceRevision 精确读取来源内容]
    INSTANTIATED --> OUTPUT[TrackerItem / 具体业务对象]
    SNAPSHOT --> RECONSTITUTE[按 LightTrackerItem 的目标身份组装业务对象]
    RECONSTITUTE --> OUTPUT
```

解析器必须进行来源链循环检测、权限校验，并保证批量查询与单条查询都返回 `TrackerItem` 或其具体业务类型，且语义一致。VIRTUAL 来源内容只用于构造当前工作集上下文中的业务对象，不因此创建目标物理行。

## 5. COW 与删除

```mermaid
stateDiagram-v2
    [*] --> VirtualActive
    VirtualActive --> InstantiatedActive: 闭包内任一节点变更 / instantiate
    InstantiatedActive --> InstantiatedDeleted: delete
    InstantiatedDeleted --> InstantiatedActive: undelete
    InstantiatedDeleted --> [*]: delete2 / 删除实体、索引、版次与关系
```

```mermaid
classDiagram
  class TrackerItemInstantiator {
    <<Shared Kernel coordination>>
    +instantiate(locator) TrackerItem
    +instantiateAll(workingSetId, commonIds, type) List~T~
  }
  class WorkingSetTrackerItemInstantiator
  class LineageInstantiationService {
    +ensureReady(MutationScope)
  }
  class InstantiationClosure {
    +List~LightTrackerItem~ topologicalOrder
  }
  class LightTrackerItem {
    +CommonId commonId
    +LocalId localId
    +TrackerId trackerId
    +markInstantiated(localRevision)
  }
  class TrackerItem {
    <<Shared Kernel interface>>
    +isInstantiated() boolean
  }
  WorkingSetTrackerItemInstantiator ..|> TrackerItemInstantiator
  WorkingSetTrackerItemInstantiator --> LineageInstantiationService : 闭包准备
  WorkingSetTrackerItemInstantiator --> TrackerItem : 重载并返回请求目标
  LineageInstantiationService --> InstantiationClosure : 规划与执行
  InstantiationClosure o-- LightTrackerItem : 当前节点及派生子树
```

- 派生 Tracker 与 LightTrackerItem 身份索引不使用延迟创建；只有 Item 物理内容使用 COW。
- 对任一节点执行 update/delete 前，先按 CommonId 查询轻量条目，再结合 SourceTrackerId 派生图筛选当前 Tracker 子树，按 Tracker 拓扑顺序实例化所有 VIRTUAL 条目。
- 实例化服务按 sourceLocalId/sourceRevision 复制确定物理行，复用目标 LocalId，改写 Tracker、产品空间等归属，再调用 `markInstantiated(localRevision)` 重置来源指针和状态；闭包全部完成后才允许提交原始变更。
- delete 复用同一实例化流程，再软删除当前实例；后代保留独立物理内容。undelete 因而可以直接复用统一 BusinessObject API。
- delete2 只允许处理回收站中的已实例化实例；成功时删除实体、LightTrackerItem 索引、版次以及所有以该对象为端点的领域关系，不保留删除占位状态，也不能恢复。

## 6. 引用与合并模型

详细内容已迁入 [09 比对合并类图](./09-merge-domain-design.md#merge-model)，此处仅保留跳转入口。

引用模型见 [09 规范引用](./09-merge-domain-design.md#reference-design)；执行保护见 [09 执行协议](./09-merge-domain-design.md#merge-execution)。

## 7. 创建工作集协作图

```mermaid
flowchart TD
    CREATE[创建 WorkingSet] --> TRACKERS[读取选中 Tracker]
    TRACKERS --> MODE{Tracker.sharingMode}
    MODE -->|SHARED| REUSE[复用来源 Tracker]
    REUSE --> BIND[创建 WorkingSetTrackerBinding]
    MODE -->|UNSHARED| SOURCE[取得父 WorkingSet 的 Binding]
    SOURCE --> DERIVED[创建派生 Tracker<br/>sourceTrackerId=来源 TrackerId]
    DERIVED --> SNAPSHOT[取得 TrackerSnapshot<br/>HEAD=点击时 capturedAt]
    SNAPSHOT --> BIND
    BIND --> INDEX[批量生成 VIRTUAL LightTrackerItem]
    INDEX --> IDENTITY[保留 CommonId<br/>分配新 LocalId<br/>记录 sourceLocalId + sourceRevision]
    IDENTITY --> DONE[索引齐备后原子发布 READY]
```

该流程不存在修改 TrackerSharingMode 的路径；任何服务都只能读取这一固有属性。索引批量创建的是派生实例身份和来源指针，不是业务内容行。
