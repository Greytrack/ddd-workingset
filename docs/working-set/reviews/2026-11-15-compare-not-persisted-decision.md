# Compare 不持久化与 MergeRound 决策裁定

**状态：Reviewed（2026-11-15，业务团队讨论）**

## 裁定

- **compare 是高频只读展示**：比较结果不持久化，不保存 `merge_comparisons`，也不以 JSONB 整份保存。前端可重复调用 compare；分页通过 `offset`/`limit` 获取，不承诺跨请求冻结。
- **compare + merge 是一个整体用例，但 compare 只作参考**：用户基于比较结果作出接收、忽略、字段级决定等选择。
- **用户决策随 MergeRound 持久化**：每个选中的差异携带其比较证据（双侧身份、presence、revision、字段与失败状态）和决定；MergeRound 保存这些证据与决定，作为后续 apply 的唯一决策范围。
- **apply 只执行 MergeRound 中的决策范围**：不重新读取比较 token，不扩大到未登记的差异；仍按 T1 的当前状态校验处理过期证据。
- **MergeRound 采用异步 LRO**：提交决策后返回可轮询任务；`MergeRoundSubmitted` 事件驱动消费者逐条执行新增、删除、更新，`GET /merge-rounds/{id}` 轮询至终态；`apply` 仅保留为幂等触发器。
- **迁移语义**：`ComparisonToken` 不再是可以回读冻结清单的持久化标识；`readDiffPage` 由重复 compare 取代。`merge_rounds.token` 保留为轮次审计标识，不再引用 `merge_comparisons`。

## 影响

| 区域 | 变化 |
| --- | --- |
| 存储 | 删除 `merge_comparisons`；`merge_rounds.token` 取消外键；轮次仍保存决策证据 JSONB |
| Compare API | 只读、可重复；支持 offset/limit；不返回可用于回读的冻结 token |
| MergeRound | 创建时指定 source/target；登记时接收 `evidence` + `decision`；保存所选差异 |
| 过期证据 | 登记时用当前状态校验客户端提交的证据；apply 时继续校验存储证据 |
| T6-E | 原“Compare JSONB 分页/保留”改为“瞬时 compare 的 UI 分页 + MergeRound 决策证据的分页/保留/规模” |

## 仍然开放

- compare 页面在用户刷新/翻页期间可能漂移；是否需要短 TTL 缓存只作为性能优化，不作为持久化事实。
- 客户端提交证据的校验边界、最大 payload、重复/伪造 evidence 的拒绝规则。
- MergeRound 决策证据的保留期限、归档、分页与清理。
- 真实元数据与重复 Requirement 回填仍属 T6-E。
- 旧比较 token/旧轮次的迁移策略；当前实现按开发环境直接移除比较表。

## 相关材料

- [T1 Merge 执行契约 §3](../domain/merge-execution-contract-v1.md#3-diff-查询能力与-comparisontoken-契约)
- [T6-E 规划](../implementation/t6-next-stage-outline.md)
- [MergeController](../../../src/main/java/bootstrap/web/MergeController.java)
- [MergeExecutionService](../../../src/main/java/sk/workingset/application/MergeExecutionService.java)
- [MergeStore](../../../src/main/java/sk/workingset/application/port/MergeStore.java)
