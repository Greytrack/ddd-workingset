---
feature: "AirEdge Backend 2.1 Knowledge Base"
mode: override
created: "2026-06-12"
---

> AirEdge Backend 2.1 知识库：为 Lattice 技能提供项目身份上下文（技术栈、架构、可信来源、目录结构）。详细编码规范以 `.agent/` 与 `AGENTS.md` 为权威来源。

## 1. Architecture Overview

企业级研发管理后端，模块化单体（Maven 多模块 + Spring Boot），集成四类业务域与统一对象底座：

- **PLM**：产品生命周期管理
- **LINK**（模块名 `links`）：需求追踪管理
- **DQA**：质量管理
- **GateFlow (gf)**：流程管理
- **ObjectFramework**：`infrastructure/` 统一业务对象框架

组件内 Biz → SDK → SDKImpl；组件间同进程 Spring 调用，禁止跨组件 Maven 直接依赖，须经 `infrastructure/`。对外 HTTP/REST；身份与外部系统保持可扩展（LDAP、OAuth2、PX 插件 JAR、开放 REST 供第三方对接）。

## 2. Tech Stack and Versions

- **Language**: Java 17 | **Framework**: Spring Boot 3.3.0 | **Build**: Maven 多模块
- **Persistence**: PostgreSQL 15.14 + MyBatis-Plus 3.5.8 + Flyway 7.15.0（**不是** JPA/Hibernate）
- **Cache**: Redisson 3.31.0 | **API**: REST + springdoc 1.7.0 + Knife4j 4.5.0（直接返回 DTO，**禁止** `Result` 包装）
- **Auth**: 自研（`AuthManagement` + `GenericOauthClient`）；Bearer Token（jjwt 0.9.1）；BCrypt（仅 `spring-security-core`，**不是** Spring Security 全栈）
- **Identity**: `IdpTypeEnum` — LDAP（`LdapManagement` + Spring LDAP）、OAUTH2（`IGenericOauthClient` / `IdpManagement`）
- **PX 扩展**: `ServiceLoader` + `PxClassLoader`；目录 `server.extensions.folder`（默认 `../../pxExtTestDir`）
- **Testing**: JUnit 5 5.10.0 + Mockito 5.5.0；优先集成测试
- **PX SPI**（`com.airedgesoft.plm.px`，`META-INF/services` 注册，包名无强制要求）：
  - `IEventAction` — 自定义事件处理
  - `ICustomAutoNumber` — 自定义自动编号
  - `IUserSyncAction` — 第三方 IdP 用户同步
  - `IReviewExternalDocumentClient` — DQA 评审外围文档源
- **Not X**: 非 Node/Go；非 JPA；非 Spring Security 全栈；Biz 禁止 `SpringContextUtils.getBean()`；**新技术须团队确认**（以根 `pom.xml` 为准）

## 3. Curated Knowledge Sources

| Topic | Source | Why We Trust It |
|-------|--------|-----------------|
| 项目总规则 | `.agent/project_rules.md` | 权威规范入口 |
| 架构 | `.agent/rules/10-architecture.md` | 分层与依赖约束 |
| 实体上下文 | `.agent/rules/13-entity-context-and-collaboration.md` | Context-first |
| REST / Swagger | `.agent/rules/04-rest-controller.md`, `06-swagger.md` | API 与 DTO |
| 测试 | `.agent/rules/07-testing.md` | 覆盖率与 TDD |
| Agent 速查 | `AGENTS.md` | 目录与技能索引 |
| PX SPI 契约 | `AirAPI/.../com/airedgesoft/plm/px/*.java` | 扩展接口定义 |
| API 测试 | `postman/` | Newman 集合 |
| Flyway | `upgrade/src/main/resources/flyway/` | Schema 真相来源 |
| 扩展点技能 | `.pi/skills/create-*-extension/SKILL.md` | Processor / TabQuery |
| DataObject | `.pi/skills/dataobject-model/SKILL.md` | 对象框架 |
| Spring Boot 3.3 | https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/ | 与 BOM 一致 |
| MyBatis-Plus | https://baomidou.com/ | 与 3.5.8 一致 |
| PostgreSQL 15 | https://www.postgresql.org/docs/15/ | 与库版本一致 |

PX / LDAP / OAuth 集成无独立内部文档，以代码为准（`LoadPxClassService`、`LdapManagement`、`GenericOauthClient`）。

## 4. Project Structure

```
airedge-backend-2.1/
├── AirAPI/              # AirConstants、PX SPI
├── apps/full/           # Spring Boot 入口
├── components/
│   ├── plm/ links/ dqa/ gf/   # 各含 biz / sdk / sdkimpl
├── infrastructure/      # biz + sdk + sdkImpl + tools（ObjectFramework、认证、PX）
├── upgrade/             # Flyway
├── postman/
└── .agent/              # 团队规范
```

包名：`com.airedgesoft.{component}.{layer}.{module}`；模块：`{component}-{type}`（如 `links-biz`）。

## 5. Project Conventions

- LINK 产品线 ↔ Maven 模块 `links`
- 常量：`AirConstants` **仅**放对外契约类标识——分类 `classId` / 页签 / 属性 **`apiId`**（及文档标明的极少例外）；**禁止**把角色模板实例 ID、JobFunction ID、业务种子行 ID 等内部固定 ID 写入 `AirConstants`（AirAPI 对外暴露面）。此类内部 ID 落在消费侧 sdkImpl（如 DataObject 上的 `static final`，参见 `WORK_SPACE_OWNER_JOB_FUNCTION_ID`）或模块内常量类；仍禁止魔法字面量散落
- 事务：仅 biz `*Management`；sdk/sdkImpl 禁止 `@Transactional`
- 实体上下文：入口加载，禁止 SDK ID 捷径 API（规则 13）
- 扩展：Processor/TabQuery 按规则 11/12；PX 经 `server.extensions.folder` 热加载
- Git 提交：中文 `类型(范围)：主题; #编号;`；**禁止**在提交消息中加入 AI 协作者信息（如 `Co-authored-by: Cursor <...>`）
- 技术选型以 `pom.xml` 为准，新依赖须团队确认
- Lattice 设计/实现：`/skill:design-blueprint` 与 `/skill:code-forge` 须**显式指定**需求或设计文档路径（如 `.lattice/requirements/features/<slug>.md`、`.lattice/context/<slug>.md`），禁止 AI 自动猜测

---
*Generated for AirEdge Backend 2.1 on 2026-06-12. Mode: override.*
*Produced by the knowledge-priming-refiner skill.*
