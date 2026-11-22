# 契约索引

**状态：Reviewed（T1～T4 已冻结，T7 已收敛；未纳入正式发布）**

本目录保存 Working Set 的跨 BC 公共契约。契约回答「对外承诺什么」；规则依据见 [事实、政策与不变量](../03-rules.md)，实现现状见
[实现现状与验收证据](../07-implementation.md)，裁定过程见[裁定时间线](../06-decisions/timeline.md)。

| 契约 | 范围 | 状态 | 入口 |
|------|------|------|------|
| **T1** Merge 执行 | 有向差异、决定、Apply Result、合并确认、轮次终态 | Reviewed（2026-11-11 冻结；2026-11-20 按 ADR-0006 调整确认归属） | [T1 契约](t1-merge-execution.md) |
| **T2** 绑定与删除 | 绑定结构、删除准入与拒绝条件、绑定移除与聚合删除的区分 | Reviewed（2026-11-11 冻结；`K1`～`K3` 已确认） | [T2 契约](t2-binding-deletion.md) |
| **T3** LightTrackerItem 物化 | 身份、双态、COW、来源固定、失败与删除保留 | Reviewed（2026-11-11 冻结；`K1′`～`K3′` 已确认） | [T3 契约](t3-lti-materialization.md) |
| **T4** 跨 BC 公共契约 | 只读 Provider、写入事件协作、九分类失败语义与错误码 | Reviewed（2026-11-11 冻结；`K1″`／`K2″` 已确认，`K3″` 团队开放） | [T4 契约](t4-cross-bc.md) |
| **T7** 工作集创建/扩展 LRO | 基线时间、阶段、任务明细与关系事件 | Reviewed（2026-11-13 裁定；2026-11-21 按 ADR-0010 调整） | [T7 契约](t7-creation-lro.md) |
| 多级 Working Set 谱系 | 单父、`parentWorkingSetId`、Tracker 来源约束、相邻父子 Merge 准入 | Reviewed（2026-11-12；`FACT-028` 已确认） | [谱系设计](working-set-lineage.md) |

## 契约之间的关系

- **T1 与 T4**：T4 定义跨 BC 的只读读取与失败语义；T1 在其上定义 diff／merge 的执行与终态。Merge 条目的业务应用由目标 BC 在独立事务提交。
- **T3 与 T4**：T3 定义 LightTrackerItem 的双态与 COW 协议；T4 的 `TrackerItemMaterializationRequestedEvent`／`CompletedEvent` 是它的跨 BC 协作面。
- **T2 与 T7**：T2 定义绑定与删除的准入；T7 的创建/扩展按各 Tracker 的基线时间尽力派生，失败明细与保留见 [后续计划 §2](../08-plan.md#2-规模与保留)。
- **T7 与谱系**：谱系设计定义多级 Working Set 的结构约束；T7 定义在其上的长流程执行。

## 未纳入本目录

- **T5**（首切片）是历史实施证据，不作为当前契约入口，见[归档实现](../archive/implementation/)。
- **T6 系列**（Merge 轮次、身份、回并、谱系约束）是 T1 下的实施裁定，见[裁定时间线](../06-decisions/timeline.md)与[归档评审](../archive/reviews/)。
- **T7-D** 批量索引旧验收已被 ADR-0010 取代，见[归档实现](../archive/implementation/t7-d-index-verification.md)。
