# Working Set 候选领域概念关系图

- **状态**：Draft / Candidate
- **设计阶段**：领域发现
- **依据**：[候选统一领域语言](../domain/glossary.md)、[事实与假设](../domain/facts.md)、[开放问题](../domain/open-questions.md)
- **用途**：暴露概念关系、身份层次和当前歧义，不表达最终聚合边界、事务边界或部署结构。

```mermaid
flowchart LR
    WS["Working Set<br/>候选工作视图"]
    Binding["Tracker Binding<br/>选择事实"]
    Tracker["Tracker<br/>独立聚合 D-001"]
    Shared["Shared Tracker<br/>同一权威对象候选"]
    Unshared["Non-shared Tracker<br/>派生产生新身份 D-002"]
    Family["Item Family<br/>逻辑对象家族"]
    Variant["Item Variant<br/>具体变体候选"]
    Item["Business Item<br/>具体业务内容"]
    Cut["Source Cut<br/>固定历史依据"]
    History["History or Snapshot<br/>可回读证据候选"]
    Compare["Difference<br/>冻结证据上的差异"]
    Decision["Merge Decision<br/>ACCEPT / KEEP_TARGET / DEFER"]
    Reference["Reference or Association<br/>逻辑关系"]

    WS -->|"包含选择"| Binding
    Binding -->|"解析到"| Tracker
    Tracker -->|"sharing mode 候选"| Shared
    Tracker -->|"sharing mode 候选"| Unshared
    Shared -->|"复用同一变体"| Variant
    Unshared -->|"派生新变体"| Variant
    Family -->|"包含多个变体"| Variant
    Variant -->|"承载或定位"| Item
    Variant -->|"初始内容依据"| Cut
    Cut -->|"必须可回读"| History
    Variant -->|"双侧对齐"| Compare
    Compare -->|"由用户或政策裁决"| Decision
    Decision -->|"通过目标业务能力应用"| Item
    Reference -->|"引用逻辑家族或具体变体<br/>待确认"| Family
    Reference -.->|"物理解析可暂时缺失"| Variant
```

## 需要图表帮助裁定的问题

- Working Set 是工作视图、发布范围还是业务分支；
- Tracker 独立聚合与非共享派生新身份已确认；D-003 已明确双维指向与逐级派生；D-004 已允许父未选时成为根；关系修改、同源多派生、父也选时的树继承及 Item 归属待裁定；
- Item Family、Item Variant、Item Revision 的身份边界；
- Reference 指向逻辑家族还是具体变体；
- Source Cut、业务 Snapshot、Merge Base 与仓库 Design Baseline 的不同含义。

除 D-001 的 Tracker 独立聚合结论外，不从本图推导其他聚合或数据库结构。

当前关系视图见 [D-003 双维关系图](tracker-dual-relations.md)；D-002 图保留为已被替代的历史视图。不把 Tracker 追溯关系等同 Working Set 父子关系。
