# ADR-0002：LightTrackerItem 作为 Working Set 权威聚合

- **状态**：Accepted
- **日期**：2026-11-10
- **决策范围**：D-007 / D-008 / D-012
- **确认人／决策权威**：用户已确认 D-007/D-008/D-012 业务裁定；本 ADR 于 2026-11-10 由用户明确批准为 Accepted
- **替代**：无（AP-003 的 ItemVariant 是候选方案，不是 Accepted ADR）
- **被替代于**：无

## 背景与已确认依据

工作集需要管理跨派生变体的公共身份与有效内容定位，同时不拥有业务 BC 的完整业务内容。历史候选曾把 ItemVariant 作为独立根（承载身份、固定来源、物化状态），或把 Item 作为通用聚合模型。

用户已确认（D-007/D-008/D-012）：

- Working Set 统一使用 TrackerItem 作为公共契约，不把 Item 作为独立通用聚合。
- LightTrackerItem 是 Working Set 专属独立聚合，不是查询投影。
- 业务 BC 的 Requirement/Part/Process 是完整独立聚合，实现 TrackerItem 接口。
- SourceLocalId/SourceRevision 表达当前有效内容来源（D-012 收敛）。

## 决定

- **TrackerItem** 是 Shared Kernel 定义的顶级公共接口；业务 BC 对象实现它获得工作集能力。
- **LightTrackerItem** 是 Working Set 专属独立聚合，持有身份（LocalId/CommonId）、来源（SourceLocalId/SourceRevision）、自身 Revision（D-018）、TrackerId/WorkingSetId，承担工作集内身份与有效变体定位权威。
- Working Set 不拥有业务对象的领域状态、生命周期与不变量；业务内容由业务 BC 通过 CQRS Query + 工厂重建。
- 不引入 Manifest、ItemVariant、OriginLocalId 或额外谱系聚合。

## 备选方案与权衡

- **A. ItemVariant 独立根（不采用）**：可承载物化内容指针，但与 LightTrackerItem 的身份/来源/定位权威高度重叠，导致重复建模。
- **B. 查询投影（排除）**：LightTrackerItem 仅作为索引视图，无法承担权威身份与有效内容定位，且 D-008 已明确排除。
- **C. LightTrackerItem 独立聚合（采纳）**：职责清晰，Working Set 管公共身份/来源/定位，业务 BC 管内容；代价是需 COW 机制与 CQRS 工厂。

## 后果

- Working Set 与业务 BC 形成双事实：LightTrackerItem 持身份/来源/定位，业务对象持完整内容。
- 需要 COW（写时复制）机制在来源变更时物化派生变体（见 ADR-0003）。
- 不引入跨 BC 大事务；业务 BC 保留提交权。

## 验证与追溯

2026-11-11 用户选择 B 澄清：本 ADR 的业务聚合实现 TrackerItem 保留；T4 的 Adapter 实现应用层 Port 与之并存。职责见 [T4 §3.1](../domain/cross-bc-contract-v1.md)，确认依据见[一致性修正记录](../reviews/2026-11-11-consistency-reconciliation.md)，验证场景 SCN-073。本澄清未替代原决定。

- 术语：CONCEPT-021（TrackerItem）、CONCEPT-022（LightTrackerItem）。
- 不变量：INV-040/041/042（身份与来源切换）、INV-051（Revision 分离）。
- 场景：SCN-021/022/023/024/035/049。
- 重估条件：若 LightTrackerItem 被证明只是技术索引、或业务拒绝其权威定位，需重新评估。
