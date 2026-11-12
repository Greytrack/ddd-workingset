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
- 物化事务边界集中在 SK：`CowEventHandler` 在单个新事务内完成“锁定 Tracker → 读取目标变体 → 业务保存 → 记录凭据”。

## 3. 事件契约

`RequirementPurged(requirementId, trackerId, occurredAt)` 由 SK 定义、ALM 在永久删除提交后发布；`bootstrap.RequirementPurgedListener` 以 `@TransactionalEventListener(AFTER_COMMIT)` 同步消费并调用 `removePurged`，处理幂等（无对应 LTI 为空操作）。业务行删除与 LTI 清理因此不在同一数据库事务内，这是 D4“跨聚合用事件交互”的直接结果。

## 4. 验证

- `mvn -B test`：**78 项通过，0 失败、0 错误、0 跳过**，含 ArchUnit（SK 不得依赖 ALM）、真实 PostgreSQL/Redis/Tomcat。
- 单元证据：
  - `RequirementMaterializationAdapterTest`：快照证据不符、target 身份与请求不符、既有目标字段/版本不符均拒绝且不写业务行。
  - `CowEventHandlerTest`：SK 先行锁定目标变体，再把 `TargetIdentity` 交给 ALM；目标自身 revision 保持不变。
  - `MergeAcceptanceTest`：`delete2` 在仍有未物化依赖时返回 409；依赖物化后删除成功，对应 LTI 由事件清理。

## 5. 未覆盖

- LTI 清理失败的补偿/重试协议（当前为删除提交后的同步事件，失败将抛出并记录日志，不回滚业务删除）。
- 删除与 COW 事件交错时的并发收敛。
- 其余 BC 的写入 Port 适配（当前仅有 ALM Requirement）。
