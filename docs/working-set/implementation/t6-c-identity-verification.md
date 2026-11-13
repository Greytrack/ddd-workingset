# T6-C 身份分配与唯一冲突验证

**状态：Reviewed（2026-11-13，实施证据）**

依据 [T4 §4](../domain/cross-bc-contract-v1.md)、[POL-005](../domain/facts.md)、[QUESTION-004](../domain/open-questions.md) 与用户 2026-11-13 裁定。

## 1. 用户确认的规则

- **默认工作集**：服务端生成一个雪花 ID，**同时作为 LocalId 与 CommonId**；创建请求不携带 CommonId。
- **非默认工作集**：服务端生成两个**不相等**的雪花 ID，分别作为 LocalId 与 CommonId。
- **派生**：CommonId 沿用来源；LocalId 由服务端生成且与 CommonId 不等。
- **同族唯一冲突**（含软删占键 D16）：统一返回 409 `DATA_INTEGRITY_CONFLICT`，不新增失败分类。

## 2. 实现

- [RequirementApplicationService](../../../src/main/java/alm/requirement/application/RequirementApplicationService.java)：创建入口按 `TrackerScopePort` 判定归属工作集，生成同一或两个不等雪花；显式指定非默认工作集时，非共享 Tracker 必须属于该工作集。
- [CreateRequirementRequest](../../../src/main/java/bootstrap/web/CreateRequirementRequest.java)：不再包含 `commonId`，身份由服务端分配。
- [MergeExecutionService](../../../src/main/java/sk/workingset/application/MergeExecutionService.java)：`CREATE` 到默认工作集时使用 CommonId 作为 LocalId，保证二者相等；非默认工作集使用新雪花。
- [WorkingSetApplicationService](../../../src/main/java/sk/workingset/application/WorkingSetApplicationService.java)：派生生成新 LocalId，并确保与来源 CommonId 不等。
- Tracker 不存在时不提前拦截身份分配，交由持久化层按既有约束返回冲突（与 D10 的兼容行为一致）。

## 3. 验收

- [RequirementCreateScopeTest](../../../src/test/java/alm/requirement/application/RequirementCreateScopeTest.java)：默认 WS 同一 ID、非默认 WS 两个不等 ID、共享 Tracker 任意工作集、非共享错误工作集 `NotIncluded`、Tracker 不存在交给持久化层（5 项）。
- [RequirementAcceptanceTest](../../../src/test/java/bootstrap/RequirementAcceptanceTest.java)：HTTP 创建默认工作集 Requirement，断言 `commonId == localId`。
- [BindingAcceptanceTest](../../../src/test/java/bootstrap/BindingAcceptanceTest.java)：删除 Tracker 后创建返回 409 `DATA_INTEGRITY_CONFLICT`；并发创建与删除场景保持通过。
- [RequirementFamilyScopeTest](../../../src/test/java/bootstrap/RequirementFamilyScopeTest.java)：requirements 与 LTI 的同工作集家族唯一索引、软删占键。

`mvn -B clean test`：**88 项通过，0 失败、0 错误、0 跳过**（含 ArchUnit、真实 PostgreSQL/Redis/Tomcat）。

## 4. 未覆盖

- 跨实例并发创建/派生的更细去重协议（QUESTION-004 剩余）。
- 回并父工作集的标识分配（T6-D）。
- 真实平台元数据回填中的既有重复身份消解（D12-A）。
