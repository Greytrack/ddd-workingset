# Working Set 候选聚合边界分析

**状态：Draft**

聚合是事务一致性边界，不是页面装载范围或外键集合。本页不选最终方案；完整竞争方案见 [提案比较](../proposals/aggregate-boundary-options.md)。候选不变量见 [invariants.md](invariants.md)。各方案的领域类图和聚合边界图将在相应方案进入正式战术评审时按 [图表渐进交付规则](../diagrams/README.md) 分别创建，不在当前证据不足时一次性补齐。

## 核心概念分析

| 概念 | 独立身份／生命周期 | 候选内部不变量 | 事务与并发关注 | 反例／未决问题 |
| --- | --- | --- | --- | --- |
| Working Set | 有长期身份；准备、发布、归档等状态维度尚未确认 | 发布 revision 与当前范围一致；发布／取消不互相覆盖 | 巨量 Binding 会造成加载与版本热点 | 改名不应加载十万 Item；`QUESTION-001/012` |
| Tracker／Tracker Branch | TrackerId 已被历史材料使用；是否另有 BranchId 未决 | Sharing Mode、产品空间/分类定位、直接来源、无环 | 共享写竞争跨 Working Set；删除需检查派生与保留依赖 | V4 取消 BranchId，但业务可能跟踪分支实例；`QUESTION-002/003` |
| Item Variant | 候选 LocalId；从 VIRTUAL 到本地内容有持续身份 | 家族、LocalId、所属 Tracker、Source Cut 不被 COW 改写 | 首次写竞争、来源事件乱序、索引/内容双事实 | 若只是缓存则不应是聚合；`QUESTION-004/006` |
| Business Item | 各业务上下文已有身份与生命周期 | 内容、状态机、revision、字段组合合法性 | Merge/COW 必须走其公开写能力 | 不能用通用字段 Map 绕过规则 |
| Merge Round | 轮次身份和可恢复生命周期 | 命令冻结、状态转换、进度与唯一终态结果 | 不持有所有业务对象；部分成功需保留 | 把整轮设为大事务会掩盖已提交结果 |
| Merge Checkpoint | 有向 source-target-family 键候选 | 确认／撤销因果单调、证据关联 | 旧事件不能覆盖新决定 | `KEEP_TARGET + true` 内容仍不同 |
| Reference | 关系身份持续跨版次 | 逻辑目标、有效性、钉版、修复不换身份 | 修复与删除竞态；每关系独立提交候选 | 右侧为空不等于无引用 |

## 跨聚合规则

- LocalId、ItemFamilyKey、Binding 占用和有向 Checkpoint 的并发唯一性无法只靠单聚合内存检查；领域需要定义键与冲突语义，持久化负责兑现。
- 是否“一次事务只改一个聚合”应由 `INV-*` 的原子可见性要求决定。当前 `TC-002` 只是 V4 架构候选。
- 业务查询可以组合多个聚合或投影，但不得把组合结果保存回命令侧当作部分聚合。

## 方案结论状态

- `AP-001`、`AP-002`、`AP-003` 均可解释部分业务事实，当前没有 Accepted 方案。
- 若 Working Set 规模小且发布需要整体原子，`AP-001` 更简单。
- 若 Tracker 派生线是业务人员直接管理的对象，`AP-002` 更自然。
- 若既要混合共享／派生又要支持巨大范围与稳定历史，`AP-003` 更有解释力，但双事实和历史成本最高。
- 最终选择被 `QUESTION-001/002/004/006/012` 阻塞。
