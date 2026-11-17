package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import sk.workingset.adapters.WorkingSetController.BindingsView;
import sk.workingset.adapters.WorkingSetController.WorkingSetView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSetLineagePolicy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkingSetLineageAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private WorkingSetApplicationService workingSets;

    private final String suffix = UUID.randomUUID().toString();
    private final List<String> created = new ArrayList<>();

    @AfterEach
    void removeWorkingSets() {
        for (String id : created.reversed()) {
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id);
        }
    }

    @Test
    void createsEmptyDescendantsAndQueriesDirectChildrenAndOrderedAncestors() {
        var root = rest.getForObject(path("1"), WorkingSetView.class);
        assertTrue(root.defaultWorkingSet());
        assertNull(root.parentWorkingSetId());

        String child = create("1", "child");
        String grandchild = create(child, "grandchild");
        var range = rest.getForObject(path(grandchild) + "/bindings", BindingsView.class);
        assertTrue(range.spaces().isEmpty());
        assertTrue(range.trackers().isEmpty());
        assertEquals(List.of(grandchild), rest.getForObject(path(child) + "/children", List.class));
        assertEquals(List.of(child, "1"), rest.getForObject(path(grandchild) + "/ancestors", List.class));
        assertEquals(List.of(), rest.getForObject(path("1") + "/ancestors", List.class));
    }

    @Test
    void rejectsSelfReferenceReverseParentAndReparentThroughCreate() {
        String child = create("1", "child");
        String grandchild = create(child, "grandchild");
        String sibling = create("1", "sibling");

        assertCreateFailure(child, child, "WorkingSetCycle");
        assertCreateFailure(grandchild, child, "WorkingSetCycle");
        assertCreateFailure(grandchild, "1", "WorkingSetCycle");
        assertCreateFailure(child, grandchild, "WorkingSetAlreadyExists");
        assertCreateFailure(sibling, grandchild, "WorkingSetParentImmutable");
        assertCreateFailure("258520000000090", "258520000000091", "WorkingSetNotFound");
        assertEquals(child, rest.getForObject(path(grandchild), WorkingSetView.class).parentWorkingSetId());
    }

    @Test
    void persistedEmptyChildHasNoComparableTrackerCandidatesInEitherDirection() {
        var child = workingSets.findWorkingSet(WorkingSetId.of(create("1", "empty")));
        var root = workingSets.findWorkingSet(WorkingSetId.of("1"));

        assertTrue(WorkingSetLineagePolicy.comparableBindings(root, child).isEmpty());
        assertTrue(WorkingSetLineagePolicy.comparableBindings(child, root).isEmpty());
    }

    /**
     * The schema is a development baseline without legacy migration, so it must be
     * repeatable on a database it initialized itself: a second run keeps explicit
     * parents and the default Working Set preset.
     */
    @Test
    void repeatedSchemaInitializationKeepsExplicitParentAndDefaultPreset() throws Exception {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var sql = connection.createStatement()) {
                String schema = "baseline_" + suffix.replace("-", "");
                sql.execute("CREATE SCHEMA " + schema);
                sql.execute("SET LOCAL search_path TO " + schema);

                var schemaScript = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
                schemaScript.populate(connection);
                sql.execute("INSERT INTO working_sets (working_set_id, parent_working_set_id)"
                        + " VALUES (258520000000080, 1)");
                sql.execute("INSERT INTO working_sets (working_set_id, parent_working_set_id)"
                        + " VALUES (258520000000081, 258520000000080)");
                schemaScript.populate(connection);

                try (var rows = sql.executeQuery("""
                        SELECT working_set_id, parent_working_set_id FROM working_sets ORDER BY working_set_id
                        """)) {
                    assertTrue(rows.next());
                    assertEquals("1", rows.getString(1));
                    assertNull(rows.getString(2));
                    assertTrue(rows.next());
                    assertEquals("258520000000080", rows.getString(1));
                    assertEquals("1", rows.getString(2));
                    assertTrue(rows.next());
                    assertEquals("258520000000081", rows.getString(1));
                    assertEquals("258520000000080", rows.getString(2));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private String create(String parent, String label) {
        var response = rest.postForEntity(path(parent) + "/children", Map.of("name", label),
                WorkingSetView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().workingSetId();
        created.add(id);
        assertEquals(parent, response.getBody().parentWorkingSetId());
        return id;
    }

    private void assertCreateFailure(String parent, String child, String reason) {
        var failure = org.junit.jupiter.api.Assertions.assertThrows(sk.workingset.domain.BindingFailure.class,
                () -> workingSets.createChildWorkingSet(WorkingSetId.of(parent), WorkingSetId.of(child)));
        assertEquals(reason, failure.reason().name());
    }

    private static String path(String id) {
        return "/api/worksets/" + id;
    }
}
