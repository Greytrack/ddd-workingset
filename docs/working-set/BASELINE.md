# Working Set Design Baseline

- **Status**：Accepted（接受范围见下文）
- **Baseline Version**：首版领域模型候选基线 v1；后续以 ADR、T1～T4/T7 契约和验收记录增量登记
- **Release Status**：Unreleased
- **Released Git Commit / Tag**：None
- **Last Updated**：2026-11-21

## 1. 权威范围

本页是当前设计与实施共同使用的 Source of Truth 入口。当前有效内容只包括：

1. 本页列出的 Accepted 业务事实、政策与组织约束；
2. 当前有效且未被 Superseded 的 ADR；
3. 当前 T1～T4、T7 契约；
4. 与上述规则一致的领域验收标准。

`Draft`、`Reviewed` 或 `Historical` 文档不因被本页链接而自动成为业务基线。实现证据不能反向定义业务规则。

## 2. 已确认的组织架构约束

`AC-001`：Working Set 位于企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。来源为用户 2026-11-09 的明确确认。

该约束固定企业架构归属和依赖方向，不证明 Working Set 是单一聚合、单一 BC、单一模块或微服务，也不允许 SK 越过业务 BC 聚合规则修改业务内容。详见 [Shared Kernel 边界](domain/shared-kernel-boundary.md) 与 [Context Map](domain/context-map.md)。

## 3. 已接受的事实与政策

已接受范围以 [事实与政策主题基线 v1](domain/facts-policy-baseline-v1.md) 为准，包含 R01—R19 的限定陈述、身份规则、KEEP_TARGET/Checkpoint 语义、单工作集 CommonId 唯一及派生附件/关系固定规则。

现行硬约束：

- `TC-002`（2026-11-19）：一个写事务最多修改一个聚合；跨聚合状态变更通过可靠事件、各自独立事务最终一致，禁止跨聚合同步事务。
- `D-007` / `FACT-012`：`TrackerItem` 是 SK 公共契约；业务对象是各 BC 的完整独立聚合。
- `D-008` / ADR-0002：LightTrackerItem 是 SK 独立聚合，是身份、来源和有效变体定位权威。

## 4. 当前 ADR

| ADR | 状态 | 说明 |
| --- | --- | --- |
| [ADR-0001](adr/0001-tracker-independent-aggregate.md) | Accepted | Tracker 独立聚合 |
| [ADR-0002](adr/0002-lighttrackeritem-authoritative-aggregate.md) | Accepted | LightTrackerItem 权威聚合 |
| [ADR-0003](adr/0003-cow-event-driven-final-consistency.md) | Accepted | COW 事件驱动最终一致 |
| [ADR-0004](adr/0004-productspace-independent-aggregate.md) | Accepted | ProductSpace 独立聚合与绑定 |
| [ADR-0005](adr/0005-merge-execution-model.md) | Accepted | Merge 执行模型；执行期复核由 ADR-0009 调整 |
| [ADR-0006](adr/0006-merge-confirmation-ownership.md) | Accepted | 合并确认归 MergeEntry；跨轮次只读查询 |
| [ADR-0007](adr/0007-root-folder-tracker-per-working-set-space.md) | Superseded | 由 ADR-0008 替代 |
| [ADR-0008](adr/0008-tracker-third-level-container.md) | Accepted | Tracker 是产品空间第三级容器 |
| [ADR-0009](adr/0009-merge-dispatch-frozen-evidence.md) | Accepted | Merge 按批准时冻结值分发 |
| [ADR-0010](adr/0010-working-set-creation-tracker-baseline-time.md) | Accepted | 工作集创建/扩展按 Tracker 基线时间尽力派生；替代 T7-D 受理冻结与快照存储 |

## 5. 当前契约

| 契约 | 状态 | 范围 |
| --- | --- | --- |
| [T1 Merge 执行](domain/merge-execution-contract-v1.md) | Reviewed | 轮次、条目、比较证据、确认与执行结果 |
| [T2 绑定与删除](domain/binding-deletion-contract-v1.md) | Reviewed | 绑定结构、删除与拒绝条件 |
| [T3 物化协议](domain/lighttrackeritem-materialization-contract-v1.md) | Reviewed | 身份、双态、COW、失败与删除保留 |
| [T4 跨 BC 契约](domain/cross-bc-contract-v1.md) | Reviewed | 只读 Port、事件协作、九分类失败语义与错误码 |
| [T7 创建/扩展 LRO](domain/working-set-creation-lro-design.md) | Reviewed | 基线时间、阶段、任务明细与关系事件 |

## 6. 实现边界

[当前实现边界](implementation/current-boundaries.md) 是 Reviewed 实现记录，不是新的业务基线。它只描述代码现状、已落地边界和与 TC-002 的差距；不得用代码现状反推业务规则。

公共查询视图 `air_inst_common` 的定位：它是查询视图/缓存，不是业务权威；业务对象摘要在下层事件中由各 BC 投影，当前 ALM 为待实现的空监听器。

## 7. 历史与候选

- [评审索引](reviews/README.md)：当前裁定与历史阶段评审分组列出；
- [实施索引](implementation/README.md)：当前验收与 T5/T7-D 历史验收；
- [提案](proposals/README.md)：探索方案，不自动成为设计；
- `domain/facts.md`、`domain/invariants.md`、`domain/context-map.md` 中的候选标记仍按本页和各自的 Accepted 子集解释。

T5 验收、旧 T7-D 冻结清单、旧 V4 迁移脚本和旧同步写 Port 均保留为历史证据，不再作为当前实现入口。

## 8. 最近基线变更

| 日期 | 主题 |
| --- | --- |
| 2026-11-19 | TC-002 单聚合写事务与跨聚合事件协作；D4 只读边界澄清 |
| 2026-11-20 | ADR-0006 合并确认归属；ADR-0008 Tracker 第三级容器；LTI 软删 `flags` 口径 |
| 2026-11-21 | ADR-0009 冻结值分发；ADR-0010 基线时间派生；产品空间删除拒绝与 Tracker 可见性 |

完整过程、异议与旧结论见 [评审索引](reviews/README.md) 与 Git 历史。
