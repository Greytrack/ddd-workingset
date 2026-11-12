# T5-S3 首次 COW 与状态切换

**状态：Reviewed。实施证据见 [S3 验证结果](t5-s3-verification-result.md)。**

实施依据：[运行时验证](t5-runtime-verification.md)、[首切片设计](t5-s0-minimal-slice-design.md)、[场景映射](t5-s0-scenario-test-mapping.md)、[T3](../domain/lighttrackeritem-materialization-contract-v1.md)、[T4](../domain/cross-bc-contract-v1.md)。实施前 `mvn -B test` 基线为 37 项通过；实施后 **46 项通过**，证据见 [S3 验证结果](t5-s3-verification-result.md)。以下裁定保留决策背景；当前实现以验证结果为准。仅实施普通字段，附件与关系仍遵循 T3 §2.1。

## 1. 当前运行时事实（不要重新发明）
- Spring Boot 3.3.5 Web + JDBC + Redis + Spring Modulith（starter-jdbc、events-jdbc）+ PostgreSQL。
- RequirementApplicationService.changeTitle 已标注 @Transactional；业务保存、快照写入、Modulith event_publication 记录在同一事务提交/回滚。
- RequirementChangedListener 已使用 @ApplicationModuleListener，组合 @TransactionalEventListener(AFTER_COMMIT) + @Async + 新事务；监听器成功后 Modulith 标记 completion_date，抛异常则保留未完成，可重启重提。
- 事件表 event_publication 由 Modulith 自动建/管理；不要手工改表结构，不要在 schema.sql 里重复创建。
- schema.sql 已建 requirements、light_tracker_items、requirement_snapshots。
- JdbcRequirementRepository 使用 lock_version 条件 upsert；0 行受影响抛 LockVersionConflictException。
- CachingLightTrackerItemRepository 已在 S3 改为事务提交后写缓存；findBySource 不缓存。
- REST 已有 /api/requirements、/api/worksets/{ws}/derive、/api/worksets/{ws}/items/{id}/content、/api/health。

## 2. 目标
来源 Requirement 普通修改提交后，事件由 Modulith 异步投递；COW 处理器按来源顺序定位未物化目标，用修改前快照构建目标业务聚合并保存，成功后一次切换 LightTrackerItem 三字段；失败可观察、可恢复，不伪造成完成。

验收：
- AT-03：来源 A→B 后，异步完成后目标内容为修改前 A，不是 B。
- AT-04：目标 Local-backed 后，来源再改 C，目标读取仍为 A，来源读取为 C。
- 首次快照优先：连续 A→B→C，目标最终为 A。
- 扇出：同一来源的多个未物化目标都物化；一个失败不影响其他。
- 失败：快照缺失、来源身份/commonId 不符、已有目标证据不一致、切换失败，均不切换、不覆盖，并留下可查询失败记录；事件保持未完成。
- 缓存：事务回滚不留下“已物化”脏缓存。

## 3. 事件模型（已实施）
RequirementChanged 改为：

public record RequirementChanged(
    long eventId,
    LocalId requirementId,
    LocalId sourceLocalId,
    Revision beforeRevision,
    Revision afterRevision,
    LockVersion beforeLockVersion,
    LockVersion afterLockVersion,
    SnapshotRef snapshotRef,
    Instant occurredAt) {}

- 普通修改：beforeRevision == afterRevision；afterLockVersion = beforeLockVersion + 1。
- 显式升级：两个 Revision 可以不同，但 afterLockVersion 仍单调。
- 发布端 RequirementApplicationService.changeTitle 使用：
  current.revision() / changed.revision()
  current.lockVersion() / changed.lockVersion()
- 删除旧 revision 字段。同步更新 RequirementChangeFlowTest、ApplicationContextTest 中的断言。
- 修改事件字段前，检查本地测试库是否存在待重提的旧格式事件。此次查询结果为 0，未执行删除。其他环境须单独确认旧格式事件的处置方案。

## 4. 物化请求与写入 Port（应用层，纯 Java）
sk.workingset.application.port.MaterializeRequest：

public record MaterializeRequest(
    LocalId targetLocalId,
    TrackerId targetTrackerId,
    CommonId commonId,
    LocalId sourceLocalId,
    Revision beforeRevision,
    Revision afterRevision,
    LockVersion beforeLockVersion,
    LockVersion afterLockVersion,
    Revision targetRevision,
    long eventId,
    SnapshotRef snapshotRef) {}

sk.workingset.application.port.RequirementWritePort：

public interface RequirementWritePort {
    void materialize(MaterializeRequest request);
}

sk.workingset.application.port.MaterializationRejectedException extends RuntimeException：
只表达“拒绝物化”，不建立九分类异常层次。

sk.workingset.application.port.TargetTransactionRunner：

public interface TargetTransactionRunner {
    void inNewTransaction(Runnable work);
}

用途：让 SK 应用层在不依赖 Spring 事务 API 的前提下，把 LTI 切换放进独立事务。

## 5. ALM 物化 Adapter（独立事务提交业务保存）
新增 alm.requirement.infrastructure.RequirementMaterializationAdapter，实现 RequirementWritePort：

构造：RequirementRepository repository, SnapshotStore snapshotStore, TransactionTemplate newTransactionTemplate
（newTransactionTemplate 的 propagation = REQUIRES_NEW）

materialize(request) 顺序：
1) snapshotStore.find(request.snapshotRef())；缺失 -> 抛 MaterializationRejectedException。
2) snapshot.localId().equals(request.sourceLocalId())；不等 -> 拒绝。
3) snapshot.revision().equals(request.beforeRevision())；不等 -> 拒绝。
4) snapshot.lockVersion().equals(request.beforeLockVersion())；不等 -> 拒绝。
5) snapshot.commonId().equals(request.commonId())；不等 -> 拒绝。
6) 在 newTransactionTemplate.execute(...) 内：
   a) repository.findById(request.targetLocalId())
   b) 已存在：校验 existing.commonId、trackerId、revision、title、description、version 与本次证据一致；
      全部一致 -> 正常返回（视为同一次有效物化的重复执行，允许后续切换）；
      任一不一致 -> 抛 MaterializationRejectedException，不覆盖。
   c) 不存在：Requirement.create(
          targetLocalId,
          request.commonId(),
          request.targetTrackerId(),
          snapshot.title(),
          snapshot.description(),
          request.targetRevision(),
          snapshot.version())
      然后 repository.save(target)。
7) 不用来源当前值补齐；不用 afterRevision 覆盖目标 revision；lock_version 由 Requirement.create 从 0 开始。

不要修改 RequirementAdapter 的读取职责与构造绑定。

## 6. 同源顺序协调器（应用层，不拥有线程池）
新增 sk.workingset.application.SourceSerialCowCoordinator：

public final class SourceSerialCowCoordinator {
    public void runSerial(LocalId sourceLocalId, long sequence, Runnable task) { ... }
}

要求：
- 每个 sourceLocalId 一个串行队列；不同来源可并行。
- 队列按 sequence（即 afterLockVersion）升序执行；同一来源同时只有一个任务在执行。
- 调用线程等待自己的任务完成（CompletableFuture），异常向调用方传播；这样 Modulith 的 completion_date 与物化完成一致。
- 不创建线程池、不使用 @Async；异步由 Modulith 提供。
- 记录顺序缺口：若当前 sequence 大于该来源已执行的最大 sequence + 1，用 SLF4J WARN 记录 ORDER_GAP（sourceLocalId、expected、actual）。S3 不实现等待缺失序号的完整缓冲。
- 队列空闲时从 map 移除，避免内存泄漏。

S3 的保证边界：
- 单实例、顺序投递下同源有序，首次快照优先成立。
- 并发同源 save/publish 交错、跨实例/多实例顺序，明确留 S4；不要在 S3 宣称。

## 7. CowEventHandler（应用层，纯 Java）
新增 sk.workingset.application.CowEventHandler：

构造：LightTrackerItemRepository items,
      RequirementWritePort writePort,
      CowFailureLog failureLog,
      TargetTransactionRunner transactionRunner

onRequirementChanged(RequirementChanged event)：
1) items.findBySource(event.sourceLocalId(), event.beforeRevision())，过滤 materialized == false。
2) 对每个目标独立 try/catch：
   - 构造 MaterializeRequest（target.localId/trackerId/commonId、sourceLocalId、
     beforeRevision/afterRevision、beforeLockVersion/afterLockVersion、
     target.revision()、event.eventId()、event.snapshotRef()）。
   - writePort.materialize(request)。
   - transactionRunner.inNewTransaction(() -> {
         target.switchToLocalBacked(target.revision());
         items.save(target);
     });
   - 成功继续下一个目标。
   - RuntimeException：failureLog.record(new Failure(... stage=MATERIALIZE 或 SWITCH,
         errorType, message, Instant.now()))，收集失败，继续下一个目标。
3) 扇出结束后若存在失败，抛出 CowMaterializationFailedException(event.eventId(), failures)；
   让 Modulith 保留未完成记录。不要吞掉异常只打日志。
4) 已 Local-backed 的目标直接跳过（INV-038/039）。
5) 不直接引用 RequirementRepository/Requirement；只经 RequirementWritePort（ArchUnit 必须继续通过）。

新增 sk.workingset.application.CowFailureLog 接口：

public interface CowFailureLog {
    void record(Failure failure);
    java.util.List<Failure> findBySource(LocalId sourceLocalId);

    record Failure(long eventId, LocalId targetLocalId, LocalId sourceLocalId,
                   Revision beforeRevision, Revision afterRevision,
                   String stage, String errorType, String message,
                   java.time.Instant occurredAt) {}
}

新增 CowMaterializationFailedException（应用层，携带 eventId 与失败列表）。

## 8. 监听器与组合根
bootstrap.RequirementChangedListener 改为：

@Component
public class RequirementChangedListener {
    private final SourceSerialCowCoordinator coordinator;
    private final CowEventHandler handler;

    @ApplicationModuleListener
    public void on(RequirementChanged event) {
        coordinator.runSerial(event.sourceLocalId(), event.afterLockVersion().value(),
            () -> handler.onRequirementChanged(event));
    }
}

- 保留 @ApplicationModuleListener，不改成普通 @EventListener，不自建执行器。
- 不捕获异常；异常让 Modulith 保留未完成记录。
- 监听器不再只记日志。

bootstrap.configuration.ApplicationConfiguration 增加：
- TransactionTemplate newTransactionTemplate(PlatformTransactionManager)，
  propagation = PROPAGATION_REQUIRES_NEW。
- RequirementMaterializationAdapter bean（RequirementWritePort）。
- JdbcCowFailureLog bean（JdbcTemplate + TransactionTemplate；内部 REQUIRES_NEW）。
- SpringTargetTransactionRunner bean（实现 TargetTransactionRunner，内部 REQUIRES_NEW）。
- SourceSerialCowCoordinator bean。
- CowEventHandler bean。
- 保留现有 JDBC 仓储、缓存装饰器、RequirementApplicationService、WorkingSetApplicationService 装配。

新增 bootstrap.configuration.SpringTargetTransactionRunner（或放在 bootstrap 下）：
  inNewTransaction(work) 用 REQUIRES_NEW TransactionTemplate 执行。

新增 sk.workingset.infrastructure.JdbcCowFailureLog：
  写 cow_failures 表；record 使用 REQUIRES_NEW 事务，确保外层失败回滚不影响失败记录。

## 9. 数据库
在 schema.sql 增加（IF NOT EXISTS，PostgreSQL）：

CREATE TABLE IF NOT EXISTS cow_failures (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT       NOT NULL,
    target_local_id VARCHAR(64)  NOT NULL,
    source_local_id VARCHAR(64)  NOT NULL,
    before_revision INTEGER      NOT NULL,
    after_revision  INTEGER      NOT NULL,
    stage           VARCHAR(32)  NOT NULL,
    error_type      VARCHAR(255) NOT NULL,
    message         TEXT,
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cow_failures_source
    ON cow_failures (source_local_id);
CREATE INDEX IF NOT EXISTS idx_cow_failures_target
    ON cow_failures (target_local_id);

不要改 requirements / light_tracker_items / requirement_snapshots 的结构。
不要手工创建 event_publication，也不要改它的结构。
light_tracker_items 的乐观锁留 S4，不在 S3 加。

## 10. 缓存一致性（已修正）
修改 CachingLightTrackerItemRepository：
- save(item)：delegate.save(item) 后，如果 TransactionSynchronizationManager.isSynchronizationActive()，
  注册 afterCommit 回调写缓存；否则立即写缓存。
- findById(localId)：命中直接返回；未命中查 DB；只有在没有活动事务时才回填缓存。
- 目标：切换事务回滚时 Redis 不会留下 materialized=true；切换提交后读取能立即看到本地内容。
- 更新 CachingLightTrackerItemRepositoryTest，覆盖“save 后读取命中缓存”和“写后更新”；
  如能构造事务回滚，增加“回滚后缓存不出现已物化状态”的断言。

## 11. 测试计划与实施结果（真实 PG/Redis/Tomcat）
等待一律用 Awaitility（已随 spring-boot-starter-test 提供），不要用 Thread.sleep。S3 实施与收尾均通过 46 项测试，见 [S3 验证结果](t5-s3-verification-result.md)。

A. 应用层测试（不启动 Web）
1) CowEventHandlerTest：
   - 快照缺失：拒绝，目标 Source-backed，cow_failures 有 MATERIALIZE 记录。
   - 快照来源 LocalId / beforeRevision / beforeLockVersion / commonId 任一不匹配：拒绝且不切换。
   - 既有目标内容/身份与快照不一致：拒绝且不覆盖；一致：允许切换。
   - 切换失败（注入 LightTrackerItemRepository.save 抛异常）：业务内容存在，重新读取 LTI 仍 Source-backed，
     cow_failures 有 SWITCH 记录，Handler 抛出 CowMaterializationFailedException。
   - 扇出：两个目标，一个失败，另一个成功；失败不影响成功。
   - 已 Local-backed 目标跳过，不重复保存。
2) SourceSerialCowCoordinatorTest：
   - 同一 source 的任务串行；不同 source 可并行；
   - 按 afterLockVersion 升序执行；
   - 任务异常向调用方传播，后续任务仍可执行；
   - ORDER_GAP 记录。
3) 事件字段测试：RequirementChangeFlowTest 改为断言 before/after Revision 与 before/after LockVersion；
   ApplicationContextTest 同步。
4) WorkingSetSliceIntegrationTest：
   - 应用层使用 Mockito 仓储端口与真实读 Adapter，AC-02 按 LocalId 断言身份。

B. HTTP 验收（@SpringBootTest(webEnvironment=RANDOM_PORT)）
新增 CowAcceptanceTest：
1) AT03_异步最终状态：
   - POST /api/requirements 创建来源（title=A，revision=3，UUID 化 commonId/trackerId）；
   - POST /api/worksets/WS1/derive 派生目标；
   - PUT /api/requirements/{source}/title 改为 B，断言 200、revision 仍 3、lockVersion=1；
   - await 直到 GET /api/worksets/WS1/items/{target}/content 返回 materialized=true；
   - 断言目标 title=A；GET /api/requirements/{source} 的 title=B。
   （不要断言 HTTP 返回瞬间未物化，Modulith 异步太快，窗口断言放应用层测试。）
2) AT04_Local-backed_独立：
   - 再 PUT 来源为 C；await 后 GET 目标 content 仍 A；GET 来源为 C。
3) fanout：同一来源派生到 WS1/WS2，修改一次，await 两个目标都 materialized=true。
4) sequence_first_snapshot：
   - 顺序 PUT A→B、B→C（等待 lockVersion 递增）；
   - await 后目标内容为 A。
5) 事件完成证据：
   - 查询 event_publication，断言本次事件 completion_date 非空；
   - 失败场景（用应用层测试制造）断言 completion_date 为空，但 S3 不实现自动重试。

C. 缓存测试
- 更新 CachingLightTrackerItemRepositoryTest，验证 save/read/写后更新与事务回滚边界。

D. 回归
- RequirementAcceptanceTest 继续通过；它现在会触发 COW（无目标时 no-op），不得因此失败。
- ApplicationContextTest 的事件字段断言更新。
- ArchitectureTest 继续通过（sk.workingset.. 不依赖 alm.requirement..）。

测试数据隔离：
- 所有 ID 用 UUID 前缀；@AfterEach 清理对应 requirements、light_tracker_items、requirement_snapshots、cow_failures；
- 按 serialized_event LIKE '%localId%' 清理 event_publication；
- 清理 Redis key workingset:lti:{localId}。
- 不要删全表，避免影响并行测试。

## 12. 实施步骤（已完成）
1) 基线：mvn -B test 37 项通过；记录当前输出。
2) 已按运行时事实落地 Modulith 异步 + `SourceSerialCowCoordinator`、`REQUIRES_NEW` 事务拓扑、`cow_failures`、事件 lockVersion 字段与缓存 afterCommit；本文件保留为实施契约。
3) 事件模型拆分 + 发布端 + 既有断言更新。
4) schema.sql 加 cow_failures；JdbcCowFailureLog。
5) MaterializeRequest、RequirementWritePort、MaterializationRejectedException、TargetTransactionRunner。
6) RequirementMaterializationAdapter（快照核验、已有目标校验、REQUIRES_NEW）。
7) SourceSerialCowCoordinator（按来源串行/按 lockVersion 排序）。
8) CowEventHandler（扇出、失败记录、抛出）。
9) Listener 接入 + ApplicationConfiguration 装配。
10) 缓存 afterCommit 修正。
11) 测试 A/B/C/D 全量补齐。
12) 文档：t5-runtime-verification.md（S3 已接入、R7 更新）、BASELINE.md、implementation/README.md、阶段 5 计划。
13) 新增 t5-s3-verification-result.md，回链 AT-03/AT-04/AT-05 前置、INV/SCN，并如实记录未验证能力。

## 13. 完成定义（DoD）
- mvn -B test 全绿（基线 37 + 新增）。
- mvn -B spring-boot:run 可启动 Tomcat；/api/health 返回 database=UP、redis=UP。
- AT-03/AT-04 以异步最终状态通过；连续修改首次快照 A 生效。
- 目标业务保存成功、LTI 切换失败时：业务内容保留、LTI 仍 Source-backed、cow_failures 有 SWITCH 记录、event_publication completion_date 为空。
- 快照缺失/来源身份不符/commonId 不符/既有目标证据不一致：拒绝、不覆盖、有失败记录。
- 扇出单目标失败不影响其他目标。
- 缓存：切换提交后读取立即返回本地内容；回滚不留下已物化缓存。
- ArchUnit 通过；SK 不依赖 ALM；Handler 只经 RequirementWritePort。
- 事件字段 before/after 与 lockVersion 在发布端、消费端、测试断言一致。
- 未实现：自动重试 daemon、去重、跨实例顺序、并发同源交错、Revision 升级端到端、Merge/删除/权限。
- 内存持久化实现已删除，应用层使用 Mockito，快照验证使用真实 JDBC。收尾及回归见 [S3 验证结果](t5-s3-verification-result.md)，恢复见 [S4 验证结果](t5-s4-verification-result.md)。

## 14. 禁止事项
- 不引入 Kafka/RabbitMQ/Redis Stream/自建 outbox/分布式事务/通用 Saga。
- 不手工改 event_publication 结构；不把 event_publication 当业务聚合。
- 不用 @Async 自定义执行器替代 Modulith；不自建线程池冒充异步投递。
- 不用 Thread.sleep 作为异步测试正确性依据。
- 不把“捕获异常 + 日志”当失败处理；必须有 cow_failures 与 Modulith 未完成记录。
- 不修改 T1～T4 已冻结契约；不新增领域聚合/状态机。
- 不实现 S4 的恢复、去重、跨实例顺序；不宣称 S3 具备这些能力。
- 不执行 Git Commit/Tag，不发布基线。
