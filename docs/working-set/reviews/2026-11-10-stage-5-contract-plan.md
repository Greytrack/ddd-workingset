# 阶段 5：契约层计划（Contracts）

**状态：Draft（计划文件；不构成任何业务裁定、ADR 或基线发布授权）**

- 日期：2026-11-10
- 前提：[阶段 4 收尾](2026-11-10-stage-4-productspace-classification-review.md#8-阶段-4-收尾2026-11-10)已完成，[首版基线审核](2026-11-10-baseline-v1-audit.md)结论「通过（附观察项）」。
- 当前阶段门位置：Capabilities / Components / Interactions 已大致裁定；**Contracts 层基本空白**；Implementation 未开始。

## 1. 阶段目标

把已确认的业务规则收敛为**可测试的跨边界契约**，使 Implementation 有明确 seam、输入输出与失败语义可依。本阶段不重新开放已确认的 FACT / POL / INV 业务子句；发现语义缺口时回退到裁定流程，而不是在契约中暗补新概念。

## 2. 议题顺序与产出物

| 序 | 议题 | 关键输入 | 产出物 | 门禁 / 依赖 |
| --- | --- | --- | --- | --- |
| T1 | **Merge 执行协议**（B1，最高优先） | D-019、FACT-027、INV-061～063、SCN-055/056、`QUESTION-015` | ① 三方案比较（令牌+幂等键 / 每次重算+乐观校验 / 串行队列）；② SCN-057 起扩展场景（过期审阅、撤销晚到、DEFER 残留、部分成功后重试、未物化目标物化与修改同事务失败）；③ MergeRound 状态机与 MergeCheckpoint 字段映射契约草案；④ 是否新建 ADR-0005 的裁定请求 | 用户选定方案；确认 ADR-0005 取舍；依赖 D-019 不重开 |
| T2 | **绑定与删除契约**（B2） | FACT-024～026、POL-012～015、INV-053～060、ADR-0004 | SpaceBinding/TrackerBinding 内部结构与键、软删 vs 彻底删除、级联实现边界、默认 WorkingSet 约束；补充 INV/SCN | 需要确认删除联动的边界（阶段 4 遗留）；可能需 ADR-0004 增补 |
| T3 | **LightTrackerItem 物化协议**（B3） | D-008/D-009/D-010/D-017、INV-024～052、ADR-0002/0003 | 完整属性表（含物化标志、内容指针）、LocalId 预分配规则、跨 BC 首次物化一致性（业务保存成功但索引失败）、失败补偿 | 与 T1 的 Revision 字段映射联动；可能修订 ADR-0002/0003 |
| T4 | **跨 BC 公共契约**（B4） | AC-001、D-007、QUESTION-013/014、`shared-kernel-boundary.md` | TrackerItem 最小能力集、create/extend/resolve/diff/merge 的失败语义、「无权/不存在/已删除/历史不可用」四分、Shared Kernel 版本治理候选 | 需要四 BC 参与确认；QUESTION-014 仍开放 |
| T5 | **最小纵向切片**（C） | T1～T4 已确认部分 | `implementation/` 的 seam 说明 + 场景-期望表（可先无生产代码）；Red→Green 证据 | **仅在契约确认后启动**；建议首切片＝非共享派生 → 首次 COW → Query 解析 |

## 3. 工作方法（每个议题统一）

1. 从已确认 FACT/POL/INV 出发，**不新增业务裁定**；发现缺口即登记为 QUESTION 并请示。
2. 至少给出两套有实质差异的方案，附反例、失败模式与放弃条件（AGENTS 第 9 节）。
3. 场景先行：每条契约主张至少对应一个 SCN（含反例、并发、失败恢复）。
4. 产出评审记录到 `reviews/`；需要长期约束的决策新建 ADR，AI 只能起草 `Proposed`。
5. 用户确认后才更新领域文档与 `BASELINE.md`；发布 / Commit / Tag 分别授权。
6. 契约草案必须可测量：输入、输出、拒绝条件、幂等语义、可观察结果。

## 4. 进入 Implementation 的门禁

- T1～T4 中与首切片相关的契约草案已由用户确认；
- 公共 seam（聚合公开行为、应用用例合同、跨 BC 集成契约）书面约定；
- 场景-期望表可执行（表格驱动即可，不要求先有生产代码）；
- 无 P0 阻塞项：`QUESTION-015`（T1）必须闭合，`QUESTION-014` 至少给出候选治理方向。

## 5. 风险与依赖

- **四 BC 参与不可得**：T4 可能需要真实业务方输入；若不可得，T4 保持在候选并明确标注，不阻塞 T1～T3。
- **执行协议影响实现选型**：T1 的幂等键、令牌、Checkpoint 键会约束持久化与消息机制，需在契约中写明可替换边界。
- **图表渲染工具缺失**：当前环境无 `mmdc`；新增或修改图在发布前需在可渲染环境复核。
- **实现证据为零**：`implementation/` 为空，T5 之前不得宣称任何行为已验证。

## 6. 不在本阶段范围

- 数据库/表结构、ORM 映射、部署拓扑、消息中间件选型（属实现设计，须在契约内选择）。
- 重新裁定已确认的 D-001～D-019 业务规则。
- 正式发布、Git Commit、Tag。

## 7. 下一步

**T1 四项裁定已完成**（2026-11-10 Q1～Q3，2026-11-11 Q4；[ADR-0005](../adr/0005-merge-execution-model.md) Accepted 并于 2026-11-11 追加补充裁定）：Q1 MergeEntry 为 MergeRound 内 Entity；Q2 ComparisonToken 必须绑双侧内容证据（Revision 仅其中一项，证据形式留实现）；Q3 Checkpoint 确认由 MergeRound 在 `markAsMerged=true` 且 `Applied` 时发起；Q4（方案 B）一轮一次执行即终态、不可重开，未完成项须新建 MergeRound，`ApplyResult` 为本轮不可变快照。

产出：[Merge 执行行为契约 v1](../domain/merge-execution-contract-v1.md)（Draft，已同步 Q4）、[MergeEntry 生命周期图](../diagrams/merge-entry-lifecycle.md)（含 MergeRound 终态图）、SCN-057～060（过期审阅、撤销晚到、`DEFER` 残留与轮次终态、部分成功后以新轮次重做）。

**T1 已关闭**（2026-11-11，[T1 契约审核](../reviews/2026-11-11-t1-contract-review.md) 结论「通过并关闭」）：契约状态 `Reviewed`；Q1～Q4 全部关闭，重试与终态不再新增领域问题；遗留仅实现协议（Token 编码、幂等键、乱序实现、Checkpoint 字段映射），不阻塞后续。

**T2 已冻结**（2026-11-11，[T2 评审](../reviews/2026-11-11-t2-binding-deletion-review.md)）：[绑定与删除契约 v1](../domain/binding-deletion-contract-v1.md) 状态 Draft → Reviewed；K1～K3 用户确认（不可恢复删除、Tracker 删除三条件、子 WS 删除成员约束）；Shared 与非共享解绑前置完全区分；ProductSpace 删除须审查全部 SpaceBinding；配套 SCN-061～065、[绑定与删除生命周期图](../diagrams/binding-lifecycle.md)、POL-016、INV-064～068。遗留「停用」生命周期（QUESTION-009.a）与实现细节，不阻塞。

**T3 已冻结**（2026-11-11，[T3 评审](../reviews/2026-11-11-t3-materialization-review.md)）：[物化协议契约 v1](../domain/lighttrackeritem-materialization-contract-v1.md) 状态 Draft → Reviewed；K1′～K3′ 用户确认（独立 `materialized` 字段、LocalId 稳定复用、事件驱动最终一致）；旧「仅由来源字段推导」候选标 Superseded；配套 SCN-066～068、INV-069～071、[物化状态与首次物化时序](../diagrams/lighttrackeritem-materialization.md)。

**当前推进 T4：跨 BC 公共契约**——TrackerItem 最小能力集、create/extend/resolve/diff/merge 的失败语义、「无权/不存在/已删除/历史不可用」四分、Shared Kernel 版本治理（`QUESTION-014`）；输入为 AC-001、D-007、`QUESTION-013/014`、[Shared Kernel 边界](../domain/shared-kernel-boundary.md)、SCN-002、B01～B12。
