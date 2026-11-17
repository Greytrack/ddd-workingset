---
feature: Links3 测试管理基础模型
requirement_doc: C:/Users/12275/Downloads/Links3.0需求文档.md
created: 2026-09-03
status: approved
---

# Links3 测试管理基础模型

> 基于 DataObjectFramework 设计测试管理领域的 Test Configuration、Bug、Test Set 三个基类，并明确其与 Test Case、Test Run 的关联边界及数据库元数据配置契约。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
| ------ | ---------- | ----------- | ------------------------ |
| 2026-09-03 | 技术方案范围限定为 Test Configuration、Bug、Test Set 三个基类 | 与当前开发责任一致，避免提前实现 Test Case、Test Run | 将完整测试管理模块纳入同一设计（范围过大） |
| 2026-09-03 | 数据库元数据未完成时不猜测 classId、tabId、属性 ID 与物理字段 | DataObjectFramework 的注册和持久化依赖真实元数据，虚构配置会使方案不可执行 | 先给出临时 ID 和表结构（拒绝） |
| 2026-09-03 | 模块设计名称采用“Links3 测试管理基础模型” | 能准确表达三个基础业务对象及其关联边界，不把范围限定为静态测试资产 | “Links3 测试资产基础模型” |
| 2026-09-03 | Level 1 能力范围确认 | 聚焦测试配置、Test Set、Bug、测试追溯及产品配置驱动扩展 | 同时完整设计 Test Case、Test Run 与 Release（超出当前职责） |
| 2026-09-03 | 三个基类直接继承 DataObject，不继承 RevisionDataObject | 需求明确 Test Configuration、Test Set、Bug 均为非版次类型 | 参考 RequirementDataObject 统一继承 RevisionDataObject（与需求冲突） |
| 2026-09-03 | 业务特化类放在 components/links/sdkImpl | 测试管理属于 Links 业务域，DataObjectRegistry/TableRegistry 可扫描整个 com.airedgesoft 包 | 放入 infrastructure/sdkImpl，造成业务底座继续膨胀 |
| 2026-09-03 | 基础 CRUD 复用 DataObjectRest/DataObjectManagement，不新增平行 REST/Service | 三个对象的通用操作已经由 DataObjectFramework 提供 | 为每个基类新增一套薄 CRUD |
| 2026-09-03 | Test Set 测试用例成员使用专用 TestSetTestCaseTable，并允许重复引用 | 需求明确同一测试用例可在同一测试集中重复添加，成员行还需承载独立顺序 | 使用具有去重语义的普通关联关系 |
| 2026-09-03 | Test Configuration 禁用采用软禁用 | 已有 Test Set/Test Run 保留历史引用，新建或编辑引用时拒绝选择禁用配置，避免破坏测试追溯 | 有引用时禁止禁用；禁用时级联更新所有引用 |
| 2026-09-03 | Test Set 状态语义由“生命周期”改为“启用/禁用”，与 Test Configuration 一致 | Test Set 只有启用/禁用两种状态，且禁用后不允许被新引用；两者复用同一框架 `life_cycle_phase` 列与内置 Status 列表 | 为 Test Set 设计独立状态机或新增 enabled 字段（与现有元数据不符） |
| 2026-09-03 | 启用/禁用取值统一由 `TestManagementStatusRules` 判定，采用「只有明确启用才放行」的严格策略 | 内置 Status 列表项均配置 itemKey，实际落库值即 item_key；节点 id 等其它形态不做映射（无法与 item_key 可靠区分，失败方向定为拒绝），空值与不可识别取值一律拒绝并输出告警 | 仅拒绝 Disabled(0)、其余放行（fail-open，会将脏数据静默放行为“可引用”）；按节点 id 回退映射（`getListItemByKey` 未命中时会回退列表首项，不可靠） |
| 2026-09-03 | 引用约束的落地位置延后到引用方 | Test Set 目前没有任何处于范围内的引用方（元数据中指向 `TestSetBaseClass` 的引用属性仅为 TestSet 自身 Overview 页签的展示绑定），唯一消费方 Test Run 尚未实现；TestSet 的 `test_config` 引用属性是 Test Configuration 的真实引用方，但需新增结果码与写入链路校验 | 在框架 AddAction/UpdateAction 加通用引用校验（覆盖所有引用方，但属基础设施改动，需先评审） |
| 2026-09-03 | Bug 支持测试上下文创建和独立创建 | Bug 还可关联 Requirement、SCM Commit、Release；不应被限制为只能来自测试执行 | 强制 Bug 必须由 Test Run/Test Case 创建 |
| 2026-09-03 | Test Set 成员排序和后续执行均使用成员行 ID，而非 Test Case ID | 同一 Test Case 允许重复出现，只有成员行 ID 能区分不同执行位置 | 使用 Test Case ID 排序和执行（会丢失重复成员语义） |

## Design: Level 2 -- Components

| Component | Layer | Responsibility |
| --- | --- | --- |
| `TestConfigurationDataObject` | `components/links/sdkImpl` | 测试配置聚合根；维护启用/禁用状态、通用属性及单实例约束 |
| `TestSetDataObject` | `components/links/sdkImpl` | Test Set 聚合根；维护启用/禁用状态、顺序执行设置和测试配置关系 |
| `BugDataObject` | `components/links/sdkImpl` | Bug 聚合根；维护缺陷生命周期及解决字段约束 |
| `TestSetTestCaseTable` | `components/links/sdkImpl` | Test Set 聚合内成员表；维护 Test Case 引用、成员行身份和执行顺序，允许重复引用 |

```text
DataObject
├── TestConfigurationDataObject
├── TestSetDataObject
└── BugDataObject

TestSetDataObject
└── TestSetTestCaseTable
    └── member row --ID reference--> TestCaseDataObject
```

### Existing framework integration

```text
DataObjectRest -> DataObjectManagement -> IAdminSession
                                        -> DataObjectRegistry
                                        -> TableRegistry
```

- 三个 DataObject 以及专用 Table 通过注解注册，被现有通用链路创建和加载。
- Test Configuration 与 Test Set/Test Run 的关联优先复用配置驱动的对象引用；元数据未确认前不新增 `TestSetConfigurationTable`。
- 不新增只转发 DataObject 行为的 `*Support` 或平行 CRUD Service。
- Test Case、Test Run 和 Bug 均保持独立聚合，跨对象仅保存 ID 引用。

## Design: Level 3 -- Interactions

### Generic object operations

```text
Client -> DataObjectRest -> DataObjectManagement (transaction)
       -> IAdminSession -> DataObjectRegistry -> specialized DataObject
       -> preAdd/preUpdate -> AddAction/UpdateAction -> postAdd/postUpdate
```

三个基类的创建、查询、编辑、删除均复用这条通用链路；业务特化仅实现启用/禁用引用契约（`isEnabledForNewReference`）与后续约束校验。

### Test Set members

```text
Client (ordered test case IDs, duplicates retained)
  -> load TestSetDataObject once
  -> TestSetTestCaseTable
       -> validate target object type and scope
       -> allocate rank for each input position
       -> create member rows in batch
```

- 每个成员行拥有独立 row ID、关联 Test Case ID 和 rank；删除成员行只解除引用。
- 重排接口接收成员行 ID，先校验其全部归属当前 Test Set，再批量更新 rank。
- 后续 Test Run 创建读取 Test Set ID、顺序执行设置、测试配置引用和有序成员行；是否创建执行快照仍由 Test Run 设计决定。

### Enabled/disabled lifecycle

Test Set 与 Test Configuration 共用同一套“启用/禁用”判定：状态存于框架 `life_cycle_phase` 列（绑定内置 Status 列表，Enabled=item_key 1、Disabled=item_key 0），并同步到 `air_inst_common.status`。

```text
Disable Test Set / Test Configuration
  -> persist disabled state on life_cycle_phase
  -> historical references (Test Set / Test Run) remain
  -> future association validation rejects disabled object
     (isEnabledForNewReference -> TestManagementStatusRules)
```

### Enforcement status

判定规则与契约已就绪，但**引用拦截完全取决于是否有引用方调用**；当前范围内的调用方情况如下：

| 对象 | 消费方 | 状态 |
| --- | --- | --- |
| Test Configuration | Test Set 的 `test_config` 引用属性（新建/编辑 Test Set 时选择配置） | 未接入（需新增结果码与 `preAdd`/`preUpdate` 引用校验） |
| Test Set | Test Run（未实现） | 未接入；范围内不存在其它引用方 |

已核实的元数据事实：

- 指向 `TestSetBaseClass`(1503) 的引用属性仅有 `Overview.Number/Description/Type/Status/Owner`，均属 `TestSetClassAttributes`(30191)，即 TestSet 自身 Overview 页签的展示绑定，**不构成外部引用方**。
- Test Set 上的 `test_config` 引用属性绑定动态条件列表 `AllTestConfig`（`air_sys_list_obj.type=4` DYNAMIC_CRITERIA），其 `criteria_id` 在 `air_sys_criteria` 中**无对应行**，即当前不过滤任何 TestConfig，选择器会提供已停用配置。

因此：在 Test Run 落地（或框架级通用引用校验落地）之前，禁用状态不会阻断任何写入。该缺口已在 `ITestSetInner` / `ITestConfigurationInner` 的契约注释中显式标注。

### Bug lifecycle and origin

```text
Bug update -> load BugDataObject -> validate state transition
  -> resolving requires solution and resolved-release reference
  -> server sets resolved time -> UpdateAction + history

Test Run Management (future) -> loaded execution context -> create BugDataObject
  -> create upstream references to Test Case/Test Run/(optional Test Set)
```

BugDataObject itself does not reverse-load execution objects. An independently created Bug may have no test-origin reference and can later be manually associated.

## Design: Level 4 -- Contracts

### SDK inner contract

```java
public interface ITestConfigurationInner extends IInnerDataObject {
    boolean isEnabledForNewReference();
}

public interface ITestSetInner extends IInnerDataObject {
    boolean isEnabledForNewReference();
}
```

### DataObject contracts

```java
@RegisterDataObject(
    classId = AirConstants.TestConfiguration.BASE_CLASS_ID,
    apiId = AirConstants.TestConfiguration.BASE_CLASS_API_ID
)
public class TestConfigurationDataObject extends DataObject
        implements ITestConfigurationInner {
    boolean isEnabledForNewReference();
    Triple<Boolean, ResCode, Object[]> preUpdate(
        List<ResCode> ignoreWarnings,
        Map<Object, Object> attrParamMap);
}

@RegisterDataObject(
    classId = AirConstants.TestSet.BASE_CLASS_ID,
    apiId = AirConstants.TestSet.BASE_CLASS_API_ID
)
public class TestSetDataObject extends DataObject
        implements ITestSetInner {
    boolean isEnabledForNewReference();
}

@RegisterDataObject(
    classId = AirConstants.Bug.BASE_CLASS_ID,
    apiId = AirConstants.Bug.BASE_CLASS_API_ID
)
public class BugDataObject extends DataObject {
    Triple<Boolean, ResCode, Object[]> preAdd(
        List<ResCode> ignoreWarnings,
        Map<Object, Object> attrParamMap,
        CreateContext context);
    Triple<Boolean, ResCode, Object[]> preUpdate(
        List<ResCode> ignoreWarnings,
        Map<Object, Object> attrParamMap);
}
```

已实现：`ITestConfigurationInner` / `ITestSetInner` 的 `isEnabledForNewReference()`（经 `TestManagementStatusRules` 严格判定），并有 DataObject 层契约测试（桩掉取值读取，验证属性读取与判定衔接）。待实现：`BugDataObject` 的 `preAdd`/`preUpdate` 与 `TestConfigurationDataObject` 的停用 transition 校验，需待状态属性与合法 `item_key` 元数据确认后再落地；**引用拦截的接入（调用方）见 Level 3 的 Enforcement status**。

### Test Set member Table contract

```java
@RegisterTable(
    tabId = TableIds.TEST_SET_TEST_CASE,
    apiId = AirConstants.TestSet.TestCaseTab.API_ID
)
public class TestSetTestCaseTable extends Table {
    Triple<Boolean, ResCode, Object[]> preCreateRows(
        List<ResCode> ignoreWarnings,
        List<CreateRowBo> createRowBoList);
    Triple<Boolean, ResCode, Object[]> preUpdate(
        List<ResCode> ignoreWarnings,
        List<IRow> rows,
        Map<Long, Map<Attribute, Object>> updateAttrs);
    Triple<Boolean, ResCode, Object[]> preDeleteRows(
        List<ResCode> ignoreWarnings,
        List<Row> rows);
}
```

Member row contract: independent row ID, parent Test Set ID, referenced Test Case ID/type, rank, and any configured association category. Duplicate Test Case IDs are valid when row IDs differ. `TestSetTestCaseTable.allowDuplicateMount()` 返回 true，是「允许重复挂载」的唯一声明，同时决定两处行为：(a) 页签「添加」弹窗候选列表（`CommonSingleQuery.getExcludeDataSql()`，经 `TableRegistry.allowDuplicateMount(tabId, apiId)` 反查）不再排除已挂载的测试用例；(b) 挂载时不去重（`shouldFilterExistDataWhenMount()` 返回 `!allowDuplicateMount()`）。框架默认 `Table.allowDuplicateMount()` 为 false，未声明该能力的页签（含已关闭挂载去重的委托/升级页签）行为不变；`preCreateRows` 按输入位置分配单调递增 rank。

关系来源（3.8 定稿）：`air_inst_tab_testcase` 是测试集-测试用例关系的**唯一来源**，只存普通页签成员行；不再写 `air_inst_tab_reference` 框架引用（不做 Reference1 上游/下游建模），也不得反过来用框架引用查询测试集成员。测试用例侧「使用位置」页签（tabId 420669，apiId `WhereUsed`，挂 TestCase 类页签集合 20226，classId 150101）读视图 `air_inst_view_testcase_tab_whereused`：视图只反转成员表两端（instance 侧 = 测试用例、related 侧 = 挂载它的测试集），行 id 仍是成员行 id，不 `DISTINCT`（保留重复挂载行），两端经 `air_inst_view_effective_common` 过滤 `delete_flag`。成员的复制/粘贴/删除只作用于成员行，因此使用位置随成员行自动出现/消失。

### Metadata configuration contract

以下产品/数据库取值已确认（来源：test3.7 元数据 + Flyway `V3.7.0.5` / `V3.7.0.18`）：

- 基类/子类：`TestSetBaseClass` 1503 / `TestSet` 15030101、`TestConfigBaseClass` 1505 / `TestConfig` 15050101、`BugBaseClass` 1502 / `Bug` 15020101。
- 主表：`air_inst_testset`（shared=0，工作集语义）、`air_inst_testconfig`（shared=1）、`air_inst_bug`（shared=1，routableType=2）。
- 页签物理对象：`air_inst_tab_testcase`（420617 TestCases）、`air_inst_tab_bug`（420619/420633 Bugs）、`air_inst_view_testset_tab_subtestset`（420620 SubItems）。
- 启用/禁用状态：复用 `life_cycle_phase` 列，绑定内置 Status 列表（id=1002，apiId="Status"，Enabled=item_key 1 / Disabled=item_key 0）。
- 引用属性：Test Set 上 `test_config`（→ TestConfig，绑定动态列表 `AllTestConfig`）；`Reference1/2/3` 为无类型约束的通用引用（无绑定列表）。
- Test Set / Test Config / Bug 的基类属性容器：30190（TestSetBaseClassAttributes）/ 30196（TestConfigBaseClassAttributes）/ 30112（BugBaseClassAttributes）；中类容器 30191 / 30197 / 30113。

仍待确认（未确认前不得猜测或写死）：

- `Rank` 属性（apiId="Rank"，node 4000000669）是否绑定到 420617 页签；未绑定时写入为安全 no-op。
- Bug 生命周期状态属性与其合法 transition `item_key`，以及 Test Configuration 停用 transition 的取值。
- 关联、附件、评论、操作记录等普通页签的启用情况（当前由框架默认承接）。
- 引用拦截的落地方式：等待 Test Run 实现后在引用方调用契约，还是在框架层新增通用引用校验（一次覆盖所有当前/未来引用方）。

### Table hook correction

The contracts above follow the actual `Table` extension points: row creation uses `preCreateRows`, row update uses `preUpdate`, and row deletion uses `preDeleteRows`. The default `Table.createRows`/`removeRows` methods retain persistence, permission, event, history, and cache behavior; `TestSetTestCaseTable` should normally override validation hooks only, not replace `doCreateRows` or `doRemoveRows`.

## Design Summary

- **Components**: `TestConfigurationDataObject`, `TestSetDataObject`, `BugDataObject`, and `TestSetTestCaseTable` in `components/links/sdkImpl`; SDK inner interfaces `ITestConfigurationInner` and `ITestSetInner`.
- **Aggregate boundaries**: each DataObject is an independent non-revision aggregate; Table rows are subordinate entities. Test Case, Test Run, Requirement, Release and SCM Commit are referenced by ID only.
- **Framework integration**: generic `DataObjectRest/DataObjectManagement` plus `DataObjectRegistry/TableRegistry`; no parallel CRUD stack and no direct SQL in business code.
- **Behavior**: Test Set and Test Configuration share the enabled/disabled status (`life_cycle_phase` + built-in Status list) and reject disabled objects in new references; Test Set members preserve duplicate Test Case occurrences by row identity; Bug supports both execution-origin and independent creation; resolving a Bug requires solution/release and server-generated resolve time.
- **Architecture**: transactions only in future biz `*Management`; SDK contracts remain implementation-free; sdkImpl performs DataObject/Table specialization and cross-instance orchestration.
- **Metadata gate**: implementation starts only after the product/database configuration checklist is supplied; all IDs remain placeholders until then.

## Requirement Drift Check

Level 4 is aligned with the in-scope PRD section “测试模块类之间的关系” onward:

- Test Set groups Test Cases, supports multiple Test Configurations, has ordered execution, and allows repeated Test Case membership.
- Test Configuration is non-revision with enabled/disabled status and is referenced by Test Set/Test Run.
- Bug is non-revision with 创建→解决→验证→关闭 lifecycle, required resolution fields, and test-related references.
- Test Set and Bug do not implement parent/child tracker relationships in this phase.

Technical choices not fully specified by the PRD are recorded as design decisions: soft disable semantics, independent Bug creation, member-row identity for duplicates, and deferral of Test Run snapshot semantics.

## Design Status

Design approved at Level 4. 元数据已确认，基础实现已落地：三个 DataObject（含 Test Set / Test Configuration 的 `isEnabledForNewReference()` 契约）、`TestSetTestCaseTable`、`TestManagementStatusRules` 判定，以及 Flyway `V3.7.0.18` 页签物理对象。

## Design: Level 1 -- Capabilities

1. **测试配置管理**：用户可以创建、维护、启用和停用可复用的测试配置，用于描述测试所需的产品、环境及其他配置条件。
2. **Test Set 管理**：用户可以创建、启用/禁用测试集，维护其中的测试用例及执行顺序，并配置顺序执行策略和适用的测试配置。
3. **Bug 生命周期管理**：用户可以登记、处理、验证和关闭缺陷，并保留缺陷状态变化及责任信息。
4. **测试资产追溯**：Test Set、Bug 能够与 Test Case、Test Run、需求等测试上下文建立可查询的关联，但本阶段不实现 Test Case 和 Test Run 本身。
5. **产品配置驱动扩展**：三个基类的业务属性、子分类和展示页签可由后续产品元数据配置扩展，基础代码不依赖尚未确定的数据库标识和固定字段。

## Open Questions

- 三个基类的 classId、class apiId、子类、主表名与启用页签已确认（见 Metadata configuration contract）；`Rank` 属性绑定与 Bug/Test Configuration 的状态 transition 取值仍待确认。
- Test Configuration 的配置维度采用固定属性、动态自定义属性，还是两者结合，尚未确认。
- Bug 是否必须由 Test Run/Test Case Run 创建，或允许独立创建，尚未确认。
- Test Set 不支持 Tracker 父子关系；本阶段也不设计嵌套 Test Set。
- Test Set 创建 Test Run 时采用引用当前测试用例，还是复制执行快照，需由 Test Run 方案最终确认。

## Constraints

- 使用现有 Biz → SDK → SDKImpl 分层与 DataObjectFramework，不引入新的 ORM 或独立持久化框架。
- 写事务只放在 biz `*Management` 编排入口；sdk/sdkImpl 不新增事务声明。
- Biz 层通过 `ISessionLoader`、`IAdminSession`、`IInnerDataObject` 和页签接口访问数据，不直接使用裸 SQL。
- 分类、页签和属性对外标识使用产品最终确认的 classId/apiId；内部固定实例 ID 不进入 `AirConstants`。
- Test Case、Test Run 不在本方案中完整实现，仅定义三个基类所需的引用方向和协作契约。
- 当前需求来源从“测试模块类之间的关系”章节开始；此前章节不作为本方案范围依据。

## Key Files

| Path | Role |
| --- | --- |
| `C:/Users/12275/Downloads/Links3.0需求文档.md` | Links 3.0 测试模块需求来源 |
| `C:/Users/12275/Downloads/codebeamer QA：测试管理.md` | Codebeamer 测试管理参考模型 |
| `backend/infrastructure/sdkImpl/src/main/java/com/airedgesoft/plm/sdkimpl/service/requirement/dataobjects/RequirementDataObject.java` | DataObject 基类特化参考 |
| `backend/infrastructure/sdkImpl/src/main/java/com/airedgesoft/plm/sdkimpl/service/requirement/dataobjects/SubRequirementTable.java` | 专用页签注册与行为参考 |
| `backend/infrastructure/sdkImpl/src/main/java/com/airedgesoft/plm/sdkimpl/service/common/DataObjectRegistryInitializer.java` | DataObject 注册机制 |
| `backend/infrastructure/sdkImpl/src/main/java/com/airedgesoft/plm/sdkimpl/service/objectframework/DataObject.java` | 通用对象生命周期 |
