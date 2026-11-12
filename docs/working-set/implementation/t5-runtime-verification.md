# T5 运行时骨架验证结果（PostgreSQL + Redis + Web + Modulith 事件）

**状态：Verified（2026-11-12）**

- 范围：把首切片从纯内存单元验证推进到可启动的 Web 运行时：Spring Boot 内嵌 Tomcat、PostgreSQL/Spring JDBC 持久化、Redis 缓存、Spring Modulith 事件发布注册表、yml 配置与最小 REST API
- 依据：[T5-S0 设计](t5-s0-minimal-slice-design.md)、[S2 验证结果](t5-s2-verification-result.md)、[S3 任务说明](t5-s3-codex-task-brief.md)
- 本记录保留运行时骨架的原始证据。首次 COW 的实施与新增证据见 [S3 验证](t5-s3-verification-result.md)。

## 1. 启动与测试

```text
mvn -B test
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

mvn -B spring-boot:run
Tomcat started on port 7102 (http)
HikariPool-air-dev - Start completed
Started WorkingsetApplication
```

- PostgreSQL 16：`127.0.0.1:5432/ddd-test`，连接成功。
- Redis：`127.0.0.1:63179`，database `5`，`PONG`。
- Web：内嵌 Tomcat 10.1.31，端口 `7102`。
- 默认 profile 为 `default`；`application-local.yml` 保留，可通过 `SPRING_PROFILES_ACTIVE=local` 激活。

## 2. 最小 REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/health` | 检查 Web、PostgreSQL、Redis |
| `POST` | `/api/requirements` | 创建 Requirement（服务端雪花 `localId`） |
| `GET` | `/api/requirements/{localId}` | 按 `LocalId` 查询 Requirement |
| `PUT` | `/api/requirements/{localId}/title` | 普通修改标题，携带 `expectedLockVersion` |
| `POST` | `/api/worksets/{workingSetId}/derive` | 非共享派生，创建 Source-backed LightTrackerItem |
| `GET` | `/api/worksets/{workingSetId}/items/{localId}` | 解析变体元数据 |
| `GET` | `/api/worksets/{workingSetId}/items/{localId}/content` | 读取有效内容 |

实测结果（2026-11-12）：

```json
HEALTH: {"database":"UP","status":"UP","redis":"UP"}
CREATE: {"localId":"443628133134624","title":"A","revision":3,"version":null,"lockVersion":0}
CHANGE: {"localId":"443628133134624","title":"B","revision":3,"version":null,"lockVersion":1}
DERIVE: {"trackerId":"443628133265696","item":{"localId":"443628133265697","sourceLocalId":"443628133134624","sourceRevision":3,"materialized":false}}
READ:   {"fields":{"title":"B","description":"demo"},"contentRevision":3,"contentLocalId":"443628133134624","materialized":false}
CONFLICT: HTTP 409 {"reason":"LOCK_VERSION_CONFLICT","expectedLockVersion":0,"actualLockVersion":1}
EVENT:    listener_id=bootstrap.RequirementChangedListener.on(sk.workingset.application.event.RequirementChanged)
          publication_date=2026-11-12 11:09:53.332481+08, completion_date=2026-11-12 11:09:53.33948+08
```

## 3. PostgreSQL 持久化证据

`schema.sql` 创建三张表：

- `requirements`：聚合内容、`revision`、可空发布大版本 `version`、`lock_version`
- `light_tracker_items`：身份/来源/`materialized` 三字段
- `requirement_snapshots`：修改前不可变快照

实测数据库记录：

```text
requirements:          local_id=443628133134624, title=B, revision=3, version=NULL, lock_version=1
light_tracker_items:   local_id=443628133265697, source_local_id=443628133134624, source_revision=3, materialized=false
requirement_snapshots: local_id=443628133134624, title=A, revision=3, version=NULL, lock_version=0
```

`JdbcRequirementRepository` 使用 `lock_version` 条件 upsert；0 行受影响显式抛出 `LockVersionConflictException`，API 映射为 409，不静默覆盖。

## 4. Redis 缓存证据

- `CachingLightTrackerItemRepository` 装饰 SK 的 `LightTrackerItemRepository` 端口。
- 只缓存 LightTrackerItem 元数据（不缓存业务内容），key 前缀 `workingset:lti:`，TTL 30 秒。
- `save` 后写穿；`findById` 缓存优先；`findBySource` 不缓存，避免来源列表失效复杂度。
- Redis 不可用时降级到 JDBC 仓储，不影响业务正确性。

实测（database 5）：

```text
exists workingset:lti:443628229325089 -> 1
ttl     workingset:lti:443628229325089 -> 30
```

## 5. 事件发布证据（Spring Modulith Event Publication Registry）

- `RequirementApplicationService.changeTitle` 使用 `@Transactional`，保存成功后发布 `RequirementChanged`。
- `bootstrap.RequirementChangedListener` 使用 `@ApplicationModuleListener` 接收事件，并交给 `CowEventHandler` / `SourceSerialCowCoordinator`；S3 已接入首次 COW，证据见 [S3 验证](t5-s3-verification-result.md)。
- **业务事务内写入事件记录**：`@Transactional` 覆盖业务保存、快照写入与 Modulith 的 `event_publication` 记录；业务回滚时事件记录一并回滚（应用内 Outbox 风格原子性，不是外部 Outbox/MQ）。
- **提交后独立事务消费**：`@ApplicationModuleListener` 组合 `@TransactionalEventListener(AFTER_COMMIT)`、`@Async` 与新事务；监听器在业务提交后由异步线程执行，失败不回滚业务事务，记录保持未完成，可通过 `IncompleteEventPublications.resubmitIncompletePublications(...)` 或启动时自动重提恢复。
- `@ApplicationModuleListener` 默认异步执行；验收测试使用 Awaitility 等待 `completion_date` 更新，避免竞态。
- **不再手写 `JdbcRequirementEventStore`**；事件持久化完全由 Modulith 注册表负责。
- S3 的失败注入场景会让 `@ApplicationModuleListener` 抛出 `CowMaterializationFailedException`，测试日志出现 `SimpleAsyncUncaughtExceptionHandler` ERROR 属预期；事件保留未完成，见 [S3 验证](t5-s3-verification-result.md)。

实测 `event_publication`：

```text
listener_id       = bootstrap.RequirementChangedListener.on(sk.workingset.application.event.RequirementChanged)
event_type        = sk.workingset.application.event.RequirementChanged
publication_date  = 2026-11-12 11:09:53.332481+08
completion_date   = 2026-11-12 11:09:53.33948+08
```

容器验收测试 `bootstrap.RequirementAcceptanceTest` 使用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` 启动真实 Tomcat，通过 `TestRestTemplate` 创建需求、修改标题，并直查 `event_publication` 断言监听器身份、事件类型、发布时间、完成时间与需求身份。

## 6. 配置与工程变更

| 项 | 内容 |
| --- | --- |
| `pom.xml` | Spring Boot Web/JDBC/Redis、Spring Modulith（starter-jdbc + events-jdbc）、PostgreSQL 驱动、`spring-boot-maven-plugin`；编译保留参数名（`-parameters`） |
| `application.yml` | `server.port=7102`、Hikari、Redis `63179/db5`、`spring.sql.init.mode=always`、`spring.profiles.active=${SPRING_PROFILES_ACTIVE:default}` |
| `schema.sql` | 三张业务表 + 来源索引，全部 `IF NOT EXISTS` |
| 组合根 | JDBC 仓储 + Redis 缓存装饰器 + Adapter 工厂 + Spring Event 发布器 + Modulith 事件监听器 |
| 事件 | Spring Modulith Event Publication Registry：事件记录与业务写入同事务提交/回滚；`@ApplicationModuleListener` 在提交后异步新事务消费，成功标记完成，失败保留未完成供重提 |

修复记录：

1. JDBC URL 原为 `jdbc:p6spy:postgresql://...`，项目无 p6spy 依赖 → 改为标准 PostgreSQL URL；保留 `DB_URL` 覆盖。
2. `spring.profiles.active=@profiles.active@` 是 Maven 占位符，Spring 直接读取报 YAML 扫描错误 → 改为 `${SPRING_PROFILES_ACTIVE:default}`。
3. `@PathVariable` 未显式命名且编译未保留参数名 → 显式写 `@PathVariable("...")`，并启用 `-parameters`。

## 7. 限制与风险

| 编号 | 项 | 说明 |
| --- | --- | --- |
| R1 | S3 已接入 | 原运行时基线只验证事件发布。首次 COW、失败留档与状态切换已补齐，见 [S3 验证](t5-s3-verification-result.md) |
| R2 | Spring Modulith 关闭告警 | 测试上下文关闭时出现 `PersistentApplicationEventMulticaster` 的 `ClassCastException` 日志；测试通过，未影响运行；后续应评估排除 Modulith 事件 JDBC 自动配置或升级版本 |
| R3 | 缓存范围 | 仅 LightTrackerItem 元数据；Requirement 内容不缓存，避免普通修改不升级 revision 导致旧内容 |
| R4 | schema 管理 | 当前用 `schema.sql` 自动建表，无 Flyway/Liquibase 版本迁移 |
| R5 | 凭据 | `application.yml` 当前包含本地数据库口令；提交/部署前应改为环境变量或外部配置，不进入版本库 |
| R6 | 鉴权 | REST 使用固定 `Caller.of("web")`，未接入权限平台 |
| R7 | 事件消费与完成状态 | 占位监听器已接入 COW。成功标记完成，单目标失败仍可完成其他目标，失败事件保持未完成。重启重提与跨实例恢复待 S4 验证 |

## 8. 复现

```text
# 1. 测试（需要本机 PostgreSQL ddd-test 与 Redis 63179）
mvn -B test

# 2. 启动
mvn -B spring-boot:run

# 3. 健康检查
curl http://127.0.0.1:7102/api/health

# 4. 创建
curl -X POST http://127.0.0.1:7102/api/requirements \
  -H "Content-Type: application/json" \
  -d '{"commonId":"C1","trackerId":"T1","title":"A","description":"demo","revision":3}'
```
