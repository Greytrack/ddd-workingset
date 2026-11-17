---
feature: process-group-model
requirement_doc: .lattice/requirements/v3.7/requirements/REQ-3.7-Task0001292-过程组.md
created: 2026-08-28
status: complete
---

# 过程组模型（Process Group Model）

> 显式化「过程」并新增「过程组」一等过程元素，构建「过程组 → 过程」导航层级；过程地图画板按 L0–L3 投影展示过程元素树（CQRS 读模型），节点展示建模统计与已建模/空壳标识；作为过程导航默认落地页（主地图全局唯一，多地图切换）；过程间定义工作产品输入输出关系，经既有绑定关系自上而下传递为下层工作区接口（来源「过程模型传递」），参与下层可疑工作产品检查（复用 REQ-1277）。工作产品归属遵循「谁产出归谁」。

## Decisions Log

| Date | Decision | Reasoning | Alternatives Considered |
|------|----------|-----------|------------------------|
| 2026-08-28 | 设计会话启动，创建上下文锚文档 | design-blueprint Step 1；需求路径显式指定（REQ-3.7-Task0001292） | 沿用需求文档自带边界（已存在，直接链接） |
| 2026-08-28 | 范围裁定：过程→工作区绑定/指向机制**不在本设计范围** | 用户裁定：绑定关系已在原模型中定义（绑定到目标工作区的工作流/活动根节点，经过程元素间关系实现），本期仅消费；后续如有变化单独设计 | 在本设计中重新定义指向存储 |
| 2026-08-28 | 范围裁定：权限矩阵**不在本设计范围** | 用户裁定：权限由配置决定 | 设计三级权限矩阵（需求功能6.4/AC-30） |
| 2026-08-28 | link 标识实现方式定为**新增树 marker 算法** | 比照 UcWorkProductSuspiciousMarker：经 external.json.treeMarkers 挂载（uc_pg），前端经 query_element_marks 调用打标展示；标记体系内新增一种 | 前端自行判断绑定状态；REST 侧新增独立查询 |
| 2026-08-28 | 传递接口**不新增来源属性**：复用 `association_type`（接口类型）+ `ORIGINAL_WORKSPACE_IDS` 来源标记派生区分 | 接口行 association_type 已承载 input/output 语义（业务键/版本对比承重，不可挪用）；模块导入接口已有 ORIGINAL_WORKSPACE_IDS 来源先例；「过程模型传递」= category=INTERFACE ∧ 来源指向导航工作区 的派生判定 | 新增 TransferSource 列表属性（冗余，与既有来源标记机制重复） |
| 2026-08-28 | Level 1 Capabilities 获批（5 项，v3） | 见 `## Design: Level 1 -- Capabilities` | — |
| 2026-08-28 | 画布/布局持久化属性统一命名 **view_schema**（RichText，DB text 长文本），过程与过程元素各一 | 用户指定；`AttributeTypeEnum.RICH_TEXT(10)` 物理列型即 text | MapLayout/CanvasData 分名 |
| 2026-08-28 | Level 2 Components 获批（后端 B1–B9；前端移除，由专门同事负责） | 见 `## Design: Level 2 -- Components` | — |
| 2026-09-14 | 增量启动：被引用过程输出要求标记（type 码 `process-group-output-requirement`） | 用户需求：过程组模型中过程/过程组 P1 经 linked 绑定目标工作区顶级过程元素 P1'，P1 的 output 工作产品要求须在 P1' 上打标，提醒目标工作区建模者覆盖该要求；语义经两轮澄清定稿 | 在 uc_pg 侧节点按自身 output 打标（首版理解，用户纠正否决）；新增独立查询接口（违反只读投影约束） |
| 2026-09-14 | 数据链路：目标侧反向行驱动（`linked ∧ direction=REVERSE`，判别字段后被 2026-09-14 实测修正改为角色列，见下）→ 批量载引用方元素 → 按引用方过程批量载其 output 行 → 批量载工作产品（发现权过滤 + 工作区+commonId 去重 + 跳转参数）；无反向行零额外查询 | `addAssociation` 正反向双写 + 关联按 workSpace 过滤加载 ⇒ 反向行已在目标工作区关联集内，零新增存储/接口 | 按正向行判别（uc_pg 自引用场景会把「我引用别人」误判为「别人要求我」）；新增 relatedInstanceId 反向查询（双写下不必要） |
| 2026-09-14 | 挂载面修订：树打标挂载从「uc_pg external.json」扩为「打标对象所在元模型 external.json」（本增量挂 uc / uc_pg 两处） | 打标对象在目标工作区树，marker 由树自身元模型配置驱动；角色列判别 + 惰性构建使宽挂载零成本；未见绑定目标元模型限制的代码证据 | 仅挂 uc（若产品确认绑定目标仅限标准过程模型可缩） |
| 2026-09-14 | 载荷新增 `OutputRequirementMarkData` 子类（结构同 LinkTargetMarkData：targets 列表） | 遵循 2026-08-28 载荷扩展裁定「新标记=新载荷子类+oneOf 注册」；LinkTargetMarkData 已定为 process-group-link 专用 | 复用 LinkTargetMarkData（语义命名不符、违反专用约定） |
| 2026-09-14 | 增量设计批准：Level 1–4 获批，status 维持 approved——被引用过程输出要求标记可进入实现（`/code-forge`） | 四级设计完成；漂移检查无冲突 | — |
| 2026-09-14 | 增量实现完成，status 置 complete（后端范围） | C1–C6 全部落地；21/21 单测通过；分层校验合规 | — |
| 2026-09-14 | 实测修正：被引用行判别字段由 `direction=REVERSE` 改为角色列（`target_role=caller`） | localzt 库实测：双写写入时角色交换条件命中（`pathTargetRole==targetRole`），发起侧行 `direction=0`、被引用侧行 `direction=1`，与设计假设相反；角色列按行视角稳定记录（被引用行恒为 `source_role=callee ∧ target_role=caller`），direction 为写入期约定不作判别 | 保留 direction 判别（实测永不命中，marker 失效）；双向兼容两种 direction 约定（过度设计，角色列已是稳定语义） |
| 2026-09-14 | 审计结论：linked 关联写入逻辑为**既有约定**（2026-05 引入），本次实现无写入侧代码改动；direction 绝对值依赖写入路径角色表达，非稳定"发起方"语义，角色列才是稳定判别；marker 按角色列判别的修复符合写入惯例 | 双写+方向交换为 2026-05-14/27 既有（关联页签 sourceRole/targetRole 调整 + 合规映射）；v3.7 提交仅 +6 行未触 swap；跨工作区自动 INTERFACE 分类为既有派生规则（AssociationServiceImpl:280）；localzt 实测：linked 两对均发起方 dir=0/对端 dir=1（UI 带角色路径触发交换），output 对 1/0（无角色路径默认），均满足"对端行方向翻转"配对不变量 | 修改写入侧统一 direction 语义（破坏既有配对/版本比对消费方，过度）；marker 继续用 direction（实测不可靠，已否决） |
| 2026-09-14 | v1.1 试验元模型清理：删除 `mm/uc_v1_1/*`（ident `uc_1_1`，原 id=3）与 web `Gateflow/uc_v1.1.json` / `tto_v1.1.json`；本增量挂载面相应收敛为 uc / uc_pg 两处 | 注册机制为 `MetaModelConfiguration` 扫描 `classpath:mm/*/process.json` 目录，无代码/Flyway 显式引用 uc_1_1，删除即注销；试验模型已废弃（前端 `ACTION_MODEL_PROPOSAL.md` 2026-07-09 标注过时） | 保留试验模型（无人消费，徒增歧义）；仅删 web 侧（后端残留会让新建工作区可选已废弃模型） |
| 2026-09-14 | 发版核查项：删除 uc_v1_1（原 metamodel id=3）后，`air_inst_product_space.metamodel`（int8）指向 id=3 的存量工作区将悬空（树/打标不可用）；发版前执行 `SELECT count(*) FROM air_inst_product_space WHERE metamodel = 3;` 核查 | 元模型 id 为工作区硬引用，注销即失效；ProcessGroupModelE2ETest 用 UCPG_METAMODEL_ID=4L（uc_pg）不受影响 | 无存量（理想态）；发版前人工确认 |
| 2026-09-15 | 用户裁定修正：linked 绑定目标工作区限定为**过程模型（uc）**——过程组模型(uc_pg)/参考模型工作区不可作为绑定目标；被引用过程输出要求标记挂载面由 uc/uc_pg 收敛为**仅 uc**；候选面（关联选择器/快速搜索）与写侧（addAssociation）同步限制 | 冒烟用例 42/46/48 预期裁定 2026-09-14 增量设计有误（原允许绑定过程组模型工作区并双挂载 uc_pg） | 保留 uc_pg 挂载（否决：违背目标限定，uc_pg 树上永不命中徒增歧义）；仅前端过滤候选（否决：API 直调可绕过，须后端收口） |
| 2026-09-15 | 二次修订：限定规则改为**元模型声明驱动**——关联声明 `targetMetamodelIdents`（`MetaAssociation`/`schema.Association` 新字段，uc/uc_pg 的 linked 均声明 `["uc"]`），后端写闸/关联选择器/快速搜索一律读声明，代码里不再写死 uc；前端同源读 widget config `targetMetamodelIdents` 透传给树接口 | 用户质询「过滤可否在元模型控制、不前后端写死」：写死常量违背「元模型是行为 SSOT」；声明化后改元模型即生效，新增元模型无需改代码 | 代码常量（否决：新增/调整元模型必须改代码重发）；前端硬编码 ident（否决：与元模型声明双写易漂移） |
| 2026-09-15 | 候选过滤下沉到**工作区层面**：`tree_list` 新增 `metamodelIdents` 请求参数（按工作区 `metamodelId` 即元模型 ident 剪枝，命中后代上移、空分组收掉），关联浏览弹窗直接拿到已剪枝的工作区树 | 用户质询「可否在工作区层面过滤，而不是等选择节点时再过滤」：原实现在元素选择阶段才返回空，用户要先点进工作区才知道不可选 | 仅保留选择器过滤（否决：体验为「点进去才发现空」）；前端客户端过滤（否决：与后端双写、且绕不过 API 直调） |
| 2026-09-16 | 增量启动（design-blueprint）：被引用过程标记载荷扩展为**全量关联内容**——每行含 元素名称/编号、类别（原始关联类型）、所属过程名+编号、所属工作区名+编号，urlInfo 保留；分类与统计归前端，纯后端增量，前端零改动。采集排除 linked（由 process-group-link 标记承载，避免重复下发绑定自身）与 sequence/execute 流程结构行；其余类型按实际行采集不做白名单 | 用户三连裁定：①不局限于弹窗所示内容、分类统计前端完成；②linked 不纳入；③归属「两者都给」= 过程名+工作区名称，并含编号信息（本域名称不唯一，编号为判别键；前端不修改 ⇒ 数据一次给全避免契约二次变更） | A 扩展本标记载荷（采纳）vs B 仅补过程名（角色/指南无数据源，弹窗名不副实）vs C 新增独立关系标记（同元素双标记冗余、载荷翻倍）；归属单口径 vs 两者都给（用户裁后者） |
| 2026-09-16 | L2 获批：新增专用行类 `ElementRelationTargetDto`（不扩展共用 `ElementMarkTargetDto`）；随语义更名——type 码 `process-group-output-requirement` → `process-group-relation-content`，枚举/marker 类/载荷类/上下文字段同步更名 | 沿 2026-09-14「载荷专用不共用」先例（link 契约零污染）；v3.7 未发版 + 前端对该 type 零消费 = 更名零成本窗口；名实相符长期收益 | 扩展共用 DTO（否决：link 场景一半字段恒 null、swagger 含混）；保留原名仅改描述（否决：名实不符长期留存，且发版后更名成本陡增） |
| 2026-09-16 | 设计批准于 Level 4。status 置 approved——可进入实现（`/code-forge`） | L1–L4 四级完成并逐级获批；漂移检查无冲突（增量能力沿 2026-09-14 裁定不写回 REQ） | — |
| 2026-09-16 | 排除面修正（用户质询）：「排除 linked/sequence/execute」不对——**仅排除 P1↔P1' 绑定指向行**（type=linked 且对端=被标记元素，即「PG 要求 P1」关系载体本身）；P1 其余关联行（含指向他元素的 linked、sequence/execute）全部下发；采集面扩大+字段增加，反向行驱动/批量解析/去重基本逻辑不变 | 用户示例定稿：PG 指定 P1 输入/输出 W1、角色 R1 → P1' 提示须符合该要求；不展示 P1 与 P1' 的指向关系；其他关系都展示 | 排除集 {linked, sequence, execute}（否决：排过头，违反「其他的关系都展示」）；整类排除 linked（否决：P1 指向其他元素的 linked 行属「其他关系」须展示） |
| 2026-09-16 | 增量实现完成：type 码/枚举/DTO/marker/挂载全部落地并更名；目标测试 36/36 通过（工厂 21 + marker 3 + 插件 12）+ Spotless。实现决策：category 空值回退 `unknown`（沿 `buildTabAssociationsMap` 惯例升格常量）、归属字段 null 安全直塞、DTO 模板 `toBuilder` 回填 category | 按蓝图 Full autonomy 落地；实现期修正 3 处：`workspaceIds` 声明被 hunk 误删、`MetaProcessExternalPluginTest` markerKey 断言漏改、Mockito 嵌套 stubbing（stubbing 型 helper 不得在 `when()` 参数内调用） | 排除集常量方案（已被用户修正裁定否决，改为装配期成对判别零新常量） |
| 2026-09-16 | 代码评审收口（4 并行评审组：标记核心 / 标记测试 / 元模型声明化过滤 / 工作区过滤+前端）：无 blocker。当场修复 2 处测试缺陷——绑定行排除断言空转（urlInfo 非空前置条件恒 false，改为按保留行名称+类别断言）、顺序钉死断言改 anyMatch（MixedRelationTypes 同类缺陷一并修），工厂 21/21 复绿 | 评审组 2/4 因上游模型 429 结构化产出失败，结论自 transcript 抢救；遗留：①major 待裁定——metamodelIdents 剪枝的 Javadoc/@Schema 与决策日志均写「命中后代上移为根」，实现实为保留容器节点（文档/代码谁对需用户裁定）②minor：快速搜索元模型过滤与写闸 null targetProcess 分支无测试 ③nit：前端 queryKey 空数组缓存分桶 | 排除集常量方案（已否决）；按 urlInfo 空转断言继续沿用（否决：守门失效） |
| 2026-09-16 | 用户裁定：metamodelIdents 剪枝语义定为**保留容器**——未命中但有命中后代的工作区保留为容器节点（命中的子工作区维持在原层级，不上移）；Javadoc/@Schema 已按此措辞修正，2026-09-15 决策行「命中后代上移」以本裁定为准 | 保留容器让用户看到「目标工作区挂在哪个导航容器下」的上下文，且零代码改动（实现本就是该行为，文档/决策措辞失真）；真上移需 reparenting 逻辑+测试且丢失层级上下文 | 实现真「后代上移为根」（否决：改动大、丢容器上下文）；维持文档失真（否决：契约与决策记录双重失真） |
| 2026-09-16 | 排除面二次修正（用户实测质询）：linked 行**全部排除**——不再保留「指向他元素的 linked 行照常下发」；linked 是「过程↔工作区绑定」载体，出现在「关系待构建」提示中语义不合理。过滤下沉至引用方关联行装载期（`loadReferencingRelationRows` 装载时排除），装配期成对排除（`isBindingRow`）删除；2026-09-16「排除面修正」行以本裁定为准 | 用户以实测返回值裁定（GF00002/GF00003 的 linked 行仍出现在载荷中不合理）；「提示存在关系待构建」应只承载 input/output/角色等真实关联要求 | 保留绑定行成对排除、下发他元素 linked 行（否决：绑定语义不属于关联要求） |
| 2026-09-16 | 标记落点修正（用户裁定）：关联内容标记从「被绑定元素」重定位到**工作区根元素**——沿 parentId 上溯到顶（生成的根分类节点 rootType，或顶层过程；父链缺失时退化为绑定元素自身）；绑定写侧行不变（仍指向根下顶级元素）；marks 元素集补入根分类节点（`getAllElements` 不含 rootType，`query_element_marks` 侧合并 `getRootElements`）；同根多绑定行归并为同一标记 | 用户指出标记应挂在树的根节点而非根下第一个元素；绑定写入不动，仅改打标键 | 改绑定写入侧（否决：用户明确只改打标元素） |
| 2026-09-16 | 标记落点定稿（用户裁定）：关联内容标记挂载到**过程节点**（工作区对应的过程实例，树根；如 623809242255712「过程模型」）；上一行「工作区根元素（parentId 上溯）」落点作废——实测库中被绑定行 instanceId 即为生成的 rootType 分类节点（parent_id=0，无父可上溯）。逻辑收敛在 `ProcessGroupRelationContentMarker`：上下文新增 `processInstanceId`（工厂构建期注入，纯数据通道），marker 以最小 id 绑定元素为唯一锚点触发，多绑定元素目标行按 类别+工作区+commonId 去重合并；工厂 parentId 攀爬、biz 元素集合并全部回退 | 过程节点是「谁被过程组要求对齐」的正确语义落点；改动面收敛符合用户「不蔓延」要求 | 挂 rootType 分类节点（否决：树根语义不符）；改绑定写入侧（用户否决） |
| 2026-09-23 | Bug 16129 修复：过程整体删除与版本删除校验均排除过程组 linked 绑定行——`hasProcessInterface` / `hasVersionInterface` 各追加 `association_type IS DISTINCT FROM 'linked'`（linked 绑定是「过程被引用」载体，非目标过程对外提供的真实接口）；整体删除新增 `deleteLinkedAssociationRows` 挂入 deleteProcessChain，按与校验对称的范围子句（过程 ID + 工作区/工作集）删除 linked 双向成对行，覆盖引用方元素已被先删、`related_process_id` 为 NULL 的脏行（仅元素 ID 匹配会漏删）；版本删除的关系切断链（severRelationshipData 按 workingset/workspace 双侧范围删除）已覆盖双向行，无需补清理 | test35 实测：0923 工作区被两个导航工作区 linked 引用（4 行 INTERFACE∧linked）致 delete_process 报 20001135；审查复审发现同因兄弟路径 hasVersionInterface 未同步（版本删除 20001141 会同样被阻塞），已同修并各配单测；事务内模拟 DELETE 恰命中 4 行、其他工作区 6 行 linked 不受影响 | 仅改校验不清理脏行（残留垃圾行会让导航侧 marker 持续点亮）；linked 行继续算接口（用户否决：无法删除过程） |

## Open Questions

- 需求开放问题（源自需求文档）：源需求描述第 7 条「过程上图用于展示过程的 shou fenng q」（疑为"手风琴"）语义待产品澄清，影响 AC-8b（首文件夹画布自由元素绘制）范围。

## Constraints

（承自需求文档 `## 边界与约束`，非协商）

- 过程地图为过程元素树的**投影读模型**（CQRS）：不做独立版本管理、不独立发布
- 过程指向工作区 = 跳转链接存储：不复制数据、不实时同步；1:1（预留 1:N 扩展）；过程不可指向其所属导航工作区自身
- 元模型有过程数据后不可切换；存量过程模型工作区不强制升级；两套元素体系并存期间由元模型动态约束元素类型合法性
- 过程不承载下级工作流，工作流/活动统一在工作区内建模（过程模型不变）
- 过程组层级不设上限（任意层递归），过程为叶子
- 画板按深度映射 L0–L3 视觉层级；大节点量分层懒加载渲染，不限节点数量
- 主地图全局唯一（一个产品空间同一时刻仅一个），新设置互斥取代；多地图切换仅影响本次浏览
- 工作产品传递方向仅自上而下（上层 → 下层），反向不传递
- 传递工作产品为**引用视图**：内容归属源工作区，下层不可编辑；「外部输入」豁免维护在下层工作区侧
- 传递接口来源标记「过程模型传递」，与手工过程接口并存且可区分；手工接口仍不在建模树展示为节点
- 传递接口版本规则复用过程接口机制（工作版本 ↔ 工作版本、有效版本 ↔ 有效版本）
- 本期画板不绘制过程间传递关系连线（连线展示随画板二期能力评估）
- **绑定关系/指向机制（原需求功能3、AC-9~AC-14）不在本设计范围**：原模型已定义（绑定到目标工作区工作流/活动根节点，经过程元素关系实现），本期仅消费
- **传递接口行不新增属性**：来源区分走派生判定（category=INTERFACE ∧ ORIGINAL_WORKSPACE_IDS 指向导航工作区）；association_type 保持与源关联一致，不得挪作来源标记（业务键/去重/版本对比承重）
- **权限矩阵（原需求功能6.4、AC-30）不在本设计范围**：由配置决定
- 编码继承、导出图片、搜索定位 → 二期；首页（引导页）降级为个人 Dashboard → 独立需求，不在本需求
- 树打标唯一入口为 `IElementMarkService.resolveMarks`（`/api/gate_flow/process/query_element_marks`）；新 marker 经打标对象所在元模型 `external.json.treeMarkers` 配置挂载（当前仅 uc——2026-09-15 裁定 linked 绑定目标仅 uc 工作区，uc_pg 挂载移除；uc_v1_1 已删除，见决策日志），不改 REST 契约
- **linked 绑定目标限定（2026-09-15 裁定，元模型声明驱动）**：规则声明在元模型关联上——`MetaAssociation.targetMetamodelIdents`（uc/uc_pg 的 linked 均声明 `["uc"]`），后端写入侧（addAssociation）、关联选择器（单区/集合批量）、快速搜索三处读声明校验/收口；前端读同源 widget config `targetMetamodelIdents` 透传给 `tree_list.metamodelIdents` 在**工作区层面**剪枝；代码中不写死元模型 ident，改元模型即生效
- 工作产品跨区可见性只扩查询链路，不复制数据（与「引用不复制」总原则一致）

## Key Files

| Path | Role |
|------|------|
| `.lattice/requirements/v3.7/requirements/REQ-3.7-Task0001292-过程组.md` | 需求文档（source of truth） |
| `backend/components/gf/sdkImpl/src/main/resources/mm/uc_pg/process.json` | uc_pg 元模型定义（processGroup/process subElements 已存在） |
| `backend/components/gf/sdkImpl/src/main/resources/mm/uc_pg/external.json` | uc_pg 扩展配置（treeMarkers、dataObjectSubClassApiId 映射） |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/service/MetaProcessService.java` | 元模型加载/注册/视图查询 |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/service/ProcessElementSubclassResolver.java` | 元模型元素 → DataObject 子类解析 |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/service/ProcessModuleServiceImpl.java` | 接口行补建/来源标记写入先例（createEdgeRowWithCategory） |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/support/AssociationMatcher.java` | 关联业务键（association_type 承重证据） |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/dataobjects/ModelNavigationDataObject.java` | IsDefault 全局互斥范式（主地图参照） |
| `backend/components/gf/biz/src/main/java/com/airedgesoft/gf/biz/modelnavigation/` | 模型导航 REST/Management |
| `backend/components/gf/biz/src/main/java/com/airedgesoft/gf/biz/processinterface/` | 过程接口（版本规则、引用提示参照） |
| `backend/components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/enums/AssociationCategoryEnum.java` | 关联分类枚举（INTERFACE = 3） |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/markers/uc/UcWorkProductSuspiciousMarker.java` | 可疑标记（REQ-1277 参照，未含外部输入豁免） |
| `backend/components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/tree/ElementTreeOperationDispatchPolicy.java` | 树操作下发策略 |
| `web/src/frontend/pages/Gateflow/router/index.tsx` | 过程导航（将改过程地图）路由 |
| `web/src/frontend/pages/Gateflow/pages/Dashboard/` | 首页 Dashboard（卡片视图落点） |
| `web/package.json` | @xyflow/react 12.9.3（画板基建） |
| `backend/components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/ElementMarkServiceImpl.java` | 树打标服务（resolveMarks 唯一入口） |
| `backend/components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/tree/IElementMarkService.java` | 树打标 SDK 契约 |
| `backend/components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/enums/ElementMarkType.java` | 标记类型枚举（link 标识将新增） |

## Design: Level 1 -- Capabilities

### 1. 元模型体系与命名
新建工作区可选元模型：过程模型 / 过程组模型 / 参考模型；产品内命名统一（统一标准模型→过程模型、文档建模模型→参考模型、过程导航→过程地图并置第一顺位、过程版本→版本、过程接口→接口）；过程组模型工作区提供 过程组+过程+工作产品+角色+指南 五种过程元素，手风琴名/首文件夹由元模型定义；存量过程模型工作区行为不变、不显示过程组/过程；过程模块功能对过程组模型不适用（不展示）。

### 2. 过程组建模与导航层级
过程组任意层嵌套、过程为叶子；增删改移（删除含子级拦截、移动防环、拖拽改父级）；流/表格/网格三视图、卡片属性与工作流过程元素一致；link 标识 = 新增树 marker 算法（treeMarkers 挂载 + query_element_marks 调用），过程绑定工作区内容时打标、未绑定不打；首文件夹画布支持自由元素绘制（浏览态/编辑态）。

### 3. 过程地图（画板+统计+落地页）
L0–L3 投影画板（渐进展开/收起、过程组节点详情面板、缩放/平移/拖拽布局/重置、空状态引导、卡片视图含定级根工作区画布缩略图）；过程节点展示绑定工作区建模统计（工作流/活动/工作产品/关联数）与已建模/空壳标识；作为过程导航默认落地页，主地图全局唯一互斥、多地图切换器（仅影响本次浏览）。权限由配置决定，不在本设计。

### 4. 工作产品传递
导航工作区中过程节点间定义工作产品输入输出关系（载体=输出方所绑工作区中的具体工作产品，引用不复制）；传递触发前提 = 既有绑定关系（消费原模型）；自上而下传递为下层工作区接口（来源「过程模型传递」、下层建模树展示、可跳源工作区、下层不可编辑）；参与下层可疑检查（复用 REQ-1277）；版本规则复用过程接口机制。

### 5. 工作产品跨区可见性（谁产出归谁）
导航工作区中定义的工作产品，若被定义为某过程的输出，则在该过程所关联（绑定指向）的工作区内查询工作产品时必须可见/可查到——查询链路按「产出归属」扩展检索范围，不复制数据。

## Design: Level 2 -- Components

### 后端组件（仅后端；前端由专门同事负责，本次仅定义契约与存储）

| # | 组件 | 分层 | 职责 | DDD 定位 |
|---|------|------|------|---------|
| B1 | `ProcessGroupLinkMarker` | sdkImpl `marking/markers/ucpg/` | link 标识 marker 算法（过程元素存在绑定→打标）；经 uc_pg `external.json.treeMarkers` 挂载；`ElementMarkType` 新增类型；前端经既有 `query_element_marks` 消费 | 领域策略（无状态，可单测） |
| B2 | `ProcessMapManagement` + `ProcessMapRest` | biz `processmap/` | 主地图解析（互斥标志→有权限 uc_pg 工作区回落→空态）、元素树投影 L0–L3、统计编排、布局保存/重置（`@Transactional`） | 应用服务 |
| B3 | `ProcessMapStatsRepository` | sdkImpl `repository/` | 绑定工作区批量统计单 SQL（工作流/活动/工作产品/关联总数；活动数>0=已建模，=0=空壳） | 领域服务实现（批量查询） |
| B4 | `IsMainMap` 工作区属性 + 互斥钩子 | sdkImpl `dataobjects/` + Flyway | `ModelingWorkspacesDataObject` 新属性；preAdd 默认 NO；postAdd/postUpdate=YES 时取消同产品空间其他工作区（照 `ModelNavigationDataObject.IsDefault` 范式） | 聚合内不变量 |
| B5 | `view_schema` 过程属性 | sdkImpl + Flyway + uc_pg process.json | `Processes`(21040101) 新属性：attribute_type=10 RichText（DB text 长文本）；过程地图画板布局/视图 schema；重置=清空；经既有属性编辑通道读写，零新 REST | 值数据 |
| B6 | `view_schema` 过程元素属性 | sdkImpl + Flyway + uc_pg process.json | `ProcessActivities`(21060101) 新属性：同上类型；承载顶级文件夹（过程/过程元素，gateflow 无独立文件夹概念）自由绘制画布 schema | 值数据 |
| B7 | 传递物化 `ProcessWorkProductTransferService` | sdkImpl `service/` | 传递核心：读导航工作区过程元素 output 关联 + 既有绑定 → 物化/同步下层工作区 INTERFACE 关联行；**不新增来源属性**（复用 association_type + ORIGINAL_WORKSPACE_IDS 派生判定）；仅自上而下；下层侧豁免标志 | 领域服务实现 |
| B8 | 传递树投影（`ProcessElementTreeSupport` 扩展） | biz | 下层元素树查询入口追加传递工作产品引用节点（来源标识、可跳转导航工作区、不可编辑；手工接口不出现）；挂查询入口，不动 `buildFinalTree` 复用方 | 应用服务（读模型装配） |
| B9 | `WorkProductQueryScope` | sdkImpl | 「谁产出归谁」：工作产品查询/选择器合并「被定义为绑定过程 output」的导航工作区工作产品（批量预解析绑定，禁 N+1；不复制数据） | 领域服务实现 |

### 不新建组件裁定

- 过程间 output 关联：复用既有 `Activity.output→Artifact` 元模型关联与关联 Widget（uc_pg process.json 已定义）
- processGroup/process 存储：复用 `ProcessActivities`(21060101) 子类 + subType 区分（`ProcessElementSubclassResolver` 回退即命中），零迁移
- 传递来源标记：**不加新属性/列表**；「过程模型传递」= `category=INTERFACE ∧ ORIGINAL_WORKSPACE_IDS 指向导航工作区` 派生判定（与模块导入接口来源标记同构）

### Flyway（v3.7）

- `view_schema` ×2：`air_sys_node`(parent 30001) 属性节点 + `air_sys_property`(parent=21040101/21060101, attribute_type=10)；metamodel ident `view_schema` ↔ ElementAttr API ID `ViewSchema`（前端 ident 映射注册 `PROPERTY_MAPPINGS`，同 flowPosition 先例）
- `IsMainMap`：同模式落工作区类

### 组件关系

```
[前端同事] ─ 消费 → query_element_marks(B1) / processmap REST(B2) / 元素树(B8) / 属性链路(B5,B6)
gf-biz:   ProcessMapManagement(B2) → B3 批量SQL / B5 布局
          ProcessElementTreeSupport(B8, 查询入口挂载)
          过程接口/元素 Management(既有) → B7 传递物化(同事务)
gf-sdkImpl: B1 marker(treeMarkers) / B4 IsMainMap互斥 / B7 TransferService / B9 QueryScope
Flyway v3.7: B4 / B5 / B6 属性种子
```

### 架构校验（custom architecture）

- Biz 不 import sdkimpl ✅（B2 经 SDK 契约调 B3；B7/B9 经 Service 接口）
- 事务仅 biz Management ✅（B2 布局保存、B7 传递同步入口）
- marker 纯内存无 IO；统计批量 SQL 在 sdkImpl ✅
- 无跨组件 Maven 依赖、无新增模块 ✅

## Design: Level 3 -- Interactions

> 范围：仅后端。前端由专门同事负责；过程绑定关系消费原模型既有实现，不重新设计；不新增未经需求明确要求的 `/process_map/{workspaceId}/board` 聚合接口。

### 1. 过程地图工作区列表

- 复用现有工作区树查询能力。
- 按当前用户可见范围、过程组模型元模型、顶层工作区（ParentID 为根节点）筛选。
- 返回顶层过程组模型工作区集合，前端以列表或缩略图展示。
- 后端不维护主地图、不保存默认选中工作区、不处理列表/缩略图切换。

### 2. 过程地图树数据来源

- 复用 `query_element_tree` 查询过程组/过程元素树。
- 读取 ParentID 层级，保留过程组递归层级，过程为叶子。
- 前端按树深度映射 L0–L3，渐进展开/收起。
- 不建立独立 ProcessMap 聚合、版本或数据副本。

### 3. 过程地图节点 marker

- 复用 `query_element_marks` → `IElementMarkService.resolveMarks` → `ElementMarkServiceImpl`。
- uc_pg 通过 `external.json.treeMarkers` 挂载 `ProcessGroupLinkMarker`。
- marker 读取原模型过程元素绑定关系：process subType 且存在绑定到目标工作区顶级过程/过程元素关系时添加 link 标记；无绑定不添加。
- 不修改绑定模型，不新增独立 link 查询接口；绑定合法性不由 marker 负责。

### 4. 过程地图节点统计

- 地图查询时批量收集过程节点绑定工作区 ID。
- `ProcessMapStatsRepository` 以批量 SQL 统计工作流、活动、工作产品、关联总数。
- `activityCount > 0` 为已建模，`activityCount = 0` 为空壳。
- 统计实时查询，不写入过程节点；禁止循环逐工作区查询。
- 具体是扩展既有响应还是新增最小统计契约，留待 Level 4 确定。

### 5. 地图布局读写

- 过程 `Processes`（21040101）的 `view_schema` 存过程地图布局/视图 schema。
- 过程地图读取既有过程属性；无值时返回空，由前端生成初始布局。
- 布局保存通过既有过程属性更新链路整体写入；重置为清空。
- `view_schema` 为 RichText（attribute_type=10），数据库物理类型为 text。
- 不新增独立布局表，不解析 schema 内部结构。

### 6. 顶级过程元素自由绘制画布

- GateFlow 无独立 Folder 领域对象；原模型顶级文件夹按过程/过程元素处理。
- 过程元素 `ProcessActivities`（21060101）的 `view_schema` 存顶级过程元素自由绘制画布 schema。
- 通过既有元素属性读写链路存取；浏览/编辑态和绘制交互由前端负责。

### 7. 顶层工作区展示

- 删除 `IsMainMap` 属性、主地图互斥钩子及对应迁移。
- 顶层过程组模型工作区统一作为列表项，前端选择列表或缩略图展示。
- 后端不处理主地图唯一性、主地图替换、默认选中和浏览记录。

### 8. 工作产品传递

- 导航工作区过程元素间复用既有 output 关联。
- 传递服务读取 output 关联和原模型绑定关系，在下层工作区物化/同步 INTERFACE 关联行。
- `association_category=INTERFACE`；`association_type` 保持源关系类型（如 input/output），不得挪作来源标识。
- 不新增 TransferSource 属性或新关联分类；来源识别复用 `ORIGINAL_WORKSPACE_IDS`，需在 Level 4 明确与过程模块来源的区分规则。
- 传递关系正反向成对维护；版本规则复用 wv↔wv、vv↔vv；仅自上而下。

### 9. 传递工作产品树投影

- 下层 `query_element_tree` 查询后，批量读取 INTERFACE 关联和源工作产品。
- 仅将命中过程模型传递来源的工作产品作为引用节点投影到下层树。
- 引用节点携带来源工作区/过程、跳转信息和不可编辑语义；手工过程接口不展示为树节点。
- 不改变源元素或下层本地工作产品数据。

### 10. 可疑检查联动

- 传递工作产品进入下层工作区可疑工作产品判定范围。
- 复用 REQ-1277 的无产出活动 ∧ 未外部输入规则。
- 外部输入豁免在下层侧维护，不修改源工作产品。
- 传递引用节点如何适配现有 marker 判定接口，留待 Level 4 明确。

### 11. 「谁产出归谁」查询

- 查询目标工作区工作产品时，批量解析指向该工作区的过程及其导航工作区。
- 合并导航工作区中被这些过程定义为 output 的工作产品。
- 按公共 ID 去重，返回来源工作区/来源过程及当前工作区编辑语义。
- 不复制工作产品、不修改物理归属、不把查询结果直接写成本地元素。
- 覆盖的既有查询入口（属性选择器、关联 Widget、列表、内置数据源、过程接口浏览）在 Level 4 明确。

### 12. Level 3 约束校验

- 不重新设计绑定关系、权限规则或前端组件。
- 不新增 `/process_map/{workspaceId}/board` 聚合接口。
- 地图是过程元素树投影；`view_schema` 分别落在过程和过程元素。
- 统计、传递、查询均需批量处理，禁止 N+1。
- 传递引用节点适配 marker、`ORIGINAL_WORKSPACE_IDS` 来源区分、查询入口覆盖范围列为 Level 4 契约问题。

### 13. Level 3 评审结论

2026-08-28：Level 3 交互设计获批准，进入 Level 4 契约定义。

## Design: Level 4 -- Contracts

### 1. 过程地图工作区查询契约

不新增 `/api/gate_flow/process_map/{workspaceId}/board` 聚合接口。顶层过程组模型工作区复用现有工作区树查询能力，按当前用户可见范围、过程组模型元模型、ParentID 根节点筛选。列表/缩略图是前端展示形式，不是后端两套契约。

```java
public interface IProcessMapWorkspaceQueryService {
    List<ProcessMapWorkspaceDto> listTopLevelProcessGroupWorkspaces(ProcessMapWorkspaceQuery query);
}

public record ProcessMapWorkspaceQuery(Long productSpaceId, Long workingsetId) {}

public record ProcessMapWorkspaceDto(
        Long workspaceId, String workspaceName, Long parentId,
        Long processId, String metamodelIdent) {}
```

### 2. 过程地图节点与统计契约

```java
public record ProcessMapNodeDto(
        Long elementId, Long commonId, Long processId, Long parentId,
        String elementName, String elementType, String elementSubType,
        Integer depth, ProcessMapNodeKind nodeKind,
        Long boundWorkspaceId, String boundWorkspaceName,
        ProcessMapStatsDto statistics, ProcessMapBuildStatus buildStatus,
        List<ElementMarkDto> marks, boolean hasChildren) {}

public enum ProcessMapNodeKind { PROCESS_GROUP, PROCESS }

public enum ProcessMapBuildStatus { BUILT, EMPTY_SHELL }

public record ProcessMapStatsDto(
        int workflowCount, int activityCount,
        int workProductCount, int associationCount) {}

public interface IProcessMapStatsService {
    Map<Long, ProcessMapStatsDto> batchQueryStats(
            Collection<Long> workspaceIds, ProcessMapStatsQueryContext context);
}

public record ProcessMapStatsQueryContext(Long workingsetId, String version) {}
```

- `PROCESS_GROUP` 可有子节点，`PROCESS` 必须为叶子。
- `depth` 用于前端 L0–L3 视觉映射，不限制实际深度。
- `buildStatus` 由 activityCount 派生。
- 统计实时查询，不写回过程节点；实现必须批量查询，禁止逐工作区 N+1。

### 3. `view_schema` 属性契约

过程 `Processes`（21040101）和过程元素 `ProcessActivities`（21060101）各新增同名属性：

```text
view_schema
```

属性类型为 `RichText`（attribute_type=10），数据库物理类型为 `text`。过程属性存过程地图布局/视图 schema；过程元素属性存顶级过程元素自由绘制画布 schema。

```java
public interface IViewSchemaService {
    String getViewSchema(IInnerDataObject target);
    void updateViewSchema(IInnerDataObject target, String viewSchema);
    void clearViewSchema(IInnerDataObject target);
}
```

后端不解析 schema、不生成初始布局、不新增布局表；读写复用既有属性更新链路。

### 4. link marker 契约

复用既有 `IMetaTreeMarker`、`IElementMarkService.resolveMarks` 和 `ElementMarkDto` 契约。

```java
public final class ProcessGroupLinkMarker implements IMetaTreeMarker {}
```

新增 `ElementMarkType.PROCESS_GROUP_LINK`。uc_pg 通过 `external.json.treeMarkers` 挂载。marker 只读取原模型绑定关系，process subType 存在绑定到目标工作区顶级过程/过程元素关系时添加标记；不修改绑定模型、不新增 link 查询接口、不负责绑定合法性校验。

### 5. 工作产品传递契约

导航工作区过程元素间复用既有 output/input 关联：

```java
public interface IProcessWorkProductTransferService {
    void synchronize(ProcessWorkProductTransferContext context);
    void synchronizeForAssociation(
            ProcessWorkProductAssociation association,
            ProcessWorkProductTransferContext context);
    void removeTransferredAssociations(ProcessWorkProductTransferContext context);
}

public record ProcessWorkProductTransferContext(
        Long navigationWorkspaceId, Long navigationProcessId,
        Long sourceWorkingsetId, String sourceVersion) {}

public record ProcessWorkProductAssociation(
        Long sourceProcessElementId, Long workProductElementId,
        String associationType, Long sourceProcessId,
        Long sourceWorkspaceId) {}
```

传递接口行固定保持：

```text
association_category = INTERFACE
association_type     = 源关系类型（input/output）
```

不新增 `TransferSource` 属性、不新增关联分类；`association_type` 不得挪作来源标记，因为它参与业务键、去重和版本对比。来源复用 `ORIGINAL_WORKSPACE_IDS`，但必须在实现前明确区分过程模型传递与过程模块导入，不能无条件假设字段现有语义足够。

### 6. 传递工作产品投影契约

```java
public interface ITransferredWorkProductProjection {
    List<TransferredWorkProductDto> queryForWorkspace(
            TransferredWorkProductQuery query);
}

public record TransferredWorkProductQuery(
        Long targetWorkspaceId, Long targetProcessId,
        Long workingsetId, String version) {}

public record TransferredWorkProductDto(
        Long sourceElementId, Long sourceCommonId,
        Long sourceWorkspaceId, Long sourceProcessId,
        String sourceWorkspaceName, String sourceProcessName,
        String workProductName, String associationType,
        boolean editable) {}
```

投影条件为 `category=INTERFACE` 且命中过程模型传递来源。投影节点不可编辑、不创建本地过程元素、不改变源工作产品。

### 7. 传递工作产品可疑检查契约

```java
public interface ITransferredWorkProductSuspiciousResolver {
    Map<Long, ElementMarkDto> resolve(
            Collection<TransferredWorkProductDto> transferredProducts,
            WorkProductSuspiciousContext context);
}

public record WorkProductSuspiciousContext(
        Long targetWorkspaceId, Long targetProcessId,
        Long workingsetId, String version) {}
```

传递工作产品进入下层可疑判定范围；判定复用 REQ-1277，无产出活动且未设置下层外部输入豁免时添加疑标记。传递引用节点如何适配现有 marker 输入类型，须在实现时提供引用节点适配器或等价的判定上下文，不复制 REQ-1277 规则。

### 8. 「谁产出归谁」查询契约

```java
public interface IWorkProductQueryScope {
    WorkProductQueryResult query(WorkProductQueryContext context);
}

public record WorkProductQueryContext(
        Long targetWorkspaceId, Long targetProcessId,
        Long workingsetId, String version,
        WorkProductQuerySource source) {}

public enum WorkProductQuerySource {
    PROPERTY_PICKER, ASSOCIATION_WIDGET,
    WORK_PRODUCT_LIST, BUILT_IN_DATA_SOURCE,
    PROCESS_INTERFACE_BROWSER
}

public record WorkProductQueryResult(
        List<WorkProductQueryItem> items, long total) {}

public record WorkProductQueryItem(
        Long elementId, Long commonId,
        Long sourceWorkspaceId, Long sourceProcessId,
        String name, WorkProductOwnership ownership,
        boolean editable) {}

public enum WorkProductOwnership {
    LOCAL, PRODUCED_BY_NAVIGATION_PROCESS
}
```

查询目标工作区时，批量解析指向该工作区的过程及所属导航工作区，合并导航工作区中由这些过程 output 的工作产品，按 commonId 去重。不复制、不修改物理归属；具体接入哪些查询入口由实现阶段按现有调用链落地。

### 9. Level 4 评审结论

2026-08-28：Level 4 契约设计获批准。设计范围为后端；前端不在本设计范围；绑定关系与权限不重新设计。

## Design Scope Revision -- 2026-08-28

### 用户裁定

- 非空白创建时的元模型覆盖属于导入/复制/加入过程模块既有处理，本设计不重新设计；原模型相关改动由其他同事负责。
- `shou fenng q` 明确为「手风琴」，属于前端概念；本设计忽略，不设计前端手风琴行为。
- 发布模型时的可疑工作产品检查属于发布检查逻辑；新增检查器由其他同事负责，本设计不展开。
- 传递接口与手工接口的树投影区分本期暂不设计；先按传递实现后的实际效果验证，不满足时另开设计。
- 原模型相关改动由其他同事负责；本设计仅保留本需求新增后端能力中已明确的部分。

### 对 Level 4 契约的收敛

以下内容从当前设计契约中移除，后续不得作为本 feature 的实现前置条件：

- 非空白创建元模型覆盖契约；
- 手风琴语义和前端交互契约；
- 发布检查器及传递工作产品可疑检查适配契约；
- 传递接口/手工接口树投影区分契约；
- `ORIGINAL_WORKSPACE_IDS` 用于树投影来源识别的实现要求。

当前仍保留的工作产品传递范围：

- 过程组模型导航工作区定义过程间 input/output 关系；
- 按既有绑定关系执行自上而下的工作产品传递；
- 目标侧接口关系按既有过程接口机制处理；
- `association_category=INTERFACE`；
- `association_type` 保持 input/output 等原关系类型；
- 不新增 `TransferSource` 属性或新关联分类；
- 发布检查、树展示区分和可疑检查由后续专项设计承接。

## Design Summary

- 后端范围：过程组模型元模型支撑、过程组/过程元素 link marker、过程地图所需顶层工作区列表与树投影支撑、过程/过程元素 `view_schema`（RichText/text）存储、工作产品传递主链路、跨区工作产品查询（谁产出归谁）。
- 既有能力直接消费：过程→工作区绑定关系、非空白创建元模型覆盖、原模型元模型改动、权限配置。
- 本期明确不设计：前端手风琴/画布交互、发布阶段可疑检查器、传递接口与手工接口的树投影区分、主地图/IsMainMap。
- 关键契约：过程和过程元素各新增 `view_schema` 属性；传递接口保持 `INTERFACE` 分类和原 `association_type`（input/output），不新增 TransferSource 或新关联分类；传递方向自上而下。
- 工作产品查询遵循「谁产出归谁」：导航工作区中由过程产出的工作产品，在该过程关联工作区查询时可见，不复制数据。
- 绑定关系、权限、前端交互、发布检查器、树展示区分如后续需要，分别单开专项设计。

## Final Decision

2026-08-28：设计范围按用户裁定收敛；Level 1–4 已完成并获批准。Status set to approved — ready for implementation of the in-scope backend work.

## Implementation Clarification -- 2026-08-28

- `view_schema` 必须作为默认配置属性增加物理列，不存放在 `attributes jsonb` 中。
- 过程表：`air_inst_gf_process.view_schema`。
- 过程元素表：`air_inst_gf_element.view_schema`。
- Flyway 本次只负责上述表结构字段及字段注释；`air_sys_node`、`air_sys_property`、元模型属性配置、列表项和国际化由其他同事负责。
- 后端需要增加一个更新行为，接收并更新过程或过程元素的 `view_schema` 字段；不解析字段内容。
- 属性名保持 `view_schema`，数据库字段、模型字段和更新行为使用同一语义。

## Implementation Progress -- 2026-08-28

- 已实现 `ProcessGroupLinkMarker`：`elementSubType=process && association_type=linked` 时经既有 `query_element_marks` 返回 `PROCESS_GROUP_LINK` 标记。
- 已实现 `UPDATE_VIEW_SCHEMA` WidgetOperation：过程上下文更新 `air_inst_gf_process.view_schema` 对应模型属性；元素上下文更新 `air_inst_gf_element.view_schema` 对应模型属性；参数 JSON 字段为 `view_schema`，内容原样存取，null 表示清空。
- 已新增 `V3.7.0.1__add_view_schema_columns.sql`，仅增加两个 text 物理列及注释。
- `ORIGINAL_WORKSPACE_IDS` 已从代码确认不可作为过程模型传递来源：原生关联、导入和过程模块同步均使用该字段，复用会触发过程模块来源收回/清理路径。
- 用户裁定过程模型传递复用关联表来源标记；实现落地为 `association_relation` 列（varchar）写入 `process-model-transfer`，`association_type` 保持 linked/input/output 等元模型关系语义。`relationship_type` 为 int8 列表字段（存列表项 ID），禁止写入字符串标记（PostgreSQL 严格类型，写入/比较会抛 bigint = character varying）。
- 传递物化/删除同步已实现：`ProcessWorkProductTransferService` 基于 `association_relation=process-model-transfer` 双向落库行做 reconcile（removeStale + ensure），避免对过程模块同步造成误删。
- 已实现 B9「谁产出归谁」查询范围服务：`IWorkProductQueryScopeService`（sdk 契约）提供 `resolveNavigationOutputWorkProductIds`（解析目标工作区下由导航过程产出、经过程模型传递引入的外部工作产品 ID）与 `mergeScopedWorkProductIds`（本地 + 传递合并去重）；sdkImpl `WorkProductQueryScopeServiceImpl` 基于 `association_relation=process-model-transfer` 落库行按承载侧（work_space=目标工作区）解析（取 related_instance_id），无状态只读；配套单测。
- link 标识补全跳转与展示信息（一对多）：`ElementMarkDto` 新增 `List<ElementMarkTargetDto> targets`（每项含 name/number/`WidgetUrlInfo`/targetWorkspaceName，形态对齐 `AssociationInfoDto`；过程节点可绑定多个工作区）；`TreeMarkContextFactory` 批量预解析全部 linked 关联行（目标元素名称/编号、跳转参数、对端工作区名，一次批量加载禁 N+1，按 工作区+元素 commonId 去重），`ProcessGroupLinkMarker` 无 IO 直接装配 `targets`；配套 `TreeMarkContextFactoryTest` 5 例（含多工作区）+ marker 富化 2 例。
- 国际化标注补齐：`ElementMarkType.PROCESS_GROUP_LINK` 补 `I18n.sys.tips.PLACEHOLDER_PROCESS_GROUP_LINK_TIP` 占位资源码（原为 null）；`UpdateViewSchemaDataModificationHandler` 三条失败消息改为资源码 + 中文 fallback + `IAirI18n` 解析（对齐 `ElementMarkType` 约定），配套 i18n 解析单测 2 例。
- view_schema 图片防误删（用户确认方案）：`view_schema` 走 `air_inst_rich_text` 富文本链路（属性列存富文本 id），画布 JSON 为新富文本格式、结构由前端定。`UpdateViewSchemaOperationParam` 新增 `object_keys`（`@JsonProperty`），前端每次保存全量传画布引用的图片 objectKey，handler `setValues` 后同事务 `IGstoreUploadRecordService.clear(objectKeys)` 认领（幂等），防止 `NoUsedFileCleanExecutor` 定时清理物理删图（框架只自动认领 type=image/imageHotpots/attachment 节点，自定义类型引用不认领）。清空语义修订为**空串**（null 会被 `collectAndPersistRichText` 跳过）；与前端约定画布 JSON 节点 type 避开三个保留值。删除/替换侧不做图片物理回收，留富文本类型重新设计。配套单测 2 例（认领/跳过）。
- view_schema 读路径补齐（结合元模型定义核查后）：现有富文本属性 description 在 widget 体系走 BUILD_IN 数据源（非 PROPERTIES），镜像新增 `BuildInDataType.VIEW_SCHEMA` + `ViewSchemaHandler`（元素/过程双上下文，经 `getInnerRichTextDisplayValue` 解析富文本 id → 原始内容）+ `ViewSchemaData` DTO；PROPERTIES 数据源另注册 `view_schema` 解析器兜底。`supportsMetaModelAddOperation` 排除 VIEW_SCHEMA（单条展示/编辑型）。元模型 widgetLibrary 挂载由元模型同事按契约配置。
- ElementMarkDto 扩展性重构（用户裁定原 targets 平铺字段粗暴）：改为「通用骨架 + 类型化载荷」——骨架固定 type（判别字段）/tipCode/tip + `data: ElementMarkData`（抽象载荷基类）；按 type 区分 data 结构（type 即判别符，不嵌 kind）。首批载荷 `LinkTargetMarkData`（targets 列表，process-group-link 专用，SuperBuilder 体系）。后续新标记 = 新载荷子类 + oneOf 注册，骨架/枚举/收集器零改动。否决 Map<String,Object>（codegen 退化 any）与泛型 DTO（返回链类型擦除）。
- 过程地图工作区列表过滤与 view_schema / graph 支持（用户裁定复用 processType 参数）：`ProcessTypeEnum` 新增 `PROCESS_MAP("ProcessMap", "过程地图", 4L)`；`ModelingWorkspacesTreeReq` 移除独立 viewSchema 字段，`processType` 支持 `PROCESS_MAP`；`ModelingWorkspacesTreeResp` 新增 `viewSchema`（布局）与 `graph`（`ProcessGraphResp` 结构，由 `IProcessGraphService.loadGraph` 加载）；`ModelingWorkspacesManagement.treeList` 在 `processType=PROCESS_MAP` 时只保留顶层过程工作区（清空子节点），并批量填充顶层工作区工作版本过程的 `view_schema`（经 `getInnerRichTextDisplayValue`，数字回退不下发）与 `graph`；非顶层或其它过程类型不填充；配套单测 2 例。

## Implementation Key Files

| Path | Role |
|------|------|
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/constants/GateflowConstants.java` | process/link/view_schema 常量 |
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/enums/ElementMarkType.java` | PROCESS_GROUP_LINK 标记类型 |
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/enums/WidgetOperationType.java` | UPDATE_VIEW_SCHEMA 操作类型 |
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/markers/ucpg/ProcessGroupLinkMarker.java` | linked 绑定 marker |
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/operation/handler/UpdateViewSchemaDataModificationHandler.java` | view_schema 更新行为 |
| `upgrade/src/main/resources/flyway/migration/versionupdates.v3.7/V3.7.0.1__add_view_schema_columns.sql` | view_schema 物理列 |

## Design Increment 2026-09-14 -- 被引用过程输出要求标记

> 场景：过程组模型工作区 PG1 内，过程 P1 定义 output → Product1（过程组对 P1 的输出要求），且 P1 经既有 linked 绑定目标工作区顶级过程元素 P1'。本增量在 **P1'**（目标工作区树）上打新标记，载荷为 Product1 信息（名称/编号/访问连接），提醒 P1' 建模者覆盖该要求。引用方可以是过程或过程组（两者均为 Activity，均可有 output 关联）。

### Level 1 -- Capabilities（已获批）

1. **被引用提醒标记**：目标工作区元素树中，被绑定的顶级过程元素（P1'）返回新 marker，载荷 = 全部引用方的 output 工作产品清单（名称、编号、访问连接——跳转至过程组模型中的该工作产品）
2. **目的**：提醒目标工作区建模者使被引用过程的建模覆盖过程组定义的输出要求
3. **引用方范围与合并**：过程/过程组均可为引用方；同一目标被多方绑定时，载荷为各引用方要求的去重并集；引用方无 output 关联则不打标
4. **只读投影**：不新增存储、不改 REST 契约、不新增接口；打标仍走 `query_element_marks` 唯一入口；无发现权限的工作产品不进载荷

### Level 2 -- Components（已获批）

| # | 组件 | 分层 | 职责 |
|---|------|------|------|
| C1 | `ElementMarkType.PROCESS_GROUP_OUTPUT_REQUIREMENT` | gf-sdk | 新标记类型 + i18n 占位资源码 + 中文 fallback |
| C2 | `OutputRequirementMarkData extends ElementMarkData` | gf-sdk dto | 新载荷子类（targets: List\<ElementMarkTargetDto\>），oneOf 注册 |
| C3 | `TreeMarkContext.elementLinkedOutputTargets` | sdkImpl `marking/` | 目标元素 id → 要求工作产品目标列表 |
| C4 | `TreeMarkContextFactory` 反查预解析 | sdkImpl `marking/` | 本工作区关联行筛 `linked ∧ target_role=caller`（被引用侧行，角色列判别，direction 不作依据）→ 批量载引用方元素 → 按引用方过程批量载 output 行 → 批量载工作产品（发现权过滤 + 工作区+commonId 去重 + `WidgetUrlInfoBuilder` 跳转参数）；无反向行零额外查询 |
| C5 | `ProcessGroupOutputRequirementMarker` | sdkImpl `marking/markers/ucpg/` | 无 IO 消费 C3，装配 `ElementMarkDto{data=OutputRequirementMarkData}`；照 `ProcessGroupLinkMarker` 范式 |
| C6 | `external.json` 挂载 | resources `mm/` | uc / uc_pg 两处 treeMarkers 追加 markerClass（uc_v1_1 已随本增量删除，见决策日志）。**2026-09-15 修订：收敛为仅 uc**（绑定目标仅 uc 工作区，uc_pg 挂载移除，见 Design Revision 2026-09-15） |

### Level 3 -- Interactions（已获批）

主流程：`resolveMarks`（目标工作区树）→ `TreeMarkContextFactory.build`：

1. 载本工作区全量关联行（既有逻辑，不变）
2. 筛 `linked ∧ target_role=caller ∧ instanceId∈本次元素集`（被引用侧行判别以角色列为准，direction 不作依据）；空 → `elementLinkedOutputTargets` 置空，零额外 IO
3. `getElements(引用方元素ids)`（跨工作区批量，既有能力）
4. 引用方按 processId 去重 → 逐引用方过程 `getProcessAllElementAssociations(pid)` 提取其 output 行（每引用方工作区一次查询，规模=引用方工作区数，与树元素数无关）
5. `getElements(工作产品ids)` → `hasDiscoveryPrivilege` 过滤 → `getProcesses` → `WidgetUrlInfoBuilder.fromElement` 跳转参数
6. 按 工作区+commonId 去重 → `elementLinkedOutputTargets[P1'.id] = targets`

marker：`ProcessGroupOutputRequirementMarker.mark(P1', context, collector)`——targets 空不打标；非空装配 `ElementMarkDto{type, tipCode/tip, data=OutputRequirementMarkData{targets}}`。

边界行为：

- 引用方元素已删除（脏行）→ `getElements` 查无 → 跳过该行
- 引用方无 output → 贡献为空；全部为空 → 不打标
- 多引用方指向同一工作产品 → 去重为单条 target
- 工作产品无发现权限 → 过滤；全被过滤 → 不打标
- `privilegeEngine` 不可用（单测）→ null 检查跳过过滤（照 `buildElementLinkTargets` 先例）
- 引用方版本/工作集：以引用方元素自身 `processId` 加载其工作区关联行

校验：IO 全在 sdkImpl 工厂（批量），marker 纯内存无 IO；biz 零改动；无跨 Maven 依赖；无聚合变更、无领域事件（查询期只读派生）；marker 无状态领域策略可单测。

### Level 4 -- Contracts（已获批）

1. `ElementMarkType` 新枚举：`PROCESS_GROUP_OUTPUT_REQUIREMENT("process-group-output-requirement", "I18n.sys.tips.PLACEHOLDER_PROCESS_GROUP_OUTPUT_REQUIREMENT_TIP", "过程组输出要求：请在此过程建模中覆盖下列工作产品")`
2. 载荷子类（gf-sdk 新文件）：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = false) class OutputRequirementMarkData extends ElementMarkData { List<ElementMarkTargetDto> targets; }`（无独立注册表，判别靠 type）
3. `TreeMarkContext` 新字段：`Map<Long, List<ElementMarkTargetDto>> elementLinkedOutputTargets = Collections.emptyMap()`（无反向绑定时为空 map；已去重 + 发现权过滤）
4. `TreeMarkContextFactory` 新私有方法：`buildLinkedOutputRequirementTargets(allAssociations, treeElementIds)`；`build()` 内非空才装配。被引用行判别取角色列 `target_role=caller`（`GateflowConstants.LINKED_SOURCE_ROLE/LINKED_TARGET_ROLE`；以 2026-09-14 实测修正为准，direction 为写入期约定不作判别）；关联类型复用 `LINKED_ASSOCIATION_TYPE`/`OUTPUT_ASSOCIATION_TYPE`，零新常量
5. `ProcessGroupOutputRequirementMarker implements IMetaTreeMarker`：`getMarkerKey()=type`；`mark` 无 IO——targets 空 return，非空 `collector.add(id, MARK.toDto() + OutputRequirementMarkData{targets})`
6. 挂载：uc / uc_pg 两处 external.json treeMarkers 追加 markerClass（uc_v1_1 试验元模型已删除）
7. 边界：`ElementMarkDto`/`ElementMarkTargetDto`/`IMetaTreeMarker`/`TreeMarkCollector`/REST 契约/前端消费方式全部不动；预解析失败返回空 map、marker 异常由 `resolveMarks` 既有 try/catch 兜底，不中断树查询

### Design Summary（增量 2026-09-14）

- 组件与分层：gf-sdk = `ElementMarkType` 新枚举 + `OutputRequirementMarkData` 载荷子类；gf-sdkImpl = `TreeMarkContext.elementLinkedOutputTargets` + `TreeMarkContextFactory` 反查预解析（批量 IO 集中于此）+ `ProcessGroupOutputRequirementMarker`（无 IO 领域策略）；resources = uc / uc_pg 两元模型 external.json 挂载
- 关键契约：type 码 `process-group-output-requirement`；复用既有 DTO/REST 契约零改动；被引用行判别 `target_role=caller`（角色列稳定语义，direction 为写入期约定不作判别）
- 架构约束：无反向行零额外查询；引用方过程按去重后逐个工作区一次关联查询（规模=引用方工作区数，与树元素数无关，非 N+1）；失败不中断树查询
- 领域决策：查询期只读派生（无聚合变更、无领域事件）；引用方范围=过程+过程组；多引用方要求取 工作区+commonId 去重并集；载荷访问连接指向过程组模型中的工作产品
- 漂移检查：与本需求既有约束无冲突（树打标唯一入口/只读投影/不复制数据全部吻合）；增量能力为需求外用户追加，REQ 文档**不补记**（用户裁定 2026-09-14），增量范围以本节与决策日志为准

### 增量 Key Files

| Path | Role |
|------|------|
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/TreeMarkContextFactory.java` | 反向输出要求预解析（改造） |
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/markers/ucpg/ProcessGroupOutputRequirementMarker.java` | 新 marker |
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/dto/OutputRequirementMarkData.java` | 新载荷子类 |
| `components/gf/sdkImpl/src/main/resources/mm/uc/external.json` | 挂载面（uc_pg 同） |
| `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/TreeMarkContextFactoryTest.java` | 反向预解析 6 场景单测 |
| `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/markers/ucpg/ProcessGroupOutputRequirementMarkerTest.java` | marker 3 场景单测 |

## Implementation Progress -- 2026-09-14（增量）

- C1 已实现：`ElementMarkType.PROCESS_GROUP_OUTPUT_REQUIREMENT`（占位资源码 + 中文 fallback，与 `PROCESS_GROUP_LINK` 同范式）。
- C2 已实现：`OutputRequirementMarkData` 载荷子类（`@SuperBuilder` 体系，oneOf 判别靠 type）。
- C3/C4 已实现：`TreeMarkContext.elementLinkedOutputTargets` + `TreeMarkContextFactory.buildLinkedOutputRequirementTargets`——`collectInboundLinkedRows` 按 `target_role=caller` 判被引用行（缺角色历史行保守跳过）、范围限定本次树元素集；`loadReferencingOutputRows` 按引用方过程缓存（同一过程仅一次查询）；`resolveWorkProductTargets` 全局批量解析 + 发现权过滤；`ResolvedWorkProductTarget` record 携带去重键（工作区+commonId，回退 id，与 link 目标对齐）。
- C5 已实现：`ProcessGroupOutputRequirementMarker`（无 IO，装配 `OutputRequirementMarkData{targets}`）。
- C6 已实现：uc / uc_pg 两处 external.json treeMarkers 挂载（uc_v1_1 试验元模型已随本增量删除，见决策日志）。
- 测试：`TreeMarkContextFactoryTest` 新增 6 例（被引用行解析 / 非反向零查询 / 引用方脏行 / 引用方无 output / 跨引用方去重 / 权限过滤）+ `ProcessGroupOutputRequirementMarkerTest` 3 例；`mvnd -pl components/gf/sdkImpl -o -Dtest=... test` 21/21 通过，Spotless 通过。
- 实现决策：被引用行判别常量取 `GateflowConstants.LINKED_SOURCE_ROLE/LINKED_TARGET_ROLE`（`target_role=caller`，见下条实测修正）；引用方→工作产品两段 `getElements` 批量，无逐元素查询；前置格式化由项目 Spotless 完成。
- 实测修正（2026-09-14）：首轮实机验证 marker 未命中。localzt 库核查（workspace 623809213544800 / 元素 623809242480992）证实 direction 写入约定与设计假设相反，判别字段修正为角色列（`GateflowConstants.LINKED_SOURCE_ROLE/LINKED_TARGET_ROLE` 新常量；`collectInboundLinkedRows` 按 `target_role=caller` 判被引用行）。测试 helper 同步改为角色构造（`inboundLinkedRow`/`outboundLinkedRow`），marking 包 58/58 通过。生效需重启加载新构建的服务。

## Design Revision -- 2026-09-15 -- linked 绑定目标限定为过程模型(uc)工作区

### 用户裁定

冒烟用例 42/46/48 的预期正确：linked 绑定（关联/引用的过程）只能挂**过程模型（uc）工作区**，不能挂过程组模型(uc_pg)/参考模型工作区。2026-09-14 增量的「挂载 uc / uc_pg 两处」设计有误，予以修正。

### 修订内容

1. **挂载面**：`ProcessGroupOutputRequirementMarker` 从 `mm/uc_pg/external.json` 摘除；保留 `mm/uc/external.json` 挂载（被引用侧只可能是 uc 工作区）。`ProcessGroupLinkMarker`（源侧 uc_pg 过程已绑定标识）不受影响。
2. **限定声明**：定位到元模型声明，而非代码常量——`targetMetamodelIdents`（`schema.Association` / `MetaAssociation` 新字段，`createMetaAssociation` 透传）。
   - uc / uc_pg 的 `Activity` linked 关联均声明 `["uc"]`；
   - 前端同源声明：widget config `targetMetamodelIdents: ["uc"]`（`processes-refines-list` / `processes-refined-list` / `activities-refines-list` / `activities-refines-list@uc_pg`）。
3. **候选面收口（读侧，全部读元模型声明）**：
   - 关联选择器（单工作区）`ProcessServiceImpl.queryElementAssociationPickerFlatNodes`：`resolveDeclaredTargetMetamodelIdents` 解析源元模型声明，目标工作区 ident 未命中 → 返回空；
   - 关联选择器（工作区集合批量）`queryElementAssociationPickerFlatNodesBatch`：同解析，候选工作区映射按声明剪枝；
   - 快速搜索 `WorkspaceSearchServiceImpl.search`：注入 `MetaProcessService`，经 `MetaProcess.collectTargetMetamodelIdents(pathExp)` 取声明并集（搜索侧不知源元素类型，故取并集），候选范围按声明收敛（含当前工作区自身）；
   - 搜索排除子类型修正：表达式显式要求 rootType（如 `Activity.rootType:linked$callee`）时不再排除根元素（原排除与子类型条件自相矛盾、恒为空）。
4. **写侧收口**：`AssociationServiceImpl.addAssociation` 按 `metaAssociation.targetMetamodelIdents` 校验目标元素所在工作区 ident，未命中抛 `PROCESS_ELEMENT_ASSOCIATION_NOT_ALLOWED`；未声明的关联不受限。uc 内部既有 linked（`Activity.workflow:linked$callee` 等，目标即 uc 工作区）满足声明，不受影响。
5. **工作区层面过滤**：`tree_list` 新增 `metamodelIdents`（ident 列表）参数，`ModelingWorkspacesManagement.filterByMetamodelIdents` 按 `resp.metamodelId`（元模型 ident）剪枝——未命中节点剪除、命中后代上移为根、非工作区空分组收掉；前端 `useGateflowWorkspacesTree(enabled, metamodelIdents)` 透传（参数入 queryKey，避免与全局调用串缓存），`AssociationBrowser` 经 `AssociationList` 的 widget config 声明取值，前端不再写死 ident。

### 影响面与兼容性

- E2E 基线（ProcessGroupModelE2ETest）：源=uc_pg、目标=`DEFAULT_METAMODEL_ID`(uc) 工作区，符合新限定，无需改动。
- 存量数据：历史上若存在指向 uc_pg 工作区的 linked 行，行展示与版本隔离不受影响；仅新增绑定被拒绝、uc_pg 树不再打输出要求标记（原挂载下也不可命中场景收敛）。
- 快速搜索按「工作区名称」匹配的语义（用例 42/43 预期）仍属待定项，本轮不实现——搜索关键字依旧按元素名称/描述匹配。

## Implementation Progress -- 2026-09-15（裁定修正 + 元模型声明化 + 工作区层面过滤）

- 元模型声明：`schema.Association` / `MetaAssociation` 新增 `targetMetamodelIdents`，`MetaProcessService.createMetaAssociation` 透传；uc / uc_pg 的 `Activity` linked 关联声明 `["uc"]`；四个 linked 卡片 widget config 同源声明 `targetMetamodelIdents: ["uc"]`。
- 后端收口（读声明，无写死）：`AssociationServiceImpl.assertTargetWorkspaceMetamodelAllowed`；`ProcessServiceImpl.resolveDeclaredTargetMetamodelIdents`（单区/集合批量）；`WorkspaceSearchServiceImpl.resolveDeclaredTargetMetamodelIdents` + `MetaProcess.collectTargetMetamodelIdents`。
- 工作区层面过滤：`ModelingWorkspacesTreeReq.metamodelIdents` + `ModelingWorkspacesManagement.filterByMetamodelIdents`（按 ident 剪枝、命中后代上移）；前端 hook 透传 + queryKey 隔离。
- 硬编码清理：删除 `GateflowConstants.LINKED_TARGET_METAMODEL_IDENT`（规则全部来自元模型声明）。
- 测试：`MetaProcessServiceTest` 新增 2 例元模型声明守卫（uc / uc_pg linked 必须声明 `["uc"]`，表达式并集收集）；`AssociationServiceImplTest` 写闸 3 例（声明内放行 / 声明外拒绝 / 未声明不限）；`ModelingWorkspacesManagementTest` 新增工作区层面过滤 1 例；`WorkspaceSearchServiceImplTest` 同步既有排除子类型期望（folder + rootType）并补 `MetaProcessService` 构造依赖。
- 生效需重启加载新构建的服务（元模型资源重新加载）。

## Naming Revision -- 2026-09-15（决策方维护，本次未改动）

- 树根节点展示名：`GateflowConstants.PROCESS_TREE_ROOT_NAME = "全景图"`（原「首页」；选择器类树根仍为「过程元素」）。
- `mm/uc_pg` 词条：`element.label.Activity = 过程组和过程`（原「工作流和活动」）、`element.label.Phase = 阶段与里程碑`（原「阶段」）；`mm/uc` 保持「工作流和活动 / 阶段」。
- 事实更正：uc_pg 元模型 `elements` 含 5 个顶层元素（Activity / Artifact / Role / Phase / Guidance），**每个顶层元素在运行时各生成一个 `subType=rootType` 的根元素**（`MetaProcessService.createElements`），因此 uc_pg 工作区树内确有 5 个分类文件夹（过程组和过程 / 工作产品 / 角色 / 阶段与里程碑 / 指南）——与冒烟用例 15 的预期一致；此前「uc_pg 仅一个根分类」的记录有误，已更正。

## Design Increment 2026-09-16 -- 被引用过程全量关联内容标记（载荷扩展）

> 背景：前端详情弹窗设计稿要求按 工作产品/角色/指南 分组展示并带「过程名称」。用户裁定（2026-09-16）：标记返回值扩展为**当前被引用过程的全部关联内容**，不限于弹窗所示三分类；分类与统计由前端完成，本增量为纯后端数据提供，**前端零改动**。

### Level 1 -- Capabilities（已获批 2026-09-16）

1. **全量内容下发**：载荷返回引用方对当前（被引用）过程定义的全部关联内容元素——按引用方实际存在的关联行采集，不做类型白名单；**仅排除 P1→P1' 绑定指向行本身**（`linked` 且对端=被标记元素，即「要求关系」载体），其余关联行全部下发（含指向其他元素的 `linked` 行与 `sequence`/`execute` 等流程结构行）。
2. **行字段**：元素名称、元素编号、类别（原始关联类型，分类归组由前端完成）、所属过程名 + 过程编号、所属工作区名称 + 工作区编号；既有 `urlInfo` 跳转参数保留。
3. **可见性与去重**：发现权过滤延续（无发现权整行不下发）；按 `工作区+commonId+类别` 去重保序。
4. **纯后端增量**：不改前端、不改标记展示形态与挂载面（仅 uc）。

### 裁定记录（2026-09-16）

- 范围：返回所有与当前过程有关联关系的内容，不局限于弹窗所示内容；分类与统计由前端完成（用户裁定原文）。
- 排除面（2026-09-16 修正）：仅排除「要求他的过程组」的绑定指向行（P1↔P1' 的 linked 对），其余关系全部展示（用户修正裁定；原「排除 linked/sequence/execute」否决）。
- 归属信息「两者都给」：过程名 + 工作区名称，并含编号信息（用户裁定；本域名称不唯一，编号为判别）。

### Level 2 -- Components（已获批 2026-09-16）

| # | 组件 | 分层 | 变更 | 职责 |
|---|------|------|------|------|
| R1 | `ElementRelationTargetDto`（新） | gf-sdk `dto` | 新增 | 关联内容行结构：`name`/`number`/`category`（原始关联类型）/`ownerProcessName`/`ownerProcessNumber`/`ownerWorkspaceName`/`ownerWorkspaceNumber`/`urlInfo` |
| R2 | `RelationContentMarkData`（原 `OutputRequirementMarkData` 更名换型） | gf-sdk `dto` | 改造 | 载荷 `targets: List<ElementRelationTargetDto>`，oneOf 判别靠 type |
| R3 | `TreeMarkContext.elementLinkedRelationTargets`（原 `elementLinkedOutputTargets` 更名） | sdkImpl `marking` | 改造 | 被引用元素 id → 全量关联内容行 |
| R4 | `TreeMarkContextFactory.buildLinkedRelationTargets`（原 `buildLinkedOutputRequirementTargets` 更名扩展） | sdkImpl `marking` | 改造 | 全量内容行预解析（IO 集中点）：反向行 → 引用方全量内容关联行 → 批量解析对端元素/归属 → 去重装配 |
| R5 | `ProcessGroupRelationContentMarker`（原 `ProcessGroupOutputRequirementMarker` 更名） | sdkImpl `markers/ucpg` | 更名，逻辑不变 | 无 IO 消费 R3 装配载荷 |
| R6 | `mm/uc/external.json` treeMarkers | resources | 仅改 markerClass 指向 | 挂载面不变（仅 uc） |

- 类型码更名：`process-group-output-requirement` → `process-group-relation-content`；枚举 `PROCESS_GROUP_OUTPUT_REQUIREMENT` → `PROCESS_GROUP_RELATION_CONTENT`；i18n 占位资源码同步更名。
- 组件挑战结论：不新增 Service/Repository（IO 仍集中工厂）；工作区名/编号解析抽工厂私有 helper 与 `buildElementLinkTargets` 共用 `sessionLoader` 范式；查询期只读派生，无聚合变更、无领域事件。

### Level 3 -- Interactions（已获批 2026-09-16）

主流程（`resolveMarks` → `TreeMarkContextFactory.build`）：

1. 载本工作区全量关联行（既有，不变）
2. 筛被引用行 `linked ∧ target_role=caller ∧ instanceId∈树元素集`（既有，不变）；空 → 空 map 零额外 IO
3. `getElements(引用方 ids)`（跨工作区批量，不变）
4. 引用方按 processId 去重 → 逐引用方过程 `getProcessAllElementAssociations(pid)`（每引用方工作区一次，不变）
5. 【扩展·修正】提取引用方**全量内容行**：仅排除绑定指向行——`type=linked ∧ relatedInstanceId=当前被标记元素`；其余行（含指向他元素的 `linked`、`sequence`/`execute`）全部采集；行三元组 = (引用方元素, 关联类型, 对端元素)
6. 【扩展】`getElements(全部对端 ids)` 统一批量（WP/Role/Guidance 同一 loader）→ `hasDiscoveryPrivilege` 过滤
7. 【扩展】归属批量解析：owner 过程名/编号（`getProcesses`，既有 `loadOwnerProcesses`）+ 工作区名/编号（新抽 `loadWorkspaces` helper，`getObjectsByInstanceIdsNP` 同 `buildElementLinkTargets` 范式）；无逐行查询
8. 【扩展】装配行：`category`=原始关联类型、归属名号、`urlInfo=WidgetUrlInfoBuilder.fromElement`；去重键 `工作区+commonId+类别`（commonId 缺省回退 id）保序 → `elementLinkedRelationTargets[P1'.id]`

marker：targets 空 → 不打标；非空 → `ElementMarkDto{type=process-group-relation-content, tip, data=RelationContentMarkData{targets}}`。

边界行为：

- 对端元素已删（脏行）→ 跳过；引用方无内容行 → 贡献空；全空 → 不打标
- 同一对端同类别被多引用方要求 → 去重单行（归属为对端自身，行数不随引用方数膨胀）
- 绑定指向行排除按 (引用方, 被标记元素) 成对生效：P1 指向其他元素的 `linked` 行不受影响，照常下发（category=linked）
- 无发现权 → 过滤；全滤空 → 不打标；`privilegeEngine` null（单测）→ 跳过过滤（既有先例）
- marker 无 IO；异常由 `resolveMarks` 既有 try/catch 兜底，不中断树查询
- IO 规模与树元素数无关：反向行为空零额外查询；新增 IO 仅对端元素/过程/工作区各一次批量

### Level 4 -- Contracts（已获批 2026-09-16）

1. `ElementMarkType` 更名换值：`PROCESS_GROUP_RELATION_CONTENT("process-group-relation-content", "I18n.sys.tips.PLACEHOLDER_PROCESS_GROUP_RELATION_CONTENT_TIP", "此过程存在关系待构建")`；删除原 `PROCESS_GROUP_OUTPUT_REQUIREMENT`；最终词条由 i18n 专人经 `PLACEHOLDER_*` 配置（既有惯例）
2. `ElementRelationTargetDto`（gf-sdk 新文件）：`@Data @Builder @NoArgsConstructor @AllArgsConstructor`，字段 `name`/`number`/`category`（原始关联类型）/`ownerProcessName`/`ownerProcessNumber`/`ownerWorkspaceName`/`ownerWorkspaceNumber`/`urlInfo`
3. `RelationContentMarkData`（原 `OutputRequirementMarkData` 更名换型，原类删除）：`@Data @SuperBuilder @EqualsAndHashCode(callSuper = false) extends ElementMarkData`，字段 `List<ElementRelationTargetDto> targets`
4. `TreeMarkContext` 字段更名换型：`Map<Long, List<ElementRelationTargetDto>> elementLinkedRelationTargets = Collections.emptyMap()`
5. `TreeMarkContextFactory`：方法更名 `buildLinkedRelationTargets`；绑定指向行排除内联于装配（`type=linked ∧ relatedInstanceId=被标记元素` 成对判别，复用 `GateflowConstants.LINKED_ASSOCIATION_TYPE`，零新常量）；新抽私有 helper `loadWorkspaces(Collection<Long>)`
6. marker 更名：`ProcessGroupRelationContentMarker`（原类删除），`getMarkerKey()=新 type`，`mark` 逻辑不变
7. `mm/uc/external.json`：treeMarkers `markerClass` 指向新 FQCN
8. 契约边界：`ElementMarkDto`/`ElementMarkTargetDto`（link 专用不动）/REST 骨架/前端消费方式全部不变；类型码更名无迁移成本（查询期派生、无持久化、前端对该 type 零消费）；测试同步更名并补场景（多类型采集 / 排除行 / 归属名号 / 三元去重 / 权限过滤）

### Design Summary（增量 2026-09-16）

- 组件与分层：gf-sdk = `PROCESS_GROUP_RELATION_CONTENT` 枚举 + `ElementRelationTargetDto` 行类 + `RelationContentMarkData` 载荷；gf-sdkImpl = `TreeMarkContext.elementLinkedRelationTargets` + `TreeMarkContextFactory.buildLinkedRelationTargets`（IO 集中点，绑定指向行成对排除）+ `ProcessGroupRelationContentMarker`（无 IO）；resources = `mm/uc/external.json` 挂载指向更新
- 关键契约：type 码 `process-group-relation-content`；行结构 8 字段（元素名号 + 类别 + 归属过程名号 + 归属工作区名号 + urlInfo）；去重键 `工作区+commonId+类别`
- 架构约束：仅排除绑定指向行（P1↔P1' 成对判别），其余关联行全量采集不做白名单；IO 全在工厂批量（与树元素数无关）；biz 零改动、无跨 Maven 依赖、查询期只读派生
- 领域决策：归属「两者都给」（过程与工作区名号均下发，编号为判别键）；分类归组归前端；发现权过滤与 null 安全边界沿既有先例
- 漂移检查：REQ-3.7 无本标记相关 AC/约束（增量能力为需求外用户追加），与本需求既有约束（树打标唯一入口 `query_element_marks`/只读投影/不复制数据/挂载仅 uc）无冲突；沿 2026-09-14 用户裁定「增量能力不补记 REQ 文档」，本轮**不写回** REQ
- 开放问题：无新增（设计期三项裁定均由用户当场落定）

### Design approved

2026-09-16 设计于 Level 4 获批，status 置 approved——可进入实现（`/code-forge`）。

## Implementation Progress -- 2026-09-16（全量关联内容标记增量）

- gf-sdk：`ElementMarkType.PROCESS_GROUP_RELATION_CONTENT`（type 码 `process-group-relation-content`，占位资源码 + fallback「此过程存在关系待构建」）；新增 `ElementRelationTargetDto`（8 字段，`@Builder(toBuilder = true)` 支撑装配期 category 回填）；`RelationContentMarkData` 替代 `OutputRequirementMarkData`（原类删除）。
- sdkImpl：`TreeMarkContext.elementLinkedRelationTargets`（更名换型）；`TreeMarkContextFactory.buildLinkedRelationTargets` —— `loadReferencingRelationRows` 去类型过滤采集引用方全部关联行（每引用方工作区一次查询），`resolveRelationTargets` 单批 `getElements` + 发现权过滤 + 归属过程/工作区名号批量解析，`assembleRelationTargets` 成对排除绑定指向行（`isBindingRow`：`linked ∧ 对端=被标记元素`）+ `工作区+commonId+类别` 三元去重保序；新增 `loadWorkspaces` helper（`buildElementLinkTargets` 同步改用）；`UNKNOWN_ASSOCIATION_TYPE` 常量收口原字面量。
- marker 更名 `ProcessGroupRelationContentMarker`（原类删除）；`mm/uc/external.json` markerClass 指向更新。
- 测试：`ProcessGroupRelationContentMarkerTest` 3 例（断言含类别/归属名号/跳转参数）；`TreeMarkContextFactoryTest` 关联段 9 例（解析+归属名号 / 零额外查询 / 引用方脏行 / 无关联行 / 跨引用方去重 / 权限过滤 / 多类别成行 / 绑定行排除他行保留 / 同对端异类别双行）；`MetaProcessExternalPluginTest` markerClass+markerKey 断言同步。
- 验证：`mvnd -pl components/gf/sdkImpl -am -o -Dtest=… test` 36/36 通过 + Spotless 通过；gf-sdk 已 install 本地仓库（无 `-am` 独立跑测试前需先 install，否则 testCompile 解析到过期构件）。

### 增量 Key Files

| Path | Role |
|------|------|
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/enums/ElementMarkType.java` | 枚举更名换值 |
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/dto/ElementRelationTargetDto.java` | 新行结构契约 |
| `components/gf/sdk/src/main/java/com/airedgesoft/gf/sdk/dto/RelationContentMarkData.java` | 新载荷（原 `OutputRequirementMarkData` 删除） |
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/TreeMarkContext.java` | 上下文字段更名换型 |
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/TreeMarkContextFactory.java` | 全量关联内容预解析（IO 集中点） |
| `components/gf/sdkImpl/src/main/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/markers/ucpg/ProcessGroupRelationContentMarker.java` | 更名后的 marker |
| `components/gf/sdkImpl/src/main/resources/mm/uc/external.json` | 挂载指向 |
| `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/TreeMarkContextFactoryTest.java` | 预解析 9 场景单测 |
| `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/marking/markers/ucpg/ProcessGroupRelationContentMarkerTest.java` | marker 3 场景单测 |
| `components/gf/sdkImpl/src/test/java/com/airedgesoft/gf/sdkimpl/service/gateflow/service/MetaProcessExternalPluginTest.java` | 挂载契约断言同步 |

## Bugfix 16140 -- 「过程组和过程」分类文件夹不应提供关联过程入口（2026-09-24）

- **现象**：uc_pg 工作区选中「过程组和过程」分类文件夹（Activity rootType → index 视图），详情页出现「关联的过程」卡片及「添加关联过程 / 批量添加」按钮。
- **根因**：`mm/uc_pg/process.json` 把 `processes-refines` 卡片错误挂在 Activity 的 **index 视图**（rootType 分类文件夹页面）。两张元模型中唯一反例——uc 及 uc_pg 其余全部 index/folder 视图均不挂 refine 卡片，uc 的 `activities-refines` 挂在工作流子类型节点视图上。该卡片 dataSource 过滤 `Activity.process:linked$caller`，而 linked 目标限定 uc 工作区（无 process 子类型），列表恒为空，属死配置 + 误出入口。
- **修复**：删除 index 视图中的 `processes-refines` 槽位（index 槽位 order 4→3）及 widgetLibrary 中唯一引用该卡片的死定义；`linked` 关系本身、过程节点「引用的过程」（`processes-refined`，单条添加）与 processGroup 视图（本就无卡片）全部不动。前端 `addMode: 'browse'` 为通用 config 驱动逻辑，卡片移除后自然失效，零前端改动。
- **保护**：`MetaProcessServiceTest` 新增三例规则守卫（修复前 RED）：`testActivityIndexView_NoProcessRefinesWidget`（根元素 index 视图无关联卡片）、`testProcessGroupSubTypeView_NoAssociationWidgets`（过程组视图无关联卡片）、`testProcessSubTypeView_KeepsRefinedWidget`（过程节点三视角保留 processes-refined）。
- **规则（用户确认 2026-09-24）**：过程组元模型中 过程元素→可关联过程；过程组元素→不能关联；过程和过程组（根元素）→不能关联。修复后状态与该规则一致，无能力被误删。
- **生效方式**：元模型资源随服务启动加载，重启服务后对新老 uc_pg 工作区同时生效；历史 folder 级 linked 行不受影响（仍在过程侧「引用的过程」可见）。
