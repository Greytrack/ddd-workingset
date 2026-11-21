# lib-deps

**三方库版本清单**（`packaging=pom`，**独立 BOM**）。只维护 **引入的第三方依赖** 的版本；新旧空间（`backend` / `airedge-v4`）各模块声明三方依赖时 **不写 `<version>`**。

## 规则

| 项 | 约定 |
|----|------|
| 职责 | 三方库（及三方 BOM）的版本号 SSOT，**新旧空间共用** |
| 版本号出现位置 | **只在本模块**（`properties` / `dependencyManagement`） |
| 消费方式 | `backend/pom.xml` 与 `airedge-v4/pom.xml` 的 `dependencyManagement` **import** 本模块；子模块只写 `groupId` + `artifactId` |
| 新增 / 升级三方库 | **必须**在本模块定义（或覆盖）版本号后再被引用 |
| 不放什么 | 业务代码；本仓库一等模块的坐标清单（见根 `pom.xml` / 根 README） |

## 独立 BOM（无 parent）

本模块 **不设 parent**（曾用 `backend` 作 parent，现已独立）：

- 独立后 `backend/pom.xml` import 本模块不会形成 BOM 解析环。
- 自带与 `backend` 根一致的 Nexus `repositories` / `pluginRepositories`（无 parent 继承，需自持）。
- 版本用 `properties` 集中定义、`dependencyManagement` 引用；`lib-exception` / `lib-id` 等新空间内部模块坐标也在此维护。

## 构建顺序

`backend` 构建前必须先 install 本模块（`lib-deps` 不在 backend reactor 中）：

```bash
mvn -f airedge-v4/lib-deps/pom.xml install -DskipTests -q
```

CI（`Jenkinsfile` backend 阶段）已处理该前置步骤。

`lib-*` 模块清单与边界见 [v4-architecture.md](../../.lattice/standards/v4/v4-architecture.md) §2.1。
