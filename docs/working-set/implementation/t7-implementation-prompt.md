# T7 实施提示词（工作集创建 LRO）

> T7 是新增的“按范围异步创建工作集”用例。每片完成后停下等待确认。[T7-A 评审](../reviews/2026-11-13-t7-a-creation-lro-review.md)已记录绑定前置、内容来源和接口细节。

## 0. 角色与目标

你是本仓库的实施者。目标是把“创建工作集”实现为**异步长流程（LRO）**：按用户选定的产品空间、共享 Tracker、非共享父 Tracker 范围，一次性创建子工作集本体、复制非共享 Tracker、绑定共享/非共享/空间、批量创建 LightTrackerItem 索引，并在索引完成后通过事件触发关系/引用构建。交付物仍是 `sk.workingset` 的契约与机制；`alm.requirement` 是占位/参考实现。

## 1. 实施边界

- **SK 优先**：LRO 聚合、状态机、任务表、事件、批量索引端口属于 `sk.workingset`；ALM 只提供最小适配（Item 身份/Revision 查询、创建校验）。
- 现有原子操作（`createChildWorkingSet`、`bindSpace`、`bindTrackerShared`、`derive`）**保留**，用于后续增量；T7 不替换、不改变 S6 的“空范围子 WS 合法”。
- 不把 ALM 的业务细节 Guard 升格为 SK 契约。

## 2. 必读（按顺序）

1. `AGENTS.md`、`BASELINE.md`；
2. `domain/working-set-creation-lro-design.md`（本设计，含用户决策与 T7-A～F 切片）；
3. `domain/working-set-lineage-design.md`（S6 单父/来源约束）、`implementation/t5-s6-verification-result.md`；
4. `domain/binding-deletion-contract-v1.md`（T2 绑定/删除）、`domain/lighttrackeritem-materialization-contract-v1.md`（T3）；
5. `domain/cross-bc-contract-v1.md`（T4 端口/失败语义）、`domain/merge-execution-contract-v1.md`（T1 LRO/事件模式）、`adr/0005-merge-execution-model.md`；
6. `domain/open-questions.md` 的 QUESTION-010（关系/引用）、QUESTION-012（规模）。

## 3. 已确认决策（不得重开）

1. 创建工作集是**异步 LRO**，提供 REST 供前端轮询。
2. 非共享 Tracker copy 覆盖父 Tracker 下**全部 Item**，**软删除剔除**。
3. 共享 Tracker 范围由**用户选择**；选哪些就在新工作集绑定哪些。
4. 批量 LTI 失败：**整体不开放重试**；一次失败或成功；允许**部分成功**；结果记录在创建任务结果中；**失败不补偿**（子 WS 独立，不影响父级）；任务明细必须显性标记**失败原因与具体位置**。
5. 关系/引用构建在 LRO 内：LTI 创建完成后**发送事件**，触发异步关系构建。
6. **复用 MergeRound 的 LRO 模式与事件框架**（状态机、进度、结果、Modulith 未完成事件）。

## 4. 现状与差距

- 已有原子能力：空子 WS 创建、逐项绑定共享/空间、逐项派生（一个 Item 一个新 Tracker）、单个 LTI 创建。
- 缺失：LRO 聚合与状态机、任务表与轮询 REST、按父 Tracker 批量复制一个子 Tracker、批量 LTI 索引、关系/引用事件与构建、失败明细与部分成功。
- 现有 `RequirementCatalog.identities(trackerId)` 只有 `localId/commonId`，没有 `revision`/软删标记；T7 需要分页的身份端口（T7-D）。

## 5. 任务分解

### T7-A 设计评审与契约
- 评审 `working-set-creation-lro-design.md`；补 T4/场景（SCN）与失败语义；
- 定 API 形状（创建/轮询）、失败明细字段、任务状态命名；
- 输出评审记录（`reviews/`）。

### T7-B LRO 骨架
- 新增 `WorkingSetCreation` 聚合与 `WorkingSetCreationStore`（任务表、状态推进、结果 JSONB）；
- `POST /api/working-set-creations`、`GET /api/working-set-creations/{id}`；
- 幂等键（parent + newId + requestId）；重复提交返回已有任务；
- 状态 `Pending → Running → Succeeded/PartiallySucceeded/Failed`；
- 异步执行入口（与 MergeRound 类似的独立事务/执行模型）。

### T7-C 范围复制与绑定
- 创建子 WS 本体（复用 `createChildWorkingSet` 的规则）；
- 每个选定父 Tracker → 一个子 Tracker（`sourceId=父`、同空间/分类、`UNSHARED`）；
- 绑定非共享 Tracker、共享 Tracker、产品空间；校验直属父范围；
- 单项失败记录明细，继续其他项。

### T7-D 批量 LTI 索引
- 分页读取父 Tracker 下 Item 身份（`localId/commonId/revision`，剔除软删）；
- 为每个 Item 创建 LTI，身份及有效内容来源遵循 [LRO 设计 §4.5](../domain/working-set-creation-lro-design.md#45-身份与-lti-索引)；
- 分批提交；逐项记录成功/失败/跳过；部分成功；
- 重复执行/重入幂等（已存在 LTI 跳过并记录）。

### T7-E 关系/引用构建
- LTI 索引完成后发布事件（如 `TrackerItemsIndexed`），SK 异步监听；
- 关系模型依赖 QUESTION-010：先落事件契约、失败明细与 Port 占位；关系实体/规则在 QUESTION-010 裁定后实现；
- 关系构建失败记录在任务明细，不阻断索引与工作集本体。

### T7-F 规模与恢复
- 批大小/分页大小配置；任务结果保留/清理；
- 并发创建任务与连接池压测（QUESTION-012）；
- 失败明细规模验证。

## 6. 验收

- `POST` 创建任务返回 `creationId`；轮询可读状态/步骤/进度/失败明细。
- 正常路径：子 WS 本体、非共享 Tracker 副本、共享/空间绑定、全部非共享 Item 的 LTI 索引均完成，任务 `Succeeded`。
- 软删 Item 不出现在索引；共享 Tracker 只绑定所选，不复制。
- 部分失败：失败项在明细中带 stage/trackerId/localId/reason；任务 `PartiallySucceeded`；已成功项保留。
- 关键步骤（创建本体）失败：任务 `Failed`，明细可读。
- 重复提交返回已有任务；任务不提供重试接口。
- 关系构建事件：索引完成后触发；构建失败可在明细读取（或按 QUESTION-010 暂缓该步骤）。
- `mvn -B clean test` 全绿（T6-D 91 项 + 新增），含 ArchUnit、真实 PostgreSQL/Redis/Tomcat。

## 7. 全局约束

- 不重开已关闭裁定（D1～D17、C3～C6/C11/C12、QUESTION-001/003/008/009/015）。
- 失败语义只用 T4 §5 九分类；软删 `Deleted`、永久删除 `NotFound`、COW 依赖 `DependencyUnavailable`。
- 跨聚合写入沿用 D3-B/D15-B：业务保存独立提交，SK 切换/索引由事件完成，Modulith 重投收敛。
- 无补偿：失败保留现场，不自动删除子 WS/Tracker/LTI；用户可按 T2 K3 删除叶子子 WS。
- 不开放重试：任务一次执行；失败后人工处理明细。
- 测试只测公共 seam；事件驱动处使用 `await`；不修改测试期望掩盖冲突。
- 未经用户明确授权，不提交、不发布、不创建 Tag。

## 8. 完成定义

- T7-A 评审记录与契约更新完成；
- 任务表/状态机/REST/批量索引/失败明细可用；
- 关系事件按 QUESTION-010 的状态落地（完整实现或明确的暂缓说明）；
- 验证记录（`implementation/t7-*-verification.md`）与 `BASELINE.md` 登记；
- 文档链接可解析、`git diff --check` 干净。

## 9. 建议的第一个动作

先评审 `working-set-creation-lro-design.md` 与 API/失败明细形状（T7-A），确认 QUESTION-010 对 T7-E 的影响；再实现 T7-B 骨架。不要先写批量索引。
