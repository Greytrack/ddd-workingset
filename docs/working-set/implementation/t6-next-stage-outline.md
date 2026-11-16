# T6 后续实施大纲

**状态：Draft（规划）**。已完成切片见[验收索引](README.md)，实施约束见 [AGENTS.md](../../../AGENTS.md) 与 [BASELINE](../BASELINE.md)。

| 切片 | 剩余范围与验证 |
| --- | --- |
| T6-E 规模、分页与保留 | Compare 不持久化；UI 一页约 20–50 条差异，MergeRound 保存这一页决策与证据，`GET /merge-rounds/{id}` 完整返回 entries/result、不分页；验证该规模下 JSONB 写入、保留清理与连接池；`requestId` 幂等防重复提交；制定真实元数据与重复 Requirement 回填方案。见 [QUESTION-012](../domain/open-questions.md)。 |
| T6-F 谱系约束 | 逐项裁定 Shared 父级一致性、同源多派生、来源可变性、批次原子性和完成可见性。见 [QUESTION-002](../domain/open-questions.md)。 |
| T6-G 关系、Baseline 与治理 | 保留 Reference/Association 解析与修复（QUESTION-010 已裁定 C，关系实现由 T7-E 推进）、业务 Baseline 含义、Shared Kernel 版本治理。见 [QUESTION-010／011／014](../domain/open-questions.md)。 |

待决定第二个 BC 的验收时机、版本治理启动时机，以及压测和真实数据回填的顺序。T7 的关系构建、批量创建规模及保留验证见 [T7 后续计划](t7-implementation-prompt.md)。

## T6-E 分页与异步 LRO 边界（2026-11-15）

- Compare 只读瞬时，一页约 20–50 条差异；UI 翻页通过重复 compare 的 `offset`/`limit` 获取，不承诺跨请求冻结。
- MergeRound 由一页决策形成，规模受一页差异数约束；`GET /merge-rounds/{id}` 直接返回完整 round/entries/result，**不做条目/结果分页**。
- 决策提交使用 `requestId` 幂等键：同一 requestId 返回同一 MergeRound；不同 source/target 使用同一 requestId 返回冲突。
- 异步 LRO 由 `MergeRoundSubmitted` 事件驱动，逐条执行新增、删除、更新；前端轮询 `GET /merge-rounds/{id}` 至终态。

## JSONB 写放大与部分更新（结论）

- 当前 `merge_rounds.state` 保存整份轮次，`updateRound` 每次重写整份 JSONB；在 20–50 条决策规模下可接受，暂不引入条目表。
- `jsonb_set` 能在语法上只替换特定字段，但 PostgreSQL JSONB 值不可变，更新仍会生成新 JSONB 值并写入新元组/TOAST，不是真正的“原地只改一块”。它可减少应用层序列化和读改写，但不能消除物理写放大。
- 若未来决策规模显著超过一页，应优先把 entries/results 规范化为独立行表，而不是依赖 `jsonb_set`；当前不为此过度设计。

## T6-E 回填语义与示例

“真实元数据回填”是指把既有 ALM/PLM 数据（工作集、Tracker、ProductSpace、Classification、Binding、Requirement、LTI/索引）按已确认身份规则导入当前 schema，并处理历史数据与约束冲突；不是生成新业务事实。

示例：

- `requirements.working_set_id` 为空或与 Tracker 所属工作集不一致：按 `trackers.working_set_id` 回填，再创建/校验工作集家族唯一索引。
- 同一 `(working_set_id, common_id)` 出现多条有效 Requirement：不能自动合并；按业务事实选定唯一有效变体，其他记录软删/归档或进入人工裁定，保留审计。
- 同族一条有效、一条软删：软删占键（D16），不得静默复活；需恢复原变体或人工处理。
- 既有 Requirement 缺少 LTI 索引或索引重复：按 T3/T7 的 CommonId、LocalId、Revision 和有效来源规则分批补建，幂等重跑。
- 旧 `merge_checkpoints` 缺少左右 LocalId：新 compare 不会命中，需从轮次证据回填或明确按“未命中”处理。
- 旧 `merge_rounds.state` 缺少 `requestId` 或差异 `kind`：读取时按 null/分类默认兼容；新提交才要求 requestId。
- 历史 `merge_comparisons` 已停止持久化：如需审计，先导出/归档，不再回填到新比较表。
- 回填应先 dry-run、统计冲突、分批提交、可重跑/可审计；不能把重复身份自动合并成新业务事实。
