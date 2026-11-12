# D2：Requirement 家族唯一性按 WorkingSet 作用域验证

**状态：Reviewed（2026-11-12；实现与测试证据）**

依据：[POL-005 / FACT-005](../domain/facts.md)、[T2 绑定与删除](../domain/binding-deletion-contract-v1.md)、[S7 验证](t5-s7-verification-result.md)。

用户裁定（2026-11-12）：CommonId 的唯一性按 **WorkingSet** 作用域，不按 Tracker 作用域。本记录只覆盖这一修正；D1 的 `BusinessObject` 设计见 [business-object-lifecycle-design.md](../domain/business-object-lifecycle-design.md)。

## 1. 数据库变更

- `requirements` 增加 `working_set_id`，由 `trackers.working_set_id` 派生；写入时由 `trg_requirements_working_set` 触发器填充，不改变 `Requirement` 领域聚合。
- 删除旧索引 `uq_requirements_tracker_family`，新增：
  ```sql
  CREATE UNIQUE INDEX uq_requirements_working_set_family
      ON requirements (working_set_id, common_id);
  ```
- 旧数据回填：`UPDATE requirements ... FROM trackers ... WHERE working_set_id IS NULL`。
- 启动时检查同一 WorkingSet 内重复 CommonId；存在重复时以明确错误中止，要求先按真实身份事实消解。
- 历史或未登记 Tracker 的行允许 `working_set_id` 为空；唯一索引忽略 NULL；新写入必须能解析 Tracker，否则触发器报错。
- 当前唯一索引覆盖软删行：`delete` 后同一家族仍占用唯一键；`delete2` 永久删除后才释放。该语义与“同一家族只允许一个有效变体”一致，后续若要允许软删后重建需重新裁定。

## 2. 应用行为

- 同一 WorkingSet、不同 Tracker 的相同 CommonId → `DataIntegrityViolationException`；REST 映射为 409 `DATA_INTEGRITY_CONFLICT`。
- 不同 WorkingSet 的相同 CommonId → 允许。
- Requirement 创建必须能解析 Tracker；未登记 Tracker 的写入被数据库拒绝。
- Repository/聚合仍不持有 `working_set_id`；它是实现映射，不反向定义领域所有权。

## 3. 测试

- 新增 [RequirementFamilyScopeTest](../../../src/test/java/bootstrap/RequirementFamilyScopeTest.java)：同 WS 跨 Tracker 重复被拒；跨 WS 允许；清理时先删派生子 Tracker 再删父 Tracker。
- 更新 [BindingAcceptanceTest](../../../src/test/java/bootstrap/BindingAcceptanceTest.java) 的测试夹具：每个业务 Item 使用独立 CommonId，避免同一 WS 内多人造 Item 共享家族；这是测试数据修正，不是放宽领域约束。
- `mvn -B test`：**77 项通过，0 失败、0 错误、0 跳过**（含真实 PostgreSQL、Redis、Tomcat、ArchUnit）。

## 4. 迁移注意

1. 上线前查询同一 WorkingSet 内重复 CommonId；有重复则按真实身份事实消解，不能自动删除。
2. 旧 Requirement 若 Tracker 未登记，保留 `working_set_id` 为空；后续创建同家族时由业务决定迁移/清理。
3. 软删行占用唯一键；需要“软删后重建同家族”时必须先 `delete2` 或重新裁定。
4. 不引入 Flyway/Liquibase；继续 `schema.sql` 的 `IF NOT EXISTS` 风格。

## 5. 未覆盖

- 真实平台元数据的批量回填与重复消解。
- 跨 WorkingSet 的家族身份是否允许（本实现允许；POL-005 只约束单 WS）。
- 软删后重建同家族的策略。
