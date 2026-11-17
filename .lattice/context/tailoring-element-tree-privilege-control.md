---
feature: tailoring-element-tree-privilege-control
requirement_doc: null
created: 2026-07-13
status: implemented
reference: .lattice/context/processes-operation-privilege-control.md
last_design_checkpoint: "2026-07-23 Final — association add/delete = host MODIFY only; no TABLE_*"
---

# 裁剪元素树权限对接

> 参照过程元素树权限对接，为裁剪问答树 `operations`、详情动作入口与写路径 Handler 统一接入 `IPrivilegeEngine`；问题/答案修改及对元素的裁剪动作关联走实例级 `MODIFY`。
>
> **2026-07-15 修订**：关联跟元素编辑权；评论（含关系评论）跳过权限；裁剪答案 Action 跟答案编辑权。
>
> **2026-07-23 最终定案**：过程元素添加/删除关联关系 **统一校验宿主元素 `MODIFY`**，**不再校验**关联关系页签 `TABLE_ADD` / `TABLE_DELETE`。`#15081` 写闸与此一致（非页签权）。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-23 | **最终定案**：关联增删统一宿主元素 `MODIFY`；明确废止「添加/删除权限 = 关联页签 TABLE_*」解读 | 产品确认；与 7-15 Spec 及 `#15081` 实现一致；权限名「添加」=`TABLE_ADD` 易与「添加关联」混淆 | 改回 `PrivilegeSpec.table(TABLE_ADD/DELETE, 420435)`（拒绝） |
| 2026-07-23 | `#15081` 写闸语义确认：`assertAssociationSourceModifyPrivilege` = 元素 `MODIFY`；底层 `skipTab*` 跳过页签 | 代码与单测已按 MODIFY；注释曾强调 TABLE_* 易误读为仍校验页签 | 改为校验 TABLE_*（与最终定案冲突） |
| 2026-07-15 | Level 1 Capabilities 批准（关联/评论/答案动作映射修订） | 用户明确：能编辑即可增删关联；评论全员开放；答案 Action 走答案 MODIFY；过程元素与裁剪问答共用 | 仅改写闸不改 canModifyActions（拒绝：入口与写闸不一致） |
| 2026-07-15 | 元素评论 ADD/EDIT/DELETE 与关系评论全部跳过权限（PrivilegeSpec=null） | 用户确认「所有用户都可评论」含改删；对齐合规评论「本特性不拦截」模式 | 仅 ADD 开放、EDIT/DELETE 仍要 MODIFY；评论走作者本人校验 |
| 2026-07-15 | Level 2 Components 批准：不新增组件，仅改 PrivilegeSpec；Support/写闸跟随 empty Spec 放行 | 保持 Spec 唯一真相源；对齐合规评论模式 | Support 内硬编码操作码跳过（拒绝） |
| 2026-07-15 | Level 3 Interactions 批准：树过滤 / canModifyActions / Widget 写闸 / batchDelete 四路径跟随新 Spec | 数据流结构不变，仅判定结果变化 | 详情另开独立权限 API（拒绝：多余） |
| 2026-07-15 | 本轮仅后端修改；前端无需改动 | 前端已消费 operations / canModifyActions；后端改 Spec 后入口与写闸自动对齐 | 同步改前端隐藏逻辑 |
| 2026-07-15 | 写路径必须对关联/评论页签调用 skipTabAdd/skipTabDelete，否则 DataObject 底层仍按 TABLE_* 拦截 | 与合规映射、附件「跟实例编辑」同一模式；PrivilegeSpec 只控 Biz 写闸，不替代 Table.createRows/removeRows 内鉴权 | 仅改 Spec 不碰 Handler（会导致有编辑权仍被页签拒绝） |
| 2026-07-15 | Level 4 Contracts 批准（含 skipTab）；设计完成可实现 | Spec 映射 + 写路径页签跳过 + 仅后端；前端零改动 | — |
| 2026-07-15 | Design approved at Level 4 (revision). Blueprint complete ready for implementation. | 四级齐全且含底层 skip 约束 | — |
| 2026-07-15 | 实现完成：PrivilegeSpec 重映射 + 关联/评论/答案动作写路径 skipTab；前端未改 | 双层门禁对齐；单测 Spec/写闸/评论覆盖同步 | — |
| 2026-07-16 | `EDIT_ASSOCIATION_NOTES` Spec=null：全员可改关联备注 | 用户确认；Biz 写闸放行，写路径仍 skip 页签启用态 | 继续跟宿主 MODIFY |
| 2026-07-15 | review 修订：`batchOperateAssociation` 补宿主 MODIFY；评论经 `ICommentableInner(..., skipTabPrivilege=true)` 同实例 skip（默认 false） | `getTable` 每次新实例，Handler 外层 skip 无效；PLM 默认仍鉴权 | 全局 default 无条件 skip / 只在 Handler 外层 skip |
| 2026-07-14 | 虚拟根 BATCH_DELETE/DRAG 以过程实例鉴权；无实例 fail-closed | 根上删除/拖拽曾固定下发；对齐过程树锚点鉴权 | 仅 CREATE 受控、实例级保留下发 |
| 2026-07-14 | 写闸：`elementIds` 批量 op 逐目标鉴权；实例级目标缺失 fail-closed | 关闭仅鉴权 req.elementId / silent skip 越权缺口 | 仅锚点 elementId |
| 2026-07-14 | 权限评估收敛 `WidgetOperationPrivilegeSupport`；过程树二次过滤 scope=DispatchPolicy.resolve | 消除 TM/PES 重复；去掉硬编码 isDispatchPrivilege 白名单 | 枚举硬编码列表 |
| 2026-07-14 | 附件 / 检查单模版 / 评论写操作改为元素实例 `MODIFY`；写闸跳过页签 TABLE_* | 有修改权限即可操作；降低页签权限配置负担 | 继续页签 TABLE_ADD/UPDATE/DELETE |
| 2026-07-14 | Option：ADD→CREATE、DELETE→实例 DELETE；COPY→CREATE；答案动作→关联页签 TABLE_ADD(+DELETE)/TABLE_DELETE | 对齐「新建/删实例/页签增删」规则；替换动作需双权限 | 父元素/答案 MODIFY 一刀切 |
| 2026-07-14 | 评论 ADD/EDIT/DELETE 曾走 Comments 页签 TABLE_*（已废止，见上条 MODIFY） | 评论页签跨类数字 ID 不同；与 ICommentableInner 一致 | 误绑固定 tabId 420491 |
| 2026-07-14 | 附件/检查单曾走页签 TABLE_*；EXPORT_MPP 仍走过程版本页签 TABLE_EXPORT_MPP | ExportMPP 按钮绑定 420425 | 误绑关联关系页签 |
| 2026-07-13 | 「裁剪问题/答案修改权限」= 实例级 `PrivilegeTypeEnum.MODIFY` (6) | 与过程树 RENAME/EDIT 语义一致；`CLIP_ADD`/`CLIP_REMOVE` 未落地且非本需求 | 使用未使用的 `CLIP_ADD`/`CLIP_REMOVE` |
| 2026-07-13 | 操作→权限映射与过程树对齐：ADD→CREATE；DELETE/BATCH_DELETE→DELETE；RENAME/MOVE/COPY/DRAG + 答案动作关联→MODIFY | 用户确认；保持两棵树权限模型一致 | 全部写操作仅看 MODIFY |
| 2026-07-13 | 范围含三层：树 `operations` 过滤 + 详情动作入口 + 写路径鉴权 | 仅 UI 过滤可被直接调 API 绕过；写路径必须防卫 | 仅查询树过滤（与过程树当前实现同窄） |
| 2026-07-13 | Level 1 Capabilities 批准 | 范围与映射清晰 | — |
| 2026-07-13 | 写路径鉴权落在 Biz 编排入口（Option A）：`TailoringManagement` + `WidgetOperationManagement`；Handler 保持纯业务 | 映射一处维护；覆盖独立 REST 与 Widget 双入口；避免 Handler 漏鉴权 | 各 Handler / TreeService 各自鉴权（B） |
| 2026-07-13 | Level 2 Components 批准（含 Option A） | 与过程树引擎复用 + Policy 共享 | 不抽 Policy、逻辑内联 |
| 2026-07-13 | Level 3 Interactions 批准 | 三入口（树/详情/写）数据流与引擎调用清晰 | 详情不返字段、仅靠树猜测 |
| 2026-07-13 | Level 4 Contracts 批准；`UPDATE_DESCRIPTION` 纳入 MODIFY 写集合；失败统一 `ObjResultCode.NO_PRIVILEGE_OPERATION` | 描述编辑同属问题/答案修改；错误码不按权限类型再拆 | 详情不控描述、仅控动作；按 CREATE/MODIFY/DELETE 分错误码 |
| 2026-07-13 | Design approved at Level 4. Blueprint complete ready for implementation. | 四级设计齐全且已批准 | — |
| 2026-07-13 | Widget 写路径仅拦截「裁剪专属 op」或「目标为裁剪问题/答案」的共享 op | 避免误拦过程元素树 RENAME/DELETE/UPDATE_DESCRIPTION 等既有写路径 | 对 Policy 映射内全部 op 无条件鉴权 |
| 2026-07-13 | ~~虚拟根无实例时仅 CREATE(ADD) 受控；BATCH_DELETE/DRAG 保留下发~~ → 已由 2026-07-14 决策废止 | 当时对齐 L3；实为根上删除/拖拽固定下发漏洞 | 无实例则剔除全部实例级 op |

## Open Questions

无。

## Constraints

- 统一复用 `IPrivilegeEngine`，禁止自建 ACL。
- 不引入新的 PrivilegeType；不启用未使用的 `CLIP_ADD`/`CLIP_REMOVE`。
- 系统固定答案（「不知道」）与虚拟根：虚拟根 ADD 走目标子类 CREATE；BATCH_DELETE/DRAG 以**过程实例**评估 DELETE/MODIFY；无鉴权对象 fail-closed；固定答案不下发写操作。
- 树操作下发只控 UI；写路径强规则由 **Biz Management** 鉴权（非 Handler 内），规则与下发映射一致。
- 答案动作关联鉴权对象 = 答案实例，非目标业务元素；权限类型 = 答案实例 `MODIFY`（不再要求关联页签 TABLE_*）。
- **最终定案（2026-07-23）**：关联关系增删 / option 鉴权对象 = 宿主元素实例 `MODIFY`；**禁止**再按关联页签 `TABLE_ADD`/`TABLE_DELETE` 作为用户写闸；关联备注不鉴权；用户写路径 **跳过**关联关系页签 `TABLE_*`。
- 元素评论与关系评论：`PrivilegeSpec` 为空（本特性不拦截）；全员可 ADD/EDIT/DELETE。
- Widget 写闸：凡 `PrivilegeSpec` 非空的操作均鉴权（过程树共享 op 与裁剪专属 op 同源）；`ADD` 的 CREATE 除外仍走独立 REST；`PrivilegeSpec` 为空则写闸跳过。
- 批量 `elementIds`/`ids` 写操作对每个目标实例鉴权；实例级目标缺失 fail-closed。
- 本轮不改合规映射、附件/检查单、过程级 `CLIP_*`、Option CREATE/DELETE 既有规则。
- **仅限后端修改**：不修改任何 `web/` 下前端文件；前端继续消费既有 `operations` / `canModifyActions`。
- **写路径页签跳过**：凡本轮改为「跟 MODIFY / 不鉴权」且实际写关联页签或评论页签的操作，在 `createRows`/`removeRows` 前必须 `skipTabAdd()` / `skipTabDelete()`；禁止假设改 Spec 即可绕过 DataObject 页签鉴权。

## Key Files

| 路径 | 角色 |
|------|------|
| `components/gf/sdk/.../enums/WidgetOperationType.java` | PrivilegeSpec 映射（本轮主改） |
| `components/gf/sdkImpl/.../dataobjects/ProcessElementsDataObject.java` | 关联增写：普通关联 skipTab |
| `components/gf/sdkImpl/.../handler/DeleteAssociationDataModificationHandler.java` | 普通关联删除 skipTab |
| `components/gf/sdkImpl/.../tailoring/TailoringElementTreeServiceImpl.java` | 答案 Action 写关联页签 skipTab |
| `components/gf/sdkImpl/.../handler/AddCommentDataModificationHandler.java` / `DeleteCommentDataModificationHandler.java` | 调 `addComment/deleteComment(..., true)` |
| `components/gf/biz/.../ProcessManagement.java` | `batchOperateAssociation`：宿主 MODIFY 写闸（与 Widget 一致） |
| 关联备注 / option / 关系评论写路径 | skipTab（按实际 mutate 点） |
| `components/gf/sdk/.../WidgetOperationTypeTest.java` 等 | Spec / 写闸单测同步 |

## Implementation Notes

**Status: Implemented 2026-07-13；写闸加固 2026-07-14；映射修订 Implemented 2026-07-15**

2026-07-15 实现要点：
1. `WidgetOperationType`：关联族 / 答案 Action → `object(MODIFY)`；评论族（含关系评论）→ Spec=null
2. 写路径 `skipTabAdd`/`skipTabDelete`：`ProcessElementsDataObject.addAssociation` / `addCommentAssociation`、`DeleteAssociationDataModificationHandler`、`AssociationOptionSupport.removeAssociationRow`、`TailoringElementTreeServiceImpl` 答案动作 mutate；**评论**：`getTable` 每次新实例，Handler 外层 skip 无效 → 经 `ICommentableInner.addComment/deleteComment(..., skipTabPrivilege=true)` 在 createRows/removeRows 同实例上 skip（默认 false，PLM 仍鉴权）
3. `ProcessManagement.batchOperateAssociation`：skip 后补宿主元素 `MODIFY` 写闸（与 Widget 关联 op 一致）
4. 单测已同步；`canModifyActions` 自动跟随 Spec

## Design: Level 1 -- Capabilities

**Status: Approved 2026-07-13；Revised & Approved 2026-07-15**

### 2026-07-13（基线，仍有效的部分）

1. **裁剪问答树操作按权限下发** — 查询树时按权限过滤 `operations`。
2. **写路径强制鉴权** — 与下发映射一致；批量逐项；目标缺失 fail-closed。
3. **特殊节点边界** — 虚拟根 / 系统固定「不知道」答案规则不变。

### 2026-07-15 修订（本轮批准）

1. **关联关系跟元素编辑权** — 能编辑元素即可增删关联 / option；**关联备注**本特性不拦截（全员可改）；跳过关联关系页签 `TABLE_*`。
2. **元素评论全员开放** — ADD / EDIT / DELETE 均不鉴权。
3. **关系评论全员开放** — 不鉴权。
4. **裁剪答案 Action 跟答案编辑权** — 增删改走答案实例 `MODIFY`；`canModifyActions` 与此一致。
5. **过程元素与裁剪问答同一套规则** — 经共享 `PrivilegeSpec` 同时生效。

### 2026-07-23 最终定案（确认，无能力增减）

1. **关联增删 = 宿主 `MODIFY` only** — Widget / `batchOperateAssociation` / `addProcessElementData`（`#15081`）同源。
2. **明确不校验页签添加/删除** — 不以 `Relationships(420435)` 的 `TABLE_ADD`/`TABLE_DELETE` 作为用户侧门禁。
3. **系统批处理例外不变** — 导入/复制/模块同步等可 skip 页签且不套用户编辑闸。

## Design: Level 2 -- Components

**Status: Approved 2026-07-13；映射迁入 PrivilegeSpec 2026-07-14；Revised & Approved 2026-07-15；Handler skipTab 修订待 Level 4 一并确认**

本轮改动点：**PrivilegeSpec 映射 + 写路径页签 skip**（sdkImpl）。

| 组件 | 分层 | DDD | 本轮职责 |
|------|------|-----|----------|
| `WidgetOperationType.PrivilegeSpec` | gf-sdk | 契约值对象 | 重声明关联 / 评论 / 答案动作映射 |
| `WidgetOperationPrivilegeSupport` | gf-biz | 应用共享 | 不变；empty Spec → 过滤与写闸放行 |
| `TailoringManagement` | gf-biz | 应用服务 | `canModifyActions` 跟随答案 MODIFY Spec |
| `WidgetOperationManagement` | gf-biz | 应用服务 | 不变；空 Spec 跳过写闸 |
| 关联/评论/答案动作写路径（Handlers + `ProcessElementsDataObject` / `TailoringElementTreeServiceImpl` 等） | gf-sdkImpl | 写适配 | 页签 mutate 前 skip；评论经 Inner 显式 skip 参数 |
| `ProcessManagement.batchOperateAssociation` | gf-biz | 应用服务 | skip 后宿主 `MODIFY` 写闸 |
| `IPrivilegeEngine` | infrastructure | 看门人 | 不变 |

**映射归属**

| 操作族 | Spec | 写路径页签 |
|--------|------|------------|
| 关联增删 / option | 对象级 `MODIFY` | 关联页签 skip ADD/DELETE |
| 关联备注 | `null`（不拦截） | 关联页签 skip 启用态（setValues） |
| 元素评论 + 关系评论 | `null`（不拦截） | 评论页签 / 关联页签 skip |
| 答案 Action 增删改 | 对象级 `MODIFY`（答案） | 关联页签 skip |

## Design: Level 3 -- Interactions

**Status: Approved 2026-07-13；契约对齐 2026-07-14；Revised & Approved 2026-07-15**

### 1. 树 / Widget operations 二次过滤
构建候选后经 Spec 过滤：`requiredPrivilege` empty（评论）→ 保留且不调引擎；关联 / 答案 Action → 宿主或答案实例 `MODIFY`。

### 2. 详情 canModifyActions
普通答案：`hasDeclaredPrivileges(UPDATE_TAILORING_ANSWER_ACTION)` → 现为答案 `MODIFY`；问题 / 「不知道」=`false`。

### 3. Widget 写路径
`assertWritePrivilege`：`requiredPrivilege` empty → 直接放行；其余非 CREATE 对目标 `hasDeclaredPrivileges`（关联 / 答案 Action = MODIFY）。

### 4. REST `batchDeleteTailoringAnswerActions`
`assertAnswerActionPrivilege(DELETE_TAILORING_ANSWER_ACTION, answer)` → 答案 `MODIFY`。

| 路径 | 旧判定 | 新判定 |
|------|--------|--------|
| 关联增删 / option | 页签 TABLE_* | 宿主元素 MODIFY |
| 关联备注 | 页签 TABLE_* | 跳过 |
| 评论（元素+关系） | MODIFY / 页签 | 跳过 |
| 答案 Action / canModifyActions / batchDelete | 页签 TABLE_* | 答案 MODIFY |

## Design: Level 4 -- Contracts

**Status: Approved 2026-07-13；映射迁入 PrivilegeSpec 2026-07-14；Revised & Approved 2026-07-15**

### 1. PrivilegeSpec 映射变更（2026-07-15）

| 操作 | Spec |
|------|------|
| `ADD_ASSOCIATIONS` / `DELETE_ASSOCIATION` / `UPDATE_ASSOCIATION_OPTION` | `object(MODIFY)` |
| `EDIT_ASSOCIATION_NOTES` | `null`（不拦截） |
| `ADD_COMMENT` / `EDIT_COMMENT` / `DELETE_COMMENT` / `ADD_ASSOCIATION_COMMENT` | `null`（不拦截） |
| `UPDATE_TAILORING_ANSWER_ACTION` / `DELETE_TAILORING_ANSWER_ACTION` | `object(MODIFY)` |

其余操作映射不变。签名 `requiredPrivilege()` / `requiredTableId()` / `additionalPrivileges()` 不变。

### 2. 详情契约

`canModifyActions` = `hasDeclaredPrivileges(UPDATE_TAILORING_ANSWER_ACTION, answer)` → 答案实例 `MODIFY`；问题 / 「不知道」=`false`。

### 3. 写路径页签跳过（强制）

Biz 写闸通过后，`createRows`/`removeRows` 前必须：

```java
tab.skipTabAdd();
tab.skipTabDelete();
```

覆盖：普通关联增删、关联备注/option、元素评论、关系评论、答案 Action 写关联页签。合规映射既有 skip 保留。

**约束（review 修订 2026-07-15）**：
- 关联 skip 后，所有非 Widget 写入口也必须显式宿主 `MODIFY`（例：`batchOperateAssociation`）。
- 评论「不鉴权 + skipTab」：GF Handler 传 `skipTabPrivilege=true`；`ICommentableInner` 默认仍鉴权（`getTable` 新实例故 skip 须在 createRows 同实例上）。

### 4. 写前断言 / 树过滤 / Widget 写闸钩子

与 2026-07-14 一致：empty Spec → 写闸放行；失败 `NO_PRIVILEGE_OPERATION`。

### 5. 实现边界

- **改**：`WidgetOperationType` + 单测 + 关联/评论/答案动作写路径 skip（GF 层）+ `batchOperateAssociation` MODIFY 写闸
- **禁改**：`web/`；`ICommentableInner` 默认路径仍鉴权

## Design Summary

### Components and layer assignments
- **gf-sdk**: `WidgetOperationType.PrivilegeSpec`（映射真相源）
- **gf-biz**: Support / TailoringManagement / WidgetOperationManagement；`ProcessManagement` 关联批量写闸
- **gf-sdkImpl**: 写路径 `skipTabAdd`/`skipTabDelete`（含 Comment Handler）
- **infrastructure**: `IPrivilegeEngine` 不改签名；`ICommentableInner` 增加可选 `skipTabPrivilege`（默认 false）

### Key contracts
- **最终定案**：关联增删 / option → 宿主 `MODIFY` + 关联页签 skip（**非** `TABLE_ADD`/`TABLE_DELETE`）
- `#15081` `addProcessElementData` → 同宿主 `MODIFY` 写闸
- 关联备注 → 不鉴权 + 页签 skip 启用态
- 评论 → GF Widget 不鉴权 + `addComment/deleteComment(..., true)`（非全局）
- 答案 Action / `canModifyActions` → 答案 `MODIFY` + 关联页签 skip

### Architectural constraints
- 仅后端；双层门禁（Biz Spec=MODIFY + DataObject 页签 skip）；禁止自建 ACL；禁止再把用户关联写闸绑回页签 TABLE_*

### Design status
**Approved (final 2026-07-23) — association privilege = host MODIFY; implementation already aligned (#15081)**
