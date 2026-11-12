# T5-S3 Codex 任务说明：首次 COW 与状态切换

**状态：Ready for implementation（2026-11-12 审查修订，采用异步事件投递；本文件不含生产代码）**

- 交付对象：Codex（实施）
- 日期：2026-11-11（初稿）／2026-11-12（按两轮审查合并结论 + 异步投递裁定修订）
- 上级依据：[T5-S0 设计](t5-s0-minimal-slice-design.md) §5/§7/§8/§9/§10/§11、[S1～S4 任务清单](t5-s0-codex-task-plan.md) S3、[场景-测试映射](t5-s0-scenario-test-mapping.md) AT-03/AT-04、T3 §4～§7、T4 §3.1～§3.3/§5、D-009～D-013/D-017/D-018、INV-037/038/039/044/050/051/052/069～071、SCN-025/030/032/067
- 前置状态：**S2 已完成并 Verified**（`mvn test` 35 项通过，[S2 验证结果](t5-s2-verification-result.md)）
- 已完成裁定（2026-11-11，继续有效）：
  1. 目标聚合版本初始化：`lock_version = 0`（目标对象独立），`version` 继承快照的发布大版本；
  2. 派生侧失败语义：Handler 按目标捕获并写入可查询失败记录，不向来源 `changeTitle` 抛出（异步投递下命令不等待 COW）；
  3. 写入协作采用 SK 定义的无状态写入 Port，方法携带完整目标身份。

## 0. 本轮修订（2026-11-12，用户正式裁定）

| 编号 | 裁定 | 对实施的含义 |
| --- | --- | --- |
| **A** | 内存 Repository 必须做实例隔离，采用 `LightTrackerItem.restore(...)` 复制；**不允许**只在测试 Stub 中复制来掩盖真实仓储问题 | `switchToLocalBacked` 后 `save` 失败，仓储中已提交状态不变；重新读取仍为 Source-backed |
| **B** | 新增独立 `RequirementMaterializationAdapter`：实现 `RequirementWritePort`、**无 LocalId 构造绑定**、依赖 `RequirementRepository` + `SnapshotStore`；`RequirementAdapter` 保留读取职责与原构造方式；组合根分别装配读/写 Adapter | 消除“一个 Adapter 同时承载读绑定与无状态写”的冲突；白名单同步扩充 |
| **C** | 物化请求必须携带 `sourceLocalId` 与 `sourceRevision`，并与 `targetRevision` 明确区分；快照必须验证属于本次事件指定来源；**不得**因 `commonId` 与 Revision 相同就假定两张快照来自同一对象 | 用 `MaterializeRequest` record 承载；Adapter 校验快照来源身份；不使用来源 Revision 覆盖目标自身 Revision |
| **D** | 不得把“Revision 升级事件不触发 COW”当作新规则（D-018/INV-052 已确认升级必须触发 COW）。事件明确区分 `beforeRevision`（修改前）与 `afterRevision`（提交后）：**目标匹配与快照核验用 `beforeRevision`，目标物化用 `target.revision`**；普通修改两者相等，显式升级可能不同 | S3 先完成普通修改闭环；升级型端到端路径登记为缺口，但事件模型现在就给出 before/after 表达，避免把修改后 Revision 错用于通用目标查找 |
| **E** | `RequirementWritePort` 在 S3 允许返回 `void`，但正常返回必须表示 BC 已确认**有效物化写入**；“目标已存在”不能直接当成功，必须验证属于同一次有效物化，否则拒绝切换；完整幂等身份与跨聚合恢复由 S4 负责；并发重复投递不属于 S3 已验证能力；不引入通用幂等框架 | Adapter 增加“已存在目标”的证据一致性校验；Handler 只在正常返回后切换 |
| **F** | **S3 采用异步事件投递**（用户 2026-11-12 裁定，优先于此前“同步 Spring Event”的实施建议）：来源 Command 保存成功后即结束，COW 在独立执行上下文消费；同源事件按来源串行；失败写入可查询记录；可靠投递、跨进程顺序与恢复留 S4 | 监听器只入队；`CowEventDispatcher` + 可注入执行器；`CowFailureLog` 记录失败；不以 `@Async`、线程池或日志冒充可靠投递；不实现同步/异步动态切换框架 |

## 1. 目标

完成「来源普通修改命令成功返回 → 事件异步进入消费 → 在独立执行上下文中定位未物化目标 → 以修改前快照构建目标业务聚合 → 保存成功 → LightTrackerItem 三字段切换」的闭环，使 AT-03/AT-04 以**异步最终状态**验收，并为 S4 的恢复路径留下可观察的失败依据。

**范围界定**：S3 是**正常首次 COW 闭环**，采用用户裁定的异步投递策略；命令成功不代表 COW 完成，读取在最终一致性窗口内仍走 Source-backed。跨聚合失败恢复、并发重复事件幂等、可靠投递与跨进程顺序由 S4 负责。不得把 S3 的普通修改闭环表述为覆盖升级型事件的通用契约。

**不实现**：可靠事件投递增强（重试/死信/去重；运行时已有 Spring Modulith `event_publication` 持久化）、切换失败后的幂等恢复（S4）、Revision 升级型事件的端到端物化（S4 前置）、Merge、删除、权限、版本治理、生产持久化、并发重复投递保证、同步/异步动态切换框架。

## 2. 允许修改的范围（白名单）

```text
src/main/java/sk/workingset/application/event/RequirementChanged.java                    # revision → beforeRevision + afterRevision
src/main/java/sk/workingset/application/port/MaterializeRequest.java                     # 新增请求记录
src/main/java/sk/workingset/application/port/RequirementWritePort.java                   # 新增接口
src/main/java/sk/workingset/application/port/MaterializationRejectedException.java       # 新增拒绝类型（仅表达“拒绝物化”）
src/main/java/sk/workingset/application/CowEventHandler.java                             # 新增处理器
src/main/java/sk/workingset/application/CowEventDispatcher.java                          # 异步投递入口（应用能力）
src/main/java/sk/workingset/application/CowFailureLog.java                               # 可查询失败记录（S4 恢复依据）
src/main/java/sk/workingset/infrastructure/SerialPerSourceCowEventDispatcher.java        # 按来源串行 + 执行器
src/main/java/sk/workingset/infrastructure/InMemoryLightTrackerItemRepository.java       # 读写实例隔离（A）
src/main/java/alm/requirement/application/RequirementApplicationService.java             # 发布 before/after
src/main/java/alm/requirement/infrastructure/RequirementMaterializationAdapter.java      # 新增写入 Adapter（B）
src/main/java/bootstrap/RequirementChangedListener.java                                  # Spring Event 适配：只入队
src/main/java/bootstrap/configuration/ApplicationConfiguration.java                      # 装配 Handler、读写 Adapter、dispatcher、执行器、监听器
src/test/java/**                                                                        # 新增/更新测试（含可控执行器）
```

**白名单扩充原因（异步投递）**：`CowEventDispatcher` 隔离“入队”与“执行”，使来源命令不等待 COW；`SerialPerSourceCowEventDispatcher` 提供同源有序（按 `sourceLocalId` 串行）与可注入执行器，避免全局串行；`CowFailureLog` 让失败具有可查询结果，替代“仅记录日志”。三者均为应用/基础设施能力，不进入领域聚合，不引入 MQ、事务框架或通用 Saga。

**禁止**：引入数据库、消息中间件、事务框架、通用幂等框架；`sk → alm` 编译依赖；修改 T1～T4 已冻结契约；实现 S4 的恢复/重试/去重逻辑；实现历史回读（O3）；修改 `RequirementAdapter` 的读取职责或构造绑定。

`RequirementSnapshot` 与 `SnapshotStore` **无需新增字段**：现有 `RequirementSnapshot` 已保存 `localId`、`commonId`、`revision`、`trackerId`、`title`、`description`、`version`、`lockVersion`，足以作为“快照来源身份 + 修改前业务事实”的不可变证据（C）。

## 3. 设计与契约细化

### 3.1 事件语义：`beforeRevision` / `afterRevision`（D）

`RequirementChanged` 调整为：

```java
public record RequirementChanged(long eventId,
                                 LocalId requirementId,
                                 LocalId sourceLocalId,
                                 Revision beforeRevision,   // 修改前来源业务版次 = 快照 revision
                                 Revision afterRevision,    // 提交后来源业务版次
                                 SnapshotRef snapshotRef,
                                 Instant occurredAt) {
}
```

- **不保留旧 `revision` 字段**，避免同一版次出现两种解释；
- 普通修改：`beforeRevision == afterRevision`（S2 已验证的语义不变）；
- 显式升级（D-018/INV-052）：`afterRevision` 可以大于 `beforeRevision`，升级同样必须触发 COW 目标匹配；
- 发布端（`RequirementApplicationService.changeTitle`）以修改前聚合 `current.revision()` 作为 `beforeRevision`、以保存后的 `changed.revision()` 作为 `afterRevision`；快照仍由 `current` 捕获；
- **使用规则**：Handler 定位目标与 Adapter 核验快照一律用 `beforeRevision`；目标聚合自身业务版次一律用 `target.revision()`；**不得**用 `afterRevision` 覆盖目标 revision，也不得用它做快照核验。

**最小接口调整的性质**：本项只把 S2 的单一 `revision` 表达拆分为“修改前/提交后”，不改变任何已确认领域语义（普通修改两值相等）。S2 验证记录中的字段名作为历史证据保留，不重写其结论。

**S3 实施缺口（登记，不在本轮关闭）**：`Requirement.upgradeRevision()` 尚未实现，因此“升级事件端到端物化”没有生产路径。S3 用**直接构造的升级事件**（`beforeRevision=3`、`afterRevision=4`）验证 Handler/Adapter 的定位与核验规则，作为 S4 的前置证据。

### 3.2 `MaterializeRequest`（C）

```java
public record MaterializeRequest(LocalId targetLocalId,
                                 TrackerId targetTrackerId,
                                 CommonId commonId,
                                 LocalId sourceLocalId,      // 本次事件的来源变体
                                 Revision sourceRevision,    // = event.beforeRevision()
                                 Revision targetRevision,    // = target.revision()
                                 long eventId,               // S3 仅用于诊断，不构成幂等判定
                                 SnapshotRef snapshotRef) {
}
```

- `sourceRevision` 与 `targetRevision` **必须分开表达**，不得互相替代；
- `eventId` 放入载荷以便日志与 S4 幂等键扩展；S3 不用它做去重；
- 请求记录为不可变值类型，不引入构建器框架。

### 3.3 `RequirementWritePort`（E）

```java
public interface RequirementWritePort {
    void materialize(MaterializeRequest request);
}
```

- **正常返回**必须表示业务 BC 已确认“本次物化写入有效”：
  - 本次新建聚合并保存成功；或
  - 既有目标与本次物化证据完全一致（同一次有效物化的重复执行，见 §3.4 第 5 步）。
- **异常返回**表示未确认有效物化，Handler 不得切换状态；异常不区分“业务拒绝”与“基础设施失败”的九分类层次（T4 §5 的九分类映射留跨 BC 契约实现，S3 不建立通用异常体系）。
- 返回 `void` 是本切片的简化；T4 §3.2 的“提交后 Revision”返回项在 S3 由 `target.revision()` 与 `event.afterRevision` 提供，不新增返回类型。

### 3.4 `RequirementMaterializationAdapter`（B/C/E）

位置：`alm.requirement.infrastructure`。构造 `(RequirementRepository, SnapshotStore)`，**不绑定 `LocalId`**。`materialize(request)` 按以下顺序执行：

1. `SnapshotStore.find(request.snapshotRef())`；缺失 → 抛 `MaterializationRejectedException`。**不得**回退到来源当前值，也不得用新身份补齐（O7）。
2. **快照来源身份**：`snapshot.localId().equals(request.sourceLocalId())`；不成立 → 拒绝。不得因 `commonId` 与 Revision 相同就假定快照属于该来源（C）。
3. **修改前版次**：`snapshot.revision().equals(request.sourceRevision())`；不成立 → 拒绝。这里的 `sourceRevision` 来自 `event.beforeRevision()`，不是 `afterRevision`（D）。
4. **家族身份**：`snapshot.commonId().equals(request.commonId())`；不成立 → 拒绝。
5. **既有目标**：`repository.findById(request.targetLocalId())`
   - 不存在 → 以快照构建目标聚合：
     - `localId = targetLocalId`（派生时预分配，COW 不更换，A2/INV-040/070）
     - `commonId = request.commonId()`（继承）
     - `trackerId = request.targetTrackerId()`（目标 Tracker，不是来源 Tracker）
     - `title`/`description` = 快照内容（**不是来源当前值**，D-011/INV-037）
     - `revision = request.targetRevision()`（**目标自身版次**，不用来源 Revision 覆盖）
     - `version = snapshot.version()`（发布大版本继承快照）
     - `lock_version = 0`（`Requirement.create` 的乐观锁初值）
     - `repository.save(target)`；保存异常向上抛出。
   - 已存在 → **验证是否为同一次有效物化**，同时满足才正常返回（允许切换）：
     - `existing.commonId().equals(request.commonId())`
     - `existing.trackerId().equals(request.targetTrackerId())`
     - `existing.revision().equals(request.targetRevision())`
     - `existing.title().equals(snapshot.title())` 且 `existing.description().equals(snapshot.description())`
     - 任一不满足 → 拒绝，**不覆盖**既有内容（E：不得把“已存在”直接当成功）。
   - **不得用旧快照覆盖已有业务修改**：拒绝路径不修改既有聚合、不补写物化完成事实；只有证据一致才允许后续切换。
6. 不做“通用字段写入”或跳过目标 BC 整体校验的捷径：本切片目标聚合由 `Requirement.create` 构建，字段来自快照；后续字段扩展必须继续走业务 BC 工厂/校验。

### 3.5 `CowEventHandler` 编排（D/E）

```java
public final class CowEventHandler {
    public void onRequirementChanged(RequirementChanged event) { ... }
}
```

1. **目标定位**（消费时确定，D-017/INV-050）：
   `items.findBySource(event.sourceLocalId(), event.beforeRevision())`，过滤 `materialized == false`（已 Local-backed 的目标直接跳过，INV-038/039）。
2. 对每个目标**独立** try/catch（扇出，INV-044）：
   - 构造 `MaterializeRequest(target.localId(), target.trackerId(), target.commonId(),
     event.sourceLocalId(), event.beforeRevision(), target.revision(), event.eventId(), event.snapshotRef())`
   - `writePort.materialize(request)`
   - 正常返回后 `target.switchToLocalBacked(target.revision())`（三字段一次更新）
   - `items.save(target)`
3. **失败处理（可观察，不只日志）**：
   - 物化未正常返回：不切换；目标保持 Source-backed；不伪造物化完成。
   - 物化成功、`items.save` 失败：不回滚业务内容；目标仍未切换；不宣称物化完成（部分提交，S4 补恢复）。
   - 每个目标失败都写入 `CowFailureLog`：`eventId`、`targetLocalId`、`sourceLocalId`、`beforeRevision`、阶段（`MATERIALIZE`/`SWITCH`）、异常类型与消息、发生时间；同时以 SLF4J 记录诊断信息。
   - **不向来源 `changeTitle` 抛出**（异步投递下命令本就不等待，仍显式保留该边界）。失败记录是 S4 恢复的查询入口，不得退化为“捕获异常后仅记录日志”。
4. `switchToLocalBacked` 的参数是目标自身 `revision`；来源 `beforeRevision` 只用于定位与快照核验，不参与目标版次赋值（D）。若目标已被并发切换，`switchToLocalBacked` 的既有守卫会显式失败并被捕获，不产生第二个物化事实。

### 3.6 异步投递与执行边界（F）

**调用链**：

```text
ALM RequirementApplicationService
  → repository.save(changed)              # 来源业务保存
  → eventPublisher.publishEvent(...)      # 保存成功后发布（S3 的“提交”= 保存成功返回）
  → Spring Event（同步）→ RequirementChangedListener.on(event)
  → CowEventDispatcher.dispatch(event)    # 只入队，立即返回
  → 异步执行器（按 sourceLocalId 串行）→ CowEventHandler.onRequirementChanged(event)
```

`RequirementChangedListener` 只做投递，不执行业务：

```java
@Component
final class RequirementChangedListener {
    private final CowEventDispatcher dispatcher;

    RequirementChangedListener(CowEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    public void on(RequirementChanged event) {
        dispatcher.dispatch(event);
    }
}
```

- 监听器同步返回，**来源 Command 不等待 COW**；来源 Command 成功不代表下游 COW 已完成。
- `CowEventDispatcher` 是应用能力接口；`SerialPerSourceCowEventDispatcher` 按 `sourceLocalId` 串行执行任务，不同来源可并行，**不引入全局串行**。
- 执行器由构造注入，组合根提供有界线程池（非 web）；测试替换为可控 `ManualExecutor`（入队 + 显式 drain/有界等待），**不以固定 `Thread.sleep` 作为正确性依据**。

**“事务提交后交付”的 S3 语义**：

- S3 无事务管理器与数据库；“来源事务成功提交”在 S3 中表现为 `repository.save` 成功返回后才发布事件；
- 引入持久化后**必须**改为事务提交后触发（`TransactionSynchronization.afterCommit` 或 `@TransactionalEventListener(AFTER_COMMIT)` + 异步执行器）；
- 不得把 S3 的“保存成功后发布”表述为已具备事务提交语义。

**S2 机制复用与缺口**：

| 可复用 | 缺口（S4 或持久化任务） |
| --- | --- |
| `RequirementChanged` 公共信封与 `SnapshotStore` 修改前快照 | 运行时已有 Spring Modulith `event_publication` 自动持久化；S3 不新增 outbox，仍无重试/死信/去重 |
| `ApplicationEventPublisher` 发布点（保存成功后发布） | 无事务提交后触发（S3 无事务） |
| S2 连续修改测试的顺序证据（同线程、`eventId` 递增、快照顺序） | 无跨线程/跨进程有序投递 |
| Spring 同步事件 → 监听器只入队 | 无执行器故障隔离与事件可恢复性 |

**可靠投递边界（不夸大）**：

- S3 队列是**进程内**的：进程崩溃、执行器拒绝、未捕获异常都会导致事件不再被处理；
- 运行时已有 Spring Modulith `event_publication` 自动持久化；S3 不新增 outbox、不实现重试/死信/去重；`CowFailureLog` 只是进程内可查询记录；
- S3 只宣称“异步执行上下文 + 同源串行 + 失败可观察”，**不宣称可靠投递或跨进程顺序**；S4 必须补齐。

**同源顺序与首次快照优先（D-011/D-018）**：

- 顺序依据不是时间戳或 Spring 默认行为，而是：① 同一来源写入由 `lock_version` 串行成功；② 保存成功后发布事件；③ `CowEventDispatcher` 按 `sourceLocalId` 串行执行入队任务。
- 在上述前提下，同一来源连续修改 `A→B→C` 的事件按入队顺序处理：首次事件先物化目标（快照 A），后续事件看到 `materialized=true` 直接跳过（INV-037/038/039）。
- **S3 可验证范围**：顺序调用下的连续修改，首次快照 `A` 生效；同源任务串行、跨来源并行。
- **S3 不保证**：并发同源修改中 `save` 与 `publish` 交错（线程 1 保存 A 后未发布、线程 2 保存 B 并先发布）、跨进程/多实例顺序、事件重复投递。S4 必须以来源变更序号、事件日志或幂等键补齐；不得用普通时间戳冒充顺序。

**监听器异常边界**：监听器只入队，业务异常发生在异步执行器内；`CowEventHandler` 仍逐目标捕获 `RuntimeException` 并写入 `CowFailureLog`。该边界只覆盖物化编排内部异常，不覆盖其他监听器或 Spring 基础设施异常。

### 3.7 读取路径

`WorkingSetApplicationService.read` 已按 `materialized` 选择内容来源，S3 **不需要修改**：

- 物化后 `materialized=true`、`effectiveContentSource=localId`、`effectiveContentRevision=revision`；
- `RequirementAdapter.readAt(revision)` 读取目标本地当前内容（AT-04）；
- 历史回读（非当前 revision 的快照回读）不在 S3；O3 留后续专项。

### 3.8 内存仓储实例隔离（A）

`InMemoryLightTrackerItemRepository` 必须模拟持久化边界：

- `save(item)` 存入 `LightTrackerItem.restore(...)` 重建的副本；
- `findById` / `findBySource` 返回副本，不返回仓储内部可变实例；
- 语义后果：Handler 在副本上调用 `switchToLocalBacked` 后 `save` 失败，仓储中的对象仍为 Source-backed；
- 不使用测试 Stub 复制来绕过该问题；隔离必须在真实内存仓储实现中成立。

**连带回归**：`bootstrap.WorkingSetSliceIntegrationTest#AC02_local_id_is_stable_and_locates_the_same_variant` 目前用 `assertSame` 比较 `derive(...).item()` 与 `resolve(...)` 的返回；隔离后两者是不同实例。该断言应改为按身份断言（`assertEquals` 比较 `localId`，并验证 `materialized` 状态来自仓储），不得删除该测试。

## 4. 执行步骤（Red → Green）

| # | 行为 | 最小产出 |
| --- | --- | --- |
| 1 | 事件 before/after 表达 | `RequirementChanged` 拆分字段；发布端使用修改前/提交后版次；S2 相关断言同步 |
| 2 | 内存仓储隔离 | `save`/`find*` 复制；S1 `assertSame` 回归修正 |
| 3 | 异步投递骨架 | `CowEventDispatcher` + `SerialPerSourceCowEventDispatcher` + 可注入 `Executor`；Listener 只入队 |
| 4 | 失败可观察 | `CowFailureLog`；Handler 在 `MATERIALIZE`/`SWITCH` 失败时写入记录 |
| 5 | 目标定位 | Handler 用 `sourceLocalId + beforeRevision` 找到未物化目标；无目标不动作 |
| 6 | 请求与写入 Port | `MaterializeRequest`、`RequirementWritePort`、`MaterializationRejectedException` |
| 7 | 写入 Adapter | 快照来源身份/版次/commonId 校验；目标聚合 `lock_version=0`、`version` 继承、`revision=targetRevision` |
| 8 | 保存后切换 | Handler 调用 Port → `switchToLocalBacked` → `items.save`；三字段一次更新 |
| 9 | 保存失败不切换 | 注入 Repository 保存异常；重新读取仍 Source-backed、无本地内容、失败记录可查询 |
| 10 | 切换失败不回滚 | 注入 `items.save` 异常；目标业务内容存在、重新读取仍 Source-backed、失败记录为 `SWITCH` |
| 11 | 扇出与独立失败 | 两个未物化目标共享来源；一个失败不影响另一个成功 |
| 12 | 已存在目标证据校验 | 内容/身份一致 → 允许切换；不一致 → 拒绝且不覆盖 |
| 13 | 异步窗口 | 可控执行器未 drain 时：命令返回后目标未物化、读取走来源当前内容；drain 后完成物化 |
| 14 | 同源顺序 | 连续 `A→B` 事件入队后 drain：首次快照 `A` 生效；同源串行、跨来源并行 |
| 15 | Spring 装配 | Listener/Dispatcher/Handler/Port/Executor 可装配；失败不回传来源命令 |
| 16 | AT-03/AT-04 异步最终状态 | 窗口内 `read(WS1)` 为来源当前值；完成后为修改前快照 `A`；来源再改后本地仍为 `A` |
| 17 | 验证记录 | `mvn test` 全绿；新增 `t5-s3-verification-result.md` 并回链 |

## 5. 测试与验收

**命令**：`mvn test`。

| # | 测试 | 断言要点 |
| --- | --- | --- |
| 1 | **异步窗口（命令不等待）** | 可控执行器未 drain：`changeTitle` 成功返回；目标 `materialized=false`；`read(WS1)` 返回来源当前内容 `B`（最终一致性窗口，不新增第三状态） |
| 2 | **异步完成后物化** | drain 后目标 `materialized=true`，内容为修改前快照 `A`；`read(WS1)` 返回 `A` |
| 3 | 三字段一致 | `materialized=true`、`sourceLocalId == localId`、`sourceRevision == revision` |
| 4 | Local-backed 独立 | 物化后来源再改为 `C` 并 drain：`read(WS1)` 仍为 `A`，`read(WS0)` 为 `C` |
| 5 | 目标定位与已物化跳过 | 仅未物化且 `sourceLocalId`/`beforeRevision` 匹配的目标被处理；已 Local-backed 跳过，不重复保存 |
| 6 | **来源/目标版次分离** | 来源 `beforeRevision=3`、目标 `revision=1`：物化后目标仍为 `1`，内容来自 revision 3 快照，`sourceRevision==1` |
| 7 | **快照缺失** | 拒绝物化；不读取来源当前值；目标仍 Source-backed；`CowFailureLog` 有 `MATERIALIZE` 记录 |
| 8 | **来源身份或 commonId 不匹配** | 拒绝物化；不切换；既有目标内容不被覆盖；失败记录可查询 |
| 9 | **升级型事件用 beforeRevision 定位** | 直接构造 `before=3 / after=4` 事件：`sourceRevision=3` 的未物化目标被处理；`sourceRevision=4` 的目标不被处理；目标 revision 不变 |
| 10 | **既有目标无法确认有效物化** | 预置同 `localId` 但内容/身份不符的 Requirement：拒绝切换；原内容不被覆盖 |
| 11 | **切换失败后可读回** | 注入 `items.save` 异常并 drain：业务内容存在；重新读取仍 `materialized=false`；`CowFailureLog` 有 `SWITCH` 记录 |
| 12 | 保存失败 | LTI 仍 Source-backed；Requirement 存储无目标记录；失败记录可查询；命令不抛 |
| 13 | 扇出 | 多目标独立成败；一个失败不回滚另一个；失败与成功都有对应可观察结果 |
| 14 | **同源顺序与首次快照** | 连续 `A→B` 事件按序入队后 drain：先执行 A 的快照物化，B 被跳过（已物化）；目标内容为 `A`；执行顺序可由测试执行器记录断言 |
| 15 | **跨来源并行** | 两个不同来源的任务可并行/互不阻塞；一个来源失败不影响另一个来源完成 |
| 16 | **Spring 装配与异步窗口** | 上下文装配 Listener/Dispatcher/Handler/WritePort/Executor；用 `@Primary` 可控执行器：`changeTitle` 返回后未物化，drain 后物化 |
| 17 | **Handler 异常不向命令传播** | 用 `@Primary` 失败 `RequirementWritePort`：`changeTitle` 正常返回、不抛；失败可查询 |
| 18 | AC-02 + 回归 | 物化前后 `localId` 不变（按身份断言）；S1/S2 的 35 项测试仍通过（事件字段断言同步更新） |

**测试替身边界与等待方式**：只在 Repository/SnapshotStore/Port 的失败注入处使用替身；不模拟 Handler 内部对象，不用 Stub 复制替代真实内存仓储隔离（A）。异步测试使用 `ManualExecutor`（入队、`drain()`/有界 `awaitQuiescence(Duration)`）或 `CountDownLatch` 完成信号，**禁止用固定 `Thread.sleep` 判断正确性**。

## 6. 完成定义（DoD）

1. `mvn test` 全绿（原 35 项 + S3 新增）；
2. AT-03/AT-04 以**异步最终状态**通过：窗口内读取来源当前内容，异步完成后读取修改前快照；AT-05 所需的“切换失败不污染仓储”前提有测试（恢复本身留 S4）；
3. 第 5 节 18 项测试可执行且通过；第 1、2、3、6、7、8、9、10、11、12、13、14、16、17 项为硬性项；
4. `CowEventHandler` 只经 `RequirementWritePort` 访问 ALM，不直接引用 `RequirementRepository`/`Requirement`（ArchUnit 规则保持通过）；
5. 内存仓储隔离在真实实现中生效，且有“save 失败后重新读取仍 Source-backed”的测试；
6. 事件 before/after 语义在发布端与消费端一致；未把 `afterRevision` 用于目标定位或快照核验；
7. 异步投递：来源命令不等待 COW；同源按键串行、跨来源可并行；失败写入可查询 `CowFailureLog`；异步测试不使用固定 `Thread.sleep`；
8. 未新增 outbox（复用 Spring Modulith `event_publication`）；未实现重试、死信、去重、跨进程顺序或并发同源交错保证（S4）；
9. 在 `implementation/` 追加「S3 验证结果」记录，回链本说明、AT-03/AT-04/AT-05 前置、相关 INV/SCN，并如实记录未验证能力（升级型端到端、并发重复投递、事务提交语义、跨进程顺序、可靠投递）。

## 7. 排除范围

S3 仅覆盖正常首次 COW 闭环 + 进程内异步执行边界；以下由 S4 负责：可靠投递增强（重试/死信/去重；运行时已有 Spring Modulith `event_publication`）、跨进程与多实例顺序、并发同源 `save`/`publish` 交错、幂等恢复与重复事件、Revision 升级端到端物化、跨聚合失败恢复。其他排除项：Merge、绑定/删除、权限、版本治理、生产持久化与消息中间件、历史回读（O3）。

## 8. 观察项与风险

| 编号 | 项 | 处理 |
| --- | --- | --- |
| O1 | 异步投递仅进程内；不承诺可靠投递、事务提交后交付、跨进程顺序或多监听器异常隔离 | 按 §3.6 表述；执行器可注入；不以 `@Async`、线程池或日志冒充可靠投递 |
| O2 | 保存与切换无跨聚合事务（双事实） | S3 接受部分提交；S4 补齐恢复；不得宣称已完成 |
| O3 | `RequirementAdapter.readAt` 非当前版次仍抛异常 | S3 不需要历史回读；留后续专项 |
| O4 | `authorize` 恒 `true` | 权限切片再处理 |
| O5 | 既有目标仅按“内容与快照完全一致”认定为同一次有效物化 | S4 改为 `eventId + targetLocalId` 幂等记录；S3 不建幂等框架 |
| O6 | 目标 `revision` 与来源 `beforeRevision` 可不同步（INV-051） | 定位/核验用来源版次，目标版次只用 `target.revision()`；不得强制相等 |
| O7 | 快照缺失/来源身份不匹配/commonId 不一致 | Adapter 显式拒绝；不得用来源当前值或新身份补齐 |
| O8 | Revision 升级型事件的端到端路径未实现（`upgradeRevision()` 未落地） | S3 用直接构造事件验证 before/after 用法；端到端留 S4 前置，不写入“升级不触发 COW”的伪规则 |
| O9 | 并发重复投递可能双写业务对象 | 明确不属于 S3 能力；S4 处理 |
| O10 | SK 应用层使用 SLF4J API | 仅为日志门面；不引入日志实现或框架绑定 |
| O11 | 并发同源修改中 `save` 与 `publish` 可能交错，导致同源队列入队顺序与真实修改顺序不一致 | S4 以来源变更序号、事件日志或幂等键补齐；S3 只保证顺序调用下的同源有序 |
| O12 | 执行器拒绝、进程崩溃会丢失进程内队列事件 | S4 完善持久化事件重试；运行时已有 Spring Modulith `event_publication`；S3 不宣称可靠投递 |
| O13 | `CowFailureLog` 仅内存，重启即丢失 | S4 持久化并作为恢复入口；S3 只保证失败可观察与可查询 |

## 9. 遇到设计缺口时

- 若发现需要新的业务裁定（如目标附件/关系基准、发布大版本继承规则变化、重试/告警阈值），**停止编码**并在设计层标注 `Proposed`；
- 不得修改 T1～T4 已冻结契约，不得在代码中暗补领域概念；
- 本文件 A～F 六项裁定与 2026-11-11 三项裁定在实施中若需改变，先更新本文件并取得确认；
- 不得把“S3 未实现”误写成“业务规则不要求”（尤其 Revision 升级触发 COW，D-018 已确认）。
