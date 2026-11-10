# Merge 执行行为契约 v1（草案）

**状态：Draft（契约候选；Q1～Q3 已由用户于 2026-11-10 裁定并经 ADR-0005 Accepted；本契约整体仍未冻结）**

- 日期：2026-11-10
- 阶段：阶段 5 契约层 T1（[阶段 5 契约层计划](../reviews/2026-11-10-stage-5-contract-plan.md)）
- 已确认依据：D-019（FACT-027、INV-061～063）、D-007/D-008/D-012/D-018、FACT-007/008、INV-006～010、POL-004、R08～R11、B06/B08/B09/B10
- 本文件**不决定**：ComparisonToken 编码、幂等键实现、事件中间件、数据库表结构、是否新建聚合。
- 本文件**不重新裁定**：直接父子准入、决策优先、BC 整体校验与提交、KEEP_TARGET 语义、COW 机制。

## 1. 范围

围绕**一条 MergeEntry 的完整生命周期**定义行为契约：

```
Diff 产生有向冻结差异 + ComparisonToken
  → 用户提交 MergeDecision
  → MergeRound 登记条目并记录执行任务
  → 校验提交时的双侧比较依据
  → 依 ItemMergeDecision 确定目标操作
  → 业务 BC 构建目标聚合并 Save
  → LightTrackerItem 按既定机制维护有效来源与 Revision
  → 依 markAsMerged 更新 MergeCheckpoint
  → MergeRound 记录单项结果、进度与最终 ApplyResult
```

图：[MergeEntry 生命周期与协作时序](../diagrams/merge-entry-lifecycle.md)。

## 2. 领域对象与职责划分

本契约涉及四类对象，**均为既有模型**，不新增聚合：

| 对象 | 形态 | 拥有的职责 | 明确不拥有 |
| --- | --- | --- | --- |
| **Diff 能力**（Compare & Merge 区域） | 查询能力／领域服务，非聚合 | 产生有向、冻结、可追溯的 `TrackerItemDiff` 与 `ComparisonToken`；只读双侧证据 | 不写业务内容；不持决定；不记录「已处理」 |
| **MergeRound** | 聚合根（合并载体，D-019） | 轮次参数、条目集合、进度、单项结果、最终 `ApplyResult`；持有本轮合并决策 | 不直接写业务字段；不判定 BC 业务合法性；不拥有 Checkpoint |
| **MergeEntry** | MergeRound 内 **Entity**（已裁定：ADR-0005） | 一次合并事项的生命周期与状态；绑定 token、决定、目标操作、结果 | 不是独立聚合；不跨轮次复用 |
| **MergeCheckpoint** | 独立聚合（D-019） | 「差异已处理」的确认/撤销事实与因果单调；按范围+家族键+双侧版次定位 | 不拥有业务内容；不证明内容相等；不替代 ApplyResult |
| **业务 BC 聚合** | 各 BC 独立聚合 | 构建目标聚合、整体校验、提交（Save）、返回真实结果与业务 Revision | 不做合并选择；不记录 Checkpoint |
| **LightTrackerItem** | 独立聚合（D-008） | 身份（LocalId/CommonId）、有效内容来源、自身 Revision 的维护 | 不构造业务内容；不判定合并合法性 |

**边界原则**：Merge 是「决定 + 编排」，内容写入与合法性始终由目标业务 BC 拥有；「已处理」由 Checkpoint 单独表达；三者不合并为一个大对象。

## 3. Diff 查询能力与 ComparisonToken 契约

### 3.1 查询能力

| 操作 | 入参 | 返回 | 说明 |
| --- | --- | --- | --- |
| `compare` | source 范围（WorkingSetId）、target 范围、方向、条目范围（可选）、分页游标 | `TrackerItemDiff` + `ComparisonToken` | 有向；同一 token 内分页属同一冻结清单；只读 |
| `readDiffPage` | `ComparisonToken`、游标 | 差异页 | 不得绕开 token 重新实时比较 |

`TrackerItemDiff` 至少包含（契约级抽象，不是表结构）：

- 记录级存在性差异（两侧各有/各无）；
- 字段级差异：字段路径、双侧值引用（证据引用，不是当前值）、适用性；
- 双侧证据引用：比较时的源/目标内容证据标识（不可变快照引用／内容指纹／版本组合等，形式待定）；
- 方向、范围、生成时间、分页完整性标记。

### 3.2 ComparisonToken

`ComparisonToken` 是**值对象**，标识一次冻结比较的证据集合。契约要求：

1. 只能由 `compare` 产生，唯一对应一份冻结清单；
2. 可随提交带回，用于校验「比较依据未变化」；
3. 不承载业务内容，不承载决定；
4. **不得只由业务 Revision 构成**：Revision 是版次标识，同一 Revision 内内容仍可变（D-015 反例），因此 token 必须绑定双侧内容证据引用，Revision 仅可作为其中一项；
5. 编码、生成方式、存储形式**不在本轮决定**（Q2 只裁定「必须绑双侧内容证据」，不涵盖编码形式）。

### 3.3 证据时点区分（已确认语义）

- Merge 的冻结比较证据（比较前双侧内容）与 COW 的「修改前不可变快照」（D-010）是不同时点证据，不得混用。
- 业务 Revision 只表达版次，**不能替代完整内容比较证据**（原则见上）。

## 4. MergeRound 主要领域行为

| 行为 | 入参 | 返回 | 前置 | 失败语义 |
| --- | --- | --- | --- | --- |
| `startRound` | 源范围、目标范围、方向、范围参数、发起人 | `MergeRoundId`、状态 `Preparing` | 直接父子准入（POL-004、D-019）；非共享 Tracker；双方可读 | `MergeNotAllowed`（兄弟/跨代、共享 Tracker、不可读） |
| `registerEntries` | `MergeRoundId`、条目集合（`entryKey`、`ComparisonToken`、`ItemMergeDecision`） | 已登记条目 ID 列表 | 轮次 `Preparing`；token 属于本轮的比较范围；同一 `entryKey` 不重复 | 重复登记 → 幂等返回原条目（幂等键形式待定）；`DEFER + markAsMerged=true` → 拒绝该条；token 与当前证据不一致 → 该条 `StaleEvidence`，要求重新比较 |
| `executeEntry` | `MergeRoundId`、`MergeEntryId` | `EntryResult` | 条目已登记且非终态 | 见 §5 状态机；终态条目不可重复执行 |
| `reportProgress` | `MergeRoundId` | `RoundProgress`（各状态计数与条目摘要） | 只读 | — |
| `completeRound` | `MergeRoundId` | `ApplyResult` | 全部条目到达终态，或显式放弃 | 存在 `Deferred`/`NotExecuted` 时不得返回 `Succeeded` |

`executeEntry` 的内部步骤（一次执行的完整行为契约）：

1. **复核证据**：以提交的 `ComparisonToken` 与当前双侧证据比对；不一致 → `StaleEvidence`，不写任何内容，要求重新比较。
2. **确定目标操作**：依 `ItemMergeDecision` 得到目标操作——字段赋值集合、显式存在性操作（创建/恢复/删除）、或「无写入」（`KEEP_TARGET`）、或「保持未完成」（`DEFER`）。
3. **调用业务 BC**：以目标 BC 的公开写能力提交（携带期望版本与证据引用）；BC 构建目标聚合并**整体校验**；成功 → Save 并返回真实结果与业务 Revision；拒绝 → `Rejected`（保留决定与拒绝原因，目标不得部分写入，INV-009）。
4. **维护 LightTrackerItem**：目标若尚未物化，业务聚合 Save 成功后按既定机制转为 Local-backed（有效来源=自身、Revision=自身 Revision，D-012/D-018/D-019）；**不新增 Merge 专用物化协议**。
5. **确认（按 `markAsMerged`）**：为 `true` 且条目终态为 `Applied` 时，请求 `MergeCheckpoint.confirm`；为 `false` 时不确认。
6. **记录结果**：写 `EntryResult`（终态、目标操作、BC 结果引用、Checkpoint 结果、失败分类），更新 `RoundProgress`。

## 5. MergeEntry 生命周期

状态（Entity 内的状态机，候选）：

| 状态 | 含义 | 可迁移到 |
| --- | --- | --- |
| `Compared` | Diff 已产生、决定未提交 | 随 `registerEntries` 进入 `Decided` |
| `Decided` | 决定已登记，等待执行 | `Applying`、`StaleEvidence`、`Deferred` |
| `Applying` | 已提交业务 BC，等待结果 | `Applied`、`Rejected` |
| `Applied` | 业务操作成功（含 `KEEP_TARGET` 的无写入） | `Confirmed`（markAsMerged=true） |
| `Rejected` | 业务 BC 校验拒绝 | 终态（可用新证据重试为新执行） |
| `StaleEvidence` | 提交依据已过期 | 终态；须重新 `compare` 产生新 token |
| `Deferred` | 用户显式延迟 | 终态（本轮不可完成） |
| `Confirmed` | 已记录 Checkpoint | 终态 |

约束：

- `Deferred` **不得**迁移到 `Confirmed`（INV-008）；
- `StaleEvidence` 不得直接执行，必须先重新比较；
- `Applied` 是业务事实，`Confirmed` 是处理事实，两者分离（FACT-008）；
- 条目终态后不可再次执行；重试以新执行实例表达，保留原结果。

## 6. MergeCheckpoint 主要领域行为与不变量

### 6.1 行为

| 行为 | 入参 | 返回 | 说明 |
| --- | --- | --- | --- |
| `confirm` | 范围、家族键、决定版本、双侧业务 Revision、证据引用 | `CheckpointId` | 记录「已处理」；KEEP_TARGET 亦可确认 |
| `revoke` | 范围、家族键、被撤销的确认、原因 | `CheckpointId` | 记录撤销事实与因果位置 |
| `lookup` | 范围、家族键、双侧业务 Revision | `Handled` / `Outdated` / `Unknown` | 供 Diff 隐藏已处理差异 |

### 6.2 不变量（契约候选，标注 Candidate）

| 编号 | 不变量 | 依据 |
| --- | --- | --- |
| CK-1 | 同一（范围、家族键）的确认必须基于不低于已存决定版本的决定；更旧的决定版本不得覆盖新确认 | INV-010 的 Checkpoint 因果顺序；B10 |
| CK-2 | 撤销一经记录，晚到的同版本或更旧确认不得使其失效 | 「旧确认不得覆盖更新的撤销」（INV-010） |
| CK-3 | 确认不等于内容相等：`KEEP_TARGET` 可确认；确认不写业务内容、不改变业务 Revision | R11；CONCEPT-015 |
| CK-4 | 任一侧业务 Revision 显式升级后，旧 Checkpoint 对该版本组合过时；普通编辑只增乐观锁 version 时不失效 | R11/R12（已确认） |
| CK-5 | Checkpoint 独立聚合，不持有业务内容、不以 MergeRound 为父，只按范围+家族键+决定版本关联 | D-019 |

## 7. ACCEPT / KEEP_TARGET / DEFER 与 markAsMerged 行为矩阵

| 决定 | markAsMerged | 目标写入 | 条目终态 | Checkpoint | 本轮可否完成 |
| --- | --- | --- | --- | --- | --- |
| `ACCEPT`（字段/存在性） | true | 目标 BC 整体校验后 Save | `Applied` → `Confirmed` | `confirm` | 是 |
| `ACCEPT` | false | 同上 | `Applied` | 不更新 | 是 |
| `KEEP_TARGET` | true | **不写**（显式保留目标） | `Applied` → `Confirmed` | `confirm`（记录已处理，不伪造相等） | 是 |
| `KEEP_TARGET` | false | 不写 | `Applied` | 不更新 | 是 |
| `DEFER` | true | 不写 | **非法，拒绝登记** | — | — |
| `DEFER` | false | 不写 | `Deferred` | 不更新 | **否** |
| 任意（BC 拒绝） | 任意 | 不写，不得部分写入 | `Rejected` | 不更新 | 条目终态，可重试 |
| 任意（证据过期） | 任意 | 不写 | `StaleEvidence` | 不更新 | 条目终态，需重新比较 |

补充规则（已确认）：`ACCEPT` 执行时读取源端**最新值**（B09/R08）；目标同字段更新不要求重新审阅（决策优先，R09）；单字段不可读时该条未完成、批次继续。

## 8. 部分成功与结果表达

`MergeRound` 状态：`Preparing` → `Running` → `Completed`（`Succeeded` / `PartiallySucceeded` / `Failed`）。

- **`Succeeded`**：全部条目 `Applied`（含 `Confirmed` 或 `Unconfirmed`）。
- **`PartiallySucceeded`**：至少一条 `Applied`，且至少一条 `Rejected`/`Deferred`/`StaleEvidence`。
- **`Failed`**：无任何条目 `Applied`。
- **不可宣称完成**：存在 `Deferred` 或未执行条目时，`completeRound` 不得返回 `Succeeded`（INV-008）。

`ApplyResult` 表达（契约级）：

- 条目结果列表：`entryKey`、终态、目标操作摘要、BC 结果引用、Checkpoint 结果、失败分类；
- 计数汇总：`applied` / `confirmed` / `rejected` / `deferred` / `staleEvidence`；
- 轮次状态与完成时间；
- **已提交项不回滚**；重试只处理未完成项（B08 已确认）；已成功项即使源又变化仍跳过（R10）。

## 9. 与 LightTrackerItem 和业务 BC 的边界

- Merge 不改变 `LocalId`/`CommonId`（INV-040）；`SourceLocalId`/`SourceRevision` 由既有 COW 机制维护（D-012/D-017）。
- 未物化目标被 Merge 修改：在 merge 中构建业务聚合并正常 Save，随后按既有机制转为 Local-backed（D-019、INV-062）——这是「物化与修改一体」，不是第三条物化路径。
- 业务对象的 `Revision` 由业务 BC 拥有；LightTrackerItem 的 `Revision` 通过事件同步（D-018）；Merge 不直接写 LightTrackerItem 的 Revision。
- Checkpoint 使用**业务 Revision**（显式升级的版次），不使用 `SourceRevision`（R11；`QUESTION-015` 第 4 项）。
- 共享 Tracker 不参与 Merge；非共享只允许直接父子准入（D-019、POL-004）。

## 10. 已确认规则与契约候选的区分

| 内容 | 性质 |
| --- | --- |
| 直接父子准入、决策优先、BC 整体校验提交、KEEP_TARGET 语义、部分成功与重试、Checkpoint 过时判定、未物化可参与 Diff、未物化目标修改即物化 | **已确认**（FACT-007/008、POL-004、R08～R11、D-019） |
| MergeRound/MergeCheckpoint 的聚合定位；合并决策归属 MergeRound | **已确认**（D-019） |
| MergeEntry 作为 MergeRound 内 Entity | **已裁定**（Q1；ADR-0005） |
| `ComparisonToken` 必须绑定双侧内容证据，Revision 仅其中一项 | **已裁定**（Q2；ADR-0005） |
| Checkpoint 确认由 MergeRound 在 `markAsMerged=true` 且条目 `Applied` 时发起 | **已裁定**（Q3；ADR-0005） |
| `TrackerItemDiff` 字段集、`ComparisonToken` 编码与存储形式 | **契约候选**（留实现） |
| MergeRound 行为签名与状态机、MergeEntry 状态机、`ApplyResult` 结构 | **契约候选** |
| CK-1～CK-5 不变量 | **契约候选**（CK-4 的过时判定已有 R11 依据） |
| 幂等键、事件中间件、表结构 | **不在本契约**（实现/后续决定） |

## 11. T1 三个阻塞问题的裁定结果（2026-11-10）

| 编号 | 问题 | 用户裁定 | 理由 |
| --- | --- | --- | --- |
| Q1 | MergeEntry 是 MergeRound 内 Entity，还是独立持久对象？ | **MergeRound 内 Entity** | D-019 已确认 MergeRound 是「合并载体并记录参数/进度/成败」；条目是轮次的组成，独立成聚合会引入第二个身份与一致性边界，且与「不新增聚合」原则冲突 |
| Q2 | `ComparisonToken` 的证据内容：只绑 Revision 是否足够？ | **不可以；必须绑定双侧内容证据引用**，Revision 仅作其中一项；证据形式留实现 | 同一 Revision 内内容仍可变（D-015 反例）；用 Revision 冒充完整比较证据会使「过期审阅」防护失效 |
| Q3 | Checkpoint 确认由谁发起：MergeRound 执行成功时自动，还是独立用户确认？ | **由 MergeRound 在 `markAsMerged=true` 且条目 `Applied` 时发起**；Checkpoint 只做因果校验 | 避免两个入口写同一事实；用户决策已通过 `markAsMerged` 表达；Checkpoint 保持独立聚合、不做反向查询 |

三项裁定已记录于 [ADR-0005](../adr/0005-merge-execution-model.md)（Accepted，2026-11-10）。

## 12. ADR-0005

**已 Accepted（2026-11-10）**：[ADR-0005：Merge 执行模型与 MergeEntry 行为契约](../adr/0005-merge-execution-model.md)。

- 已记录：三对象职责与协作顺序、MergeEntry 归属、`ComparisonToken` 的证据要求（不含编码）、Checkpoint 确认发起方。
- 未纳入：幂等键实现、Token 编码、中间件与表结构。
- 备选方案（MergeEntry 独立持久／用户单独确认）保留在 ADR 中作为重估依据；不重新请求用户选择已排除方案。

## 13. 追溯与下一步

- 追溯：D-019、FACT-027、INV-061～063、SCN-055/056；FACT-007/008、INV-006～010、POL-004、R08～R11、B06/B08/B09/B10；[ADR-0005](../adr/0005-merge-execution-model.md)。
- 图：[MergeEntry 生命周期与协作时序](../diagrams/merge-entry-lifecycle.md)。
- 下一步：补齐 SCN-057 起的执行场景（过期审阅、撤销晚到、`DEFER` 残留、部分成功后重试、未物化目标失败），冻结契约 v1；随后进入 T2 绑定与删除契约。
