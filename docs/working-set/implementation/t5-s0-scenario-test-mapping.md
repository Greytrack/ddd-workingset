# T5-S0 场景-期望-测试映射表

**状态：Historical（S1 验收规划，已完成）。** 当前验收证据见 [S1](t5-s1-verification-result.md)～[S7](t5-s7-verification-result.md) 验证结果；本表保留为规划记录。

- 日期：2026-11-11
- 依据：[T5-S0 最小纵向切片设计](t5-s0-minimal-slice-design.md)、T3（物化协议）、T4（Port/Adapter、九分类）、T2（非共享派生）
- 测试层次：① 真实领域对象单测；② Mockito 应用层测试；③ 真实 PostgreSQL、Redis、Tomcat 与 Modulith 验收。证据见 [S3](t5-s3-verification-result.md)及 [S4](t5-s4-verification-result.md)。

## 映射总表

| 场景 | 验证目标 | 测试层次 | 对应契约/场景 | 关键断言 |
| --- | --- | --- | --- | --- |
| AT-01 | 非共享派生身份与初始态 | ② 集成 | T2、SCN-021、INV-026/040/041 | `localId` 新、`commonId` 同、`materialized=false`、`sourceLocalId≠localId` |
| AT-02 | Source-backed 读取来源内容 | ② 集成 | D-013、INV-043、SCN-023 | 读到来源 revision 内容；仍 `materialized=false`；无新建业务对象 |
| AT-03 | 普通修改不升级 Revision + 首次快照优先 | ②③ 集成 | D-009/D-011、INV-037、SCN-025/030 | 保存后 revision 不变；物化内容为 A（修改前快照），非 B |
| AT-04 | 物化后三字段一致 + 本地内容独立 | ①③ | T3 §6/§7、INV-039/069、SCN-032 | `materialized=true`、`sourceLocalId=localId`、`sourceRevision=revision`；来源后续修改不影响本地 |
| AT-05 | 切换失败后的幂等恢复 | ③ 集成 | T3 §7、INV-071、SCN-067 | 业务对象数量不增；只补切换；最终三字段一致 |

## AT-01 非共享派生建立独立身份、初始未物化

| 项 | 内容 |
| --- | --- |
| Given | 默认工作集 WS0 存在 Tracker `T0` 与 Requirement `R0`（`localId=L0`、`commonId=C1`、`revision=3`、`title=A`，已物化）；子工作集 `WS1` 尚未纳入 `T0` |
| When | 在 `WS1` 执行非共享派生（来源 `T0`，时间基准 Head） |
| Then | 创建新 Tracker `T1`（`sourceId=T0`）；创建 `LTI1`：`localId=L1`（全局唯一，≠L0）、`commonId=C1`、`sourceLocalId=L0`、`sourceRevision=3`、`revision=3`、`materialized=false`；不复制业务内容 |
| 断言 | `L1 != L0`；`LTI1.commonId == C1`；`LTI1.materialized == false`；`LTI1.sourceLocalId != LTI1.localId`；`T1.sourceId == T0`；Requirement 存储中不存在 `L1` 的内容记录 |
| 反例 | 若派生时复制了内容，则 AT-02 无法证明「读取来源」；若 `L1 == L0`，违反 INV-040 |
| 关联 | SCN-021、SCN-035、INV-026/040/041；T2 非共享派生 |

## AT-02 Source-backed 通过有效内容来源读取

| 项 | 内容 |
| --- | --- |
| Given | `LTI1` 处于 Source-backed（`sourceLocalId=L0`、`sourceRevision=3`、`materialized=false`） |
| When | `read(WS1, LTI1)` |
| Then | SK 沿 `sourceLocalId` **直接定位**实际内容拥有者（`L0` 已物化，无需逐级递归），调用 `RequirementReadPort.readAt(3)`，返回内容 `title=A` 与实际读取的 `revision=3` |
| 断言 | 返回内容 `title == "A"`；返回 revision `== 3`；`LTI1.materialized` 仍为 `false`；Requirement 存储中仍无 `L1` 记录 |
| 反例 | 若 SK 自行缓存或复制内容，`readAt` 调用次数断言会失败；若返回 `sourceRevision` 之外的 revision，违反「不得用当前值冒充」 |
| 关联 | D-013、INV-041/043、SCN-023 |

## AT-03 来源普通修改（Revision 不升级）与首次快照优先

| 项 | 内容 |
| --- | --- |
| Given | `LTI1` 未物化；来源 `R0` revision=3，`title=A` |
| When | ALM 执行普通修改 `title: A→B`（不调用显式升级）；来源命令在保存成功后立即返回 |
| Then | 保存后 `R0.revision` 仍为 `3`；发布 `RequirementChanged{beforeRevision=3, afterRevision=3, snapshotRef→A}` 并进入异步投递；`LTI1` 仍 `materialized=false`；`read(WS1)` 在最终一致性窗口内返回来源当前内容 `B` |
| When | 异步完成：等待 Modulith `event_publication.completion_date` 或目标 `materialized=true`（Awaitility） |
| Then | 用 `sourceLocalId=L0` + `beforeRevision=3` 定位目标（`materialized=false`）；调用 `RequirementWritePort.materialize(MaterializeRequest{targetLocalId=L1, sourceLocalId=L0, sourceRevision=3, targetRevision=LTI1.revision, snapshotRef})`；ALM 先核验快照来源身份/版次/commonId，再以**快照 A** 构建 `L1` 聚合并保存；随后 `LTI1.switchToLocalBacked(LTI1.revision)` |
| 断言 | `R0.revision == 3`；窗口内 `read(WS1)` 为 `B`；异步完成后 `LTI1.materialized == true`、`LTI1.sourceLocalId == L1`、`LTI1.sourceRevision == 3`、`L1` 内容 `title == "A"`（**不是 B**）、`read(WS1)` 返回 `A`、`read(WS0)` 返回 `B`；测试不得用固定 `Thread.sleep` |
| 反例 | 若实现读取来源当前值，物化内容会是 B；若把命令返回当作 COW 完成，窗口内断言会失败（D-011 首次快照优先 + 异步最终一致性） |
| 关联 | D-009/D-011、INV-034/037、SCN-025/030 |

## AT-04 物化后三字段一致与本地内容独立

| 项 | 内容 |
| --- | --- |
| Given | AT-03 已异步完成（`LTI1` 为 Local-backed） |
| When | `read(WS1, LTI1)`；随后来源 `R0` 再次普通修改 `title: B→C`，命令返回并异步完成 |
| Then | `LTI1` 的三字段满足 `materialized=true`、`sourceLocalId==localId`、`sourceRevision==revision`；`read(WS1)` 返回本地内容 A；来源修改为 C 后 `read(WS1)` 仍返回 A，`read(WS0)` 返回 C；异步窗口内 `LTI1` 不会回退为 Source-backed |
| 断言 | 三字段一致（聚合后置条件）；`read(WS1).title == "A"`；来源再改后 `read(WS1).title == "A"`；`LTI1.revision` 未因来源普通修改而变化；读取不依赖异步完成顺序 |
| 反例 | 若物化后仍被来源事件覆盖，违反 INV-039；若三字段不一致，违反 INV-069 |
| 关联 | T3 §6/§7、INV-039/069、SCN-032 |
| 实现级补充 | 来源/目标版次分离、快照缺失、来源身份/commonId 不匹配、既有目标证据不一致、切换失败可读回、Spring 装配与异常不冒泡等用例见 [S3 任务说明](t5-s3-codex-task-brief.md) §5 |

## AT-05 业务保存成功但 LightTrackerItem 切换失败

| 项 | 内容 |
| --- | --- |
| Given | `LTI1` 未物化；事件已消费，ALM 已按快照成功保存 `L1` 内容 |
| When | 模拟 `LightTrackerItem` 切换失败（例如 Repository 在 `save(LTI1)` 时抛异常一次） |
| Then | 业务内容 `L1` 存在；`LTI1.materialized` 仍为 `false`；不产生第二个 `L1`；对外不宣称已物化 |
| When | 触发重试（同一 `eventId + targetLocalId` 幂等键） |
| Then | **只执行切换**，不重建/不覆盖已存在的 `L1`；切换成功后三字段一致 |
| 断言 | Requirement 存储中 `L1` 记录数 == 1；`L1` 内容仍为 A；重试后 `LTI1.materialized == true`；幂等键未导致重复业务对象 |
| 反例 | 若重试重新构建并覆盖 `L1`，可能丢失已存在的业务状态；若把切换失败当作保存失败回滚，违反 T3 §7 |
| 关联 | T3 §7、INV-071、SCN-067 |

## AC-01 编译依赖检查（架构检查）

| 项 | 内容 |
| --- | --- |
| Given | 主代码与包结构（`sk.workingset.*`、`alm.requirement.*`、`bootstrap.*`）已编译 |
| When | 运行架构测试（建议 ArchUnit）或依赖分析 |
| Then | `sk.workingset..` **不依赖** `alm.requirement..`（任何包、任何形式）；SK 只引用 Port 接口与自身类型；`bootstrap..` 允许同时引用两侧 |
| 断言 | ArchUnit 规则通过：`noClasses().that().resideInAPackage("sk.workingset..").should().dependOnClassesThat().resideInAPackage("alm.requirement..")` |
| 反例 | SK Application 直接 `new RequirementAdapter()` 或引用 `Requirement` 类型 → 规则失败 |
| 关联 | A1、T4 §3.1/§3.3 |

## AC-02 身份稳定性检查

| 项 | 内容 |
| --- | --- |
| Given | 派生创建 LightTrackerItem（`localId=L1`），随后完成首次 COW |
| When | 物化前后分别读取 `localId`，并让 ALM 按 `L1` 定位业务变体 |
| Then | `localId` 恒为 `L1`；ALM 能通过 `L1` 定位到同一 Requirement 变体；不依赖任何额外内容指针 |
| 断言 | `derive` 后 `localId==L1`；`switchToLocalBacked` 后 `localId==L1`；`RequirementRepository` 可按 `L1` 找到刚物化的 Requirement（或映射等价） |
| 反例 | 物化时另分配新 `LocalId`，或 BC 需额外指针才能定位 → 检查失败 |
| 关联 | A2、T3、INV-040/070 |

## 测试 seam 说明

| seam | 位置 | 说明 |
| --- | --- | --- |
| `WorkingSetApplicationService.derive/resolve` | SK 应用层 | 测试通过公共方法观察，不测私有方法 |
| `CowEventHandler.handle(event)` | SK 应用层 | 事件-物化集成测试入口 |
| `RequirementApplicationService.changeTitle(...)` | ALM 应用层 | 模拟来源普通修改并产生事件 |
| `RequirementReadPort` / `RequirementWritePort` | SK 出站端口 | 测试用 ALM 适配器或可观察的测试替身（仅替身外部边界，不 mock 自己的领域对象） |
| `LightTrackerItem` 聚合行为 | SK 领域层 | 单元测试直接调用 `switchToLocalBacked` 并断言 L1～L4 |

## 不在本切片验证的内容

- Merge 执行（T1）与轮次终态；
- Tracker/WorkingSet 删除（T2）；
- `Denied` 及其余九分类（无权限系统）；
- K3″ 版本治理；
- 生产级持久化、并发压力、消息中间件可靠性。
