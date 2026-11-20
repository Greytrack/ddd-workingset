# MergeEntry 生命周期与提交到确认时序

**状态：Draft / Candidate（T6-E 后的当前交互；Q1～Q4 语义见 ADR-0005，图本身未纳入正式基线）**

- 目的：表达一页 Merge 决策从 Compare、提交、异步执行、业务 BC 提交、LightTrackerItem 维护到 Checkpoint 确认的协作顺序，以及 MergeRound 的一次执行与终态。
- 适用范围：直属父子非共享 Tracker。证据分界见 [S7 实施裁定](../reviews/2026-11-12-s7-merge-implementation-decisions.md)。
- 依据：[Merge 执行契约 v1](../domain/merge-execution-contract-v1.md)、ADR-0005、D-019、T6-E、T6-B。
- 不表达：事件中间件、数据库表、事务隔离级别；不表达共享 Tracker 与兄弟/跨代合并。
- **2026-11-16 更新**：Compare 只读瞬时，不返回可回读的 `ComparisonToken`；提交入口改为整页决策（1～50 条，`requestId` 幂等），轮次由事件异步执行，客户端轮询。

## 图例

- 实线表示同步请求，虚线箭头表示回执，带开口箭头的虚线表示异步事件。
- MergeRound 拥有 MergeEntry；确认是条目的不可变状态。业务 BC、LightTrackerItem 与轮次分开提交。跨轮次查询直接读已提交的确认行。
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
    participant H as SK 物化事件处理
    participant L as LightTrackerItem
    participant C as 轮次仓储 / 旧确认表

    U->>D: compare 源范围 目标范围 方向 offset/limit
    D-->>U: TrackerItemDiff 页（瞬时证据，无 token）

    U->>R: submitDecisions requestId + 1..50 条 evidence/decision
    R->>R: 校验证据、同一 Tracker 对、活跃轮次
    R-->>U: MergeRoundId 状态 Preparing
    R-)R: 发布 MergeRoundSubmittedEvent（提交后异步）

    R->>R: apply 取得执行权，状态 Running
    loop 每个未完成条目
        R->>E: 复核身份、版次、存在性、可读性
        alt 证据已变化
            E-->>R: StaleEvidence
        else DEFER
            E-->>R: Deferred
        else 执行准入通过
            opt 决定要求写入
                R--)BC: MergeEntryApprovedEvent（决定、字段与证据）
                BC->>BC: 校验并独立提交业务结果与回执
                BC--)R: MergeEntryResolvedEvent
            end
            alt 业务拒绝
                R->>E: Rejected 保留原因
            else 成功或 KEEP_TARGET
                par 轮次提交条目结果
                    R->>E: 应用结果，按 markAsMerged 生成确认
                    R->>C: 同事务保存轮次状态和所需确认
                    C-->>R: 提交；写失败则整次轮次变更回滚
                and LTI 独立收敛（仅适用的业务写入）
                    R--)H: MergeAppliedEvent（业务结果已消费）
                    H->>L: 在 SK 独立事务中维护 LTI
                end
            end
        end
    end
    Note over R,H: 终态与本次确认同事务提交；LTI 独立推进
    U->>R: GET 轮次状态与结果
    R-->>U: 终态时返回唯一 ApplyResult

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
    Applied --> Confirmed: markAsMerged 为真，随轮次同事务持久化
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


## 3. 确认归属与只读查询

依据 [ADR-0006](../adr/0006-merge-confirmation-ownership.md) 与 [SCN-058](../scenarios/core-scenarios.md#scn-058)。组合箭头表示聚合内所有权；依赖箭头仅表示读写路径，不表示第二份确认数据。

```mermaid
classDiagram
    class MergeRound {
        聚合根
    }
    class MergeEntry {
        实体
    }
    class Confirmation {
        不可变值对象
    }
    class MergeRoundRepository
    class LegacyMergeIndex
    class MergeCheckpointQuery
    MergeRound "1" *-- "1..*" MergeEntry
    MergeEntry "1" *-- "0..1" Confirmation
    MergeRoundRepository ..> MergeRound : 保存
    MergeRoundRepository ..> LegacyMergeIndex : 同事务写确认行
    MergeCheckpointQuery ..> LegacyMergeIndex : 跨轮次只读
```
