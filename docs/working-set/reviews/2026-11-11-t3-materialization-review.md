# T3 评审记录：LightTrackerItem 物化协议契约 v1（冻结）

**状态：Reviewed（2026-11-11）；结论：T3 契约冻结。**

审核对象：[LightTrackerItem 物化协议契约 v1](../domain/lighttrackeritem-materialization-contract-v1.md)（Draft → Reviewed 冻结）、[物化状态与首次物化时序](../diagrams/lighttrackeritem-materialization.md)、SCN-066～068、INV-069～071。

审核方式：逐项核对用户 2026-11-11 最终裁定（K1′/K2′/K3′）、与 D-008～D-019 的一致性、旧候选的 Superseded 处理、文档/场景/图的一致性。**未做**实现验证与 Mermaid 实际渲染。

## 1. 最终裁定落地

| 编号 | 用户裁定 | 落地位置 |
| --- | --- | --- |
| K1′ | 新增独立 `materialized: boolean`（`false`=Source-backed、`true`=Local-backed）；与 `sourceLocalId`/`sourceRevision` 在同一次聚合状态变更中一致更新；外部不得绕过聚合行为修改；三字段不一致属元数据一致性缺陷，非第三态 | 契约 §3/§4/§9/§10；INV-069；SCN-066；图 §1 note 与 §3 易错点 |
| K2′ | 派生预分配全局唯一 LocalId；首次物化复用；COW 不变；不强制业务 BC 主键等同，但业务 BC 必须能按稳定 LocalId 定位或创建变体；不持久化内容指针、不持有完整业务字段 | 契约 §5/§9/§10；INV-070；SCN-068 |
| K3′ | 快照 → 保存成功 → 发事件；消费时确定 Source-backed 依赖者；保存成功后才切换；单个聚合事务内同步三字段；切换失败不回滚、幂等重试只补未完成步骤；保存失败不切换；Materializing 不进枚举 | 契约 §6/§7/§9/§10；INV-071；SCN-067；图 §2 时序 |

## 2. 旧候选的 Superseded 处理

| 旧候选 | 状态 | 说明 |
| --- | --- | --- |
| 「不设独立标志、仅由 `sourceLocalId == localId && sourceRevision == revision` 推导物化状态」（2026-11-11 T3 草案推荐） | **Superseded** | 保留于契约 §10 的历史表；契约 §4 明确 `sourceLocalId != localId` 只作一致性约束，不再作为权威状态定义；不存在两套并存的有效规则 |

## 3. 一致性检查

| 检查项 | 结果 |
| --- | --- |
| K1′/K2′/K3′ 均标记为用户已确认 | 通过（契约 §10、INV-069～071、SCN-066～068） |
| `materialized` 成为明确状态字段 | 通过（契约 §3 属性表、§4 权威判定、图 note） |
| 不存在第三种 Materializing 业务状态 | 通过（§4 与 §7、INV-071、图 §1 注记） |
| LocalId 在派生与物化前后稳定 | 通过（§5、INV-070、SCN-068） |
| 业务保存与切换的最终一致性规则明确 | 通过（§6 步骤 3、§7 失败矩阵、INV-071、SCN-067） |
| COW / Merge / Query 对物化状态解释一致 | 通过（§4 判定、§8 Merge 不直接写字段、图 §2 与 §3） |
| 文档 / SCN-066～068 / Mermaid 图一致 | 通过（三者均以 `materialized` + 三字段同步为口径） |
| 与 D-008～D-019、ADR-0002/0003 冲突 | 无冲突：未改字段语义与 COW 机制，仅新增状态字段并明确一致性约束 |
| 是否新增聚合 | 无；仅新增字段与契约规则 |
| Mermaid 渲染 | **未验证**（环境无 `mmdc`）；静态检查（fence 配对、通用语法）通过 |
| 实现证据 | 无（`implementation/` 仍为空），符合契约层阶段 |

## 4. 遗留（非阻塞）

- **实现细节**：表结构、索引、事件中间件、快照格式、并发锁、幂等键实现。
- **Mermaid 渲染**：发布或纳入正式基线前须在可渲染环境复核。
- **三字段不一致的修复流程**：属实现/运维协议，契约只定义「按元数据缺陷修复」，未规定具体修复工具。

## 5. 冻结结论与下一步

T3 达成验收条件：K1′～K3′ 用户确认；`materialized` 为明确状态字段；无第三态；LocalId 稳定；最终一致性规则明确；COW/Merge/Query 解释一致；文档、SCN 与图一致。

[LightTrackerItem 物化协议契约 v1](../domain/lighttrackeritem-materialization-contract-v1.md) 冻结为 `Reviewed`（未纳入正式基线、未发布、未提交）。下一步进入 **T4：跨 BC 公共契约**。
