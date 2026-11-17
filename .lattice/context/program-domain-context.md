---
feature: "Program 项目管理领域"
domain: "program"
created: "2026-07-15"
related_design: null
---

# Program 项目管理领域上下文

> Program 是 AirEdge PLM 的项目全生命周期管理域，覆盖项目组合（Portfolio）、项目集（Program）、项目（Project）、阶段（Phase）、任务（Task）五种活动类型，以及关口（Gate）与里程碑（Milestone）两种点状节点。核心能力包括计划排程、关键路径、基线对比、工时统计、审批流程、MPP 导入导出。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-15 | 健康状态实时计算、不存库 | `HealthStatusResolver#calculateHealthStatus` 基于当前时间与计划/实际日期动态计算 HEALTHY/WARNING/DELAYED，保证数据新鲜度 | 存 health_status 列，定期刷新 |
| 2026-07-15 | 健康状态按天数（LocalDate）而非毫秒判断 | `DateUtil.betweenDays` 忽略时分秒，按日历日比较，与用户直觉一致 | 精确到毫秒/小时 |
| — | Program 基类 ID=1201L，活动父类 ID=120101L，关口父类 ID=120102L | 活动类与关口/里程碑类分层，便于分类维度操作下发与状态流转 | 扁平化，所有子类直接挂在基类下 |
| — | 关键路径基于 jgrapht 图算法库 | 工业级图算法，支持 FS/SS/FF/SF 四种依赖类型 + Lag，计算 EST/LST/EFT/LFT | 自研图算法 |

## Open Questions

<!-- When resolved, capture as decision above and remove from here. -->

## Constraints

- Program 对象 **禁止** 全量查询，必须通过分页或 tracker 范围
- 基线快照创建后不可修改（immutable）
- 计划页签的依赖关系（Dependencies）必须遵循 `CriticalPathService` 的约束校验（禁环、禁自依赖）
- 工时审批走标准工作流 sign-off，非自定义状态机
- MPP 导入为异步任务（`MppImportRunnable`），不走同步响应

## Key Files

| Path | Role |
|------|------|
| `infrastructure/sdk/src/main/java/.../program/` | SDK 层：接口、DTO、枚举、核心算法（关键路径） |
| `infrastructure/sdkImpl/src/main/java/.../program/dataobject/ProgramDataObject.java` | Program DataObject 实体，生命周期钩子与回调 |
| `infrastructure/sdkImpl/src/main/java/.../program/service/ProgramService.java` | Program 核心服务实现 |
| `infrastructure/sdkImpl/src/main/java/.../program/service/CriticalPathService.java` | 关键路径计算（EST/LST/总浮时） |
| `infrastructure/sdkImpl/src/main/java/.../program/service/ProgramBaselineService.java` | 基线创建/对比 |
| `infrastructure/sdkImpl/src/main/java/.../program/processor/ProgramBaseClassProcessor.java` | Program 基类扩展处理器 |
| `infrastructure/sdkImpl/src/main/java/.../program/processor/ProgramStatusAggregator.java` | 父子级状态聚合（根任务完成 → 项目完成） |
| `infrastructure/sdkImpl/src/main/java/.../program/service/ProgramLockPolicyService.java` | 锁定策略（锁定后的编辑限制） |
| `infrastructure/sdkImpl/src/main/java/.../program/service/ProgramStatusChangeService.java` | 状态变更引擎（生命周期聚合 + 工作状态传播 + 工作流推进） |
| `infrastructure/sdk/src/main/java/.../program/utils/HealthStatusResolver.java` | 健康状态实时计算（HEALTHY/WARNING/DELAYED） |
| `infrastructure/sdk/src/main/java/.../program/utils/ProgramAttributePrivilegeContainer.java` | 属性编辑权限矩阵（CombinedStatus → 可编辑属性） |
| `infrastructure/sdk/src/main/java/.../program/criticalpath/Project.java` | 关键路径算法核心入口 |
| `infrastructure/sdk/src/main/java/.../program/criticalpath/Task.java` | 关键路径任务节点（EST/LST/EFT/LFT） |
| `infrastructure/biz/src/main/java/.../program/rest/ProgramObjectRest.java` | Program REST API |
| `infrastructure/biz/src/main/java/.../program/rest/ProgramMppRest.java` | MPP 导入导出 REST API |
| `infrastructure/biz/src/main/java/.../program/rest/ProgramWorkHoursRest.java` | 工时 REST API |
| `infrastructure/biz/src/main/java/.../program/management/dynamic/ProgramObjectManagement.java` | Program 业务编排层 |
| `infrastructure/biz/src/main/java/.../program/management/mpp/MppImportManagement.java` | MPP 导入业务逻辑 |
| `infrastructure/biz/src/main/java/.../program/management/schedule/ProgramTabScheduleQueryManagement.java` | 计划页签查询 |
| `infrastructure/biz/src/main/java/.../program/toolkit/MppFileResolver.java` | MPP 文件解析器（MPXJ 库） |
| `infrastructure/biz/src/main/java/.../program/management/button/ProgramDataObjectButtonProcessor.java` | 按钮装饰器入口（页签/对象两级分发） |
| `infrastructure/biz/src/main/java/.../program/management/button/ProgramObjectButtonHandler.java` | 对象级按钮状态矩阵 |
| `infrastructure/biz/src/main/java/.../program/management/button/ProgramTabButtonHandler.java` | 页签级按钮状态处理 |
| `AirAPI/src/main/java/.../AirConstants.java` § Program | 常量定义（分类ID、属性API ID、页签ID） |

## 领域模型

### 分类层级（数据模型）

```
ProgramBaseClass (1201)
├── ActivityClass (120101)              ← 活动类
│   ├── Portfolio   (12010101)          ← 项目组合
│   ├── Program     (12010102)          ← 项目集
│   ├── Project     (12010103)          ← 项目
│   ├── Phase       (12010104)          ← 阶段
│   └── Task        (12010105)          ← 任务
└── GateClass (120102)                  ← 关口/里程碑类
    ├── Gate        (12010201)          ← 关口
    └── Milestone   (12010202)          ← 里程碑
```

### 实例层级关系（业务规则）

- **关口/里程碑只能作为叶子节点**：Gate 和 Milestone 实例不允许有子节点
- **关口/里程碑无计划页签**：不展示 SCHEDULE 计划页签，无 ScheduleStartDate / ScheduleFinishDate / ScheduleDays / ActualStartDate / ActualFinishDate / EstimatedStartDate / EstimatedFinishDate / HealthStatus 等与排程相关的属性
- **活动类型无模型层限制**：Portfolio、Program、Project、Phase、Task 实例均可作为根节点或任意中间节点，数据模型上不做父子类型组合限制
- **展现层有层级约束**：前端树组件/甘特图在呈现时对某些层级组合有显示限制（如不允许 Task 直接挂在 Portfolio 下等），但这不是数据模型的硬约束

### 核心生命周期状态

```
ProgramStatusTypeEnum（生命周期，子节点驱动父节点）:
  PENDING_START → IN_PROCESS → TYPE_RELEASE → COMPLETE
                               → HOLD
                               → CANCEL

ProgramWorkStatusEnum（工作状态，根节点决定整棵树）:
  PENDING_START → START → IN_CHANGE / HOLD / CANCEL
```

### 状态传播机制

| 维度 | 驱动方向 | 规则 | 关键代码 |
|------|---------|------|----------|
| **生命周期** | 子节点 → 父节点（自底向上） | 叶子节点生命周期变更后，从最深叶子逐层向上聚合父节点状态；`ProgramStatusAggregator` + `ProgramStatusResolver` 按优先级决议：IN_PROCESS > COMPLETE > HOLD > CANCEL > PENDING_START | `ProgramStatusChangeService#updateProjectStatus` |
| **工作状态** | 根节点 → 整棵树（自顶向下） | 只有根节点可变更工作状态（`getChangeWorkStatusList()` 校验 `isRootNode()`），整棵树继承同一工作状态 | `ProgramDataObject#getChangeWorkStatusList` |
| **进度** | 子节点 → 父节点 | 父节点进度 = 子节点进度平均值 | `ProgramStatusChangeService#updateCurrentProcess` |
| **叶子节点** | — | 生命周期叶子节点的状态变更入口在展现层操作按钮，`runNextWorkFlowStatus` 通过工作流引擎驱动节点流转 | `ProgramStatusChangeService#runNextWorkFlowStatus` |

### 生命周期聚合优先级（ProgramStatusResolver）

```
有任一子节点正在执行(IN_PROCESS/TYPE_RELEASE) → 父=IN_PROCESS
          或 (PENDING_START + COMPLETE 共存) → 父=IN_PROCESS
          或 (PENDING_START + HOLD 共存)    → 父=IN_PROCESS
全部非CANCEL子节点都 COMPLETE               → 父=COMPLETE
全部非CANCEL+COMPLETE子节点都 HOLD          → 父=HOLD
全部子节点都 CANCEL                          → 父=CANCEL
全部非CANCEL子节点都 PENDING_START           → 父=PENDING_START
```

### 核心属性（ProgramScheduleDto / air_inst_program）

> 标记 ⚡ 的属性仅适用于活动类型（Portfolio/Program/Project/Phase/Task），关口/里程碑无这些属性。

| 属性 | API ID | 说明 |
|------|--------|------|
| 计划开始日期 | ScheduleStartDate | 计划排程开始 ⚡ |
| 计划结束日期 | ScheduleFinishDate | 计划排程结束，`null` 时健康状态无法计算 ⚡ |
| 计划完成天数 | ScheduleDays | ⚡ |
| 实际开始日期 | ActualStartDate | 决定健康状态的计算基准：有值→用 ScheduleFinishDate，无值→用 ScheduleStartDate ⚡ |
| 实际结束日期 | ActualFinishDate | ⚡ |
| 预估开始/结束日期 | EstimatedStartDate/EstimatedFinishDate | ⚡ |
| 健康状态 | HealthStatus | 实时计算，不存库；关口/里程碑无此属性 ⚡ |
| 计划进度/当前进度 | ScheduleProcess/CurrentProcess | 0-100 ⚡ |
| 资源占用状态 | ResourceOccupancy | 0(未完成)/1(已完成) |
| 日历 | Calendar | 工作日历实例 ⚡ |
| 锁定状态 | IsLocked | 锁定后限制编辑 |
| 限制条件 | ConstraintCondition/ConstraintDate | ASAP/ALAP/SNET/FNET 等 ⚡ |

### 健康状态（HealthStatusResolver）

```
calculateHealthStatus(statusType, workStatus, scheduleStart, scheduleFinish, actualStart):
  1. workStatus != START 或 isFinished(statusType) 或 scheduleFinish == null → null
  2. actualStart != null → resolver(statusType, betweenDays(now, scheduleFinish))
  3. actualStart == null → resolver(statusType, betweenDays(now, COALESCE(scheduleStart, scheduleFinish)))

resolver(statusType, diffDays):  // diffDays = (目标日期 - 今天日期) 的日历日差
  diffDays > 3  → HEALTHY / PENDING_HEALTHY     (绿色)
  0 < diffDays ≤ 3 → WARNING / PENDING_WARNING  (黄色)
  diffDays ≤ 0 → DELAYED / PENDING_DELAYED      (红色)
```

### 关键路径（CriticalPathService）

- 基于 jgrapht 图算法
- 支持 4 种依赖类型：FS（完成-开始）、SS（开始-开始）、FF（完成-完成）、SF（开始-完成）
- 支持 Lag（滞后量）正/负值
- 约束类型：ASAP、ALAP、SNET（不早于）、FNET（不晚于）、SNLT、FNLT、MSO、MFO
- 输出每个 Task 的 EST/LST/EFT/LFT/总浮时/自由浮时

### Program DataObject 生命周期钩子

| 钩子 | 职责 |
|------|------|
| `preAdd` | 校验 CurrentProcess 上限、设置默认 ResourceOccupancy；GateFlow 来源的项目根发布 `ProgramPostCreateEvent` 触发异步填充任务/依赖/团队 |
| `setValues` → `postUpdate` | 日历变更 → 关键路径重算；CURRENT_PROCESS 变更 → `refresh` 刷新进度聚合；workStatus 变更 → `updateTreeWorkStatus` 传播到整棵树；根节点切 START → 自动创建基线；Owner 变更 → 转签 + 更新子节点 parentOwner |
| `preUpdate` | 锁定校验：START 状态下锁定属性不可修改，返回 `PROGRAM_SCHEDULE_LOCKED` |
| `preDelete` | 仅 `PENDING_START` 状态可删除；校验计划页签无引用后才允许 |

### 页签结构（ProgramTabEnum）

> 标记 ⚡ 的页签仅适用于活动类型，关口/里程碑不展示。

| 页签 | 说明 |
|------|------|
| SCHEDULE (420204) | 计划排程（甘特图数据源）⚡ |
| DEPENDENCIES (420205) | 前后置依赖关系 ⚡ |
| PREDECESSOR_TASK (420319) | 前置任务（只读视图）⚡ |
| SUCCESSOR_TASK (420320) | 后置任务（只读视图）⚡ |
| TEAM (420209) | 团队资源分配 |
| WHERE_USED (420218) | 使用位置 |
| INPUT/OUTPUT (420211/420212) | 输入/输出交付物 |
| ACTION_ITEMS (420208) | 行动项 |
| CHECKLIST (420221) | 检查项 |
| ATTACHMENTS (420217) | 附件 |
| RELATIONSHIPS (420220) | 关联关系 |
| TEAM_RESOURCE (420398) | 团队资源汇总 |

## 分层架构

```
Biz (infrastructure/biz)
├── program/rest/*Rest           ← REST 控制器 (@HttpExchange)
├── program/management/*         ← 业务编排（Management）
├── program/handler/*            ← MPP 导入处理器链
└── program/toolkit/*            ← MPP 文件解析（MPXJ）

SDK (infrastructure/sdk)
├── program/IProgramInner        ← 领域接口（继承 IDataObjectFeature + IRoutableInner）
├── program/service/I*           ← 服务接口
├── program/enums/*              ← 枚举（类型、状态、页签）
├── program/dto/*                ← 传输对象
├── program/utils/*              ← 工具类（HealthStatusResolver 等）
├── program/criticalpath/*       ← 关键路径算法（纯 Java）
└── program/bo/*                 ← 业务对象

SDKImpl (infrastructure/sdkImpl)
├── program/dataobject/ProgramDataObject  ← DataObject 实体
├── program/service/*ServiceImpl         ← 服务实现
├── program/processor/*                  ← 扩展处理器
└── program/query/union/ProgramExecutingListUnionQuery  ← 联合查询
```

## 核心数据流

```
REST 请求 → *Rest → *Management → SDK Service → SDKImpl → DataObject → PostgreSQL
                                                          ↓
                                              CriticalPathService ↔ jgrapht
                                              HealthStatusResolver（实时计算）
                                              ProgramStatusAggregator（父子聚合）
```

## 外部集成

- **MPP 导入**：MPXJ 库解析 Microsoft Project 文件 → 异步线程（MppImportRunnable）创建 Program 实例树
- **Gateflow 导入**：GateflowImportManagement 从 GateFlow 流程模型生成 Program 项目结构
- **日历**：依赖 `workcalendar` 模块的工作日历实例（`InstWorkCalendarInstanceDto`）
- **审批流**：走标准 sign-off 机制（`air_inst_sign_off`）

## 扩展点

| 扩展点 | 位置 | 说明 |
|--------|------|------|
| Processor 扩展 | `ProgramBaseClassProcessor` | Program 基类操作后置处理 |
| Union Query | `ProgramExecutingListUnionQuery` 等 | 待办查询自定义 SQL |
| Button 扩展 | `ProgramObjectButtonHandler` | DataObject 操作按钮下发 |
| Tab 查询 | `ProgramTabScheduleDynamicQueryManagement` | 页签动态查询 |

## 按钮可见性（ProgramDataObjectButtonProcessor）

通过装饰器模式注入 Program 基类查询，按页签/对象两级分发：

```
ProgramDataObjectButtonProcessor (@QueryDecorator "ProgramBaseClass")
  ├── tabId != null → ProgramTabButtonHandler    ← 页签内按钮
  └── tabId == null → ProgramObjectButtonHandler  ← 对象级按钮
```

### 对象级按钮（ProgramObjectButtonHandler）

按钮可见性由 `CombinedStatus(workStatus, lifecycle)` + 叶子/非叶子 + 根节点 三维决定：

| CombinedStatus | 叶子 | ENABLE | DISABLE | HIDE |
|---------------|------|--------|---------|------|
| PENDING_START / IN_CHANGE / HOLD / CANCEL | 任意 | DELETE, STATISTICS | CHANGE_STATUS, TRANSFER_OWNER | SUBMIT, APPROVE, REJECT, CONFIRM |
| START × PENDING_START | 叶子 | SUBMIT, DELETE, TRANSFER_OWNER, STATISTICS | CHANGE_STATUS | — |
| START × IN_PROCESS | 叶子 | DELETE, CHANGE_STATUS, TRANSFER_OWNER, STATISTICS | — | — |
| START × (COMPLETE/CANCEL) | 叶子 | DELETE, CHANGE_STATUS, STATISTICS | TRANSFER_OWNER | — |
| * × HOLD | 叶子 | DELETE, CHANGE_STATUS, STATISTICS | TRANSFER_OWNER | SUBMIT, APPROVE, REJECT, CONFIRM |

**特殊规则**：
- 工作流按钮（SUBMIT/APPROVE/REJECT/CONFIRM）：叶子节点且 HOLD/CANCEL 时 DISABLE
- 基线/基线对比：仅根节点可见，非 START 或已完成时 DISABLE
- 请求审批：根节点 + 非结束状态 + 项目类型（Portfolio/Program/Project）→ ENABLE
- 统计：仅项目类型 + 有 BI License → ENABLE，否则 HIDE
- 转签：项目/项目集类型 → 过滤掉
- 快照（基线）模式：仅 EDITOR/EXPORT/COPY/FAVORITES/SHARE 可用，其他 DISABLE

### 页签级按钮（ProgramTabButtonHandler）

| 页签 | 特殊规则 |
|------|---------|
| SCHEDULE | 根节点已完成 → DELETE DISABLE；非根节点隐藏 SAVE_AND_BASELINE；已基线/已完成/暂停取消 → 权限类按钮 DISABLE |
| TEAM | 非叶子节点隐藏 ADD；已完成全部 HIDE；暂停取消全部 DISABLE |
| 其他 | 暂停取消 → 全部 DISABLE |
| 快照模式 | 所有 ENABLE 按钮 → DISABLE |

## 属性编辑权限（ProgramAttributePrivilegeContainer）

`CombinedStatus(workStatus, lifecycle)` 决定哪些内置属性可编辑，分叶子/非叶子两套映射：

**前置过滤**：已完成/暂停/取消 → 无任何属性可编辑

| CombinedStatus | 叶子节点可编辑属性 |
|---------------|-------------------|
| PENDING_START × * | SCHEDULE_MODE, PRIORITY, NAME, NOTES, OWNER, RESOURCE_OCCUPANCY, SCHEDULE_START_DATE, SCHEDULE_FINISH_DATE, PREDECESSOR_DEPENDENCY, ESTIMATED_START_DATE, ESTIMATED_FINISH_DATE |
| START/IN_CHANGE × PENDING_START | 同上 |
| START × IN_PROCESS | 以上 + CURRENT_PROCESS, ACTUAL_START_DATE, ACTUAL_FINISH_DATE |
| IN_CHANGE × IN_PROCESS | 同 PENDING_START 集合（不可编辑进度） |
| START × (COMPLETE/CANCEL) | 空 |

**AttributeRule 链**（在基础集合上动态调整）：
- `CurrentProcessRule`：叶子 + 活动类型 + IN_PROCESS + START → 追加 CURRENT_PROCESS
- `PredecessorDependencyRule`：非 START 且非完成 → 追加 PREDECESSOR_DEPENDENCY
- `StartDateRule`：关口/里程碑 → 移除 SCHEDULE_START_DATE, ESTIMATED_START_DATE, ACTUAL_START_DATE
- `IsLockedRule`：PENDING_START 或 IN_CHANGE → 追加 IS_LOCKED

**锁定排除**：START 工作状态时，调用 `IProgramLockPolicy#findLockAttrs` 获取锁定属性列表，从可编辑集合中移除。

## 锁定机制（ProgramLockPolicyService）

### 锁定属性

锁定后禁止编辑以下 5 个属性：
```
SCHEDULE_MODE, RESOURCE_OCCUPANCY, SCHEDULE_START_DATE, 
SCHEDULE_FINISH_DATE, PREDECESSOR_DEPENDENCY
```

### 默认锁定类型

通过 `SmartRuleNode.getProgramDefaultLockTypes()` 从系统配置加载默认锁定的分类 ID 列表（逗号分隔）。创建 Program 时，若类型在列表中，`IsLocked` 自动设为 1。

### 锁定生效路径

| 场景 | 机制 |
|------|------|
| 属性编辑 | `ProgramAttributePrivilegeContainer` 在 START 状态下调用 `findLockAttrs`，从可编辑集合中减去锁定属性 |
| 导入/导出 | `ProgramLockHelper.applyLockedCell` 对默认锁定类型自动设置 IsLocked=1 |
| 创建默认值 | `IProgramLockPolicy#getDefaultLockedValueByType` 返回该类型的默认锁定状态 |
