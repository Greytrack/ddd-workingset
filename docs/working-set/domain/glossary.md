# Working Set 候选统一领域语言

**状态：Draft**

以下定义用于消除讨论歧义，不表示相应概念必然是实体、聚合或独立限界上下文。来源代号见 [证据登记](evidence-register.md)，可视化关系见 [候选领域概念关系图](../diagrams/domain-concept-relationships.md)。除 `CONCEPT-019/020` 所引用的企业架构外框外，其余领域定义仍待领域专家确认；企业外框已确认也不等于内部模型已确认。

| 编号 | 推荐术语与候选定义 | 歧义／应避免混用 | 关系与上下文差异 | 状态与来源 |
| --- | --- | --- | --- | --- |
| `CONCEPT-001` | **Working Set／工作集**：有身份和生命周期、选择 Tracker 并形成工作范围的工作上下文。 | 不等同 Git Branch；不预设拥有全部 Item。 | 在 Workspace Composition 中是选择清单；是否为单一大聚合待定。 | Reported；`EXT-ARCH §0/2.1`、`V4-UL §1` |
| `CONCEPT-002` | **Tracker**：按产品空间与分类组织受管对象并参与共享或派生谱系的集合节点。 | 历史材料同时称“分类投影”“条目容器”“版本线”；需确认哪个是业务本质。 | Tracker Lineage 关注身份与谱系；查询投影不是自动的领域所有权。 | Reported；`V4-OVERVIEW:5`、`V4-UL §1` |
| `CONCEPT-003` | **Shared Tracker**：多个 Working Set 选择同一权威 Tracker 与 Item 变体的协作语义。 | “共享”不等于所有用户有权；不等于复制后同步。 | 权威点查与列表／搜索投影可能有不同可见时效。 | Reported；`EXT-ARCH §2.1/11` |
| `CONCEPT-004` | **Non-shared Tracker**：纳入目标 Working Set 后可独立演进的 Tracker 派生节点。 | `UNSHARED` 不自动说明何时复制内容，也不证明必须存在 `TrackerBranch` 类型。 | 与来源的关系需要固定切点和合并方向。 | Reported；`EXT-ARCH §2.1`、`V4-DOMAIN:408-415` |
| `CONCEPT-005` | **Tracker Branch**：用于讨论 Tracker 独立演进线的候选术语。 | V4 明确取消独立 BranchId；其他资料仍使用“分支”。不能在确认前当成独立实体。 | 若采用，可作为 Tracker 谱系中的角色，而非必然新对象。 | Pending Confirmation；`V4-UL:12`、`EXT-ARCH §0` |
| `CONCEPT-006` | **Item**：由具体业务上下文拥有内容和业务生命周期的受管业务对象。 | 不等同索引行、LightTrackerItem 或任意属性 Map。 | Requirement、Bug 等上下文可有不同规则。 | Reported；`EXT-ARCH §4/5` |
| `CONCEPT-007` | **Item Family／Item Identity**：跨派生变体识别同一逻辑业务对象家族的身份。 | `CommonId` 的唯一作用域未确认；不能与具体变体身份混用。 | 可能需要 `namespace/type + commonId` 复合键。 | Candidate；`EXT-ARCH §1/2.1/12` |
| `CONCEPT-008` | **Item Variant／Branch-local Item**：某个非共享 Tracker 上具有稳定 LocalId 的具体变体。 | 不等于业务 revision；无本地内容行仍可能存在。 | V4 名称为 LightTrackerItem；报告建议用 ItemVariant 避免技术索引含义。 | Candidate；`EXT-ARCH §1/2.1`、`V4-DOMAIN:214` |
| `CONCEPT-009` | **Item Revision**：业务内容的已发布版次或业务修订身份。 | 不等同 LocalId、并发 version、事件序号或内容时间点。 | 各业务对象上下文拥有具体规则。 | Reported；`EXT-ARCH §2.2` |
| `CONCEPT-010` | **Source Cut／来源切点**：派生变体读取初始内容、生命周期及必要从属事实的固定历史依据。 | 不等同父对象当前值、捕获时间字符串或直接父变体。 | 需要历史能力保证可回读。 | Reported；`V4-DOMAIN:438/463`、`EXT-ARCH §2.1` |
| `CONCEPT-011` | **Copy-on-Write／写时复制**：既有 VIRTUAL 变体首次本地写入时建立本地权威内容、但保持逻辑身份稳定的转换。 | 不是首次创建 Item 身份；不等同整 Tracker 预复制。 | Item Variant 与业务对象上下文共同参与，事务边界待定。 | Reported；`V4-DOMAIN:545-548` |
| `CONCEPT-012` | **Baseline／基线**：用于固定比较、派生或发布依据的一组不可变、可回读证据。 | 与仓库的 Design Baseline、业务 Snapshot、Merge Base 三者必须分开命名。 | 本文建议：治理基线称 Design Baseline；合并共同依据称 Merge Base。 | Candidate；`EXT-ARCH §2/10` |
| `CONCEPT-013` | **Difference／差异**：在固定有向比较上下文和双侧证据上得到的记录存在性或字段内容差异。 | LocalId 不同、物化状态不同不自动构成业务差异。 | 相等规则由具体字段／业务上下文提供。 | Reported；`MERGE §4.1`、`EXT-ARCH §10.1` |
| `CONCEPT-014` | **Merge／合并**：把明确决定通过目标业务能力应用，并记录结果与处理事实的有向过程。 | 不是整行覆盖；不自动承诺整轮原子。 | Compare & Merge 负责决定，业务对象上下文负责目标合法性。 | Reported；`EXT-ARCH §10` |
| `CONCEPT-015` | **Merge Decision／合并决定**：对存在性或字段差异作出的 `ACCEPT / KEEP_TARGET / DEFER` 选择。 | 数据决定与 `markAsMerged` 处理确认分离。 | 决定的有效期受冻结证据变化约束。 | Reported；`MERGE §2/4.2` |
| `CONCEPT-016` | **Merge Base／合并基准**：比较两条演进线时双方共同认可的历史依据。 | 不能伪造为直接父子关系；V4 首期尚未正式建模。 | 兄弟／跨代合并需要此概念。 | Candidate；`EXT-ARCH §11.20/12 P2` |
| `CONCEPT-017` | **Reference／引用**：由引用方持有、指向逻辑目标且可选择钉住目标版次的关系。 | 不等同 Association；物理端点为空不表示无逻辑目标。 | Reference 上下文拥有身份、有效性和修复。 | Reported；`REFERENCE CONTEXT §关系与方向` |
| `CONCEPT-018` | **Association／关联**：表达两个业务对象之间业务联系的独立关系。 | 不应把引用属性与一般关联强行统一。 | 缺端点时是否落库与 Reference 不同。 | Reported；`REFERENCE CONTEXT §关系与方向` |
| `CONCEPT-019` | **Shared Kernel／共享内核**：本项目企业架构中由四个业务 BC 共同依赖的共享能力集合，包含 Working Set、Privilege、Workflow、Class Hierarchy。 | 不自动等同 DDD 书中协同维护少量共享模型的集成模式；也不等同单一聚合、模块或微服务。 | 企业归属由 `AC-001` 固定；内部治理和模型边界仍待设计。 | Confirmed constraint；`USER-ARCH-2026-11-09` |
| `CONCEPT-020` | **Business Bounded Context／业务限界上下文**：拥有自身业务对象语言、状态机和不变量的 GateFlow、ALM、DQA 或 PLM 上下文。 | 不应因共同调用 Working Set API 而把四个 BC 的专有规则合并进 Shared Kernel。 | 业务 BC 消费共享能力，并保留对自身内容变化的最终合法性裁决。 | Confirmed landscape / boundary candidate；`AC-001` |

## 重点歧义

1. **Baseline** 至少有三义：仓库设计基线、工作范围 Snapshot／历史切点、Merge Base。后续文档必须使用限定词。
2. **Tracker Branch** 是否存在独立业务身份未确认；当前统一使用“Tracker 派生节点”，仅在比较方案时保留该术语。
3. **Item**、**Item Family**、**Item Variant**、**Item Revision** 分别表达业务对象、家族、变体和内容版次，不得互换。
4. **共享**表达权威内容复用，不表达访问权限，也不承诺所有投影零延迟。
5. **已处理**不等于内容相等；`KEEP_TARGET + markAsMerged=true` 可保留真实差异但停止重复提示。
6. **Shared Kernel** 在本项目首先是已确认的企业架构归属；是否采用经典 DDD Shared Kernel 的共同代码／模型治理方式仍需另行说明。

## 候选对话检验

> 领域专家：子工作集选择 Shared Tracker 时，是看到同一个权威 Item，还是得到一份同步副本？
>
> 架构师：如果是同一权威 Item，就不应称为分支或通过 Merge 同步；权限和列表刷新时效仍需单独确认。
>
> 领域专家：Non-shared Tracker 中 Item 首次修改时，身份是否变化？
>
> 架构师：候选模型要求 Item Variant 身份保持不变，只把内容从固定 Source Cut 转为本地权威内容；这仍需业务确认。
