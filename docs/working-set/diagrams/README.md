# Working Set 领域设计图表

**状态：Draft（目录治理规则）**

本目录保存可由 Git diff 审查的 Mermaid 源图。图表是领域文档的可视化视角，不是独立事实来源；当图与文字冲突时，应按 `AGENTS.md` 的证据和基线规则处理并修复不一致。

## 当前图表

- [domain-concept-relationships.md](domain-concept-relationships.md)：阶段 2 候选概念关系图；
- [candidate-context-map.md](candidate-context-map.md)：Working Set 内部候选 Context Map；
- [shared-kernel-context-map.md](shared-kernel-context-map.md)：`AC-001` 企业 Shared Kernel 与四个业务 BC 外框。

前两张图为 `Draft / Candidate`。Shared Kernel 外框图为 `Reviewed`，其中 `AC-001` 约束已 Confirmed，但它不是 Accepted 内部领域模型，也未发布为正式设计基线。

## 渐进交付清单

| 设计阶段／触发条件 | 最小图表 | 当前状态 |
| --- | --- | --- |
| 领域发现出现多个核心概念及歧义 | 概念关系图 | 已创建 Candidate |
| 出现多个候选模型所有者和跨边界协作 | 候选 Context Map | 已创建 Candidate |
| 战略上下文获评审 | 正式 Context Map，含上下游和集成模式 | 待阶段 3 |
| 某聚合方案进入正式比较 | 该方案的领域类图与聚合边界图 | `AP-001/002/003` 均待业务问题闭合后逐步创建 |
| 生命周期或长流程进入契约评审 | 状态图／时序图／事件流程图 | 待相应场景确认 |
| Shared、COW、Merge、并发设计拟接受 | 能解释行为、证据与一致性边界的专项图 | 接受前必须完成 |

## Mermaid 约定

1. 图表保存在 Markdown 的 `mermaid` fenced code block 中，不只保存 PNG。
2. 每个文件只承担一个清晰视角；复杂主题拆图，不用一张“全景图”塞入所有对象。
3. 节点使用稳定、语义化 ID；显示标签可以使用中英文领域术语。
4. 优先使用通用语法：`flowchart`、`classDiagram`、`stateDiagram-v2`、`sequenceDiagram`。
5. 类图必须用 stereotype 或图例标明 Aggregate Root、Entity、Value Object、Domain Service；跨聚合关系标注 `ID reference`。
6. 图外文字必须声明状态、来源、假设、相关文档和未表达范围。
7. 修改关联设计时同步更新图；无法同步时先把图标记为 `Superseded`，不得让旧图继续充当当前模型。
8. Accepted 图必须被 `BASELINE.md` 明确列出；Git 提交或 Mermaid 可渲染本身都不代表设计已接受。
