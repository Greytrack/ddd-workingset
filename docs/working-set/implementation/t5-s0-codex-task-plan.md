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

- `mvn test`：AT-01、AT-02 集成测试（内存仓库 / 内存 Adapter）；
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
- `alm.requirement.infrastructure`：`SnapshotStore`（内存）。

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

## S3 首次 COW 与状态切换

**允许修改的代码范围**

- `sk.workingset.domain`：沿用 S1 的 `LightTrackerItem.switchToLocalBacked(Revision)`（三字段一次更新，不新增领域行为）；
- `sk.workingset.application`：`CowEventHandler`、`CowEventDispatcher`、`CowFailureLog`、`RequirementWritePort`、`MaterializeRequest`、`MaterializationRejectedException`；`RequirementChanged` 拆分为 `beforeRevision`/`afterRevision` 的最小接口调整；
- `sk.workingset.infrastructure`：`InMemoryLightTrackerItemRepository` 读写实例隔离；`SerialPerSourceCowEventDispatcher`（同源串行 + 可注入执行器）；
- `alm.requirement.application`：`RequirementApplicationService` 发布修改前/提交后版次；
- `alm.requirement.infrastructure`：新增 `RequirementMaterializationAdapter` 实现 `RequirementWritePort.materialize(MaterializeRequest)`；`RequirementAdapter` 保留读取职责与构造方式；
- `bootstrap` / `bootstrap.configuration`：Listener 只入队；注册 Handler、dispatcher、执行器与失败记录；读取/物化 Adapter 分别装配。

**输入依赖**：S2 完成。

**需要建立的接口与行为**

| 项 | 最小行为 |
| --- | --- |
| 目标定位 | `materialized=false` 且 `sourceLocalId`/`sourceRevision` 匹配 **`beforeRevision`**（消费时确定，D-017）；`afterRevision` 不参与定位 |
| 异步投递 | 来源 Command 保存成功后发布事件并立即返回；Listener 只入队；COW 在独立执行上下文执行（S3 的“提交”= 保存成功返回） |
| 同源顺序 | 按 `sourceLocalId` 串行执行、跨来源并行；顺序调用下首次快照优先；并发同源交错与跨进程顺序留 S4 |
| 业务保存 | 以**快照**构建 `Requirement`（复用预分配 `localId`；目标 `revision` 取自身版次，`version` 继承快照，`lock_version=0`）；先核验快照来源身份/版次/commonId，整体校验后保存 |
| 既有目标 | 不得直接当成功；内容与身份同本次物化证据一致才正常返回，否则拒绝且不覆盖 |
| 状态切换 | 正常返回后，单聚合事务内一次更新 `materialized=true`、`sourceLocalId=localId`、`sourceRevision=revision`（`revision` 为 `target.revision()`） |
| Local-backed Query | `read` 返回本地内容（`readAt(revision)`）；异步窗口内仍走 Source-backed |
| 失败可观察 | 物化/切换失败写入 `CowFailureLog`（事件、目标、来源、版次、阶段、异常、时间），不只日志 |
| 失败分支 | 物化未正常返回不切换；切换失败不回滚业务内容；内存仓储隔离保证重新读取仍为 Source-backed（S4 补恢复） |

**可执行测试**

- AT-03（异步最终状态）：命令返回时目标未物化、`read(WS1)` 为来源当前值 `B`；异步完成后物化内容为修改前 `A`（非 `B`）；三字段一致；
- AT-04：物化后来源再改为 `C` 并异步完成后，`read(WS1)` 仍为 `A`，`read(WS0)` 为 `C`；
- 异步窗口与完成信号使用可控执行器，不用固定 `Thread.sleep`；
- 同源顺序：连续 `A→B` 按序入队，首次快照 `A` 生效；跨来源互不阻塞；
- 保存失败不切换；切换失败后重新读取仍为 Source-backed；失败均可从 `CowFailureLog` 查询；
- 快照缺失、来源身份/commonId 不匹配、既有目标证据不一致：拒绝且不切换；
- 来源/目标版次分离：来源 `beforeRevision=3`、目标 `revision=1`，物化后目标仍为 1；
- 升级型事件（直接构造 `before=3/after=4`）用 `beforeRevision` 定位；
- Spring 上下文：Listener/Dispatcher/Handler/Port/Executor 可装配，失败不冒泡到来源命令；
- **AC-02（物化部分）**：物化前后 `localId` 相同，按身份断言。

**完成定义**

- AT-03/AT-04 以异步最终状态通过；三字段只有一个写入入口；保存失败不切换、切换失败可读回 Source-backed 有测试；
- Handler 不直接操作 ALM Repository（只经 `RequirementWritePort`）；读/写 Adapter 分离；
- 事件 before/after 语义在发布端与消费端一致，未把 `afterRevision` 用于目标定位或快照核验；
- 来源命令不等待 COW；同源串行、跨来源并行；失败写入可查询记录；异步测试无固定 sleep。

**排除范围**：可靠投递（outbox/重试/死信/去重）、跨进程顺序、并发同源交错、切换失败的幂等恢复、生产消息中间件、Merge。

---

## S4 一致性恢复与回归

**允许修改的代码范围**

- `sk.workingset.application`：`CowEventHandler` 的幂等与恢复处理；
- 测试：故障注入（`LightTrackerItemRepository.save` 首次失败）、重复事件、连续修改顺序回归。

**输入依赖**：S3 完成；A3（部分提交 → 正向恢复）。

**需要建立的接口与行为**

| 项 | 最小行为 |
| --- | --- |
| 幂等键 | `eventId + targetLocalId`；同一键重复处理不产生第二个业务对象 |
| 恢复 | 保存成功但切换失败 → 重试**只执行切换**，不重建/覆盖已保存内容（即使来源已有后续合法修改） |
| 重复事件 | 已 `materialized=true` 的变体直接跳过（INV-038） |
| 首次快照顺序 | 连续修改 `A→B→C`，目标物化内容为 `A`（首次相关修改前快照） |

**可执行测试**

- AT-05：业务对象数量仍为 1、只补切换、最终三字段一致；
- 重复事件不产生对象；连续修改顺序回归（SCN-030/067）。

**完成定义**

- AT-05 通过；重复事件与顺序回归测试通过；
- 不引入生产级重试队列、死信队列或监控平台；
- 恢复路径不改变 T3 的「不覆盖已保存内容」语义。

**排除范围**：跨进程重试、分布式事务、告警平台、版本治理。

---

## 交付节奏与门禁

1. 每个任务开始前确认前置已完成（S1 前置为 P1～P5 统一）；
2. 每个任务结束提交：测试清单 + Green 证据（`mvn test` 输出）+ 与映射表（AT/AC）的对应关系；
3. 若发现需要新的业务裁定（例如字段集不足、并发语义冲突），停止编码并在设计层标注 Proposed，不得在代码中暗补；
4. 全部四任务完成后，`implementation/` 记录「T5-S1 验证结果」，回链 AT-01～AT-05 与 AC-01/AC-02。
