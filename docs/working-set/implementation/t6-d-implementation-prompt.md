# T6-D 实施提示词（子工作集新建与回并）

**状态：Historical**。以下保留实施前的任务与备选项，当前裁定和实现见 [T6-D 验收](t6-d-merge-back-verification.md)。

## 0. 角色与目标

你是本仓库的实施者。T6-D 的目标是让“子工作集新建 Item → 通过用户合并选择回并父工作集”成为端到端可验收的能力，并明确其身份、去重与来源记录语义。交付物仍是 `sk.workingset` 的契约与约束；`alm.requirement` 是占位/参考实现。

## 1. 开始前必读

1. `AGENTS.md`、`BASELINE.md`；
2. `scenarios/core-scenarios.md` 的「子工作集新建 Item 与 Tracker 边界」「子工作集新增 Item 回并父工作集」；
3. `domain/open-questions.md` 的 QUESTION-005、QUESTION-004；`facts.md` 的 POL-002/POL-007/R04/R05；
4. `domain/cross-bc-contract-v1.md` §4（create 用例）与 §5（失败语义）；
5. `domain/merge-execution-contract-v1.md`（T1，尤其存在性动作 CREATE 与 D15-B 事务边界）；
6. `implementation/t6-c-identity-verification.md`、`t6-b-merge-recovery-verification.md`、`t6-implementation-prompt.md`。

## 2. 当前状态（不要重做）

- D10 已实现工作集入口：`CreateRequirementRequest.workingSetId` + SK 的 `TrackerScopePort`；共享 Tracker 可来自任意绑定工作集、实际归默认 WS，非共享必须由所属 WS 创建。
- T6-C 已实现创建身份：默认 WS 同一雪花作为 LocalId/CommonId，非默认 WS 两个不等雪花；派生继承来源 CommonId。
- T1 的 Merge 已支持双向 CREATE：`MergeComparisonService` 按直属父子与 `tracker.sourceId` 配对，`MergeExecutionService` 的 CREATE 分支在目标 WS 创建 Item；`MergeAcceptanceTest.explicitlyCreatesDeletesAndUpdatesInEitherDirection` 已覆盖 `target=父 WS` 的 CREATE。
- C11 已让 Compare 候选过滤软删对象；D16 已定“软删即占用家族身份”；唯一冲突统一 409。
- T6-D 尚未完成的是：回并的**身份断言**、**去重/软删冲突语义**、**回并来源是否记录**、**共享 Tracker 不回并的显式验收**，以及对应契约与验证记录。

## 3. 已确认的业务语义（不得重开）

- 子工作集禁止创建 Tracker；可在已有非共享 Tracker 下新建 Item；共享 Tracker 下从子 WS 入口新建的 Item 实际归默认工作集。
- 允许子 WS 新增 Item 回并父 WS：由用户的合并决定在父 WS 创建对应 Item，目标 BC 校验并提交。
- 父子对应 Item 使用**相同 CommonId、不同 LocalId**；默认 WS 的 Item 的 CommonId=LocalId，非默认 WS 的 Item 的 CommonId≠LocalId。
- 回并**不改变工作集父子链**；不自动回并共享 Item（共享本身在默认 WS 可见）。
- Compare/Merge 只允许直属父子；兄弟/跨代拒绝（T1）。

## 4. 需要先确认的开放点（不擅自决定）

1. **回并是否只经 Merge CREATE**：场景写“通过合并选择”，建议只复用 T1 的 CREATE；不新增独立写入口，也不允许直接写父 WS 业务表。若用户要求独立入口，必须先裁定并更新 T1/T4。
2. **父侧 Item 是否记录“回并来源”**：当前 `Requirement` 无来源字段，`LightTrackerItem.local` 的 `sourceLocalId` 指向自身；QUESTION-005 明确“来源/谱系如何记录继续设计”。在确认前不得新增字段或表；先向用户确认“不记录 / 记录子侧 LocalId / 记录来源变体”三选一。
3. **重复回并的语义**：父侧已有有效同族时 Compare 显示 PRESENT，CREATE 不可用（天然不重复）；父侧同族为软删时 C11 过滤后显示 ABSENT，CREATE 会撞 `(working_set_id, common_id)` 唯一索引。需确认是返回 409（D16 现状）还是给出“请先恢复”的专用提示；不得静默复用软删行。

## 5. 任务分解

### 5.1 验证现有回并路径
- 用父 WS 为默认 WS 与父 WS 为非默认 WS 两种场景，验证 `MergeExecutionService` 的 CREATE：
  - 源=子 WS、目标=父 WS；父侧 Item 被创建；
  - 父侧默认 WS：LocalId=CommonId；父侧非默认 WS：LocalId≠CommonId；
  - 子侧 Item 身份不变；父子链不变。
- 若现有路径已满足，不要重写；只补断言与文档。

### 5.2 身份与去重
- 回并创建的身份遵循 T6-C：CommonId 来自源家族；父侧 LocalId 按父 WS 规则生成（默认同一、非默认独立）。
- 父侧已有有效同族：Compare 候选显示 PRESENT，不得提供 CREATE 的重复创建路径。
- 父侧同族软删：按 §4.3 的裁定返回明确冲突（或专用提示）；不得绕过唯一索引静默复用。
- 并发回并同一家族：唯一索引兜底，一个成功、另一个明确冲突（409）；不产生部分写入。

### 5.3 来源与事件
- 若确认记录回并来源，按 SK 的既有身份/来源模型实现，不新增绕过 TrackerItem/LTI 的字段；若确认不记录，在 QUESTION-005 与场景中写明。
- 回并的业务写入与 LTI 切换沿用 D15-B：业务 apply 独立提交、目标变体切换由 `RequirementMergeApplied` 事件完成；不新增跨聚合事务。
- 共享 Tracker 不进入回并候选；只允许非共享、直属父子。

### 5.4 验收
- 端到端：子 WS 新建 Requirement → Compare → 用户选择 CREATE → 父 WS 出现对应 Item；父侧身份规则正确。
- 默认父 WS 与非默认父 WS 各一例。
- 父侧已有有效同族：无 CREATE 候选 / 不重复创建。
- 父侧同族软删：冲突语义符合裁定。
- 共享 Tracker：不出现回并候选。
- 并发回并同一家族：一个成功、一个 409。
- 失败/重试：沿用 Merge 的独立条目事务与事件重投，重复 Apply 幂等。

### 5.5 文档
- 更新 T4 §4 的 create 用例、POL-007/QUESTION-005、回并场景（补身份、去重、来源结论）；
- 新增 `t6-d-merge-back-verification.md`；
- 同步 `implementation/README` 与 `BASELINE.md`。

## 6. 全局约束

- SK 优先：回并机制属于 SK 的 Merge 与身份协作；ALM 只做占位适配，不为其补业务 Guard。
- 不重开已关闭裁定（D1～D17、C3～C6/C11/C12、QUESTION-001/003/008/009/015）。
- 失败语义只用 T4 §5 九分类；软删 `Deleted`、永久删除 `NotFound`、COW 依赖 `DependencyUnavailable`。
- 不外泄 SK 依赖：`sk.workingset` 不得依赖 ALM（ArchUnit）。
- 测试只测公共 seam；事件驱动处使用 `await`；不修改测试期望掩盖冲突。
- 未经用户明确授权，不提交、不发布、不创建 Tag。

## 7. 完成定义

- `mvn -B clean test` 全绿（当前 88 项 + 新增）；
- 文档相对链接可解析、`git diff --check` 干净；
- T6-D 验证记录与 BASELINE 登记完成；
- 回并身份、去重、来源三项均有明确结论（实现或书面“不记录”裁定）。

## 8. 建议的第一个动作

先跑读现有 `MergeAcceptanceTest.explicitlyCreatesDeletesAndUpdatesInEitherDirection` 与 `MergeExecutionService` 的 CREATE 分支，确认父 WS（默认/非默认）的身份结果；再向用户确认 §4 的三项开放点，然后补断言与文档。不要先改代码。
