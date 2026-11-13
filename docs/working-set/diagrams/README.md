# Working Set 领域设计图表

**状态：Draft（目录治理规则）**

本目录保存可由 Git diff 审查的 Mermaid 源图。图表是领域文档的可视化视角，不是独立事实来源；当图与文字冲突时，应按 `AGENTS.md` 的证据和基线规则处理并修复不一致。

## 图表索引

| 主题 | 图 |
| --- | --- |
| 全景与边界 | [领域主线](domain-mainline.md)、[企业 Shared Kernel](shared-kernel-context-map.md)、[内部候选 Context Map](candidate-context-map.md) |
| 工作集与 Tracker | [ProductSpace 与绑定](productspace-tracker-workingset.md)、[WorkingSet 谱系](working-set-lineage.md)、[Tracker 双维关系与裁剪](tracker-dual-relations.md)、[Tracker 创建](tracker-creation-lifecycle.md)、[绑定与删除](binding-lifecycle.md) |
| 业务协作 | [TrackerItem 契约与聚合](trackeritem-contract.md)、[跨 BC 契约与版本治理候选](cross-bc-contract.md)、[Merge 生命周期](merge-entry-lifecycle.md)、[Checkpoint 与 GateFlow 切换](collaboration-behavior.md) |
| 派生与物化 | [多级有效来源](lighttrackeritem-lifecycle.md)、[T3 物化](lighttrackeritem-materialization.md) |
| 探索与历史 | [候选聚合边界](aggregate-candidates.md)、[阶段 2 领域概念关系](domain-concept-relationships.md)、[T5-S0 运行时架构](t5-s0-architecture.md)（Historical） |

图表状态、追溯与渲染要求见 [AGENTS.md §4.1](../../../AGENTS.md)。实际 Mermaid 渲染核验仍待补齐。
