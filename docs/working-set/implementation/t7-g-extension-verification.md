# T7-G 扩展工作集验收

**状态：Reviewed（2026-11-17）**。依据 [T7 设计 §12A](../domain/working-set-creation-lro-design.md)、[T7 后续计划](t7-implementation-prompt.md)、[T6-F §4.2](../reviews/2026-11-16-t6-f-lineage-constraints.md)。

## 1. 能力

- `POST /api/worksets/{target}/extensions` 受理扩展任务，body：

```json
{
  "requestId": "extension-...",
  "trackerIds": ["parent-tracker-a", "parent-tracker-b"]
}
```

- 返回 HTTP 202；轮询复用 `GET /api/working-set-creations/{id}`。
- 只处理目标尚未拥有的直接父 Tracker；目标已存在且已绑定的同一父 Tracker 派生被过滤（派生链 1:1）。
- 过滤后为空时直接返回 409 `NoDerivableTracker`，提示无可派生的跟踪分类，不创建任务。
- 新 Tracker 先以 `visible=false` 创建；任务终态后只揭示已成功绑定的 Tracker。
- 同一目标 WS 同时只允许一个 `Pending/Running` 扩展任务；不同 `requestId` 返回 409 `Preparing`。
- 扩展允许部分成功：有效 Tracker 完成派生/绑定/索引，失败项保留明细。
- 目标已有 Tracker 的 Item 不直接覆盖，由普通 Merge 加入。
- 单 Item `/api/worksets/{ws}/derive` 与按空间/分类的单 Item 派生入口已移除。

## 2. 持久化与并发

- `working_set_creations` 通过 `request->mode` 区分 `CREATE/EXTEND`。
- `uq_creation_target` 只约束 `CREATE` 目标；`uq_active_extension_target` 只约束未终态 `EXTEND`。
- `trackers.visible` 默认 `TRUE`；扩展新 Tracker 插入 `FALSE`，终态更新为 `TRUE`。
- 轮次/快照仍按 `working_set_creations`、`working_set_creation_snapshots`、`working_set_creation_items` 存储，沿用 T7-D 的冻结与游标恢复。

## 3. 测试证据

[WorkingSetCreationAcceptanceTest](../../../deploys/src/test/java/bootstrap/WorkingSetCreationAcceptanceTest.java)：

- `extendsAnExistingChildWithBatchDerivationAndFiltersAlreadyDerivedTrackers`
  - 两个父 Tracker 批量派生、索引、绑定；
  - 新 Tracker 终态可见；
  - 重复扩展返回 409 `NoDerivableTracker` 且不新增 Tracker；
  - `/derive` 返回 404。
- `extensionKeepsValidTrackerWhenAnotherSelectionFails`
  - 一个有效 Tracker + 一个不存在 Tracker；
  - 任务 `PartiallySucceeded`，有效 Tracker 可见，失败明细含 `SourceTrackerNotFound`。
- `rejectsAnotherExtensionWhileOneIsUnfinished`
  - 存在未终态扩展任务时，新请求返回 409 `Preparing`。

`mvn -B -q test`：**150 项通过，0 失败、0 错误、0 跳过**。
`WorkingSetCreationAcceptanceTest`：17 项通过。

## 4. 边界

- 扩展不自动合并目标已有 Tracker 的 Item；这些差异继续走普通 Merge。
- Tracker 派生链为 1:1：同一子工作集对同一父 Tracker 至多一个派生 Tracker。同一分类可投影多个 Tracker，但它们使用不同父 TrackerId，不是同一父下的多个派生。
- 失败的新 Tracker 保持 `visible=false`，可在任务明细中定位；不自动删除副本。
- UI 普通 Tracker/Bindings 列表过滤不可见项；任务专用轮询仍可读取任务状态。
