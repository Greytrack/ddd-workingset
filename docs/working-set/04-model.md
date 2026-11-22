# 领域模型与边界

**状态：Draft（各主题接受范围见 [BASELINE](01-baseline.md)）**

本页回答三个层次的问题：**聚合边界**（事务一致性）、**Context Map**（战略设计）、**Shared Kernel 的企业定位与各 BC 接入范围**。历史方案比较见[聚合边界竞争方案](archive/proposals/aggregate-boundary-options.md)与[阶段 3 评审](archive/reviews/2026-11-09-stage-3-architecture-review.md)。

## 1. 聚合与契约

聚合是事务一致性边界，不是页面装载范围或外键集合。

| 对象                                   | 定位                                           | 身份                                                                                           | 保护的不变量                                                                                                                                       | 跨聚合关系                                                      |
|----------------------------------------|------------------------------------------------|------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| **WorkingSet**                         | 核心聚合根                                     | `WorkingSetId`；默认根；`parentWorkingSetId` 单父                                              | 默认根不可删；单父谱系无环；SpaceBinding 前置；绑定唯一性；绑定移除/删除顺序                                                                       | ID 引用 Tracker、ProductSpace、LTI                              |
| **SpaceBinding**                       | WorkingSet 内 Entity                           | `(workingSetId, productSpaceId)`                                                               | 同一 WS/空间唯一；有 TrackerBinding 时不可移除                                                                                                     | 引用 ProductSpace                                               |
| **TrackerBinding**                     | WorkingSet 内 Entity                           | `(workingSetId, trackerId)`；槽位 `(WS, space, classification)` 可多 Tracker、至多一个有效默认 | 同一 Tracker 不重复纳入；空间一致；共享特性相容；默认资格由 Tracker 承担（T6-F）                                                                   | 引用 Tracker                                                    |
| **Tracker**                            | 独立聚合根、ProductSpace 下的第三级容器        | `TrackerId`                                                                                    | 业务原生 Tracker 只能在默认工作集创建；共享特性创建后不可变；`parentId` 创建后不可变；`sourceId` 不可变（T6-F）；删除受 Item/组织子级/派生子级约束 | ID 引用 ProductSpace、Classification、父 Tracker、来源 Tracker  |
| **LightTrackerItem**                   | 独立聚合根                                     | `LocalId`（变体）；`CommonId` 关联家族                                                         | `materialized` 三字段一致；COW 不改身份；Local-backed 后不被来源覆盖；单 WS 家族唯一                                                               | ID 引用 Tracker、WorkingSet、业务对象                           |
| **ProductSpace**                       | 独立聚合根                                     | `ProductSpaceId`                                                                               | 稳定归属身份；不随 WorkingSet 派生复制；删除前所有绑定为空                                                                                         | 被 SpaceBinding 引用                                            |
| **MergeRound**                         | 合并区域聚合根                                 | `MergeRoundId`；`requestId` 幂等                                                               | 一页 1～50 条决策；同 Tracker 对不并发；终态不可重开；唯一 `ApplyResult`                                                                           | 持有 MergeEntry（Entity）与决策；ID 引用 WorkingSet/Tracker/LTI |
| **MergeEntry**                         | MergeRound 内 Entity                           | 轮次内 `Entry.id`；双侧业务定位保存在证据中                                                    | 证据与决定绑定；终态后不可重执行                                                                                                                   | 引用 Difference 证据                                            |
| **合并确认**                           | MergeEntry 持有的值对象                        | 无独立身份；保存时沿用条目 ID                                                                  | 与轮次结果同事务；确认不证明内容相等                                                                                                               | 跨轮次只读查询按规范化 Tracker 对、家族和双侧版次命中           |
| **TrackerItem**                        | 公共契约（非聚合）                             | 无                                                                                             | 公共身份、Revision 与生命周期能力声明                                                                                                              | 业务 BC 聚合实现                                                |
| **BusinessObject（Working Set 契约）** | 公共能力接口（非聚合）                         | 无                                                                                             | `delete/undelete/delete2` 能力声明；实际规则由 BC 拥有。与 `com.airedgesoft.sharekernel.businessframework` 的同名通用接口不是同一概念             | `TrackerItem` 继承                                              |
| **Reference／Association**             | SK 关系聚合（WorkingSet 不拥有；本轮只发事件） | 业务关系聚合 ID                                                                                | 端点意图、钉版、有效性、修复；不参与 Compare/Merge                                                                                                 | 关系服务消费事件；引用业务对象/变体                             |
| **WorkingSetCreation**                 | LRO 载体聚合                                   | `creationId`；`requestId` 幂等                                                                 | 任务状态机、范围快照、部分成功明细；不拥有 WorkingSet/Tracker/LTI                                                                                  | ID 引用父/目标 WS                                               |

完整业务内容与状态机由各业务 BC 的聚合负责。Working Set 的聚合之间按身份引用。跨 BC 写入见 [T4](05-contracts/t4-cross-bc.md)。

**容器层级与 Folder Tracker**：Working Set 是一级容器，ProductSpace 是二级容器，Tracker
是三级容器；Requirement、Test、Material、Review、Folder 等业务对象位于各自 Tracker 下。应用流程默认纳入文件夹分类。Tracker
执行通用的容器规则；Folder 的生命周期由业务 BC 管理。见 [ADR-0008](06-decisions/adr-0008-tracker-third-level-container.md)。

## 2. 非聚合的共享能力

`com.airedgesoft.sharekernel.businessframework` 是 Shared Kernel 的通用业务对象、版次、属性与排序能力，不是 Working Set
聚合，也不自动拥有 Working Set 的身份或范围语义。它是一个可被多个 BC 复用的能力模块；其 `BusinessObject`/`Revisionable` 契约与
`com.airedgesoft.sharekernel.workingset.domain.TrackerItem` 的公共工作集契约不是同一概念，不能互相替代。

共享能力与业务聚合的边界：

- `businessframework` 不拥有 Working Set、Tracker、LightTrackerItem、ProductSpace 或业务 BC 的状态；
- 业务 BC 可以使用共享能力实现自身聚合，但聚合身份、不变量、状态机和持久化仍归该 BC；
- 不得把 `businessframework` 的通用类型当作跨 BC 的通用写模型，也不得用它绕过业务 BC 的整体校验。

## 3. 一致性边界

- `TC-002` 已由用户于 2026-11-19 明确确认为架构硬约束：一个写事务最多修改一个聚合，跨聚合变更通过可靠事件与独立事务最终收敛。不是业务不变量，也不得再作为可选实现策略。执行规则及反模式清单见 [AGENTS §7.2/7.4](../../AGENTS.md)，偏差见[反模式审查](archive/reviews/2026-11-19-ddd-anti-pattern-scan.md)。
- 业务对象保存与 LTI 切换是双事实：禁止跨聚合同一写事务；业务保存成功而切换失败不回滚；恢复及重投见 [T3](05-contracts/t3-lti-materialization.md)。
- 业务 BC 可以只读使用 SK 共享聚合/仓储做身份和来源校验；不得直接保存、删除或修改 SK
  聚合，也不得把直接读取当作跨聚合原子校验。跨聚合写入仍通过事件和独立事务完成（D4，2026-11-19 澄清）。
- 首次继承编辑由 ALM 保存后发布 `BusinessObjectUpdateEvent`，SK 的 `TrackerItemLocalizedListener` 在独立事务切换
  LTI；业务保存成功不等于 LTI 已立即切换。
- Merge 条目业务应用在目标 BC 独立事务提交；LTI 切换由 SK 消费 `MergeAppliedEvent` 在独立事务完成（D15-B）。
- 工作集创建任务按步骤/批次独立提交，不把全流程放进一个大事务；部分成功保留明细（T7）。

## 4. Context Map（战略设计）

本页区分业务能力、子域、限界上下文和部署。它不是微服务拆分建议，也不把每个名词机械变成上下文。

### 4.1 已确认的企业架构外框

`AC-001` 固定以下外部边界：GateFlow、ALM、DQA、PLM 是四个业务 Bounded Context；它们共同依赖企业 Shared Kernel。Shared Kernel
包含 Working Set、Privilege、Workflow、Class Hierarchy 等共享能力，本项目聚焦 Working Set 及其必要的局部协作。

该外框见 [Shared Kernel 与四个业务 BC 图](10-diagrams/shared-kernel-context-map.md)。它不证明 Working Set 内部的
`CTX-001` 至 `CTX-009` 都是独立限界上下文；这些编号只是候选模型边界，可能在后续合并为更少的上下文或模块。

### 4.2 能力与子域

| 业务能力                                | 候选子域定位                       | 说明                                                                                                              |
|-----------------------------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| 组合并发布工作范围                      | 核心域                             | 用户通过 Working Set 选择可工作的范围                                                                             |
| Tracker 共享、派生与谱系治理            | 核心域                             | 解释共享与独立演进                                                                                                |
| TrackerItem 公共契约与 LightTrackerItem | 核心域契约 + 独立聚合              | TrackerItem 是公共契约（D-007 已确认）；LightTrackerItem 是独立聚合与身份/来源权威（D-008 已确认），物化协议见 T3 |
| 具体业务对象生命周期                    | GateFlow、ALM、DQA、PLM 各自业务域 | 各 BC 拥有自身业务对象的内容、状态机和合法性                                                                      |
| Compare／Merge                          | 核心域候选                         | 需要有向证据、决定与可审计结果                                                                                    |
| Reference／Association                  | 核心邻域候选                       | 关系有独立语义、钉版和悬空状态                                                                                    |
| 历史／Snapshot                          | 支撑域，但可能是产品能力           | 是 COW、Compare 和审计的上游能力                                                                                  |
| Class Hierarchy、Privilege、Workflow    | Shared Kernel 内的其他共享能力     | 与 Working Set 的精确模型边界和调用合同待确认                                                                    |
| 搜索、审计                              | 通用／支撑域                       | 不应成为命令侧领域权威                                                                                            |

### 4.3 候选限界上下文

| 编号        | 候选上下文                                       | 拥有的语言与责任                                                                        | 不拥有                                             | 状态／主要风险                                              |
|-------------|--------------------------------------------------|-----------------------------------------------------------------------------------------|----------------------------------------------------|-------------------------------------------------------------|
| `CTX-001`   | Workspace Composition                            | Working Set、SpaceBinding/TrackerBinding、创建工作范围                                  | Tracker 谱系、Item 内容、关系                      | Candidate；`QUESTION-012` 未决（`QUESTION-001` 已关闭）     |
| `CTX-002`   | Tracker Lineage                                  | Tracker 身份、Sharing Mode、来源谱系、生命周期                                          | Working Set 的选择、完整 Item 内容                 | Candidate；可能与 CTX-001 同部署但语言边界应可见            |
| `CTX-003`   | Item Variant & Materialization                   | LightTrackerItem 已确认独立聚合（身份/来源/有效变体定位权威）、COW 延迟物化、Source Cut | Requirement/Bug 的业务字段与状态机（业务 BC 拥有） | LightTrackerItem 已确认聚合（D-008）；物化协议/权威细节候选 |
| `CTX-004`   | GateFlow／ALM／DQA／PLM Business Object Contexts | 各 BC 的 Item 内容、revision、状态机、整体合法性                                        | Working Set 私有索引、对其他 BC 的通用字段覆盖     | 企业四 BC 外框 Confirmed；内部对象合同 Candidate            |
| `CTX-005`   | Temporal History & Snapshot                      | 固定切点读取、保留、墓碑、Snapshot                                                      | 当前业务写规则                                     | Candidate；能力是否足够尚未验证                             |
| `CTX-006`   | Compare & Merge                                  | ComparisonContext、Difference、Decision、Round、Apply Result、Checkpoint                | 直接修改任意业务表                                 | Candidate；通过 CTX-004 的公开写能力应用                    |
| `CTX-007`   | Reference & Association                          | 关系身份、方向、钉版、悬空、修复                                                        | Item 内容与工作范围发布                            | Candidate；Reference 与 Association 是否同上下文仍可拆分    |
| `CTX-008`   | Class Hierarchy shared capability                | 分类元模型及规则                                                                        | Working Set/Tracker 生命周期、业务对象状态机       | Shared Kernel 内已知兄弟能力；内部边界 Candidate            |
| `CTX-009`   | Provisioning Process                             | 创建、拓展、替换、取消、恢复进度                                                        | 领域对象合法性                                     | 应用流程，不默认提升为领域上下文/聚合                       |
| `SKCAP-001` | Privilege shared capability                      | 提供权限事实与治理能力                                                                  | 把“无权”改写为“不存在”                             | Shared Kernel 内已知兄弟能力；交互 Candidate                |
| `SKCAP-002` | Workflow shared capability                       | 提供共享工作流能力或事实                                                                | 各业务 BC 的全部专有状态机                         | Shared Kernel 内已知兄弟能力；交互 Candidate                |

### 4.4 候选关系

- `CTX-001 → CTX-002`：Workspace 选择 Tracker 身份；谁是上游取决于“创建 Tracker”和“纳入 Tracker”的能力边界。
- `CTX-002 → CTX-003`：谱系提供派生身份与 Source Cut；不得把 Tracker 对象图组合进 LightTrackerItem。
- `CTX-003 ↔ CTX-004`：LightTrackerItem 公共身份与业务对象内容是双事实风险点。业务 BC 通过 TrackerItem 契约提供公共身份；候选方案是
  Published Language + 可靠事实事件，而不是共享可变对象。
- `CTX-005` 是 `CTX-001/003/006/007` 的共同上游历史能力；历史不可用必须返回明确失败，不得读当前值替代。
- `CTX-006 → CTX-004`：Merge 是业务对象上下文的客户，只能请求合法业务变化。
- `CTX-007` 消费范围发布和合并成功事实进行最终一致修复；删除守卫在投影滞后时需同步核验。
- `CTX-008 → CTX-002/004`：Working Set 与业务 BC 通过稳定 Published Language 或 ACL 消费分类规则，不共享内部对象图。
- `SKCAP-001 → Working Set 对外能力`：候选为提供授权事实；权限拒绝不得持久化成对象不存在。
- `SKCAP-002 ↔ CTX-004`：共享工作流与业务专有状态机的边界尚未确认，Working Set 不应代替业务 BC 判断专有状态转换。
- `GateFlow/ALM/DQA/PLM → Working Set`：四个 BC 消费公共工作集能力；统一入口不代表统一业务校验。

### 4.5 软件与部署边界

用户已确认四个业务 BC 的合并职责统一采用各自校验整个对象并决定提交的方式，专有校验规则由各 BC 保有。Working Set
管理合并选择、执行协调及结果记录。确认依据见 §5。

限界上下文不自动等于 Java 模块或微服务。企业 Shared Kernel 也不自动等于一个聚合或一个部署单元。`CTX-001/002/003` 可以先在
Working Set 能力内部保持模型边界；`CTX-009` 是应用编排角色。是否拆服务需要团队、可用性、吞吐和发布耦合证据，本阶段不决定。

代码模块与候选上下文不是一一对应关系；实际包职责和差距见[当前实现边界](07-implementation.md)。

### 4.6 阶段 3 收敛建议

依据 §5 与[边界场景](09-scenarios.md#第二部分shared-kernel-边界场景b01b12)，暂推荐一个 Working Set 模型边界内保留多个模型区域，不把 CTX
编号当作已成立的 BC：

| 区域                          | 当前分类与理由                                                         | 升格独立 BC 所需证据                                     |
|-------------------------------|------------------------------------------------------------------------|----------------------------------------------------------|
| Composition、Lineage、Variant | 内部领域模块候选；共用身份与范围语言，尚无独立团队/治理证据            | 独立语言、政策和生命周期的真实分歧；跨边界合同可独立演进 |
| Compare & Merge               | 独立模型区域候选；决定及确认随 MergeRound，当前基准由跨轮次查询提供    | 独立审阅治理、保留政策和团队所有权                       |
| History                       | 历史端口/支撑能力；Snapshot 若用户命名、保留、发布才可能有领域生命周期 | Snapshot 管理用例、保留责任、历史覆盖证明                |
| Reference/Association         | 关系规则归属待按类型确认；技术端点修复不是独立 BC 的充分理由           | 关系有独立业务所有者及不变量；不可复制 BC 专有关系规则   |
| Provisioning                  | 持久化应用流程                                                         | 不因有任务表或可恢复进度而成为领域 BC                    |

企业图仍保留 AC-001 的消费依赖箭头。运行时 SK 调用 BC 适配端口不代表 SK 核心反向引用业务实现：公共协议由共享能力和四 BC
共同评审，BC 实现适配器，组装层注册。BC 是业务内容、历史事实和写入结果的上游；SK 是工作范围/身份/证据协议上游。双向协作必须按不同事实标注上下游，避免同步递归调用。

候选集成：公共身份/证据/结果使用 Published Language，BC 的 ACL 转译专有语言；读取与写入按 [T4](05-contracts/t4-cross-bc.md)
区分：读取不取得写入权，跨聚合状态变化通过可靠事件在各自事务提交（TC-002）；已提交事实推进进度与修复，应用意图不称完成事实。

治理建议：共同批准公共语言，语义变化显式版本化，适配器声明支持能力和证据模式版本，先兼容后弃用；各 BC
可在兼容窗口内不同步升级。谁批准、兼容期限、是否允许双版本并存未确定（`QUESTION-014`）。共同发布是备选，成本是四 BC 被迫同步升级；同属
Shared Kernel 不足以证明应共同发布。本页的候选上下文表与[企业 Shared Kernel 图](10-diagrams/shared-kernel-context-map.md)
共同说明本建议；不决定部署单元或微服务。

## 5. Shared Kernel 边界

### 5.1 已确认约束

`AC-001`：Working Set 位于企业 Shared Kernel 内，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含
Privilege、Workflow、Class Hierarchy。该定位不在本项目方案空间内。

可视化见 [Shared Kernel 与四个业务 BC](10-diagrams/shared-kernel-context-map.md)，依据见[证据登记](archive/domain/evidence-register.md)。

### 5.2 设计原则

Working Set 管理工作范围、用户选择、执行协调与结果；业务对象的状态机、字段规则及整体合法性由所属 BC
校验并提交。公共契约统一协作语言、证据与结果，不统一四个 BC 的内部模型。

源码包的职责、读取边界与待整改项统一见[当前实现边界](07-implementation.md)。包拆分不改变 `AC-001`，也不代表新增限界上下文；跨
BC 读写契约见 [T4](05-contracts/t4-cross-bc.md)。

### 5.3 PLM Part（已确认）

<a id="plm-part-的接入范围用户已确认"></a>

Part 通过共享 Tracker 接入 Working Set。PLM 在默认工作集下独立管理 Part，其他工作集可纳入该共享 Tracker 查看共享数据。Part
不参与工作集派生、比对和合并。

### 5.4 GateFlow Process 与版次（已确认）

<a id="gateflow-process-与版次用户已确认"></a>

- Process 是 GateFlow 的核心聚合，一个 Process 有多个版本；一个版次对应一个 Working Set，父子工作集链构成版次链。
- 内容范围固定为四类：过程元素业务对象、裁剪问题、裁剪答案、合规映射；不纳入 Part 等共享 Tracker。
- GateFlow 支持从历史版次取全部内容替换叶子草稿版次内容；替换后保留草稿的版次身份、工作集 ID 与原父级。
- 替换准备期间继续使用当前完整内容并允许编辑；全部准备成功后整体切换，期间编辑可被覆盖；准备失败保留当前内容及已保存编辑。
- 替换是 GateFlow 的独立合同，不是直接父子 Merge 准入的例外；旧元素物理清理、外部引用与切换瞬间并发协议由 GateFlow 专项继续设计。

写入屏障、身份再生等候选未采纳；现行语义以本节和关联场景为准。详见 [场景集](09-scenarios.md)。

### 5.5 DQA 共享查看（已确认）

<a id="dqa-共享查看范围用户已确认"></a>

DQA 自行管理质量业务逻辑，其 Tracker 全部为共享 Tracker。Working Set 的唯一结合点是纳入查看；DQA 的对象不参与比对、合并或非共享派生。

八类对象：技术评审任务、过程保证任务、问题报告 PR、检查单、检查单模板、批注、评审文档（被评审对象）、评审报告（评审产物）。具体
Tracker 与类型数量对应关系按现有模型处理。

### 5.6 Item 创建入口与归属（已确认）

<a id="item-创建入口与归属用户已确认"></a>

- 子工作集禁止创建 Tracker。
- 子工作集可在已有非共享 Tracker 下原生新建 Item。
- 共享 Tracker 下从子工作集入口新建的 Item 实际在默认工作集创建；请求可来自任意绑定工作集。
- Working Set 解析实际创建归属，再交所属业务 BC 校验并创建合法对象；公共能力入口与生命周期归属分开表达。

### 5.7 比对与合并准入（已确认）

<a id="比对与合并的关系准入用户已确认"></a>

Compare/Merge 只允许直接父子工作集之间双向进行，支持新增、删除、修改；接收方按用户选择产生变化，由接收方 BC
校验并提交。兄弟、跨代不允许直接操作。无论接收方发生什么变动，均以用户合并决策为准；业务 BC 的合法性拒绝仍保留未完成结果。

Part 与 DQA 共享查看范围不进入 Compare/Merge；GateFlow 历史内容替换使用独立合同。

### 5.8 Shared Tracker 的修改与绑定（已确认）

Shared Tracker 自身及其 Item 的新增、修改实际发生在默认工作集；子工作集可以作为入口，但不形成独有副本；所有绑定工作集同时可见。

Shared Tracker 从子工作集移除仅解除 TrackerBinding，聚合保留；即使存在 Item 也允许解绑。原“加入后一律禁止移除”的政策已由
`POL-014` 修订，不能与现行规则并存。

### 5.9 分类与共享特性（已确认）

<a id="分类配置与-tracker-共享特性用户已确认"></a>

分类的共享特性是固定出厂设定，不允许修改，与是否已创建 Tracker 无关。Tracker 的共享特性创建时确定，后续不可变。非共享分类允许创建共享或非共享
Tracker；共享分类只能创建共享 Tracker。

### 5.10 跨 BC 契约

公共能力、两层接口、九分类失败语义与事件协作统一见 [T4 跨 BC 契约](05-contracts/t4-cross-bc.md)。本节只定义各 BC
的接入范围；契约版本治理仍为开放项。

### 5.11 历史材料

阶段 3.0 的责任模式 A/B/C 比较、候选业务契约与“仍需确认”表已由 T4
收敛；不再作为当前设计入口。需要追溯时见[阶段 3 评审](archive/reviews/2026-11-09-stage-3-architecture-review.md)
与 [T4 评审](archive/reviews/2026-11-11-t4-cross-bc-review.md)。

## 6. 未决与需验证

| 项                                 | 现状                                               | 影响                                             | 关联                  |
|------------------------------------|----------------------------------------------------|--------------------------------------------------|-----------------------|
| WorkingSet/Binding 规模与并发上限  | 未采样；当前单聚合装载全部绑定                     | 可能否定当前聚合装载假设，触发 Manifest/分片方案 | `QUESTION-012`        |
| Reference/Association 的消费与守卫 | WorkingSet 侧事件已实现；关系服务归属/消费守卫未定 | T7-E 表与端口                                    | `QUESTION-010`、T7-E  |
| 创建任务收尾                       | 保留、取消与补齐待定                               | 终态后的任务治理                                 | `QUESTION-017`        |
| 权限接入                           | 现阶段不接入；`Caller`/`authorize` 占位已移除      | 待权限模型完成后重新设计接入边界（D11）          | `QUESTION-003`        |
| SK 契约版本治理                    | 团队开放项                                         | 版本协商与兼容窗口                               | `QUESTION-014`        |
| 业务 Baseline 含义                 | 未裁定                                             | 影响不可变性与保留语义                           | `QUESTION-011`        |
