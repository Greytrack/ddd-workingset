# Working Set 聚合边界

**状态：Draft（各主题接受范围见 [BASELINE](../BASELINE.md)）**

聚合是事务一致性边界，不是页面装载范围或外键集合。本页只保留当前结论和未决边界；历史方案比较见[聚合边界竞争方案](../proposals/aggregate-boundary-options.md)（Historical）和[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)。

## 当前聚合与契约

| 对象 | 定位 | 身份 | 保护的不变量 | 跨聚合关系 |
| --- | --- | --- | --- | --- |
| **WorkingSet** | 核心聚合根 | `WorkingSetId`；默认根；`parentWorkingSetId` 单父 | 默认根不可删；单父谱系无环；SpaceBinding 前置；绑定唯一性；绑定移除/删除顺序 | ID 引用 Tracker、ProductSpace、LTI |
| **SpaceBinding** | WorkingSet 内 Entity | `(workingSetId, productSpaceId)` | 同一 WS/空间唯一；有 TrackerBinding 时不可移除 | 引用 ProductSpace |
| **TrackerBinding** | WorkingSet 内 Entity | `(workingSetId, trackerId)`；槽位 `(WS, space, classification)` 可多 Tracker、至多一个有效默认 | 同一 Tracker 不重复纳入；空间一致；共享特性相容；默认资格由 Tracker 承担（T6-F） | 引用 Tracker |
| **Tracker** | 独立聚合根 | `TrackerId` | 业务原生 Tracker 只能在默认工作集创建；共享特性创建后不可变；`parentId` 创建后不可变；`sourceId` 不可变（T6-F）；删除受 Item/组织子级/派生子级约束 | ID 引用 ProductSpace、Classification、父 Tracker、来源 Tracker |
| **LightTrackerItem** | 独立聚合根 | `LocalId`（变体）；`CommonId` 关联家族 | `materialized` 三字段一致；COW 不改身份；Local-backed 后不被来源覆盖；单 WS 家族唯一 | ID 引用 Tracker、WorkingSet、业务对象 |
| **ProductSpace** | 独立聚合根 | `ProductSpaceId` | 稳定归属身份；不随 WorkingSet 派生复制；删除前所有绑定为空 | 被 SpaceBinding 引用 |
| **MergeRound** | 合并区域聚合根 | `MergeRoundId`；`requestId` 幂等 | 一页 1～50 条决策；同 Tracker 对不并发；终态不可重开；唯一 `ApplyResult` | 持有 MergeEntry（Entity）与决策；ID 引用 WorkingSet/Tracker/LTI |
| **MergeEntry** | MergeRound 内 Entity | 轮次内 `Entry.id`；双侧业务定位保存在证据中 | 证据与决定绑定；终态后不可重执行 | 引用 Difference 证据 |
| **MergeCheckpoint** | 独立聚合根 | 规范化 Tracker 对 + `CommonId` | 确认/撤销因果单调；方向只是属性；WorkingSet 不参与；不证明内容相等 | 按 Tracker 对/家族/双侧版次关联 MergeRound |
| **TrackerItem** | 公共契约（非聚合） | 无 | 公共身份、Revision 与生命周期能力声明 | 业务 BC 聚合实现 |
| **BusinessObject** | 公共能力接口（非聚合） | 无 | `delete/undelete/delete2` 能力声明；实际规则由 BC 拥有 | `TrackerItem` 继承 |
| **Reference／Association** | SK 关系聚合（WorkingSet 不拥有；本轮只发事件） | 业务关系聚合 ID | 端点意图、钉版、有效性、修复；不参与 Compare/Merge | 关系服务消费事件；引用业务对象/变体 |
| **WorkingSetCreation** | LRO 载体聚合 | `creationId`；`requestId` 幂等 | 任务状态机、范围快照、部分成功明细；不拥有 WorkingSet/Tracker/LTI | ID 引用父/目标 WS |

完整业务内容与状态机由各业务 BC 的聚合负责。Working Set 的聚合之间按身份引用。跨 BC 写入见 [T4](cross-bc-contract-v1.md)。

## 一致性边界

- `TC-002` 已由用户于 2026-11-19 明确确认为架构硬约束：一个写事务最多修改一个聚合，跨聚合变更通过可靠事件与独立事务最终收敛。不是业务不变量，也不得再作为可选实现策略。执行规则及反模式清单见 [AGENTS §7.2/7.4](../../../AGENTS.md)，偏差见 [反模式审查](../reviews/2026-11-19-ddd-anti-pattern-scan.md)。
- 业务对象保存与 LTI 切换是双事实：不要求同一事务；业务保存成功而切换失败不回滚，幂等重试只补未完成步骤（T3 K3′、D3-B）。
- Merge 条目业务应用在目标 BC 独立事务提交；LTI 切换由 `RequirementMergeAppliedEvent` 事件在 SK 独立事务完成（D15-B）。
- 工作集创建任务按步骤/批次独立提交，不把全流程放进一个大事务；部分成功保留明细（T7）。

## 未决与需验证

| 项 | 现状 | 影响 | 关联 |
| --- | --- | --- | --- |
| WorkingSet/Binding 规模与并发上限 | 未采样；当前单聚合装载全部绑定 | 可能否定当前聚合装载假设，触发 Manifest/分片方案 | `QUESTION-012` |
| Reference/Association 的消费与守卫 | WorkingSet 侧事件已实现；关系服务归属/消费守卫未定 | T7-E 表与端口 | `QUESTION-010` |
| 创建任务收尾 | 保留、取消与补齐待定 | 终态后的任务治理 | `QUESTION-017` |
