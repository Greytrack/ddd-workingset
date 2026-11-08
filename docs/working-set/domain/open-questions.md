# Working Set 开放问题

**状态：Draft**

排序先看对身份、历史和合法状态的影响，再看是否阻塞聚合与契约设计。来源代号见 [证据登记](evidence-register.md)。

| 优先级／编号 | 问题与重要性 | 可选答案 | 对模型的影响 | 建议验证 | 阻塞 |
| --- | --- | --- | --- | --- | --- |
| P0 `QUESTION-001` | **Working Set 的业务身份是什么？** 是工作视图、发布范围、长期配置还是分支？这决定生命周期与所有权。 | A 工作视图 Manifest；B 分支本体；C 二者分离为 Workspace + Release Scope。 | 决定 Working Set 是否聚合根、是否拥有 Binding、是否需要 revision。 | 让业务方叙述创建、发布、归档、复制、恢复五个场景。 | 战略设计、`AP-001/003` |
| P0 `QUESTION-002` | **Tracker／Tracker Branch 是否有独立身份和生命周期？** | A Tracker 本身即谱系节点；B 独立 TrackerBranch；C 只是 Working Set 内配置值。 | 决定上下文、Repository、删除守卫和合并范围。 | 追踪共享、派生、解绑、删除、跨工作集复用的业务对象。 | 聚合边界、上下文图 |
| P0 `QUESTION-003` | **共享语义、权限与 SLA 到底是什么？Sharing Mode 能否变化？** | A 同一权威内容且模式永久；B 可迁移模式；C 共享只读快照。 | 决定 `INV-001`、权限边界、迁移能力和是否需要 Merge。 | 跨工作集并发写、权限不同、列表延迟的业务验收。 | SHARED 契约 |
| P0 `QUESTION-004` | **跨分支 Item 身份如何定义，CommonId 的作用域是什么？** | A Working Set 全局；B ProductSpace/type/namespace 内；C 独立 FamilyId。 | 决定 ItemFamilyKey、唯一约束、引用与对齐算法。 | 抽样 Requirement/Bug/多空间真实编号及合法重号。 | 身份模型、Merge、Reference |
| P0 `QUESTION-006` | **什么历史必须按 Source Cut 回读，保留多久？** | A 仅内容；B 内容+成员+生命周期+关系+附件；C 不保证完整历史。 | 若 C，当前 COW、Snapshot 和 Merge 候选模型可能不可行。 | 对现有历史接口做 worked example/spike，覆盖单版次对象和 delete2。 | COW、Snapshot、Compare |
| P0 `QUESTION-008` | **字段级 Merge Decision 的有效期和整体合法性由谁判定？** | A token 到任一侧变化即失效；B 仅目标变化失效；C 部分字段可继续。 | 决定比较证据、目标业务命令、部分成功和 Checkpoint。 | 用交叉字段不变量、并发编辑、KEEP_TARGET/DEFER 组合验证。 | Merge 契约 |
| P1 `QUESTION-005` | **子 Working Set 能否原生创建 Item？其家族身份如何产生？** | A 禁止；B 允许且成为新家族；C 仅特定类型允许。 | 决定来源可空、回并语义、唯一性和生命周期归属。 | 访谈新增 Requirement/Bug 的实际流程。 | Item 创建规则 |
| P1 `QUESTION-007` | **允许哪些 Compare/Merge 关系，Merge Base 如何确定？** | A 仅直接父子；B 任意祖先后代；C 兄弟／跨代显式共同基线。 | 决定是否需要 MergeBase、谱系查询和策略版本。 | 收集真实回并、兄弟协作和跨代修复案例。 | Merge 范围、`POL-004` |
| P1 `QUESTION-009` | **解绑、归档、软删、彻底删除、恢复分别意味着什么？** | 对 SHARED/UNSHARED 分别制定状态机和治理入口。 | 决定 Tracker/Binding/Item 的生命周期与保留守卫。 | 逐对象回答“用户看不见”和“权威不存在”的区别。 | 生命周期与删除 |
| P1 `QUESTION-010` | **跨 Working Set 的 Reference/Association 如何解析、钉版和修复？** | A 逻辑 Family；B 具体 Variant；C 按关系类型选择。 | 决定关系身份、悬空状态、历史和修复事件。 | 以目标缺失、无权、删除、后续纳入四场景访谈。 | 关系上下文 |
| P1 `QUESTION-011` | **Baseline 的业务含义是什么？** | A Snapshot；B 发布清单；C Merge Base；D 多概念并存但分别命名。 | 决定不可变性、保留和引用语义，避免与仓库 Design Baseline 混淆。 | 收集业务人员使用“基线”的原句和操作。 | 统一语言、历史 |
| P2 `QUESTION-012` | **Working Set 与 Binding 的规模和并发上限是多少？** | A 小集合单聚合；B 大集合版本化 Manifest；C 分片准备+发布指针。 | 决定 `AP-001` 与 `AP-003` 的可行性及热点。 | 采集 P95/P99 数量、写并发、发布频率和允许时延。 | 物理聚合伸缩，不阻塞术语确认 |
| P0 `QUESTION-013` | **Working Set 通用机制与四个业务 BC 专有规则的边界在哪里？** | A Shared Kernel 只协调身份／范围／证据，BC 校验并应用；B Shared Kernel 承担通用字段写入、BC 提供策略；C 按能力混合。 | 决定 create/extend/resolve/diff/merge 的契约、依赖方向、事务和失败语义；若边界过宽会形成巨型跨域服务。 | 分别以 ALM Requirement、PLM Part、GateFlow 与 DQA 对象完成一次 Merge 拒绝场景。 | 阶段 3.0、所有跨 BC 合同 |
| P1 `QUESTION-014` | **四个 BC 共同依赖的 Shared Kernel 如何治理版本和兼容性？** | A 共同发布；B 版本化 Published Language；C 适配器／ACL 隔离演进。 | 决定共享模型范围、契约版本、弃用策略和团队协作方式。 | 访谈发布责任、升级节奏、兼容期限和变更审批者。 | 不阻塞概念发现，阻塞正式公共契约 |

## 建议确认顺序

先以 `AC-001` 为固定外部边界完成 `QUESTION-013`，再确认 `QUESTION-001 → 002 → 003/004 → 006 → 008`。它们决定共享能力边界、身份、历史和合法性；在这些问题未闭合前，不宜确认最终内部模型边界或聚合方案。
