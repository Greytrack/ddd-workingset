# 工作集创建任务状态与步骤

**状态：Reviewed**。描述任务与执行步骤，依据 [T7 设计](../domain/working-set-creation-lro-design.md) 和 [T7-A 评审](../reviews/2026-11-13-t7-a-creation-lro-review.md)。箭头表示推进顺序，任务按 ID 关联既有聚合。终态前的范围访问返回 `Preparing`。

```mermaid
stateDiagram-v2
    [*] --> Pending
    Pending --> Running: 异步执行
    state Running {
        [*] --> CreateWorkingSet
        CreateWorkingSet --> BindProductSpaces
        BindProductSpaces --> CopyUnsharedTrackers
        CopyUnsharedTrackers --> BindUnsharedTrackers
        BindUnsharedTrackers --> BindSharedTrackers
        BindSharedTrackers --> IndexUnsharedTrackers
        IndexUnsharedTrackers --> LinkRelations: 发送索引完成事件
    }
    Running --> Failed: 创建本体失败
    Running --> PartiallySucceeded: 本体成功且有失败项
    Running --> Succeeded: 已启用步骤均完成
    Failed --> [*]
    PartiallySucceeded --> [*]
    Succeeded --> [*]
```
