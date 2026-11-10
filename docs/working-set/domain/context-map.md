# Working Set 候选 Context Map

**状态：Draft — Candidate Strategic Design**

本页区分业务能力、子域、限界上下文和部署。它不是微服务拆分建议，也不把每个名词机械变成上下文。依据见 [证据登记](evidence-register.md)，关键不确定性见 [开放问题](open-questions.md)，可视化协作方向见 [候选 Context Map 图](../diagrams/candidate-context-map.md)。

## 已确认的企业架构外框

`AC-001` 固定以下外部边界：GateFlow、ALM、DQA、PLM 是四个业务 Bounded Context；它们共同依赖企业 Shared Kernel。Shared Kernel 包含 Working Set、Privilege、Workflow、Class Hierarchy 等共享能力，本项目聚焦 Working Set 及其必要的局部协作。

该外框见 [Shared Kernel 与四个业务 BC 图](../diagrams/shared-kernel-context-map.md)。它不证明 Working Set 内部的 `CTX-001` 至 `CTX-009` 都是独立限界上下文；这些编号只是候选模型边界，可能在后续合并为更少的上下文或模块。

## 能力与子域

| 业务能力 | 候选子域定位 | 说明 |
| --- | --- | --- |
| 组合并发布工作范围 | 核心域 | 用户通过 Working Set 选择可工作的范围 |
| Tracker 共享、派生与谱系治理 | 核心域 | 解释共享与独立演进 |
| TrackerItem 公共契约与 LightTrackerItem 索引 | 核心域契约 + 候选聚合 | TrackerItem 是公共契约（D-007 已确认）；LightTrackerItem 边界与权威性待定 |
| 具体业务对象生命周期 | GateFlow、ALM、DQA、PLM 各自业务域 | 各 BC 拥有自身业务对象的内容、状态机和合法性 |
| Compare／Merge | 核心域候选 | 需要有向证据、决定与可审计结果 |
| Reference／Association | 核心邻域候选 | 关系有独立语义、钉版和悬空状态 |
| 历史／Snapshot | 支撑域，但可能是产品能力 | 是 COW、Compare 和审计的上游能力 |
| Class Hierarchy、Privilege、Workflow | Shared Kernel 内的其他共享能力 | 与 Working Set 的精确模型边界和调用合同待阶段 3.0 确认 |
| 搜索、审计 | 通用／支撑域 | 不应成为命令侧领域权威 |

## 候选限界上下文

| 编号 | 候选上下文 | 拥有的语言与责任 | 不拥有 | 状态／主要风险 |
| --- | --- | --- | --- | --- |
| `CTX-001` | Workspace Composition | Working Set、Manifest/Binding、准备与发布范围 | Tracker 谱系、Item 内容、关系 | Candidate；`QUESTION-001/012` 未决 |
| `CTX-002` | Tracker Lineage | Tracker 身份、Sharing Mode、来源谱系、生命周期 | Working Set 的选择、完整 Item 内容 | Candidate；可能与 CTX-001 同部署但语言边界应可见 |
| `CTX-003` | Item Variant & Materialization | LightTrackerItem 已确认独立聚合（身份/来源/有效变体定位权威）、COW 延迟物化、Source Cut | Requirement/Bug 的业务字段与状态机（业务 BC 拥有） | LightTrackerItem 已确认聚合（D-008）；物化协议/权威细节候选 |
| `CTX-004` | GateFlow／ALM／DQA／PLM Business Object Contexts | 各 BC 的 Item 内容、revision、状态机、整体合法性 | Working Set 私有索引、对其他 BC 的通用字段覆盖 | 企业四 BC 外框 Confirmed；内部对象合同 Candidate |
| `CTX-005` | Temporal History & Snapshot | 固定切点读取、保留、墓碑、Snapshot | 当前业务写规则 | Candidate；能力是否足够尚未验证 |
| `CTX-006` | Compare & Merge | ComparisonContext、Difference、Decision、Round、Apply Result、Checkpoint | 直接修改任意业务表 | Candidate；通过 CTX-004 的公开写能力应用 |
| `CTX-007` | Reference & Association | 关系身份、方向、钉版、悬空、修复 | Item 内容与工作范围发布 | Candidate；Reference 与 Association 是否同上下文仍可拆分 |
| `CTX-008` | Class Hierarchy shared capability | 分类元模型及规则 | Working Set/Tracker 生命周期、业务对象状态机 | Shared Kernel 内已知兄弟能力；内部边界 Candidate |
| `CTX-009` | Provisioning Process | 创建、拓展、替换、取消、恢复进度 | 领域对象合法性 | 应用流程，不默认提升为领域上下文/聚合 |
| `SKCAP-001` | Privilege shared capability | 提供权限事实与治理能力 | 把“无权”改写为“不存在” | Shared Kernel 内已知兄弟能力；交互 Candidate |
| `SKCAP-002` | Workflow shared capability | 提供共享工作流能力或事实 | 各业务 BC 的全部专有状态机 | Shared Kernel 内已知兄弟能力；交互 Candidate |

## 候选关系

- `CTX-001 → CTX-002`：Workspace 选择 Tracker 身份；谁是上游取决于“创建 Tracker”和“纳入 Tracker”的能力边界。
- `CTX-002 → CTX-003`：谱系提供派生身份与 Source Cut；不得把 Tracker 对象图组合进 LightTrackerItem。
- `CTX-003 ↔ CTX-004`：LightTrackerItem 公共身份与业务对象内容是双事实风险点。业务 BC 通过 TrackerItem 契约提供公共身份；候选方案是 Published Language + 可靠事实事件，而不是共享可变对象。
- `CTX-005` 是 `CTX-001/003/006/007` 的共同上游历史能力；历史不可用必须返回明确失败，不得读当前值替代。
- `CTX-006 → CTX-004`：Merge 是业务对象上下文的客户，只能请求合法业务变化。
- `CTX-007` 消费范围发布和合并成功事实进行最终一致修复；删除守卫在投影滞后时需同步核验。
- `CTX-008 → CTX-002/004`：Working Set 与业务 BC 通过稳定 Published Language 或 ACL 消费分类规则，不共享内部对象图。
- `SKCAP-001 → Working Set 对外能力`：候选为提供授权事实；权限拒绝不得持久化成对象不存在。
- `SKCAP-002 ↔ CTX-004`：共享工作流与业务专有状态机的边界尚未确认，Working Set 不应代替业务 BC 判断专有状态转换。
- `GateFlow/ALM/DQA/PLM → Working Set`：四个 BC 消费公共工作集能力；统一入口不代表统一业务校验。

## GateFlow 的上下文语言（已确认）

GateFlow 的 Process 聚合具有多个版次，每个版次对应一个 Working Set，父子工作集链表达版次链。GateFlow 定义叶子草稿的资格及历史内容替换业务含义，Working Set 提供相应范围协作机制。替换的业务可见性已确认为准备期间显示当前完整内容并允许编辑，全部准备成功后整体切换，期间编辑允许被覆盖。工作集内容固定为过程元素业务对象、裁剪问题、裁剪答案、合规映射四类，不纳入共享 Tracker。其内部业务细节由 GateFlow 自身设计，当前聚焦工作集协作契约。详见 [GateFlow 边界](shared-kernel-boundary.md#gateflow-process-与版次用户已确认)。

## DQA 的上下文边界（已确认）

DQA 拥有其质量业务对象的模型及业务逻辑。Working Set 仅纳入其共享 Tracker 供查看，DQA 不消费工作集比对、合并及非共享派生能力。八类对象见 [DQA 共享查看范围](shared-kernel-boundary.md#dqa-共享查看范围用户已确认)。

## 软件与部署边界

用户已确认四个业务 BC 的合并职责统一采用各自校验整个对象并决定提交的方式，专有校验规则由各 BC 保有。Working Set 管理合并选择、执行协调及结果记录。确认依据见 [职责边界](shared-kernel-boundary.md)。具体对象接入范围、适配端口与内部模型边界继续设计。

限界上下文不自动等于 Java 模块或微服务。企业 Shared Kernel 也不自动等于一个聚合或一个部署单元。`CTX-001/002/003` 可以先在 Working Set 能力内部保持模型边界；`CTX-009` 是应用编排角色。是否拆服务需要团队、可用性、吞吐和发布耦合证据，本阶段不决定。

## 阶段 3 收敛建议

依据 [边界比较](shared-kernel-boundary.md) 和 [边界场景](../scenarios/shared-kernel-boundary-scenarios.md)，暂推荐一个 Working Set 模型边界内保留多个模型区域，不把 CTX 编号当作已成立的 BC：

| 区域 | 当前分类与理由 | 升格独立 BC 所需证据 |
| --- | --- | --- |
| Composition、Lineage、Variant | 内部领域模块候选；共用身份与范围语言，尚无独立团队/治理证据 | 独立语言、政策和生命周期的真实分歧；跨边界合同可独立演进 |
| Compare & Merge | 独立模型区域候选；Decision/Checkpoint 有独立生命周期，但可先同上下文 | 独立审阅治理、保留政策和团队所有权 |
| History | 历史端口/支撑能力；Snapshot 若用户命名、保留、发布才可能有领域生命周期 | Snapshot 管理用例、保留责任、历史覆盖证明 |
| Reference/Association | 关系规则归属待按类型确认；技术端点修复不是独立 BC 的充分理由 | 关系有独立业务所有者及不变量；不可复制 BC 专有关系规则 |
| Provisioning | 持久化应用流程 | 不因有任务表或可恢复进度而成为领域 BC |

企业图仍保留 AC-001 的消费依赖箭头。运行时 SK 调用 BC 适配端口不代表 SK 核心反向引用业务实现：公共协议由共享能力和四 BC 共同评审，BC 实现适配器，组装层注册。BC 是业务内容、历史事实和写入结果的上游；SK 是工作范围/身份/证据协议上游。双向协作必须按不同事实标注上下游，避免同步递归调用。

候选集成：公共身份/证据/结果使用 Published Language，BC 的 ACL 转译专有语言；同步端口用于定位后的授权读取及单对象提交；已提交范围/内容事实用于异步进度和修复，应用意图是命令，不称事实事件。

治理建议：共同批准公共语言，语义变化显式版本化，适配器声明支持能力和证据模式版本，先兼容后弃用；各 BC 可在兼容窗口内不同步升级。谁批准、兼容期限、是否允许双版本并存未确定（QUESTION-014）。共同发布是备选，成本是四 BC 被迫同步升级；同属 Shared Kernel 不足以证明应共同发布。

[内部图](../diagrams/candidate-context-map.md)、[企业图](../diagrams/shared-kernel-context-map.md)、[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md) 共同说明本建议；不决定部署单元或微服务。
