# Working Set 开放问题

**状态：Draft（只列当前仍需裁定的问题；已关闭问题见文末索引）**

本页只保留会影响身份、历史、合法状态、契约或实现的开放问题。历史裁定过程不在此重复：D-001～D-019 见[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)，D-019 与阶段 4 见[阶段 4 评审](../reviews/2026-11-10-stage-4-productspace-classification-review.md)，T1～T7 见各契约与实施验收记录。

## 当前开放问题

| 优先级 | 编号 | 问题 | 可选答案 | 影响与阻塞 | 建议验证 |
| --- | --- | --- | --- | --- | --- |
| P0 | `QUESTION-002.j` | **同一（空间，分类）有多个 Tracker 时，Compare/Merge 如何配对？** | A 左侧全部 Tracker × 右侧全部 Tracker；B 只按直接 `sourceId` 关系配对；C 先按 `source_id`／`is_default`／名称过滤再配对 | 决定候选对生成、条目身份与 UI 展示；T6-F 已允许同源多派生，但未定配对规则 | 以“同源两个派生子 Tracker”和“同空间不同来源”两个场景验证，确保不产生伪配对 |
| P0 | `QUESTION-002.l` | **T6-F“批量派生整批原子”与 T7 创建工作集“允许部分成功”是否是同一范围？** | A 两个不同操作：T6-F 批量派生全成或全不成，T7 创建按步骤允许部分成功（推荐）；B 统一为部分成功；C 统一为整批原子 | 决定 T7 的复制/绑定/索引失败语义，以及 T6-F 批量派生任务的可见性；当前 T7 已实现部分成功，T6-F 未实现 | 以“部分 Tracker 复制失败”验证 T7；以“批量派生一项失败”验证 T6-F |
| P0 | `QUESTION-006` | **历史按 Source Cut 回读的保留期限、读取与删除守卫** | 已确认：首次编辑所需派生内容必须可读；永久删除不保留历史；附件/关系固定为派生时状态。剩余：保留多久、删除来源前的守卫、历史不可用的失败语义 | 决定 COW、Compare、审计与来源删除顺序；当前实现只保证已存快照可读，未定义保留期 | 覆盖单版次对象、`delete2`、来源删除后首次编辑、历史缺失四类场景 |
| P0 | `QUESTION-010` | **Reference/Association 剩余可见性与删除守卫** | 已裁定 C：Reference = 逻辑 Family + 可选钉版/物理定位；Association = 具体 Variant 端点。剩余：目标缺失、无权、软删、永久删除、后续纳入、钉版不可读六类的可见性与守卫 | 决定 T7-E 的关系表、悬空表示、修复事件与删除顺序 | 按六类场景做关系构建/修复验收；关系不参与 Compare/Merge（D6） |
| P1 | `QUESTION-004` | **CommonId 跨实例并发与去重协议** | 已确认：默认 WS 同一雪花、非默认两个不等雪花、派生继承 CommonId、软删占键、冲突 409。剩余：多实例并发生成/校验、重试与唯一约束的兜底边界 | 决定唯一索引、ID 生成器与冲突重试；不重开身份语义 | 并发创建/派生/回并 + 唯一约束冲突注入 |
| P1 | `QUESTION-008.b` | **同一 MergeEntry 内字段级 `DEFER` 与 `ACCEPT` 能否混用？** | A 任一 `DEFER` 使整条条目 `Deferred`，不写任何字段（当前实现）；B 应用 `ACCEPT` 字段，`DEFER` 字段留待新轮次；C 禁止同一条目混用 | 决定 `MergeDecision` 校验、`EntryResult` 表达与 Checkpoint 确认条件；当前实现选择 A 但契约未明确 | 以“一字段接受、一字段延迟”验证结果与后续轮次 |
| P1 | `QUESTION-008.c` | **Checkpoint 命中使用的“版次”口径** | A 统一用变体业务 Revision（Source-backed 侧为 LTI Revision；当前实现）；B 用实际读取的内容版次（Source-backed 侧为 `SourceRevision`）；C 双侧分别按状态取版次并同时保存两者 | 决定 Compare 隐藏已处理差异与 Confirm 写入的 revisionA/B 是否总能对齐；D-018 允许 LTI Revision 与业务 Revision 不同步 | 以 Source-backed 目标 + 普通编辑 + 显式升级三类组合验证命中/过时 |
| P1 | `QUESTION-011` | **业务 Baseline 的含义** | A Snapshot；B 发布清单；C Merge Base；D 多概念并存但分别命名 | 决定不可变性、保留、引用语义；避免与仓库 Design Baseline、Merge Base 混淆 | 收集业务人员使用“基线”的原句与操作；T6-G |
| P2 | `QUESTION-012` | **Working Set 与 Binding 的规模、并发上限** | A 小集合单聚合；B 大集合版本化 Manifest；C 分片准备 + 发布指针 | 决定 WorkingSet 聚合装载/热点与是否引入 Manifest；不阻塞当前语义 | 采集 P95/P99 数量、写并发、发布频率与允许时延 |
| P2 | `QUESTION-014` | **四个 BC 共同依赖的 Shared Kernel 如何治理版本与兼容性** | A 共同发布；B 版本化 Published Language；C Adapter/ACL 隔离演进 | 团队治理待办，不阻塞 T5/T6/T7 当前实现 | 访谈四 BC 的发布责任、升级节奏、兼容期限和变更审批者 |
| P2 | `QUESTION-017` | **T7 创建任务的取消、补齐与结果保留** | A 不提供取消/补齐，失败后人工按 T2 删除叶子 WS；B 提供取消；C 提供基于已有子 WS 的补齐任务；结果保留期另定 | 决定任务表生命周期、失败明细保留与恢复入口；当前只支持终态轮询 | 以长任务中断、部分成功、终态后清理验证 |
| P2 | `QUESTION-018` | **T7-E 关系构建失败后的重试与修复边界** | A 只记录失败明细，人工修复；B 自动重投；C 定时对账修复 | 决定 Reference/Association 的最终一致责任与幂等键 | 与 `QUESTION-010` 六类场景合并验证 |

`QUESTION-002.k` 已关闭，见 [Shared 默认与删除补位裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md#43-shared-默认与删除补位question-002k2026-11-17-用户确认)。

## 已关闭问题索引

以下问题已经用户裁定或由后续契约关闭；保留一行结论和依据，不再作为当前待裁定项。历史论证见对应评审与契约。

| 编号 | 结论 | 依据 |
| --- | --- | --- |
| `QUESTION-001` | Working Set 是工作视图/范围清单，不是分支本体或 Workspace+Release Scope | 2026-11-12 用户裁定；[S1～S7 验收](../implementation/README.md) |
| `QUESTION-002.a` | `parentId` 是同工作集直属结构父 | D-003；[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md#d-003) |
| `QUESTION-002.b` | `sourceId` 是直接父级工作集来源，不是最初来源 | D-003 |
| `QUESTION-002.c` | 允许多级逐级派生 | D-003 |
| `QUESTION-002.d1` | 创建完成后 `parentId` 不可修改 | D-006 |
| `QUESTION-002.d2` | `sourceId` 创建后不可变 | T6-F（2026-11-16） |
| `QUESTION-002.e` | 同一工作集、同一（空间，分类）允许多个 Tracker；引入 `is_default` | T6-F（2026-11-16） |
| `QUESTION-002.f` | Shared `parentId` 由默认工作集维护；子工作集允许悬挂，能构树则构树 | T6-F（2026-11-16） |
| `QUESTION-002.g` | 父也选中时默认继承，创建前可显式重组 | D-005/D-006 |
| `QUESTION-002.h` | Item 归属关闭：业务 BC 拥有业务对象聚合，SK 只负责公共身份与工作集机制 | D-007 |
| `QUESTION-002.i` | 批量派生整批原子（LRO 范式） | T6-F（2026-11-16） |
| `QUESTION-003` | 现阶段不接入权限；`Caller`/`authorize` 保持占位，待权限模型完成后接入 | D11（2026-11-12） |
| `QUESTION-005` | 原生新建 Item 由服务端分配身份；回并经直属父子 Merge CREATE 承载 | D10、T6-C、T6-D |
| `QUESTION-007` | Compare/Merge 只允许直属父子双向，兄弟/跨代拒绝 | POL-004、FACT-007 |
| `QUESTION-008` | 字段级 Merge 的执行部分已由 S7 落地；剩余并发恢复转 `QUESTION-008.a` | [S7 验证](../implementation/t5-s7-verification-result.md) |
| `QUESTION-008.a` | 执行中断后接续原轮次；并发 Apply 返回 `RoundInProgress`；业务提交凭据恢复 | T6-B、T1 §8.1 |
| `QUESTION-009` | 仅支持不可恢复的领域删除；软删/归档/恢复不纳入 T2 本版；绑定移除与聚合删除分开 | T2 K1～K3、POL-016 |
| `QUESTION-009.a` | WorkingSet/Tracker 不存在“停用”生命周期 | 2026-11-12 用户裁定 |
| `QUESTION-013` | 四 BC 各自校验整个对象并决定提交；SK 管理选择、协调与结果；两层接口并存 | T4 K1″/K2″ |
| `QUESTION-016` | 所有 BC 的所有 Object 都具备 `delete`/`undelete`/`delete2` 能力声明；具体规则/guard 由各 BC 实现 | D14（2026-11-12） |
| `QUESTION-015` | Diff/Merge 行为契约关闭；Q1～Q4 已裁定，重做须新建轮次 | T1、ADR-0005 |
| T6-E | Compare 不持久化；一页 1～50 条决策；`requestId` 幂等；异步 LRO | [2026-11-15 裁定](../reviews/2026-11-15-compare-not-persisted-decision.md)、[T6-E 验收](../implementation/t6-e-merge-round-verification.md) |
| T6-F | Shared 允许悬挂；同源多派生 + `is_default`；`sourceId` 不可变；批量派生整批原子；完成即可见 | [T6-F 裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md) |
| Checkpoint | 身份 = 两侧 Tracker 分支 + 家族；方向只是属性；WorkingSet 不参与；同一 Tracker 对禁止并发合并 | [Checkpoint 修正](../reviews/2026-11-16-merge-checkpoint-legacy-alignment.md) |

## 当前推进顺序

1. 先敲定 `QUESTION-002.j`、`QUESTION-002.l`、`QUESTION-006`、`QUESTION-010`，它们分别阻塞 T6-F 比较配对、T7-E 关系构建、T7 部分成功语义和 COW/历史边界。
2. 再敲定 `QUESTION-008.b`、`QUESTION-008.c`，收口 Merge 的字段级 `DEFER` 与 Checkpoint 版次口径。
3. `QUESTION-011`（Baseline）与 `QUESTION-014`（SK 版本治理）作为 T6-G 主题，需要业务/四 BC 参与。
4. `QUESTION-012`、`QUESTION-017`、`QUESTION-018` 作为规模与运维专题，不阻塞当前实现。

## 历史入口

- D-001～D-018 的逐项裁定与反例：[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)。
- D-019、ProductSpace/Classification、Diff/Merge 衔接：[阶段 4 评审](../reviews/2026-11-10-stage-4-productspace-classification-review.md)。
- 不变量证据与接受范围：[不变量审查](../reviews/2026-11-10-invariant-evidence-review.md)、[事实与政策基线](facts-policy-baseline-v1.md)。
- GateFlow/DQA/Part 的接入范围：[Shared Kernel 边界](shared-kernel-boundary.md)。
