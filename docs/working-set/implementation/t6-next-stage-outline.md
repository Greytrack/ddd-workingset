# T6 后续实施大纲

**状态：Draft（规划）**。已完成切片见[验收索引](README.md)，实施约束见 [AGENTS.md](../../../AGENTS.md) 与 [BASELINE](../BASELINE.md)。

| 切片 | 剩余范围与验证 |
| --- | --- |
| T6-E 规模、分页与保留 | 验证 Compare/Merge 冻结 JSONB 的分页边界、负载与保留清理，确保可追溯结果仍可读；制定真实元数据与重复 Requirement 回填方案。见 [QUESTION-012](../domain/open-questions.md)。 |
| T6-F 谱系约束 | 逐项裁定 Shared 父级一致性、同源多派生、来源可变性、批次原子性和完成可见性。见 [QUESTION-002](../domain/open-questions.md)。 |
| T6-G 关系、Baseline 与治理 | 保留 Reference/Association 解析与修复（QUESTION-010 已裁定 C，关系实现由 T7-E 推进）、业务 Baseline 含义、Shared Kernel 版本治理。见 [QUESTION-010／011／014](../domain/open-questions.md)。 |

待决定第二个 BC 的验收时机、版本治理启动时机，以及压测和真实数据回填的顺序。T7 的关系构建、批量创建规模及保留验证见 [T7 后续计划](t7-implementation-prompt.md)。
