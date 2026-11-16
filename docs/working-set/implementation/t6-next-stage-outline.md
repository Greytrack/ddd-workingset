# T6 后续实施大纲

**状态：Draft（规划）**。已完成切片见[验收索引](README.md)，实施约束见 [AGENTS.md](../../../AGENTS.md) 与 [BASELINE](../BASELINE.md)。

| 切片 | 剩余范围与验证 |
| --- | --- |
| T6-F 谱系约束 | **已裁定（2026-11-16），待实现**：Shared 允许悬挂；同源多派生 + `is_default`（每个「工作集，空间，分类」一个默认、名称在「空间，分类」内唯一、Shared 同样有默认）；`sourceId` 不可变；批量派生整批原子；完成即可见；条目身份 = 左右 Tracker + 左右工作集 + 家族 + 左右变体 + 左右版次。**实现前仍需敲定**：`is_default` 落 Tracker 还是 TrackerBinding（`QUESTION-002.k`）；候选对生成方式（全部组合 vs 按 `sourceId` 预过滤，`QUESTION-002.j`）。见 [T6-F 裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md)。 |
| T6-G 关系、Baseline 与治理 | 保留 Reference/Association 解析与修复（QUESTION-010 已裁定 C，关系实现由 T7-E 推进）、业务 Baseline 含义、Shared Kernel 版本治理。见 [QUESTION-010／011／014](../domain/open-questions.md)。 |

T6-E（瞬时 compare、一页决策 MergeRound、`requestId` 幂等、异步 LRO）已完成，验收见 [T6-E 验收](t6-e-merge-round-verification.md)。
规模、并发与保留验证统一见[规模压测与保留专题](performance-testing.md)，不作为实现切片的阻塞点。T7 关系构建见 [T7 后续计划](t7-implementation-prompt.md)。

## Merge 轮次边界（2026-11-16 收敛）

- Compare 只读瞬时；一页约 20–50 条差异，翻页通过重复 compare 的 `offset`/`limit` 获取，不承诺跨请求冻结。
- `POST /api/worksets/{target}/merge-rounds` 是唯一提交入口：`decisions` 必填 1–50 条，`requestId` 必填且不超过 128 字符；空决策、缺 `requestId`、超限一律 400。不再提供空轮次与分批登记入口。
- `requestId` 是幂等键：同键返回原轮次，同键换方向返回 `Conflict`；登记阶段证据过期整轮回滚并返回 `StaleEvidence`。
- `GET /api/merge-rounds/{id}` 完整返回 round/entries/result，不分页；`apply` 是幂等触发器。
- `merge_rounds.state` 保存整份轮次并整体重写；一页规模下不引入条目表，规模显著超过一页时改为独立行表，而不是依赖 `jsonb_set`。

## 回填

无。开发基线不迁移历史数据：数据库按 `schema.sql` 重建，演示数据由 `demo-data.sql` 生成；不存在需要 dry-run 的旧库数据。
