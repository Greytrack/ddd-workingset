# CQRS 查询侧实施记录

**状态：Reviewed（实现记录，不扩大设计基线）**

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
- 查询端口改为 `application/query/provider`；`TrackerItemCatalogQueryProvider`、`TrackerItemQueryProvider`、`RelationSourceQueryProvider` 等负责应用读取。
- 查询 Provider 实现位于 `infrastructure/providers` 与 `infrastructure/providers/mybatis`。
- Provider 实现可以复用聚合 Repository、写自定义 SQL，或使用 MyBatis-Plus Lambda；查询模型不受聚合装载方式限制。
- 聚合 Repository 接口声明在 domain；Jdbc/缓存实现在 `infrastructure/repositories`。
- 当前查询已迁移：
  - WorkingSet 摘要、子级/祖先、Tracker 列表、Classification、Bindings；
  - Requirement 详情：持久化记录与未物化继承目标在同一读快照内依次解析，不再以聚合仓储重复查找同一详情 ID；仅继承路径读取来源，仍校验来源版次。
  - Requirement 活跃身份目录由 ALM 查询 Provider 直接读取，供比较与派生索引使用；不经聚合仓储。Tracker 展示列表仅由 Working Set 查询 Provider 提供。
- Controller 暂未拆分，`WorkingSetController` 与 `RequirementController` 同时注入 command/query 服务。
- 跨聚合事务的实现差距见 [实现边界](current-boundaries.md)。

## Working Set 绑定写入

- `WorkingSetRepository.save` 保存新建根及其成员，或保存持锁重建聚合的成员差量；现有根字段没有修改命令。绑定的增删及 `shown` 修改均经聚合；空间绑定仍由根文件夹关系表示，未变更关系不重写。
- Tracker 与创建任务也统一用 `save`，保存分支由实现处理。产品空间当前无更新命令；`save` 只写新对象，重复身份不覆盖。跨工作集的 Tracker 删除清理仍由删除用例协调。
- MergeRound 与 MergeCheckpoint 使用独立仓储，决定版本由独立生成器分配；LTI 原子家族占位不以普通保存替代。
- 产品空间、Tracker 与 Working Set 各有聚合仓储；入口及初始化的事务差距见 [实现边界](current-boundaries.md)。

## 后续

- LTI 横向扫描已由 `LightTrackerItemQueryProvider` 承接；`resolve` 仍可按 ID 装载身份聚合。工作区的 `TrackerQueryProvider` 属待验证接线。
- Creation/Merge 查询迁移到查询端口。
- Controller 按 command/query 拆成独立入口。
- 关系事件 outbox/消费方仍按 T7-E 开放项处理。

## 验证

`mvn -B test`：**165 项通过，0 失败、0 错误、0 跳过**；ArchUnit 增加 query→command、command→query、application→MyBatis 和 domain→持久化框架规则。
