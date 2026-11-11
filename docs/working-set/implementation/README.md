# Implementation

**状态：Draft（目录用途；不接受任何具体实现方案）**

存放设计验证实验、验收测试说明、实现映射、迁移计划和验证结果。这里不复制生产代码，也不让实现结构反向定义未经确认的领域规则。

每项实现证据应链接对应的已接受设计或 ADR、相关业务场景和公共测试 seam；测试或实现发现语义缺口时，应回到相应设计层记录问题。

## 当前文档

- [t5-s0-minimal-slice-design.md](t5-s0-minimal-slice-design.md)：T5-S0 首切片最小纵向切片设计（Draft；P1～P5 工程选型、A1～A3 约束、最小目录/依赖、四项能力、LightTrackerItem 不变量、接口清单与 T1～T4 冲突检查）。
- [t5-s0-scenario-test-mapping.md](t5-s0-scenario-test-mapping.md)：AT-01～AT-05 与 AC-01/AC-02 场景-期望-测试映射（Draft）。
- [t5-s0-codex-task-plan.md](t5-s0-codex-task-plan.md)：S1～S4 Codex 分阶段实施任务（Draft）。
- [t5-s1-codex-task-brief.md](t5-s1-codex-task-brief.md)：S1 任务说明，可直接交付 Codex（Ready for implementation）。

配套：[T5-S0 架构与首次 COW 时序](../diagrams/t5-s0-architecture.md)、[T5-S0 设计审核记录](../reviews/2026-11-11-t5-s0-design-review.md)。
