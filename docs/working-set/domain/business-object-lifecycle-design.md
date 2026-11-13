# BusinessObject 生命周期能力设计

**状态：Reviewed（2026-11-12；D1 用户裁定已记录，D14 已落地实现；不构成基线发布）**

- 依据：[T4 跨 BC 公共契约](cross-bc-contract-v1.md)、[T2 绑定与删除](binding-deletion-contract-v1.md)、[S7 验证](../implementation/t5-s7-verification-result.md)
- 范围：`BusinessObject` / `TrackerItem` 的公共生命周期能力声明
- 不包含：具体删除规则、guard、编排实现；这些由各业务 BC 拥有

## 1. 用户裁定（2026-11-12）

1. `TrackerItem` 继承顶级公共接口 `BusinessObject`。
2. `BusinessObject` 声明所有业务对象共有的生命周期能力：`delete`（软删）、`undelete`（恢复）、`delete2`（永久删除）。
3. 具体删除规则、guard、删除编排由各业务 BC 实现；能力公开，所有 BC 的所有 Object 都具备。
4. `TrackerItemReadPort` 仍保持只读；生命周期能力不是通用 SK 写入口。

## 2. 接口定位

```text
BusinessObject（SK 顶级公共能力）
  ├─ localId / commonId
  ├─ delete / undelete / delete2（能力声明）
  └─ TrackerItem（领域公共契约）
       ├─ revision
       └─ 业务聚合实现（Requirement、Part、GateFlow 对象、DQA 对象）
```

候选签名（D14 已按此落地到 `sk.workingset.domain`）：

```java
public interface BusinessObject {
    LocalId localId();
    CommonId commonId();
    LifecycleResult delete(LifecycleCommand command);
    LifecycleResult undelete(LifecycleCommand command);
    LifecycleResult delete2(LifecycleCommand command);
}

public interface TrackerItem extends BusinessObject {
    Revision revision();
}
```

- `LifecycleCommand` 承载期望并发版本（`expectedLockVersion`，可空表示调用方已锁定）与业务原因；**不携带调用者上下文**，授权由应用层在调用前完成（D11 暂不接入权限）。
- `LifecycleResult` 表达 Applied／Rejected／Conflict 并携带变换后的对象；`delete2` 返回当前对象表示“允许由应用服务编排永久删除”，不把失败伪装成成功。
- `delete2` 是 guard/decision 结果，实际永久删除由 BC 应用服务编排；不要求 SK 提供通用删除实现。
- 不允许 SK 通过 `BusinessObject` 直接写业务表或绕过 BC 聚合校验。

## 3. 与 T2/T4 的边界

- T2 的 K1～K3 只约束 Tracker、ProductSpace、WorkingSet 的绑定/删除；**业务 TrackerItem 删除不在 T2 契约内**。
- S7 的 `Requirement.delete`/`undelete`/`delete2` 是 ALM 业务对象的实现，不是 Working Set 聚合删除规则。
- T4 的 `TrackerItemReadPort` 继续只读；`BusinessObject` 是领域公共能力，不替代 `RequirementWritePort` 等 BC 写入协作 Port。
- 该扩展改变 T4 §3.1 对 `TrackerItem` “只读”的表述，必须经接口评审/ADR 记录；不得静默改写冻结契约。

## 4. 各 BC 的责任

| 责任 | 归属 |
| --- | --- |
| 删除规则、guard、状态转换 | 各业务 BC 聚合/应用服务 |
| 删除编排、持久化、事件 | 各业务 BC |
| 公共能力声明与统一语言 | SK `BusinessObject` |
| 跨 BC 生命周期调用 | 通过各 BC 的公开 Port，不直接调用其他 BC 的聚合 |

## 5. 测试与追溯

- ALM 现有 `delete`/`undelete`/`delete2` 验收作为首个实现证据；不代表其他 BC 已实现。
- 其他 BC 接入时必须补齐自身删除规则、guard、失败语义和测试。
- D14 已把 `BusinessObject` 与 `TrackerItem` 继承加入生产接口（`sk.workingset.domain`），`Requirement` 实现三个能力；`RequirementLifecycleService.delete2` 先调用聚合 guard 再永久删除。`mvn -B test` 78 项通过。

## 6. 开放问题

1. ~~`LifecycleCommand` / `LifecycleResult` 的精确字段与返回类型。~~ D14 已定（见 §2）。
2. ~~`delete2` 是聚合方法、应用服务方法，还是两者分层。~~ D14 已定：聚合提供 guard，应用服务编排实际删除。
3. 所有 BC 是否必须实现三个方法，还是允许能力声明 + 拒绝不支持的操作（仍开放）。
4. 跨 BC 删除是否只经各 BC 的公开 Port，是否需要统一事件——D4/D13 已用 `RequirementPurged` 事件驱动 SK 侧清理；其他 BC 接入时沿用同一模式。
5. ~~T4 §3.1 的修订方式：补充说明、ADR-0006，还是新契约版本。~~ D14 已按补充说明更新 T4 §3.1/§8。
