# Domain

**状态：Draft（目录用途；不接受任何具体领域结论）**

存放经提炼的统一领域语言、业务事实、业务不变量、业务政策、领域模型及其证据来源。具体内容按需创建，不从 `pre-design/` 或对话中机械搬运。

每份文档应标明状态，并清楚区分已确认内容、假设、技术约束和开放问题；同一规则应有一个权威定义，其余位置使用链接引用。

## 已接受领域文档

- [facts-policy-baseline-v1.md](facts-policy-baseline-v1.md)：事实与政策主题基线 v1（Accepted，2026-11-10；覆盖阶段 3.1 的 R01—R19 与限定术语）；
- [model-baseline-candidate-v1.md](model-baseline-candidate-v1.md)：首版领域模型基线候选 v1（Accepted 候选，含 D-001～D-019 与阶段 4 补充；未正式发布）。

## 阶段 5 契约

- [merge-execution-contract-v1.md](merge-execution-contract-v1.md)：Merge 执行行为契约（Reviewed，T1 已关闭；MergeRound / MergeCheckpoint / Diff+ComparisonToken / MergeEntry 生命周期、行为矩阵与轮次终态）。
- [binding-deletion-contract-v1.md](binding-deletion-contract-v1.md)：绑定与删除契约（Reviewed，T2 已冻结；SpaceBinding／TrackerBinding 契约、九个操作、四类操作区分；K1～K3 用户确认）。
- [lighttrackeritem-materialization-contract-v1.md](lighttrackeritem-materialization-contract-v1.md)：LightTrackerItem 物化协议契约（Reviewed，T3 已冻结；`materialized` 状态字段、双态、LocalId 预分配、首次物化、双事实一致性；K1′～K3′ 用户确认）。
- [cross-bc-contract-v1.md](cross-bc-contract-v1.md)：跨 BC 公共契约（Draft，T4；TrackerItem 能力集与能力标记、六个用例、统一失败语义九分类、版本治理候选；待裁定 K1″～K3″）。
- [ADR-0005（Accepted）](../adr/0005-merge-execution-model.md)：Merge 执行模型的结构决策，与 T1 契约配套。

## 阶段 2 候选知识

- [evidence-register.md](evidence-register.md)：来源代号、读取范围和确认状态；
- [glossary.md](glossary.md)：候选统一领域语言；
- [facts.md](facts.md)：Reported 事实、政策、假设和技术约束；
- [invariants.md](invariants.md)：候选业务不变量及一致性分析；
- [open-questions.md](open-questions.md)：按阻塞程度排序的业务问题；
- [context-map.md](context-map.md)：候选战略上下文；
- [shared-kernel-boundary.md](shared-kernel-boundary.md)：已确认 Shared Kernel 外框与候选跨 BC 职责边界；
- [aggregate-model.md](aggregate-model.md)：候选聚合边界分析。

上述「阶段 2 候选知识」中的文件均为 `Draft`，不属于当前 Accepted 设计基线；「已接受领域文档」中的两份按各自状态行与 `BASELINE.md` 索引为准。
