# 后续计划与开放问题

**状态：Draft（只列当前仍需裁定的问题与待验证项）**

本页是「还没决定什么、下一步做什么」的唯一权威：开放问题（按优先级）、规模与保留专题、T7 后续实施计划，以及已关闭问题的索引。历史裁定过程见
[裁定时间线](06-decisions/timeline.md) 与[归档评审](archive/reviews/)。

## 1. 当前开放问题

本表只保留会影响身份、历史、合法状态、契约或实现的开放问题。

| 优先级 | 编号           | 问题                                                      | 可选答案                                                                                                                                                                                                                     | 影响与阻塞                                                                | 建议验证                                                 |
|--------|----------------|-----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------|----------------------------------------------------------|
| P0     | `QUESTION-010` | **关系服务的可见性、删除守卫与接入**                      | 身份裁定 C 见 [原裁定](archive/reviews/2026-11-15-question-010-relation-identity-decision.md)。WorkingSet 侧事件已实现；[消费守卫矩阵](archive/reviews/2026-11-17-t7-e-relation-guards-decision.md#5-关系服务侧的守卫矩阵)待确认和接入 | 影响真实关系数据的创建、悬空引用修复及消费契约                            | 对缺失、软删、永久删除、后续纳入、钉版不可读做端到端验收 |
| P1     | `QUESTION-004` | **CommonId 跨实例并发与去重协议**                         | 已确认：默认 WS 同一雪花、非默认两个不等雪花、派生继承 CommonId、软删占键、冲突 409。剩余：多实例并发生成/校验、重试与唯一约束的兜底边界                                                                                     | 决定唯一索引、ID 生成器与冲突重试；不重开身份语义                         | 并发创建/派生/回并 + 唯一约束冲突注入                    |
| P1     | `QUESTION-011` | **业务 Baseline 的含义**                                  | A Snapshot；B 发布清单；C Merge Base；D 多概念并存但分别命名                                                                                                                                                                 | 决定不可变性、保留、引用语义；避免与仓库 Design Baseline、Merge Base 混淆 | 收集业务人员使用“基线”的原句与操作；T6-G                 |
| P2     | `QUESTION-012` | **Working Set 与 Binding 的规模、并发上限**               | A 小集合单聚合；B 大集合版本化 Manifest；C 分片准备 + 发布指针                                                                                                                                                               | 决定 WorkingSet 聚合装载/热点与是否引入 Manifest；不阻塞当前语义          | 采集 P95/P99 数量、写并发、发布频率与允许时延            |
| P2     | `QUESTION-014` | **四个 BC 共同依赖的 Shared Kernel 如何治理版本与兼容性** | A 共同发布；B 版本化 Published Language；C Adapter/ACL 隔离演进                                                                                                                                                              | 团队治理待办，不阻塞 T5/T6/T7 当前实现                                    | 访谈四 BC 的发布责任、升级节奏、兼容期限和变更审批者     |
| P2     | `QUESTION-017` | **T7 创建任务的取消、补齐与结果保留**                     | A 不提供取消/补齐，失败后人工按 T2 删除叶子 WS；B 提供取消；C 提供基于已有子 WS 的补齐任务；结果保留期另定                                                                                                                   | 决定任务表生命周期、失败明细保留与恢复入口；当前只支持终态轮询            | 以长任务中断、部分成功、终态后清理验证                   |
| P2     | `QUESTION-018` | **T7-E 关系构建失败后的重试与修复边界**                   | A 只记录失败明细，人工修复；B 自动重投；C 定时对账修复                                                                                                                                                                       | 决定 Reference/Association 的最终一致责任与幂等键                         | 与 `QUESTION-010` 六类场景合并验证                       |

## 2. 规模与保留

**状态：Draft（阈值待 `QUESTION-012` 采样后确定）**

本专题统一承载 Working Set 各切片的规模、并发与保留验证。各实现切片只登记待测项，不为此阻塞；实测数据与阈值结论只写在本节。

### 2.1 范围

| 主题       | 待测内容                                                                                                                                                                 | 关联切片  |
|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|
| Merge 轮次 | 一页 20–50 条决策下 `merge_rounds.state` 行大小、轮次 `save` 整份 JSONB 重写耗时、apply 全流程耗时                                                                       | T6-E      |
| 连接池     | 每轮执行占用一个外层锁连接 + 内层写入连接，核对并行轮次与 Hikari 容量                                                                                                    | T6-B/T6-E |
| 比较       | 瞬时 compare 在候选家族规模增长时的耗时与分页稳定性（不承诺跨请求冻结）                                                                                                  | T6-E      |
| 工作集创建 | 按 Tracker 基线时间分页扫描耗时、并发创建、批大小、失败明细规模                                                                                                          | T7-F      |
| 保留与清理 | `merge_rounds`、`requirement_merge_receipts`、`air_ws_items_merge_index`、`event_publication` 的保留期与归档；合并基准历史承担去重，COW 来源及请求发布记录承担回读和去重 | T6-E/T7-F |

### 2.2 待采样指标

工作集与绑定数的 P50/P95/P99、写并发、发布频率、允许时延。采样完成前，本节只规定方法，不给出阈值；任何阈值变更按基线变更流程登记。

### 2.3 方法

- 压测独立于常规 `mvn test`，使用单独脚本或标签。
- 每次运行记录：数据集规模、并发度、P50/P95/P99、错误率、连接池峰值占用、JSONB 行大小。
- 保留/清理验证必须证明：不删除未终态轮次；不改变已确认 Checkpoint 的方向与家族语义；清理可重跑、可审计。

### 2.4 已知设计结论

- 一页 20–50 条决策时不引入条目表，`merge_rounds.state` 整份重写可接受。
- `jsonb_set` 不能消除物理写放大（JSONB 不可变）；规模显著超过一页时应把 entries/results 规范化为独立行表。
- 尚无实测数据；本节不阻塞 T6-E 与 T7-F 的实现与验收。

## 3. T7 后续实施计划

**状态：Draft。** 已有能力和验收证据见[实现现状与验收证据](07-implementation.md)；当前 API、任务行为和边界见
[T7 创建/扩展 LRO](05-contracts/t7-creation-lro.md)。

| 下一步          | 验收范围                                                                                                                                                                                              |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 关系服务接入    | 用真实业务 BC Adapter 替换空事实源，验证消费端建关系、修复原引用和守卫。依据 `QUESTION-010` 与 [T7-E 事件契约](archive/reviews/2026-11-17-t7-e-relation-guards-decision.md)。 |
| T7-F 规模与保留 | 采样快照和索引耗时、连接池压力、失败明细增长，验证清理与恢复。阈值和清理安排见 §2 及 `QUESTION-012`/`QUESTION-017`。                                                                                  |

Merge 同一个 `requestId` 携带不同 decisions、旧轮次 JSON 与 UI 重试复用仍是后续幂等边界；本计划不作新裁定。

## 4. 已关闭问题索引

以下问题已经用户裁定或由后续契约关闭；保留一行结论和依据，不再作为当前待裁定项。历史论证见对应评审与契约。

| 编号              | 结论                                                                                                                                  | 依据                                                                                                                                                  |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `QUESTION-001`    | Working Set 是工作视图/范围清单，不是分支本体或 Workspace+Release Scope                                                               | 2026-11-12 用户裁定；[S1～S7 验收](07-implementation.md)                                                                                  |
| `QUESTION-002.a`  | `parentId` 是同工作集直属结构父                                                                                                       | D-003；[阶段 3 评审](archive/reviews/2026-11-09-stage-3-architecture-review.md#d-003)                                                                 |
| `QUESTION-002.b`  | `sourceId` 是直接父级工作集来源，不是最初来源                                                                                         | D-003                                                                                                                                                 |
| `QUESTION-002.c`  | 允许多级逐级派生                                                                                                                      | D-003                                                                                                                                                 |
| `QUESTION-002.d1` | 创建完成后 `parentId` 不可修改                                                                                                        | D-006                                                                                                                                                 |
| `QUESTION-002.d2` | `sourceId` 创建后不可变                                                                                                               | T6-F（2026-11-16）                                                                                                                                    |
| `QUESTION-002.e`  | 同一工作集、同一（空间，分类）允许多个 Tracker；引入 `is_default`                                                                     | T6-F（2026-11-16）                                                                                                                                    |
| `QUESTION-002.f`  | Shared `parentId` 由默认工作集维护；子工作集允许悬挂，能构树则构树                                                                    | T6-F（2026-11-16）                                                                                                                                    |
| `QUESTION-002.g`  | 父也选中时默认继承，创建前可显式重组                                                                                                  | D-005/D-006                                                                                                                                           |
| `QUESTION-002.h`  | Item 归属关闭：业务 BC 拥有业务对象聚合，SK 只负责公共身份与工作集机制                                                                | D-007                                                                                                                                                 |
| `QUESTION-002.i`  | 原“批量派生整批原子”；已由 `QUESTION-002.l` 取代为 T7 创建/扩展部分成功                                                               | T6-F（2026-11-16）、T7-G（2026-11-17）                                                                                                                |
| `QUESTION-002.j`  | Compare/Merge 候选对按 Tracker 的直接 `sourceId` 派生关系确定：只允许直属派生父子，不允许跨级，不允许派生兄弟节点互配，不做同槽全组合 | 2026-11-17 用户裁定                                                                                                                                   |
| `QUESTION-002.k`  | Shared 默认资格与删除补位                                                                                                              | [T6-F 裁定](archive/reviews/2026-11-16-t6-f-lineage-constraints.md#43-shared-默认与删除补位question-002k2026-11-17-用户确认)                          |
| `QUESTION-002.l`  | 创建新子工作集与扩展已有子工作集均允许部分成功；T6-F i 的“批量派生整批原子”不再适用于 T7 创建/扩展 LRO，独立批量派生入口另行定义      | 2026-11-17 用户裁定                                                                                                                                   |
| `QUESTION-003`    | 现阶段不接入权限；`Caller`/`authorize` 占位已于 2026-11-19 移除，待权限模型完成后重新设计接入边界                                     | D11（2026-11-12）；2026-11-19 实现清理                                                                                                                |
| `QUESTION-005`    | 原生新建 Item 由服务端分配身份；回并经直属父子 Merge CREATE 承载                                                                      | D10、T6-C、T6-D                                                                                                                                       |
| `QUESTION-006`    | 软删保留首次物化所需快照并触发派生；永久删除须先软删，并等待仍依赖该来源的索引切换到本地，再清除来源历史                              | [T3 删除与来源保留](05-contracts/t3-lti-materialization.md#删除与来源保留question-006)                                                                |
| `QUESTION-007`    | Compare/Merge 只允许直属父子双向，兄弟/跨代拒绝                                                                                       | POL-004、FACT-007                                                                                                                                     |
| `QUESTION-008`    | 字段级 Merge 的执行部分已由 S7 落地；剩余并发恢复转 `QUESTION-008.a`                                                                  | [S7 验证](archive/implementation/t5-s7-verification-result.md)                                                                                        |
| `QUESTION-008.a`  | 执行中断后接续原轮次；并发 Apply 返回 `RoundInProgress`；业务提交凭据恢复                                                             | T6-B、T1 §8.1                                                                                                                                         |
| `QUESTION-008.b`  | 取消延期；新增/删除行级接受或忽略，更新逐字段接受或忽略；软删恢复与确认规则见执行契约 §7                                             | [T1 §7](05-contracts/t1-merge-execution.md#7-接收忽略与行级确认)                                                                                       |
| `QUESTION-008.c`  | Checkpoint 按双方变体自身业务版次命中；继承内容按来源版次回读                                                                         | [T1 §6](05-contracts/t1-merge-execution.md#6-合并确认与跨轮次查询)                                                                                     |
| `QUESTION-009`    | 仅支持不可恢复的领域删除；软删/归档/恢复不纳入 T2 本版；绑定移除与聚合删除分开                                                        | T2 K1～K3、POL-016                                                                                                                                    |
| `QUESTION-009.a`  | WorkingSet/Tracker 不存在“停用”生命周期                                                                                               | 2026-11-12 用户裁定                                                                                                                                   |
| `QUESTION-013`    | 四 BC 各自校验整个对象并决定提交；SK 管理选择、协调与结果；两层接口并存                                                               | T4 K1″/K2″                                                                                                                                            |
| `QUESTION-015`    | Diff/Merge 行为契约关闭；Q1～Q4 已裁定，重做须新建轮次                                                                                | T1、ADR-0005                                                                                                                                          |
| `QUESTION-016`    | 所有 BC 的所有 Object 都具备 `delete`/`undelete`/`delete2` 能力声明；具体规则/guard 由各 BC 实现                                      | D14（2026-11-12）                                                                                                                                     |
| T6-E              | Compare 不持久化；一页 1～50 条决策；`requestId` 幂等；异步 LRO                                                                       | [2026-11-15 裁定](archive/reviews/2026-11-15-compare-not-persisted-decision.md)、[T6-E 验收](archive/implementation/t6-e-merge-round-verification.md) |
| T6-F              | Shared 允许悬挂；同槽多 Tracker（独立分类投影）+ `is_default`；`sourceId` 不可变；派生链 1:1；完成即可见                              | [T6-F 裁定](archive/reviews/2026-11-16-t6-f-lineage-constraints.md)                                                                                   |
| 合并确认          | 确认归 MergeEntry；跨轮次当前基准使用只读查询，替代独立 Checkpoint 聚合                                                               | [ADR-0006](06-decisions/adr-0006-merge-confirmation-ownership.md)；身份裁定历史见 [Checkpoint 修正](archive/reviews/2026-11-16-merge-checkpoint-legacy-alignment.md) |

## 5. 历史入口

- D-001～D-019 的逐项裁定与反例：[阶段 3 评审](archive/reviews/2026-11-09-stage-3-architecture-review.md)、[阶段 3.1 规则收敛](archive/reviews/2026-11-09-stage-3-1-rule-convergence.md)。
- D-019、ProductSpace/Classification、Diff/Merge 衔接：[阶段 4 评审](archive/reviews/2026-11-10-stage-4-productspace-classification-review.md)。
- 不变量证据与接受范围：[不变量审查](archive/reviews/2026-11-10-invariant-evidence-review.md)、[已接受的限定陈述](03-rules.md#7-已接受的限定陈述r01r19)。
- GateFlow/DQA/Part 的接入范围：[Shared Kernel 边界](04-model.md#5-shared-kernel-边界)。
