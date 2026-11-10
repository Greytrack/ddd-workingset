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
| `CONCEPT-008` | **Item Variant／Branch-local Item**：某个非共享 Tracker 上具有稳定 LocalId 的具体变体。 | 不等于业务 revision；无本地内容行仍可能存在。 | V4 名称为 LightTrackerItem；报告建议用 ItemVariant 避免技术索引含义。 | Candidate；`EXT-ARCH §1/2.1`、`V4-DOMAIN:214` |
| `CONCEPT-009` | **Item Revision／业务版次**：由显式升级版次操作推进的业务版本标识。 | 普通编辑增加乐观锁 version，业务版次保持不变。 | 用户已确认只有显式升级版次才产生版次历史数据，版次号向前推进 +1。MergeCheckpoint 的双侧版次使用此标识。 | 用户在本对话明确确认；数值与显示编码的映射待具体设计 |
| `CONCEPT-010` | **Source Cut／来源切点**：派生变体读取初始内容、生命周期及必要从属事实的固定历史依据。 | 不等同父对象当前值、捕获时间字符串或直接父变体。 | 需要历史能力保证可回读。 | Reported；`V4-DOMAIN:438/463`、`EXT-ARCH §2.1` |
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
3. **Item**、**Item Family**、**Item Variant**、**Item Revision** 分别表达业务对象、家族、变体和内容版次，不得互换。
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
- **SourceRevision**：来源业务版次信息（快照语义候选）。

LocalId 是否直接等同业务聚合变体身份、首次物化标志与有效内容指针、物化原子性均待裁定。
