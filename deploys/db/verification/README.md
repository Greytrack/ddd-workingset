# Tracker 旧表切换核对

**Status: Draft**

schema.sql 用于初始化新库；应用不自动覆盖已有 trackers 实表，不自动迁移存量数据。测试和 Demo 专用的 tracker-fixtures.sql 不接入普通运行配置。

1. 在隔离的旧库副本上执行[只读对账](tracker-legacy-preflight.sql)，核对两表覆盖、身份、派生链及默认槽位。用真实样本对比旧系统读取结果。V4 仍通过 working_sets 管理工作集，核对旧 Tracker 的工作集、产品空间和分类在 V4 元数据中都有对应记录。
2. 若目标已有 V4 实表 trackers，先在停写窗口对账和回填，保留原有 Tracker ID 和引用。历史所有者、编号和审计信息按来源补齐。停写前排空仍以 web 记录提交人的创建和扩展任务；新任务记录数字账号供异步恢复。
3. 以新库 schema 为目标核对约束，尤其是 Requirement、LightTrackerItem 和 TrackerBinding 的外键；已有表不会被 CREATE TABLE IF NOT EXISTS 自动修改，存量切换需要显式迁移及验证。保留原表快照，完成迁移、回归和回退演练后再放开写入。若旧系统仍需写入，先明确其与 V4 的事务和并发协议。

V4 聚合删除沿用现有物理删除准入，由同一事务删除兼容行与根行。旧库已经标删的 Tracker 不参与普通查询。共享解绑仍只修改 Working Set 的绑定。
