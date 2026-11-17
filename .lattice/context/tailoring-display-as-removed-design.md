---
feature: "tailoring-display-as-removed-field"
requirement_doc: null
created: "2026-06-17"
status: "Implemented"
decisions: 2
open_questions: 0
constraints:
  - "后端统一判定划线逻辑为 REMOVED 或 forcedRemovedByParent=是"
  - "属性选项（QualityLevelOption, StateOption等）裁剪展示判定由于无父子连带移除，等价于 tailoringStatus == REMOVED"
---

# 裁剪划线展示与隐藏判定字段设计

> 在过程元素树节点、Index 行、版本比对树节点中新增一个字段，用于表达当前元素是否应该由于裁剪而展示划线（在显示裁剪元素时）或隐藏（在关闭显示裁剪元素时），便于前端直接使用，降低前端逻辑复杂度。

## Design: Level 1 -- Capabilities

### 1. 元素树节点输出能力
在查询过程元素树（如 `queryElementTree`）的响应实体中，输出新增字段 `displayAsRemoved`。

### 2. Index 行索引数据输出能力
在查询 Index 数据的响应实体（如 `ProcessElementIndexData`）中，输出 `displayAsRemoved`。

### 3. 版本比对树节点输出能力
在版本比对树（如 `queryElementTreeWithCompare`）的响应实体中，输出 `displayAsRemoved`。

### 4. 统一判定逻辑内聚能力
复用 `TailoringDisplaySupport` 的 `isDisplayAsRemoved` 判定，保证所有输出点的逻辑完全闭环和一致。
判定规则：`tailoringStatus == REMOVED || tailoringForcedRemovedByParent == true`。

### 5. 属性选项展示输出能力
在查询属性选项（如质量等级选项、状态选项）的列表或 DTO 时，同步输出 `displayAsRemoved` 判定属性，提供给前端完全一致的划线及隐藏判断基准。

## Design: Level 2 -- Components

### 1. 组件与分层架构映射表

| 组件名称 | 所在分层 | 职责描述 | 依赖关系 | DDD 角色分类 |
| :--- | :--- | :--- | :--- | :--- |
| **`ProcessElementTreeResp`** | Biz 层 (gf-biz) | 过程元素树响应 DTO | 依赖 `gf-sdk` | Value Object / DTO |
| **`ProcessElementIndexData`** | SDK 层 (gf-sdk) | 列表 Index 数据 DTO | 无依赖 | Value Object / DTO |
| **`ProcessElementTreeCompareDto`** | SDK 层 (gf-sdk) | 元素比对树节点 DTO | 无依赖 | Value Object / DTO |
| **`TailoringDisplaySupport`** | SDK 层 (gf-sdk) | 统一裁剪状态显示判定工具 | 无依赖 | Domain Service (无状态) |
| **`ProcessElementTreeSupport`** | Biz 层 (gf-biz) | 组装 `ProcessElementTreeResp` 并填充新字段 | 依赖 `gf-sdk` | Application Service / Assembler |
| **`IndexHandler`** | SDKImpl 层 (gf-sdkimpl) | 构造 `ProcessElementIndexData` 并填充新字段 | 依赖 `gf-sdk` | Port / Handler |
| **`ProcessVersionCompareServiceImpl`**| SDKImpl 层 (gf-sdkimpl) | 构造 `ProcessElementTreeCompareDto` 并填充新字段| 依赖 `gf-sdk` | Application/Domain Service |
| **`ProcessVersionManagement`** | Biz 层 (gf-biz) | 组装比对树并填充新字段 | 依赖 `gf-sdk` | Application Service / Assembler |
| **`QualityLevelOption`** | SDK 层 (gf-sdk) | 质量等级选项 DTO | 无依赖 | Value Object / DTO |
| **`StateOption`** | SDK 层 (gf-sdk) | 状态选项 DTO | 无依赖 | Value Object / DTO |
| **`QualityLevelData`** | SDK 层 (gf-sdk) | 质量等级数据 DTO | 无依赖 | Value Object / DTO |
| **`StateData`** | SDK 层 (gf-sdk) | 状态数据 DTO | 无依赖 | Value Object / DTO |
| **`QualityLevelHandler`** | SDKImpl 层 (gf-sdkimpl) | 构造 `QualityLevelData` 并填充新字段 | 依赖 `gf-sdk` | Port / Handler |
| **`StateHandler`** | SDKImpl 层 (gf-sdkimpl) | 构造 `StateData` 并填充新字段 | 依赖 `gf-sdk` | Port / Handler |

### 2. 依赖关系决定
- **Biz 层隔离**：Biz 层组装时仅依赖 SDK 的 `TailoringDisplaySupport`，不引入 `gf-sdkimpl` 的 Handler 或逻辑。
- **无循环依赖**：Biz -> SDK, SDKImpl -> SDK。
- **只读路径**：此判定只存在于只读响应中，不涉及写事务。

## Design: Level 3 -- Interactions

### 1. 过程元素树查询交互流程
1. `ProcessManagement.queryElementTree` 获取元素列表。
2. 通过 `ProcessElementTreeSupport.convertToTreeResp` 转换为树节点。
3. 节点内部通过 `TailoringDisplaySupport.isDisplayAsRemoved(element)` 计算裁剪隐藏判定并赋给 `resp.displayAsRemoved`。

### 2. Index 数据查询交互流程
1. `IndexHandler` 构造 `ProcessElementIndexData`。
2. 使用 `TailoringDisplaySupport.isDisplayAsRemoved` 判定，值赋给 `data.displayAsRemoved`。

### 3. 比对树查询交互流程
1. `ProcessVersionCompareServiceImpl` 构建 `ProcessElementTreeCompareDto` 树。
2. 使用 `TailoringDisplaySupport.isDisplayAsRemoved` 判定，值赋给 `dto.displayAsRemoved`。
3. `ProcessVersionManagement` 组装 `ProcessElementTreeWithCompareResp` 时拷贝该属性。

### 4. 判定规则与复用边界
统一使用 `TailoringDisplaySupport.isDisplayAsRemoved` 计算，防止散落。无副作用，无事务，只读路径。

### 5. 属性选项查询交互流程
1. `QualityLevelHandler`/`StateHandler` 构造 `QualityLevelData`/`StateData`。
2. 当 option 被 `setTailoringStatus(tailoringStatus)` 时，通过 getter/setter 判定其为 `REMOVED`，并映射给 options / data 对应的 `displayAsRemoved` 字段。

## Design: Level 4 -- Contracts

### 1. ProcessElementTreeResp 契约定义
```java
    @Schema(description = "是否由于裁剪展示为已移除状态（前端用于划线展示，或者在关闭显示裁剪元素时隐藏该节点）")
    private Boolean displayAsRemoved;
```

### 2. ProcessElementIndexData 契约定义
```java
    @Schema(description = "是否由于裁剪展示为已移除状态（前端用于划线展示，或者在关闭显示裁剪元素时隐藏该节点）")
    private Boolean displayAsRemoved;
```

### 3. ProcessElementTreeCompareDto 契约定义
```java
    @Schema(description = "是否由于裁剪展示为已移除状态（前端用于划线展示，或者在关闭显示裁剪元素时隐藏该节点）")
    private Boolean displayAsRemoved;
```

### 4. QualityLevelOption / StateOption 契约定义
```java
    @JsonProperty("displayAsRemoved")
    @Schema(description = "是否由于裁剪展示为已移除状态（前端用于划线展示，或者在关闭显示裁剪元素时隐藏该节点）")
    public Boolean getDisplayAsRemoved() {
        return tailoringStatus == ElementTailoringStatusListEnum.REMOVED;
    }
```

### 5. QualityLevelData / StateData 契约定义
```java
    @Schema(description = "是否由于裁剪展示为已移除状态（前端用于划线展示，或者在关闭显示裁剪元素时隐藏该节点）")
    private Boolean displayAsRemoved;
```

### 4. 映射与赋值
```java
Boolean displayAsRemoved = TailoringDisplaySupport.isDisplayAsRemoved(tailoringStatus, forcedRemovedByParent);
```

## Design Summary

**Design status: Approved -- ready for implementation**

### Components and Layer Assignments
- `ProcessElementTreeResp` (Biz layer - presentation VO)
- `ProcessElementIndexData` (SDK layer - contract VO)
- `ProcessElementTreeCompareDto` (SDK layer - contract VO)
- `TailoringDisplaySupport` (SDK layer - domain service utility)

### Key Contracts and Interfaces
- 新增 `displayAsRemoved` 字段于以上三个 DTO/VO 中。
- 使用 `TailoringDisplaySupport.isDisplayAsRemoved` 统一赋值。
- 新增 `displayAsRemoved` 字段于属性选项 DTO/VO (`QualityLevelOption`, `StateOption`, `QualityLevelData`, `StateData`) 中。

### Architectural Constraints
- 仅在 DTO/VO 层级作为只读呈现属性进行后端动态计算，不更改底层数据库结构和持久化逻辑，不引入事务。
- 强制复用已有的 `TailoringDisplaySupport`，确保判定规则唯一。

### Decisions Log
- **2026-06-17**: Design approved at Level 4. Blueprint complete ready for implementation. 新增 `displayAsRemoved` 字段简化前端裁剪状态渲染判定。
- **2026-06-17**: Implementation complete. End-to-end changes applied, unit tests compiled and passed, Living Document updated.

## Implementation Records

### Created / Modified Files
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/datasource/dto/ProcessElementIndexData.java` (Modified) — 新增 `displayAsRemoved`
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/dto/ProcessElementTreeCompareDto.java` (Modified) — 新增 `displayAsRemoved` 且更新 copy 逻辑
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/dto/QualityLevelOption.java` (Modified) — 新增 `displayAsRemoved` 动态 Getter 
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/dto/StateOption.java` (Modified) — 新增 `displayAsRemoved` 动态 Getter
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/datasource/dto/QualityLevelData.java` (Modified) — 新增 `displayAsRemoved` 字段
- `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/datasource/dto/StateData.java` (Modified) — 新增 `displayAsRemoved` 字段
- `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/datasource/buildin/handler/IndexHandler.java` (Modified) — 写入 `TailoringDisplaySupport.isDisplayAsRemoved` 映射
- `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/datasource/buildin/handler/QualityLevelHandler.java` (Modified) — 写入 `displayAsRemoved` 字段映射
- `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/datasource/buildin/handler/StateHandler.java` (Modified) — 写入 `displayAsRemoved` 字段映射
- `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/version/ProcessVersionCompareServiceImpl.java` (Modified) — 在构建比对树及幽灵节点时集成 `TailoringDisplaySupport`
- `components/gf/biz/src/main/java/com/airedgesoft/gf/biz/process/rest/resp/ProcessElementTreeResp.java` (Modified) — 新增 `displayAsRemoved`
- `components/gf/biz/src/main/java/com/airedgesoft/gf/biz/process/management/ProcessElementTreeSupport.java` (Modified) — 写入 `TailoringDisplaySupport.isDisplayAsRemoved` 映射
- `components/gf/biz/src/main/java/com/airedgesoft/gf/biz/processversion/management/ProcessVersionManagement.java` (Modified) — DTO 到 Resp 映射拷贝
- `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/service/ProcessVersionCompareServiceImplTest.java` (Modified) — 添加 `displayAsRemoved` 两个单测用例并全量通过
- `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/datasource/buildin/handler/QualityLevelHandlerTest.java` (Modified) — 增加属性选项 `displayAsRemoved` 判定单测
- `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/datasource/buildin/handler/StateHandlerTest.java` (Modified) — 增加属性选项 `displayAsRemoved` 判定单测
- `components/gf/biz/src/test/java/com/airedgesoft/gf/biz/process/management/ProcessElementTreeSupportTest.java` (Created) — 编写对 ProcessElementTreeSupport 的判定转换的单元测试并全量通过

### Verification Results
- gf-sdk、gf-sdkImpl、gf-biz 均全量编译通过。
- `ProcessVersionCompareServiceImplTest` 通过全部 8 个单测。
- `QualityLevelHandlerTest` 与 `StateHandlerTest` 通过全部选项判定单测。
- `ProcessElementTreeSupportTest` 通过全部 3 个单测。
