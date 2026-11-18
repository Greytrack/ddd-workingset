package sk.workingset.application;

import sk.workingset.application.command.*;

import static bootstrap.FixtureIds.fixtureId;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetCreation.Progress;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetCreation.Status;

/** H1 回归：目标已有同族 LTI 时，来源后来软删也只能计为跳过，不得转成排除/失败。 */
@SpringBootTest(classes = bootstrap.WorkingsetApplication.class)
class WorkingSetItemIndexerTest {
    private final String suffix = UUID.randomUUID().toString();
    private final String parent = fixtureId("parent-" + suffix);
    private final String child = fixtureId("child-" + suffix);
    private final String space = fixtureId("space-" + suffix);
    private final String classification = fixtureId("classification-" + suffix);
    private final String sourceTracker = fixtureId("source-" + suffix);
    private final String parentTracker = fixtureId("parent-tracker-" + suffix);
    private final String childTracker = fixtureId("child-tracker-" + suffix);
    private final String source = fixtureId("source-item-" + suffix);
    private final String family = fixtureId("family-" + suffix);

    @Autowired private WorkingSetItemIndexer indexer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transaction;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM light_tracker_items WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM requirements WHERE tracker_id = ?", sourceTracker);
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", sourceTracker);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM classifications WHERE classification_id = ?", classification);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
    }

    @Test
    void existingFamilyWinsOverDeletedSource() {
        jdbc.update("INSERT INTO product_spaces (product_space_id) VALUES (?)", space);
        jdbc.update("INSERT INTO classifications (classification_id, sharing_mode) VALUES (?, 'UNSHARED')",
                classification);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", parent);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)", child, parent);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, sourceTracker, space, classification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, ?, ?, ?, 'UNSHARED')
                """, parentTracker, sourceTracker, parent, space, classification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, ?, ?, ?, 'UNSHARED')
                """, childTracker, parentTracker, child, space, classification);
        jdbc.update("""
                INSERT INTO space_bindings (working_set_id, product_space_id, origin, state)
                VALUES (?, ?, 'EXPLICIT', 'ACTIVE'), (?, ?, 'EXPLICIT', 'ACTIVE')
                """, parent, space, child, space);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id,
                    sharing_mode, origin)
                VALUES (?, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION')
                """, child, childTracker, space, classification);
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision,
                    lock_version, delete_flag)
                VALUES (?, ?, ?, 'source', 'description', 3, 0, TRUE)
                """, source, family, sourceTracker);
        jdbc.update("""
                INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                    source_local_id, source_revision, materialized)
                VALUES (?, ?, ?, ?, 3, ?, 3, FALSE)
                """, fixtureId("parent-lti-" + suffix), family, parentTracker, parent, source);
        jdbc.update("""
                INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                    source_local_id, source_revision, materialized)
                VALUES (?, ?, ?, ?, 3, ?, 3, TRUE)
                """, fixtureId("existing-" + suffix), family, childTracker, child, fixtureId("existing-" + suffix));

        WorkingSetCreation.Request request = new WorkingSetCreation.Request(parent, child, "request",
                List.of(space), List.of(), List.of(parentTracker));
        WorkingSetCreation creation = new WorkingSetCreation(Long.valueOf(fixtureId("creation-" + suffix)), request, "test", Status.Running,
                Stage.INDEX_UNSHARED_TRACKERS, new Progress(0, 0, 0, 1), List.of(),
                Instant.now(), Instant.now(), null,
                Map.of(Stage.INDEX_UNSHARED_TRACKERS, new Progress(0, 0, 0, 1)), Map.of());
        WorkingSetCreation result = transaction.execute(status -> indexer.indexBatch(creation, status));

        assertEquals(1, result.indexProgress().get(parentTracker).skipped());
        assertEquals(0, result.indexProgress().get(parentTracker).failed());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM light_tracker_items WHERE working_set_id = ?", Integer.class, child));
    }
}
