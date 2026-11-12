# S5 绑定与删除实施验证

**状态：Reviewed（实施验证记录，2026-11-12）**

依据 [T2 绑定与删除契约](../domain/binding-deletion-contract-v1.md)。用户裁定 A 将本次纳入范围限定为已登记的首层 WorkingSet，直接上游固定为默认 WorkingSet。

范围限定说明：首层范围属于实施范围裁定，不改变 T2 契约对多级谱系、停用与权限的开放状态；不得据此把“直接上游只能是默认 WorkingSet”写成通用业务不变量。

## S5-A 绑定与派生

- `WorkingSet` 持有 `SpaceBinding` 与 `TrackerBinding`。身份键分别是 `(workingSetId, productSpaceId)` 和 `(workingSetId, trackerId)`，Tracker 槽位由 `(workingSetId, productSpaceId, classificationId)` 唯一约束。
- `Tracker` 持久化归属工作集、空间、分类、共享策略、直接派生来源 `sourceId` 和组织父级 `parentId`。`ProductSpace` 和 `Classification` 提供存在性与初始化共享策略，分类版本治理留待后续。
- [schema.sql](../../../src/main/resources/schema.sql) 新增 `working_sets`、`product_spaces`、`classifications`、`trackers`、`space_bindings`、`tracker_bindings`，重复启动通过 `ON CONFLICT DO NOTHING` 保留默认工作集 `1`。首层工作集及其前置数据由平台预置，本切片提供下方本地示例。
- [应用服务](../../../src/main/java/sk/workingset/application/WorkingSetApplicationService.java) 先检查目标工作集与空间绑定，再验证来源、共享策略和槽位。Shared 复用 TrackerId。显式派生接口与原 `/derive` 共用校验路径，并验证来源 Item 的 Tracker 与家族身份。Tracker、绑定和 LightTrackerItem 在同一个本地 JDBC 事务写入。缺失对象返回 404，空间或槽位冲突返回 409，来源身份或共享策略不符返回 422。

`mvn -B test` 在进入 S5-B 前通过 52 项（0 失败、0 错误、0 跳过）。最终回归包含 S5-A 及来源 Item 校验。

## S5-B 解绑与删除

| 操作 | 准入与效果 |
| --- | --- |
| `unbindTracker` | Shared 解绑保留 Tracker 和 Item。非共享通过 Item、组织子级和派生子级检查后，移除绑定并删除 Tracker。 |
| `removeSpace` | 该工作集的空间内无 TrackerBinding 时解除 SpaceBinding。 |
| `deleteTracker` | 执行三项检查，通过后解除指向该 Tracker 的绑定并删除 Tracker。空间绑定保留。 |
| `deleteProductSpace` | 从默认工作集入口发起。先检查所有工作集的 SpaceBinding，再检查空间下的 Tracker。冲突返回关联工作集 ID。 |
| `deleteWorkingSet` | 默认工作集始终拒绝。预检全部 TrackerBinding，失败时返回成员及原因。通过后依次解除 TrackerBinding、SpaceBinding，再删除 WorkingSet。 |

[删除服务](../../../src/main/java/sk/workingset/application/WorkingSetDeletionService.java) 用当前 JDBC 持久化事实检查 LightTrackerItem 和 ALM Requirement。数据库外键保护删除与 Item 新建的并发边界。新增约束以 PostgreSQL `NOT VALID` 安装，既有未登记的 Item 保留，新的 Item 引用必须指向有效 Tracker；历史 Tracker、工作集归属和绑定需要依据真实平台元数据补齐。

[真实 HTTP/JDBC 验收](../../../src/test/java/bootstrap/BindingAcceptanceTest.java) 断言 Shared 有 Item 仍可解绑，Tracker 的三项拒绝条件分别生效，空 SpaceBinding 仍阻止跨工作集空间删除，工作集预检失败保留原有绑定，以及并发创建 Item 与删除 Tracker 后无悬空引用。最终 `mvn -B test` 通过 58 项（0 失败、0 错误、0 跳过），包含 Tomcat、PostgreSQL、Redis、COW 和 ArchUnit 回归。

S5 使用 PostgreSQL 物理删除与本地事务。后续多级谱系见 [S6 验证结果](t5-s6-verification-result.md)。真实 Privilege 授权、规模验证及完整的 ProductSpace 创建与分类投影流程仍待推进。

## 本地前置数据示例

在服务完成 `schema.sql` 初始化后，向本地数据库登记示例数据。以下 ID 供本地测试使用。

```sql
INSERT INTO product_spaces (product_space_id) VALUES ('DEMO-SPACE');
INSERT INTO classifications (classification_id, sharing_mode) VALUES ('DEMO-CLASS', 'UNSHARED');
INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
VALUES ('T1', '1', 'DEMO-SPACE', 'DEMO-CLASS', 'UNSHARED');
```

使用 `POST /api/worksets/1/children` 创建 `DEMO-WS`（请求体为 `{"workingSetId":"DEMO-WS"}`），再用 `POST /api/worksets/DEMO-WS/spaces/DEMO-SPACE` 绑定空间。用 `POST /api/requirements` 在 `T1` 创建源 Requirement，再用返回的 LocalId、Revision 和 CommonId 调用 `POST /api/worksets/DEMO-WS/derive`。
