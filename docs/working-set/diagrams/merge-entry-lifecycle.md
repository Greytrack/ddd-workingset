# MergeEntry 生命周期与提交到确认时序

**状态：Draft / Candidate（T6-E 后的当前交互；Q1～Q4 语义见 ADR-0005，确认归属见 ADR-0006；图本身未纳入正式基线）**

- 目的：表达一页 Merge 决策从 Compare、提交、异步执行、业务 BC 提交、LightTrackerItem 维护到合并确认的协作顺序，以及
  MergeRound 的一次执行与终态。
- 适用范围：直属父子非共享 Tracker。登记与分发的证据边界见 [ADR-0009](../adr/0009-merge-dispatch-frozen-evidence.md)。
- 依据：[Merge 执行契约 v1](../domain/merge-execution-contract-v1.md)、ADR-0005、ADR-0006、D-019、T6-E、T6-B。
- 不表达：事件中间件、数据库表、事务隔离级别；不表达共享 Tracker 与兄弟/跨代合并。
- **2026-11-16 更新**：Compare 只读瞬时，不返回可回读的 `ComparisonToken`；提交入口改为整页决策（1～50 条，`requestId`
  幂等），轮次由事件异步执行，客户端轮询。

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
        R->>E: 读取已登记决定及冻结值
        opt 决定要求写入
            R--)BC: MergeEntryApprovedEvent（分类、冻结值、目标期望锁）
            BC->>BC: 校验目标并独立提交结果与回执
            BC--)R: MergeEntryResolvedEvent
        end
        alt 冲突或业务拒绝
            R->>E: 保存失败结果与原因
        else 成功或 IGNORED
            par 轮次提交条目结果
                R->>E: 应用结果，按差异类型及适用的确认规则生成确认
                R->>C: 同事务保存轮次状态和所需确认
                C-->>R: 提交；写失败则整次轮次变更回滚
            and LTI 独立收敛（仅适用的业务写入）
                R--)H: MergeAppliedEvent（业务结果已消费）
                H->>L: 在 SK 独立事务中维护 LTI
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
    Decided --> Applying: 按目标分类分发冻结值
    Decided --> Applied: 忽略操作
    Applying --> Applied: BC 接受
    Applying --> Rejected: BC 拒绝
    Applying --> Conflict: 目标并发修改
    Applied --> Confirmed: 符合确认规则，随轮次同事务持久化
    Applied --> [*]: 无需确认
    Confirmed --> [*]
    Rejected --> [*]: 重做须新建 MergeRound
    Conflict --> [*]: 重新 compare 后新建轮次
```

状态定义与责任边界见 [T1 §5、§8](../domain/merge-execution-contract-v1.md)
，落地证据见 [T6-E 验收](../implementation/t6-e-merge-round-verification.md)。

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

依据 [ADR-0006](../adr/0006-merge-confirmation-ownership.md) 与 [SCN-058](../scenarios/core-scenarios.md)
。组合箭头表示聚合内所有权；依赖箭头仅表示读写路径，不表示第二份确认数据。

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
