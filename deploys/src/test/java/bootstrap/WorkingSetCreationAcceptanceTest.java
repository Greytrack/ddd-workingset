package bootstrap;


import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.adapters.WorkingSetController.BindingsView;
import com.airedgesoft.sharekernel.workingset.adapters.WorkingSetController.TrackerView;
import com.airedgesoft.sharekernel.workingset.adapters.WorkingSetController.WorkingSetView;
import com.airedgesoft.sharekernel.workingset.adapters.WorkingSetCreationController.CreationView;
import com.airedgesoft.sharekernel.workingset.application.command.WorkingSetCreationService;
import com.airedgesoft.sharekernel.workingset.domain.event.TrackerItemsIndexedEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.WorkingSetCreationSubmittedEvent;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetCreation;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetCreation.Mode;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetCreation.Request;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetCreation.Stage;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetCreation.Status;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "workingset.creation.index-batch-size=2")
@Import(WorkingSetCreationAcceptanceTest.IndexEventConfiguration.class)
class WorkingSetCreationAcceptanceTest {
    private static final String CREATIONS = "/api/working-set-creations";
    private static final String SHARED_CLASSIFICATION = "14010102";
    private static final String UNSHARED_CLASSIFICATION = "14010101";
    private String child = fixtureId("child-" + UUID.randomUUID());
    private final String parent = fixtureId("p-" + child);
    private final String space = fixtureId("space-" + child);
    private final String otherSpace = fixtureId("other-" + child);
    private final String shared = fixtureId("shared-" + child);
    private final String unshared = fixtureId("unshared-" + child);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private IndexEvents indexEvents;
    @Autowired
    private WorkingSetCreationService creations;
    @Autowired
    private LightTrackerItemRepository items;
    @Autowired
    private ObjectMapper mapper;

    @AfterEach
    void cleanUp() {
        indexEvents.clear(child);
        jdbc.update("DELETE FROM air_ws_items_index WHERE workingset_id = ?", child);
        jdbc.update("DELETE FROM air_ws_items_index WHERE workingset_id = ?", parent);
        jdbc.update("DELETE FROM air_ws_items_index WHERE work_space_id IN (?, ?)", space, otherSpace);
        jdbc.update("DELETE FROM requirements WHERE tracker_id IN (SELECT tracker_id FROM trackers WHERE product_space_id IN (?, ?))",
                space, otherSpace);
        jdbc.update("""
                DELETE FROM event_publication WHERE serialized_event LIKE ANY (
                    SELECT '%' || id || '%' FROM working_set_creations WHERE new_working_set_id IN (?, ?))
                """, child, parent);
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", parent);
        jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM trackers WHERE product_space_id IN (?, ?)", space, otherSpace);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id IN (?, ?)", space, otherSpace);
    }

    @Test
    void createsOneChildAndExposesProgressWithoutPublishingPreparingRange() throws Exception {
        Request request = request("1", "first");
        String task;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = '1' FOR UPDATE")) {
                statement.executeQuery().close();
                ResponseEntity<CreationView> accepted = rest.postForEntity(CREATIONS, request, CreationView.class);
                assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
                task = accepted.getBody().creationId();
                child = accepted.getBody().targetWorkingSetId();
                assertEquals(CREATIONS + "/" + task, accepted.getHeaders().getLocation().getPath());
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Status.Running, task(task).status()));

                ResponseEntity<Map> preparing = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.CONFLICT, preparing.getStatusCode());
                assertEquals("PREPARING", preparing.getBody().get("reason"));
                ResponseEntity<Map> use = rest.postForEntity("/api/worksets/" + child + "/children",
                        Map.of("workingSetId", fixtureId("unused-" + child)), Map.class);
                assertEquals(HttpStatus.CONFLICT, use.getStatusCode());
                assertEquals("PREPARING", use.getBody().get("reason"));
                ResponseEntity<Map> extension = rest.postForEntity("/api/worksets/" + child + "/extensions",
                        Map.of("requestId", "pending-" + child, "trackerIds", List.of(shared)), Map.class);
                assertEquals(HttpStatus.CONFLICT, extension.getStatusCode());
                assertEquals("PREPARING", extension.getBody().get("reason"));
                ResponseEntity<Map> createItem = rest.postForEntity("/api/requirements", Map.of("workingSetId", child,
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
        ResponseEntity<Map> differentRequest = rest.postForEntity(CREATIONS,
                new Request("1", child, request.requestId(), List.of(), List.of(), List.of(), "另一个名称"), Map.class);
        assertEquals(HttpStatus.CONFLICT, differentRequest.getStatusCode());
        assertEquals("WorkingSetAlreadyExists", differentRequest.getBody().get("reason"));
    }

    @Test
    void bodyFailureIsTerminalAndVisibleThroughPolling() {
        ResponseEntity<CreationView> accepted = rest.postForEntity(CREATIONS, request(fixtureId("missing-" + child), "first"), CreationView.class);
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
        String firstFolder = fixtureId("folder-" + space);
        String secondFolder = fixtureId("folder-" + otherSpace);
        jdbc.update("UPDATE trackers SET is_default = true WHERE tracker_id = ?", fixtureId("unselected-" + child));
        Request request = new Request("1", child, "range", List.of(space, otherSpace), List.of(shared),
                List.of(unshared, firstFolder));
        String id = submit(request);
        CreationView completed = awaitStatus(id, Status.Succeeded);
        assertEquals(3, completed.stageProgress().get(Stage.COPY_UNSHARED_TRACKERS).succeeded());
        assertEquals(1, completed.stageProgress().get(Stage.BIND_PRODUCT_AND_TRACKER).succeeded());
        BindingsView bindings = bindings();
        assertEquals(Set.of(space, otherSpace), bindings.spaces().stream()
                .map(binding -> binding.productSpaceId()).collect(Collectors.toSet()));
        assertEquals(4, bindings.trackers().size());
        assertEquals(1, bindings.trackers().stream().filter(binding -> shared.equals(binding.trackerId())).count());
        String copy = bindings.trackers().stream().filter(binding -> "UNSHARED".equals(binding.sharingMode()))
                .filter(binding -> !"10070101".equals(binding.classificationId()))
                .findFirst().orElseThrow().trackerId();
        assertEquals(unshared, jdbc.queryForObject("SELECT source_id FROM trackers WHERE tracker_id = ?", String.class, copy));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_ws_items_index WHERE workingset_id = ?",
                Integer.class, child));
        assertEquals(id, submit(request));
        transaction.executeWithoutResult(tx -> events.publishEvent(new WorkingSetCreationSubmittedEvent(id)));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NOT NULL
                    AND event_type LIKE '%WorkingSetCreationSubmittedEvent'
                """, Integer.class, "%" + id + "%")));
        assertEquals(completed, task(id));
        assertEquals(bindings, bindings());
        assertEquals(Set.of(unshared, firstFolder, secondFolder), new HashSet<>(jdbc.queryForList(
                "SELECT source_id FROM trackers WHERE working_set_id = ? AND source_id IS NOT NULL", String.class, child)));
        assertEquals(2, jdbc.queryForObject("""
                SELECT COUNT(*) FROM air_inst_tracker_rev r JOIN air_ws_tracker t ON t.id = r.id
                WHERE r.workingset_id = ? AND r.tracker_class = 10070101 AND t.sub_class_id = r.tracker_class
                  AND r.base_class = 1007 AND r.class = 100701 AND r.type = 10070101
                """, Integer.class, Long.valueOf(child)));
    }

    @Test
    void missingTrackerSpaceReportsFailureAndKeepsOtherBindings() {
        seedRange();
        String outsideTracker = fixtureId("outside-" + child);
        seedTracker(outsideTracker, otherSpace, UNSHARED_CLASSIFICATION, "UNSHARED");
        String id = submit(new Request("1", child, "partial", List.of(space), List.of(shared),
                List.of(outsideTracker, unshared)));
        CreationView completed = awaitStatus(id, Status.PartiallySucceeded);
        WorkingSetCreation.Failure failure = completed.failures().getFirst();
        assertEquals(1, completed.failures().size());
        assertEquals(Stage.COPY_UNSHARED_TRACKERS, failure.stage());
        assertEquals(outsideTracker, failure.trackerId());
        assertEquals(otherSpace, failure.productSpaceId());
        assertEquals("MissingSpaceBinding", failure.reason());
        assertEquals(1, completed.stageProgress().get(Stage.BIND_PRODUCT_AND_TRACKER).succeeded());
        assertEquals(1, completed.stageProgress().get(Stage.INDEX_UNSHARED_TRACKERS).skipped());
        assertEquals(3, bindings().trackers().size());
        assertEquals(1, bindings().spaces().size());
    }

    @Test
    void createsGrandchildFromItsDirectParentsBindingsWithoutMode() {
        seedRange();
        FixtureIds.insertWorkingSet(jdbc, "1", parent);
        ResponseEntity<CreationView> extended = rest.postForEntity("/api/worksets/" + parent + "/extensions",
                Map.of("requestId", "extend-parent-" + child, "trackerIds", List.of(shared, unshared)), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, extended.getStatusCode());
        awaitStatus(extended.getBody().creationId(), Status.Succeeded);
        BindingsView parentScope = rest.getForObject("/api/worksets/" + parent + "/bindings", BindingsView.class);
        String parentTracker = parentScope.trackers().stream()
                .filter(binding -> UNSHARED_CLASSIFICATION.equals(binding.classificationId()))
                .findFirst().orElseThrow().trackerId();

        ResponseEntity<CreationView> created = rest.postForEntity(CREATIONS,
                Map.of("parentWorkingSetId", parent, "requestId", "grandchild-" + child, "name", "Grandchild",
                        "productSpaceIds", List.of(space), "sharedTrackerIds", List.of(shared),
                        "unsharedParentTrackerIds", List.of(parentTracker)), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, created.getStatusCode());
        child = created.getBody().targetWorkingSetId();
        CreationView completed = awaitStatus(created.getBody().creationId(), Status.Succeeded);
        assertEquals("CREATE", completed.mode());
        assertEquals(List.of(space), bindings().spaces().stream().map(binding -> binding.productSpaceId()).toList());
        assertTrue(bindings().trackers().stream().anyMatch(binding -> shared.equals(binding.trackerId())));
        assertTrue(Arrays.stream(rest.getForObject("/api/worksets/" + child + "/trackers", TrackerView[].class))
                .anyMatch(tracker -> parentTracker.equals(tracker.sourceId())));
    }

    @Test
    void rejectsSpaceAndSharedTrackerOutsideTheDirectParentsBindings() {
        seedRange();
        FixtureIds.insertWorkingSet(jdbc, "1", parent);
        FixtureIds.extendWithRootFolder(rest, jdbc, parent, space);
        CreationView completed = awaitStatus(submit(new Request(parent, child, "outside-parent",
                List.of(space, otherSpace), List.of(shared), List.of())), Status.PartiallySucceeded);
        assertEquals(List.of("NOT_INCLUDED", "SourceTrackerNotFound"),
                completed.failures().stream().map(failure -> failure.reason()).sorted().toList());
        assertTrue(completed.failures().stream().allMatch(failure -> failure.stage() == Stage.BIND_PRODUCT_AND_TRACKER));
        assertEquals(List.of(space), bindings().spaces().stream().map(binding -> binding.productSpaceId()).toList());
        assertEquals(List.of("10070101"), bindings().trackers().stream().map(binding -> binding.classificationId()).toList());
    }

    @Test
    void bindsCopiedTrackersThatShareSpaceAndClassification() {
        seedRange();
        String sameSlot = fixtureId("z-" + child);
        seedTracker(sameSlot, space, UNSHARED_CLASSIFICATION, "UNSHARED");
        CreationView completed = awaitStatus(submit(new Request("1", child, "slot", List.of(space), List.of(),
                List.of(unshared, sameSlot))), Status.Succeeded);

        assertEquals(3, completed.stageProgress().get(Stage.COPY_UNSHARED_TRACKERS).succeeded());
        assertEquals(1, completed.stageProgress().get(Stage.BIND_PRODUCT_AND_TRACKER).succeeded());
        assertTrue(completed.failures().isEmpty());
        assertEquals(3, bindings().trackers().size());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ? AND source_id IS NOT NULL",
                Integer.class, child));
    }

    @Test
    void persistsCustomWorkingSetNameAndColor() {
        String id = submit(new Request("1", child, "named", List.of(), List.of(), List.of(),
                "自定义工作集", Mode.CREATE, "#90abcdef"));
        awaitStatus(id, Status.Succeeded);
        WorkingSetView result = rest.getForObject("/api/worksets/" + child, WorkingSetView.class);
        assertEquals("自定义工作集", result.name());
        assertEquals("#90ABCDEF", result.color());
    }

    @Test
    void rejectsExistingTargetWithoutMarkingItPreparing() {
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", child);
        DomainException failure = assertThrows(DomainException.class, () ->
                creations.submit(new Request("1", child, "existing-target", List.of(), List.of(), List.of())));
        assertEquals(BindingResCode.WORKING_SET_ALREADY_EXISTS, failure.getResCode());
        assertEquals(HttpStatus.OK, rest.getForEntity("/api/worksets/" + child, Map.class).getStatusCode());
    }

    @Test
    void rejectsParentThatIsBeingCreated() throws Exception {
        String pendingParent = fixtureId("pending-" + child);
        String id;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = '1' FOR UPDATE")) {
                statement.executeQuery().close();
                id = submit(new Request("1", pendingParent, "pending-parent",
                        List.of(), List.of(), List.of()));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Status.Running, task(id).status()));
                ResponseEntity<Map> rejected = rest.postForEntity(CREATIONS, new Request(pendingParent, child, "child-of-pending",
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
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT product_space_id FROM product_spaces WHERE product_space_id = ? FOR UPDATE")) {
                statement.setString(1, space);
                statement.executeQuery().close();
                id = submit(new Request("1", child, "filter", List.of(space), List.of(), List.of()));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                    assertEquals(Status.Running, task(id).status());
                    assertEquals(1, jdbc.queryForObject(
                            "SELECT count(*) FROM working_sets WHERE working_set_id = ?", Integer.class, child));
                });
                ResponseEntity<Map> ordinary = rest.getForEntity("/api/worksets/" + child, Map.class);
                assertEquals(HttpStatus.CONFLICT, ordinary.getStatusCode());
                assertEquals("PREPARING", ordinary.getBody().get("reason"));
                assertFalse(rest.getForObject("/api/worksets/1/children", List.class).contains(child));
                ResponseEntity<WorkingSetView> view = rest.getForEntity(CREATIONS + "/" + id + "/working-set", WorkingSetView.class);
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
    void ancestorsExcludeCreatingWorkingSet() throws Exception {
        String mid = fixtureId("mid-" + child);
        String leaf = fixtureId("leaf-" + child);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, '1')", mid);
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)", leaf, mid);
        String taskId = fixtureId("task-" + child);
        Request request = new Request("1", mid, "running", List.of(), List.of(), List.of(), null, Mode.CREATE);
        long now = System.currentTimeMillis();
        WorkingSetCreation running = WorkingSetCreation.submit(taskId, request, "test", now).start(now);
        jdbc.update("""
                INSERT INTO working_set_creations
                    (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                VALUES (?, '1', ?, 'running', 'Running', CAST(? AS jsonb), now())
                """, taskId, mid, mapper.writeValueAsString(running));
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
    void usesPerTrackerBaselineTimesAndKeepsHeadOnReplay() {
        seedRange();
        String second = fixtureId("head-tracker-" + child);
        seedTracker(second, otherSpace, UNSHARED_CLASSIFICATION, "UNSHARED");
        String earlier = fixtureId("earlier-" + child);
        String later = fixtureId("later-" + child);
        String head = fixtureId("head-" + child);
        String afterHead = fixtureId("after-head-" + child);
        String unregistered = fixtureId("unregistered-" + child);
        long cutoff = 1738368000000L;
        seedItem(unshared, earlier, earlier, false);
        seedItem(unshared, later, later, false);
        seedItem(second, head, head, false);
        seedItem(second, afterHead, afterHead, false);
        seedItem(second, unregistered, unregistered, false);
        jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", unregistered);
        jdbc.update("UPDATE air_ws_items_index SET created_time = ? WHERE local_id = ?", cutoff, earlier);
        jdbc.update("UPDATE air_ws_items_index SET created_time = ? WHERE local_id = ?",
                cutoff + 1000L, later);
        jdbc.update("UPDATE air_ws_items_index SET created_time = ? WHERE local_id = ?",
                System.currentTimeMillis() + 3_600_000L, afterHead);
        String requestId = "baseline-" + child;
        Map<String, Object> request = Map.of("parentWorkingSetId", "1", "requestId", requestId, "mode", "CREATE",
                "productSpaceIds", List.of(space, otherSpace), "unsharedParentTrackerIds", List.of(unshared, second),
                "baselineAtByTracker", Map.of(unshared, cutoff));

        ResponseEntity<CreationView> response = rest.postForEntity(CREATIONS, request, CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        CreationView submitted = response.getBody();
        child = submitted.targetWorkingSetId();
        assertEquals(cutoff, submitted.baselineAtByTracker().get(Long.valueOf(unshared)));
        assertEquals(submitted.createdAt(), submitted.baselineAtByTracker().get(Long.valueOf(second)));
        awaitStatus(submitted.creationId(), Status.Succeeded);
        assertTrue(jdbc.queryForObject("""
                SELECT jsonb_exists(result->'baselineAtByTracker', ?) FROM working_set_creations WHERE id = ?
                """, Boolean.class, second, submitted.creationId()));
        assertEquals(Set.of(earlier, head), Set.copyOf(jdbc.queryForList("""
                SELECT common_id FROM air_ws_items_index WHERE workingset_id = ?
                """, String.class, child)));

        CreationView replay = rest.postForEntity(CREATIONS, request, CreationView.class).getBody();
        assertEquals(submitted.creationId(), replay.creationId());
        assertEquals(submitted.baselineAtByTracker(), replay.baselineAtByTracker());
    }

    @Test
    void extendsAtExplicitBaselineAndRejectsChangingItForTheSameRequest() {
        seedRange();
        createTarget();
        String included = fixtureId("extension-before-" + child);
        String excluded = fixtureId("extension-after-" + child);
        long cutoff = 1738368000000L;
        seedItem(unshared, included, included, false);
        seedItem(unshared, excluded, excluded, false);
        jdbc.update("UPDATE air_ws_items_index SET created_time = ? WHERE local_id = ?",
                cutoff, included);
        String endpoint = "/api/worksets/" + child + "/extensions";
        String requestId = "extension-baseline-" + child;
        Map<String, Object> request = Map.of("requestId", requestId, "trackerIds", List.of(unshared),
                "baselineAtByTracker", Map.of(unshared, cutoff));

        ResponseEntity<CreationView> response = rest.postForEntity(endpoint, request, CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        CreationView submitted = response.getBody();
        awaitStatus(submitted.creationId(), Status.Succeeded);
        assertEquals(List.of(included), jdbc.queryForList("""
                SELECT common_id FROM air_ws_items_index WHERE workingset_id = ?
                """, String.class, child));

        CreationView replay = rest.postForEntity(endpoint, request, CreationView.class).getBody();
        assertEquals(submitted.creationId(), replay.creationId());
        assertEquals(cutoff, replay.baselineAtByTracker().get(Long.valueOf(unshared)));
        assertEquals(HttpStatus.CONFLICT, rest.postForEntity(endpoint,
                        Map.of("requestId", requestId, "trackerIds", List.of(unshared),
                                "baselineAtByTracker", Map.of(unshared, cutoff - 1000L)), Map.class)
                .getStatusCode());
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
        seedTracker(second, otherSpace, UNSHARED_CLASSIFICATION, "UNSHARED");
        seedItem(second, fixtureId("other-item-" + child), fixtureId("other-item-" + child), false);
        String id = submit(new Request("1", child, "index", List.of(space, otherSpace), List.of(), List.of(unshared, second)));
        CreationView completed = awaitStatus(id, Status.Succeeded);
        assertEquals(4, completed.stageProgress().get(Stage.INDEX_UNSHARED_TRACKERS).succeeded());
        assertEquals(3, completed.indexProgress().get(unshared).succeeded());
        assertEquals(1, completed.indexProgress().get(second).succeeded());
        String copiedTracker = bindings().trackers().stream()
                .filter(binding -> !"10070101".equals(binding.classificationId()))
                .filter(binding -> space.equals(binding.productSpaceId())).findFirst().orElseThrow().trackerId();
        List<String> indexed = jdbc.queryForList("SELECT local_id FROM air_ws_items_index WHERE branch_id = ? ORDER BY common_id",
                String.class, copiedTracker);
        assertEquals(families.size(), indexed.size());
        for (int index = 0; index < families.size(); index++) {
            String family = families.get(index);
            String localId = indexed.get(index);
            LightTrackerItem item = items.findById(Long.valueOf(localId)).orElseThrow();
            assertEquals(family, Long.toString(item.commonId()));
            assertFalse(family.equals(Long.toString(item.localId())));
            assertEquals(3, item.revision());
            assertEquals(family, Long.toString(item.sourceLocalId()));
            assertEquals(3, item.sourceRevision());
            assertFalse(item.materialized());
        }
    }

    @Test
    void indexesDerivedParentAndReportsUnavailableSourcesFoundDuringScan() {
        seedRange();
        FixtureIds.insertWorkingSet(jdbc, "1", parent);
        FixtureIds.extendWithRootFolder(rest, jdbc, parent, space);
        String nativeId = fixtureId("root-" + child);
        seedItem(unshared, nativeId, nativeId, false);
        DeriveFixture.Result derive = DeriveFixture.derive(jdbc, parent, unshared, nativeId, 3, Long.valueOf(nativeId));
        String parentTracker = derive.trackerId();
        String overlap = fixtureId("overlap-" + child);
        seedItem(parentTracker, overlap, overlap, false);
        String deleted = fixtureId("deleted-" + child);
        seedItem(unshared, deleted, deleted, true);
        seedParentLti(parentTracker, fixtureId("virtual-deleted-" + child), deleted, deleted, false);
        String localDeleted = fixtureId("local-deleted-" + child);
        seedItem(parentTracker, localDeleted, localDeleted, true);
        // Simulate the window before the soft-delete event has updated the parent LTI.
        jdbc.update("UPDATE air_ws_items_index SET flags = 0 WHERE local_id = ?", localDeleted);
        String broken = fixtureId("broken-" + child);
        seedParentLti(parentTracker, broken, broken, fixtureId("missing-" + child), false);
        String stale = fixtureId("stale-" + child);
        seedItem(unshared, stale, stale, false);
        seedParentLti(parentTracker, fixtureId("virtual-stale-" + child), stale, stale, false);
        jdbc.update("UPDATE requirements SET revision = 4 WHERE local_id = ?", stale);

        String id = submit(new Request(parent, child, "index-derived", List.of(space), List.of(), List.of(parentTracker)));
        CreationView completed = awaitStatus(id, Status.PartiallySucceeded);
        assertEquals(2, completed.indexProgress().get(parentTracker).succeeded());
        assertEquals(4, completed.indexProgress().get(parentTracker).skipped());
        assertEquals(0, completed.indexProgress().get(parentTracker).failed());
        assertEquals(List.of("DELETED", "DELETED", "HISTORY_UNAVAILABLE", "NOT_FOUND"),
                completed.failures().stream().map(details -> details.reason()).sorted().toList());
        WorkingSetCreation.Failure failure = completed.failures().stream().filter(it -> it.reason().equals("NOT_FOUND"))
                .findFirst().orElseThrow();
        assertEquals(Stage.INDEX_UNSHARED_TRACKERS, failure.stage());
        assertEquals(broken, failure.localId());
        assertEquals(parentTracker, failure.trackerId());
        assertEquals("NOT_FOUND", failure.reason());
        String copied = bindings().trackers().stream().filter(binding -> !"10070101".equals(binding.classificationId()))
                .findFirst().orElseThrow().trackerId();
        String folderCopy = bindings().trackers().stream().filter(binding -> "10070101".equals(binding.classificationId()))
                .findFirst().orElseThrow().trackerId();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TrackerItemsIndexedEvent event = indexEvents.find(child);
            assertNotNull(event);
            assertEquals(id, event.creationId());
            assertEquals(Set.of(copied, folderCopy), new HashSet<>(event.trackerIds()));
            assertNotNull(event.occurredAt());
        });
        String local = jdbc.queryForObject("SELECT local_id FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?",
                String.class, child, nativeId);
        LightTrackerItem item = items.findById(Long.valueOf(local)).orElseThrow();
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity("/api/worksets/" + child + "/items/" + local, Map.class).getStatusCode());
        assertEquals(nativeId, Long.toString(item.sourceLocalId()));
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
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("SELECT local_id FROM requirements WHERE local_id = ? FOR UPDATE")) {
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
                        "SELECT local_id FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?",
                        String.class, child, first);
                ResponseEntity<Map> response = rest.getForEntity("/api/requirements/" + firstLocal, Map.class);
                assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
                assertEquals("PREPARING", response.getBody().get("reason"));
                assertEquals(HttpStatus.OK, rest.getForEntity(CREATIONS + "/" + id + "/working-set", Map.class).getStatusCode());
                String tracker = jdbc.queryForObject(
                        "SELECT tracker_id FROM trackers WHERE working_set_id = ? AND source_id = ?",
                        String.class, child, unshared);
                ItemIndexFixture.insert(jdbc, fixtureId("existing-" + child), last, tracker, child, 3, last, 3, false);
                transaction.executeWithoutResult(tx -> events.publishEvent(new WorkingSetCreationSubmittedEvent(id)));
            } finally {
                connection.rollback();
            }
        }
        CreationView completed = awaitStatus(id, Status.Succeeded);
        assertEquals(2, completed.indexProgress().get(unshared).succeeded());
        assertEquals(1, completed.indexProgress().get(unshared).skipped());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM air_ws_items_index WHERE workingset_id = ?",
                Integer.class, child));
    }

    @Test
    void extensionKeepsIndexesWhenBusinessProjectionIsDeferred() {
        seedRange();
        createTarget();
        String conflicted = fixtureId("public-index-conflict-" + child);
        String valid = fixtureId("public-index-valid-" + child);
        seedItem(unshared, conflicted, conflicted, false);
        seedItem(unshared, valid, valid, false);

        CreationView completed = awaitStatus(extension(List.of(unshared)), Status.Succeeded);
        assertTrue(completed.failures().isEmpty());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?
                """, Integer.class, Long.valueOf(child), Long.valueOf(conflicted)));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_ws_items_index WHERE workingset_id = ? AND common_id = ?
                """, Integer.class, Long.valueOf(child), Long.valueOf(valid)));
        // 公共查询视图由业务 BC 消费索引完成事件后投影；当前为待实现的空监听器。
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM air_inst_common
                WHERE id IN (SELECT local_id FROM air_ws_items_index WHERE workingset_id = ?)
                """, Integer.class, Long.valueOf(child)));
    }

    @Test
    void extendsAnExistingChildWithSharedAndDerivedTrackersAndFiltersAlreadyIncludedSources() {
        seedRange();
        createTarget();
        String secondSource = fixtureId("unshared2-" + child);
        seedTracker(secondSource, space, UNSHARED_CLASSIFICATION, "UNSHARED");
        seedItem(unshared, fixtureId("extension-" + child), fixtureId("extension-" + child), false);
        seedItem(secondSource, fixtureId("extension2-" + child), fixtureId("extension2-" + child), false);
        String first = extension(List.of(unshared, secondSource, shared));
        awaitStatus(first, Status.Succeeded);

        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM tracker_bindings WHERE working_set_id = ?",
                Integer.class, child));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM air_ws_items_index WHERE workingset_id = ?",
                Integer.class, child));
        TrackerView[] trackers = rest.getForObject("/api/worksets/" + child + "/trackers", TrackerView[].class);
        assertEquals(4, trackers.length);
        assertTrue(Arrays.stream(trackers).anyMatch(tracker -> shared.equals(tracker.trackerId())));
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity("/api/worksets/" + child + "/derive",
                Map.of("sourceTrackerId", unshared, "sourceLocalId", fixtureId("extension-" + child),
                        "sourceRevision", 3, "commonId", fixtureId("extension-" + child)), Map.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity("/api/worksets/" + child + "/spaces/" + space
                + "/classifications/" + unshared + "/derive", Map.of(), Map.class).getStatusCode());

        ResponseEntity<Map> duplicate = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", fixtureId("duplicate-" + child), "trackerIds", List.of(unshared, secondSource, shared)), Map.class);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals("NoDerivableTracker", duplicate.getBody().get("reason"));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?",
                Integer.class, child));
    }

    @Test
    void extensionKeepsExistingWorkingSetVisibleWhileTaskRuns() throws Exception {
        seedRange();
        createTarget();
        seedItem(unshared, fixtureId("extension-visible-" + child), fixtureId("extension-visible-" + child), false);
        String id;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT working_set_id FROM working_sets WHERE working_set_id = ? FOR UPDATE")) {
                statement.setLong(1, Long.parseLong(child));
                statement.executeQuery().close();
                id = extension(List.of(unshared));
                await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertEquals(Stage.COPY_UNSHARED_TRACKERS, task(id).stage()));
                ResponseEntity<Map> visible = rest.getForEntity("/api/worksets/" + child, Map.class);
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
        assertEquals(2, rest.getForObject("/api/worksets/" + child + "/trackers", TrackerView[].class).length);
    }

    @Test
    void rejectsAnotherExtensionWhileOneIsUnfinished() throws Exception {
        seedRange();
        createTarget();
        String activeId = fixtureId("active-" + child);
        Request request = new Request("1", child, fixtureId("active-request-" + child), List.of(), List.of(),
                List.of(unshared), null, Mode.EXTEND);
        WorkingSetCreation active = WorkingSetCreation.submit(activeId, request, "test", System.currentTimeMillis()).start(System.currentTimeMillis());
        jdbc.update("""
                INSERT INTO working_set_creations
                    (id, parent_working_set_id, new_working_set_id, request_id, status, result, created_at)
                VALUES (?, '1', ?, ?, 'Running', CAST(? AS jsonb), now())
                """, activeId, child, request.requestId(), mapper.writeValueAsString(active));

        ResponseEntity<Map> response = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", fixtureId("another-" + child), "trackerIds", List.of(unshared)), Map.class);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PREPARING", response.getBody().get("reason"));
    }

    private void createTarget() {
        FixtureIds.insertWorkingSet(jdbc, "1", child);
    }

    private String extension(List<String> trackerIds) {
        ResponseEntity<CreationView> response = rest.postForEntity("/api/worksets/" + child + "/extensions",
                Map.of("requestId", "extension-" + UUID.randomUUID(), "trackerIds", trackerIds), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        return response.getBody().creationId();
    }

    private void seedItem(String tracker, String localId, String commonId, boolean deleted) {
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version,
                    delete_flag, working_set_id, created_by, created_time)
                VALUES (?, ?, ?, 'source', 'description', 3, 0, ?,
                    (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?), 'test', ?)
                """, localId, commonId, tracker, deleted, tracker, System.currentTimeMillis());
        String owner = jdbc.queryForObject("SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?", String.class, tracker);
        ItemIndexFixture.insert(jdbc, localId, commonId, tracker, owner, 3, localId, 3, true);
        if (deleted) {
            jdbc.update("UPDATE air_ws_items_index SET flags = 1 WHERE local_id = ?", localId);
        }
    }

    private void seedParentLti(String tracker, String localId, String commonId, String sourceId, boolean materialized) {
        ItemIndexFixture.insert(jdbc, localId, commonId, tracker, parent, 3, sourceId, 3, materialized);
    }

    private void seedRange() {
        jdbc.update("INSERT INTO product_spaces VALUES (?), (?)", space, otherSpace);
        FixtureIds.insertClassification(jdbc, SHARED_CLASSIFICATION, "SHARED");
        FixtureIds.insertClassification(jdbc, UNSHARED_CLASSIFICATION, "UNSHARED");
        seedFolderTracker(space);
        seedFolderTracker(otherSpace);
        seedTracker(shared, space, SHARED_CLASSIFICATION, "SHARED");
        seedTracker(unshared, space, UNSHARED_CLASSIFICATION, "UNSHARED");
        seedTracker(fixtureId("unselected-" + child), otherSpace, SHARED_CLASSIFICATION, "SHARED");
    }

    private void seedTracker(String id, String spaceId, String classification, String mode) {
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, ?)
                """, id, spaceId, classification, mode);
    }

    private String seedFolderTracker(String spaceId) {
        String id = fixtureId("folder-" + spaceId);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode, is_default)
                VALUES (?, '1', ?, 10070101, 'UNSHARED', TRUE)
                """, id, spaceId);
        return id;
    }

    private BindingsView bindings() {
        return rest.getForObject("/api/worksets/" + child + "/bindings", BindingsView.class);
    }

    private String submit(Request request) {
        return creations.submit(request).id();
    }

    private List<String> orderedFamilies() {
        return Stream.of("a-", "b-", "c-").map(label -> fixtureId(label + child))
                .sorted(Comparator.comparingLong(Long::parseLong)).toList();
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
        private final Map<String, TrackerItemsIndexedEvent> byWorkingSet = new ConcurrentHashMap<>();

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
