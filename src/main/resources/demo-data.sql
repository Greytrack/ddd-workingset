-- Demo/UI data. Loaded only by the "demo" profile (application-demo.yml).
--
-- The authoritative schema presets exactly two rows: the empty default Working
-- Set ('1') and the single requirement category CL-REQUIREMENT (UNSHARED).
-- This script clears business data, keeps those two presets, and rebuilds a
-- readable traceability sample on top of them:
--
--   * one requirement category only (CL-REQUIREMENT, unshared);
--   * a tabular binding key (product space, classification) allows a Working
--     Set to bind exactly one Tracker per space, so the sample uses one Tracker
--     per product space;
--   * Working Set chain 1 -> WS-VEHICLE-V1 -> WS-POWERTRAIN-V2 -> WS-POWERTRAIN-V3
--     plus the sibling branch 1 -> WS-COCKPIT-V1;
--   * every child Tracker is an unshared derivation of its parent Tracker, and
--     every child item is a source-backed index of the requirement content that
--     the default Working Set owns.
--
-- The script is repeatable: it starts from the schema baseline every time.

TRUNCATE TABLE
    working_set_creation_items,
    working_set_creation_snapshots,
    working_set_creations,
    merge_commit_receipts,
    merge_checkpoints,
    merge_rounds,
    light_tracker_items,
    requirements,
    requirement_snapshots,
    cow_failures,
    cow_materializations,
    tracker_bindings,
    space_bindings,
    trackers
CASCADE;

-- Keep the schema presets, drop everything else.
DELETE FROM product_spaces;
DELETE FROM working_sets WHERE working_set_id <> '1';
DELETE FROM classifications WHERE classification_id <> 'CL-REQUIREMENT';

-- Re-assert the presets so the script also works if they were removed by hand.
INSERT INTO working_sets (working_set_id, parent_working_set_id, name)
VALUES ('1', NULL, '默认工作集')
ON CONFLICT (working_set_id) DO NOTHING;

INSERT INTO classifications (classification_id, sharing_mode)
VALUES ('CL-REQUIREMENT', 'UNSHARED')
ON CONFLICT (classification_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Product spaces and Working Set lineage
-- ---------------------------------------------------------------------------

INSERT INTO product_spaces (product_space_id, name) VALUES
    ('PS-VEHICLE', '整车平台'),
    ('PS-POWERTRAIN', '动力系统'),
    ('PS-CHASSIS', '底盘系统'),
    ('PS-COCKPIT', '智能座舱');

INSERT INTO working_sets (working_set_id, parent_working_set_id, name) VALUES
    ('WS-VEHICLE-V1', '1', '整车需求工作集 V1'),
    ('WS-POWERTRAIN-V2', 'WS-VEHICLE-V1', '动力需求工作集 V2'),
    ('WS-POWERTRAIN-V3', 'WS-POWERTRAIN-V2', '动力需求工作集 V3'),
    ('WS-COCKPIT-V1', '1', '座舱需求工作集 V1');

-- ---------------------------------------------------------------------------
-- Trackers: business-native in the default Working Set, derived in children
-- ---------------------------------------------------------------------------

-- Business-native trackers (source_id is null) live in the default Working Set.
INSERT INTO trackers
    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
VALUES
    ('T-VEH-SAFETY', '整车安全需求', NULL, '1', 'PS-VEHICLE', 'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-PT-REQ', '动力系统需求', NULL, '1', 'PS-POWERTRAIN', 'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-CHASSIS-REQ', '底盘需求', NULL, '1', 'PS-CHASSIS', 'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-COCKPIT-REQ', '座舱需求', NULL, '1', 'PS-COCKPIT', 'CL-REQUIREMENT', 'UNSHARED', NULL);

-- V1 derives three trackers from the default Working Set.
INSERT INTO trackers
    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
VALUES
    ('T-VEH-SAFETY-V1', '整车安全需求（V1）', 'T-VEH-SAFETY', 'WS-VEHICLE-V1', 'PS-VEHICLE',
     'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-PT-REQ-V1', '动力系统需求（V1）', 'T-PT-REQ', 'WS-VEHICLE-V1', 'PS-POWERTRAIN',
     'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-CHASSIS-REQ-V1', '底盘需求（V1）', 'T-CHASSIS-REQ', 'WS-VEHICLE-V1', 'PS-CHASSIS',
     'CL-REQUIREMENT', 'UNSHARED', NULL);

-- The powertrain tracker keeps chaining through V2 and V3.
INSERT INTO trackers
    (tracker_id, name, source_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
VALUES
    ('T-PT-REQ-V2', '动力系统需求（V2）', 'T-PT-REQ-V1', 'WS-POWERTRAIN-V2', 'PS-POWERTRAIN',
     'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-PT-REQ-V3', '动力系统需求（V3）', 'T-PT-REQ-V2', 'WS-POWERTRAIN-V3', 'PS-POWERTRAIN',
     'CL-REQUIREMENT', 'UNSHARED', NULL),
    ('T-COCKPIT-REQ-V1', '座舱需求（V1）', 'T-COCKPIT-REQ', 'WS-COCKPIT-V1', 'PS-COCKPIT',
     'CL-REQUIREMENT', 'UNSHARED', NULL);

-- ---------------------------------------------------------------------------
-- Bindings
-- ---------------------------------------------------------------------------

INSERT INTO space_bindings (working_set_id, product_space_id, origin, state) VALUES
    ('1', 'PS-VEHICLE', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('1', 'PS-POWERTRAIN', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('1', 'PS-CHASSIS', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('1', 'PS-COCKPIT', 'INITIAL_PROJECTION', 'ACTIVE'),
    ('WS-VEHICLE-V1', 'PS-VEHICLE', 'EXPLICIT', 'ACTIVE'),
    ('WS-VEHICLE-V1', 'PS-POWERTRAIN', 'EXPLICIT', 'ACTIVE'),
    ('WS-VEHICLE-V1', 'PS-CHASSIS', 'EXPLICIT', 'ACTIVE'),
    ('WS-POWERTRAIN-V2', 'PS-POWERTRAIN', 'EXPLICIT', 'ACTIVE'),
    ('WS-POWERTRAIN-V3', 'PS-POWERTRAIN', 'EXPLICIT', 'ACTIVE'),
    ('WS-COCKPIT-V1', 'PS-COCKPIT', 'EXPLICIT', 'ACTIVE');

INSERT INTO tracker_bindings
    (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
VALUES
    ('1', 'T-VEH-SAFETY', 'PS-VEHICLE', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('1', 'T-PT-REQ', 'PS-POWERTRAIN', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('1', 'T-CHASSIS-REQ', 'PS-CHASSIS', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('1', 'T-COCKPIT-REQ', 'PS-COCKPIT', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-VEHICLE-V1', 'T-VEH-SAFETY-V1', 'PS-VEHICLE', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-VEHICLE-V1', 'T-PT-REQ-V1', 'PS-POWERTRAIN', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-VEHICLE-V1', 'T-CHASSIS-REQ-V1', 'PS-CHASSIS', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-POWERTRAIN-V2', 'T-PT-REQ-V2', 'PS-POWERTRAIN', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-POWERTRAIN-V3', 'T-PT-REQ-V3', 'PS-POWERTRAIN', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION'),
    ('WS-COCKPIT-V1', 'T-COCKPIT-REQ-V1', 'PS-COCKPIT', 'CL-REQUIREMENT', 'UNSHARED', 'UNSHARED_DERIVATION');

-- ---------------------------------------------------------------------------
-- Requirement content owned by the default Working Set
-- ---------------------------------------------------------------------------
-- working_set_id is derived from the Tracker by trg_requirements_working_set.
-- revision / version are the content revision the derived items point at.

INSERT INTO requirements
    (local_id, common_id, tracker_id, name, title, description, revision, version, lock_version, delete_flag)
VALUES
    ('REQ-SAFETY-1', 'FAM-SAFETY-1', 'T-VEH-SAFETY', '电池热失控预警', '电池热失控预警',
     '热失控前 5 分钟预警并触发主动冷却', 1, 'V1.0', 0, FALSE),
    ('REQ-SAFETY-2', 'FAM-SAFETY-2', 'T-VEH-SAFETY', '碰撞后高压断电', '碰撞后高压断电',
     '碰撞信号有效后 100ms 内切断高压回路', 2, 'V1.1', 0, FALSE),
    ('REQ-PT-1', 'FAM-PT-1', 'T-PT-REQ', '电机峰值功率', '电机峰值功率',
     '后驱电机峰值功率不低于 200kW', 1, 'V1.0', 0, FALSE),
    ('REQ-PT-2', 'FAM-PT-2', 'T-PT-REQ', '快充时间', '快充时间',
     '30%-80% 充电时间不超过 25 分钟', 3, 'V1.2', 0, FALSE),
    ('REQ-CHASSIS-1', 'FAM-CHASSIS-1', 'T-CHASSIS-REQ', '制动距离', '制动距离',
     '100km/h 干地制动距离不超过 36m', 1, 'V1.0', 0, FALSE),
    ('REQ-CHASSIS-2', 'FAM-CHASSIS-2', 'T-CHASSIS-REQ', '转向手感', '转向手感',
     '中心区转向力矩梯度随速可调', 1, 'V1.0', 0, FALSE),
    ('REQ-COCKPIT-1', 'FAM-COCKPIT-1', 'T-COCKPIT-REQ', '语音唤醒延迟', '语音唤醒延迟',
     '语音唤醒响应时间不超过 500ms', 1, 'V1.0', 0, FALSE);

-- ---------------------------------------------------------------------------
-- LightTrackerItem index
-- ---------------------------------------------------------------------------
-- Root items are local-backed (materialized = true, source = self); child items
-- are source-backed (materialized = false, source = the requirement content in
-- the default Working Set at its current revision).

INSERT INTO light_tracker_items
    (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized)
VALUES
    ('REQ-SAFETY-1', 'FAM-SAFETY-1', 'T-VEH-SAFETY', '1', 1, 'REQ-SAFETY-1', 1, TRUE),
    ('REQ-SAFETY-2', 'FAM-SAFETY-2', 'T-VEH-SAFETY', '1', 2, 'REQ-SAFETY-2', 2, TRUE),
    ('REQ-PT-1', 'FAM-PT-1', 'T-PT-REQ', '1', 1, 'REQ-PT-1', 1, TRUE),
    ('REQ-PT-2', 'FAM-PT-2', 'T-PT-REQ', '1', 3, 'REQ-PT-2', 3, TRUE),
    ('REQ-CHASSIS-1', 'FAM-CHASSIS-1', 'T-CHASSIS-REQ', '1', 1, 'REQ-CHASSIS-1', 1, TRUE),
    ('REQ-CHASSIS-2', 'FAM-CHASSIS-2', 'T-CHASSIS-REQ', '1', 1, 'REQ-CHASSIS-2', 1, TRUE),
    ('REQ-COCKPIT-1', 'FAM-COCKPIT-1', 'T-COCKPIT-REQ', '1', 1, 'REQ-COCKPIT-1', 1, TRUE);

INSERT INTO light_tracker_items
    (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized)
VALUES
    ('LTI-V1-SAFETY-1', 'FAM-SAFETY-1', 'T-VEH-SAFETY-V1', 'WS-VEHICLE-V1', 1, 'REQ-SAFETY-1', 1, FALSE),
    ('LTI-V1-SAFETY-2', 'FAM-SAFETY-2', 'T-VEH-SAFETY-V1', 'WS-VEHICLE-V1', 2, 'REQ-SAFETY-2', 2, FALSE),
    ('LTI-V1-PT-1', 'FAM-PT-1', 'T-PT-REQ-V1', 'WS-VEHICLE-V1', 1, 'REQ-PT-1', 1, FALSE),
    ('LTI-V1-PT-2', 'FAM-PT-2', 'T-PT-REQ-V1', 'WS-VEHICLE-V1', 3, 'REQ-PT-2', 3, FALSE),
    ('LTI-V1-CHASSIS-1', 'FAM-CHASSIS-1', 'T-CHASSIS-REQ-V1', 'WS-VEHICLE-V1', 1, 'REQ-CHASSIS-1', 1, FALSE),
    ('LTI-V1-CHASSIS-2', 'FAM-CHASSIS-2', 'T-CHASSIS-REQ-V1', 'WS-VEHICLE-V1', 1, 'REQ-CHASSIS-2', 1, FALSE);

-- V2/V3 keep indexing the same powertrain families; the visible lineage is the
-- Tracker source chain T-PT-REQ -> ...-V1 -> ...-V2 -> ...-V3.
INSERT INTO light_tracker_items
    (local_id, common_id, tracker_id, working_set_id, revision, source_local_id, source_revision, materialized)
VALUES
    ('LTI-V2-PT-1', 'FAM-PT-1', 'T-PT-REQ-V2', 'WS-POWERTRAIN-V2', 1, 'REQ-PT-1', 1, FALSE),
    ('LTI-V2-PT-2', 'FAM-PT-2', 'T-PT-REQ-V2', 'WS-POWERTRAIN-V2', 3, 'REQ-PT-2', 3, FALSE),
    ('LTI-V3-PT-1', 'FAM-PT-1', 'T-PT-REQ-V3', 'WS-POWERTRAIN-V3', 1, 'REQ-PT-1', 1, FALSE),
    ('LTI-V3-PT-2', 'FAM-PT-2', 'T-PT-REQ-V3', 'WS-POWERTRAIN-V3', 3, 'REQ-PT-2', 3, FALSE),
    ('LTI-CK-COCKPIT-1', 'FAM-COCKPIT-1', 'T-COCKPIT-REQ-V1', 'WS-COCKPIT-V1', 1, 'REQ-COCKPIT-1', 1, FALSE);
