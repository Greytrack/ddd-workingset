# 实现现状与验收证据

**状态：Reviewed（2026-11-24 静态核对；实现证据，不是新增基线裁定）**

本页是「代码现在怎样」的唯一权威：模块与上下文边界、跨 BC 读取边界、事件协作、与 TC-002 的剩余差距、查询侧实现记录与验收证据索引。业务语义仍以
[BASELINE](01-baseline.md)、[ADR](06-decisions/README.md) 与 [T1～T7 契约](05-contracts/)为准；本页不把当前代码反推为新的业务规则。

依据：用户 2026-11-19 移除单方法 Port 和文档对齐的要求；当前实现改为只读使用共享聚合，该读取边界在本页登记为
Reviewed，不扩大既有契约接受范围。核对范围为 2026-11-19 之后的实现提交与当前工作区。

## 1. 模块与上下文边界

当前 `shared-kernel` 模块已从单一 `sk.workingset` 包（迁移前的旧命名）扩展为以下包。它们**不是**四个企业业务 BC 之外的四个新
BC；是否拆为独立限界上下文仍需按语言、所有权和运行时证据裁定。

| 包                                              | 当前拥有的职责                                                                                  | 依赖/边界                                                                                                                                 |
|-------------------------------------------------|-------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `sharedkernel` | 通用业务对象、属性、版次、排序和轻量只读投影能力；`BusinessObject`/`Revisionable` 等共享契约    | 属于 Shared Kernel 的通用能力，不是 Working Set 业务模型；不得反向依赖各 BC。`validation` 子包存在迁移期旧栈依赖例外，见其包说明。        |
| `sharedkernel`           | Tracker 聚合根、创建/改名命令、Tracker 只读 Provider、创建后绑定事件                            | Tracker 是独立聚合。当前仍直接使用 `workingset` 包的 `WorkingSet`、`Classification` 类型；这是同进程实现依赖，不等于已冻结的跨 BC 合同。 |
| `sharedkernel`      | ProductSpace 聚合根、创建/删除命令、ProductSpace 仓储                                            | ProductSpace 是独立聚合；创建后通过事件推进默认 Tracker 与绑定，剩余差距见 §4。                                                          |
| `sharedkernel`        | WorkingSet/绑定、LightTrackerItem、COW、WorkingSet 创建 LRO、Compare/Merge、关系和查询 Provider | Working Set 核心能力；命令侧聚合仓储与查询侧 Provider 分离。                                                                              |
| `com.airedgesoft.alm.requirement`               | Requirement 参考实现、Merge 执行适配、物化请求消费、继承编辑查询回退                            | 业务 BC 参考实现；只读使用 SK 共享聚合/仓储，写入通过事件协作。                                                                           |

独立 `lib-id` 模块提供 `Ids.nextId()` 雪花门面；命令服务、仓储与测试统一调用该静态入口，不再注册或注入 MyBatis-Plus `IdentifierGenerator`。

`sharedkernel` 中的 `BusinessObject` 是**通用业务对象能力接口**，与
`sharedkernel`
的公共工作集契约不是同一概念；不得因为命名相近而把二者互相替代。

## 2. 跨 BC 读取边界（2026-11-19 调整）

- ALM 按版次写入 `air_inst_requirement_rev`。`id` 标识版次行，`instance_id` 对应稳定的 LocalId，`common_id` 对应跨工作集谱系。命令仓储、当前查询与历史回读均直接访问原生表。当前查询按实例取最大的 `revision`，历史回读按实例与指定版次定位。
  签出新增业务行，取消签出删除被撤销行并恢复前一版，签入在当前行保存有效状态位。业务事务提交对应事实事件，SK 在独立事务同步索引。当前 schema 和演示数据从空库重建。
- LightTrackerItem 的当前索引和版次成员分别写入两张旧索引表，由同一次聚合保存提交；签出、取消签出和签入规则见
  [双表索引与版次成员](05-contracts/t3-lti-materialization.md#31-双表索引与版次成员)。
- ALM 等业务 BC 可以**只读**使用 SK 的共享聚合和聚合仓储（如 `LightTrackerItemRepository.findById`、
  `WorkingSetRepository.findById`、`TrackerRepository` 读取），用于完成自身用例所需的身份和来源校验。LightTrackerItem
  的身份和来源解析供系统内部使用，业务内容由相应 BC 的 API 提供。
- 不再为单个查询方法新增专用同步 Port；`TrackerItemLifecyclePort` 及其 `findInheritedTarget` 已移除。集合读取和读模型查询使用
  `application/query/provider` 下的 Provider。
- 共享 Tracker 的 Item 列表按 Tracker 读取：共享 Tracker 通过 `TrackerBinding` 对其绑定工作集可见；非共享 Tracker 仅在所属工作集可见（`RequirementQueryService.findByTracker`）。不能再用 `air_inst_requirement_rev.workingset_id` 过滤共享 Tracker 的 Item。
- LightTrackerItem 自带 `classificationId` / `numericProductSpaceId`，分别写入索引的 `sub_class_id` / `work_space_id`；LTI 仓储不再 join `air_inst_common`，也不再从公共摘要推断子类。
- 多业务对象类型通过 `BusinessObjectTypeRegistry` / `BusinessObjectTypeAdapter` 按 Tracker 分类路由；一个 Tracker 只承载一种对象类型。当前只注册 Requirement 类型，新增 BC 只加 Adapter。
- 直接读取不等于获得写入权：业务 BC **不得**调用 SK 聚合仓储的 `save`、`delete`、`remove` 或修改 SK
  聚合状态；跨聚合状态变化仍必须通过可靠事件和各自独立事务完成（TC-002）。
- 直接读取的外部状态不能满足跨聚合原子校验；需要“先校验后写入”的规则仍由目标 BC 或事件消费方在本地事务中重新校验。

## 3. 当前事件协作

ALM 创建 Requirement 时普通读取 Tracker，并在插入时过滤已软删 Tracker。创建与 Tracker 删除使用可串行化事务，冲突返回 HTTP 409。Tracker 自身的变更和删除沿用命令侧行锁。

| 事实                              | 发布方                                                                     | 消费者/后续动作                                                                                                                                                                |
|-----------------------------------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Requirement 创建后登记 LTI        | ALM `TrackerItemCreatedEvent`                                              | SK 按公共登记确认对象仍存在，再幂等登记 Local-backed LTI。已软删对象登记删除标记，永久删除后的重投跳过。                                                                       |
| Requirement 来源内容改动           | ALM `BusinessObjectUpdateEvent` | SK 为依赖目标发布 COW 请求，各请求引用同一份修改前快照 |
| Requirement 首次继承编辑           | ALM `BusinessObjectLocalizedEvent` | SK 独立事务切换该目标的 LTI |
| Requirement 版次生命周期            | ALM 签出、取消签出与签入事件 | SK 独立事务按 [T3 契约](05-contracts/t3-lti-materialization.md#31-双表索引与版次成员) 同步索引 |
| COW 业务保存                      | SK `TrackerItemMaterializationRequestedEvent`                              | ALM 保存 Requirement 并发布 `TrackerItemMaterializationCompletedEvent`                                                                                                         |
| COW 完成                          | ALM `TrackerItemMaterializationCompletedEvent`                             | SK 在独立事务切换 Local-backed；失败终态由已持久化的请求和结果事件提供查询                                                                                                                                  |
| Merge 条目批准                    | SK `MergeEntryApprovedEvent` 携带目标 Tracker 分类、冻结应用值及目标期望锁 | ALM 在同一事务中按轮次和条目加锁、查询已持久化的结果事件、首次执行时保存目标并发布 `MergeEntryResolvedEvent`。结果发布记录承担批准事件重投的去重。证据校验留在轮次登记阶段。[ADR-0009](06-decisions/adr-0009-merge-dispatch-frozen-evidence.md) |
| Merge 条目结果与确认              | ALM `MergeEntryResolvedEvent`                                              | SK 在同一轮次事务持久化条目结果和所需确认，按需发布 `MergeAppliedEvent`；确认写失败时轮次不结束                                                                                |
| Merge 目标 LTI                    | SK `MergeAppliedEvent`                                                     | SK 独立事务切换/创建 LTI                                                                                                                                                       |
| 当前索引软删 / 恢复               | ALM `BusinessObjectDeleteEvent`                                            | SK 独立事务核对当前业务状态，更新 LTI `deleted` 并映射到旧表 `flags`；保留版次成员                                                                                             |
| 来源软删 / 永久删除               | ALM `BusinessObjectUpdateEvent` / `CompleteDeleteEvent`                    | 软删保存快照并触发物化；仍有来源依赖时拒绝永久删除，完成后 SK 异步清理来源 LTI                                                                                                 |
| 派生索引完成                      | SK `TrackerItemsIndexedEvent`                                              | 各业务 BC 投影公共查询视图（当前 ALM 空监听器，待定）；SK 不写 `air_inst_common`                                                                                               |

事件只表达已发生事实；请求不是完成事实。以上流程不引入通用写 Port，也不要求消息代理或事件溯源。

## 4. 与 TC-002 的剩余差距

| 路径              | 当前状态                                                                                                                                   | 差距/待验证                                                                                                                  |
|-------------------|--------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| ProductSpace 创建 | `ProductSpaceCommandService.create` 保存 ProductSpace 并发布 `ProductSpaceCreatedEvent`；默认 Tracker 逐个在独立事务提交，之后登记绑定事件 | 部分失败后，重投复用已提交的默认 Tracker；绑定仍在独立的 WorkingSet 事务完成。                                               |
| ProductSpace 删除 | [T2 §4.3](05-contracts/t2-binding-deletion.md#43-聚合删除)；通过准入后，同一事务保存 ProductSpace 删除标记并发布 `ProductSpaceDeleted` | 只写 ProductSpace 聚合；创建和绑定入口重读有效空间，迟到的默认初始化事件遇到已删除空间时结束。 |
| WorkingSet 删除   | [T2 §4.3](05-contracts/t2-binding-deletion.md#43-聚合删除)；拒绝仍有有效子工作集（含已落库的 Preparing 子级）、绑定或归属 Tracker 的工作集 | 同一事务保存工作集删除标记、同步通用业务登记并发布 `WorkingSetDeleted`。建立子级或写入目标范围时与删除互斥。 |
| T7 创建/扩展      | 阶段按项执行，但工作集创建、Tracker 派生、范围绑定及 LTI 索引仍与各自的任务进度同事务提交                                                  | 按[反模式审查](archive/reviews/2026-11-19-ddd-anti-pattern-scan.md)将聚合副作用与任务进度拆成独立提交，用可靠结果事件推进任务。 |
| Tracker 创建      | TrackerCommandService 仅持久化 Tracker，绑定由 `TrackerCreatedEvent` 的监听器在 Working Set 事务中完成                                     | 主路径已事件化；仍需验证监听失败、重复投递、绑定拒绝和运维可观察性。ProductSpace 初始化不在该路径内。                        |
| Tracker 删除      | 软删除：`Tracker.delete()` 标记 `deleted` 并返回 `TrackerDeletedEvent`，`TrackerRepository.save` 持久化 `delete_flag`；物理删除使用 `TrackerRepository.delete(聚合)`。保留 `requireDeletable`（无 Item、无组织子级、无派生子级）校验，绑定关系保留以支持将来恢复。 | 已验收无绑定 Tracker 与 ALM Item 创建并发。软删后绑定不可见但物理保留，恢复入口与可见性待设计。 |
| 继承编辑          | ALM 保存后由 `BusinessObjectUpdateEvent` 异步切换 LTI                                                                                      | 保存成功不保证 LTI 立即 Local-backed；读取方需容忍短暂 Source-backed。切换失败不回滚业务保存。                               |
| 文件夹分类        | 空间初始化和工作集创建、扩展由应用编排默认纳入文件夹分类。Tracker 仓储按自身行重建，仅保留分类引用和三层 ID。                              | Folder 业务能力由相应 BC 提供。见 [ADR-0008](06-decisions/adr-0008-tracker-third-level-container.md)。                       |

## 5. 已移除的旧声明

以下内容不再作为当前设计入口；历史实现记录仍保留在归档评审和验收文档中：

- `TrackerItemLifecyclePort`、`InheritedEditTarget`、`Caller` 值对象及其占位授权；
- `RequirementWritePort` 及同步 ALM 物化/合并写端口；
- `BusinessTrackerItems`、`RequirementAdapter`、`DerivedItemSummaryWriter`、`TrackerItemReadPortFactory`；
- `WorkingSetQueryService.resolve`、`TrackerItemQueryProvider.revision()` 及 `Capabilities` 中未使用的能力字段；
- Merge `requestId` 的 PostgreSQL advisory lock；当前由 `MergeRepository.claim` 使用 `ON CONFLICT (request_id) DO NOTHING` 原子认领；
- LightTrackerItem Redis 缓存装饰器及 `workingset.item-cache` 配置；
- “ALM 直接读取 SK 仓储一定违规”的笼统表述；当前只禁止直接写入，读取边界见 §2。
- `bootstrap.SnowflakeIdGenerator`、`bootstrap.configuration.IdGeneratorConfig` 与 MyBatis-Plus `IdentifierGenerator` 注入；ID 生成统一由 `lib-id` 的 `Ids.nextId()` 承担。

## 6. 查询侧实施记录

`application` 按命令/查询职责拆分，查询侧使用 MyBatis-Plus 只读适配器；命令侧继续使用聚合 + JdbcTemplate。

```text
com.airedgesoft.sharekernel.workingset.application.command
com.airedgesoft.sharekernel.workingset.application.query
com.airedgesoft.alm.requirement.application.command
com.airedgesoft.alm.requirement.application.query
```

- 命令服务：`WorkingSetCommandService`、`WorkingSetCreationService`、`WorkingSetDeletionService`、`MergeExecutionService`
  、COW/Merge 命令步骤。
- 查询服务：`WorkingSetQueryService`、`RequirementQueryService`。
- 查询模型 ID 使用 `Long`；Controller 继续输出字符串 ID，保持现有 JSON 契约。
- 事件定义已迁移到 `domain/event`；事件监听器仍在 `application/eventhandler`。
- 查询端口改为 `application/query/provider`；`TrackerItemCatalogQueryProvider`、`TrackerItemQueryProvider`、
  `TrackerQueryProvider`、`LightTrackerItemQueryProvider`、`ClassificationQueryProvider`、`CowFailureLogQueryProvider`、
  `RelationSourceQueryProvider` 等负责应用读取。
- 查询 Provider 实现位于 `infrastructure/providers` 与 `infrastructure/providers/mybatis`。
- Provider 实现可以复用聚合 Repository、写自定义 SQL，或使用 MyBatis-Plus Lambda；查询模型不受聚合装载方式限制。
- 聚合 Repository 接口声明在 domain；当前命令侧实现位于 `infrastructure/repositories`，不提供 LightTrackerItem 查询缓存。
- 当前查询已迁移：
    - WorkingSet 摘要、子级/祖先、Tracker 列表、Classification、Bindings；
    - Requirement 详情：持久化记录与未物化继承目标在同一读快照内依次解析，不再以聚合仓储重复查找同一详情
      ID；仅继承路径读取来源，仍校验来源版次。
    - Requirement 活跃身份目录由 ALM 查询 Provider 直接读取，供比较与派生索引使用；不经聚合仓储。Tracker 展示列表仅由
      Working Set 查询 Provider 提供。
    - Requirement 列表按 Tracker 读取：共享 Tracker 通过绑定对所有绑定工作集可见，非共享 Tracker 仅所属工作集可见；
      `workingSetId` 用于可见性校验，不再作为 `air_inst_requirement_rev.workingset_id` 过滤条件。
- Controller 暂未拆分，`WorkingSetController` 与 `RequirementController` 同时注入 command/query 服务。
- 跨聚合事务的实现差距见 §4。

### 6.1 聚合写入约定

- `WorkingSetRepository.save` 保存新建根及其成员，或保存持锁重建聚合的成员差量；现有根字段没有修改命令。绑定的增删及
  `shown` 修改均经聚合；空间绑定仍由 Folder Tracker 关系表示，未变更关系不重写。
- Tracker 与创建任务也统一用 `save`，保存分支由实现处理；Tracker 软删除经 `Tracker.delete()` 标记后由 `save` 持久化 `delete_flag`，物理删除使用 `TrackerRepository.delete(聚合)`。产品空间当前无更新命令；`save` 只写新对象，重复身份不覆盖。
- MergeRound 仓储已拆分 `claim`（按 requestId 原子认领新轮次）与 `save`（更新已有轮次状态）；MergeCheckpointQuery 直接从旧表只读查询跨轮次最新基准。LTI 原子家族占位不以普通保存替代。
- 产品空间、Tracker 与 Working Set 各有聚合仓储；入口及初始化的事务差距见 §4。产品空间直接读写 V3 结构的 `air_inst_product_space`，以 `id` 作为产品空间标识。Tracker 和空间绑定仍通过各自的外键列引用该 ID。创建与软删除写入对应审计字段，`delete_flag` 的 `0` 表示有效，`1` 表示已删除。

### 6.2 查询侧后续

- LTI 横向扫描已由 `LightTrackerItemQueryProvider` 承接；按 ID 读取身份聚合使用 `LightTrackerItemRepository`
  ，不再在查询服务暴露仅供测试的 `resolve` 入口。Tracker 集合读取由 `TrackerQueryProvider` 承接，Tracker 创建/改名命令使用该只读
  Provider 判定可见性。
- Creation/Merge 查询迁移到查询端口。
- Controller 按 command/query 拆成独立入口。
- 关系事件 outbox/消费方仍按 T7-E 开放项处理。

## 7. 验收证据索引

2026-11-23 WorkingSet 删除修复后，隔离 PostgreSQL 全量回归 211 项，209 项通过。绑定验收 16 项、工作集创建与扩展验收 25 项全部通过，覆盖删除后不可查询、Preparing 子级拒绝删除以及创建子级与删除父级的竞争。剩余两项失败分别是 Tracker 旧兼容列空值断言和监听器装配测试的旧类名断言。

2026-11-23 使用 Java 21 在专用 PostgreSQL 测试库执行 `mvn -B -pl deploys -am test`，共 207 项通过。shared-kernel 34 项、ALM 16 项、deploys 157 项，其中 ArchUnit 14 项。回归覆盖 LTI 迟到登记、恢复及旧事件重投、工作集父子创建与删除竞态、无绑定 Tracker 删除与 Item 创建竞态、派生来源不可用及批次提交。

| 主题          | 当前结论                                                                                                 | 记录                                                                                                                                                        |
|---------------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 运行基线      | Maven 多模块；BIGINT 身份；`air_inst_common` 是公共查询视图                                              | 本页 §1                                                                                                                                                     |
| T7 创建/扩展  | 按 Tracker 基线时间尽力派生；`baselineAtByTracker` 缺省 HEAD；重放复用原任务和基线                       | [A 契约](archive/reviews/2026-11-13-t7-a-creation-lro-review.md)、[B 任务骨架](archive/implementation/t7-b-creation-task-verification.md)、[C 范围绑定](archive/implementation/t7-c-binding-verification.md)、[E 关系事件](archive/reviews/2026-11-17-t7-e-relation-guards-decision.md)、[G 扩展](archive/implementation/t7-g-extension-verification.md) |
| T6 Merge/身份 | 直属父子、瞬时 compare、整页 Merge LRO、回并；`requestId` 由 `MergeRepository.claim` 原子认领            | [B 并发恢复](archive/implementation/t6-b-merge-recovery-verification.md)、[C 身份](archive/implementation/t6-c-identity-verification.md)、[D 回并](archive/implementation/t6-d-merge-back-verification.md)、[E 轮次](archive/implementation/t6-e-merge-round-verification.md)、[F 谱系](archive/reviews/2026-11-16-t6-f-lineage-constraints.md) |
| 公共查询视图  | SK 只写 LightTrackerItem 索引；业务 BC 消费 `TrackerItemsIndexedEvent` 投影公共视图，当前 ALM 为空监听器 | [T7 §4.6](05-contracts/t7-creation-lro.md)                                                                                                                  |
| 运行时        | 历史执行时点，不证明当前工作区                                                                           | [T5 运行时验证](archive/implementation/t5-runtime-verification.md)                                                                                          |
| 规模/保留     | 阈值待采样                                                                                               | [规模与保留](08-plan.md#2-规模与保留)                                                                                                                       |

**历史验收**（已被后续契约和 ADR 替代，保留证据）：[T5 S0 设计](archive/implementation/t5-s0-minimal-slice-design.md)、[场景映射](archive/implementation/t5-s0-scenario-test-mapping.md)、[S0 评审](archive/reviews/2026-11-11-t5-s0-design-review.md)、[S1](archive/implementation/t5-s1-verification-result.md) · [S2](archive/implementation/t5-s2-verification-result.md) · [S3](archive/implementation/t5-s3-verification-result.md) · [S4](archive/implementation/t5-s4-verification-result.md) · [S5](archive/implementation/t5-s5-verification-result.md) · [S6](archive/implementation/t5-s6-verification-result.md) · [S7](archive/implementation/t5-s7-verification-result.md)、[D2 家族作用域](archive/implementation/t5-d2-family-scope-verification.md)、[D4 SK 边界](archive/implementation/t5-d4-sk-boundary-verification.md)、[T7-D 批量索引旧验收](archive/implementation/t7-d-index-verification.md)。

## 8. 未验证与阻塞

- 操作者 ID 统一从 `AuthorizationScope.currentUserId()` 获取。Working Set 和 ProductSpace 范围仍由显式输入、绑定或对象归属确定。
- T7-F 规模、失败明细保留和关系消费仍待专题验证，见 [后续计划](08-plan.md)。

## 9. 追溯

- [TC-002 反模式审查](archive/reviews/2026-11-19-ddd-anti-pattern-scan.md)
- [跨 BC 公共契约](05-contracts/t4-cross-bc.md)
- [领域模型与边界](04-model.md)
- [后续计划与开放问题](08-plan.md)
