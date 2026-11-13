package bootstrap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import bootstrap.web.WorkingSetController.WorkingSetView;
import bootstrap.web.WorkingSetController.BindingsView;
import bootstrap.web.WorkingSetCreationController.CreationView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.event.WorkingSetCreationSubmitted;
import sk.workingset.domain.WorkingSetCreation.Request;
import sk.workingset.domain.WorkingSetCreation.Stage;
import sk.workingset.domain.WorkingSetCreation.Status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @AfterEach
    void cleanUp() {
        jdbc.update("""
                DELETE FROM event_publication WHERE serialized_event LIKE ANY (
                    SELECT '%' || id || '%' FROM working_set_creations WHERE new_working_set_id = ?)
                """, child);
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id IN (?, ?)", child, parent);
        jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", child);
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
        assertEquals(5, completed.progress().succeeded());
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
}
