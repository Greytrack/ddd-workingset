# Architecture Decision Records

**状态：Accepted（目录用途；ADR-0001～0006 均为 Accepted）**

这里只记录难以逆转、缺少背景会令人意外、且存在真实权衡的重要领域或架构决策。使用 [ADR-TEMPLATE.md](ADR-TEMPLATE.md) 起草，实际 ADR 按 `0001-short-title.md` 顺序编号。

ADR 状态为 `Proposed`、`Accepted`、`Rejected` 或 `Superseded`。AI 可以起草 `Proposed` ADR，但只有用户或相应决策权威明确确认后才能标记为 `Accepted`。

- [ADR-0001：Tracker 独立聚合](0001-tracker-independent-aggregate.md)：Accepted（2026-11-10）。
- [ADR-0002：LightTrackerItem 作为 Working Set 权威聚合](0002-lighttrackeritem-authoritative-aggregate.md)：Accepted（2026-11-10）。
- [ADR-0003：COW 采用事件驱动最终一致与首次快照优先](0003-cow-event-driven-final-consistency.md)：Accepted（2026-11-10）。
- [ADR-0004：ProductSpace 独立聚合与 WorkingSet 绑定模型](0004-productspace-independent-aggregate.md)：Accepted（2026-11-10）。
- [ADR-0005：Merge 执行模型与 MergeEntry 行为契约](0005-merge-execution-model.md)：Accepted（2026-11-10）；关于 Checkpoint 的确认归属已由 ADR-0006 替代，其余决定继续有效。
- [ADR-0006：合并确认归属轮次，跨轮次独立查询](0006-merge-confirmation-ownership.md)：Accepted（2026-11-20）。

ADR 只记录决策与依据；当前规则以 [BASELINE](../BASELINE.md)、对应领域契约和实施边界为准。历史 ADR 不被静默改写；被替代关系必须双向链接。
