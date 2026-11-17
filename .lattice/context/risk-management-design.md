# 风险管理设计方案（REQ-3.7-Task0001295）

> 本文档由**需求 + 已落地实现**蒸馏而成，描述风险管理的目标模型与当前代码事实。
> 本地工作文档，不进 Git。范围聚焦：风险对象模型、风险页签、风险矩阵、使用位置、关联数量统计、分类树可见性。
>
> **最近同步（2026-09-23）**：分类树可见性规则重构（`CanCreateTrackerRule` / `RiskBaseClassRule`）；风险矩阵性能优化（批量翻译 / 坐标索引 / 跨视图复用）；风险页签行权限加工；风险版次记录页签；`TabRisksTable` 动态参数门控；补充风险状态机状态变更（弹框 + 目标状态必填）/ 工作流准出条件 / 页签图中编辑关联实例属性。

---

## 1. 范围与非目标

### 1.1 交付能力

| # | 能力 | 落点 |
|---|------|------|
| 1 | 风险数据对象（独立 tracker + 独立状态机 + 版次管控 + 使用位置反向关联） | `RiskDataObject` |
| 2 | 项目风险页签（含「显示所有层级」上卷 + 行权限加工） | `ProgramTabRisksDynamicQueryManagement` |
| 3 | 风险矩阵（初始 / 缓解后 × 威胁 / 机会 × N×M） | `RiskMatrixManagement` + `RiskRest` |
| 4 | 风险「使用位置」页签（风险 → 宿主反向关联） | `RiskWhereUsedDynamicQueryManagement` + `WhereUsedTabQueryFactory` |
| 5 | 风险「版次记录」页签（默认版次升序） | `RiskRevisionRecordsTabDynamicQueryProcessor` |
| 6 | 按宿主实例批量统计关联风险数（过滤已删除） | `IRiskService` / `RiskService` / `RiskTabRepository` |
| 7 | 产品空间分类树可见性（风险基类固定展示 + 通用 `CanCreateTracker` 规则） | `ClassTreeVisibilityEvaluator` + `ClassTreeVisibilityAssembler` |
| 8 | 风险状态机状态变更（目标状态必填弹框 / 候补审批人） | `WorkflowRoutableRest` + `WorkflowApproveRequiredChecked` + 前端 `useApprovalAction` |
| 9 | 工作流提交 = 节点准出条件（匹配条件 + 拦截器 + 必填栏位） | `WorkflowApproveRequiredChecked#checkRequired` |
| 10 | 风险页签「图中编辑」关联实例属性 | `GenericTabManagement#dataObjectDynamicUpdate` + `TabRisksTable` |
| 11 | 风险许可（License）控制 | `LicenseItemEnum.RISK`（L2 编码 `8805`） |

### 1.2 非目标（明确不做）

- **不做染色/分色**：矩阵只返回坐标 + 计数 + 明细，颜色不属接口契约。
- **不返回关联对象明细**：格子只给风险实例标识、精简属性、`smartUrl`。
- **矩阵不设 `scope` 字段**：由入参组合判场景（见 §6.1）。

---

## 2. 领域模型

### 2.1 对象定位

- 风险是一个 **tracker + 状态机** 对象，走修订（revision）体系（`RiskDataObject extends RevisionDataObject`，`RevisionDataObject implements IInnerRevisionDataObject`），可评论、可流转。
- 生命周期：`草稿 → 已识别 → 已应对 → 监控中 → 已解决 / 废弃`；仅**草稿**状态可删除（`RiskDataObject#preDeleted`），且要求「使用位置」「附件」页签均为空。
- 风险**不属于**任何项目节点；通过「**使用位置**」被项目/节点反向引用。
- **状态属性 apiId = `Overview.Status`**（`AirConstants.TableCommonAttribute.ATTR_OVERVIEW_STATUS`）；`AirConstants.Risk.ATTR_STATUS`（`"Status"`）为历史遗留，仅存量取值使用。状态列表由独立状态机节点 `RiskStatus`（`air_sys_node.id=622992094239136`）承载。
- 风险为**版次管控（changeControl）对象**：页签保存时框架先签出再签入（`DataObjectManagement#autoCheckIn`），因此编辑需 **`CHECKOUT` 权限**（见 §5.1）。

### 2.2 关系语义（核心）

风险与宿主的关联落在**页签关联行表** `air_inst_tab_risk`：

- `instance_id` = **宿主**（项目 / 活动 / 任务等计划树节点）
- `related_instance_id` = **风险实例**
- 同一个风险可被多个宿主引用；「使用位置」即按 `related_instance_id` 反查宿主。

### 2.3 评估坐标（两组）

| 视图 | 纵轴（概率） | 横轴（影响） |
|------|-------------|-------------|
| 初始矩阵 `INITIAL` | `Likelihood` | `ImpactLevel` |
| 缓解后矩阵 `POST_RESPONSE` | `PostResponseProbability` | `PostResponseImpactLevel` |

两组坐标中任一缺失/越界，则该风险在该视图计为**未评估**（不进格，计入 `unassessedRisks` / `unassessedCount`）。
**风险类别（`Category`）未填写**时无法确定威胁/机会区，按**未评估计入所有视图**。

---

## 3. 元数据与枚举

### 3.1 属性常量（`AirConstants.Risk`，父节点 `parent_id=30085`）

| 常量 | apiId | 列表/类型 | 用途 |
|------|-------|----------|------|
| `ATTR_CATEGORY` | `Category` | 风险类别列表 | 决定落**威胁**或**机会**区 |
| `ATTR_LIKELIHOOD` | `Likelihood` | 风险发生概率列表 | 初始矩阵纵轴 |
| `ATTR_IMPACT_LEVEL` | `ImpactLevel` | 风险影响等级列表 | 初始矩阵横轴 |
| `ATTR_POST_RESPONSE_PROBABILITY` | `PostResponseProbability` | 风险发生概率列表 | 缓解后纵轴 |
| `ATTR_POST_RESPONSE_IMPACT_LEVEL` | `PostResponseImpactLevel` | 风险影响等级列表 | 缓解后横轴 |
| `ATTR_RISK_TYPE` | `RiskType` | 风险类型列表 | 矩阵明细展示 |
| `ATTR_CONTENT` | `Content` | 富文本 | 详情正文 |
| `ATTR_MITIGATION_STRATEGY` | `MitigationStrategy` | 应对策略列表 | 详情 |
| `ATTR_RESPONSE_ACTIONS` | `ResponseActions` | 长文本 | 应对措施 |
| `ATTR_PROGRESS_NOTES` | `ProgressNotes` | 长文本 | 进度说明 |
| `ATTR_RISK_IMPACT` | `RiskImpact` | 长文本 | 风险影响描述 |

> 公共属性 `Number`（编号）、`Description`（描述）来自 `DataObjectCommonAttribute`。

### 3.2 坐标映射枚举

矩阵坐标统一由**列表项 key → 枚举 ordinal+1** 推导，而非解析中文描述：

| 枚举 | 列表 ID | 映射 |
|------|---------|------|
| `RiskLikelihoodEnum` | `622621994109216` | VeryLow~VeryHigh → 1~5（纵轴） |
| `RiskImpactLevelEnum` | `622621458534688` | 1~5（横轴） |
| `RiskCategoryEnum` | `622620381868320` | `RISK`=威胁 / `OPPORTUNITY`=机会 |

- 枚举 `key` 即 `air_sys_list_item.item_key`，也是属性实际存储值（三者一致）。
- **矩阵尺寸 = 对应列表启用项数量**（`loadAxis` 按 `rank` 升序取 enabled 项），默认 5×5；未启用项不参与坐标。
- 找不到匹配 key 或越界 → 坐标无效 → 未评估。

### 3.3 关键常量

- `TableIds.TABLE_RISKS = 420649`（风险页签）；`AirConstants.TableRisk.API_ID = "Risks"`
- `DynamicParamConstants.PARAM_SHOW_ALL_LEVELS = "showAllLevels"`
- `DynamicParamConstants.DYNAMIC_PARAM_TAB_EDIT_IN_DRAW = "dataObjectDynamicUpdate"`（页签内编辑）
- `DynamicParamConstants.DYNAMIC_PARAM_TAB_REMOVE_AND_DELETE = "tabRemoveAndDelete"`（移除/删除区分）
- `AirConstants.Risk`：`BASE_CLASS_ID=1205` / `CLASS_ID=120501` / `SUB_CLASS_ID=12050101` / `BASE_CLASS_API_ID="RiskBaseClass"` / `CLASS_API_ID="RiskClass"` / `SUB_CLASS_API_ID="Risk"`
- `AirConstants.TableWhereUsed.API_ID = "WhereUsed"`；`AirConstants.TableRevisionRecords.API_ID = "RevisionRecords"`
- `AirConstants.Requirement.BASE_CLASS_ID/CLASS_ID`：需求分类硬编码规则的历史参照（现已由 `CanCreateTrackerRule` 通用化）
- `AirConstants.ChecklistTemplate.API_ID = "ChecklistTemplate"`（检查单模板固定展示）

---

## 4. 数据模型

### 4.1 `air_inst_tab_risk`（页签关联行）

| 列 | 含义 |
|----|------|
| `instance_id` / `instance_type` | 宿主节点 |
| `related_instance_id` / `related_type` | 关联风险实例 |
| `tab_id` / `workingset_id` / `work_space` | 页签上下文 |
| `attributes jsonb` / `flags` / `revision` | 关联行扩展字段 |

> 该表**无 `delete_flag`**；风险删除态需借实例有效视图判断。

### 4.2 `air_inst_risk_rev`（风险修订属性）

- `post_response_impact_register` → `post_response_impact_level`（重命名），列类型从文本修为 `int8`。
- `name` 扩容为 `varchar(1000)`（`V3.7.0.34`）。

### 4.3 视图

- `air_inst_view_effective_common`：由 `air_ws_items_index JOIN air_inst_common` UNION ALL `air_inst_common` 构成，含 `id` / `instance_id` / `delete_flag`；`delete_flag = 0` 表示未删除。**统计与页签查询统一以该视图口径过滤已删除风险。**
- `air_inst_view_risk_tab_whereused`（`V3.7.0.17`）：风险使用位置专用视图，来源 `air_inst_tab_risk`。

### 4.4 迁移脚本（`versionupdates.v3.7/`）

| 脚本 | 内容 |
|------|------|
| `V3.7.0.2__upgradesql.sql` | 风险列表 / 状态机 / 属性节点 / i18n 初始数据 |
| `V3.7.0.3__upgradesql_zxm.sql` | 建 `air_inst_risk_rev`、`air_inst_tab_risk` 及索引 |
| `V3.7.0.12__upgradesql_zxm.sql` | 重建 `air_inst_risk_rev`（表结构定型） |
| `V3.7.0.17__upgradesql_zxm.sql` | 建 `air_inst_view_risk_tab_whereused` 视图 |
| `V3.7.0.18__upgradesql_zxm.sql` | `post_response_impact_register` 重命名 + 转 `int8` |
| `V3.7.0.34__upgradesql_zxm.sql` | `air_inst_risk_rev.name` 扩容 |
| `V3.7.0.36__upgradesql.sql` | 风险状态 i18n / 状态机节点 / 页签按钮权限收口 |

```sql
-- V3.7.0.18
ALTER TABLE public.air_inst_risk_rev RENAME COLUMN post_response_impact_register TO post_response_impact_level;
ALTER TABLE public.air_inst_risk_rev ALTER COLUMN post_response_impact_level TYPE int8 USING NULLIF(post_response_impact_level, '')::int8;
```

> 不改动已提交的 `V3.7.0.2/0.3/0.12/0.17`，通过新增脚本演进。

---

## 5. 关键机制设计

### 5.1 风险页签与「显示所有层级」+ 行权限加工

入口：`ProgramTabRisksDynamicQueryManagement`（`accept = ProgramBaseClass + Risks`）。

```
buildQueryWhere:
  showAllLevels = false 显式才不收窄
  showAllLevels = true  → instanceIds = programService.findAllChildrenIds(NO_BASELINE, instanceId)  // 递归上卷
  showAllLevels = false → instanceIds = [instanceId]                                                // 仅当前节点
  applySql: AND tab_main_alias.instance_id IN (...)
  setQueryCount(false)

queryDataObjectList:  // 结果后置加工
  markChildNodeRowsNotDeletable(rows, currentInstanceId)
  markRowsWithoutCheckoutPrivilegeReadOnly(rows)
```

- 参数来源 `TabListQueryDto.params[showAllLevels]`；**缺省视为 `true`**（`params == null || !Boolean.FALSE.equals(...)`）。
- 页签行数据由 `TabRisksTable` 关联行表链路产生（见 §5.8）。
- **上卷行不可删除**：`markChildNodeRowsNotDeletable` 对 `row.instanceId != currentInstanceId` 的行置 `canChecked = 0`（前端复选框置灰）→ 仅禁止批量删除，**不禁用单元格编辑**。
- **无签出权限只读**：`markRowsWithoutCheckoutPrivilegeReadOnly` 按关联风险实例 `batchHasPrivilege(CHECKOUT)`；无权限的行仅保留 `Overview.Status` 可改（`lockCellsExceptStatus`），其余属性 `canModify=false`。
  - 风险为版次管控对象，编辑需签出权限，故「有编辑权限 + 能签出」才可编辑；状态流转不受限。
- 风险页签还注册了 `ProgramTabRisksUnionQueryManagement`（`accept = ActivityClass + Risks`）：追加 `version` / `flags` 列，过滤 `flags = LATEST_CHECKIN_REVISION`，并按 `workingset + tracker` 条件与 `Type, Number` 排序。

### 5.2 风险矩阵聚合（核心）

入口：`POST /api/risk/matrix`，`RiskMatrixManagement#queryMatrix`。

```
queryMatrix
 ├─ validateScope     // classId 与 instanceId/instanceType 二选一，跨字段显式校验
 ├─ resolveScopeRisks // 场景分发
 │   ├─ 项目场景：queryProgramRisksTabRows → relatedInstanceId 优先（回退 row.id）→ getObjectsByInstanceIds
 │   └─ 产品空间：expandToLeafClasses(classId) → pageObjects(leafClassId, workSpace+workingsetId, 全量)
 ├─ loadAxis(Likelihood / ImpactLevel)          // 启用列表项 → 轴尺寸与 key→level 映射
 ├─ 初始化 views（默认 INITIAL + POST_RESPONSE），每视图架 threat / opportunity 两个 N×M 空区
 │   └─ indexCells(area)：一次性建立「坐标 → 格子」索引（IdentityHashMap）
 ├─ buildRiskItems(risks)：一次性批量翻译全部风险（避免 N+1），明细项跨视图复用
 ├─ 逐风险落格：
 │   ├─ 类别未知 → 该风险计入所有视图 unassessedRisks
 │   └─ 类别已知 → 每视图按对应两坐标 fillCell(cellIndex, ...)；坐标缺失/越界 → 该视图 unassessedRisks
 └─ summarizeArea：格子 riskCount = 明细数，区 riskCount = 各格之和
```

设计要点：

- **明细项批量构建 + 跨视图复用**：`RowDataListTranslator.convertDataObjectList` 一次翻译；同一 `RiskItemResp` 在初始/缓解后视图间共用，避免重复翻译与重复构建。
- **坐标索引 O(1) 落格**：`cellKey(l, r) = l * CELL_KEY_FACTOR + r`（`CELL_KEY_FACTOR = 1000L`），`fillCell` 由遍历匹配改为 Map 查找。
- **每视图独立统计未评估**（同一风险可能在初始已评估、缓解后未评估）；`unassessedCount` 直接取 `unassessedRisks.size()`，两处同源。
- **类别缺失计未评估**：既非威胁也非机会的风险，计入所有视图的 `unassessedRisks`（不再「跳过」）。
- **产品空间按叶子子类枚举**：`classId` 可传基类/中类/子类，递归展开到叶子；`pageNum=-1` 全量，`PAGE_SIZE_ALL=1000` 兜底。
  - ⚠️ 子类节点（`AirSubClass`，`instanceof SubClassNode`）本身即叶子，**必须短路直接收集**：其 `getSubclasses()` 抛 `ILLEGAL_CALL`，继续递归会失败。
- **项目场景复用页签处理器**：上卷逻辑不在矩阵侧重复实现，由 `showAllLevels` 透传，保证与页签口径一致。
- **格子显式携带坐标**：每格返回 `likelihood` / `impactLevel`，调用方无需依赖数组下标。
- **明细精简**：`cells` 仅保留 `Number` / `RiskType` / `Description` 三个属性（白名单过滤）。

### 5.3 使用位置（风险 → 宿主）

- `RiskWhereUsedDynamicQueryManagement`（`accept = RiskBaseClass + WhereUsed`）仅做一件事：`context.setVersionStrategy(null)`，即使用位置查询不限版本策略。
- `WhereUsedTabQueryFactory#create` 按实例类型分派，风险子类命中 `RiskWhereUsedQuery`：

```
Program 基类   → ProgramWhereUsedQuery
Manufacture   → ManufactureWhereUsedQuery
Risk 子类     → RiskWhereUsedQuery        ← 风险
其他          → WhereUsedCommonQuery
```

### 5.4 关联数量统计

`IRiskService#countRelatedRisksByInstanceIds(Collection<Long>) → Map<Long, Long>`：

- 调用方语义：无关联的实例**不出现在 Map**；可 `getOrDefault(id, 0L)` 或 `countRelatedRiskTotal(...)` 求和。
- 一次聚合 SQL 完成，避免 N+1：

```sql
SELECT t.instance_id, COUNT(1)
FROM air_inst_tab_risk t
JOIN air_inst_view_effective_common vec
  ON vec.id = t.related_instance_id AND vec.delete_flag = 0
WHERE t.instance_id IN (...)
GROUP BY t.instance_id
```

- `delete_flag=0` 过滤已删除风险，**与页签查询同口径**。
- 关联行 `related_instance_id` 指向实例 id（非 revision id），join 实例级视图不放大，`COUNT(1)` = 风险数。
- `RiskTabRepository` 负责空入参短路与 null key 过滤，保持 `IRiskService` 契约干净。

### 5.5 页签计数

`ProgramDataObjectTabCountProcessor#processRisksCount` 接入 `IRiskService`，按 `programIds` 求和（`countRelatedRiskTotal`），页签角标与矩阵/页签同口径。

### 5.6 产品空间分类树可见性（风险基类固定展示）

评估器：`ClassTreeVisibilityEvaluator`（纯 POJO，无 Spring 依赖）；调用方 `ClassTreeVisibilityAssembler#shouldShow`。

```
shouldShow(ctx):
  1) isFixedHiddenClass(ctx) → false   // 基类ID / 类ID 命中黑名单：快照、PBS、文件夹、附件、用户、产品空间
  2) RULES.anyMatch(rule -> rule.shouldShowSubClass(ctx))   // OR 语义

RULES = [ CustomTrackerRule, HasInstanceRule, CheckListTemplateRule, CanCreateTrackerRule, RiskBaseClassRule ]
```

| 规则 | 判定 | 语义 |
|------|------|------|
| `CustomTrackerRule` | `ctx.hasCustomTracker` | 有自定义（非默认）tracker → 展示 |
| `HasInstanceRule` | `ctx.instanceCount > 0` | 已有实例 → 展示 |
| `CheckListTemplateRule` | `subClassApiId == ChecklistTemplate.API_ID` | **检查单模板固定展示** |
| `CanCreateTrackerRule` | `ctx.canCreateTracker` | **通用规则**：基类开启 `CanCreateTracker` → 展示（需求这类不再硬编码） |
| `RiskBaseClassRule` | `baseClassId == Risk.BASE_CLASS_ID` | **风险基类固定展示** |

- `ctx.canCreateTracker` 由 `IInnerAirClass#canCreateSubTracker()` 计算（子类未单独配置时继承基类 / 中类）。
- 规则变更史：`RequirementSubClassRule`（按 `Requirement.CLASS_ID` 硬编码）→ 删除，由 `CanCreateTrackerRule` 取代；`RiskSubClassRule`（按 `Risk.CLASS_ID`）→ 改名 `RiskBaseClassRule` 并改为按**基类**匹配。
- 基类节点仅在存在可见子分类时才加入树（`subChildren` 非空），故「固定展示」等价于「其子分类恒可见」。

### 5.7 风险版次记录页签

`RiskRevisionRecordsTabDynamicQueryProcessor`（`accept = RiskBaseClass + RevisionRecords`）：

- 未显式指定排序时，默认按 `attributeId = 4000000054`（版次）**升序**（`queryDto.getOrderParams()` 为空才注入）。
- 其余走 `AbstractDynamicQueryProcessor` 通用链路。

### 5.8 `TabRisksTable` 关联行链路与动态参数

`TabRisksTable extends Table`（`@RegisterTable(apiId = "Risks")`）：

| 方法 | 逻辑 |
|------|------|
| `getRowsByRowIds(ids, relatedIds)` | 读 `DYNAMIC_PARAM_TAB_EDIT_IN_DRAW`；未开启 `Boolean.TRUE` → `super.getRowsByRowIds(...)`；开启 → 私有 `getRows(...)` |
| `getRows(...)` | `tabDesc == null` → 空；`TabQueryFactory.create`；**Program 基类** → `instanceId=null` + `in(INSTANCE_ID, findAllChildrenIds)`；否则 `instanceId=parent.id`，`ids` 非空才 `includeIds`；`relatedIds` 非空 → `includeRelatedIds`；`select(FLAGS)` |
| `postRowsDeleted(rows)` | 读 `DYNAMIC_PARAM_TAB_REMOVE_AND_DELETE`，据此记 `DELETE` 或 `REMOVE` 历史 |
| `handleTableReadAndModifyPrivilege(rows)` | 按 `relatedType` 分组，走 `handleRowPrivilegesByRelatedInstance`（关联对象权限） |

- `RisksTabQuery`（`@TabQueryMapping(apiId="Risks")`）：`applyMainInstanceCondition` 在无自定义条件时 `eq(instance_id, mainInstanceId)`；`getSqlTabId()` 返回 `null`（不参与 SQL 页签聚合）。

### 5.9 风险状态机与状态变更（弹框 + 目标状态必填）

**状态机**：风险为 routable 对象（`RiskDataObject implements IRiskInner`，经 `RevisionDataObject` 获得 routable 能力），状态节点为独立状态机 `RiskStatus`（`air_sys_node.id = 622992094239136`）。动作入口 `WorkflowRoutableRest`（前缀 `/api/workflow/routable`）：提交、批准、拒绝、变更状态、变更到下一个状态、编辑/合并审批人、关注人、转签、复审、终审、撤回、批量操作。

**状态变更（弹框 + 目标状态必填）完整链路**：

```
前端选择目标状态
  → useApprovalAction.runPreActionCheck(ChangeStatus)
      → POST /enter_release_checked {changeId, statusId, changeOperate: true}
          → 后端 RequestThreadHolder["changeOperate"] = true
          → WorkflowApproveRequiredChecked.checkEnterNextStatusRequired → checkStatusRequiredFields(statusId)
              → AdminWorkFlow.getAdminChangeStatusById(statusId).getRequiredFields()
      → 命中仅 P2 必填 → 打开 ApprovalActionWithRequiredFieldsModal
          → POST /api/data_object/dynamic_update 保存必填栏位
      → POST /change_status {instanceId, statusId, comment, content, actors}
  → 若缺候补审批人：POST /assign_user_checked → 打开选人弹窗 → 再 change_status
```

关键点：

- **`changeOperate` 决定是否校验目标状态必填栏位**：`checkStatusRequiredFields` 仅在 `signOffStatus == CHANGE_STATUS` 或动态参数 `changeOperate = true` 时执行；提交/批准等正常操作跳过。
- 响应 `EnterNextStatusCheckResp`：`strongMessages`（强控，阻塞）、`weakMessages`（弱控，仅提示）、`requiredFieldDetails`（空字段明细，供前端高亮定位）、`controlMode`；`approved` 仅由强控是否为空决定。
- **必填消息分层**：`priority=1`（P1：关联物料/文档/页签必填栏位）阻断并直接报错；`priority=2`（P2：封面必填栏位、附件必传、状态变更必填）→ 打开必填弹窗；P1 存在时截断 P2。
- **附件场景**：P2 中 `errorCode = ATTACHMENT_FORMAT_REQUIRED` 时，弹窗为「表单 + 附件」宽窗（`800×560`），否则窄窗（`480×600`）。
- 必填弹窗保存用 `buildRequiredFieldsCellDataList` 组装 `cellDataList`，**只提交有值的栏位**，避免空壳元素。
- ⚠️ 前端风险页签取可选目标状态仍调用已 `@Deprecated` 的 `GET /get_change_status_list/{instanceId}`（`Risks/index.tsx#fetchRiskChangeStatusOptions`，配合 `GET /get_graph/{instanceId}`）。
- **`changeOperate` 与签出无关**：`change_status` 成功后的自动签出由 `autoCheckoutOnStatusChange` 触发（条件：版次管控 + `ReleaseTime` 非空 + 目标状态 ∈ {开始, 批准} + 当前未签出），不读取来源状态。

> 风险页签/矩阵的 Status 列**不做内联编辑**（前端 `canCellEdit: () => false`），改为 `RiskStatusCell` 下拉 + 上述必填弹窗链路。

### 5.10 工作流提交 = 节点准出条件

`submit` → `WorkflowApproveRequiredChecked#checkRequired`：

```
checkRequired
 ├─ obtainCriteriaIds
 │   ├─ 开始节点（TYPE_START）：
 │   │   ├─ criteriaMap[YES] 的匹配条件（准出条件）
 │   │   └─ 准出检查拦截器 IApproveCheckedInterceptor.doReleaseChecked（STRONG→强控 / WEAK→弱控）
 │   └─ 其他节点：当前用户待办任务携带的 includeCriteria
 ├─ checkStatusRequiredFields(currentStateId)   // 退出当前状态必填（P2）
 └─ executeRequiredChecks(criteriaIds)          // P1：物料/文档/页签栏位；P2：封面/附件
      → 强控按 P1 截断 P2，再与拦截器强控合并
```

- **「提交是节点的准出条件」**：提交时（开始节点）校验的是**该节点的准出条件**——匹配条件 + 准出检查拦截器 + 必填栏位，任一强控未过则阻塞流转。
- 非开始节点提交时，校验当前用户待办任务上的 `includeCriteria`。

### 5.11 风险页签「图中编辑」修改关联实例属性

风险页签支持在表格内直接编辑**关联风险实例**的属性，链路：

```
前端页签内保存：PUT /api/dynamic_tab_object/dynamic_many_to_many_save_batch（dynamicTabUpdateBatch）
 → GenericTabManagement.dynamicTabUpdateBatch
     supportTabUpdateRelatedAttrs(table)?
       ├─ true  → dataObjectDynamicUpdate：RequestThreadHolder[dataObjectDynamicUpdate] = true
       │            → TabRisksTable.getRowsByRowIds 走 getRows（Program 基类展开下级 / 否则 includeIds）
       └─ false → commonDynamicTabUpdateBatch（仅更新关联行自身）
```

- **白名单**：`supportTabUpdateRelatedAttrs` 仅对 `ActionItem` / `TestCaseTestSteps` / `TestRunCaseTestRunSteps` / `ReviewContent` / **`Risk`** 页签返回 true；风险页签是「支持修改关联属性」的页签之一。
- **权限**：`ReadAndModifyPrivilegeCoreService.isRelatedAndNotSpecial` 依据 `SPECIAL_RELATED_TABS`（含 `TableRisk.API_ID`）判定关联属性可改；白名单内且 `readOnly != 1` → 可改；不在白名单 → 关联属性一律 `canModify = false`。
- 关联风险属性是否可编辑还叠加 §5.1 的签出权限（无 `CHECKOUT` 权限仅 `Overview.Status` 可改）。

---

## 6. 接口契约

### 6.1 请求 `RiskMatrixQueryReq`

| 字段 | 必填 | 说明 |
|------|------|------|
| `productSpaceId` | ✅ | 产品空间（WorkSpace）实例 ID |
| `workingSetId` | ✅ | 工作集 ID |
| `classId` | 场景一 | 风险分类节点，可传基类/中类/子类，后端展开到叶子 |
| `instanceId` / `instanceType` | 场景二 | 计划树节点，范围含递归上卷 |
| `showAllLevels` | ❌ | 默认 `true`；仅项目场景生效 |
| `viewTypes` | ❌ | 默认 `[INITIAL, POST_RESPONSE]` |

**场景二选一**：`validateScope` 校验 `classId` 与 `instanceId/instanceType` **不能同时提供、也不能都不提供**，否则抛 `ParamException`。

### 6.2 响应 `RiskMatrixResp`

```
RiskMatrixResp
 ├─ totalRiskCount              // 参与统计的风险总数（含未评估）
 └─ views[]
     ├─ viewType                // INITIAL | POST_RESPONSE
     ├─ unassessedCount         // 该视图未评估数（= unassessedRisks.size()）
     ├─ unassessedRisks[]       // 该视图未评估风险明细（RiskItemResp）
     ├─ threat        { riskCount, cells[N×M] }
     └─ opportunity   { riskCount, cells[N×M] }

MatrixCellResp { likelihood(≥1), impactLevel(≥1), riskCount, risks[] }
RiskItemResp   { instanceId, type, cells[Number/RiskType/Description], smartUrl }
```

- `N` / `M` = 概率轴 / 影响轴**启用列表项数量**（默认 5×5），非固定 25。
- `unassessedCount` 与 `unassessedRisks` 同源，避免计数与明细不一致。

---

## 7. 分层落地映射

| 层 | 类 | 职责 |
|----|----|------|
| REST | `RiskRest` | `POST /api/risk/matrix`，薄委托 |
| Biz | `RiskMatrixManagement` | 范围校验、范围解析、N×M 聚合 |
| Biz | `ProgramTabRisksDynamicQueryManagement` | 项目风险页签上卷 + 行权限加工 |
| Biz | `ProgramTabRisksUnionQueryManagement` | 计划树节点风险并集查询（版次列/排序） |
| Biz | `RiskWhereUsedDynamicQueryManagement` | 风险使用位置版本策略 |
| Biz | `RiskRevisionRecordsTabDynamicQueryProcessor` | 风险版次记录默认排序 |
| Biz | `ProgramDataObjectTabCountProcessor` | 风险页签角标计数 |
| Biz | `ClassTreeVisibilityEvaluator` / `ClassTreeVisibilityAssembler` | 产品空间分类树可见性 |
| Biz | `GenericTabManagement` | 页签内编辑（`dataObjectDynamicUpdate`）/ 移除挂载 |
| SDK | `IRiskService` | 关联风险数量契约 |
| SDKImpl | `RiskService` | 委托 Repository |
| SDKImpl | `RiskTabRepository` | 空值短路、结果转换 |
| SDKImpl | `IRiskTabPersistence` | 聚合 SQL（注解 Mapper） |
| SDKImpl | `RiskWhereUsedQuery` / `WhereUsedTabQueryFactory` | 使用位置查询 |
| SDKImpl | `TabRisksTable` / `RisksTabQuery` | 风险页签关联行表 |
| SDKImpl | `RiskDataObject` | 领域属性访问 + 删除态约束 + `smartUrl` |
| SDKImpl | `WorkflowApproveRequiredChecked` | 必填/准出条件校验（分层强控 + 明细） |
| SDKImpl | `ReadAndModifyPrivilegeCoreService` | 关联属性可改判定（`SPECIAL_RELATED_TABS`） |

依赖方向遵守 `Biz → SDK → SDKImpl`；`sdk/sdkImpl` **无 `@Transactional`**；构造器注入。

---

## 8. 关键设计决策

| # | 决策 | 理由 |
|---|------|------|
| 1 | 无 `scope` 字段，靠入参组合判场景 | 减少冗余状态，跨字段显式校验更直观 |
| 2 | 坐标用 `key → ordinal+1`，不解析描述 | 描述承载 i18n、易错位；key 是唯一稳定标识 |
| 3 | 矩阵尺寸随启用列表项动态 | 支持后续扩档，避免硬编码 5×5 |
| 4 | `unassessedCount` 按视图独立、OR 判定 | 两视图数据来源不同，需分开提示未评估数 |
| 5 | 未评估 = 任一坐标缺失/越界；类别缺失亦计未评估 | 与「三要素齐备才入格」一致，避免静默丢数据 |
| 6 | `cells` 白名单三属性 | 矩阵点击只需识别风险，避免下发全量属性 |
| 7 | 不返回关联对象、不做颜色 | 颜色/系数属展示层规则，不进接口契约 |
| 8 | 项目范围复用页签处理器 | 上卷口径单一来源，避免矩阵与页签不一致 |
| 9 | 统计过滤 `delete_flag=0` | 已删除风险不应计入角标与矩阵 |
| 10 | 用注解 `@Select` 聚合而非两段查询 | 项目无 mapper XML，单 SQL 免 N+1 |
| 11 | 列名重命名走新迁移脚本 | 不改已提交迁移，保证 Flyway 校验和稳定 |
| 12 | `classId` 命名（非 `classifyId`） | 与 v2 字段规范一致 |
| 13 | 矩阵落格建坐标索引 + 明细批量翻译/跨视图复用 | 消除线性扫描与逐风险翻译的 N+1 |
| 14 | 分类树通用规则按 `CanCreateTracker` 而非硬编码分类 | 新增可建 tracker 的基类无需再改规则 |
| 15 | 风险基类固定展示（按 `baseClassId`） | 风险子类默认可见，便于直接创建风险 |
| 16 | 上卷行 `canChecked=0` 而非 `canModify=false` | 「不可删除」与「不可编辑」解耦，编辑由签出权限决定 |
| 17 | 目标状态必填在 `enter_release_checked` 预校验、`change_status` 只做变更 | 弹框需要提前拿到 `requiredFieldDetails`，避免变更请求中途失败 |
| 18 | 状态变更必填仅在 `CHANGE_STATUS` / `changeOperate` 时校验 | 提交/批准即校验会误伤正常流程 |
| 19 | 风险页签 Status 列禁用内联编辑，改走 `RiskStatusCell` 下拉 | 状态变更需弹框 + 必填 + 候补审批人，不能单元格式直改 |
| 20 | 关联属性可改用页签白名单而非全量放开 | 只有 `Risk` 等特定页签允许改关联实例属性 |

---

## 9. 测试策略

累计 **16 个测试类 / 113 个用例**（risk + 分类树可见性，`@Test` 统计），全部通过：

| 模块 | 测试类 | 覆盖点 |
|------|--------|--------|
| sdk | `RiskConstantsContractTest` | 属性 apiId、动态参数、许可编码 |
| sdkImpl | `RiskDataObjectTest` | 重命名后用新 apiId 取值、删除态约束 |
| sdkImpl | `RiskTabRepositoryTest` | 空入参短路、聚合转换、null key、重复 key |
| sdkImpl | `RiskServiceTest` | 委托、求和、空集合 |
| sdkImpl | `TabRisksTableTest` | 动态参数门控、program 基类展开、includeIds / includeRelatedIds |
| sdkImpl | `WhereUsedTabQueryFactoryTest` | 风险分支命中 `RiskWhereUsedQuery`、回落通用 |
| sdkImpl | `RiskWhereUsedQueryTest` | 风险使用位置查询 |
| biz | `RiskMatrixManagementTest` | 范围校验、空风险、落格、逐视图未评估、类别缺失、坐标异常、叶子展开、上卷透传、cells 白名单、批量翻译/跨视图复用 |
| biz | `ProgramTabRisksDynamicQueryManagementTest` | `showAllLevels` 默认/显式分支、子节点不可删除、无签出权限只读 |
| biz | `RiskWhereUsedDynamicQueryManagementTest` | `accept` 组合、`versionStrategy` 置空 |
| biz | `RiskRevisionRecordsTabDynamicQueryProcessorTest` | 默认版次升序注入、已有排序不覆盖 |
| biz | `RiskRestTest` | 委托 |
| biz | `RiskMatrixQueryReqValidationTest` | 必填校验 |
| biz | `RiskMatrixViewTypeTest` | 枚举契约 |
| biz | `ClassTreeVisibilityEvaluatorTest` | 各规则 + 评估器 OR 语义 + 固定隐藏黑名单 |
| biz | `ClassTreeVisibilityAssemblerTest` | 分类树装配与可见性接入 |

运行：

```bash
cd backend
./mvnw -pl infrastructure/sdk,infrastructure/sdkImpl,infrastructure/biz -am -o \
  -Dtest='RiskConstantsContractTest,WhereUsedTabQueryFactoryTest,RiskWhereUsedQueryTest,RiskDataObjectTest,RiskTabRepositoryTest,RiskServiceTest,TabRisksTableTest,RiskRestTest,RiskWhereUsedDynamicQueryManagementTest,RiskRevisionRecordsTabDynamicQueryProcessorTest,RiskMatrixQueryReqValidationTest,RiskMatrixViewTypeTest,RiskMatrixManagementTest,ProgramTabRisksDynamicQueryManagementTest,ClassTreeVisibilityEvaluatorTest,ClassTreeVisibilityAssemblerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

> 注：`infrastructure/sdkImpl` 单模块离线编译须带 `-am`（上游 artifact 可能过旧）；出现 MapStruct `toplevel.sourcefile is null` 时删除 `target/generated-sources/annotations` 后重跑。

---

## 10. 遗留问题与后续

| # | 问题 | 影响 |
|---|------|------|
| 1 | `RiskLikelihoodEnum` 的 `desc` 为「极低/低/中/高/极高」，而矩阵纵轴期望「极小/小/中/大/极大」；`RiskImpactLevelEnum` 反之 | 描述错位（坐标映射不受影响，但易混淆） |
| 2 | `RiskMatrixResp` / `RiskMatrixViewType` 的 Javadoc 仍写「5×5 / 25 格」「缓解后未评估按初始值显示」，与实际实现（动态尺寸、不做回退）不一致 | 文档漂移，需随代码校正 |
| 3 | 项目场景风险 id 取值、产品空间 `pageObjects` 的 workSpace/workingset 语义、`POST_RESPONSE` 视图口径未实测 | 需联调验证 |
| 4 | 影响等级 / 应对策略等其余枚举未做契约测试 | 覆盖率 |
| 5 | 风险「待办处理」（`5faa4fa002`）未纳入本文范围 | 待补 |
| 6 | 风险页签前端已消费 `canChecked`（`onRowDisabled` 取 `canChecked === 0 || canView === false`），但状态列 `RiskStatusCell` 仍取已废弃的 `get_change_status_list` | 接口迁移 |
| 7 | 状态变更必填弹窗（`ApprovalActionWithRequiredFieldsModal`）与普通审批弹窗的分流规则较复杂（P1/P2 + 附件宽窗） | 回归覆盖 |
