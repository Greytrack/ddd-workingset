# MergeEntry 生命周期与提交到确认时序

**状态：Draft / Candidate（契约候选配套图；Q1～Q4 的语义已由 ADR-0005 及其 2026-11-11 补充裁定确认，图本身未纳入正式基线）**

- 目的：表达一条 MergeEntry 从 Diff、提交决策、执行、业务 BC 提交、LightTrackerItem 维护到 Checkpoint 确认的协作顺序，以及 MergeRound 的一次执行与终态。
- 适用范围：直属父子非共享 Tracker。证据分界见 [S7 实施裁定](../reviews/2026-11-12-s7-merge-implementation-decisions.md)。
- 依据：[Merge 执行行为契约 v1](../domain/merge-execution-contract-v1.md)、D-019（FACT-027、INV-061～063、SCN-055/056/059/060）、FACT-007/008、INV-006～010、POL-004、R08～R11、B06/B08/B09/B10。
- 不表达：Token 编码、幂等键实现、事件中间件、数据库表与事务隔离级别；不表达共享 Tracker（不参与 Merge）与兄弟/跨代合并（不准入）。

## 图例

- 实线调用＝同步请求；虚线返回＝结果／回执。
- `MergeRound`、`MergeCheckpoint`、`LightTrackerItem`、业务 BC 聚合之间均为**按身份引用**，图不表示同事务或聚合包含。
- `MergeEntry` 是 MergeRound 内的 Entity，图中置于 MergeRound 协作内部。

## 1. 提交到确认的协作时序（Happy Path + 关键分支）

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant D as Diff 能力
    participant R as MergeRound
    participant E as MergeEntry
    participant BC as 业务 BC 聚合
    participant L as LightTrackerItem
    participant C as MergeCheckpoint

    U->>D: compare 源范围 目标范围 方向
    D-->>U: TrackerItemDiff 与 ComparisonToken

    U->>R: startRound 源范围 目标范围 方向
    R-->>U: MergeRoundId 状态 Preparing

    U->>R: registerEntries token 与 ItemMergeDecision
    R->>E: 复核双侧内容证据，通过后登记条目
    R-->>U: 条目 ID 列表

    U->>R: apply
    alt DEFER
        R->>E: Deferred 未完成项
    else 执行决定
        R->>E: 复核身份、版次、存在性与可读性
        alt 执行准入证据已变化
            E-->>R: StaleEvidence
        else 执行准入通过
            opt 决定要求写入
                R->>BC: ACCEPT 取源最新值，或提交显式存在性决定
                BC-->>R: 整体校验及业务提交结果
            end
            alt 业务拒绝
                R->>E: Rejected 保留原因
            else 成功或 KEEP_TARGET
                opt 未物化目标发生写入
                    R->>L: 切换为 Local-backed
                end
                opt markAsMerged
                    R->>C: 确认处理事实
                    C-->>R: Checkpoint 结果
                end
                R->>E: 记录单项结果
            end
        end
    end

    R-->>U: 唯一终态 ApplyResult
    opt 重做未完成项
        U->>D: 重新 compare
        D-->>U: 新 ComparisonToken
        U->>R: 新建轮次，选择要重做的条目
    end
```

## 2. MergeEntry 状态机

```mermaid
stateDiagram-v2
    [*] --> Compared: compare 产生 token
    Compared --> Decided: registerEntries
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
    Deferred --> [*]: 未完成项 进入新轮次
```

状态定义与责任边界见 [T1 §2、§5、§8](../domain/merge-execution-contract-v1.md)，落地证据见 [S7 验证](../implementation/t5-s7-verification-result.md)。

### MergeRound 一次执行与终态

```mermaid
stateDiagram-v2
    [*] --> Preparing: startRound
    Preparing --> Running: apply 开始执行
    Running --> Completed: 本次执行结束
    Completed --> [*]: 终态 不可重开
    note right of Completed
      Succeeded 全部成功处理
      PartiallySucceeded 部分成功且存在未完成项
      Failed 无成功项且存在失败或未完成项
      未完成项须重新比较并新建 MergeRound
    end note
```
