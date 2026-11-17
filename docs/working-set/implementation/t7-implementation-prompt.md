# T7 后续实施计划

**状态：Draft**。已有能力和验收证据见 [实施索引](README.md)；当前 API、任务行为和边界见 [T7 设计](../domain/working-set-creation-lro-design.md)。

| 下一步 | 验收范围 |
| --- | --- |
| 关系服务接入 | 用真实业务 BC Adapter 替换空事实源，验证消费端建关系、修复原引用和守卫。依据 [QUESTION-010](../domain/open-questions.md) 与 [T7-E 事件契约](../reviews/2026-11-17-t7-e-relation-guards-decision.md)。 |
| T7-F 规模与保留 | 采样快照和索引耗时、连接池压力、失败明细增长，验证清理与恢复。阈值和清理安排见 [规模与保留专题](performance-testing.md)及 QUESTION-012/017。 |

Merge 同一个 `requestId` 携带不同 decisions、旧轮次 JSON 与 UI 重试复用仍是后续幂等边界；本计划不作新裁定。
