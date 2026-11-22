# T7-D 批量 LTI 索引验收

**后续裁定**：[ADR-0010](../../06-decisions/adr-0010-working-set-creation-tracker-baseline-time.md) 替代下述冻结清单、实时回退和快照存储规则。以下保留原实现和验收记录。

**状态：Superseded（ADR-0010）**
。以下保留旧实现与旧验收证据，不再作为当前规则；当前规则见 [ADR-0010](../../06-decisions/adr-0010-working-set-creation-tracker-baseline-time.md)
与 [T7 §4.5](../../05-contracts/t7-creation-lro.md)。

- 历史：受理事务曾将父 Item 身份与有效来源写入 `working_set_creation_items`
  ，索引阶段只读该快照；该冻结清单与快照存储已由 [ADR-0010](../../06-decisions/adr-0010-working-set-creation-tracker-baseline-time.md)
  替代。冻结后来源缺失、软删或版次不可读时，当时按跳过并保留明细处理。
- 生产新增 `TrackerItemsIndexedListener` 占位监听器，使事件进入 Modulith 发布记录并可重投；T7-E 接入关系构建时替换。
- 本切片索引以系统任务身份读取父内容，不校验用户级授权；权限语义留待跨 BC 权限接入时另行裁定。
- 多 Tracker 独立索引，业务 Revision、家族与有效内容来源均可通过工作集查询核对。受理时已软删的原生对象及实际来源已软删的父变体不进入候选，业务目录和父
  LTI 的重合家族只计一次。
- 两条一批的验收中，首批已提交但下一批被源记录锁阻塞时，普通入口返回 `Preparing`
  ，任务专用查询可用。重复投递通过任务行锁和游标接续。目标已有家族时保留原索引并计入跳过。
- 复制或绑定失败的父 Tracker 跳过索引。Item 失败保留已提交的其他 Item，任务可进入 `PartiallySucceeded`。索引完成事件由
  Modulith 在终态事务提交后交付消费方，扫描完成但有单项失败的子 Tracker 仍进入事件。

| 索引情形                   | 结果                                              |
|----------------------------|---------------------------------------------------|
| 索引时父对象或实际来源缺失 | 跳过，保留 `NOT_FOUND` 明细                       |
| 索引时所需版次不可读       | 跳过，保留 `HISTORY_UNAVAILABLE` 明细             |
| 对象自身或实际来源软删     | 受理时过滤；冻结后变化则跳过，保留 `DELETED` 明细 |
| 已知依赖不可用             | `DEPENDENCY_UNAVAILABLE`                          |
| 无法归类的内部故障         | `UNKNOWN`，任务明细只记录分类                     |

游标和每 Tracker 的 Item 计数保存在任务
JSONB。历史终态任务继续可读；未终态的旧任务在推进时重新计算阶段总数，没有快照时回退实时分页，仅作为兼容路径。上述“新任务在受理事务内冻结快照”的规则已由
[ADR-0010](../../06-decisions/adr-0010-working-set-creation-tracker-baseline-time.md) 替代：当前新任务按各 Tracker 的基线时间与 LocalId
游标分页扫描，不再冻结清单。关系消费与规模验证分别属于
T7-E 和 T7-F。

`mvn -B clean test` 在 PostgreSQL、Redis 和内嵌 Tomcat 环境通过 105 项，含 ArchUnit。失败、错误、跳过均为
0。批次恢复验收覆盖提交后阻塞与并发重投，未模拟进程强制退出。

目标已有同族时先认已提交索引，来源随后软删或不可读仍计为跳过；回归测试见 [WorkingSetItemIndexerTest](../../../../deploys/src/test/java/com/airedgesoft/sharekernel/workingset/application/WorkingSetItemIndexerTest.java)。
