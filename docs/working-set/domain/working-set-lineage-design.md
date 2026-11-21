# 多级 WorkingSet 谱系设计

**状态：Reviewed（2026-11-12；FACT-028 用户确认，S6 已实施并验证；未发布基线）**

> 同槽多 Tracker、Shared 默认资格、组织父级与派生约束见 [T6-F 裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md)。

-

依据：[FACT-026 / FACT-028](facts.md)、[POL-004](facts.md)、[T2 绑定与删除](binding-deletion-contract-v1.md)、[WorkingSet/Tracker 聚合](aggregate-model.md)

- 范围：多级 WorkingSet 谱系、子工作集创建、Tracker 来源约束、相邻父子 Merge 准入、删除边界
- 不包含：Merge 执行契约本身（T1）、Privilege 授权、ProductSpace/Classification 完整生命周期

## 1. 用户裁定（2026-11-12）

1. **子工作集只能有一个父工作集。**
2. **允许空范围子工作集**（可先创建、后纳入 Tracker）。
3. **WorkingSet 的谱系由 WorkingSet 自身属性维护**：新增 `parentWorkingSetId` 追溯直属上级；默认 WorkingSet 为空。
4. **有直属子工作集的中间 WorkingSet 不允许删除**；叶子 WorkingSet 才允许删除，并继续受 T2 K3 成员关系约束。WorkingSet 与
   Tracker 不存在“停用”生命周期（同日裁定），不设停用状态或命令。
5. **设计不引入 GateFlow 特例**：只描述通用 WorkingSet 谱系，不以 GateFlow 版次链作为模型依据。

## 2. 术语

| 术语              | 含义                                                                                 |
|-------------------|--------------------------------------------------------------------------------------|
| WorkingSet 谱系   | 由 `parentWorkingSetId` 形成的单父树；根为默认 WorkingSet `Id=1`                     |
| 直属父 WorkingSet | `parentWorkingSetId` 指向的工作集                                                    |
| 子 WorkingSet     | `parentWorkingSetId` 指向本 WS 的工作集；一个父可以有多个子                          |
| 子工作集范围      | 按 Tracker 来源计算的纳入范围；新绑定必须来自直属父 WS 当前有效范围                  |
| 相邻父子          | `target.parentWorkingSetId == source.id` 或 `source.parentWorkingSetId == target.id` |
| 派生链            | Tracker 的 `sourceId` 链；与 WorkingSet 谱系是不同维度                               |

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
- 父工作集必须存在，新 ID 必须未占用且与父工作集 ID 不同。
- 后置：创建 `parentWorkingSetId = parent` 的 WorkingSet；允许空范围。
- 不自动复制父 WS 的 SpaceBinding/TrackerBinding；子 WS 范围由后续纳入决定。
- 子 WS 的 Tracker 只能来自直属父 WS；不得从祖先跨级获得。

### 3.3 Tracker 来源约束

- 子 WS 建立 TrackerBinding 时：
    - Shared：复用直属父 WS 中已绑定的 Tracker；
    - 非共享派生：新 Tracker 的 `sourceId` 指向直属父 WS 的 Tracker；
    - 父级没有的 Tracker，子级不能绑定/派生；
    - 跨级（从祖父或更远）一律拒绝。
- 建立绑定时按来源校验子集。Shared 比较复用的 TrackerId，非共享比较直接 `sourceId`。默认根的有效范围来自原生 Tracker
  投影。空范围是空集。
- 父 WS 解绑某 Tracker 时，已存在的子 WS 绑定不自动级联删除；后续准入与清理按 T2 与独立切片处理。

### 3.4 Merge 准入

- 只允许相邻父子双向：`target.parent == source` 或 `source.parent == target`。
- 兄弟、跨代（祖先/后代非直接）一律拒绝。
- 非共享 Tracker 的直接派生配对规则见 [T1 Compare](merge-execution-contract-v1.md#31-查询能力)。
- 共享 Tracker 不参与 Merge（T1）。
- Merge 的 source/target 是角色，不改变谱系方向。

### 3.5 删除

- 有直属子 WS 的 WorkingSet：
    - 不允许 `deleteWorkingSet`（返回 `WorkingSetHasChildren`）。
- 叶子 WorkingSet：
    - 删除继续按 T2 K3：默认 WS 始终拒绝；全部 TrackerBinding 可合法解除 → SpaceBinding → WorkingSet。
- 删除中间 WS 不得隐式级联删除子 WS 或重挂 `parentWorkingSetId`。

## 4. 与现有规则的关系

- `FACT-026` 的“子 WS 只能纳入直接上游 Tracker”在多级下解释为“直属父 WorkingSet 的 Tracker”；S5 的“默认 WS 特例”只是首层范围。
- `POL-004/FACT-007` 的“直接父子双向 Merge”由本设计的 `parentWorkingSetId` 判定；兄弟/跨代拒绝不变。
- T2 的绑定/解绑/删除规则不变；本设计只增加谱系与来源准入。
- 不考虑 GateFlow 版次链特例；GateFlow 场景继续由其独立边界文档处理。

## 5. 实施与验收

S6 的父级回填、查询与防环、直属父来源、双向 Merge
准入和叶子删除，见 [S6 验证](../implementation/t5-s6-verification-result.md)
。数据库映射见 [schema.sql](../../../deploys/src/main/resources/schema.sql)。

## 6. 开放问题

业务待裁定项集中在 [开放问题](open-questions.md)。谱系仍需验证的边界：

1. 空范围子 WS 的实际 Merge/Compare 响应。S6 准入策略返回空候选。
2. 跨工作集的批量删除顺序。单工作集先保护默认根，再拒绝有子 WS（`WorkingSetHasChildren`），叶子按 T2 K3 执行。
3. 多级下 `Tracker.parentId`（同 WS 组织父级）与 `sourceId`（派生来源）如何与 WS 谱系联合校验；T6-F 已定 `sourceId`
   不可变，联合校验仍待设计。
4. 真实平台元数据中既有 WorkingSet 的父级回填来源与校验方式。
5. 父 WS 解绑/删除 Tracker 后，子 WS 已存在的绑定是否保留、如何清理。
