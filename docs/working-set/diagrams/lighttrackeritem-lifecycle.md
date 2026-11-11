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

## Source-backed 与 Local-backed 有效内容切换（D-009）

已确认：物化前变体为 Source-backed（依赖来源解析内容），物化后切换为 Local-backed（本地物化内容成为有效来源）；Derivation Origin 保留用于追溯谱系，不被有效内容切换覆盖。

```mermaid
flowchart LR
    SB["Source-backed<br/>未物化，有效来源=来源"] -->|"来源变更事件 + COW 物化"| LB["Local-backed<br/>已物化，有效来源=自身 LocalId"]
    LB -.->|"Derivation Origin 保留，追溯 R0"| SB
```

## 来源变更触发 COW 物化（D-009 事件时序）

已确认：来源普通修改不升级 Revision；修改前先 Query 获得修改前完整业务事实，修改保存后发来源变更事件触发 COW 物化；物化 Handler 据修改前事实形成派生变体自身内容并保存，成功后发物化完成事件；LightTrackerItem 索引 Handler 标记已物化，有效来源切换到自身。事件与事务的准确可见性协议未裁定。

```mermaid
sequenceDiagram
    actor U as 来源编辑者
    participant SBC as 来源业务 BC
    participant EV as 事件总线
    participant H as COW 物化 Handler
    participant DBC as 派生业务 BC
    participant L as LightTrackerItem

    U->>SBC: Query 修改前完整业务事实 A
    SBC-->>U: A（修改前事实）
    U->>SBC: 领域修改并仓储保存
    SBC->>SBC: 保存 B（普通修改，Revision 不升级）
    SBC->>EV: 来源变更事件
    EV->>H: 触发受影响派生变体 COW
    H->>H: 据修改前事实 A 形成派生变体完整业务聚合
    H->>DBC: 保存派生变体本地内容
    DBC-->>H: 物化成功
    H->>EV: 物化完成事件
    EV->>L: 索引 Handler
    L->>L: 标记已物化，有效来源切换到自身 LocalId
```

注意：修改前事实 A 的可靠保存/稳定引用（反例二）、物化完成前的查询可见性（反例一）、连续修改的多事件乱序与幂等（反例四）均候选。图不承诺跨 BC 事务。

## D-010 收敛：不可变快照与查询可见性

D-010 确认：修改前事实 A 作为事件关联的不可变内容快照与来源变更事件一起传递（反例二关闭）；来源修改已保存、COW 尚未完成期间，允许派生工作集暂时读取到修改后内容 B，物化完成后以修改前事实形成独立内容（反例一关闭）。连续修改的多事件乱序与「首次快照」业务选择规则仍候选（反例四）。

LightTrackerItem 主状态收敛为 Source-backed 与 Local-backed 两个，Materializing 作为技术运行状态，不增加第三种业务状态。

SourceLocalId 双语义：历史派生来源（Derivation Origin，追溯谱系）与当前有效内容来源（Effective Content Source，物化后指向自身），字段是否拆分待设计。

## 首次快照优先与乱序处理（D-011 事件时序）

已确认：对未物化派生 TrackerItem，来源首次相关修改的修改前快照确定最终物化内容；快照选择按来源修改实际先后顺序而非事件到达顺序；E2 先于 E1 到达仍采用第一次修改前快照 A。Source Change Order / Derivation Baseline / Materialization Claim 为候选协议，不自动建聚合/表。

```mermaid
sequenceDiagram
    participant SBC as 来源业务 BC
    participant EV as 事件总线
    participant H as COW 物化 Handler
    participant L as LightTrackerItem

    Note over SBC: R0 初始 A：A→B（E1 快照 A），B→C（E2 快照 B）
    SBC->>EV: E1（快照 A）
    SBC->>EV: E2（快照 B）
    EV->>H: E2 先于 E1 到达（乱序）
    H->>L: 检查 Derivation Baseline / Materialization Claim（候选）
    Note over H,L: E2 不是首次相关修改，不物化为 B
    EV->>H: E1 后到
    H->>L: 首次相关修改 = E1，快照 A
    H->>H: 物化 R1 为 A（首次快照优先）
    L->>L: 标记 Local-backed，有效来源=自身 LocalId
    Note over L: 后续 E2 或其他来源事件不覆盖已物化 A
```

注意：Source Change Order 如何表达（复用乐观锁 `lock_version` 还是独立序列）、Derivation Baseline 判定、Materialization Claim 证据保留均候选，不提前用数据库锁或消息队列偏移量替代业务裁定。

## SourceLocalId / SourceRevision 职责分离（D-012 字段对比与多级派生）

已确认：SourceLocalId 表示当前有效内容来源的 LocalId，SourceRevision 表示当前有效内容来源的业务 Revision；COW 前指向来源，COW 后指向自身。

```mermaid
flowchart LR
    subgraph Before["COW 前（Source-backed）"]
        B1["R1 未物化<br/>SourceLocalId = R0<br/>SourceRevision = 3<br/>经来源解析内容"]
    end
    subgraph After["COW 后（Local-backed）"]
        A1["R1 已物化<br/>SourceLocalId = R1<br/>SourceRevision = R1.Revision<br/>有效内容定位 = 自身"]
    end
    Before -->|"COW 物化（LocalId/CommonId 不变）"| After
```

多级派生（R0→R1→R2）：D-012 原按逐级解析绘制，现由 D-013 修正为直接有效来源定位（见下节）。历史派生关系不要求由 LightTrackerItem 保存。

```mermaid
flowchart LR
    R0["R0（WS0）<br/>CommonId=C100, LocalId=R0"]
    R1["R1（WS1）<br/>SourceLocalId=R0"]
    R2["R2（WS2）<br/>SourceLocalId=R1（D-012 逐级假设，已被 D-013 排除）"]
    R1 -->|"解析来源"| R0
    R2 -->|"解析来源（逐级，已排除）"| R1
```

## 直接有效来源定位与依赖者扇出（D-013）

已确认：SourceLocalId 直接指向实际拥有完整业务内容的变体，不逐级递归解析未物化中间变体。

场景 A（仅 R0 物化，R1/R2 未物化）：R1.SourceLocalId=R0、R2.SourceLocalId=R0，R0 修改触发 R1、R2 都 COW 物化。

```mermaid
flowchart LR
    R0["R0（WS0）已物化"]
    R1["R1（WS1）<br/>SourceLocalId=R0"]
    R2["R2（WS2）<br/>SourceLocalId=R0"]
    R0 -->|"有效来源定位"| R1
    R0 -->|"有效来源定位（直接，跳过 R1）"| R2
```

场景 B（R0/R1 已物化，R2 未物化）：R2.SourceLocalId=R1；R0 修改不物化 R2，R1 修改才物化 R2。

```mermaid
flowchart LR
    R0["R0（WS0）已物化"]
    R1["R1（WS1）已物化"]
    R2["R2（WS2）<br/>SourceLocalId=R1"]
    R0 -->|"派生"| R1
    R1 -->|"有效来源定位"| R2
```

COW 扇出时序：同一实际来源修改，触发所有依赖该来源的未物化变体物化（不限派生深度）。

```mermaid
sequenceDiagram
    participant SBC as 来源业务 BC
    participant EV as 事件总线
    participant H as COW 物化 Handler
    participant B1 as 业务 BC（R1）
    participant B2 as 业务 BC（R2）

    SBC->>SBC: 获取修改前不可变快照 A
    SBC->>SBC: 修改并保存
    SBC->>EV: 来源变更事件（快照 A）
    EV->>H: 依据 SourceLocalId 查找全部受影响变体
    H->>H: 发现 R1、R2（都 SourceLocalId=R0）
    H->>B1: 物化 R1（快照 A）
    H->>B2: 物化 R2（快照 A）
    B1-->>H: R1 物化完成
    B2-->>H: R2 物化完成
    H->>H: 通知 LightTrackerItem 更新有效来源（SourceLocalId=LocalId）
    Note over H: 不要求所有目标在同一全局事务完成
```

## 派生层级与有效来源依赖的区分（D-014）

已确认：R1 物化后，已有下游 R2 的 SourceLocalId 不自动重定向；派生层级（虚线）与有效来源依赖（实线）是两个不同维度。

```mermaid
flowchart LR
    R0["R0（WS0）已物化"]
    R1["R1（WS1）已物化<br/>SourceLocalId=R1"]
    R2["R2（WS2）未物化<br/>SourceLocalId=R0（不重定向）"]
    R0 -.->|"派生层级"| R1
    R1 -.->|"派生层级"| R2
    R0 -->|"有效来源依赖（R2 固定指向 R0）"| R2
```

R1 物化前后 R2 来源保持不变：

```mermaid
flowchart LR
    subgraph Before["R1 物化前"]
        B1["R1 未物化<br/>SourceLocalId=R0"]
        B2["R2 未物化<br/>SourceLocalId=R0"]
    end
    subgraph After["R1 物化后"]
        A1["R1 已物化<br/>SourceLocalId=R1"]
        A2["R2 未物化<br/>SourceLocalId=R0（不变）"]
    end
    Before -->|"R1 独立 COW"| After
```

说明：R2 的 SourceLocalId 固定于派生时确定的实际内容来源，直至 R2 自身 COW；不随 R1 物化重定向。派生层级仅表达 Working Set 派生关系，不决定有效内容依赖。

## 派生主流程（D-015 时间基准）

已确认：派生不复制完整业务数据，只生成 LightTrackerItem 建立身份与有效来源引用；时间基准（命名 Snapshot 或 Head）只用于选 Revision，不固定内容。

```mermaid
flowchart TD
    Start(["用户发起派生"]) --> Choose{"选择时间基准"}
    Choose -->|"命名 Snapshot"| Snap["固定 T = Snapshot 时间戳"]
    Choose -->|"Head"| Head["固定 T = 派生请求时刻"]
    Snap --> Track["创建或关联 Tracker（按选择范围）"]
    Head --> Track
    Track --> Rev["按 T 解析来源适用的最大业务 Revision"]
    Rev --> Loc["定位实际拥有内容的来源 LocalId（D-013）"]
    Loc --> Create["创建 LightTrackerItem<br/>保存 LocalId/CommonId/SourceLocalId/SourceRevision"]
    Create --> Done["完成派生，不复制完整业务聚合"]
```

说明：Derivation Time Basis（T）不等于 BeforeSnapshot；命名 Snapshot 不是完整业务内容快照。同一 Revision 内时间基准与具体内容状态的关系保持 Open（SCN-044）。

## 派生完成边界与 COW 保护（D-016 时序对比）

已确认：完整派生完成（Derivation Completion）是新变体开始受 COW 保护的边界；派生完成前来源修改不触发 COW。

场景 A：派生完成 → 来源修改 → 触发 COW。

```mermaid
sequenceDiagram
    participant W as Working Set
    participant R1 as LightTrackerItem R1
    participant SBC as 来源业务 BC
    participant H as COW Handler

    W->>R1: 完整派生完成（Derivation Completion）
    SBC->>SBC: R0 修改 B→C，形成修改前快照 B
    SBC->>H: 来源变更事件（快照 B）
    H->>R1: 检查派生已完成 → 触发 COW
    H->>H: R1 据快照 B 物化
```

场景 B：来源修改 → 派生完成 → 不因该修改触发 COW。

```mermaid
sequenceDiagram
    participant SBC as 来源业务 BC
    participant W as Working Set
    participant R1 as LightTrackerItem R1

    SBC->>SBC: R0 修改 B→C（事件携带快照 B）
    Note over R1: R1 派生尚未完成，不触发 COW
    W->>R1: 完整派生完成（Derivation Completion）
    Note over R1: R1 仍 Source-backed，Query 得到 R0 Revision 3 当前内容 C
```

场景 C：来源修改已开始 → 派生完成 → 来源修改成功提交（竞态，待裁定）。

```mermaid
sequenceDiagram
    participant SBC as 来源业务 BC
    participant W as Working Set
    participant R1 as LightTrackerItem R1

    SBC->>SBC: 来源修改开始（B→C）
    W->>R1: R1 派生完成（Derivation Completion）
    SBC->>SBC: 来源修改成功提交
    Note over SBC,R1: 待裁定竞态：以修改开始还是提交时间判断 R1 是否受 COW 保护
```

说明：如何表达和判定 Derivation Completion 与 Source Mutation 的实际先后属候选协作协议，不直接确定新数据库字段、全局锁或新聚合。（此时间边界判定现由 D-017 修订，见下节）

## COW 目标集合消费时确定（D-017 主流程时序）

已确认：COW 目标集合在 Handler 消费事件时确定，不在修改开始时固定；查询当前 SourceLocalId/SourceRevision 匹配且未物化的 LightTrackerItem，命中的都物化。前文 D-016 场景 A/B/C 时序图的「派生完成时间边界」由本裁定取代。

```mermaid
sequenceDiagram
    participant SBC as 来源业务 BC
    participant EV as 事件总线
    participant H as COW Handler
    participant L as LightTrackerItem
    participant DBC as 目标业务 BC

    SBC->>SBC: 修改前获取完整内容快照
    SBC->>SBC: 执行来源业务聚合修改并保存
    SBC->>EV: 发布携带不可变快照的变更事件
    EV->>H: 异步消费事件
    H->>L: 查询 SourceLocalId/SourceRevision 匹配且未物化的 LightTrackerItem
    L-->>H: 命中的目标集合（消费时确定）
    H->>DBC: 对各目标执行 COW（据快照物化）
    DBC-->>H: 保存完整业务聚合
    H->>EV: 物化完成事件
    EV->>L: 更新有效内容来源（SourceLocalId=LocalId、SourceRevision=Revision）
```

说明：不追溯比较修改开始时间与派生完成时间；D-011 首次快照优先的乱序/幂等实现细节留待实施评审。
