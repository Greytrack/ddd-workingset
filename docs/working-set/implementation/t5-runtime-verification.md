# T5 运行时骨架验证结果（PostgreSQL + Redis + Web）

**状态：Verified（2026-11-12）**

- 范围：把首切片从纯内存单元验证推进到可启动的 Web 运行时：Spring Boot 内嵌 Tomcat、PostgreSQL/Spring JDBC 持久化、Redis 缓存、Spring Modulith 依赖、yml 配置与最小 REST API
- 依据：[T5-S0 设计](t5-s0-minimal-slice-design.md)、[S2 验证结果](t5-s2-verification-result.md)、[S3 任务说明](t5-s3-codex-task-brief.md)
- **不包含**：S3 的 COW Handler/物化切换、Merge、删除、权限、版本治理、生产迁移与鉴权

## 1. 启动与测试

```text
mvn -B test
Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
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

## 5. 配置与工程变更

| 项 | 内容 |
| --- | --- |
| `pom.xml` | Spring Boot Web/JDBC/Redis、Spring Modulith、PostgreSQL 驱动、`spring-boot-maven-plugin`；编译保留参数名（`-parameters`） |
| `application.yml` | `server.port=7102`、Hikari、Redis `63179/db5`、`spring.sql.init.mode=always`、`spring.profiles.active=${SPRING_PROFILES_ACTIVE:default}` |
| `schema.sql` | 三张业务表 + 来源索引，全部 `IF NOT EXISTS` |
| 组合根 | JDBC 仓储 + Redis 缓存装饰器 + Adapter 工厂 + Spring Event 发布器 |
| 事件 | 仍使用 Spring Event 同步派发；Spring Modulith 依赖已引入，事件持久化未作为业务依赖 |

修复记录：

1. JDBC URL 原为 `jdbc:p6spy:postgresql://...`，项目无 p6spy 依赖 → 改为标准 PostgreSQL URL；保留 `DB_URL` 覆盖。
2. `spring.profiles.active=@profiles.active@` 是 Maven 占位符，Spring 直接读取报 YAML 扫描错误 → 改为 `${SPRING_PROFILES_ACTIVE:default}`。
3. `@PathVariable` 未显式命名且编译未保留参数名 → 显式写 `@PathVariable("...")`，并启用 `-parameters`。

## 6. 限制与风险

| 编号 | 项 | 说明 |
| --- | --- | --- |
| R1 | S3 尚未实现 | 事件已发布，但没有 `CowEventHandler`/`RequirementWritePort`/物化切换；`read` 对 Source-backed 仍读来源当前内容 |
| R2 | Spring Modulith 关闭告警 | 测试上下文关闭时出现 `PersistentApplicationEventMulticaster` 的 `ClassCastException` 日志；测试通过，未影响运行；后续应评估排除 Modulith 事件 JDBC 自动配置或升级版本 |
| R3 | 缓存范围 | 仅 LightTrackerItem 元数据；Requirement 内容不缓存，避免普通修改不升级 revision 导致旧内容 |
| R4 | schema 管理 | 当前用 `schema.sql` 自动建表，无 Flyway/Liquibase 版本迁移 |
| R5 | 凭据 | `application.yml` 当前包含本地数据库口令；提交/部署前应改为环境变量或外部配置，不进入版本库 |
| R6 | 鉴权 | REST 使用固定 `Caller.of("web")`，未接入权限平台 |
| R7 | 事件持久化 | Spring Modulith 事件表与业务事件未建立正式契约；当前业务事件由 Spring Event 直接派发。S3 任务说明已按异步投递修订，但本运行时尚未实现异步 dispatcher/COW。 |

## 7. 复现

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
