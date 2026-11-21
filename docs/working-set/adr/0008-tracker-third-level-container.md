# ADR-0008：Tracker 是产品空间下的第三级容器，Folder 是业务对象

- **状态**：Accepted
- **日期**：2026-11-20
- **决策范围**：Working Set / ProductSpace / Tracker 容器层级、Folder Tracker 与 Folder 业务对象
- **确认人／决策权威**：用户 2026-11-20 明确语义纠正
- **替代**：[ADR-0007](0007-root-folder-tracker-per-working-set-space.md)
- **被替代于**：无

**2026-11-20 后续裁定**：Folder 是业务 BC 的对象。应用编排负责默认纳入文件夹分类 `10070101`，Tracker 按统一规则创建、派生和删除。下文涉及该分类的
Tracker 专属唯一性、删除保护和比较准入的旧规则由此澄清取代。业务内容的 Compare/Merge 能力由接入的 BC 提供。

## 背景

用户于 2026-11-20 纠正上一轮“根文件夹 Tracker”的建模：

>
没有所谓的根文件夹Tracker，就是文件夹Tracker。需求、测试、物料、评审、文件夹等等将来可能的业务对象都在产品空间容器下，各自都会有自己的tracker，tracker是三级容器，产品空间是二级容器，工作集是一级容器。文件夹Tracker下将来有入口创建文件夹入口，文件夹也是一类业务对象，单版次的业务对象，可以编辑修改，可以删除，可以有树结构。

因此，Folder Tracker 不是特殊“根节点”，而是承载 Folder 业务对象的普通第三级容器。

## 决定

1. 容器层级固定为：
    - Working Set：一级容器；
    - ProductSpace：二级容器；
    - Tracker：三级容器，按业务对象类型划分；
    - 业务对象（Requirement、Test、Material、Review、Folder 等）：位于 Tracker 下。
2. 每个 `(WorkingSet, ProductSpace)` 只有一个 **Folder Tracker**。
3. 不存在“根文件夹 Tracker”概念；Folder Tracker 不是文件夹树的根节点，也不作为特殊业务对象。
4. Folder 是一类业务对象：
    - 单版次；
    - 可编辑、可删除；
    - 可以有树结构；
    - 位于 Folder Tracker 下。
5. Folder Tracker 不允许独立删除；其生命周期跟随所属 `(WorkingSet, ProductSpace)` 容器的受控卸载。
6. Folder Tracker、Folder 业务对象及其树不参与 Compare/Merge。

## 备选方案与权衡

### 方案 A：Tracker 作为第三级容器，Folder 是业务对象（采纳）

- 保护的不变量：容器层级稳定；每类业务对象有独立 Tracker；Folder 按普通业务对象生命周期管理。
- 收益：需求、测试、物料、评审、文件夹等类型可以按同一容器规则扩展；Folder 不需要特殊根节点。
- 成本：Folder Tracker 的创建、绑定和空容器管理需要明确规则。

### 方案 B：Folder Tracker 作为特殊根文件夹（未采纳）

- 问题：把容器与文件夹业务对象混为一谈；无法自然容纳 Folder 的单版次、编辑、删除和树结构。
- 结论：上一轮 ADR-0007 方案被本 ADR 替代。

### 方案 C：Folder 树作为 Tracker 内部结构而不是业务对象（未采纳）

- 问题：违反“Folder 是一类业务对象”的语义，也让 Folder 无法复用 Item 生命周期和树结构规则。

## 后果

- `Tracker.rootFolder` 命名和“根文件夹”特殊保护需要移除。
- Folder Tracker 按 `(WorkingSet, ProductSpace)` 唯一创建，不能通过普通 Tracker 删除入口删除。
- Folder 业务对象作为单版次 Item 位于 Folder Tracker 下，可编辑、删除和形成树。
- Compare/Merge 候选必须排除 Folder Tracker 及其 Folder 业务对象。
- 旧测试中的 `rootFolder` 字段应改为 `folderTracker`，旧“根文件夹”文档和 ADR 需标记替代关系。

## 验证与追溯

- 领域术语：[glossary](../domain/glossary.md)
- 聚合边界：[aggregate-model](../domain/aggregate-model.md)
- 绑定契约：[binding-deletion-contract-v1](../domain/binding-deletion-contract-v1.md)
- 实现入口：产品空间创建后的默认 Tracker
  绑定、[T7 创建和扩展](../domain/working-set-creation-lro-design.md#42-步骤与状态机)、`TrackerCommandService#delete`。
- 验收：每个 `(WS, ProductSpace)` 一个 Folder Tracker；不可独立删除；Folder 单版次可编辑/删除/树结构；不进 Compare/Merge。
- 重新评估条件：Folder 需要多版次、跨工作集共享，或需要参与 Compare/Merge 时。
