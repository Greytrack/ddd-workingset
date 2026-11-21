package bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.workingset.adapters.WorkingSetController.BindingsView;
import sk.workingset.adapters.WorkingSetController.WorkingSetView;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetLineageDomainService;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkingSetLineageAcceptanceTest {
    private final String suffix = UUID.randomUUID().toString();
    private final List<String> created = new ArrayList<>();
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private WorkingSetCommandService commands;
    @Autowired
    private WorkingSetRepository workingSets;
    @Autowired
    private ProductSpaceRepository productSpaces;

    private static String path(String id) {
        return "/api/worksets/" + id;
    }

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
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_inst_common WHERE id = ? AND parent_id = ?
                  AND common_id = id AND workingset_id = id AND tracker = 584190236948832 AND revision = 1
                """, Integer.class, Long.valueOf(grandchild), Long.valueOf(child)));
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
        WorkingSet child = workingSets.findById(WorkingSetId.of(create("1", "empty"))).orElseThrow();
        WorkingSet root = workingSets.findById(WorkingSetId.of("1")).orElseThrow();

        assertTrue(WorkingSetLineageDomainService.comparableBindings(root, child).isEmpty());
        assertTrue(WorkingSetLineageDomainService.comparableBindings(child, root).isEmpty());
    }

    /**
     * The schema is a development baseline without legacy migration, so it must be
     * repeatable on a database it initialized itself: a second run keeps explicit
     * parents and the default Working Set preset.
     */
    @Test
    void repeatedSchemaInitializationKeepsExplicitParentAndDefaultPreset() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement sql = connection.createStatement()) {
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

                try (ResultSet rows = sql.executeQuery("""
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

    @Test
    @Transactional
    void productSpaceProjectionUsesOwnIdentityAndDefaultWorkingSet() {
        long id = Long.parseLong(FixtureIds.fixtureId("product-space-summary-" + suffix));
        productSpaces.save(new ProductSpace(id, "projection test"));

        assertEquals(List.of(id, id, 1L), jdbc.queryForObject("""
                SELECT work_space, common_id, workingset_id FROM air_inst_common WHERE id = ?
                """, (row, index) -> Arrays.asList(
                row.getObject(1, Long.class), row.getObject(2, Long.class), row.getObject(3, Long.class)), id));
    }

    @Test
    void demoRebuildProjectsPhysicalAndInheritedInstances() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement sql = connection.createStatement()) {
                String schema = "common_demo_" + suffix.replace("-", "");
                sql.execute("CREATE SCHEMA " + schema);
                sql.execute("SET LOCAL search_path TO " + schema);
                ResourceDatabasePopulator rebuild = new ResourceDatabasePopulator(
                        new ClassPathResource("schema.sql"), new ClassPathResource("demo-data.sql"));
                rebuild.populate(connection);
                try (ResultSet rows = sql.executeQuery("""
                        SELECT (SELECT count(*) FROM air_inst_workingset)
                               + (SELECT count(*) FROM air_inst_tracker_rev)
                               + (SELECT count(*) FROM product_spaces)
                               + (SELECT count(*) FROM requirements)
                               + (SELECT count(*) FROM air_ws_items_index WHERE has_copied = 0),
                               count(*), array_agg(DISTINCT base_class ORDER BY base_class)
                        FROM air_inst_common
                        """)) {
                    assertTrue(rows.next());
                    assertEquals(rows.getLong(1), rows.getLong(2));
                    assertArrayEquals(new Long[]{1003L, 1007L, 1008L, 1401L},
                            (Long[]) rows.getArray(3).getArray());
                }
                try (ResultSet rows = sql.executeQuery("""
                        SELECT count(*)
                        FROM air_inst_tracker_rev child
                        JOIN air_inst_workingset workspace ON workspace.id = child.workingset_id
                        LEFT JOIN air_inst_tracker_rev parent ON parent.id = child.source_branch_id
                        WHERE child.workingset_id <> 1
                          AND (parent.workingset_id IS DISTINCT FROM workspace.parent_id
                               OR parent.common_id IS DISTINCT FROM child.common_id
                               OR parent.tracker_class IS DISTINCT FROM child.tracker_class)
                        """)) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getLong(1));
                }
                try (ResultSet rows = sql.executeQuery("""
                        SELECT count(*) FROM product_spaces p
                        LEFT JOIN air_inst_common c ON c.id = p.product_space_id
                        WHERE c.work_space IS DISTINCT FROM p.product_space_id
                           OR c.common_id IS DISTINCT FROM p.product_space_id
                           OR c.workingset_id IS DISTINCT FROM 1
                        """)) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getLong(1));
                }
                try (ResultSet rows = sql.executeQuery("""
                        SELECT to_regclass('air_inst_common_source') IS NULL
                           AND to_regprocedure('put_air_inst_common(jsonb)') IS NULL
                           AND to_regclass('air_ws_items_merge_index') IS NOT NULL
                           AND to_regclass('merge_checkpoints') IS NULL
                           AND to_regclass('merge_decision_version') IS NULL
                        """)) {
                    assertTrue(rows.next());
                    assertTrue(rows.getBoolean(1));
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private String create(String parent, String label) {
        ResponseEntity<WorkingSetView> response = rest.postForEntity(path(parent) + "/children",
                Map.of("name", label, "color", "#2468ac"),
                WorkingSetView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().workingSetId();
        created.add(id);
        assertEquals(parent, response.getBody().parentWorkingSetId());
        return id;
    }

    private void assertCreateFailure(String parent, String child, String reason) {
        DomainException failure = Assertions.assertThrows(DomainException.class,
                () -> commands.createChildWorkingSet(WorkingSetId.of(parent), WorkingSetId.of(child)));
        assertEquals(reason, ((BindingResCode) failure.getResCode()).reason());
    }
}
