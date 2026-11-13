# D4 SK 边界验证：LTI 校验与移除移回 SK

**状态：Reviewed（实施验证记录，2026-11-12）**

依据 [T4 跨 BC 公共契约](../domain/cross-bc-contract-v1.md)（K1″/K2″ 与 2026-11-12 D4 裁定）、[T3 物化协议](../domain/lighttrackeritem-materialization-contract-v1.md)、[S7 验证](t5-s7-verification-result.md)。

## 1. 用户裁定

D4 选择方案 A：把 LightTrackerItem 的校验与移除**移回 SK**；跨聚合用事件交互。此前 `RequirementMaterializationAdapter` 与 `RequirementLifecycleService` 直接注入 `TrackerRepository` / `LightTrackerItemRepository`，并由 ALM 读取、锁定 SK 聚合，越过了 T4 的 Adapter 边界。

## 2. 职责划分（实施后）

| 关注点 | 归属 | 入口 |
| --- | --- | --- |
| Tracker 锁定、目标变体读取 | SK | `TrackerItemLifecycleService.lockMaterializationTarget` |
| 内容是否仍被 COW 依赖 | SK | `TrackerItemLifecycleService.assertPurgeable` |
| LTI 移除 | SK | `TrackerItemLifecycleService.removePurged` |
| 业务对象的创建/校验/提交 | ALM | `RequirementWritePort.materialize` / `verifyMaterializedTarget` |
| 业务对象永久删除决定 | ALM | `RequirementLifecycleService.delete2` |
| 删除后清理 LTI 的通知 | 跨 BC 事件 | `RequirementPurged` → `RequirementPurgedListener` |

- 业务 BC 不再接收 SK 聚合对象：物化时 SK 传入值对象 `TargetIdentity`（localId / trackerId / commonId / revision / materialized）。
- 业务 BC 不再注入 SK 仓储：ALM 侧只依赖自身 `RequirementRepository`、`SnapshotStore` 与 SK 定义的协作端口。
- 物化事务边界（D3-B，2026-11-12 用户裁定）：`CowEventHandler` 以三段独立事务执行——① SK 锁定 Tracker 并读取目标变体；② ALM 独立提交业务保存；③ SK 记录物化凭据，随后在独立事务切换 LTI。业务保存与 SK 凭据/切换**不再要求同一数据库事务**；凭据或切换未完成时由 Modulith 未完成事件重投收敛（D13），业务保存本身按 `verifyExisting` 幂等。

## 3. 事件契约

`RequirementPurged(requirementId, trackerId, occurredAt)` 由 SK 定义、ALM 在永久删除提交后发布；`bootstrap.RequirementPurgedListener` 以 `@ApplicationModuleListener` 消费并调用 `removePurged`（D13：失败保留为未完成事件，可经 `/api/cow/recover/incomplete` 重投，处理幂等）。业务行删除与 LTI 清理因此不在同一数据库事务内，这是 D4“跨聚合用事件交互”的直接结果。

## 4. 验证

- `mvn -B test`：**78 项通过，0 失败、0 错误、0 跳过**，含 ArchUnit（SK 不得依赖 ALM）、真实 PostgreSQL/Redis/Tomcat。
- 单元证据：
  - `RequirementMaterializationAdapterTest`：快照证据不符、target 身份与请求不符、既有目标字段/版本不符均拒绝且不写业务行。
  - `CowEventHandlerTest`：SK 先行锁定目标变体，再把 `TargetIdentity` 交给 ALM；目标自身 revision 保持不变。
  - `MergeAcceptanceTest`：`delete2` 在仍有未物化依赖时返回 409；依赖物化后删除成功，对应 LTI 由事件清理。

## 5. 未覆盖

- 删除与 COW 事件交错时的并发收敛。
- 其余 BC 的写入 Port 适配（当前仅有 ALM Requirement）。
- LTI 清理失败后的重试由 Modulith 未完成事件记录 + `/api/cow/recover/incomplete` 重投（D13），不额外引入对账任务；事件处理保持幂等。
- Merge 路径的“条目应用 + LTI 切换”仍沿用独立条目事务，不按 D3-B 拆分（见 S7 验证）；D3-B 只覆盖 COW 物化的业务保存与 SK 凭据。
