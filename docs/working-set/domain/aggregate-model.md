# Working Set 候选聚合边界分析

**状态：Draft**

聚合是事务一致性边界，不是页面装载范围或外键集合。本页不选最终方案；完整竞争方案见 [提案比较](../proposals/aggregate-boundary-options.md)。候选不变量见 [invariants.md](invariants.md)。各方案的领域类图和聚合边界图将在相应方案进入正式战术评审时按 [图表渐进交付规则](../diagrams/README.md) 分别创建，不在当前证据不足时一次性补齐。

## 核心概念分析

| 概念 | 独立身份／生命周期 | 候选内部不变量 | 事务与并发关注 | 反例／未决问题 |
| --- | --- | --- | --- | --- |
| Working Set | 有长期身份；准备、发布、归档等状态维度尚未确认 | 发布 revision 与当前范围一致；发布／取消不互相覆盖 | 巨量 Binding 会造成加载与版本热点 | 改名不应加载十万 Item；`QUESTION-001/012` |
| Tracker | D-001/D-002：独立聚合及非共享派生新身份 | D-003：parentId 为同工作集结构父，sourceId 为直接父级工作集来源；禁止跳级 | 结构范围校验与来源准入分别分析；不组合整树或整链 | D-006 已禁止创建后 parentId 修改；Shared 父级一致性、sourceId 可变性、同源多派生及 Item 归属待定 |
| TrackerItem（公共契约） | 无自身聚合；是 Working Set 与业务 BC 的公共 Item 能力接口（D-007） | 公共身份与工作集语义，不拥有业务状态 | 跨 BC 协作的公共语言，不承载业务不变量 | CommonId/LocalId/Revision/Version 的分配待定 |
| LightTrackerItem（已确认独立聚合） | Working Set 专属独立聚合（D-008）；身份、来源与有效变体定位权威 | 当前 LocalId、保留 CommonId、SourceLocalId/SourceRevision、自身 Revision（D-018，与 SourceRevision 分离）、`materialized` 物化状态（K1′）、TrackerId/WorkingSetId | 与业务对象身份一致性、双事实收敛、物化并发 | 属性集与物化协议见 [T3 契约](lighttrackeritem-materialization-contract-v1.md)（已冻结）；非查询投影 |
| Business Item | 各业务上下文独立聚合（Requirement/Part/Process 等），实现 TrackerItem | 内容、状态机、revision、字段组合合法性 | Merge/COW 必须走其公开写能力 | 不能用通用字段 Map 绕过规则；SK 不拥有其完整状态 |
| Merge Round | 轮次身份和可恢复生命周期 | 命令冻结、状态转换、进度与唯一终态结果 | 不持有所有业务对象；部分成功需保留 | 把整轮设为大事务会掩盖已提交结果 |
| Merge Checkpoint | 有向 source-target-family 键候选 | 确认／撤销因果单调、证据关联 | 旧事件不能覆盖新决定 | `KEEP_TARGET + true` 内容仍不同 |
| Reference | 关系身份持续跨版次 | 逻辑目标、有效性、钉版、修复不换身份 | 修复与删除竞态；每关系独立提交候选 | 右侧为空不等于无引用 |

## 跨聚合规则

- LocalId、ItemFamilyKey、Binding 占用和有向 Checkpoint 的并发唯一性无法只靠单聚合内存检查；领域需要定义键与冲突语义，持久化负责兑现。
- 是否“一次事务只改一个聚合”应由 `INV-*` 的原子可见性要求决定。当前 `TC-002` 只是 V4 架构候选。
- 业务查询可以组合多个聚合或投影，但不得把组合结果保存回命令侧当作部分聚合。

## 方案结论状态

- `AP-001`、`AP-002`、`AP-003` 均可解释部分业务事实，当前没有 Accepted 方案。
- 若 Working Set 规模小且发布需要整体原子，`AP-001` 更简单。
- 原 `AP-002` 的独立 Branch 根与 D-001 不兼容；修订建议仍 Draft，不自动成为获批方案。
- 若既要混合共享／派生又要支持巨大范围与稳定历史，`AP-003` 更有解释力，但双事实和历史成本最高。
- 最终选择被 `QUESTION-001/002/004/006/012` 阻塞。

## 阶段 3：逐概念一致性审查

以下除 D-001 已确认的 Tracker 独立聚合地位外，均为候选。表中根/值的选择对应 [三方案图](../diagrams/aggregate-candidates.md)；业务规则的已接受范围见 BASELINE；不等于本表的聚合与事务列整体获批。各根写入必须在提交期检查自身版本和生命周期；权限由各责任所有者重校验，读取统一区分未纳入、不存在、已删除、无权、历史不可用和准备中。Source Cut 缺失不能降级为当前值。除显式声明的准备/回执中间态外，不允许把最终一致投影作为写入许可。

| 概念/候选根 | 身份、生命周期所有者 | 保护的规则/同事务变化 | 异步责任、热点与可反驳边界 |
| --- | --- | --- | --- |
| Working Set 根 | WorkingSetId；范围治理 | AP-001 配置整体；AP-003 已准备清单的发布指针 CAS | 准备可异步；十万 Binding 否定 AP-001 的装载假设，不证明 AP-003 必然获批 |
| Working Set Binding | AP-001 内部 Entity，键为范围+选择；AP-003 封闭 Manifest 内值 | 同一选择不重复、空间归属与选择一致；与所属配置/清单一起冻结 | 独立根是第四种备选，必须补跨 Binding 发布协议；空 ProductSpace Binding 不能被自动删掉 |
| Tracker 根 | TrackerId；谱系治理 | 模式、归属、直接来源候选 | 全谱系无环不能只靠单节点检查：来源冻结/祖先证据+并发限制待选；热门 SHARED 写不能锁全谱系 |
| Tracker Lineage | 谱系关系/查询视角，暂不另立根 | 边创建合法性的责任待确认，不能再分配给独立 Branch 根 | 若允许任意重挂，单节点边界不足，需独立谱系协调；不能以查询索引充当守卫 |
| 原 AP-002 的 Tracker Branch 根（不兼容，停用） | D-001 不允许该独立根及领域概念 | 原来源切点与演进关系须重新分配责任 | 不能把 BranchId 自动改为 TrackerId；见 AP-002 修订建议 |
| Item Family | 用户已确认 CommonId 全局标识逻辑家族 | 跨变体对齐键保持一致，无关 Item 不共用 CommonId | 是否具有独立生命周期仍需证据；POL-005 已确认单工作集同族仅一个有效变体 |
| Item Variant 根（AP-003） | LocalId；SK 身份/来源管理 | 身份、归属、固定来源、物化确认不复活删除 | BC 内容提交与 SK 确认分离，须查回本地权威；若只是派生索引则不应建根，跨 BC 同事务不默认要求 |
| Source Cut 值 | 历史证据定位值；历史提供者保留 | 不可变内容/成员/生命周期等证据标识 | 不是时间戳实体；保留义务需显式凭据。历史不可读直接反驳懒 COW 可行性 |
| Snapshot/业务 Baseline 根候选 | SnapshotId；用户命名/保留能力所有者待定 | 冻结清单、保留凭据完整才发布 | 若仅技术历史读结果则是值/端口；不可与仓库 Design Baseline 混用 |
| Difference 值 | 比较上下文+双侧证据+字段路径 | 不可变；模式和方向随证据固定 | 大差异可分页但须同一冻结清单，不能把每页当新实时差异；若有独立审阅生命周期可变实体属于 Session |
| Merge Session 根候选 | SessionId；Compare & Merge | 方向、证据、决定版本；不持有业务内容 | Merge Round 是会话的一次执行角色，是否独立根待规模验证；百万决定不可一次加载，需分项执行单元 |
| Merge Decision Entity/不可变版本 | Session+DecisionId+version；审阅者经会话管理 | 存在性/字段选择与证据完整性一起保存 | 单项可成为执行根但要冻结会话清单；DEFER 不得确认完成，整轮原子要求反驳分项模型 |
| Apply Result 值/回执 | 操作身份；BC 提交事实权威，SK 持有副本 | BC 内容和可恢复回执一致，SK 不伪造成功 | 回执丢失会产生未知，不能当失败重写；不是额外业务对象聚合 |
| Checkpoint 独立根 | 有向范围+家族键；Compare & Merge | 证据关联、决定版本、确认/撤销因果 CAS | 最终一致消费真实结果；旧确认晚于撤销不能覆盖；需跨范围唯一键和保留政策 |
| Reference/Association 根候选 | 各关系自身身份；关系类型的业务所有者 | 端点意图、钉版、生命周期、修复版本 | 端点修复可异步；业务关联不可假定归 SK；删除守卫不能只读滞后反向索引 |

无一方案要求跨四 BC 事务。若某对象变化要求另一 BC 的规则同刻成立，应先表达原子可见性并重新设计协议；不能用“每次只能改一个聚合”裁掉业务需求。AP-001/003 继续候选，AP-002 原根结构已不兼容、须修订；Tracker 独立聚合地位由 D-001 确认，不代表任一完整方案获批。Item 是否属于 Tracker 聚合继续待定。

## D-002 对边界的影响（2026-11-10，历史阶段；字段解释见 D-003）

已确认：非共享派生的源与派生 Tracker 为不同对象，均沿用 D-001 独立聚合地位；Working Set 不拥有其完整生命周期。parentId/sourceId 表达追溯层级，但不先认定二者为直接父/最初源。

仍为 Draft 推论：Tracker 间按身份引用表达谱系，不必组合为全谱系大聚合；Working Set 与 Tracker 作为独立聚合协作；Tracker Lineage 作为关系概念暂不单独设根。表中全谱系无环、重挂、来源冻结等仍为候选校验，不能反推多级派生或可修改已获批准。Item 归属不变。

AP-002 修订方向对独立演进的解释力增强，但原 Branch 根依然不兼容；AP-001/003 同样可表达新 Tracker 身份，不能因此选择唯一方案。见 [D-002 评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-002)。

## D-003：组织树与派生链分别分析（Draft 设计影响）

parentId 约束同一工作集的有效组织范围，sourceId 约束直接父级工作集来源。Tracker 独立聚合，不因树形关系把所有 Tracker 放入单聚合。候选校验应分别处理结构有效性、结构循环、来源准入及禁止跳级；具体保护者、跨聚合事务和并发协议尚未批准。

Shared Tracker 可以被多个工作集引用，数据库归属默认工作集不能代替当前组织范围判断。不能擅自把 parentId 搬到 Binding 或要求各工作集父级一致；先保留该语义缺口。组织树派生时是否复制、映射或重建也未裁定。图见 [双维关系](../diagrams/tracker-dual-relations.md)，规则分类见 [INV 审查](invariants.md#d-003-双维关系约束审查)。

## D-004：裁剪不改变聚合所有权（Draft 设计影响）

已确认合法终态：仅选 B，派生新 Tracker B1，其结构父为空、直接来源为 B；不自动补建 A1。Working Set 定义选择/有效范围，Tracker 保持独立身份与生命周期；不能为了保留源组织树把全部 Tracker 合入一个聚合。

候选协作须分别处理选择范围、新 Tracker 身份、目标结构父和直接来源。null 结构父是合法根状态，不是来源丢失或创建未完成。它不证明各阶段能部分发布；“已创建但未纳入”等中间态、取消/重试、并发及整批原子性仍待设计。父也被选时的默认继承与创建前显式重组已由 D-005 确认（POL-010），创建后 parentId 不可变已由 D-006 确认（POL-011）。

跨聚合校验只需兑现已确认规则，不得把完整来源祖先闭包当作发布条件。Shared 混合树有效父必须检查目标有效范围，不能只看默认工作集归属；本次不选择 parentId 的存储位置或赋予工作集完整生命周期所有权。Item 边界不变。见 [裁剪图](../diagrams/tracker-dual-relations.md#d-004-clipping)。

## D-006：结构一致性集中于创建（Draft 设计影响）

已确认的 Tracker 独立身份/生命周期、组织位置、工作集组织树、直接来源与工作集成员扩展分别表达。parentId 在完成创建前确定，完成后不再移动；其他状态变化仍由各自规则决定。创建前重组是输入/候选结构确定，不等于先完成创建再更新父字段，不需增加 Draft Tracker 根。

候选跨聚合校验集中于目标父有效范围、创建候选结构无环和来源准入；不能据此合并整树。Tracker 局部不可变性不独自保证跨 Tracker 父有效，仍需创建时的协作协议。创建时父消失的竞态、批次原子性未裁定；父后续**删除**已由 2026-11-11 K2 裁定（存在组织子级/派生子级/Item 时禁止删除），「停用」仍待裁定（`QUESTION-009.a`）。

成员扩展可继续关联新 Tracker，不移动已有节点。AP-002 获得结构稳定性依据但未获批准；Item 归属当时仍待定，现由 D-007 关闭。见 [生命周期图](../diagrams/tracker-creation-lifecycle.md) 与 SCN-016—018。

## D-007：TrackerItem 契约与业务 BC 聚合（Draft 设计影响）

已确认：TrackerItem 是公共能力契约，业务 BC 对象（Requirement/Part/Process 等）是完整独立聚合并实现该契约，Working Set 只负责公共身份与工作集机制，不拥有业务对象状态、生命周期与不变量。LightTrackerItem 是候选聚合，边界与权威性待定，不得因索引表定根或否定候选定位。类图见 [D-007 契约与聚合图](../diagrams/trackeritem-contract.md)。

## D-008：LightTrackerItem 权威聚合与 COW 延迟物化（Draft 设计影响）

已确认：LightTrackerItem 是 Working Set 专属独立聚合（非查询投影），派生时创建并分配当前 LocalId、保留 CommonId、记录 SourceLocalId/SourceRevision、经 TrackerId/WorkingSetId 指向，承担身份与有效变体定位权威。派生采用 COW，不立即物化完整业务对象副本。业务 BC 通过 CQRS Query 与工厂重建完整业务聚合，调用方不感知物化状态；Working Set 不拥有或重建业务 BC 完整状态。

候选：LightTrackerItem 完整属性与物化协议、LocalId 是否等同业务聚合变体身份、首次物化标志与有效内容指针、物化原子性、SourceRevision 版本选择。LightTrackerItem 与业务对象是双事实：LightTrackerItem 持身份/来源/定位，业务对象持完整内容与不变量，二者按 ID reference 关联，不组合、不跨 BC 事务。图见 [D-008 生命周期图](../diagrams/lighttrackeritem-lifecycle.md)。

**2026-11-11 T3 裁定（K1′～K3′，用户确认）**：物化状态改为独立 `materialized` 字段（与 `sourceLocalId`/`sourceRevision` 同一次聚合状态变更同步更新）；首次物化复用预分配 `LocalId`、不持久化内容指针；业务 BC 必须能按稳定 `LocalId` 定位或创建变体（主键不强制等同）；跨 BC 采用事件驱动 + 幂等重试（只补未完成步骤，不覆盖已有业务对象）。详见 [T3 契约](lighttrackeritem-materialization-contract-v1.md)、INV-069～071、SCN-066～068。

## D-009：来源变更 COW 与有效内容切换（Draft 设计影响）

已确认：事件驱动的来源变更 COW 机制——来源业务对象普通修改不升级 Revision；来源修改前先 Query 获得修改前完整业务事实，修改保存后发来源变更事件触发受影响派生变体 COW 物化；物化 Handler 据修改前事实形成派生变体自身完整业务聚合并保存，成功后发物化完成事件；LightTrackerItem 索引 Handler 标记已物化，有效内容来源切换到自身 LocalId 及自身有效版次。Working Set 负责公共身份、来源关系与有效内容定位，业务 BC 负责完整业务聚合构造、不变量与保存。

LightTrackerItem 需区分两个来源维度（候选，不未经确认新增字段）：

- **Derivation Origin／派生历史来源**：派生时的历史来源（sourceLocalId），用于追溯谱系，物化后不覆盖。
- **Effective Content Source／有效内容来源**：当前有效内容读取来源；物化前 Source-backed（指向来源），物化后 Local-backed（指向自身）。

候选：修改前事实的可靠保存/稳定引用、查询可见性窗口、物化唯一性与重复事件处理、SourceVersion/SourceRevision 严格定义。图见 [D-009 事件 COW 时序](../diagrams/lighttrackeritem-lifecycle.md)。

## D-010：COW 最终一致与最小模型收敛（Draft 设计影响）

已确认：COW 采用异步事件驱动、只保证最终一致；来源修改前事实作为事件关联的不可变内容快照传递；来源修改已保存、COW 未完成期间允许派生工作集暂时读到修改后内容，物化完成后以修改前事实形成独立内容；不要求跨 BC 事务。

**最小模型收敛**：领域模型围绕 Working Set、Tracker、LightTrackerItem 三类核心聚合收敛；不再引入 AP-003 的 Manifest、ItemVariant。前文「方案结论状态」与「逐概念一致性审查」表中 AP-003 的 Manifest/ItemVariant 相关表述均为历史候选，现由 D-010 标记为当前不采用（保留历史讨论）。

**LightTrackerItem 双态**：Source-backed（内容由来源解析）与 Local-backed（本地业务聚合已物化，有效来源定位到自身）为两个领域主状态；Materializing 是技术运行状态，不增加第三种业务状态。SourceLocalId 区分历史派生来源（Derivation Origin）与当前有效内容来源（Effective Content Source），字段是否拆分待设计。

不得把 Tracker、LightTrackerItem 或业务 BC 聚合包含进 Working Set 大聚合；不引入全局事务、跨 BC 大聚合或复杂安全门。图见 [契约与聚合图](../diagrams/trackeritem-contract.md) 与 [生命周期图](../diagrams/lighttrackeritem-lifecycle.md)。

## D-011：首次快照优先与物化声明（Draft 设计影响）

已确认：对尚未物化的派生 TrackerItem，来源首次触发 COW 的修改时，修改前完整业务事实（事件关联不可变快照）确定目标变体最终物化内容；同一来源后续连续修改不覆盖首次快照；快照选择按来源修改实际先后顺序而非事件到达顺序；同一目标变体不因重复事件产生多个有效物化结果；Local-backed 后来源事件不覆盖本地内容。

LightTrackerItem 保持 Source-backed / Local-backed 两个稳定业务状态，不因事件处理中间步骤增加额外领域状态。候选领域协议（不自动建聚合/表）：

- **Source Change Order**：反映来源成功修改先后顺序的机制（复用乐观锁 `lock_version` 还是独立序列待定）。
- **Derivation Baseline**：目标变体派生时已继承的来源内容位置，用于判断「首次相关修改」。
- **Materialization Claim**：候选的首次快照选择记录，保留首次快照的确定性证据。

这些候选概念不自动设立新聚合、数据库表或持久状态。图见 [D-011 事件时序](../diagrams/lighttrackeritem-lifecycle.md)。

## D-012：SourceLocalId / SourceRevision 职责分离（Draft 设计影响）

已确认：LightTrackerItem 的 SourceLocalId 表示当前有效内容来源的 LocalId，SourceRevision 表示当前有效内容来源的业务 Revision；SourceVersion 为表述错误，统一为 SourceRevision。未物化（Source-backed）时 SourceLocalId/SourceRevision 解析来源内容；COW 完成后 SourceLocalId=LocalId、SourceRevision=Revision，有效内容定位指向自身；COW 不改变 LocalId、CommonId。

前文 D-008/D-009/D-010 将 SourceLocalId 描述为历史派生来源、SourceRevision 描述为快照语义、SourceLocalId 双语义的表述均为历史分析，现由 D-012 收敛为「当前有效内容来源」单一职责。R0→R1 历史派生关系不要求由 LightTrackerItem 保存，历史谱系承载方式已确认不需要（D-018）；不新增 OriginLocalId、谱系聚合或独立历史聚合。

LightTrackerItem 保持身份/来源/有效变体定位权威：SourceLocalId/SourceRevision 只表达当前有效内容来源，历史派生关系由外部机制承载（待定）。图见 [契约与聚合图](../diagrams/trackeritem-contract.md) 与 [生命周期图](../diagrams/lighttrackeritem-lifecycle.md)。

## D-013：直接有效来源定位与依赖者批量 COW（Draft 设计影响）

已确认：多级派生中 SourceLocalId 直接指向实际拥有完整业务内容的变体，不采用对未物化中间变体逐级递归解析；直接上游已物化则指向该上游，未物化则继续向上找最近实际拥有完整内容的上游。COW 依据 SourceLocalId 发现所有依赖该实际来源的未物化 LightTrackerItem，即使分布在不同层级 Working Set 也都需物化（扇出）。

前文 D-012 的「多级派生逐级解析」候选假设被 D-013 排除，保留历史追溯。LightTrackerItem 不承担历史派生关系保存职责；不引入 Manifest、ItemVariant 或额外领域聚合。

核心聚合仍为 Working Set、Tracker、LightTrackerItem，业务 BC 保留完整业务聚合所有权。图见 [有效来源定位图](../diagrams/lighttrackeritem-lifecycle.md)。

## D-014：已有下游不随中间上游物化重定向（Draft 设计影响）

已确认：派生完成后，已存在的未物化 LightTrackerItem 不因中间上游变体物化而自动重定向 SourceLocalId；SourceLocalId 不需在生命周期内始终动态指向最近已物化祖先。只有当前变体自身完成 COW 后才切换 SourceLocalId=LocalId、SourceRevision=自身 Revision。

关键区分两个维度：

- **Working Set 派生层级**：WS0→WS1→WS2，表达派生关系。
- **有效来源依赖（SourceLocalId）**：R2 的 SourceLocalId 固定指向派生时确定的实际内容来源（如 R0），不随 R1 物化而改为 R1。

明确排除：R1 物化后自动更新所有已有下游 SourceLocalId、Query 每次重新寻找最近已物化祖先、依据派生树无差别触发所有后代 COW、为来源重定向新增第四类聚合或复杂状态机。

核心聚合仍为 Working Set、Tracker、LightTrackerItem，业务 BC 保留完整业务聚合所有权。图见 [派生层级与有效来源依赖图](../diagrams/lighttrackeritem-lifecycle.md)。

## D-015：派生时间基准与不复制内容（Draft 设计影响）

已确认：Working Set 派生不复制完整业务对象数据，仅创建 LightTrackerItem 建立身份、工作范围关联与有效内容来源引用。派生必须选择时间基准（命名 Snapshot 或 Head，Head 是派生请求确定的时间点），系统按该时间点选择之前适用的最大业务 Revision；派生时不等待来源停止变化、不因并发修改先完成而改变已选时间基准。

职责划分：

- **Working Set**：派生时间基准选择与工作范围职责。
- **Tracker**：创建及派生身份职责。
- **LightTrackerItem**：身份、来源、版次定位职责（保存 LocalId/CommonId/SourceLocalId/SourceRevision）。
- **业务 BC**：根据指定 Revision 提供 Query/工厂重建能力。

领域选择时间基准不等于要求跨聚合大事务；不提前选定数据库锁、时间戳生成方式、事务隔离级别或消息基础设施。图见 [派生主流程](../diagrams/lighttrackeritem-lifecycle.md)。

## D-016：派生仅固定版次与 COW 保护边界（Draft 设计影响）

已确认：派生仅固定业务版次（SourceRevision），不复制或固定完整业务数据。完整派生操作完成（Derivation Completion）是新派生 LightTrackerItem 开始受到来源变更 COW 保护的业务边界：派生完成前来源修改不为该变体触发 COW，派生完成后首次相关来源修改触发 COW。

关键区分：

- **Derivation Time Basis**（派生时间基准，选 Revision）≠ **Derivation Completion**（完整派生完成，COW 保护边界）。
- SourceRevision 只表达业务版次，不等于不可变内容快照。
- D-011 首次快照优先限定为「派生完成之后首次需要保护的相关来源修改」。

候选协作协议：如何表达和判定 Derivation Completion 与 Source Mutation 的实际先后（场景 A/B 判定依据）；场景 C 竞态（来源修改已开始 → 派生完成 → 来源修改提交）标记为待裁定，不自行决定以开始时间还是提交时间判断。不直接确定新数据库字段、全局锁或新聚合。（此候选协作协议现由 D-017 关闭，见下节）

## D-017：COW 目标集合在事件消费时确定（Draft 设计影响）

已确认：COW 目标集合在事件 Handler 实际消费来源变更事件时确定，不在来源修改开始时提前固定；Handler 查询当前 SourceLocalId/SourceRevision 匹配且尚未物化的 LightTrackerItem，查询命中的都参与本次 COW 物化。不追溯比较修改开始时间与完整派生完成时间，不引入跨聚合全局时序协调机制。

前文 D-016 的「Derivation Completion 与 Source Mutation 实际先后判定」「场景 C 竞态」候选协作协议由 D-017 关闭，保留历史追溯。D-016 的 Snapshot/Head 只决定 Revision、不复制完整内容的规则不变；D-016 的 COW 保护资格历史时间边界由 D-017 取代为「消费时按当前有效引用关系确定」。

保持最小模型（Working Set、Tracker、LightTrackerItem），不增加额外领域聚合、Manifest、ItemVariant 或复杂事件协调机制。图见 [COW 主流程时序](../diagrams/lighttrackeritem-lifecycle.md)。

## 阶段 4 补充：ProductSpace、Classification 与绑定（Draft 设计影响）

用户 2026-11-10 补充确认：

- **ProductSpace** 是 Shared Kernel 独立聚合，只维护自身稳定归属身份，在默认 WorkingSet 中维护生命周期、可被多个 WorkingSet 关联，本身不随 WorkingSet 派生复制/迁移；Tracker 属于一个 ProductSpace。
- **Classification** 是独立的共享分类元模型领域（另一团队维护），Tracker 是其投影；同一 ProductSpace/WorkingSet/Classification 最多一个有效 Tracker。
- **WorkingSet 聚合**负责维护当前工作集中的空间与 Tracker 绑定结构：**SpaceBinding、TrackerBinding 是两个 Entity，归属于 WorkingSet 聚合**。ProductSpace、Tracker 只维护自身归属身份，不持有绑定。
- Tracker 按共享策略复用同一聚合（Shared）或派生新聚合（非共享），并遵循 Classification 约束。
- 三类核心聚合仍为 Working Set、Tracker、LightTrackerItem；新增 ProductSpace 作为 Shared Kernel 独立聚合（不放入 WorkingSet 大聚合），Classification 通过共享契约供 Tracker 创建使用。
- 跨聚合关系均用 ID：Tracker 含 productSpaceId/classificationId/parentId/sourceId；LightTrackerItem 经 TrackerId/WorkingSetId 引用。
- WorkingSet 拥有绑定规则与物理上是否一次加载全部绑定记录是两个不同问题，不按 ORM 结构定领域边界。

**删除/移除语义（已确认）**：共享 Tracker 从子 WS 移除仅解除 TrackerBinding；非共享无 Item 且无派生子 Tracker → 删除并解除绑定；非共享有派生子 Tracker → 不允许删除；任意 Tracker 有 TrackerItem → 不允许移除；非共享有组织子节点但无派生子节点 → 不允许删除；移除最后一个 Tracker 不触发空间移除；移除 ProductSpace 时仍有 TrackerBinding → 抛异常；默认 WorkingSet 共享 Tracker 空且无子时可删除。

绑定与删除不变量见 [INV-053～060](invariants.md)；事实见 FACT-024～026、POL-012～015。图见 [ProductSpace/Tracker/WorkingSet 关系图](../diagrams/productspace-tracker-workingset.md)。

**2026-11-11 注（T2 冻结）**：上述摘要中「任意 Tracker 有 TrackerItem → 不允许移除」只约束**聚合删除**，Shared 绑定的解除不受该约束（INV-064）；删除 ProductSpace 时须审查**所有** WorkingSet 的 SpaceBinding，只检查 TrackerBinding 会留下悬空绑定（INV-066）；Tracker 删除还受组织子级、派生子级约束（K2/INV-065）。详见[绑定与删除契约 v1](binding-deletion-contract-v1.md)、INV-053～068、POL-016。

候选（未直接 Accepted）：绑定 Entity 的内部结构、软删/彻底删除机制。

## 阶段 4 补充：Diff/Merge 聚合定位（Draft 设计影响）

用户确认 Diff/Merge 衔接：

- 未物化变体可参与 Diff；未物化目标被 Merge 修改时，在 merge 中构建业务聚合正常 save（物化与修改一体）。
- 合并决策（用户决策、command 入参）可作为 ValueObject 归属 MergeRound 聚合。
- MergeCheckpoint 是独立聚合。
- MergeRound 是合并载体（LRO 发起后记录参数、进度、成功/失败）。
- 共享 Tracker 不参与 Merge；非共享只允许直接父子准入。

聚合定位：MergeRound、MergeCheckpoint 不是三类核心聚合（Working Set/Tracker/LightTrackerItem）之一，而是合并模型区域的聚合（MergeRound 载体 + MergeCheckpoint 独立）。二者与 LightTrackerItem 通过身份/Revision 衔接，不组合。
