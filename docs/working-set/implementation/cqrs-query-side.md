# CQRS 查询侧实施记录

**状态：Reviewed（2026-11-18；Phase 1～2 已实施）**

## 目标

`application` 按命令/查询职责拆分，查询侧使用 MyBatis-Plus 只读适配器；命令侧继续使用聚合 + JdbcTemplate。

## 已落地

```text
sk.workingset.application.command
sk.workingset.application.query
alm.requirement.application.command
alm.requirement.application.query
```

- 命令服务：`WorkingSetCommandService`、`WorkingSetCreationService`、`WorkingSetDeletionService`、`MergeExecutionService`、COW/Merge 命令步骤。
- 查询服务：`WorkingSetQueryService`、`RequirementQueryService`。
- 查询模型 ID 使用 `Long`；Controller 继续输出字符串 ID，保持现有 JSON 契约。
- 事件定义已迁移到 `domain/event`；事件监听器仍在 `application/eventhandler`。
- 查询端口改为 `application/query/provider`；实现位于 `infrastructure/providers` 与 `infrastructure/providers/mybatis`。
- Provider 实现可以复用聚合 Repository、写自定义 SQL，或使用 MyBatis-Plus Lambda；查询模型不受聚合装载方式限制。
- 聚合 Repository 接口声明在 domain；Jdbc/缓存实现在 `infrastructure/repositories`。
- 当前查询已迁移：
  - WorkingSet 摘要、子级/祖先、Tracker 列表、Classification、Bindings；
  - Requirement 持久化详情（继承目标仍回退现有读取能力）。
- Controller 暂未拆分，`WorkingSetController` 与 `RequirementController` 同时注入 command/query 服务。
- 命令侧事务、聚合不变量、JdbcTemplate Repository 未改变。

## 后续

- LTI 列表、`resolve`/`read` 的读模型查询迁移到查询端口。
- Creation/Merge 查询迁移到查询端口。
- Controller 按 command/query 拆成独立入口。
- 关系事件 outbox/消费方仍按 T7-E 开放项处理。

## 验证

`mvn -B test`：**165 项通过，0 失败、0 错误、0 跳过**；ArchUnit 增加 query→command、command→query、application→MyBatis 和 domain→持久化框架规则。
