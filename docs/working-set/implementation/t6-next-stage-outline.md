# T6 下一阶段实施大纲

**状态：Draft（规划；未确认、未发布）**

- 依据：[BASELINE](../BASELINE.md)、[开放问题](../domain/open-questions.md)、[T1](../domain/merge-execution-contract-v1.md)～[T4](../domain/cross-bc-contract-v1.md)、[S7 验证](t5-s7-verification-result.md)、[D4 边界验证](t5-d4-sk-boundary-verification.md)
- 目标：收敛剩余开放问题与规模/并发能力，同时保持契约、实现与验收证据一致。
- 不在本阶段：权限接入（D11 延后）、附件/关系参与 Compare/Merge（D6 本版不做）、停用生命周期（D5 不存在该概念）。

## 实施边界（重要）

- **交付物是 `sk.workingset`（Shared Kernel）的契约、规范、约束与实现**；未来各业务团队模块对接时，只带出 `sk.workingset` 的内容。
- **`alm.requirement` 目前是占位/参考实现**，用于验证 SK 契约（`TrackerItem`、`TrackerItemReadPort`、`RequirementWritePort`、事件协作等），不代表 ALM 团队最终实现，也不要求在此补齐业务细节（如具体业务 Guard、字段规则、状态机）。
- T6 各切片的判断标准：先问“这是 SK 的规范/约束/机制，还是某个 BC 的业务细节？”前者属于本阶段；后者只做最小占位适配。
- 业务 BC 由各自团队按专业领域实现，但必须遵守 `sk.workingset` 的契约；SK 不得依赖任何 BC 实现（ArchUnit 强制）。

## 当前基线（T5 收尾状态）

- 单一 ALM Requirement 纵向切片（S1～S7）已可用；COW、Compare/Merge、绑定与删除均通过真实 PostgreSQL/Redis/Tomcat 验收。
- D3-B/D15-B 已把 COW 物化与 Merge 的业务写入拆为独立提交，LTI 切换由事件在 SK 独立事务完成（可重投）。
- D10 工作集入口、C11 软删候选过滤、C7 LTI 唯一索引、D16 软删占身份语义已落地。
- 全量测试 **84 项**（含 ArchUnit）；文档链接检查通过。

## 1. 切片顺序与目标

| 切片 | 目标 | 依赖 | 主要验收 | 风险/边界 |
| --- | --- | --- | --- | --- |
| **T6-A** | Compare 候选与删除语义收口（C11 的契约化） | S7、D7、D16 | 软删对象不进入候选；永久删除返回 `NotFound`；软删读取返回 `Deleted` | 需补 SCN 与新契约说明，避免只靠实现 |
| **T6-B** | Merge 并发、重试与结果恢复（QUESTION-008.a） | T1、S7、D15-B | 并发 Apply、重复提交、执行中断、跨实例重试的幂等与结果收敛 | 轮次并发与事件重投的边界；不重开已关闭的决策优先 |
| **T6-C** | 身份分配、去重与并发协议（QUESTION-004 剩余） | D2、C7、D16 | 新建/派生/回并的 CommonId/LocalId 分配；同族去重与冲突语义 | 软删占键已定，需明确应用层冲突映射，不只靠 DB 异常 |
| **T6-D** | 子 WS 新建与回并（QUESTION-005；D10 入口已完成） | D10、T4、T2 | 子 WS 新建归属、回并父 WS、身份等值 | 共享/非共享归属差异；回并的标识与去重 |
| **T6-E** | 规模、分页与保留（D12-A、QUESTION-012） | T1、S7 | JSONB 冻结比较的分页/保留/清理；规模压测；真实元数据回填 | 当前 JSONB 为切片实现，不得当作规模承诺 |
| **T6-F** | 谱系剩余约束（QUESTION-002 剩余） | FACT-028、S6 | Shared 父级一致性、同源多派生、sourceId 可变性、派生整批原子性、创建完成可观察时点 | 不与已确认的单父/来源约束冲突 |
| **T6-G** | 关系、Baseline 与治理（QUESTION-010/011/014） | T1/T4、AC-001 | Reference/Association 解析与修复；业务 Baseline 含义；Shared Kernel 版本治理 | 团队级决策，不阻塞前六片 |

## 2. 每片的交付物

每个切片至少包含：

1. 契约/设计的明确变更（T1～T4 或新评审记录）；
2. 至少一个新场景或反例（`scenarios/core-scenarios.md`）；
3. 公共 seam 上的验收测试（不测私有方法）；
4. 验证记录（`implementation/tX-*-verification.md`）；
5. `BASELINE.md` 的裁定登记（若涉及用户确认）。

## 3. 一致性与门禁

- 每次切片必须跑 `mvn -B clean test`（含 ArchUnit）与文档相对链接检查。
- 契约、实现、验证记录、BASELINE 四处必须同时更新；不允许只改代码或只改文档。
- 跨聚合事务必须显式声明策略（D3-B/D15-B 已定），新增跨聚合写入沿用“业务保存独立提交 + 事件驱动切换”的模式。
- 新增失败分类必须落在 T4 §5 的九分类内；不得把 `Deleted` 降级为 `NotFound`（永久删除除外，D7）。

## 4. 建议的下一步

**T6-B** 的 Merge 并发与恢复见[验收记录](t6-b-merge-recovery-verification.md)。等待用户确认后进入 **T6-C（身份分配/去重）**，再推进 **T6-D（回并）**。
T6-E/T6-F/T6-G 可按业务优先级并行或后置。

## 5. 未决判断（需用户确认）

1. T6 以 SK 契约为主、`alm.requirement` 仅作占位适配；是否接入第二个 BC（Part/DQA）用于验证跨 BC 契约，还是继续以 ALM 单样例验证。
2. QUESTION-014 的版本治理是否在 T6 内启动，还是保持团队开放项。
3. 规模专项（T6-E）是现在做压测，还是等真实数据回填后再做。
