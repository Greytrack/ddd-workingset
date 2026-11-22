# DDD 反模式扫描与事务约束修正

**状态：Reviewed（TC-002 限定裁定为 Confirmed；整改方案细节仍 Draft）**

## 依据和范围

用户于 2026-11-19
明确要求“跨聚合一定要用事件交互，不能跨聚合同步事务”。本轮读取本地 [domain-driven-design Skill](../../../.agents/skills/domain-driven-design/SKILL.md)
与其 [defaults](../../../.agents/skills/domain-driven-design/references/defaults.md)；仓库无 `.lattice/config.yaml`
，按默认规则核对。该 Skill 只约束战术 DDD，不以它重新定义四个 BC 的战略关系。

权威规则与六项 Active Anti-Pattern Scan 持久化在 [AGENTS §7.2/7.4](../../../AGENTS.md)，不另建 AGENT.md
以免出现双重规范。相关入口为 [聚合模型](../domain/aggregate-model.md)、[基线](../BASELINE.md)。

## 扫描结论

| 反模式                            | 文档证据与处理                                                                                                                   | 剩余验证                               |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------|----------------------------------------|
| Cross-Aggregate Transaction       | AGENTS 原可选措辞、T2 创建 Tracker+绑定同事务、T4 直接编辑业务内容+LTI 同事务、T3 同库选项已修正；SCN 产品空间初始化已改最终一致 | 现有实现和验收仍需整改，不能判通过     |
| God Aggregate                     | 聚合模型明确 WorkingSet、Tracker、LTI、ProductSpace、MergeRound、Checkpoint 各自边界；T7 批量事务不因批处理成为单聚合            | 规模、装载与热点需实测，不擅改聚合边界 |
| Anemic Domain Model               | AGENTS 增加行为与不变量归属检查                                                                                                  | 未逐类扫描本轮已有代码，未验证         |
| Primitive Obsession               | AGENTS 增加类型混淆/校验驱动的值对象检查                                                                                         | 未逐字段扫描，未验证                   |
| Leaking Domain Logic              | 保留 BC 业务规则所有权，增加应用编排与纯领域计算边界检查                                                                         | 未逐服务扫描，未验证                   |
| Misidentified Entity/Value Object | 增加持续身份、生命周期、值相等判断                                                                                               | 未逐类型扫描，未验证                   |

## 当时的待整改项

当前进展与剩余风险见 [实现边界](../implementation/current-boundaries.md)；以下保留原评审问题，不代表现状全部仍未实施。

1. ProductSpace 初始化与 Tracker 创建/绑定：拆为可靠事件链；每个消费者写一个聚合。需要定义处理中/部分失败、重复投递、绑定拒绝与恢复的公开语义。T2
   和 SCN 对应内容已标明待完善。
2. 未物化业务对象直接编辑：BC 独立保存后以事件推进 LTI；补充保存成功但切换延迟/失败的读取与响应契约。不能再承诺同事务立即可读。
3. T7 批量索引及任务进度：LTI 与 LRO 分事务；以结果事件推进游标/进度。验证重复、乱序、消费者中断与结果不丢失；保持既有业务失败终态及不补偿政策。
4. 全部写入口后续检查事务传播、同步事件监听、批量仓储及嵌套应用服务调用。进程内发布事件不能证明已隔离事务；同库也不能例外。公共行为测试应证明源成功后目标可暂未完成、消费失败不回滚源、重投不重复副作用。

历史 reviews 和 Accepted ADR 不静默重写；旧记录中的可选事务意见按本次裁定解释。没有据此批准任何新聚合模型或发布协议。仓库已有大量暂存/未暂存
Java 改动，本轮均保留，扫描范围为设计文档而非完整实现审计。
