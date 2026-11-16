# 阶段 2 证据登记

**状态：Draft**

本页为候选知识提供稳定来源代号。来源中的强语气不代表业务确认；除非另有独立确认记录，历史材料中的声明统一视为 `Reported / Pending Confirmation`。

## 来源

| 代号 | 实际资料 | 本次读取范围 | 权威性 |
| --- | --- | --- | --- |
| `EXT-ARCH` | `C:\Users\13218\Documents\Codex\2026-11-08\referenced-chatgpt-conversation-this-is-an\outputs\working-set-ddd-domain-architecture-analysis.md` | 完整报告；SHA-256 `8C1F9ABC3C9C086F33CDE720F87AEF6293E47A7F0FB5977BC223398D237813DB` | 候选架构分析，不是正式基线 |
| `V4-OVERVIEW` | [`pre-design/workingsetModel-V4/00-overview.md`](../../../pre-design/workingsetModel-V4/00-overview.md) | 全文 | Historical |
| `V4-DOMAIN` | [`pre-design/workingsetModel-V4/01-domain-model.md`](../../../pre-design/workingsetModel-V4/01-domain-model.md) | 核心语言、边界、不变量、身份与生命周期 | Historical；部分主张与现行安全模型冲突，见 V4 对照 |
| `V4-USECASES` | [`pre-design/workingsetModel-V4/02-use-cases.md`](../../../pre-design/workingsetModel-V4/02-use-cases.md) | resolve/instantiate 等用例 | Historical；已由 T3/T4/T7 承接 |
| `V4-CONSISTENCY` | [`pre-design/workingsetModel-V4/03-consistency.md`](../../../pre-design/workingsetModel-V4/03-consistency.md) | 全文 | Historical |
| `V4-QUERY` | [`pre-design/workingsetModel-V4/04-query-command-demo.md`](../../../pre-design/workingsetModel-V4/04-query-command-demo.md) | 查询/命令示意 | Historical；无约束力 |
| `V4-LRT` | [`pre-design/workingsetModel-V4/05-workingset-creation-lrt.md`](../../../pre-design/workingsetModel-V4/05-workingset-creation-lrt.md) | 业务含义、阶段、恢复与验收章节 | Historical；T7 已改写 |
| `V4-REPLACE` | [`pre-design/workingsetModel-V4/06-workingset-content-replacement.md`](../../../pre-design/workingsetModel-V4/06-workingset-content-replacement.md) | 内容替换 | Historical；含未采纳候选（写入屏障、身份再生） |
| `V4-INSTANTIATION` | [`pre-design/workingsetModel-V4/07-item-instantiation-events.md`](../../../pre-design/workingsetModel-V4/07-item-instantiation-events.md) | 语义、事件、并发与验收章节 | Historical |
| `V4-REVIEW` | [`pre-design/workingsetModel-V4/08-design-review-and-implementation-outline.md`](../../../pre-design/workingsetModel-V4/08-design-review-and-implementation-outline.md) | 约束、缺口与评审关口 | Historical |
| `V4-PRODUCTSPACE` | [`pre-design/workingsetModel-V4/09-product-space-classification-projection.md`](../../../pre-design/workingsetModel-V4/09-product-space-classification-projection.md) | 产品空间/分类投影 | Historical；部分由 FACT-025/ADR-0004 承接 |
| `V4-UL` | [`pre-design/workingsetModel-V4/10-ubiquitous-language.md`](../../../pre-design/workingsetModel-V4/10-ubiquitous-language.md) | 全文 | Historical；术语映射源 |
| `MERGE` | [`pre-design/merge&diff/09-merge-domain-design.md`](../../../pre-design/merge&diff/09-merge-domain-design.md) | 术语、决定完整性、不变量、风险与验收章节 | Historical |
| `REFERENCE` | [`pre-design/workingsetReference/README.md`](../../../pre-design/workingsetReference/README.md) 及同目录 01、02、03、05、`CONTEXT.md` | 领域范围、模型、协作、修复与术语 | Historical |
| `AGENTS` | [`AGENTS.md`](../../../AGENTS.md) | 当前仓库治理与候选语义护栏 | 治理约束；不是领域确认记录 |
| `USER-ARCH-2026-11-09` | ChatGPT conversation `6af08f18-2ef8-83e9-a973-b5927d073c64` 最后一轮及附件 `image.png`、`image(1).png` | 用户明确说明 Shared Kernel 定位；本次读取完整末轮并查看两张原图 | 对 `AC-001` 的直接确认依据；图片不是内部领域模型 |

## 确认状态

- `Confirmed`：存在可追溯的用户或领域权威明确确认。当前已登记 `AC-001`、R01—R19、D-001～D-019 与 T1～T7 中用户裁定的部分；不代表整份来源文档已确认。
- `Reported`：资料明确如此记载，但缺少独立业务确认。
- `Pending Confirmation`：需要业务访谈、数据或实验确认。
- `Candidate`：本阶段的新架构推论或备选模型。
- `Rejected`：已被明确否定；当前阶段不擅自使用。

## 引用规则

记录优先引用“来源代号 + 章节／行号”。若是本阶段推论，来源写作“Stage 2 inference”，并列出推理所依赖的记录编号；不得把推论伪装为历史事实。
