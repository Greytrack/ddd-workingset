# Working Set 开放问题

**状态：Draft**

排序先看对身份、历史和合法状态的影响，再看是否阻塞聚合与契约设计。来源代号见 [证据登记](evidence-register.md)。

| 优先级／编号 | 问题与重要性 | 可选答案 | 对模型的影响 | 建议验证 | 阻塞 |
| --- | --- | --- | --- | --- | --- |
| P0 `QUESTION-001` | **Working Set 的业务身份是什么？** 是工作视图、发布范围、长期配置还是分支？这决定生命周期与所有权。 | A 工作视图 Manifest；B 分支本体；C 二者分离为 Workspace + Release Scope。 | 决定 Working Set 是否聚合根、是否拥有 Binding、是否需要 revision。 | 让业务方叙述创建、发布、归档、复制、恢复五个场景。 | 战略设计、`AP-001/003` |
| P0 `QUESTION-002` | **Tracker／Tracker Branch 是否有独立身份和生命周期？** | A Tracker 本身即谱系节点；B 独立 TrackerBranch；C 只是 Working Set 内配置值。 | 决定上下文、Repository、删除守卫和合并范围。 | 追踪共享、派生、解绑、删除、跨工作集复用的业务对象。 | 聚合边界、上下文图 |
| P0 `QUESTION-003` | **共享权限与可见性如何实现和验收？分类配置变化如何治理？** | 默认工作集修改、所有绑定工作集同时可见已确认；`POL-003` 已确认分类约束创建选择及 Tracker 共享特性不可变。 | 模式变更已关闭；分类配置自身能否变化及治理方式尚待确认，不得使既有 Tracker 自动转换。 | 依据 `SCN-002` 和共享特性创建场景验收；补充权限与读取面指标。 | SHARED 契约与分类协作 |
| P0 `QUESTION-004` | **CommonId 的唯一作用域及标识分配如何定义？** | 已确认派生链同 CommonId、不同 LocalId，默认工作集二者相等，非默认工作集二者不等。已确认无关 Item 跨类型、跨 Tracker 也不能拥有相同 CommonId，CommonId/LocalId 均为雪花 ID。剩余为分配协议及同族变体在单工作集中的选择约束。 | 决定 ItemFamilyKey、唯一约束、引用与对齐算法。 | 验证新建、派生、回并的标识分配与同族变体选择。 | 身份模型、Merge、Reference |
| P0 `QUESTION-006` | **什么历史必须按 Source Cut 回读，保留多久？** | A 仅内容；B 内容+成员+生命周期+关系+附件；C 不保证完整历史。 | 若 C，当前 COW、Snapshot 和 Merge 候选模型可能不可行。 | 对现有历史接口做 worked example/spike，覆盖单版次对象和 delete2。 | COW、Snapshot、Compare |
| P0 `QUESTION-008` | **字段级 Merge Decision 遇到执行中竞态时如何处理？** | B09 已确认提交前的字段变化规则，以及单字段不可读时本条未完成、继续下一条。待细化执行中版本控制和重试。 | 决定比较证据、目标业务命令、部分成功和 Checkpoint。 | 用交叉字段不变量、并发编辑、KEEP_TARGET/DEFER 组合验证。 | Merge 契约 |
| P1 `QUESTION-005` | **原生新建 Item 的家族身份及回并规则是什么？** | 已确认子工作集可在非共享 Tracker 新建 Item，共享 Tracker 创建实际归属默认工作集，子工作集禁止新建 Tracker。已确认允许将子工作集新增 Item 回并，在父工作集创建对应 Item。身份等值关系已确认，标识分配及去重协议待讨论。 | 决定来源可空、回并语义及唯一性。 | 用子工作集新建 Requirement 后回并父工作集的场景验证。 | 新建身份与回并契约 |
| 已确认 `QUESTION-007` | **Compare/Merge 的工作集关系范围** | 只允许直接父子之间，兄弟和跨代不允许直接操作。 | 准入依据为直接父子关系，source/target 表达本次方向；历史内容替换按自身合同。 | 验证直接父子可进入、兄弟/跨代被拒绝的公共行为。 | 准入实现与验证；`POL-004` |
| P1 `QUESTION-009` | **各对象删除、归档、恢复分别意味着什么？** | 子工作集的 Tracker 一旦加入，不论共享或非共享均禁止移除，已确认；各对象生命周期继续设计。 | 区分绑定移除与 Tracker/Item、整个工作集的生命周期，不从禁止解绑推导禁止删除。 | `POL-006` 及子工作集 Tracker 禁止移除场景验证绑定限制；删除、归档、恢复分别确认。 | 生命周期与删除 |
| P1 `QUESTION-010` | **跨 Working Set 的 Reference/Association 如何解析、钉版和修复？** | A 逻辑 Family；B 具体 Variant；C 按关系类型选择。 | 决定关系身份、悬空状态、历史和修复事件。 | 以目标缺失、无权、删除、后续纳入四场景访谈。 | 关系上下文 |
| P1 `QUESTION-011` | **Baseline 的业务含义是什么？** | A Snapshot；B 发布清单；C Merge Base；D 多概念并存但分别命名。 | 决定不可变性、保留和引用语义，避免与仓库 Design Baseline 混淆。 | 收集业务人员使用“基线”的原句和操作。 | 统一语言、历史 |
| P2 `QUESTION-012` | **Working Set 与 Binding 的规模和并发上限是多少？** | A 小集合单聚合；B 大集合版本化 Manifest；C 分片准备+发布指针。 | 决定 `AP-001` 与 `AP-003` 的可行性及热点。 | 采集 P95/P99 数量、写并发、发布频率和允许时延。 | 物理聚合伸缩，不阻塞术语确认 |
| P0 `QUESTION-013` | **Working Set 通用机制与四个业务 BC 专有规则的边界在哪里？** | A Shared Kernel 只协调身份／范围／证据，BC 校验并应用；B Shared Kernel 承担通用字段写入、BC 提供策略；C 按能力混合。 | 决定 create/extend/resolve/diff/merge 的契约、依赖方向、事务和失败语义；若边界过宽会形成巨型跨域服务。 | ALM 验证合并拒绝，PLM Part 验证共享纳入查看，GateFlow/DQA 按确认后的接入能力选择场景。 | 阶段 3.0、所有跨 BC 合同 |
| P1 `QUESTION-014` | **四个 BC 共同依赖的 Shared Kernel 如何治理版本和兼容性？** | A 共同发布；B 版本化 Published Language；C 适配器／ACL 隔离演进。 | 决定共享模型范围、契约版本、弃用策略和团队协作方式。 | 访谈发布责任、升级节奏、兼容期限和变更审批者。 | 不阻塞概念发现，阻塞正式公共契约 |

## 建议确认顺序

先以 `AC-001` 为固定外部边界完成 `QUESTION-013`，再确认 `QUESTION-001 → 002 → 003/004 → 006 → 008`。它们决定共享能力边界、身份、历史和合法性；在这些问题未闭合前，不宜确认最终内部模型边界或聚合方案。

## GateFlow 专项进展

QUESTION-001 在 GateFlow 场景下已部分确认，一个 Process 有多个版次，每个版次对应一个工作集，工作集父子链构成版次链路。支持历史版次全部内容替换叶子草稿内容。替换时保留草稿版次身份、工作集 ID 及原父子链已确认。准备期间继续使用当前完整内容并允许编辑、全部准备成功后整体切换且可覆盖期间编辑已确认。对象范围已确认为过程元素业务对象、裁剪问题、裁剪答案、合规映射四类，不含共享 Tracker。其内部细节业务逻辑由 GateFlow 专项设计，本阶段仅需细化跨边界身份与切换协议。见 [GateFlow 职责与场景](shared-kernel-boundary.md#gateflow-process-与版次用户已确认)。

## DQA 接入进展

QUESTION-013 的 DQA 接入范围已确认，八类对象均通过共享 Tracker 纳入查看，由 DQA 自行管理业务逻辑。工作集比对、合并及非共享派生不适用。范围见 [DQA 共享查看](shared-kernel-boundary.md#dqa-共享查看范围用户已确认)。

## 阶段 3 决策入口

GateFlow 替换已确认移除草稿独有、来源历史版次中不存在的流程元素。当前内容集合以所选历史版次为准，后续设计处理跨边界身份和引用、切换瞬间的并发提交及失败恢复协议。四类对象内部业务细节依用户要求留在 GateFlow 专项设计中。

QUESTION-013 的四 BC 合并校验与提交职责已确认，Part 也已确认通过共享 Tracker 纳入查看，工作集派生、比对、合并不适用。具体记录见 [职责边界](shared-kernel-boundary.md)。参与工作集的对象由各自 BC 校验整个对象并决定提交，Working Set 管理合并选择、协调和结果。QUESTION-013 的对象接入范围、其他能力契约，以及 QUESTION-014 的版本治理仍待确定。决策与证据见 [裁定进度](../reviews/2026-11-09-stage-3-architecture-review.md#裁定进度)。
