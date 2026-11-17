---
mode: overlay
created: "2026-06-08"
---

> AirEdge Backend 2.1 领域建模叠加规则。本项目无独立 `domain/` 包，**DataObject 框架 + `IInner*` 接口** 承担 DDD 战术角色。完整协作规约见 `.agent/rules/13-entity-context-and-collaboration.md`。

## 1. Aggregate Design Rules

### 聚合根映射

| DDD 概念 | 本项目实现 |
|----------|------------|
| 聚合根 | `*DataObject` 子类实例（PLM）或 `IInner*` 所代表的领域记录（GF 过程元素等） |
| 一致性边界 | 单个 DataObject 实例 + 其页签行（Tab）在同一写事务内由 `*Management` 编排 |
| 跨聚合协调 | `*Management` 编排 + `OperationContext` / `QueryContext` 传参；**不用**领域事件作为主要机制 |

### 边界原则

- 页签（Tab）行是聚合内的从属实体，通过 `Table.createRow` / `createRows` 批量操作，不单独暴露为 SDK 公共聚合根。
- 跨 DataObject 引用使用 **ID**（`Long`），不在一个事务内加载整棵关联树。
- 过程域（GF）的 `IProcessesInner`、`IInnerTailorable` 等 inner 接口定义单实例行为边界；过程级批量逻辑不归入 inner。

### 反模式

- **God DataObject**：在 DataObject 子类中堆积跨页签、跨实例编排 → 下沉 sdkImpl `*TabService` / `*Handler`。
- **平行 API**：sdkImpl 为单聚合能力再暴露与 inner/DataObject 重复的第二套公共方法 → 收敛到 inner `default` 或 DataObject 方法。

## 2. Entity Patterns

### 行为归属

- **MUST**：单实例领域行为放在 DataObject 子类方法或 SDK `IInner*` 的 `default` 方法中（如 `IInnerTailorable.listDecisions()`）。
- **MUST NOT**：新建 `*Support` 类仅做 `return entity.foo()` 转发。
- **MUST NOT**：实体退化为纯 DTO + 全逻辑在 Service 中（anemic 反模式）；读派生字段应从已加载的 `IInner*` 读取，而非 Service 再查库。

### 身份标识

- 持久化主键：`Long id`（数据库生成），不使用自定义 Typed ID 值对象。
- 元数据标识：分类/页签/属性使用 `AirConstants` 中的 `classId`、`apiId`；实例列表字段存 `item_key`（见 common-patterns）。

## 3. Value Object Patterns

### 列表枚举

列表型属性（`data_type = 5`）的持久化值必须是 `air_sys_list_item.item_key`：

```java
// ✅ 写入
attrs.put(ATTR_TAILORING_ACTION, TailoringDecisionActionListEnum.TAILOR_OUT.getKey());

// ❌ 禁止 apiId 或 legacy 字符串
attrs.put(ATTR_TAILORING_ACTION, "TailorOut");
```

### 无状态工具

允许 sdkImpl 内无状态纯函数类（`Merger`、`Resolver`、`RowValueSupport`），不持有实体状态，可单测。

## 4. Domain Service Rules

### 分层映射

| 角色 | 位置 | 职责 |
|------|------|------|
| 应用服务 | biz `*Management` | 编排、事务、REST 入参校验 |
| 领域服务契约 | SDK `I*Service` | 业务能力接口，无实现 |
| 领域服务实现 | sdkImpl `*ServiceImpl` | 跨实例编排、Loader、Handler |
| 单实例逻辑 | SDK `IInner*` + DataObject | default 方法、生命周期钩子 |

### SDK 契约约束

- **MUST NOT**：在 `I*Service` 上暴露 `getDerivedXxx(Long id)` 类捷径 API（见规则 13 原则 B）。
- **允许**：`I*Service.getProcess(Long id)` 等**加载完整 inner 对象**的入口；派生字段从返回的 `IInner*` 读取。

## 5. Domain Event Patterns

本项目**不以领域事件**作为跨聚合一致性的主要手段。

- 跨聚合副作用：由 `*Management` 在同一事务内顺序调用，或显式编排多个 Service 方法。
- Processor 扩展点（`preHandle` / `postHandle`）用于横切增强，不替代聚合内不变量校验。

## 6. Repository Patterns

### 数据访问

- DataObject 框架：`getInnerObject` / `ISessionLoader` 加载聚合；禁止 Biz 层直接 `SqlRunner` 或裸 SQL。
- MyBatis-Plus：sdkImpl Repository 层访问；Mapper 不暴露给 Biz。
- 批量加载：sdkImpl `*ObjectLoader.getXxx(Collection<Id>)` 构建 `Map`，禁止循环内单条 `getXxx(id)`。

## 7. Object Creation Patterns

### 页签行创建

- 纯属性新增：`Map<Object, Object>` 或 `createRow(rowData)` / `createRows(List.of(rowData))`。
- 带关联关系：仅当需要 `relatedId/relatedType` 时使用 `AddTableDto`。
- **禁止**：为传属性 map 误用 `AddTableDto`（`relatedId` 为空可能触发 NPE）。

### 删除

- 默认使用 `removeRows(...)`；`removeRowsToRecycle(...)` 仅在明确支持回收站的特化实现中可用。

## 8. Context 传递（项目特有）

入口一次加载，向下只读传递（规则 13 原则 A）：

| 域 | Context | 构建点 | 消费方 |
|----|---------|--------|--------|
| GF 写 | `OperationContext` | `ProcessServiceImpl.buildOperationContext` | Operation Handler |
| GF 读 | `QueryContext` | `ProcessServiceImpl.buildQueryContext` | 内置数据源 Handler |
| PLM | `IInnerDataObject` | Management 入口 `getInnerObject` | Processor 入参 |

**禁止**：Handler 内 `if (ctx.getX() == null) service.load(id)` 静默 fallback。

## 9. Validation Checklist

- [ ] 单实例逻辑是否在 inner `default` 或 DataObject，而非 Support 转发？
- [ ] 跨实例批量是否在 sdkImpl `*TabService` / `*Handler`？
- [ ] 是否禁止新增 SDK `getDerivedXxx(Long id)` 捷径？
- [ ] 列表字段是否写 `enum.getKey()` 而非 apiId？
- [ ] 批量循环是否避免 N+1（循环前一次解析父级属性）？
- [ ] 写事务是否仅在 biz `*Management`？

## References

- `.agent/rules/13-entity-context-and-collaboration.md`
- `.agent/quick-reference/common-patterns.md`
- `.pi/skills/dataobject-model/SKILL.md`

---
*Overlay for AirEdge Backend 2.1. Bootstrapped from lattice-init on 2026-06-08.*
