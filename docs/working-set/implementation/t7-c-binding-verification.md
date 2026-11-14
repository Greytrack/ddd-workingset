# T7-C 工作集范围绑定验收

**状态：Reviewed**。契约与失败规则见 [T7 设计 §4](../domain/working-set-creation-lro-design.md#4-模型)，场景见 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)。

| 验收场景 | 观察结果 |
| --- | --- |
| 非空范围创建 | HTTP 202，依次创建本体、绑定空间、复制非共享 Tracker、绑定非共享与共享 Tracker。非共享副本保持同一空间和分类，`sourceId` 指向父 Tracker。共享绑定沿用选中的 ID。目标 LTI 数量为零。 |
| 空间漏选、父级范围不符 | 无对应空间绑定时复制失败，后续绑定计入跳过。非根父级未纳入的空间或共享 Tracker 被拒绝。已提交范围仍可在任务终态后查询。 |
| 复制成功但绑定槽位冲突 | 轮询分别显示复制成功与绑定失败，保留未绑定副本。 |
| 幂等与可见性 | 相同请求返回同一任务，目标 ID 冲突返回 HTTP 409。事件重复投递后任务结果与副本数量一致。运行中的范围查询、绑定与按该工作集创建 Item 返回 `Preparing`。 |
| 目标已存在与父创建中 | 目标 ID 已存在时受理直接 409 `WorkingSetAlreadyExists`，该 WS 仍可普通查询；父 WS 在另一任务中时以它为父返回 409 `Preparing`。 |
| 查询过滤与任务视图 | 目标创建中时普通详情返回 `Preparing`，子级/祖先不包含目标；`GET /api/working-set-creations/{id}/working-set` 可读目标视图。 |

[任务服务](../../../src/main/java/sk/workingset/application/WorkingSetCreationService.java) 用任务行锁串行化每项推进。每项写入与进度同事务提交。已存在的副本或绑定计入跳过，已提交进度的事件重投从剩余项继续。PostgreSQL 任务表结构沿用 T7-B，既有任务 JSON 缺少 `stageProgress` 时按本体阶段恢复。

验收入口见 [WorkingSetCreationAcceptanceTest](../../../src/test/java/bootstrap/WorkingSetCreationAcceptanceTest.java)。本记录覆盖绑定阶段，后续索引行为见 [T7-D 验收](t7-d-index-verification.md)。

`mvn -B clean test` 在 PostgreSQL、Redis 和内嵌 Tomcat 环境通过 101 项，含 ArchUnit。失败、错误、跳过均为 0。
