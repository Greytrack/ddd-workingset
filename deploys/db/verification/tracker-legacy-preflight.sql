-- Run on an isolated copy of the legacy PostgreSQL database.
-- These queries report discrepancies; they do not infer or repair field values.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- A mismatch must be resolved before foreign keys are redirected to the root ID.
SELECT id, instance_id, revision, workingset_id, work_space, tracker_class
FROM public.air_inst_tracker_rev
WHERE instance_id IS DISTINCT FROM id
   OR revision IS DISTINCT FROM 1
   OR common_id IS NULL
   OR (source_branch_id IS NULL AND common_id IS DISTINCT FROM id)
   OR (source_branch_id IS NOT NULL AND common_id = id)
   OR work_space IS NULL
   OR workingset_id IS NULL
   OR share IS NULL
   OR share NOT IN (0, 1)
   OR delete_flag NOT IN (0, 1);

-- Compare values that have a confirmed correspondence. Preserve unknown legacy
-- deletion flags for review rather than treating NULL as active.
SELECT COALESCE(r.id, c.id) AS tracker_id,
       r.id IS NULL         AS missing_root,
       c.id IS NULL         AS missing_compatibility_row,
       r.tracker_class,
       c.sub_class_id,
       r.share,
       c.shared,
       r.delete_flag        AS root_delete_flag,
       c.delete_flag        AS compatibility_delete_flag
FROM public.air_inst_tracker_rev r
         FULL JOIN public.air_ws_tracker c ON c.id = r.id
WHERE r.id IS NULL
   OR c.id IS NULL
   OR r.workingset_id IS DISTINCT FROM c.workingset_id
   OR r.tracker_class IS DISTINCT FROM c.sub_class_id
   OR NULLIF(r.parent_id, 0) IS DISTINCT FROM NULLIF(c.parent_id, 0)
   OR r.source_branch_id IS DISTINCT FROM c.source_branch_id
   OR r.share IS DISTINCT FROM c.shared
   OR r.is_default IS DISTINCT FROM c.is_default
   OR r.visible IS DISTINCT FROM c.visible
   OR r.delete_flag IS DISTINCT FROM c.delete_flag
   OR r.lock_version IS DISTINCT FROM c.lock_version;

-- Organization parents belong to the same owning Working Set. Derivation
-- sources can belong to a different Working Set, but must exist.
SELECT child.id, child.parent_id, child.source_branch_id
FROM public.air_inst_tracker_rev child
         LEFT JOIN public.air_inst_tracker_rev parent ON parent.id = NULLIF(child.parent_id, 0)
         LEFT JOIN public.air_inst_tracker_rev source ON source.id = child.source_branch_id
WHERE child.delete_flag = 0
  AND ((NULLIF(child.parent_id, 0) IS NOT NULL
    AND (parent.id IS NULL OR parent.delete_flag <> 0
        OR parent.workingset_id IS DISTINCT FROM child.workingset_id))
    OR (child.source_branch_id IS NOT NULL AND (source.id IS NULL OR source.delete_flag <> 0
        OR child.common_id IS DISTINCT FROM source.common_id
        OR child.work_space IS DISTINCT FROM source.work_space
        OR child.tracker_class IS DISTINCT FROM source.tracker_class)));

SELECT workingset_id, source_branch_id, count(*) AS active_tracker_count
FROM public.air_inst_tracker_rev
WHERE delete_flag = 0
  AND source_branch_id IS NOT NULL
GROUP BY workingset_id, source_branch_id
HAVING count(*) > 1;

SELECT id, char_length(name) AS name_length, char_length(description) AS description_length
FROM public.air_inst_tracker_rev
WHERE char_length(name) > 500
   OR char_length(description) > 1000;

SELECT workingset_id, work_space, tracker_class, count(*) AS default_count
FROM public.air_inst_tracker_rev
WHERE delete_flag = 0
  AND is_default = 1
GROUP BY workingset_id, work_space, tracker_class
HAVING count(*) > 1;

COMMIT;
