-- Used only by tests and the dedicated demo database. Production queries use the legacy tables directly.
CREATE OR REPLACE VIEW working_sets AS
SELECT id AS working_set_id, parent_id AS parent_working_set_id, name
FROM air_inst_workingset WHERE delete_flag = 0;

CREATE OR REPLACE FUNCTION insert_working_set_fixture() RETURNS trigger LANGUAGE plpgsql AS '
BEGIN
    INSERT INTO air_inst_workingset (id, instance_id, common_id, workingset_id, parent_id,
        base_class, class, type, tracker, name, number, color, revision, version_type, delete_flag,
        lock_version, created_by, created_time, updated_by, updated_time)
    VALUES (NEW.working_set_id, NEW.working_set_id, NEW.working_set_id, NEW.working_set_id, NEW.parent_working_set_id,
        1008, 100801, 10080101, 584190236948832, NEW.name, ''WS-'' || NEW.working_set_id, ''#FFFF'',
        1, 10001, 0, 0, ''fixture'', 0, ''fixture'', 0);
    RETURN NEW;
END';

DROP TRIGGER IF EXISTS working_set_fixture_insert ON working_sets;
CREATE TRIGGER working_set_fixture_insert INSTEAD OF INSERT ON working_sets
FOR EACH ROW EXECUTE FUNCTION insert_working_set_fixture();

CREATE OR REPLACE VIEW trackers AS
SELECT id AS tracker_id, name, source_branch_id AS source_id, workingset_id AS working_set_id,
       work_space AS product_space_id, tracker_class AS classification_id,
       CASE share WHEN 1 THEN 'SHARED' ELSE 'UNSHARED' END AS sharing_mode,
       parent_id, is_default = 1 AS is_default, visible = 1 AS visible
FROM air_inst_tracker_rev WHERE delete_flag = 0;

CREATE OR REPLACE FUNCTION write_tracker_fixture() RETURNS trigger LANGUAGE plpgsql AS '
DECLARE
    family_id bigint;
    shared_flag bigint;
    default_flag smallint;
    visible_flag smallint;
BEGIN
    IF TG_OP = ''DELETE'' THEN
        DELETE FROM air_ws_tracker WHERE id = OLD.tracker_id;
        DELETE FROM air_inst_tracker_rev WHERE id = OLD.tracker_id;
        RETURN OLD;
    END IF;
    default_flag := CASE WHEN NEW.is_default THEN 1 ELSE 0 END;
    visible_flag := CASE WHEN COALESCE(NEW.visible, TRUE) THEN 1 ELSE 0 END;
    IF TG_OP = ''UPDATE'' THEN
        UPDATE air_inst_tracker_rev SET is_default = default_flag, visible = visible_flag WHERE id = OLD.tracker_id;
        UPDATE air_ws_tracker SET is_default = default_flag, visible = visible_flag WHERE id = OLD.tracker_id;
        RETURN NEW;
    END IF;
    shared_flag := CASE NEW.sharing_mode WHEN ''SHARED'' THEN 1 ELSE 0 END;
    IF NEW.source_id IS NULL THEN
        family_id := NEW.tracker_id;
    ELSE
        SELECT common_id INTO STRICT family_id FROM air_inst_tracker_rev WHERE id = NEW.source_id;
    END IF;
    INSERT INTO air_inst_tracker_rev (id, instance_id, common_id, name, number, source_branch_id,
        workingset_id, work_space, tracker_class, share, parent_id, is_default, visible,
        base_class, class, type, tracker, owner, revision, delete_flag, lock_version,
        created_by, created_time, updated_by, updated_time)
    SELECT NEW.tracker_id, NEW.tracker_id, family_id, NEW.name, ''FIX-'' || NEW.tracker_id, NEW.source_id,
        NEW.working_set_id, NEW.product_space_id, NEW.classification_id, shared_flag, NEW.parent_id, default_flag, visible_flag,
        c.base_class, c.class, c.type, 584578917040480, 1, 1, 0, 0, ''fixture'', 0, ''fixture'', 0
    FROM classifications c WHERE c.classification_id = NEW.classification_id;
    INSERT INTO air_ws_tracker (id, name, number, source_branch_id, workingset_id, sub_class_id, shared,
        parent_id, is_default, visible, locked, delete_flag, lock_version, created_by, created_time, updated_by, updated_time)
    VALUES (NEW.tracker_id, NEW.name, ''FIX-'' || NEW.tracker_id, NEW.source_id, NEW.working_set_id,
        NEW.classification_id, shared_flag, NEW.parent_id, default_flag, visible_flag, 0, 0, 0, ''fixture'', 0, ''fixture'', 0);
    RETURN NEW;
END';

DROP TRIGGER IF EXISTS tracker_fixture_write ON trackers;
CREATE TRIGGER tracker_fixture_write INSTEAD OF INSERT OR UPDATE OR DELETE ON trackers
FOR EACH ROW EXECUTE FUNCTION write_tracker_fixture();

CREATE SEQUENCE IF NOT EXISTS tracker_binding_fixture_id;

CREATE OR REPLACE FUNCTION write_tracker_binding_fixture() RETURNS trigger LANGUAGE plpgsql AS '
BEGIN
    IF TG_OP = ''DELETE'' THEN
        DELETE FROM air_ws_workingset_branch_relation
        WHERE workingset_id = OLD.working_set_id AND tracker_id = OLD.tracker_id;
        RETURN OLD;
    END IF;
    INSERT INTO air_ws_workingset_branch_relation
        (id, workingset_id, work_space, tracker_id, is_show, lock_version, created_by, updated_by)
    VALUES (nextval(''tracker_binding_fixture_id''), NEW.working_set_id, NEW.product_space_id, NEW.tracker_id,
            CASE WHEN COALESCE(NEW.shown, TRUE) THEN 1 ELSE 0 END, 0, ''fixture'', ''fixture'');
    RETURN NEW;
END';

DROP TRIGGER IF EXISTS tracker_binding_fixture_write ON tracker_bindings;
CREATE TRIGGER tracker_binding_fixture_write INSTEAD OF INSERT OR DELETE ON tracker_bindings
FOR EACH ROW EXECUTE FUNCTION write_tracker_binding_fixture();

-- Older fixtures delete spaces after deleting all corresponding Tracker relations.
-- Reject attempts to use fixture cleanup to bypass that ordering.
CREATE OR REPLACE FUNCTION delete_space_binding_fixture() RETURNS trigger LANGUAGE plpgsql AS '
BEGIN
    RAISE EXCEPTION ''Delete Tracker relations before removing the fixture space'';
END';

DROP TRIGGER IF EXISTS space_binding_fixture_delete ON space_bindings;
CREATE TRIGGER space_binding_fixture_delete INSTEAD OF DELETE ON space_bindings
FOR EACH ROW EXECUTE FUNCTION delete_space_binding_fixture();
