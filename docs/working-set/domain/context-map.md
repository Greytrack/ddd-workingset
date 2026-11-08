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
| Item 变体身份、固定来源与 COW | 核心／支撑域待定 | 业务显著性取决于 `QUESTION-004/006` |
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
| `CTX-003` | Item Variant & Materialization | Item Family/Variant、Source Cut、VIRTUAL/INSTANTIATED、COW 协议 | Requirement/Bug 的业务字段与状态机 | Candidate；可能只是技术索引，取决于 `QUESTION-004/006` |
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
- `CTX-002 → CTX-003`：谱系提供派生身份与 Source Cut；不得把 Tracker 对象图组合进 Item Variant。
- `CTX-003 ↔ CTX-004`：变体身份与业务内容是双事实风险点。候选方案是 Published Language + 可靠事实事件，而不是共享可变对象。
- `CTX-005` 是 `CTX-001/003/006/007` 的共同上游历史能力；历史不可用必须返回明确失败，不得读当前值替代。
- `CTX-006 → CTX-004`：Merge 是业务对象上下文的客户，只能请求合法业务变化。
- `CTX-007` 消费范围发布和合并成功事实进行最终一致修复；删除守卫在投影滞后时需同步核验。
- `CTX-008 → CTX-002/004`：Working Set 与业务 BC 通过稳定 Published Language 或 ACL 消费分类规则，不共享内部对象图。
- `SKCAP-001 → Working Set 对外能力`：候选为提供授权事实；权限拒绝不得持久化成对象不存在。
- `SKCAP-002 ↔ CTX-004`：共享工作流与业务专有状态机的边界尚未确认，Working Set 不应代替业务 BC 判断专有状态转换。
- `GateFlow/ALM/DQA/PLM → Working Set`：四个 BC 消费公共工作集能力；统一入口不代表统一业务校验。

## 软件与部署边界

限界上下文不自动等于 Java 模块或微服务。企业 Shared Kernel 也不自动等于一个聚合或一个部署单元。`CTX-001/002/003` 可以先在 Working Set 能力内部保持模型边界；`CTX-009` 是应用编排角色。是否拆服务需要团队、可用性、吞吐和发布耦合证据，本阶段不决定。
