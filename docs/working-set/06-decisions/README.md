# 决策记录（ADR 与裁定时间线）

**状态：Accepted（目录用途；ADR-0001～0006、0008～0010 有效，ADR-0007 已由 0008 替代）**

本目录保存两类决策材料：

- **[裁定时间线](timeline.md)**：按时间串起 `D-001`～`D-019`、`T1`～`T7` 契约冻结、`QUESTION` 关闭点与 ADR 接受／替代关系。需要「为什么这样定」时从时间线进入。
- **ADR**：只记录难以逆转、缺少背景会令人意外、且存在真实权衡的重要领域或架构决策。新增时用 [ADR-TEMPLATE.md](ADR-TEMPLATE.md) 起草，按 `adr-NNNN-short-title.md` 递增编号。

ADR 状态为 `Proposed`、`Accepted`、`Rejected` 或 `Superseded`。AI 可以起草 `Proposed` ADR，但只有用户或相应决策权威明确确认后才能标记为
`Accepted`。ADR 只记录决策与依据；当前规则以 [BASELINE](../01-baseline.md)、对应[契约](../05-contracts/)和[实现现状](../07-implementation.md)为准。历史 ADR 不被静默改写；被替代关系必须双向链接。

## ADR 索引

| ADR                                                    | 标题                                             | 状态       | 日期       | 替代关系                           |
|--------------------------------------------------------|--------------------------------------------------|------------|------------|------------------------------------|
| [ADR-0001](adr-0001-tracker-independent-aggregate.md)  | Tracker 独立聚合                                 | Accepted   | 2026-11-10 | —                                  |
| [ADR-0002](adr-0002-lighttrackeritem-authoritative-aggregate.md) | LightTrackerItem 作为 Working Set 权威聚合 | Accepted   | 2026-11-10 | —                                  |
| [ADR-0003](adr-0003-cow-event-driven-final-consistency.md) | COW 采用事件驱动最终一致与首次快照优先      | Accepted   | 2026-11-10 | —                                  |
| [ADR-0004](adr-0004-productspace-independent-aggregate.md) | ProductSpace 独立聚合与 WorkingSet 绑定模型 | Accepted   | 2026-11-10 | —                                  |
| [ADR-0005](adr-0005-merge-execution-model.md)          | Merge 执行模型与 MergeEntry 行为契约             | Accepted   | 2026-11-10 | Checkpoint 确认归属由 ADR-0006 替代 |
| [ADR-0006](adr-0006-merge-confirmation-ownership.md)   | 合并确认归属轮次，跨轮次独立查询                 | Accepted   | 2026-11-20 | 替代 ADR-0005 的 Checkpoint 部分    |
| [ADR-0007](adr-0007-root-folder-tracker-per-working-set-space.md) | 每个 Working Set 的产品空间拥有独立根文件夹 Tracker | Superseded | 2026-11-20 | 由 ADR-0008 替代                   |
| [ADR-0008](adr-0008-tracker-third-level-container.md)  | Tracker 是产品空间下的第三级容器，Folder 是业务对象 | Accepted | 2026-11-20 | 替代 ADR-0007                      |
| [ADR-0009](adr-0009-merge-dispatch-frozen-evidence.md) | 合并按批准时冻结的值分发                         | Accepted   | 2026-11-21 | —                                  |
| [ADR-0010](adr-0010-working-set-creation-tracker-baseline-time.md) | 工作集创建与扩展按 Tracker 基线时间尽力派生 | Accepted   | 2026-11-21 | 替代 T7-D 受理冻结与快照存储        |

## 相关文档

- [业务场景集](../09-scenarios.md)：每个裁定对应的可观察场景。
- [事实、政策与不变量](../03-rules.md)：规则编号与状态。
- [归档评审](../archive/reviews/)：裁定当时的论证、异议与反例。
