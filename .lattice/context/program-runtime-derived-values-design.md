# Program 运行时派生值设计方案（健康状态 / 计划进度 / 偏差天数）

> 状态：**现状设计（as-is）** —— 记录当前已落地的实现与口径，不含改造计划。
> 范围：`ProgramBo` 上「按需实时计算、不承载持久语义」的展示值，及其承载 / 编排 / 缓存 / 判定设施。
> 相关：`ProgramRuntime`、`IProgramRuntimeCalculator`、`IProgramCacheService`、`HealthStatusResolver`、`ProgramTimeNodeNotifyExecutor`；REQ-3.7-Task0001318（健康状态 + 进度延迟风险）；提交 `#15957`（运行时计算重构）。
> 历史文：《Program 实时派生值收敛方案》（过程稿，要点已并入本文）。

---

## 1. 背景与范围

`ProgramBo` 上有三类**按需实时计算、不落主流转表**的派生展示值：

| 派生值 | 承载对象字段 | 计算入口 |
|--------|-------------|---------|
| 健康状态 | `ProgramRuntime#healthStatus` | `IProgramRuntimeCalculator#calculateHealthStatus` |
| 计划进度 | `ProgramRuntime#scheduleProcess` | `IProgramRuntimeCalculator#calculateScheduleProcess` + `IProgramCacheService` |
| 偏差天数（6 项） | `ProgramRuntime#varianceDays`（`ProgramVarianceDays`） | `IProgramRuntimeCalculator#calculateVarianceDays` |

设计目标（现状已达成）：

1. **计算与持久化字段解耦**：派生值不再作为 `ProgramBo` 的普通字段，也不落 current 表。
2. **消除「先算再取」隐含契约**：计算结果一次性写入**不可变**的 `ProgramRuntime`，拿到实例即代表计算已完成。
3. **规则纯函数化、时点可注入**：健康状态判定与计划进度计算均为「输入 + 时点」的纯函数，支持回放「昨天 / 任意时点」。
4. **按需与批量**：只取所需细分方法；进度维度不参与判定时不产生计划进度读取（零额外开销）。

---

## 2. 分层与组件

```
infrastructure/sdk                         （无 Spring：POJO / record / 枚举 / 静态工具）
  sdk.program.dto.ProgramRuntime                  不可变运行时视图（健康状态/计划进度/偏差天数）
  sdk.program.dto.ProgramVarianceDays             6 项偏差天数 record
  sdk.program.service.IProgramRuntimeCalculator   运行时计算器契约
  sdk.program.service.IProgramCacheService         计划进度缓存契约
  sdk.program.utils.HealthStatusResolver           健康状态对外门面（静态，唯一计算入口）
  sdk.program.utils.HealthStatusCalculateReq        判定入参 record
  sdk.program.utils.HealthStatusCalculator          内部引擎（包内可见）
  common.enums.list.HealthStatusListEnum            健康状态枚举（6 档）

infrastructure/sdkImpl                      （Spring + Redis）
  sdkimpl.service.program.service.ProgramRuntimeCalculator   计算器实现（编排 + 规则搬运）
  sdkimpl.service.program.service.ProgramCacheRegistry      计划进度 Redis 按日缓存
  sdkimpl.service.program.dataobject.ProgramDataObject      列表/详情展示装配
  sdkimpl.service.program.service.ProgramBaselineService    基线快照（派生值落库）
  sdkimpl.service.program.service.ProgramTodoList           待办展示
```

依赖方向 `biz → sdk → sdkImpl`：SDK 只放契约与纯计算，sdkImpl 才依赖 `ProgramBo` / `IProgramService` / Redis。

---

## 3. 数据模型：派生值与持久化字段解耦

- `ProgramBo` **不再持有** `healthStatus` / `scheduleProcess` / 6 项 `*VarianceWorkDays` 字段；只保留 `currentProcess`（计划进度的**输入**之一）等业务字段。
- `ProgramPO`（current 表）**无派生列** —— 派生值不落主表，也不参与 MapStruct 落库。
- `ProgramBaselinePO`（基线快照）**保留** `healthStatus` / `scheduleProcess` / 6 项偏差列 —— 基线在**快照时刻冻结**派生值，属有意设计，与主表不持久化并不矛盾（见 §10 `ProgramBaselineService`）。
- 派生值允许为 `null`，语义为业务「无值」（如无计划完成日期 → 健康状态为 null），由消费方按业务语义判空。

---

## 4. 结果承载对象（不可变）

### 4.1 `ProgramRuntime`

| 字段 | 类型 | 说明 |
|------|------|------|
| `program` | `ProgramBo` | 源项目节点（持久化状态 + 简单领域行为） |
| `healthStatus` | `Long` | 健康状态 key，可空 |
| `scheduleProcess` | `Integer` | 计划进度（百分点），可空 |
| `varianceDays` | `ProgramVarianceDays` | 6 项偏差天数 |

- 全字段构造，`@Getter` 生成只读访问，唯一的写入点在 `ProgramRuntimeCalculator` 计算完成后。
- 通过 `getEstimatedStartVarianceWorkDays()` 等 6 个方法**委托** `varianceDays`，保持调用方按字段名取值的手感。

### 4.2 `ProgramVarianceDays`

- `record`，6 项 `Integer`：预估开始/结束/完成天数偏差、实际开始/结束/完成天数偏差；无日历或对应日期缺失时该项为 `null`。

---

## 5. 计算器契约与实现

### 5.1 `IProgramRuntimeCalculator` 方法矩阵

| 方法 | 时点 | 计划进度来源 | 适用场景 |
|------|------|-------------|---------|
| `calculate(ProgramBo)` | 今天（当前时刻） | 今天缓存 | 需要全部派生值（列表/详情） |
| `calculateHealthStatus(ProgramBo)` | 今天（当前时刻） | 今天缓存 | 只要健康状态，口径同 `calculate` |
| `calculateHealthStatus(ProgramBo, long dayTime)` | 指定自然日 | 该日缓存（按需） | 跨天跃迁 / 回放昨天 |
| `calculateScheduleProcess(ProgramBo)` | 今天 | **实时递归，不读缓存** | BI 进度数据集等 |
| `calculateScheduleProcess(ProgramBo, long dayTime)` | 指定自然日 | 实时递归，不读缓存 | 计划进度按时点回放 |
| `calculateVarianceDays(ProgramBo)` | 与时点无关 | 无 IO | 概览偏差列 |

> 按需原则：只需要一项时不要调 `calculate`，避免无谓计算。

### 5.2 `ProgramRuntimeCalculator` 实现要点

```java
calculate(program):
  scheduleProcess = cache.getScheduleProcess(program)              // 今天，唯一 IO
  healthStatus    = calculateHealthStatusAt(program, scheduleProcess, now)
  varianceDays    = calculateVarianceDays(program)
  return new ProgramRuntime(program, healthStatus, scheduleProcess, varianceDays)

calculateHealthStatus(program, dayTime):
  scheduleProcess = mayParticipateProgress(...) ? cache.getScheduleProcess(program, dayTime) : null
  return calculateHealthStatusAt(program, scheduleProcess, beginOfDay(dayTime))
```

- **循环依赖处理**：`ProgramCacheRegistry` 反向依赖 `IProgramRuntimeCalculator`，故计算器对 `IProgramCacheService`、`IProgramService` 采用 `@Lazy @Resource` 字段注入，避免构造期循环。
- **时点归一**：健康状态的时间维度按 `beginOfDay(dayTime)` 求值，保证同一自然日内不同时刻得到同一档位。
- **按需读进度**：`mayParticipateProgress` 预判（规则启用 ∩ 执行中 ∩ 项目集/项目/阶段/任务 ∩ 有当前进度）不成立时 **不读计划进度缓存**；判定内部仍会独立校验参与条件（宽松预判只会多算、不会错判）。
- **规则搬运**：实现逐行对应原 `ProgramBo#calculateHealthStatus()` / `#calculateScheduleProcess()` / `#calculateProgramScheduleProcess()` / `#findScheduleProcessCache()` / `#calculateVarianceDays()`，**计算口径不变**，仅改变结果承载位置。

---

## 6. 健康状态判定设计

### 6.1 判定入参 `HealthStatusCalculateReq`

| 字段 | 说明 |
|------|------|
| `statusType` | 生命周期类型 |
| `workStatus` | 工作状态 |
| `type` | 节点类型（仅执行中的项目集/项目/阶段/任务参与进度维度） |
| `scheduleStartDate` / `scheduleFinishDate` | 计划开始 / 完成日期 |
| `actualStartDate` | 实际开始日期（可空） |
| `scheduleProcess` | 计划进度（null = 不参与进度） |
| `currentProcess` | 当前进度（null 阻止进度参与） |

- `timeOnly(...)` 便捷构造：`type`/两进度置空，等价纯时间判定（跃迁通知、超期判定、基线比较等）。

### 6.2 时间维度（现状口径不变）

**门槛**（任一不满足 → `null`）：
- `workStatus == START`；
- 生命周期**非**已完成 / 取消 / 暂停（`isFinished` 或 `HOLD`）；
- `scheduleFinishDate != null`。

**基准日期**：
- 有 `actualStartDate` → 按**计划完成日期**；
- 否则按**计划开始日期**（为空兜底计划完成日期）。

**档位映射**（`differenceInDays = 距基准日期天数`）：

| 条件 | 一般 | `PENDING_START` 变体 |
|------|------|---------------------|
| `> 3` | `HEALTHY` | `PENDING_HEALTHY` |
| `0 ~ 3`（含到期当天） | `WARNING` | `PENDING_WARNING` |
| `< 0` | `DELAYED` | `PENDING_DELAYED` |

> 关口 / 里程碑无计划开始时间，时间判定自动兜底用计划完成时间。

### 6.3 进度维度（进度延迟风险）

**参与条件**：`statusType` 执行中 ∩ `type ∈ {PROGRAM, PROJECT, PHASE, TASK}` ∩ `scheduleProcess != null` ∩ `currentProcess != null`。

**规则快照 `ProgressRiskConfig`**（由计算入口按需从智能规则懒加载）：

- 来源：`AdminCache.getNodeById(INodeId.ROOT_SMART_RULE_ID)` → `SmartRuleNode`；`null` 等价未启用。
- 字段：`enabled`（`eppmProgressRiskEnabled`）、`n1`（预警阈值，默认 20）、`n2`（报警阈值，默认 40）。
- 归一：`n1 ∈ [1, 99]`、`n2 ∈ (n1, 100]`（默认 20 / 40）。

**分档**（`gap = scheduleProcess − currentProcess`）：

| 条件 | 档位 | 等级 |
|------|------|------|
| 未启用 或 `gap < n1` | 无 | 0 |
| `n1 ≤ gap < n2` | 预警（黄） | 2 |
| `gap ≥ n2` | 报警（红） | 3 |

### 6.4 综合判定 = `max(时间, 进度)`

```
timeKey == null                → null
规则未启用 或 进度不参与        → timeKey
riskLevel ≤ timeLevel          → timeKey        // 时间主导或同档（时间已红时进度回落不降级）
riskLevel == WARNING           → WARNING
riskLevel == DELAYED           → DELAYED
```

- 规则默认关闭 → 等价纯时间判定。
- 进度可产生红，但不产生绿；进度维度只升不降。

### 6.5 对外门面 `HealthStatusResolver`

- 静态门面，唯一计算入口：`calculateHealthStatus(req)`（当前时间）/ `calculateHealthStatus(req, currentTime)`（指定时间）。
- 只读预判：`progressRiskEnabled()`、`supportsProgressRisk(type)`，供调用方按需预取计划进度。
- 内部引擎 `HealthStatusCalculator` 包内可见（`ProgressRiskConfig` 可注入以绕过 `AdminCache`，便于测试）。

### 6.6 健康状态枚举 `HealthStatusListEnum`

| 枚举 | key | 颜色 | 说明 |
|------|-----|------|------|
| `HEALTHY` | 1 | `#5B8FF9` | 健康 |
| `WARNING` | 2 | `#FFB65D` | 警告 |
| `DELAYED` | 3 | `#FF998A` | 延迟 |
| `PENDING_HEALTHY` | 598012924209600 | `#8EDBB3` | 待启动-健康 |
| `PENDING_WARNING` | 598012953827776 | `#FFF099` | 待启动-警告 |
| `PENDING_DELAYED` | 598012970568128 | `#FFBFB3` | 待启动-延迟 |

---

## 7. 计划进度设计

### 7.1 计算（递归）

```
calculateScheduleProcess(program, dayTime):
  子节点为空 → 叶子：calculateProgramScheduleProcess(program, dayTime)
  否则       → 子节点递归结果求和 / 子节点数（null 计 0）

calculateProgramScheduleProcess(program, dayTime):   // 叶子
  current = 按工作日历换算 dayTime
  scheduleStartDate != null && scheduleDays != null:
      current < scheduleStartDate → 0
      否则 a = scheduleDays == 0 ? 0
              : 100 * 工作日历区间(current 结束, scheduleStartDate) / scheduleDays
      scheduleProcess = min(a, 100)
  里程碑/关口：current 同日或晚于 planFinish → 100，否则 0
```

- 非叶子取**子节点算术平均**；里程碑 / 关口按是否到达计划完成日**二值化**。

### 7.2 Redis 按日缓存 `ProgramCacheRegistry`

- key：`program:scheduleProgress::{rootParentObject}::{beginOfDay(dayTime)}`（按日隔离，支持回放）。
- 结构：`RMapCache<instanceId, scheduleProcess>`，`computeIfAbsent` 计算后 `put`，单条 TTL 1 天。
- 失效 `clearScheduleProcess(id)`：清 **今天 + 昨天** 两份按日缓存（通知跃迁需回放昨天时点的计划进度）。
- 展示口径的「今天的计划进度」走缓存；按时点回放 / BI 走 `IProgramRuntimeCalculator.calculateScheduleProcess`（**不读缓存**）。

---

## 8. 偏差天数设计

统一入口 `calculateVarianceDays`：无 `calendar` 时 6 项均不计算（全 null）。

| 项 | 计算 | 前置 |
|----|------|------|
| 预估开始偏差 | `betweenDays(scheduleStartDate, estimatedStartDate)` | 两者非空 |
| 预估结束偏差 | `betweenDays(scheduleFinishDate, estimatedFinishDate)` | estimatedFinishDate 非空 |
| 预估完成天数偏差 | `estimatedDays − scheduleDays` | 两者非空 |
| 实际开始偏差 | `betweenDays(scheduleStartDate, actualStartDate)` | 两者非空 |
| 实际结束偏差 | `betweenDays(scheduleFinishDate, actualFinishDate)` | actualFinishDate 非空 |
| 实际完成天数偏差 | `actualDays − scheduleDays` | 两者非空 |

- 纯内存计算，**无 IO**，与时点无关。

---

## 9. 时点与回放（跨天跃迁）

- `dayTime` 语义：任意时刻，按其**所在自然日**计算（健康状态按 `beginOfDay(dayTime)`，缓存 key 同口径）。
- 计划进度是「日期 + 计划数据」的**纯函数**，可按时点回放；**实际进度无历史快照**，回放时沿用当前值。
- `ProgramTimeNodeNotifyExecutor#handleHealthStatusChange`：

```
today = beginOfDay(now); yesterday = today - 1 天
statusNow      = calculator.calculateHealthStatus(program, today)
statusYesterday= calculator.calculateHealthStatus(program, yesterday)
// 仅向恶化方向升级通知（绿→黄、黄→红）；进度追上导致的好转不触发，避免降级刷屏
isHealthUpgrade(statusYesterday, statusNow) → 发送通知
```

- `isHealthUpgrade`：今日有值且（昨日无值 或 今日等级 > 昨日等级）。
- 另有一路超期/其他判定使用 `HealthStatusResolver.calculateHealthStatus(HealthStatusCalculateReq.timeOnly(...))`（纯时间）。

---

## 10. 消费方口径一览

| 消费方 | 使用的方法 | 说明 |
|--------|-----------|------|
| `ProgramDataObject#processCalculateCells` | `calculate(program)` 全量 | 列表/详情装配 7 个派生单元格（健康状态 + 计划进度 + 6 项偏差） |
| `ProgramBaselineService` | `calculateVarianceDays` + `calculateHealthStatus` | 计划进度沿用**计划树计算结果**，避免全量计算；快照落库 |
| `ProgramTodoList#processProgramCell` | `calculateHealthStatus` + `cache.getScheduleProcess` | 待办列表，按今天 |
| `ProgramTimeNodeNotifyExecutor#handleHealthStatusChange` | `calculateHealthStatus(program, today/yesterday)` | 跨天恶化升级通知 |
| `ProgramTimeNodeNotifyExecutor#currentHealthStatus` | `Resolver.calculateHealthStatus(timeOnly)` | 纯时间口径 |
| `AbstractProgramDynamicQueryManagement#processCell` | `calculateVarianceDays` + `cache.getScheduleProcess`（按需） | 概览计算列；`queryScheduleProcess` 决定是否读进度 |
| `ProjectProgressDataSetProcessor` | `calculateScheduleProcess`（递归） + `calculateVarianceDays` | BI 进度数据集 |
| `ProjectDataSetProcessor` | `calculateHealthStatus` | BI 项目集健康聚合 |
| `PhaseDataSetProcessor` | `calculateHealthStatus` | BI 阶段健康 |
| `ProgramTabScheduleQueryManagement` | `calculateHealthStatus(c)`；`Resolver(timeOnly)` → `reEvaluateHealthStatus`（完整入参） | 计划页签：先时间维度初值，进度算出后综合重评 |
| `ProgramWorkHoursStatService` | `cache.getScheduleProcess` | 工时统计明细 |

---

## 11. 迁移对照（改造前 → 现状）

| 维度 | 改造前 | 现状 |
|------|--------|------|
| 计算位置 | `ProgramBo#calculateHealthStatus/calculateScheduleProcess/calculateVarianceDays` | `IProgramRuntimeCalculator` |
| 结果承载 | 写回 `ProgramBo` 字段（可变，易漏算） | `ProgramRuntime`（不可变） |
| 落库 | `ProgramPO` 含派生列，保存时快照进 DB | `ProgramPO` 无派生列；仅基线快照保留 |
| 时点 | 方法内写死 `System.currentTimeMillis()` / `getTodayDate()` | `dayTime` 显式传参，支持回放 |
| 缓存 | `ProgramBo#findScheduleProcessCache()` | `IProgramCacheService#getScheduleProcess(program[, dayTime])`，key 按日 |
| 规则 | 每次判定读 `AdminCache` 的智能规则 | `ProgressRiskConfig` 快照，按需懒加载；可注入测试 |

---

## 12. 测试

| 模块 | 测试类 | 覆盖点 |
|------|--------|--------|
| sdk | `HealthStatusResolverTest`（49 用例） | 时间档位 / PENDING 变体 / 进度分档与 max 综合 / 阈值归一 / 门槛 null |
| sdkImpl | `ProgramRuntimeCalculatorTest`（12 用例） | 偏差天数计算与无日历、规则开关、进度跨阈值判红、计划进度只读一次、门槛 null、按时点回放、进度不参与不读缓存、叶子/子节点平均 |
| biz | `ProgramTimeNodeNotifyExecutorTest`（33 用例） | 跨天恶化升级通知、纯时间口径等 |

原 `ProgramBoTest` 中针对派生值的用例已随计算搬迁移除。

---

## 13. 已知问题与注意点

| # | 问题 | 说明 |
|---|------|------|
| 1 | `ProgramDataObject#processCalculateCells` 中 `ATTR_ESTIMATED_START_VARIANCE_WORK_DAYS` 取的是 `runtime.getEstimatedDurationVarianceWorkDays()` | 疑似复制粘贴问题（预估开始偏差被写成预估完成天数偏差）；其余 5 项正确，待确认后修正 |
| 2 | `ProgramPO` 已无派生列，但历史 DB 列是否保留 / 清理需按升级脚本确认 | 影响建表与迁移口径 |
| 3 | 实际进度（`currentProcess`）无历史快照 | 回放时进度维度沿用当前值，非严格历史口径 |
| 4 | `ProgramRuntime` 为普通类而非 record | 因需委托 getter 兼容字段名；如统一为 record 需调用方配合 |
| 5 | 进度风险规则阈值 N1/N2 默认 20/40，实际值依赖智能规则配置 | 环境差异需核对 |

---

## 14. 类索引

| 类 | 路径 |
|----|------|
| `ProgramRuntime` | `infrastructure/sdk/.../sdk/program/dto/ProgramRuntime.java` |
| `ProgramVarianceDays` | `infrastructure/sdk/.../sdk/program/dto/ProgramVarianceDays.java` |
| `IProgramRuntimeCalculator` | `infrastructure/sdk/.../sdk/program/service/IProgramRuntimeCalculator.java` |
| `IProgramCacheService` | `infrastructure/sdk/.../sdk/program/service/IProgramCacheService.java` |
| `HealthStatusResolver` | `infrastructure/sdk/.../sdk/program/utils/HealthStatusResolver.java` |
| `HealthStatusCalculateReq` | `infrastructure/sdk/.../sdk/program/utils/HealthStatusCalculateReq.java` |
| `HealthStatusCalculator` | `infrastructure/sdk/.../sdk/program/utils/HealthStatusCalculator.java` |
| `HealthStatusListEnum` | `infrastructure/sdk/.../common/enums/list/HealthStatusListEnum.java` |
| `ProgramRuntimeCalculator` | `infrastructure/sdkImpl/.../sdkimpl/service/program/service/ProgramRuntimeCalculator.java` |
| `ProgramCacheRegistry` | `infrastructure/sdkImpl/.../sdkimpl/service/program/service/ProgramCacheRegistry.java` |
| `ProgramRuntimeCalculatorTest` | `infrastructure/sdkImpl/.../sdkimpl/service/program/service/ProgramRuntimeCalculatorTest.java` |
| `HealthStatusResolverTest` | `infrastructure/sdk/.../sdk/program/utils/HealthStatusResolverTest.java` |
