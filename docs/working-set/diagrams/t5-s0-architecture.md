# T5 首切片架构与 COW 时序

**状态：Historical（S0 实施视图）。** D4/D15 已改变物化与 Merge 的事务边界，当前实现见 [D4 边界验证](../implementation/t5-d4-sk-boundary-verification.md) 与 [S7 验证](../implementation/t5-s7-verification-result.md)；本图保留为 S0 阶段证据。

## 组件依赖

实线表示调用或依赖，虚线表示实现接口。组合根装配两侧，Working Set 代码只依赖自己的端口。业务聚合仍实现领域接口 TrackerItem。

```mermaid
flowchart LR
    BOOT["Spring Boot 组合根"] --> SK["Working Set 应用层"]
    BOOT --> ALM["ALM 应用层"]
    SK --> RP["TrackerItemReadPort"]
    SK --> WP["RequirementWritePort"]
    SK --> LTI["LightTrackerItem JDBC 仓储 + Redis"]
    READ["RequirementAdapter"] -.-> RP
    WRITE["RequirementMaterializationAdapter"] -.-> WP
    READ --> DB["Requirement JDBC 仓储"]
    WRITE --> DB
    WRITE --> SNAP["JDBC 快照存储"]
    ALM --> DB
    ALM --> SNAP
```

## 首次 COW 与切换恢复

```mermaid
sequenceDiagram
    actor U as 调用方
    participant ALM as 来源 ALM
    participant M as Modulith
    participant H as Working Set COW
    participant W as ALM 写 Adapter
    participant L as LightTrackerItem 仓储

    U->>ALM: 普通修改 A → B
    ALM->>M: 同事务保存 B、修改前快照 A 与发布记录
    ALM-->>U: 来源提交成功
    M->>H: 提交后异步交付，按来源串行
    H->>L: 消费时查来源与 beforeRevision 匹配的未物化目标
    H->>W: 保存目标 A 与业务已保存凭据
    W-->>H: 独立事务提交
    H->>L: 独立事务切换三字段并记录 SWITCHED
    alt 切换成功
        L-->>H: 提交后更新 Redis
        H-->>M: 全部目标成功后标记发布完成
    else 切换失败
        H-->>M: 保留未完成记录与 SWITCH 失败证据
        Note over H,L: 恢复只补切换，保留已保存的目标内容
    end
```

切换前读取走来源选定版次，可暂时读到 B。切换后按目标自身身份和版次读取，首次物化内容为 A。扇出、失败记录与顺序缺口边界见 [S3 验证](../implementation/t5-s3-verification-result.md)和 [S4 验证](../implementation/t5-s4-verification-result.md)。
