# T5-S3 首次 COW 实施记录

**状态：Historical（已实施的任务说明）**。实际验收见 [S3 验证结果](t5-s3-verification-result.md)，当前恢复及边界见 [S4 验证结果](t5-s4-verification-result.md)。领域依据为 [T3](../domain/lighttrackeritem-materialization-contract-v1.md)和 [T4](../domain/cross-bc-contract-v1.md)。

## 1. S3 范围

ALM Requirement 普通字段的首次异步物化，覆盖 AT-03、AT-04 与 AT-05 的失败前置。T3 §2.1 定义附件与关系的固定规则。

## 3.1 事件证据

`RequirementChanged` 携带身份、快照引用、`beforeRevision` / `afterRevision` 和 `beforeLockVersion` / `afterLockVersion`。目标查找及快照校验使用来源修改前证据。目标以自身 Revision 物化，继承快照的发布大版本，乐观锁从零开始。

## 3.6 提交与消费

来源业务修改、不可变快照和 Modulith 发布记录同事务提交。Modulith 在提交后异步调用监听器。消费者按来源串行，消费时定位未物化目标，经独立写 Adapter 校验快照和提交业务内容，再独立提交 LightTrackerItem 切换。读 Adapter 继续按 LocalId 定位业务内容。

业务保存或切换失败按目标记录，扇出结束后失败事件保持未完成。切换失败保留已提交业务内容，Redis 在状态事务提交后更新。历史事件改版前本地查询结果为零，未执行删除。原进程内 Dispatcher、内存仓储方案已退出。

## 5. 验收

可执行场景见 [AT-03～AT-05 映射](t5-s0-scenario-test-mapping.md)。测试结果、故障注入和 JDBC／Mockito 迁移证据统一见 [S3 验证结果](t5-s3-verification-result.md)。
