# S6 多级 WorkingSet 谱系实施验证

**状态：Reviewed（实施验证记录，2026-11-12）**

依据 [多级 WorkingSet 谱系设计](../domain/working-set-lineage-design.md)、FACT-028、[T2](../domain/binding-deletion-contract-v1.md) 和 [T1](../domain/merge-execution-contract-v1.md)。各片完成后分别运行 `mvn -B test`。最终回归使用本机 PostgreSQL、Redis 和内嵌 Tomcat，包含 ArchUnit。

## S6-A · 谱系、创建和回填

- [WorkingSet](../../../src/main/java/sk/workingset/domain/WorkingSet.java) 保存单一、创建后不可变的 `parentWorkingSetId`。仅默认根 `1` 的父级为空。创建子级校验父存在、自指和反向父子，数据库唯一键处理并发重复创建。创建接口不复制绑定。同父重复创建返回 `WorkingSetAlreadyExists`，以创建请求重挂现有子级返回 `WorkingSetParentImmutable`。
- [仓储及 JDBC 映射](../../../src/main/java/sk/workingset/infrastructure/JdbcBindingRepositories.java) 提供直属子级与祖先查询。祖先从直属父到根排列，递归查询带防环。
- [schema.sql](../../../src/main/resources/schema.sql) 增加父级列、自引用外键、根与非根约束及索引。S5 无父级的非根数据回填为首层子级，已存在的父级保持原值。
- [HTTP](../../../src/main/java/bootstrap/web/WorkingSetController.java) 提供 `POST /api/worksets/{parentId}/children`，请求体为 `{"workingSetId":"CHILD"}`，并提供 `GET /api/worksets/{id}/children` 与 `GET /api/worksets/{id}/ancestors`。现有详情增加父级字段。
- [验收](../../../src/test/java/bootstrap/WorkingSetLineageAcceptanceTest.java) 覆盖空范围、多级查询、不可重挂和真实 PostgreSQL 重复初始化。**61 项通过**。

## S6-B · 直属父级来源与子集

- [绑定及派生服务](../../../src/main/java/sk/workingset/application/WorkingSetApplicationService.java) 校验首次纳入时的父级范围、目标 SpaceBinding、槽位与共享策略。祖先、兄弟和父级范围外的 Tracker 均拒绝。非共享派生要求父级绑定与归属一致，新 Tracker 的 `sourceId` 指向直属父 Tracker。
- Shared 使用直属父的绑定作为准入证据，TrackerId 和原归属保持稳定。默认根沿用 S5 的原生 Tracker 投影，以归属根的 Tracker 作为其有效范围。这两点解释了为何共享来源的归属 ID 不必等于直属父 ID。
- Item 来源遵循 T3。父变体未物化时，新变体沿用其实际内容来源与来源 Revision。父变体已物化时，新变体指向父变体本身。
- [绑定验收](../../../src/test/java/bootstrap/BindingAcceptanceTest.java) 验证 Shared 复用、跨级和兄弟拒绝，以及父级解绑后保留已存在的子级绑定。全局显式 `deleteTracker` 继续按 T2 解除该聚合的引用。未增加谱系级联清理。
- [COW 验收](../../../src/test/java/bootstrap/CowAcceptanceTest.java) 验证同一实际内容来源对两级派生 Item 扇出，以及后续派生选择已物化父变体。**63 项通过**。

## S6-C · Merge 准入

[WorkingSetLineagePolicy](../../../src/main/java/sk/workingset/domain/WorkingSetLineagePolicy.java) 提供准入 seam。直属父子双向通过，兄弟和跨代拒绝。候选取自子级非共享绑定，空范围返回空候选。业务能力、Compare 响应和 Merge 执行由 T1 入口负责。

[策略测试](../../../src/test/java/sk/workingset/domain/WorkingSetLineagePolicyTest.java) 和持久化空范围验收通过。**66 项通过**。

## S6-D · 删除准入

[删除服务](../../../src/main/java/sk/workingset/application/WorkingSetDeletionService.java) 先保护默认根，再拒绝存在直属子级的工作集并返回 `WorkingSetHasChildren`。叶子按 T2 K3 预检成员，再依次解除 TrackerBinding、SpaceBinding 并删除 WorkingSet。WorkingSet 与 Tracker 不存在“停用”生命周期（2026-11-12 用户裁定），服务中不提供停用状态、命令或前置校验。

[真实 HTTP/JDBC 验收](../../../src/test/java/bootstrap/BindingAcceptanceTest.java) 验证中间节点拒绝后保留绑定和子级、叶子删除，以及创建子级与删除父级并发时无悬空父引用。最终结果为 **68 项通过，0 失败、0 错误、0 跳过**。

## 仍待验证

空范围的实际 Compare/Merge 响应、跨工作集的批量删除顺序、父级解绑后的既有子绑定清理，以及依真实平台元数据进行的多级历史回填。S6 已验证单工作集的删除顺序和 S5 首层回填。
