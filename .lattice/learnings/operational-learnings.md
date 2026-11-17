[.lattice/learnings/operational-learnings.md#E6A0]
1:# Operational Learnings
2:
3:> Living document — appended by `learning-harvest` after design / implement / review cycles.
4:
5:## 用 null 表示「未裁剪」不如新增枚举值——国际化无法配置（2026-06-15）
6:
7:**触发**：将 `null` 替换为 `ElementTailoringStatusListEnum.NOT_TAILORED`。
8:
9:**模式**：
10:1. null 无法映射到列表项 item_key，框架的 columnDisplayValue 翻译机制失效
11:2. PropertiesDataSourceHandler 硬编码 `return "未裁剪"`，无法支持多语言
12:3. 新增 `NOT_TAILORED` 枚举值让所有状态统一走列表项翻译
13:4. 存量 DB 数据仍可能有 null，查询/解析处需保留 null 兼容
14:5. `@JsonValue getCode()` 返回 `getStringKey()`，key 为 null 时导致 NPE——必须分配真实 item_key
15:
16:**触发**：将 `ElementTailoringStatusListEnum.VISIBLE` 的「默认未裁剪」语义剥离为 `null`。
17:
18:**模式**：
19:1. 用 `null` 替代新增枚举值简化了改动范围：无需加 DB 列表项、无需改枚举
20:2. 上下游消费者仅判断 `== REMOVED` 的代码对 null 天然兼容（`null != REMOVED → true`）
21:3. 父子传播需显式处理 null：默认跳过 null 的逻辑要改为将 null 加入 visibleElements
22:4. 属性选项级 VISIBLE 保留（选项级「未裁剪」与元素级「无裁剪」语义不同）
23:
24:**规约**：`.agent/rules/13-entity-context-and-collaboration.md`、`.lattice/context/tailoring-null-status-design.md`
25:
26:## Context-first；禁 SDK getXxxById 捷径（2026-06-05）
27:
28:**触发**：GF 过程域移除 `IProcessService.getMetamodelIdent(Long)`，裁剪 Support 内联至 `IInnerTailorable` default。
29:
30:**模式**：
31:1. 编排入口（Management / `buildXxxContext`）一次加载聚合根，派生字段经 Context / 参数 / `IInner*` 向下传。
32:2. 不在 `I*Service` 新增「仅凭 ID 查父对象字段」的捷径 API；无上游时用 sdkImpl Loader，不上升为 SDK 契约。
33:3. 单元素逻辑 → inner/DataObject default；跨实例批量 → sdkImpl `*TabService` / Handler；禁止 `return entity.foo()` 式 Support。
34:4. 批量循环前解析共享父级属性（如 `metamodelIdent`），禁止循环内重复 `getProcess`。
35:
36:**规约**：`.agent/rules/13-entity-context-and-collaboration.md`
37:
38:## 遵循 Maven 依赖架构约束，规避反向依赖（2026-06-15）
39:
40:**触发**：设计过程元素拖拽时，`gf-sdkimpl` 需要表示拖拽类型。
41:
42:**模式**：
43:1. `infrastructure-biz` 的 `DataObjectDragType` 对 `gf-sdkimpl` 不可见。
44:2. 决不为了重用枚举而强行添加 `gf-sdkimpl -> infrastructure-biz` 的反向依赖，否则会破坏组件层级与分层单向依赖原则。
45:3. 在 `gf-sdkimpl` 的 param 包或 `gf-sdk` 契约中定义局部的 `ElementDragType` 映射。
46:4. 领域层内复用 `DataObjectFramework` 提供的 `IAdminSession.calculateRankForCurrentLevel` 核心排序计算逻辑，而非自行开发 Rank 文本拼接。
47:
48:**规约**：`.agent/rules/10-architecture.md`、`.lattice/context/process-element-batch-delete-and-sort-design.md`
49:
50:## 批量树形删除时的父辈剪枝（2026-06-15）
51:
52:**触发**：实现批量删除过程元素时，为防止多次删除或重复级联删除引发异常。
53:
54:**模式**：
55:1. 将所有待删元素 ID 存入 Set。
56:2. 对每个元素，调用 ParentId 逐级向上追溯，判断是否有任意祖先也存在于待删除 Set 中。
57:3. 若祖先在 Set 中，则从顶层删除队列中过滤剪枝（因为当祖先被删除时会递归级联删除该子元素）。
58:4. 这种方式能极大地减少数据库的多余查询和重复删除冲突风险。
59:
60:**规约**：`.lattice/context/process-element-batch-delete-and-sort-design.md`
61:
62:## 只读参考模型的合规映射与批量 SqlRunner 校验 (2026-06-16)
63:
64:**触发**：完善裁剪预览与执行中的合规映射校验，处理参考元素作为只读源被主模型引用的检测。
65:
66:**模式**：
67:1. 关系定义方向：主体元素映射到参考过程的被映射元素。由于参考过程只读，映射关系作为反向关联写在主模型侧，其在 `air_inst_gf_element_tab_association` 表中的 `related_instance_id` 是参考元素 ID。
68:2. 判定参考元素是否存在外部映射关系时，必须检查 `related_instance_id`（对应 PO 中的 `relatedInstanceId`）。
69:3. 规避 N+1 查询：禁止在循环中查询单个元素的 relationships 关系表。由于 `SqlRunner.db()` 在不启动 MyBatis 的单元测试中会抛出 `NullPointerException`，因此在使用时必须加 `try-catch` 做安全防护兼容，在异常时返回空集合。
70:
71:**规约**：`.lattice/context/tailoring-preview-compliance-validation-design.md`

## 树操作下发须在树成型后二次遍历；共享 Policy 放 gf-sdk（2026-06-16）

**触发**：过程元素树与裁剪问答树需按「有子节点 → BATCH_DELETE、非根且有子 → DRAG_ELEMENT」向 `operations` 下发。

**模式**：
1. 扁平 `convertToTreeResp` 阶段 `children` 为空，无法判断是否有子节点；须在 `buildListToTree` + `setIsLeaf` 之后递归追加操作。
2. 将判定规则提取为 `gf-sdk` 无状态 `ElementTreeOperationDispatchPolicy` + `ElementTreeOperationContext`，过程树与裁剪树共用，避免 Biz 层重复 if-else。
3. 下发挂载在**查询入口**（`queryElementTree` / `queryTailoringQuestionTree`），**非** `buildFinalTree` 内部——后者被选择器等轻量场景复用。
4. 树下发只控制 UI 入口；能否执行由既有 Handler 校验（禁删根/分类、禁环等），下发层不重复校验。
5. 分类文件夹子级保持元模型顺序、不参与 rank 排序，故不下发 `DRAG_ELEMENT`（与 `sortTreeChildrenByRank` 语义一致）。

**规约**：`.lattice/context/tree-operation-dispatch-design.md`、`.lattice/context/process-element-batch-delete-and-sort-design.md`
72:

## 树节点复杂交互操作的下发应与底层 Handler 权限解耦 (2026-06-17)

**触发**：增加导出 MMP 操作并移除过程根节点批量删除操作下发的设计。

**模式**：
1. 操作下发层（只读响应）仅控制 UI 入口的显示/隐藏逻辑，通过极简的上下文值对象（`ElementTreeOperationContext`）在内存完成判定。
2. 具体的防环、实体级联等写操作强规则仍由领域服务/操作处理器（`Handler`）统一防卫，避免在两个层级重复逻辑导致多处维护。

## 过程树根节点功能入口的聚合 (2026-06-17)

**触发**：将导出 MMP 文件的操作配置在过程元素的根节点上。

**模式**：
1. 将特定于整个主实体的顶层导出/操作挂载在树的根节点上，由根标识位（`treeRoot`）进行路由，而非创建独立的页面操作区。
2. 能有效简化页面结构设计并提升契约复用性。

## 跨多类树结构共用策略时的上下文隔离 (2026-06-17)

**触发**：实现过程元素树根节点导出 MMP 操作下发并确保裁剪树不受影响。

**模式**：
1. 当多个树节点对象（如过程树与裁剪树）共用同一个操作下发策略（`ElementTreeOperationDispatchPolicy`）时，对于特定树独有的操作（如仅过程树需要的 `EXPORT_MMP`），必须在上下文构建阶段显式指定或默认隔离（如裁剪树中默认 `treeRoot = false`），防止逻辑污染其他树结构。
2. 通过值对象属性进行隔离，比在策略内部根据特定树对象类型进行 class 判断更具扩展性且解耦。

## 复杂呈现状态封装为 DTO 只读计算属性（2026-06-17）

**触发**：统一前端对元素裁剪「划线」与「隐藏」的复杂判定逻辑，新增 `displayAsRemoved` 属性。

**模式**：
1. **计算规则统一内聚**：当界面呈现状态取决于多个持久化属性的组合（如 `status == REMOVED || forcedRemovedByParent == true`）且在多处被判定时，应在后端工具类（如 `TailoringDisplaySupport`）中内聚计算规则，避免让前端理解和组装规则。
2. **多视图 DTO 同步暴露**：同一业务概念的呈现字段必须在所有展示相关的 DTO/VO 中同步暴露（如树节点 `ProcessElementTreeResp`、列表索引 `ProcessElementIndexData` 以及比对树 `ProcessElementTreeCompareDto`），保证页面行为一致性。
3. **只读字段零数据库副作用**：纯呈现辅助属性仅作为 DTO 里的只读计算字段在后端组装时动态计算并返回给前端，不需要任何持久化结构变更或数据库 schema 迁移，保持了只读路径的高效与解耦。

**规约**：`.lattice/context/tailoring-display-as-removed-design.md`

## 通用数据模型（DataObject）在低层插入时需防范类型缺失（2026-06-17）

**触发**：解决裁剪问答动作关系由于 `association_category` 为空导致过程版本复制丢失的问题。

**模式**：
1. **低层直接操作绕过安全保障**：使用 `DataObjectFramework` 提供的通用表数据写入接口（如 `IInnerTable.createRows`）插入关联关系数据时，会绕过高层自带默认填充的辅助方法（如 `addAssociation`）。这导致非持久化约束但为业务核心分类的字段（如 `association_category`）为 `NULL`，形成隐性脏数据。
2. **批处理或 SQL 级严格过滤隐患**：库中含有 `NULL` 分类的数据虽然能通过特定业务动作码（`relationship_type`）在业务查询中正常加载（因为不带分类过滤），但当进行过程升级、版本复制（`GfElementTabAssociationCopyMapper`）、或者跨模块导入时，通用底层 SQL 筛选逻辑大多默认按照合法分类值（如 `1` - NORMAL）过滤。这导致脏数据静默丢失，从而在升级后的版本中关系消失。
3. **防御式补齐**：所有使用 `createRows` 进行通用关联表写入的地方，均应显式在属性图中补齐 `association_category`，确保数据完整性并与底层升级逻辑对齐。

**规约**：`.lattice/context/tailoring-answer-association-version-copy.md`

## 假设操作叠加试算模式（2026-06-25）

**触发**：设计手动裁剪预览——用户在执行 TAILOR_IN/TAILOR_OUT 前先预览强制可见/移除效果。

**模式**：
1. 预览接口接受假设操作（内存 DTO，如 `ManualActionDraft`），转为引擎内部 pending 行（`TailoringDecisionRowFactory.buildHypotheticalManual`），不落库。
2. 假设行与库中现有决策共存于 `pendingByTarget`，由 `aggregate` 按 `PriorityScore` 裁决（MANUAL > WIZARD），与执行路径语义一致。
3. 假设操作的元素**不进**预览过滤集（`manualTailoredElementIds`），自然出现在 toRemove/toAdd 结果中；库中已锁死的手动元素仍被过滤。
4. 传播逻辑（`ParentChildPostProcessor` 的 forcedRemovedByParent / FORCED_VISIBLE）完全复用，无需为预览单独实现。
5. SDK 接口用重载方法扩展（`calculate(processId, answers, manualActions)`），旧调用点零改动。

**适用范围**：未来其他「先预览再执行」场景（如假设移动、假设删除预览）可复用此叠加试算模式。

**规约**：`.lattice/context/tailoring-manual-preview.md`

## AirConstants 不放内部固定业务 ID（2026-07-17）

**触发**：设计「创建工作区后自动分配建模主管工程师」时，误将角色模板种子 ID 规划进 `AirConstants`。

**模式**：
1. `AirConstants`（AirAPI）是对外契约面：适合分类 `classId`、页签/属性 `apiId`，以及文档标明的极少例外。
2. 角色模板实例 ID、JobFunction ID、业务种子行 ID 等**内部固定 ID** 不要写入 `AirConstants`。
3. 落点：消费侧 sdkImpl 常量（如 DataObject 上 `static final`，对齐 `WORK_SPACE_OWNER_JOB_FUNCTION_ID`）或模块内常量类；仍禁止魔法字面量散落。
4. 已写入 `.lattice/standards/knowledge-base.md` §5 与 `architecture.md` Validation Checklist，供各 AI 工具经 Lattice 加载。

**规约**：`.lattice/standards/knowledge-base.md`、`.lattice/standards/architecture.md`

## 单测用 mvn 直接跑，不走 run_unit_tests.sh 脚本（2026-07-28）

**触发**：修复单测时走 `run_unit_tests.sh` 额外引入模块推断与批量逻辑，增加耗时和复杂度。

**模式**：
1. 日常单测直接用 `./mvnw -pl <module> -o -Dtest=<TestClass> test`
2. 改了上游模块：`./mvnw -pl <module> -am -Dtest=<TestClass> test`
3. 不要走 `run_unit_tests.sh`（封装了模块推断、批量合并、profile 等逻辑，单类场景过度）

## 签入/签出/取消签出的 flags 位状态转换须对称维护（2026-08-04）

**触发**：修复版次撤销——`markAsCheckedOut`（假签出）未反向处理签入对主对象与页签 flags 的写入，导致撤销后目标版次残留历史态标记。

**模式**：
1. 签入/签出/取消签出对 flags 的写操作是成对的状态机转换（主对象：最新签入版次第 1 位/最新版本第 2 位；页签：原操作行第 0 位）；任何绕过正式 `checkOut` 的流程（假签出模拟）必须显式执行签入变更的逆操作，不能只置 `checked_out`。
2. 逆处理以「签入时的目标状态」为基准逐项核对：主对象 flags 恢复最新签入/最新版本位，页签原操作行恢复生效位；新增/修改产生的新行由取消签出按 revision_out/in 删除，无需处理。
3. 逆操作的行集合应与签入行集合完全一致（同样的 changeControl/ignoreTable 过滤），避免漏表或误改。

**规约**：`.lattice/context/revision-revert.md`

## cancelCheckout/switchRevision 后原内存对象残留撤销前数据（2026-08-04）

**触发**：修复版次撤销——`syncCommonTableAfterRevert` 用 `dataObject.getName()` 还原名称得到撤销前的旧值。

**模式**：
1. `cancelCheckout` 删除当前版次行后仅 `remove(0)`/`setCurrentRev`，不刷新原对象的 row 与 name 等字段；`switchRevision` 返回的新对象被调用方丢弃。
2. 在变更类操作后读取对象字段，须按目标版次/目标状态重新加载（如 `IAdminSession.getInnerObjectNP(revision)`），不能信任原内存对象。
3. 版次 DTO（`RevisionDto`）不携带业务字段（name），跨版次取字段值需经 `getInnerObjectNP(IRevision)` 加载数据对象。

**规约**：`.lattice/context/revision-revert.md`

## 飞书画板节点级编辑走 raw 回写；theme 文本色码与幂等 token 是主要坑（2026-09-08）

**触发**：DQA 领域模型文档画板的术语统一与布局调整（同行评审改名、ProblemReport 归属、外部协作移侧边、UC-TR 便签补全、UC-NCR 编号统一）。

**模式**：
1. **可行路径**：`whiteboard +export --output-type raw` → 本地编辑节点 JSON → `whiteboard +update --input_format raw --overwrite`。SVG 往返有损（表格结构/连线绑定/容器类型丢失），DSL/Mermaid 属全量重绘，都不适合节点级无损编辑。报 99992402 时 hint 会劝阻"不要直接编辑 openapi json"，实际可忽略——按第 3 条定位字段即可。
2. **唯一确认的硬拒字段**：`theme_text_color_code` 带真实主题码（≠ -1）的节点必被 `field validation failed` 拒绝（单节点即可复现）。修复：码改回 `-1` 并补显式 `text_color` 等价值。`theme_fill_color_code`、`shadow`、`border_radius`（四角对象形）、`border_dasharrays`、`locked`、`parent_id`、`angle`、`z_index`、`connector.turning_points` 均实测可正常回写。
3. **`--dry-run` 不可作判据**：它只做浅层 schema 校验，通过不代表真实写入能过。校验失败的写入无副作用（画板保持原样），可直接在目标画板做「全量内容 + 单一变换」二分定位，不要新建实验画板。
4. **`--idempotent-token` 最少 10 字符**：过短报 invalid_argument，且错误只走 stderr、stdout 为空，脚本解析需兼容两侧。每次加回/变换测试的 token 必须不同，避免幂等重放。
5. **写回后节点 ID 全部重分配**：多轮修改必须每轮重新 export 取新 ID（或按几何位置/文本特征定位）；多处修改要在同一基线文件上一次改完再写——基于旧基线写第二处会把第一处冲掉。
6. **`docs +update --command append <whiteboard type="blank">` 建的空白画板可能「假活」**：`+update` 返回 ok:true 但内容不落盘（export 0 节点），不能当实验板。
7. **留底与回归**：覆盖写之前先 `+export raw` + `+export preview` 双留底；写完用 preview 导出做视觉回归；正文术语清理用 `docs +fetch` 全文再 grep 一遍。

**规约**：lark-whiteboard skill「编辑 Workflow」、本文档


## DB 集成测试本机执行：env 变量盖 profile + 五个坑（2026-09-21）

**触发**：修订记录 change_id 翻译修复（RevisonMapper.xml）需要真库回归，localzcc profile 指向的 localhost test3.1 已不存在。

**模式**：
1. 库：`test3_7_zcc`（pocmerge_v37merged 模板克隆 + 手工应用缺失迁移；apps/full 不依赖 upgrade 模块，启动不会跑 Flyway）。跑法：`mvn.cmd -o -pl infrastructure/biz -am '-Dtest=XxxTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，env `SPRING_PROFILES_ACTIVE=localzcc` + `SPRING_DATASOURCE_URL=jdbc:p6spy:postgresql://localhost:5432/test3_7_zcc?allowMultiQueries=true`（env 优先级高于 profile yml，不改用户 profile）
2. 根 pom `testFailureIgnore=true`：测试失败 mvn 仍 BUILD SUCCESS / exit 0，只认 surefire `Tests run: x, Failures: y` 行与 target/surefire-reports
3. PowerShell 传 Maven `-D` 键含点号必须整体加引号（`'-Dsurefire.failIfNoSpecifiedTests=false'`），否则在点号处拆参报 Unknown lifecycle phase
4. 独立 `-pl` 编译报"找不到符号"＝本地 SNAPSHOT 陈旧（实证 infrastructure-sdk 缺 ProgramRuntime），直接加 `-am` 走工作区反应堆，别查代码
5. 无 psql 时用 `jshell --class-path <~/.m2 的 postgresql jar>` 只读查库/SQL 语义干跑；夹具用唯一时间戳 ID + `@BeforeAll` SqlRunner 直插最小 NOT NULL 集 + `@AfterAll` 清理；DB 集成测试放 infrastructure/biz（sdkImpl 无 DB 测试先例，全 Mockito）

**规约**：`backend/AGENTS.md` §一、`.pi/skills/maven-offline-testing/SKILL.md`、本文件「单测用 mvn 直接跑，不走 run_unit_tests.sh 脚本（2026-07-28）」

- 2026-08-28 [design] 在扩展已有关系能力前，先核对现有关联字段、来源字段和版本/去重语义；能复用就不新增平行属性，避免重复建模与下游过滤遗漏。

- 2026-09-21 [bug fix] 接口慢先分层计时，区分「每次调用的固定开销」与「数据规模」：DB 直测同形状 SQL 仅 17ms 而接口 848ms（27 次调用）⇒ 瓶颈在应用侧固定成本 —— 减少**调用次数**（合并/批量）优先于优化单条 SQL。
- 2026-09-21 [bug fix] 把串行循环改并行前，先给共享的请求/上下文对象每任务一份副本：串行代码里就地 `setType` 的 DTO 在并行下会被互相改写，且只在并发断言或新旧对拍时才暴露。
- 2026-09-21 [bug fix] 合并多个分类的计数时，复用每个分类自己的框架 SQL 再 `UNION ALL`，而不是手写 `GROUP BY`：权限/回收站等条件与单分类路径逐字同源，聚合层不必重实现过滤，口径风险最低。
- 2026-09-21 [bug fix] 并行路径下 Mockito `mockStatic` 会失效（线程局部）：工作线程落到真实静态调用导致 NPE/结果不一致；测试改为安装真实且跨线程可见的静态 `ApplicationContext`（`GenericApplicationContext`）。

## TableRegistry 有 tabId 的注册不进 apiId 索引（2026-10-10）

**触发**：smarturl 解析下沉重构评审（c7da96c0 → dev-3.8-qs cherry-pick）。

**模式**：
1. `TableRegistry.register` 规则：「有 tabId 只注册 tabId，不注册 apiId」；`@RegisterTable(tabId=…, apiId=…)` 双写的类，apiIdRegistry 中查不到该 apiId。
2. 任何按 apiId 兜底查找的调度逻辑（如 `TableSmartUrlServiceImpl`）对 tabId 注册的类必然 miss；前置条件是该逻辑仅在请求 tabId 非空时进入，tabNode 由固定 tabId 解析命中（`TableIds` 硬编码 ID 的既有假设）。
3. 写 apiId fallback 测试前先确认注册路径——mock 出生产不可达的 registry 状态会让测试锁定假行为。
4. tabId 命中即用与 `getTableByTabIdOrApiId` 的「命中但 apiId 不匹配则回退」是两套语义；新增调度应复用同一解析入口（可将 `resolveRegistryEntry` public 化）。

**规约**：`.agent/rules/10-architecture.md`
