# 事实、政策与不变量

**状态：Draft（混合登记；已接受子集以 [BASELINE](01-baseline.md) 为准）**

本页是 Working Set 的规则登记：组织约束（`AC`）、业务事实（`FACT`）、业务政策（`POL`）、假设（`HYP`）、技术约束（`
TC`）与业务不变量（`INV`）。已接受的限定陈述见 §7；接受依据与场景定位保留在
[阶段 3.1 评审](archive/reviews/2026-11-09-stage-3-1-rule-convergence.md#用户批准记录与生效范围)。编号保持稳定，不因本页重排而改变。

## 1. 组织与架构约束（AC）

| 编号     | 约束                                                                                                                                                                  | 状态      | 依据                                                                      |
|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|---------------------------------------------------------------------------|
| `AC-001` | Working Set 是企业 Shared Kernel 的组成能力，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 同时包含 Privilege、Workflow、Class Hierarchy。 | Confirmed | 用户 2026-11-09 明确确认；[Shared Kernel 边界](04-model.md#5-shared-kernel-边界) |

`AC-001` 固定企业架构归属和依赖方向，不证明 Working Set 是单一聚合、单一 BC 或微服务。

## 2. 业务事实（FACT）

| 编号       | 陈述                                                                                                                                                                                                                     | 状态                                                                              | 来源/说明                                                  |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------|
| `FACT-001` | Working Set 是选择 Tracker 并形成有效工作范围的工作视图/范围清单，不是分支本体。                                                                                                                                         | 用户确认（2026-11-12 关闭 `QUESTION-001`）                                        | [S1～S7 验收](07-implementation.md)            |
| `FACT-002` | Shared Tracker 及其 Item 的新增、修改实际归属默认工作集，所有已绑定工作集同时可见，不产生子工作集独有副本。                                                                                                              | 用户已确认                                                                        | `POL-008`；权限与读取面指标待设计                          |
| `FACT-003` | 非共享 Tracker 派生产生新的独立业务身份；`sourceId` 指向直接父级 Working Set 下的来源 Tracker，逐级追溯；`parentId` 另表达同工作集直属结构父。                                                                           | 用户确认 D-002/D-003                                                              | `INV-014`～`INV-018`                                       |
| `FACT-004` | Source-backed 变体没有本地业务内容行，仍有稳定的逻辑身份。                                                                                                                                                               | 现行领域语义                                                                      | D-008                                                      |
| `FACT-005` | `CommonId` 关联同族变体，`LocalId` 标识具体变体；实例化不更换 `LocalId`。                                                                                                                                                | Accepted 子集：R05 身份关系；实例化稳定性已由 T3 兑现                             | [身份规则](02-language.md#item-身份规则已确认)             |
| `FACT-006` | 普通字段按来源 Revision 读取并在首次 COW 固定；附件与关系固定为派生时状态；来源删除不得破坏首次编辑所需事实的可读性。                                                                                                    | Accepted 限定范围 R07/R19；软删触发物化与删除守卫见 T3 的 QUESTION-006 裁定       | [T3 §2.1](05-contracts/t3-lti-materialization.md)          |
| `FACT-007` | 直接父子工作集支持双向 Compare/Merge；任一方向的新增、删除、修改均可经用户选择应用到接收方；`source/target` 是本轮角色。                                                                                                 | 用户已确认                                                                        | `POL-004`；目标 BC 校验提交                                |
| `FACT-008` | Merge 数据选择、实际 Apply Result 与处理确认是不同事实。                                                                                                                                                                 | Accepted 子集 R08～R11；新版二元决策及确认规则见 T1 §7（`QUESTION-008.b` 已关闭） | [T1](05-contracts/t1-merge-execution.md)                   |
| `FACT-009` | Reference 可在逻辑目标有效但物理端点暂不可解析时保持有效悬空状态。                                                                                                                                                       | Reported / Pending                                                                | `QUESTION-010` 剩余可见性/守卫                             |
| `FACT-010` | 创建、扩展、替换和合并被设计为可恢复长流程；受理成功不等于业务结果已可用。                                                                                                                                               | 部分 Accepted：GateFlow 替换 R16/R17；T7 创建 LRO 已落地                          | [T7](05-contracts/t7-creation-lro.md)                      |
| `FACT-011` | 架构示意把 `create / extend / resolve / diff / merge` 画作业务 BC 调用 Working Set 的统一 API 面。                                                                                                                       | 已由 T4 收敛为六个用例的契约边界                                                  | [T4](05-contracts/t4-cross-bc.md)                          |
| `FACT-012` | 工作集领域统一使用 `TrackerItem` 作为公共 Item 契约；业务对象是各 BC 的完整独立聚合，实现该接口获得公共能力。                                                                                                            | 用户确认 D-007                                                                    | `TrackerItem` 继承 `BusinessObject`（D14）                 |
| `FACT-013` | LightTrackerItem 是 Working Set 专属独立聚合，非查询投影；派生采用 COW，不立即物化完整业务对象。                                                                                                                         | 用户确认 D-008                                                                    | 属性与物化协议见 T3                                        |
| `FACT-014` | 来源变更触发 COW 采用事件驱动；普通修改不升级 Revision；物化成功后 LTI 切换到自身有效来源。                                                                                                                              | 用户确认 D-009                                                                    | 事件与事务边界见 T3/T4                                     |
| `FACT-015` | COW 采用异步事件驱动、最终一致；修改前事实作为事件关联的不可变内容快照；不要求跨 BC 事务；最小模型为 Working Set/Tracker/LightTrackerItem。                                                                              | 用户确认 D-010                                                                    | 不引入 Manifest/ItemVariant                                |
| `FACT-016` | 首次快照优先：来源首次相关修改的修改前事实决定最终物化内容；后续修改不覆盖；按实际修改顺序而非事件到达顺序。                                                                                                             | 用户确认 D-011                                                                    | 事件顺序由框架兜底（D-018）                                |
| `FACT-017` | `SourceLocalId`/`SourceRevision` 表示当前有效内容来源；物化后均指向自身；COW 不改变 `LocalId`/`CommonId`。                                                                                                               | 用户确认 D-012                                                                    | 不保存历史派生链                                           |
| `FACT-018` | 多级派生采用直接有效来源定位，不逐级递归未物化中间变体；同一实际来源修改时扇出物化所有依赖者。                                                                                                                           | 用户确认 D-013                                                                    | 历史派生关系不要求保存                                     |
| `FACT-019` | 派生完成后，已有下游不因中间上游物化而自动重定向 `SourceLocalId`；只有自身完成 COW 才切到自身。                                                                                                                          | 用户确认 D-014                                                                    | 来源依赖与派生层级分开                                     |
| `FACT-020` | 派生不复制完整业务数据；时间基准（Snapshot/Head）只用于选 Revision，不等待来源停止变化。                                                                                                                                 | 用户确认 D-015                                                                    | `SourceRevision` 不等于内容快照                            |
| `FACT-021` | 派生只固定业务版次，不冻结普通内容；完整派生完成边界的历史解释由 D-017 修订。                                                                                                                                            | **Superseded**：时间边界部分由 D-017/`FACT-022` 取代，保留原确认记录             | 见 `FACT-022`                                              |
| `FACT-022` | COW 目标集合在事件 Handler 消费时确定，不提前固定，不比较派生完成与修改开始时间。                                                                                                                                        | 用户确认 D-017                                                                    | 消费时按当前有效引用关系                                   |
| `FACT-023` | 事件顺序由事件框架顺序记录/消费；LTI 持有自身 Revision，与 `SourceRevision` 不同步；显式升级 Revision 触发依赖变体 COW。                                                                                                 | 用户确认 D-018                                                                    | 事件同步细节留实现                                         |
| `FACT-024` | ProductSpace 是 Shared Kernel 独立聚合；在默认 WorkingSet 维护生命周期，可关联多个 WorkingSet，不随派生复制/迁移。                                                                                                       | 用户确认（2026-11-10）                                                            | `POL-012`                                                  |
| `FACT-025` | 分类元模型三层、启动前初始化常驻内存；Tracker 是 Classification 的投影；产品空间创建时按分类集合投影生成 Tracker。                                                                                                       | 用户确认（2026-11-10）                                                            | 分类版本/变更协议待定                                      |
| `FACT-026` | 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 原始聚合只能在默认 WorkingSet 创建；子 WS 只能纳入直接上游。                                                                                                           | 用户确认（2026-11-10）                                                            | 多级谱系见 `FACT-028`                                      |
| `FACT-027` | 未物化变体可参与 Diff；未物化目标被 Merge 修改时构建业务聚合并 save；决策归 MergeRound；确认归 MergeEntry，见 [ADR-0006](06-decisions/adr-0006-merge-confirmation-ownership.md)；共享 Tracker 不参与 Merge；非共享只允许直接父子。 | 用户确认 D-019                                                                    | [T1](05-contracts/t1-merge-execution.md)                   |
| `FACT-028` | 多级 WorkingSet 谱系：单父、`parentWorkingSetId`、默认根为空、允许空范围子 WS、子 WS Tracker 只来自直属父、Merge 只允许直属父子双向、有子 WS 的中间 WS 不可删。                                                          | 用户确认（2026-11-12）                                                            | [谱系设计](05-contracts/working-set-lineage.md)            |

## 3. 业务政策（POL）

| 编号      | 当前政策                                                                                                                                                             | 状态                                                        | 说明                                                                                                                          |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `POL-001` | 每个治理范围只有一个默认根，普通用户不能删除。                                                                                                                       | Pending（物理 Id=1 见 `TC-001`）                            | 业务治理选择                                                                                                                  |
| `POL-002` | 子工作集不允许新建 Tracker；可在已有非共享 Tracker 下新建 Item。                                                                                                     | 用户已确认                                                  | 创建入口与 Item 归属分开                                                                                                      |
| `POL-003` | 分类共享特性为固定出厂设定；Tracker 共享特性创建时确定、后续不可变；共享分类只能建共享 Tracker，非共享分类两者可选。                                                 | 用户已确认                                                  | 不推导其他分类属性                                                                                                            |
| `POL-004` | Compare/Merge 只允许直接父子工作集双向进行，支持新增、删除、修改，作用于支持能力的非共享 Tracker。                                                                   | 用户已确认                                                  | 兄弟/跨代拒绝                                                                                                                 |
| `POL-005` | 同一 Working Set 有效范围内 `CommonId` 唯一，同一家族只允许一个有效变体。                                                                                            | Accepted（阶段 3.1）                                        | 软删占键；T6-C 已定身份分配                                                                                                   |
| `POL-006` | 原“Tracker 加入子工作集后禁止移除”已被 `POL-014` 修订：Shared 移除仅解绑；非共享可删除但须为空叶子。                                                                 | 已修订（历史政策）                                          | 不得与 `POL-014` 并存                                                                                                         |
| `POL-007` | 子工作集可在已有非共享 Tracker 下新建 Item；共享 Tracker 下从子工作集入口新建的 Item 实际在默认工作集创建。                                                          | 用户已确认                                                  | 回并见 T1 §8                                                                                                                  |
| `POL-008` | Shared 修改对所有已绑定工作集同时可见；具体读取面实现与验收指标待设计。                                                                                              | 业务要求已确认；技术指标待定                                | 不得自行放宽为业务可见延迟                                                                                                    |
| `POL-009` | 非共享派生时，未选原结构父可令新 Tracker `parentId=null` 成为目标根，不强制补派生父。                                                                                | 用户确认 D-004                                              | SCN-015                                                                                                                       |
| `POL-010` | 父也选中时默认继承已选节点间的组织关系；创建完成前可显式重组。                                                                                                       | 用户确认 D-005                                              | 不改变 `sourceId`                                                                                                             |
| `POL-011` | 创建完成后 `parentId` 不可修改；不支移动、换父或调整为根。                                                                                                           | 用户确认 D-006                                              | 只冻结组织位置                                                                                                                |
| `POL-012` | SpaceBinding 是 TrackerBinding 前置；同 WS 同空间最多一个 SpaceBinding；同 WS/空间/分类可绑定多个 Tracker；Tracker 空间必须一致；同一 Tracker 不在同一 WS 重复纳入。 | 用户确认（2026-11-10）；T6-F 修订（2026-11-16）             | 默认资格见 [T6-F](archive/reviews/2026-11-16-t6-f-lineage-constraints.md#43-shared-默认与删除补位question-002k2026-11-17-用户确认) |
| `POL-013` | Shared 纳入复用原 TrackerId 仅建绑定；非共享纳入创建新聚合 + 直接 `sourceId`；移除后可重新纳入/派生；最后 Tracker 移除不删 SpaceBinding。                            | 用户确认（2026-11-10）                                      | 事务边界见 T2 绑定/删除契约                                                                                                   |
| `POL-014` | Tracker、ProductSpace 与 WorkingSet 的删除准入及解绑顺序见[绑定与删除契约 §4](05-contracts/t2-binding-deletion.md#42-绑定移除)。                                      | 用户确认（2026-11-10；2026-11-20 调整删除入口与空空间准入） | 原裁定见 [ADR-0004](06-decisions/adr-0004-productspace-independent-aggregate.md)                                              |
| `POL-015` | WorkingSet 聚合维护当前工作集的 SpaceBinding/TrackerBinding 结构；ProductSpace/Tracker 只维护自身归属身份。                                                          | 用户确认（2026-11-10）                                      | 持久化映射见 [schema.sql](../../deploys/src/main/resources/schema.sql)                                                        |
| `POL-016` | 仅支持不可恢复的领域删除；软删/归档/恢复不纳入 T2 本版；彻底删除不强制数据库物理 DELETE；区分绑定移除、聚合删除、业务 TrackerItem 删除。                             | 用户确认（2026-11-11，T2 K1）；2026-11-23 调整：Tracker 聚合删除改为软删标记，保留绑定关系以支持将来恢复 | 业务 Item 软删见 D16                                                                                                          |
| `POL-017` | Compare/Merge 候选 Tracker 对只按直接 `sourceId` 派生关系确定：只允许直属派生父子，不允许跨级，不允许派生兄弟节点互配，不做同槽全组合。                              | 用户确认（2026-11-17）                                      | T6-F §4.2、T1 §3.1                                                                                                            |

## 4. 合并决策优先（已确认业务政策）

用户登记合并决定后，执行采用已批准的冻结值。目标业务 BC
整体校验并决定提交。已有业务目标在登记后发生并发修改时返回冲突，保留选择与结果。旧的取源最新值与接收方变动裁定由
[ADR-0009](06-decisions/adr-0009-merge-dispatch-frozen-evidence.md) 替代。

## 5. 假设（HYP）

| 编号      | 假设                                                                                    | 关联/验证                                                           |
|-----------|-----------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `HYP-001` | Working Set 更像版本化范围清单/工作视图，而非 Git Branch。                              | 已由 `FACT-001` 与 `QUESTION-001` 关闭支撑；Manifest 仅作为规模候选 |
| `HYP-002` | 单个 Tracker 独立性与整个谱系独立生命周期应分开。                                       | D-001/D-002；谱系不设聚合仍为设计推论                               |
| `HYP-003` | ItemVariant 可能是承载身份、固定来源和物化状态的领域根。                                | 已由 D-010/D-012 排除；LightTrackerItem 承担                        |
| `HYP-004` | 早期 namespace/type/CommonId 复合家族键的担忧已被全局家族唯一性排除。                   | `FACT-005`                                                          |
| `HYP-005` | History/Snapshot 是 COW、Compare 和审计的共同上游能力。                                 | `QUESTION-006`                                                      |
| `HYP-006` | Compare & Merge 应是独立模型边界，通过业务对象公开能力写目标。                          | T1/T4                                                               |
| `HYP-007` | Reference 与 Association 需要不同生命周期和缺端点语义。                                 | `QUESTION-010`                                                      |
| `HYP-008` | 超大 Working Set 可能需要 Manifest revision/分片准备，而不是加载全部 Binding 的单聚合。 | `QUESTION-012`                                                      |

## 6. 技术约束（TC）

| 编号     | 陈述                                                                                                     | 处理                                                                                                                                                                    |
|----------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `TC-001` | 默认根物理 ID 固定为 `1`。                                                                               | 部署/实现约定，不进入业务不变量                                                                                                                                         |
| `TC-002` | 一个写事务最多修改一个聚合；跨聚合变更必须通过可靠事件、独立事务最终收敛，禁止跨聚合同步事务与串联写入。 | Confirmed：用户 2026-11-19 本轮明确裁定；架构硬约束，非业务不变量；规则见 [AGENTS §7.2](../../AGENTS.md)                                                               |
| `TC-003` | 现有业务内容可能采用覆盖式表结构，历史读取能力有限。                                                     | 历史能力盘点；T3 快照表已提供最小证据                                                                                                                                   |
| `TC-004` | 旧实现存在 Long 主键、旧 branch 字段、SDK/Biz 分层。                                                     | 只作为迁移证据，不定义领域身份                                                                                                                                          |
| `TC-005` | 事件可能至少一次投递，投影存在延迟。                                                                     | 幂等键、因果序列、最大延迟                                                                                                                                              |
| `TC-006` | 客户 V3 库表以 `created_by VARCHAR + created_time BIGINT` 记录创建审计，没有 `created_at`。              | 用户 2026-11-21 裁定：`air_inst_requirement_rev`/`air_ws_items_index` 的创建审计使用该字段对，写入由 Java `System.currentTimeMillis()` 赋值；基线时间比较用 `created_time <= epochMillis`。 |
| `TC-007` | `light_tracker_items` 是 V4 初版物理表，已由 `air_ws_items_index` + `air_ws_items_revision_index` 取代。 | 生产与测试主路径都不再引用；仅保留文档历史说明。                                                                                                                        |
| `TC-008` | Checkpoint 的物理承载表是 V3 旧表 `air_ws_items_merge_index`，`MergeCheckpoint` 只是 V4 领域模型。       | 不存在当前的 `merge_checkpoints` 表；列不足走增量 DDL，不新建第二张表。                                                                                                 |
| `TC-009` | 团队时间类型统一为 epoch 毫秒；Java 侧使用 `long`（可空字段用 `Long`），不引入 `java.time.Instant`。     | 用户 2026-11-22 修正：领域、事件、REST 与持久化均以毫秒表达；`baselineAtByTracker` 接收毫秒数字，不再使用 ISO 字符串。                                                  |
| `TC-010` | 写侧仓储不读取 `RequestThreadHolder`；审计事实由聚合携带，并由应用层在构造或修改聚合时注入。             | WorkingSet 持 `owner`/`actor`，LightTrackerItem 与 Requirement 持 `createdBy`，Tracker 持 `createdBy`/`updatedBy`；仓储只使用聚合字段。                                 |
| `TC-011` | `working_set_creation_snapshots` / `working_set_creation_items` 已从代码与 schema 移除。                 | 创建/扩展按各 Tracker 的基线时间与 LocalId 游标尽力派生（ADR-0010）；旧冻结清单仅保留历史说明。                                                                         |

## 7. 已接受的限定陈述（R01—R19）

**Accepted；发布状态：Unreleased。** 归档日期 2026-11-10，确认人为用户。接受依据为
[阶段 3.1 用户批准记录](archive/reviews/2026-11-09-stage-3-1-rule-convergence.md#用户批准记录与生效范围)：明确批准 R01—R17
与限定术语，并确认 R18、R19。本节归档既有接受范围，不增加新业务决定。

| 条目            | 已接受陈述                                                                                                                                                                                         | 原编号                                                                                        | 裁定与场景定位                                                   |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| R01             | SHARED Tracker/Item 新增修改归属默认工作集，绑定各工作集同时可见，无子侧副本；原共享绑定禁止移除条款已由 R02 与 T2 取代                                                                            | FACT-002、POL-008；QUESTION-003/009                                                           | SCN-002；历史批准记录及 T2 §4.2                                  |
| R02（已修订）   | 原「任意 Tracker 加入子工作集后禁止移除」现按 2026-11-10 最新规则修订：Shared Tracker 移除=解除 TrackerBinding（非删除聚合）；非共享 Tracker 可删除但须无派生子级且为空（叶子）；空 Tracker 可移除 | POL-006（修订）/POL-014；QUESTION-009                                                         | 子工作集纳入与移除场景                                           |
| R03             | 分类共享属性为不可修改的出厂设定；Tracker 共享属性创建后不变；共享分类仅可共享，非共享分类两者可选                                                                                                 | POL-003；QUESTION-003                                                                         | 核心场景“Tracker 共享特性的创建约束”；两节分类补充确认           |
| R04             | 子工作集禁止创建 Tracker；可在已有非共享 Tracker 创建 Item，共享 Item 创建归默认工作集；子新增可回并父侧                                                                                           | POL-002 已确认子句、POL-007；QUESTION-005                                                     | 核心创建与新增回并场景；裁定进度创建能力                         |
| R05             | 派生及回并同 CommonId、不同 LocalId；实际归默认工作集时二者相等，否则不等；无关对象跨类型/Tracker 不重号；两 ID 为雪花 ID                                                                          | FACT-005 的身份子集；QUESTION-004/005                                                         | 核心新增回并、SCN-002；裁定进度身份等值；CONCEPT-007 与身份规则  |
| R06             | 直接父子双向新增/删除/修改；兄弟跨代不准入；仅适用具备能力的非共享对象                                                                                                                             | FACT-007、POL-004；QUESTION-007                                                               | SCN-007、核心双向六组合；直接父子补充确认                        |
| R07（范围澄清） | 普通字段按 D-016/D-018 的来源读取与首次 COW 规则；附件、关系保持派生时状态（R19）。来源删除不得破坏所需事实的可读性                                                                                | FACT-006；QUESTION-006；T3 §2.1                                                               | 原 D3 表述的全内容固定范围由 2026-11-11 用户选择 A 澄清；SCN-072 |
| R08             | 已选 ACCEPT 字段按批准时冻结的值执行                                                                                                                                                               | [ADR-0009](06-decisions/adr-0009-merge-dispatch-frozen-evidence.md)                           | SCN-010、B09；旧裁定见阶段 3.1 矩阵                              |
| R09             | 业务 BC 整体校验并决定提交；已有业务目标在登记后并发修改时返回冲突，保留选择与结果                                                                                                                 | FACT-007/008；[ADR-0009](06-decisions/adr-0009-merge-dispatch-frozen-evidence.md)             | B06；旧裁定见阶段 3.1 矩阵                                       |
| R10             | 批次可部分成功，重试仅未完成项；已成功项即使源又变化仍跳过                                                                                                                                         | FACT-008 关联（无独立 POL）；QUESTION-008                                                     | B08；D5                                                          |
| R11             | KEEP_TARGET 可 markAsMerged，Checkpoint 表达处理而非内容相等；双侧业务 revision 不变，普通编辑后仍隐藏；任一侧升级后用户主动比对全部适用字段                                                       | FACT-008 已确认子集；QUESTION-008                                                             | SCN-008 补充、SCN-009；KEEP_TARGET 裁定                          |
| R12             | 普通编辑只增乐观锁 `lock_version`；显式升级才产生版次历史并使业务 revision +1；发布大版本 `version`（字符串、可空）仅在发布时出现                                                                  | CONCEPT-009；QUESTION-006/008；2026-11-11 术语修正                                            | SCN-009 及 KEEP_TARGET 补充；版次裁定                            |
| R13             | Part 由 PLM 默认工作集管理，经共享 Tracker 纳入查看，不派生/比对/合并                                                                                                                              | FACT-011 关联（不接受 API 定义）；QUESTION-013                                                | 边界 Part 专项；D2                                               |
| R14             | DQA 八类对象全部共享纳入查看，自管业务逻辑，不派生/比对/合并                                                                                                                                       | FACT-011 关联；QUESTION-013                                                                   | 边界 DQA 专项列举八类；DQA 裁定                                  |
| R15             | GateFlow Process 为核心聚合，每版次一个工作集，父子链表达版次链；四类对象范围且无共享 Tracker，内部规则归 GateFlow                                                                                 | FACT-011 关联；QUESTION-001/013                                                               | 边界 GateFlow 专项；GateFlow 映射与对象范围裁定                  |
| R16             | 历史全部内容替换叶子草稿，保留版次身份、工作集 ID、原父级；目标独有元素退出当前内容                                                                                                                | FACT-010 关联；QUESTION-001/013                                                               | 边界 GateFlow 专项；身份裁定及“本轮检查结果”成员裁定             |
| R17             | 替换准备时旧完整内容可用且可编辑；全准备成功整体切换，可覆盖期间编辑；失败保留当前内容和已保存编辑                                                                                                 | FACT-010 已确认局部；QUESTION-001/013                                                         | 边界 GateFlow 专项；原子可见性裁定                               |
| R18             | 单工作集有效范围内 CommonId 唯一，同族只允许一个有效变体                                                                                                                                           | POL-005；QUESTION-004                                                                         | 用户批准记录第 2 项；SCN-011                                     |
| R19             | 派生附件与关系均固定为派生时状态                                                                                                                                                                   | FACT-006 的覆盖补充；QUESTION-006                                                             | 用户批准记录第 3 项；SCN-012                                     |

R 编号保持稳定，用于限定接受范围，不替代 `FACT`/`POL` 编号；对应场景见 [场景集](09-scenarios.md)。

### 7.1 分类、范围与例外

- 事实与领域语义：R01 的共享权威与归属、R05 身份、R06 双向能力、R07/R19 派生依据、R12 版次含义、R13—R16
  的对象范围与映射。能力存在不意味着所有对象均支持该能力。
- 当前业务政策：R01 共享权威与归属（移除规则见 R02 修订）、R02 按最新规则可移除（Shared 解除绑定/非共享叶子删除）、R03
  创建与不可变限制、R04 创建入口、R06 直接父子准入、R08—R11 的执行/确认/重试选择、R17 整体替换可见性、R18
  单范围唯一。政策可经后续明确裁定演进；当前必须遵守，不能因不是永久真理而忽略。
- R09 中 BC 整体校验与提交责任属于已确认职责约束；用户决定优先不豁免目标业务合法性。拒绝或未知结果不能冒充成功。
- 创建归属规则不自动新增 Part/DQA 的创建入口；它们仅共享纳入查看。共享对象不通过 Merge 同步。
- Tracker 移除按 [T2 §4](05-contracts/t2-binding-deletion.md) 区分：Shared 解绑不受 Item 限制；非共享删除须无
  Item、无组织子级、无派生子级。此为既有 K1～K3 裁定同步；不等于删除 Item、工作集或 GateFlow 内容成员退出。
- GateFlow 历史替换是独立合同，允许跨历史版次取内容，不是直接父子 Merge 准入的例外。准备期编辑可被覆盖，准备失败保留已保存编辑。
- R18 针对有效范围，不禁止不同工作集保存同一家族变体。R19 不自动决定永久保留、权限冻结或关系目标内容递归快照。

### 7.2 已接受术语

CONCEPT-003 的共享权威含义、CONCEPT-007 家族身份、CONCEPT-009 业务版次，以及身份规则和 KEEP_TARGET/Checkpoint
的已处理含义；CONCEPT-014/015 仅接受方向、目标整体校验和上述已确认选择语义。定义见
[统一领域语言](02-language.md)，不连带接受 DEFER 完整协议。AC-001 作为既定企业约束继续引用。

### 7.3 可直接用于测试的边界

上述 R01—R19 可作为测试期望的业务依据，不必等待完整聚合模型或 INV 获批。测试应引用具体 R/FACT/POL 与对应 SCN/B：例如
SCN-002 的共享归属、SCN-011 唯一性、SCN-012 固定历史、B06 拒绝不提交、B08 成功项不重试、B09 最新值、SCN-009 版次组合、GateFlow
专项整体切换。

这不批准场景中的全部候选实现细节。测试 seam、错误码、锁、事务、SLA 数值、回执与乱序协议未确定时，不从本基线臆造期望；先约定公开行为再实施。

### 7.4 不在本主题内

全部 INV 的完整陈述、其保护者与强一致范围仍须逐条审查；详见
[候选不变量审查](archive/reviews/2026-11-10-invariant-evidence-review.md)。聚合方案、图表、API、未确认事实和生命周期协议继续
Draft。主题接受不等于代码授权、发布、Commit 或 Tag。

## 8. 业务不变量（INV）

状态含义：**Confirmed** 表示业务规则已由用户确认，可作为测试期望；**Confirmed / Draft formal**
表示业务子句已确认、形式化或保护者仍候选；**Candidate** 表示完整陈述尚未获批；**Superseded** 表示已被后续裁定取代，保留历史编号。

### 8.1 核心不变量（INV-001～013）

| 编号      | 约束                                                                                   | 状态与依据                                                                     | 验证                             |
|-----------|----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------|
| `INV-001` | Shared 的所有绑定解析到同一权威 Tracker、同一 Item 变体和同一权威内容。                | Candidate；FACT-002；`POL-008` 的可见性要求已确认                              | 跨 WS 并发写；列表/搜索读取面    |
| `INV-002` | 非共享派生后，目标本地修改不回写来源，来源变化不覆盖目标已提交本地事实。               | Candidate；FACT-003                                                            | 来源写/目标写交错                |
| `INV-003` | Item 变体的家族身份、`LocalId` 和所属 Tracker 在查询、重试、物化时保持稳定。           | Candidate；FACT-004/005；T3 已兑现 `LocalId` 稳定                              | 重试与 COW                       |
| `INV-004` | 未物化变体的固定来源在依赖存续期间可按 Source Cut 回读；历史缺失不得以当前值冒充。     | FACT-006；`QUESTION-006` 已裁定软删除物化与永久删除守卫                        | 历史、附件、关系、删除态         |
| `INV-005` | COW 只改变内容承载，不改变既有逻辑变体身份与直接来源。                                 | Candidate；D-012/D-013                                                         | 并发首次写                       |
| `INV-006` | Compare/Merge 的方向、范围与双侧证据固定；执行使用登记时已批准的值。                   | Candidate；FACT-007；[ADR-0009](06-decisions/adr-0009-merge-dispatch-frozen-evidence.md) | 反向、登记前过期与登记后来源修改 |
| `INV-007` | 存在性差异必须显式裁决；一侧缺失不能自动推导 CREATE、RESTORE 或 DELETE。               | Candidate；FACT-007；T1 §7                                                     | 来源删除、目标已编辑反例         |
| `INV-008` | 更新的每个差异字段必须明确选择 ACCEPT 或 IGNORED；行级确认按差异类型与成功结果判定。   | Candidate；FACT-008；T1 §7                                                     | 四组合契约测试                   |
| `INV-009` | 字段级决定应用后目标整体仍须通过其业务不变量；Merge 不能绕过目标能力写字段。           | Candidate；B06；T4                                                             | 交叉字段反例                     |
| `INV-010` | 同一幂等操作重试不重复分配身份或重复应用；已完成轮次的旧确认重放不得覆盖后续轮次。     | Candidate；T1 §6                                                               | 重投和乱序                       |
| `INV-011` | 发布的工作范围必须引用封闭、可读、身份确定的成员与必要关系；准备失败不能冒充发布成功。 | Candidate；FACT-010；T7 创建任务                                               | 半批失败、取消、重启             |
| `INV-012` | 删除不得破坏仍存活的派生变体、Snapshot、审计或有效关系；无本地行不能证明不存在。       | Candidate；FACT-006/009                                                        | 删除与修复竞态                   |
| `INV-013` | 有效 Reference 的逻辑身份不因端点暂不可解析而丢失；修复不能复活已删除或已换目标关系。  | Candidate；FACT-009；`QUESTION-010`                                            | 事件重投、删除竞态               |

### 8.2 Tracker 谱系与组织（INV-014～023）

| 编号      | 约束                                                                                | 状态与依据                      | 验证                             |
|-----------|-------------------------------------------------------------------------------------|---------------------------------|----------------------------------|
| `INV-014` | 有结构父时，父节点在同一 Working Set 的有效组织范围内。                             | Confirmed / Draft formal；D-003 | SCN-014；Shared 引用范围另行核验 |
| `INV-015` | Tracker 结构关系不形成循环。                                                        | Candidate；D-003/D-006 推导     | 自环、A.parent=B/B.parent=A 反例 |
| `INV-016` | 派生 Tracker 的直接来源位于直接父级 Working Set。                                   | Confirmed / Draft formal；D-003 | T1.source=T0，T2.source=T1       |
| `INV-017` | 派生来源不跳过中间 Working Set。                                                    | Confirmed / Draft formal；D-003 | WS0→WS1→WS2                      |
| `INV-018` | 结构父子与派生来源分开表达，不相互替代。                                            | Confirmed / Draft formal；D-003 | B1.parent=A1 且 B1.source=B0     |
| `INV-019` | 未选原结构父的非共享派生允许 `parentId=null` 成为目标根，不强制补派生该父。         | Confirmed / Draft formal；D-004 | SCN-015                          |
| `INV-020` | 组织裁剪不改变直接派生来源语义；`parentId=null` 不推导 `sourceId=null` 或跨级来源。 | Confirmed / Draft formal；D-004 | B1.parent=null 且 B1.source=B    |
| `INV-021` | 目标组织结构只需在目标有效范围内合法，不必与源树完整同构。                          | Confirmed / Draft formal；D-004 | 父也选中时的映射                 |
| `INV-022` | 创建完成后 `parentId` 保持不变。                                                    | Confirmed；D-006                | SCN-017                          |
| `INV-023` | 完成创建前必须确定符合当前范围组织规则的位置；允许的结构重组在此前完成。            | Confirmed；D-005/D-006          | SCN-016/018                      |

### 8.3 LightTrackerItem 身份与 COW（INV-024～052）

| 编号      | 约束                                                                                                 | 状态与依据                                    | 验证             |
|-----------|------------------------------------------------------------------------------------------------------|-----------------------------------------------|------------------|
| `INV-024` | 首次 COW 建立本地业务对象时，该对象与 LTI 的 `CommonId`/`LocalId` 一致。                             | Confirmed 业务规则 / Draft formal；D-007、T3  | SCN-019          |
| `INV-025` | 业务对象修改成功但索引更新失败时，系统状态与可恢复结果一致，不伪造成功也不丢失已提交内容。           | Candidate；D-007、T3 双事实                   | SCN-020          |
| `INV-026` | LTI 派生时具有明确的当前 `LocalId` 与 `CommonId`。                                                   | Confirmed / Draft formal；D-008               | SCN-021          |
| `INV-027` | 派生变体的来源信息可追溯（`SourceLocalId`/`SourceRevision`）。                                       | Confirmed / Draft formal；D-008、D-012        | SCN-021/022      |
| `INV-028` | LTI 经 `TrackerId`/`WorkingSetId` 指向的 Tracker 与 WorkingSet 满足工作范围约束。                    | Confirmed / Draft formal；D-008               | SCN-022          |
| `INV-029` | 未物化不代表业务变体不存在。                                                                         | Confirmed / Draft formal；D-008               | SCN-023          |
| `INV-030` | 业务对象内部不变量由业务 BC 负责，Working Set 不拥有或重建。                                         | Confirmed；D-007/D-008                        | 跨 BC 合并/COW   |
| `INV-031` | 同一 LTI 首次物化后仅一个有效业务对象身份。                                                          | Candidate；D-008                              | SCN-024 并发反例 |
| `INV-032` | LTI 与业务对象持久化之间的可观察一致性。                                                             | Candidate；D-008、T3                          | SCN-024          |
| `INV-033` | 物化完成后有效内容来源定位到自身 `LocalId` 及自身有效版次。                                          | Confirmed；D-009                              | SCN-025          |
| `INV-034` | 来源修改前的完整业务事实可被物化 Handler 可靠取得。                                                  | Confirmed 方案：事件关联不可变内容快照；D-010 | SCN-026          |
| `INV-035` | 已物化变体不被重复或错误覆盖。                                                                       | Candidate；D-009/D-011                        | SCN-028          |
| `INV-036` | 派生历史来源与当前有效内容来源分别保持，物化切换不丢失来源追溯。                                     | **Superseded by D-012**：历史派生链不要求保存 | SCN-027（历史）  |
| `INV-037` | 首次快照确定性：目标变体最终内容由首次相关来源变更之前的事实决定。                                   | Confirmed；D-011                              | SCN-030          |
| `INV-038` | 物化结果唯一性：重放/乱序/并发不产生互相竞争的有效物化结果。                                         | Confirmed；D-011                              | SCN-030/031      |
| `INV-039` | Local-backed 后来源后续事件不改变已物化内容。                                                        | Confirmed；D-011                              | SCN-032          |
| `INV-040` | COW 不改变 `LocalId`、`CommonId`。                                                                   | Confirmed；D-012                              | SCN-035          |
| `INV-041` | Source-backed 时有效内容由 `SourceLocalId` + `SourceRevision` 解析。                                 | Confirmed；D-012                              | SCN-035/036      |
| `INV-042` | Local-backed 时 `SourceLocalId = LocalId` 且 `SourceRevision = Revision`。                           | Confirmed；D-012                              | SCN-035          |
| `INV-043` | 未物化变体直接指向实际拥有完整业务内容的来源，不逐级递归。                                           | Confirmed；D-013                              | SCN-037          |
| `INV-044` | 同一实际来源修改时保护所有直接内容依赖者，不限派生深度（扇出）。                                     | Confirmed；D-013                              | SCN-038          |
| `INV-045` | 已有未物化变体不因其他变体物化而自动重定向有效来源。                                                 | Confirmed；D-014                              | SCN-039          |
| `INV-046` | 派生身份建立与完整业务内容复制解耦（派生不复制完整数据）。                                           | Confirmed；D-015                              | SCN-043          |
| `INV-047` | 选定业务 Revision 后，派生不冻结该 Revision 的普通内容。                                             | Confirmed；D-016                              | SCN-044          |
| `INV-048` | 来源修改事件目标发现除 `SourceLocalId` 相等外，还需限定 `SourceRevision`。                           | Candidate；D-015                              | SCN-044          |
| `INV-049` | 完整派生完成前的来源修改不为该变体触发 COW。                                                         | **Superseded by D-017/INV-050**               | SCN-045（历史）  |
| `INV-050` | COW 目标集合在 Handler 消费时确定：当前 `SourceLocalId`/`SourceRevision` 匹配且未物化的 LTI 都参与。 | Confirmed；D-017                              | SCN-048          |
| `INV-051` | LTI 持有自身 Revision 字段，与 `SourceRevision` 分离。                                               | Confirmed；D-018                              | SCN-049          |
| `INV-052` | 显式升级业务 Revision 触发依赖变体 COW。                                                             | Confirmed；D-018                              | SCN-049          |

### 8.4 ProductSpace、绑定与删除（INV-053～068）

| 编号      | 约束                                                                                                                                   | 状态与依据                                                                                                                                            | 验证            |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------|
| `INV-053` | SpaceBinding 是 TrackerBinding 的业务前置。                                                                                            | Confirmed；`POL-012`                                                                                                                                  | SCN-050         |
| `INV-054` | Tracker 的 `productSpaceId` 必须等于其 TrackerBinding 的空间 ID。                                                                      | Confirmed；`POL-012`                                                                                                                                  | SCN-050         |
| `INV-055` | 同一 WorkingSet 内 ProductSpace 绑定唯一。                                                                                             | Confirmed；`POL-012`                                                                                                                                  | SCN-050         |
| `INV-056` | 同一（工作集，空间，分类）可纳入多个 Tracker，有效范围内至多一个默认 Tracker。                                                         | Confirmed；`POL-012`、[T6-F 默认资格裁定](archive/reviews/2026-11-16-t6-f-lineage-constraints.md#43-shared-默认与删除补位question-002k2026-11-17-用户确认) | SCN-050/051     |
| `INV-057` | Shared Tracker 在多个 WS 共享同一聚合身份；非共享在子 WS 拥有独立派生身份。                                                            | Confirmed；`POL-013`                                                                                                                                  | SCN-052         |
| `INV-058` | 聚合删除及绑定解除受 `POL-014` 约束。                                                                                                  | Confirmed；`POL-014`；边界由 `INV-064` 澄清                                                                                                           | SCN-052/053     |
| `INV-059` | 默认 WS 不可删除/隐藏；Tracker `parentId` 创建后不可变。                                                                               | Confirmed；FACT-026、`INV-022`                                                                                                                        | SCN-054         |
| `INV-060` | SpaceBinding、TrackerBinding 是 WorkingSet 聚合内 Entity；ProductSpace/Tracker 不持有绑定。                                            | Confirmed；`POL-015`                                                                                                                                  | SCN-050/052     |
| `INV-061` | 未物化变体可以参与 Diff。                                                                                                              | Confirmed；FACT-027                                                                                                                                   | SCN-055         |
| `INV-062` | 未物化目标被 Merge 修改时构建业务聚合正常 save（物化与修改一体）。                                                                     | Confirmed；FACT-027                                                                                                                                   | SCN-055         |
| `INV-063` | MergeRound 记录参数/进度/结果；决策和确认归 MergeRound；必须与本轮结果一起提交，跨轮次查询只读。                                       | Confirmed；FACT-027                                                                                                                                   | SCN-056/059/060 |
| `INV-064` | 删除子工作集时，Shared TrackerItem 不阻止解除其 Shared 绑定。                                                                          | Confirmed；T2 K1/K3                                                                                                                                   | SCN-061/063     |
| `INV-065` | 非共享 Tracker 解绑/删除必须同时满足：无 TrackerItem、无组织子级、无派生子级。                                                         | Confirmed；T2 K2                                                                                                                                      | SCN-061/062     |
| `INV-066` | 产品空间删除时无绑定、有效 Tracker 或业务引用。准入及删除语义见[绑定与删除契约 §4.3](05-contracts/t2-binding-deletion.md#43-聚合删除)。 | Confirmed；2026-11-23 确认有效 Tracker 阻止删除 | SCN-064 |
| `INV-067` | 删除 WorkingSet 前须无子级、无绑定且无归属 Tracker；默认 WS 始终不可删。                                                               | Confirmed；T2 K3                                                                                                                                      | SCN-063         |
| `INV-068` | 删除后不可恢复；软删/归档/恢复不属于 T2 本版；彻底删除不强制物理 DELETE。                                                              | Confirmed；`POL-016`；2026-11-23 调整：Tracker 聚合删除改为软删标记，保留绑定以支持将来恢复                                                                                                                                    | SCN-061/065     |

### 8.5 Merge 与物化（INV-069～071）

| 编号      | 约束                                                                                                   | 状态与依据        | 验证    |
|-----------|--------------------------------------------------------------------------------------------------------|-------------------|---------|
| `INV-069` | LTI 持有独立 `materialized` 字段，与 `sourceLocalId`/`sourceRevision` 在同一次聚合状态变更中一致更新。 | Confirmed；T3 K1′ | SCN-066 |
| `INV-070` | `localId` 派生时预分配、首次物化复用、COW 前后不变；BC 必须能按稳定 `localId` 定位或创建变体。         | Confirmed；T3 K2′ | SCN-068 |
| `INV-071` | 物化完成 = 业务保存成功 **且** LTI 切换成功；保存成功而切换失败不回滚，幂等重试只补未完成步骤。        | Confirmed；T3 K3′ | SCN-067 |

### 8.6 当前判断

- 完整 INV 正式接受：**0**；这不代表已确认业务约束为零，已确认子句按上表状态与 §7 使用。
- 区间状态：`INV-001`～`013` 完整形式仍候选；`INV-014`～`023`、`INV-053`～`071` 业务子句已确认；`INV-024`～`052`
  多数业务子句已确认，事务/保护方案仍候选。
- `INV-036`、`INV-049`、`INV-056` 的旧陈述已由后续裁定取代或重述，见上表。
- `TC-002` 是架构硬约束，不是业务不变量；不能反向证明聚合边界或替代业务不变量证据。
