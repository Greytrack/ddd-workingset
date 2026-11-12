# 多级 WorkingSet 谱系设计评审记录

**状态：Draft（2026-11-12；记录用户裁定与设计提案，待评审；不构成基线发布）**

- 依据：[FACT-028](../domain/facts.md)、[FACT-026/POL-004](../domain/facts.md)、[T2 绑定与删除](../domain/binding-deletion-contract-v1.md)、[多级 WorkingSet 谱系设计](../domain/working-set-lineage-design.md)
- 范围：WorkingSet 单父谱系、子 WS 创建、Tracker 来源约束、相邻父子 Merge 准入、删除/停用边界

## 1. 用户裁定（2026-11-12）

1. 子工作集只能有一个父工作集。
2. 允许空范围子 WS。
3. WorkingSet 谱系由自身属性维护，新增 `parentWorkingSetId` 追溯直属上级。
4. 有直属子 WS 的中间 WS 不允许删除或停用（有派生子级就不允许）；叶子 WS 删除按 T2 K3。
5. 设计不引入 GateFlow 特例。

解释说明：第 4 条按“有直属子 WS 的中间 WS 不允许删除/停用”记录；停用的状态与命令尚未纳入 T2，本轮只登记规则，不实现停用状态。

## 2. 设计结论

- `WorkingSet.parentWorkingSetId`：默认根 `null`，其余指向直属父；创建后不可变；禁止自指与环。
- 单父树；允许空范围子 WS；子 WS 的 Tracker 只能来自直属父 WS，子范围是父范围的子集。
- Merge 只允许直属父子双向；兄弟/跨代拒绝；共享 Tracker 不参与。
- 有子 WS 的中间 WS 拒绝删除/停用；叶子删除继续按 T2 K3；默认 WS 始终不可删。
- 不引入 GateFlow 特例，不使用 GateFlow 父子链作为模型依据。

## 3. 方案取舍

- 采用 WorkingSet 自身 `parentWorkingSetId`，而不是独立 lineage 表或完全由 Tracker 派生链推导：用户明确选择 WorkingSet 自身属性；它能表达空范围子 WS 与父子判定，且不让 WorkingSet 变成树聚合（只增加一个追溯属性）。
- Tracker 的 `sourceId`/`parentId` 仍分别表达派生链与同 WS 组织父级，不与 WS 谱系合并。

## 4. 开放问题

1. 停用的状态、命令、对子 WS 与 Tracker 的影响。
2. 空范围子 WS 的 Compare/Merge 行为。
3. 删除中间 WS 的检查顺序与失败语义。
4. 多级下 `Tracker.parentId`（同 WS 组织父级）与 `sourceId`（派生来源）的联合校验。
5. 真实平台元数据中既有 WorkingSet 父级的回填与校验。
6. 父 WS 解绑/删除 Tracker 后，子 WS 已存在绑定的保留与清理。

## 5. 未做

- 未写生产代码、未改 schema、未修改 T1～T4 冻结契约、未发布基线。
- 设计文档与图表均为 Draft；实施需用户确认后进入 S6-A。
