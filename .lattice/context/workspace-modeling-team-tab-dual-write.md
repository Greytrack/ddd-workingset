# 工作区建模团队页签双写 — 设计锚点

> 关联：[relationship-aware-permission-and-workspace-roles.md](./relationship-aware-permission-and-workspace-roles.md)

## 分配模型（已确认）

| 维度 | 规则 |
|------|------|
| 工作区 × 角色模板 | **唯一**一个 `WORKSPACE_INSTANCE`（`getOrCreateWorkspaceRoleInstance`） |
| 角色实例 × 用户 | **一对多**（同一实例可分配给多人） |
| 用户 × 工作区 | 可关联**多个**角色实例（多模板并行，普通角色分配语义） |

**非**「单用户单工作区单角色」覆盖式分配。

## 双写权威源

- **鉴权权威**：`air_user_assignments` + `RoleNode(WORKSPACE_INSTANCE)`
- **页签投影**：`air_inst_modeling_workspace_tab_role_teams`（`name`=templateId，`participants`=userId 列表）

## 页签常量（gateflow）

| 项 | 值 |
|----|-----|
| tabId | `420408` |
| apiId | `RoleTeam` |
| 物理表 | `air_inst_modeling_workspace_tab_role_teams` |

## 飞书配置交接

完整页签配置要求见项目内 `docs/modeling-team-tab-config-handoff.md`（供配置同事粘贴至 Wiki）。

## Design Status

**Approved — ready for implementation**（2026-07-01）
