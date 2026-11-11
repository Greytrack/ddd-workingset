# Working Set 知识库

**状态：Draft（知识治理入口；不接受任何具体领域结论）**

这里是 Working Set 领域知识的导航入口。[BASELINE.md](BASELINE.md) 是当前有效设计基线的首要入口。仓库和提交记录提供可追踪性，但文档只有在获得明确确认并按基线规则纳入后，才构成当前设计基线。本目录的建立不会改变 `pre-design/` 中任何材料的状态。

## 状态

一般知识文档使用：`Draft`、`Reviewed`、`Accepted`、`Superseded`、`Historical`。

ADR 使用：`Proposed`、`Accepted`、`Rejected`、`Superseded`。

状态含义、变更审批和证据优先级以根目录 [AGENTS.md](../../AGENTS.md) 为准。

## 导航

- [BASELINE.md](BASELINE.md)：当前基线状态、版本、有效领域文档、ADR 和验收标准清单；
- [domain/](domain/README.md)：统一领域语言、业务事实、不变量和领域模型；
- [scenarios/](scenarios/README.md)：业务场景、反例、边界与验收语义；
- [proposals/](proposals/README.md)：尚待比较和确认的候选设计；
- [diagrams/](diagrams/README.md)：可由 Git diff 审查的 Mermaid 领域设计图表；
- [adr/](adr/README.md)：具有真实权衡的重要决策及其演进；
- [reviews/](reviews/README.md)：设计评审记录、分歧与结论；
- [implementation/](implementation/README.md)：验收测试、实验、实现与设计的映射；
- [`pre-design/`](../../pre-design/)：前期 V3／V4、引用、比对与合并等历史输入。

## 阶段 2 候选知识入口

候选知识按主题见 [domain/](domain/README.md)，历史评审见 [reviews/](reviews/README.md)。这些目录包含混合状态材料，接受范围以 [BASELINE](BASELINE.md) 为准。

## 建议阅读顺序

1. 阅读根目录 `AGENTS.md`；
2. 阅读 [BASELINE.md](BASELINE.md)，确认当前状态和版本；
3. 阅读基线列出的当前有效领域文档和 ADR；
4. 阅读与任务相关的业务场景、验收标准和实现证据；
5. 必要时再查阅 `proposals/`、`pre-design/`、外部分析和对话等候选或历史输入。

只读取当前任务需要的材料。若文档状态、确认依据或替代关系不清楚，先记录为开放问题，不自行提升状态。

当前阶段与下一步见[阶段 5 计划](reviews/2026-11-10-stage-5-contract-plan.md)；历史决策过程见[阶段 3 决策包](reviews/2026-11-09-stage-3-architecture-review.md)。
