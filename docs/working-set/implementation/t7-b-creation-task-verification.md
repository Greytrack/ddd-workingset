# T7-B 工作集创建任务骨架验收

**状态：Reviewed**。依据 [T7-A 裁定](../reviews/2026-11-13-t7-a-creation-lro-review.md) 与 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集) 的任务受理、轮询与范围可见性部分。

- 空范围请求通过 `POST /api/working-set-creations` 受理，返回 HTTP 202 和任务轮询地址。工作集创建和任务成功结果在同一个执行事务提交。任务由 Modulith 异步事件推进，重复投递通过任务行锁和已保存状态判定是否还需执行。重启后的未完成事件沿用现有 Modulith 配置。
- 测试持有父工作集的数据库行锁，验证任务仍可轮询为 `Running`，工作集查询和以目标为父的创建命令返回 `Preparing`。放开锁后任务成功，目标工作集可访问。同一请求返回原任务，另一请求争用相同目标返回 HTTP 409。
- 父级不存在时轮询返回 `Failed` 与本体阶段的具体原因。非空范围返回 `CapabilityNotSupported`，避免将未初始化的工作集报告为成功。

公开验收见 [WorkingSetCreationAcceptanceTest](../../../src/test/java/bootstrap/WorkingSetCreationAcceptanceTest.java)。非共享 Tracker 复制与空间、Tracker 绑定由 T7-C 接入，分页 LTI 与部分成功在 T7-D 验证。关系处理与规模验证继续按 [T7 实施顺序](t7-implementation-prompt.md) 推进。

`mvn -B clean test` 使用 PostgreSQL、Redis 和内嵌 Tomcat，包含 ArchUnit。94 项通过，0 失败、0 错误、0 跳过。
