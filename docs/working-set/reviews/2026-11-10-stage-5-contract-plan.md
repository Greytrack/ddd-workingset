# 阶段 5：契约层计划（Contracts）

**状态：Draft（计划文件；不构成任何业务裁定、ADR 或基线发布授权）**

- 日期：2026-11-10
- 前提：[阶段 4 收尾](2026-11-10-stage-4-productspace-classification-review.md#8-阶段-4-收尾2026-11-10)已完成，[首版基线审核](2026-11-10-baseline-v1-audit.md)结论「通过（附观察项）」。

## 1. 阶段目标

把已确认的业务规则收敛为**可测试的跨边界契约**，使 Implementation 有明确 seam、输入输出与失败语义可依。本阶段不重新开放已确认的 FACT / POL / INV 业务子句；发现语义缺口时回退到裁定流程，而不是在契约中暗补新概念。

## 2. 议题顺序与产出物

| 议题 | 当前状态 | 契约与验证入口 |
| --- | --- | --- |
| T1 Merge | 已关闭 | [行为契约](../domain/merge-execution-contract-v1.md)、[评审](2026-11-11-t1-contract-review.md) |
| T2 绑定与删除 | 已冻结 | [行为契约](../domain/binding-deletion-contract-v1.md)、[评审](2026-11-11-t2-binding-deletion-review.md) |
| T3 物化 | 已冻结 | [行为契约](../domain/lighttrackeritem-materialization-contract-v1.md)、[评审](2026-11-11-t3-materialization-review.md) |
| T4 跨 BC | Working Set 侧已冻结；K3″ 团队开放 | [行为契约](../domain/cross-bc-contract-v1.md)、[评审](2026-11-11-t4-cross-bc-review.md) |
| T5 最小纵向切片 | 已验证 S1～S7 的切片范围 | [implementation/](../implementation/README.md) |

各议题的确认范围统一由 [BASELINE](../BASELINE.md) 登记；备选方案、裁定过程与反例保留在评审和契约中。

## 3. 工作方法（每个议题统一）

遵循根目录 [AGENTS.md](../../../AGENTS.md) 的阶段门、方案比较与验证规则。每条契约关联场景，测试通过公共 seam 验证可观察结果；语义缺口返回设计层处理。

## 4. 进入 Implementation 的门禁

- T1～T4 中与首切片相关的契约草案已由用户确认；
- 公共 seam（聚合公开行为、应用用例合同、跨 BC 集成契约）书面约定；
- 场景-期望表可执行（表格驱动即可，不要求先有生产代码）；
- 无 P0 阻塞项：`QUESTION-015`（T1）必须闭合，`QUESTION-014` 至少给出候选治理方向。

## 5. 风险与依赖

- K3″ 版本治理待四 BC 共同评审；已有候选方向，不作为 T5 首切片默认阻塞。
- Token、幂等与 Checkpoint 见 [S7 验证](../implementation/t5-s7-verification-result.md)。事件顺序与跨实例边界见 [S4 验证](../implementation/t5-s4-verification-result.md)。
- 图表渲染核验见[图表索引](../diagrams/README.md)。
- 其他问题统一见[开放问题](../domain/open-questions.md)。

## 6. 不在本阶段范围

- 数据库/表结构、ORM 映射、部署拓扑、消息中间件选型（属实现设计，须在契约内选择）。
- 重新裁定已确认的 D-001～D-019 业务规则。
- 正式发布、Git Commit、Tag。

## 7. 后续范围

实施进度与各切片证据见[实施索引](../implementation/README.md)。

下一轮候选为 T3 附件与关系、Privilege 最小接入。批量删除顺序、规模与保留策略、真实平台元数据回填仍待后续切片。（2026-11-12 更新：用户裁定 WorkingSet/Tracker 不存在“停用”概念，原“停用”待办项已删除。）
