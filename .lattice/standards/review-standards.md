---
mode: overlay
created: "2026-06-08"
---

> AirEdge Backend 2.1 代码审查流程叠加配置。质量细则以 `.lattice/standards/` 各 atom 文档与 `.agent/` 为权威来源。

## 1. Atom Loading Policy

### 始终加载

| Atom | 原因 |
|------|------|
| `clean-code` | 所有 Java 变更 |
| `architecture` | 分层与依赖为项目硬约束 |

### 条件加载

| Atom | 触发条件 |
|------|----------|
| `domain-driven-design` | 变更触及 `**/biz/**`、`**/sdk/**`、`**/sdkimpl/**`、`**/DataObject*.java`、`**/inner/**` |
| `secure-coding` | 变更触及 `*Rest.java`、请求 DTO、鉴权、文件上传、SQL 拼接 |
| `test-quality` | 变更触及 `**/test/**`、`*Test.java` |

### 不加载

- 仅修改 `.agent/`、`.lattice/`、`docs/`、`postman/` 文档时，跳过 `domain-driven-design` 与 `test-quality`。

## 2. Severity Classification

### 项目级严重级别覆盖

| 违规 | 级别 | 说明 |
|------|------|------|
| Biz import `sdkimpl` 包 | **Critical** | 破坏分层隔离 |
| sdk / sdkImpl 上 `@Transactional` | **Critical** | 事务边界违规 |
| 新增 SDK `getDerivedXxx(Long id)` 捷径 | **Critical** | 违反规则 13 |
| 魔法值 / 硬编码 classId、tabId | **High** | 应使用 `AirConstants` |
| 列表字段写 apiId 而非 `item_key` | **High** | 数据一致性风险 |
| Handler 内 silent fallback 查库 | **High** | Context 链断裂 |
| 纯委托 `*Support`（`return entity.foo()`） | **Medium** | 应内联 inner default |
| `@Autowired` 字段注入 | **Medium** | 应用构造器注入 |
| REST 返回 `Result<T>` 包装 | **Medium** | API 契约违规 |
| 日志字符串拼接 | **Low** | 使用 `{}` 占位符 |

## 3. Report Preferences

- **语言**：审查报告使用简体中文。
- **排序**：Critical → High → Medium → Low → Info。
- **分组**：按严重级别分组，每条含文件路径、行号、修复建议。
- **正面反馈**：变更符合规则 13 Context-first 或正确使用 `AirConstants` 时，在报告末尾简要列出。
- **权威引用**：修复建议须指向 `.agent/rules/` 或 `.lattice/standards/` 对应章节，不引用泛化最佳实践替代项目规约。

## 4. Scope Rules

### 排除路径（不审查）

```
**/target/**
**/.flattened-pom.xml
**/generated/**
**/jacocoReportAggr/**
upgrade/src/main/resources/flyway/**
```

### 扩展扫描

变更涉及以下路径时，额外对照 `.agent/quick-reference/common-patterns.md`：

- `**/objectframework/**` — DataObject 生命周期
- `**/processor/**` — Processor / TabQuery 扩展点
- `**/management/**` — Management 事务与编排

### Delta 范围

- 默认仅审查 **当前分支相对 main 的 diff** 或用户指定的变更文件集。
- 不因相邻未改文件存在历史违规而扩大修复范围，但可在 Info 中标注「邻近反模式」。

## 5. Insight Capture Preferences

审查结束后，若发现可复用模式，建议写入 `.lattice/learnings/operational-learnings.md`：

- Context-first 传参范例
- DataObject / Tab 操作陷阱（`AddTableDto` 误用、`item_key` 写入）
- 测试运行约定（`run_unit_tests.sh`、`SPRING_PROFILES_ACTIVE`）

每条学习 **≤ 5 行**，附触发日期与规约链接。

## 6. Health Log Preferences

- 审查摘要追加到 `.lattice/reviews/review-log.md`（append-only）。
- 每条记录：日期、审查范围（文件数/模块）、Critical/High 计数、是否通过。
- 格式示例：`2026-06-08 | components/gf/sdkImpl | C:0 H:1 | pass-with-notes`

## 7. Custom Review Dimensions

### DataObject 与页签

- `createRow` vs `AddTableDto` 选择是否正确？
- `removeRows` vs `removeRowsToRecycle` 是否匹配实现能力？
- 批量操作是否用 `createRows` 而非循环 `createRow`？

### 实体上下文（规则 13）

- 入口是否一次加载聚合根？
- Processor / Handler 是否重复 `getObjectById`？
- 批量循环是否 N+1？

### 常量与枚举

- 是否使用 `AirConstants` 与 `enum.getKey()`？
- Swagger `@Schema` 是否误用 `name` 属性？

### 测试

- 测试方法命名是否为 `test{MethodName}_{Scenario}`？
- 集成测试优先于过度 Mock？

## References

- `.lattice/standards/architecture.md`
- `.lattice/standards/ddd-principles.md`
- `.agent/rules/13-entity-context-and-collaboration.md`
- `.agent/rules/07-testing.md`

---
*Overlay for AirEdge Backend 2.1. Bootstrapped from lattice-init on 2026-06-08.*
