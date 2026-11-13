# T6-B Merge 并发与恢复验证

**状态：Reviewed（2026-11-13，实施证据）**

依据 [T1 §8.1](../domain/merge-execution-contract-v1.md#81-执行中断与结果恢复t6-b)、[ADR-0005 Q4](../adr/0005-merge-execution-model.md#2026-11-11-补充裁定轮次一次执行即终态q4) 和 [SCN-074](../scenarios/core-scenarios.md#scn-074)。

## 恢复入口

- `GET /api/merge-rounds/{id}` 查询 `Running` 或终态。若 Apply 返回 409 `RoundInProgress`，由调用者稍后查询进度或重提同一轮次。
- `POST /api/merge-rounds/{id}/apply` 可在原执行者释放执行权后接续。返回 503 `UNKNOWN` 时，使用同一轮次 ID 查询或接续，避免创建新轮次重写已有业务提交。已终态的业务失败沿用 Q4。
- `POST /api/cow/recover/incomplete` 重投未完成的 `RequirementMergeApplied`。轮次终态依据业务提交，LTI 可独立收敛。

## 一致性与升级

[MergeExecutionService](../../../src/main/java/sk/workingset/application/MergeExecutionService.java) 使用 PostgreSQL 每轮次独占锁保护 Apply 入口，使用短事务中的轮次行锁序列化条目结果和提交凭据。相较于带超时的持久租约，数据库事务锁无需为失联执行者引入续约与超时接管策略。每个活动执行者会占用一个外层锁事务连接，内部写入另用连接。并行轮次数量和连接池容量需在 T6-E 规模验证中核对。

[schema.sql](../../../src/main/resources/schema.sql) 为轮次补独立锁键与凭据协议标识，按 `(round_id, entry_id)` 保存业务提交凭据。业务写入、凭据与适用的事件发布记录一起提交，Checkpoint 与单项结果随后提交。凭据记录提交时的双侧版次，避免恢复时将更晚的修改认成原决定处理的事实。

升级时应停止旧实例写入。旧版终态仍可读取及重提并返回原结果，旧版未终态轮次缺少提交凭据，返回 `UNKNOWN`，由运维核对业务事实后按 Q4 处理。新轮次可凭提交记录恢复。

## 验收

[MergeAcceptanceTest](../../../src/test/java/bootstrap/MergeAcceptanceTest.java) 通过 HTTP 验证：

- 两个独立 Spring Boot 应用上下文共用 PostgreSQL，实例 A 提交业务后停在结果记录，实例 B 的并发 Apply 得到 409。
- 注入结果记录事务失败后，实例 B 接续原轮次。即使原来源已升级、原目标已有本地修改，恢复也只补原 Checkpoint 和结果，再推进其余条目。重复 Apply 返回相同终态。
- 注入 Merge LTI 监听失败后，重投清除未完成发布并建立目标 LTI。业务对象及轮次结果保持原样。

`mvn -B clean test` 通过 **86 项，0 失败、0 错误、0 跳过**，含 ArchUnit、真实 PostgreSQL、Redis 和内嵌 Tomcat。中断由结果记录事务故障模拟，未执行操作系统级进程终止演练。
