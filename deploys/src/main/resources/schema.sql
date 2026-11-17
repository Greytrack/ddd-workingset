-- Working Set authoritative schema (PostgreSQL).
-- Development baseline: no legacy data migration. The schema is applied to a
-- clean database/test database; demo data is loaded separately by profile.

-- ---------------------------------------------------------------------------
-- Working Sets and platform metadata
-- ---------------------------------------------------------------------------

DO 'BEGIN
    IF EXISTS (SELECT 1 FROM pg_attribute
       WHERE attrelid = to_regclass(''working_sets'') AND attname = ''working_set_id''
       AND atttypid <> ''bigint''::regtype AND NOT attisdropped) THEN
        RAISE EXCEPTION ''Legacy string IDs detected. Recreate the development database or use the dedicated demo database.'';
    END IF;
END';

CREATE TABLE IF NOT EXISTS working_sets (
    working_set_id        BIGINT PRIMARY KEY,
    parent_working_set_id BIGINT REFERENCES working_sets (working_set_id),
    name                  VARCHAR(255),
    CONSTRAINT ck_working_set_parent CHECK (
        (working_set_id = '1' AND parent_working_set_id IS NULL)
        OR (working_set_id <> '1' AND parent_working_set_id IS NOT NULL
            AND parent_working_set_id <> working_set_id)
    )
);

INSERT INTO working_sets (working_set_id, parent_working_set_id, name)
VALUES ('1', NULL, '默认工作集')
ON CONFLICT (working_set_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_working_sets_parent ON working_sets (parent_working_set_id);

CREATE TABLE IF NOT EXISTS product_spaces (
    product_space_id BIGINT PRIMARY KEY,
    name             VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS classifications (
    classification_id BIGINT PRIMARY KEY,
    sharing_mode      VARCHAR(8) NOT NULL CHECK (sharing_mode IN ('SHARED', 'UNSHARED')),
    name              VARCHAR(200) NOT NULL DEFAULT '需求',
    UNIQUE (classification_id, sharing_mode)
);

-- Base requirement category. Demo-only categories are added by demo-data.sql.
INSERT INTO classifications (classification_id, name, sharing_mode)
VALUES (258519859202016, '需求', 'UNSHARED')
ON CONFLICT (classification_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Trackers and bindings
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS trackers (
    tracker_id        BIGINT PRIMARY KEY,
    name              VARCHAR(255),
    source_id         BIGINT REFERENCES trackers (tracker_id),
    working_set_id    BIGINT NOT NULL REFERENCES working_sets (working_set_id),
    product_space_id  BIGINT NOT NULL REFERENCES product_spaces (product_space_id),
    classification_id BIGINT NOT NULL,
    sharing_mode      VARCHAR(8) NOT NULL,
    parent_id         BIGINT,
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    visible           BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (tracker_id, working_set_id),
    UNIQUE (tracker_id, product_space_id, classification_id, sharing_mode),
    CONSTRAINT trackers_classification_id_fkey
        FOREIGN KEY (classification_id) REFERENCES classifications (classification_id),
    CONSTRAINT trackers_sharing_mode_check CHECK (sharing_mode IN ('SHARED', 'UNSHARED')),
    FOREIGN KEY (parent_id, working_set_id)
        REFERENCES trackers (tracker_id, working_set_id),
    CHECK ((source_id IS NULL AND working_set_id = '1')
        OR (source_id IS NOT NULL AND working_set_id <> '1' AND sharing_mode = 'UNSHARED'))
);

ALTER TABLE trackers ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE trackers ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;

-- POL-003: an unshared classification can also have a shared Tracker.
DO 'BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
        WHERE conrelid = ''trackers''::regclass AND conname = ''trackers_classification_id_fkey'') THEN
        ALTER TABLE trackers ADD CONSTRAINT trackers_classification_id_fkey
            FOREIGN KEY (classification_id) REFERENCES classifications (classification_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
        WHERE conrelid = ''trackers''::regclass AND conname = ''trackers_sharing_mode_check'') THEN
        ALTER TABLE trackers ADD CONSTRAINT trackers_sharing_mode_check
            CHECK (sharing_mode IN (''SHARED'', ''UNSHARED''));
    END IF;
    ALTER TABLE trackers DROP CONSTRAINT IF EXISTS trackers_classification_id_sharing_mode_fkey;
END';

-- 每个（所有者工作集，空间，分类）至多一个默认 Tracker
CREATE UNIQUE INDEX IF NOT EXISTS uq_trackers_default_slot
    ON trackers (working_set_id, product_space_id, classification_id) WHERE is_default;

-- 同一（工作集，空间，分类）内业务原生 Tracker 名称唯一；派生 Tracker 继承来源名称，不参与该约束。
CREATE UNIQUE INDEX IF NOT EXISTS uq_trackers_name_slot
    ON trackers (working_set_id, product_space_id, classification_id, name)
    WHERE name IS NOT NULL AND source_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_trackers_org_parent ON trackers (parent_id);
CREATE INDEX IF NOT EXISTS idx_trackers_source ON trackers (source_id);
-- Tracker lineage is 1:1: a child Working Set has at most one derived Tracker per parent Tracker.
-- Multiple Tracker projections in the same classification use different parent TrackerIds.
CREATE UNIQUE INDEX IF NOT EXISTS uq_trackers_derived_source
    ON trackers (working_set_id, source_id) WHERE source_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_trackers_product_space ON trackers (product_space_id);

CREATE TABLE IF NOT EXISTS space_bindings (
    working_set_id  BIGINT NOT NULL REFERENCES working_sets (working_set_id),
    product_space_id BIGINT NOT NULL REFERENCES product_spaces (product_space_id),
    origin          VARCHAR(24) NOT NULL CHECK (origin IN ('INITIAL_PROJECTION', 'EXPLICIT')),
    state           VARCHAR(8) NOT NULL CHECK (state = 'ACTIVE'),
    PRIMARY KEY (working_set_id, product_space_id)
);

CREATE INDEX IF NOT EXISTS idx_space_bindings_space ON space_bindings (product_space_id);

CREATE TABLE IF NOT EXISTS tracker_bindings (
    working_set_id   BIGINT NOT NULL,
    tracker_id       BIGINT NOT NULL,
    product_space_id BIGINT NOT NULL,
    classification_id BIGINT NOT NULL,
    sharing_mode     VARCHAR(8) NOT NULL,
    origin           VARCHAR(24) NOT NULL,
    PRIMARY KEY (working_set_id, tracker_id),
    FOREIGN KEY (working_set_id, product_space_id)
        REFERENCES space_bindings (working_set_id, product_space_id),
    FOREIGN KEY (tracker_id, product_space_id, classification_id, sharing_mode)
        REFERENCES trackers (tracker_id, product_space_id, classification_id, sharing_mode),
    CHECK ((origin = 'SHARED_REUSE' AND sharing_mode = 'SHARED')
        OR (origin = 'UNSHARED_DERIVATION' AND sharing_mode = 'UNSHARED'))
);

CREATE INDEX IF NOT EXISTS idx_tracker_bindings_tracker ON tracker_bindings (tracker_id);

-- T6-F e 裁定：同一（工作集，空间，分类）允许多个 Tracker（建表时已不包含该唯一键）。

-- ---------------------------------------------------------------------------
-- Requirements and LightTrackerItem index
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS requirements (
    local_id       BIGINT PRIMARY KEY,
    common_id      BIGINT NOT NULL,
    number         VARCHAR(200) GENERATED ALWAYS AS ('REQ-' || upper(to_hex(common_id))) STORED NOT NULL,
    tracker_id     BIGINT NOT NULL REFERENCES trackers (tracker_id),
    title          TEXT NOT NULL,
    description    TEXT NOT NULL,
    revision       INTEGER NOT NULL,
    version        VARCHAR(64),
    lock_version   BIGINT NOT NULL,
    delete_flag    BOOLEAN NOT NULL DEFAULT FALSE,
    working_set_id BIGINT NOT NULL REFERENCES working_sets (working_set_id),
    name           VARCHAR(255)
);

CREATE OR REPLACE FUNCTION set_requirement_working_set() RETURNS trigger AS '
BEGIN
    SELECT t.working_set_id INTO NEW.working_set_id
    FROM trackers t WHERE t.tracker_id = NEW.tracker_id;
    IF NEW.working_set_id IS NULL THEN
        RAISE EXCEPTION ''tracker % not found for requirement %'', NEW.tracker_id, NEW.local_id
            USING ERRCODE = ''23503'';
    END IF;
    RETURN NEW;
END;
' LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_requirements_working_set ON requirements;
CREATE TRIGGER trg_requirements_working_set
    BEFORE INSERT OR UPDATE OF tracker_id ON requirements
    FOR EACH ROW EXECUTE FUNCTION set_requirement_working_set();

-- POL-005/D2: one active or soft-deleted family per Working Set.
CREATE UNIQUE INDEX IF NOT EXISTS uq_requirements_working_set_family
    ON requirements (working_set_id, common_id);
CREATE INDEX IF NOT EXISTS idx_requirements_tracker ON requirements (tracker_id);
CREATE INDEX IF NOT EXISTS idx_requirement_active_tracker_local
    ON requirements (tracker_id, local_id) WHERE NOT delete_flag;

CREATE TABLE IF NOT EXISTS light_tracker_items (
    local_id        BIGINT PRIMARY KEY,
    common_id       BIGINT NOT NULL,
    tracker_id      BIGINT NOT NULL,
    working_set_id  BIGINT NOT NULL,
    revision        INTEGER NOT NULL,
    source_local_id BIGINT NOT NULL,
    source_revision INTEGER NOT NULL,
    materialized    BOOLEAN NOT NULL,
    FOREIGN KEY (tracker_id, working_set_id)
        REFERENCES trackers (tracker_id, working_set_id),
    CHECK ((materialized = FALSE AND source_local_id <> local_id)
        OR (materialized = TRUE AND source_local_id = local_id AND source_revision = revision))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_lti_working_set_family
    ON light_tracker_items (working_set_id, common_id);
CREATE INDEX IF NOT EXISTS idx_lti_source ON light_tracker_items (source_local_id, source_revision);
CREATE INDEX IF NOT EXISTS idx_lti_tracker ON light_tracker_items (tracker_id, working_set_id);
CREATE INDEX IF NOT EXISTS idx_lti_tracker_local
    ON light_tracker_items (tracker_id, local_id);

CREATE TABLE IF NOT EXISTS requirement_snapshots (
    snapshot_ref BIGINT PRIMARY KEY,
    local_id     BIGINT NOT NULL,
    common_id    BIGINT NOT NULL,
    tracker_id   BIGINT NOT NULL,
    title        TEXT NOT NULL,
    description  TEXT NOT NULL,
    revision     INTEGER NOT NULL,
    version      VARCHAR(64),
    lock_version BIGINT NOT NULL
);

-- ---------------------------------------------------------------------------
-- Copy-on-write recovery / diagnostics
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS cow_failures (
    id BIGINT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    target_local_id BIGINT NOT NULL,
    source_local_id BIGINT NOT NULL,
    before_revision INTEGER NOT NULL,
    after_revision INTEGER NOT NULL,
    before_lock_version BIGINT,
    after_lock_version BIGINT,
    attempts INTEGER NOT NULL DEFAULT 1,
    stage VARCHAR(32) NOT NULL,
    error_type VARCHAR(255) NOT NULL,
    message TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_cow_failures_source ON cow_failures (source_local_id);
CREATE INDEX IF NOT EXISTS idx_cow_failures_target ON cow_failures (target_local_id);

CREATE TABLE IF NOT EXISTS cow_materializations (
    event_id BIGINT NOT NULL,
    target_local_id BIGINT NOT NULL,
    source_local_id BIGINT NOT NULL,
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

-- ---------------------------------------------------------------------------
-- Merge LRO (compare itself is not persisted)
-- ---------------------------------------------------------------------------

-- 一轮 MergeRound 承载一页用户决策。requestId 是幂等键，必填且全局唯一。
-- 旧版 state JSON 中的 Difference.key / ItemResult.entryKey 不兼容：开发基线清理
-- merge_commit_receipts、merge_checkpoints、merge_rounds 后重建，不做 JSON 迁移。
CREATE TABLE IF NOT EXISTS merge_rounds (
    id BIGINT PRIMARY KEY,
    token VARCHAR(64) NOT NULL UNIQUE,
    request_id VARCHAR(128) NOT NULL UNIQUE,
    state JSONB NOT NULL
);

-- 一页决策同属一个 Tracker 对；Preparing/Running 期间占用该对，作为并发合并闸门。
ALTER TABLE merge_rounds ADD COLUMN IF NOT EXISTS tracker_a_id BIGINT;
ALTER TABLE merge_rounds ADD COLUMN IF NOT EXISTS tracker_b_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uq_merge_rounds_active_tracker_pair
    ON merge_rounds (tracker_a_id, tracker_b_id)
    WHERE (state->>'state') IN ('Preparing', 'Running');

CREATE SEQUENCE IF NOT EXISTS merge_decision_version;

-- 「已处理」锚点：同一 Tracker 分支对 (tracker_a_id, tracker_b_id) 的同一家族 (common_id) 至多一行；
-- WorkingSet 不参与 Checkpoint 身份；merge_direction 只是本次确认的方向属性；
-- local_id_a/b 只是确认时的变体证据，revision_a/b 是命中锚点，decision_version 防止晚到确认覆盖新事实。
CREATE TABLE IF NOT EXISTS merge_checkpoints (
    checkpoint_id    BIGINT PRIMARY KEY,
    round_id         BIGINT NOT NULL,
    tracker_a_id     BIGINT NOT NULL,
    tracker_b_id     BIGINT NOT NULL,
    common_id        BIGINT NOT NULL,
    local_id_a       BIGINT,
    local_id_b       BIGINT,
    revision_a       INTEGER,
    revision_b       INTEGER,
    change_kind      VARCHAR(16),
    merge_direction  VARCHAR(8)  NOT NULL,
    decision_version BIGINT      NOT NULL,
    revoked          BOOLEAN     NOT NULL DEFAULT FALSE,
    remark           TEXT,
    operator         VARCHAR(64),
    confirmed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_merge_checkpoints_tracker_pair_family
    ON merge_checkpoints (tracker_a_id, tracker_b_id, common_id);
CREATE INDEX IF NOT EXISTS idx_merge_checkpoints_round ON merge_checkpoints (round_id);
CREATE INDEX IF NOT EXISTS idx_merge_checkpoints_family ON merge_checkpoints (common_id);

CREATE TABLE IF NOT EXISTS merge_commit_receipts (
    round_id BIGINT NOT NULL REFERENCES merge_rounds (id) ON DELETE CASCADE,
    entry_id BIGINT NOT NULL,
    business_ref BIGINT,
    source_revision INTEGER,
    target_revision INTEGER,
    PRIMARY KEY (round_id, entry_id)
);

-- ---------------------------------------------------------------------------
-- Working Set creation LRO
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS working_set_creations (
    id BIGINT PRIMARY KEY,
    parent_working_set_id BIGINT NOT NULL,
    new_working_set_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    UNIQUE (parent_working_set_id, new_working_set_id, request_id)
);

-- Older baselines declared new_working_set_id UNIQUE. A target may now have multiple
-- extension tasks over time, so uniqueness is scoped by mode/status below.
ALTER TABLE working_set_creations DROP CONSTRAINT IF EXISTS working_set_creations_new_working_set_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_creation_target
    ON working_set_creations (new_working_set_id)
    WHERE (COALESCE(result->'request'->>'mode', 'CREATE') = 'CREATE');

CREATE UNIQUE INDEX IF NOT EXISTS uq_creation_request_id
    ON working_set_creations (request_id)
    WHERE (COALESCE(result->'request'->>'mode', 'CREATE') = 'CREATE');

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_extension_target
    ON working_set_creations (new_working_set_id)
    WHERE (result->'request'->>'mode' = 'EXTEND'
       AND status IN ('Pending', 'Running'));

CREATE TABLE IF NOT EXISTS working_set_creation_snapshots (
    creation_id BIGINT PRIMARY KEY REFERENCES working_set_creations (id) ON DELETE CASCADE,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS working_set_creation_items (
    creation_id BIGINT NOT NULL REFERENCES working_set_creation_snapshots (creation_id) ON DELETE CASCADE,
    parent_tracker_id BIGINT NOT NULL,
    local_id BIGINT NOT NULL,
    common_id BIGINT NOT NULL,
    revision INTEGER NOT NULL,
    source_local_id BIGINT NOT NULL,
    source_revision INTEGER NOT NULL,
    PRIMARY KEY (creation_id, parent_tracker_id, local_id)
);

CREATE INDEX IF NOT EXISTS idx_creation_items_page
    ON working_set_creation_items (creation_id, parent_tracker_id, local_id);
