---
feature: dqa-xbom-pr-integration
requirement_doc: docs/xbom/PR平台与研制保证协同平台接口的信息化需求-v7.1.1(1).docx
created: 2026-08-07
---

# DQA ↔ XBOM 问题报告对接

> 研制保证协同平台问题报告（ISSUE / PR / IR）与 XBOM 双向对接：创建同步出站、状态回写入站、mock 仿真；客户映射落 PX。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-08-07 | 本期范围含场景一 ISSUE、场景二 PR，并为 F 项目 IR 留占位 | 用户确认 Option C，覆盖需求主文并预留 F 项目扩展 | 仅 PR；仅 ISSUE+PR 不含 IR 占位 |
| 2026-08-07 | 出站 XBOM HTTP 契约本期自拟；字段语义以需求表为准，字段名待正式接口到位后同步更新 | 正式 XBOM 接口文档尚未提供；先打通端到端与 mock | 阻塞等待正式文档 |
| 2026-08-07 | 产品核提供 Open API 入站回写；字段映射与出站 HTTP 全在 px-comac（经 DataObject 后事件） | 区分产品与客户业务；对齐现有 COMAC PX 范式；不新增 AirAPI SPI | 产品侧完整 IXbomPRService；新增 AirAPI Port |
| 2026-08-07 | Level 1 Capabilities 批准（保留 5 条） | 用户确认范围与能力清单 | 将 IR 从能力降为后续项 |
| 2026-08-07 | 入站回写仅更新属性（`XBOMPRStatus`/处理结论等），不驱动本系统工作流 | 与 AssociatedPR 页签改状态一致；降低与 XBOM 流程耦合 | 属性更新 + 推进工作流 |
| 2026-08-07 | 场景一 ISSUE：本地创建 ISSUE（问题报告子类型/字段区分）后即同步 XBOM；与 PR 一样走添加后事件；后续可改 | 用户暂定；统一触发模型，降低分支复杂度 | 流程完全结束后再同步；监听检查单/评审任务结束 |
| 2026-08-07 | Level 1 能力 2 修订为「创建 ISSUE 后即同步」 | 与场景一触发决策对齐 | 保留「流程结束后」原文 |
| 2026-08-07 | Level 2 Components 批准（4 组件：DataObject / Open Callback / PX EventAction / Mock） | 用户确认；不新增 AirAPI SPI | 含 AirAPI Port 的五组件方案 |
| 2026-08-07 | 出站事件的同步/异步与失败是否阻断，留给事件程序绑定配置期决定，设计不写死 | 引擎已有 `EventExecutionModeEnum` | 设计阶段固定阻断策略 |
| 2026-08-07 | Level 3 Interactions 批准 | 用户确认 | — |
| 2026-08-07 | 入站 Open REST 落在 `components/dqa/biz/.../openapi` | 域内聚；路径为通用 `/api/open/problem_report/**` | infrastructure openapi 包；`/api/open/xbom/**` |
| 2026-08-07 | Level 4 Contracts 批准 | 用户确认落点 A | — |
| 2026-08-07 | Design approved at Level 4. Blueprint complete ready for implementation. | 四级设计均已确认并落盘 | — |
| 2026-08-07 | 实现完成（本地暂存不提交）：入站 Open API + px-comac EventAction + mock-server xbom + 接口文档 V0.2 | Full autonomy；用户要求不 commit/push | — |
| 2026-08-07 | 入站契约通用化：`/api/open/problem_report/update_status`，字段 `externalNumber`/`status`/`sourceSystem`；对齐既有 OpenApi 命名与 snake_case | 对照 `/api/open/data_object`、`OpenApi*`；避免路径/字段绑定 XBOM | 保留 `/api/open/xbom/**` |
| 2026-08-07 | XBOM PR 状态确认为工作流（DefaultReviewRemarkWorkFlow / Overview.Status）；入站改为 `changeStatus`，与 XBomPrStatusEnum 节点一致；处理结论复用 Disposition | 用户确认若为工作流则推进工作流 | 独立属性 XBOMPRStatus |
| 2026-08-07 | 外部编号固定使用 apiId `XbomExternalNumber`；属性模型现场配置，本期不补 Flyway | 用户确认先按约定 apiId 访问 | 先落库迁移再建属性 |
| 2026-08-07 | 入站为内部系统通知：免 token（鉴权白名单）；必传操作人工号 `identityNumber`（IdentityNumber），按工号建会话后推进工作流 | 降低三方对接成本；审计/权限仍绑定真实操作人 | 保留 Bearer token |
| 2026-08-10 | 入站新增必填 `approvalAction`：Approve / Reject / ChangeStatus，分别走批准、拒绝、状态变更 | 用户在接口文档补充审批动作字段 | 仅 ChangeStatus |
| 2026-08-10 | 客户文档定稿：去掉鉴权说明与「亦可中文」；入站契约去掉 `recordType`；`updatedAt`/`sourceSystem` 必填；`approvalAction` 仅英文 apiId | 文档将发给客户，代码与文档参数表对齐 | 兼容中文动作名；可选 recordType |
| 2026-08-20 | 出站契约依据正式接口文档《创建XBOM ISSUE接口文档》《创建XBOM PR接口文档》定稿：HTTP POST + multipart/form-data，支持多文件 files；响应 data 按 ISSUE/PR 键返回编号（如 ISSUE-C-000001）；必填字段与枚举约束对齐文档；更新 px-comac 与 mock-server，删除遗留产品侧 Mock 服务 | 用户提供商飞正式接口文档（2026.8.20 V1） | 保留自拟 JSON 契约 |
| 2026-08-20 | XBOM 提供方确认接口采用标准平铺 multipart/form-data 表单字段（各属性为独立 Form Field Part，附件为 files Part），PX 客户端固定为平铺表单传输，Mock 服务保持多模兼容 | 提供方与用户沟通确认 | 混合 data JSON Part 模式 |
| 2026-08-20 | 出站数值取消环境变量兜底，统一按「PR自身属性优先、所属产品空间属性回退」策略读取；字段与 apiId 集中维护于 PX `XbomFieldMapping`，自动支持列表项与文本类型 | 现场配置灵活性与去硬编码 | 环境变量兜底 / 代码分散写死 apiId |

## Open Questions

- F 项目 IR 的属性与流程定制细则（需求未定稿；本期仅占位）
- 部署时事件绑定推荐默认：异步 vs 同步——配置期再定

## Constraints

- 客户侧字段映射、XBOM HTTP、项目差异必须落在 `px-comac`
- **不新增** AirAPI SPI；出站用 DataObject 添加后事件 + `IEventAction`
- 入站 `/api/open/**`，直接返回 DTO，禁止 Result 包装；状态推进工作流（DefaultReviewRemarkWorkFlow）
- 入站免 token（`excludePath` 含 `/api/open/problem_report/update_status`）；必传 `identityNumber`
- 入站按 Number（内外部编号一致）定位问题报告；不再使用独立外部编号属性 `XbomExternalNumber`
- 出站执行模式（同步/异步）配置期决定
- mock-server 仿真创建与可选回写回调
- 接口文档：`docs/xbom/研保-XBOM-接口定义.md`（及导出 docx）

## Design: Level 1 -- Capabilities

1. 同步创建 PR 到 XBOM 并回写编号
2. 创建 ISSUE 后即同步 XBOM（含结束语义）并回写编号
3. 接收 XBOM 状态回写（推进工作流）
4. F 项目 IR 可扩展（占位）
5. mock-server 可联调仿真

## Design: Level 2 -- Components

| # | 组件 | 归属 | 职责 |
|---|------|------|------|
| 1 | 问题报告 DataObject | 产品既有 | 外部编号/状态/结论；添加后事件 |
| 2 | Xbom Status Callback | `dqa-biz` openapi | 入站按编号推进工作流状态 |
| 3 | Comac Xbom PX Adapter | px-comac | 出站映射 + HTTP |
| 4 | Xbom Mock Module | mock-server | 仿真 XBOM |

## Design: Level 3 -- Interactions

- **Flow A**：创建 → POST CREATE → PX → XBOM/Mock → 回写编号（sync/async 配置期）
- **Flow B**：XBOM → Open API → Management → `changeStatus`（+ 可选 Disposition）
- **Flow C**：Mock 创建 + 可选状态回调

## Design: Level 4 -- Contracts

### 入站 Open API（研保提供）

- `POST /api/open/problem_report/update_status`
- Rest：`com.airedgesoft.dqa.biz.openapi.rest.OpenApiProblemReportRest`
- Management：`ProblemReportOpenManagement#updateStatus`
- Req：`identityNumber`(必填), `number`(必填), `status`(必填), `approvalAction`(必填: Approve/Reject/ChangeStatus，仅英文), `disposition?`, `updatedAt`(必填, Long ms), `sourceSystem`(必填, 固定 XBOM)
- 会话：按 `identityNumber` → `createUserSessionForThreadLocal`（实现侧白名单；客户文档不写鉴权说明）
- 审批动作：Approve→`approve`；Reject→`reject`；ChangeStatus→`changeStatus`
- Resp：`instanceId`, `number`, `status`（直接 DTO）

### 出站 XBOM HTTP（自拟 / Mock）

- `POST /xbom/api/v1/issues|prs|irs`（接口方法文档标注 TBD，当前为约定占位）
- 协议：HTTP POST + `multipart/form-data` 平铺表单字段；支持多附件 `files`
- 响应包装：`errCode/success/errMessage/data.ISSUE` 或 `data.PR`，编号格式 `ISSUE-C-000001` / `PR-C-000001`
- PX：`IEventAction` 实现类 + 包内 HTTP 客户端（不进 AirAPI）

### 本系统属性占位

- `Number` (内外部编号一致), `XBOMPRStatus`(已有), `XbomDisposition`

## Design Summary

- **组件与分层**：DataObject（产品）+ Open Callback（dqa-biz）+ PX EventAction（px-comac）+ Mock（mock-server）；无新 AirAPI SPI
- **关键契约**：入站 Open API 状态回写；出站自拟创建 ISSUE/PR/IR；事件同步/异步配置期决定
- **架构约束**：客户映射仅 PX；入站只改属性；DTO 直出
- **领域**：问题报告聚合关联外部编号；ISSUE/PR/IR 由子类型或字段区分
- **Open Questions**：正式 XBOM 字段名；IR 细则；属性 apiId 核对；事件默认 sync/async
- **Design status**：**Approved — ready for implementation**

## Implementation Notes

- 入站：`OpenApiProblemReportRest` → `ProblemReportOpenManagement`；按工号建会话 → 按 Number 定位（内外部编号一致） → 按 `approvalAction` 走 approve/reject/changeStatus；结论写 `Disposition`；工号未匹配码 `10110126`；动作未识别码 `10110127`
- 出站：`XbomProblemReportCreateEventAction`（SPI 已注册）；需管理端绑定问题报告子类创建后事件；`AIREDGE_XBOM_BASE_URL`/`AIREDGE_XBOM_API_TOKEN`
- Mock：本地 `mock-server-xbom-work/`；回调免 Authorization，body 含 `identityNumber`、`approvalAction=ChangeStatus`
- 验证：`ProblemReportOpenManagementTest`；px-comac compile；`go test ./internal/systems/xbom/`
- 接口文档：`docs/xbom/研保-XBOM-接口定义.md` / `.docx`（V0.9，含 approvalAction，按 number 定位）

## Key Files

- `.lattice/context/dqa-xbom-pr-integration.md` — 本蓝图
- `docs/xbom/创建XBOM ISSUE接口文档.docx` — 商飞正式接口文档（2026.8.20 V1）
- `docs/xbom/创建XBOM PR接口文档.docx` — 商飞正式接口文档（2026.8.20 V1）
- `docs/xbom/PR平台与研制保证协同平台接口的信息化需求-v7.1.1(1).docx`
- `docs/xbom/研保-XBOM-接口定义.md` / `.docx` — 开放接口定义（模板版式）
- `backend/components/dqa/biz/.../openapi/rest/OpenApiProblemReportRest.java`
- `backend/components/dqa/biz/.../openapi/management/ProblemReportOpenManagement.java`
- `backend/components/dqa/sdk/.../enums/XBomPrStatusEnum.java`
- `px-comac/.../event/XbomProblemReportCreateEventAction.java`
- `px-comac/.../xbom/XbomIssueFieldMapping.java` / `XbomPrFieldMapping.java` / `XbomIrFieldMapping.java` — ISSUE / PR / IR 独立字段映射枚举
- `px-comac/.../xbom/XbomHttpClient.java`
- `mock-server-xbom-work/internal/systems/xbom/module.go`
