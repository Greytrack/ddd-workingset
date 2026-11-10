# LightTrackerItem 派生、解析与物化生命周期

**状态：Draft / Candidate；派生与身份分配由 D-008 确认，物化协议与并发仍候选。**

目的：表达 LightTrackerItem 从派生（Derive）到解析（Resolve）与物化（Materialize）的生命周期，以及未物化状态下经 Query 与工厂重建业务聚合、首次 COW 并发。依据：[D-008](../reviews/2026-11-09-stage-3-architecture-review.md#d-008)、[术语](../domain/glossary.md)、[候选不变量](../domain/invariants.md)、[聚合分析](../domain/aggregate-model.md)。

## 派生 → 未物化 → 物化（生命周期）

已确认：派生创建 LightTrackerItem 并分配 LocalId、保留 CommonId、记录来源，不立即物化业务内容；未物化（VIRTUAL）是合法状态。物化由首次本地写入触发，其完成标志与内容指针候选。

```mermaid
stateDiagram-v2
    [*] --> Derived
    Derived --> Virtual : 派生创建 LightTrackerItem<br/>分配 LocalId 保留 CommonId<br/>记录 SourceLocalId/SourceRevision
    Virtual --> Materializing : 首次本地写入（COW）
    Materializing --> Materialized : 业务 BC 校验并提交完整内容
    Virtual --> Virtual : Resolve 定位来源与有效变体
    note right of Virtual : 未物化不代表变体不存在
```

## 未物化经 Query 与工厂重建（时序）

已确认：业务调用方不感知底层是否已物化，业务 BC 通过 CQRS Query 路径与工厂重建完整业务聚合。Working Set 只负责身份定位，不拥有或重建业务 BC 完整状态。

```mermaid
sequenceDiagram
    actor U as 业务调用方
    participant W as Working Set
    participant L as LightTrackerItem
    participant B as 业务 BC
    participant F as 业务工厂

    U->>W: Resolve（定位当前业务变体）
    W->>L: 读取 LocalId / CommonId / 来源
    L-->>W: 有效变体身份与来源
    W-->>U: 定位结果
    U->>B: CQRS Query（按身份获取业务对象）
    alt 已物化
        B-->>U: 完整业务聚合
    else 未物化（VIRTUAL）
        B->>F: 以派生来源重建完整聚合
        F-->>B: 完整业务聚合（内容来自 Source Cut）
        B-->>U: 完整业务聚合
    end
    Note over U,B: 调用方不感知物化状态；Working Set 不重建业务内容
```

## 首次 COW 并发反例（候选）

候选：同一 LightTrackerItem 首次物化并发时，最多一个业务对象物化获胜，身份保持不变；物化完成标志与有效内容指针、LocalId 是否等同业务聚合变体身份均待裁定。

```mermaid
sequenceDiagram
    actor U1 as 并发写 1
    actor U2 as 并发写 2
    participant L as LightTrackerItem
    participant B as 业务 BC

    U1->>B: 首次物化（LocalId 候选）
    U2->>B: 首次物化（同一 LocalId）
    B->>L: 检查物化状态与有效内容指针（CAS 候选）
    alt 一个提交获胜
        B-->>L: 记录物化完成（候选）
        L-->>B: 有效内容指针已建立
        B-->>U1: 成功，完整内容已提交
        B-->>U2: 冲突，重读后重新校验
    else 物化标志与指针协议未定
        Note over L,B: 双事实一致性协议候选；不得伪造成功或丢失已提交内容
    end
```

说明：此图不承诺跨 BC 事务或物化原子性；业务对象内部不变量仍由业务 BC 负责。
