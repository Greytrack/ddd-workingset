# Working Set 知识库

**状态：Draft（唯一导航入口；不接受具体领域结论）**

当前有效的业务规则以 [BASELINE](BASELINE.md) 的接受范围为准。阅读顺序固定为：

1. [BASELINE](BASELINE.md)：当前有效基线与接受范围；
2. [统一领域语言](domain/glossary.md) 与 [聚合边界](domain/aggregate-model.md)；
3. [Shared Kernel 边界](domain/shared-kernel-boundary.md) 与 [Context Map](domain/context-map.md)；
4. 按主题读取 [T1～T4、T7 契约](#当前契约)；
5. 需要追溯原因时再读 [ADR](adr/README.md) 与 [评审](reviews/README.md)。

## 当前权威文档

### 领域与边界

- [统一领域语言](domain/glossary.md)
- [聚合边界](domain/aggregate-model.md)
- [Shared Kernel 边界](domain/shared-kernel-boundary.md)
- [Context Map](domain/context-map.md)
- [事实与政策主题基线 v1](domain/facts-policy-baseline-v1.md)（Accepted）
- [不变量](domain/invariants.md)、[开放问题](domain/open-questions.md)

### 当前契约

- [T1 Merge 执行](domain/merge-execution-contract-v1.md)
- [T2 绑定与删除](domain/binding-deletion-contract-v1.md)
- [T3 LightTrackerItem 物化](domain/lighttrackeritem-materialization-contract-v1.md)
- [T4 跨 BC 公共契约](domain/cross-bc-contract-v1.md)
- [T7 工作集创建/扩展 LRO](domain/working-set-creation-lro-design.md)
- [多级 Working Set 谱系](domain/working-set-lineage-design.md)

### 实现与运行

- [当前实现边界与设计差距](implementation/current-boundaries.md)：唯一描述“代码现在怎样”的页面。
- [实施与验收索引](implementation/README.md)：当前验收证据与后续计划。
- [CQRS 查询侧实施记录](implementation/cqrs-query-side.md)。

## 图表

[图表索引](diagrams/README.md) 只列当前阅读图；历史候选图单独标注，不作为现行模型入口。

## 历史与探索

以下材料保留追溯价值，但不作为当前实现或验收依据：

- [历史评审](reviews/README.md)：阶段 2/3、T1～T5 过程与已关闭裁定；
- [实施历史](implementation/README.md#历史验收)：T5 与旧 T7-D 验收；
- [提案](proposals/README.md)：探索方案与备选模型；
- [候选 Context Map](domain/context-map.md) 中的候选标记仍按 BASELINE 解释。

状态、接受、发布和提交规则见 [AGENTS.md](../../AGENTS.md)。
