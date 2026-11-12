-- Working Set 首切片最小持久化 schema（PostgreSQL）
-- 由 spring.sql.init 在应用启动时执行；全部语句可重复执行。

CREATE TABLE IF NOT EXISTS requirements (
    local_id     VARCHAR(64) PRIMARY KEY,
    common_id    VARCHAR(64) NOT NULL,
    tracker_id   VARCHAR(64) NOT NULL,
    title        TEXT        NOT NULL,
    description  TEXT        NOT NULL,
    revision     INTEGER     NOT NULL,
    version      VARCHAR(64),
    lock_version BIGINT      NOT NULL
);

CREATE TABLE IF NOT EXISTS light_tracker_items (
    local_id         VARCHAR(64) PRIMARY KEY,
    common_id        VARCHAR(64) NOT NULL,
    tracker_id       VARCHAR(64) NOT NULL,
    working_set_id   VARCHAR(128) NOT NULL,
    revision         INTEGER     NOT NULL,
    source_local_id  VARCHAR(64) NOT NULL,
    source_revision  INTEGER     NOT NULL,
    materialized     BOOLEAN     NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lti_source
    ON light_tracker_items (source_local_id, source_revision);

CREATE TABLE IF NOT EXISTS requirement_snapshots (
    snapshot_ref VARCHAR(64) PRIMARY KEY,
    local_id     VARCHAR(64) NOT NULL,
    common_id    VARCHAR(64) NOT NULL,
    tracker_id   VARCHAR(64) NOT NULL,
    title        TEXT        NOT NULL,
    description  TEXT        NOT NULL,
    revision     INTEGER     NOT NULL,
    version      VARCHAR(64),
    lock_version BIGINT      NOT NULL
);
CREATE TABLE IF NOT EXISTS cow_failures (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    target_local_id VARCHAR(64) NOT NULL,
    source_local_id VARCHAR(64) NOT NULL,
    before_revision INTEGER NOT NULL,
    after_revision INTEGER NOT NULL,
    stage VARCHAR(32) NOT NULL,
    error_type VARCHAR(255) NOT NULL,
    message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cow_failures_source ON cow_failures (source_local_id);
CREATE INDEX IF NOT EXISTS idx_cow_failures_target ON cow_failures (target_local_id);

ALTER TABLE cow_failures ADD COLUMN IF NOT EXISTS before_lock_version BIGINT;
ALTER TABLE cow_failures ADD COLUMN IF NOT EXISTS after_lock_version BIGINT;
ALTER TABLE cow_failures ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 1;
ALTER TABLE cow_failures ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS cow_materializations (
    event_id BIGINT NOT NULL,
    target_local_id VARCHAR(64) NOT NULL,
    source_local_id VARCHAR(64) NOT NULL,
    source_revision INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    business_saved BOOLEAN NOT NULL DEFAULT FALSE,
    last_error TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, target_local_id)
);

CREATE INDEX IF NOT EXISTS idx_cow_materializations_target ON cow_materializations (target_local_id);
CREATE INDEX IF NOT EXISTS idx_cow_materializations_status ON cow_materializations (status);
