# T7 后续实施计划

**状态：Draft（T7-E 待实现；T7-F 待推进；T7-G 已实施）**。已实现的创建任务、范围绑定、受理快照、索引和扩展工作集见 [T7 设计](../domain/working-set-creation-lro-design.md)及[验收索引](README.md)。

## 1. T7 现状

| 切片 | 状态 | 说明/证据 |
| --- | --- | --- |
| T7-A 契约 | Reviewed | [T7 设计](../domain/working-set-creation-lro-design.md)、[T7-A 评审](../reviews/2026-11-13-t7-a-creation-lro-review.md) |
| T7-B 任务骨架 | 完成 | 任务表、创建/轮询 REST、状态推进、幂等；[验收](t7-b-creation-task-verification.md) |
| T7-C 范围绑定 | 完成 | 子 WS 本体、非共享复制、Shared/空间绑定、失败明细；[验收](t7-c-binding-verification.md) |
| T7-D 批量索引 | 完成 | 受理快照、分页索引、软删剔除、部分成功、事件；[验收](t7-d-index-verification.md) |
| T7-E 关系构建 | 未实现 | QUESTION-010 C 已裁定；`TrackerItemsIndexed` 仅有占位监听器；无关系表/端口/构建器 |
| T7-F 规模/保留/恢复 | 未实现 | 依赖 QUESTION-012 与 `performance-testing.md`；无规模采样与清理策略 |
| T7-G 扩展工作集/批量派生 | 已实施 | 复用 `WorkingSetCreation` LRO（`Mode.EXTEND`）；多 Tracker 过滤/批量派生/部分成功/不可见标记/活动扩展锁；`POST /api/worksets/{target}/extensions`；单 Item `/derive` 入口已移除；[验收](t7-g-extension-verification.md) |

当前创建与扩展任务已覆盖：范围冻结、复制/绑定、索引、部分成功、轮询可见性、任务幂等、扩展并发闸门。剩余缺口是 T7-E 关系构建与 T7-F 规模/保留/恢复。

## 2. 统一待办（暂缓，不阻塞 T7）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| Merge `requestId` 载荷边界 | Deferred | 同一 `requestId` 不同 decisions、旧 MergeRound JSON、UI 重试复用等边界暂不处理；当前按调用方幂等承诺运行，后续统一收口 |

## 3. T7-G 已实施语义（2026-11-17）

- 扩展工作集时，已被派生且已绑定的 Tracker 不重复派生；按直接 `sourceId` 过滤，过滤后为空直接拒绝，返回 `NoDerivableTracker`，不创建任务。
- 目标已有派生 Tracker 的 Item 通过普通 Merge 合并；扩展只处理目标尚未拥有的 Tracker，不直接覆盖已有 Tracker 的 Item。
- 创建与扩展都采用 LRO；扩展选择一批直接父 Tracker，复用受理快照和批量索引。
- 创建新子工作集、扩展已有子工作集均允许部分成功。
- 扩展期间目标 WS 整体可见可操作；同一 WS 同时只允许一个未终态扩展任务。
- 新增 Tracker 在完成前 `visible=false`；终态后只揭示已成功绑定的新 Tracker，正常 Tracker/Bindings 列表过滤不可见项。

## 4. 优先级

### P1：T7-E 关系事件

- 先按 [T7-E 事件契约](../reviews/2026-11-17-t7-e-relation-guards-decision.md) 确认事件类与 Step 边界。
- WorkingSet 不操作 Reference/Association 聚合，只发布：
  - `ReferenceCreationRequested`
  - `DanglingReferenceRepairRequested`
  - `AssociationCreationRequested`
- Reference 带双端点与版次；Association 带双端点、不带版次；事件带 `eventId`、`originalRelationId`、`originId/originType`。
- 创建/扩展的 `PUBLISH_RELATION_EVENTS` 在索引后独立执行；Merge 的 `PUBLISH_RELATION_REPAIR_EVENTS` 在 Merge 成功后独立执行。
- 关系失败不自动重试、不阻塞主流程；后续重试/回滚按 QUESTION-018 单独裁定。
- 不新增关系表/Port；关系消费方按 `originalRelationId` 回查传播怀疑、类型和方向。

### P2：T7-F 规模、保留与恢复

- 采样受理快照耗时、并发创建连接池压力、批大小、失败明细规模。
- 定义 `merge_rounds`/`merge_checkpoints`/`working_set_creations`/`event_publication` 的保留与清理。
- 阈值依赖 QUESTION-012；不阻塞 T7-G/T7-E。

## 5. 下一步

1. T7-E：先确认 QUESTION-010 的可见性/删除守卫，再建关系表、Port、构建器并替换占位监听器。
2. T7-F：抽样受理快照、并发扩展、批大小、失败明细规模与保留清理；阈值依赖 QUESTION-012。
3. 生产接入前补充扩展任务与 `/derive` 退役的运行时/API 文档。<br>
   T7-G 验收见 [t7-g-extension-verification](t7-g-extension-verification.md)。

实施与验证遵循 [AGENTS.md](../../../AGENTS.md)。各片的场景、验收与裁定登记见 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)和 [BASELINE](../BASELINE.md)。
