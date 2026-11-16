# Working Set 事实、政策、假设与技术约束

**状态：Draft（混合知识登记）；已接受子集见[事实与政策主题基线 v1](facts-policy-baseline-v1.md)，权威索引见 [BASELINE](../BASELINE.md)。**

本页登记事实、政策、假设和技术约束，不重复裁定过程。已由后续契约关闭的条目保留结论与链接，不再作为当前候选。

## Organization / Architecture Constraint（Confirmed）

| 编号 | 约束 | 状态 | 依据 |
| --- | --- | --- | --- |
| `AC-001` | Working Set 是企业 Shared Kernel 的组成能力，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 同时包含 Privilege、Workflow、Class Hierarchy。 | Confirmed | 用户 2026-11-09 明确确认；[Shared Kernel 边界](shared-kernel-boundary.md) |

`AC-001` 固定企业架构归属和依赖方向，不证明 Working Set 是单一聚合、单一 BC 或微服务。

## Business Fact

| 编号 | 陈述 | 状态 | 来源/说明 |
| --- | --- | --- | --- |
| `FACT-001` | Working Set 是选择 Tracker 并形成有效工作范围的工作视图/范围清单，不是分支本体。 | 用户确认（2026-11-12 关闭 `QUESTION-001`） | [S1～S7 验收](../implementation/README.md) |
| `FACT-002` | Shared Tracker 及其 Item 的新增、修改实际归属默认工作集，所有已绑定工作集同时可见，不产生子工作集独有副本。 | 用户已确认 | `POL-008`；权限与读取面指标待设计 |
| `FACT-003` | 非共享 Tracker 派生产生新的独立业务身份；`sourceId` 指向直接父级 Working Set 下的来源 Tracker，逐级追溯；`parentId` 另表达同工作集直属结构父。 | 用户确认 D-002/D-003 | `INV-014`～`INV-018` |
| `FACT-004` | V4 的 VIRTUAL Item 表示没有本地业务内容行但仍存在的逻辑条目。 | 历史术语；现行模型由 Source-backed LTI 表达 | D-008；不得把“无本地行”当作不存在 |
| `FACT-005` | `CommonId` 关联同族变体，`LocalId` 标识具体变体；实例化不更换 `LocalId`。 | Accepted 子集：R05 身份关系；实例化稳定性已由 T3 兑现 | [身份规则](glossary.md#item-身份规则已确认) |
| `FACT-006` | 普通字段按来源 Revision 读取并在首次 COW 固定；附件与关系固定为派生时状态；来源删除不得破坏首次编辑所需事实的可读性。 | Accepted 限定范围 R07/R19；机制见 T3 | [T3 §2.1](lighttrackeritem-materialization-contract-v1.md) |
| `FACT-007` | 直接父子工作集支持双向 Compare/Merge；任一方向的新增、删除、修改均可经用户选择应用到接收方；`source/target` 是本轮角色。 | 用户已确认 | `POL-004`；目标 BC 校验提交 |
| `FACT-008` | Merge 数据选择、实际 Apply Result 与处理确认是不同事实。 | Accepted 子集 R08～R11；DEFER 边界见 `QUESTION-008.b` | [T1](merge-execution-contract-v1.md) |
| `FACT-009` | Reference 可在逻辑目标有效但物理端点暂不可解析时保持有效悬空状态。 | Reported / Pending | `QUESTION-010` 剩余可见性/守卫 |
| `FACT-010` | 创建、扩展、替换和合并被设计为可恢复长流程；受理成功不等于业务结果已可用。 | 部分 Accepted：GateFlow 替换 R16/R17；T7 创建 LRO 已落地 | T7 设计 |
| `FACT-011` | 架构示意把 `create / extend / resolve / diff / merge` 画作业务 BC 调用 Working Set 的统一 API 面。 | 已由 T4 收敛为六个用例的契约边界 | [T4](cross-bc-contract-v1.md) |
| `FACT-012` | 工作集领域统一使用 `TrackerItem` 作为公共 Item 契约；业务对象是各 BC 的完整独立聚合，实现该接口获得公共能力。 | 用户确认 D-007 | `TrackerItem` 继承 `BusinessObject`（D14） |
| `FACT-013` | LightTrackerItem 是 Working Set 专属独立聚合，非查询投影；派生采用 COW，不立即物化完整业务对象。 | 用户确认 D-008 | 属性与物化协议见 T3 |
| `FACT-014` | 来源变更触发 COW 采用事件驱动；普通修改不升级 Revision；物化成功后 LTI 切换到自身有效来源。 | 用户确认 D-009 | 事件与事务边界见 T3/T4 |
| `FACT-015` | COW 采用异步事件驱动、最终一致；修改前事实作为事件关联的不可变内容快照；不要求跨 BC 事务；最小模型为 Working Set/Tracker/LightTrackerItem。 | 用户确认 D-010 | 不引入 Manifest/ItemVariant |
| `FACT-016` | 首次快照优先：来源首次相关修改的修改前事实决定最终物化内容；后续修改不覆盖；按实际修改顺序而非事件到达顺序。 | 用户确认 D-011 | 事件顺序由框架兜底（D-018） |
| `FACT-017` | `SourceLocalId`/`SourceRevision` 表示当前有效内容来源；物化后均指向自身；COW 不改变 `LocalId`/`CommonId`。 | 用户确认 D-012 | 不保存历史派生链 |
| `FACT-018` | 多级派生采用直接有效来源定位，不逐级递归未物化中间变体；同一实际来源修改时扇出物化所有依赖者。 | 用户确认 D-013 | 历史派生关系不要求保存 |
| `FACT-019` | 派生完成后，已有下游不因中间上游物化而自动重定向 `SourceLocalId`；只有自身完成 COW 才切到自身。 | 用户确认 D-014 | 来源依赖与派生层级分开 |
| `FACT-020` | 派生不复制完整业务数据；时间基准（Snapshot/Head）只用于选 Revision，不等待来源停止变化。 | 用户确认 D-015 | `SourceRevision` 不等于内容快照 |
| `FACT-021` | 派生只固定业务版次，不冻结普通内容；完整派生完成边界的历史解释由 D-017 修订。 | 用户确认 D-016；时间边界由 D-017 取代 | 见 `FACT-022` |
| `FACT-022` | COW 目标集合在事件 Handler 消费时确定，不提前固定，不比较派生完成与修改开始时间。 | 用户确认 D-017 | 消费时按当前有效引用关系 |
| `FACT-023` | 事件顺序由事件框架顺序记录/消费；LTI 持有自身 Revision，与 `SourceRevision` 不同步；显式升级 Revision 触发依赖变体 COW。 | 用户确认 D-018 | 事件同步细节留实现 |
| `FACT-024` | ProductSpace 是 Shared Kernel 独立聚合；在默认 WorkingSet 维护生命周期，可关联多个 WorkingSet，不随派生复制/迁移。 | 用户确认（2026-11-10） | `POL-012` |
| `FACT-025` | 分类元模型三层、启动前初始化常驻内存；Tracker 是 Classification 的投影；产品空间创建时按分类集合投影生成 Tracker。 | 用户确认（2026-11-10） | 分类版本/变更协议待定 |
| `FACT-026` | 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 原始聚合只能在默认 WorkingSet 创建；子 WS 只能纳入直接上游。 | 用户确认（2026-11-10） | 多级谱系见 `FACT-028` |
| `FACT-027` | 未物化变体可参与 Diff；未物化目标被 Merge 修改时构建业务聚合并 save；决策归 MergeRound；Checkpoint 独立；共享 Tracker 不参与 Merge；非共享只允许直接父子。 | 用户确认 D-019 | [T1](merge-execution-contract-v1.md) |
| `FACT-028` | 多级 WorkingSet 谱系：单父、`parentWorkingSetId`、默认根为空、允许空范围子 WS、子 WS Tracker 只来自直属父、Merge 只允许直属父子双向、有子 WS 的中间 WS 不可删。 | 用户确认（2026-11-12） | [谱系设计](working-set-lineage-design.md) |

## Business Policy

| 编号 | 当前政策 | 状态 | 说明 |
| --- | --- | --- | --- |
| `POL-001` | 每个治理范围只有一个默认根，普通用户不能删除。 | Pending（物理 Id=1 见 `TC-001`） | 业务治理选择 |
| `POL-002` | 子工作集不允许新建 Tracker；可在已有非共享 Tracker 下新建 Item。 | 用户已确认 | 创建入口与 Item 归属分开 |
| `POL-003` | 分类共享特性为固定出厂设定；Tracker 共享特性创建时确定、后续不可变；共享分类只能建共享 Tracker，非共享分类两者可选。 | 用户已确认 | 不推导其他分类属性 |
| `POL-004` | Compare/Merge 只允许直接父子工作集双向进行，支持新增、删除、修改，作用于支持能力的非共享 Tracker。 | 用户已确认 | 兄弟/跨代拒绝 |
| `POL-005` | 同一 Working Set 有效范围内 `CommonId` 唯一，同一家族只允许一个有效变体。 | Accepted（阶段 3.1） | 软删占键；T6-C 已定身份分配 |
| `POL-006` | 原“Tracker 加入子工作集后禁止移除”已被 `POL-014` 修订：Shared 移除仅解绑；非共享可删除但须为空叶子。 | 已修订（历史政策） | 不得与 `POL-014` 并存 |
| `POL-007` | 子工作集可在已有非共享 Tracker 下新建 Item；共享 Tracker 下从子工作集入口新建的 Item 实际在默认工作集创建。 | 用户已确认 | 回并见 T1 §8 |
| `POL-008` | Shared 修改对所有已绑定工作集同时可见；具体读取面实现与验收指标待设计。 | 业务要求已确认；技术指标待定 | 不得自行放宽为业务可见延迟 |
| `POL-009` | 非共享派生时，未选原结构父可令新 Tracker `parentId=null` 成为目标根，不强制补派生父。 | 用户确认 D-004 | SCN-015 |
| `POL-010` | 父也选中时默认继承已选节点间的组织关系；创建完成前可显式重组。 | 用户确认 D-005 | 不改变 `sourceId` |
| `POL-011` | 创建完成后 `parentId` 不可修改；不支移动、换父或调整为根。 | 用户确认 D-006 | 只冻结组织位置 |
| `POL-012` | SpaceBinding 是 TrackerBinding 前置；同 WS 同空间最多一个 SpaceBinding；同 WS/空间/分类最多一个有效 TrackerBinding；Tracker 空间必须一致；同一 Tracker 不在同一 WS 重复纳入。 | 用户确认（2026-11-10） | 多 Tracker 例外见 T6-F |
| `POL-013` | Shared 纳入复用原 TrackerId 仅建绑定；非共享纳入创建新聚合 + 直接 `sourceId`；移除后可重新纳入/派生；最后 Tracker 移除不删 SpaceBinding。 | 用户确认（2026-11-10） | 纳入/移除事务待定 |
| `POL-014` | 删除/移除八条：Shared 移除仅解绑；非共享无 Item/无派生子才删除；有派生子不允许删除；任意 Tracker 有 Item 不允许聚合删除；有组织子节点但无派生子不允许删除；最后 Tracker 移除不删空间绑定；移除 ProductSpace 时仍有 TrackerBinding 抛异常；默认 WS 下 Shared 空且无子可删。 | 用户确认（2026-11-10，修订 `POL-006`） | 边界澄清见 `INV-064` |
| `POL-015` | WorkingSet 聚合维护当前工作集的 SpaceBinding/TrackerBinding 结构；ProductSpace/Tracker 只维护自身归属身份。 | 用户确认（2026-11-10） | 绑定 Entity 内部结构待定 |
| `POL-016` | 仅支持不可恢复的领域删除；软删/归档/恢复不纳入 T2 本版；彻底删除不强制数据库物理 DELETE；区分绑定移除、聚合删除、业务 TrackerItem 删除。 | 用户确认（2026-11-11，T2 K1） | 业务 Item 软删见 D16 |

## 合并决策优先（已确认业务政策）

用户选择合并后，无论接收方发生什么变动，均以用户合并决策为准，不因接收方变化提示冲突；适用于直接父子双向的新增、删除、修改。目标业务 BC 仍整体校验并决定提交；拒绝时保留选择与结果，该条未完成。源字段值按执行时读取的最新值处理。验证见[合并删除覆盖接收方修改](../scenarios/core-scenarios.md#合并删除覆盖接收方修改)。

## Hypothesis

| 编号 | 假设 | 关联/验证 |
| --- | --- | --- |
| `HYP-001` | Working Set 更像版本化范围清单/工作视图，而非 Git Branch。 | 已由 `FACT-001` 与 `QUESTION-001` 关闭支撑；Manifest 仅作为规模候选 |
| `HYP-002` | 单个 Tracker 独立性与整个谱系独立生命周期应分开。 | D-001/D-002；谱系不设聚合仍为设计推论 |
| `HYP-003` | ItemVariant 可能是承载身份、固定来源和物化状态的领域根。 | 已由 D-010/D-012 排除；LightTrackerItem 承担 |
| `HYP-004` | 早期 namespace/type/CommonId 复合家族键的担忧已被全局家族唯一性排除。 | `FACT-005` |
| `HYP-005` | History/Snapshot 是 COW、Compare 和审计的共同上游能力。 | `QUESTION-006` |
| `HYP-006` | Compare & Merge 应是独立模型边界，通过业务对象公开能力写目标。 | T1/T4 |
| `HYP-007` | Reference 与 Association 需要不同生命周期和缺端点语义。 | `QUESTION-010` |
| `HYP-008` | 超大 Working Set 可能需要 Manifest revision/分片准备，而不是加载全部 Binding 的单聚合。 | `QUESTION-012` |

## Technical Constraint／实现事实

| 编号 | 陈述 | 处理 |
| --- | --- | --- |
| `TC-001` | 默认根物理 ID 固定为 `1`。 | 部署/实现约定，不进入业务不变量 |
| `TC-002` | 采用单聚合提交 + 可靠事件，不使用跨聚合大事务。 | 架构选择；由可见性与恢复要求验证 |
| `TC-003` | 现有业务内容可能采用覆盖式表结构，历史读取能力有限。 | 历史能力盘点；T3 快照表已提供最小证据 |
| `TC-004` | 旧实现存在 Long 主键、旧 branch 字段、SDK/Biz 分层。 | 只作为迁移证据，不定义领域身份 |
| `TC-005` | 事件可能至少一次投递，投影存在延迟。 | 幂等键、因果序列、最大延迟 |
| `TC-006` | `pre-design/` 历史材料存在失效相对链接。 | 不影响语义；导航以当前文档为准 |

## 与历史材料的边界

`pre-design/` 中的 V4 模型是历史设计输入，不是当前基线。V4 的 Manifest/ItemVariant/Tracker Branch 等概念已被 D-001/D-010 排除或改由现行模型承担；保留原文用于追溯，不据此新增当前字段或聚合。
