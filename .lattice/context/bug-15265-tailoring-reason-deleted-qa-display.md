---
feature: bug-15265-tailoring-reason-deleted-qa-display
requirement_doc: null
created: 2026-07-30
status: Implemented
ticket: 15265
decisions: 10
open_questions: 0
---

# bug-15265 裁剪理由在相关问答删除后的展示

> 裁剪助手产生的决策理由在相关问题/答案被删除后，重置前仍应正常展示问题与答案文本，而非原始 ID。仅保证新产生的决策行正确；不做历史回填。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-07-30 | 新建 context：`bug-15265-tailoring-reason-deleted-qa-display` | 无既有 context / requirement；design-blueprint Step 1 需落锚 | 直接编码 |
| 2026-07-30 | `requirement_doc = null` | 用户确认无正式需求文档 | 先跑 requirement-forge |
| 2026-07-30 | 仅保证**新产生**的决策行展示正确；不做存量回填/迁移 | 用户明确范围；物理删除后旧行已无法可靠恢复名称 | 一次性 SQL/批处理回填历史空名 |
| 2026-07-30 | Level 1 Capabilities 获批 | 用户确认能力与边界 | 问答删除后仅显示「已删除」占位（达不到「仍展示当时文本」） |
| 2026-07-30 | Level 2 快照落库选 **Option A**：决策表新增 `question_name` / `answer_name` | 语义清晰、可传输复制；不污染 `decision_rationale` | Option B：塞进 rationale |
| 2026-07-30 | Level 2 Components 获批 | 用户确认组件与分层 | — |
| 2026-07-30 | Level 3 Interactions 获批 | 写时盖戳、读优先快照、传输携带、重置随行删除 | 删除问答时回填 / 改名刷新快照 |
| 2026-07-30 | Level 4 Contracts 获批 | 常量/Flyway/映射/Factory/读优先级契约对齐 | 新增对外 REST 或 SDK 捷径 API |
| 2026-07-30 | Design approved at Level 4. Blueprint complete ready for implementation. | 四级设计已落盘，可交 `/code-forge` | — |
| 2026-07-30 | 实现完成：快照列 + 写盖戳 + 读优先 + 传输/工作集复制携带 | 对齐 L1–L4；仅新行；单测 Factory/enrich 通过 | 存量回填 |
| 2026-07-30 | Flyway 仅保留表 DDL，去掉 air_sys 数据配置 | 属性/页签绑定由专门同事负责，此处改无效 | 在本脚本内自带完整 air_sys INSERT |

## Open Questions

（无）

## Constraints

- 不做历史已落库、名称为空决策行的迁移/回填。
- 范围限于裁剪属性「理由」展示（助手路径）；不禁止删除问答；不改变重置语义。
- 过程元素物理删除、不进回收站（既有约束，见 `gf-physical-delete-no-recycle`）——读路径不可依赖回收站还原名称。
- 遵循 Biz → SDK → SDKImpl；事务仅 biz Management。
- 名称快照落在决策页签独立列（`question_name` / `answer_name`），不写入 `decision_rationale`。
- 不新增对外 REST；不新增 `I*Service` 仅凭 ID 的捷径 API。

## Key Files

- `upgrade/.../V3.5.2__tailoring_decision_qa_name_snapshot.sql` — 仅 DDL：`question_name`/`answer_name`（air_sys 配置由专人负责）
- `AirAPI/.../AirConstants.java` — `ATTR_QUESTION_NAME` / `ATTR_ANSWER_NAME`
- `gf-sdk/.../IInnerTailorable.java` — map/build 持久化名称
- `gf-sdk/.../TailoringDecisionRow.java` — 快照语义注释
- `gf-sdkImpl/.../TailoringDecisionRowFactory.java` — 写路径盖戳
- `gf-sdkImpl/.../TailoringResultCalculatorImpl.java` — 计算时取名
- `gf-sdkImpl/.../TailoringLocalOverrideDecisionSupport.java` — 本地默认覆盖盖戳
- `gf-sdkImpl/.../TailoringWizardApplyServiceImpl.java` — 传入元素 map
- `gf-sdkImpl/.../ProcessElementsDataObject.java` — 读优先快照
- `gf-sdkImpl/.../ProcessTailoringTransferServiceImpl.java` — 传输携带
- `infrastructure/.../GfElementTabTailoringDecisionCopyMapper.xml` — 工作集复制携带
- `web/.../TailoringJustificationView/index.tsx` — 前端未改

## Design Checkpoint

- Current: **Implemented**
- Completed: Level 1–4 + code-forge

## Design: Level 1 -- Capabilities

1. **助手裁剪理由可读**：经裁剪助手产生的过程元素，在相关问题/答案被删除后、重置裁剪前，「裁剪属性 → 理由」仍展示当时的问题文本与答案文本。
2. **不以原始 ID 充当文案**：对新产生的助手决策，理由区的问题/答案不以内部实例 ID 作为展示内容。
3. **展示形态不变**：理由区仍为结构化「问题 / 答案 / 结论」只读展示；不改交互、不改手动裁剪富文本路径。
4. **重置语义不变**：重置裁剪后理由清空/恢复行为与现网一致。
5. **存量不回填（边界）**：已落库且无名称快照的决策行保持现状；本能力只约束新产生的决策行。

## Design: Level 2 -- Components

### 组件与分层

| 组件 | 分层 | 职责 | DDD |
|------|------|------|-----|
| 决策行名称快照存储 | AirAPI + Flyway（420547） | 新增 `question_name` / `answer_name` 列与属性 | 聚合内从属属性 |
| `TailoringDecisionRow` + `IInnerTailorable` 映射 | gf-sdk | 名称字段持久化读写 | VO + 聚合内行为 |
| 向导写路径名称盖戳 | gf-sdkImpl | 落库前写入当时问答元素名 | 领域服务（写侧） |
| 理由读路径优先快照 | gf-sdkImpl（`ProcessElementsDataObject`） | 优先已存名称；无快照再反查（兼容存量） | 聚合读派生 |
| 传输/克隆携带快照 | gf-sdkImpl | 继承/传输复制时带上名称 | 跨实例编排 |

前端 `TailoringJustificationView` 不改。

### 架构约束
- Biz 不引入 sdkImpl；写事务仍在既有 Tailoring Management 路径。
- 单实例映射行为留在 `IInnerTailorable`；跨实例盖戳在 Calculator/Factory。

## Design: Level 3 -- Interactions

### 1. 助手裁剪写路径
Calculator/Factory 构造 WIZARD pending 时，从仍存活的问答元素读取名称写入行；Apply → `addDecision` 将 ID + 名称一并落库。快照为应用当时点；之后改名/删除不回写。

### 2. 属性面板读路径
`getTailoringJustification` → 映射决策行 → `enrichDecisionRowNames`：**已有非空名称快照则采用**；否则活实例 → 回收站 → ID 字符串（存量兼容）。前端不变。

### 3. 传输 / 上游继承
`cloneForTarget` 等复制路径一并复制 `questionName`/`answerName`。

### 4. 重置
删除本地 WIZARD 决策行，名称随行删除。

### 5. 明确不做
删除问答时不回填历史；问答改名不刷新已落库快照。

## Design: Level 4 -- Contracts

### 持久化与常量
- `ATTR_QUESTION_NAME = "QuestionName"` → `question_name`
- `ATTR_ANSWER_NAME = "AnswerName"` → `answer_name`
- Flyway：表列可空 varchar（仅 DDL）；air_sys 属性/页签绑定由专人配置；不回填存量
- `TailoringDecisionRow.questionName` / `answerName`：可落库展示快照

### 页签映射
- `mapDecisionRow` / `buildDecisionAttrs` 读写上述属性；`addDecision` 签名不变

### 写路径
- `buildPendingWizard(..., String questionName, String answerName)`
- `buildLocalDefaultOverride` / `cloneForTarget` 写入或复制名称
- Calculator 从 `tailoringById` 取 `getName()`；blank → `null`（不落 ID）

### 读路径
- `firstNonBlank(snapshot, resolveElementName(id))`；`resolveElementName` 保持现状仅服务无快照存量

### 边界
- 手动裁剪不写问答名称列；前端无契约变更；无新 REST / SDK ID 捷径 API

## Design Summary

- **组件与分层**：Flyway/AirConstants 快照列；`IInnerTailorable` 映射；Calculator/Factory 写时盖戳；`ProcessElementsDataObject` 读优先快照；传输克隆携带；前端零改。
- **关键契约**：`QuestionName`/`AnswerName` 属性；Factory 扩展参数；enrich 优先级 `snapshot > resolve`。
- **架构约束**：Biz→SDK→SDKImpl；物理删除下不可依赖回收站；快照与 rationale 分列；无新 REST/捷径 API。
- **领域决策**：名称是决策行聚合内的展示快照值对象字段，非独立聚合；写时点快照，不随源改名刷新。
- **已关闭问题**：落库方式 A；仅新行；不回填。
- **设计状态**：**Approved -- ready for implementation**

实现入口：`/code-forge`，蓝图路径：`.lattice/context/bug-15265-tailoring-reason-deleted-qa-display.md`
