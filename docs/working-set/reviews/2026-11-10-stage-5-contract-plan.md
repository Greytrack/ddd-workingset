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

**T5-S2 已完成**（2026-11-11，[验证结果](../implementation/t5-s2-verification-result.md)）：`mvn test` **35 项通过**。三项工程前置落地：Spring Boot 3.3.5 BOM + 非 web starter 与 `@SpringBootApplication` 主类；雪花 ID（3/3/5 位宽、回拨拒绝）替换 `LocalId`/`TrackerId` 的 UUID 路径；Spring Event 同步派发 `RequirementChanged`。业务闭环：`Requirement.changeTitle` 不可变且 `lock_version` 自动 +1、`revision`/`version` 不变；保存前捕获完整快照（`SnapshotStore`）；旧 `expectedLockVersion` 显式冲突且无事件/快照；事件仅在保存成功后发布。**术语修正（2026-11-11）**：`version` 为发布大版本（字符串、可空），`revision` 为手动触发的递增版次，`lock_version` 为乐观锁。**S3 任务说明已按异步策略修订**（2026-11-12，[S3 任务说明](../implementation/t5-s3-codex-task-brief.md)）：首次 COW 的目标定位、`RequirementWritePort` + `MaterializeRequest`、快照物化、三字段切换、保存/切换失败语义。本轮六项裁定：**A** 内存 Repository 用 `LightTrackerItem.restore` 做读写实例隔离（`save` 失败后重新读取仍为 Source-backed）；**B** 新增独立 `RequirementMaterializationAdapter`（无 LocalId 绑定，读/写 Adapter 分离）；**C** 物化请求区分来源身份/来源修改前版次/目标版次，并校验快照来源；**D** 事件用 `beforeRevision`/`afterRevision` 表达，目标匹配与快照核验用 `beforeRevision`，目标物化用 `target.revision()`（D-018 的升级触发 COW 不再被误写为不触发）；**E** `void` 返回只表示 BC 已确认有效物化写入，已存在目标须证据一致才允许切换，完整幂等与恢复留 S4；**F** S3 采用异步事件投递：来源 Command 保存成功后返回，Listener 只入队，`CowEventDispatcher` 按 `sourceLocalId` 串行执行（跨来源并行），失败写入可查询 `CowFailureLog`；不以 `@Async`/线程池/日志冒充可靠投递。测试改为异步最终状态验收（窗口内读来源内容、异步完成后读修改前快照），补齐来源/目标版次分离、快照缺失、身份不匹配、既有目标证据不一致、切换失败可读回、同源顺序、Spring 装配与异常不冒泡等项；可靠投递、并发重复投递、跨进程顺序与跨聚合恢复列入 S4。目标 `lock_version=0`、`version` 继承快照、Handler 捕获并记录失败不向来源抛出继续有效。
