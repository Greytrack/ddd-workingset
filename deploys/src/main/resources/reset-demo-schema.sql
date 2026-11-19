-- Destructive reset for the dedicated demo database. Run before schema.sql.
DO 'BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass(''space_bindings'') AND relkind = ''v'') THEN
        DROP VIEW space_bindings;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass(''tracker_bindings'') AND relkind = ''v'') THEN
        DROP VIEW tracker_bindings;
    END IF;
    IF to_regclass(''event_publication'') IS NOT NULL THEN
        TRUNCATE event_publication;
    END IF;
    IF to_regclass(''event_publication_archive'') IS NOT NULL THEN
        TRUNCATE event_publication_archive;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass(''trackers'') AND relkind = ''v'') THEN
        DROP VIEW trackers;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass(''working_sets'') AND relkind = ''v'') THEN
        DROP VIEW working_sets;
    END IF;
END';

DROP TABLE IF EXISTS
    working_set_creation_items, working_set_creation_snapshots, working_set_creations,
    merge_commit_receipts, merge_checkpoints, merge_rounds,
    light_tracker_items, requirement_snapshots, requirements,
    cow_failures, cow_materializations, tracker_bindings, space_bindings, air_ws_workingset_branch_relation,
    trackers, air_ws_tracker, air_inst_tracker_rev, classifications, product_spaces, working_sets, air_inst_workingset;
DROP SEQUENCE IF EXISTS merge_decision_version;
DROP SEQUENCE IF EXISTS tracker_binding_fixture_id;
