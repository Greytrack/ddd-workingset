# Working Set 首版领域模型基线候选 v1

**状态：Historical（首版候选基线；作为 2026-11-10 接受依据保留，当前权威入口见 [BASELINE.md](../BASELINE.md)）**

- 版本：v1（基于 D-001～D-019）
- 日期：2026-11-10
- 确认人：用户（2026-11-10 接受「建议批准」清单整体为候选基线）
- 关系：[BASELINE.md](../BASELINE.md) 是权威索引；本文件为候选基线，未正式发布。

本文件把已完成整体验证的 D-001～D-019 收敛为可供批准的领域模型候选，供 Codex 后续实施引用。它不重新进行领域发现、不新增零散业务问题、不扩充聚合模型。

## 1. 范围与定位

- `AC-001`（不变）：Working Set 属企业 Shared Kernel，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。
- 本基线覆盖：三类核心聚合（Working Set、Tracker、LightTrackerItem）+ 业务 BC 聚合 + TrackerItem 公共契约。
- 明确不覆盖：Manifest、ItemVariant、历史谱系聚合、具体数据库/技术实现。

普通字段与附件/关系的读取、固定时点按 [T3 §2.1](lighttrackeritem-materialization-contract-v1.md) 的 2026-11-11 裁定区分；下文 D-016 的“不冻结内容”仅适用于普通字段。业务聚合实现 TrackerItem 与 Adapter 实现应用层 Port 并存，见 [T4 §3.1](cross-bc-contract-v1.md)。

## 2. 首版领域模型

| 聚合/契约 | 类型 | 身份 | 核心职责 |
| --- | --- | --- | --- |
| **Working Set** | 核心聚合 | WorkingSetId | 选择 Tracker 形成工作范围；派生时间基准；公共身份与工作集机制 |
| **Tracker** | 核心聚合（D-001 独立） | TrackerId | 独立身份/生命周期/管理行为；SharingMode；组织树 parentId；派生链 sourceId |
| **LightTrackerItem** | 核心聚合（D-008 独立） | LocalId（当前变体）+ CommonId（家族） | 工作集内身份、来源与有效变体定位权威；Source-backed/Local-backed 双态 |
| **TrackerItem** | Shared Kernel 接口契约（D-007） | 无（契约） | 各业务 BC 对象实现以获得公共工作集能力 |
| **业务 BC 聚合** | 各 BC 独立聚合 | 各 BC 自有 | Requirement/Part/Process 等的完整业务内容、状态机、不变量 |

三类核心聚合按 ID reference 关联，不组合、不跨 BC 事务。业务 BC 聚合实现 TrackerItem 接口，但 Working Set 不拥有其领域状态。

## 3. 聚合职责与不变量矩阵

| 聚合 | 生命周期/状态 | 保护的不变量 | 跨聚合关系 |
| --- | --- | --- | --- |
| Working Set | 工作范围（准备/发布/归档等维度仍候选） | 发布范围与成员一致（INV-011 候选）；单工作集 CommonId 唯一（POL-005） | ID 引用 Tracker、LightTrackerItem |
| Tracker | 独立生命周期；创建后组织位置不可变 | parentId 创建后不变（INV-022）；parentId/sourceId 双维分离（INV-018）；结构无环（INV-015 候选） | ID 引用；不拥有业务对象 |
| LightTrackerItem | Source-backed → Local-backed | COW 不改变 LocalId/CommonId（INV-040）；Source-backed 由 SourceLocalId+SourceRevision 解析（INV-041）；Local-backed 时 SourceLocalId=LocalId、SourceRevision=Revision（INV-042）；直接指向实际拥有内容的来源（INV-043）；已有下游不因其他变体重定向（INV-045）；自身 Revision 与 SourceRevision 分离（INV-051） | ID 引用业务对象；经 TrackerId/WorkingSetId 指向 |
| TrackerItem | 无 | 公共能力契约，不承载业务不变量 | 业务 BC 实现 |
| 业务 BC 聚合 | 各 BC 生命周期 | 各 BC 完整业务不变量 | 实现 TrackerItem |

**核心一致性约束（COW 相关，去重后）**：

- 首次快照优先：物化内容由首次相关来源修改前快照决定（INV-037）。
- 物化唯一性：不因事件重放/乱序/并发生成竞争物化结果（INV-038）。
- Local-backed 后不被来源事件覆盖（INV-039）。
- 同一实际来源修改保护所有直接内容依赖者、不限派生深度（INV-044）。
- COW 目标集合在事件 Handler 消费时确定（INV-050）。
- Revision 显式升级触发 COW（INV-052）。
- 派生不复制完整内容（INV-046）、不冻结 Revision 普通内容（INV-047）。

## 4. 领域主线

1. **Working Set 创建**：选择 Tracker 形成工作范围（FACT-001）。
2. **扩展与移除**：纳入新 Tracker/Item；Shared 解绑与非共享删除遵循 [T2 绑定与删除契约](binding-deletion-contract-v1.md) §4（POL-014 修订 POL-006；INV-064～068）。
3. **Tracker 派生**：非共享派生新 Tracker 身份；parentId 组织树、sourceId 派生链（D-002/D-003）；组织裁剪（D-004）、默认继承+创建前重组（D-005）、创建后不可变（D-006）。
4. **LightTrackerItem 派生**：选择时间基准 Snapshot/Head（D-015）；只固定业务 Revision、不复制内容（D-016）；SourceLocalId 直接指向实际拥有内容的来源（D-013）；已有下游不重定向（D-014）。
5. **Query**：Resolve 定位 LightTrackerItem，业务 BC 工厂重建完整聚合（D-008）；未物化时读来源 Revision 的当前内容（D-016）。
6. **COW**：来源修改前取不可变快照 → 保存 → 发事件 → Handler 消费时查询 `SourceLocalId/SourceRevision` 匹配且未物化的 LightTrackerItem（D-017）→ 据快照物化 → 有效来源切换到自身（D-009/D-010/D-011）。
7. **Revision 升级**：显式升级业务版次触发依赖变体 COW（D-018）；普通修改不升级 Revision（D-009）。
8. **Diff**：仅直接父子工作集双向比较（POL-004）。
9. **Merge**：字段级 ACCEPT/KEEP_TARGET/DEFER，目标 BC 整体校验并提交（FACT-007/POL-004）；决策优先于接收方变动。

图示：领域关系见 [契约与聚合图](../diagrams/trackeritem-contract.md)，生命周期/COW 时序见 [LightTrackerItem 生命周期](../diagrams/lighttrackeritem-lifecycle.md)，主线流程见 [领域主线图](../diagrams/domain-mainline.md)。

## 5. D-001～D-019 决策追溯表

| 决策 | 主题 | 状态 | 基线纳入 | 追溯 |
| --- | --- | --- | --- | --- |
| D-001 | Tracker 独立聚合 | 用户确认 | 是 | [ADR-0001](../adr/0001-tracker-independent-aggregate.md)、评审 #d-001 |
| D-002 | 非共享派生新 Tracker 身份 | 用户确认 | 是 | FACT-003、评审 #d-002 |
| D-003 | parentId/sourceId 双维关系 | 用户确认 | 是 | FACT-003、INV-014~018、评审 #d-003 |
| D-004 | 组织裁剪（parentId=null） | 用户确认 | 是 | POL-009、INV-019~021、评审 #d-004 |
| D-005 | 默认继承+创建前重组 | 用户确认 | 是 | POL-010、评审 #d-005 |
| D-006 | 创建后 parentId 不可变 | 用户确认 | 是 | POL-011、INV-022/023、评审 #d-006 |
| D-007 | TrackerItem 契约+业务 BC 聚合 | 用户确认 | 是 | FACT-012、CONCEPT-021、评审 #d-007 |
| D-008 | LightTrackerItem 独立聚合+COW 延迟物化 | 用户确认 | 是 | FACT-013、CONCEPT-022、评审 #d-008 |
| D-009 | 事件驱动 COW+普通修改不升级 Revision | 用户确认 | 是 | FACT-014、评审 #d-009 |
| D-010 | 最终一致+不可变快照+最小模型 | 用户确认 | 是 | FACT-015、INV-037~039、评审 #d-010 |
| D-011 | 首次快照优先 | 用户确认 | 是 | FACT-016、INV-037~039、评审 #d-011 |
| D-012 | SourceLocalId/SourceRevision 职责分离 | 用户确认 | 是 | FACT-017、INV-040~042、评审 #d-012 |
| D-013 | 直接有效来源定位+扇出 | 用户确认 | 是 | FACT-018、INV-043/044、评审 #d-013 |
| D-014 | 已有下游不重定向 | 用户确认 | 是 | FACT-019、INV-045、评审 #d-014 |
| D-015 | 派生时间基准 Snapshot/Head | 用户确认 | 是 | FACT-020、INV-046~048、评审 #d-015 |
| D-016 | 普通字段派生选定版次；原完成时间门槛由 D-017 替代 | 原裁定及后续替代 | 按 T3 §2.1、D-017 | FACT-021、INV-049（Superseded）、INV-050、评审 #d-016 |
| D-017 | COW 目标消费时确定 | 用户确认 | 是 | FACT-022、INV-050、评审 #d-017 |
| D-018 | Revision 字段+事件顺序+Revision 升级触发 COW | 用户确认 | 是 | FACT-023、INV-051/052、SCN-049、评审 #d-018 |
| D-019 | Diff/Merge 衔接：未物化参与 Diff；Merge 修改未物化目标构建聚合并 save；决策 VO 归 MergeRound；Checkpoint 独立；共享 Tracker 不参与 Merge | 用户确认 | 是 | FACT-027、INV-061~063、SCN-055/056、[阶段 4 评审](../reviews/2026-11-10-stage-4-productspace-classification-review.md) #7 |

## 6. 已归档的旧候选（保留历史，不删除论证）

| 旧候选 | 归档原因 | 替代 |
| --- | --- | --- |
| Tracker Branch | D-001 排除独立 Branch 根 | Tracker 独立聚合 |
| Item（通用聚合） | D-007 由 TrackerItem 契约取代 | TrackerItem + 业务 BC 聚合 |
| Item Variant | D-010/D-012 不引入 ItemVariant | LightTrackerItem |
| AP-002（Branch 根模型） | D-001 不兼容，保留历史 | Tracker 独立聚合 |
| AP-003（Manifest + ItemVariant） | D-010 不采用，保留历史 | 三类核心聚合最小模型 |
| Manifest | D-010 不引入 | Working Set 工作范围 |

## 7. 基线批准清单

**已批准（用户 2026-11-10 接受为候选基线；有 D-001～D-019 或已确认事实支撑、无反例）**：

- AC-001 Shared Kernel 定位。
- 三类核心聚合（Working Set / Tracker / LightTrackerItem）及其身份/生命周期。
- TrackerItem 公共契约 + 业务 BC 独立聚合。
- Tracker 双维关系、组织裁剪、创建后不可变。
- LightTrackerItem 身份/来源/Revision 字段与 Source-backed/Local-backed 双态。
- COW 事件驱动、最终一致、首次快照优先、消费时确定目标、Revision 升级触发 COW。
- 身份规则（CommonId 家族唯一、LocalId 变体、单工作集 CommonId 唯一）。
- 合并政策（直接父子双向、决策优先、BC 校验提交、KEEP_TARGET）。
- Diff/Merge 衔接（D-019）：未物化变体可参与 Diff；未物化目标被 Merge 修改时构建业务聚合正常 save；合并决策作为 VO 归属 MergeRound；MergeCheckpoint 独立聚合；共享 Tracker 不参与 Merge、非共享只允许直接父子准入。

**阶段 4 当时暂缓的事项及后续去向**：

- Diff/Merge 执行协议：行为已由 [T1](merge-execution-contract-v1.md) 与 ADR-0005 收敛；Token 编码、幂等键、乱序实现及 Checkpoint 字段映射仍属实施待办。
- Working Set 绑定归属：本文件 §9 与 ADR-0004 已确认两个 Binding 为内部 Entity；行为及属性契约见 [T2](binding-deletion-contract-v1.md)。
- LightTrackerItem 属性、物化标志与内容定位：见已冻结的 [T3](lighttrackeritem-materialization-contract-v1.md)。确认范围见 [BASELINE](../BASELINE.md)。

## 8. 实施阶段待验证技术前提

| 前提 | 说明 | 归属 |
| --- | --- | --- |
| 事件顺序 | 事件框架按顺序记录、顺序消费，保证首次快照优先 | 实施（用户已确认由事件框架兜底） |
| 幂等去重 | 重复事件不产生多个有效物化结果 | 实施 |
| 快照序列化/可靠交付/持久化 | 修改前不可变快照的传输与存储 | 实施（D-010 已确认事件关联快照） |
| 事件同步 Revision | LightTrackerItem Revision 与业务 BC TrackerItem Revision 通过事件同步 | 实施（D-018，细节不细化） |
| Diff/Merge 与 LightTrackerItem 衔接 | Merge 变体即 LightTrackerItem 定位变体、Revision 关系（业务规则已由 D-019 确认，字段级映射待契约层） | 契约层 |
| 历史读取/保留 | Source Cut 回读能力（FACT-006） | 实施 |

---

## 9. 阶段 4 补充：ProductSpace、Classification 与绑定

用户 2026-11-10 补充确认（已并入候选基线）：

- ProductSpace 是 Shared Kernel 独立聚合，只维护自身稳定归属身份，在默认 WorkingSet 维护生命周期、可被多个 WorkingSet 关联，不随 WorkingSet 派生复制/迁移。
- Classification 是独立共享元模型，Tracker 是其投影（三层、启动前初始化、常驻内存）。
- 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 只能在默认 WorkingSet 创建原始聚合；子 WorkingSet 只能纳入直接上游。
- **WorkingSet 聚合负责维护当前工作集中的空间与 Tracker 绑定结构**：SpaceBinding、TrackerBinding 是两个 Entity，归属于 WorkingSet 聚合；ProductSpace、Tracker 只维护自身归属身份，不持有绑定。SpaceBinding 是 TrackerBinding 前置。
- Shared Tracker 纳入子 WorkingSet 复用原 TrackerId；非共享 Tracker 纳入创建新聚合、直接 sourceId。

**删除/移除语义（已裁定）**：共享 Tracker 从子 WS 移除仅解除 TrackerBinding；非共享无 Item 且无派生子 Tracker → 删除并解除绑定；非共享有派生子 Tracker → 不允许删除；任意 Tracker 有 TrackerItem → 不允许移除；非共享有组织子节点但无派生子节点 → 不允许删除（校验提示含子节点）；移除空间内最后一个 Tracker → 不触发空间移除（SpaceBinding 保留）；移除 ProductSpace 时仍有 TrackerBinding → 抛异常；默认 WorkingSet 共享 Tracker 空且无子时可删除。

追溯：FACT-024～026、POL-012～015（修订 POL-006）、INV-053～060、SCN-050～054、[关系图](../diagrams/productspace-tracker-workingset.md)、[ADR-0004](../adr/0004-productspace-independent-aggregate.md)（Accepted，2026-11-10）。

**2026-11-11 注（T2 冻结）**：上述摘要中「任意 Tracker 有 TrackerItem → 不允许移除」只约束**聚合删除**，Shared 绑定的解除不受该约束（INV-064）；删除 ProductSpace 须审查**所有** WorkingSet 的 SpaceBinding（INV-066）；Tracker 删除还受组织子级、派生子级约束（K2/INV-065）。详见[绑定与删除契约 v1](binding-deletion-contract-v1.md)、INV-053～068、POL-016。

仍待（实现细节）：绑定持久化、并发控制与物理清理。软删/归档/恢复不属于 T2 本版范围，不再作为本版待定能力。

---

## 10. Diff/Merge 聚合定位（阶段 4 补充）

用户确认 Diff/Merge 衔接（FACT-027）：

- 未物化变体可参与 Diff；未物化目标被 Merge 修改时，在 merge 中构建业务聚合正常 save（物化与修改一体）。
- 合并决策（用户决策、command 入参）作为 ValueObject 归属 MergeRound 聚合。
- MergeCheckpoint 是独立聚合；MergeRound 是合并载体（LRO 记录参数、进度、成功/失败）。
- 共享 Tracker 不参与 Merge；非共享只允许直接父子准入。

聚合定位：MergeRound、MergeCheckpoint 属合并模型区域的聚合，**不是三类核心聚合之一**。三类核心聚合仍为 Working Set/Tracker/LightTrackerItem（+ ProductSpace 独立聚合）。Merge 与 LightTrackerItem 通过身份/Revision 衔接，不组合。

追溯：D-019、FACT-027、INV-061～063、SCN-055/056。

后续行为裁定见 [T1](merge-execution-contract-v1.md) 与 [ADR-0005](../adr/0005-merge-execution-model.md)；剩余实现待办见 §7。

---

## 11. 阶段 4 收尾与首版基线审核（2026-11-10）

阶段 4 的领域知识扩充到此结束，本文件不再追加新的业务裁定。首版基线审核结论见 [2026-11-10 首版基线审核](../reviews/2026-11-10-baseline-v1-audit.md)：**通过（附观察项）**——决策 D-001～D-019 追溯、身份字段、不变量与场景覆盖、目录索引与链接均通过；观察项为：D-019 无对应 ADR、`aggregate-model` 阶段 3 候选根表待收敛、图表渲染未验证、实现证据为零。

下一步进入阶段 5 契约层，首议题为 Diff/Merge 执行协议（`QUESTION-015`）；正式发布 / Git Commit / Tag 仍未授权。

---

**本文件为 Accepted 候选基线（用户 2026-11-10 接受；未正式发布，未 Commit/Tag）。批准/正式发布/Git Commit/Tag 分别授权。**
