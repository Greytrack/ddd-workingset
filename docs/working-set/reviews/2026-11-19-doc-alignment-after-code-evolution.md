# 2026-11-19 代码演进后的文档对齐

**状态：Reviewed（用户要求同步最新代码与边界；不新增业务基线，不把实现反推为业务事实）**

## 1. 任务与范围

用户要求：

1. 读取仓库最新内容和近期改动、提交记录，修正过时设计声明；
2. 移除冗余废弃声明与设计，修正相关图示；
3. 把新设计或边界约定补充进设计文档；
4. 只改设计文档，不改代码。

本轮只修改 `docs/working-set/` 与根目录文档；不修改 Java、POM、SQL 或测试。

## 2. 证据

- 最近提交：`543cc07`、`2aee3c8`、`f19b59a`、`28e6ff7`、`6d226a4`、`26ab8b4`、`5d74dc3`、`2c50900`。
- 当前工作区：`sk.businessframework`、`sk.tracker`、`sk.productspace` 包拆分；`WorkingSetRepository`/`LightTrackerItemRepository` 命令侧收敛；`TrackerQueryProvider`、`LightTrackerItemQueryProvider`、`ClassificationCatalog` 等查询 Provider；`RequestThreadHolder` 等未完成文件。
- 相关对话：移除 `TrackerItemLifecyclePort`、`Caller`、LightTrackerItem 缓存；首次继承编辑改为 `BusinessObjectUpdateEvent` 异步切换；COW 改为 `TrackerItemMaterializationRequestedEvent`/`TrackerItemMaterializationCompletedEvent`；TC-002 单聚合写事务约束。

## 3. 文档修改

| 文档 | 修改 |
| --- | --- |
| [BASELINE](../BASELINE.md) | 更新日期/基线下沿说明，登记 2026-11-19 实现边界变更与文档对齐；不改变既有接受范围。 |
| [当前实现边界](../implementation/current-boundaries.md) | 重写为当前模块、读取/写入边界、事件流和 TC-002 差距；移除已废弃 Port/缓存/Caller 声明。 |
| [跨 BC 契约](../domain/cross-bc-contract-v1.md) | D4 改为“允许只读使用 SK 共享聚合/仓储、禁止直接写入”；补充继承编辑事件；删除 `Caller`/`authorize` 占位和 `TrackerItemLifecyclePort` 表述。 |
| [Shared Kernel 边界](../domain/shared-kernel-boundary.md) | 增加 SK 内部模块边界与直接只读约定；权限行不再声称存在 `Caller` 占位。 |
| [聚合边界](../domain/aggregate-model.md) | 增加 `businessframework` 通用能力与 Working Set 模型的区分；补充直接读取与事件写入边界；移除旧 Port 依赖。 |
| [开放问题](../domain/open-questions.md) | 关闭项 `QUESTION-003` 改为“无 `Caller` 占位；权限接入仍待定”。 |
| [CQRS 记录](../implementation/cqrs-query-side.md) | 移除 Redis 缓存声明，更新 Provider 现状和最近测试记录。 |
| [实施索引](README.md) | 用最近可复现测试记录替换过期的 165 项声明，链接当前边界。 |
| [跨 BC 图](../diagrams/cross-bc-contract.md)、[物化图](../diagrams/lighttrackeritem-materialization.md)、[Shared Kernel 图](../diagrams/shared-kernel-context-map.md) | 补充直接只读、异步继承编辑和内部模块说明；不改变图中既有业务语义。 |
| [BusinessObject 历史设计](../domain/business-object-lifecycle-design.md) | 删除对已移除 `RequirementWritePort` 的“不替代”表述，改为历史说明。 |

## 4. 新增/明确的设计边界

以下内容来自用户 2026-11-19 的直接要求（移除单方法 Port、文档对齐）以及当前实现边界，本轮作为 **Reviewed 实现边界**记录，不自动升级为新的业务事实或 Accepted 契约：

1. **单聚合写事务（TC-002）**：一个写事务最多修改一个聚合；跨聚合状态变更使用可靠事件、独立事务最终收敛。
2. **直接只读边界**：业务 BC 可以只读使用 SK 的共享聚合/仓储；不得调用保存、删除或修改 SK 聚合状态；不再为单查询方法新增专用同步 Port。
3. **继承编辑事件化**：ALM 保存首次继承编辑后发布 `BusinessObjectUpdateEvent`，SK 监听器独立切换 LTI；不回滚已提交业务保存。
4. **COW 两阶段**：SK 发实例化请求，ALM 保存并返回完成/失败事件，SK 独立切换 LTI；失败终态保留，不恢复已废弃的 Recovery。
5. **Merge 事件化**：`MergeEntryApprovedEvent` → ALM 保存 → `MergeEntryResolvedEvent` → SK 记录结果 → `MergeAppliedEvent` 驱动 LTI。
6. **SK 内部模块**：`businessframework` 是通用共享能力，不是 Working Set 聚合模型；`tracker`、`productspace`、`workingset` 是 SK 内部模块，是否成为独立 BC 仍待证据。
7. **已移除**：`Caller`、`TrackerItemLifecyclePort`、`RequirementWritePort`、LightTrackerItem Redis 缓存和 `item-cache` 配置。

## 5. 未解决/未验证

- ProductSpace 创建仍跨聚合同事务；Merge 结果与 Checkpoint 仍存在同事务路径；T7 进度仍待拆分。
- 当前工作区新增 `sk.businessframework` / `RequestThreadHolder` 未完成全量编译和测试；本轮不声称其已交付。
- `tracker`/`productspace` 对 `workingset` 的同进程依赖尚未收敛为稳定跨 BC 合同。
- 本轮不新建 ADR，也不把“直接只读”或模块拆分升级为 Accepted 业务基线；如需长期约束，应由用户确认后新建 ADR 并更新 BASELINE。

## 6. 验证

- 文档链接与 Mermaid 图人工检查。
- 未运行代码测试；用户明确要求只改设计文档。
