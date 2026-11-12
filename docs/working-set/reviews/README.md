# Reviews

**状态：Draft（目录用途；不接受任何具体评审结论）**

存放对领域模型、提案、ADR、验证证据或实现偏差的评审记录。评审记录应忠实保留异议、未决问题和所检查的证据，不把“已评审”偷换成“已接受”。

新评审可从 [REVIEW-TEMPLATE.md](REVIEW-TEMPLATE.md) 开始，并链接被评审对象及后续处理结果。

## 当前评审

- [2026-11-09-stage-2-domain-discovery-review.md](2026-11-09-stage-2-domain-discovery-review.md)：阶段 2 证据审查、冲突与风险。
- [2026-11-09-stage-3-0-shared-kernel-boundary-review.md](2026-11-09-stage-3-0-shared-kernel-boundary-review.md)：Shared Kernel 组织约束、四个业务 BC 外框及待确认合同。

- [阶段 3 正式架构评审](2026-11-09-stage-3-architecture-review.md)：边界、三方案、风险、验证与八项裁定。

阶段 3.1 的限定确认矩阵、候选首版清单及不变量推导见 [规则收敛报告](2026-11-09-stage-3-1-rule-convergence.md)。全部推导仍待批准。

- [2026-11-10 候选不变量逐条审查](2026-11-10-invariant-evidence-review.md)：区分已接受测试依据与仍待批准的 INV 全文。
- [2026-11-10 阶段 4 集中评审](2026-11-10-stage-4-productspace-classification-review.md)：ProductSpace/Classification/绑定、删除语义与 D-019 Diff/Merge 衔接裁定。
- [2026-11-10 首版基线审核](2026-11-10-baseline-v1-audit.md)：首版领域模型基线 v1 静态一致性审核；结论「通过（附观察项）」，不构成发布/Commit/Tag 授权。
- [2026-11-10 阶段 5 契约层计划](2026-11-10-stage-5-contract-plan.md)：契约层议题顺序（T1 Merge 执行协议 → T2 绑定与删除 → T3 物化协议 → T4 跨 BC 契约 → T5 最小纵向切片）、产出物与门禁。
- [2026-11-11 T1 契约审核](2026-11-11-t1-contract-review.md)：T1 通过并关闭（Reviewed）；重试与终态话题不再新增领域问题，进入 T2。
- [2026-11-11 T2 评审（冻结）](2026-11-11-t2-binding-deletion-review.md)：K1～K3 用户确认落地、4 项一致性修正、旧表述清理；绑定与删除契约 v1 冻结为 Reviewed，进入 T3。
- [2026-11-11 T3 评审（冻结）](2026-11-11-t3-materialization-review.md)：K1′～K3′ 用户确认落地（独立 materialized 字段、LocalId 稳定复用、事件驱动最终一致）、旧推导候选标 Superseded；物化协议契约 v1 冻结为 Reviewed，进入 T4。
- [2026-11-11 T4 评审（冻结）](2026-11-11-t4-cross-bc-review.md)：K1″（只读 TrackerItemReadPort + BC 自管写入）与 K2″（九分类公共失败语义）用户确认落地、架构前提（新 DDD、旧模块为历史）记录；K3″ 保持团队开放项；跨 BC 契约 v1 冻结为 Reviewed，进入 T5。
- [2026-11-11 一致性修正](2026-11-11-consistency-reconciliation.md)：两项用户裁定（派生内容固定边界 A、TrackerItem 实现责任 B）与文档一致性修正；含 SCN-072/073。
- [2026-11-11 T5-S0 设计审核](2026-11-11-t5-s0-design-review.md)：首切片设计、架构/COW 时序图、AT-01～AT-05 映射与 S1～S4 任务清单审核通过；S1 待 P1～P5 确认后交 Codex。
- [2026-11-12 多级 WorkingSet 谱系评审](2026-11-12-working-set-lineage-review.md)：FACT-028 用户裁定落地（单父、`parentWorkingSetId`、空范围子 WS、直属父 Tracker 来源、相邻父子 Merge、中间 WS 删除/停用约束）；设计 Draft，开放问题见记录。
- [2026-11-12 S7 Merge 实施裁定](2026-11-12-s7-merge-implementation-decisions.md)：Compare 以登记为分界、普通字段决策优先、Requirement `delete`/`undelete`/`delete2` 最小生命周期；S7 验证 76 项通过。
