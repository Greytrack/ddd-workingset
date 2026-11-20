# 当前实现边界与设计差距

**状态：Reviewed（2026-11-20 静态核对；实现证据，不是新增基线裁定）**

依据：用户 2026-11-19 移除单方法 Port 和文档对齐的要求；当前实现改为只读使用共享聚合，该读取边界在本页登记为 Reviewed，不扩大既有契约接受范围；`2c50900`、`543cc07`、`15f0399`、`d5d0f3b`、`0805c93`、`5e52952`、`594dcaa`、`6d4b567`、`a21926b`、`3e1e2bb`、`70772f5`、`da8685b` 及当前工作区。已确认的规范仍以 [BASELINE](../BASELINE.md) 为准；本页不把当前代码反推为新的业务规则。

## 1. 模块与上下文边界

当前 `sharedkernel` 已从单一 `sk.workingset` 包扩展为以下模块。它们**不是**四个企业业务 BC 之外的四个新 BC；是否拆为独立限界上下文仍需按语言、所有权和运行时证据裁定。

| 模块 | 当前拥有的职责 | 依赖/边界 |
| --- | --- | --- |
| `sk.businessframework` | 通用业务对象、属性、版次、排序和轻量只读投影能力；`BusinessObject`/`Revisionable` 等共享契约 | 属于 Shared Kernel 的通用能力，不是 Working Set 业务模型；不得反向依赖各 BC。`validation` 子包存在迁移期旧栈依赖例外，见其包说明。 |
| `sk.tracker` | Tracker 聚合根、创建/改名命令、Tracker 只读 Provider、创建后绑定事件 | Tracker 是独立聚合。当前仍直接使用 `sk.workingset` 的 `WorkingSet`、`Classification` 类型；这是同进程实现依赖，不等于已冻结的跨 BC 合同。 |
| `sk.productspace` | ProductSpace 聚合根、创建/删除命令、ProductSpace 仓储 | ProductSpace 是独立聚合。当前创建时仍调用 Working Set 初始化默认成员关系，见 §4 差距。 |
| `sk.workingset` | WorkingSet/绑定、LightTrackerItem、COW、WorkingSet 创建 LRO、Compare/Merge、关系和查询 Provider | Working Set 核心能力；命令侧聚合仓储与查询侧 Provider 分离。 |
| `alm.requirement` | Requirement 参考实现、Merge 执行适配、物化请求消费、继承编辑查询回退 | 业务 BC 参考实现；只读使用 SK 共享聚合/仓储，写入通过事件协作。 |

`sk.businessframework.domain` 中的 `BusinessObject` 是**通用业务对象能力接口**，与 `sk.workingset.domain.TrackerItem` 的公共工作集契约不是同一概念；不得因为命名相近而把二者互相替代。

## 2. 跨 BC 读取边界（2026-11-19 调整）

- LightTrackerItem 的当前索引和版次成员分别写入两张旧索引表，由同一次聚合保存提交；已落地动作与未决撤销规则见[双表索引与版次成员](../domain/lighttrackeritem-materialization-contract-v1.md#31-双表索引与版次成员)。
- ALM 等业务 BC 可以**只读**使用 SK 的共享聚合和聚合仓储（如 `LightTrackerItemRepository.findById`、`WorkingSetRepository.findById`、`TrackerRepository` 读取），用于完成自身用例所需的身份和来源校验。LightTrackerItem 的身份和来源解析供系统内部使用，业务内容由相应 BC 的 API 提供。
- 不再为单个查询方法新增专用同步 Port；`TrackerItemLifecyclePort` 及其 `findInheritedTarget` 已移除。集合读取和读模型查询使用 `application/query/provider` 下的 Provider。
- 直接读取不等于获得写入权：业务 BC **不得**调用 SK 聚合仓储的 `save`、`delete`、`remove` 或修改 SK 聚合状态；跨聚合状态变化仍必须通过可靠事件和各自独立事务完成（TC-002）。
- 直接读取的外部状态不能满足跨聚合原子校验；需要“先校验后写入”的规则仍由目标 BC 或事件消费方在本地事务中重新校验。

## 3. 当前事件协作

| 事实 | 发布方 | 消费者/后续动作 |
| --- | --- | --- |
| Requirement 创建后登记 LTI | ALM `TrackerItemCreatedEvent` | SK 幂等登记 Local-backed LTI；业务已删除时跳过 |
| Requirement 普通修改/继承编辑保存 | ALM `BusinessObjectUpdateEvent` | SK COW 请求；`TrackerItemLocalizedListener` 对首次继承编辑在独立事务切换 LTI |
| COW 业务保存 | SK `TrackerItemMaterializationRequestedEvent` | ALM 保存 Requirement 并发布 `TrackerItemMaterializationCompletedEvent` |
| COW 完成 | ALM `TrackerItemMaterializationCompletedEvent` | SK 在独立事务切换 Local-backed / 记录失败终态 |
| Merge 条目批准 | SK `MergeEntryApprovedEvent` | ALM 校验并保存目标 Requirement，发布 `MergeEntryResolvedEvent` |
| Merge 条目结果与确认 | ALM `MergeEntryResolvedEvent` | SK 在同一轮次事务持久化条目结果和所需确认，按需发布 `MergeAppliedEvent`；确认写失败时轮次不结束 |
| Merge 目标 LTI | SK `MergeAppliedEvent` | SK 独立事务切换/创建 LTI |
| 当前索引软删 / 恢复 | ALM `BusinessObjectDeleteEvent` | SK 独立事务核对当前业务状态，更新 LTI `deleted` 并映射到旧表 `flags`；保留版次成员 |
| 来源软删 / 永久删除 | ALM `BusinessObjectUpdateEvent` / `CompleteDeleteEvent` | 软删保存快照并触发物化；仍有来源依赖时拒绝永久删除，完成后 SK 异步清理来源 LTI |

事件只表达已发生事实；请求不是完成事实。以上流程不引入通用写 Port，也不要求消息代理或事件溯源。

## 4. 与 TC-002 的剩余差距

| 路径 | 当前状态 | 差距/待验证 |
| --- | --- | --- |
| ProductSpace 创建 | `ProductSpaceCommandService.create` 保存 ProductSpace 并发布 `ProductSpaceCreatedEvent`；默认 Tracker 批量创建后由 Working Set 独立绑定 | 默认 Tracker 的循环保存仍是用户裁定的折中多聚合事务；需记录偏差并验证部分失败与重投。 |
| ProductSpace 删除 | [T2 §4.3](../domain/binding-deletion-contract-v1.md#43-聚合删除)；Modulith 逐事件清理 | 受理后若出现新业务引用，当前步骤再次校验并中止，保留未完成事件和已提交清理结果。删除期间没有公开任务状态。 |
| T7 创建/扩展 | 阶段按项执行，但工作集创建、Tracker 派生、范围绑定及 LTI 索引仍与各自的任务进度同事务提交 | 按 [反模式审查](../reviews/2026-11-19-ddd-anti-pattern-scan.md) 将聚合副作用与任务进度拆成独立提交，用可靠结果事件推进任务。 |
| Tracker 创建 | TrackerCommandService 仅持久化 Tracker，绑定由 `TrackerCreatedEvent` 的监听器在 Working Set 事务中完成 | 主路径已事件化；仍需验证监听失败、重复投递、绑定拒绝和运维可观察性。ProductSpace 初始化不在该路径内。 |
| 继承编辑 | ALM 保存后由 `BusinessObjectUpdateEvent` 异步切换 LTI | 保存成功不保证 LTI 立即 Local-backed；读取方需容忍短暂 Source-backed。切换失败不回滚业务保存。 |
| Folder Tracker | Tracker 是 ProductSpace 下第三级容器；每个 `(WorkingSet, ProductSpace)` 一个 Folder Tracker，非共享、不可独立删除，Folder 业务对象为单版次可编辑/删除/树结构，Folder 树不参与 Compare/Merge | 需要持续验证事件幂等、绑定失败可见性、Folder 业务对象生命周期和 Compare/Merge 排除。见 [ADR-0008](../adr/0008-tracker-third-level-container.md)。 |

## 5. 已移除的旧声明

以下内容不再作为当前设计入口；历史实现记录仍保留在评审和验收文档中：

- `TrackerItemLifecyclePort`、`InheritedEditTarget`、`Caller` 值对象及其占位授权；
- `RequirementWritePort` 及同步 ALM 物化/合并写端口；
- LightTrackerItem Redis 缓存装饰器及 `workingset.item-cache` 配置；
- “ALM 直接读取 SK 仓储一定违规”的笼统表述；当前只禁止直接写入，读取边界见 §2。

## 6. 未验证与阻塞

- 工作区新增的 `RequestThreadHolder` 及 `sk.businessframework` 部分文件尚未完成全量编译/验收；本页不将其字段、方法或依赖视为已交付事实。
- 2026-11-19 的完整测试记录为 160 项通过；2026-11-20 后 Merge 确认、错误码和 LTI 索引改动已提交，最新本地报告仅覆盖 32 项局部测试，尚无全量重跑证据。
- 本页不把代码现状反向确认为业务政策；产品空间、Tracker、LTI 和 Merge 的业务语义仍以 BASELINE、ADR 和 T1～T4 契约为准。

## 7. 追溯

- [TC-002 反模式审查](../reviews/2026-11-19-ddd-anti-pattern-scan.md)
- [跨 BC 公共契约](../domain/cross-bc-contract-v1.md)
- [聚合边界](../domain/aggregate-model.md)
- [Shared Kernel 边界](../domain/shared-kernel-boundary.md)
- [实现与验收索引](README.md)
