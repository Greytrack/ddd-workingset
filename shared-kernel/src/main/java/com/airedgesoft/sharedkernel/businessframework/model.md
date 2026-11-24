# businessobject（业务对象框架内核）— model.md

> 模块归属：`shared-kernel`（**非 BC**）。当前代码位于 `domain/businessobject/`、`domain/businessobject/attributevalue/`、`domain/businessobject/attributevaluevalidation/`、`domain/numbering/`、`domain/event/`、`infrastructure/businessobject/`；目标目录 `businessobject/`。模块划分见 [../model.md](../model.md)。

> **RESTful 场景表**：本模块**无入站适配**（无 `adapters/`、无 REST 端点），仅提供领域能力与端口，故不适用 `v4-restful-api.md` §10.1 的 7 列场景表；消费方场景表见各 BC 与 `shared-kernel` 其他模块。

## 1. 职责

跨 BC 复用的**业务对象最小内核**：属性值校验策略族、聚合构造器契约、版次与排序协作接口、分类属性 schema 源、编号服务端口、领域事件标记接口。**不含业务规则**——业务规则留在消费方 BC 的聚合内。

## 2. 元素（与源码一一对应）

### 2.1 业务对象与版次

| 元素 | 形态 | 说明 |
|------|------|------|
| `BusinessObject` / `BusinessObjectImpl` | 接口 + 实现 | 业务对象统一读写面（字段级 get/set） |
| `BusinessDataCarrier` | 接口 | 字段承载契约（`FieldValue` 集合） |
| `Revisionable` | 接口（default 复用） | 签出 / 签入 / 取消签出 + 属性值批量校验 `validateFieldValues(changes, schemas)` |
| `Revision` / `RevisionItem` / `RevisionNumber` / `RowRevisionInfo` | 聚合内 VO | 版次、版次项、版次号与行版次信息 |
| `RevisionBuilder` / `BusinessObjectBuilder` / `BusinessObjectBuilderRegistry` | 构造器契约 + 注册表 | 聚合构造器；注册在模块 `infrastructure/config` 装配（**不挂领域注解**） |
| `OrderIndex` / `OrderTreeable` / `Treeable` / `RowMark` | 协作接口 | 排序、树结构、行标记能力（能力以 default 方法复用）。**2026-11-22**：容器成员值对象 `OrderMember` 已删除——树节点即成员（身份 `OrderTreeable#getId()`、位置值 `#getOrderIndex()`），不再开第二个事实源 |
| `Reference` / `OptionalClassFields` / `OptionalSubClassFields` | 值对象 / 常量 | 引用关系与保留字段定义 |

### 2.2 属性值对象（`attributevalue/`）

| 元素 | 形态 | 说明 |
|------|------|------|
| `AttributeValue` | 接口 | 属性值契约：`type()` / `isEmpty()` / `toText()` |
| `RawValue` / `TextValue` / `RichTextValue` / `NumberValue` / `NumericalValue` / `SingleListValue` / `MultipleListValue` / `CascadeSingleListValue` / `CascadeMultiListValue` / `DateValue` / `StartAndEndDateValue` / `CompositeTextValue` / `CompositeNumericValue` / `ImagesValue` / `NoOpValue` / `ImageItem` | 值对象族 | 各属性类型的解析后值（不可变） |
| `ObjectAttributePermission` | 值对象（授权承载） | 对象属性权限结果（classId / 对象 id / 可读 / 可写属性 id；消费方 v4-identityaccess） |

### 2.3 属性值校验（`validation/`）

| 元素 | 形态 | 说明 |
|------|------|------|
| `AttributeValidatorStrategy` | 接口 | 策略契约：`type()` + `validate(value, schema, mainAttributeValue)` |
| `AttributeTypeFactory` | 工厂（`final class`） | 按 `AttributeTypeEnum` 调度「串→值」与「值校验」，唯一入口 |
| `*ValidatorStrategy`（Text / RichText / Number / Numerical / SingleList / MultipleList / CascadeSingleList / CascadeMultiList / Date / StartAndEndDate / CompositeText / CompositeNumeric / Images / NoOp） | 策略实现 | 逐类型校验规则；组合类复用前段策略 |
| `AttributeValueValidationContext` | 上下文 | 一次校验的输入（schema / 值 / 主属性值） |
| `AttributeValueMetadataPort`（＋静态持有者 `AttributeValueMetadataPorts`） | **平台取数端口**：v3 取数 1:1 镜像（`AdminCache` / `IListObjFactory` / `ISessionLoader` / `GstoreHelper` / `GlobalValidatorTool` / `AttributeInheritHelper`） | 实现落 `v4-bridge-v3`（`V3AttributeValueMetadataAdapter` + 装配安装） |
| `Violation` | 违例承载 | 违例记录（`CommonResCode` + 参数） |
| `AttributeObjectState` | 值对象 | 同对象属性值状态（级联跨字段判定口径） |
| `CascadeValueRangeChecker` | 判定器 | 级联允许范围计算（跨字段） |
| `package-info` | 文档 | 包级约定与取数口径说明 |

> `AttributeValueFormatException`（值对象解析失败信号）已上提 `lib-exception`（2026-11-21），值对象 `fromText` 直接抛出。

### 2.4 端口

| 端口 | 用途 | 实现 |
|------|------|------|
| `businessobject.AttributeFacts` / `businessobject.ClassificationFacts`（只读契约） | 属性事实与分类视图（装配 / 校验族读取的字段面） | 实现 = `v4-bridge-v3` `AttributeSnapshot` / `ClassSnapshot`；取数端口 `ClassificationModel` 同在该模块（v4 分类元数据重建前不下沉） |
| `numbering.NumberService` | 编号发放 | `v4-bridge-v3` `V3NumberService` |
| `event.DomainEvent` | 领域事件标记接口 | 消费方聚合产生 |
| `businessobject.LiteBusinessObjectRepository`（domain 根 · 只读） | 只读跨聚合对象快照（`findById`／`findByIds`；2026-11-16 由 `LiteBusinessObjectService` 更名，方法面统一 find 家族） | 基础设施实现（`infrastructure.providers`） |
| `businessobject.LiteBusinessObjectTreeRepository`（domain 根 · 只读） | 通用树结构与容器成员只读事实（子树／子节点／祖先链／高度／根层与子层成员；2026-11-16 由 `LiteBusinessObjectTreeQuery` 更名，现名 `businessobject.order.OrderTreeableRepository`） | **由各 BC 聚合仓储亲身兑现**（如 `v4-alm` `RequirementRepositoryImpl`）；表级 SQL 落同包 `OrderTreeableMapper`（2026-11-22：不再单独设「树仓储」，版次表名动态入参，成员两法返回节点自身） |

## 3. 落地状态

- 属性值校验策略族与构造器：**已实现**（策略族经 `AttributeTypeFactory` 调度；`Revisionable#validateFieldValues` 批量收集违例）。
- 平台能力：v3 取数已收敛为 domain 端口 `AttributeValueMetadataPort` + `v4-bridge-v3` 适配（`V3AttributeValueMetadataAdapter`，1:1 镜像 v3 取数语句；`AttributeValueMetadataPorts` 装配期安装，同 `AuthorizationPorts` 约定）；ArchUnit 规则 4 / 6 的 `AttributeValidationSupport` 豁免已移除（2026-11-24）。
- 已知欠账：领域仍引用 v3 的 `AttributeNode` / `AttributeTypeEnum` / `AdminCache`，属迁移期登记项（见 `.lattice/standards/architecture.md` Deviation Registry）；属性值校验错误码已迁入 `SharedKernelResultCode`（2026-11-24，v3 `CommonResultCode` 同名语义平移）。
