# T5-S1 验证结果

**状态：Verified（S1 完成，2026-11-11）**

- 范围：工程骨架与派生/查询链路（AT-01、AT-02、AC-01、AC-02 的 S1 适用部分）
- 依据：[S1 任务说明](t5-s1-codex-task-brief.md)、[T5-S0 设计](t5-s0-minimal-slice-design.md)、[场景-测试映射](t5-s0-scenario-test-mapping.md)

## 1. 验证命令与结果

```text
mvn -B test
Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 2. 场景与检查对应

| 编号 | 测试类/方法 | 结果 |
| --- | --- | --- |
| AT-01 派生身份与不复制内容 | `bootstrap.WorkingSetSliceIntegrationTest#AT01_derive_creates_independent_variant_without_copying_content` | ✅ |
| AT-02 Source-backed 读来源内容 | 同上 `#AT02_source_backed_read_resolves_source_content` | ✅ |
| 失败语义：NotFound | 同上 `#resolve_missing_item_is_not_found` | ✅ |
| 失败语义：NotIncluded（不降级为 NotFound） | 同上 `#resolve_item_outside_workspace_is_not_included` | ✅ |
| AC-02 LocalId 稳定且可定位同一变体 | 同上 `#AC02_local_id_is_stable_and_locates_the_same_variant` | ✅ |
| AC-01 编译依赖（sk 不依赖 alm） | `architecture.ArchitectureTest#working_set_must_not_depend_on_alm`（ArchUnit） | ✅ |
| LightTrackerItem 不变量与状态切换 | `sk.workingset.domain.LightTrackerItemTest`（6 项） | ✅ |

## 3. 实现位置

| 层 | 包 | 内容 |
| --- | --- | --- |
| SK 领域 | `sk.workingset.domain` | `LocalId`、`CommonId`、`Revision`、`Version`、`TrackerId`、`WorkingSetId`、`TrackerItem`、`Tracker`、`LightTrackerItem` |
| SK 应用 | `sk.workingset.application` + `port` | `WorkingSetApplicationService`（derive/resolve/read）、`TrackerItemReadPort`、`LightTrackerItemRepository`、`CapabilitySet`、`Operation`、`Caller`、`TrackerItemContent`、`FailureReason`、`ApplicationFailure`、`DeriveCommand/Result`、`ReadResult` |
| SK 基础 | `sk.workingset.infrastructure` | `InMemoryLightTrackerItemRepository` |
| ALM | `alm.requirement.{domain,application,infrastructure}` | `Requirement`（实现 `TrackerItem`，`RequirementId = LocalId`）、`RequirementRepository`、`RequirementApplicationService`、`InMemoryRequirementRepository`、`RequirementAdapter` |
| 组合根 | `bootstrap.configuration` | `ApplicationConfiguration`（Spring `@Configuration`，唯一同时引用两侧的位置） |

## 4. 与设计的偏离（已记录，不改变契约）

| 项 | 偏离 | 原因 |
| --- | --- | --- |
| P2 框架 | S1 引入 `spring-context`（非 Spring Boot starter），bootstrap 用 `@Configuration` 装配，测试用纯 Java 装配 | 避免 S1 引入容器启动与 Web 依赖；S2/S3 需要时再升级 |
| 历史回读 | `RequirementAdapter.readAt` 在版次不匹配时抛 `IllegalStateException` | S1 不实现历史；不用当前值冒充，`HistoryUnavailable` 留后续切片 |
| Tracker 持久化 | `derive` 返回 `Tracker` 但不持久化 | S1 只需断言 `sourceId`；TrackerRepository 待 S2 需要时增加 |
| 授权 | `authorize` 恒允许 | 无权限平台接入（P2 范围外） |

## 4.1 S2 前置工程决定（用户 2026-11-11）

| 决定 | 内容 | 对 S1 的影响 |
| --- | --- | --- |
| 引入事件框架 | **Spring Event**（`ApplicationEventPublisher` 发布；S3 才加 `@EventListener` 消费） | S1 无事件代码，无需回改 |
| 引入雪花 ID | `IdentifierGenerator` + `SnowflakeIdGenerator` + `IdGeneratorConfig`；**替换 S1 的 UUID `LocalId.newId()`** | S1 的 `LocalId.newId()` 需在 S2 改为生成器（观察项 #1） |
| 升级 Spring Boot | 由 `spring-context` 升级为 Spring Boot（BOM + starter，非 web） | S1 的 `@Configuration` 保留；升级后须回归 12 项测试 |
| 单体部署 | `IdGeneratorConfig` 用 `@Configuration` + 固定 `DATACENTER_ID=1`、workerId 由本机地址哈希取模 | 无 |

详见 [S2 任务说明](t5-s2-codex-task-brief.md)。

## 4.2 S2 前置观察项

| # | 观察项 | 处置 |
| --- | --- | --- |
| 1 | `LocalId.newId()` 使用 UUID，与 FACT-005「CommonId/LocalId 为雪花 ID」偏离 | **S2 修复**：改由 `IdentifierGenerator` 生成 |
| 2 | `RequirementAdapter.readAt` 对非当前版次抛 `IllegalStateException` | **S3 处理**：物化需按快照读取，并补 `HistoryUnavailable` 语义 |
| 3 | `read` 中授权失败抛 `UnsupportedOperationException` | 权限切片处理，应返回 T4 的 `Denied` |
| 4 | pom 使用 `spring-context` 而非 Spring Boot | **S2 修复**：升级 Spring Boot，保留非 web |

## 5. 未做（后续任务）

事件与快照（S2）、首次 COW 与物化切换（S3）、一致性恢复与回归（S4）、Merge/删除/权限/版本治理。

## 6. 复现

```text
mvn -B -DskipTests compile   # 28 个主源文件
mvn -B test                  # 12 个测试
```
