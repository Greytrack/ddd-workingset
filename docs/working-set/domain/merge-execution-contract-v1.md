# Merge 执行行为契约 v1（草案）

**状态：Reviewed（T1 审核通过并关闭，2026-11-11；2026-11-20 按 ADR-0006 调整确认归属；契约整体仍为候选，未 Accepted、未发布）**

依据：[ADR-0005](../adr/0005-merge-execution-model.md)、[ADR-0006](../adr/0006-merge-confirmation-ownership.md)、D-019、POL-004、FACT-007/008/027、R08～R11。

## 1. 范围

用户比较直属父子工作集中的非共享 Tracker，提交所选差异的证据与决定。MergeRound 登记后异步执行，目标业务 BC 校验完整对象并提交。轮次记录单项结果，并在需要时把合并确认随轮次结果一并保存。需要 LTI 变更时由业务提交后的事件推进。

一轮只有一个终态结果。失败、过期或延迟项须重新比较并提交新轮次，可只选未完成项。旧轮次的结果继续保留。执行中断恢复见 §8.1，协作顺序见 [Merge 时序](../diagrams/merge-entry-lifecycle.md)。

## 2. 领域对象与职责划分

| 对象 | 形态 | 拥有的职责 | 明确不拥有 |
| --- | --- | --- | --- |
| **Diff 能力**（Compare & Merge 区域） | 查询能力／领域服务，非聚合 | 产生有向、可追溯的 `TrackerItemDiff` 与瞬时比较证据；只读双侧证据 | 不写业务内容；不持决定；不记录「已处理」 |
| **MergeRound** | 聚合根（合并载体，D-019） | 轮次参数、条目集合、进度、单项结果、最终 `ApplyResult`；持有本轮合并决策与条目确认 | 不直接写业务字段；不判定 BC 业务合法性；不拥有独立 Checkpoint 聚合 |
| **MergeEntry** | MergeRound 内 **Entity**（已裁定：ADR-0005） | 一次合并事项的生命周期与状态；持有所选差异证据、决定、目标操作与结果 | 不是独立聚合；不跨轮次复用 |
| **合并确认 / 当前 Checkpoint** | 确认归 MergeEntry；当前 Checkpoint 是跨轮次只读模型（ADR-0006） | 确认记录随轮次结果原子提交；读取按规范化 Tracker 对、家族及双侧版次判断 | 不拥有业务内容；不证明内容相等；查询侧无写入口 |
| **业务 BC 聚合** | 各 BC 独立聚合 | 构建目标聚合、整体校验、提交（Save）、返回真实结果与业务 Revision | 不做合并选择；不记录 Checkpoint |
| **LightTrackerItem** | 独立聚合（D-008） | 身份（LocalId/CommonId）、有效内容来源、自身 Revision 的维护 | 不构造业务内容；不判定合并合法性 |

**边界原则**：Merge 是「决定 + 编排」，内容写入与合法性始终由目标业务 BC 拥有；「已处理」由条目确认记录表达，跨轮次只读查询当前有效确认。

**附件与关系（D6，2026-11-12 用户裁定）**：本版 Compare/Merge 只处理普通字段；附件与关系**不参与比较与合并**。派生时的附件/关系固定基准仍按 T3 §2.1 生效，但 Merge 不比较、不合并、不改写它们。

## 3. Diff 查询能力与瞬时比较证据

Compare 只读且结果即时生成。各页重新比较，跨请求的差异清单可变化。历史裁定见 [Compare 不持久化](../reviews/2026-11-15-compare-not-persisted-decision.md)。

### 3.1 查询能力

| 操作 | 入参 | 返回 | 说明 |
| --- | --- | --- | --- |
| `compare` | 源和目标各自的 WorkingSetId 与 TrackerId（四项必填）、offset/limit（可选） | 所选 Tracker 对的 `TrackerItemDiff` 页 | 服务端先校验工作集直属父子关系、Tracker 各自归属、有效范围与直接派生关系，再读取该对的条目。支持父→子和子→父。分页重复执行只读比较，不承诺跨请求冻结。 |

**Tracker 级准入（2026-11-17 用户确认）**：候选 Tracker 对只按直接 `sourceId` 派生关系生成——子 Tracker 的 `sourceId` 必须等于父侧 Tracker 的 `trackerId`；只允许直属派生父子，跨级（祖父/更远）不配对，派生兄弟节点之间不配对，也不做同槽全组合。来源无法在直属父工作集有效范围内解析时不生成候选对，不回退到同槽其他 Tracker。

`TrackerItemDiff` 至少包含（契约级抽象，不是表结构）：

- 记录级存在性差异（两侧各有/各无）；
- 字段级差异：字段路径、双侧值引用（证据引用，不是当前值）、适用性；
- 双侧证据引用：比较时的源/目标内容证据标识（不可变快照引用／内容指纹／版本组合等，形式待定）；
- 方向、范围、生成时间、下一页位置或是否还有下一页。

**差异下发规则（2026-11-15）**：compare 只返回差异，不返回一致项。差异使用 `ChangeKind`：`ADDED`、`MODIFIED`、`DELETED`。新增/删除只返回存在性差异，不比较字段内容；只有 `MODIFIED` 才比较属性差异。命中 §6 的 Checkpoint 时隐藏对应差异；其余修改项按字段比较。

### 3.2 瞬时比较证据

Compare 返回瞬时差异和双侧证据。`Difference` 描述有向 `TrackerPair`（source WorkingSet + Tracker、target WorkingSet + Tracker）、家族、双侧证据和字段；轮次内条目身份由服务端在登记时生成的 `MergeRound.Entry.id` 承担。用户提交决定时必须携带所选差异证据（四元组身份、presence、revision、字段与失败状态）；MergeRound 保存该证据和决定。登记时以当前状态校验证据；apply 只执行轮次内的决策范围，并继续校验证据未过期。

### 3.3 证据时点区分（已确认语义）

- Merge 的冻结比较证据（比较前双侧内容）与 COW 的「修改前不可变快照」（D-010）是不同时点证据，不得混用。
- 业务 Revision 只表达版次，**不能替代完整内容比较证据**（原则见上）。

## 4. MergeRound 主要领域行为

| 行为 | 入参 | 返回 | 前置 | 失败语义 |
| --- | --- | --- | --- | --- |
| `startRound` | 源范围、目标范围、方向、范围参数、发起人 | `MergeRoundId`、状态 `Preparing` | 直接父子准入（POL-004、D-019）；非共享 Tracker；双方可读 | `MergeNotAllowed`（兄弟/跨代、共享 Tracker、不可读） |
| `executeEntry` | `MergeRoundId`、`MergeEntryId` | `EntryResult` | 轮次处于执行阶段（`Running`）；条目已登记且非终态 | 见 §5 状态机；终态条目不可重复执行；轮次终态后拒绝执行 |
| `reportProgress` | `MergeRoundId` | `RoundProgress`（各状态计数与条目摘要） | 只读 | — |
| `completeRound` | `MergeRoundId` | 本轮**唯一** `ApplyResult` | 本次执行已结束（全部条目到达终态）；轮次非终态 | 不存在未终态条目时方可结束；确认与轮次终态同事务提交；结束后轮次进入终态，**不可重开、不可再登记、不可再执行**（Q4） |

**异步 LRO（2026-11-16 收敛）**：`startRound` 即「提交一页决策」：`decisions` 必填 1–50 条、`requestId` 必填且不超过 128 字符，空决策、缺键或超限直接拒绝；**不再提供空轮次与分批登记入口**，一页决策要么整页登记成功、要么整轮回滚。服务端以 `requestId` 幂等键创建 MergeRound 后发布 `MergeRoundSubmittedEvent`，由异步消费者逐条执行。同一 requestId 返回原轮次，不同 source/target 返回 `Conflict`。`GET /api/merge-rounds/{id}` 轮询状态与结果；一轮对应一页约 20–50 条决策，GET 返回完整 entries/result，不做条目分页。`apply` 是幂等触发器，重复调用返回原 `ApplyResult`。

`executeEntry` 的内部步骤（一次执行的完整行为契约）：

1. **复核证据**：以用户提交并随 MergeRound 持久化的所选差异证据与当前双侧状态比对；不一致 → `StaleEvidence`，不写任何内容，要求重新比较。
2. **确定目标操作**：依 `ItemMergeDecision` 得到目标操作——按差异类型应用行决策或字段决策；忽略时不写目标，软删目标的新增接收见 §8。
3. **请求业务 BC 执行**：通过可靠事件提交（携带期望版本与证据引用）；BC 构建目标聚合并**整体校验**；成功 → Save 并返回真实结果与业务 Revision；拒绝 → `Rejected`（保留决定与拒绝原因，目标不得部分写入，INV-009）。
4. **维护 LightTrackerItem**：目标若尚未物化，业务聚合 Save 成功后按既定机制转为 Local-backed（有效来源=自身、Revision=自身 Revision，D-012/D-018/D-019）；**不新增 Merge 专用物化协议**。
5. **登记业务结果和确认**：按 §7 判定本次成功是否需要写确认；需要时条目生成不可变确认，轮次仓储在同一事务记录结果和确认行；其他情况只记录条目结果。
6. **结束**：所有条目完成时，保存唯一终态；若最后的条目需要确认，确认行与终态同事务提交。写入失败会回滚本次轮次变更；可重投已提交的业务结果。

## 5. MergeEntry 生命周期

**D15-B（业务提交与 LTI 切换分离）**：ALM 消费 `MergeEntryApprovedEvent`，独立提交业务结果与回执，发布 `MergeEntryResolvedEvent`。SK 消费结果推进轮次，适用时再发布 `MergeAppliedEvent`，由 LTI 独立事务切换。`ApplyResult` 不等待 LTI；必需的确认必须与轮次状态原子提交，不额外等待确认回执。

状态（Entity 内的状态机，候选）：

| 状态 | 含义 | 可迁移到 |
| --- | --- | --- |
| `Decided` | 决定已登记，等待执行 | `Applying`、`StaleEvidence` |
| `Applying` | 已提交业务 BC，等待结果 | `Applied`、`Rejected` |
| `Applied` | 业务操作成功（含 `IGNORED` 的无写入） | 轮次保存时无需确认则记 `Applied`；需确认则同事务记 `Confirmed` 和确认行 |
| `Rejected` | 业务 BC 校验拒绝 | 终态（重做须新建 MergeRound） |
| `StaleEvidence` | 提交依据已过期 | 终态；须重新 `compare` 并新建 MergeRound |
| `Confirmed` | 已记录 Checkpoint | 终态 |

约束：

- `StaleEvidence` 不得直接执行，必须先重新比较；
- `Applied` 是业务事实，`Confirmed` 是处理事实，两者分离（FACT-008）；
- 条目终态后不可再次执行；**跨轮次重做必须新建 MergeRound**（新 `mergeRoundId`），原结果保留可追溯（Q4）。

## 6. 合并确认与跨轮次查询

**身份与命中**：跨轮次查询以数值规范化的 Tracker 对和家族定位当前确认。最新记录只有状态为 `MERGED` 且双侧业务 Revision 匹配时才命中。先选最新再校验状态；最新为 `REVOKED` 时不回退到上一条确认。方向和 LocalId 留作证据，WorkingSet 不进入身份。确认不证明内容相同，普通编辑不改变命中版次。

**确认归属（[ADR-0006](../adr/0006-merge-confirmation-ownership.md)）**：确认是 MergeEntry 持有的值对象。轮次仓储将轮次状态与所需确认行一并提交。查询侧 `MergeCheckpointQuery` 可直接读取已提交的成员行，不建第二张表，也不提供独立修改入口。

| 步骤 | 提交边界与验收 |
| --- | --- |
| 业务提交 | 业务 BC 独立提交目标内容和结果事件。没有接收任何变更时无业务写入，轮次可以直接登记结果。 |
| 轮次提交 | 消费业务结果，按轮次和条目幂等推进。每轮每族至多一项；需要确认时用条目雪花 ID 追加确认行。若插入失败，条目和轮次状态都回滚，Tracker 对闸门保持占用。 |
| 重投 | 重投已提交的业务结果即可继续轮次收敛；已经终结的 A 轮不会重新追加确认，不改变 B 轮基准。 |
| 比较 | 只读查询按 Tracker 对和家族取最新行，再校验状态与双侧版次。比较凭据仍需在登记及执行时复核。 |

物理映射、旧表列与旧端兼容细节见[实施记录](../implementation/merge-checkpoint-rework-plan.md)；本契约只约束领域语义和跨轮次查询规则。

## 7. 接收、忽略与行级确认

**QUESTION-008.b 已裁定。** 对外动作只有 `ACCEPT` 和 `IGNORED`，没有延期状态。新增、删除提交 `existence` 行级选择；更新必须给每个差异字段提交一个选择，且可单独指定行级 `markAsMerged`。字段未填写不是忽略。

| 差异 | 决策 | 业务写入 | 成功后确认 |
| --- | --- | --- | --- |
| 新增 | `ACCEPT` | 新建；软删目标则恢复原变体 | 自动写入，使用提交后的目标版次 |
| 新增 | `IGNORED` | 无 | 不写 |
| 删除 | `ACCEPT` | 删除目标 | 不写 |
| 删除 | `IGNORED` | 无 | 不写 |
| 更新 | 每字段 `ACCEPT` / `IGNORED` | 一次提交所有接收字段；忽略值保留目标原值；全部忽略不写内容 | 仅当 `markAsMerged=true` 才写；确认覆盖当前版次的整行 |

执行接收字段仍取源端最新值（B09/R08）；同版次目标字段变动遵循决策优先（R09）；任一实际接收值不可读则整条失败，不部分写入。

行级新增/删除不接受由客户端开启 `markAsMerged`。忽略是本轮正常完成；未确认且差异仍存在时，下次比较继续展示。BC 拒绝、冲突、过期证据不写确认。

## 8. 轮次终态与结果表达

**软删目标（QUESTION-008.b 最新裁定）**：来源存在而目标软删时，对外差异类型为新增（`ADDED`），证据保留目标 `Presence.DELETED`、原 LocalId 与乐观锁。接收后，业务 BC 使用该身份及期望锁恢复目标，沿用原变体及自身版次；不创建第二个同族对象。恢复成功自动确认。客户端不提交 `RESTORE`，它只是内部业务操作。


**新建家族保护**：席位空闲时按 [T4 身份规则](cross-bc-contract-v1.md#4-六个用例的契约边界)创建。目标工作集中若由另一个 Tracker 占用同族席位，返回冲突；软删目标恢复时若并发变化，按证据过期或乐观锁冲突处理，不降级为新建。

`MergeRound` 状态：`Preparing` → `Running` → 终态（`Succeeded` / `PartiallySucceeded` / `Failed`）。实现**没有独立的 `Completed` 阶段**：本轮全部条目完成后直接进入三种终态之一；`completeRound` 只是一次执行结束的动作，不新增状态。

**一次执行结束即进入终态**；终态轮次不可重开、不可再登记决定、不可再执行条目（Q4，2026-11-11 用户裁定）。

终态判定（只针对本轮）：

| 终态 | 条件 |
| --- | --- |
| `Succeeded` | 本轮全部条目成功处理，没有拒绝、冲突、过期等未成功条目 |
| `PartiallySucceeded` | 至少一项成功，且存在未完成或失败项 |
| `Failed` | 本轮没有成功项，且存在失败或未完成项 |

- **成功处理项** = 条目终态 `Applied` 或 `Confirmed`，包括明确选择忽略的无写入结果。
- 空轮次（无条目）沿用 `Failed`，不扩展枚举。

`ApplyResult` 是**本轮执行结果的不可变终态快照**：

- 条目结果列表：`entryId`（轮次内唯一）、终态、目标操作摘要、BC 结果引用、Checkpoint 结果、失败分类；
- 计数汇总：`applied` / `confirmed` / `rejected` / `conflict` / `staleEvidence` / `unknown`；
- 轮次状态与完成时间；
- **只汇总本轮**；新轮次不修改旧轮次结果；已提交项不回滚（B08 已确认）。

跨轮次重做（Q4）：

- 未完成项（`Rejected` / `Conflict` / `StaleEvidence`）需重新 `compare` 并创建新 `MergeRound`，使用新 `mergeRoundId`；
- 新轮次可只选未完成条目，不自动重复上一轮已成功条目（R10 精神）；
- 新轮次的用户决定、执行状态、`ApplyResult` 与旧轮次相互独立，旧结果保留可追溯；
- 消息重复投递的幂等处理**不属于**业务重试，仍遵循原幂等规则。

### 8.1 执行中断与结果恢复（T6-B）

- 同一轮次只允许一个 Apply 请求推进。执行权仍被持有时，其他 Apply 返回 HTTP 409 `RoundInProgress`（T1 轮次冲突）。终态重提返回原 `ApplyResult`。
- Apply 受理后先持久化 `Running`。执行者退出后，调用者可用原轮次 ID 再次 Apply，接续已受理的同一次执行。恢复跳过已记录结果的条目。
- 以轮次 ID 和条目 ID 查询业务提交回执；BC 业务提交、回执和 `MergeEntryResolvedEvent` 发布记录同事务保存。重复交付以回执兑现已提交结果，不重写业务对象。待派发与已派发但等待结果应分别核对。
- 已受理的轮次在提交或结果暂时无法确认时保留 `Running`，按 T4 返回 `Unknown`。恢复以持久化凭据判定，业务拒绝等已记录结果遵循 Q4。旧版未终态轮次缺少提交凭据，须先核对已有业务事实。
- LTI 切换仍按 D15-B 独立异步收敛。2026-11-18 用户裁定移除 `/api/cow/recover/incomplete`；切换失败不再提供恢复入口。轮次结果以业务提交事实为准。


## 9. 与 LightTrackerItem 和业务 BC 的边界

- Merge 不改变 `LocalId`/`CommonId`（INV-040）；`SourceLocalId`/`SourceRevision` 由既有 COW 机制维护（D-012/D-017）。
- 未物化目标被 Merge 修改：在 merge 中构建业务聚合并正常 Save，随后按既有机制转为 Local-backed（D-019、INV-062）——这是「物化与修改一体」，不是第三条物化路径。
- 业务对象的 `Revision` 由业务 BC 拥有；LightTrackerItem 的 `Revision` 通过事件同步（D-018）；Merge 不直接写 LightTrackerItem 的 Revision。
- Checkpoint 使用**业务 Revision**（显式升级的版次），不使用 `SourceRevision`（R11；`QUESTION-015` 第 4 项）。
- 共享 Tracker 不参与 Merge；非共享只允许直接父子准入（D-019、POL-004）。

## 10. 已确认规则与契约候选的区分

| 内容 | 性质 |
| --- | --- |
| 直接父子准入、决策优先、BC 整体校验提交、忽略变更语义、部分成功、Checkpoint 过时判定、未物化可参与 Diff、未物化目标修改即物化 | **已确认**（FACT-007/008、POL-004、R08～R11、D-019） |
| MergeRound 的聚合定位；合并决策与确认归 MergeEntry，随轮次结果同事务保存 | **已确认**（D-019；ADR-0006 于 2026-11-20 调整确认归属） |
| MergeEntry 作为 MergeRound 内 Entity | **已裁定**（Q1；ADR-0005） |
| 所选差异证据必须绑定双侧内容证据，Revision 仅其中一项 | **已裁定**（Q2；ADR-0005；2026-11-15 后随 MergeRound 持久化） |
| 合并确认由 MergeEntry 按 §7 在条目成功时生成，随轮次结果同事务保存；跨轮次读取当前确认 | **已裁定**（ADR-0006，2026-11-20） |
| 一轮一次执行即终态、不可重开；未完成项须新建 MergeRound；`ApplyResult` 为本轮不可变快照 | **已裁定**（Q4，2026-11-11） |
| `TrackerItemDiff` 字段集、瞬时比较证据的编码与传输形式 | **契约候选**（留实现） |
| MergeRound 行为签名与状态机、MergeEntry 状态机、`ApplyResult` 字段结构 | **契约候选** |
| CK-1～CK-5 不变量 | **契约候选**（CK-4 的过时判定已有 R11 依据） |
| 幂等键、事件中间件、表结构 | **不在本契约**（实现/后续决定） |

## 11. 裁定记录

Q1～Q4 的原始方案、理由与接受范围见 [ADR-0005](../adr/0005-merge-execution-model.md) 和 [T1 评审](../reviews/2026-11-11-t1-contract-review.md)。现行瞬时证据契约见 §3.2。

## 12. 追溯与验收

- 追溯：D-019、FACT-027、INV-061～063、SCN-055/056/059/060；FACT-007/008、INV-006～010、POL-004、R08～R11、B06/B08/B09/B10；[ADR-0005](../adr/0005-merge-execution-model.md)。
- 图：[MergeEntry 生命周期与协作时序](../diagrams/merge-entry-lifecycle.md)（含 MergeRound 终态图）。
- 实施证据：[T6-E 轮次接口](../implementation/t6-e-merge-round-verification.md)、[T6-B 中断恢复](../implementation/t6-b-merge-recovery-verification.md)、[T6-D 回并](../implementation/t6-d-merge-back-verification.md)。
