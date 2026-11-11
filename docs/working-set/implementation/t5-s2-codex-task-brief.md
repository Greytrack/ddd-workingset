# T5-S2 Codex 任务说明：来源修改、快照与事件

**状态：Ready for implementation（实施任务说明；不含生产代码）**

- 交付对象：Codex（实施）
- 日期：2026-11-11
- 上级依据：[T5-S0 设计](t5-s0-minimal-slice-design.md)（P1～P5、A1～A3）、[S1～S4 任务清单](t5-s0-codex-task-plan.md) S2、[场景-测试映射](t5-s0-scenario-test-mapping.md) AT-03、T3（物化）、T4（Port/事件信封）、P4
- 前置状态：**S1 已完成并通过审查**（`mvn test` 12 项通过，`t5-s1-verification-result.md`）
- 本轮工程决定（用户 2026-11-11）：**升级 Spring Boot**、**事件框架用 Spring Event**、**雪花 ID 生成器替换 UUID**

## 1. 目标

完成「来源 Requirement 普通修改 → 修改前不可变快照 → 来源事件发布」的闭环，为 AT-03 的全链路准备可验证证据；同时落地三项工程前置（Spring Boot、Spring Event、雪花 ID）。

**不实现**：COW Handler、目标物化、`switchToLocalBacked` 调用、Local-backed Query（属 S3）。

## 2. 允许修改的范围（白名单）

```
pom.xml
src/main/java/bootstrap/**                          # 主类、IdGeneratorConfig、配置调整
src/main/java/sk/workingset/application/port/IdentifierGenerator.java   # 新增接口
src/main/java/sk/workingset/infrastructure/SnowflakeIdGenerator.java    # 新增实现
src/main/java/sk/workingset/application/WorkingSetApplicationService.java  # 注入生成器
src/main/java/sk/workingset/domain/LocalId.java      # 移除/替换 newId()
src/main/java/sk/workingset/application/event/**     # RequirementChanged、SnapshotRef（SK 定义公共信封）
src/main/java/alm/requirement/domain/Requirement.java                  # changeTitle + version/lock_version
src/main/java/alm/requirement/application/RequirementApplicationService.java  # 修改编排
src/main/java/alm/requirement/infrastructure/**      # SnapshotStore、内存实现
src/test/java/**
```

**禁止**：引入数据库、消息中间件、微服务/服务发现、动态插件系统；`sk → alm` 编译依赖；修改 T1～T4 契约；实现 S3 的 COW 逻辑。

## 3. 三项工程前置（本轮必须落地）

### 3.1 升级 Spring Boot

- `pom.xml` 引入 Spring Boot BOM（`spring-boot-dependencies`）与 `spring-boot-starter`（**非 web**，避免启动容器）；保留 Java 21。
- 新增 `bootstrap` 主类（`@SpringBootApplication`），`bootstrap.configuration` 保持组合根职责。
- 领域层仍为纯 Java（不依赖 Spring），A1 不变。
- `mvn test` 在升级后仍须 12 项全绿（回归基线）。

### 3.2 雪花 ID 生成器（替换 UUID）

- 新增 `IdentifierGenerator` 接口（SK 应用层 Port）：`long nextId()`（或等价最小方法）。
- 新增 `SnowflakeIdGenerator` 实现：按用户给定算法（`START_STAMP`、`DATACENTER_BIT=3`、`MACHINE_BIT=3`、`SEQUENCE_BIT=5`、时钟回拨检测、`synchronized`）。
  - **移除外部依赖**：不使用 `@Slf4j`/`LogModuleTypeConstant`/`AppException`/`GlobalResultCode`/`SpringContextUtils`/静态 `INSTANCE` holder/MyBatis OGNL 入口（本仓库无这些设施）；日志用 SLF4J API 或省略，异常用 `IllegalStateException`。
- `IdGeneratorConfig`（`bootstrap.configuration`）：`@Configuration` + `@Bean IdentifierGenerator`；`DATACENTER_ID=1`，workerId 由本机地址哈希取模（用户给定逻辑）。
- `LocalId` 改由生成器产生（`String.valueOf(generator.nextId())`）：删除 `LocalId.newId()` 的 UUID 实现；`WorkingSetApplicationService` 注入 `IdentifierGenerator`。
- 与 FACT-005（CommonId/LocalId 为雪花 ID）一致。

### 3.3 Spring Event 作为事件框架

- 事件类型 `RequirementChanged`（SK 定义公共信封，不可变）：`eventId`（雪花 ID）、`requirementId`、`sourceLocalId`、`revision`、`snapshotRef`、`occurredAt`。
- 发布：ALM 应用服务注入 `ApplicationEventPublisher`，保存成功后 `publishEvent(...)`。
- **S2 不实现消费**（S3 才加 `@EventListener` 的 COW Handler）。
- 顺序语义记录：Spring Event 默认同线程同步派发 → 满足 D-018 顺序消费前提；**若后续引入 `@Async`，必须显式排序**（S3 约束）。

## 4. 执行步骤（Red → Green）

| # | 行为 | 最小产出 |
| --- | --- | --- |
| 1 | Spring Boot 升级 | BOM + starter + 主类；`mvn test` 12 绿回归 |
| 2 | 雪花 ID 基础设施 | `IdentifierGenerator` + `SnowflakeIdGenerator` + `IdGeneratorConfig`；唯一性测试（同毫秒多 ID 不重复） |
| 3 | LocalId 接入生成器 | `WorkingSetApplicationService.derive` 用生成器预分配；删除 `LocalId.newId()` 的 UUID 路径 |
| 4 | `Requirement.changeTitle` | 不可变聚合返回新实例；`lock_version` 自动 +1；`revision` 不变；`version`（发布大版本）不变；以 `expectedLockVersion` 做乐观锁校验 |
| 5 | 修改前快照 | `SnapshotRef`（SK 定义）+ `SnapshotStore`（ALM 内存实现）：保存前捕获完整内容（title/description/revision/version/lock_version） |
| 6 | 事件发布 | `RequirementChanged` + `ApplicationEventPublisher.publishEvent`（保存成功后） |
| 7 | 测试 | AT-03 前半 + 事件捕获 + 雪花 ID 唯一性 |

## 5. 测试与验收

**命令**：`mvn test`（或 `mvn verify`）。

| 测试 | 断言要点 |
| --- | --- |
| Spring Boot 基线 | 升级后原 12 项仍全绿；`ApplicationContext` 可加载（`@SpringBootTest` 或显式上下文测试） |
| 雪花 ID | 连续生成的 ID 唯一；`eventId`、新 `localId` 均为雪花值（非 UUID 格式） |
| AT-03 前半 | 修改 `title A→B` 后 `revision` 仍为 3、`lock_version` 递增、`version` 保持为空；快照内容为 `A`（修改前） |
| 事件 | 发布 1 条 `RequirementChanged`，载荷含 `eventId/requirementId/sourceLocalId/revision/snapshotRef/occurredAt`；顺序可观察 |
| 并发证据 | 以错误的 `expectedLockVersion` 修改 → 拒绝（`Rejected`/冲突语义），不得静默覆盖 |

## 6. 完成定义（DoD）

1. `mvn test` 全绿（原 12 项 + S2 新增测试）；
2. Spring Boot 上下文可加载，领域层无 Spring 依赖（AC-01 仍通过）；
3. `LocalId` 由雪花生成器产生，仓库内无 UUID `newId()` 路径；
4. 事件信封字段完整、`eventId` 为雪花 ID、发布发生在保存成功之后；
5. 未引入 COW Handler / `switchToLocalBacked` 调用 / Local-backed Query；
6. 在 `implementation/` 追加「S2 验证结果」记录并回链本说明与 AT-03。

## 7. 排除范围

COW Handler 与物化切换、Local-backed Query、Merge、绑定/删除、权限、版本治理、生产持久化与消息中间件。

## 8. 观察项与风险（S2 期间记录，不擅自改契约）

| 编号 | 项 | 处理 |
| --- | --- | --- |
| O1 | 雪花位宽 `3+3+5` → 单机每毫秒 32 ID（约 32k ID/s） | 按用户给定参数实现；若压测不足再评估位宽，不擅自修改算法 |
| O2 | Spring Event 默认同步顺序 | 作为 S3 消费顺序前提记录；引入异步必须显式排序 |
| O3 | `RequirementAdapter.readAt` 对非当前版次抛异常（S1 遗留） | **S3** 需改为按快照读取；S2 不改 |
| O4 | `authorize` 恒 true（S1 遗留） | 权限切片再处理；S2 不改 |
| O5 | Spring Boot 依赖下载可用性 | 若离线/镜像缺失，回退到 `spring-context` 并记录偏离，不阻塞 S2 业务行为 |

## 9. 遇到设计缺口时

- 若发现需要新的业务裁定（如快照内容范围、事件顺序语义冲突），**停止编码**并在设计层标注 `Proposed`；
- 不得修改 T1～T4 已冻结契约，不得在代码中暗补领域概念。
