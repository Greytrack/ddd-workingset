---
feature: bug-15177-normal-privilege-on-template-role
requirement_doc: null
created: 2026-07-27
bug_id: 15177
design_status: Implemented — ready for review
---

# Bug 15177：模板角色关联普通权限不生成权限实例

模板角色绑定普通权限（`PrivilegeKind.NORMAL`）时，角色实例化/同步应直接关联同一权限节点，不创建工作区权限实例；解绑与删除只处理关系，不误删普通权限节点。存量脏数据不清理（测试阶段，仅修正向逻辑）。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-27 | 新建本 context 文档专载 bug 15177 设计 | 与工作区角色大蓝图解耦，范围明确 | 并入 `relationship-aware-permission-and-workspace-roles.md`（范围过大） |
| 2026-07-27 | 仅修正向逻辑，不做存量脏数据清理/迁移 | 尚未正式上线，处于测试阶段 | 加 Flyway 清理错误克隆的 WORKSPACE_INSTANCE |
| 2026-07-27 | 从模板解绑普通权限时只解绑角色实例关系，绝不删除普通权限节点 | 普通权限为共享配置节点，可被多角色引用 | 级联删除权限节点 |
| 2026-07-27 | Level 1 Capabilities 通过（5 条） | 覆盖配置绑定、实例化、增量同步、解绑清理、完整性校验 | 只修实例化不同步增量（会漏「后绑普通权限」） |
| 2026-07-27 | Level 2 Components 通过；NORMAL 短路选 Option B（调用方分流 + PrivilegeService 防御） | 对齐 CriteriaTemplateService 模式，双保险；解绑必须在 RoleService 按 kind 找目标 ID | 仅改调用方 / 仅改 PrivilegeService（解绑仍坏） |
| 2026-07-27 | Level 3 Interactions 通过（实例化分流 / BIND / UNBIND / 全量同步与清理） | 目标权限 ID 按 kind 决定；只删关系不删 NORMAL 节点 | — |
| 2026-07-27 | Level 4 Contracts 通过（用户进入 code-forge 视为批准） | 公开签名不变；PrivilegeNode 增判定方法；调用方分流 + PrivilegeService 防御 | 不加领域方法、调用方直接比枚举 |
| 2026-07-27 | Design approved at Level 4. Blueprint complete ready for implementation. | code-forge 启动 | — |
| 2026-07-27 | 实现完成：PrivilegeNode 判定 + PrivilegeService/RoleService/RoleTemplate 分流；E2E 断言兼容 NORMAL 直绑 | 对齐蓝图 Option B；batch/同步对 NORMAL 跳过实例索引 | — |
| 2026-07-27 | Impl complete — ready for `/review` | 单测 PrivilegeNode/PrivilegeService/RoleService/RoleTemplate 共 85+21 通过 | — |
| 2026-07-27 | 普通权限关联角色列表/添加弹窗只展示 NORMAL 角色 | 配置页不应暴露模板/实例角色；模板侧仍可绑定普通权限 | 仅前端过滤 |

## Open Questions

（无）

## Constraints

- 不清理存量错误生成的权限实例（测试阶段，仅正向修复）
- 解绑/删除路径不得物理删除 `PrivilegeKind.NORMAL` 权限节点
- 改动落在 `infrastructure` 角色权限域（`RoleTemplateServiceImpl` / `RoleService` / `PrivilegeService`），不改 REST 契约除非必要

## Key Files

| Path | Role |
|------|------|
| `backend/infrastructure/sdk/.../PrivilegeNode.java` | `isNormal()` / `requiresWorkspaceInstantiation()` |
| `backend/infrastructure/sdk/.../IPrivilegeService.java` | resolve/batch 契约 javadoc |
| `backend/infrastructure/sdk/.../IRoleTemplateService.java` | 实例化契约 javadoc |
| `backend/infrastructure/sdkImpl/.../PrivilegeService.java` | NORMAL 防御短路；batch 惰性索引 |
| `backend/infrastructure/sdkImpl/.../RoleService.java` | BIND/UNBIND/全量同步按 kind 分流 |
| `backend/infrastructure/sdkImpl/.../RoleTemplateServiceImpl.java` | 实例化仅 TEMPLATE 走 provision |
| `backend/components/gf/biz/.../WorkspaceRoleTeamDbAssertions.java` | E2E 断言兼容直接关联的普通权限 |

## Design: Level 1 -- Capabilities

1. **模板角色可关联普通权限**：配置端把普通权限绑到模板角色时，行为合法且不触发权限实例创建。
2. **角色实例化时普通权限直接挂接**：由模板生成工作区角色实例时，普通权限以同一权限节点直接关联到角色实例；仅模板权限才生成工作区权限实例。
3. **增量同步按形态分流**：模板上新增/移除普通权限时，对各工作区角色实例只增删关系；模板权限仍走「解析/创建权限实例再绑定」。
4. **解绑与清理不伤普通权限**：从模板解绑或清理工作区角色实例时，普通权限只解除角色关系，不删除该权限节点；权限实例仍按现有生命周期清理。
5. **完整性校验仍成立**：角色实例上的有效权限数量（直接关联的普通权限 + 已实例化的模板权限）与模板侧可实例化权限数一致。

## Design: Level 2 -- Components

| 组件 | 层 | 职责 |
|------|-----|------|
| `PrivilegeNode` | infrastructure SDK | 权限形态归属；表达「是否需工作区实例化」 |
| `PrivilegeService` | infrastructure SDKImpl | resolve/batch 时 NORMAL 原样返回，禁止 clone |
| `RoleTemplateServiceImpl` | infrastructure SDKImpl | 实例化编排：仅 TEMPLATE 走条件/权限实例化；NORMAL 直接进绑定 ID 列表 |
| `RoleService`（同步/解绑） | infrastructure SDKImpl | 增量 BIND/UNBIND 与全量同步：按 kind 决定绑定目标 ID（自身 vs 实例） |
| `CriteriaTemplateService` | infrastructure SDKImpl | **不变**（已对非模板条件短路） |

架构约束：不新增 REST；不改工作区清理策略；存量脏数据不清理。分流判定以 `PrivilegeKind`（`PrivilegeNode.effectiveKind()`）为准。

## Design: Level 3 -- Interactions

核心规则：绑定目标权限 ID = NORMAL→自身 / TEMPLATE→工作区权限实例；解绑用同一 ID 删关系，从不删 NORMAL 节点。

- **Flow A 实例化**：`RoleTemplateServiceImpl` 按 kind 分流 → 仅 TEMPLATE 走 Criteria/Privilege resolve-provision → 绑定 NORMAL IDs ∪ 权限实例 IDs；`PrivilegeService` 对误传 NORMAL 防御返回自身。
- **Flow B 增量 BIND**：NORMAL 直接插 ROLE↔privilegeId；TEMPLATE 按 workspace resolve/provision 后再绑。
- **Flow C 增量 UNBIND**：NORMAL 删 value==privilegeId；TEMPLATE 删 value==instancePrivId；只删关系。
- **Flow D**：全量同步复用 B 规则重绑；工作区清理只删角色实例/权限实例/条件实例，不触及 NORMAL。

## Design: Level 4 -- Contracts

### PrivilegeNode
- `boolean requiresWorkspaceInstantiation()` — 仅 `TEMPLATE`
- `boolean isNormal()` — `effectiveKind() == NORMAL`
- `countInstantiablePrivileges` 不变（计非 WORKSPACE_INSTANCE）

### IPrivilegeService（签名不变）
- `resolveForWorkspaceInstance` / `resolveOrProvisionWorkspaceInstancesBatch`：NORMAL 与已是实例 → 返回自身 ID，不 clone；TEMPLATE → 复用或 provision

### RoleTemplateServiceImpl
- 仅 TEMPLATE 走 criteria/privilege batch；bindIds = NORMAL IDs ∪ 实例 IDs；更新 javadoc

### RoleService（公开签名不变）
- BIND/UNBIND/全量同步目标 ID：NORMAL→privilegeId；TEMPLATE→workspace 权限实例 ID（UNBIND 无实例则跳过）

### 不改
- CriteriaTemplateService、REST/DTO、配对校验、工作区清理只删实例

## Design Summary

- **组件与层**：`PrivilegeNode`（SDK）+ `PrivilegeService` / `RoleTemplateServiceImpl` / `RoleService`（SDKImpl）；`CriteriaTemplateService` 不动
- **关键契约**：按 `PrivilegeKind` 决定绑定目标 ID；PrivilegeService 防御短路；解绑只删关系
- **约束**：只修正向；不删 NORMAL 节点；不改 REST
- **领域**：角色↔权限仍按 ID 关系关联；无新领域事件
- **开放问题**：无（存量清理已否决）
- **设计状态**：**Implemented — ready for `/review`**
