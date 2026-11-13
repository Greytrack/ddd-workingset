# LightTrackerItem 物化协议契约 v1

**状态：Reviewed（T3 契约冻结，2026-11-11；K1′～K3′ 已由用户正式确认；未纳入正式基线、未发布）**

- 日期：2026-11-11
- 阶段：阶段 5 契约层 T3（[阶段 5 契约层计划](../reviews/2026-11-10-stage-5-contract-plan.md)）
- 已确认依据：D-007～D-019、FACT-014～023/027、INV-024～052/061～063/069～071、[ADR-0002](../adr/0002-lighttrackeritem-authoritative-aggregate.md)、[ADR-0003](../adr/0003-cow-event-driven-final-consistency.md)、SCN-019～049
- 本文件**不决定**：数据库表结构、事件中间件、快照序列化格式、并发锁与幂等键的具体实现。
- 本文件**不重新裁定**：LightTrackerItem 的聚合地位、SourceLocalId/SourceRevision 语义、COW 事件驱动与最终一致、首次快照优先、目标消费时确定、Revision 分离。

## 1. 范围

LightTrackerItem 的**完整属性契约**（含独立的物化状态字段）、Source-backed / Local-backed 双态、LocalId 的分配与复用、**首次物化**协议、业务 BC 与 LightTrackerItem 的双事实一致性，以及与 T1（Merge 执行）和 T2（绑定与删除）的衔接。

## 2. 已确认依据摘要（不重复论证，只引用）

聚合与责任依据见 [ADR-0002](../adr/0002-lighttrackeritem-authoritative-aggregate.md)，COW 与顺序前提见 [ADR-0003](../adr/0003-cow-event-driven-final-consistency.md)，D-007～D-019 的确认链见[模型基线](model-baseline-candidate-v1.md) §5。本契约在 §3～§8 定义属性、行为及跨契约衔接。

### 2.1 内容固定范围（用户确认，2026-11-11，选择 A）

普通字段沿用 D-016/D-018：派生仅选定业务 Revision，Source-backed 读取来源该版次的当前普通字段，首次 COW 以首次相关修改前快照固定。附件与关系沿用 R19：固定为派生时状态，来源后续变化不得改变该派生基准；关系目标的全部内容不递归冻结。目标合法的本地修改仍由业务 BC 管理。**附件与关系不参与 Compare/Merge（D6，2026-11-12 用户裁定）**：Merge 只比较与合并普通字段，固定基准不被 Merge 改写。

首次物化必须将普通字段快照与目标派生时固定的附件、关系事实组合后，由 BC 整体校验；不得用来源事件中的较新附件、关系覆盖派生基准。固定事实缺失时不得回退到来源当前值或宣称物化完成。存储、回读与恢复机制仍属实现待办，不在 LightTrackerItem 增加业务内容副本。

确认依据与备选方案见[一致性修正记录](../reviews/2026-11-11-consistency-reconciliation.md)，验收见 SCN-072。

## 3. 属性契约

属性集为**概念级**（不是表结构）；具体列、索引与序列化留实现。

| 属性 | 语义 | 可变性 | 依据 |
| --- | --- | --- | --- |
| `localId` | 当前变体身份（全局唯一）；派生时预分配 | COW 不改变 | D-008、INV-026/040/070 |
| `commonId` | 家族身份；派生继承 | COW 不改变 | FACT-005、INV-040/070 |
| `trackerId` | 所属 Tracker（ID 引用） | 派生后不变 | D-008、INV-028 |
| `workingSetId` | 所属工作集（ID 引用） | 派生后不变 | D-008、INV-028 |
| `revision` | 自身业务 Revision（显式升级推进；普通编辑不推进） | 可显式升级 | D-018、INV-051 |
| `sourceLocalId` | **当前有效内容来源**的 LocalId | 由 COW 切换 | D-012、INV-041/042 |
| `sourceRevision` | 当前有效来源的业务 Revision | 由 COW 切换 | D-012、INV-041/042 |
| **`materialized`** | **物化状态布尔字段**：`false` = Source-backed，`true` = Local-backed；Query 模型可直接据此筛选 | 由 COW 切换 | 用户确认（K1′，2026-11-11） |
| 派生基准 | 普通字段仅选定 Snapshot/Head 对应的业务 Revision；附件与关系固定范围见 §2.1 | 派生时建立 | D-015、D-016、R19、2026-11-11 裁定 A |
| 内容定位 | **不持久化「内容指针」**；Local-backed 时内容由业务 BC 按稳定 `localId` 提供 | — | 用户确认（K2′，2026-11-11） |

**不持有**：业务字段值、业务状态机、业务不变量、完整内容副本（D-007/ADR-0002）。

## 4. 双态契约与状态判定

| 状态 | 权威判定 | 必须同时成立的一致性约束 | 内容来源 |
| --- | --- | --- | --- |
| **Source-backed** | `materialized = false` | `sourceLocalId` 指向外部实际有效内容来源；`sourceRevision` 为该来源的业务 Revision | 由 `sourceLocalId` + `sourceRevision` 解析到的实际内容拥有者（D-013） |
| **Local-backed** | `materialized = true` | `sourceLocalId = localId` 且 `sourceRevision = revision` | 自身；业务 BC 按 `localId` 重建 |

约束：

- **`materialized` 是物化状态的权威字段**，属 LightTrackerItem 的正式状态契约；`sourceLocalId != localId` **不再**作为唯一权威物化状态定义，只保留为身份/来源一致性约束。
- 三类字段（`materialized`、`sourceLocalId`、`sourceRevision`）必须在**同一次 LightTrackerItem 聚合状态变更**中一致更新；状态字段不得由外部服务绕过聚合行为独立修改。
- 若三者不一致（例如 `materialized=true` 而来源仍指向外部），属**元数据一致性缺陷**，不是第三种业务状态，须由修复流程收敛。
- 只有两个**业务状态**；`Materializing` 是技术运行态，**不纳入领域枚举**（D-010）。
- `SourceLocalId` 只表达当前有效内容来源，不承担历史派生关系（D-012/D-018）。
- 未物化不等于不存在（INV-029）；Query 调用方不感知物化过程（D-008）。

## 5. LocalId 契约（K2′，用户确认）

- **派生时预分配全局唯一 `LocalId`**（D-008 第 7 条）：即使尚未物化，变体已拥有稳定身份。
- **首次物化必须复用该 `LocalId`**：物化前后不得出现两个变体身份。
- **COW 不改变** `LocalId` / `CommonId`（INV-040）。
- **不强制业务 BC 的存储主键等于 `LocalId`**；但业务 BC **必须能够通过稳定 `LocalId` 定位或创建对应业务变体**（跨 BC 解析能力是契约要求）。
- **不持久化额外内容指针**，不持有完整业务字段：内容定位由业务 BC 负责，避免形成第三份真相。

## 6. 首次物化协议

**触发**：来源业务对象修改保存成功后发出的来源变更事件（携带修改前不可变完整快照）。

**目标确定**：Handler **消费时**查询满足条件的 **Source-backed**（`materialized = false`）且 `sourceLocalId`/`sourceRevision` 匹配的 LightTrackerItem（D-017、INV-050）；命中多少个就物化多少个（扇出，INV-044）。

**内容**：普通字段取事件关联的**修改前不可变快照**（D-010、INV-034/037）；附件与关系取目标派生基准，遵循 §2.1。

**步骤**（每次物化一条变体）：

1. 校验该变体仍为 Source-backed 且 `sourceLocalId`/`sourceRevision` 匹配；若已 Local-backed 则跳过（幂等，INV-038/039）。
2. 业务 BC 按 §2.1 组合普通字段快照与目标的附件、关系事实构建完整聚合，**使用预分配的 `LocalId`**，按目标 BC 规则整体校验后保存。
3. 保存成功后，在**单个 LightTrackerItem 聚合事务**中同时更新：`materialized = true`、`sourceLocalId = localId`、`sourceRevision = revision`。
4. 记录物化完成事实；完成记录/事件不得早于实际保存成功（D-012 技术约束）。
5. 若目标 BC 拒绝保存：**不切换状态**（`materialized` 保持 false），按事件重试或告警；不得伪造物化完成。

**完成标志**：业务聚合保存成功 **且** LightTrackerItem 切换成功（`materialized=true`）。只满足其一即为**未完成**（双事实，见 §7）。

## 7. 跨 BC 一致性（双事实，K3′ 用户确认）

LightTrackerItem 与业务聚合是**双事实**：前者持身份/来源/物化状态/定位，后者持完整内容与不变量。两者按 ID 引用，不组合；**不要求业务 BC 与 LightTrackerItem 在同一数据库事务提交**（ADR-0002）。

| 失败模式 | 处理 |
| --- | --- |
| 业务保存成功、LightTrackerItem 切换失败 | **不回滚已保存的业务内容**；幂等重试**仅补齐尚未完成的步骤**，不无条件覆盖已有业务对象；对外不得宣称「已物化」 |
| 业务保存失败 | **不切换状态**；事件重试；超阈值告警，保持 Source-backed |
| 事件重复投递 | 幂等：`materialized=true` 的变体直接跳过，不生成第二个有效物化结果（INV-038） |
| 事件乱序（旧事件晚到） | 已 Local-backed 不再被覆盖（INV-039）；未物化时以首次相关修改的快照为准（INV-037） |
| 物化期间被查询 | 普通字段允许暂时读到来源修改后内容；附件与关系保持 §2.1 的派生基准，不随来源变化；物化切换后返回本地内容 |
| 三字段不一致 | 按元数据一致性缺陷修复（补齐切换），不引入第三种状态 |

**一致性方案**：业务保存成功后**事件驱动**更新 LightTrackerItem + 幂等重试（与 D-009/D-010 一致，不引入跨 BC 大事务）。同库同事务是允许的实现选项，但不是契约要求。**2026-11-12 D3-B 用户裁定**：物化的业务保存由业务 BC 独立提交，SK 的物化凭据与 LTI 切换分事务执行；未完成部分由 Modulith 未完成事件重投收敛，因此本条按“不要求同一事务”执行。

## 8. 与 Merge（T1）和绑定（T2）的衔接

- **未物化目标参与 Diff**：不要求先物化（D-019）。
- **Merge 修改未物化目标**：业务 BC 构建目标聚合并正常 Save（D-019、INV-062），保存成功后通过**同一状态切换行为**更新 LightTrackerItem（`materialized`、`sourceLocalId`、`sourceRevision` 同步切换）。
- **Merge 不直接写** `materialized`、`sourceLocalId`、`sourceRevision`、`revision`：只能经由既有 COW/同步机制。
- **MergeCheckpoint 不以 `materialized` 作为确认依据**：仍使用既定的双侧比较证据与业务 Revision 语义（T1 CK-3/CK-4）。
- **Tracker 删除**继续检查关联 TrackerItem（T2、POL-014），不因状态字段改变生命周期政策；绑定移除不影响变体身份与来源字段。

## 9. 规则性质

既有规则沿用 D-008～D-019 的确认范围；本轮 K1′、K2′、K3′ 分别关联 INV-069、INV-070、INV-071。具体定义见 §3～§8，确认索引见 §10，技术实现排除范围见文首。

## 10. K1′～K3′ 最终裁定（用户确认，2026-11-11）

| 编号 | 用户裁定 | 状态 |
| --- | --- | --- |
| K1′ | §3～§4：物化字段与状态约束 | **用户已确认** |
| K2′ | §5：LocalId 契约 | **用户已确认** |
| K3′ | §6～§7：首次物化与双事实一致性 | **用户已确认** |

### 已被取代的候选（历史痕迹）

| 旧候选 | 状态 | 取代者 |
| --- | --- | --- |
| K1′ 旧推荐：**不设独立标志**，仅由 `sourceLocalId == localId && sourceRevision == revision` 推导物化状态（2026-11-11 T3 草案） | **Superseded** | 用户 2026-11-11 裁定：改用独立 `materialized` 字段；来源字段一致性降为约束，不再是唯一权威状态定义 |

旧推荐保留用于追溯设计理由，不再作为当前有效契约；不得与「独立状态字段」并存为两套有效规则。

## 11. 追溯与下一步

- 追溯：D-007～D-019、FACT-014～023/027、INV-024～052/061～063/069～071、SCN-019～049、SCN-066～068、[ADR-0002](../adr/0002-lighttrackeritem-authoritative-aggregate.md)、[ADR-0003](../adr/0003-cow-event-driven-final-consistency.md)。
- 图：[LightTrackerItem 生命周期](../diagrams/lighttrackeritem-lifecycle.md)、[物化状态与首次物化时序](../diagrams/lighttrackeritem-materialization.md)。
- 评审：[T3 评审记录](../reviews/2026-11-11-t3-materialization-review.md)。
- 当前进度与下一步统一见[阶段 5 计划](../reviews/2026-11-10-stage-5-contract-plan.md)。
