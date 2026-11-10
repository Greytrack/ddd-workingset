# ADR-0004：ProductSpace 独立聚合与 WorkingSet 绑定模型

- **状态**：Proposed
- **日期**：2026-11-10
- **决策范围**：ProductSpace / Classification / SpaceBinding / TrackerBinding（阶段 4 补充）
- **确认人／决策权威**：用户已确认相关业务规则（2026-11-10 补充）；本 ADR 文档尚待批准
- **替代**：无
- **被替代于**：无

## 背景与已确认依据

WorkingSet 需要关联 ProductSpace 与 Tracker。历史候选把 ProductSpace Binding 与 Tracker Binding 放在 WorkingSet 聚合内（AP-001），或作为 Manifest（AP-003）。用户补充确认：

- ProductSpace 是 Shared Kernel 独立聚合，在默认 WorkingSet 维护生命周期、可关联多个 WorkingSet，不随 WorkingSet 派生复制/迁移（FACT-024）。
- Classification 是独立共享元模型，Tracker 是其投影（FACT-025）。
- 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 只能在默认 WorkingSet 创建原始聚合（FACT-026）。
- 绑定前置、唯一性与纳入/移除规则（POL-012～014）。

## 决定

- ProductSpace、Tracker、LightTrackerItem、WorkingSet 各自为独立聚合；业务 BC 对象仍为独立聚合。
- **WorkingSet 聚合负责维护当前工作集中的空间与 Tracker 绑定结构**：SpaceBinding、TrackerBinding 是两个 **Entity**，归属于 WorkingSet 聚合；SpaceBinding 是 TrackerBinding 的业务前置。
- ProductSpace、Tracker 只维护自身稳定归属身份，不持有绑定。
- Tracker 含 productSpaceId、classificationId、parentId、sourceId；跨聚合关系均用 ID。
- Classification 通过共享契约供 Tracker 创建使用，WorkingSet 不负责其内部建模。
- 不新增同时拥有 ProductSpace、Tracker、WorkingSet 的大聚合；不为绑定关系单独新增聚合。

## 备选方案与权衡

- **A. WorkingSet 大聚合持有全部绑定（未采用）**：Binding 作为内部实体，但把 ProductSpace/Tracker 拖入 WorkingSet 大聚合，违背独立聚合与生命周期归属。
- **B. 绑定单独成聚合（未采用）**：绑定是关系、无独立生命周期，独立成聚会引入额外身份与一致性负担。
- **C. WorkingSet 持有绑定规则 + 关系实体（采纳）**：职责清晰、跨聚合用 ID；代价是需要绑定前置与槽位唯一约束。

## 后果

- ProductSpace 创建时按 Classification 集合投影生成 Tracker（跨聚合协作，非包含）。
- 删除/移除语义：共享 Tracker 从子 WS 移除仅解除 TrackerBinding；非共享无 Item 且无派生子 Tracker 才可删除；有派生子 Tracker 不允许删除；任意 Tracker 有 TrackerItem 不允许移除；非共享有组织子节点但无派生子节点不允许删除；移除最后一个 Tracker 不触发空间移除；移除 ProductSpace 时仍有 TrackerBinding 抛异常；默认 WS 共享 Tracker 空且无子可删。
- WorkingSet 拥有绑定规则与物理加载全部绑定记录是两个问题，不按 ORM 定边界。

## 验证与追溯

- 事实：FACT-024～026；政策：POL-012～014；不变量：INV-053～059；场景：SCN-050～054。
- 图：[ProductSpace/Tracker/WorkingSet 关系](../diagrams/productspace-tracker-workingset.md)。
- 重估条件：绑定规模/并发热点、ProductSpace 生命周期变化、OR M 实现约束。
