---
feature: GF Physical Delete No Recycle
requirement_doc: null
created: 2026-07-24
---

# GF Physical Delete No Recycle

> 引导页（模型导航）、工作区、工作区集合、过程、过程元素、合规范围删除时直接物理删除，不进入回收站；删除前用领域校验提示「被使用」，并避免 `delete2` 触发的全库 ID 扫描。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-24 | 引导页 = ModelNavigation（模型导航） | 与产品文案「引导页」及 `ModelNavigationDataObject` 一致 | 将「引导页」理解为前端路由 `/process/start/options`（非 DataObject） |
| 2026-07-24 | 过程纳入范围：整链删除路径做一致性对齐 | `ProcessDeletionService` 已物理删；需确认校验提示完整、不误入回收站路径 | 过程完全排除出本特征 |
| 2026-07-24 | 工作区集合纳入范围：已有 `delete()+delete2()`，需补齐免全扫与提示对齐 | 今日已物理删但仍会走 `inuseCheck` 全库扫描 | 视为已完成、不改动 |
| 2026-07-24 | 物理删除不得依赖 `search_id_across_database` 全库扫描；「被使用」用领域校验 + 明确 ResCode 提示 | `delete2`→`inuseCheck`→`ReferenceCheck.checkGlobalDbReferences` 对批量元素删除不可接受 | 沿用框架全库扫描换通用提示 |
| 2026-07-24 | Level 1 Capabilities 获批 | 范围与「免全扫 + 领域提示」能力对齐 | — |
| 2026-07-24 | Level 2 Components 获批；跳过全库扫描选 **Option A** | 六类 GF 类 ID 加入 `ReferenceCheck.IGNORE_CHECK_CLASS_IDS`，与 InputOutput 先例一致、改动面小 | Option B：DataObject 可覆写 `skipGlobalReferenceCheck()` 钩子 |
| 2026-07-24 | Level 3 Interactions 获批；合规范围删除采用「先校验被引用 → 再拆关联 → 再物理删」 | 避免半删；失败时关联未动 | 仅靠 preDeleted、Management 仍先拆关联 |
| 2026-07-24 | Level 4 Contracts 获批；工作区 IGNORE 用 **A1**（`ModelingWorkspacesDataObject.SUB_CLASS_ID`） | 工作区 BASE 复用 ProductSpace，用子类 ID 避免扩大产品空间免扫语义 | A2：IGNORE ProductSpace BASE_CLASS_ID |
| 2026-07-24 | Design approved at Level 4. Blueprint complete ready for implementation. | 四级设计已落盘，可交 `/code-forge` | — |
| 2026-07-24 | 实现完成：IGNORE 名单 + DataObject soft+delete2 + 合规先检后拆 + 单测通过 | 对齐 L1–L4；过程整链未改 | — |

## Open Questions

（无）

## Constraints

- 六类对象删除后不可从回收站恢复（不进回收站）。
- 物理删除路径禁止触发 `ValidationUtil.checkIdWhereUsed` / `search_id_across_database` 全库扫描。
- 对象仍被业务依赖时必须阻止删除，并返回可理解的业务提示（非笼统「对象被引用」若领域已有更具体码）。
- 遵循 Biz → SDK → SDKImpl；事务仅在 biz Management；DataObject 生命周期钩子落在 gf-sdkImpl。
- 工作区豁免仅用 `ModelingWorkspacesDataObject.SUB_CLASS_ID`（10030301），不用 ProductSpace BASE。

## Key Files

- `infrastructure/.../ReferenceCheck.java` — IGNORE_CHECK_CLASS_IDS 豁免 GF 物理删类
- `infrastructure/.../ReferenceCheckIgnoreClassIdsTest.java` — 豁免名单单测
- `gf-sdk/.../IComplianceScopeReferenceQuery.java` — 外部引用查询契约
- `gf-sdk/.../GateflowResultCode.java` — `SCOPE_REFERENCED_BY_OTHER_PROCESS`
- `gf-sdkImpl/.../ComplianceScopeReferenceQueryImpl.java` — reference_model 查询实现
- `gf-sdkImpl/.../ModelNavigationDataObject.java` — soft + delete2
- `gf-sdkImpl/.../ModelingWorkspacesDataObject.java` — 递归后 soft + delete2
- `gf-sdkImpl/.../ModelingWorkspaceSetsDataObject.java` — 已有 soft + delete2（未改逻辑）
- `gf-sdkImpl/.../ProcessElementsDataObject.java` — soft + delete2
- `gf-sdkImpl/.../ComplianceScopesDataObject.java` — preDeleted 外部引用 + soft + delete2
- `gf-biz/.../ComplianceScopeManagement.java` — deleteScope 先检后拆
- 过程整链：`ProcessDeletionServiceImpl` + `ProcessDeletionRepository`（未改）

## Design: Level 1 -- Capabilities

1. **直接物理删除、不进回收站** — 引导页（模型导航）、工作区、工作区集合、过程、过程元素、合规范围删除后从库清除，回收站不可见、不可恢复。
2. **被使用时明确业务提示并阻止删除** — 按对象类型做领域依赖校验；失败返回可读业务提示（非依赖全库扫描文案）。
3. **删除校验不拖垮性能** — 物理删除与可删判断不走全库 ID 扫描；批量删元素等场景保持可接受耗时。
4. **过程整链删除语义保持一致** — 按工作区删过程仍走既有整链物理清理，并在接口/合规/模块等依赖场景给出对应提示。
5. **已物理删路径对齐同一套规则** — 工作区集合等已物理删路径与「免全扫 + 领域提示」对齐。

## Design: Level 2 -- Components

### 组件与分层

| 组件 | 分层 | 职责 | DDD |
|------|------|------|-----|
| ReferenceCheck 豁免名单 | infrastructure sdkImpl | GF 目标类 ID 列入 `IGNORE_CHECK_CLASS_IDS`，`delete2` 跳过全库扫描 | 基础设施策略 |
| ModelNavigationDataObject | gf-sdkImpl | 引导页物理删 + 默认页不可删 | 实体 |
| ModelingWorkspacesDataObject | gf-sdkImpl | 工作区物理删 + 子递归；有过程不可删 | 聚合根 |
| ModelingWorkspaceSetsDataObject | gf-sdkImpl | 工作区集合物理删对齐 + 免全扫 | 实体 |
| ProcessElementsDataObject | gf-sdkImpl | 过程元素物理删（单/批共用）+ 免全扫 | 实体 |
| ComplianceScopesDataObject | gf-sdkImpl | 合规范围物理删 + 外部过程引用校验 | 实体 |
| ProcessDeletionService / Repository | gf-sdkImpl | 过程整链物理删与既有依赖提示 | 领域服务 |
| GateflowResultCode | gf-sdk | 「被使用」提示码（复用；合规范围缺则补） | 契约 |

### 架构决策
- 通用实例删除：`DataObject.delete`（软标）→ 同路径接 `delete2` 物理清；豁免后仍跑 criteria/snapshot 等非全库检查。
- 过程整链：继续 `ProcessDeletionService` SQL 物理删，不强制改 `delete2`。
- 页签行：`removeRows`（本就物理）与实例物理删正交。

## Design: Level 3 -- Interactions

### 1. 通用实例物理删除
调用方 → `preDeleted`（领域被使用校验）→ `super.delete()`（软标）→ `delete2()`（criteria/snapshot；类 ID 在 IGNORE 名单则跳过全库扫描）→ `doCompleteDelete` 物理清。

### 2. 工作区删除
`preDeleted`（本区/子区有过程则 `WORKSPACE_CHILDREN_HAS_PROCESS`）→ 递归子区 `delete()` → 本区 soft+`delete2`。

### 3. 过程元素批量删除
`BatchDeleteElementsHandler` 父辈剪枝后根元素 `delete()` → 每实例走流 1（免全扫）→ 既有裁剪链/图缓存副作用。

### 4. 合规范围删除
`deleteScope`：解析 → **先**校验外部 `reference_model` 引用（失败则明确 ResCode，关联未动）→ 拆本过程 Scope/元素页签行 → `scope.delete()` 物理删。

### 5. 过程整链删除
`ProcessDeletionService` 既有校验（`PROCESS_CANNOT_DELETE_*`）+ Repository SQL 物理清；不经 `delete2`；与 IGNORE 名单正交。

## Design: Level 4 -- Contracts

### IGNORE_CHECK_CLASS_IDS（Option A + 工作区 A1）

```text
InputOutput.BASE_CLASS_ID
ModelNavigationDataObject.BASE_CLASS_ID          // 2101
ModelingWorkspacesDataObject.SUB_CLASS_ID        // 10030301 ModelingSpace only
ModelingWorkspaceSetsDataObject.BASE_CLASS_ID    // 2103
ProcessesDataObject.BASE_CLASS_ID                // 2104
ComplianceScopesDataObject.BASE_CLASS_ID         // 2105
ProcessesElement.BASE_CLASS_ID                   // 2106
```

### DataObject 约定

- `delete()`：领域副作用（若有）→ `super.delete()` → `super.delete2()`（不进回收站；免全扫）。
- `preDeleted`：ModelNavigation / Workspace 沿用既有码；ComplianceScopes 新增外部引用拦截。
- `ProcessesDataObject.delete()` 保持抛错；整链走 `IProcessDeletionService.deleteWholeProcessByWorkspace(Long)`。

### 合规范围引用查询

```java
public interface IComplianceScopeReferenceQuery {
    boolean isReferencedByOtherProcesses(Long scopeId);
}
```

- 新增 `GateflowResultCode.SCOPE_REFERENCED_BY_OTHER_PROCESS`（文案：合规范围仍被其他过程引用，不能删除）。
- `ComplianceScopeManagement.deleteScope`：resolve → 引用校验 → 拆页签 → `scope.delete()`。

### 失败码

| 场景 | ResCode |
|------|---------|
| 默认引导页 | `MODEL_NAVIGATION_DEFAULT_CANNOT_DELETE` |
| 工作区仍有过程 | `WORKSPACE_CHILDREN_HAS_PROCESS` |
| 合规范围被外部引用 | `SCOPE_REFERENCED_BY_OTHER_PROCESS`（新增） |
| 过程整链 | 既有 `PROCESS_CANNOT_DELETE_*` |
| criteria / snapshot | 框架既有码（`delete2` 仍保留） |

## Design Summary

- **Components / layers**：infrastructure `ReferenceCheck` 豁免 + gf-sdkImpl 各 DataObject 物理删钩子 + `ProcessDeletionService` 整链对齐 + gf-sdk `GateflowResultCode` / `IComplianceScopeReferenceQuery`。
- **Key contracts**：IGNORE 名单（工作区用 SUB_CLASS_ID）；`delete`→soft+`delete2`；`deleteScope` 先检后拆；过程整链签名不变。
- **Architectural constraints**：不进回收站；禁全库 ID 扫描；领域提示优先；Biz 事务 / sdkImpl 无事务。
- **Domain**：被使用校验按对象类型；合规范围以 `reference_model.related_instance_id` 判定外部引用。
- **Open questions**：无。
- **Design status**：**Approved -- ready for implementation**

就绪后可执行 `/code-forge`，蓝图路径：`.lattice/context/gf-physical-delete-no-recycle.md`。

## Implementation Status

**Completed 2026-07-24** — L3 交互可追溯：通用 soft+delete2、工作区递归、元素批删走 DataObject.delete、合规先检后拆、过程整链未改。依赖方向 Biz→SDK←SDKImpl；无新增越层依赖。
