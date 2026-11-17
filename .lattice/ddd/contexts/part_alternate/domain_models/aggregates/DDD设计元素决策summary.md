# DDD设计元素决策summary

> 每次 DDD 决策讨论的经验沉淀。本文档随 [part（物料领域模型）](./part.md) 的决策过程持续演进，为后续建模提供决策依据与经验教训。

## 五元素总览表

| 元素 | 核心判据（全中才使用） | 本模型实例 | 反模式/注意 |
|---|---|---|---|
| **聚合根** Aggregate Root | ① 有一致性边界（聚合内状态必须原子变更）② 是业务规则入口（不变量由聚合根方法保证）③ 对外只暴露意图方法（`checkOut`/`moveToRecycleBin`），不暴露内部结构 | `Part`（身份 `instanceId` 稳定，版本是状态非身份；组合值对象 + 实体） | 持有跨聚合数据；版本参与身份 |
| **实体** Entity | ① 有身份（id 区分实例）② 有生命周期（状态流转）③ 归属某聚合、与聚合根同事务变更 ④ 无独立一致性边界 | `CheckoutRecord`（有版次 ID、签出→签入流转、与 Part 同事务） | **不独立成聚合**：现网 `checkOut()` 单事务内插行+更新 Part 状态，独立聚合会违反"一个事务只改一个聚合" |
| **值对象** Value Object | ① 无独立身份 ② 按值相等 ③ 不可变（整体替换）④ 无独立生命周期 ⑤ 成组出现 ⑥ 有行为/约束或复用价值 | `ModelingField`（归属建模）、`VersionInfo`（版本版次+版本链）、`FieldValue`（元数据扩展字段）、`PartDeletionContext`/`PartCreateContext`/`RevisionContext`（判定上下文） | 纯 ID/外键→裸 `Long`（`designFile`）；枚举→裸 `Long`；单字段无行为→不拆；有身份→实体 |
| **领域服务** Domain Service | ① 规则跨越多个聚合/实体，不属于任何单一聚合 ② 无状态 ③ 编排跨聚合行为（校验/协调），不做本聚合内的事 | `RevisionRevertService`/`DefaultRevertService`（回滚编排：规格校验→回滚→清理引用→事件）；`PartValidationService` 已规格化移除 | 无状态规则却塞进聚合根；只做转发（贫血）；含 IO 的校验应拆给规格+仓储 |
| **规格** Specification | ① 判定依赖跨聚合数据 ② 规则数量多、可组合/复用 ③ 判定无 IO（消费上下文） | 创建：`CreatePartSpecification`（`NumberUnique`+`NameUnique`+`AttributeRules`）；删除：`PartDeletionSpecification`（8 项）；回滚：`RevisionRevertSpecification` 链（7 项） | 入参校验强行规格化（简单必填直接判）；单规则无组合需求 |

## 领域服务 vs 规格（易混点）

| 维度 | 领域服务 | 规格 |
|---|---|---|
| 本质 | 编排跨聚合行为（有副作用） | 纯判定（无副作用） |
| 数据来源 | 自己查仓储/调服务 | 消费传入的上下文 |
| 场景 | `DefaultRevertService` 编排回滚流程 | `RevisionRevertSpecification` 判定"能否回滚" |
| 关系 | 领域服务**调用**规格（先判后做） | 被领域服务/应用服务调用 |

## 聚合根设计策略

- **身份稳定**：版本（revision）不参与身份——`(instanceId, revision)` 只是定位具体状态的复合键
- **聚合内联当前状态**：当前版次内联（`VersionInfo`/`CheckoutRecord`），历史版本惰性按需加载
- **不持有跨聚合数据**：跨聚合引用用 ID（`designFile`），引用检查走规格模式（不常驻属性）

## 规格结构要点

- 一规则一类（单 if + 自带异常码），组合器短路执行（`AndSpecification`）
- **判定无 IO**：子规格只消费上下文，不查库
- **上下文按需构建**：`{主体}Context` 由查询仓储按需组装，成本仅在动作时付出
- **命名惯例**：规格 `{主体}{动作}Specification`，上下文 `{主体}Context`（对齐现网 `RevisionRevertSpecification` + `RevisionContext`）

## 决策顺序（建模时）

```
1. 这个对象有独立身份 + 生命周期 + 独立一致性边界？ → 聚合根
   └─ 有身份但归属聚合、同事务？ → 实体
   └─ 无身份、按值、不可变、成组？ → 值对象
   └─ 都不是 → 裸类型/DTO
2. 行为是否依赖跨聚合数据？
   ├─ 是 + 规则多可组合 → 规格模式（判定无 IO，上下文按需构建）
   └─ 否 → 聚合根方法（本聚合状态）或应用服务（权限/编排）
3. 规则跨越多个聚合、需要编排副作用？ → 领域服务（调规格先判后做）
4. 事务边界 → 始终在应用服务（biz *Management）
```

## 经验教训（历次决策记录）

### 值对象命名：Attribute → Field → FieldValue

- `OptionalAttribute` → `ExtensionAttribute` → `ExtensionField` → `ExtensionFieldValue` → `FieldValue`
- **教训**：属性（Attribute）只表达元数据（apiId/type），但模型里它还携带 `value`——命名必须反映完整语义（字段值）。最终 `FieldValue` 简洁且表达"含值的字段"。

### 预制扩展字段：出现即有效

- 系统可关闭字段（元数据 `enabled`），但**关闭后不实例化**——值对象集合里出现的都是启用的
- **教训**：不要用 `isEnabled()` 运行时判断，用"不实例化"表达关闭；`enabled` 字段从值对象移除

### CheckoutState：值对象 → 聚合根 → 实体（最终）

- 先建模为值对象（签出五字段成组）
- 用户质疑后升级为聚合根（"签出记录也是页签、通过事件产生数据"）
- **最终降级为聚合内实体**——聚合边界 = 一致性边界：现网 `checkOut()` 单事务内"插版次行 + 更新 Part 状态"，独立聚合违反"一个事务只改一个聚合"
- **教训**：有身份≠聚合根；持久化独立≠聚合独立；判据是**事务一致性边界**，不是"有没有自己的表"

### 多版本表达：身份 vs 版本

- `instanceId` 是聚合身份（稳定），`revision` 是状态版本（不参与身份）
- `(instanceId, revision)` 是**定位具体状态的复合键**，不是身份
- 版本链：`VersionInfo.prevRevisionId` 指向父版次，历史惰性按需加载
- **教训**：版本参与身份会导致"每次签出 = 新实体"，跨版本一致性（BOM/替代料引用）无法表达

### 跨聚合引用：值对象 → 裸 Long 外键

- `DrawRef(drawId, drawType)` 初判为值对象
- **代码证据**：现网 `ATTR_DESIGN_FILE` 仅一个 `Long` 字段（存 draw revisionId），`DrawDto.id/type` 是查询返回 DTO 不是 Part 字段——`DrawRef` 是臆造
- 最终：裸 `Long` 外键，符合 DDD "reference by identity" 与规范 §2
- **教训**：值对象必须基于实际字段成组，不能从查询 DTO 倒推建模

### 仓储接口：不被持久化技术绑架

- `save(part)` 单一方法覆盖新建/更新（实现层按 id 有无区分 insert/update），**不拆 `add`/`update`**
- `save` 无返回值（不暴露 DB 生成 id）
- 接口无 MyBatis/SQL/分页字样；实现类在基础设施层（sdkImpl）
- **教训**：Repository 接口"不透露任何底层持久化机制痕迹"（Vernon ch12）；调用方不该知道对象新旧

### 校验：领域服务 → 规格（统一规格化）

- `PartValidationService`（唯一性 + 属性规则）→ 拆为 `CreatePartSpecification` 组合（`NumberUnique`/`NameUnique`/`AttributeRules`）
- 创建/删除/回滚三处校验统一规格模式：组合器 + 子规格 + 上下文
- **教训**：跨聚合判定（唯一性查重、引用检查）统一规格；简单入参校验（必填）才保留普通方法

### 删除保护：聚合常驻属性 → 规格模式

- 初版 `DeletionGuard` 值对象作为 Part 属性——意味着**每次加载 Part 构建 11 项跨聚合查询**
- 改为 `PartDeletionSpecification` + `PartDeletionContext`（按需构建）——成本仅在删除动作时付出
- **教训**：跨聚合高成本检查不做聚合常驻属性；判定（规格）与数据获取（查询仓储）分离

### 撤销语义：Undo → CancelCheckOut/Unmark/RevertRevision

- 类图初用 `undoLastOperation()`
- **现网名词**：`CancelCheckOut`（取消签出）、`Unmark`（取消标记）、`RevisionRevert`（版次回滚）——无通用 Undo
- **教训**：命名必须对齐 Ubiquitous Language，不要发明通用动词

### 权限校验：领域层 → 应用层

- 初版校验链在 `Part.checkOut()` 内（含权限）
- 调整为：权限（`PrivilegeTypeEnum.CHECKOUT`）在应用层，领域只保证"什么状态能签出"
- **教训**：权限是应用/安全关注点（谁能操作），领域规则是状态约束（什么状态允许）——两者分离

### 领域事件：创建/签出/签入

- `PartCreated`/`PartCheckedOut`/`PartCheckedIn` 用 `BaseDomainEvent` + `ApplicationEventPublisher` + `@EventListener`
- 用于**横切关注**（历史/异步/通知），**不用于跨聚合一致性**（项目规范 §5）
- 事件只携带 `partId`/`revision`/操作人/时间，不携带聚合内部状态
- **教训**：事件是横切机制，跨聚合副作用仍由应用服务同事务编排

### 页签何时独立为聚合根（建模决策）

**结论**：数据量足够大、操作足够复杂的页签独立建模为聚合根；其余页签作为主聚合内的实体（控制在三个实体以内）。

- **独立建模为聚合根**：
  - 数据量足够大、操作足够复杂——放到主对象（Part）里会混淆很多东西
  - 属于**通用页签功能**（多分类复用，如替代料页签挂在不同物料分类上）
- **作为主聚合内实体**：数据量小、操作简单、与主对象同事务变更——如 `CheckoutRecord`（签出记录）

**IDDD 依据（ch10.3「设计小型 Aggregates」）**：
> 结合性能与可扩展性考量……为庞大的集合新增一个元素，系统也会将数千个待办事项加载到内存中。这种大型 Aggregate 的性能和可扩展性始终表现不佳。

> Niclas Hedhman 透露，其团队设计的 Aggregates 中，约 70% 仅由一个根 Entity 搭配若干 Value 类型构成，剩余 30% 也总共只包含两到三个 Entities。

> 当你偶尔遇到真正的一致性规则时，可根据需要新增少量 Entities，或酌情添加一个集合，但仍需始终坚持将整体规模控制在最小。

**教训**：聚合边界 = 一致性边界 + 数据规模。页签数据量大且操作复杂时，作为主聚合内实体会导致"加载主对象 = 加载全部页签行"的性能噩梦（对应 IDDD 对大型集合的警告）——独立成聚合，用 ID 引用 + 按需查询隔离规模。

### 批量查询独立建模（查询模型）

**结论**：批量查询（列表/搜索/报表）走独立的查询模型，不加载聚合。

- 领域模型关联多（Part → VersionInfo/CheckoutRecord/FieldValue/历史版次），批量查询时大部分场景**不需要那么多字段和关联**
- 查询模型是非规范化投影（`PartQueryModel`/`PartListRow`），独立于聚合，走专用查询仓储

**IDDD 依据（ch12.3「用例最优查询」）**：
> 你系统的某些使用场景在呈现领域数据视图时，可能不会完全遵循单一 Aggregate 类型的轮廓……你可能会改为使用所谓的*用例最优查询*。在这种方式下，你对持久化机制指定一个复杂查询，并将结果动态地放入专门设计用于满足该用例需求的值对象中。

> 对于一个 Repository 在某些情况下返回 Value Object 而不是 Aggregate 实例来说，这不应该显得奇怪。

**IDDD 依据（ch4.6 CQRS）**：
> 查询模型是一种非规范化数据模型。其目的并非实现领域行为，仅用于提供显示所需的数据。

> 命令模型（写入模型）中，Aggregates 将不包含查询方法，仅保留命令方法；Repositories 将精简为仅包含 save() 以及单一查询方法 fromId()。

**教训**：
- 聚合是**写模型**（一致性边界），查询模型是**读模型**（非规范化投影）——两者分离
- 列表/批量查询走 `PartQueryModel` + 专用查询仓储，只 SELECT 所需列，不加载聚合集合
- 查询模型属**应用层**（无行为的数据 DTO），查询仓储实现属**基础设施层**——不属于领域层
- 若为批量查询创建大量跨仓储查找方法 → 代码异味（"Repository 掩盖了 Aggregate 设计不当"），先反思聚合边界，再考虑 CQRS
