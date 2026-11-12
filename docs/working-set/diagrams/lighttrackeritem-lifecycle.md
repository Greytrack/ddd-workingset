# LightTrackerItem 派生与多级有效来源

**状态：Draft（D-008、D-012～D-015 配套图）**

本页展示派生身份、有效来源及多级依赖。双态切换和消费时匹配见 [T3 物化图](lighttrackeritem-materialization.md)，单实例乱序和失败恢复证据见 [S4 验证](../implementation/t5-s4-verification-result.md)。

派生分配 LocalId、继承 CommonId 并记录实际内容来源。读路径和写入责任见 [T4](../domain/cross-bc-contract-v1.md)。

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

## 派生主流程（D-015 时间基准）

已确认：派生不复制完整业务数据，只生成 LightTrackerItem 建立身份与有效来源引用；时间基准（命名 Snapshot 或 Head）只用于选 Revision，普通字段仅选定版次，附件与关系的派生时固定范围见 [T3 §2.1](../domain/lighttrackeritem-materialization-contract-v1.md)。

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

## 历史追溯

逐级内容解析由 D-013 排除，派生完成时间门槛由 D-017 的消费时匹配替代。原时序、备选协议与反例见[阶段 3 评审](../reviews/2026-11-09-stage-3-architecture-review.md)及 Git 版本 `404a356:docs/working-set/diagrams/lighttrackeritem-lifecycle.md`。仍待裁定的并发与历史回读边界见[开放问题](../domain/open-questions.md)。
