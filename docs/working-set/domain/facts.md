# Working Set 候选事实、政策、假设与技术约束

**状态：Draft（混合知识登记）；已接受条目保持 Accepted，权威归档见 [事实与政策主题基线 v1](facts-policy-baseline-v1.md)。**

来源代号见 [证据登记](evidence-register.md)。已有逐条用户确认的事实、政策及组织架构约束；其余历史陈述仍待确认。文档保持 Draft，限定确认范围及候选首版清单见 [阶段 3.1 收敛](../reviews/2026-11-09-stage-3-1-rule-convergence.md)。

## Organization / Architecture Constraint（Confirmed）

| 编号 | 约束 | 状态 | 直接依据 | 固定范围与仍可设计范围 |
| --- | --- | --- | --- | --- |
| `AC-001` | Working Set 是企业 Shared Kernel 的组成能力，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 同时包含 Privilege、Workflow、Class Hierarchy 等共享能力。 | Confirmed | `USER-ARCH-2026-11-09`：用户明确称其为外部集体确定、当前无法动摇的大基调；两张附件图与文字一致 | 固定：Working Set 不移出 Shared Kernel。开放：内部领域模型、聚合边界、模块、事务、对外契约、与业务 BC 及其他共享能力的局部协作。 |

`AC-001` 不是 Accepted ADR，也不证明 Working Set 是单一聚合、单一 BC 或微服务。

## Business Fact（逐条标注确认范围）

| 编号 | 陈述 | 确认状态 | 来源 | 关联／验证方式 |
| --- | --- | --- | --- | --- |
| `FACT-001` | 历史资料把 Working Set 描述为选择 Tracker 并形成有效工作范围，而非拥有全部 Item。 | Reported / Pending | `EXT-ARCH §0`、`V4-UL:10` | 访谈工作集创建、发布、归档的业务负责人；关联 `QUESTION-001` |
| `FACT-002` | SHARED Tracker 及其 Item 的新增、修改实际归属默认工作集，所有已绑定工作集同时可见，不产生子工作集独有副本。 | 用户已确认 | 本对话用户对共享修改归属的明确说明 | [SCN-002](../scenarios/core-scenarios.md#scn-002-引用-shared-tracker)；权限与可见性实现继续设计 |
| `FACT-003` | 非共享 Tracker 派生产生新的独立业务身份；sourceId 指向直接父级 Working Set 下的来源 Tracker，逐级追溯，不跳过中间工作集。parentId 另表达同工作集直属结构父子关系，不属于派生维度。 | 用户确认 D-002 身份事实；D-003 澄清双维语义 | 用户 2026-11-10 D-003 正式裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-003) | SCN-013/014；修改规则、组织树继承、同源多派生及 Item 边界待定 |
| `FACT-004` | VIRTUAL Item 在没有本地业务内容行时仍被描述为已存在的逻辑条目。 | Reported / Pending | `V4-DOMAIN:214/547`、`EXT-ARCH §1` | 以查询、权限、删除和引用场景验证；`QUESTION-004` |
| `FACT-005` | 历史材料使用 CommonId 关联同族变体、LocalId 标识具体变体，并要求实例化不更换 LocalId。 | 部分 Accepted：R05 身份关系；实例化稳定性待验证 | `V4-OVERVIEW:5`、`V4-DOMAIN:19/30` | 派生与回并的等值关系已由用户确认，见 [身份规则](glossary.md#item-身份规则用户已确认)；CommonId 家族唯一性及雪花 ID 已由用户确认，实例化身份稳定性继续验证；`QUESTION-004` |
| `FACT-006` | 派生内容读取被要求固定到可回读的 Source Cut，而不是跟随来源最新值。 | Accepted：R07/R19 固定派生内容含附件关系；保留协议待设计 | `V4-DOMAIN:438/463`、`EXT-ARCH §3.1` | 证明历史、附件、关系及删除态均可按切点回读；`QUESTION-006` |
| `FACT-007` | 直接父子工作集支持双向比对、合并；任一方向的新增、删除、修改均可经用户选择应用到接收方。source/target 是本轮角色，不固定为父或子。 | 用户已确认 | 本对话用户明确说明父子双向新增、删除、修改的接收效果 | [双向合并场景](../scenarios/core-scenarios.md#直接父子双向合并新增删除修改)；目标业务 BC 校验并提交 |
| `FACT-008` | Merge 数据选择、实际 Apply Result 与处理确认被描述为不同事实。 | 部分 Accepted：R08—R11；DEFER 完整条件仍候选 | `MERGE §4.2-4.4`、`EXT-ARCH §1/10` | 用 `KEEP_TARGET/DEFER` 四组合验收；`QUESTION-008` |
| `FACT-009` | Reference 可在逻辑目标有效但物理端点暂不可解析时保持有效悬空状态。 | Reported / Pending | `REFERENCE CONTEXT:14/25`、`REFERENCE 05 §4` | 访谈用户可见语义及删除守卫；`QUESTION-010` |
| `FACT-010` | 创建、拓展、替换和合并被设计为可恢复长流程，受理成功不等于业务结果已可用。 | 部分 Accepted：R16/R17 GateFlow 替换；其他长流程仍候选 | `EXT-ARCH §7.3`、`V4-LRT §3-9` | 用重启、取消、半批失败场景验证；关联 `INV-011` |
| `FACT-011` | 当前架构示意把 `create / extend / resolve / diff / merge` 画作业务 BC 调用 Working Set 的统一 API 面。 | Reported / Pending | `USER-ARCH-2026-11-09` 附件 `image(1).png` | 确认这些是稳定业务能力、现有 API 名称还是示意占位；关联 `QUESTION-013` |
| `FACT-012` | 工作集领域统一使用 TrackerItem 作为公共 Item 契约；Part、Requirement 等业务对象是各自业务 BC 的完整独立聚合，通过实现 TrackerItem 接口获得公共工作集能力。Working Set 不拥有业务对象聚合，只负责公共 TrackerItem 身份与工作集机制。 | 用户确认 D-007 | 本对话用户 D-007 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-007) | LightTrackerItem 聚合边界、COW 事务协议仍候选；QUESTION-002.h 关闭 |
| `FACT-013` | LightTrackerItem 是 Working Set 专属独立聚合，非查询投影；派生采用 Copy-on-Write，不立即物化完整业务对象副本，而是创建 LightTrackerItem、分配当前变体 LocalId、保留 CommonId，记录 SourceLocalId/SourceRevision，经 TrackerId/WorkingSetId 指向，承担身份与有效变体定位权威。业务 BC 通过 CQRS Query 与工厂重建完整业务聚合，调用方不感知底层物化状态；Working Set 不拥有或重建业务 BC 完整领域状态。 | 用户确认 D-008 | 本对话用户 D-008 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-008) | LocalId 是否等同业务聚合变体身份、首次物化标志/内容指针、同步原子性、SourceRevision 版本选择仍候选 |
| `FACT-014` | 来源变更触发 COW 物化采用事件驱动机制：业务聚合普通内容修改不自动升级 Revision（Revision 升级是独立显式行为）；来源修改前先 Query 获得修改前完整业务事实，修改并保存后发送来源变更事件，触发受影响派生变体的 COW 物化；物化 Handler 据修改前事实形成派生变体自身完整业务聚合并保存，成功后发送物化完成事件；LightTrackerItem 索引 Handler 收到后标记已物化，有效内容来源定位到自身 LocalId 及自身有效版次。Working Set 负责公共身份、来源关系与有效内容定位，业务 BC 负责完整业务聚合构造、业务不变量与保存。 | 用户确认 D-009（机制）；事件与事务可见性协议未裁定 | 本对话用户 D-009 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-009) | 修改前事实的可靠保存/稳定引用、查询可见性、来源追溯、重复事件与并发仍候选 |
| `FACT-015` | COW 采用异步事件驱动、只保证最终一致；来源修改前事实作为事件关联的不可变内容快照与来源变更事件一起传递；来源修改已保存、COW 未完成期间允许派生工作集暂时读到修改后内容，物化完成后以修改前事实形成独立内容；不要求跨 BC 事务。领域模型围绕 Working Set、Tracker、LightTrackerItem 三类核心聚合收敛，不再引入 AP-003 的 Manifest、ItemVariant；业务 BC 对象仍独立聚合，TrackerItem 仍是公共接口。 | 用户确认 D-010 | 本对话用户 D-010 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-010) | SourceLocalId 字段是否拆分、连续修改首次快照业务选择规则、快照序列化/交付/持久化仍候选 |
| `FACT-016` | 首次快照优先：对尚未物化的派生 TrackerItem，来源首次触发 COW 的修改时，修改前完整业务事实（事件关联的不可变快照）确定目标变体最终物化内容；同一来源后续连续修改不覆盖首次快照；快照选择按来源业务修改的实际先后顺序而非事件到达顺序（E2 先于 E1 到达仍采用第一次修改前内容）；同一目标变体不因重复事件产生多个有效物化结果；Local-backed 后来源后续事件不覆盖本地内容。 | 用户确认 D-011 | 本对话用户 D-011 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-011) | Source Change Order 用并发 version 还是独立序列、多级派生目标识别、Materialization Claim 证据保留仍候选 |
| `FACT-017` | LightTrackerItem 的 SourceLocalId 表示当前有效内容来源的 LocalId，SourceRevision 表示当前有效内容来源的业务 Revision；SourceVersion 为表述错误，统一为 SourceRevision。未物化（Source-backed）时 SourceLocalId/SourceRevision 解析来源内容；COW 完成后 SourceLocalId=LocalId、SourceRevision=Revision，有效内容定位指向自身；COW 不改变 LocalId、CommonId。R0→R1 历史派生关系不要求由 LightTrackerItem 保存；不新增 OriginLocalId、ItemVariant、Manifest 或谱系聚合。 | 用户确认 D-012 | 本对话用户 D-012 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-012) | 历史谱系承载方式已确认不需要（D-018）、多级事件传播已由 D-013/D-017 闭合 |
| `FACT-018` | 多级派生采用直接有效来源定位：LightTrackerItem 的 SourceLocalId 直接指向实际拥有完整业务内容的变体，不采用对未物化中间变体逐级递归解析；直接上游已物化则指向该上游，未物化则继续向上找最近实际拥有完整内容的上游。COW 依据 SourceLocalId 发现所有依赖该实际来源的未物化 LightTrackerItem，即使分布在不同层级 Working Set 也都需物化（扇出）；目标物化后 SourceLocalId=LocalId、SourceRevision=Revision。 | 用户确认 D-013 | 本对话用户 D-013 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-013) | 来源修改与派生并发、事件延迟/乱序的首次来源确认、依赖者集合确定时机、上游新物化节点的重新定位、SourceRevision 可变头版次派生基准仍开放 |
| `FACT-019` | 派生完成后，已存在的未物化 LightTrackerItem 不因中间上游变体物化而自动重定向 SourceLocalId；SourceLocalId 不需在生命周期内始终动态指向最近已物化祖先。只有当前变体自身完成 COW 后才切换 SourceLocalId=LocalId、SourceRevision=自身 Revision。当前有效来源关系与 Working Set 派生层级是两个不同维度；来源修改时基于当前有效来源依赖关系识别需物化变体，而非递归遍历工作集后代。新派生变体仍按 D-013 选择 SourceLocalId。 | 用户确认 D-014 | 本对话用户 D-014 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-014) | 并发派生时间边界仍开放 |
| `FACT-020` | Working Set 派生不复制完整业务对象数据，仅创建 LightTrackerItem 建立身份、工作范围关联与有效内容来源引用（LocalId/CommonId/SourceLocalId/SourceRevision）。派生必须选择时间基准（命名 Snapshot 或 Head，Head 是派生请求确定的时间点而非动态查询时刻），系统按该时间点选择之前适用的最大业务 Revision；派生时不等待来源停止变化、不因并发修改先完成而改变已选时间基准。 | 用户确认 D-015 | 本对话用户 D-015 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-015) | 同一 Revision 内时间基准与具体内容状态的关系（SourceRevision 相同但内容已变）保持 Open |
| `FACT-021` | 派生仅固定业务版次，不复制或固定完整业务数据；命名 Snapshot/Head 时间基准只确定来源适用的业务 Revision。完整派生操作完成（Derivation Completion）是新派生 LightTrackerItem 开始受到来源变更 COW 保护的业务边界：派生完成前来源修改不为该变体触发 COW，派生完成后首次相关来源修改触发 COW（据修改前快照物化）。D-011 首次快照优先限定为派生完成之后首次需要保护的相关来源修改。上述「派生完成前后」时间边界已由 D-017 修订：COW 目标改为在事件 Handler 消费时按当前有效引用关系确定，不再比较派生完成与来源修改的先后（见 FACT-022）。 | 用户确认 D-016 | 本对话用户 D-016 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-016) | 完整派生完成与来源修改实际先后的判定协议、场景 C 竞态仍候选（COW 保护资格时间边界现由 D-017 修订） |
| `FACT-022` | COW 目标集合在事件 Handler 实际消费来源变更事件时确定：Handler 查询当前有效内容来源指向本次修改来源（SourceLocalId/SourceRevision 匹配）且尚未物化的 LightTrackerItem，查询命中的都参与本次 COW 物化；不在来源修改开始时提前固定目标集合，不追溯比较修改开始时间与完整派生完成时间，不引入跨聚合全局时序协调机制。 | 用户确认 D-017 | 本对话用户 D-017 裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-017) | D-011 首次快照优先的乱序/幂等实现细节留待实施评审 |
| `FACT-023` | 事件顺序由事件框架顺序记录、顺序消费兜底，Source Change Order 的先后判定不是领域模型关注细节；LightTrackerItem 自身持有 Revision 字段，与 SourceRevision 不同步（派生初期 SourceRevision 可为来源 Revision、自身 Revision 为初始值），业务 BC 的 TrackerItem 实现也持有 Revision，两者通过事件同步；Revision 显式升级触发依赖变体 COW。 | 用户确认 D-018（整体验证收敛） | 本对话用户整体验证裁定；[评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-018) | 事件同步细节、幂等去重留待实施 |
| `FACT-024` | ProductSpace 是 Shared Kernel 独立聚合；在默认 WorkingSet 中维护生命周期，但可关联多个 WorkingSet；本身不随 WorkingSet 派生复制或迁移；Tracker 属于一个 ProductSpace。WorkingSet 关联 ProductSpace，空间绑定独立于 Tracker 绑定。 | 用户确认（2026-11-10 补充） | 本对话用户裁定 | 创建初始化、绑定唯一性、移除协议 |
| `FACT-025` | 系统有三层分类元模型，元数据在启动前初始化到平台元数据库、常驻内存；Tracker 是 Classification 在某 ProductSpace/WorkingSet 中的具体投影，遵守其属性/共享策略/规则约束；一个 Classification 可对应多个 Tracker；Tracker 不随元数据初始化自动创建，而在创建 ProductSpace 时按分类集合投影生成。 | 用户确认（2026-11-10 补充） | 本对话用户裁定 | 分类版本/变更协议仍待 |
| `FACT-026` | 默认 WorkingSet 由平台元数据预初始化，固定 ID=1，不可删除/隐藏；用户创建子 WorkingSet 前，业务数据均在默认 WorkingSet；Tracker 只能在默认 WorkingSet 创建原始聚合，子 WorkingSet 只能通过纳入直接上游 Tracker（共享绑定或非共享派生）维持来源链完整。 | 用户确认（2026-11-10 补充） | 本对话用户裁定 | 默认 WorkingSet 归档等生命周期维度待定 |
| `FACT-027` | Diff/Merge 衔接裁定：① 未物化变体可以参与 Diff；② 未物化目标被 Merge 修改时，在 merge 过程中构建业务聚合正常 save（物化与修改一体）；③ 合并决策是用户决策、command 入参，可作为 ValueObject 归属 MergeRound 聚合；④ MergeCheckpoint 是独立聚合；⑤ MergeRound 是合并载体（LRO 发起后记录参数、进度、成功/失败信息）；⑥ 共享 Tracker 不参与 Merge，非共享只允许直接父子准入。 | 用户确认（2026-11-10 阶段 4 补充） | 本对话用户裁定 | 差异冻结/令牌、幂等与乱序协议仍候选 |

## Business Policy（逐条标注确认范围）

| 编号 | 当前历史主张 | 状态 | 来源 | 为什么不是不变量／如何验证 |
| --- | --- | --- | --- | --- |
| `POL-001` | 每个治理范围只有一个默认根且普通用户不能删除。 | Pending | `V4-OVERVIEW:7`、`EXT-ARCH §1` | 业务治理选择；物理 ID=1 另列 `TC-001` |
| `POL-002` | 子工作集不允许新建 Tracker。历史材料另主张 Tracker 只能由默认根创建。 | 子工作集禁止创建已由用户确认；其他创建入口范围待定 | 本对话用户明确说明；历史 `V4-DOMAIN:394-398` | 区分创建 Tracker 与在已有 Tracker 下创建 Item |
| `POL-003` | 分类配置的共享特性为固定出厂设定，不允许修改；Tracker 的共享特性在创建时确定，后续不允许修改。分类配置为共享时只能创建共享 Tracker；分类配置为非共享时，创建时可选择共享或非共享。 | 用户已确认 | 本对话用户明确确认创建选择规则、创建后不可变及分类共享特性为固定出厂设定 | [创建验收场景](../scenarios/core-scenarios.md#tracker-共享特性的创建约束)；分类共享特性变更问题已关闭，不以是否已创建 Tracker 为条件；不推导其他分类属性的变更规则或允许迁移 |
| `POL-004` | 比对、合并只允许直接父子工作集之间双向进行，支持新增、删除、修改，作用于支持这些能力的对应非共享 Tracker。 | 用户已确认直接父子准入范围 | 本对话用户明确答复；历史 `MERGE:42/841` | 兄弟及跨代不允许直接比对/合并；GateFlow 历史内容替换适用独立规则 |
| `POL-005` | 同一个 Working Set 有效范围内 CommonId 唯一，同一家族只允许一个有效变体。 | Accepted（阶段 3.1 用户裁定） | 用户 2026-11-09 明确确认；历史 `V4-DOMAIN:425-427` | SCN-011；QUESTION-004 仅保留分配、去重及并发协议 |
| `POL-006` | （已被 2026-11-10 最新规则修订）Tracker 加入子工作集后按新规则可移除：Shared Tracker 移除=解除 TrackerBinding（非删除聚合）；非共享 Tracker 可删除但须无派生子级且为空（叶子）。 | 已修订 | 本对话用户 2026-11-10 最新业务规则 | 详见 POL-014；原「禁止移除」结论不再生效，不能两套规则同时成立 |
| `POL-007` | 子工作集可在已有非共享 Tracker 下新建 Item；共享 Tracker 下通过子工作集入口新建的 Item 实际在默认工作集创建。 | 用户已确认 | 本对话用户明确答复，细节见 [创建场景](../scenarios/core-scenarios.md#子工作集新建-item-与-tracker-边界) | 回并父工作集创建对应 Item 已获用户确认，见 [回并场景](../scenarios/core-scenarios.md#子工作集新增-item-回并父工作集)。CommonId/LocalId 等值关系已确认，标识分配机制待定；`QUESTION-005` |
| `POL-008` | SHARED 修改对所有已绑定工作集同时可见；具体读取面的实现与验收指标待设计。 | 业务要求已确认；技术指标待定 | 本对话用户明确确认“所有工作集同时可见” | 不得自行改为允许业务可见延迟；`QUESTION-003` |
| `POL-012` | 必须先有目标 WorkingSet 的 SpaceBinding 才能建立 TrackerBinding；同一 WorkingSet 同一 ProductSpace 最多一个有效 SpaceBinding；同一 WorkingSet/ProductSpace/Classification 最多一个有效 TrackerBinding；Tracker.productSpaceId 必须等于绑定空间 ID；一个 Tracker 不在同一 WorkingSet 重复纳入。 | 用户确认（2026-11-10 补充） | 本对话用户裁定 | 绑定生命周期协议、SLA 待定 |
| `POL-013` | Shared Tracker 纳入子 WorkingSet 复用原 TrackerId、仅建新绑定；非共享 Tracker 纳入创建新 Tracker 聚合、建立直接 sourceId；非共享成功移除后可重新纳入、重新派生新身份；移除空间内最后一个 Tracker 不移除 SpaceBinding；ProductSpace 移出是独立显式操作。 | 用户确认（2026-11-10 补充） | 本对话用户裁定 | 纳入/移除事务与并发协议待定 |
| `POL-014` | 删除/移除语义：共享 Tracker 从子 WS 移除仅解除 TrackerBinding；非共享 Tracker 无 TrackerItem 且无派生子 Tracker → 删除并解除绑定；非共享有派生子 Tracker → 不允许删除；任意 Tracker 有 TrackerItem → 不允许移除；非共享有组织子节点但无派生子节点 → 不允许删除（校验提示含子节点）；移除空间内最后一个 Tracker 不触发空间移除（SpaceBinding 保留）；移除 ProductSpace 时仍有 TrackerBinding → 抛异常，有 Tracker 不允许移除空间绑定；默认 WorkingSet 下共享 Tracker 空且无子时可删除。 | 用户确认（2026-11-10，修订 POL-006） | 本对话用户裁定 | 软删/彻底删除机制待定 |
| `POL-015` | WorkingSet 负责维护当前工作集中的空间与 Tracker 绑定结构；SpaceBinding、TrackerBinding 是两个 Entity，归属于 WorkingSet 聚合。ProductSpace、Tracker 只维护自身稳定归属身份，不持有绑定。 | 用户确认（2026-11-10） | 本对话用户裁定 | 绑定 Entity 内部结构待定 |

## POL-009：非共享派生组织裁剪（用户已确认）

来源：用户 2026-11-10 D-004 正式裁定。派生时仅选择部分 Tracker，若原结构父未带入目标工作集，允许新 Tracker.parentId=null，使其成为目标根；不强制自动派生未选择的父 Tracker。不同 Working Set 可拥有不同 Tracker 组织树，parentId 的组织结果不改变 sourceId 的直接派生来源含义。

例：WS0 中 B.parentId=A，仅选择 B 派生 WS1；产生独立身份 B1，B1.parentId=null，B1.sourceId=B，不创建 A1。此为已确认合法结果，不补造所有输入下唯一的裁剪算法，也不授权派生后的任意移动。适用于非共享派生，Shared 及混合树有效父解析仍待裁定。原父也被选择的处理、同源多派生数量和整批原子性均不由本政策决定。验证见 [SCN-015](../scenarios/core-scenarios.md#scn-015-非共享-tracker-派生组织裁剪)。

## Hypothesis（Stage 2 inference）

| 编号 | 假设 | 依据 | 关联与验证 |
| --- | --- | --- | --- |
| `HYP-001` | Working Set 更像版本化 Manifest／工作视图，而非 Git Branch。 | `FACT-001`、SHARED/UNSHARED 可混合 | 用 10 个核心场景验证其解释力；`AP-003` |
| `HYP-002` | 历史假设混合了单个 Tracker 独立性与整个谱系的独立生命周期。 | D-001/D-002 已确认单个 Tracker 独立聚合及非共享派生新身份；不确认整个谱系是聚合 | 谱系按身份引用、Lineage 不单独设根仍是设计推论；AP-002/003 不自动获批 |
| `HYP-003` | ItemVariant 可能是承载身份、固定来源和物化状态的领域根，而非纯索引。 | `FACT-004/005/006` | 判断这些状态是否具有业务生命周期；`QUESTION-004/006` |
| `HYP-004` | 历史候选曾因担心跨类型重号而提出 namespace/type/CommonId 复合家族键。 | 已被用户确认的全局家族唯一性排除其重号前提 | CommonId 足以标识逻辑家族，类型可用于路由或校验；见 [身份规则](glossary.md#item-身份规则用户已确认) |
| `HYP-005` | History/Snapshot 是 COW、Compare 和审计的共同上游能力。 | `FACT-006` | 做历史可读性 spike；`QUESTION-006` |
| `HYP-006` | Compare & Merge 应是独立模型边界，通过业务对象公开能力写目标。 | `FACT-007/008` | 验证各业务对象字段规则与失败语义；`QUESTION-008` |
| `HYP-007` | Reference 与 Association 需要不同生命周期和缺端点语义。 | `FACT-009` | 业务访谈关系方向、删除、恢复；`QUESTION-010` |
| `HYP-008` | 超大 Working Set 需要 Manifest revision／分片准备，而不是加载全部 Binding 的单聚合。 | `EXT-ARCH §5.1/11.18` | 收集 Binding 规模和并发指标；`QUESTION-001` |

## Technical Constraint／现有实现事实

| 编号 | 陈述 | 状态 | 来源 | 处理 |
| --- | --- | --- | --- | --- |
| `TC-001` | 默认根物理 ID 被 V4 固定为 `1`。 | Reported implementation convention | `V4-OVERVIEW:7` | 不进入业务不变量；确认部署兼容需求 |
| `TC-002` | V4 偏好单聚合提交加可靠事件，不使用跨聚合事务。 | Architecture constraint/proposal | `V4-CONSISTENCY:3-13` | 需由可见性与恢复要求验证，而非反向定义业务 |
| `TC-003` | 现有业务内容可能采用覆盖式表结构，历史读取能力未知。 | Pending technical fact | `EXT-ARCH §6 B/12 P0` | 做历史、附件、关系、墓碑能力盘点 |
| `TC-004` | 旧实现存在 Long 主键、旧 branch 字段、SDK/Biz 分层等结构。 | Reported legacy constraint | `EXT-ARCH §3.3`、`V4-OVERVIEW:38` | 只作为迁移证据，不定义领域身份 |
| `TC-005` | 事件可能至少一次投递，投影存在延迟。 | Architecture assumption | `REFERENCE 05 §1/5` | 明确幂等键、因果序列和最大延迟 |
| `TC-006` | `pre-design/` 内存在大量失效相对链接。 | Observed repository condition | 阶段 1/2 链接检查 | 不影响语义，但降低历史证据可导航性 |

## 合并决策优先于接收方变动（已确认业务政策）

用户明确确认：选择合并后，无论接收方发生什么变动，均以用户的合并决策为准，不因接收方变化提示合并冲突。该政策适用于直接父子双向合并的新增、删除、修改。例如子侧删除 Requirement 后父侧又修改它，用户选择向父侧合并删除，仍执行删除，不要求因父侧新修改重新确认。

本政策决定变化竞争中的业务优先级，沿用此前已确认的目标业务 BC 整体校验及提交职责；业务规则拒绝仍保留未完成结果。执行协议应兑现该优先级，不把内部乐观锁竞争直接升级为要求用户重新裁定的合并冲突。实际结果与失败仍须如实记录，不能把未提交操作记为成功。源字段值继续按已确认政策在执行时读取最新值。

验证见 [合并删除覆盖接收方修改](../scenarios/core-scenarios.md#合并删除覆盖接收方修改)。

## Tracker 加入子工作集后禁止移除（历史政策，已被 2026-11-10 修订）

原政策（POL-006 旧版）：Tracker 一旦加入某个子工作集，无论共享还是非共享，后续均不允许移除。**该结论已被 2026-11-10 最新业务规则修订**：Shared Tracker 移除=解除 TrackerBinding（非删除聚合）；非共享 Tracker 可删除但须无派生子级且为空（叶子）。见 POL-014、SCN-052。本节保留为历史，不再生效。

阶段 3.1 用户已批准 R01—R17 及限定术语，接受范围以 [BASELINE](../BASELINE.md) 为准；本页其余候选内容仍 Draft。FACT-006 的派生历史覆盖现已明确包含派生时的附件与关系状态（用户 2026-11-09 直接裁定，SCN-012）；保留期限、读取和删除守卫协议仍待设计，不推导永久保留。

## D-002 的确认范围（历史记录，双字段合称追溯的解释由 D-003 澄清）

用户 2026-11-10 明确确认：非共享派生建立新的独立 Tracker，源与派生是不同领域对象，追溯层级通过 `parentId`、`sourceId` 表达。Tracker 拥有自己的状态、生命周期及管理行为，Working Set 不拥有其完整生命周期；D-001 的独立聚合及停用 Tracker Branch 规则继续有效。Shared Tracker 的 FACT-002/POL-008 完全不变。

`parentId` 不先解释为直接父，`sourceId` 不先解释为最初来源；不自动确认多级派生、谱系可修改、同谱系共存或 Item 归属。FACT-003 原历史陈述中的 Item 变体部分不借本次 Tracker 裁定整体升级，既有 Item 身份裁定仍按原范围有效。来源和影响见 [阶段 3 D-002](../reviews/2026-11-09-stage-3-architecture-review.md#d-002)。

POL-002 的禁止子工作集新建 Tracker 与本次派生产生新身份分别保留：前者是创建入口政策，后者是派生结果；不从派生结果推导开放任意原生新建入口。具体用例分类与权限协议待设计。

## D-003 当前双维关系语义

用户明确确认：Tracker.parentId 是同一 Working Set 内的直属结构父节点；Tracker.sourceId 是直接父级 Working Set 下的来源 Tracker，沿直接派生链逐级指向，不跨越中间工作集。两者分别表达组织树和派生链，不互相替代。用户的 WS0→WS1→WS2、T0←T1←T2 示例同时关闭“仅允许一层派生”的旧选项。

D-002 身份独立与生命周期裁定继续有效；其将两个字段一起描述为派生追溯的分析不再是当前解释。Shared 权威复用不变；结构有效性按工作集的有效组织范围检查，不能只比较数据库归属字段。Shared 在多个工作集是否必须具有一致结构父节点尚未裁定。见 [D-003 图](../diagrams/tracker-dual-relations.md) 与 [规则分类](invariants.md#d-003-双维关系约束审查)。

## POL-010：默认继承与创建前显式重组（D-005，用户已确认）

非共享派生时原父也被选择，默认继承已选节点间的组织关系；用户可在创建完成前显式指定新的合法组织结构，包括使子节点为根。未选原父仍适用 POL-009，不自动补派生。两种组织结果均不改变直接 sourceId。来源：本轮 D-006 正文第 3 条直接重申，以及引用对话第 5 题选择 C；证据见 [D-005 补录](../reviews/2026-11-09-stage-3-architecture-review.md#d-005)。默认政策、显式重组能力和最终结构合法性分别成立，不等于创建后允许修改。

## POL-011：创建完成后组织位置不可变（D-006，用户已确认）

Tracker 新建时确定组织位置，parentId 可为 null。创建完成后 parentId 不可修改，不支持移动、更换父节点或调整为根；允许的裁剪与显式重组须在创建完成前确定。来源为本轮用户 D-006 八项裁定，见 [记录](../reviews/2026-11-09-stage-3-architecture-review.md#d-006)。

此限制只针对组织位置，不冻结 Tracker 的其他业务状态、身份生命周期或 Working Set 的成员扩展；也不扩大 POL-002 的原生创建入口。sourceId 直接来源语义不变，但不由此自动批准其所有生命周期可变性规则。父后续删除/停用的子节点处理仍待裁定，不能自动清空 parentId、级联删除或禁止父删除。

## D-007：TrackerItem 契约与 LightTrackerItem 候选定位

已确认（用户 D-007 裁定，见 [评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-007)）：工作集领域统一使用 TrackerItem 作为公共 Item 概念，不继续将 Item 作为独立于 TrackerItem 的通用聚合模型；Working Set Shared Kernel 声明顶级 TrackerItem interface；Part、Requirement 等是各自业务 BC 的完整独立聚合，通过实现该接口获得参与 Working Set 管理的公共能力；Working Set 不拥有业务对象状态、业务生命周期与业务不变量，只负责公共 TrackerItem 身份与工作集机制；Tracker 仍是独立聚合，Shared Kernel 定位不变。

候选（Draft）：LightTrackerItem 作为专属领域聚合管理轻量化索引信息，其完整聚合边界、内部属性与领域行为，是否拥有有效变体及版本定位的权威状态，以及 CommonId/LocalId/Revision/Version 在 TrackerItem 契约与 LightTrackerItem 之间的分配，均未确认。不得因已有索引表就判定 LightTrackerItem 必为聚合根，也不否定候选聚合定位。跨 BC 首次 COW 的事务与一致性协议仍候选。
