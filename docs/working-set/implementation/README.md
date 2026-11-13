# Implementation

**状态：Draft（目录用途；不接受任何具体实现方案）**

存放设计验证实验、验收测试说明、实现映射、迁移计划和验证结果。这里不复制生产代码，也不让实现结构反向定义未经确认的领域规则。

每项实现证据应链接对应的已接受设计或 ADR、相关业务场景和公共测试 seam；测试或实现发现语义缺口时，应回到相应设计层记录问题。

## 当前文档

- [t5-s0-minimal-slice-design.md](t5-s0-minimal-slice-design.md)：T5-S0 首切片最小纵向切片设计（Draft；P1～P5 工程选型、A1～A3 约束、最小目录/依赖、四项能力、LightTrackerItem 不变量、接口清单与 T1～T4 冲突检查）。
- [t5-s0-scenario-test-mapping.md](t5-s0-scenario-test-mapping.md)：AT-01～AT-05 与 AC-01/AC-02 场景-期望-测试映射（Historical，S1 验收规划，已完成）。
- [S1～S4 原任务清单](t5-s0-codex-task-plan.md)保留历史版本入口。
- [t5-s1-verification-result.md](t5-s1-verification-result.md)：S1 验证结果（Verified，2026-11-11；`mvn test` 12 项通过，含 AT-01/AT-02/AC-01/AC-02）。
- [t5-s2-verification-result.md](t5-s2-verification-result.md)：S2 验证结果（Verified，2026-11-11；`mvn test` 35 项通过，含 AT-03 前半、事件信封/顺序、并发冲突、Spring 上下文与雪花 ID）。
- [S3 实施记录](t5-s3-codex-task-brief.md)保留当时范围与事务决策。
- [S3 验证结果](t5-s3-verification-result.md)保留 AT-03/AT-04 与失败前置的历史证据，并登记内存持久化删除及测试迁移的最新回归。
- [S4 验证结果](t5-s4-verification-result.md)记录 S3 收尾、AT-05、未完成事件重提、同源有界等待及 Revision 升级。
- [t5-s5-verification-result.md](t5-s5-verification-result.md)：S5 绑定与删除验证（Reviewed，2026-11-12；`mvn test` 58 项通过，真实 PostgreSQL/Redis/Tomcat；首层 WorkingSet 范围）。
- [t5-s6-verification-result.md](t5-s6-verification-result.md)：S6 多级 WorkingSet 谱系验证（Reviewed，2026-11-12；`mvn test` 68 项通过；S6-A～D、真实 PostgreSQL/Redis/Tomcat）。
- [S7 Merge 执行验证](t5-s7-verification-result.md)记录 Compare、MergeRound、Checkpoint 和 Requirement 删除生命周期的真实验收。
- [t5-d2-family-scope-verification.md](t5-d2-family-scope-verification.md)：D2 Requirement 家族唯一性按 WorkingSet 作用域（Reviewed，2026-11-12；`mvn test` 77 项通过；真实 PostgreSQL）。
- [t5-d4-sk-boundary-verification.md](t5-d4-sk-boundary-verification.md)：D4 把 LTI 校验与移除移回 SK，业务 BC 只经 `TrackerItemLifecyclePort` 与 `RequirementPurged` 事件协作（Reviewed，2026-11-12；`mvn test` 78 项通过）。
- [T6-B Merge 恢复验证](t6-b-merge-recovery-verification.md)记录并发 Apply、跨实例接续和事件重投的验收。
- [t6-next-stage-outline.md](t6-next-stage-outline.md)：T6 下一阶段实施大纲（Draft；切片顺序、验收与一致性门禁）。
- [t6-implementation-prompt.md](t6-implementation-prompt.md)：T6 实施提示词（交给实施者的完整任务说明；含必读顺序、切片任务、约束与验收命令）。
- [t5-runtime-verification.md](t5-runtime-verification.md)：运行时骨架验证（Verified，2026-11-12；Spring Boot Web 7102 + PostgreSQL/JdbcTemplate + Redis 缓存 + Spring Modulith Event Publication Registry；`mvn test` 37 项通过，含 API/数据库/缓存与容器验收测试）。

配套：[T5-S0 架构与首次 COW 时序](../diagrams/t5-s0-architecture.md)、[T5-S0 设计审核记录](../reviews/2026-11-11-t5-s0-design-review.md)。
