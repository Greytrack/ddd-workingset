# 跨 BC 公共契约 v1

**状态：Reviewed（T4 契约冻结，2026-11-11；K1″、K2″ 已由用户正式确认；K3″ 保持团队开放项）**

- 日期：2026-11-11
- 阶段：阶段 5 契约层 T4（[阶段 5 契约层计划](../reviews/2026-11-10-stage-5-contract-plan.md)）
- 已确认依据：AC-001、D-007、FACT-011/012、QUESTION-013、Part/DQA 接入范围、GateFlow 版次链、POL-002/003/004/007/008/014/016、INV-001/009/011/012/030
- 本文件**不决定**：具体 API 签名、包名、Maven 模块数量、Spring Bean 装配、传输协议、事务实现、权限缓存时效。
- 本文件**不重新裁定**：四 BC 提交权与接入范围、绑定与删除（T2）、Diff/Merge 执行（T1）、物化协议（T3）。

## 0. 架构前提（2026-11-11 用户确认）

- 项目后续采用**新的 DDD 架构设计**；旧版本 `biz / sdk / sdkImpl` 的模块划分与依赖关系属**历史实现**，不再作为任何新设计的默认前提或架构约束。
- 先确定 **Bounded Context、Aggregate、Application Service、Port/Adapter** 的责任与依赖关系，**再**决定物理模块与包结构。
- 不提前固定 Maven 模块数量、包名或 Spring Bean 实现方式。
- 本文档只表达**职责与依赖方向**，不表达部署与模块拓扑。

## 1. 范围

Shared Kernel（Working Set）与四个业务 BC（GateFlow、ALM、DQA、PLM）之间的公共契约面：

1. `TrackerItem` **只读**能力契约与**写入协作 Port** 的结构；
2. 六个用例的边界：`create` 工作范围、`create` 业务对象并纳入、`extend`、`resolve`、`diff`、`merge`；
3. **九分类公共失败语义**及其在各用例上的适用子集；
4. Shared Kernel 的契约版本治理（`QUESTION-014`，团队开放项）。

四个 BC 的差异确认仍需各 BC 参与；本轮已按 K1″/K2″ 冻结 Working Set 侧契约。

## 2. 已确认职责边界（摘要）

| 规则 | 来源 |
| --- | --- |
| Working Set 属企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 提供公共工作集能力；SK 还包含 Privilege、Workflow、Class Hierarchy | AC-001 |
| `TrackerItem` 是 SK 定义的公共契约；业务对象是各 BC 的完整独立聚合；SK 只负责公共身份与工作集机制 | D-007、FACT-012 |
| 参与 Working Set 的对象由**各自 BC 校验整个对象并决定是否提交**；SK 负责用户选择、执行协调与结果记录 | QUESTION-013（用户确认） |
| Part 通过共享 Tracker 纳入查看；不参与派生、比对、合并 | shared-kernel-boundary |
| DQA 八类对象全部共享查看；不参与派生、比对、合并 | shared-kernel-boundary |
| GateFlow：一版次一工作集，父子链构成版次链；四类对象；支持历史版次整体替换 | shared-kernel-boundary |
| 子工作集禁止创建 Tracker；共享 Tracker 下从子工作集入口新建的 Item 实际归默认工作集；可回并父工作集 | POL-002/007 |
| 比对、合并仅允许直接父子工作集、双向；接收方按用户决策产生变化，由接收方 BC 校验提交 | POL-004、FACT-007 |
| 共享 Tracker 与其 Item 的修改归属默认工作集，所有绑定工作集同时可见（读取面 SLA 待定） | POL-008 |
| 绑定移除/删除语义（POL-014 修订、2026-11-11 K1～K3）：Shared 移除仅解绑、非共享删除受三项检查、删除不可恢复 | POL-014、POL-016、T2 契约 |
| 分类共享特性不可变；Tracker 共享特性创建时确定、不可修改 | POL-003 |

## 3. 契约结构：只读契约 + 写入协作 Port

**K1″（用户确认，2026-11-11）**：Shared Kernel 定义 `TrackerItem` **只读**公共契约与能力声明；完整业务聚合的创建、物化、修改、删除、整体校验与提交由所属业务 BC 执行。SK 可以协调 COW、Merge 等跨 BC 流程，但**不能绕过**业务 BC 聚合、领域规则或 Repository 执行通用 Save。

### 3.1 `TrackerItemReadPort`（SK 定义，各 BC 实现，**只读**）

**两层接口并存（用户确认，2026-11-11，选择 B）**：业务聚合继续实现领域公共接口 `TrackerItem`（D-007）；BC Adapter 另行实现应用层 `TrackerItemReadPort` 与适用的写入协作 Port。前者表达对象的公共领域能力，后者承接跨 BC 调用、授权与适配；聚合不因此实现应用层 Port，也不向 SK 开放 Repository 或通用 Save。字段/方法的精确签名留实现契约，不从两层接口推导新的业务能力。见[裁定依据](../reviews/2026-11-11-consistency-reconciliation.md)、SCN-073。

| 能力 | 语义 | 归属 |
| --- | --- | --- |
| 稳定身份 | 提供 `commonId`、`localId` | BC 实现 → SK 消费 |
| Revision | 提供当前业务 `Revision` | 同上 |
| 内容解析 | 按指定 `Revision` 解析出完整业务内容（BC 内部工厂重建） | 同上 |
| 能力声明 | 声明 `comparable` / `mergeable` / `derivable` / `creatable` / `historyReadable` | 同上 |
| 授权事实 | 对指定操作与调用者返回授权结论（BC 或 Privilege 提供） | 同上 |

**明确不属于 `TrackerItemReadPort`**：整体校验、保存/提交、删除、状态机推进——这些是**业务 BC 的职责**（见 §3.3），不是只读契约的方法。

**2026-11-12 D14 用户裁定（已落地）**：`BusinessObject` 在 `sk.workingset.domain` 内声明，`TrackerItem` 继承它；`BusinessObject` 声明 `delete`/`undelete`/`delete2` 三个生命周期能力，`delete2` 是 guard/decision，实际永久删除由各 BC 应用服务编排。`LifecycleCommand` 只承载期望并发版本与业务原因，不携带调用者上下文（D11 暂不接权限）；`LifecycleResult` 区分 Applied/Rejected/Conflict 并携带变换后的对象。`TrackerItemReadPort` 仍保持只读；写入协作仍走各 BC 的 WritePort。`Requirement` 作为 ALM 实现已具备该能力，`RequirementLifecycleService.delete2` 先调用聚合 guard 再删除。设计见[BusinessObject 生命周期能力设计](business-object-lifecycle-design.md)。

**2026-11-12 D4 用户裁定（方案 A：移回 SK）**：`Tracker` 与 `LightTrackerItem` 两个聚合及其仓储归 SK 独占；业务 BC **不得**注入 `TrackerRepository` / `LightTrackerItemRepository`，也不接收 SK 聚合对象。物化所需的 Tracker 锁定与目标变体读取由 SK 应用层完成，业务 BC 只接收值对象 `TargetIdentity`（localId / trackerId / commonId / revision / materialized）。业务对象永久删除后的 LTI 清理由 SK 定义、业务 BC 发布的 `RequirementPurged` 事件驱动，SK 侧处理幂等。协作接口为 SK 定义的 `TrackerItemLifecyclePort`（`lockMaterializationTarget` / `assertPurgeable` / `removePurged`，由 SK 实现），不走通用 Save 或聚合透传。实现证据见 [S7 验证](../implementation/t5-s7-verification-result.md)与 [D4 边界验证](../implementation/t5-d4-sk-boundary-verification.md)。

### 3.2 写入协作 Port（SK 定义 Outbound Port，BC 通过 Adapter 实现）

- **每个 BC 一个专用 Port**（如 `RequirementWritePort`），**不是**具有通用 CRUD 权限的 `TrackerItem` 接口。
- 请求载荷（契约级，非最终 API）：目标身份、业务 `Revision`、独立的乐观锁版本（`lock_version`）/写入前置证据、字段与存在性决定、审阅证据引用、操作身份（幂等）、调用者上下文。业务 Revision 与发布 `version` 不充当乐观锁；证据形式留实现契约确定（CONCEPT-009、ADR-0005）。
- 返回（契约级）：成功 + 提交后的业务 `Revision`（普通编辑不升级版次）；或业务拒绝 / 冲突 / 未知结果等（见 §5）。并发控制不得改变 T1 已确认的决策优先与执行语义。
- 结构可具有 **SPI 扩展特征**（按能力声明发现可用实现），但**不强制** Java `ServiceLoader` 或动态插件机制。

```
SK 应用层 ──依赖──▶ TrackerItemReadPort（只读）
SK 应用层 ──依赖──▶ <BC>WritePort（写入协作，Outbound Port）
业务 BC ──实现──▶ 上述两个 Port 的 Adapter
业务 BC 内部：聚合构建、整体校验、持久化、领域事件
```

- **D3-B（2026-11-12 用户裁定）**：COW 物化的业务保存由业务 BC **独立提交**；SK 的物化凭据与 LightTrackerItem 切换在独立事务中进行，**不要求跨 BC 同事务**。未完成的凭据/切换通过 Modulith 未完成事件重投收敛（D13）；业务保存按目标身份与快照证据幂等。
- **D15-B（2026-11-13 用户裁定）**：Merge 的条目应用同样独立提交；目标变体切换/创建由 `RequirementMergeApplied` 事件在 SK 独立事务完成，与 COW 采用同一模式（异步、幂等、可重投）。
- Merge 执行中断的业务提交凭据和恢复结果见 [T1 §8.1](merge-execution-contract-v1.md#81-执行中断与结果恢复t6-b)。返回值与失败语义沿用 §5。

### 3.3 业务 BC 必须拥有的职责

1. 聚合构建与重建（按 Revision / 快照）；
2. 完整不变量校验（含交叉字段、状态机、必填依赖）；
3. 持久化与乐观锁并发控制；
4. 领域事件的产生与发布；
5. 写入决定权（接受、拒绝、冲突）。

SK 不实现上述职责，也不通过任何通用字段写入绕过它们。

**2026-11-13 实施边界（用户确认）**：当前 `alm.requirement` 是**占位/参考实现**，只用于验证 SK 契约；本项目交付物是 `sk.workingset` 的契约、规范与约束。各业务 BC 由专门团队按专业领域实现，但必须遵守本契约的只读/写入 Port、失败语义、事件、身份与跨聚合事务约束。参考实现不覆盖全部业务规则（例如具体业务 Guard），不得以其细节反推 SK 契约。

### 3.4 能力声明与协作范围

不要求所有 BC 实现全部能力。按 `capabilities()` 决定哪些协作契约适用：

| 标记 | 说明 | 已知取值 |
| --- | --- | --- |
| `comparable` | 支持字段级 `diff` | ALM/GateFlow = true；Part、DQA = false |
| `mergeable` | 支持 `merge` 应用 | 同上 |
| `derivable` | 支持非共享派生 | 同上 |
| `creatable` | 支持在工作集内新建 Item | 按 BC 合同（共享 Tracker 下实际归默认工作集） |
| `historyReadable` | 支持按 Revision / Source Cut 回读 | 按对象；不可读须显式返回 `HistoryUnavailable` |

**原则**：能力不支持 ≠ 无差异 / 空结果，必须返回 `CapabilityNotSupported`。

## 4. 六个用例的契约边界

| 用例 | SK 定义与责任 | BC 责任（Adapter） | 适用失败分类（子集，见 §5.2） |
| --- | --- | --- | --- |
| `create` 工作范围 | 定义 outbound port；准备与发布范围，固定来源 | 不自动创建业务对象 | §5.2；具体业务拒绝见 T2 |
| `create` 业务对象并纳入 | 定义纳入请求；以事实身份去重 | 创建、校验、授权并提交合法对象 | §5.2 |
| `extend` 扩展范围 | 建立稳定身份与来源 Revision；准备与发布 | 提供普通字段来源读取及派生时附件、关系固定能力，内容范围按 T3 §2.1 | §5.2 |
| `resolve` 定位与读取 | 按工作集与身份定位有效变体；**不能独立承诺内容可读** | 授权并返回权威内容/生命周期/历史 | §5.2 |
| `diff` 有向比较 | 固定方向与冻结证据、组织差异（T1 已定 `ComparisonToken`） | 定义字段语义、相等规则、不可比较与脱敏字段 | §5.2；执行期见 T1 |
| `merge` 应用并确认 | 保存决定、编排执行、记录结果与 Checkpoint（T1 已定） | 重新授权、整体校验、提交目标变化 | §5.2；执行结果见 T1 |

**不要求每个接口统一返回九类**：§5.2 是适用分类的唯一矩阵。原本节三个创建/扩展用例遗漏 `Preparing` 的摘要按该矩阵及 T4 评审 §3 校正。

**工作集入口（D10，2026-11-13 已实现）**：`CreateRequirementRequest` 增加 `workingSetId`（为空时默认默认工作集）；共享 Tracker 实际归属默认工作集、请求可来自任意绑定工作集；非共享 Tracker 必须由它所在的工作集创建，否则返回 `NotIncluded`。业务 BC 通过 SK 的 `TrackerScopePort` 解析归属，不直接访问 SK 仓储；实际 `working_set_id` 仍由 Tracker 派生。

**身份分配（T6-C，2026-11-13 用户确认）**：创建请求不携带 CommonId，由服务端按工作集分配——默认工作集生成一个雪花 ID 同时作为 LocalId 与 CommonId；非默认工作集生成两个不相等的雪花 ID。派生沿用来源 CommonId，LocalId 由服务端生成且与 CommonId 不等。同族唯一冲突（含软删占键）统一返回 409 `DATA_INTEGRITY_CONFLICT`，不新增失败分类。

## 5. 九分类公共失败语义（K2″，用户确认）

### 5.1 术语与禁止的偷换

| 分类 | 含义 | 禁止的偷换 |
| --- | --- | --- |
| `NotFound` | 对象从未存在，或不在当前身份空间内 | 不得把「无权」「已删除」「未纳入」报成不存在。**例外（D7，2026-11-12 用户裁定）：业务对象被永久删除后不保留墓碑与内容，`NotFound` 是其正确语义**；软删仍须返回 `Deleted` |
| `NotIncluded` | 对象存在但未纳入当前工作集范围 | **不得降级为 `NotFound`** |
| `Denied` | 对象存在但调用方无权 | **不得降级为 `NotFound`**，不得泄露内容 |
| `Deleted` | 已删除（不可恢复） | 不得当作「历史不可用」或「当前值缺失」 |
| `HistoryUnavailable` | 所需历史/切点不可读 | **不得用当前值冒充历史值** |
| `Preparing` | 范围/成员处于准备中的显式中间态 | 不得当作已完成 |
| `CapabilityNotSupported` | 对象/BC 不支持该能力 | **不得解释为空差异或无记录** |
| `DependencyUnavailable` | Privilege / Workflow / Class Hierarchy 等依赖不可用；**也用于业务对象内容仍被未物化变体依赖（COW 未完成，D7/C4）** | 不得默许操作通过；不得当作 `Preparing` 的可等待中间态 |
| `Unknown` | 执行结果未知（回执丢失等） | **不得解释为确定未执行**；调用方须按既有幂等协议恢复或确认 |

**九分类是跨 BC 的统一语义词汇表**，不是九状态聚合生命周期，也不是要求统一异常类层次。每用例只暴露适用子集（§5.2）。

### 5.2 各用例适用矩阵

| 用例 | NotFound | NotIncluded | Denied | Deleted | HistoryUnavailable | Preparing | CapabilityNotSupported | DependencyUnavailable | Unknown |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| create 范围 | — | — | ✔ | — | — | ✔ | — | ✔ | ✔ |
| create 对象并纳入 | — | ✔ | ✔ | — | — | ✔ | ✔ | ✔ | ✔ |
| extend | — | ✔ | ✔ | — | ✔ | ✔ | ✔ | ✔ | ✔ |
| resolve | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | — |
| diff | — | — | ✔ | — | ✔ | ✔ | ✔ | — | ✔ |
| merge | — | — | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |

`—` 表示该用例不应以该分类表达；`✔` 表示适用。

### 5.3 与既有执行语义的关系（不覆盖）

- **T1**：`StaleEvidence`、`Rejected`、`Conflict`、`Deferred`、轮次终态等具体执行结果**保持不变**；九分类不替换、不合并它们。
- **T3**：双事实一致性保持；`LightTrackerItem` 切换失败**不得**被解释为业务 BC `Save` 失败（保存成功即成功，切换失败属元数据收敛）。
- **T2**：绑定/删除的业务拒绝（`TrackerHasItems` 等）保持为具体失败语义，不强行映射为九分类。

## 6. 契约版本治理（`QUESTION-014`）——团队开放项

**状态：K3″ 保持 Open，不标记 Accepted；待 GateFlow、ALM、DQA、PLM 四个 BC 团队共同评审。**

- 推荐候选仍为「版本化公共契约 + 能力协商 + 兼容期」；ACL 可作为各 BC 内部隔离手段。
- 列为**团队治理待办**，**不作为 T5 首个纵向切片的默认阻塞条件**。
- 本轮不设计版本发布平台、兼容矩阵或统一升级工具。
- 三张图与判据对比见[跨 BC 契约图 §6](../diagrams/cross-bc-contract.md)；若采纳版本化方案，仍需四 BC 确认兼容期长度、破坏性变更审批人、能力协商形式。

## 7. 与 T1～T3 的衔接

- **T1（Merge 执行）**：`diff`/`merge` 的执行与终态由 T1 定义；本契约提供能力声明与失败词汇。
- **T2（绑定与删除）**：绑定事实由 SK 管理，BC 只读；删除受 `TrackerItem` 存在性约束。
- **T3（物化）**：BC 保存后由 SK 切换 `materialized`；BC 必须能按稳定 `localId` 定位或创建变体（INV-070）。
- **权限**：Privilege 提供身份/授权事实，BC 裁决专有业务操作，SK 裁决范围操作；依赖不可用返回 `DependencyUnavailable`。**2026-11-12 D11 用户裁定：现阶段不接入权限**——`Caller` 与 `authorize` 仅作占位，不实现身份解析、授权判定或 `Denied` 分类；等权限模型开发完毕后再接入，本版不以权限缺口为由放宽或收紧其他契约。

## 8. 规则性质

| 内容 | 性质 |
| --- | --- |
| 四 BC 提交权与校验职责、Part/DQA 接入范围与不参与派生/比对/合并、GateFlow 版次链、共享可见性、创建入口归属、直接父子双向准入、共享特性不可变 | **已确认**（AC-001、D-007、QUESTION-013、POL-002/003/004/007/008、FACT-007/011/012） |
| 只读 `TrackerItemReadPort` + 独立写入协作 Port；整体校验与提交归 BC | **用户确认**（2026-11-11，K1″） |
| 九分类公共失败语义与「跨 BC 词汇表而非生命周期」定位 | **用户确认**（2026-11-11，K2″） |
| 六个用例的边界划分与适用分类矩阵 | **契约 v1（本文件冻结）** |
| 版本治理（K3″） | **团队开放项**（`QUESTION-014`，未裁定） |
| SK 独占 Tracker/LightTrackerItem 聚合与仓储；业务 BC 只经 `TrackerItemLifecyclePort` 与 `RequirementPurged` 事件协作（D4） | **用户确认**（2026-11-12，方案 A：移回 SK，跨聚合用事件） |
| `BusinessObject` 在 SK 内声明 delete/undelete/delete2 能力，`TrackerItem` 继承并由各 BC 实现（D14） | **用户确认**（2026-11-12，已落地） |
| 权限接入时机：现阶段不接入，待权限模型完成后接入（D11） | **用户确认**（2026-11-12） |
| 具体 API 签名、包名、模块、传输协议、事务、权限缓存时效 | **不在本契约** |

## 9. 裁定结果（2026-11-11）

| 编号 | 用户裁定 | 状态 |
| --- | --- | --- |
| K1″ | §3：只读契约与写入协作责任 | **用户已确认** |
| K2″ | §5：公共失败语义及适用矩阵 | **用户已确认** |
| K3″ | §6：版本治理候选与团队待办 | **Open（未裁定）** |

## 10. 追溯与下一步

- 追溯：AC-001、D-007、FACT-011/012、QUESTION-013/014、POL-002/003/004/007/008/014/016、INV-001/009/011/012/030、[Shared Kernel 边界](shared-kernel-boundary.md)、[Context Map](context-map.md)、B01～B12、SCN-069～071。
- 图：[跨 BC 契约面与失败语义](../diagrams/cross-bc-contract.md)。
- 评审：[T4 评审记录](../reviews/2026-11-11-t4-cross-bc-review.md)。
- 下一步：进入 **T5 最小纵向切片**（首切片只涉及单一业务 BC 与 SK，不受 K3″ 阻塞）。
