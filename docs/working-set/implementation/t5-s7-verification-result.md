# S7 T1 Merge 执行验证

**状态：Reviewed（实施验证记录，2026-11-12）**

依据 [T1](../domain/merge-execution-contract-v1.md)、[ADR-0005](../adr/0005-merge-execution-model.md) 和 [S7 用户裁定](../reviews/2026-11-12-s7-merge-implementation-decisions.md)。

## S7-A · Compare 与 ComparisonToken

[Compare 服务](../../../src/main/java/sk/workingset/application/MergeComparisonService.java) 通过 S6 的谱系准入选择直属父子非共享 Tracker 对，按 CommonId 匹配业务身份，通过 TrackerItemReadPort 读取内容。比较区分现存、缺失、标记删除及不可读。不可读侧携带原因，例如 `HISTORY_UNAVAILABLE`。空范围返回空候选和空差异。

ComparisonToken 引用 `merge_comparisons` 中的冻结清单，含双侧 LocalId、业务及内容版次、发布版本、乐观锁与字段证据。分页读取同一份清单。登记复核双侧证据；执行使用用户裁定的分界。一个比较凭据对应至多一个轮次。

## S7-B · MergeRound、MergeEntry 与 ApplyResult

[MergeRound](../../../src/main/java/sk/workingset/domain/merge/MergeRound.java) 拥有条目和唯一终态快照，持久化于 `merge_rounds`。轮次从 `Preparing` 进入 `Running`，按结果完成为 `Succeeded`、`PartiallySucceeded` 或 `Failed`。重复 Apply 返回原结果。新轮次重新比较，由用户选择要执行的条目。

字段决定显式登记；存在性决定区分 `CREATE`、`RESTORE`、`DELETE`、`KEEP_TARGET` 与 `DEFER`。有 DEFER 的条目整体延迟。 `KEEP_TARGET + markAsMerged=true` 可确认成功且保持内容原样。独立条目事务保留已提交项，并记录拒绝、冲突、过期、延迟或未知结果。

## S7-C · Checkpoint、ALM 写入与生命周期

- [Checkpoint 映射](../../../src/main/java/sk/workingset/infrastructure/JdbcMergeStore.java) 按有向源、目标及 CommonId 保存决定版本与双侧业务 Revision。确认和撤销按版本单调更新，已处理的同版次差异可由 Compare 隐藏。旧确认无法覆盖更新的撤销。
- [ALM Merge 写入](../../../src/main/java/alm/requirement/application/RequirementMergeWriter.java) 通过 RequirementWritePort 接收操作身份、决定和证据，调用聚合行为并整体校验。未物化目标用目标原有有效内容构建聚合并保存，再沿用 LightTrackerItem 的本地切换行为。轮次行锁与每条结果同事务落库提供 `mergeRoundId + entryId` 幂等边界。
- [Requirement 生命周期](../../../src/main/java/bootstrap/web/RequirementController.java) 增加 `delete`、`undelete`、`delete2`。标记删除用 `requirements.delete_flag` 保留业务行，正常读取返回 410。永久删除清除业务行、该对象的业务快照和对应 LightTrackerItem；未物化依赖仍需要内容时返回 `PREPARING`。T1 已冻结的比较与结果证据作为执行审计保留。
- [schema.sql](../../../src/main/resources/schema.sql) 新增 `merge_comparisons`、`merge_rounds`、`merge_checkpoints`、决定版本序列和 `delete_flag`，并建立 Tracker 内 Requirement 家族唯一索引。已有库如含同 Tracker、同 CommonId 的重复 Requirement，初始化前须按真实身份事实消解。

## S7-D · HTTP 与真实验收

| 请求 | 用途 |
| --- | --- |
| `POST /api/worksets/{target}/compare/{source}` | 生成比较，支持可选 `trackerId` 和 `limit` |
| `GET /api/comparisons/{token}?offset=0&limit=100` | 读取冻结差异页 |
| `POST /api/worksets/{target}/merge-rounds` | 以 `comparisonToken` 创建轮次 |
| `POST /api/merge-rounds/{id}/entries` | 登记 `entries`，元素含 `entryKey` 与 `decision` |
| `POST /api/merge-rounds/{id}/apply`、`GET /api/merge-rounds/{id}` | 执行和读取进度或终态 |
| `POST /api/worksets/{target}/merge-checkpoints/{source}/{family}/revoke` | 按 `expectedDecisionVersion` 撤销确认 |
| `POST /api/requirements/{id}/{delete\|undelete\|delete2}` | Requirement 生命周期 |

[真实验收](../../../src/test/java/bootstrap/MergeAcceptanceTest.java) 覆盖双向新增、删除和修改，显式恢复，准入拒绝与空范围，冻结分页，登记过期、执行时取最新值及版次过期，KEEP_TARGET、DEFER、BC 拒绝、部分成功、独立新轮次，未物化目标和删除依赖。最终 `mvn -B test` 通过 **76 项，0 失败、0 错误、0 跳过**，包含 PostgreSQL、Redis、Tomcat 和 ArchUnit。

## 切片边界

S7 仅覆盖 ALM Requirement 普通字段。完整历史回读、附件关系、Privilege、停用、批量删除顺序、真实平台元数据回填及完整删除限制仍待后续切片。冻结比较和轮次目前按清单持久化，面向大范围的扫描、分批和保留策略尚无规模验证。
