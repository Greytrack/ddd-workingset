# 数据库验证

**Status: Reviewed（仅当前 schema 的空库重建与运行核对；不包含旧库迁移）**

`schema.sql` 是当前权威表结构，`demo-data.sql` 是 Demo 演示数据。开发、测试和 Demo 环境统一从空库执行：

```text
schema.sql
demo-data.sql（仅 demo profile）
```

不再保留旧表对账、V4 迁移预检或历史修正脚本。需要保留存量的正式环境必须单独编写一次性迁移方案并在隔离副本验证，不得把历史脚本重新放回本目录。
