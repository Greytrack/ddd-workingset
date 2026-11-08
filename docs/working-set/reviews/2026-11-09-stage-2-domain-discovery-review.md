# 阶段 2：Working Set 领域发现审查

- **状态**：Draft
- **日期**：2026-11-09
- **范围**：候选知识提取、证据分类、战略与聚合方案比较；不批准领域模型或 ADR

## 结论摘要

阶段 2 已把散落在外部报告和 `pre-design/` 的主张整理为可追溯候选知识，但没有发现可证明已经由业务方确认的领域事实或不变量。当前适合进入“问题确认与反例验证”，不适合直接实施任一聚合模型。

## 实际读取资料

1. 仓库治理：`AGENTS.md`、根 `README.md`、`docs/working-set/README.md`、`BASELINE.md`、domain/adr/proposals 现有说明与模板。
2. 外部报告：`working-set-ddd-domain-architecture-analysis.md`，完整分段读取并校验 SHA-256，作为 `EXT-ARCH` 候选来源。
3. `pre-design/`：建立全部 28 个 Markdown 文件清单；重点审阅 V4 overview、domain、consistency、creation LRT、instantiation、review outline、UL，Merge 专项的术语/决定/不变量/风险/验收，以及 Reference 专项的范围、模型、协作、修复和术语。其余 V3/V4 文件通过标题与相关声明检索纳入来源定位，未声称逐字接受。
4. 方法资料：实际读取 `domain-modeling`、`ubiquitous-language`、`domain-driven-design`、`ddd-refiner`、`design-first`、`tdd` 的 `SKILL.md`，并读取适用的 CONTEXT/ADR format、DDD defaults、design methodology detail、TDD tests/mocking。
5. 本地 DDD 资料库仅检查了文件目录；本轮没有引用其章节作为业务证据，避免用理论替代业务确认。

## 提取结果

- `Business Fact`：10 条，全部 `Reported / Pending Confirmation`，Confirmed 为 0。
- `Business Policy`：8 条，全部待裁定。
- `Hypothesis`：8 条，均为 Stage 2 inference。
- `Technical Constraint`：6 条，均不升级为业务事实。
- `Domain Concept`：18 个候选术语。
- `Business Invariant`：13 条候选，Confirmed 为 0。
- `Open Question`：12 个，其中 6 个 P0。
- `Architecture Proposal`：3 套主要聚合方案，另保留不可变 ContentVersion + Overlay 作为长期基准。

## 事实、领域与实现混淆

1. **默认根 ID=1**：V4 写成强规则；更合理分类是部署／引导约定 `TC-001`。业务问题是是否存在不可由普通用例删除的默认治理根。
2. **单聚合事务**：V4 写成普遍规约；它是 `TC-002` 架构选择，必须由可见性、恢复和补偿需求证明。
3. **LightTrackerItem**：名称像技术索引，但又承载逻辑存在、稳定身份、固定来源和物化状态。若这些是业务事实，它可能是 Item Variant 根；否则不应提升为聚合。
4. **Tracker 是分类投影还是业务谱系节点**：两种定义混在一起，直接影响 `CTX-002` 是否成立。
5. **全 Working Set CommonId 唯一**：V4 当作规则，但缺少跨类型、产品空间和 namespace 的业务证据。

## 主要冲突

| 冲突 | 历史主张 | 挑战证据 | 当前处理 |
| --- | --- | --- | --- |
| 默认根身份 | 物理 ID 固定 1 | 报告认为只是编码约定 | `POL-001` 与 `TC-001` 分离 |
| Tracker 创建 | 仅默认根可创建 | 子工作集新建 Item/Tracker 的业务能力未裁定 | `POL-002`、`QUESTION-002/005` |
| Sharing Mode | 永久不可变 | 可通过新 Tracker + 迁移表达变更 | `POL-003` 待裁定 |
| Item 唯一性 | 工作集内裸 CommonId 全局唯一 | 跨类型／空间可能合法重号 | `POL-005`、`HYP-004` |
| Merge 范围 | 仅相邻父子 | 未来兄弟／跨代需共同基线 | `POL-004`、`QUESTION-007` |
| 事务边界 | 每次只改一个聚合 | 业务只规定原子可见结果，不规定技术事务数 | `TC-002`，不作为不变量 |
| SHARED 即时性 | 容易被理解为所有读取零延迟 | 权威点查与投影 SLA 可不同 | `POL-008` |

## 当前不适合直接接受的设计

- Working Set、Tracker/Tracker Branch、ItemVariant 的最终聚合边界。
- 报告推荐的 Manifest + Tracker Lineage + ItemVariant 方案。
- History/Snapshot、Reference、Compare & Merge 的最终上下文拆分。
- CommonId/LocalId 的作用域、原生标量相等和跨聚合唯一性机制。
- 仅直接父子 Merge、Sharing Mode 永久、非空 UNSHARED 解绑等政策。

## 高风险问题

1. 历史内容、附件、关系、生命周期和墓碑若不能按切点读取，COW/Snapshot/Merge 共同失效。
2. SHARED 新成员可能在多个 Working Set 触发 FamilyKey 冲突，事后事件无法兑现强唯一。
3. ItemVariant 与 Business Item 双事实若无清晰权威协议，会出现删除后复活或旧来源覆盖本地写。
4. 字段级 Merge 可组合出整体非法状态，必须回到目标业务能力验证。
5. 巨量 Binding 可能让 Working Set 单聚合成为热点，但当前没有规模数据。
6. `pre-design/` 中存在 93 个失效相对链接（15 个文件），降低历史证据导航可靠性；本阶段未改写历史材料。

## 推荐后续确认顺序

1. Working Set 身份与用户能力；
2. Tracker／Tracker Branch 身份和生命周期；
3. SHARED 权威、权限与 SLA；
4. Item Family/Variant/revision/version 的身份体系；
5. Source Cut 历史合同；
6. Merge 证据、决定有效期和目标合法性；
7. 删除、关系与规模政策。

## 基线影响

本阶段只形成候选知识索引。`BASELINE.md` 继续保持 `Draft / Unreleased`，当前已确认领域文档、Accepted ADR 和正式验收标准仍均为无。
