# Domain

**状态：Draft（目录用途；不接受任何具体领域结论）**

存放经提炼的统一领域语言、业务事实、业务不变量、业务政策、领域模型及其证据来源。具体内容按需创建，不从 `pre-design/` 或对话中机械搬运。

每份文档应标明状态，并清楚区分已确认内容、假设、技术约束和开放问题；同一规则应有一个权威定义，其余位置使用链接引用。

## 已接受领域文档

- [facts-policy-baseline-v1.md](facts-policy-baseline-v1.md)：事实与政策主题基线 v1（Accepted，2026-11-10；覆盖阶段 3.1 的 R01—R19 与限定术语）；
- [model-baseline-candidate-v1.md](model-baseline-candidate-v1.md)：首版领域模型基线候选 v1（Accepted 候选，含 D-001～D-019 与阶段 4 补充；未正式发布）。

## 阶段 5 契约

契约正文按主题维护，确认范围见 [BASELINE](../BASELINE.md)，进度见[阶段 5 计划](../reviews/2026-11-10-stage-5-contract-plan.md)。

- [Merge 执行](merge-execution-contract-v1.md)：轮次、条目、比较证据与 Checkpoint。
- [绑定与删除](binding-deletion-contract-v1.md)：绑定结构、操作与拒绝条件。
- [物化协议](lighttrackeritem-materialization-contract-v1.md)：身份、状态转换与失败恢复。
- [跨 BC 公共契约](cross-bc-contract-v1.md)：Port、用例边界、失败分类与治理待办。
- [多级 WorkingSet 谱系](working-set-lineage-design.md)：单父谱系、`parentWorkingSetId`、空范围子 WS、直属父 Tracker 来源、相邻父子 Merge（Draft）。
- [BusinessObject 生命周期能力](business-object-lifecycle-design.md)：`TrackerItem` 继承 `BusinessObject`，delete/undelete/delete2 能力声明由各 BC 实现（Draft）。

## 阶段 2 候选知识

- [evidence-register.md](evidence-register.md)：来源代号、读取范围和确认状态；
- [glossary.md](glossary.md)：候选统一领域语言；
- [facts.md](facts.md)：Reported 事实、政策、假设和技术约束；
- [invariants.md](invariants.md)：候选业务不变量及一致性分析；
- [open-questions.md](open-questions.md)：按阻塞程度排序的业务问题；
- [context-map.md](context-map.md)：候选战略上下文；
- [shared-kernel-boundary.md](shared-kernel-boundary.md)：已确认 Shared Kernel 外框与候选跨 BC 职责边界；
- [aggregate-model.md](aggregate-model.md)：候选聚合边界分析。

上述资料包含已确认子句与候选内容；文件级 Draft 不取消已登记的条款确认。具体接受范围以 [BASELINE](../BASELINE.md) 为准。
