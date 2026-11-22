# Compare 不持久化与 MergeRound 决策裁定

**状态：Reviewed（2026-11-15，业务团队讨论）**

## 裁定

- **compare 是高频只读展示**：比较结果不持久化，不保存 `merge_comparisons`，也不以 JSONB 整份保存。前端可重复调用
  compare；分页通过 `offset`/`limit` 获取，不承诺跨请求冻结。
- **compare + merge 是一个整体用例，但 compare 只作参考**：用户基于比较结果作出接收、忽略、字段级决定等选择。
- **用户决策随 MergeRound 持久化**：每个选中的差异携带其比较证据（双侧身份、presence、revision、字段与失败状态）和决定；MergeRound
  保存这些证据与决定，作为后续 apply 的唯一决策范围。
- **apply 只执行 MergeRound 中的决策范围**：不重新读取比较 token，不扩大到未登记的差异；仍按 T1 的当前状态校验处理过期证据。
- **MergeRound 采用异步 LRO**：提交决策后返回可轮询任务；`MergeRoundSubmittedEvent` 事件驱动消费者逐条执行新增、删除、更新，
  `GET /merge-rounds/{id}` 轮询至终态；`apply` 仅保留为幂等触发器。
- **MergeRound 规模边界**：一轮对应 UI 一页约 20–50 条决策；`GET /merge-rounds/{id}` 完整返回 entries/result，不做条目页；当前整份
  state JSONB 写入在该规模可接受。`jsonb_set` 语法上可局部替换，但 PostgreSQL JSONB 不可变，仍会写新元组/TOAST；规模明显增大时再规范化
  entries/results。
- **提交幂等**：决策提交必须携带 `requestId`；同一 requestId 返回同一 MergeRound，不重复创建或重复发布异步执行事件；不同
  source/target 使用同一 requestId 返回冲突。
- **Compare 只下发差异**：一致项不返回；差异分为 `ADDED`、`MODIFIED`、`DELETED`。新增/删除不比较字段；`MODIFIED` 才比较属性。
- **Checkpoint 命中**：左右 `LocalId` 与左右 `Revision` 全匹配时视为该基准无差异，不返回该差异，也不比较属性；未命中才比较更新字段。
- **迁移语义**：`ComparisonToken` 不再是可以回读冻结清单的持久化标识；`readDiffPage` 由重复 compare 取代。
  `merge_rounds.token` 保留为轮次审计标识，不再引用 `merge_comparisons`。

## 影响

| 区域        | 变化                                                                                           |
|-------------|------------------------------------------------------------------------------------------------|
| 存储        | 删除 `merge_comparisons`；`merge_rounds.token` 取消外键；轮次仍保存决策证据 JSONB              |
| Compare API | 只读、可重复；支持 offset/limit；不返回可用于回读的冻结 token                                  |
| MergeRound  | 创建时指定 source/target；登记时接收 `evidence` + `decision`；保存所选差异                     |
| 过期证据    | 登记时用当前状态校验客户端提交的证据；apply 时继续校验存储证据                                 |
| T6-E        | 原“Compare JSONB 分页/保留”改为“瞬时 compare 的 UI 分页 + MergeRound 决策证据的分页/保留/规模” |

## 仍然开放

- compare 页面在用户刷新/翻页期间可能漂移；是否需要短 TTL 缓存只作为性能优化，不作为持久化事实。
- 客户端提交证据的校验边界已收敛为一页 1–50 条、`requestId` ≤128；伪造 evidence 由登记阶段校验拒绝。
- MergeRound 决策证据的保留期限、归档与清理见[规模压测与保留专题](../implementation/performance-testing.md)。
- 旧比较 token/旧轮次的迁移策略：开发基线不迁移历史数据，比较表已删除。

## 相关材料

- [T1 Merge 执行契约 §3](../domain/merge-execution-contract-v1.md#3-diff-查询能力与瞬时比较证据)
- [T6-E 规划](../implementation/t6-next-stage-outline.md)
- [MergeController](../../../sharedkernel/src/main/java/com/airedgesoft/sharekernel/workingset/adapters/MergeController.java)
- `MergeExecutionService.java`（当时路径）
- [MergeRepository](../../../sharedkernel/src/main/java/com/airedgesoft/sharekernel/workingset/domain/merge/MergeRepository.java)
