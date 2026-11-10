# ADR-0001：Tracker 独立聚合与领域术语收敛

- **状态**：Accepted
- **日期**：2026-11-10
- **决策范围**：D-001 的 Tracker 独立聚合及术语边界
- **确认人／决策权威**：用户已正式确认 D-001；本 ADR 于 2026-11-10 由用户明确批准为 Accepted
- **替代**：无（原 AP-002 是候选方案，不是 Accepted ADR）
- **被替代于**：无

## 背景与已确认依据

用户明确确认 Tracker 拥有独立业务身份、状态、生命周期及管理行为。原 AP-002 将演进线、来源及生命周期组合为 Tracker Branch 根，造成独立概念与身份映射的混淆。原文及本次裁定见 [阶段 3 D-001 记录](../reviews/2026-11-09-stage-3-architecture-review.md#d-001tracker-独立聚合裁定2026-11-10)。

## 决定记录与待批准范围

D-001 已确认：Tracker 为独立聚合，不是 Working Set 内部实体；Working Set 可定义范围或关联；统一语言不再使用 Tracker Branch 作为独立领域概念或根。

本 ADR 于 2026-11-10 由用户明确批准为 Accepted。不同工作集 Tracker 身份关系与 Item 聚合归属等未决项已由后续裁定逐步关闭；不批准 AP-001/002/003、不新增基线发布。

## 备选方案与权衡

- **独立 Tracker（已裁定）**：表达独立管理与生命周期；代价是 Working Set 范围与 Tracker 之间需要协作契约，独立聚合不自行保证跨范围一致性。
- **Working Set 内部 Tracker 实体（已排除）**：便于同配置原子管理，但把生命周期依附工作集，与 D-001 不兼容。
- **独立 Tracker Branch（原 AP-002，已排除）**：曾试图显式承载演进身份、来源切点和生命周期；增加一个身份与治理层，当前不符合统一语言。其语义不能通过替换名称自动迁移到 Tracker。

这些备选保留为决策背景，不重新请求用户选择已排除方案。

## 后果、迁移与兼容

修订 [术语](../domain/glossary.md)、[聚合分析](../domain/aggregate-model.md)、[问题](../domain/open-questions.md)、[方案](../proposals/aggregate-boundary-options.md) 与 [图表](../diagrams/aggregate-candidates.md)。原 AP-002 文字保留并显式标为不兼容，当前图中不保留独立 Branch 根。历史阶段 2 材料不回写。

尚不能制定 BranchId 到 TrackerId 的实现迁移；未读取生产代码，不能假定已有该类型或映射。Tracker 内部成员、不变量、事务和跨工作集身份均需后续证据，不从独立聚合地位推导 Item 归属。

## 验证与追溯

文档验收：当前图无 Tracker Branch 根；Tracker 不被 Working Set 组合；未把 Item 归属或跨工作集 Tracker 身份写成已确认；三 AP 不升级 Accepted。业务状态转换、公开 seam 和运行测试仍待后续设计，不虚构实现入口。

重新评估须有新的用户裁定及业务证据；不能以实现便利恢复停用根。

## D-002 后续裁定关联（2026-11-10）

本 ADR 已 Accepted，不另起 ADR 编号。上文 D-001 时点的跨工作集身份未决项，现由 [D-002 用户裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-002) 部分关闭：非共享派生产生不同身份的新 Tracker；parentId/sourceId 表达追溯层级。Shared 规则不变，Working Set 不拥有 Tracker 完整生命周期。

字段指向、多级派生、谱系可变性及同谱系共存仍待裁定；Item 归属不变。按身份引用及 Lineage 不设独立聚合仅为推论，不能从业务裁定推导单一存储/事务方案。原文未决描述保留其时点背景，以此追加记录表达新证据。

## D-003 / D-005 / D-006 后续裁定关联（2026-11-10）

本 ADR 已 Accepted，不另起 ADR 编号。上文 D-002 时点的追溯字段与可变性未决项，已由后续裁定逐步关闭：D-003 确认 parentId 为同工作集直属结构父、sourceId 为直接父级工作集来源（双维独立，见 [裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-003)）；D-005 确认父也选择时默认继承、创建完成前可显式重组（POL-010）；D-006 确认创建完成后 parentId 不可变、不支持移动/换父/改根（POL-011，见 [裁定](../reviews/2026-11-09-stage-3-architecture-review.md#d-006)）。

组织位置不可变是 Tracker 独立生命周期约束的一部分，进一步支持 D-001「Tracker 独立聚合、Working Set 不拥有其完整生命周期」的结论，但不推导 Item 归属，也不选择 AP-002 或任何聚合方案。sourceId 可变性、Shared 混合树、同源多派生、派生整批原子性与 Item 聚合归属仍待裁定。原文未决描述保留其时点背景，以此追加记录表达新证据。
