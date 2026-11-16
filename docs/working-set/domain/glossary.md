# Working Set 统一领域语言

**状态：Draft（术语表；已确认子集以 [事实与政策基线](facts-policy-baseline-v1.md) 和 [BASELINE](../BASELINE.md) 为准）**

本页是术语的权威入口，不重复裁定过程。每个术语只定义“是什么”；历史论证见[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)与各契约。术语不表示相应概念必然是实体、聚合或限界上下文。

## 核心术语

| 编号 | 术语 | 定义 | 应避免的混用 |
| --- | --- | --- | --- |
| `CONCEPT-001` | **Working Set／工作集** | 有身份和生命周期、选择 Tracker 并形成有效工作范围的工作上下文或范围清单。 | 不等同 Git Branch；不预设拥有全部 Item；不等同分支本体。 |
| `CONCEPT-002` | **Tracker** | 具有独立业务身份、状态、生命周期与管理行为的独立聚合。 | 不作为 Working Set 内部实体；不使用 Tracker Branch 作为独立领域概念。 |
| `CONCEPT-003` | **Shared Tracker／共享 Tracker** | 多个 Working Set 绑定同一权威 Tracker、同一 Item 变体和同一权威内容的协作语义。 | “共享”不等于所有用户有权；不等于复制后同步；修改不通过 Merge 传播。 |
| `CONCEPT-004` | **Non-shared Tracker／非共享 Tracker** | 非共享协作模式下的 Tracker；派生时产生具有独立身份的新 Tracker，源与派生是不同对象。 | 不等同同一 Tracker 的视图；不引入 Tracker Branch。 |
| `CONCEPT-007` | **Item Family／Item 家族** | 以 `CommonId` 识别跨派生变体的同一逻辑对象家族。 | 无关 Item 跨类型、跨 Tracker 也不能共享 `CommonId`。 |
| `CONCEPT-009` | **Business Revision／业务版次** | 由显式“升级版次”操作推进的业务版本标识；普通编辑不推进。 | 不等于乐观锁 `lock_version`，也不等于发布大版本 `version`。 |
| `CONCEPT-010` | **Source Cut／来源切点** | 物化（COW）时固定普通字段内容的“修改前不可变快照”及其证据。 | 不等于来源当前值、捕获时间字符串、直接父变体或 `SourceRevision`。 |
| `CONCEPT-011` | **Copy-on-Write／写时复制** | 既有未物化变体首次需要本地内容时，建立本地权威内容但保持逻辑身份不变的转换。 | 不是首次创建 Item 身份；不等同整 Tracker 预复制。 |
| `CONCEPT-012` | **Baseline／基线（业务）** | 用于固定比较、派生或发布依据的一组不可变、可回读证据。 | 与仓库 Design Baseline、业务 Snapshot、Merge Base 必须分开命名；业务含义仍见 `QUESTION-011`。 |
| `CONCEPT-013` | **Difference／差异** | 在固定有向比较上下文和双侧证据上得到的记录存在性或字段内容差异。 | `LocalId` 不同、物化状态不同不自动构成业务差异。 |
| `CONCEPT-014` | **Merge／合并** | 把明确决定通过目标业务能力应用，并记录结果与处理事实的有向过程。 | 不是整行覆盖；不自动承诺整轮原子；不直接写业务字段。 |
| `CONCEPT-015` | **Merge Decision／合并决定** | 对存在性或字段差异作出的 `ACCEPT / KEEP_TARGET / DEFER` 选择，以及 `markAsMerged`。 | 数据决定、实际 Apply Result 与“已处理”确认是不同事实。 |
| `CONCEPT-016` | **Merge Base／合并基准** | 比较两条演进线时双方共同认可的历史依据。 | 不能伪造为直接父子关系；V4 首期尚未正式建模。 |
| `CONCEPT-017` | **Reference／引用** | 由引用方持有、指向逻辑目标且可选择钉住目标版次的关系。 | 不等同 Association；物理端点为空不表示无逻辑目标。 |
| `CONCEPT-018` | **Association／关联** | 表达两个业务对象之间业务联系的独立关系。 | 不把引用属性与一般关联强行统一；缺端点时落库语义与 Reference 不同。 |
| `CONCEPT-019` | **Shared Kernel／共享内核** | 企业架构中由四个业务 BC 共同依赖的共享能力集合，包含 Working Set、Privilege、Workflow、Class Hierarchy。 | 不自动等同 DDD 经典 Shared Kernel 的共同代码治理，也不等同单一聚合、模块或微服务。 |
| `CONCEPT-020` | **Business Bounded Context／业务限界上下文** | 拥有自身业务对象语言、状态机和不变量的 GateFlow、ALM、DQA 或 PLM 上下文。 | 不因共同调用 Working Set API 而把四个 BC 的专有规则合并进 Shared Kernel。 |
| `CONCEPT-021` | **TrackerItem** | Working Set Shared Kernel 声明的公共 Item 能力契约；各业务 BC 的领域对象实现它。 | 不等同业务对象的完整领域模型；不把 `Item` 作为独立通用聚合。 |
| `CONCEPT-022` | **LightTrackerItem／轻量身份项** | Working Set 专属独立聚合，负责工作集内身份、来源与有效变体定位的权威，不是查询投影。 | 不拥有完整 Part/Requirement 内容；不等同业务对象本身。 |
| `CONCEPT-023` | **ProductSpace／产品空间** | Shared Kernel 独立聚合；维护自身稳定归属身份，可被多个 Working Set 关联，不随 Working Set 派生复制。 | 不归 Working Set 聚合所有；不因绑定关系复制。 |
| `CONCEPT-024` | **Classification／分类元模型** | 独立共享分类元模型；Tracker 是它在某产品空间/工作集中的投影。 | 不是 Tracker 的属性集合；共享特性是出厂设定。 |
| `CONCEPT-025` | **SpaceBinding／空间绑定** | Working Set 聚合内的 Entity，表达当前工作集与 ProductSpace 的有效关联；TrackerBinding 的前置。 | 不是 ProductSpace 自身的字段；不是数据库外键。 |
| `CONCEPT-026` | **TrackerBinding／Tracker 绑定** | Working Set 聚合内的 Entity，表达当前工作集与 Tracker 的有效关联。 | 不是 Tracker 自身生命周期的一部分。 |
| `CONCEPT-027` | **MergeRound／合并轮次** | 一页合并决策的执行载体聚合；记录本轮参数、条目、进度与唯一终态结果。 | 不是三类核心聚合之一；终态不可重开。 |
| `CONCEPT-028` | **MergeCheckpoint／合并检查点** | 表达“差异已处理”的确认/撤销事实的独立聚合；不证明内容相等。 | 不等同 Apply Result；不按 Working Set 定位。 |
| `CONCEPT-029` | **WorkingSetCreation／工作集创建任务** | 按用户选择范围创建子工作集的异步执行记录；保存范围、进度与结果。 | 不拥有 Working Set/Tracker/LTI；不是创建入口本身。 |

## Item 身份规则（已确认）

- Item 借助工作集沿派生链派生，各变体拥有相同 `CommonId`、不同 `LocalId`。
- 子工作集原生新建 Item 回并后，父侧与子侧保持相同 `CommonId`、不同 `LocalId`。
- 默认工作集中的 Item 满足 `CommonId = LocalId`；非默认工作集中的 Item 满足 `CommonId != LocalId`。判断依据是 Item 的实际归属，不是查看入口。
- 共享 Tracker 的 Item 归属默认工作集；子工作集纳入共享 Tracker 后查看的是同一个 Item，不分配新身份。
- 两个无派生关系的 Item，即使类型或 Tracker 不同，也不允许拥有相同 `CommonId`；`CommonId` 在逻辑家族层面全局唯一。
- `CommonId` 与 `LocalId` 均为雪花 ID。单工作集 `CommonId` 唯一、同族只允许一个有效变体；软删占键，只能恢复或永久删除。
- 服务端分配：创建请求不携带 `CommonId`；默认工作集生成同一个雪花作为 `LocalId`/`CommonId`，非默认工作集生成两个不等雪花；派生沿用来源 `CommonId`、生成不等 `LocalId`。

## 来源、版本与 COW（现行语义）

| 术语 | 定义 |
| --- | --- |
| **Source-backed** | `materialized = false`；内容由 `sourceLocalId` + `sourceRevision` 指向的外部有效来源解析。 |
| **Local-backed** | `materialized = true`；`sourceLocalId = localId` 且 `sourceRevision = revision`，内容由自身业务聚合提供。 |
| **SourceLocalId** | **当前有效内容来源**的 `LocalId`，不是历史派生来源；物化后等于自身。 |
| **SourceRevision** | 当前有效内容来源的业务 Revision，不是不可变快照；物化后等于自身 Revision。 |
| **Materializing** | COW 的技术运行态，不构成第三种业务状态。 |
| **Lock Version（`lock_version`）** | 普通修改提交时递增的乐观锁版本；不等于 Business Revision。 |
| **Release Version（`version`）** | 发布大版本，字符串、可空；只在发布时出现。 |

已确认规则：

1. 派生只固定业务 Revision（Snapshot/Head 只用于选 Revision），不复制、不冻结普通字段内容；附件与关系固定为派生时状态，且不参与 Compare/Merge（R19、D6）。
2. 来源普通修改触发事件；目标集合在 Handler **消费时**按当前 `SourceLocalId`/`SourceRevision` 匹配且未物化的 LTI 确定，命中多少物化多少（扇出）。
3. 首次相关修改的修改前快照优先；事件重放/乱序不产生第二个有效物化结果；Local-backed 后不被来源事件覆盖。
4. `SourceLocalId` 直接指向实际拥有完整内容的变体，不逐级递归未物化中间变体；派生完成后已有下游不因中间上游物化而重定向。
5. 历史派生链（R0→R1）不是业务必需，不要求 LTI 保存；不引入 Manifest、ItemVariant、OriginLocalId 或谱系聚合。
6. 事件顺序由事件框架顺序记录、顺序消费兜底；`Source Change Order` 不是领域模型关注点。LTI 自身 Revision 与业务 BC 的 Revision 通过事件同步（细节留实现）。
7. 物化完成 = 业务 BC 保存成功 **且** LTI 切换成功；保存成功而切换失败不回滚业务内容，幂等重试只补未完成步骤。

以上规则的历史裁定见 [阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-008) 至 [D-018](../reviews/2026-11-09-stage-3-architecture-review.md#d-018)；完整属性与失败语义见 [T3 物化契约](lighttrackeritem-materialization-contract-v1.md)。

## 重点歧义

1. **Baseline** 至少有三义：仓库 Design Baseline、业务 Snapshot/历史切点、Merge Base。后续必须使用限定词。
2. **共享** 表达权威内容复用，不表达访问权限；“无权”不得偷换为“不存在”。
3. **已处理** 与内容相等分别表达：`KEEP_TARGET + markAsMerged=true` 可以保留真实差异；普通编辑只增 `lock_version`，不使已处理差异重新出现；任一侧显式升级业务 Revision 后，旧 Checkpoint 对该版本组合过时。
4. **Tracker 组织树**（`parentId`）与**Tracker 派生链**（`sourceId`）是两个维度；前者表达同工作集结构父，后者表达直接父级工作集来源。二者不互相替代。
5. **Working Set 谱系**（`parentWorkingSetId`）与 Tracker 派生链也是不同维度；Compare/Merge 只允许直属父子工作集。
6. **VIRTUAL** 是 V4 历史术语，现行模型用 Source-backed 表达“未物化但存在”；不得把“无本地内容行”当作不存在。
7. **TRACKER 共享特性**创建后不可变；分类的共享特性是出厂设定，不可修改。非共享分类允许创建共享或非共享 Tracker。

## 历史术语（仅追溯，不作为当前模型）

| 术语 | 状态 | 替代/说明 |
| --- | --- | --- |
| Tracker Branch | 停用 | D-001 排除独立 Branch 根；由 Tracker 独立聚合承担。 |
| Item（通用聚合） | Superseded | D-007 由 `TrackerItem` 公共契约 + 各 BC 业务聚合取代。 |
| Item Variant | Superseded | D-010/D-012 不引入；职责由 LightTrackerItem 承担。 |
| Manifest | 不采用 | D-010 最小模型收敛；Working Set 直接维护绑定结构。 |
| ComparisonToken（持久化冻结清单） | Superseded | 2026-11-15 compare 不持久化；决策与所选差异证据随 MergeRound 保存。 |
| Merge Session | Superseded | 由 MergeRound 承载轮次；Checkpoint 独立聚合。 |
| LRT／Long-Running Transaction | 术语收敛 | 现称 LRO（Long-Running Operation）或异步任务；不是数据库长事务。 |

## 示例对话

> **领域专家**：子工作集绑定 Shared Tracker 后，修改的是哪一份内容？
>
> **架构师**：默认工作集里的同一份权威内容；所有绑定工作集同时可见，不复制、不通过 Merge 同步。
>
> **领域专家**：非共享派生第一次修改时，`LocalId` 会变吗？
>
> **架构师**：不会。派生时已预分配 `LocalId`，首次修改只是从 Source-backed 切到 Local-backed；`CommonId` 也保持不变。
>
> **领域专家**：那 Compare 显示“无差异”，是否等于已经合并？
>
> **架构师**：不等于。内容相等和“已处理”是两件事；`KEEP_TARGET` 也可以确认已处理，但不伪造内容相等。
