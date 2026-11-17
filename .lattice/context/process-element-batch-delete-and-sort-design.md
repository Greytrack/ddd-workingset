---
feature: "Process Element Batch Delete and Sort"
requirement_doc: null
created: "2026-06-15"
---

# Process Element Batch Delete and Sort

> 支持过程元素（Process Element）的批量删除和拖拽排序/重排序。

## Decisions Log

<!-- Add new at bottom. Never remove. -->

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-06-15 | 方案 A: 通过 execute_widget_operation 接口集成 | 与现有 Widget 框架统一，利用统一入口的事务控制与操作日志记录，减少暴露新 HTTP 接口。 | 方案 B: 暴露独立 REST 接口 |
| 2026-06-15 | 设计在 Level 4 获批，蓝图完成，准备实现 | 满足所有用例与架构规约，已完成 4 个设计层级的推演。 | - |

## Open Questions

<!-- When resolved, capture as decision above and remove from here. -->

## Constraints

<!-- Non-negotiable once recorded. Add only when confirmed. -->

## Key Files

<!-- Add as dev progresses. List paths with brief role note. -->

## Design: Level 1 -- Capabilities

### 1. 批量删除过程元素 (Batch Delete Process Elements)
* **功能描述**：支持用户在过程元素树上多选非根节点/非分类节点，并一键删除。
* **级联规则**：删除一个元素时，递归删除该元素底下的所有子孙节点。
* **合法性校验**：
  - 禁止删除过程的根节点及各内置分类节点。
  - 被删除的所有元素必须属于当前操作的过程。
* **副作用处理**：
  - 批量删除后，需针对各被删元素的原父节点（去重后）重新计算裁剪链条状态。
  - 清理受影响元素在 Redis 中的流程图缓存（Graph Cache）。

### 2. 过程元素拖拽排序/重组 (Drag and Sort Process Elements)
* **功能描述**：支持用户在树形结构上拖拽某一元素，并放置到参考元素的前面（`BEFORE`）、后面（`AFTER`）或作为其子节点（`CHILD`）。
* **三种排序类型行为**：
  - `BEFORE`：将元素移至参考元素前，新的 `parentId` 与参考元素一致，新的 `rank` 介于参考元素与其前一兄弟元素之间。
  - `AFTER`：将元素移至参考元素后，新的 `parentId` 与参考元素一致，新的 `rank` 介于参考元素与其后一兄弟元素之间。
  - `CHILD`：将元素作为子元素挂载在参考元素下，新的 `parentId` 变更为参考元素 ID，排序处于该子级层级的最末尾（最大 `rank`）。
* **合法性校验**：
  - **禁环校验**：不能拖拽到自身，且不能拖拽到自身的子孙节点下。
  - **跨过程限制**：只允许在同一个过程版本内移动元素，禁止跨过程拖拽。
  - **类型限制**：根节点与分类节点禁止被拖拽移动。
  - **权限校验**：需验证目标父节点是否具备创建子元素的权限。
* **副作用处理**：
  - 重新计算受影响节点及新旧父节点层级的裁剪状态链。
  - 清理旧父级与新父级的流程图缓存。

## Design: Level 2 -- Components

### 1. 组件与分层架构映射

| 组件名称 | 所在分层 | 职责描述 | 依赖关系 | DDD 分类 |
| :--- | :--- | :--- | :--- | :--- |
| **ProcessRest** | Biz 层 (gf-biz) | 暴露外部统一 HTTP/REST 端口 `/execute_widget_operation`，入参校验。 | 依赖 `gf-sdk`, `WidgetOperationManagement` | 外部接口层 |
| **WidgetOperationManagement** | Biz 层 (gf-biz) | 业务编排与事务控制层，记录操作历史（History Event）。包装 `executeOperation` 执行结果。 | 依赖 `gf-sdk` 接口 (`IProcessService`) | 应用服务层 (Orchestrator) |
| **WidgetOperationType** | SDK 接口层 (gf-sdk) | 定义 Widget 操作类型枚举，新增 `BATCH_DELETE` 和 `DRAG_ELEMENT` 枚举值。 | 无依赖 | 契约/值对象 |
| **IProcessService** | SDK 接口层 (gf-sdk) | 过程服务接口，定义 `executeOperation` 入口。 | 无外组件依赖 | 应用服务接口 |
| **ProcessServiceImpl** | SDKImpl 实现层 (gf-sdkimpl) | 应用服务实现，通过 `OperationHandlerFactory` 调度对应的处理器，完成 Context 解析及执行。 | 依赖 `gf-sdk`, `OperationHandlerFactory` | 应用服务实现 |
| **BatchDeleteElementsDataModificationHandler** | SDKImpl 实现层 (gf-sdkimpl) | 批量删除过程元素的处理器，解析参数并循环执行删除。 | 依赖 `ProcessObjectLoader`, `TailoringAncestorChainRecalculator` | 领域处理器 (Domain Handler) |
| **DragElementDataModificationHandler** | SDKImpl 实现层 (gf-sdkimpl) | 拖拽排序/重组的处理器，计算 Rank 值并更改父子层级关系。 | 依赖 `ProcessObjectLoader`, `TailoringAncestorChainRecalculator`, `IProcessGraphService` | 领域处理器 (Domain Handler) |
| **ProcessElementsDataObject** | SDKImpl 实现层 (gf-sdkimpl) | 过程元素核心 DataObject，代表聚合根（Aggregate Root）/ 实体。执行具体的 `delete` 和 `setValues(parent, rank)`。 | 依赖 `DataObjectFramework` | 聚合根/实体 (Entity) |
| **TailoringAncestorChainRecalculator** | SDKImpl 实现层 (gf-sdkimpl) | 结构变更后重新计算受影响节点和父辈节点裁剪状态链的领域服务。 | 依赖 `ProcessObjectLoader` | 领域服务 (Domain Service) |

### 2. 依赖关系与依赖防线 (Dependency Constraints)
* **Biz 层隔离**：`ProcessRest` 和 `WidgetOperationManagement` 只允许直接依赖 `gf-sdk` 层，所有的具体 Handler 及 `DataObject` 实现类对 Biz 层不可见。
* **无循环依赖**：Biz (gf-biz) -> SDK (gf-sdk) <- SDKImpl (gf-sdkimpl)。
* **无事务下沉**：`gf-sdkimpl` 的 Handler 与 Service 不添加 `@Transactional`，事务生命周期由 Biz 层的 `WidgetOperationManagement`（编排层）声明控制（`@Transactional(rollbackFor = Exception.class)`）。

### 3. 领域模型决策 (DDD Models)
* **聚合根 (Aggregate Root)**: `ProcessElementsDataObject`（过程元素）。
* **实体 (Entity)**: `StateOption`, `QualityLevelOption`（挂载在过程元素下的实体）。
* **值对象 (Value Object)**: `IRank` / `DefaultRank`（用于计算元素相对位置和排序值的数值对象）。
* **参数 DTO**: `BatchDeleteElementsOperationParam`, `DragElementOperationParam`。

## Design: Level 3 -- Interactions

### 1. 批量删除时序交互流

```
[前端 (Frontend)]
   │
   │ 1. POST /api/gate_flow/process/execute_widget_operation
   │    { operationType: "BATCH_DELETE", operationParam: { elementIds: [101, 102] } }
   ▼
[ProcessRest]
   │
   │ 2. 接收请求并调用 executeOperation(req)
   ▼
[WidgetOperationManagement]  <-- 开启 @Transactional 事务与版本废弃校验
   │
   │ 3. 转发至 IProcessService.executeOperation(...)
   ▼
[ProcessServiceImpl]
   │
   │ 4.1 获取 BatchDeleteElementsDataModificationHandler 实例
   │ 4.2 调用 handler.parseParam(param) 解析参数 DTO
   │ 4.3 调用 handler.execute(param, context)
   ▼
[BatchDeleteElementsDataModificationHandler]
   │
   │ 5.1 获取所有目标元素，验证其存在且同属当前过程版本，非根非分类节点
   │ 5.2 收集这些元素的原 parentId（去重，记为 parentIdsToRecalculate）
   │ 5.3 对每一个待删元素，解析所有子孙节点，深度降序排序（叶子节点最先）
   │     descendant.delete() -> element.delete()
   │
   │ 5.4 对 parentIdsToRecalculate 中每个有效的 parentId：
   │     ├─ 调用 ancestorChainRecalculator.recalculateAfterStructureChange(...) 重新计算裁剪链
   │     └─ 调用 processGraphService.deleteGraphCache(...) 清除 Redis 图缓存
   │
   │ 5.5 返回 DataModificationResult<List<Long>> (包含所有删除成功的元素 ID)
   ▼
[WidgetOperationManagement]
   │
   │ 6.1 对每个删除的元素 ID，向系统发布 HistoryAddEvent 记录操作历史
   │ 6.2 组装响应体 ExecuteWidgetOperationResp
   ▼
[前端 (Frontend)] 接收成功响应
```

### 2. 过程元素拖拽排序/重组时序交互流

```
[前端 (Frontend)]
   │
   │ 1. POST /api/gate_flow/process/execute_widget_operation
   │    {
   │       operationType: "DRAG_ELEMENT",
   │       operationParam: { dragId: 201, dragType: "BEFORE", referenceId: 202 }
   │    }
   ▼
[ProcessRest]
   │
   │ 2. 接收请求并调用 executeOperation(req)
   ▼
[WidgetOperationManagement]  <-- 开启 @Transactional 事务与版本废弃校验
   │
   │ 3. 转发至 IProcessService.executeOperation(...)
   ▼
[ProcessServiceImpl]
   │
   │ 4.1 获取 DragElementDataModificationHandler 实例
   │ 4.2 调用 handler.execute(param, context)
   ▼
[DragElementDataModificationHandler]
   │
   │ 5.1 从 ProcessObjectLoader 获取被拖拽元素 dragElement 和参考元素 referenceElement
   │ 5.2 校验两者必须存在、同属一个过程，且非根非分类节点
   │ 5.3 校验禁环逻辑：referenceId 绝对不能是 dragId 本身或其子孙节点
   │ 5.4 确定目标父节点 newParentId:
   │     ├─ 若 CHILD: newParentId = referenceId
   │     └─ 若 BEFORE/AFTER: newParentId = referenceElement.getParentId()
   │ 5.5 计算新 Rank 字符串 (通过 IAdminSession 封装的算法)：
   │     ├─ 若 CHILD: calculateRankForCurrentLevel(newParentId, type, DESC)
   │     └─ 若 BEFORE/AFTER: calculateRankForCurrentLevel(newParentId, type, referenceId, isBefore, dragId)
   │ 5.6 更新属性值:
   │     dragElement.setValues({ parentId: newParentId, rank: newRank })
   │ 5.7 触发结构变更后的副作用处理：
   │     ├─ 重新计算新旧父节点层级及自身的裁剪状态链
   │     └─ 清理新旧父节点及自身的 Redis 流程图缓存
   │ 5.8 返回 DataModificationResult<Long> (包含被移动的元素 ID)
   ▼
[WidgetOperationManagement]
   │
   │ 6.1 发布 HistoryAddEvent 记录元素移动历史
   │ 6.2 组装响应体并返回
   ▼
[前端 (Frontend)] 接收成功响应
```

## Design: Level 4 -- Contracts

### 1. WidgetOperationType 新增操作契约 (SDK 接口)

文件：`com.airedgesoft.gf.sdk.enums.WidgetOperationType`

```java
/**
 * 批量删除元素
 */
BATCH_DELETE("BATCH_DELETE", "批量删除元素", "批量删除元素"),

/**
 * 拖拽移动元素
 */
DRAG_ELEMENT("DRAG_ELEMENT", "拖拽移动元素", "拖拽移动元素")
```

### 2. Handler 注册表契约 (SDKImpl 实现)

文件：`com.airedgesoft.gf.sdkimpl.service.gateflow.operation.OperationTypeHandlerRegEnum`

```java
BATCH_DELETE(WidgetOperationType.BATCH_DELETE, "批量删除元素", BatchDeleteElementsDataModificationHandler.class),
DRAG_ELEMENT(WidgetOperationType.DRAG_ELEMENT, "拖拽移动元素", DragElementDataModificationHandler.class)
```

### 3. 操作参数契约 DTO (SDKImpl 实现)

文件：`com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param.BatchDeleteElementsOperationParam`

```java
package com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param;

import lombok.Data;
import java.util.List;

/**
 * 批量删除元素操作参数
 */
@Data
public class BatchDeleteElementsOperationParam {
    /**
     * 待删除的元素ID列表
     */
    private List<Long> elementIds;
}
```

文件：`com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param.DragElementOperationParam`

```java
package com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param;

import lombok.Data;

/**
 * 拖拽移动元素操作参数
 */
@Data
public class DragElementOperationParam {
    /**
     * 被拖动的元素ID
     */
    private Long dragId;

    /**
     * 拖动类型：BEFORE（前）、AFTER（后）、CHILD（子）
     */
    private ElementDragType dragType;

    /**
     * 参考节点ID
     */
    private Long referenceId;
}
```

文件：`com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param.ElementDragType`

```java
package com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param;

/**
 * 元素拖拽类型枚举
 */
public enum ElementDragType {
    BEFORE,
    AFTER,
    CHILD
}
```

### 4. Handler 接口定义契约 (SDKImpl 实现)

* **BatchDeleteElementsDataModificationHandler**：
  ```java
  package com.airedgesoft.gf.sdkimpl.service.gateflow.operation.handler;

  import com.airedgesoft.gf.sdkimpl.service.gateflow.operation.DataModificationHandler;
  import com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param.BatchDeleteElementsOperationParam;
  import com.airedgesoft.gf.sdk.dto.DataModificationResult;
  import java.util.List;

  public class BatchDeleteElementsDataModificationHandler 
      implements DataModificationHandler<BatchDeleteElementsOperationParam, List<Long>> {
      
      @Override
      public OperationTypeHandlerRegEnum getSupportedOperationType() {
          return OperationTypeHandlerRegEnum.BATCH_DELETE;
      }

      @Override
      public DataModificationResult<List<Long>> execute(
          BatchDeleteElementsOperationParam param, OperationContext context) {
          // 实现逻辑...
      }

      @Override
      public Class<BatchDeleteElementsOperationParam> getParamType() {
          return BatchDeleteElementsOperationParam.class;
      }
  }
  ```
* **DragElementDataModificationHandler**：
  ```java
  package com.airedgesoft.gf.sdkimpl.service.gateflow.operation.handler;

  import com.airedgesoft.gf.sdkimpl.service.gateflow.operation.DataModificationHandler;
  import com.airedgesoft.gf.sdkimpl.service.gateflow.operation.param.DragElementOperationParam;
  import com.airedgesoft.gf.sdk.dto.DataModificationResult;

  public class DragElementDataModificationHandler 
      implements DataModificationHandler<DragElementOperationParam, Long> {
      
      @Override
      public OperationTypeHandlerRegEnum getSupportedOperationType() {
          return OperationTypeHandlerRegEnum.DRAG_ELEMENT;
      }

      @Override
      public DataModificationResult<Long> execute(
          DragElementOperationParam param, OperationContext context) {
          // 实现逻辑...
      }

      @Override
      public Class<DragElementOperationParam> getParamType() {
          return DragElementOperationParam.class;
      }
  }
  ```

## Design Summary

### 1. 组件与层级分配 (Components & Layers)
* **Biz 层**：`ProcessRest` (外部端点), `WidgetOperationManagement` (业务编排、事务声明与操作历史记录)。
* **SDK 层**：`WidgetOperationType` (新增 `BATCH_DELETE`, `DRAG_ELEMENT` 枚举契约)。
* **SDKImpl 层**：`BatchDeleteElementsDataModificationHandler` (批量删除处理域), `DragElementDataModificationHandler` (拖拽排序处理域), `ProcessElementsDataObject` (聚合根), `TailoringAncestorChainRecalculator` (裁剪重算服务)。

### 2. 核心契约与接口 (Key Contracts & Interfaces)
* **入参 DTO**：`BatchDeleteElementsOperationParam` (批量元素ID列表), `DragElementOperationParam` (拖拽源、拖拽类型、参考节点ID)。
* **拖拽类型值对象**：局部 `ElementDragType` (BEFORE, AFTER, CHILD) 避免外部反向依赖。
* **统一入口**：前端调用 `/execute_widget_operation` 分发至上述两个新注册的 Handler。

### 3. 架构约束与规约 (Architectural Constraints & Guidelines)
* **事务边界**：Handler 中严禁声明 `@Transactional`。事务由 Biz 层的 `WidgetOperationManagement.executeOperation` 统一管控。
* **依赖方向**：`gf-sdkimpl` $\rightarrow$ `gf-sdk` 且依赖 `infrastructure-sdkImpl`，不反向依赖 `infrastructure-biz`。
* **Context-first 传参**：批量循环前提取共享父属性，不在循环内重复加载 `getProcess`。

* **状态**：**Completed -- Implemented and Verified**

## Key Files

* `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/enums/WidgetOperationType.java` -- 新增 BATCH_DELETE, DRAG_ELEMENT 操作契约
* `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/OperationTypeHandlerRegEnum.java` -- 注册新 Handler
* `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/param/ElementDragType.java` -- 新增拖拽类型值对象，解耦依赖
* `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/param/BatchDeleteElementsOperationParam.java` -- 批量删除参数契约
* `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/param/DragElementOperationParam.java` -- 拖拽排序参数契约
* `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/handler/BatchDeleteElementsDataModificationHandler.java` -- 批量删除处理器核心实现
* `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/handler/DragElementDataModificationHandler.java` -- 拖拽排序处理器核心实现
* `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/handler/BatchDeleteElementsDataModificationHandlerTest.java` -- 批量删除单元测试
* `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/handler/DragElementDataModificationHandlerTest.java` -- 拖拽排序单元测试

## Implementation Decisions

* **高效率级联删除过滤**：批量删除时，如果一个元素已经被它的父祖节点包含在待删除的列表里，它在执行顶层删除时会被跳过，改由父节点的递归删除机制统一执行，以避免出现重复删除找不到数据的数据库异常。
* **IAdminSession 排序重用**：在 `DragElementDataModificationHandler` 中直接调用底座 `IAdminSession` 的 `calculateRankForCurrentLevel` 核心逻辑，通过传入 `referenceId` 和 `dragId` 计算得到标准的 Rank 排序字串，实现与平台 DataObject 排序风格的一致。
