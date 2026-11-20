-- Read-only preflight for a legacy database, before enabling V4 constraints.
SELECT local_id, count(*) FROM air_ws_items_index GROUP BY local_id
HAVING local_id IS NULL OR count(*) > 1;

SELECT workingset_id, common_id, count(*) FROM air_ws_items_index GROUP BY workingset_id, common_id
HAVING workingset_id IS NULL OR common_id IS NULL OR count(*) > 1;

SELECT local_id, revision, count(*) FROM air_ws_items_revision_index GROUP BY local_id, revision
HAVING local_id IS NULL OR revision IS NULL OR revision <= 0 OR count(*) > 1;

SELECT i.id, i.local_id, i.revision, i.has_copied FROM air_ws_items_index i
LEFT JOIN air_inst_tracker_rev t ON t.id = i.branch_id
WHERE t.id IS NULL OR i.workingset_id IS DISTINCT FROM t.workingset_id
   OR i.work_space_id IS DISTINCT FROM t.work_space
   OR i.revision IS NULL OR i.revision <= 0 OR i.source_revision IS NULL OR i.source_revision <= 0
   OR i.source_local_id IS NULL OR i.has_copied IS NULL OR i.has_copied NOT IN (0, 1)
   OR (i.has_copied = 0 AND i.source_local_id = i.local_id)
   OR (i.has_copied = 1 AND (i.source_local_id <> i.local_id OR i.source_revision <> i.revision))
   OR NOT EXISTS (SELECT 1 FROM air_ws_items_revision_index r WHERE r.local_id = i.local_id AND r.revision = i.revision);

SELECT r.id, r.local_id, r.revision FROM air_ws_items_revision_index r
LEFT JOIN air_ws_items_index i ON i.local_id = r.local_id
WHERE i.id IS NULL OR (r.work_space_id, r.workingset_id, r.master_id, r.branch_id, r.common_id, r.sub_class_id)
    IS DISTINCT FROM (i.work_space_id, i.workingset_id, i.master_id, i.branch_id, i.common_id, i.sub_class_id);
