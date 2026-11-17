---
mode: override
---

> AirEdge Backend 2.1 架构标准（Biz → SDK → SDKImpl 组件化分层）。`architecture_mode: custom` — 本文档为 architecture atom 唯一参考。

## 1. Layer Definitions

| Layer | Location | Responsibility |
|-------|----------|----------------|
| **Biz** | `components/{c}/biz/` | REST、应用服务、`*Management` 编排；唯一事务声明处 |
| **SDK** | `components/{c}/sdk/` | 对外接口、枚举、DTO 契约；无实现细节 |
| **SDKImpl** | `components/{c}/sdkimpl/` | SDK 实现、DAO、Operation Handler、框架扩展 |
| **Infrastructure SDK/SDKImpl** | `infrastructure/` | DataObject、跨组件能力；PLM/GF 桥接 |
| **AirAPI** | `AirAPI/` | 跨模块常量（`AirConstants`） |
| **Apps** | `apps/` | Spring Boot 启动与装配 |

## 2. Dependency Rules

- Biz → 本组件 SDK + `infrastructure/sdk`（**禁止** Biz 依赖任何 `sdkimpl`）
- 组件 SDKImpl → 本组件 SDK + `infrastructure/sdk` + **`infrastructure/sdkImpl`**（允许）
- App → Component → Infrastructure（单向，**禁止循环**）
- **组件间隔离**（`components/gf` ↔ `components/plm` ↔ `components/dqa` ↔ `components/links`）：**禁止** Maven 模块与 import 交叉依赖；须经 `infrastructure/`
- SDK / SDKImpl：**禁止** `@Transactional`

### 2.1 模块边界 vs 包名（易混淆）

| Maven 模块 | 典型 Java 包前缀 | 角色 |
|------------|------------------|------|
| `infrastructure/sdk` | `com.airedgesoft.plm.sdk.*` | 跨组件契约（接口、inner、DTO） |
| `infrastructure/sdkImpl` | `com.airedgesoft.plm.sdkimpl.*` | DataObject 框架等底座实现 |
| `components/plm/sdk` | `com.airedgesoft.plm.sdk.draw.*` 等 | PLM **组件**自有 SDK |
| `components/plm/sdkImpl` | `com.airedgesoft.plm.sdkimpl.draw.*`、`...actionitem.*` 等 | PLM **组件**自有实现 |

**判定口诀**：`com.airedgesoft.plm.sdkimpl.service.objectframework.*` 等属于 **infrastructure**，不是 PLM 组件穿透。  
**违规**是指 `components/gf` import `components/plm` 下的类（如 `com.airedgesoft.plm.biz.*`、`com.airedgesoft.plm.sdkimpl.draw.*`），或反向。

## 3. Boundary Rules

- REST 在 biz `*.rest`；直接返回 `{Feature}Resp`，不包 `Result`
- 业务异常边界：Controller 不吞异常；统一由框架处理 `AppException` / `ParamException`
- 扩展点（Processor、TabQuery）注册在 sdkimpl，调用链从 Management 进入

## 4. Per-Layer Rules

**Biz**: `@RequiredArgsConstructor` 注入；`*Management` 写操作加 `@Transactional(rollbackFor = Exception.class)`；纯读 `query*` / `preview*` 无事务。

**SDK**: 接口 + 类型；无 Spring 事务、无 MyBatis 实现类。

**SDKImpl**: Repository、Handler、Support、DataObject/Tab 扩展；可依赖 `infrastructure/sdk` 与 **`infrastructure/sdkImpl`**（继承 `DataObject`/`Table`、`@RegisterDataObject` 等）；不可被 biz 直接 import 实现类。

## 5. Key Flows

典型写操作：`*Rest` → `*Management`（事务）→ `I*Service`（SDK）→ `*ServiceImpl`（SDKImpl）→ DB / DataObject。

读操作：Rest → Service（或 Management 只读方法）→ SDKImpl 查询。

## 6. Validation Checklist

- [ ] 新增类是否落在正确 layer 包路径？
- [ ] Biz 是否未 import `sdkimpl` 包？
- [ ] 事务是否仅在 Management（或文档允许的 biz 编排入口）？
- [ ] 分类/`apiId` 等对外契约常量是否来自 `AirConstants`？内部固定业务 ID（角色模板、JobFunction 等）是否落在消费侧常量而非 `AirConstants`/字面量散落？
- [ ] 组件间（gf/plm/dqa/links）是否无新增 **Maven 模块**交叉依赖？
- [ ] 是否未 import 其他 **组件**的 `biz` / `sdkimpl` 包（`plm.biz.*`、`plm.sdkimpl.draw.*` 等）？

## 7. Anti-Patterns

- Biz 依赖 SDKImpl 或 infrastructure sdkImpl 具体类
- `@Autowired` 字段注入、`SpringContextUtils.getBean()` in biz
- SDKImpl 上 `@Transactional`
- REST 返回 `Result<T>` 包装
- 魔法数字/字符串业务状态
- SDK 仅凭 ID 的捷径 API（`getDerivedXxx(Long id)` 替代 Context / Inner 传参）
- Handler 内 silent fallback 查库；批量循环内重复解析同一父级属性（N+1）
- 纯委托 `*Support`（`return entity.foo()`）替代 inner default 或 DataObject 方法

## 8. Ambiguity Signals

若变更涉及 DataObject / TabQuery / Processor 扩展，必须先读 `.agent/rules/02-data-access.md`、`11-*`、`12-*` 与对应 skill，再改代码。

若变更涉及 Service 传参、Handler 上下文、单聚合 vs 批量编排，必须先读 `.agent/rules/13-entity-context-and-collaboration.md`（与规则 10 并列）。

---
*Custom architecture for AirEdge Backend 2.1. Refine via `/architecture-refiner`.*
