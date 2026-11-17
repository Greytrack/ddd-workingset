# T6-F 谱系约束裁定（2026-11-16）

**状态：Accepted（用户 2026-11-16 裁定）**。范围：[QUESTION-002](../domain/open-questions.md) 在 D-003～D-007 之后仍开放的 5 项（f、e、d2、i、完成可见性）。依据：[多级 WorkingSet 谱系设计](../domain/working-set-lineage-design.md)、[T6-F 大纲](../implementation/t6-next-stage-outline.md)。

## 1. 裁定

### 1.1 f Shared Tracker 父级一致性 —— 允许悬挂

用户裁定：Shared Tracker 自身生命周期在默认工作集维护，`parentId` 始终不变；子工作集可以不绑定它的父 Tracker，允许悬挂；子工作集视图里能构成树就构建成树，构建不了也不阻塞。

- Shared Tracker 只有一行，`parentId` 是默认工作集维护的字段；绑定工作集不复制、不覆盖、不校验它。
- 子工作集绑定 Shared Tracker 时不要求其父在本工作集已绑定；组织树渲染以“父可解析则挂、不可解析则作根”为准（现状 `renderTrackerTree` 已按此实现）。
- 非共享派生 Tracker 的 `parentId` 仍由派生时确定，`Tracker.derived` 置 `null`；本裁定不要求派生继承来源的组织父级。

### 1.2 e 同槽多 Tracker（独立分类投影）—— 引入 `is_default`

用户裁定：同一工作集、同一产品空间 + 同一分类允许多个 Tracker；第一个创建的是默认 Tracker，默认资格与 Shared 例外见 §4.3（有效范围内至多一个）；新增 `is_default` 标识；产品空间创建时同步创建默认 Tracker；产品空间创建后可在空间内按分类投影新建同一分类的多个 Tracker；Tracker 名称不能重复。

> **2026-11-17 语义澄清**：这里“同源”指同一产品空间 + 分类的**分类投影源**，不是多个 Tracker 共享同一个父 Tracker。每个 Tracker 的派生链是 1:1：子 Tracker 的 `sourceId` 指向一个具体父 Tracker，且同一子工作集对同一父 Tracker 至多一个派生 Tracker。多个同分类 Tracker 各自有独立的 `sourceId`/派生链，彼此不是兄弟派生关系。

- 默认资格的现行规则见 §4.3。
- **业务原生 Tracker 只能在默认工作集创建**；子工作集不允许独立创建 Tracker，只能从直属父工作集纳入 Shared Tracker 或派生非共享 Tracker。名称唯一因此是“业务原生 Tracker 创建作用域”的约束，不是绑定作用域。
- “产品空间创建”同时创建默认 Tracker（业务原生，位于默认工作集）；其余 Tracker 通过既有派生/新建入口创建。
- Item 层仍受 POL-005 约束：同一工作集同一 CommonId 只有一个有效变体；本裁定不放宽 Item 唯一性。

### 1.3 d2 `sourceId` 可变性 —— 不可变

派生建立后 `sourceId` 不可修改；来源错误只能删除重建（受删除守卫约束）。

### 1.4 i 派生整批原子 —— LRO 范式，整批原子

批量派生按 T7 的 LRO 范式：受理时冻结快照、异步执行、全部成功才可见；失败整批不可见、可重试。

> **2026-11-17 T7-G 补充**：创建新子工作集与扩展已有子工作集均允许**部分成功**。因此本节“整批原子”不再适用于 T7 创建/扩展 LRO；扩展按部分成功记录明细、成功项保留。若未来保留独立的“批量派生”入口，其原子性需另行定义，不能直接套用到扩展 LRO。

### 1.5 创建完成的可见性 —— 完成即可见

LRO 成功后即可见、可绑定、可合并；不引入发布闸门。发布/基线语义留给 T6-G（QUESTION-011）。

## 2. 对现有实现的影响

| 项 | 现状 | 需要改动 |
| --- | --- | --- |
| f 允许悬挂 | `tracker_bindings` 不校验父可解析；UI 已把不可解析父作根 | **无代码改动**，补读模型与文档说明 |
| e 同槽多 Tracker | 原状：`tracker_bindings` 有 `UNIQUE (working_set_id, product_space_id, classification_id)`；无 `is_default`；无产品空间创建入口 | **已于 2026-11-17 完成**（见 §3/§5）；分类投影多 Tracker，派生链仍为 1:1 |
| d2 不可变 | 无更新 `source_id` 的路径 | 无代码改动 |
| i 批量派生 | 只有单条 `/derive`；T7-G 已新增扩展 LRO，按部分成功执行 | **2026-11-17 已由 T7-G 部分成功语义取代**；单 Item `/derive` 公开入口已删除 |
| 完成即可见 | `requireReady` + creation 状态已实现 | 无代码改动 |

## 3. e 的实现清单（2026-11-17 已实施，保留原清单）

1. `trackers` 增加 `is_default BOOLEAN NOT NULL DEFAULT FALSE`，部分唯一索引保证每个（所有者工作集，空间，分类）至多一个默认。**已实施**。
2. `tracker_bindings` 去掉 `UNIQUE (working_set_id, product_space_id, classification_id)`，主键保持 `(working_set_id, tracker_id)`。**已实施**。
3. 新增产品空间创建命令并同步创建默认 Tracker。**已实施**（`POST /api/worksets/{id}/product-spaces`）。产品空间只能归属默认工作集：路径中的 `{id}` 仅为入口占位，不参与目标选择，所有调用一律固定到默认工作集；入口层不做额外限制，这是有意设计，不是待收紧项。
4. Tracker 名称唯一约束：**已实施**为 `(working_set_id, product_space_id, classification_id, name)`，仅业务原生 Tracker 参与；因业务原生 Tracker 只存在于默认工作集，实际等价于（空间，分类）内唯一。作用域说明见 §4 注。
5. compare/merge 候选配对：**已按 §4.2 实施**。只按直接 `sourceId` 配对；跨级、派生兄弟互配、同槽全组合均被排除。`Difference.key` 已于 2026-11-17 废弃，条目身份由 `MergeRound.Entry.id` + 完整 evidence 承担，见 §4.1。

## 4. 2026-11-16 补充裁定（细化）

| 项 | 裁定 |
| --- | --- |
| 名称唯一作用域 | 同一（产品空间，分类）内唯一。业务原生 Tracker 只在默认工作集创建，2026-11-17 实施为（工作集，空间，分类）索引；因创建只发生在默认工作集，两者当前等价。子工作集不允许原生创建，故不产生跨工作集同名原生 Tracker；未来若开放子 WS 原生创建，需重新裁定 |
| 多 Tracker 下的 compare/merge 配对 | 条目证据由左 trackerId + 右 trackerId + 左 workingSetId + 右 workingSetId + commonId + 左 localId + 右 localId + 左版次 + 右版次组成；轮次内条目身份由 `MergeRound.Entry.id` 承担 |
| 产品空间创建入口 | 新增 `POST /api/worksets/{id}/product-spaces`，同时创建默认 Tracker。产品空间只能在默认工作集创建：`{id}` 仅为入口占位、不参与目标选择，任何调用都固定到默认工作集；入口层不限制调用来源，这是有意设计 |

### 4.1 实现含义

- 名称唯一按（工作集，空间，分类）实现，仅业务原生 Tracker 参与。业务原生 Tracker 只能在默认工作集创建，因此当前等价于原裁定的（产品空间，分类）内唯一；子工作集不能原生创建 Tracker，故不会出现跨工作集同名原生 Tracker。派生 Tracker 继承来源名称，不参与唯一约束。若未来允许子工作集原生创建，需要重新确认作用域。
- **2026-11-17 裁定：`Difference.key` 废弃。** 客户端不再提交 `entryKey`；轮次内条目身份为 `MergeRound.Entry.id`。`TrackerPair` 为有向四元组 `(sourceWorkingSetId, sourceTrackerId, targetWorkingSetId, targetTrackerId)`，WorkingSet 是本次比较角色上下文；跨轮次审计使用完整 evidence（四元组 + 家族 + 双侧 LocalId/Revision/Presence）。Checkpoint 仍只使用两侧 TrackerId + 家族，不吸收 WorkingSet。
- 同一家族的 Item 在同一工作集只属于一个 Tracker（POL-005 与 Item 归属），所以“左侧 Tracker 集合 × 右侧 Tracker 集合”不会让同一家族重复出现。
- Checkpoint 按（规范化 Tracker 对，家族）判定“已处理”，方向只是属性；WorkingSet 不进入 Checkpoint 身份。轮次上下文与条目证据中的双侧定位（含 LocalId、版次）用于过期校验，不改变 Checkpoint 键。详见 [Checkpoint 身份修正](2026-11-16-merge-checkpoint-legacy-alignment.md)。

### 4.2 候选配对（QUESTION-002.j，2026-11-17 用户确认）

- **只按 Tracker 的直接 `sourceId` 派生关系配对**：子 Tracker 的 `sourceId` 必须等于父侧 Tracker 的 `trackerId`。
- 只允许直属派生父子；跨级（祖父/更远）不配对，派生兄弟节点之间不配对。
- 不做同槽“左侧全部 Tracker × 右侧全部 Tracker”的全组合。
- 同一父 Tracker 的多个直接子 Tracker 各自独立成对；每个子 Tracker 只对应自己的直接来源。
- 来源无法在直属父工作集有效范围内解析时不生成候选对，不回退到同槽其他 Tracker。

### 4.3 Shared 默认与删除补位（QUESTION-002.k，2026-11-17 用户确认）

- 默认标记保留在 `Tracker.is_default`，按所有者工作集、空间、分类至多一个。子工作集只在绑定了根工作集的默认 Shared Tracker 时才拥有该 Shared 默认项；只绑定非默认 Shared Tracker 的槽可以没有默认项。
- 解绑或删除默认 Tracker 后，同槽其余 Tracker 保留原标记。删除仍按 T2 准入规则执行。
- `/api/worksets/{id}/trackers` 返回自有 Tracker 与已绑定 Tracker 的并集，并展示原 Tracker 的 `isDefault`。验收见 [BindingAcceptanceTest](../../../deploys/src/test/java/bootstrap/BindingAcceptanceTest.java)。

## 5. 实施状态（2026-11-16）

- 已按 §2 落地：`trackers.is_default` + 部分唯一索引、名称唯一（仅业务原生 Tracker）、去掉绑定三元唯一键、`POST /api/worksets/{id}/product-spaces` 固定到默认工作集并同步默认 Tracker/绑定、派生默认标记；`Difference.key`/`entryKey` 已废弃，条目身份改为 `Entry.id` + evidence。
- [TrackerDefaultAcceptanceTest](../../../deploys/src/test/java/bootstrap/TrackerDefaultAcceptanceTest.java) 覆盖默认唯一、名称唯一、绑定约束移除，以及 [SCN-051](../scenarios/core-scenarios.md#scn-051) 的产品空间初始绑定和 Item 读取；
- 全量测试 150 项通过（含错误码契约、默认 Tracker 边界、TrackerPair 四元组、直接 `sourceId` 配对、T7-G 扩展与关系事件契约/发布器测试）。

## 6. 关联材料

- [QUESTION-002](../domain/open-questions.md)、[多级 WorkingSet 谱系设计](../domain/working-set-lineage-design.md)、[T6 后续大纲](../implementation/t6-next-stage-outline.md)
- 场景：SCN-014、SCN-057～060（Compare/Merge）、T5-S6 谱系验证
- 影响文档：[绑定与删除契约](../domain/binding-deletion-contract-v1.md)（“最后一个 Tracker”判定）、[merge-execution-contract-v1](../domain/merge-execution-contract-v1.md)（候选配对）
