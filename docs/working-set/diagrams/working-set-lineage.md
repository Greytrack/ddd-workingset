# WorkingSet 谱系与相邻父子 Merge

**状态：Reviewed（FACT-028 已确认，S6 已实施；图本身未纳入正式基线）**

> 图中的 Tracker 数量仅作示例。同槽多 Tracker 与默认资格见 [T6-F 裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md)。

目的：展示 WorkingSet 单父谱系、子 WS Tracker 来源约束，以及 Merge 只允许直属父子的准入。不表达 GateFlow 版次链特例。

## 1. 谱系树

```mermaid
flowchart TD
    D["默认 WorkingSet<br/>Id=1<br/>parent=null"]
    A["子 WorkingSet A<br/>parent=1"]
    B["子 WorkingSet B<br/>parent=1"]
    A1["孙 WorkingSet A1<br/>parent=A"]
    A2["孙 WorkingSet A2<br/>parent=A"]
    D --> A
    D --> B
    A --> A1
    A --> A2
```

- 单父：每个非默认 WS 只有一个 `parentWorkingSetId`。
- 空范围子 WS 允许存在；谱系不因无 TrackerBinding 而缺失。
- 不允许自指、反向父子或环。

## 2. Tracker 来源约束

```mermaid
flowchart LR
    S0["默认 WS 的 Shared Tracker S"]
    SA["A 绑定 S"]
    SA1["A1 绑定 S"]
    S0 -->|"Shared 复用"| SA
    SA -->|"Shared 复用"| SA1
    T0["默认 WS 的非共享 Tracker T0"]
    TA["A 的 Tracker T1<br/>source=T0"]
    TA1["A1 的 Tracker T2<br/>source=T1"]
    T0 -->|"非共享派生"| TA
    TA -->|"非共享派生"| TA1
    T0 -. "跨级拒绝" .-> TA1
```

- 子 WS 只能从直属父 WS 获得 Tracker。
- Shared 复用父 WS 的 TrackerId；非共享创建新 Tracker + `sourceId` 指向父 WS 的 Tracker。
- 新绑定按 Tracker 来源校验直属父级范围。已有绑定的后续清理见[谱系设计](../domain/working-set-lineage-design.md)。

## 3. Merge 准入

```mermaid
flowchart TD
    S["source WS"] -->|"target.parent == source"| T["target WS"]
    T -->|"source.parent == target（反向）"| S
    B1["兄弟 WS"] -. "拒绝" .-> B2["兄弟 WS"]
    G1["祖先 WS"] -. "跨代拒绝" .-> G2["孙 WS"]
```

- 只允许直属父子双向；`source/target` 是角色，不改变谱系方向。
- Tracker 级候选只按直接 `sourceId` 配对；同一父的多个直接子各自成对，派生兄弟之间不配对，跨级不配对。
- 兄弟 WS、跨代 WS 一律拒绝；共享 Tracker 不参与 Merge。

## 4. 删除边界

```mermaid
flowchart TD
    M["中间 WS（有直属子 WS）"] -->|"delete"| R["拒绝（WorkingSetHasChildren）"]
    L["叶子 WS"] -->|"delete"| D["按 T2 K3：解绑 → 解空间 → 删除"]
    Root["默认 WS"] -->|"delete"| RR["始终拒绝"]
```

- WorkingSet 与 Tracker 不存在“停用”生命周期（用户 2026-11-12 裁定），故不表达停用状态或命令。

## 5. 未表达内容

- 不表达 GateFlow 版次链特例。
- 不表达 Tracker 组织树（`parentId`）与派生链（`sourceId`）的内部细节，只表达它们与 WS 谱系的不同维度。
- 不表达 MergeRound/Checkpoint 的执行细节，见 T1 契约。
