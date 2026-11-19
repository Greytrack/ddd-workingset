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
- 查询端口改为 `application/query/provider`；`TrackerItemCatalogQueryProvider`、`TrackerItemQueryProvider`、`RelationSourceQueryProvider`、`TrackerScopePort` 也已从 `application/port` 移入。
- 查询 Provider 实现位于 `infrastructure/providers` 与 `infrastructure/providers/mybatis`。
- Provider 实现可以复用聚合 Repository、写自定义 SQL，或使用 MyBatis-Plus Lambda；查询模型不受聚合装载方式限制。
- 聚合 Repository 接口声明在 domain；Jdbc/缓存实现在 `infrastructure/repositories`。
- 当前查询已迁移：
  - WorkingSet 摘要、子级/祖先、Tracker 列表、Classification、Bindings；
  - Requirement 详情：持久化记录与未物化继承目标在同一读快照内依次解析，不再以聚合仓储重复查找同一详情 ID；仅继承路径读取来源，仍校验来源版次。
  - Requirement 活跃身份目录由 ALM 查询 Provider 直接读取，供比较与派生索引使用；不经聚合仓储。Tracker 展示列表仅由 Working Set 查询 Provider 提供。
- Controller 暂未拆分，`WorkingSetController` 与 `RequirementController` 同时注入 command/query 服务。
- 命令侧事务、聚合不变量、JdbcTemplate Repository 未改变。

## Working Set 绑定写入

- `WorkingSetRepository.save` 保存新建根及其成员，或保存持锁重建聚合的成员差量；现有根字段没有修改命令。绑定的增删及 `shown` 修改均经聚合；空间绑定仍由根文件夹关系表示，未变更关系不重写。
- Tracker 与创建任务也统一用 `save`，保存分支由实现处理。产品空间当前无更新命令；`save` 只写新对象，重复身份不覆盖。跨工作集的 Tracker 删除清理仍由删除用例协调。
- MergeRound 与 MergeCheckpoint 使用独立仓储。轮次幂等受理及 LTI 原子家族占位保留专用领域语义，不以普通保存替代。
- 产品空间命令与仓储由独立的 `sk.productspace` 包提供。主入口为 `POST /api/product-spaces` 和 `DELETE /api/product-spaces/{spaceId}`；旧路由由同一控制器兼容。创建仍在同一事务内初始化默认工作集成员，挂载/卸载仍由 Working Set 管理。

## 后续

- LTI 列表、`resolve`/`read` 的读模型查询迁移到查询端口。
- Creation/Merge 查询迁移到查询端口。
- Controller 按 command/query 拆成独立入口。
- 关系事件 outbox/消费方仍按 T7-E 开放项处理。

## 验证

`mvn -B test`：**165 项通过，0 失败、0 错误、0 跳过**；ArchUnit 增加 query→command、command→query、application→MyBatis 和 domain→持久化框架规则。
