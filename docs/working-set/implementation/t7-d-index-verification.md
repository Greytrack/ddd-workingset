# T7-D 批量 LTI 索引验收

**状态：Reviewed**。2026-11-14 用户裁定为 1A、2A、3B、4A、5A、6A。设计入口见 [创建 LRO](../domain/working-set-creation-lro-design.md)，验收入口见 [WorkingSetCreationAcceptanceTest](../../../src/test/java/bootstrap/WorkingSetCreationAcceptanceTest.java)。

- 多 Tracker 独立索引，业务 Revision、家族与有效内容来源均可通过工作集查询核对。扫描过滤软删原生对象及实际来源已软删的父变体，业务目录和父 LTI 的重合家族只计一次。
- 两条一批的验收中，首批已提交但下一批被源记录锁阻塞时，普通入口返回 `Preparing`，任务专用查询可用。重复投递通过任务行锁和游标接续。目标已有家族时保留原索引并计入跳过。
- 复制或绑定失败的父 Tracker 跳过索引。Item 失败保留已提交的其他 Item，任务可进入 `PartiallySucceeded`。索引完成事件由 Modulith 在终态事务提交后交付消费方，扫描完成但有单项失败的子 Tracker 仍进入事件。

| 索引情形 | 结果 |
| --- | --- |
| 父对象或实际来源缺失 | `NOT_FOUND` |
| 所需版次不可读 | `HISTORY_UNAVAILABLE` |
| 对象自身或 Source-backed 的实际来源已软删 | 过滤，读取端口的 `DELETED` 用于识别该情形 |
| 已知依赖不可用 | `DEPENDENCY_UNAVAILABLE` |
| 无法归类的内部故障 | `UNKNOWN`，任务明细只记录分类 |

游标和每 Tracker 的 Item 计数保存在任务 JSONB。历史终态任务继续可读，未终态的旧任务在推进时重新计算阶段总数。关系消费与规模验证分别属于 T7-E 和 T7-F。

`mvn -B clean test` 在 PostgreSQL、Redis 和内嵌 Tomcat 环境通过 104 项，含 ArchUnit。失败、错误、跳过均为 0。批次恢复验收覆盖提交后阻塞与并发重投，未模拟进程强制退出。
