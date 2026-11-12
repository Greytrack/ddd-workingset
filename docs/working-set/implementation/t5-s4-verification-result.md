# T5-S4 一致性恢复与回归

**状态：Reviewed。2026-11-12，本机 PostgreSQL、Redis 与内嵌 Tomcat。**

依据为 [S4 任务](t5-s0-codex-task-plan.md)、[S3 验证](t5-s3-verification-result.md)、[场景映射](t5-s0-scenario-test-mapping.md)与 [T3 §6～§7](../domain/lighttrackeritem-materialization-contract-v1.md)。对应 AT-05、SCN-030/049/067、INV-037/038/039/044/050/051/052/069～071。

## 阶段 0 · S3 收尾

- 当时先验证内存仓储的实例隔离和按 LocalId 读回。内存实现随后退役，当前持久化证据见 [S3 测试迁移](t5-s3-verification-result.md)。
- `cow_failures` 增加修改前后锁版本、尝试次数及解决时间，旧记录可缺少锁版本。
- `mvn -B test` 在阶段 0 前后均为 **46 项通过，失败与错误均为 0**。

## S4 验收

`mvn -B test` 最终结果为 **Tests run: 50, Failures: 0, Errors: 0, Skipped: 0**。ArchUnit 通过。

| 验收 | 可观察证据 |
| --- | --- |
| AT-05 失败前置 | 对目标 `443663974816033` 注入 LTI 写后回滚。Requirement 仍为 A，LTI 保持 Source-backed，`cow_failures.stage=SWITCH` 且锁版本为 0/1。收据为 FAILED，`event_publication.completion_date` 为空。另一个目标成功切换。 |
| AT-05 仅补切换 | 目标业务行随后改为 `edited locally`。两次 HTTP switch-only 均返回 204，目标仅一条业务行，读取保持编辑结果，收据为 SWITCHED，失败行有 resolved_at。重提后原事件标记完成，成功目标尝试次数仍为 1。 |
| 手动与启动重提 | 分别经 HTTP 202 与重新创建 Spring 应用上下文触发。失败目标从 FAILED 转为 SWITCHED，尝试次数为 2，成功目标仍为 1。业务行仍为一条，原事件完成。 |
| 同源乱序 | 序号 2 先到时在时限内等待 1，执行顺序为 1、2，其他来源可继续。缺口超时产生 ORDER_GAP。默认等待 1 秒，可通过 `COW_ORDER_GAP_WAIT` 配置。 |
| Revision 升级 | 在真实事务中升级来源 3→4，LockVersion 0→1。快照 Revision 为 3，事件消费按 3 匹配，目标保留自身 Revision 1，HTTP 读到修改前内容 A。 |

## 实施与 S3 差异

| 层 | 修改及新增文件 |
| --- | --- |
| ALM | `Requirement`、`RequirementApplicationService`、`RequirementMaterializationAdapter` |
| SK 应用 | `CowEventHandler`、`CowFailureLog`、`SourceSerialCowCoordinator`、`RequirementWritePort`，新增 `CowMaterializationStore` 与 `CowRecoveryService` |
| SK 基础设施 | `InMemoryLightTrackerItemRepository`、`CachingLightTrackerItemRepository`、`JdbcCowFailureLog`，新增 `JdbcCowMaterializationStore` |
| 入口与配置 | `ApplicationConfiguration`、新增 `CowRecoveryController`、`application.yml`、`schema.sql` |
| 测试 | `CowAcceptanceTest`、`WorkingSetSliceIntegrationTest`、`CowEventHandlerTest`、`SourceSerialCowCoordinatorTest` |

收据以 `(event_id, target_local_id)` 唯一定位。业务保存与 `business_saved` 凭据同事务提交，避免两次提交之间丢失恢复依据。额外保存 `source_revision` 以校验切换前的来源证据。LTI、SWITCHED 与失败解决时间在另一事务提交。恢复只验证已保存业务对象的稳定身份和版次，允许其字段已被合法编辑。恢复事务读 LTI 时直接查 JDBC。

Modulith 1.2.13 的启动重提读取 `spring.modulith.republish-outstanding-events-on-restart`，已按实际版本修正键名并验证。测试只按本次 ID 清理。

## 验证边界

单实例内，首次快照顺序受有界等待限制。缺失事件在超时后到达时，无法保证它先执行。跨实例顺序、多监听器、任意崩溃点与数据库提交故障的组合未覆盖。重启测试验证了持久化记录在新上下文启动时的重提。常驻自动重试、告警、附件和关系、Merge、绑定删除、权限与版本治理在本次范围之外。
