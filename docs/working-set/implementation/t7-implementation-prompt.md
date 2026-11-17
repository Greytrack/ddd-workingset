# T7 后续实施计划

**状态：Draft（T7-E 契约已裁定 C；T7-F 待推进；T7-G 待设计）**。已实现的创建任务、范围绑定、受理快照和索引见 [T7 设计](../domain/working-set-creation-lro-design.md)及[验收索引](README.md)。

## 1. T7 现状

| 切片 | 状态 | 说明/证据 |
| --- | --- | --- |
| T7-A 契约 | Reviewed | [T7 设计](../domain/working-set-creation-lro-design.md)、[T7-A 评审](../reviews/2026-11-13-t7-a-creation-lro-review.md) |
| T7-B 任务骨架 | 完成 | 任务表、创建/轮询 REST、状态推进、幂等；[验收](t7-b-creation-task-verification.md) |
| T7-C 范围绑定 | 完成 | 子 WS 本体、非共享复制、Shared/空间绑定、失败明细；[验收](t7-c-binding-verification.md) |
| T7-D 批量索引 | 完成 | 受理快照、分页索引、软删剔除、部分成功、事件；[验收](t7-d-index-verification.md) |
| T7-E 关系构建 | 未实现 | QUESTION-010 C 已裁定；`TrackerItemsIndexed` 仅有占位监听器；无关系表/端口/构建器 |
| T7-F 规模/保留/恢复 | 未实现 | 依赖 QUESTION-012 与 `performance-testing.md`；无规模采样与清理策略 |
| T7-G 扩展工作集/批量派生 | 未设计 | 承接 T6-F i（批量派生整批原子）；不在 T7-A 原切片表中；`/derive` 公开入口仍在 |

当前创建任务已覆盖：范围冻结、复制、绑定、索引、部分成功、轮询可见性、任务幂等。缺口主要是“对已有工作集的扩展/批量派生”和“关系构建”。

## 2. 统一待办（暂缓，不阻塞 T7）

| 项 | 状态 | 说明 |
| --- | --- | --- |
| Merge `requestId` 载荷边界 | Deferred | 同一 `requestId` 不同 decisions、旧 MergeRound JSON、UI 重试复用等边界暂不处理；当前按调用方幂等承诺运行，后续统一收口 |

## 3. 优先级

### P0：先做 T7-G 的契约设计，再实现

理由：

- T6-F §1.4 已裁定批量派生“整批原子”，i 已明确转 T7-G；这是 T6-F 最后一个未实现项。
- T7-G 完成后才能删除 `/api/worksets/{ws}/derive` 公开入口，收敛“Item 只经合并传递”的主线。
- T7-G 可复用 T7 的 LRO 状态机/事件/失败明细，增量主要在“目标工作集已存在”的扩展语义，不需要先引入新的关系模型。
- 先设计再编码，避免把 T7-A 的“新建 WS 部分成功”与 T6-F 的“批量派生整批原子”混成一套语义。

实现前需要先确认：

1. **QUESTION-002.l**：建议 A——T7 创建新工作集允许部分成功；T7-G 扩展已有工作集整批原子（全成或全不成）。
2. **扩展目标**：已有子 WS，按直接父 WS 的 Tracker 选择批量复制/绑定/索引。
3. **已存在项**：目标已有同 Tracker/同族 LTI 时，是跳过视为成功，还是整批冲突；整批原子下必须明确定义。
4. **幂等键**：沿用创建任务的 `(parentWorkingSetId,newWorkingSetId,requestId)`，还是使用目标 WS + requestId 的扩展任务键。
5. **可见性**：扩展期间目标 WS 是否进入 `Preparing`，还是只对新增范围加锁；这会影响普通入口和并发写入。

### P1：T7-E 关系构建

- 先补关系契约：Reference 逻辑 Family + 可选钉版/物理定位；Association 具体 Variant 端点；缺端点不落库。
- 先确认 QUESTION-010 的剩余可见性与删除守卫：
  - 目标缺失、无权、软删、永久删除、后续纳入、钉版不可读；
  - 关系不参与 Compare/Merge（D6）。
- 再建关系表/Port/构建器，替换 `TrackerItemsIndexedListener` 占位。
- 关系进度是否进入任务视图需要单独裁定。

### P2：T7-F 规模、保留与恢复

- 采样受理快照耗时、并发创建连接池压力、批大小、失败明细规模。
- 定义 `merge_rounds`/`merge_checkpoints`/`working_set_creations`/`event_publication` 的保留与清理。
- 阈值依赖 QUESTION-012；不阻塞 T7-G/T7-E。

## 4. 建议第一步

1. 确认 `QUESTION-002.l` 采用 A（T7 创建部分成功、T7-G 整批原子）。
2. 写 T7-G 契约：目标、范围、原子性、已存在项、幂等、可见性、失败明细。
3. 补 `T7-G` 场景与验收（扩展已有 WS、部分/全部失败、重复提交、`/derive` 退役）。
4. 实现 T7-G 后再进入 T7-E；T7-F 可并行采样，不阻塞。

实施与验证遵循 [AGENTS.md](../../../AGENTS.md)。各片的场景、验收与裁定登记见 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)和 [BASELINE](../BASELINE.md)。
