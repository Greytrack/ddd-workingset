# Working Set 设计（V4）

**状态：Draft（唯一导航入口；不接受具体领域结论）**

当前有效的业务规则以 [BASELINE](01-baseline.md) 的接受范围为准。目录按**阅读顺序**编号：编号即章序，历史材料统一收在
[archive](archive/)。

## 阅读路径

1. [BASELINE](01-baseline.md)：当前有效基线与接受范围；
2. [统一领域语言](02-language.md)：术语、身份规则与书写约定；
3. [事实、政策与不变量](03-rules.md)：`FACT` / `POL` / `INV` 与已接受的限定陈述 `R01—R19`；
4. [领域模型与边界](04-model.md)：聚合、Context Map、Shared Kernel；
5. [契约](05-contracts/README.md)：T1～T4、T7 与谱系设计；
6. [实现现状](07-implementation.md) → [后续计划](08-plan.md)；
7. 需要追溯原因时：[裁定时间线](06-decisions/timeline.md) → [归档评审](archive/reviews/)。

## 文档地图

| 章 | 文档 | 回答什么 | 状态 |
|----|------|----------|------|
| 01 | [基线](01-baseline.md) | 现在有效的是什么 | Accepted（接受范围见正文） |
| 02 | [统一领域语言](02-language.md) | 术语怎么用 | Draft |
| 03 | [事实、政策与不变量](03-rules.md) | 规则编号、状态与已接受范围 | Draft（混合登记；§7 已接受） |
| 04 | [领域模型与边界](04-model.md) | 聚合与上下文如何划分 | Draft |
| 05 | [契约](05-contracts/README.md) | 对外承诺什么 | Reviewed |
| 06 | [决策记录](06-decisions/README.md) | 为什么这样定 | Accepted（ADR 各自状态） |
| 07 | [实现现状](07-implementation.md) | 代码现在怎样 | Reviewed |
| 08 | [后续计划](08-plan.md) | 还没决定什么、下一步做什么 | Draft |
| 09 | [业务场景集](09-scenarios.md) | 可观察行为是什么 | Draft |
| 10 | [图表](10-diagrams/README.md) | 视觉摘要 | 各图状态见目录页 |
| — | [归档](archive/) | 历史证据与已关闭裁定 | Historical |

## 单一权威与去重规则

同一主题只保留一处权威声明；其他文档用链接引用，不复制规则。发现冲突时先回到权威页，不在下游文档自行裁定。

| 主题 | 权威页 | 其他页面的写法 |
| --- | --- | --- |
| 当前接受范围 | [BASELINE](01-baseline.md) | 只链接，不复述接受范围 |
| 业务事实与政策 | [事实、政策与不变量](03-rules.md) | 场景页只描述可观察行为 |
| 聚合边界 | [领域模型与边界](04-model.md) | 图和提案只链接，不另立聚合规则 |
| Shared Kernel 定位 | [领域模型 §5](04-model.md#5-shared-kernel-边界) + `AC-001` | Context Map 只表达关系，不重定义定位 |
| 跨 BC 协作 | [T4](05-contracts/t4-cross-bc.md) | [实现现状](07-implementation.md) 只记录实现差距 |
| LTI 物化 | [T3](05-contracts/t3-lti-materialization.md) | 实施验收只记录证据 |
| Merge 执行 | [T1](05-contracts/t1-merge-execution.md) + [ADR-0005](06-decisions/adr-0005-merge-execution-model.md) | 评审只记录过程与异议 |
| 绑定与删除 | [T2](05-contracts/t2-binding-deletion.md) | 场景页只描述可观察行为 |
| 创建/扩展 | [T7](05-contracts/t7-creation-lro.md) | 实施记录只记录阶段和结果 |
| 代码现状 | [实现现状](07-implementation.md) | 不得用代码现状反推业务规则 |
| 裁定顺序 | [裁定时间线](06-decisions/timeline.md) | ADR 只记录决策与依据 |
| 图表 | [图表索引](10-diagrams/README.md) | 图是正文的视觉摘要，不另立规则 |

## 文档状态与历史

- `Draft` / `Reviewed` 不等于 `Accepted`；只有 BASELINE 列出的接受范围才是当前基线。
- T5-S0～S7、D2/D4、旧 T7-D 属于历史实施证据；从[实现现状 §7](07-implementation.md#7-验收证据索引)进入，不从这些文档复制当前规则。
- 旧同步写 Port、`WorkingSetApplicationService`、`RequirementAdapter` 等名称只保留在历史记录中；当前入口见[已移除的旧声明](07-implementation.md#5-已移除的旧声明)。
- 历史评审、历史提案与阶段材料统一在 [archive](archive/) 下，按原目录分为 `archive/reviews/`、`archive/implementation/`、`archive/proposals/`、`archive/domain/`。

## 图表

[图表索引](10-diagrams/README.md) 只列当前阅读图；历史候选图单独标注，不作为现行模型入口。

## 归档

以下材料保留追溯价值，但不作为当前实现或验收依据：

- [历史评审](archive/reviews/README.md)：阶段 2/3/4/5、T1～T7 过程与已关闭裁定；
- [历史实施验收](archive/implementation/)：T5 与旧 T7-D 验收；
- [历史提案](archive/proposals/)：探索方案与备选模型；
- [历史领域材料](archive/domain/)：证据登记、首版模型候选、业务对象生命周期设计。

状态、接受、发布和提交规则见 [AGENTS.md](../../AGENTS.md)。
