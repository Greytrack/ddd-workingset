# Architecture Decision Records

**状态：Accepted（目录用途；4 个 Accepted ADR 于 2026-11-10 经用户批准；ADR-0005 为 Proposed，待用户裁定）**

这里只记录难以逆转、缺少背景会令人意外、且存在真实权衡的重要领域或架构决策。使用 [ADR-TEMPLATE.md](ADR-TEMPLATE.md) 起草，实际 ADR 按 `0001-short-title.md` 顺序编号。

ADR 状态为 `Proposed`、`Accepted`、`Rejected` 或 `Superseded`。AI 可以起草 `Proposed` ADR，但只有用户或相应决策权威明确确认后才能标记为 `Accepted`。

- [ADR-0001：Tracker 独立聚合](0001-tracker-independent-aggregate.md)：记录已确认 D-001；ADR 文档 Accepted（2026-11-10）。
- [ADR-0002：LightTrackerItem 作为 Working Set 权威聚合](0002-lighttrackeritem-authoritative-aggregate.md)：记录 D-007/D-008/D-012；ADR 文档 Accepted（2026-11-10）。
- [ADR-0003：COW 采用事件驱动最终一致与首次快照优先](0003-cow-event-driven-final-consistency.md)：记录 D-009～D-018；ADR 文档 Accepted（2026-11-10）。
- [ADR-0004：ProductSpace 独立聚合与 WorkingSet 绑定模型](0004-productspace-independent-aggregate.md)：记录阶段 4 ProductSpace/Classification/SpaceBinding/TrackerBinding 裁定（FACT-024～026、POL-012～014、INV-053～059）；ADR 文档 Accepted（2026-11-10）。
- [ADR-0005：Merge 执行模型与 MergeEntry 行为契约](0005-merge-execution-model.md)：记录 MergeEntry 归属与生命周期、三对象协作顺序、ComparisonToken 证据要求、Checkpoint 确认发起方；ADR 文档 **Proposed**（待用户裁定）。
