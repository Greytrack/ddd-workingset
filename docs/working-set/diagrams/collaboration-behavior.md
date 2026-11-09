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

KEEP_TARGET 已确认可通过 markAsMerged 记录处理决定而保留目标内容，决定写入 MergeCheckpoint。双侧业务版次未变时下次比对不再显示该差异，普通编辑只增加乐观锁版本也继续隐藏。该无内容变更路径直接记录决定与确认，图中业务内容提交分支适用于存在数据变更的情况。DEFER 保留未处理状态。部分对象失败不改变其他对象的成功事实。

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

B08 已确认批次允许部分成功。重试只进入未完成项的执行流程，已成功项保留结果并跳过，即使其源端再次变化。结果未知项先查回原提交结果的恢复协议仍为候选。

## 用户主动比对与 Checkpoint 适用性

以下分支表达 SCN-009 已确认的业务规则。图表文件的整体候选状态仍用于其余执行协议，Checkpoint 存储结构待设计。

```mermaid
flowchart TD
    Start["用户主动发起比对"] --> Match{"存在适用的已处理 Checkpoint<br/>且双侧业务版次均相同"}
    Match -->|"是"| Hide["抑制对应已处理差异<br/>普通编辑增加乐观锁版本也不改变此结果"]
    Match -->|"任一侧业务版次已升级"| Expired["旧 Checkpoint 对新版本组合过时"]
    Expired --> Compare["重新比较所有适用字段<br/>包含曾合并与未合并的字段"]
    Match -->|"无适用 Checkpoint"| Compare
    Compare --> Result["显示实际仍存在的差异"]
```

## GateFlow 草稿内容整体切换

**业务结果已由用户确认，内部协议为 Candidate。** 目的为表达 V3 的可见内容与准备内容的区别。依据 [GateFlow 场景](../scenarios/shared-kernel-boundary-scenarios.md#gateflow-草稿版次内容替换) 和 [职责边界](../domain/shared-kernel-boundary.md#gateflow-process-与版次用户已确认)。此图不规定数据库事务或聚合组成。准备期间已确认允许编辑，切换时可覆盖期间编辑。内容范围已固定为过程元素业务对象、裁剪问题、裁剪答案、合规映射四类，不纳入共享 Tracker。内部业务细节属于 GateFlow 专项。切换瞬间的并发提交、重试和取消协议待设计。

```mermaid
flowchart TD
    Start["GateFlow 请求以 V1 内容替换 V3 草稿"] --> Prepare["准备完整替换内容<br/>V3 使用当前完整内容并允许编辑"]
    Prepare --> Ready{"是否全部准备成功"}
    Ready -->|"否或准备失败"| Old["V3 保留当前内容及期间已保存编辑"]
    Ready -->|"是"| Switch["整体切换 V3 当前内容<br/>期间编辑可被覆盖"]
    Switch --> New["V3 内容完整对应 V1<br/>V3 独有流程元素退出当前内容"]
    New --> Identity["V3 版次身份和工作集 ID 保留<br/>父级仍为 V2"]
```
