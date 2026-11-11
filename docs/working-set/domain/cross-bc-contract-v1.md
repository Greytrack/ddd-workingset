# 跨 BC 公共契约 v1（草案）

**状态：Draft（契约候选；不改变 AC-001、D-007、QUESTION-013 已确认的职责边界，不构成 Accepted 或发布）**

- 日期：2026-11-11
- 阶段：阶段 5 契约层 T4（[阶段 5 契约层计划](../reviews/2026-11-10-stage-5-contract-plan.md)）
- 已确认依据：AC-001、D-007、FACT-011/012、QUESTION-013（BC 整体校验并提交、SK 管选择/协调/结果）、Part/DQA 接入范围、GateFlow 版次链、POL-002/003/004/007/008/014、INV-001/009/011/012/030
- 本文件**不决定**：具体 API 签名、Java 方法、传输协议、事务实现、权限缓存时效、各 BC 的专有校验规则。
- 本文件**不重新裁定**：四个 BC 的提交权与接入范围、绑定与删除（T2）、Diff/Merge 执行（T1）、物化协议（T3）。

## 1. 范围

Shared Kernel（Working Set）与四个业务 BC（GateFlow、ALM、DQA、PLM）之间的**公共契约面**：

1. `TrackerItem` 能力契约（必需能力与能力标记）；
2. 六个用例的边界：`create` 工作范围、`create` 业务对象并纳入、`extend`、`resolve`、`diff`、`merge`；
3. **统一失败语义**（无权／不存在／已删除／历史不可用／未纳入／准备中，以及能力不支持、依赖不可用、未知结果）；
4. Shared Kernel 的**契约版本治理**候选（`QUESTION-014`）。

本轮只产出 Working Set 侧的契约草案与待访项；四个 BC 的差异确认仍需各 BC 参与。

## 2. 已确认职责边界（摘要）

| 规则 | 来源 |
| --- | --- |
| Working Set 属企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 提供公共工作集能力；SK 还包含 Privilege、Workflow、Class Hierarchy | AC-001 |
| `TrackerItem` 是 SK 定义的公共契约；业务对象是各 BC 的完整独立聚合；SK 只负责公共身份与工作集机制，不拥有业务状态、生命周期与不变量 | D-007、FACT-012 |
| 参与 Working Set 的对象由**各自 BC 校验整个对象并决定是否提交**；SK 负责用户选择、执行协调与结果记录 | QUESTION-013（用户确认） |
| Part 通过共享 Tracker 纳入查看；**不参与**工作集派生、比对、合并 | shared-kernel-boundary |
| DQA 八类对象全部共享查看；**不参与**派生、比对、合并 | shared-kernel-boundary |
| GateFlow：一个 Process 多版次，一版次一工作集，父子链构成版次链；四类对象；支持历史版次整体替换 | shared-kernel-boundary |
| 子工作集禁止创建 Tracker；共享 Tracker 下从子工作集入口新建的 Item 实际归默认工作集；子新增可回并父工作集 | POL-002/POL-007、shared-kernel-boundary |
| 比对、合并仅允许直接父子工作集、双向；接收方按用户决策产生新增/删除/修改，由接收方 BC 校验提交 | POL-004、FACT-007 |
| 共享 Tracker 与其 Item 的修改归属默认工作集，所有绑定工作集同时可见（读取面 SLA 待定） | POL-008 |
| 绑定移除/删除语义（2026-11-10 修订 POL-006，2026-11-11 K1～K3）：Shared 移除仅解绑、非共享删除受三项检查、删除不可恢复 | POL-014、POL-016、T2 契约 |
| 分类共享特性为不可变出厂设定；Tracker 共享特性创建时确定、不可修改 | POL-003 |

## 3. `TrackerItem` 能力契约

### 3.1 必需能力（所有参与对象）

| 能力 | 语义 | 责任方 | 依据 |
| --- | --- | --- | --- |
| 身份读取 | 提供 `CommonId`、`LocalId`、业务 `Revision` | BC 实现、SK 消费 | D-012/D-018、FACT-012 |
| 内容重建 | 按业务 `Revision` 重建完整聚合（CQRS Query + 工厂） | 业务 BC | D-008、ADR-0002 |
| 整体校验 | 提交前对完整对象校验（含字段组合、状态机、跨字段不变量） | 业务 BC | INV-009、QUESTION-013 |
| 提交 | 由 BC 决定并提交；SK 不得绕过其公开写能力 | 业务 BC | D-007、INV-009 |
| 授权事实 | 读取/写入授权由 BC 或 Privilege 提供；无权不得降级为不存在 | BC / Privilege | B02、INV-001 |

### 3.2 能力标记（capability flags）

按对象类型声明的能力，是契约的一部分：

| 标记 | 含义 | 已知取值 |
| --- | --- | --- |
| `comparable` | 支持字段级比对（`diff`） | ALM/GateFlow = true；Part、DQA = false |
| `mergeable` | 支持合并应用（`merge`） | ALM/GateFlow = true；Part、DQA = false |
| `derivable` | 支持非共享派生 | 同 `mergeable` |
| `creatable` | 支持在当前工作集新建 Item | 按 BC 合同（共享 Tracker 下实际归默认工作集） |
| `historyReadable` | 支持按 Source Cut / Revision 回读历史 | 按对象；不可读须显式返回 |

**原则**：**能力不支持 ≠ 无差异 / 空结果**。不支持时必须返回 `CapabilityNotSupported`，不得让调用方误判为「没有差异」或「没有数据」（B05 已确认「不支持字段不能算相等」）。

## 4. 六个用例的契约

失败分类见 §5；具体错误码与消息留实现。

| 用例 | 调用方 | SK 责任 | BC 责任 | 主要失败语义 |
| --- | --- | --- | --- | --- |
| `create` 工作范围 | BC 应用 → SK | 建立工作上下文、固定来源、准备与发布范围（准备可重试、发布原子） | 不自动创建业务对象 | `Conflict`、`NotAllowed`、`DependencyUnavailable` |
| `create` 业务对象并纳入 | BC 应用创建并提交 → 请求 SK 纳入 | 以事实身份去重、建立纳入待完成/已纳入 | 校验、授权并提交合法对象；返回已提交事实 | 创建失败与纳入失败**分开表达**（不得称「对象创建失败」当纳入失败） |
| `extend` 扩展范围 | BC 应用 → SK | 保留稳定身份与 Source Cut 凭据、准备与发布 | 提供准入与固定内容能力 | `NotIncluded`、`SharingModeMismatch`、`SourceUnavailable`、`NotAllowed` |
| `resolve` 定位与读取 | 查询者 → SK 定位 → BC 授权读取 | 按工作集与身份定位有效变体；**不能独立承诺内容可读** | 授权并返回权威内容/生命周期/历史 | `NotFound`、`NotIncluded`、`Denied`、`Deleted`、`HistoryUnavailable`、`Preparing` |
| `diff` 有向比较 | 用户 → SK 组织、BC 语义比较 | 固定方向与冻结证据、组织差异（T1 已定 Token） | 定义字段语义、相等规则、不可比较与脱敏字段 | `CapabilityNotSupported`、`Denied`、`HistoryUnavailable`、`Incomplete` |
| `merge` 应用并确认 | 用户决定 → SK 编排 | 保存决定、编排执行、记录结果与 Checkpoint（T1 已定） | 重新授权、整体校验、提交目标变化 | `Rejected`、`Conflict`、`DependencyFailed`、`Unknown` |

补充（已确认）：

- `diff`/`merge` 的准入、执行与终态已由 T1 契约定义（有向冻结、`ComparisonToken`、`MergeEntry`、轮次终态）；本契约只规定能力面与失败分类。
- `resolve` 的「未物化」不是失败：沿 `SourceLocalId` 解析到实际拥有内容的变体（D-013、T3）。
- 只读查询与写入必须使用**同一套失败语言**，避免「无权」在内部被偷换为「不存在」。

## 5. 统一失败语义

| 分类 | 含义 | 禁止的偷换 | 依据 |
| --- | --- | --- | --- |
| `NotFound` | 对象从未存在，或不在当前身份空间内 | 不得把「无权」「已删除」报成不存在 | B02 |
| `NotIncluded` | 对象存在但未纳入当前工作集范围 | 不得与 `NotFound` 混淆 | INV-011 |
| `Denied` | 对象存在但调用方无权访问/操作 | **不得降级为 `NotFound`**（不得在内部把无权当不存在） | POL-008、INV-001、B02 |
| `Deleted` | 已删除（不可恢复，POL-016） | 不得当作「历史不可用」或「当前值缺失」 | INV-012、POL-016 |
| `HistoryUnavailable` | 所需历史/切点不可读 | **不得用当前值冒充历史值** | INV-004、INV-012 |
| `Preparing` | 范围/成员处于准备中的显式中间态 | 不得当作已完成 | INV-011 |
| `CapabilityNotSupported` | 对象不支持该能力（如 Part/DQA 的 diff/merge） | 不得返回「无差异」「空结果」 | B05 |
| `DependencyUnavailable` | Privilege / Workflow / Class Hierarchy 等依赖不可用 | **不得默许操作通过** | shared-kernel-boundary |
| `Unknown` | 执行结果未知（回执丢失等） | 不得当作「未执行」再写 | B10 |

对外披露策略：仅内部可信接口可见具体原因；对外按权限政策脱敏（B02、B12）。

## 6. 契约版本治理（`QUESTION-014`）

| 方案 | 说明 | 收益 | 代价 |
| --- | --- | --- | --- |
| A 共同发布 | 四个 BC 与 SK 同步升级，单一版本 | 简单、无兼容分支 | 任一 BC 节奏受限；共享内核成为发布瓶颈 |
| **B（推荐）版本化 Published Language** | 契约带版本与能力协商；向后兼容扩展、兼容期、弃用策略 | 各 BC 可独立演进；能力协商明确 | 需要版本矩阵与弃用治理 |
| C ACL 隔离演进 | 每个 BC 自带适配器，SK 只发布稳定语义 | 最大隔离 | 适配层与映射成本高；语义漂移风险 |

**推荐 B，C 作为 BC 内部实现手段**：共享契约以版本化能力集发布；新增能力必须向后兼容（新增可选能力），破坏性变更走新版本 + 兼容期 + 弃用公告。此方案需用户裁定。

## 7. 与 T1～T3 的衔接

- **T1（Merge 执行）**：定义 `diff`/`merge` 的执行与终态；本契约提供 `comparable`/`mergeable` 能力标记与失败分类。
- **T2（绑定与删除）**：绑定事实由 SK 管理，BC 只读（不拥有绑定）；删除受 `TrackerItem` 存在性约束。
- **T3（物化协议）**：BC 保存后由 SK 切换 `materialized`；BC 必须能按稳定 `LocalId` 定位或创建变体（INV-070）。
- **权限**：Privilege 提供身份/授权事实，业务 BC 裁决专有业务操作，SK 裁决范围操作；依赖不可用返回 `DependencyUnavailable`。

## 8. 已确认规则与契约候选的区分

| 内容 | 性质 |
| --- | --- |
| 四 BC 提交权与校验职责、Part/DQA 接入范围与不参与派生/比对/合并、GateFlow 版次链、共享可见性、创建入口归属、直接父子双向准入、共享特性不可变 | **已确认**（AC-001、D-007、QUESTION-013、POL-002/003/004/007/008、FACT-007/011/012） |
| 六个用例的名称与边界划分 | **契约候选**（本轮草案） |
| 能力标记（`comparable`/`mergeable`/`derivable`/`creatable`/`historyReadable`） | **契约候选**（K1″） |
| 统一失败语义九分类 | **契约候选**（K2″） |
| 版本治理方案 B | **契约候选**（K3″，`QUESTION-014`） |
| 具体 API 签名、传输协议、事务、权限缓存时效 | **不在本契约** |

## 9. 待裁定项与推荐（K1″～K3″）

| 编号 | 问题 | 推荐 | 理由 |
| --- | --- | --- | --- |
| K1″ | `TrackerItem` 能力集的边界：只定义读取/身份/授权 + BC 自管写入，还是提供统一写端口由 SK 代写？ | **只读契约 + BC 自管写入**（不给 SK 统一写端口） | QUESTION-013 已确认 BC 整体校验并决定提交；统一写端口会绕过 BC 不变量（INV-009），并让 SK 反向依赖四个 BC 的字段模型 |
| K2″ | 失败语义采用「六态 + 三附加」统一模型，还是仅四态（无权/不存在/已删除/历史不可用）？ | **六态 + 三附加**：增加 `NotIncluded`、`Preparing`、`CapabilityNotSupported`、`DependencyUnavailable`、`Unknown` | 未纳入与准备中在 Working Set 中真实存在（INV-011），能力不支持与依赖不可用已被 B05/B12 确认为必须显式表达；仅四态会迫使调用方把它们错归为「不存在」 |
| K3″ | 版本治理：A 共同发布、B 版本化 Published Language、C ACL 隔离？ | **B**（版本化 PL + 能力协商 + 兼容期），C 作为 BC 内部手段 | 四 BC 独立演进节奏不同；A 让 SK 成发布瓶颈；纯 C 会产生语义漂移。需要用户确认后写入契约 |

三项为**契约候选**，需用户确认后方可标记为已裁定；在此之前本契约保持 Draft。

## 10. 追溯与下一步

- 追溯：AC-001、D-007、FACT-011/012、QUESTION-013/014、POL-002/003/004/007/008/014/016、INV-001/009/011/012/030、[Shared Kernel 边界](shared-kernel-boundary.md)、[Context Map](context-map.md)、B01～B12。
- 图：[跨 BC 契约面与失败语义](../diagrams/cross-bc-contract.md)、[Shared Kernel 外框](../diagrams/shared-kernel-context-map.md)。
- 下一步：用户裁定 K1″～K3″ 后冻结本契约（或先按 K1″/K2″ 冻结、K3″ 留待四 BC 版本治理访谈）；随后进入 T5 最小纵向切片。
