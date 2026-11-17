# Working Set 聚合边界

**状态：Draft（当前聚合结论已由 D-001～D-019、ADR-0001～0005 与 T1～T7 收敛；未决项见文末）**

聚合是事务一致性边界，不是页面装载范围或外键集合。本页只保留当前结论和未决边界；历史方案比较见[聚合边界竞争方案](../proposals/aggregate-boundary-options.md)（Historical）和[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)。

## 当前聚合与契约

| 对象 | 定位 | 身份 | 保护的不变量 | 跨聚合关系 |
| --- | --- | --- | --- | --- |
| **WorkingSet** | 核心聚合根 | `WorkingSetId`；默认根 `1`；`parentWorkingSetId` 单父 | 默认根不可删；单父谱系无环；SpaceBinding 前置；绑定唯一性；绑定移除/删除顺序 | ID 引用 Tracker、ProductSpace、LTI |
| **SpaceBinding** | WorkingSet 内 Entity | `(workingSetId, productSpaceId)` | 同一 WS/空间唯一；有 TrackerBinding 时不可移除 | 引用 ProductSpace |
| **TrackerBinding** | WorkingSet 内 Entity | `(workingSetId, trackerId)`；槽位 `(WS, space, classification)` 可多 Tracker、至多一个有效默认 | 同一 Tracker 不重复纳入；空间一致；共享特性相容；默认标记由 Tracker 行承担（T6-F） | 引用 Tracker |
| **Tracker** | 独立聚合根 | `TrackerId` | 业务原生 Tracker 只能在默认工作集创建；共享特性创建后不可变；`parentId` 创建后不可变；`sourceId` 不可变（T6-F）；删除受 Item/组织子级/派生子级约束 | ID 引用 ProductSpace、Classification、父 Tracker、来源 Tracker |
| **LightTrackerItem** | 独立聚合根 | `LocalId`（变体）+ `CommonId`（家族） | `materialized` 三字段一致；COW 不改身份；Local-backed 后不被来源覆盖；单 WS 家族唯一 | ID 引用 Tracker、WorkingSet、业务对象 |
| **ProductSpace** | 独立聚合根 | `ProductSpaceId` | 稳定归属身份；不随 WorkingSet 派生复制；删除前所有绑定为空 | 被 SpaceBinding 引用 |
| **MergeRound** | 合并区域聚合根 | `MergeRoundId`；`requestId` 幂等 | 一页 1～50 条决策；同 Tracker 对不并发；终态不可重开；唯一 `ApplyResult` | 持有 MergeEntry（Entity）与决策；ID 引用 WorkingSet/Tracker/LTI |
| **MergeEntry** | MergeRound 内 Entity | 轮次内 `entryId`；条目身份含双侧 Tracker/工作集/家族/变体/版次 | 证据与决定绑定；终态后不可重执行 | 引用 Difference 证据 |
| **MergeCheckpoint** | 独立聚合根 | 规范化 Tracker 对 + `CommonId` | 确认/撤销因果单调；方向只是属性；WorkingSet 不参与；不证明内容相等 | 按 Tracker 对/家族/双侧版次关联 MergeRound |
| **TrackerItem** | 公共契约（非聚合） | 无 | 公共身份、Revision 与生命周期能力声明 | 业务 BC 聚合实现 |
| **BusinessObject** | 公共能力接口（非聚合） | 无 | `delete/undelete/delete2` 能力声明；实际规则由 BC 拥有 | `TrackerItem` 继承 |
| **Reference／Association** | 关系模型候选 | 各自关系身份 | 端点意图、钉版、有效性、修复；不参与 Compare/Merge | 引用业务对象/变体 |
| **WorkingSetCreation** | LRO 载体聚合 | `creationId`；`requestId` 幂等 | 任务状态机、范围快照、部分成功明细；不拥有 WorkingSet/Tracker/LTI | ID 引用父/目标 WS |

## 已确认的聚合结论

1. **Working Set 不是拥有全部 Item 的大聚合**：它拥有工作范围与绑定结构；Tracker、LTI、业务对象均按 ID 引用。
2. **Tracker 独立聚合**（D-001）：非共享派生产生新 Tracker 身份（D-002）；`parentId` 与 `sourceId` 是组织树/派生链两个维度（D-003）。
3. **LightTrackerItem 是独立聚合**（D-008）：负责身份、来源与有效变体定位权威；不是查询投影；业务内容由 BC 聚合拥有。
4. **业务 BC 聚合独立**（D-007）：Requirement/Part/Process 等实现 `TrackerItem`，拥有完整内容、状态机与不变量；SK 不拥有、不重建。
5. **ProductSpace 独立聚合**（ADR-0004）：不放入 WorkingSet；SpaceBinding/TrackerBinding 是 WorkingSet 内 Entity（POL-015）。
6. **Merge 是独立模型区域**（D-019、ADR-0005）：MergeRound 持有条目与决定；MergeCheckpoint 独立；不把业务内容放进 Merge 聚合。
7. **跨聚合只按 ID 引用，不跨 BC 大事务**；业务内容写入由目标 BC 的公开写能力完成（T4）。

## 一致性边界

- 单聚合提交 + 可靠事件是当前架构选择（`TC-002`），不是业务不变量；是否成立由可见性与恢复要求验证。
- 业务对象保存与 LTI 切换是双事实：不要求同一事务；业务保存成功而切换失败不回滚，幂等重试只补未完成步骤（T3 K3′、D3-B）。
- Merge 条目业务应用在目标 BC 独立事务提交；LTI 切换由 `RequirementMergeApplied` 事件在 SK 独立事务完成（D15-B）。
- Checkpoint 的因果单调目前由持久化条件（`decision_version`、`revoked`）兑现；领域对象尚未封装 confirm/revoke 行为。
- 工作集创建任务按步骤/批次独立提交，不把全流程放进一个大事务；部分成功保留明细（T7）。

## 未决与需验证

| 项 | 现状 | 影响 | 关联 |
| --- | --- | --- | --- |
| WorkingSet/Binding 规模与并发上限 | 未采样；当前单聚合装载全部绑定 | 可能否定当前聚合装载假设，触发 Manifest/分片方案 | `QUESTION-012` |
| 同空间/分类多 Tracker 的 Compare/Merge 配对 | 当前 `candidates` 实际按直接 `sourceId` 配对；`Difference.key` 已废弃，条目身份由 `MergeRound.Entry.id` + evidence 承担 | 配对规则仍待确认；分页/重放不再依赖页内序号 | `QUESTION-002.j`、T6-F §4.1 |
| MergeCheckpoint 的领域行为 | 契约有 confirm/revoke/lookup；代码以 Store/SQL 实现因果条件 | 聚合贫血、规则分散；是否需要富聚合待评估 | T1 §6 |
| Reference/Association 的聚合归属 | QUESTION-010 C 已定端点身份；关系归属/删除守卫未定 | T7-E 表与端口 | `QUESTION-010` |
| MergeRound 条目持久化 | 当前整轮 JSONB；一页 20～50 条 | 超过一页时是否改独立条目表 | T6-E、`QUESTION-012` |
| WorkingSetCreation 是否独立聚合 | T7 作为 LRO 载体聚合；只记录编排事实 | 任务恢复/保留/取消 | T7、`QUESTION-017` |

## 历史方案（不再作为当前候选）

- **AP-001（Working Set 配置主导）**：保留了“范围配置 + 绑定”的视角，但不能把 Tracker/LTI/业务对象组合进 WorkingSet；当前 WorkingSet 聚合即该视角的最小实现。
- **AP-002（Tracker Branch 根）**：原独立 Branch 根与 D-001 不兼容，已停用；修订方向由 Tracker 独立聚合承担。
- **AP-003（Manifest + ItemVariant）**：D-010 明确不采用；不引入 Manifest/ItemVariant，工作范围由 WorkingSet 直接维护。

比较矩阵、反例与放弃条件保留在[聚合边界竞争方案](../proposals/aggregate-boundary-options.md)和[候选聚合图](../diagrams/aggregate-candidates.md)（均标 Historical）。本页不因历史方案保留而重新开放已关闭选择。
