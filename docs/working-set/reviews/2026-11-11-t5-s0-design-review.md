# T5-S0 设计审核记录

**状态：Reviewed（2026-11-11）；结论：P1～P5 已统一、A1～A3 已确认，S1 具备立即启动条件。**

审核对象：

- [T5-S0 最小纵向切片设计](../implementation/t5-s0-minimal-slice-design.md)（Draft）
- [T5-S0 架构与首次 COW 时序](../diagrams/t5-s0-architecture.md)（Draft / Candidate）
- [AT-01～AT-05、AC-01～AC-02 场景-期望-测试映射](../implementation/t5-s0-scenario-test-mapping.md)（Draft）
- [S1～S4 Codex 实施任务清单](../implementation/t5-s0-codex-task-plan.md)（Draft）
- [S1 任务说明（交付 Codex）](../implementation/t5-s1-codex-task-brief.md)（Ready for implementation）

审核方式：对照 T1～T4 已冻结契约、INV-069～071、SCN-066～068/072/073 与仓库实际条件（JDK 21.0.7、Maven 3.9.11、`src/` 为空）逐项核对；**未做**实现验证与 Mermaid 实际渲染。

## 1. 审核结果

| 检查项 | 结果 |
| --- | --- |
| 首切片范围与 T5 约定一致（仅 ALM、不含 Merge/删除/权限/版本治理） | 通过 |
| 依赖方向：SK 只依赖两个应用层 Port，ALM Adapter 实现，SK 不引用 ALM 聚合 | 通过（设计 §4/§5、图 §1/§2） |
| 两层接口并存（T4 §3.1 选择 B） | 通过：`Requirement` 实现 `TrackerItem`；`RequirementAdapter` 实现应用层 Port |
| 业务 Revision 不当乐观锁（T4 §3.2） | 通过：P5 引入 `lock_version`；写入以 `lock_version` 作并发前置；`version` 为发布大版本（可空） |
| 四项能力（derive/resolve/read/materialize）与契约对应 | 通过 |
| LightTrackerItem 不变量 L1～L4 与 T3 一致 | 通过（materialized 三字段同步、身份不可变） |
| 内容固定范围（T3 §2.1 选择 A） | 通过：首切片仅普通字段；附件/关系不被较新事件覆盖 |
| 快照生产/消费关系（D-011/D-017） | 通过（消费时定位、不读来源当前值） |
| 保存与切换顺序、A3 三类情形 | 通过（保存失败不切换；切换失败只补切换；不回滚；不覆盖后续合法修改） |
| AT-01～AT-05 覆盖与可观察断言 | 通过（每场景含 Given/When/Then/断言/反例） |
| AC-01（编译依赖）、AC-02（身份稳定）已定义 | 通过（ArchUnit 规则 + 运行时断言，含反例） |
| 任务可独立完成与验证（S1～S4） | 通过（每任务含范围、输入依赖、测试、完成定义、排除范围） |
| S1 任务说明可直接交付 Codex | 通过（白名单范围、10 步 Red→Green、5 类测试、DoD、禁止项） |
| 不改 T1～T4 已冻结契约 | 通过（未修改任何契约文件） |
| 不新增领域聚合、不写生产代码 | 通过（`src/` 保持为空） |
| 不引用旧 biz/sdk/sdkImpl 结构 | 通过（明确「彻底退出新架构设计依据」） |
| Mermaid 渲染 | **未验证**（无 `mmdc`）；静态检查通过（图 4 块、fence 配对） |

## 2. 工程选型统一结果（P1～P5）

| 编号 | 决定 | 依据/状态 |
| --- | --- | --- |
| P1 | **Maven + Java 21**（部署仅支持 17 时回退） | **已确定**：本机 JDK 21.0.7 LTS、Maven 3.9.11、`.idea` `languageLevel=JDK_21`；**部署/CI 环境未验证** |
| P2 | Spring Boot + 纯 Java Domain | 采用（Spring 仅装配/测试） |
| P3 | 单 Maven 模块 + BC/DDD 分包 | 采用；不预建多模块；旧模块结构退出 |
| P4 | 公共事件信封 + BC 自有不可变快照 | 采用（概念级；序列化/投递留实施） |
| P5 | `localId`/`commonId`/`trackerId`/`title`/`description`/`revision`/`version`/`lock_version` | 采用（首切片范围）；`version` 发布大版本可空、`revision` 手动版次、`lock_version` 乐观锁 |

## 3. 架构前置约束落实（A1～A3）

| 编号 | 要求 | 落实 |
| --- | --- | --- |
| A1 | SK 编译期不依赖 BC 实现；SK 定义 Outbound Port；BC Adapter 实现；Bootstrap 唯一组合 | 设计 §4 依赖规则 + AC-01 ArchUnit 规则 + 图 §1（不画 `sk → alm`） |
| A2 | LocalId 稳定定位；不依赖可变内容指针；materialized 三字段同步 | 设计 §7 不变量 + AC-02 检查 + S1 派生部分/S3 物化部分 |
| A3 | 最终收敛；区分部分提交/补偿方向；COW 不回滚、不覆盖后续修改；Merge 保留既有语义 | 设计 §9 + S1～S4 失败分支 + S4 恢复任务 |

## 4. 工程风险

| 编号 | 风险 | 状态 |
| --- | --- | --- |
| R1 | 仓库无构建系统（`src/` 空、无 `pom.xml`、0 Java 文件） | 已确认；S1 第一步建骨架 |
| R2 | 部署/CI 环境未验证（仅本地 JDK 21 + Maven 证据） | 未验证；CI 接入时复核 P1 |
| R3 | 快照序列化/可靠投递 | 概念级已定（P4），实现留专项 |
| R4 | `RequirementId` 与 `LocalId` 映射 | BC 自决（A2 第 5 条）；S1 暂以 1:1 实现 |
| R5 | Mermaid 渲染 | 发布前复核 |

## 5. 结论

- S0 交付物齐备：设计（含 P1～P5、A1～A3、目录/依赖、接口清单、冲突检查）、架构与 COW 时序图、AT/AC 映射、S1～S4 任务清单、S1 任务说明、本审核记录。
- **P1～P5 已统一、A1～A3 已确认；S1 具备立即启动条件**，可直接使用 [S1 任务说明](../implementation/t5-s1-codex-task-brief.md) 交付 Codex。
- 唯一未验证项是部署/CI 环境的 JDK 版本（不阻塞本地 S1 实施）。
- 未做：生产代码、基线发布、ADR 批准、Git Commit/Tag。
