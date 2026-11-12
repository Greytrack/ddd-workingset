# 多级 WorkingSet 谱系设计

**状态：Draft（2026-11-12；核心谱系规则由用户确认，设计待评审；不构成正式基线）**

- 依据：[FACT-026 / FACT-028](facts.md)、[POL-004](facts.md)、[T2 绑定与删除](binding-deletion-contract-v1.md)、[WorkingSet/Tracker 聚合](aggregate-model.md)
- 范围：多级 WorkingSet 谱系、子工作集创建、Tracker 来源约束、相邻父子 Merge 准入、删除/停用边界
- 不包含：Merge 执行契约本身（T1）、停用状态实现、Privilege 授权、ProductSpace/Classification 完整生命周期

## 1. 用户裁定（2026-11-12）

1. **子工作集只能有一个父工作集。**
2. **允许空范围子工作集**（可先创建、后纳入 Tracker）。
3. **WorkingSet 的谱系由 WorkingSet 自身属性维护**：新增 `parentWorkingSetId` 追溯直属上级；默认 WorkingSet 为空。
4. **有直属子工作集的中间 WorkingSet 不允许删除或停用**；叶子 WorkingSet 才允许删除，并继续受 T2 K3 成员关系约束。停用的状态与命令仍待实现。
5. **设计不引入 GateFlow 特例**：只描述通用 WorkingSet 谱系，不以 GateFlow 版次链作为模型依据。

## 2. 术语

| 术语 | 含义 |
| --- | --- |
| WorkingSet 谱系 | 由 `parentWorkingSetId` 形成的单父树；根为默认 WorkingSet `Id=1` |
| 直属父 WorkingSet | `parentWorkingSetId` 指向的工作集 |
| 子 WorkingSet | `parentWorkingSetId` 指向本 WS 的工作集；一个父可以有多个子 |
| 子工作集范围 | 按 Tracker 来源计算的纳入范围；新绑定必须来自直属父 WS 当前有效范围 |
| 相邻父子 | `target.parentWorkingSetId == source.id` 或 `source.parentWorkingSetId == target.id` |
| 派生链 | Tracker 的 `sourceId` 链；与 WorkingSet 谱系是不同维度 |

## 3. 模型

### 3.1 WorkingSet 聚合

- 新增属性 `parentWorkingSetId`（可空）：
  - 默认 WorkingSet `Id=1`：`null`；
  - 其他 WorkingSet：必须指向一个已存在的 WorkingSet；
  - 创建后不可变；不允许自指；不允许形成环。
- `WorkingSet` 仍持有 `SpaceBinding` / `TrackerBinding`；谱系属性不改变绑定 Entity 的归属。
- 不把子工作集集合放入聚合；父子查询通过仓储按 `parentWorkingSetId` 查询。
- 空范围子 WS 合法：允许无 SpaceBinding/TrackerBinding。

### 3.2 创建子工作集

- 操作：`createChildWorkingSet(parentWorkingSetId, newWorkingSetId)`。
- 前置：父 WS 存在；新 ID 未占用；`parentWorkingSetId` 非默认自指。
- 后置：创建 `parentWorkingSetId = parent` 的 WorkingSet；允许空范围。
- 不自动复制父 WS 的 SpaceBinding/TrackerBinding；子 WS 范围由后续纳入决定。
- 子 WS 的 Tracker 只能来自直属父 WS；不得从祖先跨级获得。

### 3.3 Tracker 来源约束

- 子 WS 建立 TrackerBinding 时：
  - Shared：复用直属父 WS 中已绑定的 Tracker；
  - 非共享派生：新 Tracker 的 `sourceId` 指向直属父 WS 的 Tracker；
  - 父级没有的 Tracker，子级不能绑定/派生；
  - 跨级（从祖父或更远）一律拒绝。
- 建立绑定时按来源校验子集。Shared 比较复用的 TrackerId，非共享比较直接 `sourceId`。默认根的有效范围来自原生 Tracker 投影。空范围是空集。
- 父 WS 解绑某 Tracker 时，已存在的子 WS 绑定不自动级联删除；后续准入与清理按 T2 与独立切片处理。

### 3.4 Merge 准入

- 只允许相邻父子双向：`target.parent == source` 或 `source.parent == target`。
- 兄弟、跨代（祖先/后代非直接）一律拒绝。
- 共享 Tracker 不参与 Merge（T1）。
- Merge 的 source/target 是角色，不改变谱系方向。

### 3.5 删除与停用

- 有直属子 WS 的 WorkingSet：
  - 不允许 `deleteWorkingSet`；
  - 不允许停用（停用状态/命令待实现，规则先登记）。
- 叶子 WorkingSet：
  - 删除继续按 T2 K3：默认 WS 始终拒绝；全部 TrackerBinding 可合法解除 → SpaceBinding → WorkingSet；
  - 停用的具体行为待独立设计。
- 删除中间 WS 不得隐式级联删除子 WS 或重挂 `parentWorkingSetId`。

## 4. 与现有规则的关系

- `FACT-026` 的“子 WS 只能纳入直接上游 Tracker”在多级下解释为“直属父 WorkingSet 的 Tracker”；S5 的“默认 WS 特例”只是首层范围。
- `POL-004/FACT-007` 的“直接父子双向 Merge”由本设计的 `parentWorkingSetId` 判定；兄弟/跨代拒绝不变。
- T2 的绑定/解绑/删除规则不变；本设计只增加谱系与来源准入。
- 不考虑 GateFlow 版次链特例；GateFlow 场景继续由其独立边界文档处理。

## 5. 持久化映射

```sql
ALTER TABLE working_sets
    ADD COLUMN IF NOT EXISTS parent_working_set_id VARCHAR(128)
        REFERENCES working_sets (working_set_id);

-- S5 已存在的首层子 WS 回填默认根；多级历史数据需按真实平台元数据回填。
UPDATE working_sets
SET parent_working_set_id = '1'
WHERE working_set_id <> '1' AND parent_working_set_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_working_sets_parent
    ON working_sets (parent_working_set_id);
```

- `parent_working_set_id` 可空仅用于默认根；非根应在应用层/迁移中保证非空。
- 自指与环由应用层校验；数据库可加 `CHECK (parent_working_set_id IS NULL OR parent_working_set_id <> working_set_id)`。
- 不引入 Flyway/Liquibase；继续 `schema.sql` 的 `IF NOT EXISTS` 风格。

## 6. 场景与测试映射

| 场景 | 断言 |
| --- | --- |
| 创建子 WS | 默认 WS `parent=null`；子 WS `parent=1`；允许空范围 |
| 单父约束 | 一个子 WS 只能有一个 `parentWorkingSetId`；创建后不可变 |
| 多级树 | 默认 WS → 子 WS → 孙 WS；查询可回溯直属上级 |
| 防环/自指 | 自指、反向父子、跨级重挂均拒绝 |
| 子 WS Tracker 来源 | 只能绑定/派生直属父 WS 的 Tracker；跨级/父级没有的 Tracker 拒绝 |
| 空范围 | 空子 WS 可创建；纳入后范围是父级子集 |
| Merge 准入 | 直属父子双向通过；兄弟/跨代拒绝；共享 Tracker 拒绝 |
| 中间 WS 删除/停用 | 有子 WS 时拒绝；叶子按 T2 K3；默认 WS 拒绝 |
| 回归 | S1～S5 58 项、ArchUnit、真实 PG/Redis/Tomcat |

## 7. 实施切片（计划）

| 切片 | 内容 | 依赖 |
| --- | --- | --- |
| S6-A | `WorkingSet.parentWorkingSetId`、schema/回填、`createChildWorkingSet`、谱系查询、防环 | S5 |
| S6-B | Tracker 来源改为直属父 WS；空范围；范围子集；绑定/派生改造 | S6-A |
| S6-C | Merge 相邻父子准入（双向、兄弟/跨代拒绝） | S6-A + T1 Merge |
| S6-D | 删除/停用中间 WS 约束；停用状态与命令另立设计 | S6-A + T2 |

## 8. 开放问题

1. 停用的状态、命令、对子 WS 与 Tracker 的影响（T2 未纳入；QUESTION-009.a）。
2. 空范围子 WS 的实际 Merge/Compare 响应。S6 准入策略返回空候选。
3. 跨工作集的批量删除顺序。单工作集先保护默认根，再拒绝有子 WS（`WorkingSetHasChildren`），叶子按 T2 K3 执行。
4. 多级下 `Tracker.parentId`（同 WS 组织父级）与 `sourceId`（派生来源）如何与 WS 谱系联合校验。
5. 真实平台元数据中既有 WorkingSet 的父级回填来源与校验方式。
6. 父 WS 解绑/删除 Tracker 后，子 WS 已存在的绑定是否保留、如何清理。

## 9. 状态与下一步

- 用户已确认第 1 节的核心规则。S6 按明确实施授权完成，实施证据和后续范围见 [S6 验证结果](../implementation/t5-s6-verification-result.md)。
