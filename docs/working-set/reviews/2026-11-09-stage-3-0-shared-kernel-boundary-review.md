# 阶段 3.0：Shared Kernel 边界评审

- **状态**：Draft Review
- **日期**：2026-11-09
- **范围**：Working Set 的企业 Shared Kernel 定位与四个业务 BC 的职责边界

## 读取证据

- ChatGPT conversation `6af08f18-2ef8-83e9-a973-b5927d073c64` 最后一轮完整消息；
- 附件 `image.png`：GateFlow、ALM、DQA、PLM 四个 Bounded Context 围绕 Shared Kernel，内列 Class Hierarchy、Privilege、Workflow、WorkingSet；
- 附件 `image(1).png`：业务侧调用 Working Set 对外 API 面 `create / extend / resolve / diff / merge` 的示意；
- 仓库现有 `facts.md`、`glossary.md`、`context-map.md`、候选 Context Map 和 `BASELINE.md`。

## 已确认结论

`AC-001`：Working Set 是企业 Shared Kernel 的组成能力，为 GateFlow、ALM、DQA、PLM 四个业务 BC 提供公共工作集能力；当前项目不讨论把 Working Set 移出 Shared Kernel。

确认依据是用户明确说明该定位由外部集体确定、无法动摇。这里的 Confirmed 只适用于组织架构约束，不表示 Working Set 内部领域模型或 ADR 已 Accepted。

## 对现有设计的修正

1. 阶段 2 的 `CTX-001...CTX-009` 不能被读成企业级九个 Bounded Context；它们只是 Working Set 能力内部的候选模型边界。
2. Classification 不再描述为外部上游，而是 Shared Kernel 内的 Class Hierarchy 兄弟能力；Privilege、Workflow 同样需要局部协作设计。
3. `CTX-004` 具体化为 GateFlow、ALM、DQA、PLM 四个业务 BC，它们拥有各自业务对象的状态机和整体合法性。
4. Working Set Shared Kernel 不应因统一入口而直接吸收四个 BC 的专有规则。

## 仍为候选的内容

- `create / extend / resolve / diff / merge` 是否是最终稳定业务能力及其精确语义；
- Working Set 与 Privilege、Workflow、Class Hierarchy 的同步／异步协作；
- 四个 BC 提供 Adapter、命令、事件或混合合同；
- BC 拒绝合并决定、部分成功和重试的结果语言；
- Shared Kernel 的版本治理和兼容策略；
- Working Set 内部限界上下文与聚合边界。

## 阶段门建议

先完成 `QUESTION-013`：选一个具体 BC 的具体对象，用 `resolve` 与 `merge` 各走一次成功和拒绝场景，明确共享机制与专有规则的交界。完成后再恢复 `QUESTION-001/002` 的 Working Set 与 Tracker 身份讨论。
