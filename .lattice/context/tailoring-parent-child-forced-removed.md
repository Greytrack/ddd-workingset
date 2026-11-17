---
feature: "裁剪父子结构 — 强制移除与划线展示"
requirement_doc: null
created: 2026-06-16
status: "Implemented"
decisions: 5
open_questions: 0
constraints:
  - "子节点内在 tailoringStatus 不因父移除而改写"
  - "复用 RemovedDueToParentElementRemoval（tailoringForcedRemovedByParent）"
  - "划线规则 A：REMOVED 或 tailoringForcedRemovedByParent=是"
  - "仅显式 TAILOR_IN 上拉父链；NOT_TAILORED 子不触发父上浮"
---

# 裁剪父子结构 — 强制移除与划线展示

> 调整父子裁剪传播语义：父裁出后子保持自身状态并标记 forcedRemovedByParent；显式裁入上拉父链；统一树/Index/Graph 展示契约以支持划线删除态。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-06-16 | 划线规则 A：`tailoringStatus=REMOVED` 或 `tailoringForcedRemovedByParent=是` | 用户确认；`FORCED_VISIBLE` 不划线 | 仅 REMOVED 划线；后端计算 displayAsRemoved |
| 2026-06-16 | 仅显式 `TAILOR_IN` 上拉父链 | 用户确认「强制载入=手动裁入」 | 维持现状：NOT_TAILORED 视同可见并上浮父 |
| 2026-06-16 | `forcedRemovedByParent` 采用条件粘性 | 父因一子裁入变 FORCED_VISIBLE 后，其他兄弟仍保持 forcedRemoved；仅本元素显式 TAILOR_IN 时清除自身标记 | 纯重算（兄弟会丢标记）；纯粘性（裁入子无法取消划线） |
| 2026-06-16 | 父下新增子与其他兄弟一致 | 新增子默认 NOT_TAILORED + forcedRemovedByParent=是（划线）；无需与当前实现一致 | 新增子不参与 forcedRemoved |
| 2026-06-16 | Implementation complete | ParentChildPostProcessor 条件粘性 + hasUpliftableChild；TailoringDisplaySupport；树/Index/Graph DTO 与填充 | — |
| 2026-06-16 | Design approved at Level 4. Blueprint complete ready for implementation. | Level 1–4 逐层确认并持久化 | — |
| 2026-06-17 | 修复未裁剪（NOT_TAILORED）祖先链无法被子孙显式裁入（TAILOR_IN）向上穿透上浮的 Bug | 1. 允许 forcedRemovedByParent=是 且拥有有效子孙的节点计算 newlyForcedVisibleByChild 逻辑<br>2. 修正 statusChanged 检查逻辑，在 parent/child 标记变化时正常触发新轮次重算以使状态扩散<br>3. 避免 forcedVisibleByChild 的节点被父级连带强制移除 | 无 |

## Open Questions

（已全部解决）

## Constraints

- 子节点内在 `tailoringStatus` 不因父移除而改写
- 复用 `RemovedDueToParentElementRemoval` → API `tailoringForcedRemovedByParent`
- 版本比对：`REMOVED` 仍视为业务不存在；因父连带移除的子（NOT_TAILORED + forcedRemoved）仍展示
- 范围：后端传播逻辑 + 展示契约（树/Index/Graph/元模型示例）；前端按字段划线

## Key Files

- `components/gf/sdkImpl/.../tailoring/ParentChildPostProcessor.java` — 父子传播核心（条件粘性 + TAILOR_IN 可见子集）
- `components/gf/sdkImpl/.../tailoring/TailoringAncestorChainRecalculator.java` — 结构变更后重算
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/tailoring/TailoringDisplaySupport.java` — 划线判定（新建）
- `components/gf/biz/.../ProcessElementTreeSupport.java` — 元素树组装
- `components/gf/biz/.../ProcessElementTreeResp.java` — 树响应 DTO
- `components/gf/sdk/.../datasource/dto/ProcessElementIndexData.java` — Index 行 DTO
- `components/gf/sdkImpl/.../datasource/buildin/handler/IndexHandler.java` — Index 行
- `components/gf/sdkImpl/.../tailoring/GraphTailoringItemsBuilder.java` — 流程图裁剪标记
- `components/gf/sdk/.../dto/TailoringAggregateResult.java` — 聚合结果 DTO（已有，不扩展）

## Design: Level 1 -- Capabilities

1. **父裁出后子保持自身裁剪状态**：子 `tailoringStatus` 保持聚合结果，不被改写为 REMOVED。
2. **子节点标记因父移除而强制移除**：父 REMOVED 时子孙 `tailoringForcedRemovedByParent=是`；含父下新增子（NOT_TAILORED）。
3. **划线删除态展示**：`REMOVED` 或 `tailoringForcedRemovedByParent=是` 时划线；`FORCED_VISIBLE` 不划线。
4. **仅显式裁入上拉父链**：手动 `TAILOR_IN` 后祖先链变 `FORCED_VISIBLE`；默认 NOT_TAILORED 子不上拉父。
5. **展示契约统一**：树/Index/Graph 输出 `tailoringForcedRemovedByParent` 与 `tailoringForcedVisibleByChild`。

## Design: Level 2 -- Components

| # | 组件 | 层 | 职责 |
|---|------|-----|------|
| 1 | `ParentChildPostProcessor` | sdkImpl / tailoring | 条件粘性 forcedRemoved、仅 TAILOR_IN 计入 visible 子集、FORCED_VISIBLE+forcedVisibleByChild 祖先向下传播 |
| 2 | `TailoringAncestorChainRecalculator` | sdkImpl 编排 | 裁剪/结构变更后子树重算并落库 |
| 3 | `TailoringDisplaySupport`（新建） | sdk / tailoring | 统一划线判定 `isDisplayAsRemoved(status, forcedRemovedByParent)` |
| 4 | `IInnerTailorable.applyTailoringAggregate` | SDK inner | 聚合结果写入概览属性（已有） |
| 5 | `ProcessElementTreeSupport` | biz | 树响应填充 forced 字段 |
| 6 | `IndexHandler` | sdkImpl 读路径 | Index 行输出 forced 字段 |
| 7 | `GraphTailoringItemsBuilder` | sdkImpl 读路径 | forcedRemoved 时输出 element 级 graph 裁剪项 |
| 8 | 展示 DTO | sdk / biz | `ProcessElementTreeResp`、`ProcessElementIndexData` 增加 forced 字段 |

```
TailoringDecisionAggregator
        │ aggregateByElement
        ▼
ParentChildPostProcessor  ← 核心变更
        │
   ┌────┼────┐
   ▼    ▼    ▼
applyAggregate  TreeSupport  IndexHandler / GraphBuilder
                      └── TailoringDisplaySupport（划线判定复用）
```

## Design: Level 3 -- Interactions

### 流程 1：父节点裁出（TAILOR_OUT）

```
用户 TAILOR_OUT(父 P)
  → TailorOutHandler → ManualOperationSupport → AncestorChainRecalculator
  → aggregate: P=REMOVED, 子=各自内在状态
  → ParentChildPostProcessor: 子 forcedRemovedByParent=是, status 不变
  → applyTailoringAggregate 落库 → 读路径按规则 A 划线
```

### 流程 2：子显式裁入（TAILOR_IN）

```
用户 TAILOR_IN(子 A)
  → recalculateAfterElementChange(A, intrinsic)
  → visibleElements 仅含 finalAction=TAILOR_IN 且 status∈{VISIBLE,FORCED_VISIBLE}
  → P: REMOVED → FORCED_VISIBLE + forcedVisibleByChild=是
  → A: isExplicitTailorIn → forcedRemovedByParent=否
  → B（兄弟）: 条件粘性 → forcedRemovedByParent=是（保持）
```

### 流程 3：父下新增子节点

```
batchCreate → recalculateAfterStructureChanges([新子 C])
  → C: NOT_TAILORED
  → 祖先判定含 FORCED_VISIBLE+forcedVisibleByChild → C forcedRemovedByParent=是
```

### 流程 4：读路径展示

```
IProcessElementsInner
  → ProcessElementTreeSupport / IndexHandler / GraphTailoringItemsBuilder
  → TailoringDisplaySupport.isDisplayAsRemoved(...) 统一划线判定
```

### ParentChildPostProcessor 单轮算法

1. **removedAncestors**：`REMOVED` 或 `FORCED_VISIBLE && forcedVisibleByChild`
2. **visibleChildren（上拉父链）**：`finalAction==TAILOR_IN` 且 `status∈{VISIBLE,FORCED_VISIBLE}`
3. **forcedRemovedByParent**：`(hasRemovedAncestor || prev.forcedRemoved) && !isExplicitTailorIn`
4. **forcedVisibleByChild**：`(status==REMOVED && hasVisibleChild) || prev.forcedVisibleByChild`
5. **finalStatus**：`forcedVisibleByChild ? FORCED_VISIBLE : intrinsic.status`

### 边界

- 版本比对：仅 `REMOVED` 视为不存在
- 多轮传播：`tailoringStatus` 变化触发新轮（父 REMOVED→FORCED_VISIBLE）

## Design: Level 4 -- Contracts

### `TailoringDisplaySupport`（新建）

路径：`components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/tailoring/TailoringDisplaySupport.java`

```java
public final class TailoringDisplaySupport {
    public static boolean isDisplayAsRemoved(
            ElementTailoringStatusListEnum tailoringStatus,
            boolean forcedRemovedByParent);
    public static boolean isDisplayAsRemoved(TailoringAggregateResult result);
    public static boolean isDisplayAsRemoved(IProcessElementsInner element);
}
```

### `ParentChildPostProcessor` 私有判定

- `isExplicitTailorIn(agg)` — 仅显式裁入计入可见子
- `isLogicalRemovalAncestor(agg)` — REMOVED 或 FORCED_VISIBLE+forcedVisibleByChild
- `hasLogicalRemovalAncestor(...)` — 向上查找逻辑裁出祖先
- `resolveForcedRemovedByParent(...)` — 条件粘性计算

### 展示 DTO 扩展

**`ProcessElementTreeResp` / `ProcessElementIndexData`**：

```java
private Boolean tailoringForcedRemovedByParent;
private Boolean tailoringForcedVisibleByChild;
```

填充：`YesOrNoListEnum.YES == element.getRemovedDueToParentElementRemoval()` / `getForceDisplayOfFactorElement()`

### 读路径

- `ProcessElementTreeSupport.buildElementNode` — 填充 forced 字段
- `IndexHandler.buildIndexRow` — 同上
- `GraphTailoringItemsBuilder` — `TailoringDisplaySupport.isDisplayAsRemoved(element)` 时输出 `type="element"` 项

### 持久化（已有，不变）

| 聚合字段 | PLM 属性 | API 字段 |
|----------|----------|----------|
| `forcedRemovedByParent` | `RemovedDueToParentElementRemoval` | `tailoringForcedRemovedByParent` |
| `forcedVisibleByChild` | `ForceDisplayOfFactorElement` | `tailoringForcedVisibleByChild` |
| `tailoringStatus` | `TailoringStatus` | `tailoringStatus` |

### 测试契约

| 测试类 | 用例 |
|--------|------|
| `ParentChildPostProcessorTest` | NOT_TAILORED 不上浮父；裁入子清除 forcedRemoved；兄弟粘性；FORCED_VISIBLE 祖先下新增子 |
| `TailoringDisplaySupportTest` | 规则 A 四种组合 |
| `GraphTailoringItemsBuilderTest` | forcedRemoved 输出 element 级 item |
| `TailoringAncestorChainRecalculatorTest` | 调整 NOT_TAILORED 相关期望 |

### 明确不改

- `ElementTailoringStatusListEnum` 枚举值
- 版本比对 `isTailoringRemoved` 语义
- `PropertiesDataSourceHandler`（已支持）
- 前端代码（本仓库无）

---

## Design Summary

### 组件与分层

| 层 | 组件 | 变更类型 |
|----|------|----------|
| sdkImpl / tailoring | `ParentChildPostProcessor` | **核心修改** |
| sdk / tailoring | `TailoringDisplaySupport` | **新建** |
| sdkImpl / tailoring | `GraphTailoringItemsBuilder` | 修改 |
| sdkImpl / datasource | `IndexHandler` | 修改 |
| biz / process | `ProcessElementTreeSupport` | 修改 |
| sdk / biz DTO | `ProcessElementIndexData`, `ProcessElementTreeResp` | 字段扩展 |

### 核心契约

- **传播**：条件粘性 `forcedRemovedByParent`；仅 `TAILOR_IN` 上拉父链；逻辑裁出祖先含 `FORCED_VISIBLE+forcedVisibleByChild`
- **展示**：规则 A — `REMOVED || forcedRemovedByParent` → 划线
- **持久化**：复用现有概览属性，无 schema 变更

### 架构约束

- 传播逻辑留在 sdkImpl；展示组装在 biz / Handler 读路径
- 版本比对 `REMOVED` 语义不变
- 遵循 Biz → SDK → SDKImpl 分层

### 设计状态

**Implemented — tests passing**

### 实现记录（2026-06-16）

| 文件 | 说明 |
|------|------|
| `components/gf/sdkImpl/.../ParentChildPostProcessor.java` | 条件粘性 + 仅 TAILOR_IN 上浮 + 逻辑裁出祖先传播 |
| `components/gf/sdk/.../TailoringDisplaySupport.java` | 规则 A 划线判定 |
| `components/gf/sdk/.../ProcessElementIndexData.java` | forced 字段 |
| `components/gf/biz/.../ProcessElementTreeResp.java` | forced 字段 |
| `components/gf/biz/.../ProcessElementTreeSupport.java` | 树节点填充 forced 字段 |
| `components/gf/sdkImpl/.../IndexHandler.java` | Index 行填充 forced 字段 |
| `components/gf/sdkImpl/.../GraphTailoringItemsBuilder.java` | forcedRemoved 时输出 element 级 graph 项 |

**单测**：`ParentChildPostProcessorTest`(7)、`TailoringAncestorChainRecalculatorTest`(8)、`GraphTailoringItemsBuilderTest`(11)、`TailoringDisplaySupportTest`(6) — 全部通过。

**前端划线**：`tailoringStatus === 'REMOVED' || tailoringForcedRemovedByParent === true`
