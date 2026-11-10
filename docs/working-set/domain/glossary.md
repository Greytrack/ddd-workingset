# Working Set 候选统一领域语言

**状态：Draft**

以下定义用于消除讨论歧义，不表示相应概念必然是实体、聚合或独立限界上下文。来源代号见 [证据登记](evidence-register.md)，可视化关系见 [候选领域概念关系图](../diagrams/domain-concept-relationships.md)。除 `CONCEPT-019/020` 所引用的企业架构外框外，其余定义按条目及下文的用户确认范围区分，不能整体视为已确认；企业外框已确认也不等于内部模型已确认。

| 编号 | 推荐术语与候选定义 | 歧义／应避免混用 | 关系与上下文差异 | 状态与来源 |
| --- | --- | --- | --- | --- |
| `CONCEPT-001` | **Working Set／工作集**：有身份和生命周期、选择 Tracker 并形成工作范围的工作上下文。 | 不等同 Git Branch；不预设拥有全部 Item。 | 在 Workspace Composition 中是选择清单；是否为单一大聚合待定。 | Reported；`EXT-ARCH §0/2.1`、`V4-UL §1` |
| `CONCEPT-002` | **Tracker**：具有独立业务身份、状态、生命周期及管理行为的独立聚合。 | 不使用 Tracker Branch 作为独立领域概念；不作为 Working Set 内部实体。 | Working Set 可定义其工作范围或关联，不拥有其完整生命周期；D-002 确认非共享派生产生新 Tracker 身份，Item 聚合归属仍待确认。 | 用户正式确认 D-001（2026-11-10）；见 [裁定记录](../reviews/2026-11-09-stage-3-architecture-review.md#d-001tracker-独立聚合裁定2026-11-10) |
| `CONCEPT-003` | **Shared Tracker**：多个 Working Set 选择同一权威 Tracker 与 Item 变体的协作语义。 | “共享”不等于所有用户有权；不等于复制后同步。 | 所有绑定工作集同时可见已确认；各读取面须兑现 POL-008，指标与实现待设计。 | Reported；`EXT-ARCH §2.1/11` |
| `CONCEPT-004` | **Non-shared Tracker**：非共享协作模式下的 Tracker；派生时产生具有独立身份的新 Tracker，源与派生为不同对象。 | 不等同同一 Tracker 的视图，也不引入 Tracker Branch。 | parentId 表达同工作集直属结构父，sourceId 表达直接父级工作集中的来源；两维独立（D-003）。 | 用户确认 D-002；[裁定记录](../reviews/2026-11-09-stage-3-architecture-review.md#d-002) |
| `CONCEPT-005` | **Tracker Branch（停用术语，仅历史追溯）** | D-001 排除其作为独立领域概念或聚合根。 | 原描述混合了演进线、来源及生命周期，不机械映射为一个 Tracker；具体语义重新审查。 | Historical；由 D-001 终止候选资格 |
| `CONCEPT-006` | **Item（停用为通用聚合；由 TrackerItem 契约取代）**：历史中指由具体业务上下文拥有内容和业务生命周期的受管业务对象。 | D-007 后不再作为独立于 TrackerItem 的通用聚合模型；不等同 LightTrackerItem 索引行。 | 各 BC 业务对象实现 TrackerItem 接口获得公共能力，专有规则由各 BC 拥有。 | Superseded by D-007；`EXT-ARCH §4/5` |
| `CONCEPT-007` | **Item Family／Item Identity**：以 CommonId 识别跨派生变体的同一逻辑对象家族。 | 无关 Item 跨类型、跨 Tracker 也不能共享 CommonId。 | 同一家族各变体共享 CommonId、拥有不同 LocalId。 | 本对话用户已确认身份关系与唯一性 |
| `CONCEPT-008` | **Item Variant／Branch-local Item（停用术语，由 LightTrackerItem 取代）**：历史中指某个非共享 Tracker 上具有稳定 LocalId 的具体变体。 | 不等于业务 revision；无本地内容行仍可能存在。 | V4 名称为 LightTrackerItem；D-010/D-012 已确认不引入 ItemVariant，其职责由 LightTrackerItem（CONCEPT-022）承担。 | Superseded by D-010/D-012；`EXT-ARCH §1/2.1`、`V4-DOMAIN:214` |
| `CONCEPT-009` | **Item Revision／业务版次**：由显式升级版次操作推进的业务版本标识。 | 普通编辑增加乐观锁 version，业务版次保持不变。 | 用户已确认只有显式升级版次才产生版次历史数据，版次号向前推进 +1。MergeCheckpoint 的双侧版次使用此标识。 | 用户在本对话明确确认；数值与显示编码的映射待具体设计 |
| `CONCEPT-010` | **Source Cut／来源切点**：物化（COW）时固定内容、生命周期及必要从属事实的历史依据，即修改前不可变快照。 | 不等同父对象当前值、捕获时间字符串或直接父变体；不等同 SourceRevision（未物化读取的来源 Revision 引用）。 | 需要历史能力保证可回读。 | Reported；`V4-DOMAIN:438/463`、`EXT-ARCH §2.1`；D-018 澄清 |
| `CONCEPT-011` | **Copy-on-Write／写时复制**：既有 VIRTUAL 变体首次本地写入时建立本地权威内容、但保持逻辑身份稳定的转换。 | 不是首次创建 Item 身份；不等同整 Tracker 预复制。 | Item Variant 与业务对象上下文共同参与，事务边界待定。 | Reported；`V4-DOMAIN:545-548` |
| `CONCEPT-012` | **Baseline／基线**：用于固定比较、派生或发布依据的一组不可变、可回读证据。 | 与仓库的 Design Baseline、业务 Snapshot、Merge Base 三者必须分开命名。 | 本文建议：治理基线称 Design Baseline；合并共同依据称 Merge Base。 | Candidate；`EXT-ARCH §2/10` |
| `CONCEPT-013` | **Difference／差异**：在固定有向比较上下文和双侧证据上得到的记录存在性或字段内容差异。 | LocalId 不同、物化状态不同不自动构成业务差异。 | 相等规则由具体字段／业务上下文提供。 | Reported；`MERGE §4.1`、`EXT-ARCH §10.1` |
| `CONCEPT-014` | **Merge／合并**：把明确决定通过目标业务能力应用，并记录结果与处理事实的有向过程。 | 不是整行覆盖；不自动承诺整轮原子。 | Compare & Merge 负责决定，业务对象上下文负责目标合法性。 | Reported；`EXT-ARCH §10` |
| `CONCEPT-015` | **Merge Decision／合并决定**：对存在性或字段差异作出的 `ACCEPT / KEEP_TARGET / DEFER` 选择。 | 数据决定与 `markAsMerged` 处理确认分离。 | [B09](../scenarios/shared-kernel-boundary-scenarios.md#b09-接受字段时取源端最新值) 已确认 ACCEPT 字段使用执行读取的源最新值。目标同一 ACCEPT 字段在提交前的编辑不阻断覆盖。 | Reported；`MERGE §2/4.2` |
| `CONCEPT-016` | **Merge Base／合并基准**：比较两条演进线时双方共同认可的历史依据。 | 不能伪造为直接父子关系；V4 首期尚未正式建模。 | 兄弟／跨代合并需要此概念。 | Candidate；`EXT-ARCH §11.20/12 P2` |
| `CONCEPT-017` | **Reference／引用**：由引用方持有、指向逻辑目标且可选择钉住目标版次的关系。 | 不等同 Association；物理端点为空不表示无逻辑目标。 | Reference 上下文拥有身份、有效性和修复。 | Reported；`REFERENCE CONTEXT §关系与方向` |
| `CONCEPT-018` | **Association／关联**：表达两个业务对象之间业务联系的独立关系。 | 不应把引用属性与一般关联强行统一。 | 缺端点时是否落库与 Reference 不同。 | Reported；`REFERENCE CONTEXT §关系与方向` |
| `CONCEPT-019` | **Shared Kernel／共享内核**：本项目企业架构中由四个业务 BC 共同依赖的共享能力集合，包含 Working Set、Privilege、Workflow、Class Hierarchy。 | 不自动等同 DDD 书中协同维护少量共享模型的集成模式；也不等同单一聚合、模块或微服务。 | 企业归属由 `AC-001` 固定；内部治理和模型边界仍待设计。 | Confirmed constraint；`USER-ARCH-2026-11-09` |
| `CONCEPT-020` | **Business Bounded Context／业务限界上下文**：拥有自身业务对象语言、状态机和不变量的 GateFlow、ALM、DQA 或 PLM 上下文。 | 不应因共同调用 Working Set API 而把四个 BC 的专有规则合并进 Shared Kernel。 | 业务 BC 消费共享能力，并保留对自身内容变化的最终合法性裁决。 | Confirmed landscape / boundary candidate；`AC-001` |
| `CONCEPT-021` | **TrackerItem／公共 Item 契约**：Working Set Shared Kernel 声明的顶级接口，是各业务 BC 领域对象参与工作集管理的公共能力契约。 | 不等同业务对象的完整领域模型，也不把 `Item` 作为独立通用聚合。 | 业务对象通过实现该接口获得公共身份与工作集语义；公共机制不拥有业务状态、生命周期与不变量。 | 用户确认 D-007；[裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-007) |
| `CONCEPT-022` | **LightTrackerItem／轻量身份项**：Working Set 专属的独立聚合，负责工作集内身份、来源与有效变体定位的权威，不是单纯的查询投影。 | 不拥有完整 Part／Requirement 对象；不等同业务对象本身；不等于查询投影。 | 派生时创建并分配当前变体 LocalId、保留 CommonId，记录 SourceLocalId/SourceRevision，经 TrackerId/WorkingSetId 指向；完整属性与物化协议候选。 | 独立聚合已由 D-008 确认；[裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-008) |

## Item 身份规则（用户已确认）

确认来源为本对话中用户对 Item 派生和回并身份的明确说明。

- Item 借助工作集沿链路派生，各个变体拥有相同 CommonId，各自 LocalId 不同。
- 子工作集原生新建 Item 回并后，父侧与子侧对应 Item 保持相同 CommonId、不同 LocalId。
- 默认工作集中的 Item 满足 CommonId = LocalId。
- 非默认工作集中的 Item 满足 CommonId != LocalId。

用户已确认默认/非默认按 Item 的实际归属判断。子工作集纳入共享 Tracker 后，查看的仍是归属默认工作集的同一个 Item，因此 CommonId=LocalId，查看入口变化不分配新身份。用户进一步确认，两个无派生关系的 Item 即使属于不同类型或不同 Tracker，也不允许拥有相同 CommonId。CommonId 在逻辑对象家族层面全局唯一，同族变体复用该值。CommonId 与 LocalId 均采用雪花 ID。单工作集 CommonId 唯一、同族只允许一个有效变体已于阶段 3.1 确认。标识生成协调、去重与子侧原生新建后回并默认工作集时的标识分配协议继续设计。

例如非共享派生链可表达为默认节点 (CommonId=100, LocalId=100)、子节点 (100,201)、孙节点 (100,302)。数值仅说明等值关系。

## 重点歧义

1. **Baseline** 至少有三义：仓库设计基线、工作范围 Snapshot／历史切点、Merge Base。后续文档必须使用限定词。
2. **Tracker** 已确认为独立聚合；Tracker Branch 为停用的历史术语。D-002 已确认非共享派生为不同 Tracker 身份；D-003 已明确双维字段及逐级派生；可变性、组织继承和同源共存约束仍待确认。Shared 既有同一权威语义不变。
3. **Item（停用）**、**Item Family**、**Item Revision** 分别表达业务对象、家族和内容版次；**Item Variant** 已被 LightTrackerItem（CONCEPT-022）取代，不得互换。
4. **共享**表达权威内容复用，不表达访问权限；所有绑定工作集同时可见已确认，不能以投影延迟自行放宽该要求。
5. **已处理**与内容相等分别表达。用户已确认 `KEEP_TARGET + markAsMerged=true` 可保留真实差异，决定写入 MergeCheckpoint，双侧版次均不变时停止重复提示。详见 [确认场景](../scenarios/core-scenarios.md#keep_target-的已处理确认)，这里的版次已确认为显式升级的业务 revision。普通编辑使乐观锁版本增加但业务版次不变时，已处理差异继续隐藏。任一侧显式升级业务版次后，用户再次主动比对时旧 MergeCheckpoint 对新版本组合过时，所有适用字段重新比较并显示实际差异。
6. **Shared Kernel** 在本项目首先是已确认的企业架构归属；是否采用经典 DDD Shared Kernel 的共同代码／模型治理方式仍需另行说明。

## 候选对话检验

> 领域专家：子工作集选择 Shared Tracker 时，已确认看到同一个权威 Item。
>
> 架构师：因此不通过 Merge 同步；权限与兑现同时可见的读取协议仍需设计。
>
> 领域专家：Non-shared Tracker 中 Item 首次修改时，身份是否变化？
>
> 架构师：候选模型要求 Item Variant 身份保持不变，只把内容从固定 Source Cut 转为本地权威内容；这仍需业务确认。

## 阶段 3.1 接受范围与补充

限定术语已按 [首版批准记录](../reviews/2026-11-09-stage-3-1-rule-convergence.md#用户批准记录与生效范围) 接受，其余候选定义不整体升级。用户明确确认：同一工作集有效范围内 CommonId 唯一，同族只允许一个有效变体；跨工作集同族仍共用 CommonId。Source Cut 的派生内容覆盖附件与关系，二者均固定为派生时状态；这不自动表示关系所指对象的全部内容也递归冻结。

## Tracker 追溯用语（D-002 历史解释，现由 D-003 澄清）

`parentId`、`sourceId` 是已确认用于表达 Tracker 追溯层级的名称，不凭字段名推导直接父或最初来源。二者也不等于 Working Set 的父级或内容 Source Cut。`Tracker Lineage` 可作为描述 Tracker 间追溯关系的候选术语，暂不单独设立聚合只是设计建议，不是用户已接受的聚合结论。多级链图只能在多级派生得到裁定后作为已确认行为绘制。

## Tracker 双维关系（D-003，用户已确认）

- **结构父节点 / parentId**：同一 Working Set 内该 Tracker 的直属结构父 Tracker。它表达组织树，不表达复制或派生。
- **直接来源 / sourceId**：直接父级 Working Set 下用于派生该 Tracker 的来源 Tracker。不是最初来源；不能跳过中间工作集。
- **Tracker 组织树**与**Tracker 派生链**为两个关系维度，可以同时存在，不互相替代。Tracker Lineage 若使用，仅描述 sourceId 维度；独立关系聚合仍未批准。

例：WS1 的 B1.parentId=A1，同时 B1.sourceId 指向 WS0 的来源；两个字段不必指同一对象。D-004 已确认未选择原结构父时可令新 Tracker.parentId=null 成为根；D-006 已确认创建后 parentId 不可变；sourceId 生命周期可变性及共享对象跨工作集父级一致性仍不从术语推导。完整示例见 [双维关系图](../diagrams/tracker-dual-relations.md)。

## Tracker Hierarchy 与 Tracker Derivation（D-004）

- **Tracker Hierarchy / Tracker 组织树**：一个 Working Set 有效组织范围内，以 parentId 表达的直属结构关系。不同工作集可以有不同组织树。
- **Tracker Derivation / Tracker 派生关系**：跨直接父子 Working Set、以 sourceId 表达的直接来源关系；非共享派生建立新的 Tracker 身份。不要把 Hierarchy 合并称为 Derivation，也不引入 Tracker Branch。
- **根 Tracker**：在当前工作集组织树中没有结构父的 Tracker；D-004 场景以 parentId=null 表达，其 sourceId 仍可非空。

裁定来源：[D-004](../reviews/2026-11-09-stage-3-architecture-review.md#d-004)。父未选时允许裁剪为根，不强制补齐祖先；D-005/D-006 已确认父也选时默认继承且允许创建前显式重组，创建后不可移动。Shared 混合树规则仍未确认。

## 创建时组织位置与完成边界（D-005 / D-006）

**创建时组织位置**：Tracker 在创建完成前确定的 parentId（同工作集有效组织父或 null 根）。非共享派生默认继承已选父子结构，允许用户在完成前显式选择其他合法结构。**创建完成后组织位置稳定性**：已有 Tracker 的 parentId 不再允许改变。

这不是新增 Draft Tracker 实体或状态机。“创建完成”是业务边界，其持久化、发布时点与重试协议仍待设计。组织位置稳定不等于 Tracker 其他属性不可变，也不等于 Working Set 成员集合封闭。不同工作集的不同树形成于各自创建/派生过程，而非允许已创建节点随后移动。见 [生命周期图](../diagrams/tracker-creation-lifecycle.md)。

## TrackerItem 公共契约与 LightTrackerItem（D-007）

用户正式确认：工作集领域统一使用 `TrackerItem` 作为公共 Item 概念，不继续将 `Item` 作为独立于 TrackerItem 的通用聚合模型；Working Set Shared Kernel 声明顶级 `TrackerItem` interface；各业务 BC 领域对象通过实现该接口获得参与 Working Set 管理的公共能力。

- **已确认**：TrackerItem 是公共领域能力契约；Part、Requirement 等是各自业务 BC 的完整独立聚合；Working Set 不拥有其领域状态、业务生命周期与业务不变量，只负责公共 TrackerItem 身份及工作集相关机制。
- **候选**：LightTrackerItem 作为专属领域聚合的完整边界、内部属性、领域行为，及其是否拥有有效变体与版本定位的权威状态；CommonId、LocalId、Revision、Version 在 TrackerItem 契约与 LightTrackerItem 之间的分配；跨 BC 首次 COW 的事务与一致性协议。不得因已有索引表就判定 LightTrackerItem 必为聚合根，也不否定候选聚合定位。

Tracker 仍是独立聚合，不因管理 TrackerItem 工作范围而拥有业务对象聚合。类图见 [D-007 契约与聚合图](../diagrams/trackeritem-contract.md)。

## Derive / Resolve / Materialize 与身份字段（D-008）

用户确认 LightTrackerItem 是 Working Set 专属独立聚合（非查询投影），负责身份、来源与有效变体定位权威；派生采用 Copy-on-Write，不立即物化完整业务对象副本。派生时创建 LightTrackerItem、分配当前变体 LocalId、保留 CommonId，记录 SourceLocalId/SourceRevision，经 TrackerId/WorkingSetId 指向。业务 BC 通过 CQRS Query 与工厂重建完整业务聚合，调用方不感知底层物化状态；Working Set 不拥有或重建业务 BC 完整状态。

以下为候选统一语言，不固定其全部生命周期行为：

- **Derive／派生**：从来源变体派生出新变体身份，创建 LightTrackerItem 并分配 LocalId，不立即物化完整业务内容。
- **Resolve／解析**：解析当前业务变体及其来源（经 LightTrackerItem 定位）。
- **Materialize／物化**：形成变体自身的完整业务持久化内容（首次本地写入触发）。
- **CommonId**：共同逻辑身份，跨派生变体保留。
- **LocalId**：当前变体身份，派生时分配。
- **SourceLocalId**：直接来源变体身份。
- **SourceRevision**：来源业务版次信息；可能指向来源头版次，不保证对应不可变历史内容（D-009 纠正「快照语义」旧假设）。SourceVersion 是表述错误，已由 D-012 统一为 SourceRevision，不得与并发控制 Version 混淆。

LocalId 是否直接等同业务聚合变体身份、首次物化标志与有效内容指针、物化原子性均待裁定。

## 来源变更 COW 的领域语义（D-009）

用户确认事件驱动的来源变更 COW 机制，并纠正若干旧假设。以下术语区分：

- **Business Revision／业务版次**：显式升级的业务版次；普通内容修改不自动升级 Revision。
- **Concurrent Version／并发版本**：普通修改可能推动的并发控制版本，与 Business Revision 分开。
- **Derivation Origin／派生历史来源**：派生时的历史来源（如 sourceLocalId 指向的 R0），用于追溯谱系。
- **Effective Content Source／有效内容来源**：当前有效内容的读取来源；物化前指向来源，物化后切换到自身 LocalId 及自身有效版次。
- **Source-backed**：依赖来源解析内容的未物化变体。
- **Local-backed**：本地物化内容成为有效来源的变体。
- **Materialization／物化**：基于既有事实创建当前变体的完整业务内容。

SourceVersion 是表述错误，已由 D-012 统一为 SourceRevision，不得与并发控制 Version 混淆；SourceRevision 可能指向来源头版次，不保证对应不可变历史内容。不得把派生历史来源与当前有效内容来源混为一谈。

## COW 最终一致与最小模型收敛（D-010）

用户确认：

- **不可变内容快照**：来源修改前的完整业务事实作为事件关联的不可变内容快照，与来源变更事件一起传递；不得将可变的头 Revision 或仅有 SourceVersion 的事件当作不可变快照。
- **最终一致**：来源修改已保存、COW 尚未完成期间，允许派生工作集暂时读取到来源修改后的内容；物化完成后以需要保护的修改前事实形成独立内容。
- **LightTrackerItem 双态**：Source-backed（内容由来源解析）与 Local-backed（本地业务聚合已物化，有效来源定位到自身）为两个领域主状态；Materializing 是处理过程/技术运行状态，不增加第三种业务状态。
- **SourceLocalId 双语义**：历史派生来源（Derivation Origin）与当前有效内容来源（Effective Content Source）分别表达；物化后有效 SourceLocalId 指向自身，历史派生来源保留追溯。字段是否拆分仍待设计。
- **最小模型**：领域模型围绕 Working Set、Tracker、LightTrackerItem 三类核心聚合收敛，不引入 Manifest 或 ItemVariant。

SourceVersion 是表述错误，已由 D-012 统一为 SourceRevision，不得与并发控制 Version 混淆。

## 首次快照优先与来源变更顺序（D-011）

用户确认：对尚未物化的派生 TrackerItem，来源首次发生需要触发 COW 的修改时，该修改前的完整业务事实确定目标变体最终应物化的内容；同一来源之后的连续修改不能覆盖首次确定的快照；快照选择按来源业务修改的实际先后顺序，而非事件到达顺序。

候选领域协议/设计概念（不自动设立新聚合、数据库表或持久状态）：

- **Source Change Order／来源变更顺序**：能够反映来源成功修改先后顺序的机制。
- **BeforeSnapshot／修改前快照**：来源修改前不可变的完整业务事实。
- **Derivation Baseline／派生基准**：目标变体派生时已继承的来源内容位置。
- **Materialization Claim／物化声明**：候选的首次快照选择记录。

Source Change Order、Derivation Baseline、Materialization Claim 均为候选领域协议或设计概念。

## SourceLocalId / SourceRevision 的最终语义（D-012）

用户正式裁定并修正此前表述：

- **SourceLocalId** 表示**当前有效内容来源**的 LocalId，而不是必须永久保存的历史派生来源身份。未物化（Source-backed）时指向来源变体（如 R0）；COW 完成后等于自身 LocalId（如 R1）。
- **SourceRevision** 表示**当前有效内容来源**的业务 Revision，不是不可变内容快照。COW 完成后等于自身 Revision。
- **SourceVersion** 是表述错误，统一更正为 `SourceRevision`，不得与并发控制 Version 混淆。
- R0 → R1 的历史派生关系不是业务必需，不要求保存（D-018）；历史派生来源与当前有效内容来源在领域语义上必须区分，历史关系不要求承载。
- 不因此新增 OriginLocalId、ItemVariant、Manifest 或额外谱系聚合。

修正说明：前文 D-008 的「SourceLocalId = 直接来源变体身份」、D-009 的「SourceRevision 快照语义」、D-010 的「SourceLocalId 双语义（历史派生来源 + 当前有效内容来源）」均为历史分析，现由 D-012 收敛为「当前有效内容来源」单一职责，保留历史记录但以本节为准。

## 多级派生的直接有效来源定位（D-013）

用户确认，并修正此前「逐级递归解析未物化中间变体」的候选假设：

- **SourceLocalId 直接指向实际拥有完整业务内容的变体**，不是直接父工作集中的历史派生来源。
- 直接上游已物化 → SourceLocalId 指向该上游；直接上游未物化 → 继续向上寻找最近一个实际拥有完整内容的上游，直接定位。
- **不采用对未物化中间变体逐级递归解析**。多级派生（R0→R1→R2）中，若仅 R0 已物化，则 R1.SourceLocalId=R0、R2.SourceLocalId=R0（R2 跳过未物化的 R1 直接指向 R0）。
- COW 依据 SourceLocalId 发现所有依赖该实际内容来源的未物化 LightTrackerItem，即使分布在不同层级 Working Set 也都要物化（扇出）。

前文 D-012 的「多级派生逐级解析」候选假设被 D-013 排除，保留历史追溯。

## 已有下游不随中间上游物化重定向（D-014）

用户确认，澄清 D-013 的边界：

- SourceLocalId 在**派生时**按 D-013 选择实际拥有完整业务内容的来源（直接上游未物化则向上找最近已物化上游）。
- **派生完成后**，已存在的未物化变体不因中间上游变体物化而自动重定向 SourceLocalId；SourceLocalId 不需要在生命周期内始终动态指向最近已物化祖先。
- 只有当前 LightTrackerItem 自身完成 COW 后，才将 SourceLocalId 切换为自身 LocalId、SourceRevision 切换为自身 Revision。
- 当前有效来源关系与 Working Set 的派生层级是两个不同维度；来源修改时基于当前有效来源依赖关系识别需物化变体，而非递归遍历工作集后代。

示例：R0 已物化，R1/R2 未物化均 SourceLocalId=R0；R1 独立物化后 SourceLocalId=R1，R2 仍保持 SourceLocalId=R0，不自动改为 R1。

## 派生时间基准与命名 Snapshot / Head（D-015）

用户确认 Working Set 派生不复制完整业务对象数据，仅创建 LightTrackerItem 建立身份、工作范围关联与有效内容来源引用。派生时必须选择时间基准：

- **Derivation Time Basis／派生时间基准**：派生操作选择的时间基准，用于确定来源适用的业务 Revision。
- **Named Snapshot／命名快照**：具有名称的时间戳，作为派生时间基准。
- **Head／当前头**：用户提交派生操作瞬间对应的时间点，不是动态查询时刻。

系统根据选定时间点选择该时间点之前适用的最大业务 Revision。**Derivation Time Basis 不等于 BeforeSnapshot**；命名 Snapshot 不是完整业务内容快照，只用于选 Revision。派生时不等待来源停止变化，不因并发修改先完成而改变已选时间基准。

## 派生仅固定版次与 Derivation Completion 边界（D-016）

用户确认，修正此前将派生时间点解释为完整业务内容快照的设计假设：

- 派生操作不复制或固定完整业务数据，只生成 LightTrackerItem 保存身份及有效来源引用（LocalId、CommonId、SourceLocalId、SourceRevision）。
- 命名 Snapshot 或 Head 时间基准只用于确定来源适用的业务 Revision；即使同一 Revision 内发生普通内容修改，派生也不要求保留所选时间点的完整内容。
- **Derivation Completion／完整派生完成**：完整派生操作完成，新变体进入有效工作范围的业务边界，也是该变体开始受到来源变更 COW 保护的边界。
- **COW Protection／COW 保护**：派生完成后来源相关修改触发的物化保护。
- **Source Mutation／来源修改**：来源业务内容修改。

Derivation Time Basis 不等于 Derivation Completion；SourceRevision 不等于不可变内容快照。D-011 的首次修改前快照优先原则限定为「派生完成之后首次需要保护的相关来源修改」。

## COW 目标在事件消费时确定（D-017）

用户确认，修订 D-016 的「完整派生完成后才进入 COW 保护范围」时间边界：

- COW 目标集合在事件 Handler 实际消费来源变更事件时确定，不在来源修改开始时提前固定。
- Handler 查询当前有效内容来源指向本次修改来源的 LightTrackerItem（保留 SourceLocalId、SourceRevision、未物化状态等匹配条件），查询命中的都参与本次 COW 物化。
- 不追溯比较来源修改开始时间与完整派生完成时间，不引入专门的跨聚合全局时序协调机制。
- 即使派生变体在来源修改开始后才完成派生，只要 Handler 消费时满足条件，也参与本次物化。

D-016 的 Snapshot/Head 只决定 Revision、不复制完整内容的规则保持不变；D-016 的 COW 保护资格历史时间边界由本裁定取代。

## 整体验证收敛：Revision 字段、事件顺序与历史派生关系（D-018）

用户确认以下收敛（整体验证裁定，非零散问题）：

1. **事件顺序由事件框架兜底**：事件框架按顺序记录事件、消费时按顺序消费；Source Change Order 的先后判定不是领域模型需要关注的细节。D-011 首次快照优先的「实际先后顺序」由事件框架顺序消费保证。
2. **LightTrackerItem 持有 Revision**：LightTrackerItem 自身持有 Revision 字段，与 SourceRevision 不是始终同步（派生初期 SourceRevision 可为来源 Revision、自身 Revision 为初始值）。业务 BC 实现的 TrackerItem 也持有 Revision，两者通过事件同步（细节不细化）。
3. **Revision 显式升级触发 COW**：显式升级业务 Revision 触发依赖变体 COW。
4. **Source Cut 与 SourceRevision 区分**：Source Cut 是物化（COW）时的固定依据（修改前不可变快照）；SourceRevision 是未物化时读取的来源 Revision 引用（Query 读当前内容）。二者不混用。
5. **历史派生关系（R0→R1）不是业务必需**：不要求保存历史派生链，移除相关的承载方式待裁定与约束。

## ProductSpace、Classification 与绑定（阶段 4 补充）

用户 2026-11-10 补充确认：

- **ProductSpace／产品空间**：Shared Kernel 独立聚合；只维护自身稳定归属身份；在默认 WorkingSet 中维护生命周期，可被多个 WorkingSet 关联；本身不随 WorkingSet 派生复制/迁移。
- **Classification／分类元模型**：独立共享分类元模型领域（三层，另一团队维护，启动前初始化、常驻内存）；Tracker 是其投影。
- **SpaceBinding／空间绑定**：WorkingSet 聚合内的 **Entity**，表达当前工作集与 ProductSpace 的有效关联；TrackerBinding 的业务前置。
- **TrackerBinding／Tracker 绑定**：WorkingSet 聚合内的 **Entity**，表达当前工作集与 Tracker 的有效关联；受空间绑定和槽位唯一约束。

**绑定归属**：WorkingSet 负责维护当前工作集中的空间与 Tracker 绑定结构；SpaceBinding、TrackerBinding 是两个 Entity，归属于 WorkingSet 聚合。ProductSpace、Tracker 只维护自己的稳定归属身份，不持有绑定。Tracker 按共享策略复用同一聚合（Shared）或派生新聚合（非共享）；同一 ProductSpace、同一 WorkingSet、同一 Classification 最多一个有效 Tracker。

**删除/移除语义（用户 2026-11-10 确认）**：

- 共享 Tracker 从子 WorkingSet 移除：仅解除 TrackerBinding（空 Tracker 可移除规则）。
- 非共享 Tracker 从所属子 WorkingSet 移除：无 TrackerItem 数据且无派生子 Tracker → 删除该 Tracker 并解除绑定。
- 非共享 Tracker 已有派生子 Tracker → 不允许删除（保留来源链）。
- 任意 Tracker 存在 TrackerItem 数据 → 不允许移除（避免遗留业务数据）。
- 非共享 Tracker 有组织子节点但无派生子节点 → 不允许删除（校验提示含有子节点）。
- ProductSpace 移除最后一个 Tracker → 不触发空间移除，SpaceBinding 保留。
- 移除 ProductSpace 时仍有 TrackerBinding → 抛出异常，有 Tracker 时不允许移除空间绑定。
- 默认 WorkingSet 下共享 Tracker 在空且无子时可删除。

## Diff/Merge 聚合定位（阶段 4 补充，D-019）

用户 2026-11-10 确认（FACT-027）：

- **MergeRound／合并轮次**：合并的载体聚合。发起 LRO 后记录本轮参数、进度、成功与失败信息，并持有本轮合并决策；**不是三类核心聚合之一**，属合并模型区域。
- **Merge Decision／合并决策**：用户决策，来自 command 入参；作为 **ValueObject 归属 MergeRound 聚合**，不是独立聚合或实体根。`CONCEPT-015` 的决定语义在此收敛归属。
- **MergeCheckpoint／合并检查点**：**独立聚合**，表达「差异已处理」的确认/撤销事实，不等同内容相等；键候选为有向 source-target-family，确认/撤销需因果单调。
- **差异冻结／比较令牌**：待定执行协议（`QUESTION-015`），尚未确认机制，不得据此宣称 D-019 已确定执行细节。
- 准入与物化：未物化变体可参与 Diff；未物化目标被 Merge 修改时在 merge 中构建业务聚合正常 save（物化与修改一体）；共享 Tracker 不参与 Merge，非共享只允许直接父子准入。
