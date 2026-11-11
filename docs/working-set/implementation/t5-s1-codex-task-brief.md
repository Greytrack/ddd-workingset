# T5-S1 Codex 任务说明：工程骨架与派生/Query

**状态：Ready for implementation（实施任务说明；不含生产代码）**

- 交付对象：Codex（实施）
- 日期：2026-11-11
- 上级依据：[T5-S0 最小纵向切片设计](t5-s0-minimal-slice-design.md)（P1～P5、A1～A3、§4 目录与依赖、§11 接口清单）、[场景-期望-测试映射](t5-s0-scenario-test-mapping.md)（AT-01/AT-02、AC-01/AC-02）、[S1～S4 任务清单](t5-s0-codex-task-plan.md)
- 前置状态：**P1～P5 已统一、A1～A3 已确认**；本机 JDK 21.0.7、Maven 3.9.11 已安装

## 1. 目标

建立最小可运行的工程骨架，完成**非共享派生 → Source-backed Query** 的纵向链路，使 AT-01、AT-02 通过，并通过 AC-01（编译依赖）与 AC-02 的派生部分检查。

**不实现**：事件、快照、COW 物化、Local-backed 切换（属 S2～S4）。

## 2. 允许修改的范围（白名单）

```
pom.xml
src/main/java/sk/workingset/domain/**
src/main/java/sk/workingset/application/**
src/main/java/sk/workingset/infrastructure/**
src/main/java/alm/requirement/domain/**
src/main/java/alm/requirement/application/**
src/main/java/alm/requirement/infrastructure/**
src/main/java/bootstrap/configuration/**
src/test/java/**（同构包）
```

**禁止**：多 Maven 模块、数据库、消息中间件、微服务/服务发现、动态插件系统、`sk → alm` 的编译依赖、旧 `biz/sdk/sdkImpl` 结构。

## 3. 执行步骤（Red → Green，逐条）

| # | 行为 | 最小产出 |
| --- | --- | --- |
| 1 | 工程骨架 | `pom.xml`（Java 21、JUnit 5、Spring Boot 仅用于装配/测试）、包结构、`mvn -q -DskipTests compile` 通过 |
| 2 | 值对象与领域接口 | `LocalId`/`CommonId`/`Revision`/`Version`/`LockVersion`/`TrackerId`/`WorkingSetId`；`TrackerItem`（`commonId()`/`localId()`/`revision()`）；`Version` 为发布大版本（字符串、可空），`LockVersion` 为乐观锁 |
| 3 | `LightTrackerItem` 聚合 | 派生构造：预分配 `localId`、继承 `commonId`、`sourceLocalId`=内容拥有者、`sourceRevision`、`materialized=false`；只读视图；L1/L3 后置条件 |
| 4 | `Tracker`（最小） | 非共享派生：新身份 + `sourceId` 指向来源 |
| 5 | SK Port 与仓库 | `TrackerItemReadPort`（`readAt`/`revision`/`capabilities`/`authorize`）、`LightTrackerItemRepository`（`save`/`findById`/`findBySource`），内存实现 |
| 6 | SK 应用服务 | `WorkingSetApplicationService.derive` / `resolve`（`NotFound`/`NotIncluded`） |
| 7 | ALM 最小实现 | `Requirement`（P5 字段，实现 `TrackerItem`）、内存 `RequirementRepository`、`RequirementApplicationService.create`、`RequirementAdapter implements TrackerItemReadPort`；**以 `LocalId` 作为唯一标识（`RequirementId = LocalId`，不另建独立 ID）** |
| 8 | `read` 编排 | `materialized=false` 时沿 `sourceLocalId` 调 `readAt(sourceRevision)`；返回内容 + 实际 revision |
| 9 | bootstrap 装配 | Spring `@Configuration`/`@Bean` 装配 SK 与 ALM 实现（组合根是唯一同时引用两侧的位置） |
| 10 | 测试 | AT-01、AT-02；`resolve` 两个失败分类；AC-01 ArchUnit；AC-02 派生部分 |

## 4. 测试与验收

**命令**：`mvn test`（或 `mvn verify`）。

| 测试 | 断言要点 |
| --- | --- |
| AT-01 | 派生后 `localId` 新且稳定、`commonId` 继承、`sourceLocalId ≠ localId`、`materialized=false`、`Tracker.sourceId` 指向来源、Requirement 无新增内容记录 |
| AT-02 | `read` 返回来源 revision 3 的内容（`title=A`），`materialized` 仍为 false，未新建业务对象 |
| resolve 失败 | 未命中 → `NotFound`；存在但未纳入 → `NotIncluded`（两者不合并） |
| AC-01 | ArchUnit：`noClasses().that().resideInAPackage("sk.workingset..").should().dependOnClassesThat().resideInAPackage("alm.requirement..")` 通过 |
| AC-02（派生） | 同一 `localId` 可用于 `findById` 与 `findBySource` 定位；`commonId` 前后一致 |

## 5. 完成定义（DoD）

1. `mvn test` 全绿（含上述 5 类测试）；
2. AC-01 规则通过，且主代码中 `sk.*` 无 `alm.*` 引用；
3. 主代码无事件/快照/COW/物化切换代码；
4. 无数据库、无消息中间件、无多模块；
5. 在 `implementation/` 追加简短「S1 验证结果」记录（测试命令、通过数、对应 AT/AC），并回链本说明。

## 6. 排除范围

事件与快照、COW 物化、Local-backed 切换、Merge、绑定/删除、权限、版本治理、生产持久化、性能与并发压测。

## 7. 遇到设计缺口时

- 若发现 P5 字段不足以表达 AT-01/AT-02（例如缺少定位所需身份），**停止编码**，在 `implementation/t5-s0-minimal-slice-design.md` 或新评审记录中标注 `Proposed`，等待裁定；
- 不得在代码中暗补新的领域概念、状态或通用框架；
- 不得修改 T1～T4 已冻结契约。
