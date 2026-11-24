# lib-restful

**AirEdge V4 RESTful 协议基建库**：把《API Design Patterns》（JJ Geewax, Manning 2021）、Google AIP（aip.dev）与 IETF RFC 的契约规范沉淀为可复用的框架能力——切面、Filter、ResponseAdvice、注解契约、AST 解析器与存储侧支撑，让业务代码以最低心智负担产出合规 API。

- 包根：`com.airedgesoft.lib.restful`（单 Maven 模块，按功能分包，**每个包目录下有独立 README.md**，含标准符合性、使用方法与类图设计）
- 标准对照 SSOT：[v4-restful-patterns-compliance.md](../../.lattice/standards/v4/v4-restful-patterns-compliance.md)（28 主题全景矩阵 + 偏离登记 D1~D17）
- 契约主规范：[v4-restful-api.md](../../.lattice/standards/v4/v4-restful-api.md) · 分层规范：[v4-architecture.md](../../.lattice/standards/v4/v4-architecture.md) · 错误规范：[v4-exception-handling.md](../../.lattice/standards/v4/v4-exception-handling.md)
- 自动装配：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（引入依赖即生效，不依赖宿主 `scanBasePackages`）
- 基准用法示例：`airedge-v4/demo-singlemodule`

## 包索引（23 个功能包）

> Tier 口径见矩阵 §2.1：🟢 A = 框架完全接管（业务零感知）；🔵 B = 声明式注解驱动；🟣 C = 工具与门禁守护；🟠 D = 核心领域语义（框架提供基建）。

| 包 | 解决的问题 | 权威标准 | Tier | README |
|---|---|---|---|---|
| `etag` | body `lockedVersion` 并发控制 + 表示 hash `ETag` / 304 / 201 Location 自动装配 | AIP-154/131/133 · ch6/28 · RFC 9110（D11/D13） | 🟢 A | [README](src/main/java/com/airedgesoft/lib/restful/etag/README.md) |
| `concurrency` | `DeadlockSafeExecutor` 主键升序加锁防死锁（单聚合实例事务内） | ch17 | 🟠 D | [README](src/main/java/com/airedgesoft/lib/restful/concurrency/README.md) |
| `idempotency` | `Idempotency-Key` 头幂等：加锁、指纹校验（409/422）、响应回放 | AIP-155 · ch23/26 · IETF idempotency-key 草案（D4/D5） | 🟢 A | [README](src/main/java/com/airedgesoft/lib/restful/idempotency/README.md) |
| `dryrun` | `?validateOnly=true` 预检：完整执行后事务回滚不落库 | AIP-163 · ch27（D8） | 🟢 A | [README](src/main/java/com/airedgesoft/lib/restful/dryrun/README.md) |
| `sunset` | `@Sunset` 旧端点自动下发 `Deprecation` / `Sunset` 标头 | AIP-180/185 · ch24 · RFC 8594 · IETF Deprecation 头（RFC 9745 形态待收敛） | 🟣 C（门禁主题下的运行时提示） | [README](src/main/java/com/airedgesoft/lib/restful/sunset/README.md) |
| `readmask` | `?readMask` 字段投影：白名单自动派生、record 重建剪枝 | AIP-157 · ch8 | 🟢 A | [README](src/main/java/com/airedgesoft/lib/restful/readmask/README.md) |
| `abac` | 字段级脱敏（Omit）+ `_access` 权限伴随节点 + 写路径校验契约 | ⭐ 自有扩展（AIP-157/161 + google.rpc FieldViolation 语义） | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/abac/README.md) |
| `fieldtoggle` | 预定义字段部署期启停：读剔除 / 点名引用 400 | AIP-157/161 · ch5 | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/fieldtoggle/README.md) |
| `updatemask` | PATCH `?updateMask` 三态解析、白名单校验、`ModelPatcher` 反射覆盖 | AIP-134/161 · ch7（D6/D9/D15） | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/updatemask/README.md) |
| `filter` | AIP-160 过滤语法 AST 解析 + `@Filterable` 白名单 + SPI 参数化下推 | AIP-160 · ch20 | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/filter/README.md) |
| `orderby` | `orderBy` 解析 + `@Sortable` 白名单 + 主键升序末位兜底 | AIP-132 · ch22 | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/orderby/README.md) |
| `pagination` | `Page<T>` 信封 + AES-GCM 不透明游标 + `pageSize` 语义钳制 | AIP-158/132 · ch19 | 🟢 A（游标层；编排在应用层） | [README](src/main/java/com/airedgesoft/lib/restful/pagination/README.md) |
| `batch` | `@BatchLimit` 上限门禁 + `:batchGet` 稀疏对齐保序 | AIP-231/233/234/235 · ch16（D3/D10/D14） | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/batch/README.md) |
| `lro` | `@LroOperation` 长耗时操作：202 + Location + 轮询/取消 + OperationStore | AIP-151 · ch10/11 | 🔵 B | [README](src/main/java/com/airedgesoft/lib/restful/lro/README.md) |
| `softdelete` | 软删读拦截（v3 列模型 `delete_flag=0` + `updated_time`，表级白名单）/ `showDeleted` 放行 / 恢复与永久删除支撑 | AIP-164 · ch25（D1/D2） | 🟢 A（读路径） | [README](src/main/java/com/airedgesoft/lib/restful/softdelete/README.md) |
| `singleton` | `@SingletonResource` 单例契约标记（ArchUnit 规则 13 锚点） | AIP-156 · ch12（D16） | 🟣 C | [README](src/main/java/com/airedgesoft/lib/restful/singleton/README.md) |
| `problem` | RFC 9457 ProblemDetail 构建收口 + i18n 解析装配 | RFC 9457 · AIP-193（D12） | 🟢 A（BC 一次性装配 advice） | [README](src/main/java/com/airedgesoft/lib/restful/problem/README.md) |
| `openapi` | springdoc 契约补全（幂等头 / readMask；`Etagged*` 平铺器当前惰性） | AIP-180/185 契约门禁基建 | 🟣 C（文档基建） | [README](src/main/java/com/airedgesoft/lib/restful/openapi/README.md) |
| `streaming` | `StreamingExportSupport` 游标分批流式导出（NDJSON / JSON 数组） | ch18 · AIP-136:53（D17） | 🟠 D | [README](src/main/java/com/airedgesoft/lib/restful/streaming/README.md) |
| `outbox` | Transactional Outbox 本地消息表：同事务入箱、至少一次派发 | ch17（事件最终一致性立场） | 🟠 D | [README](src/main/java/com/airedgesoft/lib/restful/outbox/README.md) |
| `persistence` | Spring Data JDBC 版本基线取给 + 软删/恢复/expunge 存储侧支撑 | 支撑 AIP-154/164（无独立主题） | 🟠 D | [README](src/main/java/com/airedgesoft/lib/restful/persistence/README.md) |
| `id` | 契约 String ↔ 存储 Long 主键规范形态转换 | AIP-122（契约主键 String 口径） | 基建 | [README](src/main/java/com/airedgesoft/lib/restful/id/README.md) |
| `path` | URI → 集合段派生唯一口径（contextPath 剥离 / 解码 / 剥动词） | 支撑 AIP-122 资源名解析 | 基建 | [README](src/main/java/com/airedgesoft/lib/restful/path/README.md) |

## 业务感知程度速查

- **零感知（框架全局接管，无需业务声明）**：`idempotency`（`Idempotency-Key` 头）、`readmask`（`?readMask` 投影）、`pagination` 游标安全层、`etag` 的 `ETag` 响应头（任何 JSON 响应恒下发）。
- **一个注解 / 一处声明 / 一段配置**：`etag` 版本校验与 201 `Location`（`@EtagResource` / body `LockedCommand`；条件读取 304 另需 `@EnableRepresentationCache`）、`problem`（BC 持有 `ProblemDetails` advice 样板）、`softdelete` 读路径（`airedge.restful.soft-delete.enabled=true` + `tables` 白名单；拦截器自动挂链首，`auto-register` 缺省 true）、`openapi`（契约门禁文档基建）、`@EtagResource`、`@EnableRepresentationCache`、`@ValidUpdateMask`、`@Filterable`、`@Sortable`、`@BatchLimit`、`@LroOperation`、`@Sunset`、`@SingletonResource`、`@DryRun`、写请求体实现 `LockedCommand`。
- **业务显式编排（框架只给工具）**：`concurrency`、`streaming`、`outbox`、`persistence`、分页切片编排（应用层 QueryService）。

各包 README 内含：解决的问题 → 标准符合性（AIP / 章节 / RFC / 偏离登记）→ 使用方法（真实 Java + HTTP 示例）→ 设计方案（mermaid 类图覆盖包内全部类型 + 类职责一览 + 协作流程 + 配置项/错误码）。