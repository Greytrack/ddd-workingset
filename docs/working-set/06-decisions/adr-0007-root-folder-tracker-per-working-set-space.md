# ADR-0007：每个 Working Set 的产品空间拥有独立根文件夹 Tracker

- **状态**：Superseded
- **日期**：2026-11-20
- **决策范围**：Working Set / ProductSpace / Root Folder Tracker 的归属、共享与比较合并边界
- **确认人／决策权威**：用户 2026-11-20 明确语义调整
- **替代**：无
- **被替代于**：[ADR-0008](adr-0008-tracker-third-level-container.md)（2026-11-20，Folder Tracker 不是特殊根文件夹）
- **相关**：[ADR-0004](adr-0004-productspace-independent-aggregate.md)；本 ADR 补充“根文件夹 Tracker 可在各 Working Set
  创建”的例外，不改变 ProductSpace 独立聚合和绑定归属。

## 背景

此前实现按“产品空间在默认 Working Set 创建一个根文件夹，其他 Working Set 只绑定该 Tracker”处理。用户于 2026-11-20 明确纠正：

> 每个 Working Set 下的每个产品空间拥有自己的根文件夹 Tracker；根文件夹 Tracker
> 是非共享的，用来承载每个工作集每个产品空间各自的文件夹树。只是约定文件夹树不支持合并，虽然是非共享但是不允许合并比对。

这决定了根文件夹 Tracker 的身份作用域，也影响 SpaceBinding 建立后的 Tracker 创建与 Compare/Merge 准入。

## 决定

1. 根文件夹是 `Tracker` 聚合，分类为文件夹分类（`10070101`），不是 WorkingSet 内部记录。
2. 根文件夹 Tracker 的身份作用域为 `(WorkingSetId, ProductSpaceId)`：每个 Working Set 的每个产品空间各自拥有一个根文件夹
   Tracker。
3. 根文件夹 Tracker 是非共享 Tracker（`UNSHARED`），其文件夹树不跨 Working Set 复用。
4. 根文件夹 Tracker 及其文件夹树**不参与 Compare/Merge**；即使它属于非共享 Tracker，也不得作为比较或合并对象。
5. SpaceBinding 建立后，由独立事件为该 `(WorkingSet, ProductSpace)` 创建根文件夹 Tracker，再通过 Tracker 创建事件建立
   WorkingSet 绑定；不得在 WorkingSet 绑定事务内直接创建 Tracker。
6. ProductSpace 创建时负责为默认 Working Set 创建其根文件夹 Tracker；其他 Working Set 在绑定空间时按同一规则按需创建。

## 备选方案与权衡

### 方案 A：每个 Working Set/ProductSpace 独立根文件夹 Tracker（采纳）

- 保护的不变量：每个工作集拥有独立文件夹树；根文件夹不跨工作集共享。
- 收益：文件夹树完全按工作集隔离；不会因其他工作集删除/移动影响本工作集。
- 成本：绑定空间后需要额外 Tracker 创建事件，存在短暂最终一致窗口。

### 方案 B：产品空间级单一根文件夹 Tracker，被所有 Working Set 共享（未采纳）

- 收益：根文件夹身份数量少，绑定简单。
- 问题：不符合“每个工作集各自文件夹树”的业务语义；根文件夹变成跨工作集共享对象，破坏非共享隔离。

### 方案 C：根文件夹随 Working Set 聚合内部创建（未采纳）

- 问题：违反 Tracker 独立聚合和 TC-002 单写事务约束；根文件夹无法独立参与 Tracker 生命周期。

## 后果

- 根文件夹 Tracker 的 `workingSetId` 必须等于持有它的 Working Set；`productSpaceId` 指向对应空间。
- `bindSpace` 只创建 SpaceBinding 并发布事件；根文件夹 Tracker 由独立 Tracker 事务创建。
- Compare/Merge 候选必须排除根文件夹 Tracker。
- 删除空间时，根文件夹 Tracker 的处理仍需遵守绑定与删除契约，不得因根文件夹而绕过删除约束。
- 旧测试中“子 Working Set 绑定默认工作集根文件夹”的断言不再成立。

## 验证与追溯

- 领域术语：[glossary](../02-language.md)
- 聚合边界：[aggregate-model](../04-model.md)
- 绑定契约：[binding-deletion-contract-v1](../05-contracts/t2-binding-deletion.md)
- 实现入口：`WorkingSetCommandService#bindSpace`、`SpaceBoundRootFolderListener`
- 验收：按工作集隔离的根文件夹 Tracker 创建、绑定、隐藏、删除；根文件夹不进入 Compare/Merge。
- 重新评估条件：文件夹树需要跨工作集共享，或根文件夹需要参与合并时。
