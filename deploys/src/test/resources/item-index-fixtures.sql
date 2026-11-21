-- Test-only adapter for historical fixture SQL. Production reads and writes both physical tables directly.
CREATE OR REPLACE VIEW light_tracker_items AS
SELECT local_id,
       common_id,
       branch_id        AS tracker_id,
       workingset_id    AS working_set_id,
       revision,
       source_local_id,
       source_revision,
       (has_copied = 1) AS materialized
FROM air_ws_items_index;

CREATE OR REPLACE FUNCTION write_item_index_fixture() RETURNS trigger
    LANGUAGE plpgsql AS
'
    BEGIN
        IF TG_OP = ''DELETE'' THEN
            DELETE FROM air_ws_items_index WHERE local_id = OLD.local_id;
            RETURN OLD;
        ELSIF TG_OP = ''UPDATE'' THEN
            UPDATE air_ws_items_index
            SET common_id       = NEW.common_id,
                revision        = NEW.revision,
                source_local_id = NEW.source_local_id,
                source_revision = NEW.source_revision,
                has_copied      = CASE WHEN NEW.materialized THEN 1 ELSE 0 END
            WHERE local_id = OLD.local_id;
            UPDATE air_ws_items_revision_index
            SET common_id = NEW.common_id,
                revision  = NEW.revision
            WHERE local_id = OLD.local_id
              AND revision = OLD.revision;
            RETURN NEW;
        END IF;
        INSERT INTO air_ws_items_index
        (id, local_id, common_id, branch_id, workingset_id, revision, source_local_id, source_revision, has_copied,
         work_space_id, sub_class_id)
        SELECT NEW.local_id,
               NEW.local_id,
               NEW.common_id,
               NEW.tracker_id,
               NEW.working_set_id,
               NEW.revision,
               NEW.source_local_id,
               NEW.source_revision,
               CASE WHEN NEW.materialized THEN 1 ELSE 0 END,
               t.work_space,
               t.tracker_class
        FROM air_inst_tracker_rev t
        WHERE t.id = NEW.tracker_id;
        INSERT INTO air_ws_items_revision_index (id, local_id, common_id, branch_id, workingset_id, revision)
        VALUES (NEW.local_id, NEW.local_id, NEW.common_id, NEW.tracker_id, NEW.working_set_id, NEW.revision);
        RETURN NEW;
    END';
DROP TRIGGER IF EXISTS item_index_fixture_write ON light_tracker_items;
CREATE TRIGGER item_index_fixture_write
    INSTEAD OF INSERT OR UPDATE OR DELETE
    ON light_tracker_items
    FOR EACH ROW
EXECUTE FUNCTION write_item_index_fixture();
