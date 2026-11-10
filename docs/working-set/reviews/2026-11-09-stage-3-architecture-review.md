# 阶段 3：Shared Kernel 边界与候选架构正式评审

**状态：Draft；评审建议，等待用户裁定**

日期：2026-11-09。检查起点：`b8ff675a84df6444329495f165174f486828fada`，工作区干净。当前设计阶段为战略/战术候选比较与业务契约推演，非实施。用户本轮明确授权联合形成候选决策包，未授权接受模型、发布基线、提交或打 Tag。

## 结论及证据边界

AC-001 固定 Working Set 属于企业 Shared Kernel、四个消费 BC 及三项兄弟共享能力；排除“移出 Shared Kernel”方案，不限定内部 BC 数、聚合数、事务、统一业务对象或部署。没有新 Business Fact/Invariant 被确认。

推荐责任模式 C：SK 管身份定位、范围、冻结证据、决定、结果与确认；各 BC 授权读取、解释字段语义、整体校验并实际提交内容。A 保留为更简单的委托型方案；B 仅在 BC 显式委托有限通用修改、且提交内校验完整时可行，当前证据不足。比较详见 [边界](../domain/shared-kernel-boundary.md)。暂不唯一选择聚合模型；AP-001 是简单基准，规模及发布要求支持时再考虑 AP-003，Branch 真实独立生命周期支持时考虑 AP-002。

## 实际读取及未读取

- 根 AGENTS、README；知识库 README/BASELINE；domain 的 glossary、facts、invariants、evidence-register、open-questions、context-map、shared-kernel-boundary、aggregate-model；proposals 聚合三方案；scenarios 核心场景；现有 diagrams、reviews、ADR 模板及目录说明。部分批量输出被截断，关键边界、方案、问题、场景和图表已单独重读；不将截断部分宣称为逐字审阅。
- 关联 Codex 聊天最近 4 轮与 ChatGPT 聊天最近 10 轮已通过 read_thread 获取，历史输出部分截断；本次任务以附件原文和当前 Git 文档为准。未重新查看两张原始架构图片，AC-001 直接由本轮用户任务再次明确给出。
- pre-design 全部 Markdown 路径及标题/关键词定位；V4 overview、consistency、Reference CONTEXT 读取请求有截断；定向复读 V4 08 §5 接入合同、07 §6—7 可见性/删除，确认 Part 可不受管、提交回执与索引确认分离。其余历史文档仅定位，未全面复审。
- 方法：本地 `D:/coding/IdeaProjects/union/airedge/.agents/skills/` 下 domain-modeling、domain-driven-design、design-first 的 SKILL；DDD defaults 与 design methodology 必要片段。未读本地 DDD 书籍、未重新打开仓库外阶段 1 报告；EXT-ARCH 仅通过阶段 2 证据登记间接引用。
- FastCtx 工具本会话不可用；无 .codegraph 目录，未索引仓库。未读取生产实现或运行业务测试。

## 主要发现与反例

| 级别 | 发现/证据 | 影响与建议 |
| --- | --- | --- |
| P0 | 四 BC 具体对象能力需分别定义 | Part 已确认共享纳入查看，由 PLM 在默认工作集管理，不派生/比对/合并。GateFlow 已确认 Process/版次/工作集映射及草稿内容替换，对象范围已固定四类且不纳入共享 Tracker，DQA 已确认八类对象均以共享 Tracker 纳入查看 |
| P0 | COW/Compare 依赖完整固定历史，尚无能力验证 | 历史失败必须显式返回，不能当前值兜底；源删除前保留守卫与附件/关系回读需要实验 |
| P0 | B06 分别合法字段可组合非法 | 业务 BC 提交内整体校验；预览校验不授权通用引擎绕过聚合 |
| P0 | B09 的审阅值和执行值可能不同 | 已确认执行读取源端最新值。需区分审阅与实际应用证据，并定义执行中的源与目标并发处理 |
| P0 | B10 成功但回执丢失 | BC 内容与可查回执需可靠一致；SK 恢复查原操作，不能直接重写；Checkpoint 因果 CAS |
| P1 | AP-001 热点与 AP-003 多事实协议成本均缺规模证据 | 收集 P95/P99 Binding、并发、历史保留指标后选型 |
| P1 | 现有 CTX 名称易被误读为九个独立 BC | 现暂按内部模型区域分类，独立语言/治理证据齐备再拆 |
| P1 | BASELINE 的仓库尚无提交描述过时 | 修正文案，Released Commit 仍为 None；存在 Git HEAD 不意味着领域基线已发布 |

场景详情 [B01—B12](../scenarios/shared-kernel-boundary-scenarios.md)；原 [SCN-001—010](../scenarios/core-scenarios.md) 保留。B06 的 x≤y 是推演夹具，不是任何 BC 的业务规则。不能把这些文档推演算作四 BC 真实业务验证完成。

## Context Map、聚合与契约

[Context Map 正文](../domain/context-map.md) 建议 Composition/Lineage/Variant 暂为内部模块，Merge 为独立模型区域候选、History 为支撑端口、Provisioning 为应用流程，关系按类型确认所有者。企业归属仍见 [企业图](../diagrams/shared-kernel-context-map.md)。运行时回调端口不改变代码依赖方向：BC 实现公共协议，SK 核心不依赖 BC 实现。治理推荐公共语言版本化与能力协商，审批权威、兼容窗口未确定。

[聚合矩阵](../domain/aggregate-model.md) 逐项覆盖 16 类概念，包含身份、根/实体/值的候选归属、生命周期、事务、异步责任、热点与反例；[竞争提案](../proposals/aggregate-boundary-options.md) 保留三套及迁移代价。不用全族 Item Family 或全谱系 Lineage 大聚合来掩盖唯一性与无环并发问题。

create 区分工作范围与业务对象创建；extend 由 SK 发布新范围；resolve 区分 SK 定位与 BC 授权内容；diff 由 SK 固定证据、BC 给出语义差异；merge 由 SK 管决定，BC 校验并提交，SK 查回结果再确认。完整输入/结果、幂等、权限、历史、并发及恢复见 [候选契约](../domain/shared-kernel-boundary.md)。

## 图表与产物范围

复用企业外框和内部候选 Context Map，更新内部分类说明；新增 [三候选类图与边界图](../diagrams/aggregate-candidates.md) 和 [Merge/Shared/COW 行为图](../diagrams/collaboration-behavior.md)。连线不表达部署或跨 BC 事务；所有新增图均 Candidate。没有创建 ADR：模型所有权和源证据合同尚需裁定，先以本评审保存真实权衡，后续裁定再起草 Proposed ADR。

本轮新增：本报告、边界场景、两份图表。修改：shared-kernel-boundary、context-map、aggregate-model、invariants、open-questions、竞争提案、知识库/场景/图表/评审导航、BASELINE、两份现有 Context Map 图的候选协作说明。历史材料和生产代码未修改。

## 验证与后续

本轮执行 Markdown 本地链接、Mermaid fence/图类型/基本结构、状态、Git diff/check 与变更范围检查；结果由完成回复报告。基本结构检查不等于 Mermaid 渲染检查，纳入正式基线前仍须渲染与图文复核。未运行生产测试，未宣称历史合同或并发协议已经兑现。

BASELINE 保持 Draft / Unreleased，正式领域模型、Accepted ADR、验收标准仍为空。下一步先裁定下列 1—4，再请四 BC 专家补具体对象与真实拒绝用例，验证历史能力与单决定提交/回执查回 seam，最后收敛聚合和 Proposed ADR。没有新增或替代既有 Accepted ADR。

## 等待用户裁定的决策清单

各项的备选方案保留供评审。D1、D3、D4 与 D5 已有具体场景的用户裁定。见 [裁定进度](#裁定进度)。

| 决策/问题 | 推荐答案及依据/当前证据 | 真实可选答案 | 不同答案的影响 | 暂不决定阻塞 |
| --- | --- | --- | --- | --- |
| D1 / QUESTION-013 谁提交业务内容？ | C 按能力分工，BC 提交；B06/07、INV-009 候选与 V4 08 §5 支持 | A 全部语义比较也委托 BC；B 有限通用引擎并由 BC 提交内守卫 | C/A 保留 BC 内容上游与自身聚合；B 需声明委托字段及规则模型、收窄通用写契约并保证锁内合法性，实现策略维护成本增加 | 所有跨 BC 写合同 |
| D2 / QUESTION-013 哪些对象支持哪些能力？ | Part 已确认为共享 Tracker，由 PLM 在默认工作集管理，其他工作集纳入查看 | 历史非受管 Part 方案已由用户本轮确认替代 | Part 的工作集派生/比对/合并不适用，适配能力按类型声明 | 工作集协作契约及共享查看验收 |
| D3 / QUESTION-006 历史保留覆盖什么？ | 按操作所需的内容、成员、生命周期、附件、关系保留固定证据；B04/05、FACT-006 待验证 | 创建时物化完整副本；不提供懒 COW/相关比较 | 固定历史端口支撑 Variant 小根；物化转由 BC 创建流程承担、存储及准备延迟增加；不保留则删减 Source Cut 契约和能力 | COW/Compare 可行性 |
| D4 / QUESTION-008 源值变化后合入哪个值？ | 用户已确认执行时取源端最新值，见 B09 | 评审曾比较使用审阅值、阻断并重新审阅 | SK 保留字段选择及审阅证据；源端提供当前值；目标 BC 继续负责整体合法性与提交。比较与执行证据需分离 | 提交期间的并发控制、源字段删除的业务含义及恢复协议仍待定 |
| D5 / QUESTION-008 合并成功粒度与重试范围？ | 用户已确认允许部分成功，重试仅处理未完成项；B08 | 评审曾比较业务依赖组原子及整轮原子 | 批次需分项记录结果，已成功项即使源再次变化也跳过；结果/Checkpoint 收敛协议待验证 | 结果未知时的可靠判定及恢复验收 |
| D6 / QUESTION-001/002/012 聚合组织？ | 先 AP-001 简单基准，规模证明后 AP-003；有独立 Branch 生命周期才 AP-002；当前缺规模与业务裁定 | 直接 AP-003；直接 AP-002 | AP-001 配置根强一致；AP-003 发布指针+多根准备；AP-002 Branch 根主导；内部区域/发布合同/加载与迁移实现随之改变 | 最终类图与一致性 ADR |
| D7 / QUESTION-004 家族键作用域？ | 用户已确认 CommonId 全局区分逻辑家族，跨类型/Tracker 的无关 Item 不重号；CommonId/LocalId 均为雪花 ID | 早期复合家族键候选的重号前提已被排除 | 比对以 CommonId 对齐家族，类型可承担路由/校验职责；标识分配需兑现雪花 ID 唯一性 | 同族变体选择约束及标识分配验证 |
| D8 / QUESTION-014 版本治理？ | 公共语言共同审批、版本化兼容、BC 能力声明，允许兼容窗口内异步升级；AC-001 仅固定归属 | 四 BC 与共享能力共同发布 | 版本化保持模型隔离，协议/回执持久化需保留版本；共同发布增加团队依赖和发布耦合，并不自动改变聚合或允许跨 BC 事务 | 正式公共契约发布 |

D4 的源字段取值已按用户裁定更新。D6 若数据表明整体配置不大且可接受冲突，应保留简单方案而非预先选 AP-003。

## 裁定进度

- **D1 的四 BC 合并职责已确认，QUESTION-013 其余能力继续细化。** 用户明确将 [B06 的职责划分](../scenarios/shared-kernel-boundary-scenarios.md#b06alm-requirement-的合并拒绝) 统一应用于参与 Working Set 的 ALM、PLM、GateFlow、DQA 对象。各 BC 校验整个对象并决定提交，保留各自校验规则；Working Set 负责合并选择、执行协调与结果记录。对象接入范围及正式契约仍待评审。

- **D3 / QUESTION-006 部分确认。** 用户在本对话明确裁定 [B04 首次编辑的内容依据](../scenarios/shared-kernel-boundary-scenarios.md#b04-首次编辑的内容依据)。来源后续修改或彻底删除时，派生 Requirement 仍可基于派生时内容首次编辑。删除完成前须保证该内容仍可读取。保留方式、期限及附件与关系的覆盖范围待裁定。该裁定适用于首次编辑。

- **D4 / QUESTION-008 部分确认。** 用户明确选定 [B09 执行读取源最新值](../scenarios/shared-kernel-boundary-scenarios.md#b09-接受字段时取源端最新值)。用户也明确确认，提交前目标端同一字段被他人修改时，目标整体校验通过后，用源端最新的「定稿」覆盖且不提示字段冲突。审阅证据与执行证据分离。当前业务没有单字段不可读的常规路径。该极端情况按用户裁定保留本条 Requirement 未完成并继续处理下一条。执行过程中的并发处理仍待裁定。

- **D5 / QUESTION-008 已确认批次部分成功与重试范围。** 用户明确接受 [B08](../scenarios/shared-kernel-boundary-scenarios.md#b08-部分成功与批次重试)。重试只处理未完成的 B，保留已成功的 A，即使 A 的源端再次变化也跳过 A。成功结果如何可靠查回及提交结果未知时的恢复机制仍需验证。

- **QUESTION-008 的 KEEP_TARGET 确认语义已确认。** 用户明确允许内容未合并且仍有差异时设置 markAsMerged，决策结果记入 MergeCheckpoint。双侧版次均不变时，下次比对不显示该差异。见 [已确认场景](../scenarios/core-scenarios.md#keep_target-的已处理确认)。用户进一步明确版次为显式升级的业务 revision，普通修改只增加乐观锁 version。显式升级才产生版次历史数据并推进版次号 +1。双侧业务版次不变时，内容普通修改后已处理差异仍隐藏。用户进一步确认比对由用户主动发起。任一侧显式升级业务版次后，再次比对时旧 MergeCheckpoint 对新版本组合过时，之前合并过与未合并过的字段均重新比对，显示仍存在的差异。过时记录的物理保留方式与乱序协议仍待设计。

- **D2 的 PLM Part 接入范围已确认。** 用户明确 Part 通过共享 Tracker 接入，PLM 在默认工作集独立管理，其他工作集纳入共享查看，Part 不派生、不比对、不合并。依据见 [职责边界](../domain/shared-kernel-boundary.md#plm-part-的接入范围用户已确认)。该确认替代本轮早期基于 V4 非受管 Part 的推演，历史输入保留。GateFlow 对象范围见后续裁定，DQA 接入范围见后续共享查看裁定。

- **QUESTION-001/013 的 GateFlow 场景部分确认。** 用户明确 Process 为 GateFlow 核心聚合，一个 Process 多个版次，每个版次一个工作集，工作集父子链构成版次链。可从历史版次取全部内容，替换叶子草稿版次内容。见 [GateFlow 场景](../scenarios/shared-kernel-boundary-scenarios.md#gateflow-草稿版次内容替换)。用户进一步确认替换时保留叶子草稿的版次身份、工作集 ID 及原父级，例如 V1 内容替换 V3 后父级仍为 V2。历史 V4 的内部元素身份与范围切换实现仍为候选。

- **GateFlow 替换的原子可见性已确认。** 用户明确 V3 在全部准备成功前继续使用当前完整内容，随后整体切换。用户进一步确认准备期间允许编辑，期间编辑被整体覆盖符合业务语义。准备失败时继续使用当前内容及已保存编辑。该裁定更新历史 V4 §7.1 暂停写入的候选政策。具体事务边界与切换瞬间的并发提交协议继续设计。见 [场景](../scenarios/shared-kernel-boundary-scenarios.md#gateflow-草稿版次内容替换) 与 [行为图](../diagrams/collaboration-behavior.md#gateflow-草稿内容整体切换)。

- **GateFlow 对象范围及讨论边界已确认。** 用户明确工作集只包含过程元素业务对象、裁剪问题、裁剪答案、合规映射四类，不纳入共享 Tracker。四类内部细节业务逻辑留在 GateFlow BC 自身讨论，当前聚焦 Working Set 协作。相关场景已移除共享内容替换的待决分支。

- **D2 的 DQA 接入范围已确认。** DQA 自行管理八类质量业务对象，Tracker 全部共享，唯一工作集结合点为纳入查看。DQA 不进入比对/合并或非共享派生路径。具体类型见 [DQA 范围](../domain/shared-kernel-boundary.md#dqa-共享查看范围用户已确认)。此前以四个 BC 都提供合并案例的验证计划已改为按实际接入能力验证。

- **QUESTION-005 创建能力及归属已确认。** 子工作集可在已有非共享 Tracker 下新建 Item，共享 Tracker 下从子工作集入口新建的 Item 实际归属默认工作集。子工作集不能创建 Tracker。详见 [创建场景](../scenarios/core-scenarios.md#子工作集新建-item-与-tracker-边界)。用户进一步确认允许子工作集新增 Item 通过合并在父工作集创建对应 Item。见 [新增回并场景](../scenarios/core-scenarios.md#子工作集新增-item-回并父工作集)。家族身份分配与去重协议继续设计。

- **QUESTION-004/005 的 Item 身份等值关系已确认。** 用户明确沿工作集派生的 Item 共用 CommonId、各自 LocalId 不同，默认工作集 CommonId=LocalId，非默认工作集 CommonId!=LocalId；新增回并的父子对象同样使用相同 CommonId、不同 LocalId。用户进一步确认按 Item 实际归属判断，子工作集查看共享 Tracker 中的默认工作集 Item 时，仍是同一个 Item，CommonId=LocalId。详见 [身份规则](../domain/glossary.md#item-身份规则用户已确认)。用户进一步确认无关 Item 跨类型/Tracker 不共用 CommonId，CommonId 与 LocalId 都是雪花 ID。标识分配协议及单工作集内同族变体选择继续细化。

- **QUESTION-007 / POL-004 已确认。** 用户明确比对和合并仅允许直接父子工作集之间进行，兄弟和跨代不能直接操作。关系准入与各类型能力条件共同适用。GateFlow 历史内容替换按独立合同处理。见 [准入边界](../domain/shared-kernel-boundary.md#比对与合并的关系准入用户已确认)。

## 共享修改归属与禁止移除的补充确认

用户明确确认：共享 Tracker 自身及内部 Item 的新增、修改实际发生在默认工作集，所有绑定该 Tracker 的工作集同时可见；已绑定的共享 Tracker 不允许移除。已同步 `FACT-002`、`POL-008`、共享边界与 `SCN-002`，并收窄 `QUESTION-003/009`。本次也补齐此前 DQA 默认工作集归属的未决描述。当时非共享 Tracker 的移出规则尚未确认，现由下节补充确认收敛；共享对象删除及模式变更不由此次确认推导。文档总体仍为 Draft，不发布基线。

## 子工作集 Tracker 移除政策统一确认

用户明确确认当前业务限定：Tracker 一旦加入某个子工作集，不论共享还是非共享，后续不允许移除。此为当前业务政策，记录为 `POL-006`，不以存在 Item 或本地修改为前提。已同步共享边界、核心验收场景及 `QUESTION-009`，并标明历史“最后一个 Tracker 移出”反例不适用于子工作集。原历史输入中的“销毁”未被此确认覆盖；Item 删除、整个工作集删除及恢复仍独立讨论。未升级整体文档状态或发布基线。

## 分类约束与共享特性不可变的补充确认

用户确认 Tracker 的共享特性创建时确定，后续不允许修改；分类配置为共享时，Tracker 只能共享；分类配置为非共享时，创建可选共享或非共享。已更新 `POL-003`、边界职责、四种创建组合及双向修改拒绝场景，并收窄 `QUESTION-003`。历史“新 Tracker + 迁移”仅是未经批准的候选，不能作为此次确认的绕行方案。当时分类配置自身的变更政策尚待确认，现由下节补充确认关闭；缺省值和校验协议仍待设计，不自动推导。

## 分类共享特性为固定出厂设定的补充确认

用户明确确认分类配置自身的共享特性也不允许修改，出厂设定即固定。此限制不以是否已经创建 Tracker 为前提。已补充 `POL-003`、共享边界和分类修改拒绝场景，并从 `QUESTION-003` 移除分类共享特性变更这一待决项。仅确认当前讨论的共享属性，不自行扩展为其他分类属性的完整生命周期规则。文档整体保持 Draft，未发布基线。

## 直接父子双向合并能力的补充确认

用户明确确认父子工作集支持双向比对、合并，源侧新增、删除、修改均可使接收方产生对应变化。已同步 `FACT-007`、`POL-004`、职责边界、`QUESTION-007/009` 及六种方向/变化组合场景；合并图说明明确字段读取路径与删除存在性操作的区别。接收方业务 BC 的最终校验与提交职责、共享 Tracker 不参与合并、Tracker 绑定禁止移除等既有规则继续有效。未将删除能力推导为具体软删、彻底删除或恢复机制；文档整体保持 Draft。

## 合并决策优先于接收方变动的补充确认

用户确认子侧删除合并到父侧时，即使父侧已有新修改仍执行删除，不提示冲突，并明确推广为“无论接收方发生了什么变动，以决策为准”。已记录通用业务政策、双向删除覆盖修改场景，更新边界、`QUESTION-008` 与合并图的拒绝分支。目标 BC 校验及真实结果记录职责继续有效；内部并发协议需兑现该优先级，不能重新引入基于接收方变化的用户冲突裁定。整体文档仍为 Draft。

## 本轮检查结果

- **GateFlow 全量替换的成员规则已确认。** 用户明确 V3 独有、V1 没有的流程元素应从 V3 当前内容移除，使替换后的内容完整对应 V1。见 [GateFlow 场景](../scenarios/shared-kernel-boundary-scenarios.md#gateflow-草稿版次内容替换)。移除后的物理清理与外部引用策略继续设计。

当前 docs/working-set Markdown 本地文件链接失效 0；12 个 Mermaid 块图类型检查通过，新图代码围栏成对。环境无 mmdc，未执行渲染检查或完整 Mermaid parser；仍是接受前待办。历史 pre-design 本轮简单扫描记录 94 个失效本地链接引用（阶段 2 报告记为 93，扫描口径未统一），未修改。git diff --check 已通过；变更仅限 docs/working-set 的 13 个既有 Markdown 与 4 个新增 Markdown，未修改生产代码、历史输入或 AGENTS；Git HEAD 存在，无提交/Tag 操作。一次历史图类型扫描因旧材料使用其他语法中止，随后限定当前知识库重新检查通过。

## D-001：Tracker 独立聚合裁定（2026-11-10）

**用户正式确认的领域建模决定；ADR 文档仍 Proposed，未发布新基线。**

直接依据：用户本轮明确指定 D-001，确认 Tracker 是独立聚合，拥有独立业务身份、状态、生命周期及管理行为。Working Set 可以定义其工作范围或关联，但 Tracker 不作为 Working Set 内部实体；统一语言只使用 Tracker，Tracker Branch 不作为独立概念或根。

QUESTION-002 的独立性问题关闭。跨 Working Set 的 Tracker 身份关系及 Item 是否属于 Tracker 聚合仍未确认；不改变先前已确认的共享权威规则，也不将其泛化为所有工作集同一身份。D6 中以 Branch 独立生命周期作为 AP-002 准入前提的建议现已失效，保留原评审文本供历史追溯。

影响：术语 CONCEPT-002 正式化，CONCEPT-005 停用；AP-001/003 的 Tracker 独立根兼容但其余结构未批准，AP-002 原 Branch 根不兼容，来源与演进语义须重审；候选类图和边界图移除该根，不以改名偷偷接受身份映射。Item 归属继续待定，未新增其业务不变量。

参见 [Proposed ADR-0001](../adr/0001-tracker-independent-aggregate.md)、[修订方案](../proposals/aggregate-boundary-options.md)、[图表](../diagrams/aggregate-candidates.md)。本次不更新已接受事实政策主题的范围，不发布、提交或创建 Tag。

<a id="d-002"></a>
## D-002：Tracker 独立身份与派生谱系（2026-11-10）

**业务裁定：用户明确确认；设计推论：Draft；不发布新基线。** 来源为本聊天用户标题“阶段 3 · D-002：Tracker 独立身份与派生谱系”下的七项裁定。关联前节 D-001 与 [ADR-0001](../adr/0001-tracker-independent-aggregate.md)，不另设冲突决策编号。

1. 非共享 Tracker 派生产生新的独立业务身份，源与派生是两个不同领域对象。
2. Tracker 之间有追溯层级，通过 parentId/sourceId 表达；不先定义具体指向。
3. Tracker 是独立聚合，拥有业务身份、状态、生命周期及管理行为。Working Set 不拥有其完整生命周期。
4. 统一领域语言只使用 Tracker，不恢复独立 Tracker Branch。
5. 仅针对非共享派生；Shared 的已有权威复用、默认工作集归属及同时可见规则不变。

归档至 FACT-003、CONCEPT-002/004，QUESTION-002 收窄为 a—e 及 Item 边界。D-001 记录中的“非共享身份关系未决”保留为历史状态，由本节补充关闭，不修改 D-001 原始裁定。

设计影响：按身份引用表达谱系、Working Set/Tracker 独立聚合协作、Lineage 暂不单设根仍为 Draft 推论；Item 边界仍待评审。AP-002 修订方向解释力增强，不代表接受 AP-002，也不恢复原 Branch 根；AP-001/003 同样可以承载不同 Tracker 身份，完整方案未批准。

仍待用户裁定：parentId 是否为直接父、sourceId 是否为最初来源、是否允许多级派生、谱系能否修改、同一 Working Set 能否有同谱系多个 Tracker。POL-005 的 Item CommonId 唯一性不能反推 Tracker 共存政策。对应 [问题与影响](../domain/open-questions.md#question-002d-002-后保留的子问题)。

一致性注意：POL-002 原生创建入口限制不因派生产生新身份自动放宽；不把新身份等同允许用户任意创建。已接受 Shared 规则不参与本次修改。新的 Tracker 关系也不改变已确认的 Item 身份规则。

追溯：[SCN-013](../scenarios/core-scenarios.md#scn-013-非共享-tracker-派生独立身份)、[候选图](../diagrams/aggregate-candidates.md#d-002非共享派生身份与追溯边界)、[方案影响](../proposals/aggregate-boundary-options.md#d-002-影响与修订方向draft)。无实现或运行测试证据；图形渲染仍待验证。

<a id="d-003"></a>
## D-003：Tracker 双维关系模型（2026-11-10）

**用户正式确认；候选图与架构推论仍 Draft；不发布基线。** 直接依据：本聊天用户“阶段 3 · D-003”裁定及 WS0/WS1/WS2 示例。

parentId 表达同工作集的直属结构父子关系；sourceId 表达直接父级工作集下的来源 Tracker，逐级指向，不跨中间工作集。两字段是组织树与派生链两个维度，不能互换。Tracker 继续独立聚合，不使用独立 Tracker Branch。多级逐级派生已由示例确认。

**历史澄清**：保留前节 D-002 七项裁定与当时分析。将两个字段一起解释为派生谱系不准确，由本节明确纠正；D-002 的非共享新身份、Shared 不变及生命周期结论继续有效。不是删除历史或将 D-001 ADR 自动 Accepted。

候选约束逐条记录为 INV-014—018：同范围结构父、直接父工作集来源、禁止跳级、双维分离直接有裁定支持；结构无环为组织树推导候选，不自动批准其算法、事务或所有 INV 全文。Shared 结构有效性不能用数据库归属代替有效组织范围；其不同工作集父级一致性仍未确认。

六项开放问题：Shared 父级一致性、同一工作集同源多个派生、parentId 可修改性、sourceId 创建后不可变性、跨工作集树继承、Item 聚合归属。保留在 QUESTION-002，旧 a/b/c 关闭，旧修改问题分解为两个维度。

影响：[术语](../domain/glossary.md)、[事实](../domain/facts.md)、[不变量](../domain/invariants.md)、[聚合分析](../domain/aggregate-model.md)、[方案](../proposals/aggregate-boundary-options.md) 均区分两维；AP-001/002/003 继续 Draft，不因树形连线聚合全部 Tracker。旧 D-002 图标记 Superseded，[新图](../diagrams/tracker-dual-relations.md) 用工作集有效范围框及独立 Tracker 根表示两维共存；场景见 SCN-014。

<a id="d-004"></a>
## D-004：Tracker 派生时的组织结构裁剪（2026-11-10）

**用户正式裁定；候选模型推论保持 Draft。** 直接依据：本聊天“阶段 3 · D-004”的八项规则及只选 B 的示例。关联 D-001 独立聚合、D-002 非共享新身份与 D-003 双维语义，不替代它们已确认的身份规则。

确认：parentId 是同工作集直属结构父，sourceId 是直接父级工作集来源。非共享派生获得新身份；只选 B 而不选其父 A 时，允许 B1.parentId=null 成为 WS1 根，B1.sourceId=B，不自动创建 A1。不同工作集可有不同组织树，组织结果变化不改变直接来源语义。不引入 Tracker Branch。

知识分类：POL-009 是当前选择/裁剪政策及合法结果；Tracker Hierarchy 与 Tracker Derivation 分开定义。INV-019/020 的业务子句有直接裁定，INV-021 的目标合法结构而非完整源树同构是推论；形式化/技术保护方案继续 Draft，不自动升级全部 INV。

影响与旧假设：当前文档未发现已接受的完整树继承要求；候选 INV-011/SCN-001 封闭清单、AP-001 配置完整、AP-003 Manifest 不能解释为来源祖先闭包。保留旧推演并增加这一限定，不静默变更历史决定。D-003 的泛化“树继承未定”收窄为“父也选择时如何继承未定”。Shared 跨范围父级一致性不从“不同工作集树可不同”得出答案。

聚合与生命周期：Tracker 仍独立，根是合法组织位置而非未完成创建；Working Set 的范围协作不变成 Tracker 完整生命周期所有权。跨聚合准备、失败恢复、纳入可见性及整批原子性均未确认，不借 Merge 部分成功或 GateFlow 整体切换政策代答。

本轮五项未决：父也选择是否必须保留关系、派生后能否修改 parentId、Shared/非共享混合树有效父、同目标同源多派生、整批派生原子性。此前 sourceId 可变性、Item 归属继续开放，不自动关闭。

追溯：[POL-009](../domain/facts.md#pol-009非共享派生组织裁剪用户已确认)、[SCN-015](../scenarios/core-scenarios.md#scn-015-非共享-tracker-派生组织裁剪)、[完整双维及裁剪图](../diagrams/tracker-dual-relations.md)、[候选方案影响](../proposals/aggregate-boundary-options.md)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-005"></a>
## D-005：父也选择时的默认继承与创建前显式重组（2026-11-10）

**业务政策：用户已确认；技术组织协议仍 Draft；不发布基线。** 直接依据：用户对“非共享派生且原父也被选择时组织关系如何处理”的裁定（引用对话第 5 题选择 C），并由 D-006 正文第 3 条重申。

确认：非共享派生时，若原结构父也被选择，默认继承已选节点间的组织关系（子仍以已选父为结构父）；用户可在创建完成前显式指定新的合法组织结构，包括使子节点成为根（parentId=null）。未选择原父时仍适用 POL-009（裁剪为根），不自动补派生未选父。以上两种组织结果均不改变直接 sourceId。

知识分类：`POL-010`（业务政策，见 [facts](../domain/facts.md#pol-010默认继承与创建前显式重组d-005用户已确认)）。默认继承、显式重组能力与最终结构合法性分别成立，均不等于创建后允许修改（后者由 D-006 关闭）。

关闭项：QUESTION-002.g（父也选中时是否必须保留关系）。仍未决：组织树映射、提交与范围校验协议、创建批次原子性。

追溯：[POL-010](../domain/facts.md#pol-010默认继承与创建前显式重组d-005用户已确认)、[INV-023](../domain/invariants.md)、[SCN-016](../scenarios/core-scenarios.md#scn-016-创建前显式重组)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-006"></a>
## D-006：Tracker 创建后组织关系不可变（2026-11-10）

**业务政策：用户正式裁定；技术守卫与创建协议仍 Draft；不发布基线、不修改代码、不提交或打 Tag。**

用户本轮明确裁定以下八项，作为 Tracker 组织位置的创建边界：

1. Tracker 的 `parentId` 表达同一 Working Set 内直属组织父节点。
2. 新建 Tracker 时必须确定其组织位置，可以是根节点，即 `parentId = null`。
3. 非共享 Tracker 派生创建时按 D-004、D-005 处理组织结构：未带入源 Tracker 的父节点时可裁剪父子关系、设 `parentId = null`；原父节点也被选择时默认继承组织关系；用户可在创建完成前显式指定新的合法组织结构。
4. Tracker 一旦创建完成，`parentId` 不允许修改。
5. 创建完成后，不支持对已有 Tracker 进行移动、更换父节点或调整为根节点。
6. 不同 Working Set 可以在各自创建或派生过程中形成不同的组织树。
7. Tracker 的 `sourceId` 继续表示直接父级 Working Set 中的来源 Tracker，不受组织结构裁剪和重组影响。
8. Tracker 仍然是独立聚合，统一领域语言不使用 Tracker Branch。

知识分类：`POL-011`（业务政策，见 [facts](../domain/facts.md#pol-011创建完成后组织位置不可变d-006用户已确认)）；对应候选约束 `INV-022/023`。此裁定只冻结组织位置，不冻结 Tracker 的其他业务状态、身份生命周期，也不封闭 Working Set 的成员扩展；不扩大 `POL-002` 的原生创建入口。`sourceId` 直接来源语义不变，但不由此自动批准其生命周期可变性规则。无需为此设计复杂 Draft Tracker 状态机。

关闭项：QUESTION-002.d1（创建后 parentId 可修改 → 不可修改）。仍未决：Shared 混合树有效父及跨工作集一致性、同源多派生、sourceId 创建后可变性、派生整批原子性、Item 聚合归属。父 Tracker 后续删除或停用时子节点如何处理属于 QUESTION-009.a 的独立生命周期问题，本轮不选择：不自动清空或重挂 parentId、不级联删除，也不因此重新开放移动权限。

追溯：[POL-011](../domain/facts.md#pol-011创建完成后组织位置不可变d-006用户已确认)、[INV-022/023](../domain/invariants.md#d-005--d-006组织位置的创建边界)、[生命周期图](../diagrams/tracker-creation-lifecycle.md)、[SCN-017](../scenarios/core-scenarios.md#scn-017-创建后组织位置不可变)、[SCN-018](../scenarios/core-scenarios.md#scn-018-创建时父缺失与组织环反例)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-007"></a>
## D-007：TrackerItem 公共契约与业务 BC 聚合边界（2026-11-10）

**业务裁定：用户正式确认；LightTrackerItem 聚合边界与事务协议仍 Draft；不发布基线、不修改代码、不提交或打 Tag。**

### 已确认（用户裁定）

1. 工作集领域统一使用 `TrackerItem` 作为公共 Item 概念，不继续将 `Item` 作为独立于 TrackerItem 的通用聚合模型。
2. Working Set Shared Kernel 内声明顶级 `TrackerItem` interface。
3. 各业务 BC 的领域对象通过实现 TrackerItem 接口获得参与 Working Set 管理的公共能力。
4. Part、Requirement 等业务对象是各自业务 BC 中的完整独立聚合。
5. Working Set 不拥有各业务 BC 业务对象的完整领域状态、业务生命周期与业务不变量。
6. Working Set 负责公共 TrackerItem 身份及工作集相关机制。
7. Working Set 内计划引入 `LightTrackerItem`，作为专属领域聚合，用于管理轻量化索引信息。
8. Tracker 仍然是独立聚合，不因管理 TrackerItem 的工作范围而拥有业务对象聚合。
9. Shared Kernel 的组织定位保持不变。

知识分类：`FACT-012`（[facts](../domain/facts.md)）；术语 `CONCEPT-021/022`（[glossary](../domain/glossary.md)）。第 7 项 LightTrackerItem 的「专属领域聚合」是候选定位，其完整边界与权威性未确认，不得因已有索引表就判定其必为聚合根，也不否定该候选定位。

### 关闭项

QUESTION-002.h（Item 聚合归属）：业务 BC 拥有具体业务对象聚合，Working Set 通过 TrackerItem 契约协作，只负责公共身份与工作集机制。原「Tracker 内部或独立边界」二选一不再成立。

### 仍需候选设计（不自动批准）

- LightTrackerItem 的完整聚合边界、内部属性和领域行为。
- LightTrackerItem 是否拥有有效变体及版本定位的权威状态。
- CommonId、LocalId、Revision、Version 等信息如何分配到 TrackerItem 契约和 LightTrackerItem。
- 跨 BC 首次 COW 修改的事务与一致性协议。
- 业务 revision 与 Working Set revision 的关系；TrackerItem 接口的最小共同语义；跨 BC 业务校验与公共工作集操作的协调。

### 对候选方案的影响

AP-001 的 ID 引用可表达独立业务聚合，但不得把业务对象组合进 Working Set；AP-002 修订方向不得让 Tracker 拥有业务对象聚合；AP-003 的 ItemVariant 独立根候选与 LightTrackerItem 在「轻量索引/公共身份」上高度重叠，需澄清命名差异或边界，避免重复建模。三者均继续候选，不自动选定。

### 追溯

[术语](../domain/glossary.md)、[事实](../domain/facts.md)、[候选不变量](../domain/invariants.md)、[开放问题](../domain/open-questions.md)、[聚合分析](../domain/aggregate-model.md)、[契约与聚合图](../diagrams/trackeritem-contract.md)、[方案影响](../proposals/aggregate-boundary-options.md)、[SCN-019/020](../scenarios/core-scenarios.md#scn-019)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-008"></a>
## D-008：LightTrackerItem 权威聚合与 COW 延迟物化（2026-11-10）

**业务与架构裁定：用户正式确认；物化协议、身份等值关系与并发仍 Draft；不发布基线、不修改代码、不提交或打 Tag。**

### 已确认（用户十二项裁定）

1. Working Set 负责公共 TrackerItem 身份与工作集相关机制。
2. 各业务 BC 的 Part、Requirement、Process 等是独立完整业务聚合，由各自 BC 管理业务内容与领域不变量。
3. Working Set 定义 TrackerItem 公共接口，业务 BC 对象实现。
4. LightTrackerItem 是 Working Set 专属独立聚合，不是单纯查询投影。
5. 创建顺序：先 Working Set 聚合、Tracker 聚合，再执行 TrackerItem 派生。
6. 派生采用 Copy-on-Write，不立即创建和持久化完整业务对象副本。
7. 派生时创建 LightTrackerItem，分配当前变体 LocalId，保留 CommonId。
8. LightTrackerItem 记录 SourceLocalId、SourceRevision。
9. LightTrackerItem 经 TrackerId 指向所属 Tracker、经 WorkingSetId 指向所属 Working Set。
10. LightTrackerItem 承担工作集内身份与有效变体定位权威。
11. 业务 BC 通过 CQRS Query 路径获取业务对象，借助工厂重建完整业务聚合；调用方不感知底层物化状态。
12. Working Set 不拥有或直接重建各业务 BC 完整领域状态。

知识分类：`FACT-013`（[facts](../domain/facts.md)）；术语 `CONCEPT-022` 更新（[glossary](../domain/glossary.md)）。Derive/Resolve/Materialize 为候选统一语言，不固定其全部生命周期行为。

### 候选不变量审查

提取 INV-026—032 逐条审查（[invariants](../domain/invariants.md)）：INV-026/027/029/030 有 D-008 直接确认支撑，INV-028（范围约束）/031（首次物化唯一身份）/032（可观察一致性）仍候选。不因 D-008 自动把候选不变量标记 Accepted。

### 架构审查重点（保留候选）

- LightTrackerItem 权威状态 vs 查询投影：已确认非投影，但完整属性与物化协议候选。
- LocalId 是否同时代表完整业务聚合变体身份：待裁定。
- SourceRevision 快照语义与版本选择策略：待裁定。
- Current Revision 与 SourceRevision 的责任划分：待裁定。
- COW 状态演进：Derive → 未物化 → 物化，物化完成标志与有效内容指针候选。
- Query 返回完整业务聚合 vs CQRS 查询模型：已确认经 Query + 工厂重建，具体查询模型选择候选。
- 跨 BC 首次物化事务与并发：候选。
- AP-003 Manifest/Variant 与 LightTrackerItem 是否重复：需澄清，避免重复建模。

### 尚待用户裁定

1. 源端 Revision 派生后变化，未物化子工作集读取哪个版本。
2. LightTrackerItem.LocalId 是否直接作为业务聚合变体 LocalId。
3. 首次物化是否必须使用派生时预分配 LocalId。
4. 首次物化完成标志与有效内容指针定义。
5. 业务内容与 LightTrackerItem 更新是否必须同步原子完成。

不得因已有 `air_ws_items_index` 数据库表反向决定领域模型，只可作实现映射参考。

### 追溯

[术语](../domain/glossary.md)、[事实](../domain/facts.md)、[候选不变量](../domain/invariants.md)、[开放问题](../domain/open-questions.md)、[聚合分析](../domain/aggregate-model.md)、[契约与聚合图](../diagrams/trackeritem-contract.md)、[生命周期图](../diagrams/lighttrackeritem-lifecycle.md)、[方案影响](../proposals/aggregate-boundary-options.md)、[SCN-021—024](../scenarios/core-scenarios.md#scn-021)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-009"></a>
## D-009：来源变更触发 COW 物化及有效内容切换（2026-11-10）

**业务裁定：用户正式确认事件驱动 COW 机制；事件与事务可见性协议、历史内容保护与来源身份语义仍 Draft；不发布基线、不修改代码、不提交或打 Tag。**

### 已确认（用户十一项）

1. 业务聚合普通内容修改不自动升级 Revision；Revision 升级是独立显式业务行为。
2. SourceVersion 可能指向来源头版次，不保证对应不可变历史内容。
3. 仅通过可变来源头版次解析未物化变体，源端变化可能导致读取事实失真。
4. 来源修改前先 Query 获得修改前完整业务事实。
5. 来源业务 BC 随后执行领域修改及仓储保存。
6. 保存后发送来源变更事件，触发受影响派生变体 COW 物化。
7. COW 物化 Handler 据修改前事实形成派生变体自身完整业务聚合并保存。
8. 物化成功后发送物化完成事件。
9. LightTrackerItem 索引 Handler 收到后标记已物化，有效内容来源定位到自身 LocalId 及自身有效版次。
10. Working Set 负责公共身份、来源关系与有效内容定位；业务 BC 负责完整业务聚合构造、不变量与保存。
11. 采用事件驱动来源变更 COW，但事件与事务的准确可见性协议尚未裁定。

知识分类：`FACT-014`（[facts](../domain/facts.md)）；术语区分见 [glossary](../domain/glossary.md)（Business Revision、Concurrent Version、Derivation Origin、Effective Content Source、Source-backed、Local-backed、Materialization）。SourceRevision 与 SourceVersion 是否同义未确认，不机械归并。

### 必须纠正的既有模型假设

- 不得将业务 Revision 变化等同所有普通业务修改。
- 不得将 SourceRevision/SourceVersion 自动解释为不可变历史快照。
- 不得默认未物化变体可永久通过源端最新头版次得到创建时内容。
- 不得默认查询到修改前数据就意味着异步 Handler 之后仍能取得该数据。
- 不得将业务 BC 完整业务数据所有权转移给 Working Set。

### 重点反例审查

- 反例一（SCN-029）：物化完成前查询可见性（A/B/延迟可见）——未裁定。
- 反例二（SCN-026）：修改前事实 A 的不可变快照/稳定引用及谁拥有——候选。
- 反例三（SCN-027）：sourceLocalId 物化切换后如何追溯 R1 来源于 R0——区分 Derivation Origin 与 Effective Content Source，候选。
- 反例四（SCN-028）：连续修改多事件乱序是否重复/错误覆盖已物化变体——候选。

### 特别评审的设计问题

1. 修改前完整业务事实如何可靠保存并交给物化 Handler。
2. 业务修改保存后、物化完成前，派生变体查询可见性。
3. 派生历史来源与当前有效内容来源是否分别建模。
4. SourceVersion / SourceRevision 的严格定义。
5. COW 物化的唯一性、重复事件与并发处理。
（第 6 点未在本轮请求中完整给出，待补充。）

### 追溯

[术语](../domain/glossary.md)、[事实](../domain/facts.md)、[候选不变量](../domain/invariants.md)、[开放问题](../domain/open-questions.md)、[聚合分析](../domain/aggregate-model.md)、[生命周期图](../diagrams/lighttrackeritem-lifecycle.md)、[SCN-025—029](../scenarios/core-scenarios.md#scn-025)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-010"></a>
## D-010：COW 最终一致性与最小聚合模型收敛（2026-11-10）

**业务与架构裁定：用户正式确认；快照序列化/交付/持久化与首次快照业务选择规则仍 Draft；不发布基线、不修改代码、不提交或打 Tag。**

### 已确认（用户十四项）

1. COW 采用异步事件驱动策略，只保证最终一致性。
2. 来源业务聚合修改前，通过 Query 获取修改前完整业务事实。
3. 修改前事实作为事件关联的不可变内容快照，与来源变更事件一起传递。
4. 来源业务 BC 执行修改并保存后，发送来源变更事件。
5. COW Handler 据事件中的修改前快照物化尚未具有完整本地业务内容的派生 TrackerItem。
6. 目标业务聚合物化完成后发送物化完成事件。
7. LightTrackerItem 索引 Handler 标记已物化，有效内容来源切换为自身 LocalId 及自身有效版次。
8. 来源修改已保存、COW 尚未完成期间，允许派生工作集暂时读取到来源修改后的内容。
9. 物化完成后，派生变体以需要保护的修改前业务事实形成独立内容。
10. 不要求来源业务修改、目标物化与 LightTrackerItem 切换在同一跨 BC 事务。
11. 不再引入 AP-003 的 Manifest、ItemVariant。
12. 领域模型围绕 Working Set、Tracker、LightTrackerItem 三类核心聚合收敛。
13. 业务 BC 的 Requirement、Part、Process 仍为独立完整业务聚合。
14. TrackerItem 仍为 Shared Kernel 定义的公共领域接口。

知识分类：`FACT-015`（[facts](../domain/facts.md)）。

### 领域一致性边界（三职责）

- 来源业务修改：所属 BC 保证不变量、获取修改前完整快照并关联事件、保存成功后可靠交付事件。
- 派生业务聚合物化：SK 的 COW 机制确定目标变体、目标 BC 据快照构造并保存、完成后发物化完成事件。
- LightTrackerItem 状态切换：维护当前变体身份与有效内容定位、物化完成后切换有效来源到自身、不要求与来源修改同步提交。

不强行增加全局事务、跨 BC 大聚合或复杂安全门。

### 不可变快照语义

修改前快照必须稳定表达需要保护的完整业务事实；不得将可变的头 Revision 或仅有 SourceVersion 的事件当作不可变快照。Query 读取修改前事实不等于异步处理时仍可从来源数据库重建；事件关联的不可变快照是核心。事件序列化、可靠交付与持久化留待实施设计。

### LightTrackerItem 生命周期收敛

主状态收敛为 Source-backed（内容由来源解析）与 Local-backed（本地聚合已物化，有效来源定位到自身）；Materializing 是技术运行状态，不增加第三种业务状态。SourceLocalId 区分历史派生来源与当前有效内容来源，字段是否拆分待设计。

### 连续修改反例

A → B（E1 快照 A）→ C（E2 快照 B），事件可能乱序。需审查如何避免 R1 被 E2 错误覆盖，首次快照业务选择规则待用户确认；不提前用数据库锁或消息队列偏移量替代业务裁定。

### 聚合候选方案收敛

AP-001、AP-002 保留历史方案与可复用论证；AP-003 标记当前不采用，不删除历史讨论；不再引入 Manifest 或 ItemVariant 聚合。最小模型只含 Working Set、Tracker、LightTrackerItem 三类核心聚合，保留业务 BC 独立聚合与 TrackerItem 公共接口。

### 追溯

[术语](../domain/glossary.md)、[事实](../domain/facts.md)、[候选不变量](../domain/invariants.md)、[开放问题](../domain/open-questions.md)、[聚合分析](../domain/aggregate-model.md)、[方案收敛](../proposals/aggregate-boundary-options.md)、[契约与聚合图](../diagrams/trackeritem-contract.md)、[生命周期图](../diagrams/lighttrackeritem-lifecycle.md)、[SCN-029/030](../scenarios/core-scenarios.md#scn-029)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。

<a id="d-011"></a>
## D-011：连续来源修改时首次快照优先（2026-11-10）

**业务裁定：用户正式确认；Source Change Order / Derivation Baseline / Materialization Claim 等候选协议仍 Draft；不发布基线、不修改代码、不提交或打 Tag。**

### 已确认（用户九项）

1. 对尚未物化的派生 TrackerItem，来源首次发生需要触发 COW 的修改时，该修改前完整业务事实确定目标变体最终应物化内容。
2. 同一来源之后的连续修改不能覆盖首次确定的快照。
3. 快照选择按来源业务修改的实际先后顺序，而非事件到达顺序。
4. 即使第二次来源修改事件早于第一次事件被消费，最终也必须采用第一次修改前的内容。
5. 同一目标变体不得因重复事件产生多个不同的有效物化结果。
6. 目标 LightTrackerItem 成为 Local-backed 后，来源后续修改事件不能覆盖其本地业务内容。
7. 保持 D-010：事件异步处理、最终一致性，COW 完成前允许暂时读取变化后的来源内容。
8. 修改前完整事实通过事件关联的不可变快照传递。
9. Working Set、Tracker、LightTrackerItem 仍是最小领域模型核心，不重新引入 Manifest、ItemVariant。

知识分类：`FACT-016`（[facts](../domain/facts.md)）。

### 三个核心领域不变量

INV-037 首次快照确定性；INV-038 物化结果唯一性；INV-039 本地化后不可被源事件覆盖（[invariants](../domain/invariants.md)）。不将消息队列偏移量、数据库锁等技术实现写成领域不变量。

### 候选领域协议（不自动建聚合/表）

- **Source Change Order**：反映来源成功修改先后顺序的机制。
- **BeforeSnapshot**：来源修改前不可变完整业务事实。
- **Derivation Baseline**：目标变体派生时已继承的来源内容位置。
- **Materialization Claim**：候选的首次快照选择记录。

### 事件顺序与幂等评审重点

A. 如何确认一条事件是相对目标变体派生基准的首次相关修改（Derivation Baseline）。
B. 为什么不能采用「最先收到的事件直接物化」（事件乱序，E2 先到不代表是首次修改）。
C. 复用已有 Concurrent Version 还是独立变更序列表达来源修改顺序。
D. 物化采用已分配 LocalId，重复事件不生成不同有效业务对象（幂等）。
E. 多级派生中各层需要保护的目标变体如何识别。

以上 A—E 均为候选协议，不提前确定数据库锁、消息队列、Outbox 或分布式事务方案。

### 追溯

[术语](../domain/glossary.md)、[事实](../domain/facts.md)、[候选不变量](../domain/invariants.md)、[开放问题](../domain/open-questions.md)、[聚合分析](../domain/aggregate-model.md)、[生命周期图](../diagrams/lighttrackeritem-lifecycle.md)、[SCN-030—034](../scenarios/core-scenarios.md#scn-030)。未创建或接受 ADR，未发布基线，未修改代码、提交或打 Tag。
