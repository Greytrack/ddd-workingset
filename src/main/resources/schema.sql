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
