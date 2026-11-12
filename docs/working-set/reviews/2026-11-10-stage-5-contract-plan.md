# 阶段 5：契约层计划（Contracts）

**状态：Draft（计划文件；不构成任何业务裁定、ADR 或基线发布授权）**

- 日期：2026-11-10
- 前提：[阶段 4 收尾](2026-11-10-stage-4-productspace-classification-review.md#8-阶段-4-收尾2026-11-10)已完成，[首版基线审核](2026-11-10-baseline-v1-audit.md)结论「通过（附观察项）」。
- 更新：2026-11-11。T1 已关闭，T2～T4 已冻结；当前准备 T5，尚无实现验证证据。

## 1. 阶段目标

把已确认的业务规则收敛为**可测试的跨边界契约**，使 Implementation 有明确 seam、输入输出与失败语义可依。本阶段不重新开放已确认的 FACT / POL / INV 业务子句；发现语义缺口时回退到裁定流程，而不是在契约中暗补新概念。

## 2. 议题顺序与产出物

| 议题 | 当前状态 | 契约与验证入口 |
| --- | --- | --- |
| T1 Merge | 已关闭 | [行为契约](../domain/merge-execution-contract-v1.md)、[评审](2026-11-11-t1-contract-review.md) |
| T2 绑定与删除 | 已冻结 | [行为契约](../domain/binding-deletion-contract-v1.md)、[评审](2026-11-11-t2-binding-deletion-review.md) |
| T3 物化 | 已冻结 | [行为契约](../domain/lighttrackeritem-materialization-contract-v1.md)、[评审](2026-11-11-t3-materialization-review.md) |
| T4 跨 BC | Working Set 侧已冻结；K3″ 团队开放 | [行为契约](../domain/cross-bc-contract-v1.md)、[评审](2026-11-11-t4-cross-bc-review.md) |
| T5 最小纵向切片 | 准备中；待 seam、场景期望与执行证据 | [implementation/](../implementation/README.md) |

各议题的确认范围统一由 [BASELINE](../BASELINE.md) 登记；备选方案、裁定过程与反例保留在评审和契约中。

## 3. 工作方法（每个议题统一）

遵循根目录 [AGENTS.md](../../../AGENTS.md) 的阶段门、方案比较与验证规则。每条契约关联场景，测试通过公共 seam 验证可观察结果；语义缺口返回设计层处理。

## 4. 进入 Implementation 的门禁

- T1～T4 中与首切片相关的契约草案已由用户确认；
- 公共 seam（聚合公开行为、应用用例合同、跨 BC 集成契约）书面约定；
- 场景-期望表可执行（表格驱动即可，不要求先有生产代码）；
- 无 P0 阻塞项：`QUESTION-015`（T1）必须闭合，`QUESTION-014` 至少给出候选治理方向。

## 5. 风险与依赖

- K3″ 版本治理待四 BC 共同评审；已有候选方向，不作为 T5 首切片默认阻塞。
- Token 编码、幂等键、事件顺序、Checkpoint 字段映射等须在实施中兑现既有契约。
- 图表尚需实际渲染核验；仓库尚无运行测试或实现验证证据。
- 其他问题统一见[开放问题](../domain/open-questions.md)。

## 6. 不在本阶段范围

- 数据库/表结构、ORM 映射、部署拓扑、消息中间件选型（属实现设计，须在契约内选择）。
- 重新裁定已确认的 D-001～D-019 业务规则。
- 正式发布、Git Commit、Tag。

## 7. 下一步

**T5-S0 已完成**（2026-11-11，[S0 审核](../reviews/2026-11-11-t5-s0-design-review.md)）：产出[最小纵向切片设计](../implementation/t5-s0-minimal-slice-design.md)（含 P1～P5 工程选型、A1～A3 约束、最小目录/依赖、接口清单与 T1～T4 冲突检查）、[架构与首次 COW 时序图](../diagrams/t5-s0-architecture.md)、[AT-01～AT-05 与 AC-01/AC-02 映射](../implementation/t5-s0-scenario-test-mapping.md)、[S1～S4 Codex 任务清单](../implementation/t5-s0-codex-task-plan.md)。范围仅 ALM Requirement；不含 Merge、删除、权限与版本治理。

**P1～P5 已统一、A1～A3 已确认；S1 可立即启动**——[S1 任务说明](../implementation/t5-s1-codex-task-brief.md) 可直接交付 Codex（白名单范围、10 步 Red→Green、5 类测试与 DoD）。P1 = Maven + Java 21（依据本地 JDK 21.0.7 与 Maven 3.9.11；部署/CI 环境未验证）。仓库当前无构建系统与 Java 代码（R1），S1 第一件事为建骨架。

**T5-S1 已完成**（2026-11-11，[验证结果](../implementation/t5-s1-verification-result.md)）：`mvn test` **12 项通过**（AT-01/AT-02、resolve 的 NOT_FOUND/NOT_INCLUDED、AC-01 ArchUnit、AC-02、LightTrackerItem 不变量 6 项）；建成 pom（Java 21 + spring-context + JUnit 5 + ArchUnit）与 28 个主源文件（`sk.workingset.*` / `alm.requirement.*` / `bootstrap.configuration`），AC-01 编译依赖检查通过。**S2 前置决定已定**（2026-11-11：Spring Boot 升级、Spring Event、雪花 ID 替换 UUID），[S2 任务说明](../implementation/t5-s2-codex-task-brief.md) 已就绪（含修改前快照与事件发布）。

**T5-S2 已完成**（2026-11-11，[验证结果](../implementation/t5-s2-verification-result.md)）：`mvn test` **35 项通过**。三项工程前置落地：Spring Boot 3.3.5 BOM + 非 web starter 与 `@SpringBootApplication` 主类；雪花 ID（3/3/5 位宽、回拨拒绝）替换 `LocalId`/`TrackerId` 的 UUID 路径；Spring Event 同步派发 `RequirementChanged`。业务闭环：`Requirement.changeTitle` 不可变且 `lock_version` 自动 +1、`revision`/`version` 不变；保存前捕获完整快照（`SnapshotStore`）；旧 `expectedLockVersion` 显式冲突且无事件/快照；事件仅在保存成功后发布。**术语修正（2026-11-11）**：`version` 为发布大版本（字符串、可空），`revision` 为手动触发的递增版次，`lock_version` 为乐观锁。

**T5-S3 已实施**。Modulith 提交后异步消费，业务物化、状态切换和失败记录独立提交。以 [S3 验证记录](../implementation/t5-s3-verification-result.md)为当前证据，具体边界见 [S3 实施契约](../implementation/t5-s3-codex-task-brief.md)。

**T5-S4 已通过单实例验收**。恢复、手动与启动重提、顺序边界、Revision 升级及剩余验证范围统一见 [S4 验证结果](../implementation/t5-s4-verification-result.md)。

**T5-S5 已通过首层范围验收**：WorkingSet 聚合、SpaceBinding/TrackerBinding、Shared/非共享绑定、解绑/删除/级联校验；`mvn test` 58 项通过。证据见 [S5 验证结果](../implementation/t5-s5-verification-result.md)。该切片只覆盖已登记的首层 WorkingSet，直接上游固定为默认 WorkingSet；该限定是实施范围裁定，不改变 T2 契约对多级谱系与停用的开放状态。

**T5-S6 多级 WorkingSet 谱系（设计完成，待评审/实施）**：FACT-028 已确认单父、`parentWorkingSetId`、空范围子 WS、直属父 Tracker 来源、相邻父子 Merge、中间 WS 删除/停用约束；设计见 [working-set-lineage-design.md](../domain/working-set-lineage-design.md)，图为 [working-set-lineage.md](../diagrams/working-set-lineage.md)。实施切片：S6-A 谱系与创建子 WS、S6-B Tracker 来源与范围、S6-C Merge 准入、S6-D 删除/停用约束。
