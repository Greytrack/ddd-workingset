# reference（引用行公共域）— model.md

> 模块归属：`shared-kernel`（**公共域，用户裁定 2026-11-19：引用行落 shared-kernel**——引用可跨业务对象/业务线挂出， 与 `PropagationSuspect` / `RelationRowId` 同族落公共层）。设计稿 SSOT：`9/10/11/12 号稿`（`.lattice/ddd/contexts/requirement/domain_models/`）， 冲突处以 context 文档裁决表为准。

## 1. 职责

跨业务对象的**引用行公共域**：一条引用＝一个独立聚合根（行即聚合），承载「引用方（下游）＋ 属性 ＋ 目标（上游）＋ 目标版次 ＋ 传播怀疑 ＋ 版次记录」； 写模型经 shared-kernel 仓储实现落 v3 现表（`air_inst_tab_reference`，见 `infrastructure/reference/`），公共判定与形态守卫在本域。

## 2. 元素

| 元素 | 形态 | 说明 |
|------|------|------|
| `Reference` | 聚合根 ★ | 四元组身份诞生即定形（无 setTarget）；实现 `PropagationSuspectHolder`；构造即记本版次「新增」记录；`markUnchanged` 幂等；`hasEffectiveRecord` 整行删除判据 |
| `TargetRevision` / `TargetRevisionMode` | 值对象/枚举 | 目标版次两态（followLatest / pinnedTo），不用 null 兼职 |
| `ReferenceType` | 枚举 | 引用类型（DIRECT/RELATIVE 由调用方按目标 Tracker 共享性装配；DANGLE/VERSION 挂账） |
| `ReferenceRevisionRecord` | 值对象 | 版次 ＋ 三态（ADDED/UNCHANGED/INVALIDATED），自然键=版次每版次至多一条 |
| `ReferenceRepository` | 父仓储端口 | 继承 `PropagationSuspectHolderRepository`；实现上提 `infrastructure/reference/ReferenceRepositoryImpl`（Spring JDBC 适配 `air_inst_tab_reference`，跨 BC 复用） |
| `ReferenceGuardDomainService` | 领域服务 | 纯判定：必须签出（口径 15）、≤200 行（口径 14）、判重（裁决 5：键=targetId）；无 IO，Facts 预取 |
| `ReferenceGuardQuery` | 应用读端口（`application/query`） | 守卫事实预取：编辑事实（经 `ReferenceEditorFacts` 回注）＋ 有效行 ＋ 目标存在性；实现 `infrastructure/reference/ReferenceGuardQueryImpl` |
| `ReferenceEditorFacts` | 应用读端口（`application/query`） | 引用方编辑事实（版次/签出态/删除位）依赖倒置回注点；消费方 BC 实现（如 v4-alm 读 `RequirementRepository`） |
| `ReferenceTypeResolver` | 应用读端口（`application/query`） | 引用类型装配（裁决 4：Tracker 共享→DIRECT）；实现 `infrastructure/reference/ReferenceTypeResolverImpl` |

## 3. 错误码

引用公共域错误码落 `SharedKernelResultCode`（300100033–300100042，`REFERENCE_*` 段）； 应用层专属码（如 `REFERENCE_TARGET_NOT_FOUND` 目标存在性）由消费方 BC 自持（v4-alm `RequirementResultCode`）。

## 4. 裁决（SSOT 见 context 文档 `reference-aggregate-suspect-propagation-refactor.md`）

| # | 裁决 | 落点 |
|---|------|------|
| 2 | mergeRevision 按 v3 保留（推翻口径 6 无基线） | `PropagationSuspect.upstream/downstreamMergeRevision`；创建基线 upstream=目标 revision（null→1）、downstream=宿主 revision |
| 3 | 传播类型落 `air_inst_tab_reference.propagation_type`（v3 列表项 key），命令携带行内快照 | `PropagationSuspect.propagationType` |
| 4 | referenceType 覆盖（DIRECT/RELATIVE 按 Tracker 共享性装配；DANGLE/VERSION 挂账） | `ReferenceType` |
| 5 | 判重（键=targetId） | `ReferenceGuardDomainService` |
| 7 | attributeApiId 缺省 "Reference1"（本轮不读 air_sys_node） | `Reference.DEFAULT_ATTRIBUTE_API_ID` |

## 5. 消费方

| 消费方 | 用途 |
|--------|------|
| `v4-alm` | 应用服务（新增/整值替换/删除）、`ReferenceEditorFacts` 实现（读需求聚合）、签出事件驱动版次记录 |
| 其他 BC | 同一引用行公共域复用（跨线挂出）；仓储/守卫/类型装配实现已上提 shared-kernel 直接复用，仅需自实现 `ReferenceEditorFacts` |

## 6. 约束

- 领域层零框架依赖（无 Spring 注解）；仅依赖 `lib-exception` / `lib-id` / 本 kernel 类型。
- 版次模型映射（v4 记录集 ↔ v3 行产物）在 `infrastructure/reference/ReferenceMapper` 内完成（shared-kernel SSOT），领域层不见列名。
- 端语义固定：`sourceId`=引用方=下游、`targetId`=被引用=上游；方向判定在值对象内部。

## 7. 待办

1. 第二消费方（PLM 物料引用）接入时验证 `ReferenceEditorFacts` 回注面与宿主冗余列填充口径（公共基础设施已上提 shared-kernel，2026-11-21 用户裁定）。
2. DANGLE/VERSION 装配规则随工作集轮；node 属性配置随 node 轮（届时 "Reference1" 缺省由配置替换）。
