\set ON_ERROR_STOP on
BEGIN;
LOCK TABLE air_inst_workingset, air_inst_tracker_rev, product_spaces, requirements, requirement_snapshots, light_tracker_items,
    air_inst_common IN SHARE ROW EXCLUSIVE MODE;
\ir ../../src/main/resources/common-index-backfill.sql
COMMIT;
