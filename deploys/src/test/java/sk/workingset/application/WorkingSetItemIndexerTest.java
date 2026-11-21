package sk.workingset.application;

import bootstrap.ItemIndexFixture;
import bootstrap.WorkingsetApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.WorkingSetItemIndexer;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.lighttrackeritem.ItemClassification;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSetCreation;
import sk.workingset.domain.workingset.WorkingSetCreation.Progress;
import sk.workingset.domain.workingset.WorkingSetCreation.Stage;
import sk.workingset.domain.workingset.WorkingSetCreation.Status;
import sk.workingset.domain.workingset.WorkingSetId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static bootstrap.FixtureIds.fixtureId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * H1 回归：目标已有同族 LTI 时，来源后来软删也只能计为跳过，不得转成排除/失败。
 */
@SpringBootTest(classes = WorkingsetApplication.class)
class WorkingSetItemIndexerTest {
    private final String suffix = UUID.randomUUID().toString();
    private final String parent = fixtureId("parent-" + suffix);
    private final String child = fixtureId("child-" + suffix);
    private final String space = fixtureId("space-" + suffix);
    private final String sourceTracker = fixtureId("source-" + suffix);
    private final String parentTracker = fixtureId("parent-tracker-" + suffix);
    private final String childTracker = fixtureId("child-tracker-" + suffix);
    private final String source = fixtureId("source-item-" + suffix);
    private final String family = fixtureId("family-" + suffix);

    @Autowired
    private WorkingSetItemIndexer indexer;
    @Autowired
    private LightTrackerItemRepository items;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM air_ws_items_index WHERE workingset_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM requirements WHERE tracker_id = ?", sourceTracker);
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", sourceTracker);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
    }

    @Test
    void existingFamilyWinsOverDeletedSource() {
        jdbc.update("INSERT INTO product_spaces (product_space_id) VALUES (?)", space);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", parent);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)", child, parent);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, 14010101, 'UNSHARED')
                """, sourceTracker, space);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, ?, ?, 14010101, 'UNSHARED')
                """, parentTracker, sourceTracker, parent, space);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, ?, ?, 14010101, 'UNSHARED')
                """, childTracker, parentTracker, child, space);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id,
                    sharing_mode, origin)
                VALUES (?, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION'),
                       (?, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION')
                """, parent, parentTracker, space, "14010101", child, childTracker, space, "14010101");
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision,
                    lock_version, delete_flag, working_set_id, created_by, created_time)
                VALUES (?, ?, ?, 'source', 'description', 3, 0, TRUE,
                    (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?), 'test', ?)
                """, source, family, sourceTracker, sourceTracker, System.currentTimeMillis());
        ItemIndexFixture.insert(jdbc, fixtureId("parent-lti-" + suffix), family, parentTracker, parent, 3, source, 3, false);
        LocalId winner = LocalId.of(fixtureId("existing-" + suffix));
        LocalId contender = LocalId.of(fixtureId("contender-" + suffix));
        ItemClassification classification = new ItemClassification(Long.parseLong(space), 14010101L);
        LightTrackerItem existing = LightTrackerItem.local(winner, CommonId.of(family), TrackerId.of(childTracker),
                WorkingSetId.of(child), Revision.of(3), classification);
        LightTrackerItem duplicate = LightTrackerItem.local(contender, CommonId.of(family), TrackerId.of(childTracker),
                WorkingSetId.of(child), Revision.of(3), classification);
        transaction.executeWithoutResult(status -> {
            assertTrue(items.claimFamily(existing));
            assertFalse(items.claimFamily(duplicate));
        });
        assertEquals(Revision.of(3), items.findById(winner).orElseThrow().currentRevisionIndex().revision());
        assertTrue(items.findById(contender).isEmpty());

        WorkingSetCreation.Request request = new WorkingSetCreation.Request(parent, child, "request",
                List.of(space), List.of(), List.of(parentTracker));
        Instant acceptedAt = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
        WorkingSetCreation creation = new WorkingSetCreation(Long.valueOf(fixtureId("creation-" + suffix)), request, "test", Status.Running,
                Stage.INDEX_UNSHARED_TRACKERS, new Progress(0, 0, 0, 1), List.of(),
                acceptedAt, acceptedAt, null,
                Map.of(Stage.INDEX_UNSHARED_TRACKERS, new Progress(0, 0, 0, 1)), Map.of());
        WorkingSetCreation result = transaction.execute(status -> indexer.indexBatch(creation, status));

        assertEquals(1, result.indexProgress().get(parentTracker).skipped());
        assertEquals(0, result.indexProgress().get(parentTracker).failed());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM air_ws_items_index WHERE workingset_id = ?", Integer.class, child));
    }
}
