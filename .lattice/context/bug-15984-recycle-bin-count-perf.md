---
feature: bug-15984-recycle-bin-count-perf
requirement_doc: null
created: 2026-09-20
status: in-progress
---

# bug-15984 回收站左侧树 count 统计接口性能

> 工单 #15984；接口 `POST /api/query/recycle_bin_search_count`。bug-fix 流程（复现优先）：本文件记录观察/根因/决策/保护，供后续会话继承。
>
> **阅读顺序建议**：先看「当前实现（V3：一次分组聚合）」——它是线上实际生效的版本；其余标有「历史」的章节是走到 V3 的过程记录与实测证据，其中提到的 `countObjectsByClasses` / `buildQuerySql` / 3 线程池等**代码已不存在**。

## 当前实现（V3：一次分组聚合，2026-09-21）

### 调用链

`SearchRest#recycleBinSearchCount` → `SearchManagement#recycleBinSearchCount`（无缓存）→ `QueryService#recycleBinSearchCount`：

1. 取全部基类 `admin.getAirClasses(AdminEnum.ALL_BASECLASS)`；
2. 构造 `InstCommonQueryParam`：`deleteFlag = RECYCLE_BIN_DELETE_FLAG(1)`、`baseClassIds = 全部基类`、`dynamicParams[RECYCLE_REQUIREMENT_ROOT_ONLY_MIXED] = true`；
3. **一条** SQL 按基类分组统计 —— `IInstCommonViewPersistence#countGroupByBaseClass`：
   `SELECT base_class, COUNT(1) AS total_count FROM air_inst_view_effective_common ${ew.customSqlSegment} GROUP BY base_class`
   其中 where 子句由 MyBatis-Plus 平台能力（`Wrapper#getCustomSqlSegment`）生成，参数仍走 `#{ew.paramNameValuePairs.*}` 预编译绑定；
4. 按 `PreDefinedFolderEnum`（`classId` 非空成员）把分组结果归位到文件夹，缺失分类计 0，"全部（回收站根）= 各分类求和"。

### 条件与列表查询同源（关键设计）

`InstCommonTableServiceImpl#buildSearchQueryWrapper(param)` 是**列表查询与统计共用的**过滤条件构建（keyword、`queryClass` 节点判定、workSpace/baseClass/subClass、各 `eqIfPresent`、`dynamicParams`、回收站、`conditionParams`、`excludeIds`、排除工作集/报表/公共类/TestCase、`filterFinishOrCancel`、发现权限 SQL），`searchByCondition` 在其上追加排序 + 分页，`countGroupByBaseClass` 在其上追加 `base_class in (...)`。

- **需求"回收站根节点"**：`RECYCLE_REQUIREMENT_ROOT_ONLY_MIXED` 生成的是
  `(base_class <> <需求基类> OR (base_class = <需求基类> AND (parent_id IS NULL OR NOT EXISTS (...))))`。
  该条件**只约束需求基类，对其他基类恒真**，因此可与其余基类共用同一条分组统计，无需为需求单独发一次查询（这正是它与列表查询在全量场景下的用法同源之处）。
- 测试运行用例 `type <> <SUB_CLASS_TEST_RUN_CASE_ID>` 在共用条件里是对所有分类无条件生效的（旧逐分类路径只在需求/测试用例副本上追加等效条件）。
- 发现权限沿用列表查询的 `getAppendDiscoverSqlByAdminCriteriasNoAnd`（与 `...Criterias` 仅差 AND 前缀，可见范围一致）。

### 当前 Key files

| Path | Role |
|---|---|
| `infrastructure/sdkImpl/.../query/search/service/QueryService.java` | 统计编排：构造 param → 一次分组统计 → 归位到文件夹；全部 = 求和 |
| `infrastructure/sdkImpl/.../query/search/dataobject/QuickSearch.java` | `countGroupByBaseClass` 委托入口 |
| `infrastructure/sdkImpl/.../service/dataobject/common/InstCommonTableServiceImpl.java` | `buildSearchQueryWrapper`（列表/统计共用条件）+ `countGroupByBaseClass` |
| `infrastructure/sdkImpl/.../dao/persistence/IInstCommonViewPersistence.java` | `@Select` 分组统计 SQL（`${ew.customSqlSegment}`） |
| `infrastructure/sdk/.../sdk/dataobject/dto/BaseClassGroupCountDTO.java` | 分组结果（`baseClassId` / `totalCount`） |
| `infrastructure/sdk/.../common/enums/PreDefinedFolderEnum.java` | 回收站文件夹 ↔ 分类（`classId`），`classId=null` 表示不统计 |
| `web/src/frontend/pages/RecycleBin/index.tsx` | 调用方（3 处 `fetchCountMap`） |

### 当前保护（Protection）

- `QueryServiceTest#recycleBinSearchCount_singleGroupCountQuery`：**一次**分组统计（`times(1)`）+ 参数契约（`delete_flag=1`、全部基类、`RECYCLE_REQUIREMENT_ROOT_ONLY_MIXED=true`）。
- `QueryServiceTest#recycleBinSearchCount_requirementRootAndTotalBySum`：分组结果归位到文件夹、缺失分类计 0、"全部 = 各分类求和"。
- `QueryServiceTest#recycleBinSearchCount_propagatesFailure`：统计失败原样抛出（不静默丢分类）。
- `QueryServiceTest#recycleBinSearchCount_excludesFoldersWithoutClassId`：`classId=null` 的成员不参与统计、不出现在结果中。
- `QueryServiceTest#recycleBinFolder_riskContract`：枚举契约（描述 / 基类 id / 文件夹 id）。
- `InstCommonTableServiceImplTest#testCountGroupByBaseClass_RecycleBinConditionsInSql`：**SQL 级**断言 —— 捕获统计调用实际使用的 wrapper，断言其 where 子句确实携带 `delete_flag` / `base_class IN` / 需求根节点条件（`base_class <> 1401`、`parent_id IS NULL`、`NOT EXISTS`）/ `type <> <测试运行用例>` / `base_class NOT IN`（仅断言入参发现不了"条件未进入 SQL"的回归）。
- 已删除的旧保护（随旧实现一并移除，勿再引用）：`recycleBinSearchCount_batchesNonChangeControlClasses`、`recycleBinSearchCount_executesTypesConcurrently`、`recycleBinSearchCount_includesRiskFolder`、`AdminSessionTest#testCountObjectsByClasses_...`。

### 口径确认（2026-09-21，用户澄清）

- **count 与版次无关**：`air_inst_view_effective_common` 是为"快速查询全局所有分类数据的 id / name / number 等"构建的便捷视图，关键作用是判断数据**是否被软删除**。回收站统计只关心"该分类下是否存在已软删除的行"，因此**不分单版次 / 多版次**，也无需与旧版次表口径（`MainRevQuery` + `LATEST_CHECKIN_REVISION` —— 那是数据对象查询的通用策略，不是回收站计数的语义）对拍。
- **回收站不统计产品空间自身**：`PreDefinedFolderEnum` 中没有产品空间文件夹；`EMPTY_PRODUCT_SPACE_ID` 这类实例（`base_class = ProductSpace`）即使在分组结果里出现一行，也会在按 `classId` 归位时被忽略 —— 统计入口**不需要**补 `excludeIds`。

### 已知缺口（待办，按优先级）

1. **条件构建仍有重复**：`InstCommonTableServiceImpl#countByCondition` 未复用 `buildSearchQueryWrapper`（两者现有差异：`ne(type, Workingset/CommonClass)` + `ne(base_class, Report)` vs `notIn(base_class, 4项)` + `ne(type, TestRunCase)`，且后者多 `excludeIds`/`filterFinishOrCancel`）——差异是否有意为之尚无记录，属口径漂移风险。
2. **命名规范**：`QueryServiceTest` 方法名未遵循 `07-testing.md` 的 `test{MethodName}_{Scenario}()`（沿用了被替换测试的既有风格）；新增的 `InstCommonTableServiceImplTest` 已按该规范命名。

---

## Bug summary

- **Observed**：本机库 `local_dev37`（视图仅 71 行、回收站 3 行）下，前端回收站页调该接口耗时 **8.7s**。
- **Expected**：分类数据量 <1000 时应在数百毫秒内返回；且耗时不应随"分类个数"线性增长。
- **Reproduction**：前端 `web/src/frontend/pages/RecycleBin/index.tsx`（`fetchCountMap`，3 处调用）→ `POST /api/query/recycle_bin_search_count`；或本机 profile `autotestcjc`（DB=`local_dev37`，Redis=`127.0.0.1:63179`）直接调 `IQueryService.recycleBinSearchCount()`。

## 调用链（历史：逐分类统计，已被 V3 取代）

`SearchRest#recycleBinSearchCount` → `SearchManagement#recycleBinSearchCount`（无缓存）→ `QueryService#recycleBinSearchCount`：对 `RecycleStatType` **27 个枚举值**逐个 `session.countObjects(classId, req)`，随后"全部 = 各分类求和"。

单次 `countObjects`：
`AdminSession#countObjects` → `QueryAction#countObjects` = `buildMainQuery(...).executeCount()`；其中
- 主表为 `UNION ALL` 视图 `air_inst_view_effective_common`；
- `onlyDeleted()` → `delete_flag = 1`；
- `withPrivilege=true` → `PrivilegeEngine#getAppendDiscoverSqlByAdminCriterias` → `DisCoveryPrivilegeCoreService#genExistsSqlWithAnd`（权限 `EXISTS` 子查询；结果按 `buildCacheKey(airClasses, isBaseClass, privilegeIds)` **按线程缓存**）；
- 需求/测试用例额外 `parent_id is null OR NOT EXISTS(...)`（`DynamicParamConstants.RECYCLE_REQUIREMENT_ROOT_ONLY_MIXED`）；
- 变更控制分类（`yesOrNoChangeControl()==true`）走 `MainRevQuery` + `DataObjectFlagsEnum.LATEST_CHECKIN_REVISION`（**最新签入版次**，见 `QueryAction#buildMainQuery`）；
- `executeCount()` → `PgSqlParserUtil.toCountSql(toSql())` 重写 + `SqlRunner.db().selectCount(...)`。

## 证据：JDBC 探针（本机库，2026-09-20）

| 测量 | 结果 |
|---|---|
| 视图总行 / 回收站行 | 71 / 3 |
| 单次 count（delete_flag=1 + base_class，无权限子查询） | 2 ms |
| **27 次串行** | **17 ms**（≈0.6 ms/次） |
| 27 次并行（8 连接） | 530 ms（更慢：连接建立主导） |
| 单条 `GROUP BY` 全分类 / 单条多 `FILTER`(3 分类) | 1 ms / 1 ms |
| 执行计划 | `idx_inst_common_tracker_del_time` 索引扫描；Planning 0.4ms / Execution 0.4ms |

**结论：8.7s 不可能来自 SQL 或"27 次往返"**——DB 侧总量 ~17ms。开销必在**应用侧每次调用的固定成本**（权限 SQL 构建、JSqlParser 重写、tracker/工作集解析、Spring/MyBatis 开销），或权限 `EXISTS` 真实形态带来的**规划成本**。这也是 V3 直接把 27 次调用压成 1 次的依据。

## 测量记录（本机库 local_dev37，profile autotestcjc，2026-09-20）

### 应用内插桩（`RecycleBinCountDiagnosticTest` 连续 3 次调用）

| 轮次 | 串行（修复前） | 3 线程定长池 | 提升 |
|---|---|---|---|
| #1（冷） | 1282 ms | **767 ms** | 1.7× |
| #2（热） | 569 ms | **272 ms** | 2.1× |
| #3（热） | 539 ms | **209 ms** | 2.6× |

- 单次 `countObjects`：`buildMs` avg 3.3ms（max 58）→ **构建不是瓶颈**；`execMs` avg 23.8ms（串行）→ **39.4ms（3 线程并发时，含争用）**，说明开销在**执行层固定成本**，与数据量无关（同库 JDBC 直测同形状 SQL 仅 0.6ms/次）。
- **口径对拍**：串行 vs 并行两轮结果**逐类型完全一致**（28 个 key：27 分类 + 回收站根，根=各分类求和）。
- 真权限 SQL 长度 avg 2183 / max 4187 字符 → "巨型 EXISTS 撑爆规划"假设不成立。

### 并行改造中发现并修掉的并发缺陷（历史）

初版并行时 24 个普通类型共享同一个可变 `DynamicListReq`（原串行代码里 `queryReq.setType(classId)` 就地改写它）→ 并行下 `type` 会被其他类型改写（`DynamicListReq#getType()` 被 `DynamicQuery`/`hashCode` 读取，`argThat` 断言直接抓出）。修复：**每类型一份独立请求副本**（`copyRecycleQuery`）；`applyRootOnlyCondition` 改为在副本上追加条件。

> V3 不再有工作线程，上述 TTL 上下文传播 / 请求副本问题随之消失（`PrivilegeParallelTaskExecutorTest` 固化的并行不变量仍对其它并行场景有效）。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|---|---|---|---|
| 2026-09-20 | 用户初选修复方向 = A（分组聚合）+ C（并行） | 工单为性能问题，期望减少 27 次调用 | 纯缓存 / 纯并行 / 前端按需 |
| 2026-09-20 | 先落 **C（3 线程定长池）** 并实测效果 | 用户要求"先看效果"；DB 侧仅 17ms，瓶颈是每次调用的固定开销，并行可直接摊薄 | 直接上 A（需框架能力） |
| 2026-09-20 | 线程池必须 `TtlExecutors` 包装 + 3 守护线程 + `@PreDestroy` 关闭 | 统计链路依赖 `RequestThreadHolder`（用户/工作集/权限），未包装会导致权限过滤失效；`PrivilegeParallelTaskExecutorTest:122` 已固化该不变量 | 裸 `Executors.newFixedThreadPool`；复用 `commonExecutorService`（线程数与用户要求不符） |
| 2026-09-20 | 参数准备留在主线程，只把 `countObjects` 放进工作线程 | `AdminCache` 查询与本类参数构造无需并行；且 Mockito static mock 是线程局部的（测试可断言性） | 整个 `countByType` 进线程 |
| 2026-09-21 | 枚举合并：`RecycleStatType` 并入 `PreDefinedFolderEnum`（`classId` 置于 `desc` 之前），无法映射分类的成员 `classId=null` 且**不参与统计** | 一个枚举表达"文件夹 + 其统计分类"，避免两处枚举漂移；`classId=null` 天然表达"不计入" | 保留两个枚举 + 映射表 |
| 2026-09-21 | **不**改 `DynamicListReq` 增字段；请求上下文用 `QueryService` 局部变量/副本传递 | 用户明确约束；公共 DTO 不应为单场景长状态 | 给 DTO 加缓存字段 |
| 2026-09-21 | 方案 A 只对**非变更控制**分类启用（`IInnerAirClass#yesOrNoChangeControl()==false`） | 变更控制类走版次表（`MainRevQuery` + 最新签入版次），查询形状不同，合并收益不确定且口径风险高 | 全部合并成一条 |
| 2026-09-21 | 合并方式 = **复用每分类自己的框架 SQL** 后 `UNION ALL`（新增窄接口 `IAdminSession#countObjectsByClasses`） | 每个分类的 WHERE（回收站 / 权限 `EXISTS` / 工作集）逐字保持与原 `countObjects` 同源，不重写业务条件，口径风险最低 | 手写 `GROUP BY` 聚合（需重实现权限条件，风险高） |
| 2026-09-21 | 批大小 `RECYCLE_COUNT_BATCH_SIZE = 5` | 批越大调用越少，但单条 UNION ALL 越长；5 在实测中兼顾两者 | 1（等于不改） / 27（一条） |
| 2026-09-21 | 需求 / 测试用例保持逐分类统计 | 这两个分类在**自己的请求副本**上追加"回收站根节点"条件（`RECYCLE_REQUIREMENT_ROOT_ONLY_MIXED`），无法共用同一份请求参数 | 把该条件也塞进合并接口 |
| 2026-09-21 | 取不到类信息（非 `IInnerAirClass`）时**退回逐个统计** | 保守优先于漏统计；串行路径本身正确 | 默认合并 |
| **2026-09-21** | **V3：统计改为一次按基类分组聚合**（`IInstCommonViewPersistence#countGroupByBaseClass`，`${ew.customSqlSegment}` 嵌入 where），废弃 UNION ALL 分批 + 3 线程池；条件抽成 `InstCommonTableServiceImpl#buildSearchQueryWrapper` 供列表/统计共用 | 用户实测"应用后性能明显提升"；与列表查询**同源**使口径漂移风险最低；一次往返、无并行上下文风险、无常驻线程池 | 保留 UNION ALL 合并 / 只合并非变更控制分类 / 手写多 `FILTER` |
| **2026-09-21** | **需求根节点条件经 `InstCommonQueryParam.dynamicParams` 传入统计**（`RECYCLE_REQUIREMENT_ROOT_ONLY_MIXED`） | 该条件写作 `base_class <> 需求基类 OR (...)`，**对非需求基类恒真**，因此可与其余基类共用同一条分组统计，无需为需求单独发一次查询（替代旧方案中"需求必须逐分类"的约束） | 为需求单独发一条统计 SQL |
| **2026-09-21** | **删除 `IAdminSession#countObjectsByClasses`、`AdminSession#countObjectsByClasses`、`QueryAction#buildQuerySql`、`AdminSessionTest`** | 合并统计路径废弃后它们全部失去引用者（死代码）；`buildQuerySql` 的 javadoc 也只在描述该废弃路径 | 保留（死代码 / 过时 javadoc） |
| **2026-09-21** | `delete_flag = 1` 在 `QueryService` 内以私有常量 `RECYCLE_BIN_DELETE_FLAG` 表达 | `08-code-style.md` 禁止魔法值；该值属本类单场景口径，不宜上提 `AirConstants`（对外契约，AirAPI） | 上提 `AirConstants` / 复用 `YesOrNoListEnum.YES` |
| **2026-09-21** | 口径确认：回收站 count **与版次无关**（视图是软删除判定用的便捷视图），且**不统计产品空间自身**；统计入口不补 `excludeIds` | 用户澄清：视图服务"快速查询全局各分类的 id/name/number 并判断是否软删除"，count id 不涉及版次语义；产品空间基类不在 `recycleFolders`，分组结果中的该行归位时被忽略 | 对拍旧版次表口径 / 补 `excludeIds` |
| **2026-09-21** | `BaseClassGroupCountDTO.baseClass` → `baseClassId`（SQL 别名 `base_class AS base_class_id`）；统计 `dynamicParams` 改传可变 `HashMap` 副本；`IAdminSession#countObjectsNP` javadoc 补"不做发现权限过滤"；`AdminSession#buildListQueryContext(req, objectType)` 重载**内联回单参版本** | 命名揭示语义；可变副本避免误写不可变 Map；javadoc 与实现一致；该重载唯一调用者是单参版本（中间人） | 保留原命名 / 保留重载 |

## 方案 A 实现（历史：UNION ALL 分批 + 3 线程池，已被 V3 取代）

- `IAdminSession#countObjectsByClasses(List<Long> objectTypes, DynamicListReq req)`（新增窄接口，javadoc 明确"仅非变更控制分类"）。
- `AdminSession#countObjectsByClasses`：逐分类 `queryAction.buildQuerySql(type, buildListQueryContext(req, type))` 取原 SQL，拼成
  `SELECT <type> AS object_type, COUNT(1) AS cnt FROM (<原SQL>) counted_i UNION ALL ...`，一次 `SqlRunner.db().selectList(...)` 取回，映射 `分类id → 数量`。
  为此把 `buildListQueryContext(req)` 重构为 `buildListQueryContext(req, objectType)` 重载；`QueryAction#buildQuerySql(Object, MainQueryContext)` 为窄暴露（内部仍是 `buildMainQuery`）。
  （**V3 已删除**：`countObjectsByClasses`、`buildQuerySql`；`buildListQueryContext(req, objectType)` 重载仍在，供 `buildListQueryContext(req)` 委托。）
- `QueryService#recycleBinSearchCount`：主线程分桶 →（非变更控制 ∧ 非需求/测试用例）按 5 分批；变更控制 / 需求 / 测试用例进单分类任务；
  批任务与单任务统一投递到既有 3 线程 TTL 定长池（每任务一份 `DynamicListReq` 副本），`awaitAll` 后按枚举顺序汇总、"全部 = 求和"。
- 空 `classId`、缓存中查不到的类：仍然跳过（warn），不进入任何路径（V3 保留该行为：`node == null` 时 `continue`）。

### 口径与性能实测（本机 `local_dev37`，profile `autotestcjc`，2026-09-21，同一次运行内对比）

| 路径 | 耗时 | 说明 |
|---|---|---|
| 串行逐分类（改造前口径） | **848 ms** | 27 次调用 |
| 合并统计（同线程，19 个分类 → 4 批 + 8 个单分类 = 12 次调用） | **323 ms** | 仅减少调用次数 |
| **方案 A + C 实现（合并 + 3 线程池）** | **169 ms** | ≈ **5× 于串行** |
| **V3（一次分组聚合）** | 优于上者（用户实测"明显提升"） | 1 次调用；建议补一次成对测量的具体数值 |

- **逐分类对拍（方案 A/C 时期）**：27 个分类 `serial / batched / service` 三列**全部相等**（非零：物料 1、需求 1、用户 1、用户组 3、测试用例 1、测试集 1、测试运行 1、测试配置 1）；"全部"= 各分类求和 = 9。断言在测试内，不一致即失败。**V3 尚未做同等对拍（见"已知缺口"1）。**

### 审查后修复（历史：2026-09-21，`/skill:review` 发现）

| 级别 | 问题 | 修复 |
|---|---|---|
| High | 专用池 `Executors.newFixedThreadPool` = **无界队列 + 默认拒绝策略**，与项目池约定（`ThreadPoolConfig`：有界队列 + `CallerRunsPolicy`）不一致，池满时无背压 | 改为 `ThreadPoolExecutor(core=max=3, LinkedBlockingQueue(64), CallerRunsPolicy)` + 守护线程命名；队列满时退回调用线程执行（退化为串行但不积压）。**V3 后该池整体移除** |
| Medium | `RequestThreadHolder.DISCOVERY_SQL / DISCOVERY_IDS` 是**未覆写 `copy()` 的 TTL**（按引用透传），并行 worker 会并发写同一个 `HashMap` | 值改为 `ConcurrentHashMap`（javadoc 写明"值必须是并发容器"）——该修复对其它并行场景仍有意义 |
| Medium | `AdminSession#countObjectsByClasses` 与 `QueryService#awaitAll` 错误路径**零测试** | 新增 `AdminSessionTest#testCountObjectsByClasses_EmptyTypes_ReturnsEmptyMapWithoutQuery`（null/空/全 null 短路不查库）；`QueryServiceTest#recycleBinSearchCount_propagatesFirstFailure`（任一分类失败原样抛出）。**V3 后前者随被测方法删除** |
| Low | 「需追加根节点条件」的成员在 `buildTypeQuery` / `canBatchCount` 各硬编码一次 | 提取 `needsRootOnlyCondition(folder)` 单一判定。**V3 后该判定随路径删除** |

- 回归（方案 A/C 时期）：`QueryServiceTest`(7) + `AdminSessionTest`(1) + `QueryActionTest`(29) = **37/37 绿**；`RequestThreadHolderTest` 等 sdk 侧 **66/66 绿**；`spotless:check` 通过。
- 未采纳/未做：H2（`1000000004L` 魔法值，属历史遗留，规则 §4 允许只记 Info）、M2/M4/L1/L2/L4/L5 与 Info 项（保留在审查报告，本次未改）。

## Root cause hypotheses

| # | 假设 | 状态 |
|---|---|---|
| H1 | 27 次串行 SQL、每次 ~322ms 固定开销 | **已否证**（DB 总量 17ms） |
| H2 | 权限 `EXISTS` 真实 SQL 过大 → 每次重写/规划昂贵 | **已否证**（真权限 SQL avg 2183 / max 4187 字符） |
| H3 | 应用侧每类固定开销（权限 SQL 构建 + JSqlParser 重写 + 缓存解析等） | **已确认**（`buildMs`≈3ms vs `execMs`≈24ms；同形状 SQL 直测 0.6ms）→ V3 用"1 次调用"把该类开销压到最小 |
| H4 | 冷启动（AdminCache/权限缓存预热）导致首个请求慢 | 部分成立（#1 冷 vs #2/#3 热：1282ms vs 569/539ms） |

## 仍未解决 / 下一步候选

1. **工单报的 8.7s 与实测（本机改造前 848ms）差一个量级**：需确认线上测量条件（冷/热、用户权限规模、数据量、是否含前端）。V3 已把调用数降到 1 次，剩余开销若仍在应用侧单次固定成本，需要线上打点确认。
2. **条件构建去重**：先判定 `countByCondition` 与 `buildSearchQueryWrapper` 的现有差异是否有意为之，再决定是否合并（见"已知缺口"1）。
3. **方案 B（短 TTL 结果缓存）**：仍未实施；V3 之后收益空间变小，可暂缓。
4. 是否把"一次分组统计"推广到其它"多分类计数"场景（如文件夹树）——本次未动，留作后续。

## Open questions

- **线上 8.7s 的构成**：需要线上打点确认剩余开销在哪一层（权限 SQL 规模 / 连接池 / GC），否则无法判断"数百毫秒"目标是否达成。
