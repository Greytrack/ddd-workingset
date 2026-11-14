package bootstrap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import bootstrap.web.WorkingSetController.WorkingSetView;
import bootstrap.web.WorkingSetController.BindingsView;
import bootstrap.web.WorkingSetCreationController.CreationView;
import bootstrap.web.LightTrackerItemView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import sk.workingset.application.event.TrackerItemsIndexed;
import sk.workingset.application.event.WorkingSetCreationSubmitted;
import sk.workingset.domain.WorkingSetCreation.Request;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetCreation.Status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "workingset.creation.index-batch-size=2")
@Import(WorkingSetCreationAcceptanceTest.IndexEventConfiguration.class)
class WorkingSetCreationAcceptanceTest {
    private static final String CREATIONS = "/api/working-set-creations";
    private final String child = UUID.randomUUID().toString();
    private final String parent = "p-" + child;
    private final String space = "space-" + child;
    private final String otherSpace = "other-" + child;
    private final String shared = "shared-" + child;
    private final String unshared = "unshared-" + child;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transaction;
    @Autowired private IndexEvents indexEvents;

    @AfterEach
    void cleanUp() {
        indexEvents.clear(child);
        jdbc.update("DELETE FROM light_tracker_items WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM light_tracker_items WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM requirements WHERE tracker_id IN (SELECT tracker_id FROM trackers WHERE product_space_id IN (?, ?))",
                space, otherSpace);
        jdbc.update("""
                DELETE FROM event_publication WHERE serialized_event LIKE ANY (
                    SELECT '%' || id || '%' FROM working_set_creations WHERE new_working_set_id = ?)
                """, child);
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", child);
        jdbc.update("DELETE FROM trackers WHERE product_space_id IN (?, ?)", space, otherSpace);
        jdbc.update("DELETE FROM classifications WHERE classification_id IN (?, ?)", shared, unshared);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id IN (?, ?)", space, otherSpace);
    }

    @Test
    void createsOneChildAndExposesProgressWithoutPublishingPreparingRange() throws Exception {
        var request = request("1", "first");
        String task;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = '1' FOR UPDATE")) {
                statement.executeQuery().close();
                var accepted = rest.postForEntity(CREATIONS, request, CreationView.class);
                assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
                task = accepted.getBody().creationId();
                assertEquals(CREATIONS + "/" + task, accepted.getHeaders().getLocation().getPath());
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Status.Running, task(task).status()));

                var preparing = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.CONFLICT, preparing.getStatusCode());
                assertEquals("PREPARING", preparing.getBody().get("reason"));
                var use = rest.postForEntity("/api/worksets/" + child + "/children",
                        Map.of("workingSetId", "unused-" + child), Map.class);
                assertEquals(HttpStatus.CONFLICT, use.getStatusCode());
                assertEquals("PREPARING", use.getBody().get("reason"));
                var bind = rest.postForEntity("/api/worksets/" + child + "/spaces/" + space, null, Map.class);
                assertEquals(HttpStatus.CONFLICT, bind.getStatusCode());
                assertEquals("PREPARING", bind.getBody().get("reason"));
                var createItem = rest.postForEntity("/api/requirements", Map.of("workingSetId", child,
                        "trackerId", shared, "title", "item", "description", "description", "revision", 1), Map.class);
                assertEquals(HttpStatus.CONFLICT, createItem.getStatusCode());
                assertEquals("PREPARING", createItem.getBody().get("reason"));
                assertEquals(task, rest.postForEntity(CREATIONS, request, CreationView.class).getBody().creationId());
            } finally {
                connection.rollback();
            }
        }

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(Status.Succeeded, task(task).status()));
        assertEquals(1, task(task).progress().succeeded());
        assertNotNull(task(task).completedAt());
        assertEquals("1", rest.getForObject("/api/worksets/" + child, WorkingSetView.class).parentWorkingSetId());
        assertEquals(task, rest.postForEntity(CREATIONS, request, CreationView.class).getBody().creationId());
        var differentRequest = rest.postForEntity(CREATIONS, request("1", "second"), Map.class);
        assertEquals(HttpStatus.CONFLICT, differentRequest.getStatusCode());
        assertEquals("WorkingSetAlreadyExists", differentRequest.getBody().get("reason"));
    }

    @Test
    void bodyFailureIsTerminalAndVisibleThroughPolling() {
        var accepted = rest.postForEntity(CREATIONS, request("missing-" + child, "first"), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
        String id = accepted.getBody().creationId();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(Status.Failed, task(id).status()));
        var failure = task(id).failures().getFirst();
        assertEquals("CREATE_WORKING_SET", failure.stage().name());
        assertEquals("WorkingSetNotFound", failure.reason());
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity("/api/worksets/" + child, Map.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity(CREATIONS + "/missing", Map.class).getStatusCode());
    }

    @Test
    void initializesSelectedSpaceAndTrackersAndKeepsOneCopyAfterRedelivery() {
        seedRange();
        var request = new Request("1", child, "range", List.of(space), List.of(shared), List.of(unshared));
        String id = submit(request);
        var completed = awaitStatus(id, Status.Succeeded);
        assertEquals(6, completed.progress().succeeded());
        var bindings = bindings();
        assertEquals(List.of(space), bindings.spaces().stream().map(binding -> binding.productSpaceId()).toList());
        assertEquals(2, bindings.trackers().size());
        assertEquals(1, bindings.trackers().stream().filter(binding -> shared.equals(binding.trackerId())).count());
        String copy = bindings.trackers().stream().filter(binding -> "UNSHARED".equals(binding.sharingMode()))
                .findFirst().orElseThrow().trackerId();
        assertEquals(unshared, jdbc.queryForObject("SELECT source_id FROM trackers WHERE tracker_id = ?", String.class, copy));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM light_tracker_items WHERE working_set_id = ?",
                Integer.class, child));
        assertEquals(id, submit(request));
        transaction.executeWithoutResult(tx -> events.publishEvent(new WorkingSetCreationSubmitted(id)));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NOT NULL
                    AND event_type LIKE '%WorkingSetCreationSubmitted'
                """, Integer.class, "%" + id + "%")));
        assertEquals(completed, task(id));
        assertEquals(bindings, bindings());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void missingTrackerSpaceReportsFailureAndKeepsOtherBindings() {
        seedRange();
        String outsideTracker = "outside-" + child;
        seedTracker(outsideTracker, otherSpace, unshared, "UNSHARED");
        String id = submit(new Request("1", child, "partial", List.of(space), List.of(shared),
                List.of(outsideTracker, unshared)));
        var completed = awaitStatus(id, Status.PartiallySucceeded);
        var failure = completed.failures().getFirst();
        assertEquals(1, completed.failures().size());
        assertEquals(Stage.COPY_UNSHARED_TRACKERS, failure.stage());
        assertEquals(outsideTracker, failure.trackerId());
        assertEquals(otherSpace, failure.productSpaceId());
        assertEquals("MissingSpaceBinding", failure.reason());
        assertEquals(1, completed.stageProgress().get(Stage.BIND_UNSHARED_TRACKERS).skipped());
        assertEquals(1, completed.stageProgress().get(Stage.INDEX_UNSHARED_TRACKERS).skipped());
        assertEquals(2, bindings().trackers().size());
        assertEquals(1, bindings().spaces().size());
    }

    @Test
    void rejectsSpaceAndSharedTrackerOutsideTheDirectParentsBindings() {
        seedRange();
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/1/children",
                Map.of("workingSetId", parent), Map.class).getStatusCode());
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/" + parent + "/spaces/" + space,
                null, Map.class).getStatusCode());
        var completed = awaitStatus(submit(new Request(parent, child, "outside-parent",
                List.of(space, otherSpace), List.of(shared), List.of())), Status.PartiallySucceeded);
        assertEquals(List.of("NOT_INCLUDED", "SourceTrackerNotFound"),
                completed.failures().stream().map(failure -> failure.reason()).toList());
        assertEquals(List.of(space), bindings().spaces().stream().map(binding -> binding.productSpaceId()).toList());
        assertEquals(List.of(), bindings().trackers());
    }

    @Test
    void retainsCopiedTrackerWhenItsBindingLosesTheSlot() {
        seedRange();
        String sameSlot = "z-" + child;
        seedTracker(sameSlot, space, unshared, "UNSHARED");
        var completed = awaitStatus(submit(new Request("1", child, "slot", List.of(space), List.of(),
                List.of(unshared, sameSlot))), Status.PartiallySucceeded);

        assertEquals(2, completed.stageProgress().get(Stage.COPY_UNSHARED_TRACKERS).succeeded());
        assertEquals(1, completed.stageProgress().get(Stage.BIND_UNSHARED_TRACKERS).succeeded());
        assertEquals(1, completed.failures().size());
        var failure = completed.failures().getFirst();
        assertEquals(Stage.BIND_UNSHARED_TRACKERS, failure.stage());
        assertEquals(sameSlot, failure.trackerId());
        assertEquals("SlotOccupied", failure.reason());
        assertEquals(1, bindings().trackers().size());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void rejectsExistingTargetWithoutMarkingItPreparing() {
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", child);
        var response = rest.postForEntity(CREATIONS, new Request("1", child, "existing-target",
                List.of(), List.of(), List.of()), Map.class);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("WorkingSetAlreadyExists", response.getBody().get("reason"));
        assertEquals(HttpStatus.OK, rest.getForEntity("/api/worksets/" + child, Map.class).getStatusCode());
    }

    @Test
    void rejectsParentThatIsBeingCreated() throws Exception {
        String pendingParent = "pending-" + child;
        String id;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = '1' FOR UPDATE")) {
                statement.executeQuery().close();
                id = submit(new Request("1", pendingParent, "pending-parent",
                        List.of(), List.of(), List.of()));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Status.Running, task(id).status()));
                var rejected = rest.postForEntity(CREATIONS, new Request(pendingParent, child, "child-of-pending",
                        List.of(), List.of(), List.of()), Map.class);
                assertEquals(HttpStatus.CONFLICT, rejected.getStatusCode());
                assertEquals("PREPARING", rejected.getBody().get("reason"));
            } finally {
                connection.rollback();
            }
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(Status.Succeeded, task(id).status()));
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + id + "%");
        jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", pendingParent);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", pendingParent);
    }

    @Test
    void filtersCreatingWorkingSetFromQueriesButTaskViewIsAvailable() throws Exception {
        seedRange();
        String id;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "SELECT product_space_id FROM product_spaces WHERE product_space_id = ? FOR UPDATE")) {
                statement.setString(1, space);
                statement.executeQuery().close();
                id = submit(new Request("1", child, "filter", List.of(space), List.of(), List.of()));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertEquals(Status.Running, task(id).status());
                    assertEquals(1, jdbc.queryForObject(
                            "SELECT count(*) FROM working_sets WHERE working_set_id = ?", Integer.class, child));
                });
                var ordinary = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.CONFLICT, ordinary.getStatusCode());
                assertEquals("PREPARING", ordinary.getBody().get("reason"));
                assertFalse(rest.getForObject("/api/worksets/1/children", List.class).contains(child));
                var view = rest.getForEntity(CREATIONS + "/" + id + "/working-set", WorkingSetView.class);
                assertEquals(HttpStatus.OK, view.getStatusCode());
                assertEquals(child, view.getBody().workingSetId());
            } finally {
                connection.rollback();
            }
        }
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(Status.Succeeded, task(id).status()));
    }

    @Test
    void ancestorsExcludeCreatingWorkingSet() {
        String mid = "mid-" + child;
        String leaf = "leaf-" + child;
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", mid);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)", leaf, mid);
        jdbc.update("""
                INSERT INTO working_set_creations
                    (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                VALUES (?, '1', ?, 'running', 'Running', CAST(? AS jsonb), now())
                """, "task-" + child, mid, "{}");
        try {
            var ancestors = rest.getForObject("/api/worksets/" + leaf + "/ancestors", List.class);
            assertFalse(ancestors.contains(mid));
            assertTrue(ancestors.contains("1"));
        } finally {
            jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", mid);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id IN (?, ?)", mid, leaf);
        }
    }

    @Test
    void indexesAllThreeNativeItemsWithInheritedIdentityAndSource() {
        seedRange();
        var families = List.of("a-" + child, "b-" + child, "c-" + child);
        for (String family : families) {
            seedItem(unshared, family, family, false);
        }
        seedItem(unshared, "deleted-" + child, "deleted-" + child, true);
        String second = "other-tracker-" + child;
        seedTracker(second, otherSpace, unshared, "UNSHARED");
        seedItem(second, "other-item-" + child, "other-item-" + child, false);
        String id = submit(new Request("1", child, "index", List.of(space, otherSpace), List.of(), List.of(unshared, second)));
        var completed = awaitStatus(id, Status.Succeeded);
        assertEquals(2, completed.stageProgress().get(Stage.INDEX_UNSHARED_TRACKERS).succeeded());
        assertEquals(3, completed.indexProgress().get(unshared).succeeded());
        assertEquals(1, completed.indexProgress().get(second).succeeded());
        String copiedTracker = bindings().trackers().stream()
                .filter(binding -> space.equals(binding.productSpaceId())).findFirst().orElseThrow().trackerId();
        var indexed = jdbc.queryForList("SELECT local_id FROM light_tracker_items WHERE tracker_id = ? ORDER BY common_id",
                String.class, copiedTracker);
        assertEquals(families.size(), indexed.size());
        for (int index = 0; index < families.size(); index++) {
            String family = families.get(index);
            String localId = indexed.get(index);
            var item = rest.getForObject("/api/worksets/" + child + "/items/" + localId, LightTrackerItemView.class);
            assertEquals(family, item.commonId());
            assertFalse(family.equals(item.localId()));
            assertEquals(3, item.revision());
            assertEquals(family, item.sourceLocalId());
            assertEquals(3, item.sourceRevision());
            assertFalse(item.materialized());
        }
    }

    @Test
    void indexesDerivedParentAndExcludesDeletedSourcesWhileRecordingMissingSources() {
        seedRange();
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/1/children",
                Map.of("workingSetId", parent), Map.class).getStatusCode());
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/" + parent + "/spaces/" + space,
                null, Map.class).getStatusCode());
        String nativeId = "root-" + child;
        seedItem(unshared, nativeId, nativeId, false);
        var derive = rest.postForEntity("/api/worksets/" + parent + "/derive",
                Map.of("sourceTrackerId", unshared, "sourceLocalId", nativeId, "commonId", nativeId,
                        "sourceRevision", 3), bootstrap.web.DeriveView.class);
        assertEquals(HttpStatus.CREATED, derive.getStatusCode());
        String parentTracker = derive.getBody().trackerId();
        String overlap = "overlap-" + child;
        seedItem(parentTracker, overlap, overlap, false);
        seedParentLti(parentTracker, overlap, overlap, overlap, true);
        String deleted = "deleted-" + child;
        seedItem(unshared, deleted, deleted, true);
        seedParentLti(parentTracker, "virtual-deleted-" + child, deleted, deleted, false);
        String localDeleted = "local-deleted-" + child;
        seedItem(parentTracker, localDeleted, localDeleted, true);
        seedParentLti(parentTracker, localDeleted, localDeleted, localDeleted, true);
        String broken = "broken-" + child;
        seedParentLti(parentTracker, broken, broken, "missing-" + child, false);
        String stale = "stale-" + child;
        seedItem(unshared, stale, stale, false);
        seedParentLti(parentTracker, "virtual-stale-" + child, stale, stale, false);
        jdbc.update("UPDATE requirements SET revision = 4 WHERE local_id = ?", stale);

        String id = submit(new Request(parent, child, "index-derived", List.of(space), List.of(), List.of(parentTracker)));
        var completed = awaitStatus(id, Status.PartiallySucceeded);
        assertEquals(2, completed.indexProgress().get(parentTracker).succeeded());
        assertEquals(List.of("NOT_FOUND", "HISTORY_UNAVAILABLE"),
                completed.failures().stream().map(details -> details.reason()).toList());
        var failure = completed.failures().getFirst();
        assertEquals(Stage.INDEX_UNSHARED_TRACKERS, failure.stage());
        assertEquals(broken, failure.localId());
        assertEquals(parentTracker, failure.trackerId());
        assertEquals("NOT_FOUND", failure.reason());
        String copied = bindings().trackers().getFirst().trackerId();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var event = indexEvents.find(child);
            assertNotNull(event);
            assertEquals(id, event.creationId());
            assertEquals(List.of(copied), event.trackerIds());
        });
        String local = jdbc.queryForObject("SELECT local_id FROM light_tracker_items WHERE working_set_id = ? AND common_id = ?",
                String.class, child, nativeId);
        var item = rest.getForObject("/api/worksets/" + child + "/items/" + local, LightTrackerItemView.class);
        assertEquals(nativeId, item.sourceLocalId());
        assertEquals(3, item.sourceRevision());
    }

    @Test
    void keepsPreparingBetweenCommittedBatchesAndSerializesEventRedelivery() throws Exception {
        seedRange();
        String first = "a-" + child;
        String second = "b-" + child;
        String last = "c-" + child;
        for (String family : List.of(first, second, last)) {
            seedItem(unshared, family, family, false);
        }
        String id;
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement("SELECT local_id FROM requirements WHERE local_id = ? FOR UPDATE")) {
                statement.setString(1, last);
                statement.executeQuery().close();
                id = submit(new Request("1", child, "index-running", List.of(space), List.of(), List.of(unshared)));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    var running = task(id);
                    assertEquals(Status.Running, running.status());
                    assertEquals(Stage.INDEX_UNSHARED_TRACKERS, running.stage());
                    assertNotNull(running.indexProgress().get(unshared));
                    assertEquals(2, running.indexProgress().get(unshared).succeeded());
                });
                String firstLocal = jdbc.queryForObject(
                        "SELECT local_id FROM light_tracker_items WHERE working_set_id = ? AND common_id = ?",
                        String.class, child, first);
                var response = rest.getForEntity("/api/worksets/" + child + "/items/" + firstLocal, Map.class);
                assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
                assertEquals("PREPARING", response.getBody().get("reason"));
                assertEquals(HttpStatus.OK, rest.getForEntity(CREATIONS + "/" + id + "/working-set", Map.class).getStatusCode());
                String tracker = jdbc.queryForObject(
                        "SELECT tracker_id FROM trackers WHERE working_set_id = ? AND source_id = ?",
                        String.class, child, unshared);
                jdbc.update("""
                        INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                            source_local_id, source_revision, materialized) VALUES (?, ?, ?, ?, 3, ?, 3, false)
                        """, "existing-" + child, last, tracker, child, last);
                transaction.executeWithoutResult(tx -> events.publishEvent(new WorkingSetCreationSubmitted(id)));
            } finally {
                connection.rollback();
            }
        }
        var completed = awaitStatus(id, Status.Succeeded);
        assertEquals(2, completed.indexProgress().get(unshared).succeeded());
        assertEquals(1, completed.indexProgress().get(unshared).skipped());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM light_tracker_items WHERE working_set_id = ?",
                Integer.class, child));
    }

    private void seedItem(String tracker, String localId, String commonId, boolean deleted) {
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version, delete_flag)
                VALUES (?, ?, ?, 'source', 'description', 3, 0, ?)
                """, localId, commonId, tracker, deleted);
    }

    private void seedParentLti(String tracker, String localId, String commonId, String sourceId, boolean materialized) {
        jdbc.update("""
                INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                    source_local_id, source_revision, materialized) VALUES (?, ?, ?, ?, 3, ?, 3, ?)
                """, localId, commonId, tracker, parent, sourceId, materialized);
    }

    private void seedRange() {
        jdbc.update("INSERT INTO product_spaces VALUES (?), (?)", space, otherSpace);
        jdbc.update("INSERT INTO classifications VALUES (?, 'SHARED'), (?, 'UNSHARED')", shared, unshared);
        seedTracker(shared, space, shared, "SHARED");
        seedTracker(unshared, space, unshared, "UNSHARED");
        seedTracker("unselected-" + child, otherSpace, shared, "SHARED");
    }

    private void seedTracker(String id, String spaceId, String classification, String mode) {
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, ?)
                """, id, spaceId, classification, mode);
    }

    private BindingsView bindings() {
        return rest.getForObject("/api/worksets/" + child + "/bindings", BindingsView.class);
    }

    private String submit(Request request) {
        var response = rest.postForEntity(CREATIONS, request, CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        return response.getBody().creationId();
    }

    private CreationView awaitStatus(String id, Status expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(expected, task(id).status()));
        return task(id);
    }

    private CreationView task(String id) {
        return rest.getForObject(CREATIONS + "/" + id, CreationView.class);
    }

    private Request request(String parent, String requestId) {
        return new Request(parent, child, requestId, List.of(), List.of(), List.of());
    }

    @TestConfiguration
    static class IndexEventConfiguration {
        @Bean
        IndexEvents indexEvents() {
            return new IndexEvents();
        }
    }

    static class IndexEvents {
        private final Map<String, TrackerItemsIndexed> byWorkingSet = new java.util.concurrent.ConcurrentHashMap<>();

        public TrackerItemsIndexed find(String workingSetId) {
            return byWorkingSet.get(workingSetId);
        }

        public void clear(String workingSetId) {
            byWorkingSet.remove(workingSetId);
        }

        @ApplicationModuleListener
        public void on(TrackerItemsIndexed event) {
            byWorkingSet.put(event.workingSetId(), event);
        }
    }
}
