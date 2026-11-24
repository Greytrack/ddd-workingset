-- =====================================================================
-- Outbox 本地消息表（H2 兼容形态）— Transactional Outbox（Tier D 基建）
-- 供 demo 接入批次执行（测试 schema.sql / Flyway）。与业务表同库。
-- 派发顺序 = create_time_ms 升序；至少一次投递，消费端按 id 幂等去重。
-- 信箱取号（lib-event）：seq / source_event_id 与 PostgreSQL 形态同构（h2 测试无 advisory 锁，单实例语义等价）。
-- =====================================================================

CREATE TABLE IF NOT EXISTS airedge_outbox (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    event_type       VARCHAR(128) NOT NULL,
    aggregate_type   VARCHAR(128) NOT NULL,
    aggregate_id     VARCHAR(64)  NOT NULL,
    payload          CLOB         NOT NULL,
    create_time_ms   BIGINT       NOT NULL,
    published_time_ms BIGINT      NULL,
    seq              BIGINT       NULL,
    source_event_id  VARCHAR(128) NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_airedge_outbox_group_seq
    ON airedge_outbox (aggregate_type, aggregate_id, seq);

CREATE UNIQUE INDEX IF NOT EXISTS uk_airedge_outbox_dedup
    ON airedge_outbox (source_event_id, aggregate_type, aggregate_id);

CREATE INDEX IF NOT EXISTS idx_airedge_outbox_unpublished
    ON airedge_outbox (published_time_ms, create_time_ms);
