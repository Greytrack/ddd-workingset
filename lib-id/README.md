# lib-id

**只放 ID 生成相关技术能力。** 允许 / 禁止与模块清单见 [v4-architecture.md](../../.lattice/standards/v4/v4-architecture.md) §2.1。

包：`com.airedgesoft.lib.id`

| 类型 | 说明 |
|------|------|
| `SnowflakeIdGenerator` | 与现网一致的雪花算法（无 Spring / MyBatis） |
| `Ids` | 门面：`nextId()` / `newId()` / `newId(prefix)`；可用 `configure(datacenterId, machineId)` |

位布局与起始时间戳与 `backend` 的 `SnowflakeIdGenerator` 保持一致，便于共库迁移。
