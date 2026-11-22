# QUESTION-010：跨 Working Set 关系身份裁定

**状态：Reviewed（2026-11-15，用户选择 C）**

## 裁定

跨 Working Set 的 Reference / Association 按关系类型拆分目标身份：

- **Reference**：使用逻辑 Family 作为目标身份，保存 `targetCommonId` 与 `targetTrackerId`；可选 `targetRevision` 表示钉版，可选
  `targetLocalId` 表示当前工作集内的物理定位。右侧为空时，引用行仍有效，保持悬空；后续纳入目标时补齐同一行右侧，不新建引用。
- **Association**：使用具体 Variant 端点，保存 `sourceLocalId` / `targetLocalId`；缺端点时不落库，保留恢复意图，后续按原意图建立合法关联；下游关联丢弃，不恢复。
- **边界**：沿用已确认 D6，附件/关系不参与 Compare/Merge；T7-E 本轮只负责在创建/扩展/ Merge 完成后发布关系事件，**不创建或修改
  Reference/Association 聚合**；关系 Compare/Merge 不在本轮范围。常规业务 BC 修改/删除导致的关系变化由业务 BC 维护。
- **权限**：2026-11-17 补充明确：工作集场景**跳过权限**，不产生 `Denied` 状态；关系构建按数据可读性处理，权限由业务
  BC/后续接入负责。此前的 D11 占位不落地。

本裁定采用 pre-design 关系设计的身份拆分方向，**不自动接受**其全部字段、聚合行为、传播规则或 Merge 接入方案。

## 备选与理由

| 选项                     | 目标身份                                        | 主要问题                                                                   |
|--------------------------|-------------------------------------------------|----------------------------------------------------------------------------|
| A 逻辑 Family            | 两类关系都按 CommonId/Tracker 族解析            | Association 目标可能随上下文漂移；两类关系语义被强行统一                   |
| B 具体 Variant           | 两类关系都钉具体 LocalId                        | 变体替换、合并和跨工作集后纳入造成大量修复；Reference 的逻辑目标稳定性丢失 |
| **C 按关系类型（选择）** | Reference 逻辑 Family；Association 具体 Variant | 两套解析/修复路径，但符合两类关系的不同生命周期                            |

选择 C 的原因：`HYP-007` 指出 Reference 与 Association 可能需要不同生命周期和缺端点语义；`FACT-009` 的悬空描述主要适用于
Reference；pre-design 已按该方向设计，落地成本最低；同时避免把 Association 强行建模为可悬空关系。

## 仍然开放

- `FACT-009` 仍为 Reported / Pending：悬空引用的用户可见性、删除守卫和查询表现需要按目标缺失、软删、永久删除、后续纳入、钉版不可读五类场景确认；权限本轮跳过。
- `QUESTION-006`：钉版所需历史保留期限与不可读时的行为。
- `QUESTION-009`：删除目标时是允许悬空、阻止删除还是提示修复，仍需与删除守卫一起裁定。
- Association 的类型、规则、传播怀疑和缺端点恢复意图的持久化方式。
- Reference 的 `RelationRevisionRecord`、去重作用域、属性元数据与修复消费凭据是否全部进入首版。
- T7-E 的任务视图、失败明细和事件消费边界。

## 相关材料

- [T7 设计 §4.6](../../05-contracts/t7-creation-lro.md#46-关系事件发布)
- [T7-E 守卫裁决草案](2026-11-17-t7-e-relation-guards-decision.md)
- [T7-E 后续计划](../../08-plan.md)
- [FACT-009 / HYP-007](../../03-rules.md)
- [QUESTION-010 原问题](../../08-plan.md)
