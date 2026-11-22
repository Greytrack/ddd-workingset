# 实现现状与验收证据

**状态：Reviewed（2026-11-22 静态核对；实现证据，不是新增基线裁定）**

本页是「代码现在怎样」的唯一权威：模块与上下文边界、跨 BC 读取边界、事件协作、与 TC-002 的剩余差距、查询侧实现记录与验收证据索引。业务语义仍以
[BASELINE](01-baseline.md)、[ADR](06-decisions/README.md) 与 [T1～T7 契约](05-contracts/)为准；本页不把当前代码反推为新的业务规则。

依据：用户 2026-11-19 移除单方法 Port 和文档对齐的要求；当前实现改为只读使用共享聚合，该读取边界在本页登记为
Reviewed，不扩大既有契约接受范围。核对范围为 2026-11-19 之后的实现提交与当前工作区。

## 1. 模块与上下文边界

当前 `shared-kernel` 模块已从单一 `sk.workingset` 包（迁移前的旧命名）扩展为以下包。它们**不是**四个企业业务 BC 之外的四个新
BC；是否拆为独立限界上下文仍需按语言、所有权和运行时证据裁定。

| 包                                              | 当前拥有的职责                                                                                  | 依赖/边界                                                                                                                                 |
|-------------------------------------------------|-------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `com.airedgesoft.sharekernel.businessframework` | 通用业务对象、属性、版次、排序和轻量只读投影能力；`BusinessObject`/`Revisionable` 等共享契约    | 属于 Shared Kernel 的通用能力，不是 Working Set 业务模型；不得反向依赖各 BC。`validation` 子包存在迁移期旧栈依赖例外，见其包说明。        |
| `com.airedgesoft.sharekernel.tracker`           | Tracker 聚合根、创建/改名命令、Tracker 只读 Provider、创建后绑定事件                            | Tracker 是独立聚合。当前仍直接使用 `workingset` 包的 `WorkingSet`、`Classification` 类型；这是同进程实现依赖，不等于已冻结的跨 BC 合同。 |
| `com.airedgesoft.sharekernel.productspace`      | ProductSpace 聚合根、创建/删除命令、ProductSpace 仓储                                            | ProductSpace 是独立聚合；创建后通过事件推进默认 Tracker 与绑定，剩余差距见 §4。                                                          |
| `com.airedgesoft.sharekernel.workingset`        | WorkingSet/绑定、LightTrackerItem、COW、WorkingSet 创建 LRO、Compare/Merge、关系和查询 Provider | Working Set 核心能力；命令侧聚合仓储与查询侧 Provider 分离。                                                                              |
| `com.airedgesoft.alm.requirement`               | Requirement 参考实现、Merge 执行适配、物化请求消费、继承编辑查询回退                            | 业务 BC 参考实现；只读使用 SK 共享聚合/仓储，写入通过事件协作。                                                                           |

`com.airedgesoft.sharekernel.businessframework.domain` 中的 `BusinessObject` 是**通用业务对象能力接口**，与
`com.airedgesoft.sharekernel.workingset.domain.TrackerItem`
的公共工作集契约不是同一概念；不得因为命名相近而把二者互相替代。

## 2. 跨 BC 读取边界（2026-11-19 调整）

- LightTrackerItem 的当前索引和版次成员分别写入两张旧索引表，由同一次聚合保存提交；已落地动作与未决撤销规则见
  [双表索引与版次成员](05-contracts/t3-lti-materialization.md#31-双表索引与版次成员)。
- ALM 等业务 BC 可以**只读**使用 SK 的共享聚合和聚合仓储（如 `LightTrackerItemRepository.findById`、
  `WorkingSetRepository.findById`、`TrackerRepository` 读取），用于完成自身用例所需的身份和来源校验。LightTrackerItem
  的身份和来源解析供系统内部使用，业务内容由相应 BC 的 API 提供。
- 不再为单个查询方法新增专用同步 Port；`TrackerItemLifecyclePort` 及其 `findInheritedTarget` 已移除。集合读取和读模型查询使用
  `application/query/provider` 下的 Provider。
- 共享 Tracker 的 Item 列表按 Tracker 读取：共享 Tracker 通过 `TrackerBinding` 对其绑定工作集可见；非共享 Tracker 仅在所属工作集可见（`RequirementQueryService.findByTracker`）。不能再用 `requirements.working_set_id` 过滤共享 Tracker 的 Item。
- LightTrackerItem 自带 `classificationId` / `numericProductSpaceId`，分别写入索引的 `sub_class_id` / `work_space_id`；LTI 仓储不再 join `air_inst_common`，也不再从公共摘要推断子类。
- 多业务对象类型通过 `BusinessObjectTypeRegistry` / `BusinessObjectTypeAdapter` 按 Tracker 分类路由；一个 Tracker 只承载一种对象类型。当前只注册 Requirement 类型，新增 BC 只加 Adapter。
- 直接读取不等于获得写入权：业务 BC **不得**调用 SK 聚合仓储的 `save`、`delete`、`remove` 或修改 SK
  聚合状态；跨聚合状态变化仍必须通过可靠事件和各自独立事务完成（TC-002）。
- 直接读取的外部状态不能满足跨聚合原子校验；需要“先校验后写入”的规则仍由目标 BC 或事件消费方在本地事务中重新校验。

## 3. 当前事件协作

| 事实                              | 发布方                                                                     | 消费者/后续动作                                                                                                                                                                |
|-----------------------------------|----------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Requirement 创建后登记 LTI        | ALM `TrackerItemCreatedEvent`                                              | SK 幂等登记 Local-backed LTI；业务已删除时跳过                                                                                                                                 |
| Requirement 普通修改/继承编辑保存 | ALM `BusinessObjectUpdateEvent`                                            | SK COW 请求；`TrackerItemLocalizedListener` 对首次继承编辑在独立事务切换 LTI                                                                                                   |
| COW 业务保存                      | SK `TrackerItemMaterializationRequestedEvent`                              | ALM 保存 Requirement 并发布 `TrackerItemMaterializationCompletedEvent`                                                                                                         |
| COW 完成                          | ALM `TrackerItemMaterializationCompletedEvent`                             | SK 在独立事务切换 Local-backed / 记录失败终态                                                                                                                                  |
| Merge 条目批准                    | SK `MergeEntryApprovedEvent` 携带目标 Tracker 分类、冻结应用值及目标期望锁 | ALM 只处理 Requirement 分类，在自己的事务中校验并保存目标，发布 `MergeEntryResolvedEvent`。证据校验留在轮次登记阶段。[ADR-0009](06-decisions/adr-0009-merge-dispatch-frozen-evidence.md) |
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
| ProductSpace 删除 | [T2 §4.3](05-contracts/t2-binding-deletion.md#43-聚合删除)；存在 WorkingSet 绑定时拒绝，Modulith 逐事件删除空 Tracker 和空间                | 每步复核绑定及业务引用。受理后出现新引用时中止，保留未完成事件和已提交的空容器清理结果。删除期间没有公开任务状态。           |
| WorkingSet 删除   | [T2 §4.3](05-contracts/t2-binding-deletion.md#43-聚合删除)；拒绝仍有子工作集、绑定或归属 Tracker 的工作集                                   | 成功路径只写 WorkingSet 聚合。                                                                                               |
| T7 创建/扩展      | 阶段按项执行，但工作集创建、Tracker 派生、范围绑定及 LTI 索引仍与各自的任务进度同事务提交                                                  | 按[反模式审查](archive/reviews/2026-11-19-ddd-anti-pattern-scan.md)将聚合副作用与任务进度拆成独立提交，用可靠结果事件推进任务。 |
| Tracker 创建      | TrackerCommandService 仅持久化 Tracker，绑定由 `TrackerCreatedEvent` 的监听器在 Working Set 事务中完成                                     | 主路径已事件化；仍需验证监听失败、重复投递、绑定拒绝和运维可观察性。ProductSpace 初始化不在该路径内。                        |
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
  `TrackerQueryProvider`、`LightTrackerItemQueryProvider`、`ClassificationCatalog`、`CowFailureLogQueryProvider`、
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
      `workingSetId` 用于可见性校验，不再作为 `requirements.working_set_id` 过滤条件。
- Controller 暂未拆分，`WorkingSetController` 与 `RequirementController` 同时注入 command/query 服务。
- 跨聚合事务的实现差距见 §4。

### 6.1 聚合写入约定

- `WorkingSetRepository.save` 保存新建根及其成员，或保存持锁重建聚合的成员差量；现有根字段没有修改命令。绑定的增删及
  `shown` 修改均经聚合；空间绑定仍由 Folder Tracker 关系表示，未变更关系不重写。
- Tracker 与创建任务也统一用 `save`，保存分支由实现处理。产品空间当前无更新命令；`save` 只写新对象，重复身份不覆盖。跨工作集的
  Tracker 删除清理仍由删除用例协调。
- MergeRound 仓储已拆分 `claim`（按 requestId 原子认领新轮次）与 `save`（更新已有轮次状态）；MergeCheckpointQuery 直接从旧表只读查询跨轮次最新基准。LTI 原子家族占位不以普通保存替代。
- 产品空间、Tracker 与 Working Set 各有聚合仓储；入口及初始化的事务差距见 §4。

### 6.2 查询侧后续

- LTI 横向扫描已由 `LightTrackerItemQueryProvider` 承接；按 ID 读取身份聚合使用 `LightTrackerItemRepository`
  ，不再在查询服务暴露仅供测试的 `resolve` 入口。Tracker 集合读取由 `TrackerQueryProvider` 承接，Tracker 创建/改名命令使用该只读
  Provider 判定可见性。
- Creation/Merge 查询迁移到查询端口。
- Controller 按 command/query 拆成独立入口。
- 关系事件 outbox/消费方仍按 T7-E 开放项处理。

## 7. 验收证据索引

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

- `RequestThreadHolder` 已在写侧仓储中移除：审计事实由 WorkingSet/`LightTrackerItem`/Requirement/Tracker
  聚合携带，应用层在构造或修改时注入；`com.airedgesoft.sharekernel.businessframework` 部分文件仍未完成全量验收，本页不将其字段、方法或依赖视为已交付事实。
- 2026-11-22 全量测试：shared-kernel 33、alm 16、ArchitectureTest 14 通过；deploys 154 项中 21 failures + 10 errors，集中在
  Merge、Binding、Tracker、Requirement 与 Snowflake 的既有语义/夹具问题。时间类型统一为 epoch 毫秒、仓储审计解耦相关的测试均通过。
- 2026-11-22 针对性回归：共享 Tracker 列表可见/非共享隔离、Requirement 读侧、Merge `claim`/`save` 约定的编译与局部测试通过；不能替代全量集成回归。
- 数据库/Redis 集成回归需在专用空库执行；当前工作区未执行全量。
- T7-F 规模、失败明细保留和关系消费仍待专题验证，见 [后续计划](08-plan.md)。
- 2026-11-19 的完整测试记录为 **160 项通过**；ArchUnit 规则覆盖 query→command、command→query、application→MyBatis 和
  domain→持久化框架边界。2026-11-20 后的改动尚未完成全量回归。

## 9. 追溯

- [TC-002 反模式审查](archive/reviews/2026-11-19-ddd-anti-pattern-scan.md)
- [跨 BC 公共契约](05-contracts/t4-cross-bc.md)
- [领域模型与边界](04-model.md)
- [后续计划与开放问题](08-plan.md)
