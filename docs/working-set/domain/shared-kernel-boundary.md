# Working Set Shared Kernel 边界

**状态：Draft（已确认组织约束与 BC 边界；跨 BC 交互结论以 T4 契约为准）**

## 已确认约束

`AC-001`：Working Set 位于企业 Shared Kernel 内，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。该定位不在本项目方案空间内。

可视化见 [Shared Kernel 与四个业务 BC](../diagrams/shared-kernel-context-map.md)，依据见[证据登记](evidence-register.md)。

## 设计原则

四个业务 BC 对参与 Working Set 的对象**统一负责校验整个业务对象并决定是否提交**，各自保留专有校验规则；Working Set 负责用户选择、执行协调与结果记录。共用 API 只统一协作语言、证据和结果结构，不假定四类对象具有相同领域行为。

> Working Set Shared Kernel 管理跨四个业务 BC 共同成立的工作集语义和通用机制；业务对象的专有状态机、字段规则和整体合法性仍由对应业务 BC 拥有。

### SK 内部模块（实现边界）

当前 `sharedkernel` 源码按以下包组织。它们是 Shared Kernel 的内部模块，不自动升级为独立限界上下文或微服务，也不改变 `AC-001` 的企业边界：

| 包 | 地位 | 当前约束 |
| --- | --- | --- |
| `sk.businessframework` | Shared Kernel 的通用业务对象/版次/属性能力 | 不是 Working Set 业务模型，不得反向依赖各 BC；`validation` 子包存在迁移期旧栈依赖例外。 |
| `sk.tracker` | Tracker 聚合及其命令/查询/事件处理 | Tracker 是独立聚合；当前仍使用 `sk.workingset` 的 WorkingSet/Classification 类型，包拆分不等于已完成跨 BC 契约收敛。 |
| `sk.productspace` | ProductSpace 聚合及其命令/仓储 | ProductSpace 是独立聚合；产品空间创建的默认工作集初始化仍存在跨聚合同事务，见[实现边界](../implementation/current-boundaries.md)。 |
| `sk.workingset` | WorkingSet、LightTrackerItem、COW、创建 LRO、Compare/Merge、关系与查询 Provider | Working Set 核心能力；命令仓储与查询 Provider 分开。 |

**读取与写入**：业务 BC 可以只读使用 SK 共享聚合/仓储完成身份和来源校验；不得直接保存、删除或修改 SK 聚合。跨聚合状态变化通过可靠事件和独立事务完成（TC-002）。单个查询方法不再新增同步 Port，集合读取使用 `application/query/provider`。详见 [T4 D4](cross-bc-contract-v1.md) 和[当前实现边界](../implementation/current-boundaries.md)。

<a id="plm-part-的接入范围用户已确认"></a>
## PLM Part（已确认）

Part 通过共享 Tracker 接入 Working Set。PLM 在默认工作集下独立管理 Part，其他工作集可纳入该共享 Tracker 查看共享数据。Part 不参与工作集派生、比对和合并。

<a id="gateflow-process-与版次用户已确认"></a>
## GateFlow Process 与版次（已确认）

- Process 是 GateFlow 的核心聚合，一个 Process 有多个版本；一个版次对应一个 Working Set，父子工作集链构成版次链。
- 内容范围固定为四类：过程元素业务对象、裁剪问题、裁剪答案、合规映射；不纳入 Part 等共享 Tracker。
- GateFlow 支持从历史版次取全部内容替换叶子草稿版次内容；替换后保留草稿的版次身份、工作集 ID 与原父级。
- 替换准备期间继续使用当前完整内容并允许编辑；全部准备成功后整体切换，期间编辑可被覆盖；准备失败保留当前内容及已保存编辑。
- 替换是 GateFlow 的独立合同，不是直接父子 Merge 准入的例外；旧元素物理清理、外部引用与切换瞬间并发协议由 GateFlow 专项继续设计。

写入屏障、身份再生等候选未采纳；现行语义以本节和关联场景为准。

<a id="dqa-共享查看范围用户已确认"></a>
## DQA 共享查看（已确认）

DQA 自行管理质量业务逻辑，其 Tracker 全部为共享 Tracker。Working Set 的唯一结合点是纳入查看；DQA 的对象不参与比对、合并或非共享派生。

八类对象：技术评审任务、过程保证任务、问题报告 PR、检查单、检查单模板、批注、评审文档（被评审对象）、评审报告（评审产物）。具体 Tracker 与类型数量对应关系按现有模型处理。

<a id="item-创建入口与归属用户已确认"></a>
## Item 创建入口与归属（已确认）

- 子工作集禁止创建 Tracker。
- 子工作集可在已有非共享 Tracker 下原生新建 Item。
- 共享 Tracker 下从子工作集入口新建的 Item 实际在默认工作集创建；请求可来自任意绑定工作集。
- Working Set 解析实际创建归属，再交所属业务 BC 校验并创建合法对象；公共能力入口与生命周期归属分开表达。

<a id="比对与合并的关系准入用户已确认"></a>
## 比对与合并准入（已确认）

Compare/Merge 只允许直接父子工作集之间双向进行，支持新增、删除、修改；接收方按用户选择产生变化，由接收方 BC 校验并提交。兄弟、跨代不允许直接操作。无论接收方发生什么变动，均以用户合并决策为准；业务 BC 的合法性拒绝仍保留未完成结果。

Part 与 DQA 共享查看范围不进入 Compare/Merge；GateFlow 历史内容替换使用独立合同。

## Shared Tracker 的修改与绑定（已确认）

Shared Tracker 自身及其 Item 的新增、修改实际发生在默认工作集；子工作集可以作为入口，但不形成独有副本；所有绑定工作集同时可见。

Shared Tracker 从子工作集移除仅解除 TrackerBinding，聚合保留；即使存在 Item 也允许解绑。原“加入后一律禁止移除”的政策已由 `POL-014` 修订，不能与现行规则并存。

<a id="分类配置与-tracker-共享特性用户已确认"></a>
## 分类与共享特性（已确认）

分类的共享特性是固定出厂设定，不允许修改，与是否已创建 Tracker 无关。Tracker 的共享特性创建时确定，后续不可变。非共享分类允许创建共享或非共享 Tracker；共享分类只能创建共享 Tracker。

## 跨 BC 契约结论（T4）

- **K1″**：Shared Kernel 定义只读 `TrackerItemQueryProvider` 与能力声明；完整业务聚合的创建、物化、修改、删除、整体校验与提交由所属 BC 执行。SK 可协调 COW/Merge，但不能绕过 BC 聚合、领域规则或 Repository 通用 Save。
- **K2″**：九分类公共失败语义（`NotFound`、`NotIncluded`、`Denied`、`Deleted`、`HistoryUnavailable`、`Preparing`、`CapabilityNotSupported`、`DependencyUnavailable`、`Unknown`）是跨 BC 词汇表，不是聚合生命周期；每个用例只暴露适用子集。
- **两层接口**：业务聚合实现领域接口 `TrackerItem`（并继承 `BusinessObject`）；BC Adapter 另行实现只读契约。跨聚合写入通过请求和结果事件；聚合不实现应用层 Port，也不向 SK 开放 Repository。
- **K3″ 保持 Open**：Shared Kernel 的版本治理、兼容期与变更审批需四 BC 共同评审，不阻塞当前实现。

完整职责、六个用例、失败矩阵与 Port 结构见 [T4 跨 BC 契约](cross-bc-contract-v1.md)。

## 当前未决

| 主题 | 状态 | 入口 |
| --- | --- | --- |
| 权限接入 | 现阶段不接入；`Caller`/`authorize` 占位已移除，待权限模型完成后重新设计接入边界（D11） | `QUESTION-003` |
| GateFlow 替换的并发、重试、外部引用 | 业务结果已确认；切换瞬间并发准入与旧元素清理待 GateFlow 专项 | 本节 GateFlow |
| Reference/Association 构建与删除守卫 | QUESTION-010 已裁定 C；六类可见性/守卫待确认 | `QUESTION-010`、T7-E |
| SK 契约版本治理 | 团队开放项 | `QUESTION-014` |
| 业务 Baseline 含义 | 未裁定 | `QUESTION-011` |

## 历史材料

阶段 3.0 的责任模式 A/B/C 比较、候选业务契约与“仍需确认”表已由 T4 收敛；不再作为当前设计入口。需要追溯时见[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)与 [T4 评审](../reviews/2026-11-11-t4-cross-bc-review.md)。
