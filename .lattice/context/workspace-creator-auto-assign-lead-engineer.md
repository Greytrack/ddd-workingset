---
feature: workspace-creator-auto-assign-lead-engineer
requirement_doc: null
created: 2026-07-17
related:
  - .lattice/context/relationship-aware-permission-and-workspace-roles.md
  - .lattice/context/workspace-modeling-team-tab-dual-write.md
---

# 工作区创建人自动加入建模主管工程师

> 创建工作区成功后，将当前登录用户自动分配到「建模主管工程师」工作区角色（固定模板 ID），并同步鉴权分配与建模团队页签投影。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-17 | 角色定位：固定模板 ID（种子角色节点 `611677623757088`，显示名「建模主管工程师」/ api 名 `ProcessModeler`） | 跨环境名称可变；ID 与 Flyway 种子一致，解析稳定 | 按显示名；按 apiId 字符串查找 |
| 2026-07-17 | 创建入口：标准创建 + 导入等旁路凡「新建」工作区均触发；复用已有工作区不触发 | 用户选择覆盖旁路，避免导入建区后创建人无角色；复用不重复分配 | 仅 UI/API 标准创建 |
| 2026-07-17 | 双写：鉴权权威（`assignWorkspaceRole`）+ 建模团队页签投影同步 | 与既有 RoleTeam 双写约定一致，页签与鉴权不漂移 | 仅写鉴权，页签手工维护 |
| 2026-07-17 | 创建人 = 当前登录用户（`RequestThreadHolder`） | 用户确认；导入路径已用同一来源设 owner | 对象 `createdBy` 字段 |
| 2026-07-17 | ~~RequestThreadHolder~~ → **创建人/主管分配取 `ATTR_OWNER`**（与同 `postAdd` 内 JobFunction 一致） | Review：消除同钩子双源；Owner 由 `preAdd` 写入，与职能团队引导同源 | `RequestThreadHolder.getLongUserId()`（上一决策，已撤销） |
| 2026-07-17 | 建模主管模板 ID **不**进 `AirConstants`；落在 `ModelingWorkspacesDataObject`（或同模块 sdkImpl 常量），对齐 `WORK_SPACE_OWNER_JOB_FUNCTION_ID` | AirAPI/`AirConstants` 对外契约面，只适合 classId/apiId 等；内部种子 ID 不应暴露 | 写入 `AirConstants`（已否决） |
| 2026-07-17 | 无登录用户：视为异常兜底，**跳过**自动分配并 `warn`；正常路径保证有登录用户 | 业务上不存在无登录创建；防御性兜底避免创建整单失败 | 抛异常使创建失败 |
| 2026-07-17 | ~~跳过~~ → **无 userId 时直接报错**（创建失败回滚） | 用户改判：空会话属严重异常，不应静默成功出「无主管」工作区 | 跳过 + warn（上一决策，已撤销） |
| 2026-07-17 | Design approved at Level 4. Blueprint complete ready for implementation. | L1–L4 均已确认并写入本锚点 | — |
| 2026-07-17 | 实现：引导抽为包内可见 `assignCreatorAsModelingLeadEngineer()`，由 `postAdd` 调用 | 便于单测；不新增公共 SDK API | 逻辑全部内联在 `postAdd` |
| 2026-07-17 | 种子模板 ID 核对通过：`611677623757088`（`ProcessModeler` / 建模主管工程师） | Flyway `V3.5.0.18` 节点与 i18n 一致 | — |

## Open Questions

（无）

## Constraints

- 复用既有 `IWorkspaceRoleAssignmentService` / RoleTeam 双写路径，禁止另建 assignment 表或绕过实例化。
- 事务仅在 biz `*Management`（或文档允许的编排入口）；sdk/sdkImpl 不加 `@Transactional`。
- 模板 ID 禁止魔法字面量散落；**禁止**写入 `AirConstants`；落消费侧 sdkImpl 常量。
- `AirConstants` 仅 classId/apiId 等对外契约常量（见 knowledge-base / operational-learnings）。

## Key Files

| Path | Role | Layer |
|------|------|-------|
| `components/gf/sdk/.../GateflowResultCode.java` | `WORKSPACE_CREATOR_USER_REQUIRED` | gf-sdk |
| `components/gf/sdkImpl/.../ModelingWorkspacesDataObject.java` | 模板 ID 常量 + `postAdd` / `assignCreatorAsModelingLeadEngineer` | gf-sdkImpl |
| `components/gf/sdkImpl/.../ModelingWorkspacesDataObjectTest.java` | 双写成功路径 + 无用户报错 | test |
| `IWorkspaceRoleAssignmentService` | 工作区角色分配（鉴权权威） | infrastructure SDK |
| `upsertRoleTeamRow` / RoleTeam 页签 | 页签投影双写 | DataObject |
| `.lattice/context/workspace-modeling-team-tab-dual-write.md` | 双写模型约定 | design |

## Design: Level 1 -- Capabilities

1. **创建后即具备主管权限**：用户新建工作区成功后，创建人自动成为该工作区的「建模主管工程师」，无需再手工分配。
2. **旁路新建同等生效**：经导入等旁路**新建**的工作区，创建人同样自动获得该角色；**复用已有工作区**不重复分配。
3. **页签与权限一致**：分配后，建模团队页签能看到创建人已在该角色下，与鉴权生效结果一致。
4. **幂等安全**：同一工作区对同一创建人重复触发时，不产生重复成员、不破坏已有其他角色分配。

## Design: Level 2 -- Components

| # | 组件 | 层 | 职责 | DDD |
|---|------|-----|------|-----|
| 1 | `ModelingWorkspacesDataObject` 模板 ID 常量 | gf-sdkImpl | 建模主管角色模板种子 ID | 消费侧内部常量 |
| 2 | `ModelingWorkspacesDataObject.postAdd` | gf-sdkImpl | 创建成功后触发引导 | 聚合根生命周期钩子 |
| 3 | `IWorkspaceRoleAssignmentService` | infrastructure SDK（既有） | `assignWorkspaceRole` | 领域服务（鉴权权威） |
| 4 | `upsertRoleTeamRow` | DataObject / Inner（既有） | RoleTeam 页签投影 | 聚合内页签从属 |

```
新建 ModelingWorkspace → postAdd → [常量模板 ID]
                              ├→ IWorkspaceRoleAssignmentService.assignWorkspaceRole
                              └→ upsertRoleTeamRow（含创建人）
```

明确排除：`AirConstants` / AirAPI 不承载该模板 ID；不经 `RoleTeamManagement` REST 入口。

## Design: Level 3 -- Interactions

### 主路径

1. DataObject 框架创建 `ModelingWorkspacesDataObject` 成功 → 进入 `postAdd`（含既有 Tracker / Owner JobFunction）。
2. 解析工作区 `ATTR_OWNER` 作为主管用户。
3. **若 Owner 为空**：抛异常，创建失败回滚。
4. `assignWorkspaceRole(userId, 模板ID常量, workspaceId)` → getOrCreate 实例 + 追加 assignment。
5. `upsertRoleTeamRow(模板ID, [userId], skipHistory=true)` → 页签投影与鉴权一致。

### 边界

| 场景 | 行为 |
|------|------|
| 新建（含导入旁路） | 走 `postAdd` |
| 复用已有工作区 | 不进 `postAdd` |
| 无 Owner | 报错失败 |
| 重复触发 | assign 追加幂等；页签 upsert |

顺序：先鉴权，再页签。无领域事件。

## Design: Level 4 -- Contracts

### 新增

```java
// ModelingWorkspacesDataObject（gf-sdkImpl）
public static final long MODELING_LEAD_ENGINEER_ROLE_TEMPLATE_ID = 611677623757088L;

// postAdd：既有逻辑之后
// userId = this.getInnerValue(ATTR_OWNER, Long.class);
// null → AppException(WORKSPACE_CREATOR_USER_REQUIRED)
// assignWorkspaceRole(userId, MODELING_LEAD_ENGINEER_ROLE_TEMPLATE_ID, this.getId());
// upsertRoleTeamRow(MODELING_LEAD_ENGINEER_ROLE_TEMPLATE_ID, List.of(userId), true);

// GateflowResultCode
WORKSPACE_CREATOR_USER_REQUIRED(20001309, "创建工作区时 Owner 不能为空");
```

### 复用（签名不变）

- `IWorkspaceRoleAssignmentService.assignWorkspaceRole(userId, templateId, workspaceId)`
- `upsertRoleTeamRow(templateId, participantUserIds, skipHistory)`

### 失败语义

| 条件 | 结果 |
|------|------|
| userId / Owner == null | `WORKSPACE_CREATOR_USER_REQUIRED`，创建回滚 |
| assign / upsert 失败 | 原异常上抛，创建回滚 |

不新增 REST / Management / 公共 SDK 接口。

## Design Summary

- **Components**：`postAdd` 挂载 + DataObject 侧模板 ID 常量 + 既有 `assignWorkspaceRole` + `upsertRoleTeamRow`
- **Key contracts**：`MODELING_LEAD_ENGINEER_ROLE_TEMPLATE_ID`、`WORKSPACE_CREATOR_USER_REQUIRED`；复用既有分配/页签 API
- **Architectural constraints**：不写 `AirConstants`；双写顺序先鉴权后页签；不经 `RoleTeamManagement`；无新 REST
- **Domain**：工作区聚合生命周期引导；角色实例/分配为既有领域服务；页签为聚合内投影
- **Open questions resolved**：固定模板 ID；旁路新建；双写；创建人=登录用户；无 userId → 报错
- **Remaining**：实现前核对种子模板 ID
- **Design status**：Approved — ready for implementation

## Design Checkpoint

- Status: **Approved — ready for implementation**
- Last level: Level 4 (Contracts) — approved 2026-07-17

## Implementation Status

- **Done** 2026-07-17：`GateflowResultCode` + `ModelingWorkspacesDataObject` 引导 + 单测 2 条通过
- 种子 ID 已核对；Open Question 已关闭
