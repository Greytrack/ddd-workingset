# T5-S0 架构与首次 COW 时序

**状态：Draft / Candidate（T5-S0 实施配套图；不表达生产部署与持久化；图本身未纳入正式基线）**

- 目的：表达首切片的**最小目录/包依赖**、组件依赖方向、首次 COW 时序与读取路径判定。
- 适用范围：阶段 5 · T5-S0，仅 ALM Requirement 一个业务 BC；不含 Merge、删除、权限、版本治理。
- 依据：[T5-S0 最小纵向切片设计](../implementation/t5-s0-minimal-slice-design.md)（P1～P5、A1～A3）、T3（物化协议）、T4（Port/Adapter、两层接口并存、九分类）、D-008～D-019、INV-070/071。
- 不表达：数据库表、消息中间件、部署拓扑、生产级并发控制、Mermaid 渲染结果。

## 图例

- 实线＝依赖/调用；虚线＝实现/发布关系。
- SK 只依赖两个应用层 Port 的**接口**；ALM 的 Adapter 实现它们；SK 不引用 ALM 聚合类型。
- `LightTrackerItem` 的三字段（`materialized`、`sourceLocalId`、`sourceRevision`）只在聚合行为内一次更新。
- **禁止**：`sk.*` 依赖 `alm.*`（任何包、任何形式）；`bootstrap` 是唯一允许同时引用两者的位置。

## 1. 最小目录与包依赖

```mermaid
flowchart TB
    BOOT["bootstrap.configuration<br/>组合根（Spring @Configuration / @Bean）"]

    subgraph SK["sk.workingset"]
        SKD["domain<br/>LightTrackerItem / TrackerItem / 值对象<br/>纯 Java"]
        SKA["application<br/>WorkingSetApplicationService / CowEventHandler / port"]
        SKI["infrastructure<br/>内存仓库实现"]
    end

    subgraph ALM["alm.requirement"]
        ALMD["domain<br/>Requirement（实现 TrackerItem）"]
        ALMA["application<br/>RequirementApplicationService"]
        ALMI["infrastructure<br/>RequirementAdapter / 内存仓库 / 快照存储"]
    end

    BOOT --> SKI
    BOOT --> SKA
    BOOT --> ALMI
    SKA --> SKD
    SKI --> SKA
    ALMD -.->|"实现领域接口"| SKD
    ALMI -.->|"实现应用层 Port"| SKA
    ALMA --> ALMD
```

读图要点：`sk.*` 各层自下而上依赖自身；`alm.requirement.domain` 单向依赖 SK 公共契约（实现 `TrackerItem`）；`alm.requirement.infrastructure` 单向依赖 SK 应用层 Port（实现 Adapter）；`bootstrap` 同时依赖两侧完成装配。**图中不画 `sk → alm` 的箭头，因为该方向被禁止（AC-01）。**

## 2. 组件依赖方向

```mermaid
flowchart LR
    subgraph SKC["Working Set Shared Kernel"]
        APP["WorkingSetApplicationService<br/>derive / resolve / read"]
        COW["CowEventHandler<br/>消费来源变更事件"]
        LTI["LightTrackerItem 聚合<br/>materialized / sourceLocalId / sourceRevision"]
        TR["Tracker 聚合<br/>非共享派生 sourceId"]
        WS["WorkingSet 聚合<br/>单工作集 + 绑定"]
        RP["TrackerItemReadPort<br/>只读"]
        WP["RequirementWritePort<br/>写入协作"]
    end

    subgraph ALMC["ALM 业务 BC"]
        RAPP["RequirementApplicationService<br/>创建 / 普通修改 / 保存 / 发事件"]
        RAGG["Requirement 聚合<br/>实现 TrackerItem"]
        ADP["RequirementAdapter<br/>实现两个 Port"]
        RREPO["RequirementRepository<br/>内存"]
        SNAP["SnapshotStore<br/>修改前不可变快照"]
    end

    APP --> LTI
    APP --> TR
    APP --> WS
    APP --> RP
    COW --> LTI
    COW --> WP

    RP -.->|"实现"| ADP
    WP -.->|"实现"| ADP
    ADP --> RAGG
    RAGG --> RREPO
    RAPP --> RAGG
    RAPP --> SNAP
    RAPP -.->|"发布 RequirementChanged"| COW
```

## 3. 首次 COW 时序

```mermaid
sequenceDiagram
    autonumber
    actor U as 用户
    participant AAPP as ALM RequirementApplicationService
    participant SNAP as SnapshotStore
    participant RAGG as Requirement 聚合
    participant EV as 事件总线（同步顺序）
    participant COW as SK CowEventHandler
    participant LTI as LightTrackerItem
    participant WP as RequirementWritePort（ALM Adapter）
    participant Q as Query 服务

    U->>AAPP: 修改 title A→B（普通修改）
    AAPP->>SNAP: 读取修改前完整内容，保存不可变快照
    AAPP->>RAGG: 保存修改（lock_version 自动 +1，Revision 与发布 version 不变）
    AAPP->>EV: 发布 RequirementChanged（snapshotRef + revision）
    EV->>COW: 顺序派发
    COW->>LTI: 查询 materialized=false 且 sourceLocalId/sourceRevision 匹配
    LTI-->>COW: 目标变体（扇出）
    COW->>WP: materialize(targetLocalId, snapshotRef, revision)
    WP->>RAGG: 以快照构建聚合 → 整体校验（含 lock_version 并发）→ 保存（幂等键）
    alt 保存失败
        RAGG-->>WP: 拒绝
        WP-->>COW: 失败
        COW->>COW: 不切换状态，重试（幂等）
    else 保存成功
        RAGG-->>WP: 成功
        WP-->>COW: 成功
        COW->>LTI: switchToLocalBacked(revision)（单聚合事务，三字段同步）
        alt 切换失败
            COW->>COW: 不回滚业务内容，重试只补切换
        else 切换成功
            COW->>COW: 记录物化完成
        end
        Q->>LTI: resolve（materialized=true）
        Q->>RAGG: readAt(revision)
        RAGG-->>Q: 本地内容 B
    end
```

## 4. 读取路径判定

```mermaid
flowchart TD
    A["read 请求（工作集 + Item）"] --> B{"LightTrackerItem.materialized?"}
    B -->|"false"| C["沿 sourceLocalId 直接定位实际内容拥有者"]
    C --> D["readAt(sourceRevision) → 来源内容 A"]
    B -->|"true"| E["readAt(revision) → 本地内容 B"]
    D --> F["返回内容 + 实际读取的 revision"]
    E --> F
```

## 5. 五个检查点

1. **依赖方向（AC-01）**：`sk.*` 不依赖 `alm.*`；SK 只引用 Port 接口；`bootstrap` 唯一同时引用两侧。
2. **身份稳定（AC-02）**：派生预分配 `LocalId`，首次 COW 复用，COW 前后可用同一 `LocalId` 定位变体。
3. **三字段同步**：`switchToLocalBacked` 是唯一写入入口；不一致即元数据缺陷（T3/INV-069）。
4. **失败分离**：保存失败不切换；切换失败不回滚业务内容、只补切换（A3/T3 §7）。
5. **内容固定范围**：首切片普通字段；附件、关系固定派生时状态，不被较新事件覆盖（T3 §2.1/SCN-072）。
