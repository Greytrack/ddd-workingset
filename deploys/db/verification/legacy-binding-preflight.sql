-- Run on an isolated copy of the customer schema before enabling V4 writes.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

SELECT id, workingset_id, work_space, tracker_id, is_show
FROM air_ws_workingset_branch_relation
WHERE workingset_id IS NULL OR work_space IS NULL OR tracker_id IS NULL OR is_show NOT IN (0, 1);

SELECT workingset_id, tracker_id, count(*) AS relation_count,
       array_agg(id ORDER BY id) AS relation_ids
FROM air_ws_workingset_branch_relation
WHERE workingset_id IS NOT NULL AND work_space IS NOT NULL AND tracker_id IS NOT NULL
GROUP BY workingset_id, tracker_id
HAVING count(*) > 1;

SELECT r.id, r.workingset_id, r.work_space, r.tracker_id
FROM air_ws_workingset_branch_relation r
LEFT JOIN air_inst_workingset w ON w.id = r.workingset_id AND w.delete_flag = 0
LEFT JOIN product_spaces p ON p.product_space_id = r.work_space
LEFT JOIN air_inst_tracker_rev t ON t.id = r.tracker_id AND t.delete_flag = 0
WHERE r.tracker_id IS NOT NULL
  AND (w.id IS NULL OR p.product_space_id IS NULL OR t.id IS NULL
       OR r.work_space IS DISTINCT FROM t.work_space
       OR (t.share = 0 AND r.workingset_id IS DISTINCT FROM t.workingset_id));

SELECT r.workingset_id, r.work_space,
       count(*) FILTER (WHERE t.tracker_class = 10070101 AND NULLIF(t.parent_id, 0) IS NULL
                           AND t.share = 0 AND t.workingset_id = r.workingset_id) AS root_folders
FROM air_ws_workingset_branch_relation r
JOIN air_inst_tracker_rev t ON t.id = r.tracker_id AND t.work_space = r.work_space AND t.delete_flag = 0
WHERE r.workingset_id IS NOT NULL
GROUP BY r.workingset_id, r.work_space
HAVING count(*) FILTER (WHERE t.tracker_class = 10070101 AND NULLIF(t.parent_id, 0) IS NULL
                           AND t.share = 0 AND t.workingset_id = r.workingset_id) <> 1;

COMMIT;
