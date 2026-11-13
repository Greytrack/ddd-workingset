# T6-D 回并裁定评审

**状态：Reviewed**

用户在 T6-D 实施对话中确认只使用 Merge CREATE，来源沿用 Merge 审计，并明确要求自动恢复父侧软删变体后合并更新。当前规则见 [T1 §8](../domain/merge-execution-contract-v1.md#8-轮次终态与结果表达)。

单独恢复后再 Compare 可以沿用两个独立操作，但中间会暴露恢复后的旧内容，还需重新确认。选定方案在同一业务条目事务内恢复并更新，失败可回滚到原软删状态，D16 家族席位和原变体身份继续有效。实现沿用现有唯一约束与条目事务，可在现有 Merge 记录追溯，无需迁移或新增 ADR。

原实施提示词提出并发失败返回 HTTP 409。不同轮次的同族竞争沿用 T1 逐条结果，目标由缺席变为有效时返回条目级 `StaleEvidence`，以保留其他条目的执行结果。实测见 [T6-D 验收](../implementation/t6-d-merge-back-verification.md)。
