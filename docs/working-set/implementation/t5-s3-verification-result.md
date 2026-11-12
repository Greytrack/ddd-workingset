# T5-S3 首次 COW 验证结果

**状态：Reviewed。2026-11-12 在本机 PostgreSQL、Redis 与内嵌 Tomcat 上验证。**

依据：[S3 实施契约](t5-s3-codex-task-brief.md)、[运行时基线](t5-runtime-verification.md)、[验收映射](t5-s0-scenario-test-mapping.md)。范围为 AT-03、AT-04、AT-05 的失败前置，以及 INV-037/039/044/050/051/069～071。完整恢复属于 AT-05 后续验证。

| 层 | 实施 |
| --- | --- |
| SK 应用 | 事件信封拆分 before/after Revision 和 LockVersion。新增物化请求、写 Port、切换事务 Port、同源协调器、逐目标 COW 编排、失败记录接口及聚合异常。 |
| ALM | 来源应用服务发布修改前后证据。独立写 Adapter 校验快照与既有目标，物化保存独立提交。 |
| 基础设施 | Modulith 监听器等待 COW 结果。组合根装配独立事务。PostgreSQL 增加 `cow_failures`，Redis 在 LTI 提交后更新。 |
| 验证 | HTTP 验收、Adapter 证据校验、同源队列和缓存回滚测试，事件字段回归与 ArchUnit。 |

`mvn -B test` 实施前 **37 项通过**，实施后 **46 项通过，Failures 0，Errors 0，Skipped 0**。验收使用 Awaitility 等待条件。既有 Modulith 关闭告警仍在测试日志中出现。

`mvn -B spring-boot:run` 启动 Tomcat 7102，`GET /api/health` 返回 `{"status":"UP","database":"UP","redis":"UP"}`。

## HTTP 与数据库证据

| 场景 | 观察结果 |
| --- | --- |
| AT-03/04，顺序 PUT A → B → C | 两次 PUT 均为 200，业务 Revision 保持 3，LockVersion 依次为 1、2。来源 GET 为 C，两目标 content GET 为 A 且 `materialized=true`。目标行 `443633957255456` 的 title=A、source_local_id 等于自身、source_revision=3。两条发布记录均有 completion_date。 |
| AT-05 前置，LTI 写入后注入异常 | 目标 `443633958719777` 的业务行仍为 A，LTI 数据库行 `materialized=false`。GET 仍走来源并返回 B。另一目标成功切换。失败记录为 SWITCH，事件 completion_date 为空。 |
| 物化拒绝 | 派生家族与来源快照不匹配时，目标没有业务行，LTI 仍为 Source-backed，失败记录为 MATERIALIZE，事件未完成。Adapter 测试另覆盖快照缺失、来源身份、版次、并发版本及既有目标证据不一致。 |

测试按本次来源和目标 ID 清理，服务端 LocalId 仍按既定雪花规则生成。切换失败测试同时验证真实数据库事务回滚和 Redis 不发布未提交状态。协调器验证已排队任务按序号执行、不同来源可并行、异常传播和 ORDER_GAP。处理器测试使用直接构造的升级形状事件，验证按修改前版次匹配、保留目标自身版次、已物化目标跳过。

## 升级记录与限制

修改事件字段前查询本地 `ddd-test.event_publication`，未完成记录为 **0**。自动审查拦截了批量删除命令，本次未删除既有事件。业务表结构和 Modulith 注册表结构沿用运行时基线。

S3 依赖单实例顺序投递来保障首次快照。协调器按当前队列中已有的序号排序并记录缺口，等待缺失序号、跨实例排序、并发同源交错、自动重试、去重、跨聚合恢复及 Revision 升级端到端留 S4。既有目标按身份、内容及发布版本比对，精确物化来源凭据仍留 S4。附件、关系、历史回读、Merge、删除、权限和版本治理按相应后续切片处理。

## 收尾核对

- `mvn -B test` 实测 46 项通过（2026-11-12），与实施记录一致。
- 失败注入场景（物化拒绝、切换回滚）会让 `@ApplicationModuleListener` 抛出 `CowMaterializationFailedException`，日志出现 `SimpleAsyncUncaughtExceptionHandler` ERROR 属预期；对应 `event_publication.completion_date` 为空。
- 偏离：`InMemoryLightTrackerItemRepository` 未做实例隔离（S3 原 A 裁定）；生产路径使用 JDBC，内存实现仅供测试。`CowEventHandlerTest` 因此仍依赖同一实例；S3 收尾决定补隔离或改按身份断言。
- 待办：`ApplicationContextTest` 方法名仍写 `synchronously`，应改为“发布后同步监听器可见”，避免与 Modulith 异步消费混淆。
