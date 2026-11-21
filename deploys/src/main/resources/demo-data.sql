-- Initialize the Demo dataset once per database after schema.sql.
-- Seed IDs use a fixed synthetic Snowflake time range (2024-01-01, worker 7/7).
DO '
BEGIN
CREATE OR REPLACE FUNCTION pg_temp.demo_id (n bigint) RETURNS bigint LANGUAGE sql IMMUTABLE
    AS ''SELECT ((126230400000 + n) << 11) | 2016'';

IF EXISTS (SELECT 1 FROM generate_series(101, 106) seed(n)
    JOIN product_spaces space ON space.product_space_id = pg_temp.demo_id(seed.n)) OR EXISTS (SELECT 1 FROM generate_series(200, 204) seed(n)
    JOIN air_inst_workingset workset ON workset.id = pg_temp.demo_id(seed.n)) THEN
    RETURN;
    END IF;

CREATE TEMP TABLE demo_spaces AS
    SELECT n::bigint AS n, name
    FROM (VALUES (1, ''整车平台''), (2, ''动力系统''), (3, ''底盘系统''), (4, ''智能座舱''),
        (5, ''车联网''), (6, ''质量与合规'')) AS space(n, name);

INSERT INTO product_spaces (product_space_id, name)
SELECT pg_temp.demo_id(100 + n), name FROM demo_spaces;

INSERT INTO air_inst_workingset
    (id, instance_id, common_id, workingset_id, parent_id, base_class, class, type, tracker,
        name, number, color, revision, version_type, delete_flag, lock_version, created_by, created_time, updated_by, updated_time)
SELECT pg_temp.demo_id(n), pg_temp.demo_id(n), pg_temp.demo_id(n), pg_temp.demo_id(n),
    CASE WHEN parent = 0 THEN 1 ELSE pg_temp.demo_id(parent) END,
    1008, 100801, 10080101, 584190236948832, name, ''WS-'' || pg_temp.demo_id(n), color,
    1, 10001, 0, 0, ''demo'', 1704067200000, ''demo'', 1704067200000
FROM (VALUES (200, 0, ''整车平台 V1'', ''#2563EB''),
    (201, 200, ''动力系统 V2'', ''#0F766E''),
    (202, 201, ''动力系统 V3'', ''#047857''),
    (203, 0, ''智能座舱 V1'', ''#7C3AED''),
    (204, 0, ''质量与合规评审'', ''#B45309'')) AS ws(n, parent, name, color)
ORDER BY n;

CREATE TEMP TABLE demo_trackers AS
    SELECT n AS space, t AS slot, pg_temp.demo_id(300 + n * 10 + t) AS id,
        name || (ARRAY [''功能需求'', ''验证要求'', ''公共规范''])[t] AS name,
        14010101::bigint AS classification,
        CASE WHEN t = 3 THEN ''SHARED'' ELSE ''UNSHARED'' END AS sharing
    FROM demo_spaces CROSS JOIN generate_series(1, 3) AS t;

CREATE TEMP TABLE demo_derivations
    (ws bigint, space bigint, slot integer, source_ws bigint);
INSERT INTO demo_derivations VALUES
    (200, 1, 1, 0), (200, 2, 1, 0), (200, 2, 2, 0), (200, 3, 1, 0),
    (201, 2, 1, 200), (201, 2, 2, 200),
    (202, 2, 1, 201),
    (203, 4, 1, 0), (203, 4, 2, 0), (203, 5, 1, 0),
    (204, 6, 1, 0), (204, 6, 2, 0);

CREATE TEMP TABLE demo_tracker_rows AS
    WITH space_trackers AS (SELECT pg_temp.demo_id(8000000 + row_number() OVER (ORDER BY owner_ws, work_space)) AS tracker_id,
        owner_ws, work_space
    FROM (SELECT 1::bigint AS owner_ws, pg_temp.demo_id(100 + n) AS work_space FROM demo_spaces
    UNION ALL
    SELECT DISTINCT pg_temp.demo_id(ws), pg_temp.demo_id(100 + space) FROM demo_derivations) AS scopes)
    SELECT id, id AS common_id, NULL::bigint AS source_id, 1::bigint AS owner_ws,
        pg_temp.demo_id(100 + space) AS work_space, name, classification,
        CASE WHEN sharing = ''SHARED'' THEN 1 ELSE 0 END AS share, (slot = 1)::int AS is_default
    FROM demo_trackers
    UNION ALL
    SELECT pg_temp.demo_id(d.ws * 10 + d.space * 10000 + d.slot), t.id,
        CASE WHEN d.source_ws = 0 THEN t.id ELSE pg_temp.demo_id(d.source_ws * 10 + d.space * 10000 + d.slot) END,
        pg_temp.demo_id(d.ws), pg_temp.demo_id(100 + d.space), t.name, t.classification, 0, (d.slot = 1)::int
    FROM demo_derivations d JOIN demo_trackers t ON t.space = d.space AND t.slot = d.slot
    UNION ALL
    SELECT current.tracker_id, root.tracker_id, parent.tracker_id, current.owner_ws, current.work_space,
        ''文件夹'', 10070101, 0, 1
    FROM space_trackers current
        JOIN space_trackers root ON root.owner_ws = 1 AND root.work_space = current.work_space
        LEFT JOIN air_inst_workingset ws ON ws.id = current.owner_ws AND current.owner_ws <> 1
        LEFT JOIN space_trackers parent ON parent.owner_ws = ws.parent_id AND parent.work_space = current.work_space;

INSERT INTO air_inst_tracker_rev
    (id, instance_id, common_id, source_branch_id, workingset_id, work_space, name, number, tracker_class,
        share, is_default, base_class, class, type, tracker, owner, revision, delete_flag, lock_version,
        created_by, created_time, updated_by, updated_time)
SELECT t.id, t.id, t.common_id, t.source_id, t.owner_ws, t.work_space, t.name, ''TR-'' || t.id, t.classification,
    t.share, t.is_default, c.base_class_id, c.class_id, c.classification_id,
    584578917040480, 1, 1, 0, 0, ''demo'', 1704067200000, ''demo'', 1704067200000
FROM demo_tracker_rows t JOIN classifications c ON c.classification_id = t.classification;

INSERT INTO air_ws_tracker
    (id, workingset_id, sub_class_id, source_branch_id, name, number, shared, is_default, visible, locked,
        delete_flag, lock_version, created_by, created_time, updated_by, updated_time)
SELECT id, owner_ws, classification, source_id, name, ''TR-'' || id, share, is_default, 1, 0,
    0, 0, ''demo'', 1704067200000, ''demo'', 1704067200000
FROM demo_tracker_rows;

INSERT INTO air_ws_workingset_branch_relation
    (id, workingset_id, work_space, tracker_id, lock_version, created_by, created_time, updated_by, updated_time)
SELECT pg_temp.demo_id(9000000 + row_number() OVER (ORDER BY owner_ws, tracker_id)),
    owner_ws, work_space, tracker_id, 0, ''demo'', 1704067200000, ''demo'', 1704067200000
FROM (SELECT owner_ws, work_space, id AS tracker_id FROM demo_tracker_rows
UNION ALL
SELECT pg_temp.demo_id(child.ws), pg_temp.demo_id(100 + child.space), shared.id
FROM (SELECT DISTINCT ws, space FROM demo_derivations) child
    JOIN demo_trackers shared ON shared.space = child.space AND shared.slot = 3) AS memberships;

CREATE TEMP TABLE demo_items AS
    SELECT pg_temp.demo_id(100000 + t.space * 1000 + t.slot * 100 + i) AS id, t.id AS tracker,
        t.space, t.slot, i,
        t.name || '' · '' || (ARRAY [''启动自检'',''访问控制'',''响应时限'',''状态上报'',''异常降级'',''数据保留'',
            ''诊断日志'',''故障恢复'',''版本兼容'',''性能监控'',''离线策略'',''安全审计'',''启停条件'',''告警规则'',
            ''接口校验'',''资源限额'',''权限复核'',''数据完整性'',''变更记录'',''联调验收''])[i] AS title
    FROM demo_trackers t CROSS JOIN generate_series(1, 20) AS i;

INSERT INTO requirements (local_id, common_id, tracker_id, title, name, description, revision, version, lock_version)
SELECT id, id, tracker, title, title, ''定义'' || title || ''的验收条件，记录预期结果和异常处理方式。'',
    CASE WHEN i % 5 = 0 THEN 2 ELSE 1 END, ''1.0'', 0
FROM demo_items;

WITH indexed (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized) AS (SELECT local_id, common_id, tracker_id, working_set_id, revision, local_id, revision, true FROM requirements)
INSERT INTO air_ws_items_index
    (id, local_id, common_id, branch_id, workingset_id, revision, source_local_id, source_revision, has_copied, flags,
        work_space_id, master_id, parent_branch_id, sub_class_id)
SELECT d.local_id, d.local_id, d.common_id, d.tracker_id, d.working_set_id,
    d.revision, d.source_local_id, d.source_revision, CASE WHEN d.materialized THEN 1 ELSE 0 END, 0,
    t.work_space, t.common_id, t.source_branch_id, 14010101
FROM indexed d JOIN air_inst_tracker_rev t ON t.id = d.tracker_id;

-- A sample of edited V1 copies supports viewing differences and comparing materialized content.
INSERT INTO requirements (local_id, common_id, tracker_id, title, name, description, revision, version, lock_version)
SELECT pg_temp.demo_id(200000 + d.ws * 1000 + d.space * 100 + d.slot * 20 + i.i),
    i.id, pg_temp.demo_id(d.ws * 10 + d.space * 10000 + d.slot),
    i.title || ''（V1 修订）'', i.title || ''（V1 修订）'',
    ''新增回归验收条件，记录系统状态和边界场景。'', 1, ''1.0'', 0
FROM demo_derivations d JOIN demo_items i ON i.space = d.space AND i.slot = d.slot
WHERE d.ws = 200 AND i.i <= 3;

WITH indexed (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized) AS (SELECT pg_temp.demo_id(200000 + d.ws * 1000 + d.space * 100 + d.slot * 20 + i.i),
    i.id, pg_temp.demo_id(d.ws * 10 + d.space * 10000 + d.slot), pg_temp.demo_id(d.ws),
    CASE WHEN i.i % 5 = 0 THEN 2 ELSE 1 END,
    CASE WHEN d.ws IN (200, 201, 202) AND i.i <= 3
        THEN pg_temp.demo_id(200000 + 200 * 1000 + d.space * 100 + d.slot * 20 + i.i)
        ELSE i.id END,
    CASE WHEN i.i % 5 = 0 THEN 2 ELSE 1 END,
    d.ws = 200 AND i.i <= 3
FROM demo_derivations d JOIN demo_items i ON i.space = d.space AND i.slot = d.slot)
INSERT INTO air_ws_items_index
    (id, local_id, common_id, branch_id, workingset_id, revision, source_local_id, source_revision, has_copied, flags,
        work_space_id, master_id, parent_branch_id, sub_class_id)
SELECT d.local_id, d.local_id, d.common_id, d.tracker_id, d.working_set_id,
    d.revision, d.source_local_id, d.source_revision, CASE WHEN d.materialized THEN 1 ELSE 0 END, 0,
    t.work_space, t.common_id, t.source_branch_id, 14010101
FROM indexed d JOIN air_inst_tracker_rev t ON t.id = d.tracker_id;

INSERT INTO air_ws_items_revision_index
    (id, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, revision, sub_class_id)
SELECT id, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, revision, sub_class_id
FROM air_ws_items_index;

-- Demo items currently at revision 2 also have a real revision 1 snapshot and index member.
INSERT INTO requirement_snapshots
    (snapshot_ref, local_id, common_id, tracker_id, title, description, revision, version, lock_version)
SELECT pg_temp.demo_id(9500000 + row_number() OVER (ORDER BY local_id)),
    local_id, common_id, tracker_id, title, ''首次登记：'' || title, 1, version, 0
FROM requirements WHERE revision = 2;

INSERT INTO air_ws_items_revision_index
    (id, work_space_id, workingset_id, master_id, branch_id, common_id, local_id, revision, sub_class_id)
SELECT s.snapshot_ref, i.work_space_id, i.workingset_id, i.master_id, i.branch_id, i.common_id, i.local_id,
    s.revision, i.sub_class_id
FROM requirement_snapshots s JOIN air_ws_items_index i ON i.local_id = s.local_id;

-- Public summaries are part of the initial Demo dataset, including inherited item summaries.
WITH source_rows (
    id, base_class, class, type, work_space, number, name, version, revision, delete_flag, lock_version,
    created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id,
    parent_id, tracker, max_revision
    ) AS (SELECT id, base_class, class, type, work_space, number, name, NULL::VARCHAR, revision, delete_flag, lock_version,
    created_by, created_time, updated_by, updated_time, NULL::INTEGER, owner, common_id, workingset_id,
    parent_id, 584190236948832, 1
FROM air_inst_workingset WHERE id <> 1
UNION ALL
SELECT id, base_class, class, type, work_space, number, name, version, revision, delete_flag, lock_version,
    created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id,
    parent_id, 584578917040480, 1
FROM air_inst_tracker_rev
UNION ALL
SELECT product_space_id, 1003, 100301, 10030101, product_space_id, NULL, name, NULL, 1, 0, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, product_space_id, 1, NULL, 584189545406816, 1
FROM product_spaces
UNION ALL
SELECT r.local_id, 1401, 140101, 14010101, t.work_space, r.number, COALESCE(r.name, r.title),
    r.version, r.revision, CASE WHEN r.delete_flag THEN 1 ELSE 0 END, r.lock_version,
    NULL, NULL, NULL, NULL, NULL, NULL, r.common_id, r.working_set_id, NULL, r.tracker_id,
    GREATEST(r.revision, h.max_revision)
FROM requirements r
    JOIN air_inst_tracker_rev t ON t.id = r.tracker_id
    LEFT JOIN (SELECT local_id, max(revision) AS max_revision FROM requirement_snapshots GROUP BY local_id) h
    ON h.local_id = r.local_id
UNION ALL
SELECT l.local_id, 1401, 140101, 14010101, t.work_space, r.number,
    CASE WHEN r.revision = l.source_revision THEN COALESCE(r.name, r.title) END,
    CASE WHEN r.revision = l.source_revision THEN r.version END,
    l.revision, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    l.common_id, l.workingset_id, NULL, l.branch_id, l.revision
FROM air_ws_items_index l
    JOIN requirements r ON r.local_id = l.source_local_id
    JOIN air_inst_tracker_rev t ON t.id = l.branch_id
WHERE l.has_copied = 0 AND NOT EXISTS (SELECT 1 FROM requirements m WHERE m.local_id = l.local_id))
INSERT INTO air_inst_common (id, base_class, class, type, work_space, number, name, version, revision, delete_flag, lock_version,
    created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id,
    parent_id, tracker, max_revision)
SELECT * FROM source_rows;

DROP TABLE demo_items, demo_tracker_rows, demo_derivations, demo_trackers, demo_spaces;
END';
