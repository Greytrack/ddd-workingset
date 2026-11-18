# 2026-11-18 仓库降噪与设计—实现对齐

**状态：Reviewed（整理记录；不改变已接受领域语义）**

## 范围

对重构后的多模块仓库做一次降噪：删除失效工具配置与运行残留，修正文档状态/计数/断链，清理无生产调用的代码，并修复审查中发现的实现偏差。

## 已完成

| 类别 | 改动 |
| --- | --- |
| 工具配置 | 删除根目录 `.lattice/`（AirEdge Backend 2.1 遗留的 161 个配置文件与上下文）；Lattice Skills 仍按 AGENTS §12 从外部目录读取。 |
| 运行残留 | 删除根目录 `run-web.log`、`run-web-err.log`。 |
| 文档 | 修正 `reviews/README.md` 断链；T7-E 标为 Reviewed 并只保留真实未决项；T1 删除已废弃的 `Compared`/`ComparisonToken` 表述；facts 的 `POL-013/015` 去掉已落地的“待定”；T5 验收标 Historical，`BusinessObject` 设计并入 T4 后标 Historical。 |
| 代码 | 删除未使用的 `CowRecoveryService.listFailures`、`CapabilitySet.readOnly`、`ResCode.getValue`、`ReferenceEndpointEvent.isResolved`。 |
| 单 Item 派生 | 删除 `WorkingSetApplicationService.derive`、`DeriveCommand`、`DeriveResult` 及仅供其使用的 `nextDistinctLocalId`/`resolveContentSource`；验收夹具迁移到测试侧 `DeriveFixture`，读取/解析测试改用 `LightTrackerItem.derive`。 |
| 修复 | `WorkingSetItemIndexer` 的 LocalId 排序改为数值序，与 SQL 一致；`TrackerItemsIndexedEvent` 使用索引完成时点；扩展任务运行期间不再拦截目标 WorkingSet 的已有范围读取；Merge 同一 `requestId` 并发提交先取 advisory lock，消除唯一索引死锁。 |

## 验证

`mvn -B test`：**165 项通过，0 失败、0 错误、0 跳过**；测试库已清理至基线。

## 仍开放（未在本次清理中裁定）

- `WorkingSetApplicationService` 的 WorkingSet + Tracker/LTI 同事务写入，与 `TC-002` 单聚合提交的取舍需 ADR 或明确一致性说明。
- T7-E 关系事件仍无持久化 outbox/消费方；Merge 关系修复失败只记日志。
- `bindings` 单表存储方案尚未实施；当前仍是 `space_bindings` + `tracker_bindings`。
- QUESTION-006/008.b/008.c/010/011/012/014/017/018 仍按 [开放问题](../domain/open-questions.md) 处理。
