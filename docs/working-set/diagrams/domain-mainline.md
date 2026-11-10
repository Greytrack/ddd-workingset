# Working Set 领域主线

**状态：Draft / Candidate（首版基线候选配套图；未发布为正式基线）**

目的：表达 Working Set 领域从创建到合并的业务主线。范围：主干流程，不表达数据库、部署或具体 API。依据：[首版基线候选](../domain/model-baseline-candidate-v1.md)、D-001～D-019。领域关系见 [契约与聚合图](trackeritem-contract.md)，生命周期/COW 时序见 [LightTrackerItem 生命周期](lighttrackeritem-lifecycle.md)。

## 领域主线

```mermaid
flowchart TD
    A["Working Set 创建<br/>选择 Tracker 形成工作范围"] --> B["扩展<br/>纳入新 Tracker/Item；绑定后禁止移除"]
    B --> C["Tracker 派生<br/>非共享派生新身份<br/>parentId 组织树 / sourceId 派生链"]
    C --> D["LightTrackerItem 派生<br/>选择 Snapshot/Head 时间基准<br/>固定 Revision，SourceLocalId 直接定位"]
    D --> E["Query<br/>Resolve 定位 + BC 工厂重建<br/>未物化读来源 Revision 当前内容"]
    E --> F["COW<br/>来源修改→不可变快照→事件<br/>Handler 消费时确定目标→物化→切换自身"]
    F --> G["Revision 升级<br/>显式升级触发 COW"]
    G --> H["Diff<br/>直接父子双向比较"]
    H --> I["Merge<br/>ACCEPT/KEEP_TARGET/DEFER<br/>目标 BC 校验提交"]
```

说明：主线表达业务动作顺序，不表示单事务或强一致。COW 为异步最终一致；未物化期间允许暂时读到来源修改后内容。业务 BC 拥有完整业务内容与提交权。三类核心聚合（Working Set/Tracker/LightTrackerItem）按 ID reference 关联，不组合、不跨 BC 事务。
