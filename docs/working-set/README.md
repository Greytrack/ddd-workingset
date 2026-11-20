# Working Set 知识库

**状态：Draft（导航入口；不接受具体领域结论）**

当前有效的业务规则以 [BASELINE](BASELINE.md) 的接受范围为准。阅读顺序：先看 [领域主线图](diagrams/domain-mainline.md) 和 [统一领域语言](domain/glossary.md)，再按主题进入契约；需要追溯原因时再读 ADR 和评审。

## 当前设计

- **模型与边界**：[聚合边界](domain/aggregate-model.md) · [Shared Kernel 边界](domain/shared-kernel-boundary.md) · [Context Map](domain/context-map.md)
- **跨 BC 协作**：[跨 BC 公共契约](domain/cross-bc-contract-v1.md)
- **范围与生命周期**：[绑定与删除](domain/binding-deletion-contract-v1.md) · [多级 Working Set](domain/working-set-lineage-design.md) · [异步创建](domain/working-set-creation-lro-design.md)
- **派生、物化与合并**：[物化协议](domain/lighttrackeritem-materialization-contract-v1.md) · [Merge 执行契约](domain/merge-execution-contract-v1.md)
- **业务语言与规则**：[统一领域语言](domain/glossary.md) · [事实与政策基线](domain/facts-policy-baseline-v1.md) · [不变量](domain/invariants.md) · [业务场景](scenarios/README.md)
- **开放问题**：[开放问题](domain/open-questions.md)

## 图表

[图表索引](diagrams/README.md) 只列当前有效的阅读图；历史候选图在索引中单独标注，不作为当前模型入口。

## 实施与历史

- [当前实现边界与设计差距](implementation/current-boundaries.md)：代码现状、已落地边界和 TC-002 整改项。
- [实施与验收索引](implementation/README.md)：验收证据与后续计划。
- [ADR](adr/README.md)：已接受决策；[评审](reviews/README.md)：裁定依据与历史评审。
- [提案](proposals/README.md) 与阶段 2 候选知识只作探索材料，不自动成为当前设计。

状态、接受、发布和提交规则见 [AGENTS.md](../../AGENTS.md)。
