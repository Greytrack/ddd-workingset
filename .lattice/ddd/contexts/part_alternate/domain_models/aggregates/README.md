# 物料领域 — 领域模型文档导航

> Part（物料）限界上下文下，物料/页签领域模型的 DDD 设计文档。
>
> **出处标注约定**：规约后的 `[IDDD-chX]` 指《实现领域驱动设计》（Vaughn Vernon，见 `D:\AppData\study\MDE\impl-ddd`）章节；`[DDD]` 指 Evans 蓝皮书；`[CleanArch]` 指 Clean Architecture（Robert C. Martin）；`[项目]` 指本项目 `.lattice/standards/ddd-principles.md` 规范。

## 导航

| 文档                                                                       | 内容                                     |
| ------------------------------------------------------------------------ | -------------------------------------- |
| [part.md](part.md)                                                       | 物料领域模型（Part 聚合根、属性/行为设计、核心场景验证）        |
| [tabs.md](tabs.md)                                                       | 页签领域模型（BOM / 替代料 / 关联关系，页签聚合边界）        |
| [query_model.md](query_model.md)                                         | 通用查询模型（批量查询 DataObject / Table 的读模型设计） |
| [DDD设计元素决策summary.md](DDD设计元素决策summary.md)                               | DDD 元素使用原则与历次决策经验沉淀                    |
| [class-design-overview.md](class-design-overview.md)                     | 分类页签体系 DDD 重构设计概述                      |
| [knowledge-level-class-diagram.md](knowledge-level-class-diagram.md)     | 知识层级类图                                 |
| [operational-level-class-diagram.md](operational-level-class-diagram.md) | 运行层级类图                                 |

## 议题与设计规约

### 议题1：DataObject ⇄ 分类体系（AirClass）对象

**问题**：DataObject（如 Part）与分类体系（AirClass/AirSubClass）如何协作？分类决定实例的结构，实例承载状态。

**设计规约**：
1. **DataObject 默认是聚合根**——一个 DataObject 实例 = 一个聚合（一致性边界），不把业务逻辑下沉到分类。**例外见规约 5（文件对象）**。`[项目] §1`
2. **分类（AirClass）是元数据（Metadata），不是领域对象**——分类定义实例的页签/属性/生命周期结构，不承载实例状态；实例通过 `type/classId` 引用分类。分类的变化不影响已实例化对象的身份（身份 = `instanceId` 稳定）。`[IDDD-ch5]` 身份独立于状态
3. **属性设计遵循三分类**——建模属性（创建后不变，如 `RelationModelingField`）、必备属性（有行为约束）、可选属性（元数据驱动 `FieldValue`）。`[项目] 属性建模 + [IDDD-ch6] 值对象判据`
4. **部分规则提取到共享（share）层用组合**——跨 DataObject 复用的规则（生命周期判定、唯一性校验、删除保护）不放单一聚合内，提取为共享规格/值对象组合。`[CleanArch] 依赖方向 + [IDDD-ch10.3] 小型聚合`
5. **例外：文件对象不作为聚合根**——文件对象（`AttachmentDataObject`，`object_key` 指向 Gstore 文件）虽是 DataObject 子类（`@RegisterDataObject`），但**无独立领域行为**（只有文件元数据：objectKey/fileFormat/fileSize/url），作为**附件聚合根的关联实体**存在——由宿主聚合经附件页签（`TabAttachmentTable`）持有，文件生命周期由 `AttachmentFileService` 管理。`[IDDD-ch5]` 实体 = 有身份但归属聚合 + `[项目] §1` 页签行是从属实体
   - **判据**：DataObject 是否承载独立领域行为？有 → 聚合根（Part/BomTab）；仅承载被动数据（文件元数据、引用记录）→ 关联实体（文件对象）。`[IDDD-ch5]` 实体与值对象判据

### 议题2：关系设计 ⇄ 页签体系

**问题**：BOM/替代料/关联关系都是页签，何时独立为聚合根，何时作为 DataObject 内的实体？

**设计规约**：
1. **页签操作过于复杂、数据量过多、或通用（多分类复用）→ 独立为聚合根**（如 `BomTab` 数千行、`Association` 通用关联）。`[IDDD-ch10.3]` 大型集合作为聚合内实体会导致"加载主对象 = 全量页签行"的性能噩梦
2. **页签简单、物料特有、数据量可控 → 作为实体挂载到 DataObject**（如 `Alternate` 挂 Part、`CheckoutRecord` 挂 Part）。`[IDDD-ch10.3]` 70% 聚合仅 1 根实体 + 值对象，其余 ≤2-3 实体
3. **聚合内实体不超过 3 个**——若超出，审视聚合边界，考虑拆分独立聚合。`[IDDD-ch10.3]`（Niclas Hedhman：30% 聚合也只含 2-3 个实体）
4. **页签跨聚合协调用应用服务编排**（同一事务内顺序调用），不用领域事件做跨聚合一致性。`[项目] §5`
5. **关系建模属性值对象化**——关系两端的身份（`instanceId`/`instanceType`/`relatedId`/`relatedType`）成组为 `RelationModelingField`（创建后不变）。`[IDDD-ch6]` 成组出现 + 不可变
6. **页签与建模分开**——页签是**界面呈现/交互容器**，不是领域模型的载体：
   - **页签不能完全表达模型之间的关系**：如局部替代料（按 BOM 行作用域，`union_table_instance_id` 定位）是"BOM 行 ⇄ 替代料"的**三元关系**，页签扁平行表达不了；自循环树（物料引用自身/递归结构）超出页签的集合语义——这类关系必须在领域层建模（实体/值对象/聚合），页签只是其投影。
   - **页签上的 Action 操作不等同于领域行为**：按钮/菜单（`TabBomButtonEnum`/`TabLocalAlternateButtonEnum` 等）是**界面操作配置**，服务呈现（显隐/排序/生命周期控制 `queryButtonAfterProcessor`）——领域行为（`addAlternate`/`checkOut`/`revertRevision`）在聚合根与规格，两者分离。
   - **页签配置应体现界面呈现**：`@RegisterTable`/页签元数据（列头/按钮/布局）是 UI 配置，不进入领域模型——领域层只建模"关系本身"（`Alternate`/`BomItem`/`AssociationItem`），页签是其渲染层。
   - **页面变化 ≠ 模型变化**：改页面（加列/调按钮/换布局）通常只需改**页签配置**；从模型视角，领域模型未变——唯一可能受影响的只是**应用层查询逻辑**（投影列/过滤条件），领域模型的各个方法保持稳定。这是"页签与建模分开"的价值所在：**UI 演进不侵蚀领域模型**，领域层不被界面需求牵着走。
   `[IDDD-ch4.2]` UI 层不含领域逻辑 + `[项目] §1` 页签行是聚合内从属实体

### 议题3：批量分页查询设计

**问题**：列表/搜索/报表场景批量查询，如何避免加载聚合完整状态？

**设计规约**：
1. **查询模型独立于领域模型（读模型）**——`DataObjectQuery`/`TableQuery`/`PageResult` 是非规范化投影 DTO，不加载聚合（`Part`/`BomTab` 完整性）。`[IDDD-ch4.6]` CQRS：查询模型是"非规范化数据模型，目的非实现领域行为"
2. **用例最优查询**——持久化机制指定复杂查询，结果放入专为用例设计的 DTO；Repository 返回 DTO 而非聚合实例是合法的。`[IDDD-ch12.3]`
3. **查询仓储与聚合仓储分离**——`DataObjectQueryRepository`/`TableQueryRepository`（只读）不经过 `PartRepository`/`BomTabRepository`（聚合仓储管写）。`[IDDD-ch4.6]` 命令模型仓储仅 save + fromId
4. **查询模型不属于领域层**——查询 DTO 在应用层（无行为），查询仓储接口在领域层契约，实现在基础设施层。`[CleanArch] 依赖倒置 + [IDDD-ch4.2] 分层`
5. **分页契约统一**——`PageQuery`（pageNum/pageSize/sortByList）+ `PageResult<T>`（total/pages/size/curPage/results）跨 DataObject/Table 复用。`[IDDD-ch12]` Repository 不透露持久化机制痕迹

### 议题4：类的相关定义思考

**问题**：聚合根/实体/值对象/领域服务/规格五元素如何判定？

**设计规约**（详见 [DDD设计元素决策summary.md](DDD设计元素决策summary.md)）：
1. **聚合根**：有一致性边界（原子变更）+ 规则入口 + 对外只暴露意图方法。`[DDD] Aggregate + [IDDD-ch10]`
2. **实体**：有身份 + 生命周期 + 归属聚合同事务，无独立一致性边界。`[IDDD-ch5]`
3. **值对象**：无身份 + 按值相等 + 不可变 + 无生命周期 + 成组出现 + 有行为/复用价值；单字段无成组 → 裸类型（不建值对象）。`[IDDD-ch6]`
4. **领域服务**：规则跨越多个聚合、无状态、编排跨聚合行为；含 IO 的校验拆给规格+仓储。`[IDDD-ch7]`
5. **规格**：判定依赖跨聚合数据 + 规则多可组合 + 判定无 IO（消费上下文）；上下文按需构建。`[IDDD-ch12.3] 用例最优查询 + [项目] §1`
6. **实体方法只在承载业务规则时存在**——纯赋值用 setter；规则外置到聚合根方法/规格。`[项目] §2` 禁止贫血转发
7. **仓储接口纯净**——不被持久化技术绑架：`save` 覆盖新建/更新（实现层按 id 分流 insert/update）、无 DB 生成 id 返回、无 SQL/MyBatis 字样。`[IDDD-ch12]`

### 议题5：如何落地（从 REST 到基础设施，全局评审）

**问题**：四个议题的模型如何映射到当前项目的真实分层（Rest → Management → 领域 → 基础设施）？每个环节的落地规约与评审点。

**1. RESTful 接口层（biz `*Rest`）**
- `@RestController` + `@RequiredArgsConstructor`（构造器注入）+ `@HttpExchange("/api/xxx")` + `@PostExchange`（现网 `DataObjectRest` 模式）。`[项目]` 构造器注入禁止 `@Autowired` 字段注入
- **只做入参解析与响应**：`RequestThreadHolder.setWorkingset/setWorkspace` 设上下文，调 Management，返回 DTO——不含业务逻辑。`[IDDD-ch4.2]` UI 层不含领域逻辑
- 幂等操作加 `@RepeatSubmit`（现网 `dynamicUpdate`）
- **评审点**：Rest 是否有业务判断？是否直接碰仓储/领域？

**2. 应用层（biz `*Management`）**
- **唯一事务边界**：`@Transactional(rollbackFor = Exception.class)` 只在此层（现网 `DataObjectManagement` 全部写操作）。`[项目] §4` 应用服务 = 编排 + 事务 + 入参校验
- **编排三职责**：① 权限校验（应用层关注点）② 调规格/工厂/聚合根 ③ `Repository.save` 落库
- **上下文传递**：`RequestThreadHolder`（workspace/workingset）+ `OperationContext`/`QueryContext` 只读下传。`[项目] §8`
- **评审点**：事务是否只在 Management？跨聚合协调是否同事务顺序调用（非领域事件）？

**3. 领域层（SDK `I*Service` + sdkImpl `*ServiceImpl` + DataObject）**
- 领域服务契约在 SDK（`I*Service`，无实现），实现在 sdkImpl（`*ServiceImpl`，跨实例编排）。`[项目] §4`
- 单实例行为在 DataObject 方法或 `IInner*` default 方法（`Part.checkOut`/`Alternate` setter）。`[项目] §2`
- 规格（`AddAlternateSpecification` 等）+ 工厂（`PartFactory`）+ 聚合根编排落在领域层。`[IDDD-ch7/ch11]`
- **评审点**：领域层是否被持久化/框架技术污染？实体是否只剩 setter + 规则外置？

**4. Shared 分类体系 + 通用校验**
- **分类体系**（AirClass）是元数据：`classId`/`apiId` 经 `AdminCache.getNodeById` 读取，不参与实例状态。`[项目] §2` 元数据标识用 `AirConstants`
- **通用校验提取 shared**：跨 DataObject 复用的规则（生命周期判定 `getLifeCyclePhaseEnum`、名称唯一 `checkNameExsits`、删除守卫）放共享层（`IPartDocumentCommonAction` default 方法）用组合而非继承。`[CleanArch] 依赖方向 + [IDDD-ch10.3]`
- 列表枚举字段存 `item_key`（枚举类承载语义）。`[项目] §3`
- **评审点**：校验是否重复实现？共享层是否无状态纯函数？

**5. 事务实现**
- **写事务仅在 biz Management**：`@Transactional(rollbackFor = Exception.class)`，sdk/sdkImpl **禁止** `@Transactional`（`[项目] §4` 与 AGENTS.md）
- 特殊场景 `REQUIRES_NEW`（现网 `PartAlternateTab.removeRows`）——卸载+重排原子完成，独立子事务
- **跨聚合协调**：同一事务内顺序调用多个聚合方法（`PartManagement` 编排 `BomTab` + `Part`），不用领域事件（`[项目] §5`）
- **评审点**：是否有 `@Transactional` 泄漏到非 Management 层？跨聚合是否同事务？

**6. 权限落地**
- **权限校验在应用层**：`checkValidOperation(PrivilegeTypeEnum.XXX)`（现网 `RevisionDataObject.checkOut` 入口），领域不感知"谁能操作"，只保证"什么状态允许"。`[CleanArch] 关注点分离`
- 数据权限经 `RequestThreadHolder.setWorkspace`（fail-closed 过滤，现网 `dynamicAddWithRestReturn` 注释）
- 页签级权限：`assertTabEnabled`（启用/禁用检查，`TABLE_UN_ENABLED_*` 错误码）
- **评审点**：权限是否泄漏进领域/实体？数据权限是否随 workspace 上下文正确传递？

7. **仓储设计实现评审**
- **聚合仓储**（`PartRepository`/`BomTabRepository`）：`save`/`findById` 管写，接口纯净（无 MyBatis/SQL 字样）。`[IDDD-ch12]` + `[项目] §6`
- **查询仓储**（`DataObjectQueryRepository`/`TableQueryRepository`）：只读投影，返回 DTO 非聚合。`[IDDD-ch12.3] 用例最优查询`
- **实现层**：MyBatis-Plus sdkImpl Repository，Mapper 不暴露给 Biz。`[项目] §6`
- **批量加载防 N+1**：`*ObjectLoader.getXxx(Collection<Id>)` 构建 Map，禁止循环内单条查。`[项目] §6`
- **评审点**：仓储接口是否被持久化技术绑架？批量查询是否 N+1？查询是否加载了聚合完整状态？

8. **审计日志（操作历史）**
- **事件驱动异步落库**：`BaseHistoryHelper` 生成 `HistoryAddDto` → 发布 `HistoryAddEvent`（`BaseDomainEvent`）→ `HistoryEventListener`（`@Async @EventListener`）消费 → `HistoryService` → `sysHistoryRepository` 落库。`[IDDD-ch8]` 领域事件 + `[项目] §5`
- **动作枚举化**：`HistoryActionListEnum`（CREATE/CHECKOUT/CHECKIN/REVERT 等）承载操作类型与 i18n 模板（`commonTplResCode`）。`[项目] §3` 枚举承载语义
- **上下文来源**：`RequestThreadHolder`（操作人/语言/客户端）+ `operateNodeId`（操作菜单）+ `operateObjId`（操作对象）
- **横切关注**：审计是写操作的横切副作用，**不参与聚合一致性**——随业务事务同进程发布事件，异步消费落库。`[IDDD-ch8]` 事件用于横切 + `[项目] §5`
- **评审点**：审计动作是否全覆盖（增删改/签出签入/回滚/移动）？是否携带操作人/对象/菜单上下文？异步失败是否可追溯？

**落地评审清单（每项落地前过一遍）**：
1. Rest 无业务逻辑、构造器注入 ✅
2. 事务只在 Management、跨聚合同事务编排 ✅
3. 领域层无框架技术污染、实体规则外置 ✅
4. 通用校验提取 shared 组合、无重复实现 ✅
5. 权限在应用层、数据权限随上下文 ✅
6. 聚合仓储纯净、查询仓储独立、批量防 N+1 ✅
7. 审计动作全覆盖、事件异步落库、上下文完整 ✅

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
4. 页签数据量大/操作复杂/通用？ → 独立聚合根；否则实体挂 DataObject（≤3 实体）
5. 批量/列表/报表查询？ → 独立查询模型（读模型），不加载聚合
6. 事务边界 → 始终在应用服务（biz *Management）
```
