# T5-S0 最小纵向切片设计（首切片：非共享派生 → COW 物化 → Query）

**状态：Draft（实施设计；不改变 T1～T4 已冻结契约；本轮不含生产代码）**

- 日期：2026-11-11
- 阶段：阶段 5 · T5-S0（S1 实施前置设计）
- 已确认依据：T2（绑定与删除）、T3（物化协议，含 §2.1 内容固定范围）、T4（跨 BC 公共契约，含 §3.1 两层接口并存、§3.2 业务 Revision 不当乐观锁）、D-008～D-019、INV-070/071、SCN-021～049/066～068/072/073
- 本文件**不决定**：具体 API 的最终签名、序列化格式、事件投递实现、事务实现、生产持久化

## 1. 首切片范围

**目标闭环**：非共享 Tracker 派生 → 创建 LightTrackerItem（Source-backed）→ Source-backed Query → 来源普通修改（Revision 不升级）→ 首次 COW 物化 → Local-backed Query。

| 项 | 内容 |
| --- | --- |
| 参与 BC | 仅 **ALM Requirement** |
| 参与聚合 | `WorkingSet`（单工作集 + 绑定）、`Tracker`（非共享派生）、`LightTrackerItem`、ALM `Requirement` |
| 不在首切片 | Diff/Merge 全流程、四 BC 全量集成、ProductSpace/Tracker 删除、完整权限平台、公共契约版本治理（K3″）、通用 Saga/分布式事务框架、通用 SPI 插件注册平台 |

## 2. 工程选型 P1～P5

| 编号 | 决定 | 状态与依据 |
| --- | --- | --- |
| P1 构建与 JDK | **Maven + Java 21**（部署仅支持 17 时回退）；**ID 生成用雪花算法**（`IdentifierGenerator` + `SnowflakeIdGenerator`，单体 `@Configuration` 装配） | **已确定**：本机 JDK 21.0.7 LTS、Maven 3.9.11、`.idea` `languageLevel=JDK_21`；**雪花 ID 由用户 2026-11-11 决定**（与 FACT-005 一致，替换 S1 的 UUID）；部署/CI 环境未验证 |
| P2 应用框架 | **Spring Boot + Spring Event + 纯 Java Domain**；Spring 承担启动/DI/事务/集成测试与事件派发；领域不依赖 Spring/ORM | **采用**（用户 2026-11-11 决定：升级 Spring Boot、事件框架用 Spring Event）；不引入微服务框架、服务发现、插件系统 |
| P3 代码组织 | 按 BC 组织 + DDD 分层（详见 §4）；**单 Maven 模块 + 逻辑包**，不强制每个目录成为模块 | **采用**（用户推荐）；旧 `biz / sdk / sdkImpl` 结构彻底退出新架构依据 |
| P4 快照承载 | **公共事件信封 + BC 自有不可变快照**（详见 §8） | **采用（概念级）**；序列化/可靠投递/顺序实现留实施协议 |
| P5 Requirement 字段 | `localId`、`commonId`、`trackerId`、`title`、`description`、`revision`、`version`、`lock_version` | **采用（首切片范围）**；`revision` 为手动版次，`version` 为发布大版本（字符串、可空），`lock_version` 为乐观锁（自动 +1）；不实现状态机、审批流、评论、附件、复杂属性模型 |

P1～P4 为工程实施决策，不新增领域 ADR；P5 字段集只服务首切片验证，不代表 ALM Requirement 最终模型。

## 3. 架构前置约束 A1～A3（用户已确认，2026-11-11）

### A1 Working Set 编译依赖

Working Set **编译期不依赖**具体业务 BC 的内容或实现；只依赖公共契约与自身 Outbound Port，ALM 等 BC 通过 Adapter 提供实现。禁止：

- Working Set Domain 直接引用 `Requirement` 聚合类；
- Working Set Application 直接依赖 ALM 的具体 Application Service 实现；
- Shared Kernel 直接操作其他 BC 的 Repository；
- 为快速实现首切片重新引入旧版模块耦合。

允许：Bootstrap/Composition Root 在组合阶段引用各实现完成装配。

### A2 LightTrackerItem 稳定身份

`LocalId` 能稳定定位业务 BC 中的同一业务变体，不依赖额外可变内容指针：

1. 派生时预分配 `LocalId`；2. 首次 COW 复用同一 `LocalId`；3. COW 不改变 `LocalId`/`CommonId`；4. BC 能按 `LocalId` 稳定定位或创建完整聚合；5. BC 自决数据库主键是否等于 `LocalId`；6. LightTrackerItem 不保存完整业务内容、不引入可变内容指针。

延续 T3 的 `materialized` 字段：`false` = Source-backed；`true` = Local-backed，且 `sourceLocalId=localId`、`sourceRevision=revision`；三字段在同一次聚合状态更新中保持一致。

### A3 跨聚合一致性与补偿目标

跨聚合操作必须**最终收敛**到一致的业务结果，不允许把中间部分提交冒充整体成功。区分三类：

1. **执行中的部分提交**：BC 保存成功、LightTrackerItem 切换失败 → 暂时不一致，属**待恢复过程**，不是已完成物化。
2. **最终一致与补偿方向**：按场景可选**正向恢复**（重试剩余步骤）或**反向补偿**（依明确业务规则撤销已提交影响）；不预先规定所有场景都回滚，也不假设都适合正向恢复。
3. **COW 与 Merge 的既有差异**：
   - COW 继续遵守 T3：保存成功切换失败**不回滚**业务内容；幂等恢复补齐切换；**恢复不得用旧快照覆盖**已保存且可能被后续合法修改的内容；保存与切换均成功后才可报告物化完成。
   - MergeRound 继续遵守 T1：多条目可独立成败；`PartiallySucceeded` 合法；已成功条目不因其他条目失败回滚；重做失败条目须新建轮次。

事务边界、补偿机制、幂等实现与恢复策略留专项实施设计，**不阻塞 S1**；本约束不重开 T1/T3 已冻结规则。

## 4. 最小工程目录与依赖方向

```text
ddd-workingset/
├── pom.xml                                  # 单 Maven 模块（Spring Boot BOM + starter，非 web）
├── src/main/java/
│   ├── sk/workingset/domain/                # LightTrackerItem、TrackerItem、值对象
│   ├── sk/workingset/application/           # 应用服务、CowEventHandler、port/、event/
│   ├── sk/workingset/infrastructure/        # 内存仓库、SnowflakeIdGenerator
│   ├── alm/requirement/domain/              # Requirement 聚合（实现 TrackerItem）
│   ├── alm/requirement/application/         # RequirementApplicationService
│   ├── alm/requirement/infrastructure/      # Adapter、内存仓库、快照存储
│   └── bootstrap/                           # 主类（@SpringBootApplication）+ configuration/（组合根、IdGeneratorConfig）
└── src/test/java/                           # 与主代码同构的包
```

**依赖规则**（AC-01 的判据）：

```
sk.workingset.domain        → 仅 JDK（纯 Java）
sk.workingset.application  → sk.workingset.domain + 自身 port
sk.workingset.infrastructure→ application/domain（内存实现）
alm.requirement.domain     → sk.workingset.domain 的公共契约（实现 TrackerItem；单向）
alm.requirement.application→ 自身 domain（+ 必要时 SK 公共值对象）
alm.requirement.infrastructure → sk.workingset.application.port（实现 Port）
bootstrap.configuration    → 所有实现（组合根）
禁止：sk.* → alm.*（任何包、任何形式）
```

## 5. 应用职责与聚合边界

| 组件 | 所属 | 职责 | 不拥有 |
| --- | --- | --- | --- |
| `WorkingSetApplicationService` | SK | `derive` / `resolve` / `read` 编排 | 业务内容、业务校验 |
| `CowEventHandler` | SK | 消费来源事件、定位目标、编排物化 | 业务内容构建 |
| `LightTrackerItem` / `Tracker` / `WorkingSet` | SK | 派生身份、物化状态与有效来源、范围与绑定 | 业务状态机、业务字段 |
| `TrackerItem`（领域接口） | SK 定义 | 公共身份与 Revision（由 BC 聚合实现） | 应用层调用与授权 |
| `TrackerItemReadPort` / `RequirementWritePort` | SK 定义（应用层 Outbound Port） | 只读能力；写入协作（物化保存） | 不实现业务逻辑 |
| `Requirement`（聚合） | ALM | 完整内容、不变量、`revision`/`version`/`lock_version`、提交；实现 `TrackerItem` | 工作集范围、绑定、物化状态；不实现应用层 Port |
| `RequirementApplicationService` | ALM | 创建/普通修改/保存；产生快照与事件 | 工作集编排 |
| `RequirementAdapter` | ALM | **另行实现** `TrackerItemReadPort` 与 `RequirementWritePort` | 不向 SK 开放 Repository 或通用 Save |
| `bootstrap` | 组合根 | 装配实现（Spring Bean） | 领域行为 |

**两层接口并存**（T4 §3.1 选择 B）：领域接口 `TrackerItem` 表达公共领域能力；应用层 Port 承接跨 BC 调用、授权与适配；聚合不实现应用层 Port。

## 6. 四项最小协作能力

| 能力 | 入参 | 行为 | 失败 |
| --- | --- | --- | --- |
| `derive` | 父 WS、非共享来源 Tracker、来源 Requirement + Revision/Head | 创建目标 Tracker（`sourceId` 指来源）+ LightTrackerItem（预分配 `localId`、继承 `commonId`、`sourceLocalId`=实际内容拥有者、`sourceRevision`=选定 Revision、`materialized=false`）；不复制内容 | `NotFound`、`NotIncluded` |
| `resolve` | 工作集 + Item 引用 | 返回有效变体视图（localId/commonId/revision/sourceLocalId/sourceRevision/materialized） | `NotFound`、`NotIncluded` |
| `read` | 工作集 + Item 引用 | `materialized=false` → 沿 `sourceLocalId` 直接定位并 `readAt(sourceRevision)`；`true` → `readAt(revision)` | `NotFound`、`NotIncluded` |
| `materialize` | 来源事件 + 目标 LocalId + 快照引用 | 调用 `RequirementWritePort.materialize` → 保存成功 → 聚合内切换三字段 | 保存失败不切换；切换失败只补切换 |

## 7. LightTrackerItem 聚合与不变量（A2 + T3）

| 行为/不变量 | 内容 |
| --- | --- |
| `derive(...)` | 构造并预分配 `localId`、继承 `commonId`、设置来源与 `materialized=false` |
| `switchToLocalBacked(revision)` | 一次设置 `materialized=true`、`sourceLocalId=localId`、`sourceRevision=revision`；不提供单独 setter |
| L1 | `materialized=false` ⇒ `sourceLocalId ≠ localId`，`sourceRevision` 为来源 Revision |
| L2 | `materialized=true` ⇒ `sourceLocalId == localId` 且 `sourceRevision == revision` |
| L3 | `localId`、`commonId` 派生后不可变 |
| L4 | `revision` 只由显式升级推进；来源普通修改不改已物化变体的 `revision` |
| 状态 | Source-backed → Local-backed 单向；`Materializing` 不作为业务状态 |

## 8. 来源事件与快照（P4）

- **生产（ALM）**：修改前读取完整内容 → 形成不可变快照（`snapshotRef`）→ 保存 → 发布 `RequirementChanged{eventId, requirementId, sourceLocalId, revision, snapshotRef, occurredAt}`。
- **事件信封**：携带来源 `LocalId`、业务 `Revision`、事件身份等公共元数据；业务字段由 ALM 定义与解释。
- **消费（SK）**：按顺序消费；在消费时查询 `materialized=false` 且 `sourceLocalId`/`sourceRevision` 匹配的目标（D-017）；快照只用于内容构建，**不读来源当前值**（D-011 首次快照优先）。
- **禁止**：以来源当前内容或可变 Head Revision 代替修改前快照；连续来源变更必须满足首次相关修改快照优先。

## 9. 保存与切换顺序（A3 + T3）

```
事件 → Handler 定位目标集合
  → 每个目标：RequirementWritePort.materialize(targetLocalId, snapshotRef, revision)
       ALM：以快照构建 Requirement 聚合 → 整体校验（含 lock_version 并发）→ 保存（幂等键 eventId + targetLocalId）
  → 保存成功 → LightTrackerItem.switchToLocalBacked(revision)（单聚合事务）
  → 记录物化完成
失败分支：
  保存失败        → 不切换，重试（幂等）
  保存成功切换失败 → 不回滚业务内容；重试只补切换（A3/T3 §7、SCN-067）
```

**内容固定范围（T3 §2.1 选择 A）**：首切片仅普通字段（`title`）；派生仅选定业务 Revision；附件与关系固定为派生时状态（R19），首切片不涉及，但实现不得用较新附件/关系覆盖派生基准（SCN-072）。

**并发与版次（T4 §3.2 + P5，2026-11-11 术语修正）**：写入携带独立 `lock_version` 作乐观锁；`lock_version` 在每次 command 处理后数据变动时自动 +1。`revision` 是手动触发的递增版次；`version` 是发布大版本（字符串、可空，只有某个版次发布时才出现）。普通修改只推进 `lock_version`，不升级 `revision`，也不改变 `version`。业务 `revision` 与发布 `version` 均不充当乐观锁。

## 10. 三字段一致性

- 三字段只能经 `switchToLocalBacked` 一次更新；聚合外无 setter。
- Query 以 `materialized` 判断，不用 `sourceLocalId != localId` 推导。
- 不一致视为元数据一致性缺陷：S1 用聚合后置条件断言暴露，不引入修复流程。

## 11. 最小 Java 接口与类型清单

以下为**类型与职责清单**（签名留实现微调；不构建通用框架）：

**SK 领域（`sk.workingset.domain`）**

| 类型 | 职责 |
| --- | --- |
| `LocalId` / `CommonId` / `Revision` / `Version` / `LockVersion` / `TrackerId` / `WorkingSetId` | 值对象（包装标识/版本/乐观锁，纯 Java）；`Version` 为发布大版本（字符串、可空），`LockVersion` 为乐观锁 |
| `TrackerItem`（接口） | `commonId()`、`localId()`、`revision()` |
| `LightTrackerItem`（聚合根） | `derive(...)`、`switchToLocalBacked(Revision)`、只读访问器（含 `materialized`） |

**SK 应用（`sk.workingset.application`）**

| 类型 | 职责 |
| --- | --- |
| `WorkingSetApplicationService` | `derive(DeriveCommand)`、`resolve(ResolveQuery)`、`read(ReadQuery)` |
| `CowEventHandler` | `onRequirementChanged(RequirementChanged)` |
| `TrackerItemReadPort`（port） | `readAt(Revision)`、`revision()`、`capabilities()`、`authorize(Operation, Caller)` |
| `RequirementWritePort`（port） | `materialize(LocalId, SnapshotRef, Revision)` |
| `LightTrackerItemRepository`（port） | `findBySource(LocalId, Revision)`、`findById(LocalId)`、`save(LightTrackerItem)` |
| `EventPublisher`（port） | `publish(DomainEvent)`（进程内实现） |
| `IdentifierGenerator`（port） | `long nextId()`：雪花 ID 生成（P1/用户 2026-11-11）；`LocalId` 由其产生 |

**ALM（`alm.requirement`）**

| 类型 | 职责 |
| --- | --- |
| `Requirement`（聚合，实现 `TrackerItem`） | `create(...)`、`changeTitle(String, LockVersion)`、`upgradeRevision()`（预留，不触发 COW）、访问器；`version` 发布大版本可空、`lockVersion` 乐观锁 |
| `RequirementId` | **即 `LocalId`**（用户 2026-11-11 裁定：LocalId 是所有业务对象聚合的唯一标识，不另建独立 ID） |
| `RequirementApplicationService` | `create(...)`、`changeTitle(...)`（快照 + 事件） |
| `RequirementAdapter` | 实现 `TrackerItemReadPort`、`RequirementWritePort` |
| `SnapshotStore` / `RequirementRepository`（内存） | 快照存取、聚合存取 |

**共享事件**

| 类型 | 职责 |
| --- | --- |
| `RequirementChanged` | 公共信封：`eventId`、`requirementId`、`sourceLocalId`、`revision`、`snapshotRef`、`occurredAt` |
| `SnapshotRef` | 不可变快照引用 |

**辅助类型（最小集，不扩展为通用框架）**

| 类型 | 职责 |
| --- | --- |
| `TrackerItemContent` | `readAt(Revision)` 的返回：内容 + 实际读取的 `revision`（BC 定义内容结构） |
| `CapabilitySet` | 能力声明集合（`comparable`/`mergeable`/`derivable`/`creatable`/`historyReadable`） |
| `Operation` | 授权判定用操作类型（`READ`、`WRITE`） |
| `Caller` | 调用者身份/上下文（首切片最小实现，权限系统不接入） |
| `Materialized` | 物化状态（首切片用 `boolean` 字段，不单独建值对象） |

**基础设施与组合根**

| 类型 | 职责 |
| --- | --- |
| `SnowflakeIdGenerator`（`sk.workingset.infrastructure`） | 实现 `IdentifierGenerator`：雪花算法（用户 2026-11-11 决定；去外部日志/异常/静态 holder 依赖） |
| `IdGeneratorConfig`（`bootstrap.configuration`） | `@Configuration` + `@Bean IdentifierGenerator`；`DATACENTER_ID=1`、workerId 由本机地址哈希取模 |
| `WorkingsetApplication`（`bootstrap`） | `@SpringBootApplication` 主类（非 web） |
| `ApplicationConfiguration`（`bootstrap.configuration`） | 组合根：装配内存仓库、应用服务、Adapter 工厂 |

`RequirementChanged` 由 **SK 定义、ALM 通过 Spring Event（`ApplicationEventPublisher`）发布**；S3 以 `@EventListener` 消费（默认同步、同线程顺序）。

**明确不构建**：通用 CRUD Repository、动态 SPI 注册器、通用 Saga/事务框架、跨 BC 事件总线平台、四 BC 通用适配层。

## 12. 与 T1～T4 的冲突检查

| 检查项 | 结论 |
| --- | --- |
| T4 §3.1 两层接口并存 | 一致：`Requirement` 实现 `TrackerItem`；`RequirementAdapter` 实现应用层 Port |
| T4 §3.2 业务 Revision 不当乐观锁 | 一致：新增 `lock_version` 作并发证据；`revision` 仅手动版次，`version` 仅发布大版本 |
| T3 §2.1 内容固定范围（A） | 一致：首切片普通字段；附件/关系不涉及但不得被覆盖 |
| T3 `materialized` 三字段一致性 | 一致：A2 与 §7/§10 |
| T3 COW 失败语义（保存成功不回滚） | 一致：A3 与 §9 |
| T2 非共享派生 / 绑定 | 一致：`derive` 仅走非共享路径；绑定只读 |
| T1 Merge 轮次语义 | 未进入首切片；A3 保留其语义，不重开 |
| AC-001 / QUESTION-013（BC 校验提交） | 一致：SK 不代写、不操作 BC Repository |
| 雪花 ID（P1） | 一致：FACT-005 要求 CommonId/LocalId 为雪花 ID；S1 的 UUID 偏离在 S2 修复 |
| 无冲突项 | **未发现实际冲突** |

## 13. 最小持久化与测试适配

| 项 | 最小实现 |
| --- | --- |
| LightTrackerItem 存储 | 内存仓库（`save`、`findById`、`findBySource`） |
| Requirement 存储 | 内存仓库（ALM） |
| 快照存储 | 内存 `SnapshotStore`（按 `snapshotRef` 取） |
| 事件派发 | 进程内同步、顺序（满足 D-018 顺序消费前提） |
| 装配 | Spring Boot 组合根（`bootstrap.configuration`） |
| 外部依赖 | 无数据库、无消息中间件 |

**测试层次**：聚合单元测试（LTI 不变量）；应用服务集成测试（derive/resolve/read）；事件-物化集成测试（AT-03/04/05）；**架构检查**（AC-01 编译依赖、AC-02 身份稳定）。

## 14. 工程风险与尚未确定项

| 编号 | 风险/未定项 | 状态 |
| --- | --- | --- |
| R1 | 仓库**无构建系统**（无 `pom.xml`，`src/` 为空，0 Java 文件） | 已确认；S1 第一件事为建骨架 |
| R2 | 部署/CI 运行环境未验证（仅本地 JDK 21 + Maven 3.9.11 证据） | 未验证；CI 接入时复核 P1 |
| R3 | 快照序列化与可靠投递 | 概念级已定（P4），实现留专项 |
| R4 | ~~`RequirementId` 与 `LocalId` 的映射~~ | **已裁定（用户 2026-11-11）：LocalId 是所有业务对象聚合的唯一标识，RequirementId = LocalId；S1 直接以 `LocalId` 作为聚合主键，不需要映射** |
| R5 | Mermaid 渲染未验证（无 `mmdc`） | 发布前复核 |

## 15. 追溯

- 契约：T2、T3（含 §2.1）、T4（含 §3.1/§3.2）、D-008～D-019、INV-070/071
- 场景与检查：SCN-021～049、SCN-066～068、SCN-072、SCN-073、AT-01～AT-05、AC-01/AC-02
- 裁定记录：[2026-11-11 一致性修正](../reviews/2026-11-11-consistency-reconciliation.md)
- 图：[T5-S0 架构与首次 COW 时序](../diagrams/t5-s0-architecture.md)
- 映射：[场景-期望-测试映射](t5-s0-scenario-test-mapping.md)
- 任务：[Codex 分阶段实施任务](t5-s0-codex-task-plan.md)；[S1 任务说明](t5-s1-codex-task-brief.md)
- 审核：[T5-S0 设计审核记录](../reviews/2026-11-11-t5-s0-design-review.md)
