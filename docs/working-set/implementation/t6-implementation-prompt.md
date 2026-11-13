# T6 实施提示词

> 交给实施者（Codex/开发者）的完整任务说明。按此执行，不要跳步；每片完成后回到用户确认再进入下一片。

## 0. 角色与目标

你是本仓库的实施者。目标是在**不重开已确认裁定**的前提下，按 T6 切片顺序把 Working Set 的遗留开放问题与规模/并发能力收敛到可验收状态。仓库是长期演进的领域设计仓库，不是一次性代码生成项目；先理解业务与契约，再实现。

## 0.1 实施边界（必须先理解）

- **交付物是 `sk.workingset`（Shared Kernel）的契约、规范、约束与实现**；未来各业务团队模块对接时，只带出 `sk.workingset` 的内容。
- **`alm.requirement` 目前是占位/参考实现**，只用于验证 SK 契约（`TrackerItem`、`TrackerItemReadPort`、`RequirementWritePort`、事件协作等）。它不代表 ALM 团队最终的业务实现，也不要求在此补齐业务细节（例如具体业务 Guard、字段规则、状态机）。
- 业务 BC（ALM/Part/GateFlow/DQA 等）由各自团队按专业领域实现，但**必须遵守 `sk.workingset` 定义的规范与约束**（只读/写入 Port、失败语义、事件、身份与唯一性、跨聚合事务策略）。
- 判断实现重点时：先问“这是 SK 的规范/约束/机制，还是某个 BC 的业务细节？”——前者在 T6 范围内；后者只需最小占位适配，不为占位实现增加业务复杂度。
- SK 编译期不得依赖任何业务 BC 实现（ArchUnit 已强制）；不得把 ALM 的实现细节升格为 SK 契约。

## 1. 开始前必读（按顺序）

1. 根目录 `AGENTS.md`（证据等级、基线变更协议、测试与提交规则）；
2. `docs/working-set/BASELINE.md`（当前有效基线）；
3. `docs/working-set/domain/open-questions.md`（当前开放问题与状态）；
4. T1～T4 契约：`merge-execution-contract-v1.md`、`binding-deletion-contract-v1.md`、`lighttrackeritem-materialization-contract-v1.md`、`cross-bc-contract-v1.md`；
5. `docs/working-set/implementation/t5-s7-verification-result.md`、`t5-d4-sk-boundary-verification.md`、`t6-next-stage-outline.md`；
6. 相关代码入口：`bootstrap/configuration/ApplicationConfiguration`、`bootstrap/web/*Controller`、`sk/workingset/application/*`、`sk/workingset/domain/*`、`alm/requirement/*`、`src/main/resources/schema.sql`；
7. Lattice Skills：`domain-modeling`、`ubiquitous-language`、`domain-driven-design`（按需读取真实 `SKILL.md`）。

## 2. 当前基线（不可重开）

- 单一 ALM Requirement 纵向切片 S1～S7 已实现；`mvn -B clean test` **84 项通过**（含 ArchUnit，真实 PostgreSQL/Redis/Tomcat）。
- 已裁定：D1（BusinessObject）、D2/C7（家族唯一性按 WorkingSet）、D3-B/D15-B（业务保存独立提交 + 事件驱动 LTI 切换）、D4/D13（LTI 移回 SK、事件重投）、D5（无停用）、D6（附件/关系不参与 Merge）、D7（永久删除即 `NotFound`）、D8/D9（QUESTION-001/008 状态）、D10（工作集入口）、D11（权限延后）、D12-A（JSONB 为切片实现）、D14（BusinessObject 落地）、D16（软删占身份）、D17（生命周期不产生 Unknown）；C3/C4/C5/C6/C11/C12 已修。
- 明确不做：权限接入（D11 延后）、附件/关系参与 Compare/Merge（D6）、停用生命周期（D5）。
- 不得重开：QUESTION-001、QUESTION-003（延后）、QUESTION-008 已关闭部分、QUESTION-009、QUESTION-015；也不得把 C3/C4/C5/C6/C11/C12 已修项回退。

## 3. 全局约束

1. **设计先行**：每片先明确契约/场景变更，再写实现；重大且难逆的决策按 `domain-modeling` 的 ADR 三条件决定是否出 ADR。
2. **SK 优先**：T6 的交付重心是 `sk.workingset` 的契约与约束；`alm.requirement` 只做最小占位适配与契约验证，不为其补业务细节 Guard/规则。
3. **聚合边界**：聚合是事务一致性边界；跨聚合按 ID 引用，不做对象图装载；跨聚合写入沿用 D3-B/D15-B 模式（业务保存独立提交、SK 切换由事件完成、Modulith 未完成事件可重投）。
4. **失败语义**：只用 T4 §5 九分类；软删返回 `Deleted`、永久删除返回 `NotFound`（D7）、COW 依赖返回 `DependencyUnavailable`；不得把 `Deleted` 降级为 `NotFound`。
5. **C11 约束**：Compare 候选过滤软删对象；`RESTORE` 当前不可达，恢复走 BC 的 `undelete`；不要擅自恢复 Merge RESTORE。
6. **D16 约束**：软删即占用家族身份；创建冲突返回明确冲突（409 `DATA_INTEGRITY_CONFLICT`），不得静默复用软删行。
7. **测试**：只测公共 seam；期望来自已确认规则而非实现重算；覆盖不变量、边界、反例、并发与失败恢复；事件驱动处使用 `await`；不修改测试期望来掩盖领域冲突。
8. **文档**：每片同步更新 T1～T4、`scenarios/core-scenarios.md`、验证记录与 `BASELINE.md`；文档相对链接必须可解析。
9. **提交**：未经用户明确授权，不得 `git commit`、创建 Tag 或发布基线；获得授权后按“代码 / 文档”拆分提交。

## 4. 切片任务

### T6-B（优先）Merge 并发、重试与结果恢复（QUESTION-008.a）

**现状**：`MergeExecutionService.apply` 逐条执行；`executeEntry` 使用独立事务；`JdbcMergeStore.findRound(id, true)` 支持 `FOR UPDATE`；重复 `apply` 返回原结果；`ComparisonAlreadyUsed`、`RoundClosed` 已有；`RequirementMergeApplied` 已接入重投。

**任务**：
1. 定义并实现：同一轮次并发 Apply 的冲突语义（已终态返回原结果；执行中第二个调用返回 409 `RoundInProgress` 或等价 `MergeFailure`，不重复执行条目）。
2. 定义跨实例收敛：轮次行锁 + 状态机 + 事件重投；中断后重新 `apply` 应继续未完成条目或返回已收敛终态，不得重复写业务。
3. 补充恢复入口/说明（可复用 `/api/cow/recover/incomplete` 的未完成事件重投；新增 Merge 轮次中断的恢复说明）。
4. 更新 T1（并发/重试边界）、S7 验证与 BASELINE 登记。

**验收**：并发两个 `apply`（一个成功、一个得到明确冲突或原结果）；重复提交幂等；执行中断后恢复；事件重投后 LTI 状态收敛；不重开 T1 的“同轮不提供业务重试”（Q4）。

**涉及文件**：`MergeExecutionService`、`JdbcMergeStore`、`MergeRound`、`MergeController`、`MergeAcceptanceTest`、T1、S7 验证。

### T6-C 身份分配、去重与并发协议（QUESTION-004 剩余）

**现状**：D2/C7 唯一索引（requirements 与 LTI 按 `working_set_id, common_id`）；D16 软删占键；`SnowflakeIdGenerator`；`RequirementApplicationService.create` 已接受 `workingSetId`。

**任务**：
1. 明确 CommonId/LocalId 分配：创建、派生、回并；默认工作集二者相等、非默认不等的实现核对。
2. 明确同族去重与冲突语义：软删占键、永久删除释放；应用层把唯一冲突映射为明确失败（不得只靠 DB 异常）。
3. 并发创建/派生的幂等与冲突验收。

**验收**：同 WS 同族并发创建只有一个成功；软删/恢复/永久删除后的身份可用性符合 D16/D7；跨 WS 同族允许；标识分配符合已确认规则。

**涉及文件**：`RequirementApplicationService`、`JdbcRequirementRepository`、`WorkingSetApplicationService`、`schema.sql`、`RequirementFamilyScopeTest`、T2/T4、facts/open-questions。

### T6-D 子工作集新建与回并（QUESTION-005）

**现状**：D10 已实现工作集入口与归属解析；回并尚未实现。

**任务**：实现子 WS 新建归属（共享归默认、非共享归当前）与回并父 WS（在父 WS 创建对应 Item）；校验身份等值（同 CommonId、不同 LocalId）与去重；补场景与验收。

**验收**：子 WS 新建 → 回并父 WS 的完整链路；共享/非共享归属正确；回并去重与冲突语义明确。

**涉及文件**：`RequirementController`、`RequirementApplicationService`、`WorkingSetApplicationService`、`RequirementCatalog`/`BusinessTrackerItems`（明确职责）、T2/T4、SCN。

### T6-E 规模、分页与保留（D12-A、QUESTION-012）

**现状**：`merge_comparisons`/`merge_rounds` 整份 JSONB；分页基于整份清单；保留/清理、规模压测、真实元数据回填未做。

**任务**：定义保留/清理策略；对冻结比较与轮次做分页/规模验证；真实平台元数据与重复 Requirement 回填方案；不改变 T1 语义。

**验收**：分页边界、保留/清理不影响已确认结果；规模压测有数据；回填方案可执行。

**涉及文件**：`JdbcMergeStore`、`MergeComparisonService`、schema、S7 验证、T1。

### T6-F 谱系剩余约束（QUESTION-002 剩余）

**任务**：逐项访谈/裁定并实现：Shared 父级一致性、同源多派生、`sourceId` 可变性、派生整批原子性、创建完成可观察时点。

**验收**：每项有场景与测试；不推翻单父、来源约束、相邻父子 Merge 等已确认规则。

### T6-G 关系、Baseline 与治理（QUESTION-010/011/014）

**任务**：Reference/Association 解析与修复；业务 Baseline 含义；Shared Kernel 版本治理。团队级决策，需用户确认后启动。

**验收**：按用户裁定更新 T4/ADR；不阻塞前六片。

## 5. 每片交付物与完成定义

每片必须同时交付：

1. 契约/设计变更（T1～T4 或新评审记录）；
2. 至少一个新场景或反例；
3. 公共 seam 上的验收测试；
4. 验证记录（`implementation/tX-*-verification.md`）；
5. `BASELINE.md` 登记（涉及用户确认时）。

完成定义：`mvn -B clean test` 全绿（当前 84 项 + 新增）、文档链接可解析、`git diff --check` 干净、ArchUnit 通过、无未登记的契约/实现差异。

## 6. 验证命令

```powershell
mvn -B clean test
# 文档相对链接检查（仓库内脚本或人工核对）
git diff --check
```

## 7. 禁止事项

- 重开已关闭的裁定或回退 C3/C4/C5/C6/C11/C12；
- 未经授权提交、发布基线或创建 Tag；
- 用修改测试期望掩盖领域冲突；
- 把软删对象重新纳入 Compare 候选或恢复 Merge RESTORE（C11）；
- 引入各 BC 的专有 `delete2` guard（C12）；
- 在 SK 中依赖 ALM 实现（ArchUnit 会失败）；
- 删除历史评审记录或静默改写已确认结论。

## 8. 建议的第一个动作

从 **T6-B** 开始：先写并发/重试的契约与场景（T1 补充 + SCN），再实现轮次并发与恢复，最后补验收与 BASELINE 登记。完成后停下等待用户确认，再进入 T6-C。
