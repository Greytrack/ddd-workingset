---
feature: GateFlow License Control
requirement_doc: null
created: 2026-07-16
status: complete
---

# GateFlow License Control

> 过程导航 (GateFlow) License 控制集成，限制或启用相关类及按钮。

## Decisions Log

<!-- Add new at bottom. Never remove. -->

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-16 | Design approved at Level 4. Status set to approved — ready for implementation. | 设计已完成并通过评审。 | 无。 |
| 2026-07-16 | Implementation completed end-to-end. | 代码全部实现、编译通过并通过相关单测。 | 无。 |

## Open Questions

None.

## Constraints

None.

## Design: Level 1 -- Capabilities

1. **License 控制项扩展 (CAP-1)**：在 `LicenseItemEnum` 中新增 `GATE_FLOW` 枚举，绑定 L1 "91" (GateFlow)、L2 "9101" (过程导航) 以及类 ID 集合 `[100303, 2101, 2103, 2104, 2105, 2106]`。
2. **开发容器 Mock 授权 (CAP-2)**：在 `mock-license.json` 中配置模块授权，使本地开发环境默认开启过程导航模块。
3. **类与操作按钮层级联动控制 (CAP-3)**：根据 `GATE_FLOW` 状态限制关联类的启用状态，自动隐藏或置灰相关按钮。

## Design: Level 2 -- Components

1. `LicenseItemEnum` (Enum) - 定义 L1/L2 授权码和类映射，位于 `infrastructure/sdk`。
2. `MenuCodeEnum` (Enum) - 定义菜单标识 `Gateflow`，位于 `infrastructure/sdk`。
3. `GateFlowMenuPolicy` (MenuPolicy) - 前端菜单可见性策略，位于 `infrastructure/biz`。
4. `GenericQueryManagement` (Management) - 拦截 Tab 按钮查询并过滤未授权类，位于 `infrastructure/biz`。
5. `mock-license.json` (Config) - Mock 授权配置，位于外部 `.devcontainer/` 目录。

## Design: Level 3 -- Interactions

1. **菜单可见性控制流**：前端向 `/api/menu/authorizedFrontEndMenus` 请求菜单树，后端 `MenuManagement` 调用 `GateFlowMenuPolicy`，策略根据 `LicenseService` 中的 `GATE_FLOW` L2 编码 ("9101") 状态进行过滤下发。
2. **类与操作按钮联动流**：前端请求 Tab 按钮列表（关联过程 subclass `21040101`），`GenericQueryManagement.queryTabButtons` 校验 `iAirClass.getEnabled()`。而该 subclass 在 `AirSubClass` 底层会向 `LicenseService` 获取 `noEffectiveClassIdLicenseCodeMap`。若 `2104` 存在于其中，`getEnabled()` 返回 `NO`，`queryTabButtons` 拦截并直接返回空按钮列表 `[]`，从而使前台按钮被置灰或隐藏。

## Design: Level 4 -- Contracts

1. **`LicenseItemEnum` 枚举扩展**：
   + `GATE_FLOW(List.of(100303L, 2101L, 2103L, 2104L, 2105L, 2106L), "9101", "91", ContolTypeEnum.CLASS_HIERARCHY, "过程导航", "GateFlow")`
2. **`MenuCodeEnum` 枚举扩展**：
   + `GATE_FLOW("Gateflow", "过程导航")`
3. **`GateFlowMenuPolicy` 新增支持策略**：
   + 实现 `MenuPolicy`，匹配 `"Gateflow"` 菜单节点，校验 L2 "9101" 控制项。
4. **`GenericQueryManagement` 方法调整**：
   + 在 `queryTabButtons(ButtonQueryDto)` 开始处，拦截判断 `!YesOrNoListEnum.YES.getKey().equals(iAirClass.getEnabled())` 则直接返回空列表。
5. **`mock-license.json` 属性配置**：
   + 在 `bizItems` 及 `customData.bizItems` 均加入 `"91"` 及 `"9101"` 的默认 `enabled: true` 配置。

## Key Files

| Path | Role |
|---|---|
| backend/infrastructure/sdk/src/main/java/com/airedgesoft/plm/sdk/license/enums/LicenseItemEnum.java | 定义 License 控制项与 Class 映射 |
| backend/infrastructure/sdk/src/main/java/com/airedgesoft/plm/common/enums/MenuCodeEnum.java | 增加过程导航菜单编码 |
| backend/infrastructure/biz/src/main/java/com/airedgesoft/plm/menu/policy/GateFlowMenuPolicy.java | 增加过程导航菜单可见性策略 |
| backend/infrastructure/biz/src/main/java/com/airedgesoft/plm/dataobject/management/GenericQueryManagement.java | 拦截未授权类的按钮下发 |
| .devcontainer/mock-license.json | 开发容器中使用的 Mock License 配置文件 |

## Design Summary

- **组件与分层**：
  - `LicenseItemEnum` (Enum) - `infrastructure/sdk`
  - `MenuCodeEnum` (Enum) - `infrastructure/sdk`
  - `GateFlowMenuPolicy` (MenuPolicy) - `infrastructure/biz`
  - `GenericQueryManagement` (Management) - `infrastructure/biz`
  - `mock-license.json` - 外部配置
- **关键契约**：
  - `LicenseItemEnum` 绑定类列表 `[100303L, 2101L, 2103L, 2104L, 2105L, 2106L]`。
  - `MenuCodeEnum` 新增 `GATE_FLOW("Gateflow", "过程导航")` 并通过 `GateFlowMenuPolicy` 管理可见性。
  - `GenericQueryManagement.queryTabButtons` 对禁用类直接返回空列表。
- **架构约束**：单向依赖方向，`biz` 依赖 `sdk`，整个 License 控制留在基础设施层闭环，不向业务组件层反向耦合。
- **领域模型决策**：通过通用的类启用态进行动态控制，避免对每个具体的实体/按钮进行写死逻辑硬编码。
- **说明**：无需求文档链接 — 差异检查已跳过。
