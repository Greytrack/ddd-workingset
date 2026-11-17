# Working Set 业务不变量

**状态：Draft（混合登记；已接受子集以[事实与政策基线](facts-policy-baseline-v1.md)为准）**

本页只保留不变量的当前陈述、状态与验证入口，不重复裁定过程。状态含义：

- **Confirmed**：业务规则已由用户确认，可作为测试期望；
- **Confirmed / Draft formal**：业务子句已确认，形式化、保护者或事务方案仍候选；
- **Candidate**：完整陈述尚未获批；
- **Superseded**：已被后续裁定取代，保留历史编号。

## 核心不变量（INV-001～013）

| 编号 | 约束 | 状态与依据 | 验证 |
| --- | --- | --- | --- |
| `INV-001` | Shared 的所有绑定解析到同一权威 Tracker、同一 Item 变体和同一权威内容。 | Candidate；FACT-002；`POL-008` 的可见性要求已确认 | 跨 WS 并发写；列表/搜索读取面 |
| `INV-002` | 非共享派生后，目标本地修改不回写来源，来源变化不覆盖目标已提交本地事实。 | Candidate；FACT-003 | 来源写/目标写交错 |
| `INV-003` | Item 变体的家族身份、`LocalId` 和所属 Tracker 在查询、重试、物化时保持稳定。 | Candidate；FACT-004/005；T3 已兑现 `LocalId` 稳定 | 重试与 COW |
| `INV-004` | 未物化变体的固定来源在依赖存续期间可按 Source Cut 回读；历史缺失不得以当前值冒充。 | Candidate；FACT-006；`QUESTION-006` 保留期 | 历史、附件、关系、删除态 |
| `INV-005` | COW 只改变内容承载，不改变既有逻辑变体身份与直接来源。 | Candidate；D-012/D-013 | 并发首次写 |
| `INV-006` | Compare/Merge 的方向、范围与双侧证据固定；执行读取源最新值并另记实际证据。 | Candidate；FACT-007；R08/B09 | 反向与过期证据 |
| `INV-007` | 存在性差异必须显式裁决；一侧缺失不能自动推导 CREATE、RESTORE 或 DELETE。 | Candidate；FACT-007；T1 §7 | 来源删除、目标已编辑反例 |
| `INV-008` | `markAsMerged=true` 前所有适用差异必须明确为 ACCEPT 或 KEEP_TARGET；DEFER 不得伪装已处理。 | Candidate；FACT-008；T1 §7 | 四组合契约测试 |
| `INV-009` | 字段级决定应用后目标整体仍须通过其业务不变量；Merge 不能绕过目标能力写字段。 | Candidate；B06；T4 | 交叉字段反例 |
| `INV-010` | 同一幂等操作重试不重复分配身份或重复应用；旧确认不得覆盖更新的撤销/决定。 | Candidate；T1 CK-1/CK-2 | 重投和乱序 |
| `INV-011` | 发布的工作范围必须引用封闭、可读、身份确定的成员与必要关系；准备失败不能冒充发布成功。 | Candidate；FACT-010；T7 创建任务 | 半批失败、取消、重启 |
| `INV-012` | 删除不得破坏仍存活的派生变体、Snapshot、审计或有效关系；无本地行不能证明不存在。 | Candidate；FACT-006/009 | 删除与修复竞态 |
| `INV-013` | 有效 Reference 的逻辑身份不因端点暂不可解析而丢失；修复不能复活已删除或已换目标关系。 | Candidate；FACT-009；`QUESTION-010` | 事件重投、删除竞态 |

## Tracker 谱系与组织（INV-014～023）

| 编号 | 约束 | 状态与依据 | 验证 |
| --- | --- | --- | --- |
| `INV-014` | 有结构父时，父节点在同一 Working Set 的有效组织范围内。 | Confirmed / Draft formal；D-003 | SCN-014；Shared 引用范围另行核验 |
| `INV-015` | Tracker 结构关系不形成循环。 | Candidate；D-003/D-006 推导 | 自环、A.parent=B/B.parent=A 反例 |
| `INV-016` | 派生 Tracker 的直接来源位于直接父级 Working Set。 | Confirmed / Draft formal；D-003 | T1.source=T0，T2.source=T1 |
| `INV-017` | 派生来源不跳过中间 Working Set。 | Confirmed / Draft formal；D-003 | WS0→WS1→WS2 |
| `INV-018` | 结构父子与派生来源分开表达，不相互替代。 | Confirmed / Draft formal；D-003 | B1.parent=A1 且 B1.source=B0 |
| `INV-019` | 未选原结构父的非共享派生允许 `parentId=null` 成为目标根，不强制补派生该父。 | Confirmed / Draft formal；D-004 | SCN-015 |
| `INV-020` | 组织裁剪不改变直接派生来源语义；`parentId=null` 不推导 `sourceId=null` 或跨级来源。 | Confirmed / Draft formal；D-004 | B1.parent=null 且 B1.source=B |
| `INV-021` | 目标组织结构只需在目标有效范围内合法，不必与源树完整同构。 | Confirmed / Draft formal；D-004 | 父也选中时的映射 |
| `INV-022` | 创建完成后 `parentId` 保持不变。 | Confirmed；D-006 | SCN-017 |
| `INV-023` | 完成创建前必须确定符合当前范围组织规则的位置；允许的结构重组在此前完成。 | Confirmed；D-005/D-006 | SCN-016/018 |

## LightTrackerItem 身份与 COW（INV-024～052）

| 编号 | 约束 | 状态与依据 | 验证 |
| --- | --- | --- | --- |
| `INV-024` | 首次 COW 建立本地业务对象时，该对象与 LTI 的 `CommonId`/`LocalId` 一致。 | Confirmed 业务规则 / Draft formal；D-007、T3 | SCN-019 |
| `INV-025` | 业务对象修改成功但索引更新失败时，系统状态与可恢复结果一致，不伪造成功也不丢失已提交内容。 | Candidate；D-007、T3 双事实 | SCN-020 |
| `INV-026` | LTI 派生时具有明确的当前 `LocalId` 与 `CommonId`。 | Confirmed / Draft formal；D-008 | SCN-021 |
| `INV-027` | 派生变体的来源信息可追溯（`SourceLocalId`/`SourceRevision`）。 | Confirmed / Draft formal；D-008、D-012 | SCN-021/022 |
| `INV-028` | LTI 经 `TrackerId`/`WorkingSetId` 指向的 Tracker 与 WorkingSet 满足工作范围约束。 | Confirmed / Draft formal；D-008 | SCN-022 |
| `INV-029` | 未物化不代表业务变体不存在。 | Confirmed / Draft formal；D-008 | SCN-023 |
| `INV-030` | 业务对象内部不变量由业务 BC 负责，Working Set 不拥有或重建。 | Confirmed；D-007/D-008 | 跨 BC 合并/COW |
| `INV-031` | 同一 LTI 首次物化后仅一个有效业务对象身份。 | Candidate；D-008 | SCN-024 并发反例 |
| `INV-032` | LTI 与业务对象持久化之间的可观察一致性。 | Candidate；D-008、T3 | SCN-024 |
| `INV-033` | 物化完成后有效内容来源定位到自身 `LocalId` 及自身有效版次。 | Confirmed；D-009 | SCN-025 |
| `INV-034` | 来源修改前的完整业务事实可被物化 Handler 可靠取得。 | Confirmed 方案：事件关联不可变内容快照；D-010 | SCN-026 |
| `INV-035` | 已物化变体不被重复或错误覆盖。 | Candidate；D-009/D-011 | SCN-028 |
| `INV-036` | 派生历史来源与当前有效内容来源分别保持，物化切换不丢失来源追溯。 | **Superseded by D-012**：历史派生链不要求保存 | SCN-027（历史） |
| `INV-037` | 首次快照确定性：目标变体最终内容由首次相关来源变更之前的事实决定。 | Confirmed；D-011 | SCN-030 |
| `INV-038` | 物化结果唯一性：重放/乱序/并发不产生互相竞争的有效物化结果。 | Confirmed；D-011 | SCN-030/031 |
| `INV-039` | Local-backed 后来源后续事件不改变已物化内容。 | Confirmed；D-011 | SCN-032 |
| `INV-040` | COW 不改变 `LocalId`、`CommonId`。 | Confirmed；D-012 | SCN-035 |
| `INV-041` | Source-backed 时有效内容由 `SourceLocalId` + `SourceRevision` 解析。 | Confirmed；D-012 | SCN-035/036 |
| `INV-042` | Local-backed 时 `SourceLocalId = LocalId` 且 `SourceRevision = Revision`。 | Confirmed；D-012 | SCN-035 |
| `INV-043` | 未物化变体直接指向实际拥有完整业务内容的来源，不逐级递归。 | Confirmed；D-013 | SCN-037 |
| `INV-044` | 同一实际来源修改时保护所有直接内容依赖者，不限派生深度（扇出）。 | Confirmed；D-013 | SCN-038 |
| `INV-045` | 已有未物化变体不因其他变体物化而自动重定向有效来源。 | Confirmed；D-014 | SCN-039 |
| `INV-046` | 派生身份建立与完整业务内容复制解耦（派生不复制完整数据）。 | Confirmed；D-015 | SCN-043 |
| `INV-047` | 选定业务 Revision 后，派生不冻结该 Revision 的普通内容。 | Confirmed；D-016 | SCN-044 |
| `INV-048` | 来源修改事件目标发现除 `SourceLocalId` 相等外，还需限定 `SourceRevision`。 | Candidate；D-015 | SCN-044 |
| `INV-049` | 完整派生完成前的来源修改不为该变体触发 COW。 | **Superseded by D-017/INV-050** | SCN-045（历史） |
| `INV-050` | COW 目标集合在 Handler 消费时确定：当前 `SourceLocalId`/`SourceRevision` 匹配且未物化的 LTI 都参与。 | Confirmed；D-017 | SCN-048 |
| `INV-051` | LTI 持有自身 Revision 字段，与 `SourceRevision` 分离。 | Confirmed；D-018 | SCN-049 |
| `INV-052` | 显式升级业务 Revision 触发依赖变体 COW。 | Confirmed；D-018 | SCN-049 |

## ProductSpace、绑定与删除（INV-053～068）

| 编号 | 约束 | 状态与依据 | 验证 |
| --- | --- | --- | --- |
| `INV-053` | SpaceBinding 是 TrackerBinding 的业务前置。 | Confirmed；`POL-012` | SCN-050 |
| `INV-054` | Tracker 的 `productSpaceId` 必须等于其 TrackerBinding 的空间 ID。 | Confirmed；`POL-012` | SCN-050 |
| `INV-055` | 同一 WorkingSet 内 ProductSpace 绑定唯一。 | Confirmed；`POL-012` | SCN-050 |
| `INV-056` | 同一（工作集，空间，分类）可纳入多个 Tracker，有效范围内至多一个默认 Tracker。 | Confirmed；`POL-012`、[T6-F 默认资格裁定](../reviews/2026-11-16-t6-f-lineage-constraints.md#43-shared-默认与删除补位question-002k2026-11-17-用户确认) | SCN-050/051 |
| `INV-057` | Shared Tracker 在多个 WS 共享同一聚合身份；非共享在子 WS 拥有独立派生身份。 | Confirmed；`POL-013` | SCN-052 |
| `INV-058` | 删除/移除八条语义（见 `POL-014`）。 | Confirmed；`POL-014`；边界由 `INV-064` 澄清 | SCN-052/053 |
| `INV-059` | 默认 WS 不可删除/隐藏；Tracker `parentId` 创建后不可变。 | Confirmed；FACT-026、`INV-022` | SCN-054 |
| `INV-060` | SpaceBinding、TrackerBinding 是 WorkingSet 聚合内 Entity；ProductSpace/Tracker 不持有绑定。 | Confirmed；`POL-015` | SCN-050/052 |
| `INV-061` | 未物化变体可以参与 Diff。 | Confirmed；FACT-027 | SCN-055 |
| `INV-062` | 未物化目标被 Merge 修改时构建业务聚合正常 save（物化与修改一体）。 | Confirmed；FACT-027 | SCN-055 |
| `INV-063` | MergeRound 记录参数/进度/结果；决策归 MergeRound；MergeCheckpoint 独立。 | Confirmed；FACT-027 | SCN-056/059/060 |
| `INV-064` | “有 TrackerItem 不允许移除”只约束聚合删除；Shared 解绑不受该约束。 | Confirmed；T2 K1/K3 | SCN-052/061 |
| `INV-065` | 非共享 Tracker 解绑/删除必须同时满足：无 TrackerItem、无组织子级、无派生子级。 | Confirmed；T2 K2 | SCN-061/062 |
| `INV-066` | 删除 ProductSpace 前所有 WorkingSet 无指向它的 SpaceBinding，且空间下无 Tracker。 | Confirmed；T2 | SCN-064 |
| `INV-067` | 删除 WorkingSet 前全部 TrackerBinding/SpaceBinding 可合法解除；默认 WS 始终不可删。 | Confirmed；T2 K3 | SCN-063 |
| `INV-068` | 删除后不可恢复；软删/归档/恢复不属于 T2 本版；彻底删除不强制物理 DELETE。 | Confirmed；`POL-016` | SCN-061/065 |

## Merge 与物化（INV-069～071）

| 编号 | 约束 | 状态与依据 | 验证 |
| --- | --- | --- | --- |
| `INV-069` | LTI 持有独立 `materialized` 字段，与 `sourceLocalId`/`sourceRevision` 在同一次聚合状态变更中一致更新。 | Confirmed；T3 K1′ | SCN-066 |
| `INV-070` | `localId` 派生时预分配、首次物化复用、COW 前后不变；BC 必须能按稳定 `localId` 定位或创建变体。 | Confirmed；T3 K2′ | SCN-068 |
| `INV-071` | 物化完成 = 业务保存成功 **且** LTI 切换成功；保存成功而切换失败不回滚，幂等重试只补未完成步骤。 | Confirmed；T3 K3′ | SCN-067 |

## 当前判断

- 完整 INV 正式接受：**0**；这不代表已确认业务约束为零。已确认子句按上表状态和[事实与政策基线](facts-policy-baseline-v1.md)使用。
- `INV-001`～`013` 的完整形式仍候选；`INV-014`～`023` 的业务子句已确认；`INV-024`～`052` 多数业务子句已确认，但事务/保护方案仍候选；`INV-053`～`071` 多数已确认。
- `INV-036`、`INV-049` 已由后续裁定取代，不再作为当前验收依据；`INV-056` 已按 T6-F 重述为“同槽位多 Tracker + 有效范围内至多一个默认”，原“槽位唯一”不再适用。
- `TC-002` 的“单聚合事务”只是兑现上述语义的候选架构，不能反向证明聚合边界。
