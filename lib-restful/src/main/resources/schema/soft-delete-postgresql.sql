-- =====================================================================
-- 软删除列参考 DDL（PostgreSQL）— v4-restful-api.md §5.1
-- 列模型沿用 v3 既有表：delete_flag int4（0 = 存活 / 1 = 已删）+ updated_time int8（Epoch 毫秒）。
-- 既有业务表已具备这两列（v3 baseline / v3.x 迁移脚本），**禁止为软删另加列**；
-- 本片段仅供新建表 / 测试 schema 参考（示例以 demo_task_write 为例，按需替换表名）。
-- 删除写 UPDATE ... SET delete_flag = 1, updated_time = <now millis>；
-- 恢复回写 delete_flag = 0 并刷新 updated_time；删除时刻 = 已删行的 updated_time。
-- =====================================================================

ALTER TABLE demo_task_write
    ADD COLUMN IF NOT EXISTS delete_flag int4 NOT NULL DEFAULT 0;

ALTER TABLE demo_task_write
    ADD COLUMN IF NOT EXISTS updated_time int8;

CREATE INDEX IF NOT EXISTS idx_demo_task_write_delete_flag
    ON demo_task_write (delete_flag);
