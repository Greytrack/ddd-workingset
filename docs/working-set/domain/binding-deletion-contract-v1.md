# 绑定与删除契约 v1

**状态：Reviewed（T2 契约冻结，2026-11-11；K1～K3 已由用户正式确认；未纳入正式基线、未发布）**

- 日期：2026-11-11
- 阶段：阶段 5 契约层 T2（[阶段 5 契约层计划](../reviews/2026-11-10-stage-5-contract-plan.md)）
- 已确认依据：FACT-024/025/026、POL-012/013/014/015、INV-053～060、INV-064～068（2026-11-11 确认）、SCN-050～054、SCN-061～065、[ADR-0004](../adr/0004-productspace-independent-aggregate.md)
- 本文件**不决定**：数据库表结构、物理删除/清理策略、并发锁与消息机制、软删状态机、跨聚合数据库大事务。
- 本文件**不重新裁定**：绑定归属（WorkingSet）、两个 Entity 的划分、POL-014 的八条删除/移除语义、ProductSpace 独立聚合地位。

## 1. 范围

SpaceBinding／TrackerBinding 的领域行为，以及四类操作的区别与约束：

1. **绑定移除**（unbind）：解除 WorkingSet 与 Tracker 的关系；
2. **Tracker 聚合删除**：Tracker 不再作为有效业务对象存在；
3. **ProductSpace 聚合删除**：空间的所有绑定解除后才可删除；
4. **WorkingSet 删除**：受成员关系约束的显式操作，默认 WorkingSet 永不删除。

同时定义「移除 / 删除 / 软删 / 彻底删除」的语义（K1）与级联边界（K2/K3）。**K1～K3 均为用户 2026-11-11 正式确认的业务裁定，不再作为候选方案讨论。**

## 2. 已确认规则摘要（不重复论证，只引用）

| 规则 | 来源 |
| --- | --- |
| ProductSpace 是 Shared Kernel 独立聚合，可被多个 WorkingSet 关联，不随 WorkingSet 派生复制/迁移；Tracker 属于一个 ProductSpace；在默认 WorkingSet 中维护生命周期 | FACT-024 |
| Classification 为三层共享元模型、启动前初始化常驻内存；Tracker 是其在 ProductSpace/WorkingSet 中的投影；创建 ProductSpace 时按分类集合投影生成 Tracker | FACT-025 |
| 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 只能在默认 WorkingSet 创建原始聚合；子 WS 只能纳入直接上游 | FACT-026 |
| SpaceBinding 是 TrackerBinding 前置；同一 WS 同 ProductSpace 最多一个有效 SpaceBinding；同一 WS/ProductSpace/Classification 最多一个有效 TrackerBinding；Tracker.productSpaceId 必须等于绑定空间；同一 Tracker 不在同一 WS 重复纳入 | POL-012 |
| Shared 纳入复用原 TrackerId 仅建绑定；非共享纳入创建新聚合 + 直接 sourceId；移除后可重新纳入/派生；最后 Tracker 移除不删 SpaceBinding；ProductSpace 移出是独立显式操作 | POL-013 |
| 删除/移除八条：Shared 移除仅解绑；非共享无 Item 且无派生子才删除；有派生子不允许删除；任意 Tracker 有 TrackerItem 不允许移除；有组织子节点但无派生子不允许删除；最后 Tracker 移除不删空间绑定；移除 ProductSpace 时仍有 TrackerBinding 抛异常；默认 WS 下 Shared 空且无子可删 | POL-014（修订 POL-006） |
| SpaceBinding、TrackerBinding 是归属 WorkingSet 聚合的两个 Entity；ProductSpace、Tracker 只维护自身归属身份，不持有绑定 | POL-015 |
| 领域删除不可恢复；软删、归档、恢复不纳入本版契约；领域彻底删除不强制映射为数据库物理 DELETE | POL-016（2026-11-11） |

对应不变量：INV-053（空间绑定前置）、INV-054（空间 ID 一致）、INV-055（空间绑定唯一）、INV-056（槽位唯一）、INV-057（Shared/非共享身份）、INV-058（删除/移除语义）、INV-059（默认 WS 与 parentId 不可变）、INV-060（两 Entity 归属）、INV-064（解绑边界）、INV-065（非共享三项检查）、INV-066（ProductSpace 删除审查）、INV-067（WorkingSet 删除约束）、INV-068（删除不可恢复）。

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
| TB-4 | `sharingMode` 必须与 Classification 的共享特性相容（POL-003） |

### 3.3 默认 WorkingSet 约束

- Id=1 固定，不可删除、不可隐藏（INV-059/FACT-026）；**默认 WorkingSet 始终不可删除**（K3）。
- Tracker 原始聚合只能在此创建；子 WorkingSet 只能纳入直接上游（FACT-026）。
- 默认 WorkingSet 下的 Shared Tracker 在空且无子节点时可删除（POL-014）。

## 4. 操作契约

失败分类为契约命名，具体错误码与消息留实现。

### 4.1 绑定与纳入

| 操作 | 入参 | 前置 | 后置 / 结果 | 失败语义 |
| --- | --- | --- | --- | --- |
| `bindSpace` | workingSetId、productSpaceId | WorkingSet 可用；ProductSpace 存在；同 WS 无同空间的有效 SpaceBinding（INV-055） | 建立 SpaceBinding | `AlreadyBound`、`ProductSpaceNotFound`、`WorkingSetNotWritable` |
| `bindTracker`（Shared） | workingSetId、trackerId | 同 WS 已有该空间 SpaceBinding（INV-053）；槽位未占用（INV-056）；未重复纳入；共享特性相容 | 建立 TrackerBinding，复用上游 `trackerId` | `MissingSpaceBinding`、`SlotOccupied`、`TrackerAlreadyBound`、`SharingModeMismatch` |
| `bindTracker`（非共享派生） | workingSetId、productSpaceId、classificationId | 同上；来源为直接上游 Tracker | 创建新 Tracker 聚合（直接 `sourceId`）+ 新 TrackerBinding | 同上 + `SourceTrackerNotFound` |

### 4.2 绑定移除（按共享策略分别约束）

| 操作 | 入参 | 前置 | 后置 / 结果 | 失败语义 |
| --- | --- | --- | --- | --- |
| `unbindTracker`（**Shared**） | workingSetId、trackerId | 当前 WorkingSet 存在有效 TrackerBinding；该绑定为 Shared；调用者具备解除权限 | **仅解除 TrackerBinding**；Tracker 聚合保留；**即使该 Tracker 拥有 TrackerItem 也允许解除子工作集关联** | `TrackerBindingNotFound`、`NotSharedBinding`、`PermissionDenied` |
| `unbindTracker`（**非共享**） | workingSetId、trackerId | 当前 WorkingSet 存在有效 TrackerBinding；且同时满足：无 TrackerItem、无组织子级（`parentId` 指向它）、无派生子级（`sourceId` 指向它） | 删除该 Tracker 聚合 + 解除 TrackerBinding | `TrackerHasItems`、`TrackerHasOrgChildren`、`TrackerHasDerivedChildren` |
| `removeSpace` | workingSetId、productSpaceId | 该 WS 内该空间无任何 TrackerBinding（SB-4） | 解除 SpaceBinding（ProductSpace 聚合保留） | `SpaceStillHasTrackers` |

**关键区分（K1）**：Shared 解绑是「解除关系」，即使 Tracker 拥有 Item 也允许；非共享解绑是「删除聚合 + 解除关系」，必须同时通过 Item、组织子级、派生子级三项检查。POL-014 中「任意 Tracker 有 TrackerItem 不允许移除」只约束**聚合删除**，不约束 Shared 解绑（INV-064）。

### 4.3 聚合删除

| 操作 | 入参 | 前置 | 后置 / 结果 | 失败语义 |
| --- | --- | --- | --- | --- |
| `deleteTracker` | trackerId | 无 TrackerItem、无组织子级、无派生子级（POL-014、SCN-053） | 删除 Tracker 聚合 + 解除其 TrackerBinding | `TrackerHasItems`、`TrackerHasOrgChildren`、`TrackerHasDerivedChildren` |
| `deleteProductSpace` | productSpaceId | ① 该空间在**所有** WorkingSet 中不存在任何有效 SpaceBinding；② 该空间下无 Tracker 聚合存在；如仍有 TrackerBinding，必须先按 Tracker 的解除规则处理，**不得直接清理 TrackerBinding 或级联删除 Tracker**；③ 即使某 WorkingSet 已无 TrackerBinding，只要 SpaceBinding 仍存在也不得删除；④ 保留 ProductSpace 在默认 WorkingSet 维护生命周期的既有规则（FACT-024）；⑤ 不因删除 ProductSpace 自动删除其他 WorkingSet 或 Tracker | 删除 ProductSpace 聚合 | `SpaceStillBoundInWorkingSet`（列出 WorkingSet）、`SpaceStillHasTrackers` |
| `deleteWorkingSet` | workingSetId | ① 非默认 WorkingSet（Id=1 始终不可删）；② 全部 TrackerBinding 均可合法解除——Shared 直接解除（不受 Item 限制）、非共享须通过 Item/组织子级/派生子级三项检查；③ 全部 TrackerBinding 解除后，才显式解除该 WorkingSet 的 SpaceBinding；④ 任一关系不能合法解除即拒绝删除 | 显式解除全部 TrackerBinding → 显式解除 SpaceBinding → 删除 WorkingSet；**不允许通过隐式级联绕过规则** | `DefaultWorkingSetUndeletable`、`MemberCannotBeUnbound`（列出成员与原因） |

**业务准入与行为顺序**：`deleteWorkingSet` 定义的是准入条件与解除顺序，不预设跨聚合数据库大事务。

**ProductSpace 删除的引用完整性**：移除最后一个 Tracker 后 SpaceBinding 仍保留（SB-3）；成功执行 `removeSpace` 才解除对应 SpaceBinding。因此删除 ProductSpace 必须显式审查**所有 WorkingSet 的 SpaceBinding**；只检查 TrackerBinding 会留下指向已删除空间的悬空绑定（INV-066）。

## 5. 移除 / 删除 / 软删 / 彻底删除（K1，用户确认）

| 概念 | 契约 |
| --- | --- |
| 绑定移除（unbind） | 解除 WorkingSet 与 Tracker 的关系；Shared 不删除聚合（即使有 Item 也允许），非共享才删除聚合；与业务内容删除无关 |
| 聚合删除（delete） | Tracker／ProductSpace／WorkingSet 不再作为有效业务对象存在；受第 4.3 节约束 |
| 业务 TrackerItem 删除 | 业务 BC 的 Item 生命周期；Merge 可传递删除但由目标 BC 校验提交；不在本契约 |
| 软删 / 归档 / 恢复 | **不纳入本版契约**；未来如需支持，作为独立能力设计 |

**语义边界**：

- 当前**仅支持不可恢复的领域删除**；恢复能力不属于本版契约。
- 「领域彻底删除」＝该聚合不再作为有效业务对象存在、业务上不可访问、不可恢复；**不强制映射为数据库物理 `DELETE`**。实现层可保留审计副本、延迟清理或历史归档，但不得让业务侧产生「可恢复」的预期。
- 三类删除/移除必须分开表达：绑定移除、聚合删除、业务 TrackerItem 删除。
- **业务 TrackerItem 的永久删除（D7，2026-11-12 用户裁定）**：删除后不保留墓碑、内容或历史；查询返回 `NotFound` 是正确语义（`Deleted` 只用于软删对象）。本契约不约束该生命周期，由业务 BC 的 `BusinessObject.delete2` guard 与自身应用服务编排。

## 6. 级联边界（K2 / K3，用户确认）

- **删 Tracker（K2）**：存在组织子级（其他 Tracker 的 `parentId` 指向它）、存在派生子级（其他 Tracker 的 `sourceId` 指向它）、或存在 TrackerItem 时，**禁止删除**；**不自动级联删除子 Tracker、不自动断开或重挂 `parentId`/`sourceId`**。
- **删 ProductSpace**：不得存在任何 WorkingSet 的有效 SpaceBinding（即使该 WS 已无 TrackerBinding）；如仍有 TrackerBinding，必须先按 Tracker 的解除规则处理，不得直接清理绑定或级联删 Tracker；不因删除 ProductSpace 自动删除其他 WorkingSet 或 Tracker；保留 ProductSpace 在默认 WorkingSet 维护生命周期的既有规则。
- **删 WorkingSet（K3）**：默认 WorkingSet（Id=1）始终不可删除；删除前须能合法解除全部 TrackerBinding 与 SpaceBinding（Shared 解绑不受 Item 限制，非共享须三项检查）；解除顺序为 TrackerBinding → SpaceBinding → WorkingSet；存在任何不能合法解除的关系即拒绝，不允许隐式级联绕过。
- **不存在“停用”生命周期**：WorkingSet 与 Tracker 都没有停用状态或停用命令（用户 2026-11-12 裁定）；删除契约中不引入任何停用状态及其行为。

## 7. K1～K3 最终裁定（用户确认，2026-11-11）

| 编号 | 用户裁定 | 状态 |
| --- | --- | --- |
| K1 | 仅支持不可恢复的领域删除；软删、归档、恢复不纳入本版契约；不将领域彻底删除强制映射为数据库物理 DELETE；区分绑定移除、聚合删除与业务 TrackerItem 删除 | **用户已确认** |
| K2 | Tracker 有组织子级、派生子级或 TrackerItem 时禁止删除；不自动级联删除子 Tracker、不断开或重挂 `parentId`/`sourceId` | **用户已确认** |
| K3 | 默认 WorkingSet 始终不可删除；子 WorkingSet 删除前须能合法解除全部 TrackerBinding 与 SpaceBinding（Shared 解绑不受 Item 限制，非共享须三项检查）；解除顺序 TrackerBinding → SpaceBinding → WorkingSet；任一不可解除即拒绝 | **用户已确认** |

以上为已批准的业务裁定，不再作为候选方案反复讨论。

## 8. 规则性质

| 内容 | 性质 |
| --- | --- |
| 两 Entity 归属、空间绑定前置、两处唯一性、productSpaceId 一致、Shared/非共享纳入方式、POL-014 八条删除/移除语义、默认 WS 约束 | **已确认**（FACT-024～026、POL-012～015、INV-053～060） |
| K1 删除语义（不可恢复、软删不纳入、不强制物理 DELETE） | **用户确认**（2026-11-11，POL-016、INV-068） |
| K2 Tracker 删除三条件与不级联/不断链 | **用户确认**（2026-11-11，INV-065） |
| K3 子 WorkingSet 删除的成员关系约束与解除顺序 | **用户确认**（2026-11-11，INV-067） |
| `deleteProductSpace` 的引用完整性（所有 WorkingSet 的 SpaceBinding 均须为空） | **用户确认**（2026-11-11，INV-066） |
| 绑定 Entity 属性集、身份键命名、操作签名与失败分类 | **契约 v1（本文件冻结）**；实现细节留后续 |
| 数据库表、唯一约束实现、并发锁、物理清理策略、软删字段 | **不在本契约** |

## 9. 与 Merge／COW 的边界

- 绑定移除后，该 Tracker 不再属于该 WorkingSet，无法参与以该 WS 为 source/target 的 Merge（直接父子准入不变）。
- Tracker 删除受 Item/子级约束，避免破坏 Merge/COW 的变体可达性（POL-014、K2）。
- Merge 与 COW 只读写业务内容与 LightTrackerItem，不直接修改绑定结构。

## 10. 追溯与下一步

- 追溯：FACT-024～026、POL-012～016、INV-053～060、INV-064～068、SCN-050～054、SCN-061～065、[ADR-0004](../adr/0004-productspace-independent-aggregate.md)。
- 图：[ProductSpace/Tracker/WorkingSet 关系](../diagrams/productspace-tracker-workingset.md)、[绑定与删除生命周期](../diagrams/binding-lifecycle.md)。
- 评审：[T2 评审记录](../reviews/2026-11-11-t2-binding-deletion-review.md)。
- 下一步：进入 **T3（LightTrackerItem 物化协议）**。
