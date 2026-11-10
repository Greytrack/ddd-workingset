# ADR-0005：Merge 执行模型与 MergeEntry 行为契约

- **状态**：Proposed
- **日期**：2026-11-10
- **决策范围**：MergeEntry 的归属与生命周期、MergeRound／MergeCheckpoint／Diff 能力／业务 BC／LightTrackerItem 的协作顺序、ComparisonToken 的证据要求（不含编码）、Checkpoint 确认的发起方
- **确认人／决策权威**：待用户确认（本 ADR 由 AI 起草为 Proposed，未批准）
- **替代**：无
- **被替代于**：无

## 背景

D-019 已确认 Merge 的聚合定位（MergeRound 为载体、MergeCheckpoint 独立、合并决策作为 VO 归属 MergeRound）与四条业务规则（未物化可参与 Diff、未物化目标被修改时构建业务聚合并 save、共享 Tracker 不参与、非共享只允许直接父子准入）。执行协议仍为空白（`QUESTION-015`）。

若不作出本决策，实施者会各自选择条目归属、确认发起方与比较凭据的证据强度，导致：同一「已处理」事实有两个写入口、过期审阅无法识别、MergeRound 与 Checkpoint 责任重叠。契约草案见 [Merge 执行行为契约 v1](../domain/merge-execution-contract-v1.md)，图见 [MergeEntry 生命周期](../diagrams/merge-entry-lifecycle.md)。

### 已确认依据

- **Business Fact**：FACT-027（Diff/Merge 衔接六条裁定）、FACT-007（直接父子双向、source/target 为角色）、FACT-008（决定、Apply Result 与处理确认是不同事实）。
- **Business Invariant**：INV-061～063（未物化参与 Diff、修改即物化、MergeRound 记录与 Checkpoint 独立）；INV-006～010（有向冻结证据、存在性显式裁决、KEEP_TARGET 与 DEFER 的已处理语义、幂等与乱序）；INV-009（不得绕过目标 BC 整体校验）；INV-040（COW 不改 LocalId/CommonId）。
- **Business Policy**：POL-004（仅直接父子）；R08～R11（ACCEPT 读源最新值、决策优先、部分成功与重试、KEEP_TARGET 的 Checkpoint 语义）。
- **Technical Constraint**：无。本 ADR 不决定 Token 编码、幂等键实现、事件中间件与表结构。

### 假设与开放问题

- **Hypothesis**：业务 BC 能提供「整体校验 + 提交 + 返回真实结果与业务 Revision」的公开写能力（职责已由 D-007/INV-009 确认，具体 seam 待 T4）。
- **Open Question**：`QUESTION-015`（差异冻结/令牌、幂等键、乱序确认、Checkpoint 字段映射的细节）；本 ADR 只裁定结构与发起方，不裁定实现形式。

## 决定

（Proposed 建议；用户批准后方可 Accepted）

1. **MergeEntry 作为 MergeRound 聚合内的 Entity**，表达一条合并事项的完整生命周期：`Compared → Decided → Applying → Applied / Rejected / StaleEvidence / Deferred → Confirmed`。不独立成聚合，不跨轮次复用。
2. **协作顺序固定**：Diff 产生 `TrackerItemDiff` + `ComparisonToken` → MergeRound 登记决策并建立条目 → 执行时复核证据 → 依 `ItemMergeDecision` 确定目标操作 → 业务 BC 构建目标聚合并整体校验提交 → LightTrackerItem 按既有 COW 机制维护有效来源与 Revision → 依 `markAsMerged` 更新 Checkpoint → MergeRound 记录单项结果、进度与 `ApplyResult`。
3. **`ComparisonToken` 必须绑定双侧内容证据引用**；业务 Revision 只可作为其中一项，不得单独构成 Token（同一 Revision 内内容仍可变）。编码与存储形式留实现。
4. **Checkpoint 确认由 MergeRound 发起**：仅当 `markAsMerged=true` 且条目终态为 `Applied` 时调用 `MergeCheckpoint.confirm`；Checkpoint 保持独立聚合，只做因果校验，不反向查询 MergeRound。

## 备选方案与权衡

### 方案 A（采纳）

- 保护的不变量与适用场景：与 D-019 的「MergeRound 是载体」一致；条目状态与轮次进度同源；确认只有一条写入路径。适用于直接父子、非共享 Tracker 的常规合并。
- 收益与成本：责任清晰、实现 seam 明确、避免第二身份；代价是 MergeRound 承担编排复杂度，条目不能跨轮次复用。
- 失败模式与放弃条件：若出现「同一事项跨多轮复用」或「条目需要独立查询/审计入口」的真实业务需求，应重新评估。

### 方案 B（未采纳）：MergeEntry 作为独立持久对象

- 保护的不变量与适用场景：执行历史可独立查询与保留；Round 仅作索引。
- 收益与成本：审计友好；代价是引入第二个身份与跨聚合一致性，条目与轮次参数/进度需双向同步。
- 失败模式与放弃条件：与「不新增聚合」原则冲突；轮次终态与条目状态易分叉。

### 方案 C（未采纳）：确认由用户单独发起

- 保护的不变量与适用场景：处理事实与执行事实更彻底解耦；用户可延后确认。
- 收益与成本：确认时机更灵活；代价是同一「已处理」事实有两个写入口，需要额外协调与权限语义。
- 失败模式与放弃条件：若业务要求「先执行、后由不同角色确认」，需改为本方案并补充角色授权契约。

## 后果

- 正面后果：三对象责任边界明确；过期审阅（Token 失效）与非法组合（`DEFER + markAsMerged`）在执行前即可拒绝；部分成功与重试已有明确表达。
- 负面后果与风险：MergeRound 聚合承担编排并可能成为轮次级热点（大规模条目需分批执行）；确认时机绑定执行结果，若 BC 提交成功但 Checkpoint 写入失败，需由恢复/重试协议补齐（属 `QUESTION-015`）。
- 迁移与兼容影响：无既有实现；不影响 ADR-0001～0004。共享 Tracker 与兄弟/跨代合并仍在准入层拒绝，不因本 ADR 放宽。

## 验证与追溯

- 业务场景／反例：SCN-055（未物化参与 Diff 与修改）、SCN-056（MergeRound/Checkpoint 定位）；待补 SCN-057 起——过期审阅、撤销晚到、`DEFER` 残留、部分成功后重试、未物化目标物化与修改失败。B06（BC 拒绝不部分写入）、B08（部分成功与重试）、B09（ACCEPT 读源最新值）、B10（丢回执与乱序）。
- 验收条件或测试：§7 行为矩阵八行、§5 状态机迁移、§8 `ApplyResult` 表达；测试 seam 与实现入口待 `implementation/`。
- 实现入口：待办（当前无实现证据）。
- 重新评估条件：出现跨轮次条目复用需求；Checkpoint 需要独立确认入口；Token 证据形式无法兑现「过期审阅」防护；BC 写能力与整体校验职责变化。
