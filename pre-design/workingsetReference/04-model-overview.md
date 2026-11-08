# 模型关系总览

Reference、Association 是独立聚合根；整体规则见 [02](./02-domain-design.md)，事件协议见 [05](./05-event-driven-repair.md)。

## 1. 聚合边界类图

```mermaid
classDiagram
  direction TB
  class BusinessObject {
    <<interface>>
    createReferences(params)
    createAssociations(params)
  }
  class TrackerItem {
    <<interface>>
    trackerId
    commonId
    localId
  }
  class Requirement {
    <<AggregateRoot>>
  }
  class Reference {
    <<AggregateRoot>>
    id
    sourceId
    attributeApiId
    targetIdentity
    targetCommonId
    targetTrackerId
    targetRevisionId
    markUnchanged(revision)
    markInvalid(revision)
    discardRevision(revision)
  }
  class RelationRevisionRecord {
    <<ValueObject>>
    revision
    mark
    originRowId_alwaysNull
  }
  class Association {
    <<AggregateRoot>>
    id
    sourceId
    targetId
    associationType
    assignRule(rule)
    removeRule()
  }
  class AssociationRule {
    <<ValueObject>>
  }
  class PropagationSuspect {
    <<interface>>
  }
  class ReferenceResolutionService {
    <<DomainService>>
    resolve(reference, context)
  }
  class WorkingSet {
    <<AggregateRoot>>
  }
  class WorkingSetTrackerBinding {
    <<Entity>>
  }
  class Tracker {
    <<AggregateRoot>>
  }
  class LightTrackerItem {
    <<InternalEntity>>
    trackerId
    commonId
    localId
    sourceRef
  }
  class MergeRound {
    <<AggregateRoot>>
  }
  class MergeDecision {
    <<CommandValue>>
  }
  class MergeEntry {
    <<CommandValue>>
  }
  class MergeCheckpoint {
    <<PersistentConfirmation>>
  }
  BusinessObject <|-- TrackerItem
  TrackerItem <|.. Requirement
  BusinessObject ..> Reference : 工厂创建而非组合
  BusinessObject ..> Association : 工厂创建而非组合
  Reference ..> BusinessObject : source与target按身份关联
  Association ..> BusinessObject : 两端按身份关联
  Reference "1" *-- "1..*" RelationRevisionRecord : 聚合内记录
  Reference *-- "1" PropagationSuspect : 能力状态承载
  Association *-- "1" PropagationSuspect : 能力状态承载
  Association *-- "0..1" AssociationRule
  ReferenceResolutionService ..> Reference : 直接解析目标身份与钉版
  WorkingSet *-- "0..*" WorkingSetTrackerBinding
  WorkingSetTrackerBinding --> Tracker : 按ID选择
  Tracker --> "0..*" LightTrackerItem : 管理索引并非聚合组合
  LightTrackerItem --> "0..1" Requirement : 本地物理内容
  ReferenceResolutionService ..> Tracker : 目标族根ID
  ReferenceResolutionService ..> LightTrackerItem : 当前上下文目标解析
  MergeRound ..> WorkingSet : 固定有序双方
  MergeRound ..> Tracker : 选定对应Tracker
  MergeDecision ..> MergeRound : 轮次ID
  MergeDecision *-- "1..*" MergeEntry
  MergeEntry ..> Reference : 选择逻辑引用差异
  MergeEntry ..> MergeCheckpoint : 使用有向确认依据
```

`*--` 表示聚合内从属记录 / 值对象或命令值组合；`..>` 表示使用、工厂或身份关联。工厂创建关系不代表聚合包含；各关系各自持有传播状态。

## 2. 模型职责

| 模型 | 与关系模型的边界 |
|---|---|
| BusinessObject | 基础能力与创建工厂；不组合包含 Reference / Association |
| TrackerItem | 受 Tracker 管理的业务对象契约，具体实现可作为关系两端 |
| Tracker | 类型、共享策略、数据版本线与派生族；不包含全 Tracker 关系集合 |
| LightTrackerItem | 内部身份、来源与存储状态索引，不是 TrackerItem 子类或业务概要对象，不持有关系 |
| WorkingSet / Binding | 选择目标 Tracker 与解析上下文；SHARED 对象及关系可多工作集复用 |
| Reference / Association | 分别组合版次记录、规则及自身传播能力，跨聚合按身份关联 |
| Merge | 用例 / 服务协作，不另建聚合 |
| MergeRound | 轻量轮次聚合，只管轮次身份、双方与状态，不包含关系或全部条目 |
| MergeDecision / MergeEntry | 不可变命令值，携带决定和比较依据 |
| MergeCheckpoint | 独立有向确认事实，不是解析缓存，修复不会自动确认 |

VIRTUAL 只描述 Item 内容。引用行已落库、右侧可空；后续 COW 不重复建关系。

## 3. 应用协作

```mermaid
flowchart TD
  M[应用服务\ncreate / extend / mutate / merge事务]
  W[WorkingSet协调器\nBinding / 索引 / COW闭包]
  C[Reference及Association命令能力\nGuard + 工厂 + 聚合行为]
  P[各自Repository\n独立聚合装载与保存]
  Q[QueryService\n唯一回显拼装]
  V[各自Provider\n有效版次 / 方向查询]
  L[LiteBusinessObjectService\n批量业务概要]
  R[ReferenceResolutionService\n规范目标与钉版解析]
  E[提交后事实事件\n分支合并或工作集拓展]
  H[引用事件处理器\n独立批次事务与幂等重试]
  D[TrackerDiffService / TrackerMergeService\n比较与选择应用]
  M --> W
  M --> C
  C --> P
  M --> D
  D --> R
  M --> E
  E --> H
  H --> R
  R --> W
  Q --> V
  Q --> L
  Q --> R
```

应用服务编排事务，领域服务协调跨对象规则，聚合维护不变量，仓储保存聚合。事件处理器使用独立消费事务。

## 4. 示例

父工作集 A → B，子工作集只纳入 A：

1. 建 A' 索引和 Reference R'；左侧 A'、右侧 NULL，记录有效，A' 内容可为 VIRTUAL。
2. 拓展或合并纳入 B'，提交后事件将同一 R' 右侧补为 B'；无对应钉版则保持悬空。
3. A' 签出只追加 R' 版次记录；删除使当前记录失效，取消签出撤销该记录。后续事件不能复活失效引用。

缺端点关联不建行，后续不自动补建。
