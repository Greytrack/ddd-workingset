-- Demo fixtures bypass repositories. Use the same SQL to fill missing rows in existing databases.
WITH source_rows (
    id, base_class, class, type, work_space, number, name, version, revision, delete_flag, lock_version,
    created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id,
    parent_id, tracker, max_revision
) AS (
    SELECT id, base_class, class, type, work_space, number, name, NULL::VARCHAR, revision, delete_flag, lock_version,
           created_by, created_time, updated_by, updated_time, NULL::INTEGER, owner, common_id, workingset_id,
           parent_id, 584190236948832, 1
    FROM air_inst_workingset
    UNION ALL
    SELECT id, base_class, class, type, work_space, number, name, version, revision, delete_flag, lock_version,
           created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id,
           parent_id, 584578917040480, 1
    FROM air_inst_tracker_rev
    UNION ALL
    SELECT product_space_id, 1003, 100301, 10030101, NULL, NULL, name, NULL, 1, 0, NULL,
           NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 584189545406816, 1
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
           l.common_id, l.working_set_id, NULL, l.tracker_id, l.revision
    FROM light_tracker_items l
    JOIN requirements r ON r.local_id = l.source_local_id
    JOIN air_inst_tracker_rev t ON t.id = l.tracker_id
    WHERE NOT l.materialized AND NOT EXISTS (SELECT 1 FROM requirements m WHERE m.local_id = l.local_id)
)
INSERT INTO air_inst_common (
    id, base_class, class, type, work_space, number, name, version, revision, delete_flag, lock_version,
    created_by, created_time, updated_by, updated_time, checked_out, owner, common_id, workingset_id,
    parent_id, tracker, max_revision
)
SELECT s.*
FROM source_rows s
WHERE NOT EXISTS (
    SELECT 1 FROM air_inst_common c
    WHERE c.id = s.id AND c.base_class = s.base_class
      AND c.class IS NOT DISTINCT FROM s.class AND c.type IS NOT DISTINCT FROM s.type
);
