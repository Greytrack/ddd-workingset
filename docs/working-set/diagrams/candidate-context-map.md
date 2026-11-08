# Working Set 候选 Context Map

- **状态**：Draft / Candidate
- **设计阶段**：领域发现／战略设计候选
- **依据**：[候选 Context Map 文档](../domain/context-map.md)、[候选聚合分析](../domain/aggregate-model.md)、[阶段 2 审查](../reviews/2026-11-09-stage-2-domain-discovery-review.md)
- **用途**：展示 Working Set 能力内部的候选模型所有者与协作方向；企业 Shared Kernel 外框另见 [Shared Kernel Context Map](shared-kernel-context-map.md)。本图不表示微服务、Java 模块或已接受内部上下游关系。

```mermaid
flowchart LR
    Workspace["Workspace Composition<br/>工作范围与发布候选"]
    Lineage["Tracker Lineage<br/>共享与派生谱系候选"]
    Variant["Item Variant and Materialization<br/>身份、来源与 COW 候选"]
    Business["GateFlow / ALM / DQA / PLM BCs<br/>各自业务对象模型"]
    History["Temporal History and Snapshot<br/>历史与保留候选"]
    Merge["Compare and Merge<br/>差异、决定与结果候选"]
    Relation["Reference and Association<br/>关系与修复候选"]
    Classification["Class Hierarchy<br/>Shared Kernel 兄弟能力"]
    Privilege["Privilege<br/>Shared Kernel 兄弟能力"]
    Workflow["Workflow<br/>Shared Kernel 兄弟能力"]
    Provisioning["Provisioning Process<br/>长流程编排角色"]

    Workspace -->|"选择 Tracker identity"| Lineage
    Lineage -->|"提供派生谱系与 Source Cut"| Variant
    Variant -->|"提供变体身份与来源事实"| Business
    Business -->|"发布内容与生命周期事实"| Variant
    History -->|"固定内容和生命周期证据"| Workspace
    History -->|"固定来源证据"| Variant
    History -->|"双侧比较证据"| Merge
    Merge -->|"请求合法业务变化"| Business
    Workspace -->|"发布范围事实"| Relation
    Merge -->|"成功应用事实"| Relation
    Relation -->|"解析逻辑端点"| Variant
    Classification -->|"分类身份与规则"| Lineage
    Classification -->|"字段与状态约束"| Business
    Privilege -.->|"授权事实候选"| Workspace
    Privilege -.->|"授权事实候选"| Business
    Workflow -.->|"共享流程事实候选"| Business
    Provisioning -.->|"编排准备与发布"| Workspace
    Provisioning -.->|"编排派生与恢复"| Lineage
    Provisioning -.->|"编排身份与关系准备"| Variant
```

## 未确认边界

- Workspace Composition 与 Tracker Lineage 可以同部署，但是否同一限界上下文未确认。
- Item Variant 可能是领域上下文，也可能只是技术索引；取决于稳定身份和 Source Cut 是否具有业务意义。
- History/Snapshot 是支撑能力还是用户可管理业务能力未确认。
- Reference 与 Association 是否属于同一上下文仍待关系类型和生命周期证据。
- Provisioning 当前只表示应用流程角色，不自动成为领域聚合或微服务。
- Privilege、Workflow、Class Hierarchy 与 Working Set 的精确合同尚未确认；同属 Shared Kernel 不等于共享同一对象模型或事务。
