# 阶段 4：ProductSpace、Classification 与绑定模型集中补充评审

**状态：Draft / Reviewed（集中补充评审，供基线批准前决策）**

- 日期：2026-11-10
- 范围：ProductSpace、Classification、SpaceBinding/TrackerBinding、默认 WorkingSet、Tracker 删除与移除。
- 依据：用户 2026-11-10 补充确认；[首版基线候选](../domain/model-baseline-candidate-v1.md)、FACT-024～026、POL-012～014、INV-053～059、SCN-050～054。

## 1. 背景

用户在正式批准首版基线前，补充了 ProductSpace、Classification、默认 WorkingSet 与 Tracker 删除/移除规则。本评审集中记录已确认规则、旧结论修订、以及 4 个影响模型正确性的一致性问题。不重新开展开放式领域发现。

## 2. 已确认规则（用户 2026-11-10）

- ProductSpace 是 Shared Kernel 独立聚合，在默认 WorkingSet 维护生命周期、可关联多个 WorkingSet，不随 WorkingSet 派生复制/迁移；Tracker 属于一个 ProductSpace。
- Classification 是独立共享元模型（三层、启动前初始化、常驻内存）；Tracker 是其投影；Tracker 不随元数据初始化自动创建，而在创建 ProductSpace 时按分类集合投影生成。
- 默认 WorkingSet 固定 Id=1、不可删除/隐藏；Tracker 只能在默认 WorkingSet 创建原始聚合；子 WorkingSet 只能纳入直接上游 Tracker。
- 绑定前置与唯一：SpaceBinding 是 TrackerBinding 前置；同一 WorkingSet/ProductSpace 空间绑定唯一；同一 WorkingSet/ProductSpace/Classification 槽位唯一；Tracker.productSpaceId 一致。
- 纳入：Shared 复用原 TrackerId（仅建新绑定）；非共享创建新聚合（直接 sourceId）；非共享移除后可重新纳入、新身份。
- 移除/删除：Shared 移除=解除 TrackerBinding；非共享可删除但须无派生子级且为空（叶子）；移除空间内最后一个 Tracker 不移除 SpaceBinding；ProductSpace 移出是独立显式操作。

## 3. 旧结论修订

- `POL-006` / Accepted 基线 `R02`「Tracker 纳入后禁止移除」被最新规则修订：改为「Shared 移除=解除 TrackerBinding；非共享叶子+空可删除」。已在 facts.md 与 facts-policy-baseline-v1.md 标注修订，不能两套规则同时生效。
- 原 SCN「子工作集 Tracker 禁止移除」与新 SCN-052 冲突，需以新规则为准（SCN-052 覆盖纳入/移除）。

## 4. 四个一致性问题评审

### A. Shared Tracker 解除绑定是否受「Tracker 有 Item 不允许删除」约束

**意见**：解除 TrackerBinding ≠ 删除 Tracker 聚合。「有 Item 不允许删除」针对聚合删除，不约束子 WorkingSet 解除绑定。子 WorkingSet 解除 Shared Tracker 绑定不受该约束；默认 WorkingSet 的共享 Tracker 根绑定/删除另见 D。

**结论**：不冲突。解除绑定是关系操作，不触发聚合删除约束。

### B. 非共享 Tracker 有组织子节点但无派生子节点，能否删除（D-006 parentId 不可变）

**意见**：D-006 禁止 parentId 修改。删除有组织子节点的 Tracker 会造成子节点 parentId 悬空，违反「删除不得造成无效组织父子关系」。因此「叶子」= **无组织子节点且无派生子节点**。

**裁定（用户 2026-11-10）**：非共享 Tracker 有组织子节点但无派生子节点 → **不允许删除**，校验提示含有子节点。

### C. 显式移除 ProductSpace 时仍有 TrackerBinding

**意见**：SpaceBinding 是 TrackerBinding 前置（INV-053）。若移除 ProductSpace 时仍有 TrackerBinding，会违反前置不变量。

**裁定（用户 2026-11-10）**：移除 ProductSpace 时仍有 TrackerBinding → **抛出异常**，有 Tracker 时不允许移除空间绑定。

### D. 默认 WorkingSet 的共享 Tracker 根绑定/删除

**意见**：默认 WorkingSet 是共享 Tracker 的权威锚点（FACT-002）。子 WorkingSet 解除引用与删除权威 Tracker 是不同操作。

**裁定（用户 2026-11-10）**：默认 WorkingSet 下共享 Tracker 在**空且无子时可以删除**；子 WorkingSet 解除 Shared Tracker 绑定仅解除 TrackerBinding。

## 5. 删除语义已裁定（用户 2026-11-10）

1. 共享 Tracker 从子 WS 移除：仅解除 TrackerBinding（空 Tracker 可移除规则）。
2. 非共享 Tracker 从所属子 WS 移除：无 TrackerItem 且无派生子 Tracker → 删除并解除绑定。
3. 非共享 Tracker 已有派生子 Tracker → 不允许删除（保留来源链）。
4. 任意 Tracker 有 TrackerItem 数据 → 不允许移除（避免遗留业务数据）。
5. 非共享有组织子节点但无派生子节点 → 不允许删除（校验提示含子节点）。
6. ProductSpace 移除最后一个 Tracker → 不触发空间移除，SpaceBinding 保留。
7. 移除 ProductSpace 时仍有 TrackerBinding → 抛异常，有 Tracker 不允许移除空间绑定。
8. 默认 WorkingSet 下共享 Tracker 在空且无子时可删除。

仍待确认（实现细节）：软删/彻底删除机制、级联删除实现。

## 6. 对基线候选的影响

- `model-baseline-candidate-v1.md` 新增第 9 节（ProductSpace/Classification/绑定）。
- 新增 [ADR-0004](../adr/0004-productspace-independent-aggregate.md)（Proposed，未自动 Accepted）。
- 新增 INV-053～059、SCN-050～054、[关系图](../diagrams/productspace-tracker-workingset.md)。
- 是否阻塞基线批准：A/D 不冲突；B/C 是待确认的删除/移除联动边界，不改变已确认的聚合边界。建议基线批准时把第 5 节 1–4 点标为「批准但不含删除联动细节」，或先裁定 B/C。

## 7. D-019：Diff/Merge 衔接裁定（2026-11-10）

用户确认（阶段 4 补充）：

1. 未物化变体可以参与 Diff。
2. 未物化目标被 Merge 修改时，在 merge 过程中构建业务聚合正常 save（物化与修改一体）。
3. 合并决策是用户决策、command 入参，可作为 ValueObject 归属 MergeRound 聚合。
4. MergeCheckpoint 是独立聚合。
5. MergeRound 是合并载体（LRO 发起后记录参数、进度、成功/失败信息）。
6. 共享 Tracker 不参与 Merge；非共享只允许直接父子准入。

知识分类：FACT-027；INV-061~063；场景 SCN-055/056。

聚合定位：MergeRound、MergeCheckpoint 属合并模型区域的聚合，不是三类核心聚合（Working Set/Tracker/LightTrackerItem）之一。

仍待（执行协议，留实施）：差异冻结/比较令牌、幂等键、乱序确认、MergeCheckpoint 的「业务 Revision」与 SourceRevision/Revision 精确字段映射。
