# 当前设计一致性修正

**状态：Reviewed（两项裁定由用户于 2026-11-11 明确确认，已同步文档；实现与图表渲染待验证）**

日期：2026-11-11。范围：当前 Unreleased v1 的文档一致性；不新增实施或发布授权。

## 已有依据可直接修正

| 问题 | 裁定依据 | 修正位置 |
| --- | --- | --- |
| 业务 Revision 被用作乐观锁、每次保存返回新 Revision | R12、CONCEPT-009、ADR-0005 双侧内容证据要求 | [T4](../domain/cross-bc-contract-v1.md) §3.2；区分业务版次和并发证据，不改变 T1 决策优先政策 |
| 绑定后禁止移除的旧主线 | POL-014、T2 K1～K3、INV-064～068 | [模型基线](../domain/model-baseline-candidate-v1.md) §4、[主线图](../diagrams/domain-mainline.md)、[事实政策基线](../domain/facts-policy-baseline-v1.md) R01 与范围说明；ADR-0004 添加后续澄清并保留历史 |
| T4 创建/扩展摘要遗漏 Preparing | T4 §5.2、T4 评审 §3、配套图矩阵一致 | §4 改引用 §5.2，不再维护第二套适用列表 |
| removeSpace 与移除最后一个 Tracker 混淆 | T2 SB-3 与 removeSpace 后置条件 | [T2](../domain/binding-deletion-contract-v1.md) §4.3 引用完整性说明 |
| 阶段 4 暂缓事项仍被呈现为当前未定 | ADR-0004、T1～T3 已确认范围 | 模型基线 §7、§9、§10 回链后续契约 |

## 用户裁定与替代关系

1. **派生内容固定边界——选择 A**。用户在本聊天明确回复“A”：普通字段沿用 D-016/D-018；附件、关系保持派生时状态。原 R07/FACT-006 的全部内容派生时固定表述被限定；R19 保留，D-016 不冻结普通字段保留。其他方案为全部首次 COW 固定（未选，会取消附件关系的派生基准）或全部派生时固定（未选，会改变当前来源读取）。权威定义见 [T3 §2.1](../domain/lighttrackeritem-materialization-contract-v1.md)，SCN-072 验证。既有数据若曾采用混合解释，须核查固定事实；本仓库无实现证据，不能宣称无需数据迁移。
2. **TrackerItem 实现责任——选择 B**。用户明确回复“B：聚合接口与 Adapter 同时保留”：保留 D-007/ADR-0002 的业务聚合实现 TrackerItem；Adapter 另行实现应用层 Port。替代方案 A（仅 Adapter、聚合无需接口）未选；B 的代价是需保持领域接口与应用 Port 职责分离，但保留既定公共领域契约。定义见 [T4 §3.1](../domain/cross-bc-contract-v1.md)，SCN-073 验证。不替代 ADR-0002，不引入 ServiceLoader、通用 Save 或新聚合。

首次快照优先及消费时确定目标已有 D-017/D-018、ADR-0003 依据；事件顺序、可靠投递与幂等仍需实现证据，不作为新增业务裁定。

## 验证范围

本轮检查文档差异、链接目标及相关措辞；未执行实现测试。主线图仅调整节点文字，未进行 Mermaid 实际渲染。
