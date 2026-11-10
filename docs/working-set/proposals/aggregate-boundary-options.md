# Working Set 聚合边界竞争方案

**状态：Draft — Architecture Proposals**

三套方案均为候选，不构成 ADR。证据来源见 [证据登记](../domain/evidence-register.md)，候选不变量见 [invariants.md](../domain/invariants.md)。方案类图和聚合边界图遵循 [图表渐进交付清单](../diagrams/README.md)，只在对应方案进入正式比较时逐个补充。

## `AP-001`：Working Set 主导的配置聚合

**核心**：Working Set 聚合拥有 ProductSpace Binding 与 Tracker Binding，原子维护当前配置；Tracker 和 Item 仍按 ID 引用，不放入大对象图。

- **保护**：发布范围结构一致、空间与 Tracker Binding 匹配、发布／取消互斥。
- **优点**：模型简单；小中规模范围可一次加载和校验；配置变化易理解。
- **代价**：Binding 多或并发纳入频繁时成为热点；不能把所有 Item 纳入聚合。
- **共享/COW/Merge**：共享 Binding 指同一 Tracker；COW 和 Merge 在其他聚合完成。
- **反例**：数万 Tracker 的 Working Set；仅改名却与高频 Binding 变更冲突。
- **适用／放弃**：范围有明确上限且整体配置必须原子时适用；P99 规模或冲突率失控时放弃。

## `AP-002`：原 Tracker Branch 独立演进模型（不兼容 D-001）

**状态：Draft，原结构不得作为当前候选实施；以下保留历史方案及权衡。**

**核心**：Tracker Branch 是主要聚合根，拥有派生身份、固定来源和生命周期；Working Set 只维护对 Branch 的选择。

- **保护**：每条 Tracker 演进线的 Sharing Mode、直接来源、无环、派生与删除守卫。
- **优点**：独立演进、分支合并和 Tracker 级并发自然；Working Set 更轻。
- **代价**：若业务并不认识 Tracker Branch，会引入技术实体；跨 Tracker 的发布范围一致性更难。
- **共享/COW/Merge**：Shared 指同一 Branch 或权威 Tracker；Non-shared 建新 Branch；Item COW 依附 Branch。
- **反例**：用户只关心一个整体工作范围，不单独管理 Tracker Branch；空 ProductSpace Binding 无法由 Branch 解释。
- **适用／放弃**：业务明确管理 Tracker 分支生命周期时适用；Branch 仅是实现细节时放弃。

### AP-002 修订建议（Draft，非术语替换）

原 Branch 根组合了独立生命周期、派生身份与来源切点。D-001 只确定独立管理者为 Tracker，不确定原 BranchId 与 TrackerId 的映射，当时尚不确定跨工作集是否新建 Tracker；D-002 现已明确非共享派生建立新的独立 Tracker，Shared 不变。建议去掉独立 Branch 根，Working Set 通过关联选择独立 Tracker；将演进关系、来源证据的归属列为未决，暂不画入 Tracker 内部。Item 的包含或独立引用均待确认。若修订后与 AP-001 无实质区别，应合并方案而非仅改名维持三套。

## `AP-003`：版本化 Manifest + Tracker 谱系 + Item Variant

**核心**：Working Set 根只原子切换已准备好的 Manifest revision；Binding 可分片准备，Tracker 谱系和 Item Variant 各自管理身份与来源。该方案吸收 `EXT-ARCH §6 方案 A`，但不接受其推荐结论。

- **保护**：发布指针与封闭清单一致；Tracker 谱系规则；Item Variant 身份与 Source Cut 稳定。
- **优点**：兼顾混合共享／派生、大规模准备和稳定身份；长流程可恢复。
- **代价**：Manifest、Tracker、ItemVariant 与业务 Item 多边协议复杂；依赖完整历史和墓碑。
- **共享/COW/Merge**：Shared Manifest entry 指同一权威 Tracker；Non-shared 指派生节点；COW 保持 Variant 身份；Merge 使用固定双侧证据。
- **反例**：历史系统无法回读附件／关系；ItemVariant 只是最终一致缓存却被误当权威；小规模场景可能过度设计。
- **适用／放弃**：大范围、强审计、混合共享/派生是核心时适用；无法兑现历史合同或业务拒绝变体身份时放弃。

## 补充基准：不可变 ContentVersion + Overlay

`EXT-ARCH §6 方案 B` 是长期基准：派生只继承不可变版本指针，本地写创建新版本。它显著简化历史证据和 fan-out，但对覆盖式存储、附件、关系和搜索改造最大。本阶段保留为重新评估条件，不另编号为当前聚合候选。

## 比较矩阵

| 维度 | AP-001 | AP-002 | AP-003 |
| --- | --- | --- | --- |
| 最自然解释 | 整体配置与发布 | Tracker 独立演进 | 大规模混合共享／派生 |
| 主要强一致点 | Working Set 配置 | 原 Branch 根（已不兼容） | Manifest 发布指针 + 各根内部状态 |
| 主要热点 | Working Set version | 热门 Shared Tracker | 协议与准备索引 |
| 历史依赖 | 中 | 中高 | 高 |
| 实施复杂度 | 低至中 | 中 | 高 |
| 关键放弃条件 | 范围巨大 | D-001 已排除独立 Branch 根 | 历史/双事实无法兑现 |

任何选择都必须先回答 `QUESTION-001/002/004/006/012`，并用 [核心场景](../scenarios/core-scenarios.md) 反驳验证。

## 阶段 3 正式比较补充

责任模式 A/B/C 与 AP-001/002/003 是正交维度：前者决定谁能提交业务内容，后者决定工作范围与谱系如何保持一致。推荐责任 C 不意味着推荐 AP-003。

当前建议以 AP-001 作为最简单的范围配置基准；若收集到巨大 Binding 规模和原子切换要求，再选 AP-003；AP-002 原 Branch 根已由 D-001 排除，其修订方向不再以确认 Branch 身份为前提。三者均保持业务 BC 内容提交边界，保留历史/权限/删除的显式失败。迁移从 AP-001 到 AP-003 需冻结原选择为首个 Manifest、校验成员身份映射、双读核对并 CAS 切换，不能重分配 LocalId；AP-002 的 Branch 身份迁移则需映射原 Tracker 与派生节点，不能只改术语。

图表：[三套类图与聚合边界](../diagrams/aggregate-candidates.md)。逐概念规则：[聚合审查](../domain/aggregate-model.md)。判别场景：[边界场景](../scenarios/shared-kernel-boundary-scenarios.md)。决策状态与反例：[正式评审](../reviews/2026-11-09-stage-3-architecture-review.md)。

## D-001 兼容性审查（2026-11-10）

- AP-001：以 ID 选择独立 Tracker 与 D-001 兼容；若将 Tracker 本身作为配置内部实体则不兼容。Binding 可作为候选内部实体不等于 Tracker 被组合。Item 根结构仍仅方案假设。
- AP-002：独立 Tracker Branch、BranchId、Non-shared 必建 Branch、COW 依附 Branch 均不能继续采用；修订建议见上节，未选择新身份方案。
- AP-003：独立 Tracker 与 D-001 兼容；D-002 已确认非共享派生节点对应不同 Tracker 身份；追溯字段指向及 ItemVariant 独立根仍未批准。固定审阅证据不改变执行取源最新值政策。

所有方案继续 Draft，不因局部兼容而 Accepted。决策与开放边界见 [Proposed ADR-0001](../adr/0001-tracker-independent-aggregate.md)；图表同步仅移除失效根及标明候选假设。

## D-002 影响与修订方向（Draft）

- AP-001：范围配置可引用新派生 Tracker；配置范围不拥有 Tracker 完整生命周期。创建 Tracker 与纳入范围的可见性、失败恢复协议仍未确定。
- AP-002：独立 Tracker 的演进管理获得更多业务依据；候选模型应以不同 Tracker 身份及追溯关系解释非共享派生，不能恢复独立 Branch 根，也不能自动分配 parentId/sourceId 指向。独立演进不证明 Item 属于 Tracker。
- AP-003：Manifest 可选择新派生 Tracker；这是身份事实，不证明必须采用 Manifest、ItemVariant 独立根或全谱系事务。Shared 仍选择既有权威 Tracker。

跨 Tracker 身份引用、Working Set 独立聚合协作、Lineage 暂不设聚合均保持推论。五项追溯问题及规模/一致性证据不足前，不批准任何 AP。修订后的 AP-002 是否与 AP-001 有实质差异仍须比较，不能为维持三方案数量制造新概念。

## D-003 双维关系修订建议（Draft）

D-002 章节为历史分析；其中 parentId/sourceId 合称派生追溯、字段尚未确定的描述现由 D-003 澄清。parentId 是同工作集组织树，sourceId 是跨直接父子工作集派生链，多级来源逐级指向。

- AP-001：Binding/工作范围可为结构父有效性提供候选校验上下文，不因此拥有 Tracker 生命周期；不要以物理归属替代 Shared 有效范围。
- AP-002 修订：Tracker 独立演进解释力保留，但必须拆开组织与派生约束；不可复活 Branch 根，也不可把 parentId 视为来源复制。结构调整与来源变更分别待裁定。
- AP-003：Manifest 候选需要分别表达组织范围和来源身份证据；是否随 Manifest 冻结、如何继承组织树仍未批准，ItemVariant 根不因此获批。

三者均可表达双维关系，D-003 不选择方案、不设全树/全链大聚合。候选图见 [双维关系图](../diagrams/tracker-dual-relations.md)，完整架构推论保持 Draft。

## D-004：组织裁剪对三方案的约束（Draft）

未在当前候选文档中发现已接受的“完整源树必须继承”规则，但 AP-001 配置完整性、AP-003 封闭 Manifest 等表述容易被误用为祖先闭包。现明确修正：仅选 B 时可形成根 B1，不得以这些候选假设强制自动创建未选的 A1。

- AP-001：配置校验允许根 Tracker；Working Set 内 Binding 组织不应强制复刻源树。配置原子候选不等于 Tracker 创建/纳入整批事务已获批准。
- AP-002 修订：独立 Tracker 可有新的组织位置，sourceId 仍指直接来源。根状态不丢失派生身份，不恢复 Branch 根；派生后移动仍未裁定。
- AP-003：封闭清单指目标合法范围而非来源组织祖先闭包；准备流程不能暗中增加未选父。发布指针不证明整批 Tracker 创建原子，需另行评审。

三个方案均须解释 SCN-015；父也选择的映射、Shared 混合树、同源多派生、原子性仍开放。无方案因此 Accepted。

## D-005 / D-006：创建前选择、创建后稳定（Draft）

本节更新 D-003/D-004 时点“移动与继承未定”的状态，不删历史权衡。POL-010 确认默认继承与创建前显式重组；POL-011 禁止创建后修改 parentId。

- AP-001：不能通过 Working Set 配置更新移动已有 Tracker；成员扩展与新对象创建仍可继续，范围配置并未全部冻结。
- AP-002 修订：Tracker 独立根可保护自身完成后的组织位置；创建时仍需跨节点范围/环校验，不能据此认定 AP-002 胜出。
- AP-003：不再以运行期可变组织树作为 Manifest 必要性的理由；成员规模、版本化范围与发布可见性仍可另作依据。新 Manifest 不得暗中改写已有 Tracker.parentId。

三方案均不承诺整批创建原子、不决定父删除/停用或 Item 边界，不增加复杂 Draft Tracker 状态机。

## D-007：TrackerItem 契约对三方案的影响（Draft）

D-007 确认 TrackerItem 是公共契约，业务 BC 对象是独立聚合，Working Set 只负责公共身份；LightTrackerItem 是候选聚合。据此重新审查：

- **AP-001**：以 ID 引用选择独立 Tracker 与业务对象，与「业务 BC 拥有聚合」兼容；但 Binding 作为配置内部实体只承载工作范围，不得把业务对象组合进 Working Set。需明确业务对象实现 TrackerItem 后，其公共身份由 SK 维护、内容由 BC 提交。
- **AP-002 修订**：独立 Tracker 演进保留，但其原 Branch 根已不兼容；修订方向不得让 Tracker 拥有业务对象聚合，TrackerItem 契约已排除「Tracker 内部 Item」的解释。
- **AP-003**：Manifest 选择 Tracker 与业务对象身份；其 ItemVariant 独立根候选与 LightTrackerItem 候选聚合在「轻量索引/公共身份」上高度重叠，需澄清二者是同一概念的命名差异还是不同边界，避免重复建模。

LightTrackerItem 的出现不必然替代 ItemVariant、Manifest 或索引概念：若 LightTrackerItem 只是索引，则不构成独立聚合根，与 AP-003 的 ItemVariant 权威根形成对比；若它拥有有效变体与版本定位权威，则更接近变体聚合。三者均继续候选，不自动选定。CommonId/LocalId/Revision/Version 的分配、COW 事务协议仍待裁定。

## D-008：LightTrackerItem 权威聚合对三方案的影响（Draft）

D-008 确认 LightTrackerItem 是独立聚合并承担身份、来源与有效变体定位权威。据此更新：

- **AP-003 的 Manifest/Variant 与 LightTrackerItem 的关系**：AP-003 的 ItemVariant 独立根候选在「身份/来源/有效变体定位」上与 LightTrackerItem 高度重合。若 LightTrackerItem 已承担该权威，则 ItemVariant 独立根可能冗余，应澄清是同一概念的命名收敛还是不同边界（例如 ItemVariant 承载物化内容指针，LightTrackerItem 承载身份索引）。不得为维持三方案数量重复建两个身份聚合。
- **Manifest**：仍可承载工作范围的发布指针与封闭清单，不与 LightTrackerItem 的身份定位职责冲突；LightTrackerItem 通过 WorkingSetId 指向工作范围，二者是引用关系而非组合。
- **AP-001/002**：不受 LightTrackerItem 权威地位改变其兼容性结论；AP-001 不得把 LightTrackerItem 组合进 Working Set 配置，AP-002 修订不得让 Tracker 拥有业务对象。

LocalId 是否等同业务聚合变体身份、物化完成标志与有效内容指针、物化原子性仍待裁定，不自动选定任一 AP。
