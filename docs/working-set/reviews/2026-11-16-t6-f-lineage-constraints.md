# T6-F 谱系约束裁定（2026-11-16）

**状态：Accepted（用户 2026-11-16 裁定）**。范围：[QUESTION-002](../domain/open-questions.md) 在 D-003～D-007 之后仍开放的 5 项（f、e、d2、i、完成可见性）。依据：[多级 WorkingSet 谱系设计](../domain/working-set-lineage-design.md)、[T6-F 大纲](../implementation/t6-next-stage-outline.md)。

## 1. 裁定

### 1.1 f Shared Tracker 父级一致性 —— 允许悬挂

用户裁定：Shared Tracker 自身生命周期在默认工作集维护，`parentId` 始终不变；子工作集可以不绑定它的父 Tracker，允许悬挂；子工作集视图里能构成树就构建成树，构建不了也不阻塞。

- Shared Tracker 只有一行，`parentId` 是默认工作集维护的字段；绑定工作集不复制、不覆盖、不校验它。
- 子工作集绑定 Shared Tracker 时不要求其父在本工作集已绑定；组织树渲染以“父可解析则挂、不可解析则作根”为准（现状 `renderTrackerTree` 已按此实现）。
- 非共享派生 Tracker 的 `parentId` 仍由派生时确定，`Tracker.derived` 置 `null`；本裁定不要求派生继承来源的组织父级。

### 1.2 e 同源多派生 —— 允许多个，引入 `is_default`

用户裁定：同一工作集、同一来源（同产品空间 + 同分类）允许多个 Tracker；第一个创建的是默认 Tracker，有且仅有一个默认 Tracker；新增 `is_default` 标识；产品空间创建时同步创建默认 Tracker；产品空间创建后可在空间内按分类投影新建同一分类的多个 Tracker；Tracker 名称不能重复。

- 每个（工作集，产品空间，分类）恰好一个 `is_default = true` 的 Tracker。
- “产品空间创建”同时创建默认 Tracker（业务原生，位于默认工作集）；其余 Tracker 通过既有派生/新建入口创建。
- Item 层仍受 POL-005 约束：同一工作集同一 CommonId 只有一个有效变体；本裁定不放宽 Item 唯一性。

### 1.3 d2 `sourceId` 可变性 —— 不可变

派生建立后 `sourceId` 不可修改；来源错误只能删除重建（受删除守卫约束）。

### 1.4 i 派生整批原子 —— LRO 范式，整批原子

批量派生按 T7 的 LRO 范式：受理时冻结快照、异步执行、全部成功才可见；失败整批不可见、可重试。

### 1.5 创建完成的可见性 —— 完成即可见

LRO 成功后即可见、可绑定、可合并；不引入发布闸门。发布/基线语义留给 T6-G（QUESTION-011）。

## 2. 对现有实现的影响

| 项 | 现状 | 需要改动 |
| --- | --- | --- |
| f 允许悬挂 | `tracker_bindings` 不校验父可解析；UI 已把不可解析父作根 | **无代码改动**，补读模型与文档说明 |
| e 多派生 | `tracker_bindings` 有 `UNIQUE (working_set_id, product_space_id, classification_id)`；无 `is_default`；无产品空间创建入口 | **结构性改动**（见 §3） |
| d2 不可变 | 无更新 `source_id` 的路径 | 无代码改动 |
| i 整批原子 | 只有单条 `/derive`，天然原子；批量派生未实现 | 新增批量派生 LRO（复用 T7 范式） |
| 完成即可见 | `requireReady` + creation 状态已实现 | 无代码改动 |

## 3. e 的实现清单（待细化项确认后执行）

1. `trackers` 增加 `is_default BOOLEAN NOT NULL DEFAULT FALSE`，部分唯一索引保证每个（工作集，空间，分类）只有一个默认。
2. `tracker_bindings` 去掉 `UNIQUE (working_set_id, product_space_id, classification_id)`，主键保持 `(working_set_id, tracker_id)`。
3. 新增产品空间创建命令并同步创建默认 Tracker（当前只有删除绑定入口）。
4. Tracker 名称唯一约束（作用域待定）。
5. compare/merge 候选配对：同一（空间，分类）多 Tracker 时的配对规则（必须新增裁定）。

## 4. 2026-11-16 补充裁定（细化）

| 项 | 裁定 |
| --- | --- |
| 默认 Tracker 键范围 | 每个（工作集，空间，分类）恰好一个默认 |
| 名称唯一作用域 | 同一（产品空间，分类）内唯一 |
| 多派生后的 compare/merge 配对 | 条目身份 = 左 trackerId + 右 trackerId + 左 workingSetId + 右 workingSetId + commonId + 左 localId + 右 localId + 左版次 + 右版次 |
| Shared 与 `is_default` | Shared 分类也参与；每个（工作集，空间，分类）都有默认 Tracker |
| 产品空间创建入口 | 新增 `POST /api/workspaces/1/product-spaces`，同时创建默认 Tracker |

### 4.1 实现含义

- `is_default` 是（工作集，空间，分类）上的唯一默认标记，用部分唯一索引兑现；Shared 与 UNSHARED 都参与。
- 名称唯一改为（产品空间，分类）内唯一，不再跨空间或跨工作集比较。
- 条目身份包含双侧 Tracker 与工作集：`Comparison.TrackerPair` 需要携带 workingSetId；`Difference.key` 改为由该复合键派生的稳定串，不再使用序号。
- 同一家族的 Item 在同一工作集只属于一个 Tracker（POL-005 与 Item 归属），所以“左侧 Tracker 集合 × 右侧 Tracker 集合”不会让同一家族重复出现。
- Checkpoint 仍按（方向，家族）判定“已处理”；条目证据中的双侧定位用于过期校验。

### 4.2 仍需确认

候选对的生成方式：同一（空间，分类）下取“左侧全部 Tracker × 右侧全部 Tracker”的全部组合，还是先按 `source_id`/`is_default`/名称过滤后再配对？

## 5. 关联材料

- [QUESTION-002](../domain/open-questions.md)、[多级 WorkingSet 谱系设计](../domain/working-set-lineage-design.md)、[T6 后续大纲](../implementation/t6-next-stage-outline.md)
- 场景：SCN-014、SCN-057～060（Compare/Merge）、T5-S6 谱系验证
- 影响文档：[绑定与删除契约](../domain/binding-deletion-contract-v1.md)（“最后一个 Tracker”判定）、[merge-execution-contract-v1](../domain/merge-execution-contract-v1.md)（候选配对）
