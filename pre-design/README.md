# pre-design：历史设计输入

**状态：Historical（全部材料均为历史设计输入，不构成当前设计基线）**

本目录保存 V3/V4、Reference、Merge & Diff 等前期材料，用于追溯设计来源、备选方案、反例和放弃理由。阅读时按 [AGENTS.md](../AGENTS.md) 第 13 节处理：吸收有价值洞见，但必须用当前证据重新验证，不得直接作为实现依据。

## 目录与当前关系

| 目录 | 内容 | 当前关系 |
| --- | --- | --- |
| `workingsetModel-V3/` | V3 领域模型、类图、Java demo、DDD 评审 | 历史；部分被 V4 与现行模型取代 |
| `workingsetModel-V4/` | V4 领域模型、用例、一致性、LRO、内容替换、实例化事件、产品空间投影、统一语言 | 历史；D-001/D-010 等已改写多数核心结论 |
| `workingsetReference/` | 领域范围、模型、交互、事件驱动修复 | 历史；引用/关系与修复的部分候选仍待 T7-E 验证 |
| `merge&diff/` | Merge 领域设计 v2.21 | 历史；执行模型已由 T1/ADR-0005/T6-E 收敛 |

## V4 关键概念的现行去向

| V4 概念 | 现行处理 |
| --- | --- |
| VIRTUAL／INSTANTIATED | 改为 Source-backed／Local-backed（`materialized`） |
| Manifest、ItemVariant | D-010 不采用；由 Working Set 直接维护绑定 + LightTrackerItem 承担身份/来源 |
| Tracker Branch | D-001 停用；Tracker 独立聚合 |
| WorkingSet Content／contentRevision | 不作为当前范围版本；Compare 的 `contentRevision` 是有效内容版次，不是范围版本 |
| 内容替换写入屏障 | R17 改为准备期可编辑、切换可覆盖 |
| 任务 retry/cancel | T7 不开放任务重试；取消/补齐仍待 `QUESTION-017` |
| Snapshot 聚合 | 仅保留派生时间基准与 `requirement_snapshots` 证据；未建 Snapshot 聚合 |

## 当前权威入口

- [BASELINE](../docs/working-set/BASELINE.md)
- [统一领域语言](../docs/working-set/domain/glossary.md)
- [事实与政策基线](../docs/working-set/domain/facts-policy-baseline-v1.md)
- [聚合边界](../docs/working-set/domain/aggregate-model.md)
- [开放问题](../docs/working-set/domain/open-questions.md)

## 已知问题

- 本目录内部分相对链接指向已移动或重命名的旧文件（`TC-006`），不影响语义；需要引用时以当前文档中的 Git 路径为准。
- 不得为了“看起来一致”而改写或删除本目录的历史材料；如采用其中候选，先在当前文档中建立新的确认记录并标注来源。
