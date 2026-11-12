# Implementation

**状态：Draft（目录用途；不接受任何具体实现方案）**

存放设计验证实验、验收测试说明、实现映射、迁移计划和验证结果。这里不复制生产代码，也不让实现结构反向定义未经确认的领域规则。

每项实现证据应链接对应的已接受设计或 ADR、相关业务场景和公共测试 seam；测试或实现发现语义缺口时，应回到相应设计层记录问题。

## 当前文档

- [t5-s0-minimal-slice-design.md](t5-s0-minimal-slice-design.md)：T5-S0 首切片最小纵向切片设计（Draft；P1～P5 工程选型、A1～A3 约束、最小目录/依赖、四项能力、LightTrackerItem 不变量、接口清单与 T1～T4 冲突检查）。
- [t5-s0-scenario-test-mapping.md](t5-s0-scenario-test-mapping.md)：AT-01～AT-05 与 AC-01/AC-02 场景-期望-测试映射（Draft）。
- [t5-s0-codex-task-plan.md](t5-s0-codex-task-plan.md)：S1～S4 Codex 分阶段实施任务（Draft）。
- [t5-s1-codex-task-brief.md](t5-s1-codex-task-brief.md)：S1 任务说明，可直接交付 Codex（Ready for implementation）。
- [t5-s1-verification-result.md](t5-s1-verification-result.md)：S1 验证结果（Verified，2026-11-11；`mvn test` 12 项通过，含 AT-01/AT-02/AC-01/AC-02）。
- [t5-s2-codex-task-brief.md](t5-s2-codex-task-brief.md)：S2 任务说明（Ready for implementation；Spring Boot 升级、雪花 ID、Spring Event、修改前快照与事件发布）。
- [t5-s2-verification-result.md](t5-s2-verification-result.md)：S2 验证结果（Verified，2026-11-11；`mvn test` 35 项通过，含 AT-03 前半、事件信封/顺序、并发冲突、Spring 上下文与雪花 ID）。
- [S3 实施记录](t5-s3-codex-task-brief.md)保留当时范围与事务决策。
- [S3 验证结果](t5-s3-verification-result.md)保留 AT-03/AT-04 与失败前置的历史证据，并登记内存持久化删除及测试迁移的最新回归。
- [S4 验证结果](t5-s4-verification-result.md)记录 S3 收尾、AT-05、未完成事件重提、同源有界等待及 Revision 升级。
- [t5-s5-verification-result.md](t5-s5-verification-result.md)：S5 绑定与删除验证（Reviewed，2026-11-12；`mvn test` 58 项通过，真实 PostgreSQL/Redis/Tomcat；首层 WorkingSet 范围）。
- [t5-runtime-verification.md](t5-runtime-verification.md)：运行时骨架验证（Verified，2026-11-12；Spring Boot Web 7102 + PostgreSQL/JdbcTemplate + Redis 缓存 + Spring Modulith Event Publication Registry；`mvn test` 37 项通过，含 API/数据库/缓存与容器验收测试）。

配套：[T5-S0 架构与首次 COW 时序](../diagrams/t5-s0-architecture.md)、[T5-S0 设计审核记录](../reviews/2026-11-11-t5-s0-design-review.md)。
