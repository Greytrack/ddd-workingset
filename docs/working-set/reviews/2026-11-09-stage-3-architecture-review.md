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
| P0 | 四 BC 具体对象能力未完全确定；V4 08 §5 允许 Part 不接入 | 不可假定所有 Part 都会 COW。GateFlow/DQA 无可确认具体对象，保留专家问题 |
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

各项的备选方案保留供评审。D1、D3 与 D4 已有具体场景的用户裁定。见 [裁定进度](#裁定进度)。

| 决策/问题 | 推荐答案及依据/当前证据 | 真实可选答案 | 不同答案的影响 | 暂不决定阻塞 |
| --- | --- | --- | --- | --- |
| D1 / QUESTION-013 谁提交业务内容？ | C 按能力分工，BC 提交；B06/07、INV-009 候选与 V4 08 §5 支持 | A 全部语义比较也委托 BC；B 有限通用引擎并由 BC 提交内守卫 | C/A 保留 BC 内容上游与自身聚合；B 需声明委托字段及规则模型、收窄通用写契约并保证锁内合法性，实现策略维护成本增加 | 所有跨 BC 写合同 |
| D2 / QUESTION-013 哪些对象受管？ | 按类型能力声明；Requirement 作首个样例，Part 保留非受管分支；V4 08 §5 与 07 §2 | 所有四 BC 对象强制接入；或仅有限白名单 | 声明式准入保持 BC 所有权，强制接入扩大 Variant/历史/事件边界与契约；一致性及适配器实施量不同 | Part、GateFlow/DQA 场景验收 |
| D3 / QUESTION-006 历史保留覆盖什么？ | 按操作所需的内容、成员、生命周期、附件、关系保留固定证据；B04/05、FACT-006 待验证 | 创建时物化完整副本；不提供懒 COW/相关比较 | 固定历史端口支撑 Variant 小根；物化转由 BC 创建流程承担、存储及准备延迟增加；不保留则删减 Source Cut 契约和能力 | COW/Compare 可行性 |
| D4 / QUESTION-008 源值变化后合入哪个值？ | 用户已确认执行时取源端最新值，见 B09 | 评审曾比较使用审阅值、阻断并重新审阅 | SK 保留字段选择及审阅证据；源端提供当前值；目标 BC 继续负责整体合法性与提交。比较与执行证据需分离 | 提交期间的并发控制、源字段删除的业务含义及恢复协议仍待定 |
| D5 / QUESTION-008 合并成功粒度？ | 单目标整体合法提交，批次显式部分成功；B06/08/10 | 业务依赖组原子；整轮原子 | 分项保持各 BC 聚合及结果/Checkpoint 收敛；依赖组或整轮需重定边界/协调与可见性协议，实现代价显著变化 | 批次终态与恢复验收 |
| D6 / QUESTION-001/002/012 聚合组织？ | 先 AP-001 简单基准，规模证明后 AP-003；有独立 Branch 生命周期才 AP-002；当前缺规模与业务裁定 | 直接 AP-003；直接 AP-002 | AP-001 配置根强一致；AP-003 发布指针+多根准备；AP-002 Branch 根主导；内部区域/发布合同/加载与迁移实现随之改变 | 最终类图与一致性 ADR |
| D7 / QUESTION-004 家族键作用域？ | namespace/type/CommonId 候选，要求真实重号样本；跨类型重号反例 | 独立 FamilyId；全 WS CommonId 唯一 | 影响身份模块、唯一性权威、Variant/Reference 键、对齐合同与冲突机制；迁移须保留身份映射 | diff/引用与唯一性 |
| D8 / QUESTION-014 版本治理？ | 公共语言共同审批、版本化兼容、BC 能力声明，允许兼容窗口内异步升级；AC-001 仅固定归属 | 四 BC 与共享能力共同发布 | 版本化保持模型隔离，协议/回执持久化需保留版本；共同发布增加团队依赖和发布耦合，并不自动改变聚合或允许跨 BC 事务 | 正式公共契约发布 |

D4 的源字段取值已按用户裁定更新。D6 若数据表明整体配置不大且可接受冲突，应保留简单方案而非预先选 AP-003。

## 裁定进度

- **D1 / QUESTION-013 部分确认。** 用户在本对话明确裁定 [B06 的 ALM Requirement 合并拒绝](../scenarios/shared-kernel-boundary-scenarios.md#b06alm-requirement-的合并拒绝)。ALM 路径的提交裁决与拒绝结果已明确。四 BC 的公共责任模式、其他对象规则及正式契约仍待评审。

- **D3 / QUESTION-006 部分确认。** 用户在本对话明确裁定 [B04 首次编辑的内容依据](../scenarios/shared-kernel-boundary-scenarios.md#b04-首次编辑的内容依据)。来源后续修改或彻底删除时，派生 Requirement 仍可基于派生时内容首次编辑。删除完成前须保证该内容仍可读取。保留方式、期限及附件与关系的覆盖范围待裁定。该裁定适用于首次编辑。

- **D4 / QUESTION-008 部分确认。** 用户明确选定 [B09 执行读取源最新值](../scenarios/shared-kernel-boundary-scenarios.md#b09-接受字段时取源端最新值)。用户也明确确认，提交前目标端同一字段被他人修改时，目标整体校验通过后，用源端最新的「定稿」覆盖且不提示字段冲突。审阅证据与执行证据分离。当前业务没有单字段不可读的常规路径。该极端情况按用户裁定保留本条 Requirement 未完成并继续处理下一条。执行过程中的并发处理仍待裁定。

## 本轮检查结果

当前 docs/working-set Markdown 本地文件链接失效 0；12 个 Mermaid 块图类型检查通过，新图代码围栏成对。环境无 mmdc，未执行渲染检查或完整 Mermaid parser；仍是接受前待办。历史 pre-design 本轮简单扫描记录 94 个失效本地链接引用（阶段 2 报告记为 93，扫描口径未统一），未修改。git diff --check 已通过；变更仅限 docs/working-set 的 13 个既有 Markdown 与 4 个新增 Markdown，未修改生产代码、历史输入或 AGENTS；Git HEAD 存在，无提交/Tag 操作。一次历史图类型扫描因旧材料使用其他语法中止，随后限定当前知识库重新检查通过。
