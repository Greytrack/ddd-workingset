# T4 评审记录：跨 BC 公共契约 v1（冻结）

**状态：Reviewed（2026-11-11）；结论：T4 契约冻结。**

审核对象：[跨 BC 公共契约 v1](../domain/cross-bc-contract-v1.md)（Draft → Reviewed）、[跨 BC 契约面与失败语义](../diagrams/cross-bc-contract.md)、SCN-069～071、[Shared Kernel 边界](../domain/shared-kernel-boundary.md)（一致性调整）。

审核方式：逐项核对用户 2026-11-11 裁定（K1″ 方案 A、K2″ 九分类、K3″ Open）、架构前提变更、与 T1～T3 的一致性、Port/Adapter 依赖方向、九分类在六个用例上的适用范围。**未做**实现验证与 Mermaid 实际渲染。

## 1. 架构前提落地

| 要求 | 落地 |
| --- | --- |
| 新 DDD 架构；`biz / sdk / sdkImpl` 为历史实现 | 契约 §0 明确；全库检索确认仅此一处提及，且仅作为「历史、不作默认前提」说明 |
| 先定 BC / Aggregate / Application Service / Port-Adapter 责任与依赖，再定物理模块 | §0 与 §3 以职责与依赖方向表达，不涉及模块拓扑 |
| 不提前固定 Maven 模块、包名、Spring Bean | §0 明确；契约「不决定」列表包含 |

## 2. K1″ 落地（方案 A：只读公共契约 + BC 自主管理写入）

| 裁定要点 | 落地位置 |
| --- | --- |
| SK 定义 TrackerItem 公共**读取与能力**契约（身份、Revision、内容解析、能力声明、授权事实） | §3.1 `TrackerItemReadPort` 五项 |
| 聚合创建、物化、修改、删除、整体校验、提交由所属 BC 执行 | §3.3 五项 BC 职责 |
| SK 可协调 COW/Merge，但不能绕过聚合、领域规则或 Repository 执行通用 Save | §3 开头 + §3.3 |
| SK 应用层可定义 Outbound Port，BC 通过 Adapter 提供；SPI 特征但不强制 ServiceLoader | §3.2 |
| **分开** `TrackerItemReadPort` 与写入协作 Port，不能合并为通用 CRUD 接口 | §3.1/§3.2 分节；图 §1 |
| BC 应用服务保留写入决定权（聚合、不变量、持久化、领域事件） | §3.3 |
| 不要求所有 BC 实现全部能力，按能力声明控制协作契约 | §3.4 |
| 只通过稳定身份与必要公共数据跨 BC 传递，不共享完整可变聚合 | §3.1（只读）+ §3.3（内容由 BC 持有） |
| 不新增聚合、不改变 T1/T3 行为 | §7、§8 |

**修正项**：原「五项必需能力」把「整体校验、提交」列为 TrackerItem 能力——已改为**业务 BC 职责**（§3.3），`TrackerItemReadPort` 明确不含这些方法。

## 3. K2″ 落地（九分类公共失败语义）

| 裁定要点 | 落地位置 |
| --- | --- |
| 沿用既有九分类术语，不新造名称 | §5.1（NotFound/NotIncluded/Denied/Deleted/HistoryUnavailable/Preparing/CapabilityNotSupported/DependencyUnavailable/Unknown） |
| 九分类是跨 BC 统一语义，不是九状态生命周期 | §5.1 定位说明；图 §3 |
| 每个用例只暴露适用分类，不要求统一返回九类 | §5.2 六用例 × 九分类矩阵 |
| `NotIncluded` 不得降级为 `NotFound` | §5.1 禁止列 |
| `CapabilityNotSupported` 不得解释为空差异/无记录 | §5.1、§3.4 |
| `Unknown` 不得解释为确定未执行，须按幂等协议恢复 | §5.1 |
| 保留 T1 的 `StaleEvidence`/`Rejected` 等执行结果 | §5.3 |
| 保留 T3 双事实；切换失败不得解释为 BC Save 失败 | §5.3 |
| 审查各用例适用范围，避免重复设计异常状态机 | §5.2 矩阵 + §5.1「不是异常类层次」 |

## 4. 一致性检查

| 检查项 | 结果 |
| --- | --- |
| K1″/K2″ 均标注用户已确认 | 通过（§9 裁定结果表、SCN-069/070、图 §5） |
| K3″ 保持 Open | 通过（§6、§9、`QUESTION-014`、SCN-071、图 §6） |
| SK 与 BC 写入职责不重叠 | 通过：SK 只持范围/身份/证据/协调/结果；BC 持聚合/校验/持久化/事件 |
| Port/Adapter 依赖方向明确 | 通过：SK → 两个 Port；BC Adapter 实现；SK 不依赖 BC 内部聚合 |
| 不再引用 biz/sdk/sdkImpl 为新架构结构 | 通过（仅作为历史说明出现一次） |
| T1～T3 规则不变 | 通过：§7 与 §5.3 明确保留；未修改 T1/T2/T3 契约 |
| 文档、场景、图一致 | 通过：九分类、矩阵、Port 结构三处口径一致 |
| Mermaid 渲染 | **未验证**（无 `mmdc`）；静态检查通过（5 块、fence 配对、通用语法） |
| 实现证据 | 无（`implementation/` 为空），符合契约层阶段 |

## 5. 遗留

- **K3″（版本治理）**：团队开放项，待 GateFlow、ALM、DQA、PLM 四团队共同评审；不作为 T5 阻塞。讨论稿见[契约图 §6](../diagrams/cross-bc-contract.md)。
- **`extend` 的补偿细节**：拒绝后的部分成功与补偿协议未定，属实现/后续契约。
- **通用差异模型覆盖面**：各 BC 字段语义与不可比较字段的完整清单待各 BC 提供。
- **依赖服务时效**：Privilege/Workflow/Class Hierarchy 的缓存与撤权时效待确认。
- **Mermaid 渲染**：发布前须在可渲染环境复核。

## 6. 冻结结论与下一步

T4 达成验收条件：K1″/K2″ 用户确认；K3″ 团队开放项；写入职责不重叠；Port/Adapter 依赖方向明确；不引用旧模块结构；T1～T3 不变；核心契约可冻结。

[跨 BC 公共契约 v1](../domain/cross-bc-contract-v1.md) 冻结为 `Reviewed`（未纳入正式基线、未发布、未提交）。下一步进入 **T5 最小纵向切片**：建议首切片为「非共享派生 → 首次 COW → Query 解析」，验证 `materialized` 三字段切换、`localId` 复用与九分类读取失败语义。
