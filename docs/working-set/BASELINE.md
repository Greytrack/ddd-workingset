# Working Set Design Baseline

- **Status**: Accepted（事实与政策主题基线 + 首版领域模型候选基线 + ADR-0001～0005 全部 Accepted；未正式发布）
- **Baseline Version**: 首版领域模型候选基线 v1（阶段 4，基于 D-001～D-019 及阶段 4 补充）
- **Release Status**: Unreleased
- **Released Git Commit**: None（仓库已有 Git 提交，但尚无正式发布的设计基线提交）
- **Carrying Git Commit(s)**: `4f5c8fb`（阶段 4 规则与评审更新：BASELINE、ADR-0001、facts/glossary/invariants/aggregate-model、core-scenarios 等 11 文件）；`cc17d1c`（阶段 4 新增：ADR-0002～0004、首版基线候选、阶段 4 评审、domain-mainline 与 productspace-tracker-workingset 两图，8 文件）
- **Released Git Tag**: None
- **Last Updated**: 2026-11-11

## 当前基线状态

本页维护接受范围、版本与确认依据；领域规则见对应文档，阶段进度统一见[阶段 5 计划](reviews/2026-11-10-stage-5-contract-plan.md)。事实与政策主题、首版领域模型候选及 ADR-0001～0005 已接受；T1～T4 契约保持 Reviewed，具体裁定范围见下文。正式发布仍为 Unreleased。

## 候选基线（用户已接受，未正式发布）

用户于 2026-11-10 接受[首版领域模型基线候选 v1](domain/model-baseline-candidate-v1.md)的批准清单：D-001～D-019、核心聚合、ProductSpace、业务 BC 聚合、TrackerItem 契约及阶段 4 补充。确认依据见[阶段 3 决策包](reviews/2026-11-09-stage-3-architecture-review.md)与[阶段 4 评审](reviews/2026-11-10-stage-4-productspace-classification-review.md)。

当时暂缓的契约细节及后续裁定，分别见模型文档 §7 和本页「阶段 5 契约层」；不能把阶段 4 的待定清单当作当前未确认清单。[首版基线审核](reviews/2026-11-10-baseline-v1-audit.md)保留当时的观察项。

## 当前已确认领域文档

正式领域归档为 [事实与政策主题基线 v1](domain/facts-policy-baseline-v1.md)（Accepted）。原始范围与确认依据保留于 [阶段 3.1 确认矩阵与批准记录](reviews/2026-11-09-stage-3-1-rule-convergence.md)：

- R01—R17 的限定陈述及报告中列明的 CONCEPT-003/007/009、身份规则、KEEP_TARGET/Checkpoint、CONCEPT-014/015 的限定含义。
- R18 / POL-005：同一工作集 CommonId 唯一，同族只允许一个有效变体。
- R19 / FACT-006 补充：派生附件、关系均固定为派生时状态。

确认人：用户；直接依据见报告“用户批准记录与生效范围”。报告的冲突分析、候选 INV 推导和未解决协议不因本清单而被接受。[facts](domain/facts.md) 与 [glossary](domain/glossary.md) 是混合状态文档，仅上述限定内容属于本清单。该阶段 3.1 批次不整体接受 INV、候选聚合、图表、API 或未确认 FACT/POL 子句；后续接受范围按本页其他批次分别登记。承载此次接受内容的 Git Commit 尚未创建或登记。

## 已确认的组织架构约束

- `AC-001`：Working Set 属于企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。来源为用户于 2026-11-09 对既定企业架构的明确确认，详见 [Shared Kernel 边界](domain/shared-kernel-boundary.md) 和 [Context Map](diagrams/shared-kernel-context-map.md)。

`AC-001` 缩小了设计方案空间，但不接受任何 Working Set 内部聚合、限界上下文、API、事务或部署方案；本次仅接受上述规则与术语，不接受完整领域设计。

## 当前有效 ADR

- [ADR-0001：Tracker 独立聚合](adr/0001-tracker-independent-aggregate.md)：Accepted（D-001，2026-11-10）。
- [ADR-0002：LightTrackerItem 作为 Working Set 权威聚合](adr/0002-lighttrackeritem-authoritative-aggregate.md)：Accepted（D-007/D-008/D-012，2026-11-10）。
- [ADR-0003：COW 采用事件驱动最终一致与首次快照优先](adr/0003-cow-event-driven-final-consistency.md)：Accepted（D-009～D-018，2026-11-10）。
- [ADR-0004：ProductSpace 独立聚合与 WorkingSet 绑定模型](adr/0004-productspace-independent-aggregate.md)：Accepted（FACT-024～026、POL-012～014、INV-053～059、SCN-050～054；2026-11-10）。
- [ADR-0005：Merge 执行模型与 MergeEntry 行为契约](adr/0005-merge-execution-model.md)：Accepted（Q1～Q3 裁定：MergeEntry 为 MergeRound 内 Entity、ComparisonToken 必须绑双侧内容证据、Checkpoint 由 MergeRound 发起；2026-11-10）。

## 相关验收标准

已接受规则关联的业务结果见阶段 3.1 矩阵所列 SCN/B 场景。新增 [SCN-011/012](scenarios/core-scenarios.md) 分别验证单工作集唯一性及派生附件/关系固定；其验收表述仍 Draft，未运行测试。场景文档内其余候选预期和协议不整体接受。首切片的实际覆盖范围与证据见 [实施索引](implementation/README.md)。

## 已整理但未确认的候选知识

阶段 2 已形成以下 `Draft` 文档，供后续业务确认和正式架构评审使用；除上文明确纳入的限定子句外，其余内容不属于 Accepted 清单：

- [证据登记](domain/evidence-register.md)
- [候选统一领域语言](domain/glossary.md)
- [候选事实、政策、假设与技术约束](domain/facts.md)
- [候选业务不变量](domain/invariants.md)
- [开放问题](domain/open-questions.md)
- [候选 Context Map](domain/context-map.md)
- [Shared Kernel 边界](domain/shared-kernel-boundary.md)
- [候选领域设计图表](diagrams/README.md)
- [候选聚合边界](domain/aggregate-model.md)
- [竞争性聚合方案](proposals/aggregate-boundary-options.md)
- [核心候选场景](scenarios/core-scenarios.md)
- [阶段 2 设计审查](reviews/2026-11-09-stage-2-domain-discovery-review.md)

## 一致性裁定与开放问题

2026-11-11 的内容固定和接口责任裁定见[一致性修正记录](reviews/2026-11-11-consistency-reconciliation.md)，契约入口为 T3 §2.1 与 T4 §3.1。接受范围仅限用户确认的规则，关联 SCN-072/073。

剩余问题统一见[开放问题](domain/open-questions.md)；首切片门禁与实施待办见[阶段 5 计划](reviews/2026-11-10-stage-5-contract-plan.md)。已确认规则与待验证的技术前提应分别读取。

## 最近基线变更

| 日期 | 记录与确认依据 |
| --- | --- |
| 2026-11-13 | T7-A：用户确认 [创建 LRO 契约](domain/working-set-creation-lro-design.md)及终态前的 `Preparing` 行为。T7-B 的空范围异步任务、轮询与幂等受理见 [验收记录](implementation/t7-b-creation-task-verification.md)。正式发布状态沿用现有基线。 |
| 2026-11-13 | T7-C：非空范围受理、空间前置、非共享 Tracker 复制与绑定、共享 Tracker 复用及逐项结果已实现，见 [T7-C 验收](implementation/t7-c-binding-verification.md)。 |
| 2026-11-13 | T6-D：用户确认回并入口、来源审计与软删恢复更新规则，见 [T1 §8](domain/merge-execution-contract-v1.md#8-轮次终态与结果表达) 和 [T6-D 验收](implementation/t6-d-merge-back-verification.md)。`mvn -B clean test` 91 项通过。 |
| 2026-11-13 | T6-C：创建请求不再携带 CommonId；默认工作集服务端生成同一雪花作为 LocalId/CommonId，非默认工作集生成两个不等 ID，派生继承来源 CommonId；唯一冲突统一 409。见 [T6-C 验证](implementation/t6-c-identity-verification.md)；`mvn -B clean test` 88 项通过 |
| 2026-11-13 | T6-B 按本轮实施任务补充 [T1 §8.1](domain/merge-execution-contract-v1.md#81-执行中断与结果恢复t6-b) 的轮次并发及恢复边界，见 [SCN-074](scenarios/core-scenarios.md#scn-074) 和 [验收](implementation/t6-b-merge-recovery-verification.md)。接受范围与发布状态沿用现有基线。 |
| 2026-11-13 | 实施边界（用户确认）：交付物为 `sk.workingset` 的契约、规范与约束；`alm.requirement` 为占位/参考实现，各业务 BC 由专门团队实现并遵守 SK 契约；参考实现不覆盖业务细节 Guard，不得以其缺失反推 SK 契约（T4 §3.3、business-object-lifecycle-design） |
| 2026-11-13 | D16：软删即占用家族身份，只能恢复或永久删除（T2 §5、QUESTION-004）。D17：生命周期能力不产生 `Unknown`（设计文档）。D10：新增工作集入口与归属解析（`TrackerScopePort`、`CreateRequirementRequest.workingSetId`，T4 §4）。D12-A：JSONB 为切片实现，规模/保留/回填为后续专项（T1）。D15-B：Merge 条目应用独立提交、目标变体切换由 `RequirementMergeApplied` 事件在 SK 独立事务完成（T1 §5、T4 §3.2）；`mvn -B test` 83 项通过 |
| 2026-11-13 | C7：LTI 层补 `(working_set_id, common_id)` 唯一索引（`uq_lti_working_set_family`），与 requirements 的 WorkingSet 作用域家族唯一性一致，新增验收。D3-B：COW 物化的业务保存由业务 BC 独立提交，SK 物化凭据与 LTI 切换分事务，未完成部分经 Modulith 未完成事件重投收敛（T3 §7、T4 §3.2、D4 验证）；`mvn -B clean test` 79 项通过 |
| 2026-11-12 | D6：附件/关系不参与 Compare/Merge（T1、T3 §2.1）。D7：业务对象永久删除后不保留墓碑/内容/历史，查询返回 `NotFound` 是正确语义（T4 §5.1、T2 §5）；软删仍返回 `Deleted`。D10：新增工作集入口（归属解析）作为下一片实现点（T4 §4）。D13：`RequirementPurged` 改用 Modulith 监听并接入未完成事件重投。D14：`BusinessObject` 在 SK 内声明、`TrackerItem` 继承、`Requirement` 实现（T4 §3.1/§8）。同批修复：`readInheritedAt` 检查删除（C3）、`PREPARING` 改为 `DEPENDENCY_UNAVAILABLE`（C4）、`Summary.applied` 去重（C6）；`mvn -B test` 78 项通过 |
| 2026-11-12 | D11：现阶段不接入权限，等权限模型开发完毕后再接入；`Caller`/`authorize` 保持占位，不落地 `Denied`，见 [T4 §7](domain/cross-bc-contract-v1.md)。D4：用户选择方案 A，把 LightTrackerItem 校验与移除移回 SK，业务 BC 不再注入 SK 仓储；跨聚合用 `RequirementPurged` 事件交互，见 [T4 §3.1](domain/cross-bc-contract-v1.md) 与 [D4 边界验证](implementation/t5-d4-sk-boundary-verification.md)（`mvn -B test` 78 项通过） |
| 2026-11-12 | D5：用户裁定 WorkingSet/Tracker **不存在“停用”概念**，相关表述与代码已清除（`assertDisableAllowed` 移除，`mvn -B test` 77 项通过），[QUESTION-009.a](domain/open-questions.md) 关闭。D8：用户按 A（工作视图/范围清单）收尾关闭 [QUESTION-001](domain/open-questions.md)，与 S1～S7 实现及本 BASELINE 一致。D9：用户裁定关闭 [QUESTION-008](domain/open-questions.md) 已实现部分（登记前/后证据分界、普通字段决策优先、部分成功、新轮次、Checkpoint），剩余并发/重试/结果恢复协议转 `QUESTION-008.a` |
| 2026-11-12 | D1：`TrackerItem` 继承 `BusinessObject`，`BusinessObject` 声明 delete/undelete/delete2 公共能力，具体规则/guard/编排由各业务 BC 实现；设计见 [business-object-lifecycle-design.md](domain/business-object-lifecycle-design.md)，Draft，接口签名待评审。D2：Requirement 家族唯一性按 WorkingSet 作用域（用户选择 A），schema/索引已修正，见 [D2 验证](implementation/t5-d2-family-scope-verification.md) |
| 2026-11-12 | [S7 实施裁定](reviews/2026-11-12-s7-merge-implementation-decisions.md)：Compare 以登记为分界，登记后普通字段按决策优先；Requirement `delete`/`undelete`/`delete2` 最小生命周期；S7 验证见 [S7 验证结果](implementation/t5-s7-verification-result.md)，未发布 |
| 2026-11-12 | [FACT-028](domain/facts.md) 多级 WorkingSet 谱系核心规则：单父、`parentWorkingSetId`、空范围子 WS、直属父 Tracker 来源、相邻父子 Merge、中间 WS 不允许删除；WorkingSet/Tracker 不存在“停用”生命周期（同日裁定）；设计见 [working-set-lineage-design.md](domain/working-set-lineage-design.md)，Draft，未发布 |
| 2026-11-11 | T1 关闭、T2～T4 冻结：见下方契约登记及各议题评审；T1 Q4 见 ADR-0005 补充裁定 |
| 2026-11-10 | [阶段 4 收尾与 D-019](reviews/2026-11-10-stage-4-productspace-classification-review.md)、[首版基线审核](reviews/2026-11-10-baseline-v1-audit.md)；ADR-0001～0005 接受依据见各 ADR |
| 2026-11-09～10 | [阶段 3.1 规则确认](reviews/2026-11-09-stage-3-1-rule-convergence.md)、[分主题接受与 INV 审查](reviews/2026-11-10-invariant-evidence-review.md) |
| 2026-11-09 | [阶段 2 候选整理](reviews/2026-11-09-stage-2-domain-discovery-review.md)、[AC-001 边界确认](reviews/2026-11-09-stage-3-0-shared-kernel-boundary-review.md)；候选图表未纳入基线 |
| 2026-11-08 | 建立基线入口，当时尚无 Accepted 设计基线 |

## 按主题接受的治理规则

用户于 2026-11-10 确认允许分主题接受；依据见[逐条 INV 审查](reviews/2026-11-10-invariant-evidence-review.md)。状态、接受、发布和提交的规则统一见根目录 [AGENTS.md](../../AGENTS.md)。

## 发布与变更规则

基线更新必须遵循根目录 [AGENTS.md](../../AGENTS.md) 中的 `Baseline Change Protocol`。获得明确确认后，才可将具体领域文档或 ADR 纳入本清单；正式发布时记录对应 Git Commit，并仅在获得授权后创建 Git Tag。

## 阶段 3 候选评审登记

[阶段 3 决策包](reviews/2026-11-09-stage-3-architecture-review.md)保留责任 A/B/C、三套聚合方案、B01—B12 及 D-001～D-018 裁定过程；当前模型入口见上方候选基线。

## 阶段 5 契约层（2026-11-10 起）

以下登记沿用既有用户确认，不将整篇 Reviewed 契约升级为 Accepted，也不扩大各批次的确认范围。

| 主题 | 文档与状态 | 用户确认范围 | 确认依据 |
| --- | --- | --- | --- |
| T1 | [Merge 执行](domain/merge-execution-contract-v1.md)，Reviewed | Q1～Q4；ADR-0005 Accepted，含 2026-11-11 补充裁定 | [T1 评审](reviews/2026-11-11-t1-contract-review.md) |
| T2 | [绑定与删除](domain/binding-deletion-contract-v1.md)，Reviewed | K1～K3；POL-016、INV-064～068 | [T2 评审](reviews/2026-11-11-t2-binding-deletion-review.md) |
| T3 | [物化协议](domain/lighttrackeritem-materialization-contract-v1.md)，Reviewed | K1′～K3′；INV-069～071 | [T3 评审](reviews/2026-11-11-t3-materialization-review.md) |
| T4 | [跨 BC 公共契约](domain/cross-bc-contract-v1.md)，Reviewed | K1″、K2″及新 DDD 架构前提；K3″ 保持 Open | [T4 评审](reviews/2026-11-11-t4-cross-bc-review.md) |
| T5 | [T5-S0 最小纵向切片设计](implementation/t5-s0-minimal-slice-design.md)，Draft；S1～S7 已通过各自范围的验证 | ALM Requirement 普通字段；单实例异步 COW、恢复与升级；S5 首层绑定与删除；S6 多级谱系与相邻父子准入；S7 直属父子 Compare/Merge 与 Requirement 最小生命周期 | [实施证据索引](implementation/README.md) |

术语沿用 2026-11-11 裁定。`version` 为可空的发布大版本，`revision` 为手动推进的业务版次，`lock_version` 为提交时递增的乐观锁版本。

SCN-057～073 与配套图表由各契约链接。进度、下一步及门禁统一见[阶段 5 计划](reviews/2026-11-10-stage-5-contract-plan.md)。
