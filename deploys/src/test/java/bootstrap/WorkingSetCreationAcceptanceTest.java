package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import sk.workingset.adapters.WorkingSetController.WorkingSetView;
import sk.workingset.adapters.WorkingSetController.BindingsView;
import sk.workingset.adapters.WorkingSetCreationController.CreationView;
import sk.workingset.adapters.LightTrackerItemView;
import sk.workingset.adapters.WorkingSetController.TrackerView;
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
import sk.workingset.domain.event.TrackerItemsIndexedEvent;
import sk.workingset.application.command.WorkingSetCreationService;
import sk.workingset.domain.event.WorkingSetCreationSubmittedEvent;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetCreation.Mode;
import sk.workingset.domain.WorkingSetCreation.Request;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetCreation.Status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "workingset.creation.index-batch-size=2")
@Import(WorkingSetCreationAcceptanceTest.IndexEventConfiguration.class)
class WorkingSetCreationAcceptanceTest {
    private static final String CREATIONS = "/api/working-set-creations";
    private String child = fixtureId("child-" + UUID.randomUUID());
    private final String parent = fixtureId("p-" + child);
    private final String space = fixtureId("space-" + child);
    private final String otherSpace = fixtureId("other-" + child);
    private final String shared = fixtureId("shared-" + child);
    private final String unshared = fixtureId("unshared-" + child);

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transaction;
    @Autowired private IndexEvents indexEvents;
    @Autowired private WorkingSetCreationService creations;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;

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
        Request request = request("1", "first");
        String task;
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = '1' FOR UPDATE")) {
                statement.executeQuery().close();
                org.springframework.http.ResponseEntity<CreationView> accepted = rest.postForEntity(CREATIONS, request, CreationView.class);
                assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
                task = accepted.getBody().creationId();
                child = accepted.getBody().targetWorkingSetId();
                assertEquals(CREATIONS + "/" + task, accepted.getHeaders().getLocation().getPath());
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Status.Running, task(task).status()));

                org.springframework.http.ResponseEntity<Map> preparing = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.CONFLICT, preparing.getStatusCode());
                assertEquals("PREPARING", preparing.getBody().get("reason"));
                org.springframework.http.ResponseEntity<Map> use = rest.postForEntity("/api/worksets/" + child + "/children",
                        Map.of("workingSetId", fixtureId("unused-" + child)), Map.class);
                assertEquals(HttpStatus.CONFLICT, use.getStatusCode());
                assertEquals("PREPARING", use.getBody().get("reason"));
                org.springframework.http.ResponseEntity<Map> bind = rest.postForEntity("/api/worksets/" + child + "/spaces/" + space, null, Map.class);
                assertEquals(HttpStatus.CONFLICT, bind.getStatusCode());
                assertEquals("PREPARING", bind.getBody().get("reason"));
                org.springframework.http.ResponseEntity<Map> createItem = rest.postForEntity("/api/requirements", Map.of("workingSetId", child,
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
        org.springframework.http.ResponseEntity<Map> differentRequest = rest.postForEntity(CREATIONS,
                new Request("1", child, request.requestId(), List.of(), List.of(), List.of(), "另一个名称"), Map.class);
        assertEquals(HttpStatus.CONFLICT, differentRequest.getStatusCode());
        assertEquals("WorkingSetAlreadyExists", differentRequest.getBody().get("reason"));
    }

    @Test
    void bodyFailureIsTerminalAndVisibleThroughPolling() {
        org.springframework.http.ResponseEntity<CreationView> accepted = rest.postForEntity(CREATIONS, request(fixtureId("missing-" + child), "first"), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
        String id = accepted.getBody().creationId();
        child = accepted.getBody().targetWorkingSetId();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertEquals(Status.Failed, task(id).status()));
        WorkingSetCreation.Failure failure = task(id).failures().getFirst();
        assertEquals("CREATE_WORKING_SET", failure.stage().name());
        assertEquals("WorkingSetNotFound", failure.reason());
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity("/api/worksets/" + child, Map.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity(CREATIONS + "/258519859202017", Map.class).getStatusCode());
    }

    @Test
    void initializesSelectedSpaceAndTrackersAndKeepsOneCopyAfterRedelivery() {
        seedRange();
        Request request = new Request("1", child, "range", List.of(space), List.of(shared), List.of(unshared));
        String id = submit(request);
        CreationView completed = awaitStatus(id, Status.Succeeded);
        assertEquals(7, completed.progress().succeeded());
        BindingsView bindings = bindings();
        assertEquals(List.of(space), bindings.spaces().stream().map(binding -> binding.productSpaceId()).toList());
        assertEquals(2, bindings.trackers().size());
        assertEquals(1, bindings.trackers().stream().filter(binding -> shared.equals(binding.trackerId())).count());
        String copy = bindings.trackers().stream().filter(binding -> "UNSHARED".equals(binding.sharingMode()))
                .findFirst().orElseThrow().trackerId();
        assertEquals(unshared, jdbc.queryForObject("SELECT source_id FROM trackers WHERE tracker_id = ?", String.class, copy));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM light_tracker_items WHERE working_set_id = ?",
                Integer.class, child));
        assertEquals(id, submit(request));
        transaction.executeWithoutResult(tx -> events.publishEvent(new WorkingSetCreationSubmittedEvent(id)));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NOT NULL
                    AND event_type LIKE '%WorkingSetCreationSubmittedEvent'
                """, Integer.class, "%" + id + "%")));
        assertEquals(completed, task(id));
        assertEquals(bindings, bindings());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void missingTrackerSpaceReportsFailureAndKeepsOtherBindings() {
        seedRange();
        String outsideTracker = fixtureId("outside-" + child);
        seedTracker(outsideTracker, otherSpace, unshared, "UNSHARED");
        String id = submit(new Request("1", child, "partial", List.of(space), List.of(shared),
                List.of(outsideTracker, unshared)));
        CreationView completed = awaitStatus(id, Status.PartiallySucceeded);
        WorkingSetCreation.Failure failure = completed.failures().getFirst();
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
        FixtureIds.insertWorkingSet(jdbc, "1", parent);
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/" + parent + "/spaces/" + space,
                null, Map.class).getStatusCode());
        CreationView completed = awaitStatus(submit(new Request(parent, child, "outside-parent",
                List.of(space, otherSpace), List.of(shared), List.of())), Status.PartiallySucceeded);
        assertEquals(List.of("NOT_INCLUDED", "SourceTrackerNotFound"),
                completed.failures().stream().map(failure -> failure.reason()).toList());
        assertEquals(List.of(space), bindings().spaces().stream().map(binding -> binding.productSpaceId()).toList());
        assertEquals(List.of(), bindings().trackers());
    }

    @Test
    void bindsCopiedTrackersThatShareSpaceAndClassification() {
        seedRange();
        String sameSlot = fixtureId("z-" + child);
        seedTracker(sameSlot, space, unshared, "UNSHARED");
        CreationView completed = awaitStatus(submit(new Request("1", child, "slot", List.of(space), List.of(),
                List.of(unshared, sameSlot))), Status.Succeeded);

        assertEquals(2, completed.stageProgress().get(Stage.COPY_UNSHARED_TRACKERS).succeeded());
        assertEquals(2, completed.stageProgress().get(Stage.BIND_UNSHARED_TRACKERS).succeeded());
        assertTrue(completed.failures().isEmpty());
        assertEquals(2, bindings().trackers().size());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void persistsCustomWorkingSetName() {
        String id = submit(new Request("1", child, "named", List.of(), List.of(), List.of(), "自定义工作集"));
        awaitStatus(id, Status.Succeeded);
        assertEquals("自定义工作集",
                rest.getForObject("/api/worksets/" + child, WorkingSetView.class).name());
    }

    @Test
    void rejectsExistingTargetWithoutMarkingItPreparing() {
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", child);
        sk.workingset.domain.BindingFailure failure = assertThrows(sk.workingset.domain.BindingFailure.class, () ->
                creations.submit(new Request("1", child, "existing-target", List.of(), List.of(), List.of()),
                        "test"));
        assertEquals("WorkingSetAlreadyExists", failure.reason().name());
        assertEquals(HttpStatus.OK, rest.getForEntity("/api/worksets/" + child, Map.class).getStatusCode());
    }

    @Test
    void rejectsParentThatIsBeingCreated() throws Exception {
        String pendingParent = fixtureId("pending-" + child);
        String id;
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = '1' FOR UPDATE")) {
                statement.executeQuery().close();
                id = submit(new Request("1", pendingParent, "pending-parent",
                        List.of(), List.of(), List.of()));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Status.Running, task(id).status()));
                org.springframework.http.ResponseEntity<Map> rejected = rest.postForEntity(CREATIONS, new Request(pendingParent, child, "child-of-pending",
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
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT product_space_id FROM product_spaces WHERE product_space_id = ? FOR UPDATE")) {
                statement.setString(1, space);
                statement.executeQuery().close();
                id = submit(new Request("1", child, "filter", List.of(space), List.of(), List.of()));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertEquals(Status.Running, task(id).status());
                    assertEquals(1, jdbc.queryForObject(
                            "SELECT count(*) FROM working_sets WHERE working_set_id = ?", Integer.class, child));
                });
                org.springframework.http.ResponseEntity<Map> ordinary = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.CONFLICT, ordinary.getStatusCode());
                assertEquals("PREPARING", ordinary.getBody().get("reason"));
                assertFalse(rest.getForObject("/api/worksets/1/children", List.class).contains(child));
                org.springframework.http.ResponseEntity<WorkingSetView> view = rest.getForEntity(CREATIONS + "/" + id + "/working-set", WorkingSetView.class);
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
        String mid = fixtureId("mid-" + child);
        String leaf = fixtureId("leaf-" + child);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", mid);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)", leaf, mid);
        jdbc.update("""
                INSERT INTO working_set_creations
                    (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                VALUES (?, '1', ?, 'running', 'Running', CAST(? AS jsonb), now())
                """, fixtureId("task-" + child), mid, "{}");
        try {
            List ancestors = rest.getForObject("/api/worksets/" + leaf + "/ancestors", List.class);
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
        List<String> families = orderedFamilies();
        for (String family : families) {
            seedItem(unshared, family, family, false);
        }
        seedItem(unshared, fixtureId("deleted-" + child), fixtureId("deleted-" + child), true);
        String second = fixtureId("other-tracker-" + child);
        seedTracker(second, otherSpace, unshared, "UNSHARED");
        seedItem(second, fixtureId("other-item-" + child), fixtureId("other-item-" + child), false);
        String id = submit(new Request("1", child, "index", List.of(space, otherSpace), List.of(), List.of(unshared, second)));
        CreationView completed = awaitStatus(id, Status.Succeeded);
        assertEquals(2, completed.stageProgress().get(Stage.INDEX_UNSHARED_TRACKERS).succeeded());
        assertEquals(3, completed.indexProgress().get(unshared).succeeded());
        assertEquals(1, completed.indexProgress().get(second).succeeded());
        String copiedTracker = bindings().trackers().stream()
                .filter(binding -> space.equals(binding.productSpaceId())).findFirst().orElseThrow().trackerId();
        List<String> indexed = jdbc.queryForList("SELECT local_id FROM light_tracker_items WHERE tracker_id = ? ORDER BY common_id",
                String.class, copiedTracker);
        assertEquals(families.size(), indexed.size());
        for (int index = 0; index < families.size(); index++) {
            String family = families.get(index);
            String localId = indexed.get(index);
            LightTrackerItemView item = rest.getForObject("/api/worksets/" + child + "/items/" + localId, LightTrackerItemView.class);
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
        FixtureIds.insertWorkingSet(jdbc, "1", parent);
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/" + parent + "/spaces/" + space,
                null, Map.class).getStatusCode());
        String nativeId = fixtureId("root-" + child);
        seedItem(unshared, nativeId, nativeId, false);
        DeriveFixture.Result derive = DeriveFixture.derive(jdbc, parent, unshared, nativeId, Revision.of(3), CommonId.of(nativeId));
        String parentTracker = derive.trackerId();
        String overlap = fixtureId("overlap-" + child);
        seedItem(parentTracker, overlap, overlap, false);
        seedParentLti(parentTracker, overlap, overlap, overlap, true);
        String deleted = fixtureId("deleted-" + child);
        seedItem(unshared, deleted, deleted, true);
        seedParentLti(parentTracker, fixtureId("virtual-deleted-" + child), deleted, deleted, false);
        String localDeleted = fixtureId("local-deleted-" + child);
        seedItem(parentTracker, localDeleted, localDeleted, true);
        seedParentLti(parentTracker, localDeleted, localDeleted, localDeleted, true);
        String broken = fixtureId("broken-" + child);
        seedParentLti(parentTracker, broken, broken, fixtureId("missing-" + child), false);
        String stale = fixtureId("stale-" + child);
        seedItem(unshared, stale, stale, false);
        seedParentLti(parentTracker, fixtureId("virtual-stale-" + child), stale, stale, false);
        jdbc.update("UPDATE requirements SET revision = 4 WHERE local_id = ?", stale);

        String id = submit(new Request(parent, child, "index-derived", List.of(space), List.of(), List.of(parentTracker)));
        CreationView completed = awaitStatus(id, Status.PartiallySucceeded);
        assertEquals(2, completed.indexProgress().get(parentTracker).succeeded());
        assertEquals(2, completed.indexProgress().get(parentTracker).skipped());
        assertEquals(0, completed.indexProgress().get(parentTracker).failed());
        assertEquals(List.of("HISTORY_UNAVAILABLE", "NOT_FOUND"),
                completed.failures().stream().map(details -> details.reason()).sorted().toList());
        WorkingSetCreation.Failure failure = completed.failures().stream().filter(it -> it.reason().equals("NOT_FOUND"))
                .findFirst().orElseThrow();
        assertEquals(Stage.INDEX_UNSHARED_TRACKERS, failure.stage());
        assertEquals(broken, failure.localId());
        assertEquals(parentTracker, failure.trackerId());
        assertEquals("NOT_FOUND", failure.reason());
        String copied = bindings().trackers().getFirst().trackerId();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TrackerItemsIndexedEvent event = indexEvents.find(child);
            assertNotNull(event);
            assertEquals(id, event.creationId());
            assertEquals(List.of(copied), event.trackerIds());
            assertNotNull(event.occurredAt());
        });
        String local = jdbc.queryForObject("SELECT local_id FROM light_tracker_items WHERE working_set_id = ? AND common_id = ?",
                String.class, child, nativeId);
        LightTrackerItemView item = rest.getForObject("/api/worksets/" + child + "/items/" + local, LightTrackerItemView.class);
        assertEquals(nativeId, item.sourceLocalId());
        assertEquals(3, item.sourceRevision());
    }

    @Test
    void keepsPreparingBetweenCommittedBatchesAndSerializesEventRedelivery() throws Exception {
        seedRange();
        List<String> families = orderedFamilies();
        String first = families.getFirst();
        String last = families.getLast();
        for (String family : families) {
            seedItem(unshared, family, family, false);
        }
        String id;
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement("SELECT local_id FROM requirements WHERE local_id = ? FOR UPDATE")) {
                statement.setString(1, last);
                statement.executeQuery().close();
                id = submit(new Request("1", child, "index-running", List.of(space), List.of(), List.of(unshared)));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    CreationView running = task(id);
                    assertEquals(Status.Running, running.status());
                    assertEquals(Stage.INDEX_UNSHARED_TRACKERS, running.stage());
                    assertNotNull(running.indexProgress().get(unshared));
                    assertEquals(2, running.indexProgress().get(unshared).succeeded());
                });
                String firstLocal = jdbc.queryForObject(
                        "SELECT local_id FROM light_tracker_items WHERE working_set_id = ? AND common_id = ?",
                        String.class, child, first);
                org.springframework.http.ResponseEntity<Map> response = rest.getForEntity("/api/worksets/" + child + "/items/" + firstLocal, Map.class);
                assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
                assertEquals("PREPARING", response.getBody().get("reason"));
                assertEquals(HttpStatus.OK, rest.getForEntity(CREATIONS + "/" + id + "/working-set", Map.class).getStatusCode());
                String tracker = jdbc.queryForObject(
                        "SELECT tracker_id FROM trackers WHERE working_set_id = ? AND source_id = ?",
                        String.class, child, unshared);
                jdbc.update("""
                        INSERT INTO light_tracker_items (local_id, common_id, tracker_id, working_set_id, revision,
                            source_local_id, source_revision, materialized) VALUES (?, ?, ?, ?, 3, ?, 3, false)
                        """, fixtureId("existing-" + child), last, tracker, child, last);
                transaction.executeWithoutResult(tx -> events.publishEvent(new WorkingSetCreationSubmittedEvent(id)));
            } finally {
                connection.rollback();
            }
        }
        CreationView completed = awaitStatus(id, Status.Succeeded);
        assertEquals(2, completed.indexProgress().get(unshared).succeeded());
        assertEquals(1, completed.indexProgress().get(unshared).skipped());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM light_tracker_items WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void extendsAnExistingChildWithSharedAndDerivedTrackersAndFiltersAlreadyIncludedSources() {
        seedRange();
        createTarget();
        String secondSource = fixtureId("unshared2-" + child);
        seedTracker(secondSource, space, unshared, "UNSHARED");
        seedItem(unshared, fixtureId("extension-" + child), fixtureId("extension-" + child), false);
        seedItem(secondSource, fixtureId("extension2-" + child), fixtureId("extension2-" + child), false);
        String first = extension(List.of(unshared, secondSource, shared));
        awaitStatus(first, Status.Succeeded);

        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM tracker_bindings WHERE working_set_id = ?",
                Integer.class, child));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM light_tracker_items WHERE working_set_id = ?",
                Integer.class, child));
        TrackerView[] trackers = rest.getForObject("/api/worksets/" + child + "/trackers", TrackerView[].class);
        assertEquals(3, trackers.length);
        assertTrue(java.util.Arrays.stream(trackers).anyMatch(tracker -> shared.equals(tracker.trackerId())));
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity("/api/worksets/" + child + "/derive",
                Map.of("sourceTrackerId", unshared, "sourceLocalId", fixtureId("extension-" + child),
                        "sourceRevision", 3, "commonId", fixtureId("extension-" + child)), Map.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity("/api/worksets/" + child + "/spaces/" + space
                + "/classifications/" + unshared + "/derive", Map.of(), Map.class).getStatusCode());

        org.springframework.http.ResponseEntity<Map> duplicate = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", fixtureId("duplicate-" + child), "trackerIds", List.of(unshared, secondSource, shared)), Map.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("NoDerivableTracker", duplicate.getBody().get("reason"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void extensionKeepsExistingWorkingSetVisibleWhileTaskRuns() throws Exception {
        seedRange();
        createTarget();
        seedItem(unshared, fixtureId("extension-visible-" + child), fixtureId("extension-visible-" + child), false);
        String id;
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.PreparedStatement statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = ? FOR UPDATE")) {
                statement.setLong(1, Long.parseLong(child));
                statement.executeQuery().close();
                id = extension(List.of(unshared));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Stage.BIND_PRODUCT_SPACES, task(id).stage()));
                org.springframework.http.ResponseEntity<Map> visible = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.OK, visible.getStatusCode());
            } finally {
                connection.rollback();
            }
        }
        awaitStatus(id, Status.Succeeded);
    }

    @Test
    void extensionKeepsValidTrackerWhenAnotherSelectionFails() {
        seedRange();
        createTarget();
        String valid = fixtureId("valid-" + child);
        seedItem(unshared, valid, valid, false);
        String id = extension(List.of(unshared, fixtureId("missing-" + child)));
        CreationView completed = awaitStatus(id, Status.PartiallySucceeded);
        assertEquals(1, completed.failures().size());
        assertEquals("SourceTrackerNotFound", completed.failures().getFirst().reason());
        assertTrue(rest.getForObject("/api/worksets/" + child + "/trackers", TrackerView[].class).length == 1);
    }

    @Test
    void rejectsAnotherExtensionWhileOneIsUnfinished() throws Exception {
        seedRange();
        createTarget();
        String activeId = fixtureId("active-" + child);
        Request request = new Request("1", child, fixtureId("active-request-" + child), List.of(), List.of(),
                List.of(unshared), null, Mode.EXTEND);
        WorkingSetCreation active = WorkingSetCreation.submit(activeId, request, "test", Instant.now()).start(Instant.now());
        jdbc.update("""
                INSERT INTO working_set_creations
                    (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                VALUES (?, '1', ?, ?, 'Running', CAST(? AS jsonb), now())
                """, activeId, child, request.requestId(), mapper.writeValueAsString(active));

        org.springframework.http.ResponseEntity<Map> response = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", fixtureId("another-" + child), "trackerIds", List.of(unshared)), Map.class);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PREPARING", response.getBody().get("reason"));
    }

    private void createTarget() {
        FixtureIds.insertWorkingSet(jdbc, "1", child);
    }

    private String extension(List<String> trackerIds) {
        org.springframework.http.ResponseEntity<CreationView> response = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", "extension-" + UUID.randomUUID(), "trackerIds", trackerIds), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        return response.getBody().creationId();
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
        seedTracker(fixtureId("unselected-" + child), otherSpace, shared, "SHARED");
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
        return creations.submit(request, "test").id();
    }

    private List<String> orderedFamilies() {
        return java.util.stream.Stream.of("a-", "b-", "c-").map(label -> fixtureId(label + child))
                .sorted(java.util.Comparator.comparingLong(Long::parseLong)).toList();
    }

    private CreationView awaitStatus(String id, Status expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(expected, task(id).status()));
        return task(id);
    }

    private CreationView task(String id) {
        return rest.getForObject(CREATIONS + "/" + id, CreationView.class);
    }

    private Request request(String parent, String requestId) {
        return new Request(parent, child, requestId + "-" + UUID.randomUUID(), List.of(), List.of(), List.of());
    }

    @TestConfiguration
    static class IndexEventConfiguration {
        @Bean
        IndexEvents indexEvents() {
            return new IndexEvents();
        }
    }

    static class IndexEvents {
        private final Map<String, TrackerItemsIndexedEvent> byWorkingSet = new java.util.concurrent.ConcurrentHashMap<>();

        public TrackerItemsIndexedEvent find(String workingSetId) {
            return byWorkingSet.get(workingSetId);
        }

        public void clear(String workingSetId) {
            byWorkingSet.remove(workingSetId);
        }

        @ApplicationModuleListener
        public void on(TrackerItemsIndexedEvent event) {
            byWorkingSet.put(event.workingSetId(), event);
        }
    }
}
