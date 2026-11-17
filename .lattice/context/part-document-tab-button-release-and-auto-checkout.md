# 物料文档页签按钮放开与保存自动签出（任务1335）

- **状态**: draft
- **设计**: Without approved design（用户口头需求 + 完全自主实现模式）
- **分支**: `dev-3.8-zcc`
- **范围红线**: 仅物料（Part）/文档（Document）；Draw（设计文件）排除

## 需求

1. **签入状态放开按钮**：主对象为物料/文档且处于最新版次签入状态时——
   - 物料的 BOM（新建/添加/删除/粘贴）与文档的结构（新建/添加/删除/粘贴）按钮放开为可用；
   - 物料/文档的关联文件页签（上传/拷贝/签出/取消签出/签入/删除/粘贴）按钮放开为可用；
   - 其余按钮（保存/取消/导出等）与历史版次行为维持原逻辑；权限级禁用（最严者胜）不得被越过。
2. **保存自动签出**：上述每个按钮对应的保存 API，操作前检查签出状态，未签出则自动签出后再执行。
   - 用户决策：签入（上传签入）场景自动签出**主对象+目标附件**；其余场景仅主对象。

## 实现方案

### 读侧（按钮可见性）

| 文件 | 变更 |
|------|------|
| `infrastructure/sdk/.../button/TabBomButtonEnum.java` | `LATEST_CHECKIN` 映射中 CREATE/ADD/DELETE/PASTE 由 DISABLE 改为 ENABLE（用户明确允许直接修改该枚举）。`TabBom.queryButtonAfterProcessor` 通过既有 `getMinPriority` 合并，无需改动。 |
| `infrastructure/sdkImpl/.../partdocument/PartDocumentButtonReleaseSupport.java`（新增） | `RELATED_FILE_RELEASED_BUTTON_API_IDS`（7 个关联文件按钮 apiId）+ `shouldReleaseCheckedInButton(phase, parent, buttonApiId, released)`：仅当 phase==LATEST_CHECKIN 且基类 ∈ {PartBaseClass, DocumentBaseClass} 且按钮在放开集合内才返回 true。`TabRelatedVersionFileButtonEnum` 按用户要求未改动，放开逻辑收敛在本 Support。 |
| `infrastructure/sdkImpl/.../dataobject/TabRelatedFileTable.java` | `queryButtonAfterProcessor` 内：命放开条件 → configuredView=ENABLE，再与权限状态 `getMinPriority` 合并（权限 DISABLE/HIDE 依旧最严者胜）。 |

生命周期判定复用 `IPartDocumentCommonAction.getLifeCyclePhaseEnum`（!latest→HISTORY；latest+checkedOut→LATEST_CHECKOUT；else LATEST_CHECKIN）。

### 写侧（保存自动签出）

新增 `infrastructure/biz/.../dataobject/management/PartDocumentAutoCheckOutManagement.java`：

- `autoCheckOutMainForTabWrite(IAirObject)`：instanceof `IInnerRevisionDataObject` → 基类 ∈ {Part, Document}（`getAirBaseClass().getAPIID()`）→ `getAirSubClass().yesOrNoChangeControl()` → `isLatestRevision()` → `!isCheckedOut()` 则 `checkOut()`。
- `autoCheckOutTarget(IInnerRevisionDataObject)`：同上但**不限基类**（签入目标附件由调用方明确指定）。
- 不满足守卫时静默返回（维持原行为：`RevisionTable.checkCanOperation` 原有未签出报错兜底）。

接线点（均在核心操作前、`assertTabEnabled` 之后）：

| 类 | 方法 | 自动签出对象 |
|----|------|--------------|
| `GenericTabManagement` | `createObjectAndMount`（quickCreate 委托覆盖） | `table.getParent()` |
| `GenericTabManagement` | `tabDynamicMount` / `tabDynamicRemoveMount` / `pasteTabMount` | latest-swap 后的 `dataObject` |
| `CommonTabAttachmentManagement` | `uploadFileToTable`（instanceRevisionId 分支后）/ `borrowFileToTable`（latest-swap 后）/ `checkOut` / `cancelCheckOut` | 主对象 |
| `DataObjectManagement` | `checkinInstanceForUploadFile` | 先主对象（mainInstanceId 非空时按 `getObjectByInstanceIdNP` 加载），再 `autoCheckOutTarget(附件)`，最后 `checkInForUploadFile` |

事务语义：自动签出在调用方 `@Transactional` 内执行，后续操作失败整体回滚（签出也随之回滚），保证原子性。

### 明确不做

- `TabRelatedVersionFileButtonEnum` 不修改（用户决策）。
- 权限控制导致的不可操作维持（合并取最严）。
- Draw/设计文件、文件夹页签、子需求页签粘贴分支不在放开范围（守卫自然 no-op）。
- 历史（HISTORY）版次与已签出（LATEST_CHECKOUT）状态行为不变。

## 测试（TDD 红→绿，全部通过）

| 测试类 | 数量 | 覆盖 |
|--------|------|------|
| `TabBomButtonEnumTest`（sdk） | 8 | LATEST_CHECKIN 放开矩阵/其余按钮/签出态/历史态 |
| `TabBomButtonAfterProcessorTest`（sdkImpl） | 5 | TabBom 合并行为：放开生效、权限 DISABLE/HIDE 保持、Save 仍禁用、签出/历史不变 |
| `PartDocumentButtonReleaseSupportTest`（sdkImpl） | 8 | 守卫矩阵 + 7 按钮集合 |
| `TabRelatedFileTableTest`（sdkImpl） | 7 | queryButtonAfterProcessor 集成（Part/Document 签入放开、其他阶段不变、签出态回归） |
| `PartDocumentAutoCheckOutManagementTest`（biz） | 11 | 守卫矩阵（基类/版次控制/最新/已签出/null/非版次对象）+ 目标附件 |
| `CommonTabAttachmentManagementAutoCheckOutTest`（biz） | 5 | 上传/拷贝/签出/取消签出 委托+顺序、空列表不触发 |
| `GenericTabManagementAutoCheckOutTest`（biz） | 5 | 新建挂载/添加/删除/粘贴 委托+顺序、空删除不触发 |
| `DataObjectCheckInForUploadAutoCheckOutTest`（biz） | 2 | 主对象→目标附件→签入 顺序；无主对象ID时仅附件 |
| `CommonTabAttachmentManagementTest`（既有回归） | 12 | 补充新依赖 mock 后全绿 |

## 关键技术事实（实现要点）

1. **mvn 必须 `-am`**：离线 `-pl <module>` 无 `-am` 会用 ~/.m2 里过期 AirAPI 快照导致编译错。
2. `IInnerTable.getParent()` 返回 `IAirObject`（非 IInnerDataObject）→ 守卫入口签名放宽为 IAirObject。
3. `IRelatedFileTable`（AirAPI）与 `IInnerTable`（sdk）是平行接口分支：单测用 `mock(IInnerTable.class, withSettings().extraInterfaces(IRelatedFileTable.class))` 组合；**biz 测试禁止 import sdkImpl 具体类**（TabRelatedFileTable）。
4. `RedissonClient.getList` 泛型推断会让 `when(...).thenReturn` 编译失败，用 `doReturn(...).when(mock).getList(anyString())`。
5. `IPrivilegeEngine.hasPrivilege` 首参是基本型 int，matcher 必须用 `anyInt()`。
6. 构造器注入的类新增依赖后，所有 `@InjectMocks` 既有测试类必须补对应 `@Mock`，否则该依赖注入 null。
7. PowerShell 测试日志：`mvn ... *> $env:TEMP\x.log` 后 `Select-String -CaseSensitive "Tests run:|BUILD SUCCESS|BUILD FAILURE"`。

## 相关文件

- 生产代码：`TabBomButtonEnum.java`、`PartDocumentButtonReleaseSupport.java`、`TabRelatedFileTable.java`、`PartDocumentAutoCheckOutManagement.java`、`GenericTabManagement.java`、`CommonTabAttachmentManagement.java`、`DataObjectManagement.java`
- 既有回归修复：`CommonTabAttachmentManagementTest.java`（补 @Mock）
