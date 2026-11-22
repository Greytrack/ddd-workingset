# Working Set 知识库

**状态：Draft（唯一导航入口；不接受具体领域结论）**

当前有效的业务规则以 [BASELINE](BASELINE.md) 的接受范围为准。阅读顺序固定为：

1. [BASELINE](BASELINE.md)：当前有效基线与接受范围；
2. [统一领域语言](domain/glossary.md) 与 [聚合边界](domain/aggregate-model.md)；
3. [Shared Kernel 边界](domain/shared-kernel-boundary.md) 与 [Context Map](domain/context-map.md)；
4. 按主题读取 [T1～T4、T7 契约](#当前契约)；
5. 需要追溯原因时再读 [ADR](adr/README.md) 与 [评审](reviews/README.md)。

## 单一权威与去重规则

同一主题只保留一处权威声明；其他文档用链接引用，不复制规则。发现冲突时先回到权威页，不在下游文档自行裁定。

| 主题 | 权威页 | 其他页面的写法 |
| --- | --- | --- |
| 当前接受范围 | [BASELINE](BASELINE.md) | 只链接，不复述接受范围 |
| 业务事实与政策 | [事实与政策主题基线 v1](domain/facts-policy-baseline-v1.md) | `facts.md` 只保留候选、证据与待确认项 |
| 聚合边界 | [聚合边界](domain/aggregate-model.md) | 图和提案只链接，不另立聚合规则 |
| Shared Kernel 定位 | [Shared Kernel 边界](domain/shared-kernel-boundary.md) + `AC-001` | Context Map 只表达关系，不重定义定位 |
| 跨 BC 协作 | [T4](domain/cross-bc-contract-v1.md) | [当前实现边界](implementation/current-boundaries.md) 只记录实现差距 |
| LTI 物化 | [T3](domain/lighttrackeritem-materialization-contract-v1.md) | 实施验收只记录证据 |
| Merge 执行 | [T1](domain/merge-execution-contract-v1.md) + [ADR-0005](adr/0005-merge-execution-model.md) | 评审只记录过程与异议 |
| 绑定与删除 | [T2](domain/binding-deletion-contract-v1.md) | 场景页只描述可观察行为 |
| 创建/扩展 | [T7](domain/working-set-creation-lro-design.md) | 实施记录只记录阶段和结果 |
| 代码现状 | [当前实现边界](implementation/current-boundaries.md) | 不得用代码现状反推业务规则 |
| 图表 | [图表索引](diagrams/README.md) | 图是正文的视觉摘要，不另立规则 |

## 文档状态与历史

- `Draft` / `Reviewed` 不等于 `Accepted`；只有 BASELINE 列出的接受范围才是当前基线。
- T5-S0～S7、D2/D4、旧 T7-D 属于历史实施证据；从[实施索引的历史验收](implementation/README.md#历史验收)进入，不从这些文档复制当前规则。
- 旧同步写 Port、`WorkingSetApplicationService`、`RequirementAdapter` 等名称只保留在历史记录中；当前入口见[已移除的旧声明](implementation/current-boundaries.md#5-已移除的旧声明)。

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
