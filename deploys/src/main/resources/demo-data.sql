-- Runs after reset-demo-schema.sql and schema.sql, only against the dedicated Demo database.
-- Seed IDs use a fixed synthetic Snowflake time range (2024-01-01, worker 7/7).
CREATE OR REPLACE FUNCTION pg_temp.demo_id(n bigint) RETURNS bigint LANGUAGE sql IMMUTABLE
AS 'SELECT ((126230400000 + n) << 11) | 2016';

CREATE TEMP TABLE demo_spaces AS
SELECT n::bigint AS n, name
FROM (VALUES (1, '整车平台'), (2, '动力系统'), (3, '底盘系统'), (4, '智能座舱'),
             (5, '车联网'), (6, '质量与合规')) AS space(n, name);

INSERT INTO product_spaces (product_space_id, name)
SELECT pg_temp.demo_id(100 + n), name FROM demo_spaces;

INSERT INTO classifications (classification_id, sharing_mode, name)
VALUES (pg_temp.demo_id(1), 'SHARED', '公共规范');

INSERT INTO working_sets (working_set_id, parent_working_set_id, name) VALUES
    (pg_temp.demo_id(200), 1, '整车平台 V1'),
    (pg_temp.demo_id(201), pg_temp.demo_id(200), '动力系统 V2'),
    (pg_temp.demo_id(202), pg_temp.demo_id(201), '动力系统 V3'),
    (pg_temp.demo_id(203), 1, '智能座舱 V1'),
    (pg_temp.demo_id(204), 1, '质量与合规评审');

CREATE TEMP TABLE demo_trackers AS
SELECT n AS space, t AS slot, pg_temp.demo_id(300 + n * 10 + t) AS id,
       name || (ARRAY['功能需求', '验证要求', '公共规范'])[t] AS name,
       CASE WHEN t = 3 THEN pg_temp.demo_id(1) ELSE pg_temp.demo_id(0) END AS classification,
       CASE WHEN t = 3 THEN 'SHARED' ELSE 'UNSHARED' END AS sharing
FROM demo_spaces CROSS JOIN generate_series(1, 3) AS t;

INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode, is_default)
SELECT id, name, 1, pg_temp.demo_id(100 + space), classification, sharing, slot <> 2
FROM demo_trackers;

CREATE TEMP TABLE demo_derivations (ws bigint, space bigint, slot integer, source_ws bigint);
INSERT INTO demo_derivations VALUES
    (200, 1, 1, 0), (200, 2, 1, 0), (200, 2, 2, 0), (200, 3, 1, 0),
    (201, 2, 1, 200), (201, 2, 2, 200),
    (202, 2, 1, 201),
    (203, 4, 1, 0), (203, 4, 2, 0), (203, 5, 1, 0),
    (204, 6, 1, 0), (204, 6, 2, 0);

INSERT INTO trackers (tracker_id, name, source_id, working_set_id, product_space_id,
                      classification_id, sharing_mode, is_default)
SELECT pg_temp.demo_id(d.ws * 10 + d.space * 10000 + d.slot),
       t.name,
       CASE WHEN d.source_ws = 0 THEN t.id ELSE pg_temp.demo_id(d.source_ws * 10 + d.space * 10000 + d.slot) END,
       pg_temp.demo_id(d.ws), pg_temp.demo_id(100 + d.space), t.classification, t.sharing, d.slot = 1
FROM demo_derivations d JOIN demo_trackers t ON t.space = d.space AND t.slot = d.slot
ORDER BY d.ws, d.space, d.slot;

INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode, is_default)
SELECT pg_temp.demo_id(8000000 + row_number() OVER (ORDER BY owner, space)),
       '文件夹', owner, space, 10070101, 'UNSHARED', TRUE
FROM (
    SELECT 1 AS owner, pg_temp.demo_id(100 + n) AS space FROM demo_spaces
    UNION ALL
    SELECT DISTINCT pg_temp.demo_id(ws), pg_temp.demo_id(100 + space) FROM demo_derivations
) AS folders;

INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
SELECT working_set_id, tracker_id, product_space_id, classification_id, sharing_mode,
       CASE WHEN sharing_mode = 'SHARED' THEN 'SHARED_REUSE' ELSE 'UNSHARED_DERIVATION' END
FROM trackers
UNION ALL
SELECT pg_temp.demo_id(child.ws), shared.id, pg_temp.demo_id(100 + child.space),
       shared.classification, 'SHARED', 'SHARED_REUSE'
FROM (SELECT DISTINCT ws, space FROM demo_derivations) child
JOIN demo_trackers shared ON shared.space = child.space AND shared.slot = 3;

CREATE TEMP TABLE demo_items AS
SELECT pg_temp.demo_id(100000 + t.space * 1000 + t.slot * 100 + i) AS id, t.id AS tracker,
       t.space, t.slot, i,
       t.name || ' · ' || (ARRAY['启动自检','访问控制','响应时限','状态上报','异常降级','数据保留',
           '诊断日志','故障恢复','版本兼容','性能监控','离线策略','安全审计','启停条件','告警规则',
           '接口校验','资源限额','权限复核','数据完整性','变更记录','联调验收'])[i] AS title
FROM demo_trackers t CROSS JOIN generate_series(1, 20) AS i;

INSERT INTO requirements (local_id, common_id, tracker_id, title, name, description, revision, version, lock_version)
SELECT id, id, tracker, title, title, '定义' || title || '的验收条件，记录预期结果和异常处理方式。',
       CASE WHEN i % 5 = 0 THEN 2 ELSE 1 END, '1.0', 0
FROM demo_items;

INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                                 source_local_id, source_revision, materialized)
SELECT local_id, common_id, tracker_id, working_set_id, revision, local_id, revision, true FROM requirements;

-- A sample of edited V1 copies supports viewing differences and comparing materialized content.
INSERT INTO requirements (local_id, common_id, tracker_id, title, name, description, revision, version, lock_version)
SELECT pg_temp.demo_id(200000 + d.ws * 1000 + d.space * 100 + d.slot * 20 + i.i),
       i.id, pg_temp.demo_id(d.ws * 10 + d.space * 10000 + d.slot),
       i.title || '（V1 修订）', i.title || '（V1 修订）',
       '新增回归验收条件，记录系统状态和边界场景。', 1, '1.0', 0
FROM demo_derivations d JOIN demo_items i ON i.space = d.space AND i.slot = d.slot
WHERE d.ws = 200 AND i.i <= 3;

INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                                 source_local_id, source_revision, materialized)
SELECT pg_temp.demo_id(200000 + d.ws * 1000 + d.space * 100 + d.slot * 20 + i.i),
       i.id, pg_temp.demo_id(d.ws * 10 + d.space * 10000 + d.slot), pg_temp.demo_id(d.ws),
       CASE WHEN i.i % 5 = 0 THEN 2 ELSE 1 END,
       CASE WHEN d.ws IN (200, 201, 202) AND i.i <= 3
            THEN pg_temp.demo_id(200000 + 200 * 1000 + d.space * 100 + d.slot * 20 + i.i)
            ELSE i.id END,
       CASE WHEN i.i % 5 = 0 THEN 2 ELSE 1 END,
       d.ws = 200 AND i.i <= 3
FROM demo_derivations d JOIN demo_items i ON i.space = d.space AND i.slot = d.slot;

DROP TABLE demo_items, demo_derivations, demo_trackers, demo_spaces;
