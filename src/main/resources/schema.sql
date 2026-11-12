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

CREATE TABLE IF NOT EXISTS working_sets (
    working_set_id VARCHAR(128) PRIMARY KEY
);

INSERT INTO working_sets (working_set_id) VALUES ('1') ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS product_spaces (
    product_space_id VARCHAR(64) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS classifications (
    classification_id VARCHAR(64) PRIMARY KEY,
    sharing_mode VARCHAR(8) NOT NULL CHECK (sharing_mode IN ('SHARED', 'UNSHARED')),
    UNIQUE (classification_id, sharing_mode)
);

CREATE TABLE IF NOT EXISTS trackers (
    tracker_id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) REFERENCES trackers (tracker_id),
    working_set_id VARCHAR(128) NOT NULL REFERENCES working_sets (working_set_id),
    product_space_id VARCHAR(64) NOT NULL REFERENCES product_spaces (product_space_id),
    classification_id VARCHAR(64) NOT NULL,
    sharing_mode VARCHAR(8) NOT NULL,
    parent_id VARCHAR(64),
    UNIQUE (tracker_id, working_set_id),
    UNIQUE (tracker_id, product_space_id, classification_id, sharing_mode),
    FOREIGN KEY (classification_id, sharing_mode) REFERENCES classifications (classification_id, sharing_mode),
    FOREIGN KEY (parent_id, working_set_id) REFERENCES trackers (tracker_id, working_set_id),
    CHECK ((source_id IS NULL AND working_set_id = '1')
        OR (source_id IS NOT NULL AND working_set_id <> '1' AND sharing_mode = 'UNSHARED'))
);

CREATE TABLE IF NOT EXISTS space_bindings (
    working_set_id VARCHAR(128) NOT NULL REFERENCES working_sets (working_set_id),
    product_space_id VARCHAR(64) NOT NULL REFERENCES product_spaces (product_space_id),
    origin VARCHAR(24) NOT NULL CHECK (origin IN ('INITIAL_PROJECTION', 'EXPLICIT')),
    state VARCHAR(8) NOT NULL CHECK (state = 'ACTIVE'),
    PRIMARY KEY (working_set_id, product_space_id)
);

CREATE TABLE IF NOT EXISTS tracker_bindings (
    working_set_id VARCHAR(128) NOT NULL,
    tracker_id VARCHAR(64) NOT NULL,
    product_space_id VARCHAR(64) NOT NULL,
    classification_id VARCHAR(64) NOT NULL,
    sharing_mode VARCHAR(8) NOT NULL,
    origin VARCHAR(24) NOT NULL,
    PRIMARY KEY (working_set_id, tracker_id),
    UNIQUE (working_set_id, product_space_id, classification_id),
    FOREIGN KEY (working_set_id, product_space_id) REFERENCES space_bindings (working_set_id, product_space_id),
    FOREIGN KEY (tracker_id, product_space_id, classification_id, sharing_mode)
        REFERENCES trackers (tracker_id, product_space_id, classification_id, sharing_mode),
    CHECK ((origin = 'SHARED_REUSE' AND sharing_mode = 'SHARED')
        OR (origin = 'UNSHARED_DERIVATION' AND sharing_mode = 'UNSHARED'))
);

-- 旧切片可能已有未登记 Tracker 的 Item。保留历史行，新的引用由外键强制校验。
DO 'BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = ''fk_requirements_tracker'' AND conrelid = ''requirements''::regclass) THEN
        ALTER TABLE requirements ADD CONSTRAINT fk_requirements_tracker
            FOREIGN KEY (tracker_id) REFERENCES trackers (tracker_id) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = ''fk_lti_tracker'' AND conrelid = ''light_tracker_items''::regclass) THEN
        ALTER TABLE light_tracker_items ADD CONSTRAINT fk_lti_tracker
            FOREIGN KEY (tracker_id, working_set_id) REFERENCES trackers (tracker_id, working_set_id) NOT VALID;
    END IF;
END';

CREATE INDEX IF NOT EXISTS idx_requirements_tracker ON requirements (tracker_id);
CREATE INDEX IF NOT EXISTS idx_lti_tracker ON light_tracker_items (tracker_id, working_set_id);
CREATE INDEX IF NOT EXISTS idx_trackers_org_parent ON trackers (parent_id);
CREATE INDEX IF NOT EXISTS idx_trackers_source ON trackers (source_id);
CREATE INDEX IF NOT EXISTS idx_trackers_product_space ON trackers (product_space_id);
CREATE INDEX IF NOT EXISTS idx_tracker_bindings_tracker ON tracker_bindings (tracker_id);
CREATE INDEX IF NOT EXISTS idx_space_bindings_space ON space_bindings (product_space_id);
