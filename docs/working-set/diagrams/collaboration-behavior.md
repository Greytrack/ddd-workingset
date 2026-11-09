# 合并、共享与 COW 的候选行为

**状态：Draft / Candidate**

目的：验证提交、结果和确认的区分。依据 [责任与契约](../domain/shared-kernel-boundary.md)、[B01—B12](../scenarios/shared-kernel-boundary-scenarios.md)、[评审](../reviews/2026-11-09-stage-3-architecture-review.md)。假设 BC 支持提交去重和结果回读；不表示整轮事务、传输协议或最终接口。预校验不是提交许可；B09 已确认接受字段在执行时读取源最新值。目标同一接受字段在提交前被修改时，合法提交会覆盖该字段。若极端情况下某个已选源字段不可读，则本条 Requirement 保持未完成，批次继续下一条。图中展示源字段可读的执行路径，执行中竞态处理待定。

```mermaid
sequenceDiagram
    actor U as 审阅者
    participant W as Working Set
    participant B as 业务 BC 适配能力
    participant K as Checkpoint
    U->>W: diff 双侧固定证据与方向
    W->>B: 经授权读取并语义比较
    B-->>W: Difference 或不可读原因
    U->>W: decision 字段及存在性选择
    W->>W: 保存决定版本和审阅证据
    W->>B: 执行时读取已选字段的权威源最新值
    B-->>W: 返回本次源值及版本
    W->>B: validation / apply 同一操作身份与执行所读字段值
    B->>B: 基于当前目标重授权、整体校验、提交期并发控制
    alt 拒绝或冲突
        B-->>W: 拒绝回执 无业务变化
        W-->>U: 未完成 可修改决定或重新比较
    else 提交成功
        B->>B: 提交内容与可恢复结果
        B-->>W: result 实际版本与操作身份
        W->>W: 保存真实 Apply Result
        W->>K: 证据完整才 CAS 推进 checkpoint
        K-->>W: 已确认或更新冲突
    else 结果未知
        W->>B: 按原操作身份查询结果
        B-->>W: 原结果或仍在处理中
    end
```

KEEP_TARGET 可不执行内容提交；仍需证据校验后才能确认。DEFER 保留未处理状态。部分对象失败不改变其他对象的成功事实。

```mermaid
flowchart TB
    A["WS A Binding"] --> S["SHARED 权威 Tracker"]
    B["WS B Binding"] --> S
    S --> I["同一 Variant / 业务内容"]
    I --> P["BC 合法提交后 权威点查可见"]
    P -.-> Q["列表投影异步 SLA 待定"]
    S --> C["UNSHARED 派生 - 新 Tracker 和稳定目标 LocalId"]
    C --> V["VIRTUAL - 固定 Source Cut 保留历史"]
    V --> W["首次写入 由目标 BC 校验并 CAS"]
    W --> L["本地权威内容已提交"]
    L -.-> M["物化确认最终一致 不改身份或复活删除"]
    W --> F["冲突 / 历史不可用 / 拒绝"]
```

派生箭头表示业务动作而非复制同步。源后续变/删不改变目标固定切点；若保留能力不可用，必须阻止依赖该切点的成功发布或写入。异步标记滞后时本地提交与墓碑优先，不能读取旧来源覆盖目标。
