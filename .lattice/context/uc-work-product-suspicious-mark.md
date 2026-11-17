---
feature: uc-work-product-suspicious-mark
status: complete
requirement_doc: null
created: 2026-08-11
updated: 2026-08-19
---

# UC 工作产品可疑标记（独立查询接口）

> UC 元模型下，提供独立接口按过程计算元素（工作产品）的实时疑问标记（无产出活动），过程元素树不再返回 `marks` 字段。前端在元素增删改或关系变更时可轻量快速调用该接口刷新标记状态，无需全量重载高代价的整棵建模树。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-08-11 | 独立「元模型打标」SPI（类比 `IMetaConstraint`），UC 实现复用/抽取与 `WorkProductOutputValidator` 相同的「无 output」判定；树查询走轻量路径，不跑完整发版报告 | 打标与发版校验同构、职责分离；树查询成本可控，规则可共享判定避免分叉 | B：树查询直接复用发版校验管线只跑 `work-product-output`（零分叉但耦合高、成本高） |
| 2026-08-11 | V1 只做「疑」标识；不做「外部输入」单独标记 | UC UI 仅要求「疑」+ hover；外部输入由用户看到「疑」后人工确认即可 | 同期增加「外」等第二类标记 |
| 2026-08-11 | Marker 经 `uc/process.json` 的 `treeMarkers[]`（与 `constraints` 同构）配置驱动 | 与发版校验装配方式一致，可扩展 | B：按 metamodelIdent 的 Java 注册表（改动少但扩展靠改代码） |
| 2026-08-11 | 使用轻量 `TreeMarkContext`（元素摘要 + tab 关联 map），不复用 `ConstraintContext` | 打标只需关联与元素类型；解耦校验专用结构 | B：直接复用 `ConstraintContext` |
| 2026-08-11 | 打标文案用 `ElementMarkType` 枚举定义业务码 + tip 的 `I18n.sys.tips.*` 资源；`toDto()` 下发编码与当前语言文案 | 对齐 WidgetOperationType i18n 模式 | Marker 内散落常量 |
| 2026-08-11 | 暂不提交 Flyway i18n 数据；枚举使用 `PLACEHOLDER_*` 资源码，同事配置词条后由本人更新枚举 | 国际化配置有专人负责 | 本迭代直接写入 i18n SQL |
| 2026-08-12 | API 字段统一为 `List<ElementMarkDto>`；单项仅 `type` + `tipCode` + `tip` | 「是否疑问」由列表是否含对应 type 表达；tips 供 hover；保留多 mark 扩展 | B：`Boolean suspicious` 单字段（多疑问类型需再加字段） |
| 2026-08-12 | 去掉 badge/icon 字段；前端自行按 type 渲染 "!" 等 UI 徽标 | 后端只给疑问类型与 tip，图标多语言由前端控制 | 后端下发 icon/badge |
| 2026-08-19 | **解耦元素标记与元素树**：移除树节点 `marks` 返回值，新增 `POST /api/gate_flow/process/query_element_marks` 独立接口 | 刷新整棵树开销大，元素标记在活动关联等操作发生变化时需要高频刷新；独立接口可让前端轻量轮询/局部刷新，树结构保持纯粹 | B：树与独立接口并存均返回 marks（双维护、易产生客户端时序不一致）；C：树接口加入参开关（树节点序列化开销仍存） |
| 2026-08-19 | 实现完成：移除树 `marks` 字段及回填逻辑；新增 `query_element_marks` 接口；全量单元测试通过 | 按独立接口蓝图 Full autonomy 落地 | — |

## Open Questions

（无）

## Constraints

- 标记为实时计算，**不修改**工作产品持久化数据 / schema
- 打标算法为 **UC 元模型特有**，经 `treeMarkers` 机制挂载；非 UC 过程返回空 Map
- 命中条件（任一）：仅有输入无输出；无输入无输出（本质：**无任何活动/工作流 output 关联**）
- 树节点 `ProcessElementTreeResp` 移除 `marks` 字段；`queryElementTree` 不再调用打标 enrich 逻辑
- 新增独立 POST 接口 `/api/gate_flow/process/query_element_marks`，入参为 `ProcessElementMarksQueryReq`（继承 `BaseGfProcessReq`：`workspaceId`, `version`）
- 响应返回 `Map<Long, List<ElementMarkDto>>`（以 `elementId` 为 key；无疑问的元素不出现在 key 中或对应空列表）
- 单元素异常：warn + 跳过，不影响其余元素标记返回

## Key Files

| Path | Role | 改动类型 |
|------|------|----------|
| `gf-biz/.../rest/req/ProcessElementMarksQueryReq.java` | 查询元素标记请求 DTO | 新增 |
| `gf-biz/.../rest/ProcessRest.java` | 暴露 `/query_element_marks` 接口 | 修改 |
| `gf-biz/.../management/ProcessManagement.java` | 编排 `queryElementMarks`，移除 `queryElementTree` 中的 `appendMarks` | 修改 |
| `gf-biz/.../rest/resp/ProcessElementTreeResp.java` | 移除 `marks` 字段 | 修改 |
| `gf-biz/.../management/ProcessElementTreeSupport.java` | 移除 `appendMarks` / `applyMarks` 及 `IElementMarkService` 依赖 | 修改 |
| `gf-sdk/.../dto/ElementMarkDto.java` | `type` / `tipCode` / `tip` DTO | 保留 |
| `gf-sdk/.../enums/ElementMarkType.java` | 疑问类型枚举 + tip i18n 资源映射 | 保留 |
| `gf-sdk/.../tree/IElementMarkService.java` | 过程元素标记服务 SDK 接口 | 保留 |
| `gf-sdkImpl/.../marking/ElementMarkServiceImpl.java` | 打标门面实现 | 保留 |
| `gf-sdkImpl/.../marking/markers/uc/UcWorkProductSuspiciousMarker.java` | UC 疑问标记计算 | 保留 |
| `gf-sdkImpl/.../marking/WorkProductOutputUsage.java` | 共享「是否被 output 引用」判定 | 保留 |
| `gf-sdkImpl/.../mm/uc/process.json` | `treeMarkers` 规则注册 | 保留 |

## Design Checkpoint

- Current: Implementation complete（独立接口与树解耦实现完成）
- Approved: Level 1–4
- Next: 建议执行 `/review` 进行代码审查

## Design: Level 1 -- Capabilities

1. **独立元素标记查询** — 提供 `POST /api/gate_flow/process/query_element_marks` 接口，支持根据 `workspaceId` + `version` 查询当前过程中所有元素的疑问标记集合，返回 `Map<Long, List<ElementMarkDto>>`。
2. **树接口解耦纯净化** — `queryElementTree` 响应中彻底移除 `marks` 字段与打标回填逻辑，降低树查询与组装开销。
3. **轻量前端局部刷新** — 前端在建立/移除关联或增删元素时，无需重拉耗时的整棵树，直接调用独立标记接口静默刷新内存中的标记状态并更新节点视图。
4. **元模型驱动与零副作用** — 继续复用 `IMetaTreeMarker` 机制，按元模型配置计算，只读无 DB 写入；非 UC 过程返回空 Map。
5. **规范文案与前端自适应渲染** — 后端下发 `type` + `tipCode` + `tip`，前端按 `type` 渲染 "!" 徽标及 hover 提示。

## Design: Level 2 -- Components

| # | Component | Layer | 职责 | DDD 角色 |
|---|-----------|-------|------|----------|
| 1 | **ProcessElementMarksQueryReq** | `gf-biz/rest/req` | 元素标记查询请求参数（继承 `BaseGfProcessReq`） | DTO / Request |
| 2 | **ProcessRest.queryElementMarks** | `gf-biz/rest` | 路由 `@PostExchange("/query_element_marks")` | 适配器 / API 契约 |
| 3 | **ProcessManagement.queryElementMarks** | `gf-biz/management` | 加载过程上下文与元素，调用打标服务 | 应用服务编排 |
| 4 | **IElementMarkService** | `gf-sdk/tree` | 元素打标门面接口 | 领域服务契约 |
| 5 | **ElementMarkServiceImpl** | `gf-sdkImpl/marking` | 调度 `IMetaTreeMarker` 并收集打标结果 | 领域服务实现 |
| 6 | **UcWorkProductSuspiciousMarker** | `gf-sdkImpl/marking/markers/uc` | UC 无 output 活动关联判定 | 领域规则（UC） |
| 7 | **ProcessElementTreeResp** | `gf-biz/rest/resp` | 移除 `marks` 字段，保持树节点职责专一 | 视图 DTO |
| 8 | **ProcessElementTreeSupport** | `gf-biz/management` | 移除 `appendMarks`/`applyMarks`，解耦打标服务 | 树装配辅助 |

## Design: Level 3 -- Interactions

### Flow 1: 独立查询元素标记（POST /api/gate_flow/process/query_element_marks）

```
前端 (React/Axios)
  │
  ├── 1. POST /api/gate_flow/process/query_element_marks { workspaceId, version }
  ▼
ProcessRest.queryElementMarks(req)
  │
  ├── 2. req.validate()
  ├── 3. processManagement.queryElementMarks(req)
  ▼
ProcessManagement
  │
  ├── 4. 解析 ProcessContext (actualProcessId, metamodelIdent)
  ├── 5. 非 UC 或空上下文直接返回 Collections.emptyMap()
  ├── 6. processService.getAllElements(actualProcessId) 加载全量元素
  ├── 7. elementMarkService.resolveMarks(actualProcessId, metamodelIdent, loadedElements)
  ▼
ElementMarkServiceImpl
  │
  ├── 8. MetaProcessService 获取 treeMarkers (UcWorkProductSuspiciousMarker)
  ├── 9. TreeMarkContextFactory.build(actualProcessId) 构建关联上下文
  ├── 10. DefaultTreeMarkCollector 收集所有命中元素
  ▼
返回 Map<Long, List<ElementMarkDto>> (如 { 2001: [{ type: "work-product-suspicious", ... }] })
```

### Flow 2: 过程元素树查询（POST /api/gate_flow/process/query_element_tree）

```
前端 (React/Axios)
  │
  ├── 1. POST /api/gate_flow/process/query_element_tree { workspaceId, version }
  ▼
ProcessRest → ProcessManagement.queryElementTree
  │
  ├── 2. 加载元素与分类节点，进行权限过滤
  ├── 3. convertElementsToTreeNodes & buildFinalTree 组装树结构
  ├── 4. appendBatchAndSortOperations 下发操作按钮
  │      （不再调用 appendMarks，ProcessElementTreeResp 不含 marks）
  ▼
返回 List<ProcessElementTreeResp> 纯粹树结构
```

## Design: Level 4 -- Contracts

### 1. 请求 DTO: `ProcessElementMarksQueryReq`

```java
package com.airedgesoft.gf.biz.process.rest.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 过程元素标记查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessElementMarksQueryReq extends BaseGfProcessReq {
}
```

### 2. REST 接口定义 (`ProcessRest.java`)

```java
@PostExchange("/query_element_marks")
@Operation(summary = "查询过程元素标记", description = "查询指定过程版本下所有元素的只读标记（如 UC 工作产品可疑标记）")
public Map<Long, List<ElementMarkDto>> queryElementMarks(@RequestBody ProcessElementMarksQueryReq req) {
    req.validate();
    return processManagement.queryElementMarks(req);
}
```

### 3. HTTP 报文契约示例

- **Request**:
```http
POST /api/gate_flow/process/query_element_marks
Content-Type: application/json

{
  "workspaceId": 10001,
  "version": "_wv"
}
```

- **Response**:
```json
{
  "2001001": [
    {
      "type": "work-product-suspicious",
      "tipCode": "I18n.sys.tips.PLACEHOLDER_WORK_PRODUCT_SUSPICIOUS_TIP",
      "tip": "请确认是否遗漏产出活动"
    }
  ]
}
```
*注：无标记时返回空 JSON 对象 `{}`。*

### 4. 树响应 DTO 变更 (`ProcessElementTreeResp.java`)

- 移除字段：
```java
// 删除该字段定义及 getter/setter
// private List<ElementMarkDto> marks;
```

### 5. `IElementMarkService` 接口签名（保持不变，复用现有高效批量算法）

```java
Map<Long, List<ElementMarkDto>> resolveMarks(Long processId, String metamodelIdent,
        List<? extends IProcessElementsInner> elements);
```

## Design Summary

- **核心变更**：从 `ProcessElementTreeResp` 中剔除 `marks` 字段；新增独立轻量接口 `POST /api/gate_flow/process/query_element_marks` 返回 `Map<Long, List<ElementMarkDto>>`。
- **性能与体验**：解耦树结构与标记计算，前端在关联关系发生变动时可通过轻量接口快速刷新标记字典，避免重拉整棵树的昂贵开销。
- **架构约束**：打标计算仍保持只读、无数据库 schema 副作用；通过 `IMetaTreeMarker` 机制实现元模型特异性隔离；异常安全降级保护。
- **Design Status**：**Approved（设计评审完成，可进入编码落地）**

