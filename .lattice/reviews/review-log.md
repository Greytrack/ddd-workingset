# Review Log

## 2026-09-21 — 修订记录变更编号读取修复（change_id 翻译）+ 跑单测经验沉淀

- **Scope**: 2 files（infrastructure/sdkImpl `RevisonMapper.xml` + infrastructure/biz `RevisionChangeNumberTranslateTest`）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality（review-standards overlay）
- **Result**: 0 critical, 0 high, 0 medium | pass-with-notes（3 Info）
- **Key findings**: 测试夹具直用数字 classId（Info——测试惯例 vs §2 魔法值 High 的边界，两种口径并存）；mapper 两表条件重复 4 处（Info）；`order by revision` 依赖输出列解析（Info，当前安全）
- **Strengths**: 读取侧翻译 SQL coalesce 单点收敛零 Java 改动；回归先 RED 后 GREEN；夹具唯一 ID 自清理；跑单测经验已沉淀 operational-learnings + Mnemon 手册 cbc1554d

## 2026-08-19 — uc-work-product-suspicious-mark（独立标记查询接口与树解耦）

- **Scope**: 12 files (gf-biz + gf-sdkImpl + tests), 独立标记查询 + 树 marks 字段移除
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: 0 critical, 0 high, 0 medium, 0 low | pass
- **Key findings**: 无分层/架构违规，REST 契约清晰，Spotless 格式通过，单测全量通过（91/91）
- **Strengths**: 树结构与可疑打标计算彻底解耦；Context-first 传参无 DB 写副作用；单测覆盖全量 11 类输入/输出动态生命周期与边界场景

## 2026-07-06 — ADD_URL_ATTACHMENT 外部链接附件功能

- **Scope**: 14 files, gf-sdk + gf-sdkImpl + infrastructure-sdkImpl（枚举/handler/页签/测试）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: 0 critical, 1 high, 2 medium, 2 low
- **Key findings**: `AttachmentFileDto.setFileUrl` 截断 query string（高优先，外部鉴权链接失效）；`TabAttachmentTable` 查重硬编码
  `SUB_CLASS_ID` 与放行白名单不一致
- **Strengths**: 零硬编码 classId、Context-first 合规、`RequestThreadHolder` finally 清理规范、action 语义正确（ADD vs
  UPLOAD）

## 2026-07-06 — 属性裁剪删除 + dependent 展示（全量 diff 161 文件）

- **Scope**: 161 files（~1382+/7460-）, sdk + sdkImpl + biz + web
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 1 critical, 2 high, 3 medium, 2 low
- **Key findings**: 未跟踪源文件被已提交代码引用（CI 编译必失败）；存量 ATTRIBUTE scope 决策被 parse 降级为
  ELEMENT；IndexHandler/过程树未复用 dependent 父上下文
- **Strengths**: 属性裁剪链路删除一致，TailoringDisplaySupport 统一展示规则，ProcessGraphServiceImpl 已对齐 dependent 子元素

## 2026-07-06 — 属性裁剪整包删除 + dependent 展示

- **Scope**: 119 files（~862+/5647-）, sdk + sdkImpl + biz + web
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 2 high, 4 medium, 2 low
- **Key findings**: ProcessElementTreeSupport/IndexHandler 未复用 dependent 父上下文；API 层仍保留 attributeName/optionId
  参数与响应字段；E2E 测试未纳入版本控制
- **Strengths**: 属性裁剪链路整包删除一致，展示规则收敛至 TailoringDisplaySupport，流程图/Widget 读路径已对齐 dependent
  子元素

## 2026-07-06 — dependent 裁剪展示（修复后复审）

- **Scope**: 45 files + 5 未跟踪, sdk + sdkImpl + biz + web
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 2 medium, 3 low, 2 info
- **Key findings**: 过程树/Index 读路径未复用 dependent 父上下文兜底；E2E 与新类文件未纳入版本控制
- **Strengths**: 上轮 High 项已关闭，决策聚合单点化，流程图与 Widget 读路径对齐

## 2026-07-06 — dependent 子元素裁剪展示传播

- **Scope**: 42 files, sdk + sdkImpl + biz + web（裁剪读/写路径、流程图、Widget 列表）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 2 high, 3 medium, 3 info
- **Key findings**: 决策聚合逻辑在 DisplaySupport 与 Aggregator 重复；流程图 enrichTailoring 未复用 dependent 父上下文兜底；父元素
  isLogicalRemovalAncestor 未做 decisions 兜底
- **Strengths**: 展示规则收敛到 sdk TailoringDisplaySupport，写路径 getElements 重载修复根因准确，单测覆盖主场景

## 2026-06-25 — 修复新建元素裁剪用户/日期错误填充

- **Scope**: 4 files, gf-sdk + gf-sdkImpl（领域契约 + Widget 查询展示）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 1 warning, 2 suggestion
- **Key findings**: `finalAction.isDefaultState()` 单独为真时可能在 FORCED_VISIBLE 等状态清空审计字段；裁剪展示规则在 SDK
  与 Handler 双处维护
- **Strengths**: 根因定位准确，数据层+展示层防御，单测覆盖主路径

## 2026-07-14 — 角色/权限/条件形态配对（含未推送 commit + 未提交硬校验）

- **Scope**: 14 files (sdk/sdkImpl/biz + RolePrivilege 前端筛选)；相对 `origin/dev-3.5-zt` + working tree
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality, knowledge-priming, collaborative-judgment
- **Result**: 0 critical, 1 high, 2 medium, 1 low/suggestion
- **Key findings**: chore(lattice) commit 夹带 RBAC 业务变更；`@deprecated` 缺 `@Deprecated`；正向允许路径测试偏少
- **Strengths**: 配对轴改为 PrivilegeKind，双入口校验+实例只读+前后端筛选对齐；普通↔模板条件对称硬校验补全

## 2026-07-15 — RBAC UI 默认普通 Tab + TableCommon 空参重拉 + 前端助手复用

- **Scope**: 13 files（sdk PrivilegeKind/RoleNode、sdkImpl PairingSupport、TableCommon、RolePrivilege/组件筛选）；工作区未提交
  diff（分支已与 origin 同步）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, knowledge-priming, collaborative-judgment
- **Result**: 0 critical, 1 high, 2 medium, 1 low
- **Key findings**: AddPrivilegeModal 去掉实例前端过滤后依赖后端单点防御；TableCommon 空 `{}` 全局重拉语义扩张；组件依赖页面
  config 跨层耦合
- **Strengths**: `fromCriteriaKind` 收敛配对映射；「全部」不刷新根因定位准确；默认普通 Tab 与 URL 显式 `all` 仍兼容

## 2026-07-17 — workspace-creator-auto-assign-lead-engineer

- **Scope**: 3 Java files（gf-sdk GateflowResultCode + gf-sdkImpl ModelingWorkspacesDataObject + Test）；另有 lattice
  标准/锚点文档
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 1 medium（已修：主管分配改用 ATTR_OWNER）, 1 low | pass-with-notes
- **Key findings**: 初审同 postAdd 双源 userId；已统一为 ATTR_OWNER；单测命名未对齐 test{Method}_{Scenario}
- **Strengths**: 模板 ID 落 DataObject 常量、先 assign 后页签双写、Owner 空硬失败与蓝图一致

## 2026-07-23 — 裁剪子元素识别与跳转一致性优化

- **Scope**: 10 files (gf-biz + test + tailoring web page)；工作区 working tree diff
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: 0 critical, 0 high, 0 medium, 1 low | pass-with-notes
- **Key findings**: `resolveGateflowElementUrl` 在遍历循环中通过 `processService.getElement` 反查父元素，存在 N+1
  数据库查询潜在风险
- **Strengths**: 零魔法值硬编码、子元素识别树拼接逻辑对齐、图表与标签解析规则完全对齐、前端类型扩展与页面适配完整

## 2026-07-24 — 权限角色增删性能（缓存刷新 + 模板增量同步）

- **Scope**: 6 主代码 + 4 单测（rolepriv sdk/sdkImpl）；排除无关 frontend/yml
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 1 high, 2 medium, 2 low
- **Key findings**: PrivilegeSettingManagement 写路径缺事务放大增量同步部分失败风险；实例循环仍有 N+1；boolean bind 原始类型
- **Strengths**: 角色先于权限刷新顺序正确；增量 API 与全量并存；单测直接锁定刷新范围回归

## 2026-07-24 — 权限角色增删性能（复审：事务/枚举/批量同步）

- **Scope**: 10 files (+1 未跟踪枚举), biz + sdk + sdkImpl + tests
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 2 high, 0 medium, 1 low
- **Key findings**: PrivilegeBindingOp.java 未纳入 git；RoleSettingManagement 角色侧增删权限仍缺事务；解绑刷新略宽
- **Strengths**: 上轮 High/Medium 三项均已关闭；增量同步批量查询结构清晰

## 2026-07-24 — 权限角色增删性能（三轮复审）

- **Scope**: 12 files, biz Management + sdk/sdkImpl rolepriv + tests
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 0 medium, 1 low, 1 info
- **Key findings**: 上轮两项 High 已关闭；解绑刷新集合仍略宽；部分 Management 改动尚未 stage
- **Strengths**: 权限/角色两侧写路径事务齐全；增量同步批量化与枚举契约完整

## 2026-07-25 — template-role-disable-hide-from-team（#15116）

- **Scope**: 7 files, infrastructure sdk + sdkImpl + biz + tests
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 1 medium, 2 low, 1 info
- **Key findings**: 角色团队落库行过滤缺 biz 单测；`findInstanceIds` 与 sync 重复查询；多实例逐个注册 session afterCommit
- **Strengths**: 复用 `syncWorkspaceInstancesMetadata` 补齐 `updateEnabled` 缺口；展示与权限路径对齐蓝图；模板禁用同步单测覆盖主路径

# Review Log

## 2026-07-26 — workspace-delete-cascade-rbac-instances

- **Scope**: 22 files, gf-sdkImpl + infra sdk/sdkImpl（RBAC 生命周期清理）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（secure-coding 轻量）
- **Result**: C:0 H:1 M:2 L:1 | pass-with-notes
- **Key findings**: AdminCache `criteriaRowInfo` 就地清空事务回滚不同步；list 缓存非空时跳过属性索引；Criteria 用
  SpringContextUtils 取 where-used
- **Strengths**: WORKSPACE_INSTANCE 断言 + 配置页闸门分离；C1 挂载对称创建侧；不动 AdminNode afterCommit

## 2026-07-26 — workspace-delete-cascade-rbac-instances（复审：缓存对齐 + 回滚补偿）

- **Scope**: 22 files, gf-sdkImpl + infra sdk/sdkImpl + tests
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:1 M:2 L:3 I:4 | pass-with-notes
- **Key findings**: 回滚补偿分支零单测覆盖；`listWorkspaceInstanceIds`/`deleteWorkspaceInstanceCriteria` 无外部消费者却挂在
  SDK 契约上；`reconcileCacheToDb` 命名与行为方向相反
- **Strengths**: 上轮 1 High + 2 Medium 全部关闭；`batchDelete` 保持纯净不影响配置页删除路径；sdkImpl 新增方法零
  `@Transactional`

## 2026-07-26 — workspace-delete-cascade-rbac-instances（三轮复审：契约收缩 + 回滚单测）

- **Scope**: 23 files, gf-sdkImpl + infra sdk/sdkImpl + tests
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:0 M:0 L:2 I:3 | pass
- **Key findings**: 条件仍逐条 `batchDelete`；`afterCompletion` 读库依赖连接未归还（有意保留）；上轮 High/Medium 均已关闭
- **Strengths**: SDK 契约只留工作区级入口；回滚/提交对照单测锁住缓存对齐语义；单次回滚钩子注册

## 2026-07-26 — workspace-delete-cascade-rbac-instances（四轮复审：抽公共拆环）

- **Scope**: 27 files, gf-sdkImpl + infra sdk/sdkImpl + CriteriaReferenceSupport + tests
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:0 M:0 L:2 I:3 | pass
- **Key findings**: 条件仍逐条 `batchDelete`；引用匹配 token/正则仍为字面量（自 WhereUsed 下沉）；无 @Lazy，环已结构消除
- **Strengths**: 纯静态 `CriteriaReferenceSupport` 打断 Privilege↔Template↔WhereUsed；WhereUsed 委托公共匹配、Template
  同源扫缓存

# Review Log

## 2026-07-26 — query_element_tree 权限批量缓存性能优化

- **Scope**: 6 files (gf-biz management + tests), biz 编排 / 鉴权横切
- **Atoms**: clean-code, architecture, secure-coding, test-quality, knowledge-priming, collaborative-judgment
- **Result**: 0 critical, 2 warning, 3 suggestion
- **Key findings**: 发现权限 empty+probe 推断 unrestricted 语义脆弱；根节点从 getAllElements 派生与原 BASE_CLASS 查询不完全等价
- **Strengths**: 用 batchHasPrivilege / 发现批量 / ADD 缓存对准 N+1，失败回退单条鉴权，符合 fail-closed 与规则 13 批量前解析模式

## 2026-07-26 — query_element_tree 权限批量缓存（复审：三态/根查询/生命周期）

- **Scope**: 8 files (gf-biz management + tests), biz 编排 / 鉴权横切
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:0 H:0 M:0 L:2 I:2 | pass
- **Key findings**: 上轮 2 warning 已关闭；根语义回归仍缺显式 verify(getRootElements)；实例权限单类型异常回退无单测
- **Strengths**: DiscoveryCacheMode 三态 + 显式 isSkipAll；getRootElements 恢复；ADD 挂到
  TreeSupport.withPrivilegeContext；fail-closed 与引擎空列表语义对齐

# Review Log

## 2026-07-26 — workspace-dynamic-add 创建性能优化

- **Scope**: 12 files；infrastructure sdk/sdkImpl + gf-sdkImpl（RBAC 实例化 / DataObject postAdd / 单测）
- **Atoms**: knowledge-priming, learning-harvest, collaborative-judgment, clean-code, architecture,
  domain-driven-design, secure-coding, test-quality
- **Result**: 1 critical, 1 warning, 2 suggestion
- **Key findings**: sdkImpl 新增 `@Transactional`；批量 provision 跳过 history 与单条路径审计不一致；batch
  创建路径单测缺口；quiet/public provision 重复
- **Strengths**: Criteria/Privilege 批量预取对齐 N+1 治理；session 刷新仅增量用户；接口落 SDK、实现落 sdkImpl
- **Verdict**: pass-with-notes | C:1 H:0（warning 按 Medium 计）

## 2026-07-26 — workspace-dynamic-add 创建性能优化（复审：history/DRY/单测）

- **Scope**: 13 files；infra sdk/sdkImpl + gf-sdkImpl + review-log
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:1 H:0 M:0 L:1 | pass-with-notes
- **Key findings**: 上轮 warning/suggestions 已关闭；sdkImpl `@Transactional` 仍在（与同文件既有方法一致）；boolean
  双开关可日后收成枚举
- **Strengths**: flush 补写 CREATE history + 一次刷缓存；provision 共享核心；batch 创建单测锁住回归

## 2026-07-26 — workspace-dynamic-add 创建性能优化（三轮复审：simplify 后）

- **Scope**: 13 files；infra sdk/sdkImpl + gf-sdkImpl
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:1 H:0 M:0 L:1 | pass-with-notes
- **Key findings**: 上轮 warning/suggestions 仍关闭；sdkImpl `@Transactional` 仍在；insert+flush 双重 AdminCache reload
  为已知后续债
- **Strengths**: deferFlush 单开关；flush 补 history；postAdd PERF 收敛；索引/顺序重复已去掉

## 2026-07-26 — 工作区创建 RBAC 实例化性能优化

- **Scope**: 21 files；sdk / sdkImpl（rolepriv、criteria、i18n、objectframework）+ web 管理端 3 页 + 单测
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（未加载 secure-coding：无 Rest/鉴权入口变更）
- **Result**: C:0（Critical 邻近事务注解未计新增违规） H:2 M:2 L:1 | pass-with-notes
- **Key findings**: 批量路径仍循环 appliedTo；新方法延续 sdkImpl @Transactional；单测方法名仍含 WritesHistory；角色路径可能二次刷缓存
- **Strengths**: 批量 createNodes/properties + 合并刷缓存；修复 OptimizedBatchRefreshNode 批次跳过；afterCommit 二次
  register 陷阱有注释；实例不写 CREATE history 前后端一致
- **Metrics**: findings≈5 / 21 files；主导 atom=architecture+N+1 custom

## 2026-07-26 — 工作区创建 RBAC 性能优化（修复后复审）

- **Scope**: 24 files；sdk / sdkImpl（rolepriv、criteria、i18n、objectframework、appliedTo）+ web 3 页 + 单测 + review-log
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（未加载 secure-coding）
- **Result**: C:0 H:0 M:1 L:2 | pass-with-notes
- **Key findings**: 上轮 appliedTo N+1 / 角色二次刷缓存 / 单测命名已关闭；batchInsertForNewPrivileges 先写 AdminCache 后
  insert（失败时缓存污染）；criteria 行仍按行 addDomain
- **Strengths**: 新建 appliedTo 真批量落库；provision defer 刷缓存与 bind 合流；单测覆盖 batchInsert + skipsCreateHistory
- **Metrics**: findings≈3 / 24 files；主导 atom=clean-code 缓存一致性

## 2026-07-26 — 工作区角色实例权限不完整(10103052) 修复

- **Scope**: 5 files；sdk(IRoleService) + sdkImpl(OptimizedBatchRefreshNode/RoleService/RoleTemplateServiceImpl) + 单测
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:0 M:1 L:1 | pass-with-notes
- **Key findings**: 批量刷缓存补 rebuildRelationshipIndexForParent 为根因修复；reloadRoleNodeFromDb 兜底掩盖脏缓存但根因已闭环；新增
  SDK 契约 reloadRoleNodeFromDb(Long) 属缓存运维非 rule13 捷径
- **Strengths**: 批刷与单刷缓存重建对齐；兜底 reload 立即重建供同请求校验；两个单测锁住 heal-then-return 与
  incomplete-throws

## 2026-07-27 — Bug 15174 删除角色模板级联清理实例

- **Scope**: 12 files；sdk + sdkImpl（rolepriv）+ 单测
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（未加载 secure-coding：无 Rest/鉴权入口变更）
- **Result**: C:0 H:0 M:1 L:2 | pass-with-notes
- **Key findings**: deleteRole 级联后再校验模板自身 DELETE/成员占用（依赖 @Transactional 回滚）；缺 NORMAL
  权限跳过与「权限孤儿但条件仍共享」单测；sdkImpl @Transactional 为邻近既有模式
- **Strengths**: 闸门与存在性查询职责分离；引用计数后再删权限/条件；复用 findPrivilegesByRoleIds 与 #15130 清理原语

## 2026-07-27 — Bug 15174 删除角色模板级联（修复后复审）

- **Scope**: 12 files；sdk + sdkImpl（rolepriv）+ 单测
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（未加载 secure-coding）
- **Result**: C:0 H:0 M:0 L:1 | pass
- **Key findings**: 上轮 Medium（fail-fast）与 Low（覆盖矩阵）已关闭；残留单测命名风格与文件内约定一致
- **Strengths**: 闸门→自身校验→级联顺序正确；内置/自占用不级联有单测锁住；NORMAL 与共享 criteria 边界已覆盖

# Review Log

## 2026-07-29 — export-history-perf-keyset-pagination (7d38569c)

- **Scope**: 4 files, infrastructure/sdkImpl（持久层 + Repository + BO + Service）
- **Atoms**: clean-code, architecture, domain-driven-design
- **Result**: C:0 W:3 S:3 | pass-with-notes
- **Key findings**: `pageForExport` 新增无调用（死代码）；`exportHistory` God Function 60+ 行混合 6
  个关注点；ExcelWriter/ByteArrayOutputStream 未 try-finally 包裹存在资源泄漏风险
- **Strengths**: keyset 游标分页消除深分页退化；i18n computeIfAbsent 模板缓存对齐 N+1 治理；静态 ACTION_DESC_MAP 根除百万级
  ERROR 日志；ThreadLocal SDF 线程安全正确
- **Metrics**: findings≈6 / 4 files；主导 atom=clean-code

## 2026-07-27 — frontend-template-instance-role-visibility

- **Scope**: 9 files（utils + 三页面 + env），工具层/页面层
- **Atoms**: clean-code, architecture, test-quality
- **Result**: 0 critical, 1 warning, 2 suggestion
- **Key findings**: Jump builders 未按 visibility clamp/隐藏；getRoleMorphVisibility 空 catch 吞错；三页重复取 visibility
- **Strengths**: 解析层内聚实例依赖模板；单测可注入；模板关用空 Tab 列表隐藏切换条


## 2026-07-28 — copyProcess 裁剪数据复制（bug 15231）
- **Scope**: 2 files（ProcessServiceImpl + Test；排除无关 pnpm-lock），gf-sdkImpl
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:0 M:2 L:1 | pass-with-notes
- **Key findings**: 子类解析逻辑与模块导入重复；`getElementsForModuleCopy` 命名语义泄漏；parentId=0 条件略宽于模块路径
- **Strengths**: 复用既有裁剪传输服务与 FORCE 模式；运行时证据闭环；回归单测锁住映射与 transfer 调用

## 2026-07-30 — copyProcess 版本工作集裁剪问答 parentId=null 跳过
- **Scope**: 2 files, gf-sdkImpl（ProcessServiceImpl + ProcessServiceImplTest）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 1 medium, 1 low
- **Key findings**: 根因在 repairTailoringTreeIndex 未写 parent=0，本次仅消费侧兜底；单测未断言答案 parent 映射到新建问题 ID
- **Strengths**: 修复面极小、注释说清 null↔0 语义、先红后绿回归单测已覆盖主路径

## 2026-07-30 — bug-15265 名称快照 + bug-15271 裁剪版本灰显
- **Scope**: 16 files (gf sdk/sdkImpl/AirAPI/infra mapper + GateflowExtras); Flyway 按标准排除
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:1 M:1 L:2 | pass-with-notes
- **Key findings**: air_sys 属性配置未随 DDL 交付（发布依赖专人）；GateflowExtras 树非空查不到时无 numeric 回退
- **Strengths**: Context-first 写盖戳 + 读优先快照；传输/WS 复制列齐全；parentId=null 回归单测扎实

## 2026-07-30 — parentId null/0 根语义全路径兼容（不改 repair SQL）
- **Scope**: ~28 files, gf biz/sdk/sdkImpl（排除 bug-15265 与前端无关改动）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:0 M:1 L:2 | pass-with-notes
- **Key findings**: 模块导入 createElementCopy 仍手写 ==0 靠非 Number→0 兜底；CHILD 注释写 null 但实际 API 拒 null；工作区混有 bug-15265 勿同提
- **Strengths**: sdk 集中 helper + 读双认/写规范 0；保留 repair SQL 约定；copyProcess null 根回归单测到位

## 2026-08-11 — DQA XBOM 入站 OpenAPI + UC 工作产品树打标
- **Scope**: ~30 files, dqa-biz/sdk + gf-biz/sdk/sdkImpl + apps auth/swagger（排除 jar/脚本/文档）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:0 H:3 M:3 L:2 | pass-with-notes
- **Key findings**: 免 token 入站可推进工作流；`workingsetId=1L` 魔法值 + 全量子类扫描 fallback；树打标在 `queryElementTree` 已加载后再次 `getAllElements`
- **Strengths**: treeMarkers SPI 与 constraints 同构；`WorkProductOutputUsage` 共享判定；Biz 经 SDK 接口打标；入站直接返回 DTO

## 2026-08-11 — UC 树打标 + DQA 问题报告 OpenAPI（复审 working tree）
- **Scope**: ~28 files, gf marking SPI/biz enrich + dqa openapi + px-comac xbom（排除 jar/专利/脚本）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: 1 critical, 1 high, 2 medium, 2 low | fail-with-notes
- **Key findings**: 免 token 路径写 ThreadLocal 会话但 AuthInterceptor 不清理；`updatedAt` 必填未消费；无 output 判定 O(元素×关联)
- **Strengths**: treeMarkers 与 constraints 同构；共享 WorkProductOutputUsage；Context-first 复用已加载 elements；入站 DTO 直出 + 审批动作单测齐全

## 2026-08-11 — UC 可疑标记 + DQA↔XBOM OpenAPI/PX（当前 working tree）
- **Scope**: ~35 files, gf marking + dqa openapi/IP allowlist + px-comac xbom + apps yml/swagger
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:1 H:1 M:2 L:2 | fail-with-notes
- **Key findings**: 主 yml 默认放行 127.0.0.1 + IpUtil 信任 XFF 可绕过免 token 工作流接口；sourceSystem 契约未强校验；Validator 仍按 Map 重算 output Set
- **Strengths**: Context-first 复用 elements；treeMarkers 配置驱动；IP matcher fail-closed/CIDR；会话 finally 清理与单测齐全

## 2026-08-11 — process-category-folder-uuid-number
- **Scope**: 8 files（含新建 Support/测试）, gf-sdk + gf-sdkImpl + gf-biz
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 2 medium, 1 low
- **Key findings**: D2 分类掩码未覆盖 IndexHandler/关联列表等读路径；创建路径缺「UUID 不占号」集成级断言
- **Strengths**: `CategoryFolderNumberSupport` 放 gf-sdk 复用正确；过程根保留过程编号契约清晰；树/比对/选择器主路径已对齐
- **Follow-up (fixed same day)**: Index/Assoc/Graph/裁剪/合规等统一 `toDisplayNumber`；落地 `putPersistedNumberAttr` + `testInitCategoryNodes_UsesUuidNumber_DoesNotConsumeAutoNumber`

## 2026-08-12 — UC 工作产品疑问标记（契约简化后）
- **Scope**: ~20 files, gf-sdk + gf-sdkImpl/marking + gf-biz queryElementTree
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: 0 critical, 0 high, 1 medium, 2 low | pass-with-notes
- **Key findings**: TreeMarkContext 仍持有未消费的关联 map；`isUsedAsOutput(Map)` 生产路径已弃用；`output` 关联类型常量可上收 GateflowConstants
- **Strengths**: Biz→SDK 分层与 Context-first 复用 elements；与发版校验共享 WorkProductOutputUsage + 预计算 Set；契约收敛为 type+tip

## 2026-08-11 — UC可疑标记 + 分类UUID编号 + DQA XBOM OpenAPI（合报复审）
- **Scope**: ~45 files, gf marking/CategoryFolder + dqa openapi + apps yml/swagger + px-comac xbom（排除 jar/专利/脚本）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:1 H:1 M:2 L:2 | fail-with-notes
- **Key findings**: IpUtil 信任 XFF 可伪造 127.0.0.1 绕过免 token 工作流接口；主 yml 默认环回白名单；sourceSystem 未按 XBOM 强校验；分类创建缺 DataObject 级 UUID 断言
- **Strengths**: Context-first 打标、共享 WorkProductOutputUsage、分类掩码 D2 已铺开、会话 finally 清理、IP matcher fail-closed

## 2026-09-01 — gateflow-view-schema-and-process-group-marking
- **Scope**: 30 files, gf-biz + gf-sdk + gf-sdkImpl + tests
- **Atoms**: clean-code, architecture, secure-coding, test-quality
- **Result**: 2 critical, 7 warning, 4 suggestion | pass (fixed)
- **Key findings**: SDKImpl/Lambda 中 setRollbackOnly 事务边界清理；删除与 syncTransfer 异常隔离保护；findProcessModelTransfers 补齐双向查询；TreeMarkContextFactoryTest 恢复全量 12 场景测试并完善去重断言
- **Strengths**: Link targets Context-First 批量预解析规避 N+1；PROCESS_MAP 版本过滤严密；DataObject 契约实现对称

## 2026-09-14 — 过程组模型落库改造 + 过程地图未初始化工作区修复（推送前复审）
- **Scope**: 21 files, gf-biz + gf-sdk + gf-sdkImpl + web ProcessTree + lattice 文档
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality
- **Result**: C:0 H:2 L:1 I:1 | pass-with-notes
- **Key findings**: PROCESS_MAP 元模型回退未排除"有版本行但工作版本为模块/参考模型"的工作区（已修：回退限无版本行工作区）；标记列迁移经查证无需迁移——relationship_type 自 v3.3 为 int8（V3.3.0__upgradesql.sql:215），旧字符串写入受 PG 类型约束从未落库，association_relation varchar(255) 已存在（V3.0.0:8232/V3.3.0:255）；deleteProcessModelTransfer 第三参恒为常量
- **Strengths**: DTO 删字段与 web 引用同步 clean cutover；标记展示批量预解析无 N+1；常量统一 GateflowConstants

## 2026-09-15 — 过程组视图页全屏 + 单子节点撑满（未提交代码审核）
- **Scope**: 5 files, gf-sdkImpl 元模型 JSON + web（dynamic / MainContentLayout ProcessTree / VisualizationProcessGroup）
- **Atoms**: clean-code, architecture（叠加配置常开）；DDD / secure-coding / test-quality 未触发
- **Result**: C:0 H:0 M:3 L:3 I:1 | pass-with-notes
- **Key findings**: ① body 级 `gf-viz-fullscreen` 隐含单实例假设，多实例卸载会互删 class（当前渲染路径只挂一个，未触发）；② `gf-unknown-single-child` 按结构命中，除 uc_pg 视图外还波及 tto 的 index/description 单子节点元素页，且 `> .gf-viz-wrapper` 直接子选择器在插入 `.gf-anchor` 时会静默失效（visualization_pg 自带 title）；③ uc_pg 视图删 `index` 卡片的波及面已核实：仅过程组模型，uc/tto 不受影响，后端无测试断言 view children
- **Strengths**: 全屏沿用 AirFlow 既有 CSS overlay + z-index 分层（900/1000）约定；i18n 复用既有词条且中英齐备；ProcessTree 判定口径与既有 `nodeKey` 同源，无性能开销
- **Follow-up (fixed same day)**: 用户实测「并没有撑满」→ 查真实 DOM：Common/Card 根节点本身就是 `.ant-collapse.gf-card-tabs-collapse`，不存在 `.gf-card` 包裹层，`.tabcard*` 属于 TabCard（AirFlow 用）；原 CSS 照抄 AirFlow 两层全未命中。已把选择器对齐真实折叠链（`.gf-card-tabs-collapse > .ant-collapse-item > .ant-collapse-content > .ant-collapse-content-box > .gf-card-panel-content > .gf-card-content-inner > .process-group-canvas` 逐层 flex:1），并覆盖 `.gf-anchor` 中间层；uc 的 start 视图同步删 `index`

## 2026-09-17 — 过程地图卡片视图保留全部顶层根工作区 #15965（修复 delta 复审）
- **Scope**: 5 files, gf-biz（Management/Req/测试）+ gf-sdk（枚举注释）+ web 注释（未提交工作区 delta）
- **Atoms**: clean-code, architecture, domain-driven-design(规则13), secure-coding(暴露面), test-quality
- **Result**: C:0 H:0 M:0 L:2 I:3 | pass-with-notes
- **Key findings**: 两组 PROCESS_MAP 用例在行集不再依赖元模型值/版本类型后断言行为重复、用例名声称的场景已无桩支撑（KeepsModuleVersionWorkspace / KeepsWorkspaceWhenWorkingIsModule）；`.lattice/context/process-group-model.md` 查询契约小节仍写「只保留顶层过程工作区」待同步；loadGraph 现覆盖参考模型/模块工作版本的根工作区（try-catch 兜底，前端四档降级）
- **Strengths**: 删除逐工作区 getMetaModelInfoById 回退（N+1），PROCESS_MAP 版本表查询 2 次→1 次；hasDiscoveryPrivilege 过滤在树构建上游不变，行集放宽无越权暴露；测试命名遵循 test{Method}_{Scenario} 且 mock 边界与既有套件一致

## 2026-09-21 — 回收站统计性能优化 #15984（可选合并统计 + 3 线程池，提交 7b1b1a91a0 + 7d9c3b4d59）
- **Scope**: 6 files, infrastructure/sdk（IAdminSession/PreDefinedFolderEnum）+ infrastructure/sdkImpl（QueryService/AdminSession/QueryAction/测试）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:0 H:2 M:4 L:5 | pass-with-notes
- **Key findings**: 专用池 `Executors.newFixedThreadPool` 为无界队列 + 默认拒绝策略，与项目池约定（ThreadPoolConfig 有界队列 + CallerRunsPolicy）不一致；`applyRootOnlyCondition` 中 `1000000004L` 魔法值（随方法重写搬运）；TTL 按引用透传 `DISCOVERY_SQL`（HashMap）致同请求并行 worker 可能并发写；`AdminSession#countObjectsByClasses` 与 `awaitAll` 错误路径零测试
- **Strengths**: 复用每分类自己的框架 SQL 再 UNION ALL（条件与单分类逐字同源），真实库三路对拍证明 27 分类口径一致且 848ms→169ms；不往 DynamicListReq 加状态，用局部副本传参；新增 classId 全部走 AirConstants

## 2026-09-23 — Bug 16129 过程删除接口校验排除 linked 绑定（修复 delta 复审）
- **Scope**: 3 files, gf-sdkImpl（ProcessDeletionRepository + ProcessDeletionServiceImpl Javadoc + 新测试类）；另 .lattice/context 1 行决策
- **Atoms**: clean-code、architecture（叠加常开）；domain-driven-design（sdkimpl 触发）、secure-coding（SQL 拼接触发）、test-quality（新增测试）
- **Result**: C:0 H:1 M:1 L:3 I:4 | pass-with-notes
- **Key findings**: ① 同类校验 `hasVersionInterface` 未同步排除 linked（版本删除 20001141 仍会被过程组绑定阻塞，同一根因的兄弟路径）；② 测试断言段含 `for` 循环（AAA / 条件逻辑反模式）；③ Javadoc 错别字「本投验 / 不阻塄」，且「匹配面与校验对称」为过度声明（DELETE 未带 `tab_id`）；④ `ProcessInterfaceQueryServiceImpl`（接口列表/计数）不排除 linked，与本次「绑定≠接口」裁定口径不一致（待产品裁定）
- **Strengths**: SQL 值全参数化（字符串插值仅内部注册表类型常量）、常量走 `GateflowConstants`/`AirConstants`、分层与事务边界零违规、删除面按「过程 ID + 工作区」兜底 `related_process_id IS NULL` 脏行、测试抽共享打桩且命名合 `test{Method}_{Scenario}`、test35 事务回滚预演证明影响面精确
- **Follow-up (fixed same day)**: H① hasVersionInterface 已同修（同加 `IS DISTINCT FROM 'linked'`，RED→GREEN 单测，DB 预演计数=0；版本清理链 severRelationshipData 原生双侧覆盖，无需补清理）；M② 断言段 for 循环抽为 `indexOfSqlContaining` helper；L③ 错别字与「匹配面对称」过度声明已改写；L④ 测试 1/2 无用 lenient 桩已删；版本删除服务 Javadoc 口径同步。剩余 I 级（表名常量、谓词抽取、接口列表口径）留待后续

## 2026-09-24 — 过程树/版本比对树根节点与分类文件夹名称国际化（元模型 i18n）

- **Scope**: 18 files（gf-biz + gf-sdk + gf-sdkImpl + web/frontend），未提交工作区变更
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（review-standards overlay；secure-coding 未触发）
- **Result**: 0 critical, 0 high, 1 medium, 2 low, 2 info | pass-with-notes
- **Key findings**: `resolveCategoryTypeLabel`/`resolveProcessTreeRootName` 在 biz 与 sdkImpl 逐字重复（应收敛至 SDK 契约）；根名称 locale 未命中直接回退常量而非 zh_CN，与分类 label 回退语义不一致；树构建循环内逐节点重复 `getRootElements`（对照 learning「禁止循环内重复 getProcess」）
- **Strengths**: SDK 契约经 `default` 方法向后兼容扩展；构造器注入零 `@Autowired`；测试含「业务元素名称不被覆盖」守卫用例且用哨兵值防假阳性

## 2026-09-24 — 过程树/版本比对树命名国际化 复审（元模型 i18n + bug 16140）

- **Scope**: 21 files（gf biz/sdk/sdkImpl + resources + web/frontend），未提交工作区变更（同日复审，上一条为初审）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（overlay；secure-coding 未触发）
- **Result**: 0 critical, 0 high, 1 medium, 2 low, 3 info | pass-with-notes
- **Key findings**: M① `resolveCategoryTypeLabel` default 方法与既有 `MetamodelLabelSupport.typeLabel` 逻辑逐字重复（平行路径、一缓存一无缓存，应 Support 委托 default 方法收敛单源）；L② `PROCESS_TREE_ROOT_NAME` 常量与 zh_CN 属性双源（未支持 locale 回退时可能陈旧）；L③ 树循环内逐分类节点重复 `getRootElements`（内存级，量小可接受）
- **Strengths**: 初审「逻辑在 biz 与 sdkImpl 重复」已收敛为 SDK default 方法；构造器注入 + sdkImpl 零 `@Transactional`；测试用哨兵值防回退假阳性、含业务元素不被覆盖守卫与保留面回归
- **Follow-up (fixed same day)**: M① 已修——`MetamodelLabelSupport.typeLabel` 缓存 miss 分支委托 `IMetaModelService#resolveCategoryTypeLabel`，filter 链单源化（Support 仅存缓存与 Spring 定位）；gf-sdk 8/8、biz 33/33、sdkImpl 31/58+27/27 通过（`testScopes_ProcessGroupMetamodelAllowsProcessOnly` 为 HEAD 既有失败：process.json scopes `[Process,ProcessModule]` vs 测试期望 `[Process]`，与本次无关，待定 scopes 口径）；另 spotless 修了 IMetaModelService Javadoc 超长行

## 2026-09-24 — 过程树命名国际化 三审（收敛后验证，元模型 i18n + bug 16140）

- **Scope**: 23 files（gf biz/sdk/sdkImpl + mm 资源 + web/frontend + .lattice），未提交工作区变更（同日第三审）
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（overlay；secure-coding 未触发）
- **Result**: 0 critical, 0 high, 3 low, 2 info | pass-with-notes
- **Key findings**: L① 树循环内直调未缓存 `resolveCategoryTypeLabel`，未复用同语义带缓存的 `MetamodelLabelSupport.typeLabel`（biz 既定模式，两条平行调用路径应收敛）；L② `PROCESS_TREE_ROOT_NAME` 与 6 properties 双源且 locale 未命中直落中文常量（异于 `getLocalizedDescription` 三级回退）；L③ process.json 合并行格式噪声
- **Strengths**: 全景图→体系地图改名 10 处零遗漏（grep 验证）；bug 16140 死配置删除 + 三例守卫测试 RED→GREEN；testScopes 更名顺带修复 HEAD 既有失败；M① 收敛（Support 委托 default）验证到位

## 2026-09-28 — 计划移动到目标节点（ScheduleMove）

- **Scope**: 13 files（sdk + sdkImpl + biz + test），移动接口 = 复用计划页签删除 + 新增
- **Atoms**: clean-code, architecture, secure-coding, test-quality
- **Result**: 1 critical, 5 warning, 8 suggestion
- **Key findings**: 挂载后置异步 `updateRootObjectInfo` 逃逸事务，环检测回滚会留下脏根信息（critical）；`doRemoveRows` 绕过 TABLE_DELETE 权限；`parentObject==0`/空 `statusType` 校验缺口；环检测仅覆盖单棵树内依赖；新错误码未登记 sys_tips
- **Strengths**: 分层与事务边界正确，复用删除/新增只传顶层节点，依赖处理扩展点解耦，Spotless + 12 单测通过

## 2026-10-10 — smarturl 解析下沉 Table#resolveSmartUrl（c7da96c0，cherry-pick 至 dev-3.8-qs）
- **Scope**: 19 文件，AirAPI/infrastructure biz-sdk-sdkImpl + 测试
- **Atoms**: clean-code、architecture、ddd、test-quality
- **Result**: 0 critical，1 warning（apiId 兜底不可达，经确认 tabId 非空前提下降为备注），1 warning（调度语义与 resolveRegistryEntry 不一致），2 suggestion（冗余 import、null 加固）
- **Key findings**: apiIdRegistry 对 tabId 注册类不可达；fallback 测试 mock 了生产不可达状态；null/NumberFormatException 既有缺陷原样迁移
- **Strengths**: 消除 sdkImpl→biz 反向依赖、ATTR_OWNER 常量化、null/空语义文档化 + 全分支调度测试


## 2026-10-09 — GF 过程导航筛选与关键词搜索
- **Scope**: 2 files（前端枚举、WorkspacesHierarchy 未提交变更）
- **Atoms**: clean-code, architecture（仅适用项）；无新增学习沉淀
- **Result**: C:0 H:1 M:2 | 未通过
- **Key findings**: 下拉筛选未接入数据流；删除 treeSearchConfig 导致名称列漏搜子节点；顶部搜索未展开已折叠的命中路径。卡片是否搜索后代需明确产品口径。
- **Validation**: tsc、定向 ESLint 通过；直接提取当前过滤函数和 AirTable getFilterData，以两层树复现搜索问题；未做浏览器验收。

## 2026-10-09 — 任务1335 物料文档页签按钮放开 + 保存自动签出（TDD，dev-3.8-zcc）

- **Scope**: 17 files（infrastructure sdk 2 + sdkImpl 3 + biz 9 + 测试 7）：枚举映射 / ReleaseSupport / 自动签出守卫 + 9 保存入口接线
- **Atoms**: clean-code, architecture, domain-driven-design(overlay), secure-coding, test-quality（review-standards overlay 生效）
- **Result**: C:0 H:2 M:1 L:3 I:2 | pass-with-notes
- **Key findings**: ① TabBom 枚举映射直改泄漏到 TABLE_DRAW_STRUCTURE（Draw 签入态按钮亮但保存报未签出，用户批准直改 vs 范围红线，双解待裁定）；② uploadFileToTable/borrowFileToTable 自动签出先于权限校验且两方法无 @Transactional；③ 新测试硬编码 tabId/classId 字面量（§2 High，测试夹具口径缓解）
- **Strengths**: 零 biz→sdkImpl 依赖、sdk/sdkImpl 零新增 @Transactional；权限合并最严者胜有测试锁定；守卫 Context-first 只消费已加载对象、无 getDerivedXxx 捷径；生产代码零新增魔法值

## 2026-10-11 — Links 测试结果计算重构

- **Scope**: links sdk/biz/sdkImpl 的结果枚举、组合计算、step→case→run 回写、统计/快照及定向单测
- **Atoms**: clean-code, architecture, domain-driven-design, test-quality（overlay）
- **Result**: C:0 H:3（已修复） M:2（业务口径待确认） | pass-with-notes
- **Key findings**: Critical 短路只看当批 FAILED、漏历史值与 BLOCKED；未完成步骤被过滤后误聚合；step 允许提交计算态/空原子字段。已修复并加回归测试。仍待确认 case 复测是否允许结果降级、run 是否对未完成用例对称保持不计算。
- **Validation**: links-sdk 12/12、links-biz 65/65 定向单测通过；按要求未运行 `TestManagementAcceptanceTest`
- **Strengths**: 五值域与 NA/空值语义分离清晰；组合规则下沉为纯 SDK 支撑类；两两组合、折叠、统计与业务短路均有单测保护

## 2026-10-10 — 建模工作区树空间维度筛选（全部/只看根/我负责的/我参与的，dev-3.8-zt）

- **Scope**: 7 files（gf biz 3 + gf sdk 1 + links sdkImpl pom 1 + infrastructure sdk 1 + tests 2）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality（review-standards overlay）
- **Result**: C:0 H:0 M:0 L:2 I:1 | pass-with-notes
- **Key findings**: ① ModelingWorkspacesManagement.java:110 Javadoc 嵌套重复注释块；② collectMyInvolvedWorkspaceIds 未使用形参 tree；③ ModelingWorkspacesRestTest 建议使用枚举 getKey() 替代测试硬编码字面量
- **Strengths**: 树形剪枝逻辑严谨自洽（直接命中 isMatched=true，祖先容器节点 isMatched=false, canChecked=0）；内存过滤 ATTR_OWNER 严守规则 13 Context-first 零额外 DB 开销；分层与事务边界零违规；26 单元测试与 Spotless 全绿

## 2026-10-12 — 建模工作区树空间筛选与 PROCESS_MAP 规范化（dev-3.8-zt）

- **Scope**: 6 files（gf biz 1 + gf sdk 1 + links sdkImpl pom 1 + infrastructure sdk 1 + tests 2）
- **Atoms**: clean-code, architecture, domain-driven-design, secure-coding, test-quality（review-standards overlay）
- **Result**: C:0 H:0 M:0 L:0 I:0 | pass
- **Key findings**: ① 去除多余兼容逻辑，统一通过 dynamicParams.workspaceFilterId 对齐前端当前契约；② 移除 ModelingWorkspacesTreeReq 冗余字段与 DynamicParamConstants 冗余别名；③ 单测与 REST 测试全量对齐枚举常量
- **Strengths**: 契约单一真相、零冗余兼容代码；严守规则 13 Context-first 零多余查库；PROCESS_MAP 平铺保序；28 单元测试与 Spotless 全绿

## 2026-10-12 — TestRun 执行接口拆分与验收对齐

- **Scope**: 单步 judge/clear、RunCase complete、Run 自动/强制结束、专属步骤页签写校验及 REST 验收用例
- **Atoms**: code-forge、architecture、clean-code、secure-coding、test-quality
- **Key findings**: 不再单调上卷暂存步骤；完成集合独立于复制的历史结果；通用 dynamicTabUpdateBatch 不承载执行上下文，专属 Table.updateRows 负责文本写入校验。真实验收发现 Run.TestDate 元数据缺失，经用户明确要求跳过 Run.TestUser/TestDate，Step/RunCase 审计保持正常。
- **Validation**: 109 项定向单测通过；autotestdev37 最终验收 31 项、0 失败/错误、1 项原有禁用；包含真实通用页签文本保存、clear 保留文本、部分判定完成、无步骤单选完成及自动/强制结束。直接核验 Surefire 报告，不依赖父 POM 的 testFailureIgnore 构建结果。

## 2026-10-16 — 测试集 Bugs 框架视图审查
- **Scope**: 7 files（links biz/sdkImpl、视图迁移、定向测试、精简方案）；对照使用位置 Table/视图机制
- **Atoms**: knowledge-priming, learning-harvest, collaborative-judgment, clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:0 H:0 M:0 L:0 | pass-after-fix（发现并修复 1 项漏数）
- **Key findings**: TestSet 视图错误按 Association flags 过滤；普通 Association flags 可为空，移除该过滤，与平台 Associations 查询一致
- **Validation**: 6 项单测通过；完整迁移及空 flags、双向关联、去重、解除关联的 PostgreSQL 事务断言通过，全部回滚；未进行新实现端到端页面验证
- **Strengths**: View + 页签元数据 + Table 接入正确；筛选、排序、分页复用通用查询，无 TestSet-Bug 独立关系

## 2026-10-20 — 测试管理应用编排下沉审查
- **Scope**: links biz/sdk/sdkImpl 当前暂存与未暂存重构及直接调用链；只出报告，未修改业务代码
- **Atoms**: knowledge-priming, learning-harvest, collaborative-judgment, clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: C:0 H:3 M:2 L:1 I:2 | changes-requested（含条件性风险，详见报告）
- **Key findings**: judge/clear 遗漏 AssignedTo；RunCase/RunStep 直接保存守卫缺口；完成后新增 Bug 引用与 Run 冻结策略冲突；start 重复读取及 O(n²) 扫描
- **Validation**: SDKImpl 定向 85 项通过；业务定向 71 项中 69 通过、非受让人 judge/clear 两项失败。审查期间测试迁包/旧方法引用已修复，不再列为当前编译阻断；未跑数据库验收
- **Strengths**: 快照批量读与富文本解析下沉合理，生产事务仍在 Biz；报告 backend/docs/testmanagement-orchestration-review-20261020.md；经验仅提出，未自动写入工程规范

## 2026-10-20 — 编排下沉审查更正与第 1、2 项验证
- **Scope**: SDK 执行快照契约、judge/clear 权限回归、Bug 引用业务边界；原审查保留历史，本条更正其结论
- **Correction**: Run 完成后可随时新增/引用关联 Bug；Reference 写入引用表，不属于 Run 普通实体字段编辑，不受执行态 guard 限制。撤回原 High“Bug 引用与冻结冲突”及要求专门执行授权放行的建议
- **Result**: 原审查 H:3 更正为 H:2；其中 judge/clear 问题已验证修复，剩余为执行属性直接保存的条件性风险，不包含 BugReferences
- **Validation**: 第 1、2 项定向 117 项单测全部通过，Java 定向 Spotless 通过；本次 Bug 口径更正仅更新记录和设计文档，未修改关系写入或 guard 实现，未进行数据库关联验收
- **Boundary**: 仍保留普通执行字段冻结及平台已有认证/引用合法性/权限规则；框架 setValues 会先调用 preUpdate，不能仅凭最终落引用表就声称已验证绕过 guard
## 2026-10-20 — 展示测试运行用例关联缺陷追溯信息
- **Scope**: 7 files（links sdk/sdkImpl/biz + tests + blueprint）
- **Atoms**: knowledge-priming, learning-harvest, collaborative-judgment, clean-code, architecture, domain-driven-design, secure-coding, test-quality
- **Result**: 0 critical, 2 warning, 1 suggestion | pass-with-notes
- **Key findings**: 追溯服务的 Association 类型过滤比现有 Bugs 视图更宽，且未复用视图的源用例有效性过滤；同优先级重复关系的审计信息选择不稳定
- **Strengths**: Biz→SDK→SDKImpl 分层正确，批量关系读取避免 N+1，字段/对象权限过滤和异常传播有测试覆盖
- **Warning fixes**: 查询按现有 Bugs 视图过滤有效执行快照、来源用例及 Association 类型；重复关系按来源优先级、创建时间、关系 ID 确定审计信息，空值排最后，两项 warning 已关闭
- **Validation**: TestRunBugTraceabilityServiceImplTest 10 项通过（Failures: 0, Errors: 0）；Spotless、git diff --check 通过，未执行数据库或页面端到端验证

## 2026-10-28 | TestResults/420606 四属性同步后审查

- **Scope**: TestSteps 普通长文本列，以及 Overview.Revision/Description/Number 三项参考属性的投影、原文下发、挂载顺序、分页和只读合同。
- **Result**: pass；未发现新增业务实现缺陷。保留通用普通属性读取与轻量参考列投影，不引入 JSON 解析或响应二次拼装；补齐四列元数据测试，并清理合并后恢复的已删除 TestStep 常量测试引用。
- **Validation**: 36 tests，0 failures / 0 errors / 0 skipped。包含两项真实 REST/DataObject/PostgreSQL 验收：超过 1000 字符 JSON 原文下发、多步骤/零步骤各一行、挂载顺序、分页 total、只读单元格，以及源版次 2 与执行版次 1 的区分。Spotless 检查通过。
- **Closure**: 此前 420606 缺少 TestSteps 列的验收待办已关闭；本轮未修改 SQL、平台元数据或前端。

## 2026-10-28 | 测试步骤属性化后端复审

- **Scope**: links Biz/SDK/SDKImpl 的通用属性保存、DataObject、生成/重建、judge/complete、TestResults 视图与验收。
- **Result**: C:0，已修复运行写 Map 不可变、运行 JSON 保存缺少校验、重建 Owner 误取受让维度；结果页签列配置仍待元数据同步，不能判定全量验收通过。
- **Validation**: 355 项，354 passed / 1 failed / 0 errors / 0 skipped；325 项单元编排和 29 项真实 REST/DataObject/PostgreSQL 验收通过。唯一失败明确指向 420606 缺少 TestSteps 列。Spotless 与本轮代码/文档 diff 检查通过。
- **View**: 按用户明确要求，仅重建 local_dev38.public.air_inst_view_testruncase_tab_details，保留所有者与授权、不使用 CASCADE；409 唯一挂载关系对应 409 行，JSON 原值差异 0。
- **Report**: backend/specs/links/test-management/test-step-attribute-review.md；视图/元数据合同见同目录 test-results-json-view-contract.md。
