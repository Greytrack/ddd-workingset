---
feature: relationship-aware-permission-and-workspace-roles
requirement_doc:
  - docs/relationship-aware-permission-design.md
  - docs/工作区角色分配与授权.md
created: 2026-06-22
design_status: Draft — scoped assignment + applicable_scope_codes，待评审开放问题
---

# 关系感知权限与工作区角色授权（统一蓝图）

在既有 RBAC + ABAC + criteria 引擎上，泛化可注册关系路径（ReBAC-lite）落地类型五跨实例条件，并补齐过程导航工作区上下文角色分配与权限生效（类型六 + P0 需求）。术语见主文档 **附录 A**（RBAC / ABAC / ReBAC + ReBAC-lite 简化说明）。

## Requirement Sources

| 来源 | 路径 | 范围 |
|------|------|------|
| 飞书《权限原始讨论》 | https://my.feishu.cn/wiki/IH0iwdoAMiYK4Nk6jizcKqbynYg | 类型一～六树形/关系驱动权限 |
| 工作区角色分配与授权 | `docs/工作区角色分配与授权.md` | 模块角色模板、工作区成员、权限刷新 |

**主设计文档**：`docs/relationship-aware-permission-design.md`（2026-06-22：扩展 `air_user_assignments` + `applicable_scope_codes`）

## Design: Level 1 — Capabilities

### 跨实例关系条件（类型五）

1. 关系定义注册（起点分类 → 关系 → 目标分类）
2. 关系感知 criteria 条件行 + 双路径一致求值（内存 / SQL）
3. 类型五页签按钮双重权限（OR criteria）
4. 产品空间穿透注册式收敛（行为不变）
5. 新增关系零核心代码改动验证

### 工作区成员角色（类型六 + 需求）

6. **RoleNode**「所属模块」+「适用范围」多选（`applicable_scope_codes`）与过程导航预置三角色（**非 JobFunction**）
7. 工作区成员写入 **`air_user_assignments`**（`scope_type=WORKSPACE`, `scope_id=workspaceId`）；v1 **仅** `USER_ASSIGN_ROLE`
8. **Session 激活**工作区角色（类比 `pxRoleIds`）+ 工作区所有者隐式权限
9. 无角色工作区不可见 / 403
10. 角色模板变更下次访问刷新 + 引用保护删除
11. 工作区树父子传播（与产品空间树对齐，二期性能）

## Design: Level 2 — Components

| 组件 | 层 | 职责 |
|------|-----|------|
| `RelationKind` / `IRelationshipDefinition` / `IRelationshipRegistry` | infrastructure SDK | 关系契约 |
| `RelationshipRegistryImpl` | infrastructure SDKImpl | 注册表 + seed |
| `ConditionPenetrateHelper` / `CriteriaRowInfo` / `AbstractEvaluableObjectValue` | SDKImpl | 穿透泛化 |
| `RoleModuleCode` + `SysRoleDto.moduleCode` | SDK/SDKImpl | RoleNode 所属模块 |
| `RoleApplicableScopeCode` / `RoleApplicableScopeCatalog` | SDK | 适用范围可选项目录 |
| `SysRoleDto.applicableScopeCodes` | SDK/SDKImpl | RoleNode 适用范围多选 |
| `IRoleService.listByFilter` | SDK/SDKImpl | 配置页按模块 + 适用范围过滤候选角色 |
| `RoleAssignmentScopeType` / `UserAssignmentsRepository` | SDKImpl | `GLOBAL` / `WORKSPACE` scope 隔离读写 |
| `IWorkspaceMembershipService` | SDK/SDKImpl | 工作区成员 CRUD（底层写 scoped assignment） |
| `IWorkspaceRoleResolver` | SDK | 解析 membership + owner |
| `WorkspaceSessionRoleActivator` | SDKImpl | Filter：注入 workspaceRoleIds |
| `WorkspaceRoleManagement` | gf biz | 角色管理页 API |
| `IWorkspaceOwnerPrivilegeProvider` | SDK | 所有者隐式权限 |
| `PrivilegeEngine` / `AdminRoleService` | SDKImpl | 合并 getRoleIds() 鉴权 |

**明确排除**：`JobFunctionManagement`、`TableFunctionTeams*`（产品空间职能域，与工作区 RBAC 无关）

## Design: Level 3 — Interactions

- **类型五**：FE → Rest → `IPrivilegeEngine` → criteria OR + 穿透 → 按钮可用性；列表走 `QueryAction` SQL 注入。
- **工作区成员添加**：Owner → 角色管理 REST → `WorkspaceRoleManagement` → `UserAssignmentsRepository`（`WORKSPACE` scope UPSERT）→ 缓存失效。
- **工作区操作**：Filter `setWorkspace` → Resolver → `activateWorkspaceRoles` → `PrivilegeEngine`；`finally clearWorkspaceRoles`。

## Design: Level 4 — Contracts

见主设计文档 §5.4（关系注册表、Session 角色、`IWorkspaceMembershipService`、scoped `air_user_assignments`、`applicable_scope_codes`）。

## Design Summary

- **双能力、单底座**：不替换 `IPrivilegeEngine`；跨实例关系条件泛化 criteria 穿透；工作区成员角色扩展 **RoleNode 模板维度** + **scoped assignment** + **Session 角色激活**。
- **概念边界**：工作区角色 ≠ JobFunction；`applicable_scope_codes`（模板可被选用）≠ `scope_type`（绑定在何处生效）。
- **存储**：成员不新建表；`air_user_assignments` 增 `scope_type` + `scope_id`；登录只读 `GLOBAL`，工作区 Filter 只读 `WORKSPACE`。
- **Session 方向**：复用 `pxRoleIds` 合并模式 + `RequestThreadHolder.setWorkspace()`。
- **分期**：一期 — 关系框架 + 类型五 + 工作区角色 P0；二期 — 树闭包、MEMBERSHIP criteria。

## Constraints

- 不引入 Zanzibar/OpenFGA
- 不重构 Folder/Tracker
- `IPrivilegeEngine` 对外签名不变
- 关系条件遵守 `isSkipAll()`
- Biz 不依赖 sdkImpl 实现类
- v1 权限粒度 = CSV 控制点，非每按钮配置
- **禁止用 JobFunction 代替工作区角色模板**
- v1 工作区成员 **仅** `USER_ASSIGN_ROLE`（不支持组/部门）
- `UserAssignmentsRepository` 所有存量路径默认 `scope_type=GLOBAL`，防止误删工作区成员

## Open Questions

1. 工作区内鉴权：`workspaceRoleIds` **并集** vs **替换**全局 `roleIds`？（设计推荐并集）
2. ~~membership 持久化：独立表 vs DataObject 子页签？~~ **已决**：扩展 `air_user_assignments`（`scope_type` + `scope_id`）
3. 全局系统管理员是否 bypass 工作区 membership 门禁？
4. 工作区所有者隐式权限：硬编码 vs CSV/配置表？
5. 类型五文档→评审任务精确 tab/attr apiId；关系路径首版 1 跳

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|-------------------------|
| 2026-06-22 | 采用 ReBAC-lite（策略 C） | 穿透机制已存在；与 ABAC/SQL 原生协同；低风险增量 | A 继续硬编码；B 完整 ReBAC |
| 2026-06-22 | ~~工作区成员复用 FunctionTeams + JobFunction~~ **已撤销** | 工作区角色与职能是独立概念；需求明确指向系统角色管理 | JobFunction 模块扩展 |
| 2026-06-22 | 工作区角色模板 = **RoleNode** + `moduleCode` | 与需求「系统角色管理」一致；直接绑定 PrivilegeNode | JobFunction |
| 2026-06-22 | ~~工作区成员 = 专用 assignment 表~~ **已撤销** | 与全局分配统一建模；减少表数量 | `air_workspace_role_assignment` |
| 2026-06-22 | 工作区成员 = **扩展 `air_user_assignments`**（`scope_type`/`scope_id`） | 概念统一；`GLOBAL` 与 `WORKSPACE` 分 scope 读取；迁移成本低 | 独立表；DataObject 子页签 |
| 2026-06-22 | v1 工作区成员 **仅 `USER_ASSIGN_ROLE`** | 需求确认；简化 Resolver 与 UI | 组/部门展开 |
| 2026-06-22 | 角色模板增 **`applicable_scope_codes`**（多选） | 配置入口按场景过滤可选角色；与分配 scope 解耦 | 仅用 `module_code` |
| 2026-06-22 | 生效方式 = **Session 激活**（`workspaceRoleIds`） | 已有 `pxRoleIds` 先例；配合 `setWorkspace`；不污染登录态 | 全局 USER_ASSIGN_ROLE；对象 ACL |
| 2026-06-22 | 双能力并行、分期交付 | 类型五与工作区角色解耦实现，共享基础设施回归测试 | 合并为单一里程碑 |

## Key Files

- `docs/relationship-aware-permission-design.md`
- `docs/工作区角色分配与授权.md`
- `infrastructure/sdk/.../user/dto/CurrentUserDto.java`
- `infrastructure/sdk/.../RequestThreadHolder.java`
- `infrastructure/sdk/.../rolepriv/IRoleService.java`
- `infrastructure/sdkImpl/.../criteria/util/ConditionPenetrateHelper.java`
- `infrastructure/sdkImpl/.../rolepriv/PrivilegeEngine.java`
- `infrastructure/sdkImpl/.../dao/UserAssignmentsRepository.java`
