-- Demo/UI data reset.
-- Activated only by the "demo" profile (see application-demo.yml), so normal
-- tests and production-like runs are not affected.
--
-- Keeps the existing classification definitions and the default Working Set,
-- while replacing all Working Sets, Product Spaces, Trackers, Requirements
-- and LightTrackerItems with a readable, multi-level traceability sample.

TRUNCATE TABLE
    light_tracker_items,
    requirements,
    requirement_snapshots,
    cow_failures,
    cow_materializations,
    tracker_bindings,
    space_bindings,
    trackers,
    working_sets,
    product_spaces,
    merge_checkpoints,
    merge_commit_receipts,
    merge_rounds,
    merge_comparisons,
    working_set_creations
CASCADE;

-- The Modulith publication table is created by its own schema initializer; it
-- is intentionally not touched here so this script works on a fresh database.
-- Demo resets are only for local UI data and do not replay creation tasks.

-- Default Working Set is the root of every demo hierarchy.
INSERT INTO working_sets (working_set_id, parent_working_set_id, name)
VALUES ('1', NULL, '默认工作集');

-- Demo-only classifications; existing classification rows are untouched.
INSERT INTO classifications (classification_id, sharing_mode) VALUES
    ('CL-SAFETY-UNSHARED', 'UNSHARED'),
    ('CL-PERF-UNSHARED', 'UNSHARED'),
    ('CL-PT-UNSHARED', 'UNSHARED'),
    ('CL-CHASSIS-SHARED', 'SHARED'),
    ('CL-INFOTAINMENT-SHARED', 'SHARED')
ON CONFLICT (classification_id) DO NOTHING;

INSERT INTO product_spaces (product_space_id, name) VALUES
    ('PS-VEHICLE', '整车平台'),
    ('PS-POWERTRAIN', '动力系统'),
    ('PS-CHASSIS', '底盘系统'),
    ('PS-INFOTAINMENT', '智能座舱');

-- Multi-level Working Set chain:
-- 1 -> WS-VEHICLE-V1 -> WS-POWERTRAIN-V2 -> WS-POWERTRAIN-V3
-- and a sibling branch 1 -> WS-CHASSIS-V1.
INSERT INTO working_sets (working_set_id, parent_working_set_id, name) VALUES
    ('WS-VEHICLE-V1', '1', '整车需求工作集 V1'),
    ('WS-POWERTRAIN-V2', 'WS-VEHICLE-V1', '动力系统需求工作集 V2'),
    ('WS-POWERTRAIN-V3', 'WS-POWERTRAIN-V2', '动力系统需求工作集 V3'),
    ('WS-CHASSIS-V1', '1', '底盘需求工作集 V1');

-- Default Working Set trackers (business-native; source_id is null).
INSERT INTO trackers
    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
VALUES
    ('T-VEH-SAFETY', '整车安全需求', NULL, '1', 'PS-VEHICLE', 'CL-SAFETY-UNSHARED', 'UNSHARED', NULL),
    ('T-VEH-PERF', '整车性能需求', NULL, '1', 'PS-VEHICLE', 'CL-PERF-UNSHARED', 'UNSHARED', 'T-VEH-SAFETY'),
    ('T-PT-REQ', '动力系统需求', NULL, '1', 'PS-POWERTRAIN', 'CL-PT-UNSHARED', 'UNSHARED', NULL),
    ('T-CHASSIS-REQ', '底盘需求', NULL, '1', 'PS-CHASSIS', 'CL-CHASSIS-SHARED', 'SHARED', NULL),
    ('T-INFOTAINMENT-REQ', '智能座舱需求', NULL, '1', 'PS-INFOTAINMENT', 'CL-INFOTAINMENT-SHARED', 'SHARED', NULL);

-- V1 copies the two vehicle-level unshared trackers and the powertrain tracker.
INSERT INTO trackers
    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
VALUES
    ('T-VEH-SAFETY-V1', '整车安全需求（V1）', 'T-VEH-SAFETY', 'WS-VEHICLE-V1', 'PS-VEHICLE',
     'CL-SAFETY-UNSHARED', 'UNSHARED', NULL),
    ('T-VEH-PERF-V1', '整车性能需求（V1）', 'T-VEH-PERF', 'WS-VEHICLE-V1', 'PS-VEHICLE',
     'CL-PERF-UNSHARED', 'UNSHARED', 'T-VEH-SAFETY-V1'),
    ('T-PT-REQ-V1', '动力系统需求（V1）', 'T-PT-REQ', 'WS-VEHICLE-V1', 'PS-POWERTRAIN',
     'CL-PT-UNSHARED', 'UNSHARED', NULL);

-- V2 and V3 continue the powertrain tracker chain.
INSERT INTO trackers
    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
VALUES
    ('T-PT-REQ-V2', '动力系统需求（V2）', 'T-PT-REQ-V1', 'WS-POWERTRAIN-V2', 'PS-POWERTRAIN',
     'CL-PT-UNSHARED', 'UNSHARED', NULL),
    ('T-PT-REQ-V3', '动力系统需求（V3）', 'T-PT-REQ-V2', 'WS-POWERTRAIN-V3', 'PS-POWERTRAIN',
     'CL-PT-UNSHARED', 'UNSHARED', NULL);

INSERT INTO space_bindings (working_set_id, product_space_id, origin, state) VALUES
    ('1', 'PS-VEHICLE', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('1', 'PS-POWERTRAIN', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('1', 'PS-CHASSIS', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('1', 'PS-INFOTAINMENT', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('WS-VEHICLE-V1', 'PS-VEHICLE', 'EXPLICIT', 'ACTIVE'),
    ('WS-VEHICLE-V1', 'PS-POWERTRAIN', 'EXPLICIT', 'ACTIVE'),
    ('WS-VEHICLE-V1', 'PS-CHASSIS', 'EXPLICIT', 'ACTIVE'),
    ('WS-POWERTRAIN-V2', 'PS-POWERTRAIN', 'EXPLICIT', 'ACTIVE'),
    ('WS-POWERTRAIN-V3', 'PS-POWERTRAIN', 'EXPLICIT', 'ACTIVE'),
    ('WS-CHASSIS-V1', 'PS-CHASSIS', 'EXPLICIT', 'ACTIVE');

INSERT INTO tracker_bindings
    (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
VALUES
    ('1', 'T-VEH-SAFETY', 'PS-VEHICLE', 'CL-SAFETY-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('1', 'T-VEH-PERF', 'PS-VEHICLE', 'CL-PERF-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('1', 'T-PT-REQ', 'PS-POWERTRAIN', 'CL-PT-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('1', 'T-CHASSIS-REQ', 'PS-CHASSIS', 'CL-CHASSIS-SHARED', 'SHARED', 'SHARED_REUSE'),
    ('1', 'T-INFOTAINMENT-REQ', 'PS-INFOTAINMENT', 'CL-INFOTAINMENT-SHARED', 'SHARED', 'SHARED_REUSE'),
    ('WS-VEHICLE-V1', 'T-VEH-SAFETY-V1', 'PS-VEHICLE', 'CL-SAFETY-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-VEHICLE-V1', 'T-VEH-PERF-V1', 'PS-VEHICLE', 'CL-PERF-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-VEHICLE-V1', 'T-PT-REQ-V1', 'PS-POWERTRAIN', 'CL-PT-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-VEHICLE-V1', 'T-CHASSIS-REQ', 'PS-CHASSIS', 'CL-CHASSIS-SHARED', 'SHARED', 'SHARED_REUSE'),
    ('WS-POWERTRAIN-V2', 'T-PT-REQ-V2', 'PS-POWERTRAIN', 'CL-PT-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-POWERTRAIN-V3', 'T-PT-REQ-V3', 'PS-POWERTRAIN', 'CL-PT-UNSHARED', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-CHASSIS-V1', 'T-CHASSIS-REQ', 'PS-CHASSIS', 'CL-CHASSIS-SHARED', 'SHARED', 'SHARED_REUSE');

-- Business-native requirements in the default Working Set.
INSERT INTO requirements
    (local_id, common_id, tracker_id, name, title, description, revision, version, lock_version, delete_flag)
VALUES
    ('REQ-SAFETY-1', 'FAM-SAFETY-1', 'T-VEH-SAFETY', '电池热失控预警', '电池热失控预警',
     '热失控前 5 分钟预警并触发冷却', 1, 'V1.0', 0, FALSE),
    ('REQ-SAFETY-2', 'FAM-SAFETY-2', 'T-VEH-SAFETY', '碰撞后高压断电', '碰撞后高压断电',
     '碰撞信号有效后 100ms 内切断高压', 2, 'V1.1', 0, FALSE),
    ('REQ-PERF-1', 'FAM-PERF-1', 'T-VEH-PERF', '整车续航里程', '整车续航里程',
     'CLTC 工况续航不低于 600km', 1, 'V1.0', 0, FALSE),
    ('REQ-PERF-2', 'FAM-PERF-2', 'T-VEH-PERF', '快充时间', '快充时间',
     '30%-80% 充电时间不超过 25 分钟', 3, 'V1.2', 0, FALSE),
    ('REQ-PT-1', 'FAM-PT-1', 'T-PT-REQ', '电机峰值功率', '电机峰值功率',
     '后驱电机峰值功率不低于 200kW', 1, 'V1.0', 0, FALSE),
    ('REQ-CH-1', 'FAM-CH-1', 'T-CHASSIS-REQ', '制动距离', '制动距离',
     '100km/h 干地制动距离不超过 36m', 1, 'V1.0', 0, FALSE),
    ('REQ-INF-1', 'FAM-INF-1', 'T-INFOTAINMENT-REQ', '语音唤醒延迟', '语音唤醒延迟',
     '语音唤醒响应时间不超过 500ms', 1, 'V1.0', 0, FALSE);

-- Root LTI rows make the default/business-native items visible through the
-- same Tracker item endpoint used by the UI.
INSERT INTO light_tracker_items
    (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized)
VALUES
    ('REQ-SAFETY-1', 'FAM-SAFETY-1', 'T-VEH-SAFETY', '1', 1, 'REQ-SAFETY-1', 1, TRUE),
    ('REQ-SAFETY-2', 'FAM-SAFETY-2', 'T-VEH-SAFETY', '1', 2, 'REQ-SAFETY-2', 2, TRUE),
    ('REQ-PERF-1', 'FAM-PERF-1', 'T-VEH-PERF', '1', 1, 'REQ-PERF-1', 1, TRUE),
    ('REQ-PERF-2', 'FAM-PERF-2', 'T-VEH-PERF', '1', 3, 'REQ-PERF-2', 3, TRUE),
    ('REQ-PT-1', 'FAM-PT-1', 'T-PT-REQ', '1', 1, 'REQ-PT-1', 1, TRUE),
    ('REQ-CH-1', 'FAM-CH-1', 'T-CHASSIS-REQ', '1', 1, 'REQ-CH-1', 1, TRUE),
    ('REQ-INF-1', 'FAM-INF-1', 'T-INFOTAINMENT-REQ', '1', 1, 'REQ-INF-1', 1, TRUE);

-- V1 indexes every copied family; source points back to the default
-- requirement, so content and names remain traceable to the root.
INSERT INTO light_tracker_items
    (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized)
VALUES
    ('LTI-V1-SAFETY-1', 'FAM-SAFETY-1', 'T-VEH-SAFETY-V1', 'WS-VEHICLE-V1', 1, 'REQ-SAFETY-1', 1, FALSE),
    ('LTI-V1-SAFETY-2', 'FAM-SAFETY-2', 'T-VEH-SAFETY-V1', 'WS-VEHICLE-V1', 2, 'REQ-SAFETY-2', 2, FALSE),
    ('LTI-V1-PERF-1', 'FAM-PERF-1', 'T-VEH-PERF-V1', 'WS-VEHICLE-V1', 1, 'REQ-PERF-1', 1, FALSE),
    ('LTI-V1-PERF-2', 'FAM-PERF-2', 'T-VEH-PERF-V1', 'WS-VEHICLE-V1', 3, 'REQ-PERF-2', 3, FALSE),
    ('LTI-V1-PT-1', 'FAM-PT-1', 'T-PT-REQ-V1', 'WS-VEHICLE-V1', 1, 'REQ-PT-1', 1, FALSE);

-- V2/V3 index the same powertrain family. The tracker source chain
-- (T-PT-REQ -> ...-V1 -> ...-V2 -> ...-V3) is the visible lineage.
INSERT INTO light_tracker_items
    (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized)
VALUES
    ('LTI-V2-PT-1', 'FAM-PT-1', 'T-PT-REQ-V2', 'WS-POWERTRAIN-V2', 1, 'REQ-PT-1', 1, FALSE),
    ('LTI-V3-PT-1', 'FAM-PT-1', 'T-PT-REQ-V3', 'WS-POWERTRAIN-V3', 1, 'REQ-PT-1', 1, FALSE);
