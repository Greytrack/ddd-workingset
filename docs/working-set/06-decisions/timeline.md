# 裁定时间线

**状态：Draft（索引页；只记录裁定顺序与权威入口，不重复规则全文）**

本页把 Working Set 的裁决链路按时间串起来：每次裁定的领域编号（`D`）、契约冻结（`T`）、开放问题关闭点（`QUESTION`）与
ADR 的接受／替代关系。规则全文见[事实、政策与不变量](../03-rules.md)，决策依据见各 ADR，历史论证见[归档评审](../archive/reviews/)。

**读法**：需要「现在有效的是什么」看 [BASELINE](../01-baseline.md) 与 [03-rules](../03-rules.md)；需要「为什么这样定」沿本页链接进入
ADR 与归档评审。本页不作为任何规则的接受依据。

## 阶段 2：领域发现（2026-11-09）

| 日期       | 事件               | 结论                                                                     | 权威入口                                                                                    |
|------------|--------------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| 2026-11-09 | 候选来源清单       | 28 个 Markdown 来源建立定位；V4 overview／domain／consistency 纳入来源   | [领域发现评审](../archive/reviews/2026-11-09-stage-2-domain-discovery-review.md)            |
| 2026-11-09 | Shared Kernel 定位 | `AC-001` 企业架构外框确认：Working Set 属企业 Shared Kernel 的公共能力   | [边界评审](../archive/reviews/2026-11-09-stage-3-0-shared-kernel-boundary-review.md)、[04-model §5](../04-model.md#5-shared-kernel-边界) |

## 阶段 3：架构与规则收敛（2026-11-09～11-10）

| 日期       | 事件                          | 结论                                                                       | 权威入口                                                                                     |
|------------|-------------------------------|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| 2026-11-10 | `D-001` Tracker 独立聚合      | Tracker 拥有独立业务身份、状态与生命周期；排除独立 Branch 根               | [ADR-0001](adr-0001-tracker-independent-aggregate.md)                                        |
| 2026-11-10 | `D-002`／`D-003` Tracker 双维 | `parentId` 表达同工作集结构父；`sourceId` 表达直接父级工作集来源；非共享派生产生独立身份 | [ADR-0001](adr-0001-tracker-independent-aggregate.md)、[`SCN-013`／`SCN-014`](../09-scenarios.md) |
| 2026-11-10 | `D-004` 派生组织裁剪          | 未选原结构父时允许 `parentId=null` 成为目标根                              | [`POL-009`](../03-rules.md)                                                                  |
| 2026-11-10 | `D-005`／`D-006` 组织位置     | 父也选中时默认继承、创建前可显式重组；创建后 `parentId` 不可变             | [`POL-010`／`POL-011`](../03-rules.md)                                                       |
| 2026-11-10 | `D-007` TrackerItem 公共契约  | `TrackerItem` 是公共契约；业务对象是各 BC 的完整独立聚合                   | [`FACT-012`](../03-rules.md)                                                                 |
| 2026-11-10 | `D-008` LightTrackerItem 权威 | LightTrackerItem 是 SK 独立聚合，负责身份／来源／有效变体定位              | [ADR-0002](adr-0002-lighttrackeritem-authoritative-aggregate.md)                             |
| 2026-11-10 | `D-009` 来源变更触发 COW      | COW 采用事件驱动最终一致；普通修改不升级 Revision                          | [ADR-0003](adr-0003-cow-event-driven-final-consistency.md)                                   |
| 2026-11-10 | `D-010` 最小聚合模型收敛      | 排除 Manifest／ItemVariant；最小模型为 WorkingSet／Tracker／LTI            | [`FACT-015`](../03-rules.md)                                                                 |
| 2026-11-10 | `D-011` 首次快照优先          | 来源首次相关修改的修改前事实决定最终物化内容                               | [`FACT-016`](../03-rules.md)、[`SCN-030`](../09-scenarios.md)                                |
| 2026-11-10 | `D-012` 来源字段语义          | `SourceLocalId`／`SourceRevision` 表示当前有效内容来源，物化后指向自身      | [`FACT-017`](../03-rules.md)、[`SCN-035`](../09-scenarios.md)                                |
| 2026-11-10 | `D-013` 直接有效来源定位      | 不逐级递归未物化中间变体；同一来源修改扇出所有依赖者                       | [`FACT-018`](../03-rules.md)、[`SCN-037`](../09-scenarios.md)                                |
| 2026-11-10 | `D-014` 下游不自动重定向      | 中间上游物化不改变已有下游的 `SourceLocalId`                               | [`FACT-019`](../03-rules.md)、[`SCN-039`](../09-scenarios.md)                                |
| 2026-11-10 | `D-015` 时间基准选 Revision   | 派生不复制完整业务数据；时间基准只用于选 Revision                          | [`FACT-020`](../03-rules.md)、[`SCN-043`](../09-scenarios.md)                                |
| 2026-11-10 | `D-016` 只固定版次            | 派生只固定业务版次，不冻结普通内容                                        | [`FACT-021`](../03-rules.md)（Superseded 部分见 `D-017`）                                    |
| 2026-11-10 | `D-017` 消费时确定目标集合    | COW 目标集合在 Handler 消费时确定，不比较修改开始与派生完成时间            | [`FACT-022`](../03-rules.md)、[`SCN-048`](../09-scenarios.md)                                |
| 2026-11-10 | `D-018` 事件顺序与 Revision   | 事件顺序由框架记录；LTI 持有自身 Revision，与 `SourceRevision` 分离        | [`FACT-023`](../03-rules.md)、[`SCN-049`](../09-scenarios.md)                                |
| 2026-11-10 | `D-019` 未物化参与 Diff/Merge | 未物化变体可参与 Diff；决策归 MergeRound、确认归 MergeEntry                | [`FACT-027`](../03-rules.md)                                                                 |
| 2026-11-10 | ProductSpace 独立聚合         | ProductSpace 在默认 WorkingSet 维护生命周期，不随派生复制                  | [ADR-0004](adr-0004-productspace-independent-aggregate.md)                                   |
| 2026-11-10 | 规则收敛 `R01—R19`            | 用户批准 R01—R17 及限定术语，确认 R18／R19                                 | [03-rules §7](../03-rules.md#7-已接受的限定陈述r01r19)、[规则收敛评审](../archive/reviews/2026-11-09-stage-3-1-rule-convergence.md) |
| 2026-11-10 | 阶段 4 分类与产品空间         | 分类元模型三层、Tracker 是 Classification 投影；默认 WS 固定 Id=1          | [`FACT-024`～`FACT-026`](../03-rules.md)、[阶段 4 评审](../archive/reviews/2026-11-10-stage-4-productspace-classification-review.md) |
| 2026-11-10 | 首版基线审核                  | 通过（附观察项）；不构成发布、Commit 或 Tag 授权                           | [基线审核](../archive/reviews/2026-11-10-baseline-v1-audit.md)                               |

## 阶段 5：契约冻结（2026-11-11）

| 日期       | 事件                    | 结论                                                                                   | 权威入口                                                                     |
|------------|-------------------------|----------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| 2026-11-11 | `T1` Merge 执行冻结     | 有向差异、决定、Apply Result 与 Checkpoint 分离；一页 1～50 条决策                     | [T1 契约](../05-contracts/t1-merge-execution.md)、[T1 评审](../archive/reviews/2026-11-11-t1-contract-review.md) |
| 2026-11-11 | `T2` 绑定与删除冻结     | 绑定移除、聚合删除、业务 Item 删除三者区分（`K1`～`K3`）                               | [T2 契约](../05-contracts/t2-binding-deletion.md)、[T2 评审](../archive/reviews/2026-11-11-t2-binding-deletion-review.md) |
| 2026-11-11 | `T3` 物化协议冻结       | 独立 `materialized` 字段；业务保存与 LTI 切换双事实（`K1′`～`K3′`）                    | [T3 契约](../05-contracts/t3-lti-materialization.md)、[T3 评审](../archive/reviews/2026-11-11-t3-materialization-review.md) |
| 2026-11-11 | `T4` 跨 BC 契约冻结     | 只读 Provider + 写入事件协作；九分类公共失败语义（`K1″`／`K2″`，`K3″` 开放）           | [T4 契约](../05-contracts/t4-cross-bc.md)、[T4 评审](../archive/reviews/2026-11-11-t4-cross-bc-review.md) |
| 2026-11-11 | `QUESTION-004` 关闭     | 单工作集同族有效变体唯一（`POL-005`）                                                  | [`SCN-011`](../09-scenarios.md)                                              |
| 2026-11-11 | `QUESTION-006` 关闭     | 软删保留首次物化所需快照；永久删除须先软删并等待切换                                   | [T3 §删除与来源保留](../05-contracts/t3-lti-materialization.md)              |
| 2026-11-11 | 一致性修正              | 两项裁定同步文档：`D-003` 双维关系、`D-007` 职责划分                                   | [一致性修正](../archive/reviews/2026-11-11-consistency-reconciliation.md)    |

## 实施切片（2026-11-12～11-18）

| 日期       | 事件                        | 结论                                                                              | 权威入口                                                                              |
|------------|-----------------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| 2026-11-12 | `QUESTION-001` 关闭         | Working Set 是工作视图／范围清单，不是分支本体                                    | [`FACT-001`](../03-rules.md)、[S1～S7 验收](../archive/implementation/)               |
| 2026-11-12 | `QUESTION-003`／`D11` 权限  | 现阶段不接入权限；`Caller`／`authorize` 占位后续移除                               | [`QUESTION-003`](../08-plan.md#4-已关闭问题索引)                                      |
| 2026-11-12 | `D14` 生命周期能力          | `BusinessObject` 声明 `delete`／`undelete`／`delete2` 能力，规则由各 BC 实现       | [`QUESTION-016`](../08-plan.md#4-已关闭问题索引)                                      |
| 2026-11-12 | 多级谱系 `FACT-028`         | 单父、`parentWorkingSetId`、Merge 只允许直属父子双向                              | [谱系设计](../05-contracts/working-set-lineage.md)、[谱系评审](../archive/reviews/2026-11-12-working-set-lineage-review.md) |
| 2026-11-12 | `T5` 首切片验收             | S1～S7 纵向切片完成（历史证据，不再作为当前设计入口）                             | [T5 历史验收](../archive/implementation/)                                            |
| 2026-11-13 | `T7-A` 创建 LRO             | 创建工作集为异步长流程；范围、进度与部分成功明细                                   | [T7 契约](../05-contracts/t7-creation-lro.md)、[T7-A 评审](../archive/reviews/2026-11-13-t7-a-creation-lro-review.md) |
| 2026-11-15 | Compare 不持久化            | compare 只读瞬时；MergeRound 保存决策与所选证据，不返回可回读 token                | [2026-11-15 裁定](../archive/reviews/2026-11-15-compare-not-persisted-decision.md)     |
| 2026-11-15 | `QUESTION-010` 身份裁定 C   | Reference 用逻辑 Family，Association 用具体 Variant                               | [关系身份裁定](../archive/reviews/2026-11-15-question-010-relation-identity-decision.md) |
| 2026-11-16 | `T6-E` Merge 轮次收敛       | 一页 1～50 条决策；`requestId` 幂等；异步 LRO 与终态不可重开                      | [T6-E 验收](../archive/implementation/t6-e-merge-round-verification.md)               |
| 2026-11-16 | `T6-F` 谱系约束             | Shared 允许悬挂；同槽多 Tracker + `is_default`；`sourceId` 不可变；派生链 1:1     | [T6-F 裁定](../archive/reviews/2026-11-16-t6-f-lineage-constraints.md)                |
| 2026-11-16 | Checkpoint 旧表对齐         | 物理承载表为 V3 `air_ws_items_merge_index`；`MergeCheckpoint` 只是 V4 领域模型    | [对齐裁定](../archive/reviews/2026-11-16-merge-checkpoint-legacy-alignment.md)、[重构方案](../archive/implementation/merge-checkpoint-rework-plan.md) |
| 2026-11-17 | `T7-G` 扩展改为部分成功     | 扩展与创建均允许部分成功；独立批量派生入口另行定义                                | [`QUESTION-002.l`](../08-plan.md#4-已关闭问题索引)、[T7-G 验收](../archive/implementation/t7-g-extension-verification.md) |
| 2026-11-18 | 移除 COW Recovery           | 物化失败即终态，不重投、不补切换                                                  | [COW 恢复裁定](../archive/reviews/2026-11-18-remove-cow-recovery.md)                 |
| 2026-11-18 | `T7-E` 关系事件             | SK 只发布关系请求，不操作关系聚合；消费守卫矩阵待接入                              | [T7-E 裁定](../archive/reviews/2026-11-17-t7-e-relation-guards-decision.md)           |

## 架构收敛（2026-11-19～11-22）

| 日期       | 事件                          | 结论                                                                                         | 权威入口                                                                                  |
|------------|-------------------------------|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| 2026-11-19 | `TC-002` 单聚合写事务         | 一个写事务最多修改一个聚合；跨聚合变更通过可靠事件与独立事务最终收敛                           | [反模式审查](../archive/reviews/2026-11-19-ddd-anti-pattern-scan.md)、[03-rules `TC-002`](../03-rules.md) |
| 2026-11-19 | 跨 BC 读取边界                | 业务 BC 可只读使用 SK 共享聚合／仓储；禁止直接写入；移除单方法 Port                            | [实现现状 §2](../07-implementation.md)                                                    |
| 2026-11-19 | 文档对齐                      | 模块边界、直接只读、事件写入与代码现状同步                                                     | [文档对齐](../archive/reviews/2026-11-19-doc-alignment-after-code-evolution.md)           |
| 2026-11-20 | 产品空间删除                  | 仅在默认工作集删除空产品空间；存在绑定时拒绝删除                                               | [产品空间删除裁定](../archive/reviews/2026-11-20-product-space-deletion.md)               |
| 2026-11-20 | `ADR-0006` 合并确认归属       | 确认归 MergeEntry；跨轮次当前基准使用只读查询，替代独立 Checkpoint 聚合                       | [ADR-0006](adr-0006-merge-confirmation-ownership.md)                                      |
| 2026-11-20 | `ADR-0008` Tracker 第三级容器 | Tracker 是产品空间下的第三级容器，Folder 是业务对象；`ADR-0007` 被替代                        | [ADR-0008](adr-0008-tracker-third-level-container.md)、[ADR-0007](adr-0007-root-folder-tracker-per-working-set-space.md)（Superseded） |
| 2026-11-21 | `ADR-0009` 冻结值分发         | Merge 按批准时冻结的值分发，不取源最新值                                                      | [ADR-0009](adr-0009-merge-dispatch-frozen-evidence.md)、[Merge 分发 BC 边界](../archive/reviews/2026-11-21-merge-dispatch-bc-boundary.md) |
| 2026-11-21 | `ADR-0010` 基线时间派生       | 工作集创建／扩展按 Tracker 基线时间尽力派生；替代 T7-D 受理冻结与快照存储                     | [ADR-0010](adr-0010-working-set-creation-tracker-baseline-time.md)                        |
| 2026-11-21 | V3 审计列对齐                 | `created_by`／`created_time` 取代 `created_at`；`light_tracker_items` 退出主路径；Checkpoint 承载表统一 | [`TC-006`～`TC-008`](../03-rules.md)                                                      |
| 2026-11-22 | 时间类型统一                  | 领域、事件、REST 与持久化统一为 epoch 毫秒；写侧仓储不读 `RequestThreadHolder`                | [`TC-009`／`TC-010`](../03-rules.md)                                                      |
| 2026-11-22 | 创建／扩展快照表移除          | `working_set_creation_snapshots`／`working_set_creation_items` 从代码与 schema 移除           | [`TC-011`](../03-rules.md)                                                                |
| 2026-11-22 | 文档治理                      | 单一权威导航、断链与锚点修复、历史／现行分离、跨文件去重、图表与术语统一                       | [BASELINE §8](../01-baseline.md)、[文档收敛](../archive/reviews/2026-11-16-doc-consolidation.md)、[仓库降噪](../archive/reviews/2026-11-18-cleanup-and-alignment.md) |

## ADR 接受与替代关系

| ADR      | 状态       | 关键结论                                              | 替代关系                        |
|----------|------------|-------------------------------------------------------|---------------------------------|
| ADR-0001 | Accepted   | Tracker 独立聚合                                      | —                               |
| ADR-0002 | Accepted   | LightTrackerItem 权威聚合                             | —                               |
| ADR-0003 | Accepted   | COW 事件驱动最终一致                                  | —                               |
| ADR-0004 | Accepted   | ProductSpace 独立聚合与绑定                           | —                               |
| ADR-0005 | Accepted   | Merge 执行模型与 MergeEntry 行为契约                  | Checkpoint 确认归属由 ADR-0006 替代 |
| ADR-0006 | Accepted   | 合并确认归 MergeEntry；跨轮次只读查询                 | 替代 ADR-0005 的 Checkpoint 部分 |
| ADR-0007 | Superseded | 每个 Working Set 的产品空间拥有独立根文件夹 Tracker    | 由 ADR-0008 替代                |
| ADR-0008 | Accepted   | Tracker 是第三级容器，Folder 是业务对象               | 替代 ADR-0007                   |
| ADR-0009 | Accepted   | Merge 按批准时冻结值分发                              | —                               |
| ADR-0010 | Accepted   | 创建／扩展按 Tracker 基线时间尽力派生                 | 替代 T7-D 受理冻结与快照存储     |

## 仍未关闭的问题

| 编号           | 问题                                         | 状态     | 入口                                       |
|----------------|----------------------------------------------|----------|--------------------------------------------|
| `QUESTION-010` | 关系服务的可见性、删除守卫与接入             | P0 开放  | [08-plan §1](../08-plan.md#1-当前开放问题) |
| `QUESTION-004` | CommonId 跨实例并发与去重协议（剩余部分）    | P1 开放  | [08-plan §1](../08-plan.md#1-当前开放问题) |
| `QUESTION-011` | 业务 Baseline 的含义                         | P1 开放  | [08-plan §1](../08-plan.md#1-当前开放问题) |
| `QUESTION-012` | Working Set 与 Binding 的规模、并发上限      | P2 开放  | [08-plan §2](../08-plan.md#2-规模与保留)   |
| `QUESTION-014` | Shared Kernel 版本与兼容性治理               | P2 开放  | [08-plan §1](../08-plan.md#1-当前开放问题) |
| `QUESTION-017` | T7 创建任务的取消、补齐与结果保留            | P2 开放  | [08-plan §1](../08-plan.md#1-当前开放问题) |
| `QUESTION-018` | T7-E 关系构建失败后的重试与修复边界         | P2 开放  | [08-plan §1](../08-plan.md#1-当前开放问题) |

已关闭问题的完整索引见[后续计划与开放问题 §4](../08-plan.md#4-已关闭问题索引)。
