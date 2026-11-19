-- Tests may remove source fixtures using direct SQL. Clean only leftover
-- test projection rows at context startup. No trigger masks repository writes.
DELETE FROM air_inst_common c
WHERE (c.base_class, c.class, c.type) IN (
    (1008, 100801, 10080101), (1009, 100901, 10090101),
    (1003, 100301, 10030101), (1401, 140101, 14010101))
  AND NOT EXISTS (
      SELECT 1 FROM air_inst_workingset WHERE id = c.id
      UNION ALL SELECT 1 FROM air_inst_tracker_rev WHERE id = c.id
      UNION ALL SELECT 1 FROM product_spaces WHERE product_space_id = c.id
      UNION ALL SELECT 1 FROM requirements WHERE local_id = c.id
      UNION ALL SELECT 1 FROM light_tracker_items WHERE local_id = c.id AND NOT materialized
  );
