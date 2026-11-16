# MergeEntry 生命周期与提交到确认时序

**状态：Draft / Candidate（T6-E 后的当前交互；Q1～Q4 语义见 ADR-0005，图本身未纳入正式基线）**

- 目的：表达一页 Merge 决策从 Compare、提交、异步执行、业务 BC 提交、LightTrackerItem 维护到 Checkpoint 确认的协作顺序，以及 MergeRound 的一次执行与终态。
- 适用范围：直属父子非共享 Tracker。证据分界见 [S7 实施裁定](../reviews/2026-11-12-s7-merge-implementation-decisions.md)。
- 依据：[Merge 执行契约 v1](../domain/merge-execution-contract-v1.md)、ADR-0005、D-019、T6-E、T6-B。
- 不表达：事件中间件、数据库表、事务隔离级别；不表达共享 Tracker 与兄弟/跨代合并。
- **2026-11-16 更新**：Compare 只读瞬时，不返回可回读的 `ComparisonToken`；提交入口改为整页决策（1～50 条，`requestId` 幂等），轮次由事件异步执行，客户端轮询。

## 图例

- 实线调用＝同步请求；虚线返回＝结果/回执。
- `MergeRound`、`MergeCheckpoint`、`LightTrackerItem`、业务 BC 聚合之间均按身份引用，图不表示同事务或聚合包含。
- `MergeEntry` 是 MergeRound 内的 Entity，图中置于 MergeRound 协作内部。

## 1. 提交到确认的协作时序

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant D as Compare 能力
    participant R as MergeRound
    participant E as MergeEntry
    participant BC as 业务 BC 聚合
    participant L as LightTrackerItem
    participant C as MergeCheckpoint

    U->>D: compare 源范围 目标范围 方向 offset/limit
    D-->>U: TrackerItemDiff 页（瞬时证据，无 token）

    U->>R: submitDecisions requestId + 1..50 条 evidence/decision
    R->>R: 校验证据、同一 Tracker 对、活跃轮次
    R-->>U: MergeRoundId 状态 Preparing
    R-)R: 发布 MergeRoundSubmitted（提交后异步）

    R->>R: apply 取得执行权，状态 Running
    loop 每个未完成条目
        R->>E: 复核身份、版次、存在性、可读性
        alt 证据已变化
            E-->>R: StaleEvidence
        else DEFER
            E-->>R: Deferred
        else 执行准入通过
            opt 决定要求写入
                R->>BC: ACCEPT 取源最新值，或提交显式存在性决定
                BC-->>R: 整体校验及业务提交结果
            end
            alt 业务拒绝
                R->>E: Rejected 保留原因
            else 成功或 KEEP_TARGET
                opt 未物化目标发生写入
                    R->>L: 切换为 Local-backed（独立事务/事件）
                end
                opt markAsMerged 且 Applied
                    R->>C: 确认处理事实
                    C-->>R: Checkpoint 结果
                end
                R->>E: 记录单项结果
            end
        end
    end
    R-->>U: 唯一终态 ApplyResult（GET 轮询）

    opt 重做未完成项
        U->>D: 重新 compare
        U->>R: 新建轮次（新 requestId）
    end
```

## 2. MergeEntry 状态机

```mermaid
stateDiagram-v2
    [*] --> Decided: 一页决策提交并登记
    Decided --> StaleEvidence: 执行准入证据变化
    Decided --> Deferred: 用户 DEFER
    Decided --> Applying: 执行准入通过
    Applying --> Applied: BC 接受 或 KEEP_TARGET
    Applying --> Rejected: BC 拒绝
    Applied --> Confirmed: markAsMerged 为真 且 Checkpoint 接受
    Applied --> [*]: markAsMerged 为假
    Confirmed --> [*]
    Rejected --> [*]: 重做须新建 MergeRound
    StaleEvidence --> [*]: 重新 compare 后新建轮次
    Deferred --> [*]: 未完成项进入新轮次
```

状态定义与责任边界见 [T1 §5、§8](../domain/merge-execution-contract-v1.md)，落地证据见 [T6-E 验收](../implementation/t6-e-merge-round-verification.md)。

### MergeRound 一次执行与终态

```mermaid
stateDiagram-v2
    [*] --> Preparing: submitDecisions
    Preparing --> Running: apply 开始执行
    Running --> Succeeded: 全部条目成功处理
    Running --> PartiallySucceeded: 至少一项成功且存在未完成/失败项
    Running --> Failed: 无成功项且存在失败/未完成项
    Succeeded --> [*]
    PartiallySucceeded --> [*]
    Failed --> [*]
    note right of PartiallySucceeded
      未完成项须重新比较并新建 MergeRound
      终态不可重开、不可再登记
    end note
```

当前实现没有独立 `Completed` 阶段：本轮全部条目完成后直接进入三种终态之一。
