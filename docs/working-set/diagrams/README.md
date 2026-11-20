# Working Set 领域设计图表

**状态：Draft（目录治理规则）**

图表按用途分组，不按文件数量累积。当前业务阅读优先看“全景与边界”“核心流程”；历史候选图只用于追溯，不作为当前模型入口。每幅图链接其依据，视觉约定见 [AGENTS.md §4.1](../../../AGENTS.md)。

## 当前图表

| 主题 | 图 |
| --- | --- |
| 全景与边界 | [领域主线](domain-mainline.md)、[企业 Shared Kernel](shared-kernel-context-map.md) |
| 工作集与 Tracker | [ProductSpace 与绑定](productspace-tracker-workingset.md)、[WorkingSet 谱系](working-set-lineage.md)、[工作集创建任务](working-set-creation-lro.md)、[Tracker 双维关系与裁剪](tracker-dual-relations.md)、[绑定与删除](binding-lifecycle.md) |
| 业务协作 | [TrackerItem 契约与聚合](trackeritem-contract.md)、[跨 BC 契约](cross-bc-contract.md)、[Merge 生命周期](merge-entry-lifecycle.md)、[合并确认与 GateFlow 切换](collaboration-behavior.md) |
| 派生与物化 | [多级有效来源](lighttrackeritem-lifecycle.md)、[T3 物化](lighttrackeritem-materialization.md) |

## 历史与探索

- [候选聚合边界](aggregate-candidates.md)：Historical。
- [两套聚合方案比较](../proposals/aggregate-boundary-options.md)：Historical。
- S0 旧架构图见 [S0 评审](../reviews/2026-11-11-t5-s0-design-review.md)。

图表更新规则：关联设计实质变化时同一变更内更新，或标为 Superseded 并链接替代图；不用装饰性图重复正文。
