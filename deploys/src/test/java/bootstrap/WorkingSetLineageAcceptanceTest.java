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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSetLineagePolicy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkingSetLineageAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private WorkingSetCommandService commands;
    @Autowired private WorkingSetRepository workingSets;

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
        WorkingSetView root = rest.getForObject(path("1"), WorkingSetView.class);
        assertTrue(root.defaultWorkingSet());
        assertNull(root.parentWorkingSetId());
        assertEquals("#FFFF", root.color());

        String child = create("1", "child");
        String grandchild = create(child, "grandchild");
        assertEquals("#2468AC", rest.getForObject(path(grandchild), WorkingSetView.class).color());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_inst_workingset
                WHERE id = ? AND parent_id = ? AND id = instance_id AND id = common_id AND id = workingset_id
                  AND base_class = 1008 AND class = 100801 AND type = 10080101
                  AND tracker = 584190236948832 AND revision = 1 AND version_type = 10001
                  AND color = '#2468AC'
                """, Integer.class, Long.valueOf(grandchild), Long.valueOf(child)));
        BindingsView range = rest.getForObject(path(grandchild) + "/bindings", BindingsView.class);
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
        sk.workingset.domain.WorkingSet child = workingSets.findWorkingSet(WorkingSetId.of(create("1", "empty"))).orElseThrow();
        sk.workingset.domain.WorkingSet root = workingSets.findWorkingSet(WorkingSetId.of("1")).orElseThrow();

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
        try (java.sql.Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (java.sql.Statement sql = connection.createStatement()) {
                String schema = "baseline_" + suffix.replace("-", "");
                sql.execute("CREATE SCHEMA " + schema);
                sql.execute("SET LOCAL search_path TO " + schema);

                ResourceDatabasePopulator schemaScript = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
                schemaScript.populate(connection);
                new ResourceDatabasePopulator(new ClassPathResource("tracker-fixtures.sql")).populate(connection);
                sql.execute("INSERT INTO working_sets (working_set_id, parent_working_set_id)"
                        + " VALUES (258520000000080, 1)");
                sql.execute("INSERT INTO working_sets (working_set_id, parent_working_set_id)"
                        + " VALUES (258520000000081, 258520000000080)");
                schemaScript.populate(connection);

                try (java.sql.ResultSet rows = sql.executeQuery("""
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
        org.springframework.http.ResponseEntity<WorkingSetView> response = rest.postForEntity(path(parent) + "/children",
                Map.of("name", label, "color", "#2468ac"),
                WorkingSetView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().workingSetId();
        created.add(id);
        assertEquals(parent, response.getBody().parentWorkingSetId());
        return id;
    }

    private void assertCreateFailure(String parent, String child, String reason) {
        sk.workingset.domain.BindingFailure failure = org.junit.jupiter.api.Assertions.assertThrows(sk.workingset.domain.BindingFailure.class,
                () -> commands.createChildWorkingSet(WorkingSetId.of(parent), WorkingSetId.of(child)));
        assertEquals(reason, failure.reason().name());
    }

    private static String path(String id) {
        return "/api/worksets/" + id;
    }
}
