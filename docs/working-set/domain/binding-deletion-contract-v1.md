# 绑定与删除契约 v1（草案）

**状态：Draft（契约候选；不改变 FACT-024～026、POL-012～015、INV-053～060 已确认规则，不构成 Accepted 或发布）**

- 日期：2026-11-11
- 阶段：阶段 5 契约层 T2（[阶段 5 契约层计划](../reviews/2026-11-10-stage-5-contract-plan.md)）
- 已确认依据：FACT-024/025/026、POL-012/013/014/015、INV-053～060、SCN-050～054、[ADR-0004](../adr/0004-productspace-independent-aggregate.md)
- 本文件**不决定**：数据库表结构、软删与恢复的实现机制（待 K1 裁定）、并发锁与消息机制。
- 本文件**不重新裁定**：绑定归属（WorkingSet）、两个 Entity 的划分、POL-014 的八条删除/移除语义、ProductSpace 独立聚合地位。

## 1. 范围

SpaceBinding／TrackerBinding 的领域行为、绑定与移除操作、Tracker 删除约束、级联边界、默认 WorkingSet 约束，以及「移除 / 删除 / 软删 / 彻底删除」的语义区分（后者作为待裁定项 K1）。

## 2. 已确认规则摘要（不重复论证，只引用）

| 规则 | 来源 |
| --- | --- |
| ProductSpace 是 Shared Kernel 独立聚合，可被多个 WorkingSet 关联，不随 WorkingSet 派生复制/迁移；Tracker 属于一个 ProductSpace | FACT-024 |
| Classification 为三层共享元模型、启动前初始化常驻内存；Tracker 是其在 ProductSpace/WorkingSet 中的投影；创建 ProductSpace 时按分类集合投影生成 Tracker | FACT-025 |
| 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 只能在默认 WorkingSet 创建原始聚合；子 WS 只能纳入直接上游 | FACT-026 |
| SpaceBinding 是 TrackerBinding 前置；同一 WS 同 ProductSpace 最多一个有效 SpaceBinding；同一 WS/ProductSpace/Classification 最多一个有效 TrackerBinding；Tracker.productSpaceId 必须等于绑定空间；同一 Tracker 不在同一 WS 重复纳入 | POL-012 |
| Shared 纳入复用原 TrackerId 仅建绑定；非共享纳入创建新聚合 + 直接 sourceId；移除后可重新纳入/派生；最后 Tracker 移除不删 SpaceBinding；ProductSpace 移出是独立显式操作 | POL-013 |
| 删除/移除八条：Shared 移除仅解绑；非共享无 Item 且无派生子才删除；有派生子不允许删除；有 TrackerItem 不允许移除；有组织子节点但无派生子不允许删除；最后 Tracker 移除不删空间绑定；删 ProductSpace 时仍有 TrackerBinding 抛异常；默认 WS 下 Shared 空且无子可删 | POL-014（修订 POL-006） |
| SpaceBinding、TrackerBinding 是归属 WorkingSet 聚合的两个 Entity；ProductSpace、Tracker 只维护自身归属身份，不持有绑定 | POL-015 |

对应的不变量：INV-053（空间绑定前置）、INV-054（空间 ID 一致）、INV-055（空间绑定唯一）、INV-056（槽位唯一）、INV-057（Shared/非共享身份）、INV-058（删除/移除语义）、INV-059（默认 WS 与 parentId 不可变）、INV-060（两 Entity 归属）。

## 3. 绑定模型契约

### 3.1 SpaceBinding（WorkingSet 聚合内 Entity）

| 项 | 契约 |
| --- | --- |
| 身份键 | `(workingSetId, productSpaceId)`，同一 WorkingSet 内唯一（INV-055） |
| 必需属性 | workingSetId、productSpaceId、纳入来源（初始化投影 / 用户显式关联）、状态 |
| SB-1 | 不得存在没有对应 SpaceBinding 的 TrackerBinding（INV-053） |
| SB-2 | 关联 Tracker 的 `productSpaceId` 必须等于本绑定的 `productSpaceId`（INV-054） |
| SB-3 | 移除空间内最后一个 Tracker 不删除 SpaceBinding（POL-013） |
| SB-4 | 仍有 TrackerBinding 时不能移除 SpaceBinding（POL-014） |

### 3.2 TrackerBinding（WorkingSet 聚合内 Entity）

| 项 | 契约 |
| --- | --- |
| 身份键 | `(workingSetId, trackerId)`，同一 WorkingSet 内不重复纳入（POL-012） |
| 槽位键 | `(workingSetId, productSpaceId, classificationId)` 最多一个有效（INV-056） |
| 必需属性 | workingSetId、trackerId、productSpaceId、classificationId、sharingMode、建立方式（Shared 复用 / 非共享派生） |
| TB-1 | 建立前必须存在同 WS 的 SpaceBinding（INV-053） |
| TB-2 | 绑定的 `productSpaceId` 与 Tracker 的 `productSpaceId` 一致（INV-054） |
| TB-3 | Shared：`trackerId` 与上游聚合相同；非共享：新 Tracker 身份 + 直接 `sourceId`（POL-013/INV-057） |
| TB-4 | `sharingMode` 必须与 Classification 的共享特性相容（POL-003；共享分类只能共享，非共享分类两者可选） |

### 3.3 默认 WorkingSet 约束

- Id=1 固定，不可删除、不可隐藏（INV-059/FACT-026）。
- Tracker 原始聚合只能在此创建；子 WorkingSet 只能纳入直接上游（FACT-026）。
- 默认 WorkingSet 下的 Shared Tracker 在空且无子节点时可删除（POL-014）。

## 4. 操作契约

失败分类为契约命名，具体错误码与消息留实现。

| 操作 | 入参 | 前置（已确认规则） | 后置 / 结果 | 失败语义 |
| --- | --- | --- | --- | --- |
| `bindSpace` | workingSetId、productSpaceId | WorkingSet 可用；ProductSpace 存在；同 WS 无同 ProductSpace 的有效 SpaceBinding（INV-055） | 建立 SpaceBinding，返回其身份 | `AlreadyBound`、`ProductSpaceNotFound`、`WorkingSetNotWritable` |
| `bindTracker`（Shared） | workingSetId、trackerId | 同 WS 已有该空间 SpaceBinding（INV-053）；槽位未占用（INV-056）；未重复纳入；共享特性相容 | 建立 TrackerBinding，复用上游 `trackerId` | `MissingSpaceBinding`、`SlotOccupied`、`TrackerAlreadyBound`、`SharingModeMismatch` |
| `bindTracker`（非共享派生） | workingSetId、productSpaceId、classificationId | 同上；来源改为直接上游 Tracker | 创建新 Tracker 聚合（直接 `sourceId`）+ 新 TrackerBinding | 同上 + `SourceTrackerNotFound` |
| `unbindTracker` | workingSetId、trackerId | TrackerBinding 存在；无 TrackerItem（POL-014） | Shared：仅解除绑定，聚合保留；非共享叶子（无派生子、无组织子节点）：删除聚合并解除绑定 | `TrackerHasItems`、`TrackerHasDerivedChildren`、`TrackerHasOrgChildren` |
| `removeSpace` | workingSetId、productSpaceId | 该空间无任何 TrackerBinding（POL-014） | 解除 SpaceBinding | `SpaceStillHasTrackers` |
| `deleteTracker` | trackerId（默认 WS 内原始聚合） | 无 Item、无派生子、无组织子节点（POL-014、SCN-053） | 删除聚合并解除对应绑定 | 同 `unbindTracker` 的三类拒绝 |

关键先后语义：`unbindTracker` 的「有 Item 不允许移除」针对**聚合删除**；Shared 的解除绑定不受该约束（SCN-052 边界）。

## 5. 移除 / 删除 / 软删 / 彻底删除

| 概念 | 含义 | 是否在本契约 |
| --- | --- | --- |
| 绑定移除（unbind） | 解除 WorkingSet 与 Tracker 的关联；对 Shared 不删除聚合 | 已确认（POL-013/014） |
| 聚合删除（delete） | Tracker 聚合不再存在；受八条约束 | 已确认（POL-014），实现机制待定 |
| 业务内容删除 | 业务 BC 的 TrackerItem 生命周期；Merge 可传递删除但由目标 BC 校验提交 | 属业务 BC，不在本契约 |
| 软删 / 归档 / 恢复 | 可恢复的隐藏态与恢复操作 | **未裁定**（QUESTION-009），列为 K1 |

**边界**：绑定移除不等于 Item 删除，也不等于 WorkingSet 删除；不得以「禁止解绑」推导「禁止删除」，反之亦然。

## 6. 级联边界

- **删 ProductSpace**：若任一 WorkingSet 仍有该空间的 TrackerBinding → 抛异常（POL-014），不级联解除。
- **删 Tracker**：不级联删除子 Tracker 或子节点；子级存在即拒绝（POL-014、D-006 禁止重挂）。
- **删 WorkingSet**：默认 WorkingSet 不可删除；子 WorkingSet 的删除与其绑定的处理为 **K3**。
- **父 Tracker 删除/停用**：子 Tracker 的处理为 **K2**（QUESTION-009.a）；不得自动清空或重挂 `parentId`。

## 7. 关键决策点与推荐

### K1 软删 vs 彻底删除（QUESTION-009）

| 选项 | 说明 | 代价 |
| --- | --- | --- |
| **A（推荐）** | 当前只支持**彻底删除**（不可恢复）；软删/归档/恢复作为独立后续能力立项 | 不引入未确认的恢复语义；代价是暂无撤销能力 |
| B | 删除＝软删，可恢复；彻底删除为独立操作 | 需要恢复语义、保留期与权限规则，均未裁定 |
| C | 两套都建模 | 复杂度最高，且缺少业务证据 |

推荐 A：POL-014 只裁定了「删除/移除」，没有恢复语义；把软删并入会凭空增加业务概念。

### K2 父 Tracker 删除/停用时的子 Tracker（QUESTION-009.a）

| 选项 | 说明 |
| --- | --- |
| **A（推荐）** | 拒绝父删除/停用（有组织子节点即拒绝，与 POL-014 一致） |
| B | 允许父删除，子级显式断链 |
| C | 级联删除 |

推荐 A：与「有组织子节点但无派生子节点不允许删除」「D-006 禁止重挂」一致，且不引入隐藏级联。

### K3 子 WorkingSet 删除与绑定处理

| 选项 | 说明 |
| --- | --- |
| **A（推荐）** | 删除子 WorkingSet 前要求其绑定可全部解除（无 TrackerItem 约束），否则拒绝；删除时显式解除绑定 |
| B | 删除时级联解除全部绑定 |
| C | 禁止删除仍有绑定的 WorkingSet |

推荐 A：显式、可审计，不引入隐式级联。

## 8. 已确认规则与契约候选的区分

| 内容 | 性质 |
| --- | --- |
| 两 Entity 归属、空间绑定前置、两处唯一性、productSpaceId 一致、Shared/非共享纳入方式、八条删除/移除语义、默认 WS 约束 | **已确认**（FACT-024～026、POL-012～015、INV-053～060） |
| 绑定 Entity 的属性集与身份键命名（`(ws,p)`、`(ws,tracker)`、槽位键） | **契约候选**（结构抽象，非表结构） |
| 操作签名与失败分类命名 | **契约候选** |
| 软删/归档/恢复语义（K1）、父 Tracker 删除/停用（K2）、子 WS 删除（K3） | **待用户裁定** |
| 数据库表、唯一约束实现、并发锁、软删字段 | **不在本契约** |

## 9. 与 Merge／COW 的边界

- 绑定移除后，该 Tracker 不再属于该 WorkingSet，无法参与以该 WS 为 source/target 的 Merge（直接父子准入不变）。
- Tracker 删除受「有 Item 禁止」约束，避免破坏 Merge/COW 的变体可达性（POL-014）。
- Merge 与 COW 只读写业务内容与 LightTrackerItem，不直接修改绑定结构。

## 10. 追溯与下一步

- 追溯：FACT-024～026、POL-012～015、INV-053～060、SCN-050～054、[ADR-0004](../adr/0004-productspace-independent-aggregate.md)。
- 图：[ProductSpace/Tracker/WorkingSet 关系](../diagrams/productspace-tracker-workingset.md)。
- 下一步：用户裁定 K1～K3 后冻结本契约，并补 SCN-061 起（软删语义、父 Tracker 删除、子 WorkingSet 删除）。
