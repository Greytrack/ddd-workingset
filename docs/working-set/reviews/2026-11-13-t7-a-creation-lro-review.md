# T7-A 工作集创建长流程评审

**状态：Reviewed**

当前基线为首版领域模型候选基线 v1，尚未发布。T7 范围与六项用户裁定见 [LRO 设计](../domain/working-set-creation-lro-design.md#2-用户决策2026-11-13)。本评审细化公开任务契约，保留 T2/T3 与 QUESTION-010 的边界。

| 核对项 | 结论与反例 |
| --- | --- |
| 空范围与绑定顺序 | 原子空子工作集仍合法。范围创建先完成 SpaceBinding，再绑定 Tracker。只选 Tracker、漏选其空间时，该 Tracker 项失败，不能为其隐式扩大范围。默认根按原生投影、非根按父工作集绑定校验空间和 Tracker。 |
| 非共享 Tracker 与 Item | 每个所选父 Tracker 只复制一次。分页范围合并业务原生 Item 与已纳入 LTI，按变体身份去重并排除软删。仅查询 BC 实体会漏掉未物化 Item。祖父持有业务内容时，孙 Item 按 T3 直接定位该内容来源。 |
| 幂等与中断 | 同一请求复用任务，目标 ID 冲突明确拒绝。业务写入与该步骤的进度凭据一并提交。进程在提交后中断时，事件重投读到凭据并继续下一项。已记录失败后继续其他可执行项，保留已提交结果。 |
| 失败与关系 | 缺失、范围外、无法读取和无法确认分别沿用 T2/T4 的语义。关系事件在索引终结后发送，待 QUESTION-010 决定解析规则。关系能力未接入时，任务视图明确显示该步骤暂缓。 |
| 目标已存在与查询过滤（2026-11-14） | 受理前检查 `newWorkingSetId` 是否已存在于 `working_sets`；已存在直接 409 `WorkingSetAlreadyExists`，不把已有 WS 标记 `Preparing`。普通查询（详情、子级、祖先、绑定、Item 读取）过滤 Pending/Running 的目标；任务上下文通过 `GET /api/working-set-creations/{id}/working-set` 读取目标，普通接口保持 `Preparing`。 |

## 运行中可见性裁定

场景是任务已提交子工作集和首批 LTI，前端轮询仍为 `Running`，其他调用者此时查询或修改该范围。

| 方案 | 影响 |
| --- | --- |
| A · 分阶段可见 | 沿用原子入口即时可见，改动集中在 LRO。调用者可能读取到半成品，或在任务进行时删除、解绑或再次派生，导致任务结果与实际范围分叉。 |
| B · 运行中返回 `Preparing`（用户确认） | 轮询可读进度。查询范围或通过现有命令使用目标工作集时返回 `Preparing`，终态后开放已提交范围。需要覆盖读写入口、异步执行者入口及失败后保留范围的测试。 |

任务聚合与既有 WorkingSet、Tracker、LTI 分离，两种方案均保持各聚合的业务所有权。接口、流程和场景见 [T7 设计](../domain/working-set-creation-lro-design.md)、[状态图](../diagrams/working-set-creation-lro.md) 与 [SCN-075](../scenarios/core-scenarios.md#scn-075-按范围异步创建工作集)。
