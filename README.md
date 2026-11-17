# Working Set DDD

本仓库用于持续沉淀企业级 PLM／ALM Working Set 的领域发现、设计决策、验证证据与实现映射。

开始工作前请先阅读 [AGENTS.md](AGENTS.md)，再查看 [当前设计基线](docs/working-set/BASELINE.md) 和 [Working Set 知识库导航](docs/working-set/README.md)。`pre-design/` 保存历史设计输入，不代表当前已接受的设计基线。

根 `pom.xml` 聚合四个 Maven 模块。

| 模块 | 职责 |
| --- | --- |
| `exception` | 公共异常基类、错误码接口和字段违规明细，使用纯 Java |
| `sharedkernel` | Working Set 领域、应用服务、持久化适配器及业务错误码 |
| `alm` | Requirement 领域、应用服务、持久化适配器及业务错误码，依赖 Shared Kernel |
| `deploys` | Spring Boot 启动、全局 Web 配置、Swagger UI、Knife4j、运行资源和跨模块集成测试 |

各业务模块的 REST Controller 和请求类型位于自身的 `adapters` 包，事件监听入口位于 `application.eventhandler`。

使用 JDK 21，在仓库根目录构建并启动：

```sh
mvn -pl deploys -am -DskipTests package
java -jar deploys/target/deploys-0.1.0-SNAPSHOT.jar
```

构建默认使用 `dev`，可在 IDEA 的 Maven Profiles 勾选 `local` 或 `demo`，命令行可用 `-Plocal` 或 `-Pdemo`。IDEA 中运行 `bootstrap.WorkingsetApplication`。应用需连接 PostgreSQL 和 Redis，默认端口及连接配置见 [application.yml](deploys/src/main/resources/application.yml)。
