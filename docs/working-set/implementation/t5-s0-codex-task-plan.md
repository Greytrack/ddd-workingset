# T5-S0 Codex 分阶段实施任务清单（S1～S4）

**状态：Draft（实施编排；每项任务的代码范围与完成定义；不含生产代码）**

- 日期：2026-11-11
- 依据：[T5-S0 最小纵向切片设计](t5-s0-minimal-slice-design.md)（P1～P5、A1～A3）、[场景-期望-测试映射](t5-s0-scenario-test-mapping.md)、T3/T4 契约
- 前置：**P1～P5 已统一**（P1 = Maven + Java 21，依据本地环境证据；P2 Spring Boot + 纯 Java Domain；P3 单模块 + BC/DDD 分层；P4 事件信封 + BC 自有快照；P5 七字段），**A1～A3 已确认**
- 执行方式：每任务按 Red → Green 推进，一次一个行为；测试通过即签核该任务

## 任务总览

| 任务 | 目标 | 对应场景/检查 | 输入依赖 |
| --- | --- | --- | --- |
| S1 | 工程骨架与派生/查询 | AT-01、AT-02、AC-01、AC-02（派生部分） | P1～P5、A1/A2 |
| S2 | 来源修改与快照事件 | AT-03（前半） | S1 |
| S3 | 首次 COW 与状态切换 | AT-03、AT-04 | S2 |
| S4 | 一致性恢复与回归 | AT-05 + 重复事件/首次快照回归 | S3 |

---

## S1 工程骨架与派生/查询

**允许修改的代码范围**

- 新建 `pom.xml`（单 Maven 模块，Java 21，JUnit 5，Spring Boot 仅用于装配与集成测试）；
- 新建 `src/main/java` 包：`sk.workingset.domain`、`sk.workingset.application`（含 `port`）、`sk.workingset.infrastructure`、`alm.requirement.domain`、`alm.requirement.application`、`alm.requirement.infrastructure`、`bootstrap.configuration`；
- 新建 `src/test/java` 同构包（含架构测试）；
- **不允许**：多模块、数据库、消息中间件、微服务框架、动态插件系统。

**输入依赖**：P1（JDK 21 + Maven 3.9.11 已确认）、P2、P3（包结构与依赖规则）、P5（字段集）。

**需要建立的接口与行为**

| 项 | 最小行为 |
| --- | --- |
| 值对象与领域接口 | `LocalId`/`CommonId`/`Revision`/`Version`/`LockVersion`/`TrackerId`/`WorkingSetId`；`TrackerItem`（`commonId()`/`localId()`/`revision()`）；`Version` 为发布大版本（字符串、可空），`LockVersion` 为乐观锁 |
| `LightTrackerItem`（聚合根） | 派生构造：预分配 `localId`、继承 `commonId`、`sourceLocalId`=实际内容拥有者、`sourceRevision`=选定 Revision、`materialized=false`；只读视图 |
| `Tracker`（最小） | 非共享派生：新身份 + `sourceId` 指向来源 |
| `WorkingSetApplicationService` | `derive`、`resolve`、`read`（`materialized=false` 时沿 `sourceLocalId` 读来源 revision） |
| `TrackerItemReadPort`（SK Port） | `readAt(Revision)`、`revision()`、`capabilities()`、`authorize(Operation, Caller)` |
| `LightTrackerItemRepository`（SK Port） | `save`、`findById(LocalId)`、`findBySource(LocalId, Revision)` |
| ALM `Requirement`（聚合） | 最小字段（P5）；实现 `TrackerItem` |
| ALM `RequirementApplicationService` | `create(...)` |
| ALM `RequirementAdapter` | 实现 `TrackerItemReadPort` |
| `bootstrap.configuration` | Spring `@Configuration`/`@Bean` 装配上述实现 |

**可执行测试**

- `mvn test`：AT-01、AT-02 集成测试，当前回归采用 Mockito 仓储与真实读 Adapter；
- `resolve` 的 `NotFound`、`NotIncluded` 各一测试；
- **AC-01**：ArchUnit 规则（`sk.workingset..` 不依赖 `alm.requirement..`）；
- **AC-02（派生部分）**：派生后 `localId` 已分配且稳定、`commonId` 继承、`materialized=false`。

**完成定义**

- AT-01、AT-02 通过；AC-01 规则通过；失败分类测试通过；
- 主代码中无 COW/事件/物化代码；`src/main` 与 `src/test` 均编译通过；
- 依赖方向符合设计 §4 规则（SK 不含 `alm` 引用）。

**排除范围**：事件、快照、COW、物化切换、Merge、删除、权限、版本治理。

---

## S2 来源修改与快照事件

**允许修改的代码范围**

- `pom.xml`：升级 Spring Boot（BOM + starter，非 web）；
- `bootstrap`：主类（`@SpringBootApplication`）、`IdGeneratorConfig`（`@Bean IdentifierGenerator`）、组合根调整；
- `sk.workingset.application.port`：`IdentifierGenerator` 接口；
- `sk.workingset.infrastructure`：`SnowflakeIdGenerator`（去外部依赖）；
- `sk.workingset.application`：`RequirementChanged` 事件类型（公共信封）；
- `alm.requirement.domain`：`Requirement` 增加 `changeTitle(String, LockVersion)`（推进 `lock_version`，不升级 `revision`、不改变发布 `version`）；
- `alm.requirement.application`：`changeTitle(...)` 编排（先取快照、再保存、再发事件）；
- `alm.requirement.infrastructure`：`SnapshotStore`。当前快照回归使用 PostgreSQL JDBC。

**输入依赖**：S1 完成；P4（信封 + BC 自有快照）；用户 2026-11-11 决定（Spring Boot 升级、Spring Event、雪花 ID）。

**需要建立的接口与行为**

| 项 | 最小行为 |
| --- | --- |
| Spring Boot 升级 | BOM + starter（非 web）+ 主类；升级后原 12 项测试仍绿 |
| 雪花 ID | `IdentifierGenerator` + `SnowflakeIdGenerator` + `IdGeneratorConfig`；`LocalId` 由其生成，删除 UUID 路径 |
| 普通修改 | `title: A→B`，`lock_version` 自动 +1，**`revision` 不升级、`version` 不变**；`expectedLockVersion` 乐观锁 |
| 修改前快照 | 保存前读取完整内容写入 `SnapshotStore`，返回 `snapshotRef` |
| 来源事件 | `RequirementChanged{eventId, requirementId, sourceLocalId, revision, snapshotRef, occurredAt}`；经 `ApplicationEventPublisher` 发布（S2 不消费）。**S3 修订**：拆分为 `beforeRevision`/`afterRevision`，见 [S3 任务说明](t5-s3-codex-task-brief.md) §3.1 |
| 并发证据 | 写入以 `lock_version` 作乐观锁前置（`Revision` 与发布 `version` 不参与并发判定） |

**可执行测试**

- AT-03 前半：`revision` 保持 3、`lock_version` 递增、`version` 保持为空、快照内容为 `A`、事件被发布且载荷完整；
- 事件顺序可观察（同步总线断言）。

**完成定义**

- 上述测试通过；未引入 Handler 或物化逻辑；
- `RequirementChanged` 为不可变类型，字段与信封约定一致。

**排除范围**：目标定位、物化保存、三字段切换、Local-backed Query。

---

## S3 首次 COW 与状态切换（已完成，2026-11-12）

**实施结果**：首次 COW 已接入 Modulith 异步监听器、`SourceSerialCowCoordinator`、独立事务与 `cow_failures`；`mvn test` **46 项通过**，真实 PostgreSQL/Redis/Tomcat 验收见 [S3 验证结果](t5-s3-verification-result.md)。

**已落地**

- `sk.workingset.application`：`CowEventHandler`、`SourceSerialCowCoordinator`、`CowFailureLog`、`CowMaterializationFailedException`、`RequirementWritePort`、`MaterializeRequest`、`MaterializationRejectedException`、`TargetTransactionRunner`；`RequirementChanged` 拆分 before/after Revision 与 LockVersion。
- `sk.workingset.infrastructure`：`JdbcCowFailureLog`；`CachingLightTrackerItemRepository` 改为事务提交后写缓存。
- `alm.requirement.application` / `infrastructure`：发布修改前后证据；`RequirementMaterializationAdapter` 独立事务保存业务内容并校验快照/既有目标。
- `bootstrap`：`RequirementChangedListener` 使用 `@ApplicationModuleListener`，异常保留事件未完成；`ApplicationConfiguration` 装配 `REQUIRES_NEW`、协调器、失败记录与写 Port。

**行为**

| 项 | 已实现 |
| --- | --- |
| 目标定位 | `materialized=false` 且 `sourceLocalId`/`sourceRevision` 匹配 `beforeRevision`（D-017） |
| 异步投递 | Modulith 在来源事务提交后异步消费；来源命令不等待 COW |
| 同源顺序 | `SourceSerialCowCoordinator` 按 `sourceLocalId` 串行、按 `afterLockVersion` 排序；跨来源并行 |
| 业务保存 | 快照构建目标聚合，目标 revision 取自身版次，`version` 继承快照，`lock_version=0`；证据核验后独立事务提交 |
| 状态切换 | 保存成功后独立事务一次更新三字段 |
| 失败可观察 | `cow_failures` 落库；监听器抛异常，Modulith 保留 `completion_date=null` |

**测试**：AT-03/AT-04、首次快照优先、扇出、失败前置、缓存回滚、同源协调器、事件字段与 ArchUnit；详见 [S3 验证结果](t5-s3-verification-result.md)。

**S3 收尾**：内存持久化实现已删除，测试改用 Mockito 与真实 JDBC。当前测试范围见 [最小持久化与测试适配](t5-s0-minimal-slice-design.md#13-最小持久化与测试适配)。

**排除范围**：自动重试、去重、跨实例/并发同源顺序、跨聚合恢复、Revision 升级端到端、Merge/删除/权限/版本治理（S4+）。

---

## S4 一致性恢复与回归（已完成，2026-11-12）

实施证据与剩余边界见 [S4 验证结果](t5-s4-verification-result.md)。

**允许修改的代码范围**

- `sk.workingset.application`：`CowEventHandler` 的幂等/恢复编排、`CowRecoveryService`、`SourceSerialCowCoordinator` 的有界等待；
- `sk.workingset.infrastructure`：`JdbcCowMaterializationStore`、`JdbcCowFailureLog` 增列；
- `alm.requirement.domain` / `application`：`upgradeRevision()` 与升级事件；
- `bootstrap` / `bootstrap.configuration`：恢复入口与事务装配；
- `src/main/resources/schema.sql`：`cow_materializations`、`cow_failures` 增列；
- `src/test/java/**`：AT-05、重复事件、乱序、升级、重提。

**输入依赖**：S3 完成（46 项通过）；Modulith `event_publication` 未完成记录可重提。

**需要建立的接口与行为**

| 项 | 最小行为 |
| --- | --- |
| 幂等键 | `eventId + targetLocalId` 记录到 `cow_materializations`；重复处理不产生第二个业务对象 |
| 恢复 | 业务保存成功但切换失败 → 重试**只执行切换**，不重建/覆盖已保存内容（即使来源已有后续合法修改） |
| 重复事件 | 已 `materialized=true` 的变体直接跳过（INV-038）；未完成记录可重提 |
| 顺序 | 同源按 `afterLockVersion` 有界等待缺失序号；跨实例用 advisory lock（可选）；ORDER_GAP 记录 |
| 升级 | `upgradeRevision()` 递增 `revision`/`lock_version`；事件 before/after 不同；目标按 `beforeRevision` 匹配 |
| 失败消费 | `cow_failures` 可查询/标记恢复；不引入告警平台 |

**可执行测试**

- AT-05：业务保存成功、切换失败 → 恢复只补切换；业务内容不变；LTI 最终 Local-backed；
- 重复事件/重提：不产生第二对象；`completion_date` 更新；
- 乱序：序号 2 先于 1，有界等待/ORDER_GAP；首次快照边界；
- 升级：`before=3/after=4`，目标 revision 保持自身值；
- 扇出：失败目标恢复不影响成功目标；
- 回归：46 项全绿；ArchUnit。

**完成定义**

- AT-05 通过；重提只补切换；重复事件幂等；顺序缺口有确定行为；升级事件端到端可验证；
- 不引入 MQ/分布式事务/全局串行/常驻重试 daemon；
- 恢复路径不改变 T3 的「不覆盖已保存内容」语义。

**排除范围**：跨进程分布式锁（若未实现）、告警平台、Merge/删除/权限/版本治理。

---

## S5 绑定与删除

分阶段实施结果、运行前置与剩余范围见 [S5 验证结果](t5-s5-verification-result.md)。

## S6 多级 WorkingSet 谱系

四片实施和回归见 [S6 验证结果](t5-s6-verification-result.md)。

## 交付节奏与门禁

1. 每个任务开始前确认前置已完成（S1 前置为 P1～P5 统一）；
2. 每个任务结束提交：测试清单 + Green 证据（`mvn test` 输出）+ 与映射表（AT/AC）的对应关系；
3. 若发现需要新的业务裁定（例如字段集不足、并发语义冲突），停止编码并在设计层标注 Proposed，不得在代码中暗补；
4. 全部四任务完成后，`implementation/` 记录「T5-S1 验证结果」，回链 AT-01～AT-05 与 AC-01/AC-02。
