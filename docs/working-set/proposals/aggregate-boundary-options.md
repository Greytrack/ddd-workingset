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

## `AP-002`：Tracker Branch 独立演进模型

**核心**：Tracker Branch 是主要聚合根，拥有派生身份、固定来源和生命周期；Working Set 只维护对 Branch 的选择。

- **保护**：每条 Tracker 演进线的 Sharing Mode、直接来源、无环、派生与删除守卫。
- **优点**：独立演进、分支合并和 Tracker 级并发自然；Working Set 更轻。
- **代价**：若业务并不认识 Tracker Branch，会引入技术实体；跨 Tracker 的发布范围一致性更难。
- **共享/COW/Merge**：Shared 指同一 Branch 或权威 Tracker；Non-shared 建新 Branch；Item COW 依附 Branch。
- **反例**：用户只关心一个整体工作范围，不单独管理 Tracker Branch；空 ProductSpace Binding 无法由 Branch 解释。
- **适用／放弃**：业务明确管理 Tracker 分支生命周期时适用；Branch 仅是实现细节时放弃。

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
| 主要强一致点 | Working Set 配置 | 单 Tracker Branch | Manifest 发布指针 + 各根内部状态 |
| 主要热点 | Working Set version | 热门 Shared Tracker | 协议与准备索引 |
| 历史依赖 | 中 | 中高 | 高 |
| 实施复杂度 | 低至中 | 中 | 高 |
| 关键放弃条件 | 范围巨大 | Branch 无业务身份 | 历史/双事实无法兑现 |

任何选择都必须先回答 `QUESTION-001/002/004/006/012`，并用 [核心场景](../scenarios/core-scenarios.md) 反驳验证。
