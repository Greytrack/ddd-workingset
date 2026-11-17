---
feature: 派生需求
requirement_doc: .lattice/requirements/features/derive-requirement.md
created: 2026-08-21
status: complete
---

# 派生需求

> 基于源需求派生一条同类型的新需求，自动加入源需求所在 Tracker 同层并建立派生关联。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|---|---|---|---|
| 2026-08-21 | 接口放 `RequirementRest`（`POST /api/requirement/derive`），Req/Resp 放 `requirement.rest.req` / `rest.resp` | 跟随现有 `convert` 接口模式，同域聚合 | 新建独立 Rest 类 |
| 2026-08-21 | 编排放 `RequirementDeriveManagement`（biz 层，`@Transactional`） | 架构规范：事务仅 biz `*Management` | 放 sdkImpl（违反分层，禁止） |
| 2026-08-21 | 同层挂载用 `dynamicAdd` + `setParentId(源.parentId)` + `setTrackerId(源.trackerId)` | `DynamicAddReq` 原生支持 parentId/trackerId，创建即同层，无需事后移动 | 创建后调 `persistTreeDragSql` 移动 |
| 2026-08-21 | 建关联内联在 Management（调 `GenericTabManagement.tabDynamicMount`） | 单一调用点，避免过度抽象 | 新建防腐层类（`RequirementDeriveAssociationCreator`） |
| 2026-08-21 | 关联类型占位 `RelationTypeEnum.IS_DERIVED_FROM`（衍生自），集中在 `DERIVE_RELATION_TYPE` 常量 | 合法枚举、列表项不缺失；正式「派生与」落地后只改一处 | 新增枚举（须数据库列表项同步，产品后期做） |
| 2026-08-21 | 权限走 `dynamicAdd` 默认（不 skipPrivilegeCheck） | 派生=创建需求，受框架默认权限约束，fail-closed | `skipPrivilegeCheck=true`（仅转化等系统操作需要） |

## Open Questions

- 正式关联类型「派生与」的列表项与枚举落地后，替换 `DERIVE_RELATION_TYPE` 常量。

## Constraints

- 需求创建走 DataObjectFramework，禁止直接 SQL
- 关联挂载走既有关联页签机制（`RelatedObject` + `ATTR_RELATIONSHIP_TYPE` / `ATTR_PROPAGATION_TYPE`）
- 事务仅 biz 层 `*Management`

## Key Files

| Path | Role |
|---|---|
| infrastructure/biz/src/main/java/com/airedgesoft/plm/requirement/rest/req/DeriveRequirementReq.java | 入参 DTO |
| infrastructure/biz/src/main/java/com/airedgesoft/plm/requirement/rest/resp/DeriveRequirementResp.java | 出参 DTO |
| infrastructure/biz/src/main/java/com/airedgesoft/plm/requirement/management/RequirementDeriveManagement.java | 编排（事务） |
| infrastructure/biz/src/main/java/com/airedgesoft/plm/requirement/rest/RequirementRest.java | REST 端点 |
| infrastructure/biz/src/test/java/com/airedgesoft/plm/requirement/management/RequirementDeriveManagementTest.java | 单元测试 |
