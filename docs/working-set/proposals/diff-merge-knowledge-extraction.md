# Diff / Merge 知识提取（来自 pre-design 历史输入）

**状态：Draft / Candidate（探索层知识提炼；不构成正式基线）**

- 来源：`pre-design/merge&diff/09-merge-domain-design.md`（v2.21）、`pre-design/workingsetModel-V4/00～10`。
- 依据：AGENTS.md 第 13 节（`pre-design/` 为历史设计输入，需吸收和挑战，不是已确认基线）；当前 D-001～D-019 与阶段 4 补充。
- 目的：提炼可复用的 Diff/Merge 设计知识，标注与当前基线的映射、适配与冲突，为契约层及「Diff/Merge 与 LightTrackerItem 衔接」提供输入。**不自动升级为 Accepted，不修改业务代码。**

## 1. 可复用知识（与当前基线一致或可延续）

### 1.1 业务本质

- Tracker 承载记录集合（TrackerItem）；比对记录存在性与属性值，叠加用户决定，执行记录增减或属性值修改，并确认本轮差异是否已处理。记录就是 TrackerItem，不新增 Record 业务实体。

### 1.2 定向视角

- source / target 是本轮角色，不固定父/子；A→B 与 B→A 均可，切换方向即交换角色。
- 不推断历史变化来源：差异是当前方向下的比较表达，不要求来源曾修改。
- 派生关系（Tracker 的 sourceId）与本次 source/target 角色分开表达。

### 1.3 准入边界（与 POL-004 一致）

- 仅相邻父子 WorkingSet 中直接对应的 UNSHARED Tracker；子 Tracker.sourceId = 父 Tracker.id；禁止跨级、兄弟或不对应 Tracker。
- 共享 Tracker 不参与 Merge，不通过 Merge 同步。

### 1.4 数据决定与处理确认分离（与 R08–R11 一致）

- 4 种组合：ACCEPT / KEEP_TARGET × markAsMerged true/false。
- KEEP_TARGET + markAsMerged=true：目标不变，确认本轮已处理；MergeCheckpoint 抑制后续同版次差异。
- DEFER / 未选择：不得 markAsMerged=true；明确保留并确认须 KEEP_TARGET + true。
- 数据决定、ApplyResult、已处理 Checkpoint 是不同事实。

### 1.5 存在性 / 生命周期差异

- recordAction：CREATE / RESTORE / DELETE。
- 一侧不存在不能自动推导 CREATE/RESTORE/DELETE；必须显式选择。
- 删除是显式选择的存在性操作，不因来源删除自动删除目标。

### 1.6 事务与一致性（与当前一致）

- 单聚合事务；业务内容、索引、Round、Checkpoint 各自提交；异步事件收敛。
- 部分成功不伪装整轮回滚；已成功项即使源再变化也跳过重试。
- Round 终态与唯一 ApplyResult；终态不重开，新决定创建新轮次。

### 1.7 比较证据与令牌

- 比较冻结方向、双侧内容 / 存在性证据；ComparisonToken 关联服务端可信快照。
- 执行时校验方向、身份、版本、权限；过期或失配须重新比较，不信任前端内容，不把查询对象当写指令。

### 1.8 MergeCheckpoint

- 有向键（sourceWs, sourceTracker, targetWs, targetTracker, commonId）。
- markedAsMerged + ConfirmationBasis（双侧提交时证据）；true 保存最终依据；false 清空当前确认。
- CAS 防旧 true 覆盖新 false；反向合并不复用原方向确认。

### 1.9 幂等与并发（实现相关，非领域不变量）

- 幂等键（mergeRoundId, commonId, actionVersion）；重复事件沿用 eventId；旧确认不得覆盖更新的撤销。
- 这些属执行协议，不写成领域不变量。

## 2. 需适配的概念（旧 → 新映射）

| 旧术语 / 设计 | 当前基线 | 适配说明 |
| --- | --- | --- |
| TrackerItem 继承 BusinessObject | TrackerItem 是 Shared Kernel 顶级接口（D-007） | 当前不强制 BusinessObject 继承；delete/undelete/delete2 是否进入公共契约待定 |
| LightTrackerItem 私有聚合 | LightTrackerItem 独立聚合（D-008） | 一致；当前进一步明确身份/来源/Revision/COW |
| VIRTUAL / INSTANTIATED | Source-backed / Local-backed（D-010） | 术语映射；Materializing 是技术状态 |
| sourceVersion / targetVersion | SourceRevision / Revision（D-012/D-018） | MergeCheckpoint 双侧版次应对齐当前业务 Revision，而非 SourceRevision（来源内容版次） |
| SourceCut（比较前固定切点） | Source Cut = 物化固定依据（D-018）；SourceRevision = 未物化来源引用 | Merge 冻结证据与物化快照语义需对齐 |
| LightTrackerItem.parentLocalId（直接父变体） | 历史派生关系不要求保存（D-018） | parentLocalId 不再要求由 LightTrackerItem 保存 |
| MergeRound 作为聚合根 | 三类核心聚合不含 MergeRound；Merge 是独立模型区域候选（阶段 3） | 不放入三类核心聚合，作为候选独立模型区域 |
| ComparisonToken / ApplyLease / fencing | 候选技术协议 | 不进入领域不变量，留实施 |
| recordAction CREATE/RESTORE/DELETE | 与当前合并政策一致 | 保留 |

## 3. 不采用 / 过时的部分（与当前基线冲突或已被排除）

- Manifest / ItemVariant / 独立 Tracker Branch：D-001/D-010 已排除，不采用。
- 「TrackerItem 继承 BusinessObject 并持有索引 / 代理」：D-007 明确业务对象不持有 LightTrackerItem，不声明代理。
- 把 MergeRound 设为与 Working Set/Tracker/LightTrackerItem 并列的核心聚合：当前最小模型不含；Merge 为独立模型区域候选。
- 具体技术实现（框架事务、Outbox/Saga、数据库表、消息基础设施）：不进入领域基线，留实施。
- 已被 D-001～D-019 与阶段 4 修订的旧规则（如 Tracker 移除/删除、历史谱系保存要求）：以当前为准。

## 4. 对「Diff/Merge 与 LightTrackerItem 衔接」的启发

这是当前整体验证的「缺失 4」。旧设计提供以下可复用线索：

1. **Merge 的操作对象**：旧设计以 TrackerItem（CommonId/LocalId）为记录；当前 LightTrackerItem 持有 LocalId/CommonId/Revision。Merge 的「变体」应是 LightTrackerItem 定位的变体；字段级决定作用于业务 BC 聚合（BC 校验提交），SK 记录决定与结果。
2. **Revision 关系**：MergeCheckpoint 的「双侧版次」应对齐当前业务 Revision（显式升级版次），而非 SourceRevision（当前有效内容来源版次）。KEEP_TARGET 的「版次不变则隐藏」沿用 R11。
3. **COW 与 Merge 的边界**：Merge 是用户主动比对/合并（POL-004）；COW 是来源变更触发的自动物化。两者都是有向操作，但触发与目标不同；Merge 不应绕过物化状态，目标应是可读的业务变体（已物化或按 Query 重建）。
4. **证据时点区分**：Merge 的冻结证据（比较前双侧内容）与 COW 的修改前快照（BeforeSnapshot）是不同时点证据，不应混用。

## 5. 已裁定（2026-11-10 用户确认，FACT-027）

- **未物化变体可参与 Diff**：不要求已物化。
- **未物化目标被 Merge 修改**：在 merge 过程中构建业务聚合正常 save（物化与修改一体）。
- **合并决策**：是用户决策、command 入参，可作为 ValueObject 归属 MergeRound 聚合。
- **MergeCheckpoint**：独立聚合。
- **MergeRound**：合并载体（LRO 发起后记录参数、进度、成功/失败信息）。
- 共享 Tracker 不参与 Merge；非共享只允许直接父子准入（已确认）。

仍待（执行协议，留实施）：差异冻结/比较令牌、幂等键、乱序确认、MergeCheckpoint 的「业务 Revision」与 SourceRevision/Revision 的精确字段映射。

## 6. 来源与证据边界

- 本文件是历史设计输入的知识提炼，**不是** D-001～D-019 之外的新的用户裁定，也**不是** Accepted 基线。
- 可复用知识需经用户确认后才能进入正式设计或 ADR；本文件不改变当前基线范围。
