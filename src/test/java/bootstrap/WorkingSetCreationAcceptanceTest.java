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
import bootstrap.web.WorkingSetCreationController.CreationView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.workingset.domain.WorkingSetCreation.Request;
import sk.workingset.domain.WorkingSetCreation.Status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkingSetCreationAcceptanceTest {
    private static final String CREATIONS = "/api/working-set-creations";
    private final String child = UUID.randomUUID().toString();

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", child);
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
    void rejectsUnimplementedRangeInitializationWithoutCreatingAnEmptyChild() {
        var request = new Request("1", child, "range", List.of("space"), List.of(), List.of());
        var response = rest.postForEntity(CREATIONS, request, Map.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("CAPABILITY_NOT_SUPPORTED", response.getBody().get("reason"));
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity("/api/worksets/" + child, Map.class).getStatusCode());
    }

    private CreationView task(String id) {
        return rest.getForObject(CREATIONS + "/" + id, CreationView.class);
    }

    private Request request(String parent, String requestId) {
        return new Request(parent, child, requestId, List.of(), List.of(), List.of());
    }
}
