# Working Set 领域设计图表

**状态：Draft（目录治理规则；图本身状态以各页为准）**

图表只保留当前阅读所需的最小集合。候选图与历史图单独标注，不作为当前模型入口。每幅图链接其依据，视觉约定见 [AGENTS.md §4.1](../../../AGENTS.md)。

## 视觉摘要与权威关系

图是正文的视觉摘要，不另立规则；图文冲突以正文契约/BASELINE 为准。

- [Context Map](../domain/context-map.md) 是战略文本候选；[企业 Shared Kernel 图](shared-kernel-context-map.md) 只表达 AC-001 的企业外框，不重定义 Working Set 内部聚合或部署单元。
- [跨 BC 契约图](cross-bc-contract.md) 是 [T4](../domain/cross-bc-contract-v1.md) 的视觉摘要；[MergeEntry 生命周期](merge-entry-lifecycle.md) 是 [T1](../domain/merge-execution-contract-v1.md) 的摘要；[创建/扩展 LRO](working-set-creation-lro.md) 是 [T7](../domain/working-set-creation-lro-design.md) 的摘要。
- 图不重复完整规则字段；图中未画的失败语义、版本治理和实现差距仍以正文/实现边界为准。

## 当前阅读图

| 主题             | 图                                                                                                                                                             | 状态                 |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| 全景与边界       | [领域主线](domain-mainline.md)                                                                                                                                 | Candidate            |
| 企业外框         | [Shared Kernel 与四个 BC](shared-kernel-context-map.md)                                                                                                        | Reviewed             |
| 工作集与 Tracker | [ProductSpace/Tracker/WorkingSet](productspace-tracker-workingset.md)、[WorkingSet 谱系](working-set-lineage.md)、[创建/扩展 LRO](working-set-creation-lro.md) | Candidate / Reviewed |
| Tracker 与绑定   | [Tracker 双维关系](tracker-dual-relations.md)、[绑定与删除](binding-lifecycle.md)                                                                              | Candidate            |
| 业务协作         | [TrackerItem 契约](trackeritem-contract.md)、[跨 BC 契约](cross-bc-contract.md)、[MergeEntry 生命周期](merge-entry-lifecycle.md)                               | Candidate            |
| 派生与物化       | [多级有效来源](lighttrackeritem-lifecycle.md)、[双态与首次物化](lighttrackeritem-materialization.md)                                                           | Candidate            |

## 历史与探索

- [候选聚合边界](aggregate-candidates.md)：Historical，方案比较已由当前聚合模型收敛。
- [Checkpoint/GateFlow 内容切换](collaboration-behavior.md)：Candidate，保留 GateFlow 专项；不替代 T1 MergeEntry 生命周期。
- [聚合方案比较](../proposals/aggregate-boundary-options.md)：Historical。

图表更新规则：关联设计实质变化时同一变更内更新，或标为 Superseded 并链接替代图；不用装饰性图重复正文。
