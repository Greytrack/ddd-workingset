# T2 评审记录：绑定与删除契约 v1（冻结）

**状态：Reviewed（2026-11-11）；结论：T2 契约冻结。**

审核对象：[绑定与删除契约 v1](../domain/binding-deletion-contract-v1.md)（Draft → Reviewed 冻结）、[绑定与删除生命周期图](../diagrams/binding-lifecycle.md)、[ProductSpace/Tracker/WorkingSet 关系图](../diagrams/productspace-tracker-workingset.md)、SCN-050～054 / SCN-061～065、INV-053～060 / INV-064～068、POL-012～016。

审核方式：逐项核对用户最终裁定（K1=A、K2=A、K3=A）、4 项一致性修正、旧表述清理、规则冲突、图表一致性。**未做**实现验证与 Mermaid 实际渲染。

## 1. 用户最终裁定的落地

| 编号 | 用户裁定（2026-11-11） | 落地位置 |
| --- | --- | --- |
| K1 | 仅支持不可恢复的领域删除；软删/归档/恢复不纳入本版；不强制映射数据库物理 DELETE；区分绑定移除、聚合删除与业务 TrackerItem 删除 | 契约 §5/§7；POL-016；INV-068；SCN-065；图 §4 易错点 1 |
| K2 | Tracker 存在组织子级、派生子级或 TrackerItem 时禁止删除；不级联、不断开或重挂 `parentId`/`sourceId`；只裁定删除，不裁定停用 | 契约 §4.3/§6/§7；INV-065；SCN-062；facts.md D-006 段；open-questions QUESTION-009.a（仅停用开放） |
| K3 | 默认 WorkingSet（Id=1）始终不可删除；子 WS 删除前须合法解除全部 TrackerBinding 与 SpaceBinding；解除顺序 TrackerBinding → SpaceBinding → WS；任一不可解除即拒绝 | 契约 §4.3/§6/§7；INV-067；SCN-063；图 §3.1 时序 |

## 2. 四项一致性修正

| 问题 | 修正 | 位置 |
| --- | --- | --- |
| `unbindTracker` 统一写「无 TrackerItem」与 Shared 仅解绑矛盾 | 拆为 Shared（存在有效绑定 + 具备权限，**即使有 Item 也允许**）与非共享（Item／组织子级／派生子级三项） | 契约 §4.2；SCN-061；图决策图与 §3.1 |
| 非共享解绑即聚合删除，须三项检查 | 非共享行明确三项并列必要条件与三个失败分类 | 契约 §4.2；INV-065 |
| ProductSpace 删除只查 TrackerBinding 不足 | `deleteProductSpace` 前置改为「所有 WorkingSet 无有效 SpaceBinding」，并明确有 TrackerBinding 须先按 Tracker 规则解除、不得直接清理或级联 | 契约 §4.3/§6；INV-066；SCN-064；图 §3.2 |
| 删除 WorkingSet 不得无条件级联 | 表达为受成员关系约束的业务操作：准入条件 + 解除顺序，任一不可解除即拒绝；不预设跨聚合大事务 | 契约 §4.3；INV-067；SCN-063 |

## 3. 旧表述清理

| 旧表述 | 处理 |
| --- | --- |
| 「父 Tracker 停用必然禁止」（facts.md D-006 段原写「不能…禁止父删除」） | 改写为：删除已由 K2 裁定（有组织子级/派生子级/Item 禁止），停用仍待裁定 |
| 「Shared Tracker 有 Item 不能解绑」 | POL-014 保留原文并加 2026-11-11 边界澄清（「有 Item 不允许移除」只约束聚合删除）；INV-064 形式化；aggregate-model 摘要、model-baseline 摘要加注 |
| 「只检查 TrackerBinding 就能删除 ProductSpace」 | `deleteProductSpace` 前置改为审查所有 WorkingSet 的 SpaceBinding；SCN-064、图 §3.2、productspace 关系图同步 |
| 「待确认（QUESTION-009.a）」 | QUESTION-009 标为已裁定（K1～K3）；QUESTION-009.a 收敛为「仅停用部分开放」 |

## 4. 审查结果

| 检查项 | 结果 |
| --- | --- |
| Shared 解绑与非共享聚合删除前置完全区分 | 通过（契约 §4.2、SCN-061、图决策图与 §3.1） |
| ProductSpace 删除不会留下有效 SpaceBinding | 通过（`deleteProductSpace` 前置、INV-066、SCN-064） |
| 子 WorkingSet 删除不会留下悬空 Tracker / ProductSpace 引用 | 通过（K3 顺序：TrackerBinding → SpaceBinding → WS；INV-067、SCN-063） |
| 与 D-006、POL-012～015、INV-053～060 的冲突 | 无冲突：D-006 的 parentId 不可变未被放宽；POL-014 原文保留仅加边界澄清 |
| 图与操作表一致 | 通过（决策图、两张删除时序图与 §4 操作表一致） |
| 聚合职责不变 | 通过：WorkingSet 管绑定，ProductSpace/Tracker/LightTrackerItem 各自独立，ID 引用，不新增聚合 |
| 未引入软删状态机 / 停用状态 | 通过（K1、§6 末条） |
| Mermaid 渲染 | **未验证**（环境无 `mmdc`）；静态检查（fence 配对、通用语法）通过 |
| 实现证据 | 无（`implementation/` 仍为空），符合契约层阶段 |

## 5. 遗留（非阻塞，不属本次冻结范围）

- ~~**停用生命周期**：`QUESTION-009.a` 的停用部分仍开放（本轮明确只裁定删除）。~~ **2026-11-12 更新：用户裁定 WorkingSet/Tracker 不存在“停用”概念，`QUESTION-009.a` 已关闭；`assertDisableAllowed` 已从代码移除。**
- **实现细节**：绑定 Entity 的表结构、唯一约束实现、并发锁、物理清理策略、软删字段均不在契约内。
- **Mermaid 渲染**：发布或纳入正式基线前须在可渲染环境复核。

## 6. 冻结结论与下一步

T2 达成完成标准：K1/K2/K3 均为用户已确认；Shared 解绑与非共享聚合删除完全区分；ProductSpace 删除无残留 SpaceBinding；子 WorkingSet 删除无悬空引用；与既有已确认规则无冲突；图与操作表一致。

[绑定与删除契约 v1](../domain/binding-deletion-contract-v1.md) 冻结为 `Reviewed`（未纳入正式基线、未发布、未提交）。下一步进入 **T3：LightTrackerItem 物化协议**。
