# 首版领域模型基线 v1 审核记录

**状态：Reviewed；2026-11-10；结论：通过（附观察项）。不构成正式发布、Commit 或 Tag 授权。**

审核对象：[首版领域模型基线候选 v1](../domain/model-baseline-candidate-v1.md)（Accepted 候选）、[事实与政策主题基线 v1](../domain/facts-policy-baseline-v1.md)、ADR-0001～0004、FACT-001～027 / POL-001～015 / INV-001～063 / SCN-001～056 与 9 张领域图。

审核方式：静态一致性核对（文档全文、表格、编号连续性、相对链接、目录索引、状态标注、追溯链）+ 既有评审记录比对。**未做**业务方访谈、实现验证与 Mermaid 实际渲染；本记录不声称核验过未读取的材料。

## 1. 审核结论

**通过（附观察项）**。首版领域基线在「事实与政策主题 + 三类核心聚合（Working Set / Tracker / LightTrackerItem）+ ProductSpace 独立聚合 + Merge 模型聚合定位」范围内自洽、可追溯，未发现阻塞性矛盾与未授权状态提升。可以结束阶段 4 的领域知识扩充并转入阶段 5 契约层设计。

正式发布、Git Commit 与 Tag 未授权；实现（代码/测试）未开始，符合当前阶段。

## 2. 检查项与结果

| 检查项 | 方法 | 结果 |
| --- | --- | --- |
| 决策可追溯 D-001～D-019 | 候选基线第 5 节追溯表 ↔ 各评审记录 | 19 条全部闭合，D-019 关联 FACT-027、INV-061～063、SCN-055/056 |
| 事实/政策编号 | 全文编号提取 | FACT-001～027、POL-001～015 连续无缺口 |
| 不变量编号与状态 | invariants.md 全表 | INV-001～063 连续；INV-053～063 标「用户确认」，INV-001～052 保持 Candidate，INV-049 标 Superseded 且有 INV-050 替代说明 |
| 场景追溯 | 46 个 `## SCN-` 标题 + 56 个编号 | 每个场景均回链规则编号；SCN-050～056 覆盖阶段 4 |
| 身份字段一致性 | glossary / invariants / 候选基线三处对照 | CommonId、LocalId、Revision、SourceLocalId、SourceRevision 语义一致（D-012/D-018 收敛后） |
| 聚合边界 | 候选基线第 2/10 节 ↔ aggregate-model | 三类核心聚合 + ProductSpace 独立聚合 + MergeRound 载体 / MergeCheckpoint 独立；Merge 不属核心聚合，与 LightTrackerItem 仅 ID 引用 |
| ADR 覆盖 | ADR-0001～0004 ↔ D-001～D-019 | 0001→D-001；0002→D-007/008/012；0003→D-009～018；0004→阶段 4 ProductSpace/绑定。D-019 无 ADR（见 F2） |
| 未决项登记 | open-questions.md + 候选基线「暂缓批准」 | QUESTION-001～015 登记；D-019 执行协议已登记为 QUESTION-015 |
| 文档状态标注 | 45 份文档文首扫描 | 全部有状态，无缺失 |
| 目录索引覆盖 | 7 个目录 README 比对 | 0 缺口 |
| 相对链接 | 全库 Markdown 扫描 | 46 份全部可解析 |
| 图表 | 静态检查 | 11 图 39 个 mermaid 块、fence 配对、类型均为通用语法；实际渲染未执行 |
| 实现证据 | implementation/ | 空；无验收测试或运行证据，与「设计先行」阶段一致 |

## 3. 发现项

| 编号 | 严重度 | 内容 | 处理 |
| --- | --- | --- | --- |
| F1 | 已修 | glossary 缺 `MergeRound` 术语，而 D-019 已确认其为载体聚合 | 本轮已在 [glossary](../domain/glossary.md) 新增「Diff/Merge 聚合定位（D-019）」小节 |
| F2 | 待阶段 5 | D-019（Merge 聚合定位）无对应 ADR | 建议在阶段 5 B1 决定是否新建 ADR-0005；不得由 AI 自行标记 Accepted |
| F3 | 待阶段 5 | [aggregate-model](../domain/aggregate-model.md) 阶段 3 候选根表仍列「Merge Session 根候选／Checkpoint 独立根」，与 D-019 已确认的 MergeRound 载体 + MergeCheckpoint 独立聚合并存 | 该表为阶段 3 历史候选（已在阶段 4 补充节标注）；阶段 5 契约层需显式收敛或在表中加状态行 |
| F4 | 未验证 | Mermaid 实际渲染未执行；实现与验收测试为零 | 已在 [diagrams/README](../diagrams/README.md) 记录；发布前须在可渲染环境复核 |
| F5 | 已登记 | Diff/Merge 执行协议（差异冻结/比较令牌、幂等键、乱序确认、Checkpoint 字段映射） | QUESTION-015（P0，阶段 5 B1） |

## 4. 明确不在本基线内的部分

- Working Set 聚合内部结构细节；SpaceBinding/TrackerBinding 内部结构与软删、彻底删除、级联实现。
- LightTrackerItem 完整属性、物化标志、内容指针、LocalId 预分配与跨 BC 首次物化一致性。
- Diff/Merge 执行协议（F5）与 MergeCheckpoint「业务 Revision」字段映射。
- INV 的完整形式化与强一致范围（INV-001～052 仍 Candidate）；四 BC 公共契约版本治理（QUESTION-014）。
- 图表的渲染与正式接受；实现 seam 与验收测试证据。

## 5. 下一步

1. 阶段 4 领域知识扩充到此结束（见 [阶段 4 集中评审](2026-11-10-stage-4-productspace-classification-review.md) 的收尾结论）。
2. 转入阶段 5：契约层，首议题为 B1 Merge 执行协议（QUESTION-015），产物为方案比较 + SCN-057 起扩展场景 + 契约草案 + 是否新建 ADR-0005 的裁定请求。
3. 正式发布 / Commit / Tag 仍需用户单独授权。
