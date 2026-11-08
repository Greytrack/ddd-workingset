# Working Set Shared Kernel 边界

**状态：Draft（包含 Confirmed 组织约束与 Candidate 交互设计）**

## 已确认约束

`AC-001`：Working Set 位于企业 Shared Kernel 内，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；Shared Kernel 还包含 Privilege、Workflow、Class Hierarchy。该定位不在本项目的方案空间内。

可视化见 [Shared Kernel 与四个业务 BC](../diagrams/shared-kernel-context-map.md)。直接依据见 [证据登记](evidence-register.md) 的 `USER-ARCH-2026-11-09`。

## 设计原则

> Working Set Shared Kernel 管理跨四个业务 BC 共同成立的工作集语义和通用机制；业务对象的专有状态机、字段规则和整体合法性仍由对应业务 BC 拥有。

因此，共用 API 只能统一协作语言、证据和结果结构，不能假定 Requirement、Part、GateFlow 对象和 DQA 对象具有相同领域行为。

## 候选职责边界

以下是阶段 3.0 的评审起点，不是已确认契约。

| 候选能力 | Working Set Shared Kernel 的通用责任 | 业务 BC 的专有责任 | 仍需确认 |
| --- | --- | --- | --- |
| `create` | 建立工作上下文、范围选择、稳定身份映射和可恢复任务候选 | 校验并创建合法业务对象或暴露已有对象事实 | `create` 是创建 Working Set、Tracker 还是业务 Item；事务返回语义 |
| `extend` | 扩展已发布范围、保留幂等身份并发布范围事实候选 | 提供新增业务对象事实，处理本 BC 的准入规则 | 扩展粒度、拒绝后的部分成功和补偿 |
| `resolve` | 按 Working Set、Tracker 和身份解析有效 Variant／Source Cut 候选 | 授权并返回本 BC 的权威内容、生命周期和历史 | 权限、无权／不存在／已删除的错误语言 |
| `diff` | 固定有向上下文、对齐身份、组织通用 Difference 证据候选 | 定义字段语义、相等规则、不可比较字段和脱敏 | 通用差异模型能覆盖哪些 BC；扩展字段合同 |
| `merge` | 保存决定、编排应用、记录 Apply Result／Checkpoint 候选 | 校验并提交目标业务变化，保护聚合整体不变量 | BC 拒绝、部分成功、重试、事件和事务边界 |

## 候选协作方式

1. **稳定 Port／Adapter**：Working Set 定义最小 Published Language，各 BC 提供适配器。优点是同步校验清晰；风险是接口膨胀和 Shared Kernel 反向依赖所有 BC。
2. **命令与事实事件**：Working Set 发布意图或范围事实，BC 独立处理并返回结果事实。优点是解耦；风险是中间态、补偿和用户等待语义复杂。
3. **混合方式**：解析、比较读取走稳定查询端口，目标写入走 BC 命令，跨聚合修复走事件。它最符合现有候选场景，但必须逐能力证明，不能默认采用。

“共享一个通用业务对象 Map 并由 Working Set 直接写所有字段”不是默认候选，因为它会绕过四个 BC 的专有模型。

## 阶段 3.0 必须回答

- 哪些 create／extend／resolve／diff／merge 名称是稳定业务能力，哪些只是示意 API？
- 每个 BC 向 Working Set 暴露的最小公共语言是什么？
- BC 拒绝 Merge Decision 时，共享内核如何表达未执行、冲突、部分成功和可重试？
- Privilege、Workflow、Class Hierarchy 与 Working Set 的交互是共享模型、同步端口还是事实事件？
- Shared Kernel 的模型变更由谁批准，四个 BC 如何版本兼容？

在这些问题闭合前，不确认跨 BC 接口签名，也不据此选择 Working Set 内部聚合方案。
