-- Working Set authoritative schema (PostgreSQL).
-- Development baseline: applied to a clean database/test database; demo data is loaded separately by profile.

-- ---------------------------------------------------------------------------
-- Working Sets and platform metadata
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS air_inst_workingset
(
    "id"                 bigint                 NOT NULL,
    "base_class"         bigint                 NOT NULL,
    "class"              bigint,
    "type"               bigint                 NOT NULL,
    "number"             character varying(255) NOT NULL,
    "name"               character varying(255),
    "description"        character varying(2000),
    "color"              character varying(500),
    "workingset_id"      bigint,
    "work_space"         bigint,
    "owner"              bigint,
    "instance_id"        bigint                 NOT NULL,
    "common_id"          bigint                 NOT NULL,
    "parent_id"          bigint,
    "delete_flag"        integer                NOT NULL,
    "flags"              character varying(255),
    "attributes"         jsonb,
    "revision"           bigint,
    "version_type"       bigint                 NOT NULL DEFAULT 10001,
    "lock_version"       bigint                 NOT NULL,
    "created_by"         character varying(50)  NOT NULL,
    "created_time"       bigint                 NOT NULL,
    "updated_by"         character varying(50)  NOT NULL,
    "updated_time"       bigint                 NOT NULL,
    "date01"             bigint,
    "date02"             bigint,
    "date03"             bigint,
    "date04"             bigint,
    "date05"             bigint,
    "text01"             character varying(150),
    "text02"             character varying(150),
    "text03"             character varying(150),
    "text04"             character varying(150),
    "text05"             character varying(150),
    "text06"             character varying(150),
    "text07"             character varying(150),
    "text08"             character varying(150),
    "text09"             character varying(150),
    "text10"             character varying(150),
    "list01"             bigint,
    "list02"             bigint,
    "list03"             bigint,
    "list04"             bigint,
    "list05"             bigint,
    "list06"             bigint,
    "list07"             bigint,
    "list08"             bigint,
    "list09"             bigint,
    "list10"             bigint,
    "multilist01"        character varying(4000),
    "multilist02"        character varying(4000),
    "multilist03"        character varying(4000),
    "multilist04"        character varying(4000),
    "multilist05"        character varying(4000),
    "multilist06"        character varying(4000),
    "multilist07"        character varying(4000),
    "multilist08"        character varying(4000),
    "multilist09"        character varying(4000),
    "multilist10"        character varying(4000),
    "numeric01"          numeric,
    "numeric02"          numeric,
    "numeric03"          numeric,
    "numeric04"          numeric,
    "numeric05"          numeric,
    "numeric06"          numeric,
    "numeric07"          numeric,
    "numeric08"          numeric,
    "numeric09"          numeric,
    "numeric10"          numeric,
    "largetext01"        character varying(4000),
    "largetext02"        character varying(4000),
    "largetext03"        character varying(4000),
    "largetext04"        character varying(4000),
    "largetext05"        character varying(4000),
    "compositetext01"    character varying(255),
    "compositetext02"    character varying(255),
    "compositetext03"    character varying(255),
    "compositetext04"    character varying(255),
    "compositetext05"    character varying(255),
    "compositenumber01"  character varying(255),
    "compositenumber02"  character varying(255),
    "compositenumber03"  character varying(255),
    "compositenumber04"  character varying(255),
    "compositenumber05"  character varying(255),
    "startdate01"        bigint,
    "endstartdate01"     bigint,
    "startdate02"        bigint,
    "endstartdate02"     bigint,
    "startdate03"        bigint,
    "endstartdate03"     bigint,
    "startdate04"        bigint,
    "endstartdate04"     bigint,
    "startdate05"        bigint,
    "endstartdate05"     bigint,
    "richtext01"         text,
    "richtext02"         text,
    "richtext03"         text,
    "richtext04"         text,
    "richtext05"         text,
    "date001"            bigint,
    "date002"            bigint,
    "date003"            bigint,
    "date004"            bigint,
    "date005"            bigint,
    "text001"            character varying(150),
    "text002"            character varying(150),
    "text003"            character varying(150),
    "text004"            character varying(150),
    "text005"            character varying(150),
    "text006"            character varying(150),
    "text007"            character varying(150),
    "text008"            character varying(150),
    "text009"            character varying(150),
    "text010"            character varying(150),
    "list001"            bigint,
    "list002"            bigint,
    "list003"            bigint,
    "list004"            bigint,
    "list005"            bigint,
    "list006"            bigint,
    "list007"            bigint,
    "list008"            bigint,
    "list009"            bigint,
    "list010"            bigint,
    "multilist001"       character varying(4000),
    "multilist002"       character varying(4000),
    "multilist003"       character varying(4000),
    "multilist004"       character varying(4000),
    "multilist005"       character varying(4000),
    "multilist006"       character varying(4000),
    "multilist007"       character varying(4000),
    "multilist008"       character varying(4000),
    "multilist009"       character varying(4000),
    "multilist010"       character varying(4000),
    "numeric001"         numeric,
    "numeric002"         numeric,
    "numeric003"         numeric,
    "numeric004"         numeric,
    "numeric005"         numeric,
    "numeric006"         numeric,
    "numeric007"         numeric,
    "numeric008"         numeric,
    "numeric009"         numeric,
    "numeric010"         numeric,
    "largetext001"       character varying(4000),
    "largetext002"       character varying(4000),
    "largetext003"       character varying(4000),
    "largetext004"       character varying(4000),
    "largetext005"       character varying(4000),
    "compositetext001"   character varying(255),
    "compositetext002"   character varying(255),
    "compositetext003"   character varying(255),
    "compositetext004"   character varying(255),
    "compositetext005"   character varying(255),
    "compositenumber001" character varying(255),
    "compositenumber002" character varying(255),
    "compositenumber003" character varying(255),
    "compositenumber004" character varying(255),
    "compositenumber005" character varying(255),
    "startdate001"       bigint,
    "endstartdate001"    bigint,
    "startdate002"       bigint,
    "endstartdate002"    bigint,
    "startdate003"       bigint,
    "endstartdate003"    bigint,
    "startdate004"       bigint,
    "endstartdate004"    bigint,
    "startdate005"       bigint,
    "endstartdate005"    bigint,
    "richtext001"        text,
    "richtext002"        text,
    "richtext003"        text,
    "richtext004"        text,
    "richtext005"        text,
    "tracker"            bigint,
    "workingset_scop"    bigint                          DEFAULT 0,
    PRIMARY KEY ("id"),
    CONSTRAINT ck_workingset_identity CHECK (
        id = instance_id AND id = common_id AND workingset_id IS NOT NULL AND id = workingset_id),
    CONSTRAINT ck_workingset_parent CHECK (
        (id = 1 AND parent_id IS NULL) OR (id <> 1 AND parent_id IS NOT NULL AND parent_id <> id)),
    CONSTRAINT workingset_parent_fk FOREIGN KEY (parent_id) REFERENCES air_inst_workingset (id)
);

INSERT INTO air_inst_workingset (id, instance_id, common_id, workingset_id, parent_id,
                                 base_class, class, type, tracker, name, number, color, revision, version_type,
                                 delete_flag,
                                 lock_version, created_by, created_time, updated_by, updated_time)
SELECT 1,
       1,
       1,
       1,
       NULL,
       1008,
       100801,
       10080101,
       584190236948832,
       '默认工作集',
       'WS-1',
       '#FFFF',
       1,
       10001,
       0,
       0,
       'system',
       now_ms,
       'system',
       now_ms
FROM (SELECT (EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) * 1000)::bigint AS now_ms) AS seed
ON CONFLICT (id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_air_workingset_parent ON air_inst_workingset (parent_id) WHERE delete_flag = 0;

CREATE TABLE IF NOT EXISTS product_spaces
(
    product_space_id BIGINT PRIMARY KEY,
    name             VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS classifications
(
    classification_id BIGINT PRIMARY KEY,
    sharing_mode      VARCHAR(8)   NOT NULL CHECK (sharing_mode IN ('SHARED', 'UNSHARED')),
    name              VARCHAR(200) NOT NULL,
    base_class_id     BIGINT       NOT NULL,
    class_id          BIGINT       NOT NULL,
    UNIQUE (classification_id, sharing_mode)
);

INSERT INTO classifications (classification_id, sharing_mode, name, base_class_id, class_id)
VALUES (10030101, 'UNSHARED', '产品空间', 1003, 100301),
       (10070101, 'UNSHARED', '文件夹', 1007, 100701),
       (10080101, 'UNSHARED', '工作集', 1008, 100801),
       (10090101, 'UNSHARED', 'Tracker', 1009, 100901),
       (14010101, 'UNSHARED', '需求', 1401, 140101),
       (14010102, 'SHARED', '需求（共享）', 1401, 140101)
ON CONFLICT (classification_id) DO UPDATE SET base_class_id = EXCLUDED.base_class_id,
                                              class_id      = EXCLUDED.class_id;

-- ---------------------------------------------------------------------------
-- Trackers and bindings
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS air_inst_tracker_rev
(
    "id"                         bigint                 NOT NULL,
    "base_class"                 bigint                 NOT NULL,
    "class"                      bigint,
    "type"                       bigint                 NOT NULL,
    "name"                       character varying(2000),
    "work_space"                 bigint,
    "delete_flag"                integer                NOT NULL,
    "number"                     character varying(255) NOT NULL,
    "flags"                      character varying(255),
    "attributes"                 jsonb,
    "revision_id"                bigint,
    "revision"                   bigint,
    "description"                text,
    "tracker_class"              bigint                 NOT NULL,
    "default_layout"             bigint                 NOT NULL DEFAULT '584532998805760'::bigint,
    "share"                      bigint,
    "owner"                      bigint                 NOT NULL,
    "tracker"                    bigint,
    "life_cycle_phase"           bigint,
    "checked_out"                integer,
    "instance_id"                bigint,
    "version_type"               bigint                 NOT NULL DEFAULT 10001,
    "common_id"                  bigint,
    "workingset_id"              bigint,
    "parent_id"                  bigint,
    "related_instance_id"        bigint,
    "related_type"               bigint,
    "lock_version"               bigint                 NOT NULL,
    "created_by"                 character varying(50)  NOT NULL,
    "created_time"               bigint                 NOT NULL,
    "updated_by"                 character varying(50)  NOT NULL,
    "updated_time"               bigint                 NOT NULL,
    "date01"                     bigint,
    "date02"                     bigint,
    "date03"                     bigint,
    "date04"                     bigint,
    "date05"                     bigint,
    "text01"                     character varying(150),
    "text02"                     character varying(150),
    "text03"                     character varying(150),
    "text04"                     character varying(150),
    "text05"                     character varying(150),
    "text06"                     character varying(150),
    "text07"                     character varying(150),
    "text08"                     character varying(150),
    "text09"                     character varying(150),
    "text10"                     character varying(150),
    "list01"                     bigint,
    "list02"                     bigint,
    "list03"                     bigint,
    "list04"                     bigint,
    "list05"                     bigint,
    "list06"                     bigint,
    "list07"                     bigint,
    "list08"                     bigint,
    "list09"                     bigint,
    "list10"                     bigint,
    "multilist01"                character varying(4000),
    "multilist02"                character varying(4000),
    "multilist03"                character varying(4000),
    "multilist04"                character varying(4000),
    "multilist05"                character varying(4000),
    "multilist06"                character varying(4000),
    "multilist07"                character varying(4000),
    "multilist08"                character varying(4000),
    "multilist09"                character varying(4000),
    "multilist10"                character varying(4000),
    "numeric01"                  numeric,
    "numeric02"                  numeric,
    "numeric03"                  numeric,
    "numeric04"                  numeric,
    "numeric05"                  numeric,
    "numeric06"                  numeric,
    "numeric07"                  numeric,
    "numeric08"                  numeric,
    "numeric09"                  numeric,
    "numeric10"                  numeric,
    "largetext01"                character varying(4000),
    "largetext02"                character varying(4000),
    "largetext03"                character varying(4000),
    "largetext04"                character varying(4000),
    "largetext05"                character varying(4000),
    "compositetext01"            character varying(255),
    "compositetext02"            character varying(255),
    "compositetext03"            character varying(255),
    "compositetext04"            character varying(255),
    "compositetext05"            character varying(255),
    "compositenumber01"          character varying(255),
    "compositenumber02"          character varying(255),
    "compositenumber03"          character varying(255),
    "compositenumber04"          character varying(255),
    "compositenumber05"          character varying(255),
    "startdate01"                bigint,
    "endstartdate01"             bigint,
    "startdate02"                bigint,
    "endstartdate02"             bigint,
    "startdate03"                bigint,
    "endstartdate03"             bigint,
    "startdate04"                bigint,
    "endstartdate04"             bigint,
    "startdate05"                bigint,
    "endstartdate05"             bigint,
    "richtext01"                 text,
    "richtext02"                 text,
    "richtext03"                 text,
    "richtext04"                 text,
    "richtext05"                 text,
    "date001"                    bigint,
    "date002"                    bigint,
    "date003"                    bigint,
    "date004"                    bigint,
    "date005"                    bigint,
    "text001"                    character varying(150),
    "text002"                    character varying(150),
    "text003"                    character varying(150),
    "text004"                    character varying(150),
    "text005"                    character varying(150),
    "text006"                    character varying(150),
    "text007"                    character varying(150),
    "text008"                    character varying(150),
    "text009"                    character varying(150),
    "text010"                    character varying(150),
    "list001"                    bigint,
    "list002"                    bigint,
    "list003"                    bigint,
    "list004"                    bigint,
    "list005"                    bigint,
    "list006"                    bigint,
    "list007"                    bigint,
    "list008"                    bigint,
    "list009"                    bigint,
    "list010"                    bigint,
    "multilist001"               character varying(4000),
    "multilist002"               character varying(4000),
    "multilist003"               character varying(4000),
    "multilist004"               character varying(4000),
    "multilist005"               character varying(4000),
    "multilist006"               character varying(4000),
    "multilist007"               character varying(4000),
    "multilist008"               character varying(4000),
    "multilist009"               character varying(4000),
    "multilist010"               character varying(4000),
    "numeric001"                 numeric,
    "numeric002"                 numeric,
    "numeric003"                 numeric,
    "numeric004"                 numeric,
    "numeric005"                 numeric,
    "numeric006"                 numeric,
    "numeric007"                 numeric,
    "numeric008"                 numeric,
    "numeric009"                 numeric,
    "numeric010"                 numeric,
    "largetext001"               character varying(4000),
    "largetext002"               character varying(4000),
    "largetext003"               character varying(4000),
    "largetext004"               character varying(4000),
    "largetext005"               character varying(4000),
    "compositetext001"           character varying(255),
    "compositetext002"           character varying(255),
    "compositetext003"           character varying(255),
    "compositetext004"           character varying(255),
    "compositetext005"           character varying(255),
    "compositenumber001"         character varying(255),
    "compositenumber002"         character varying(255),
    "compositenumber003"         character varying(255),
    "compositenumber004"         character varying(255),
    "compositenumber005"         character varying(255),
    "startdate001"               bigint,
    "endstartdate001"            bigint,
    "startdate002"               bigint,
    "endstartdate002"            bigint,
    "startdate003"               bigint,
    "endstartdate003"            bigint,
    "startdate004"               bigint,
    "endstartdate004"            bigint,
    "startdate005"               bigint,
    "endstartdate005"            bigint,
    "richtext001"                text,
    "richtext002"                text,
    "richtext003"                text,
    "richtext004"                text,
    "richtext005"                text,
    "instance_tree_show_content" character varying(20)           DEFAULT '1'::character varying,
    "reference1"                 bigint,
    "reference01"                bigint,
    "reference001"               bigint,
    "rank"                       character varying(64),
    "source_branch_id"           bigint,
    "color"                      character varying(255),
    "visible"                    smallint                        DEFAULT 1,
    "locked"                     smallint                        DEFAULT 0,
    "is_default"                 smallint                        DEFAULT 0,
    "instance_type"              bigint                 NOT NULL DEFAULT 0,
    "tab_id"                     bigint,
    "revision_in"                bigint,
    "revision_out"               bigint,
    "edit_row"                   bigint,
    "checkout_user"              character varying(50),
    "checkout_time"              bigint,
    "checkin_user"               character varying(50),
    "checkin_time"               bigint,
    "release_time"               bigint,
    "version"                    character varying(50),
    "main_version"               character varying(50),
    "small_version"              integer,
    "last_revision_id"           bigint,
    "last_version_id"            bigint,
    "related_common_id"          bigint,
    "related_workingset_id"      bigint,
    "related_work_space"         bigint,
    "is_global"                  smallint,
    "version_rule_id"            bigint,
    "version_number"             character varying(500),
    "instance_baseline"          bigint,
    PRIMARY KEY ("id"),
    CONSTRAINT tracker_v4_identity CHECK (
        instance_id IS NOT NULL AND instance_id = id AND revision IS NOT NULL AND revision = 1
            AND common_id IS NOT NULL AND common_id > 0
            AND ((source_branch_id IS NULL AND common_id = id) OR (source_branch_id IS NOT NULL AND common_id <> id))
            AND work_space IS NOT NULL AND workingset_id IS NOT NULL
            AND share IS NOT NULL AND share IN (0, 1) AND delete_flag IN (0, 1)),
    CONSTRAINT tracker_v4_lineage CHECK (
        (source_branch_id IS NULL AND workingset_id = 1)
            OR (source_branch_id IS NOT NULL AND workingset_id <> 1 AND share = 0)),
    CONSTRAINT tracker_v4_workingset_fk FOREIGN KEY (workingset_id) REFERENCES air_inst_workingset (id),
    CONSTRAINT tracker_v4_space_fk FOREIGN KEY (work_space) REFERENCES product_spaces (product_space_id),
    CONSTRAINT tracker_v4_classification_fk FOREIGN KEY (tracker_class) REFERENCES classifications (classification_id),
    CONSTRAINT tracker_v4_source_fk FOREIGN KEY (source_branch_id) REFERENCES air_inst_tracker_rev (id),
    CONSTRAINT uq_tracker_rev_owner UNIQUE (id, workingset_id),
    FOREIGN KEY (parent_id, workingset_id) REFERENCES air_inst_tracker_rev (id, workingset_id)
);

CREATE TABLE IF NOT EXISTS air_ws_tracker
(
    "id"               bigint NOT NULL,
    "parent_id"        bigint,
    "number"           character varying(500),
    "name"             character varying(500),
    "description"      character varying(1000),
    "color"            character varying(255),
    "workingset_id"    bigint NOT NULL,
    "sub_class_id"     bigint NOT NULL,
    "source_branch_id" bigint,
    "visible"          smallint,
    "locked"           smallint,
    "shared"           integer,
    "is_default"       smallint,
    "attributes"       jsonb,
    "delete_flag"      integer,
    "lock_version"     bigint,
    "created_by"       character varying(50),
    "created_time"     bigint,
    "updated_by"       character varying(50),
    "updated_time"     bigint,
    PRIMARY KEY ("id"),
    FOREIGN KEY (id) REFERENCES air_inst_tracker_rev (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tracker_rev_owner ON air_inst_tracker_rev (id, workingset_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tracker_rev_binding ON air_inst_tracker_rev (id, work_space, tracker_class, share);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tracker_rev_default ON air_inst_tracker_rev (workingset_id, work_space, tracker_class)
    WHERE is_default = 1 AND delete_flag = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tracker_rev_name ON air_inst_tracker_rev (workingset_id, work_space, tracker_class, name)
    WHERE name IS NOT NULL AND source_branch_id IS NULL AND delete_flag = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_tracker_rev_source ON air_inst_tracker_rev (workingset_id, source_branch_id)
    WHERE source_branch_id IS NOT NULL AND delete_flag = 0;
CREATE INDEX IF NOT EXISTS idx_tracker_rev_parent ON air_inst_tracker_rev (parent_id);
CREATE INDEX IF NOT EXISTS idx_tracker_rev_source ON air_inst_tracker_rev (source_branch_id);
CREATE INDEX IF NOT EXISTS idx_tracker_rev_space ON air_inst_tracker_rev (work_space);
CREATE INDEX IF NOT EXISTS idx_tracker_instance_type ON air_inst_tracker_rev (instance_type);
CREATE INDEX IF NOT EXISTS idx_tracker_revision ON air_inst_tracker_rev (revision);
CREATE INDEX IF NOT EXISTS idx_tracker_version ON air_inst_tracker_rev (version);
CREATE INDEX IF NOT EXISTS tracker_number_test_index ON air_inst_tracker_rev (number);
CREATE INDEX IF NOT EXISTS tracker_number_ws_test_index ON air_inst_tracker_rev (number, workingset_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tracker_rev_space_id ON air_inst_tracker_rev (id, work_space);
CREATE TABLE IF NOT EXISTS air_ws_workingset_branch_relation
(
    id                  BIGINT PRIMARY KEY,
    workingset_id       BIGINT REFERENCES air_inst_workingset (id),
    work_space          BIGINT REFERENCES product_spaces (product_space_id),
    tracker_id          BIGINT,
    baseline_id         BIGINT,
    lock_version        BIGINT,
    created_by          VARCHAR(50),
    created_time        BIGINT,
    updated_by          VARCHAR(50),
    updated_time        BIGINT,
    product_baseline_id BIGINT,
    is_show             INTEGER DEFAULT 1,
    FOREIGN KEY (tracker_id, work_space) REFERENCES air_inst_tracker_rev (id, work_space)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ws_relation_membership
    ON air_ws_workingset_branch_relation (workingset_id, tracker_id)
    WHERE workingset_id IS NOT NULL AND work_space IS NOT NULL AND tracker_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ws_relation_space ON air_ws_workingset_branch_relation (work_space, workingset_id);
CREATE INDEX IF NOT EXISTS idx_ws_relation_tracker ON air_ws_workingset_branch_relation (tracker_id);

CREATE OR REPLACE VIEW tracker_bindings AS
SELECT r.workingset_id                                                                 AS working_set_id,
       r.tracker_id,
       r.work_space                                                                    AS product_space_id,
       t.tracker_class                                                                 AS classification_id,
       CASE t.share WHEN 1 THEN 'SHARED' ELSE 'UNSHARED' END::varchar                  AS sharing_mode,
       CASE t.share WHEN 1 THEN 'SHARED_REUSE' ELSE 'UNSHARED_DERIVATION' END::varchar AS origin,
       COALESCE(r.is_show, 1) <> 0                                                     AS shown
FROM air_ws_workingset_branch_relation r
         JOIN air_inst_tracker_rev t ON t.id = r.tracker_id AND t.work_space = r.work_space AND t.delete_flag = 0
         JOIN air_inst_workingset w ON w.id = r.workingset_id AND w.delete_flag = 0
         JOIN product_spaces p ON p.product_space_id = r.work_space
WHERE t.share = 1
   OR (t.share = 0 AND t.workingset_id = r.workingset_id);

CREATE OR REPLACE VIEW space_bindings AS
SELECT DISTINCT working_set_id, product_space_id, 'RELATION'::varchar AS origin, 'ACTIVE'::varchar AS state
FROM tracker_bindings;

-- ---------------------------------------------------------------------------
-- Requirements and LightTrackerItem index
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS requirements
(
    created_by     VARCHAR(50)                                                                  NOT NULL,
    created_time   BIGINT                                                                       NOT NULL,
    local_id       BIGINT PRIMARY KEY,
    common_id      BIGINT                                                                       NOT NULL,
    number         VARCHAR(200) GENERATED ALWAYS AS ('REQ-' || upper(to_hex(common_id))) STORED NOT NULL,
    tracker_id     BIGINT                                                                       NOT NULL REFERENCES air_inst_tracker_rev (id),
    title          TEXT                                                                         NOT NULL,
    description    TEXT                                                                         NOT NULL,
    revision       INTEGER                                                                      NOT NULL,
    version        VARCHAR(64),
    lock_version   BIGINT                                                                       NOT NULL,
    delete_flag    BOOLEAN                                                                      NOT NULL DEFAULT FALSE,
    working_set_id BIGINT                                                                       NOT NULL REFERENCES air_inst_workingset (id),
    name           VARCHAR(255)
);

-- POL-005/D2: one active or soft-deleted family per Working Set.
CREATE UNIQUE INDEX IF NOT EXISTS uq_requirements_working_set_family
    ON requirements (working_set_id, common_id);
CREATE INDEX IF NOT EXISTS idx_requirements_tracker ON requirements (tracker_id);
CREATE INDEX IF NOT EXISTS idx_requirement_active_tracker_local
    ON requirements (tracker_id, local_id) WHERE NOT delete_flag;

CREATE TABLE IF NOT EXISTS air_ws_items_index
(
    id                    BIGINT PRIMARY KEY,
    work_space_id         BIGINT NOT NULL CHECK (work_space_id > 0),
    workingset_id         BIGINT,
    master_id             BIGINT,
    parent_branch_id      BIGINT,
    branch_id             BIGINT,
    common_id             BIGINT,
    local_id              BIGINT,
    parent_local_id       BIGINT,
    source_local_id       BIGINT,
    revision              INTEGER,
    has_copied            SMALLINT,
    flags                 INTEGER,
    lock_version          BIGINT,
    created_by            VARCHAR(255),
    created_time          BIGINT,
    updated_by            VARCHAR(255),
    updated_time          BIGINT,
    source_revision       INTEGER,
    sub_class_id          BIGINT NOT NULL CHECK (sub_class_id > 0),
    effective_revision    INTEGER,
    effective_flags       VARCHAR(256),
    flag1                 BOOLEAN,
    flag2                 BOOLEAN,
    flag3                 BOOLEAN,
    flag4                 BOOLEAN,
    flag5                 BOOLEAN,
    effective_checked_out SMALLINT,
    CHECK (local_id IS NOT NULL AND common_id IS NOT NULL AND branch_id IS NOT NULL AND workingset_id IS NOT NULL
        AND revision > 0 AND revision IS NOT NULL AND source_revision > 0 AND source_revision IS NOT NULL
        AND source_local_id IS NOT NULL AND has_copied IS NOT NULL AND
           ((has_copied = 0 AND source_local_id <> local_id)
               OR (has_copied = 1 AND source_local_id = local_id AND source_revision = revision)))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_air_ws_items_local ON air_ws_items_index (local_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_air_ws_items_family ON air_ws_items_index (workingset_id, common_id);
CREATE INDEX IF NOT EXISTS idx_air_ws_items_source ON air_ws_items_index (source_local_id, source_revision);
CREATE INDEX IF NOT EXISTS idx_air_ws_items_branch ON air_ws_items_index (branch_id, local_id);

CREATE TABLE IF NOT EXISTS air_ws_items_revision_index
(
    id            BIGINT PRIMARY KEY,
    work_space_id BIGINT,
    workingset_id BIGINT,
    master_id     BIGINT,
    branch_id     BIGINT,
    common_id     BIGINT,
    local_id      BIGINT  NOT NULL,
    revision      INTEGER NOT NULL CHECK (revision > 0),
    flags         INTEGER,
    lock_version  BIGINT,
    created_by    VARCHAR(255),
    created_time  BIGINT,
    updated_by    VARCHAR(255),
    updated_time  BIGINT,
    sub_class_id  BIGINT,
    CONSTRAINT fk_air_ws_items_revision_owner
        FOREIGN KEY (local_id) REFERENCES air_ws_items_index (local_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_air_ws_items_revision ON air_ws_items_revision_index (local_id, revision);

CREATE TABLE IF NOT EXISTS requirement_snapshots
(
    snapshot_ref BIGINT PRIMARY KEY,
    local_id     BIGINT  NOT NULL,
    common_id    BIGINT  NOT NULL,
    tracker_id   BIGINT  NOT NULL,
    title        TEXT    NOT NULL,
    description  TEXT    NOT NULL,
    revision     INTEGER NOT NULL,
    version      VARCHAR(64),
    lock_version BIGINT  NOT NULL
);

-- ---------------------------------------------------------------------------
-- Copy-on-write recovery / diagnostics
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS cow_failures
(
    id                  BIGINT PRIMARY KEY,
    event_id            BIGINT       NOT NULL,
    target_local_id     BIGINT       NOT NULL,
    source_local_id     BIGINT       NOT NULL,
    before_revision     INTEGER      NOT NULL,
    after_revision      INTEGER      NOT NULL,
    before_lock_version BIGINT,
    after_lock_version  BIGINT,
    attempts            INTEGER      NOT NULL DEFAULT 1,
    stage               VARCHAR(32)  NOT NULL,
    error_type          VARCHAR(255) NOT NULL,
    message             TEXT,
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_cow_failures_source ON cow_failures (source_local_id);
CREATE INDEX IF NOT EXISTS idx_cow_failures_target ON cow_failures (target_local_id);

CREATE TABLE IF NOT EXISTS cow_materializations
(
    event_id        BIGINT      NOT NULL,
    target_local_id BIGINT      NOT NULL,
    source_local_id BIGINT      NOT NULL,
    source_revision INTEGER     NOT NULL,
    status          VARCHAR(32) NOT NULL,
    attempts        INTEGER     NOT NULL DEFAULT 0,
    business_saved  BOOLEAN     NOT NULL DEFAULT FALSE,
    last_error      TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, target_local_id)
);

CREATE INDEX IF NOT EXISTS idx_cow_materializations_target ON cow_materializations (target_local_id);
CREATE INDEX IF NOT EXISTS idx_cow_materializations_status ON cow_materializations (status);

-- ---------------------------------------------------------------------------
-- Merge LRO (compare itself is not persisted)
-- ---------------------------------------------------------------------------

-- 一轮 MergeRound 承载一页用户决策。requestId 是幂等键，必填且全局唯一。
CREATE TABLE IF NOT EXISTS merge_rounds
(
    id           BIGINT PRIMARY KEY,
    token        VARCHAR(64)  NOT NULL UNIQUE,
    request_id   VARCHAR(128) NOT NULL UNIQUE,
    state        JSONB        NOT NULL,
    tracker_a_id BIGINT,
    tracker_b_id BIGINT
);

-- 一页决策同属一个 Tracker 对；Preparing/Running 期间占用该对，作为并发合并闸门。
CREATE UNIQUE INDEX IF NOT EXISTS uq_merge_rounds_active_tracker_pair
    ON merge_rounds (tracker_a_id, tracker_b_id)
    WHERE (state ->> 'state') IN ('Preparing', 'Running');

-- One immutable row per confirmation. The entry's snowflake ID makes delivery idempotent.
CREATE TABLE IF NOT EXISTS air_ws_items_merge_index
(
    id              BIGINT PRIMARY KEY,
    batch_id        BIGINT    NOT NULL,
    merge_seq       BIGSERIAL NOT NULL,
    branch_a_id     BIGINT    NOT NULL,
    branch_b_id     BIGINT    NOT NULL,
    common_id       BIGINT    NOT NULL,
    local_id_a      BIGINT,
    local_id_b      BIGINT,
    revision_a      INTEGER,
    revision_b      INTEGER,
    merge_action    VARCHAR(255),
    diff_type       VARCHAR(255),
    merge_time      TIMESTAMP(6) WITHOUT TIME ZONE DEFAULT now(),
    merge_status    VARCHAR(32)                    DEFAULT 'MERGED',
    operator        VARCHAR(64),
    remark          TEXT,
    lock_version    INTEGER                        DEFAULT 0,
    created_by      VARCHAR(64),
    created_time    BIGINT,
    updated_by      VARCHAR(64),
    updated_time    BIGINT,
    merge_direction VARCHAR(8)                     DEFAULT 'A_TO_B',
    CHECK (branch_a_id <= branch_b_id),
    CHECK (merge_status IN ('MERGED', 'REVOKED'))
);

CREATE INDEX IF NOT EXISTS idx_air_ws_merge_current
    ON air_ws_items_merge_index (branch_a_id, branch_b_id, common_id, merge_seq DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_air_ws_merge_batch_range
    ON air_ws_items_merge_index (batch_id, branch_a_id, branch_b_id, merge_seq DESC);

CREATE TABLE IF NOT EXISTS requirement_merge_receipts
(
    round_id        BIGINT      NOT NULL,
    entry_id        BIGINT      NOT NULL,
    outcome         VARCHAR(32) NOT NULL,
    business_ref    BIGINT,
    source_revision INTEGER     NOT NULL,
    target_revision INTEGER     NOT NULL,
    created         BOOLEAN     NOT NULL,
    inherited       BOOLEAN     NOT NULL,
    failure         TEXT,
    PRIMARY KEY (round_id, entry_id)
);

-- ---------------------------------------------------------------------------
-- Working Set creation LRO
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS working_set_creations
(
    id                    BIGINT PRIMARY KEY,
    parent_working_set_id BIGINT       NOT NULL,
    new_working_set_id    BIGINT       NOT NULL,
    request_id            VARCHAR(128) NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    result                JSONB        NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    UNIQUE (parent_working_set_id, new_working_set_id, request_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_creation_target
    ON working_set_creations (new_working_set_id)
    WHERE result -> 'request' ->> 'mode' = 'CREATE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_creation_request_id
    ON working_set_creations (request_id)
    WHERE result -> 'request' ->> 'mode' = 'CREATE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_extension_target
    ON working_set_creations (new_working_set_id)
    WHERE (result -> 'request' ->> 'mode' = 'EXTEND'
        AND status IN ('Pending', 'Running'));

-- Common business instance index. The owning repository writes it in its own transaction;
-- there are no database triggers or automatic projections.
CREATE TABLE IF NOT EXISTS air_inst_common
(
    id            BIGINT PRIMARY KEY,
    base_class    BIGINT NOT NULL,
    class         BIGINT,
    type          BIGINT,
    work_space    BIGINT,
    number        VARCHAR(255),
    name          VARCHAR(2000),
    status        BIGINT,
    version       VARCHAR(255),
    revision      BIGINT,
    delete_flag   INTEGER,
    lock_version  BIGINT,
    created_by    VARCHAR(255),
    created_time  BIGINT,
    updated_by    VARCHAR(255),
    updated_time  BIGINT,
    checked_out   INTEGER,
    owner         BIGINT,
    common_id     BIGINT,
    workingset_id BIGINT,
    parent_id     BIGINT,
    tracker       BIGINT,
    max_revision  BIGINT
);

COMMENT ON COLUMN air_inst_common.owner IS '所有者';
COMMENT ON COLUMN air_inst_common.workingset_id IS '实例所属工作集';
COMMENT ON COLUMN air_inst_common.tracker IS '所属或自举 Tracker';

-- Soft-deleted rows still occupy the key. Unspecified scope or family remains NULL.
CREATE UNIQUE INDEX IF NOT EXISTS uq_air_inst_common_workingset_family
    ON air_inst_common (workingset_id, common_id);
CREATE INDEX IF NOT EXISTS idx_air_inst_common_tracker_workingset
    ON air_inst_common (tracker, workingset_id);

-- Bootstrap only the missing default root summary.
INSERT INTO air_inst_common (id, base_class, class, type, work_space, number, name, revision, delete_flag, lock_version,
                             created_by, created_time, updated_by, updated_time, owner, common_id, workingset_id,
                             parent_id, tracker, max_revision)
SELECT id,
       base_class,
       class,
       type,
       work_space,
       number,
       name,
       revision,
       delete_flag,
       lock_version,
       created_by,
       created_time,
       updated_by,
       updated_time,
       owner,
       common_id,
       workingset_id,
       parent_id,
       584190236948832,
       1
FROM air_inst_workingset
WHERE id = 1
ON CONFLICT (id) DO NOTHING;
