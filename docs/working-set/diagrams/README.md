# Working Set 领域设计图表

**状态：Draft（目录治理规则）**

本目录保存可由 Git diff 审查的 Mermaid 源图。图表是领域文档的可视化视角，不是独立事实来源；当图与文字冲突时，应按 `AGENTS.md` 的证据和基线规则处理并修复不一致。

## 当前图表

- [domain-concept-relationships.md](domain-concept-relationships.md)：阶段 2 候选概念关系图；
- [candidate-context-map.md](candidate-context-map.md)：Working Set 内部候选 Context Map；
- [shared-kernel-context-map.md](shared-kernel-context-map.md)：`AC-001` 企业 Shared Kernel 与四个业务 BC 外框。

前两张图为 `Draft / Candidate`。Shared Kernel 外框图为 `Reviewed`，其中 `AC-001` 约束已 Confirmed，但它不是 Accepted 内部领域模型，也未发布为正式设计基线。

## 渐进交付清单

| 设计阶段／触发条件 | 最小图表 | 当前状态 |
| --- | --- | --- |
| 领域发现出现多个核心概念及歧义 | 概念关系图 | 已创建 Candidate |
| 出现多个候选模型所有者和跨边界协作 | 候选 Context Map | 已创建 Candidate |
| 战略上下文获评审 | 正式 Context Map，含上下游和集成模式 | candidate-context-map 已创建（Candidate），正式 Context Map 仍待阶段 3 评审 |
| 某聚合方案进入正式比较 | 该方案的领域类图与聚合边界图 | [aggregate-candidates](aggregate-candidates.md) 已创建（Candidate）；D-001 已排除 AP-002 Branch 根，当前方案为 Tracker 独立聚合 |
| 生命周期或长流程进入契约评审 | 状态图／时序图／事件流程图 | 已创建 tracker-creation-lifecycle、tracker-dual-relations、lighttrackeritem-lifecycle、productspace-tracker-workingset、domain-mainline（均 Draft） |
| Shared、COW、Merge、并发设计拟接受 | 能解释行为、证据与一致性边界的专项图 | COW 与绑定专项图已创建；Merge 执行协议图待契约层（差异冻结/幂等/Checkpoint） |

## Mermaid 约定

1. 图表保存在 Markdown 的 `mermaid` fenced code block 中，不只保存 PNG。
2. 每个文件只承担一个清晰视角；复杂主题拆图，不用一张“全景图”塞入所有对象。
3. 节点使用稳定、语义化 ID；显示标签可以使用中英文领域术语。
4. 优先使用通用语法：`flowchart`、`classDiagram`、`stateDiagram-v2`、`sequenceDiagram`。
5. 类图必须用 stereotype 或图例标明 Aggregate Root、Entity、Value Object、Domain Service；跨聚合关系标注 `ID reference`。
6. 图外文字必须声明状态、来源、假设、相关文档和未表达范围。
7. 修改关联设计时同步更新图；无法同步时先把图标记为 `Superseded`，不得让旧图继续充当当前模型。
8. Accepted 图必须被 `BASELINE.md` 明确列出；Git 提交或 Mermaid 可渲染本身都不代表设计已接受。

- [三套候选类图与聚合边界](aggregate-candidates.md)：AP-001/002/003 正式比较视图，均 Candidate。
- [合并与共享/COW 行为](collaboration-behavior.md)：候选提交、回执与 Checkpoint 责任。

以上补齐本轮候选评审视图；此前渐进清单中的“待创建”是阶段 2 历史进度，当前三方案图已创建，尚待业务裁定及正式渲染检查。

- [Tracker 双维关系](tracker-dual-relations.md)：D-003 同工作集组织树与跨直接父子工作集派生链，Draft；替代旧 D-002 字段未定图。

- [D-004 派生组织裁剪图](tracker-dual-relations.md#d-004-clipping)：仅选 B，目标 B1 为根，直接来源仍为 B；不自动创建 A1。

- [trackeritem-contract.md](trackeritem-contract.md)：D-007/D-008 TrackerItem 契约与聚合图，Draft / Candidate。

- [D-008 LightTrackerItem 派生/解析/物化生命周期](lighttrackeritem-lifecycle.md)：派生创建 LightTrackerItem、COW 延迟物化、Query 工厂时序与首次 COW 并发反例，Draft / Candidate。

- [tracker-creation-lifecycle.md](tracker-creation-lifecycle.md)：Tracker 创建与组织位置冻结生命周期（D-005/D-006 语义），Draft。

- [domain-mainline.md](domain-mainline.md)：Working Set 领域从创建到合并的业务主线，Draft / Candidate（首版基线候选配套图）。

- [productspace-tracker-workingset.md](productspace-tracker-workingset.md)：ProductSpace／Tracker／Working Set 归属与绑定关系，Draft / Candidate（阶段 4 补充）。

- [merge-entry-lifecycle.md](merge-entry-lifecycle.md)：MergeEntry 从提交到确认的协作时序与状态机，Draft / Candidate（阶段 5 T1，配套 [Merge 执行行为契约](../domain/merge-execution-contract-v1.md)）。

- [binding-lifecycle.md](binding-lifecycle.md)：绑定移除、Tracker 删除、ProductSpace 删除、WorkingSet 删除四类操作的区分、拒绝条件与删除时序，Draft / Candidate（阶段 5 T2，配套 [绑定与删除契约](../domain/binding-deletion-contract-v1.md)；K1～K3 已确认）。

- [lighttrackeritem-materialization.md](lighttrackeritem-materialization.md)：`materialized` 状态字段、Source-backed / Local-backed 双态判定与首次物化（COW）三字段同步切换时序，Draft / Candidate（阶段 5 T3，配套 [物化协议契约](../domain/lighttrackeritem-materialization-contract-v1.md)；K1′～K3′ 已确认）。

- [cross-bc-contract.md](cross-bc-contract.md)：四个业务 BC 与 Shared Kernel 的契约面、能力标记与统一失败语义（含 resolve 决策路径），Draft / Candidate（阶段 5 T4，配套 [跨 BC 公共契约](../domain/cross-bc-contract-v1.md)）。

## 渲染检查状态（2026-11-10，2026-11-11 更新）

- **静态检查（脚本执行）**：15 个图文件、49 个 `mermaid` 代码块；fence 配对完整（每文件 ``` 数为偶数）；图类型均为 `flowchart`、`classDiagram`、`stateDiagram-v2`、`sequenceDiagram`（通用语法，无专有扩展）。
- **实际渲染：未执行**。当前环境无 `mmdc`；`npx @mermaid-js/mermaid-cli` 在 45 秒内无响应（疑似需联网下载 puppeteer）。按 `AGENTS.md` 4.1 第 6 条，图纳入正式基线与发布前须在可渲染环境完成渲染检查并回填结论；当前状态记为「静态检查通过 / 渲染未验证」，不得据此宣称图已通过渲染评审。
