-- =====================================================================
-- Outbox 本地消息表（PostgreSQL 形态）— Transactional Outbox（Tier D 基建）
-- 供 demo 接入批次执行（Flyway / 生产 DDL 流水线）。与业务表同库。
-- 派发顺序 = create_time_ms 升序；至少一次投递，消费端按 id 幂等去重。
-- 信箱取号（lib-event）：seq = per-信箱连续序号（Router 定向命令事件，同事务 max+1 赋号）；
--   source_event_id = 源事件 id（Router 幂等键——(source_event_id, target) 唯一约束挡重复转发）。
-- ⚠️ 行永久保留（硬约束）：信箱序号 max(seq)+1 依赖历史行在，禁止时间性清理/归档
--   （清理即序号回退静默丢消息）；生产护栏 = 应用数据库角色 REVOKE DELETE ON airedge_outbox。
-- =====================================================================

CREATE TABLE IF NOT EXISTS airedge_outbox (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    event_type       VARCHAR(128) NOT NULL,
    aggregate_type   VARCHAR(128) NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,
    payload          TEXT         NOT NULL,
    create_time_ms   BIGINT       NOT NULL,
    published_time_ms BIGINT      NULL,
    seq              BIGINT       NULL,
    source_event_id  VARCHAR(128) NULL
);

CREATE INDEX IF NOT EXISTS idx_airedge_outbox_unpublished
    ON airedge_outbox (published_time_ms, create_time_ms);

-- 已有库升级（必须先加列再建索引——索引引用 seq 列；新表形态已含时这两句为 no-op）：
ALTER TABLE airedge_outbox ADD COLUMN IF NOT EXISTS seq BIGINT;
ALTER TABLE airedge_outbox ADD COLUMN IF NOT EXISTS source_event_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_airedge_outbox_group_seq
    ON airedge_outbox (aggregate_type, aggregate_id, seq);

CREATE UNIQUE INDEX IF NOT EXISTS uk_airedge_outbox_dedup
    ON airedge_outbox (source_event_id, aggregate_type, aggregate_id);

-- 禁删护栏（设计 §9.8 硬约束）：先撤销 PUBLIC 兜底；
-- 生产 DDL 流水线还须对应用数据库角色显式 REVOKE DELETE ON airedge_outbox FROM <app_role>
-- （表 owner 天然保留 DELETE，护栏对 owner 不生效——应用连接角色不得为 owner）。
REVOKE DELETE ON airedge_outbox FROM PUBLIC;
