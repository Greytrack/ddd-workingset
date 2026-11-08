# 工作集 V4 统一语言

## 1. 模型与边界

| 术语 | 定义 | 不应混用 |
|---|---|---|
| 分类元模型／Classification | 独立共享内核维护的三层分类体系，提供分类身份、属性约束和规则约束；启动前预置 | 不在工作集内复制定义另一套元模型 |
| 分类投影 | 依据分类创建具有自身身份、状态和持久化内容的 Tracker | 不是只读 CQRS 投影，不是缓存 |
| ProductSpace／产品空间 | 用户工作区最小单元，独立聚合，在默认工作集维护生命周期 | 不等同 WorkingSet、Project 或分类 |
| WorkingSet／工作集 | 选择 Tracker、组织有效内容范围和派生体系的独立聚合 | 不组合拥有全部业务对象 |
| 默认工作集 | 平台元数据预置的根，ID=1，不可删除、不可隐藏 | 不通过普通运行时 create 创建 |
| Tracker | 分类在产品空间、工作集内的持久化投影与条目容器，独立聚合 | 不再区分 TrackerBranch 或独立 BranchId |
| WorkingSetProductSpaceBinding／空间绑定 | WorkingSet 内部实体，显式记录产品空间纳入，组织 0..* 个 Tracker绑定 | 不是产品空间副本，不从Tracker数量推导 |
| WorkingSetTrackerBinding／Tracker绑定 | WorkingSet 内部实体，归属一个空间绑定，记录Tracker选择及有效范围 | 绑定不转移 Tracker 的生命周期归属 |
| TrackerItem | 继承 BusinessObject 的公共接口，具体业务聚合实现它 | 不是第二份统一持久化实体，不持有 LightTrackerItem |
| LightTrackerItem | 工作集 SK 私有独立聚合，维护条目身份、来源及状态摘要 | 不是业务聚合内部 Entity，不是代理或纯查询缓存 |
| BusinessObject | 业务对象基础协议，包含身份及 delete／undelete／delete2 行为 | 实现它不意味着自动参与 Tracker 管控 |
| WorkingSetProvisioning | 工作集创建、拓展和替换的持久化应用流程 | 不是容纳所有参与对象的领域聚合 |

## 2. 身份、归属与唯一性

ID 均使用不可变强类型值对象。跨聚合只保存对方的 ID，不保存聚合对象；同一 ID 类型可用于不同角色字段。整体图标注“按 ID 关联”，ID 类型及使用关系单独展示于 [01 §2.3](./01-domain-model.md#23-身份值对象与使用关系)。

| 身份类型 | 使用位置 |
|---|---|
| WorkingSetId | WorkingSet.id、parentId，以及生命周期 owningWorkingSetId |
| ProductSpaceId | ProductSpace.id、Tracker.productSpaceId、空间绑定的 productSpaceId |
| TrackerId | Tracker.id、parentTrackerId、sourceTrackerId；条目和绑定的 trackerId |
| ProductSpaceBindingId | 空间绑定身份及显式空间移出参数 |
| TrackerBindingId | Tracker绑定身份及移出／隐藏参数 |
| SnapshotId | 指定快照身份；HEAD 不构造伪 SnapshotId |
| CommonId／LocalId | 两种不同身份值类型，由 InstanceIdentity 组合 |
| ClassificationId／ProjectId | 外部分类／项目公开的身份合同，不在 SK 定义重复身份体系 |

值对象的标量存储方式不改变领域类型；原生 CommonId 与 LocalId 的标量相等也不意味着两种类型可互换。ID 不承担仓储查询、延迟加载或其他聚合生命周期。

| 名称 | 精确定义 |
|---|---|
| productSpaceId | Tracker 所属产品空间身份；派生保持不变 |
| classifyId | Tracker 投影依据的分类身份；派生保持不变 |
| owningWorkingSetId | 对象生命周期的权威工作集；产品空间与共享 Tracker 固定为 1，非共享 Tracker 为所属工作集 |
| 请求 workingSetId | 调用所在的操作／读取上下文，不自动等于生命周期归属；条目归属通过 Tracker 解析 |
| TrackerId | Tracker 实例身份；共享绑定复用，非共享派生生成新身份 |
| CommonId | 同族业务条目的共同身份；一个工作集有效范围内唯一，跨工作集可相同 |
| LocalId | 业务条目实例的全局唯一身份；原生与 CommonId 相等，派生分配新 LocalId；实例化不改变 |
| InstanceIdentity | CommonId 与 LocalId 组成的身份值；不额外混入工作集、Tracker 定位字段 |
| TrackerItemLocator | workingSetId 与 commonId 定位有效条目；历史读取还需明确内容范围或 snapshot |
| ParentTrackerId | Tracker 目录父级，不表示工作集派生来源 |
| SourceTrackerId | 非共享 Tracker 的直接派生来源；必须来自直接父工作集 |
| Binding.sourceTrackerId | 纳入时选择的直接父级 Tracker，表达选择入口；共享 Tracker 本体不因绑定改变来源 |
| parentLocalId | 条目的直接父变体身份；原生为空，实例化不改为自身 |
| ItemSourceRef／source | 当前内容定位，包括 sourceLocalId、sourceRevision、sourceCut；与直接父级关系分开 |

唯一性同时满足：

1. 同一有效工作集内一个 CommonId 对应一个条目。
2. 同一有效工作集内，同一产品空间、同一分类最多一个 Tracker。
3. 同一 Tracker 不重复纳入同一工作集；准备中的纳入也占用该选择。
4. 同一工作集有效范围内同一空间只有一个空间绑定；绑定的 Tracker 必须属于该空间。
5. 不同空间可有同分类的多个 Tracker；同一空间的 Tracker 可跨工作集共享或派生。
6. 业务版次和历史范围不构成新的同身份业务实例。

## 3. 共享与生命周期

| 术语 | 语义 |
|---|---|
| TrackerSharingMode | Tracker 创建时确定、此后不变的共享策略 |
| SHARED | 同一产品空间的 Tracker 跨工作集复用；Tracker 及其条目在默认工作集维护生命周期 |
| UNSHARED | 子工作集纳入时派生独立副本；Tracker 及条目在各自所属工作集维护生命周期 |
| 纳入 | 从直接父工作集选择 Tracker；共享建绑定，非共享派生副本并绑定 |
| 空间纳入／空间移出 | 显式建立／结束空间绑定；不创建或删除ProductSpace；移出空间与移出Tracker是不同操作 |
| 移出／解绑 | 结束当前工作集选择，不等于删除 Tracker 或其业务数据 |
| delete | 业务对象软删除，进入回收站 |
| undelete | 从软删除状态恢复，沿用原身份 |
| delete2 | 从软删除状态彻底删除，仍须满足引用与历史保留义务 |
| Tracker 删除 | 必须无相关条目；非共享还须无派生子级。子工作集的共享“删除”只解绑 |

子工作集不能通过普通 REST 新建 Tracker。默认工作集初始无产品空间和 Tracker；空间创建事件读取全部分类并逐个投影。重复纳入的新请求报错，相同请求重试幂等；非共享副本删除完成后可重新纳入并获得新身份。

移除最后一个 Tracker 不结束空间绑定，空间列表以空间绑定为依据；空间快照须记录无 Tracker 的空空间。两层绑定属于同一 WorkingSet 聚合，ProductSpace 与 Tracker 仍各为独立聚合。

## 4. 版次、快照与实例化

| 术语 | 语义 |
|---|---|
| revision／业务版次 | 每个对象至少版次 1；多版次对象修改升版次，单版次对象始终为 1 |
| 内容并发 version | 检测内容变化及并发冲突；单版次对象也会发生内容版本变化 |
| 索引 version | LightTrackerItem 自身并发控制版本，不代替业务版本 |
| contentRevision | 工作集有效内容范围的发布序号，不是条目的业务版次 |
| HEAD | 无 snapshotId，使用用户提交时刻固定的时间戳；按该切点逐实例取最新适用版次 |
| Snapshot | 独立快照聚合，以 snapshotId 定位固定时间切点及保留依据 |
| TrackerSnapshot | 来源 Tracker 与 capturedAt 的不可变切点值 |
| VIRTUAL | 逻辑条目已存在，本地业务内容尚未建立，按固定来源读取 |
| INSTANTIATED | 目标本地内容已建立；索引通过完成事件确认该事实 |
| instantiate／Instantiation | 沿用既有身份建立本地内容；不是补建新的逻辑聚合 |

指定 snapshot 时选择每个实例在切点前或当时已生效的最大版次；单版次也须取得当时内容，不读取当前值冒充历史。实例化确认后 source 指向自身及本地版次，parentLocalId 保持直接父级。

## 5. 事件与操作

| 名称 | 事实或操作含义 |
|---|---|
| ProductSpaceCreated | 产品空间创建已提交，触发固定分类集合的逐项 Tracker 初始化 |
| TrackerCreated | Tracker 原始创建已提交，触发默认绑定；不表示全部空间初始化完成 |
| TrackerItemChanged | 当前业务条目写入已提交，携带修改前内容依据，触发受影响派生条目保护 |
| ItemInstantiated | 目标本地内容已建立，驱动私有索引确认；不修改业务生命周期、不发业务创建事实 |
| create／extend／replaceContents | 创建子工作集、增量纳入、保留工作集身份并替换有效内容 |
| WorkingSetContentsReplaced | 目标有效内容范围已切换；旧资源另行检查依赖与清理 |
| LRT | Long-Running Task，长时间运行任务；不是长数据库事务 |

事件可靠性由框架保证；领域契约定义生产事实、消费行为、幂等依据及可见结果，不设计框架事务实现。

## 6. 专项术语入口

引用关联的 Reference、Association、钉版与悬空修复见 [引用领域模型](../workingsetReference/02-domain-design.md)。比对合并的 MergeDecision、MergePolicy、MergeEntry、MergeRound、ApplyResult、MergeCheckpoint 见 [合并专项 §2](../merge&diff/09-merge-domain-design.md#2-统一语言)。不在本体另设同名异义类型。
