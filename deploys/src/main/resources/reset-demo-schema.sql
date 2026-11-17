-- Destructive reset for the dedicated demo database. Run before schema.sql.
DO 'BEGIN
    IF to_regclass(''event_publication'') IS NOT NULL THEN
        TRUNCATE event_publication;
    END IF;
    IF to_regclass(''event_publication_archive'') IS NOT NULL THEN
        TRUNCATE event_publication_archive;
    END IF;
END';

DROP TABLE IF EXISTS
    working_set_creation_items, working_set_creation_snapshots, working_set_creations,
    merge_commit_receipts, merge_checkpoints, merge_rounds,
    light_tracker_items, requirement_snapshots, requirements,
    cow_failures, cow_materializations, tracker_bindings, space_bindings,
    trackers, classifications, product_spaces, working_sets;
DROP SEQUENCE IF EXISTS merge_decision_version;
