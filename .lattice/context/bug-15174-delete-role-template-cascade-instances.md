---
feature: bug-15174-delete-role-template-cascade-instances
requirement_doc: "#15174"
created: 2026-07-27
bug_id: 15174
design_status: Implemented — unit tests passing (80/80)
related:
  - .lattice/context/workspace-delete-cascade-rbac-instances.md
  - .lattice/context/bug-15177-normal-privilege-on-template-role.md
  - .lattice/context/relationship-aware-permission-and-workspace-roles.md
---

# Bug 15174：删除角色模板时级联清理工作区实例

> 已生成实例的角色模板，在全部角色实例无用户/用户组关联时，应允许删除模板，并级联删除该模板在各工作区的角色实例及仅被这些实例使用的权限/条件实例。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-27 | 新建本 context 锚点 | 独立 bug 修复，与 #15130（按 workspace 删）方向不同 | 并入 workspace-delete-cascade 锚点（关注点不同） |
| 2026-07-27 | 删除前置条件选 Option A：全部角色实例无用户/用户组分配才允许删 | 与 bug 描述一致；避免静默剥夺工作区成员角色 | Option B：始终允许删并顺带清分配（否决） |
| 2026-07-27 | 从 Level 1 开始设计 | 跨聚合编排 + 删除策略变更，需完整蓝图 | 直接改 assertDeletableAsTemplate（会漏实例清理） |
| 2026-07-27 | Level 1（Capabilities）5 条定稿 | 用户确认符合预期 | — |
| 2026-07-27 | 级联编排挂在 `IRoleTemplateService`（A1） | 与 #15130 `deleteWorkspaceInstances` 对称；编排知识集中 | A2 全写进 RoleService.deleteRole |
| 2026-07-27 | 专属权限/条件用引用计数判定（B1） | 多模板可共享权限/条件实例，避免误删 | B2 按模板溯源一律删 |
| 2026-07-27 | Level 2（Components）定稿 | 用户确认 A1+B1 | — |
| 2026-07-27 | Level 3（Interactions）定稿 | 用户确认；明确 batchDeleteRole 与现有一致（逐 id 调 deleteRole，事务在 deleteRole 内） | — |
| 2026-07-27 | 占用拒绝码选 C1：保留 `ROLE_TEMPLATE_REFERENCED(10103042)`，改文案 | 调用点少、无新码；语义从「有实例」改为「有成员占用」 | C2 新增独立结果码 |
| 2026-07-27 | `isReferencedByInstances` 保持「是否存在实例」；删除闸门用 `hasAssignedPrincipalsOnInstances` | 查询与删除闸门职责分离 | 改写 isReferenced 语义（易混淆） |
| 2026-07-27 | Level 4（Contracts）定稿 | 用户确认 C1 | — |
| 2026-07-27 | Design approved at Level 4. Blueprint complete ready for implementation. | L1–L4 均已确认并写入本锚点 | — |

## Open Questions

（无）

## Constraints

- 复用 #15130 已有 `deleteWorkspaceInstanceRole/Privilege` 原语及条件实例清理能力，不放宽配置页 `assertMutable*`
- `PrivilegeKind.NORMAL` 权限节点不得物理删除（对齐 #15177）
- 事务：`batchDeleteRole` 不改；级联挂在 `RoleService.deleteRole` 现有 `@Transactional` 内；sdkImpl 级联方法不加新 `@Transactional`
- 实例清理不写 admin history（对齐 #15130）
- 被其他角色实例仍引用的权限/条件实例不得误删（B1）
- 级联编排落在 `IRoleTemplateService.deleteInstancesByTemplate`，由 `RoleService.deleteRole` 在删模板前调用（A1）
- 占用拒绝：码 `10103042` 不变，文案改为「%s的角色实例仍有用户或用户组关联，禁止删除」

## Key Files

| Path | Role |
|------|------|
| `backend/infrastructure/sdk/.../RoleNode.java` | `assertDeletableAsTemplate` 闸门语义 |
| `backend/infrastructure/sdk/.../RoleTemplateResultCode.java` | `ROLE_TEMPLATE_REFERENCED` 文案 |
| `backend/infrastructure/sdkImpl/.../RoleService.java` | `deleteRole` 入口 + 实例占用校验 |
| `backend/infrastructure/sdk/.../IRoleTemplateService.java` | `deleteInstancesByTemplate` |
| `backend/infrastructure/sdkImpl/.../RoleTemplateServiceImpl.java` | B1 编排实现 |
| `backend/infrastructure/sdk/.../IRoleService.java` | `listWorkspaceInstancesByTemplate` / `hasAssignedPrincipalsOnInstances` |
| `backend/infrastructure/sdk/.../IPrivilegeService.java` | `deleteWorkspaceInstancePrivilege` |
| `backend/infrastructure/sdk/.../ICriteriaTemplateService.java` | 暴露 `deleteWorkspaceInstanceCriteria` |
| `backend/infrastructure/biz/.../RoleSettingManagement.java` | `batchDeleteRole`（不改） |

## Design: Level 1 -- Capabilities

1. **无成员占用时可删模板**：当某工作区角色模板在各工作区的全部角色实例均无用户、用户组分配时，管理员可在角色设置中删除该模板（不再因「存在实例」被一律拦截）。
2. **级联清理全部角色实例**：删除模板时，自动清除该模板在所有工作区下生成的 `WORKSPACE_INSTANCE` 角色节点，不遗留孤儿角色实例。
3. **级联清理专属权限/条件实例**：同步清除仅服务于上述角色实例的工作区权限实例与条件实例；共享的 `NORMAL` 普通权限只解除关系、不删除节点；仍被其他角色/模板引用的实例不误删。
4. **仍有成员则拒绝删除**：任一角色实例仍存在用户或用户组分配时，删除被拒绝并给出明确业务提示（不静默删除成员或级联剥夺权限）。
5. **删除原子且幂等**：模板删除与实例清理在同一事务边界内完成；任一步失败整体回滚；重复执行或部分已清理时不报错、不误删他区/他模板数据。

**范围外**：模板自身仍有用户/用户组/部门分配 → `ROLE_USED_DELETE_ERROR`；内置角色与配置页只读闸门不变；不清理 `WORKSPACE_TEMPLATE` / `NORMAL` 配置节点本身。

## Design: Level 2 -- Components

| # | 组件 | 层 | 职责 | 新增/既有 |
|---|------|-----|------|-----------|
| 1 | `RoleSettingManagement.batchDeleteRole` | infra-biz | 事务边界入口（现有调用链不变） | 既有 |
| 2 | `RoleService.deleteRole` | infra-sdkImpl | 模板删除闸门改为「实例是否有成员」；通过后触发级联再删模板节点 | 改语义 |
| 3 | `IRoleTemplateService.deleteInstancesByTemplate` | infra-sdk / sdkImpl | 编排：定位该模板全部角色实例 → 角色→权限→条件逆序清理 | 新增方法 |
| 4a | `IRoleService` | infra-sdk / sdkImpl | 按模板列实例；校验实例成员占用；复用 `deleteWorkspaceInstanceRole` | 扩展 + 复用 |
| 4b | `IPrivilegeService` | infra-sdk / sdkImpl | 复用 `deleteWorkspaceInstancePrivilege`；B1 引用计数后删 | 复用 + 引用判定 |
| 4c | `ICriteriaTemplateService` | infra-sdk / sdkImpl | 暴露单条条件实例清理；B1 引用计数后删 | 扩展 + 复用 |
| 5 | `RoleNode` 领域闸门 | infra-sdk | 废弃「有实例即禁删」；改为「有占用则禁删」 | 改语义 |
| 6 | 缓存 / session 刷新 | infra-sdkImpl | 随角色原语刷新（已有） | 既有 |

```
[infra-biz]                    [infra-sdk]                      [infra-sdkImpl]
RoleSettingManagement
  └─ batchDeleteRole ──────► IRoleService.deleteRole
                               ├─ 闸门：实例无成员？
                               └─► IRoleTemplateService
                                   deleteInstancesByTemplate        │
                                                                    ├─► Role 实例清理
                                                                    ├─► 专属 Privilege 实例清理
                                                                    └─► 专属 Criteria 实例清理
                               └─ 再删模板 RoleNode（配置页路径）
```

**明确排除**：不新建独立 CleanupService；不放宽 `assertMutable*`；不改 REST 契约形状；不碰 GF 工作区删除路径。

**DDD**：Role / Privilege / Criteria 独立聚合；跨聚合同事务编排（对齐 #15130）。NORMAL 为共享配置，不进级联物理删除。

## Design: Level 3 -- Interactions

### Flow A：配置页删除角色模板（主路径）

```
RoleSettingManagement.batchDeleteRole   // 不变：逐 id 调 deleteRole
  └─ RoleService.deleteRole(templateId)  // 现有 @Transactional，每 id 独立事务
       1. assertMutableInAdminConfig / 内置规则（既有）
       2. 若非 WORKSPACE_TEMPLATE → 走既有普通角色删除（不变）
       3. 若是 WORKSPACE_TEMPLATE：
          a. 列出全部角色实例（templateId → WORKSPACE_INSTANCE）
          b. 任一实例存在 USER / USER_GROUP 分配？
             → 是：拒绝删除，不进入级联
             → 否：继续
          c. roleTemplateService.deleteInstancesByTemplate(templateId)
          d. 删除模板自身（既有：节点 + 关系 + 模板侧 assignments 检查 + history）
```

### Flow B：`deleteInstancesByTemplate`（B1 引用计数）

```
1. 列出 roleInstances = 该模板全部 WORKSPACE_INSTANCE
2. 【先收集】对各 roleInstance：
     - 查 ROLE↔PRIVILEGE → candidatePrivilegeIds
     - 仅保留 PrivilegeKind.WORKSPACE_INSTANCE（跳过 NORMAL）
     - 从这些权限实例收集 candidateCriteriaIds
3. 【删角色】逐个 deleteWorkspaceInstanceRole
4. 【删专属权限】candidatePrivilegeId 无剩余 ROLE↔PRIVILEGE → deleteWorkspaceInstancePrivilege
5. 【删专属条件】candidateCriteriaId 无剩余权限引用 → deleteWorkspaceInstanceCriteria + 缓存对齐
6. 无实例：幂等 no-op
```

顺序：**先收集 → 角色 → 权限 → 条件**。

### 边界场景

| 场景 | 行为 |
|------|------|
| 无任何角色实例 | 跳过级联，直接删模板 |
| 有实例但均无 USER/USER_GROUP | 级联后删模板 |
| 任一实例有 USER/USER_GROUP | 拒绝，不删任何东西 |
| 权限/条件仍被其他角色/权限引用 | 保留 |
| NORMAL 权限 | 只解绑，不删节点 |
| 清理中途失败 | 整段回滚（`deleteRole` 事务） |

### 事务

- `batchDeleteRole` **不改**：与现有一致，逐个 `roleService.deleteRole`
- 级联与删模板共享 `RoleService.deleteRole` 现有 `@Transactional`
- sdkImpl 级联方法**不加新** `@Transactional`

## Design: Level 4 -- Contracts

### 编排

```java
// IRoleTemplateService
void deleteInstancesByTemplate(Long templateId);
// isReferencedByInstances：保持「是否存在实例」；删除闸门不复用此方法
```

### 角色域

```java
// IRoleService
List<RoleNode> listWorkspaceInstancesByTemplate(Long templateId);
boolean hasAssignedPrincipalsOnInstances(Long templateId);
void deleteWorkspaceInstanceRole(Long roleId); // 既有

// RoleNode
void assertDeletableAsTemplate(boolean hasAssignedPrincipals);
// WORKSPACE_TEMPLATE && hasAssignedPrincipals → ROLE_TEMPLATE_REFERENCED

// RoleService.deleteRole（签名不变）
// WORKSPACE_TEMPLATE：assertDeletableAsTemplate(hasAssignedPrincipalsOnInstances)
//   → deleteInstancesByTemplate → 既有删模板节点
```

### 权限 / 条件

```java
// IPrivilegeService（既有）
void deleteWorkspaceInstancePrivilege(Long privilegeId);

// ICriteriaTemplateService（新增对外）
void deleteWorkspaceInstanceCriteria(Long criteriaId);
```

B1 引用计数留在 `RoleTemplateServiceImpl` 编排内，不上升为独立 SDK 方法。

### 失败类型

| 条件 | 行为 |
|------|------|
| `templateId == null` | `PARAM_CHECK_ERROR` |
| 实例有 USER/USER_GROUP 占用 | `ROLE_TEMPLATE_REFERENCED(10103042)`，文案：「%s的角色实例仍有用户或用户组关联，禁止删除」 |
| 模板自身有 users/userGroups | 既有 `ROLE_USED_DELETE_ERROR` |
| 非 WORKSPACE_INSTANCE 进原语 | 既有 NOT_WORKSPACE_INSTANCE 码 |
| 节点不存在 | 幂等跳过 |

### 不改动

`batchDeleteRole`；REST；`deleteWorkspaceInstances(workspaceId)`；`assertMutable*`。

## Design Summary

- **组件与分层**：`RoleService.deleteRole`（闸门 + 调用）→ `IRoleTemplateService.deleteInstancesByTemplate` 编排 → 复用 `deleteWorkspaceInstanceRole/Privilege` + 暴露 `deleteWorkspaceInstanceCriteria`。
- **关键契约**：`deleteInstancesByTemplate(templateId)`；`listWorkspaceInstancesByTemplate`；`hasAssignedPrincipalsOnInstances`；`assertDeletableAsTemplate(hasAssignedPrincipals)`；C1 保留 `10103042` 改文案。
- **架构约束**：`batchDeleteRole` 行为不变；不放宽配置页只读闸门；B1 不误删共享实例；NORMAL 不物理删除；实例清理不写 history。
- **领域模型**：仅清该模板的角色实例及专属权限/条件实例；模板配置节点在级联后按配置页路径删除。
- **已解决开放问题**：A1/B1/C1；`isReferencedByInstances` 保持存在性查询；事务挂在现有 `deleteRole`。
- **Design status**：**Approved — ready for implementation**
