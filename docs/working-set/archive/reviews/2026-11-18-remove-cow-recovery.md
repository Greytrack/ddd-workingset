# 2026-11-18 移除 COW Recovery

**状态：Reviewed（用户直接裁定）**

## 决定

- 删除 `CowRecoveryService`、`CowRecoveryController`、`/api/cow/recover/incomplete` 与
  `/api/cow/recover/switch/{eventId}/{targetLocalId}`。
- COW 失败即终态：记录失败阶段与原因后结束，不重投、不补切换、不执行恢复。
- `CowFailureLogQueryProvider` 仅保留失败审计，不再提供 `resolve`；`resolved_at` 不再写入。
- `CowEventHandler` 不再抛出聚合异常触发 Modulith 重投；当前事件按已处理结束。
- 正常成功路径的 LTI 切换内联到 `CowEventHandler`，仍使用独立事务。

## 影响

- 移除了“业务已保存但 LTI 未切换”的补偿入口；该状态保留为 FAILED，需要人工判断，不再由系统自动修正。
- 删除 `CowMaterializationFailedException`。
- 删除依赖恢复入口的验收测试：AT-05 switch-only、未完成事件重投。
- Merge 的 LTI 监听失败不再通过 `/api/cow/recover/incomplete` 重投；失败保持未完成记录或由后续独立运维机制处理，本次不提供恢复入口。

## 验证

- 主代码 `mvn -B -q -DskipTests compile` 通过。
- 测试编译当前被工作区中 `SnowflakeIdGenerator` 迁移（sharedkernel → deploys）阻塞，未完成全量验证。
