---
feature: workspace-delete-cascade-rbac-instances
requirement_doc: "#15130"
created: 2026-07-25
related:
  - .lattice/context/relationship-aware-permission-and-workspace-roles.md
  - .lattice/context/workspace-creator-auto-assign-lead-engineer.md
  - .lattice/context/gf-physical-delete-no-recycle.md
design_status: Implemented
---

# 删除工作区级联清理 RBAC 实例（角色 / 权限 / 条件）

> #15130：当前工作区没有过程，但删除被条件实例拦截（`OBJECT_USED_IN_CRITERIA`）。删除工作区时应顺带清理该工作区实例化产出的角色 / 权限 / 条件实例，以及对应的用户分配，使空过程工作区可正常删除。

## 根因（代码核实）

- 创建 / 分配工作区角色时，`IRoleTemplateService` 会实例化 `WORKSPACE_INSTANCE` 的角色、权限、条件；条件实例（`AdminCriteria`）的行 `value` 绑定 workspaceId。
- 删除走 `ModelingWorkspacesDataObject.delete()` → `delete2()` → `DataObject.inuseCheck()` → `ICriteriaWhereUsedService.checkReferencedByCriteria(id)`，命中条件实例引用即抛 `OBJECT_USED_IN_CRITERIA`，即使工作区无过程也被拦截。
- 删除侧目前缺少与创建侧对称的实例清理逻辑。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-25 | 新建本 context 锚点（非复用创建侧锚点） | 删除侧级联清理是独立关注点，创建侧锚点已 Approved | 复用 relationship-aware 锚点 |
| 2026-07-25 | 从 Level 1 开始（多组件缺陷修复：GF 生命周期 + infrastructure RBAC） | 跨组件、有边界与顺序判断 | 从 Level 2/4 直接入手 |
| 2026-07-25 | 清理范围：该工作区 `WORKSPACE_INSTANCE` 角色/权限/条件 + `air_user_assignments` 工作区分配；模板不动 | 用户确认；模板为共享配置 | 连模板一起删（否决） |
| 2026-07-25 | 递归删除已核实存在：`getAllChildrenDataObjects()` 为 PG 递归查询返回**所有层级**后代，`delete()` 对每个后代再调同一重写方法 | 代码核实，非假设 | 假定仅删直接子级 |
| 2026-07-25 | 清理逻辑挂在「工作区删自身」处，而非遍历全后代一次性清 | 天然随既有递归覆盖每层，与删除粒度对齐；避免与重复递归叠加放大 | 在顶层一次性批量清理全后代 |
| 2026-07-25 | Level 1（Capabilities）5 条能力定稿 | 用户确认 | — |
| 2026-07-25 | 清理契约放在 `IRoleTemplateService`（A1） | 与 `instantiateWorkspaceRole` 生命周期对称；依赖集已含 role/privilege/criteria | 新建 `IWorkspaceRbacInstanceCleanupService`（A2） |
| 2026-07-25 | 各领域服务新增实例清理原语（B1），不绕底层 repository | 清理知识留在各自聚合；方法内断言必须为 WORKSPACE_INSTANCE | 编排器直调 `IAdminNode.deleteNodes`（B2） |
| 2026-07-25 | 既有 `deleteRole`/`deletePrivilege`/`deleteByCriteriaId` 不可复用 | 均有配置页只读闸门，对实例必抛 READONLY | 放宽闸门（会破坏配置页保护） |
| 2026-07-25 | Level 2（Components）定稿 | 用户确认 A1+B1 | — |
| 2026-07-25 | 挂载时机 C1：递归删子之后、`super.delete()` 软删之前清理 RBAC | 工作区仍存活时清理，失败时尚未软删，语义清晰 | C2 软删与硬删之间 |
| 2026-07-25 | Level 3（Interactions）定稿 | 用户确认 C1 | — |
| 2026-07-25 | 原语命名 D1：按 ID 单删 `deleteWorkspaceInstanceRole/Privilege/Criteria` | 与既有 `deleteRole(Long)` 风格一致；定位留编排器 | D2 各服务按 workspaceId 自查自删 |
| 2026-07-25 | Level 4（Contracts）定稿 | 用户确认 D1 | — |
| 2026-07-25 | Design approved at Level 4. Blueprint complete ready for implementation. | L1–L4 均已确认并写入本锚点 | — |
| 2026-07-25 | 实例清理原语**不写** admin history / 审计历史 | 工作区实例为系统派生数据，非用户配置；删工作区时无需保留实例删除审计 | 对齐 `deleteRole` 写 history（否决） |
| 2026-07-25 | Implemented：SDK 契约 + 三原语 + RoleTemplate 编排 + GF C1 挂载；单测通过 | 用户确认全自动实现 | — |
| 2026-07-26 | 工作区清理路径：删条件实例前清空 AdminCache 上该实例的 `criteriaRowInfo`；不改 AdminNode afterCommit 刷新 | inuseCheck 同事务读缓存行值；用户确认不改底层缓存刷新机制 | 在 batchDelete/deleteNodes 同步 `excuteRefreshNodeCache` |
| 2026-07-26 | review 收口：改为「按 DB 真相对齐缓存」——`CriteriaBaseNodeService.reconcileCacheFromDb` 删后 reload（DB 已删→缓存移除），回滚钩子 `afterCompletion(STATUS_ROLLED_BACK)` 再 reload（DB 恢复→缓存复原）；`batchDelete` 保持纯净 | 就地清空 `criteriaRowInfo` 回滚不复原、半截缓存；单一 reload 语义同时覆盖正向与回滚，仅作用清理路径 | 继续用 masking + 单独回滚复原 |
| 2026-07-26 | `listWorkspaceInstanceIds` 改为属性索引 ∪ AdminCache 扫描（原为缓存非空即跳过索引） | 单一来源滞后会漏删→仍触发 OBJECT_USED_IN_CRITERIA | 缓存优先、empty 才 fallback |
| 2026-07-26 | `ICriteriaWhereUsedService` 构造器注入（原 `SpringContextUtils.getBean`） | sdkImpl 优先 DI；无循环依赖（where-used 用字段注入，不回指本类） | 保留 getBean |

## Open Questions

- 既有隐患（本次不修）：`children` 已含全层级后代，而每个 `delete()` 又递归删自己的后代 → 深层节点被重复删除。仅记录，不扩大范围。

## Implementation Notes

- 清理顺序：角色 → 权限 → 条件（创建逆序）；无实例幂等 no-op。
- Session 刷新必须在删 assignments **之前**（`refreshUserSessionsByRoleId` 先 collect 用户）。
- 外层 `DataObjectManagement.dynamicDelete` 的 `TransactionalExecutor`（`PROPAGATION_REQUIRED`）包住整个 `dataObject.delete()`，清理与软/硬删同事务；sdkImpl 未加 `@Transactional`。
- 条件落点：`ICriteriaTemplateService.deleteWorkspaceInstanceCriteria` + `listWorkspaceInstanceIds`（非 `CriteriaBaseNodeService` 对外契约）。
- 结果码：`ROLE_NOT_WORKSPACE_INSTANCE(10103054)`、`PRIVILEGE_NOT_WORKSPACE_INSTANCE(10103055)`、`CRITERIA_NOT_WORKSPACE_INSTANCE(10106060)`。

## Constraints

- 事务仅在 biz `*Management`（或文档允许的编排入口）；sdk/sdkImpl 禁止 `@Transactional`。
- 组件隔离：`gf-sdkImpl` 经 infrastructure SDK 契约调用 RBAC 清理能力，禁止反向依赖 infrastructure-biz。
- 复用既有实例定位能力（`RoleNode/PrivilegeNode/AdminCriteria.workspaceInstanceFilters`、`listWorkspaceInstances`），禁止绕过实例化模型另写 SQL。
- 禁止魔法字面量；模板/种子 ID 不进 `AirConstants`。
- 仅清理 `WORKSPACE_INSTANCE` 形态，`WORKSPACE_TEMPLATE` / `NORMAL` 不受影响。
- 不放宽配置页 `assertMutable*`；不白名单绕过 `inuseCheck`。

## Key Files

| Path | Role | Layer |
|------|------|-------|
| `gf-sdkImpl/.../ModelingWorkspacesDataObject.java` | 工作区删除入口（C1 挂载点） | gf-sdkImpl |
| `infra-sdk/.../rolepriv/IRoleTemplateService.java` | `deleteWorkspaceInstances` | infra-sdk |
| `infra-sdk/.../rolepriv/IRoleService.java` | `deleteWorkspaceInstanceRole` | infra-sdk |
| `infra-sdk/.../rolepriv/IPrivilegeService.java` | `deleteWorkspaceInstancePrivilege` | infra-sdk |
| Criteria SDK / `ICriteriaTemplateService` | `deleteWorkspaceInstanceCriteria` + `listWorkspaceInstanceIds` | infra-sdk |
| `infra-sdkImpl/.../rolepriv/service/RoleTemplateServiceImpl.java` | 清理编排实现 | infra-sdkImpl |
| `infra-sdkImpl/.../rolepriv/service/RoleService.java` | 角色实例清理原语 | infra-sdkImpl |
| `infra-sdkImpl/.../rolepriv/service/PrivilegeService.java` | 权限实例清理原语 | infra-sdkImpl |
| `infra-sdkImpl/.../rolepriv/service/CriteriaTemplateServiceImpl.java` | 条件实例清理原语 | infra-sdkImpl |

## Design: Level 1 -- Capabilities

1. **空过程工作区可正常删除**：当前工作区（及其子工作区）无过程时，删除不再被工作区实例化的条件所拦截。
2. **级联清理实例化角色 / 权限 / 条件**：删除工作区时，同步清除该工作区实例化产出的 `WORKSPACE_INSTANCE` 角色、权限、条件实例；共享模板（`WORKSPACE_TEMPLATE` / `NORMAL`）不受影响。
3. **同步清理工作区成员分配**：移除指向这些角色实例的 `air_user_assignments`，受影响用户权限随之失效。
4. **子工作区一并清理**：递归删除子工作区时，各自的角色 / 权限 / 条件实例与成员分配同样被清理，不遗留孤儿数据。
5. **删除保持原子与幂等**：清理与删除在同一事务边界内完成，任一步失败整体回滚；重复触发或部分已清理时不报错、不误删他区数据。

**范围外**：过程存在时的拦截规则（`WORKSPACE_CHILDREN_HAS_PROCESS`）保持不变。

## Design: Level 2 -- Components

| # | 组件 | 层 | 职责 | 新增/既有 |
|---|------|-----|------|-----------|
| 1 | `ModelingWorkspacesDataObject` 删除钩子 | gf-sdkImpl | 工作区删自身前触发 RBAC 实例清理 | 既有类，加调用 |
| 2 | `IRoleTemplateService.deleteWorkspaceInstances(workspaceId)` | infra-sdk / sdkImpl | 清理编排：定位该区实例，按角色→权限→条件逆序清理 | 新增方法 |
| 3a | `IRoleService.deleteWorkspaceInstanceRole` | infra-sdk / sdkImpl | 断言 WORKSPACE_INSTANCE；删角色节点 + 角色-权限关系 + user/userGroup assignments | 新增方法 |
| 3b | `IPrivilegeService.deleteWorkspaceInstancePrivilege` | infra-sdk / sdkImpl | 断言 WORKSPACE_INSTANCE；删权限节点 + 关系 + 权限缓存 | 新增方法 |
| 3c | `deleteWorkspaceInstanceCriteria` | infra-sdk / sdkImpl | 断言 WORKSPACE_INSTANCE；删条件行 + 条件节点 | 新增方法 |
| 4 | 权限缓存 / 用户 session 刷新 | infra-sdkImpl | 清理后失效受影响缓存 | 既有能力复用 |

```
[gf-sdkImpl]                         [infra-sdk]                 [infra-sdkImpl]
ModelingWorkspacesDataObject
  └─ delete() 删自身前 ──────────► IRoleTemplateService
                                   deleteWorkspaceInstances         │
                                                                    ├─► IRoleService 实例清理
                                                                    ├─► IPrivilegeService 实例清理
                                                                    └─► Criteria 实例清理
```

**明确排除**：不放宽配置页 `assertMutable*`；不新建独立 CleanupService；不白名单绕过 `inuseCheck`（会留孤儿）。

**DDD**：Role / Privilege / Criteria 为独立聚合；跨聚合同事务编排与创建侧 `instantiateWorkspaceRole` 保持一致（非领域事件）。

## Design: Level 3 -- Interactions

### 挂载时机（C1）

```
ModelingWorkspacesDataObject.delete():
  1. 递归删除子工作区（各自走完整 delete，含各自 RBAC 清理）
  2. deleteWorkspaceInstances(this.getId())   ← 软删之前
  3. super.delete()   // 软删
  4. super.delete2()  // inuseCheck 通过 → 物理删除
```

### 清理顺序（创建侧逆序）

角色实例 → 权限实例 → 条件实例

### 主路径数据流

1. GF：`workspaceId = this.getId()` → `IRoleTemplateService.deleteWorkspaceInstances(workspaceId)`
2. 编排：按 `workspaceInstanceFilters(null, workspaceId)` 分别列出角色 / 权限 / 条件实例 ID
3. 对每个角色实例调用 `deleteWorkspaceInstanceRole`（含 assignments + session 刷新）
4. 对每个权限实例调用 `deleteWorkspaceInstancePrivilege`（含缓存）
5. 对每个条件实例调用 `deleteWorkspaceInstanceCriteria`（行 + 节点）
6. 返回；无实例则幂等 no-op
7. 继续软删 + 硬删；`inuseCheck` 不再命中本区条件实例

### 边界

| 场景 | 行为 |
|------|------|
| 无任何实例 | 幂等 no-op |
| 仅有条件无角色（脏数据） | 仍按 workspaceId 删条件，避免继续挡删除 |
| 子工作区 | 各自 delete 内先清自己的实例 |
| 过程仍存在 | preDeleted 拦截，到不了清理 |
| 清理中途失败 | 整段删除失败；工作区尚未软删（C1） |

### 事务

sdkImpl 不加 `@Transactional`；依赖外层 biz 删除入口事务（或框架 `TransactionalExecutor`）。实现阶段核对外层边界。

## Design: Level 4 -- Contracts

### 编排

```java
// IRoleTemplateService
void deleteWorkspaceInstances(Long workspaceId);
```

### 领域原语（D1）

```java
// IRoleService
void deleteWorkspaceInstanceRole(Long roleId);

// IPrivilegeService
void deleteWorkspaceInstancePrivilege(Long privilegeId);

// Criteria SDK / ICriteriaTemplateService（实现时选定落点）
void deleteWorkspaceInstanceCriteria(Long criteriaId);
```

### GF 调用点

`ModelingWorkspacesDataObject.delete()`：递归子区之后、`super.delete()` 之前调用 `deleteWorkspaceInstances(this.getId())`。经 `SpringContextUtils.getBean`（与同文件既有模式一致）；不新增 gf-sdk 接口。

### 失败类型

| 条件 | 行为 |
|------|------|
| `workspaceId == null` | `PARAM_CHECK_ERROR` |
| 原语收到非 WORKSPACE_INSTANCE | 明确业务码，拒绝删除 |
| 节点不存在 | 幂等跳过 |
| 其它持久化失败 | 向上抛出，整段回滚 |

### 不改动

`deleteRole` / `deletePrivilege` / `deleteByCriteriaId` 及配置页只读闸门；`preDeleted` 过程校验；`inuseCheck` 本身（靠先清条件）。

## Design Summary

- **组件与分层**：GF `ModelingWorkspacesDataObject`（C1 挂载）→ infra `IRoleTemplateService.deleteWorkspaceInstances` 编排 → `IRoleService` / `IPrivilegeService` / Criteria 三个实例清理原语。
- **关键契约**：`deleteWorkspaceInstances(workspaceId)`；`deleteWorkspaceInstanceRole/Privilege/Criteria(id)`（D1）。
- **架构约束**：组件隔离经 SDK；sdkImpl 无 `@Transactional`；不放宽配置页只读闸门；不白名单绕过 `inuseCheck`。
- **领域模型**：仅清 `WORKSPACE_INSTANCE`；模板不动；成员分配随角色原语清理；顺序角色→权限→条件。
- **已解决开放问题**：递归删除已核实；A1/B1/C1/D1 均已确认；实例删除不写审计历史。
- **Design status**：**Implemented**
- **验证**：`RoleTemplateServiceImplTest` / `RoleServiceTest` / `PrivilegeServiceTest` / `CriteriaTemplateServiceImplTest` / `RoleNodeDomainTest` / `PrivilegeNodeTest` / `ModelingWorkspacesDataObjectTest` 全部通过。
