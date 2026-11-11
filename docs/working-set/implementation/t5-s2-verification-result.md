# T5-S2 验证结果

**状态：Verified（S2 完成，2026-11-11）**

- 范围：来源 Requirement 普通修改 → 修改前不可变快照 → 来源事件发布（AT-03 前半），以及 Spring Boot、Spring Event、雪花 ID 三项工程前置
- 依据：[S2 任务说明](t5-s2-codex-task-brief.md)、[T5-S0 设计](t5-s0-minimal-slice-design.md)、[场景-测试映射](t5-s0-scenario-test-mapping.md)、[S1 验证结果](t5-s1-verification-result.md)

## 1. 验证命令与结果

```text
mvn -B test
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 2. 场景与检查对应

| 编号 | 测试类/方法 | 结果 |
| --- | --- | --- |
| AT-03 前半：普通修改不升级 Revision、不改变发布 version、lock_version 递增、快照为修改前内容 | `bootstrap.RequirementChangeFlowTest#AT03_change_title_keeps_revision_and_version_but_increments_lock_version` | ✅ |
| 事件仅在保存成功后发布 | 同上 `#event_is_published_only_after_saved_state_is_visible` | ✅ |
| 并发证据：旧 `expectedLockVersion` 显式拒绝，无事件、无快照 | 同上 `#stale_expected_lock_version_is_rejected_without_event_or_snapshot` | ✅ |
| 连续修改：事件有序、每次捕获修改前快照 | 同上 `#consecutive_changes_publish_ordered_events_and_capture_each_pre_change_snapshot` | ✅ |
| 保存失败不发布事件 | 同上 `#save_failure_does_not_publish_event_or_change_stored_content` | ✅ |
| Spring Boot 非 web 上下文可加载 | `bootstrap.ApplicationContextTest#context_loads_without_web_and_exposes_composition_root_beans` | ✅ |
| `RequirementChanged` 经 Spring Event 同步派发 | 同上 `#requirement_changed_is_delivered_through_spring_event_synchronously` | ✅ |
| 雪花 ID 唯一性、位段、时钟回拨 | `sk.workingset.infrastructure.SnowflakeIdGeneratorTest`（5 项） | ✅ |
| 派生 LocalId/TrackerId 为雪花值（非 UUID） | `bootstrap.WorkingSetSliceIntegrationTest#derived_local_id_is_a_snowflake_value_not_uuid` | ✅ |
| changeTitle 不可变、version 可空与 lock_version 自动递增 | `alm.requirement.domain.RequirementTest`（6 项） | ✅ |
| 快照完整性与修改隔离（含 version/lock_version） | `alm.requirement.infrastructure.SnapshotStoreTest`（4 项） | ✅ |
| S1 回归（AT-01/AT-02、AC-01、AC-02） | `bootstrap.WorkingSetSliceIntegrationTest`（6 项）、`architecture.ArchitectureTest`、`sk.workingset.domain.LightTrackerItemTest`（6 项） | ✅ |

## 3. 实现位置

| 层 | 包 | 内容 |
| --- | --- | --- |
| SK 应用端口 | `sk.workingset.application.port` | `IdentifierGenerator`（`long nextId()`） |
| SK 事件信封 | `sk.workingset.application.event` | `RequirementChanged`（`eventId`/`requirementId`/`sourceLocalId`/`revision`/`snapshotRef`/`occurredAt`）、`SnapshotRef` |
| SK 基础设施 | `sk.workingset.infrastructure` | `SnowflakeIdGenerator`（3/3/5 位宽、2020-01-01 纪元、回拨拒绝、`synchronized`） |
| ALM 领域 | `alm.requirement.domain` | `Requirement.changeTitle(String, LockVersion)`（不可变、`lock_version` 自动 +1、`revision`/`version` 不变）、`LockVersionConflictException` |
| ALM 应用 | `alm.requirement.application` | `RequirementApplicationService.changeTitle`（并发校验 → 修改前快照 → 保存 → 发布事件） |
| ALM 基础设施 | `alm.requirement.infrastructure` | `RequirementSnapshot`、`SnapshotStore`、`InMemorySnapshotStore`（引用为雪花 ID） |
| 组合根 | `bootstrap` / `bootstrap.configuration` | `WorkingsetApplication`（`@SpringBootApplication`，非 web）、`IdGeneratorConfig`、`ApplicationConfiguration`（装配快照存储与 Spring Event 发布器） |

## 4. 工程前置落地

| 项 | 落地结果 |
| --- | --- |
| Spring Boot | `spring-boot-dependencies` 3.3.5 BOM + `spring-boot-starter`（非 web）；`WorkingsetApplication` 启动类；领域层仍为纯 Java |
| 雪花 ID | `START_STAMP=1577836800000L`；`DATACENTER_BIT=3`、`MACHINE_BIT=3`、`SEQUENCE_BIT=5`；`IdGeneratorConfig` 使用 `DATACENTER_ID=1`、workerId 为本机地址哈希取模；`LocalId.newId()` 的 UUID 路径已删除 |
| Spring Event | ALM 应用服务注入 `ApplicationEventPublisher`，保存成功后发布 `RequirementChanged`；默认同线程同步派发，满足 D-018 顺序消费前提 |

## 5. 与设计的偏离（已记录，不改变契约）

| 项 | 偏离 | 原因 |
| --- | --- | --- |
| 事件监听测试 | 使用 `PayloadApplicationEvent<RequirementChanged>` 监听（Spring `ApplicationListener<E extends ApplicationEvent>` 的泛型边界） | 不把 SK 事件信封改成 Spring 类型；事件类型保持框架无关 |
| 快照类型位置 | `RequirementSnapshot` 放在 `alm.requirement.infrastructure` | 遵循 S2 白名单（仅允许新增 infrastructure 快照存储）；S3 通过 `SnapshotStore` 回读 |
| 快照引用 | `SnapshotRef.id` 为雪花 ID 字符串 | 与 FACT-005 一致；引用不含内容 |
| `TrackerId` | 由雪花生成器产生（原 `T-<UUID>` 移除） | 完成 UUID 路径清理，未改动 TrackerId 契约 |
| `lock_version` 自动递增 | 内存实现由 `Requirement.changeTitle` 在数据变动时自动 +1；未引入数据库事务钩子 | 与用户术语一致（不需要调用方显式触发）；持久化落地时由事务层统一维护 |

## 6. 观察项（S3 前置）

| 编号 | 项 | 状态 |
| --- | --- | --- |
| O1 | 位宽 3+3+5 → 单机每毫秒 32 个 ID | 按给定参数实现；压测不足时再评估 |
| O2 | Spring Event 默认同步顺序 | 已记录；S3 若引入 `@Async` 必须显式排序 |
| O3 | `RequirementAdapter.readAt` 对非当前版次抛异常 | **S3** 改为按快照读取 |
| O4 | `authorize` 恒 `true` | 权限切片再处理 |
| O5 | Spring Boot 依赖下载 | 本机镜像可用，升级成功；未回退 `spring-context` |

## 7. 未做（后续任务）

S3：`CowEventHandler`、`RequirementWritePort.materialize`、`switchToLocalBacked`、Local-backed Query；S4：保存成功但切换失败的幂等恢复、重复事件与顺序回归。Merge、删除、权限、版本治理不在首切片。

## 8. 复现

```text
mvn -B -DskipTests compile   # 38 个主源文件
mvn -B test                  # 35 个测试
```
