-- Run on the legacy PostgreSQL copy. Reports only; does not modify legacy data.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;

-- Expected result: exactly one active root, with ID 1 and color #FFFF.
SELECT id, parent_id, delete_flag, color
FROM public.air_inst_workingset
WHERE id = 1
   OR (delete_flag = 0 AND NULLIF(parent_id, 0) IS NULL);

SELECT id,
       instance_id,
       common_id,
       workingset_id,
       base_class,
       class,
       type,
       tracker,
       revision,
       version_type
FROM public.air_inst_workingset
WHERE instance_id IS DISTINCT FROM id
   OR common_id IS DISTINCT FROM id
   OR workingset_id IS DISTINCT FROM id
   OR base_class IS DISTINCT FROM 1008
   OR class IS DISTINCT FROM 100801
   OR type IS DISTINCT FROM 10080101
   OR tracker IS DISTINCT FROM 584190236948832
   OR revision IS DISTINCT FROM 1
   OR version_type IS DISTINCT FROM 10001;

SELECT child.id, child.parent_id, parent.delete_flag AS parent_deleted
FROM public.air_inst_workingset child
         LEFT JOIN public.air_inst_workingset parent ON parent.id = NULLIF(child.parent_id, 0)
WHERE child.delete_flag = 0
  AND child.id <> 1
  AND (parent.id IS NULL OR parent.delete_flag <> 0 OR child.id = parent.id);

WITH RECURSIVE reachable(id) AS (SELECT id
                                 FROM public.air_inst_workingset
                                 WHERE id = 1
                                   AND delete_flag = 0
                                 UNION
                                 SELECT child.id
                                 FROM public.air_inst_workingset child
                                          JOIN reachable parent ON child.parent_id = parent.id
                                 WHERE child.delete_flag = 0)
SELECT ws.id, ws.parent_id
FROM public.air_inst_workingset ws
WHERE ws.delete_flag = 0
  AND NOT EXISTS (SELECT 1 FROM reachable WHERE reachable.id = ws.id);

SELECT id, color
FROM public.air_inst_workingset
WHERE color IS NOT NULL
  AND color !~ '^#([[:xdigit:]]{3,4}|[[:xdigit:]]{6}|[[:xdigit:]]{8})$';

COMMIT;
