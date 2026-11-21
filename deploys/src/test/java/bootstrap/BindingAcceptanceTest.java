package bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.common.error.DomainException;
import sk.workingset.adapters.WorkingSetController.BindingsView;
import sk.workingset.adapters.WorkingSetController.TrackerView;
import sk.workingset.adapters.WorkingSetController.WorkingSetView;
import sk.workingset.adapters.WorkingSetCreationController.CreationView;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSetCreation.Status;
import sk.workingset.error.BindingResCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BindingAcceptanceTest {
    private final String suffix = UUID.randomUUID().toString();
    private final String ws = fixtureId("WS-" + suffix);
    private final String other = fixtureId("OTHER-" + suffix);
    private final String space = fixtureId("SPACE-" + suffix);
    private final String sharedClass = "14010102";
    private final String unsharedClass = "14010101";
    private final String shared = fixtureId("SH-" + suffix);
    private final String unshared = fixtureId("UN-" + suffix);
    private final String sourceItem = fixtureId("SOURCE-" + suffix);
    private final List<String> worksets = new ArrayList<>();
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TestRestTemplate rest;

    private static String unbindPath(String id, String tracker) {
        return path(id) + "/trackers/" + tracker;
    }

    private static String trackerPath(String tracker) {
        return "/api/trackers/" + tracker;
    }

    private static void assertDirectSourceFailure(Executable action) {
        DomainException failure = Assertions.assertThrows(DomainException.class, action);
        assertEquals(BindingResCode.SOURCE_TRACKER_NOT_FOUND, failure.getResCode());
    }

    private static String sharedPath(String id, String tracker) {
        return path(id) + "/trackers/shared/" + tracker;
    }

    private static String path(String id) {
        return "/api/worksets/" + id;
    }

    private static void assertFailure(ResponseEntity<Map> result, HttpStatus status, String reason) {
        assertEquals(status, result.getStatusCode(), () -> String.valueOf(result.getBody()));
        assertEquals(reason, result.getBody().get("reason"));
    }

    @BeforeEach
    void provision() {
        for (String child : List.of(ws, other)) {
            createChild("1", child);
        }
        jdbc.update("INSERT INTO product_spaces VALUES (?)", space);
        FixtureIds.insertClassification(jdbc, sharedClass, "SHARED");
        FixtureIds.insertClassification(jdbc, unsharedClass, "UNSHARED");
        original(shared, sharedClass, "SHARED", null);
        original(unshared, unsharedClass, "UNSHARED", null);
        businessItem(sourceItem, unshared);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM requirements WHERE tracker_id IN (?, ?)", shared, unshared);
        for (String id : worksets.reversed()) {
            jdbc.update("DELETE FROM air_ws_items_index WHERE workingset_id = ?", id);
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", id);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id);
        }
        jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
    }

    @Test
    void extensionReusesSharedTrackersAndMountsTheirSpace() {
        assertTrue(rest.getForObject(path("1"), WorkingSetView.class).defaultWorkingSet());
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity(sharedPath(ws, shared), null, Map.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.postForEntity(spacePath(ws), null, Map.class).getStatusCode());
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, name, classification_id, sharing_mode, is_default)
                VALUES (?, 1, ?, '文件夹', 10070101, 'UNSHARED', TRUE)
                """, fixtureId("root-folder-" + space), space);
        extendShared(ws, shared);
        assertFailure(rest.postForEntity(path(ws) + "/extensions", FixtureIds.extensionRequest(shared), Map.class),
                HttpStatus.CONFLICT, "NoDerivableTracker");

        String anotherShared = fixtureId("ALT-" + suffix);
        original(anotherShared, sharedClass, "SHARED", null);
        extendShared(ws, anotherShared);

        BindingsView bindings = rest.getForObject(path(ws) + "/bindings", BindingsView.class);
        assertEquals(space, bindings.spaces().getFirst().productSpaceId());
        assertTrue(bindings.trackers().stream().anyMatch(binding -> binding.trackerId().equals(shared)));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE working_set_id = ? AND classification_id <> 10070101
                """, Integer.class, ws));
    }

    @Test
    void trackerListIncludesOwnedAndBoundSharedTrackersExactlyOnce() {
        bindSpace(ws);
        String owned = derive(ws, unshared, sourceItem).trackerId();
        String listPath = path(ws) + "/trackers";
        assertEquals(List.of(owned), trackerIds(listPath));

        extendShared(ws, shared);
        assertEquals(List.of(owned, shared).stream().sorted().toList(), trackerIds(listPath));
        assertEquals(List.of(), trackerIds(path(other) + "/trackers"));

        assertEquals(HttpStatus.NOT_FOUND, delete(unbindPath(ws, shared)).getStatusCode());
        assertEquals(List.of(owned, shared).stream().sorted().toList(), trackerIds(listPath));
    }

    private List<String> trackerIds(String path) {
        return List.of(rest.getForObject(path, TrackerView[].class)).stream()
                .filter(tracker -> !"10070101".equals(tracker.classificationId())).map(TrackerView::trackerId).toList();
    }

    @Test
    void sharedDefaultComesFromRootAndIsNotReplacedAfterDelete() {
        String alternate = fixtureId("ALT-" + suffix);
        original(alternate, sharedClass, "SHARED", null);
        jdbc.update("UPDATE trackers SET is_default = true WHERE tracker_id = ?", shared);
        for (String child : List.of(ws, other)) {
            bindSpace(child);
            extendShared(child, alternate);
        }
        assertEquals(Map.of(alternate, false), trackerDefaults(ws));
        for (String child : List.of(ws, other)) {
            extendShared(child, shared);
        }
        assertEquals(Map.of(shared, true, alternate, false), trackerDefaults(ws));

        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(shared)).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertEquals(Map.of(alternate, false), trackerDefaults(ws));
            assertEquals(Map.of(alternate, false), trackerDefaults(other));
        });
    }

    private Map<String, Boolean> trackerDefaults(String id) {
        return List.of(rest.getForObject(path(id) + "/trackers", TrackerView[].class)).stream()
                .filter(tracker -> !"10070101".equals(tracker.classificationId()))
                .collect(Collectors.toMap(TrackerView::trackerId, TrackerView::isDefault));
    }

    @Test
    void doesNotCreateSecondDerivationForTheSameParentTracker() {
        bindSpace(ws);
        derive(ws, unshared, sourceItem);
        DomainException failure = assertThrows(DomainException.class, () -> derive(ws, unshared, sourceItem));
        assertEquals(BindingResCode.TRACKER_ALREADY_BOUND, failure.getResCode());
    }

    @Test
    void grandchildRequiresItsDirectParentsRangeAndSharedReuseKeepsTheOriginalId() {
        String grandchild = fixtureId("GRAND-" + suffix);
        createChild(ws, grandchild);
        bindSpace(grandchild);

        assertSharedOutsideParent(grandchild);
        assertDirectSourceFailure(() -> derive(grandchild, unshared, sourceItem));

        bindSpace(other);
        DeriveFixture.Result sibling = derive(other, unshared, sourceItem);
        assertDirectSourceFailure(() -> derive(grandchild, sibling.trackerId(), sourceItem));

        bindSpace(ws);
        extendShared(ws, shared);
        extendShared(grandchild, shared);

        assertEquals(shared, rest.getForObject(path(grandchild) + "/bindings", BindingsView.class)
                .trackers().stream().filter(binding -> "SHARED".equals(binding.sharingMode()))
                .findFirst().orElseThrow().trackerId());
    }

    @Test
    void standaloneUnbindRoutesAreUnavailableAndPreserveBindings() {
        bindSpace(ws);
        String folderTrackerRelation = """
                SELECT id FROM air_ws_workingset_branch_relation
                WHERE workingset_id = ? AND work_space = ? AND tracker_id IS NOT NULL AND tracker_id <> ?
                """;
        Long folderTrackerBinding = jdbc.queryForObject(folderTrackerRelation, Long.class,
                Long.valueOf(ws), Long.valueOf(space), Long.valueOf(shared));
        extendShared(ws, shared);
        assertEquals(folderTrackerBinding, jdbc.queryForObject(folderTrackerRelation, Long.class,
                Long.valueOf(ws), Long.valueOf(space), Long.valueOf(shared)));
        businessItem(fixtureId("ITEM-" + suffix), shared);
        assertEquals(HttpStatus.NOT_FOUND, delete(spacePath(ws)).getStatusCode());

        assertEquals(HttpStatus.NOT_FOUND, delete(unbindPath(ws, shared)).getStatusCode());
        assertEquals(folderTrackerBinding, jdbc.queryForObject(folderTrackerRelation, Long.class,
                Long.valueOf(ws), Long.valueOf(space), Long.valueOf(shared)));
        assertEquals(1, count("SELECT count(*) FROM requirements WHERE tracker_id = ?", shared));
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
        BindingsView bindings = rest.getForObject(path(ws) + "/bindings", BindingsView.class);
        assertTrue(bindings.trackers().stream().anyMatch(binding -> shared.equals(binding.trackerId())));
        assertEquals(space, bindings.spaces().getFirst().productSpaceId());
        assertFailure(delete(trackerPath(shared)), HttpStatus.CONFLICT, "TrackerHasItems");
    }

    @Test
    void trackerDeletionRejectsItemsAndRemovesEmptyTrackerAndItsBinding() {
        bindSpace(ws);
        DeriveFixture.Result target = derive(ws, unshared, sourceItem);
        String tracker = target.trackerId();
        assertFailure(delete(trackerPath(tracker)), HttpStatus.CONFLICT, "TrackerHasItems");
        assertEquals(1, count("SELECT count(*) FROM air_ws_items_index WHERE branch_id = ?", tracker));
        assertEquals(1, count("SELECT count(*) FROM tracker_bindings WHERE tracker_id = ?", tracker));
        assertFailure(delete(trackerPath(unshared)), HttpStatus.CONFLICT, "TrackerHasItems");

        jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", target.item().localId().value());
        assertFailure(delete(trackerPath(unshared)), HttpStatus.CONFLICT, "TrackerHasItems");
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", sourceItem);
        assertFailure(delete(trackerPath(unshared)), HttpStatus.CONFLICT, "TrackerHasDerivedChildren");

        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(tracker)).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertEquals(0, count("SELECT count(*) FROM tracker_bindings WHERE tracker_id = ?", tracker));
            assertEquals(0, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", tracker));
        });
    }

    @Test
    void trackerDeletionRejectsOrganizationChildrenAndRemovesOnlyItsBindings() {
        String child = fixtureId("CHILD-" + suffix);
        original(child, unsharedClass, "UNSHARED", shared);
        assertFailure(delete(trackerPath(shared)), HttpStatus.CONFLICT, "TrackerHasOrgChildren");
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE parent_id = ?", shared));
        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(child)).getStatusCode());
        bindSpace(ws);
        bindSpace(other);
        extendShared(ws, shared);
        extendShared(other, shared);

        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(shared)).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertEquals(0, count("SELECT count(*) FROM tracker_bindings WHERE tracker_id = ?", shared));
            assertEquals(0, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
        });
        assertEquals(2, count("SELECT count(*) FROM space_bindings WHERE product_space_id = ?", space));
        assertFailure(rest.postForEntity("/api/requirements", Map.of("trackerId", shared,
                        "title", "A", "description", "deleted tracker", "revision", 3), Map.class),
                HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT");
    }

    @Test
    void deletesOnlyAnUnboundEmptyProductSpaceFromTheDefaultWorkset() {
        String endpoint = path("1") + "/product-spaces/" + space;
        assertEquals(HttpStatus.NOT_FOUND, delete("/api/product-spaces/" + space).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, delete(path(ws) + "/product-spaces/" + space).getStatusCode());
        jdbc.update("UPDATE requirements SET delete_flag = true WHERE local_id = ?", sourceItem);
        assertFailure(delete(endpoint), HttpStatus.CONFLICT, "SpaceHasBusinessReferences");

        jdbc.update("DELETE FROM requirements WHERE local_id = ?", sourceItem);
        long retainedReference = Long.parseLong(fixtureId("direct-reference-" + suffix));
        jdbc.update("INSERT INTO air_inst_common (id, base_class, work_space) VALUES (?, 1001, ?)",
                retainedReference, space);
        assertFailure(delete(endpoint), HttpStatus.CONFLICT, "SpaceHasBusinessReferences");
        jdbc.update("DELETE FROM air_inst_common WHERE id = ?", retainedReference);

        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                VALUES (?, ?, ?, ?, 'SHARED', 'SHARED_REUSE'), (?, ?, ?, ?, 'SHARED', 'SHARED_REUSE')
                """, ws, shared, space, sharedClass, other, shared, space, sharedClass);
        assertFailure(delete(endpoint), HttpStatus.CONFLICT, "SpaceStillBound");
        assertEquals(2, count("SELECT count(*) FROM space_bindings WHERE product_space_id = ?", space));
        assertEquals(1, count("SELECT count(*) FROM product_spaces WHERE product_space_id = ?", space));
        assertEquals(2, count("SELECT count(*) FROM trackers WHERE product_space_id = ?", space));
        jdbc.update("DELETE FROM tracker_bindings WHERE product_space_id = ?", space);

        assertEquals(HttpStatus.ACCEPTED, delete(endpoint).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertEquals(0, count("SELECT count(*) FROM product_spaces WHERE product_space_id = ?", space));
            assertEquals(0, count("SELECT count(*) FROM space_bindings WHERE product_space_id = ?", space));
            assertEquals(0, count("SELECT count(*) FROM trackers WHERE product_space_id = ?", space));
        });
    }

    @Test
    void worksetDeletionRejectsBindingsAndOwnedTrackersBeforeDeletingTheWorkset() {
        assertFailure(delete(path("1")), HttpStatus.CONFLICT, "DefaultWorkingSetUndeletable");
        String owned = fixtureId("owned-" + suffix);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, ?, ?, ?, 'UNSHARED')
                """, owned, unshared, ws, space, unsharedClass);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                VALUES (?, ?, ?, ?, 'UNSHARED', 'UNSHARED_DERIVATION')
                """, ws, owned, space, unsharedClass);

        assertFailure(delete(path(ws)), HttpStatus.CONFLICT, "WorkingSetStillBound");
        assertEquals(1, count("SELECT count(*) FROM tracker_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM space_bindings WHERE working_set_id = ?", ws));

        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", ws);
        assertFailure(delete(path(ws)), HttpStatus.CONFLICT, "WorkingSetHasTrackers");
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", owned));
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", owned);
        assertEquals(HttpStatus.NO_CONTENT, delete(path(ws)).getStatusCode());
        assertEquals(0, count("SELECT count(*) FROM working_sets WHERE working_set_id = ?", ws));
        assertEquals(0, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", owned));
        assertEquals(0, count("SELECT count(*) FROM space_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
    }

    @Test
    void middleWorksetRejectsDeleteBeforeRemovingAnyBindings() {
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id, classification_id, sharing_mode, origin)
                VALUES (?, ?, ?, ?, 'SHARED', 'SHARED_REUSE')
                """, ws, shared, space, sharedClass);
        String grandchild = fixtureId("GRAND-" + suffix);
        createChild(ws, grandchild);

        ResponseEntity<Map> blocked = delete(path(ws));
        assertFailure(blocked, HttpStatus.CONFLICT, "WorkingSetHasChildren");
        assertEquals(List.of(grandchild), blocked.getBody().get("childWorkingSetIds"));

        assertEquals(ws, rest.getForObject(path(grandchild), WorkingSetView.class).parentWorkingSetId());
        BindingsView bindings = rest.getForObject(path(ws) + "/bindings", BindingsView.class);
        assertTrue(bindings.trackers().stream().anyMatch(binding -> shared.equals(binding.trackerId())));
        assertEquals(space, bindings.spaces().getFirst().productSpaceId());

        assertEquals(HttpStatus.NO_CONTENT, delete(path(grandchild)).getStatusCode());
        assertFailure(delete(path(ws)), HttpStatus.CONFLICT, "WorkingSetStillBound");
        assertEquals(1, count("SELECT count(*) FROM space_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM tracker_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
        assertFailure(delete(path("1")), HttpStatus.CONFLICT, "DefaultWorkingSetUndeletable");
    }

    @Test
    void concurrentChildCreationAndParentDeletionCannotLeaveADanglingParent() throws Exception {
        String child = fixtureId("RACE-CHILD-" + suffix);
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<ResponseEntity<Map>> create = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return rest.postForEntity(path(ws) + "/children", Map.of("name", "并发创建"), Map.class);
            });
            Future<ResponseEntity<Map>> remove = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return delete(path(ws));
            });

            ResponseEntity<Map> createResult = create.get(10, TimeUnit.SECONDS);
            if (createResult.getStatusCode() == HttpStatus.CREATED) {
                String createdChild = (String) createResult.getBody().get("workingSetId");
                worksets.add(createdChild);
                assertFailure(remove.get(10, TimeUnit.SECONDS), HttpStatus.CONFLICT, "WorkingSetHasChildren");
                assertEquals(ws, rest.getForObject(path(createdChild), WorkingSetView.class).parentWorkingSetId());
            } else {
                assertFailure(createResult, HttpStatus.NOT_FOUND, "WorkingSetNotFound");
                assertEquals(HttpStatus.NO_CONTENT, remove.get(10, TimeUnit.SECONDS).getStatusCode());
                assertEquals(0, count("SELECT count(*) FROM working_sets WHERE working_set_id = ?", child));
            }
        }
    }

    @Test
    void concurrentItemCreationAndTrackerDeletionCannotLeaveAnOrphan() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<HttpStatusCode> create = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return rest.postForEntity("/api/requirements", Map.of("trackerId", shared,
                                "title", "A", "description", "concurrent", "revision", 3), Map.class)
                        .getStatusCode();
            });
            Future<HttpStatusCode> remove = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return delete(trackerPath(shared)).getStatusCode();
            });

            if (create.get(10, TimeUnit.SECONDS) == HttpStatus.CREATED) {
                assertEquals(HttpStatus.CONFLICT, remove.get(10, TimeUnit.SECONDS));
                assertEquals(1, count("SELECT count(*) FROM requirements WHERE tracker_id = ?", shared));
                assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
            } else {
                assertEquals(HttpStatus.CONFLICT, create.get(10, TimeUnit.SECONDS));
                assertEquals(HttpStatus.NO_CONTENT, remove.get(10, TimeUnit.SECONDS));
                assertEquals(0, count("SELECT count(*) FROM requirements WHERE tracker_id = ?", shared));
                assertEquals(0, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
            }
        }
    }

    private void businessItem(String id, String tracker) {
        jdbc.update("""
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version,
                    working_set_id, created_by, created_time)
                VALUES (?, ?, ?, 'A', 'test', 3, 0,
                    (SELECT workingset_id FROM air_inst_tracker_rev WHERE id = ?), 'test', ?)
                """, id, fixtureId("family-" + id), tracker, tracker, System.currentTimeMillis());
    }

    private int count(String sql, String parameter) {
        return jdbc.queryForObject(sql, Integer.class, parameter);
    }

    private ResponseEntity<Map> delete(String path) {
        return rest.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, Map.class);
    }

    private void original(String tracker, String classification, String mode, String parent) {
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode, parent_id)
                VALUES (?, '1', ?, ?, ?, ?)
                """, tracker, space, classification, mode, parent);
    }

    private void createChild(String parent, String child) {
        FixtureIds.insertWorkingSet(jdbc, parent, child);
        worksets.add(child);
    }

    private void bindSpace(String id) {
        String folder = FixtureIds.ensureDefaultTracker(jdbc, id, space);
        jdbc.update("""
                INSERT INTO air_ws_workingset_branch_relation (id, workingset_id, work_space, tracker_id)
                VALUES (?, ?, ?, ?)
                """, Long.valueOf(fixtureId("mount-" + folder)), Long.valueOf(id), Long.valueOf(space), Long.valueOf(folder));
    }

    private DeriveFixture.Result derive(String target, String sourceTracker, String sourceLocalId) {
        return DeriveFixture.derive(jdbc, target, sourceTracker, sourceLocalId, Revision.of(3),
                CommonId.of(fixtureId("family-" + sourceLocalId)));
    }

    private String spacePath(String id) {
        return path(id) + "/spaces/" + space;
    }

    private void extendShared(String target, String tracker) {
        FixtureIds.extendScope(rest, target, Status.Succeeded, tracker);
    }

    private void assertSharedOutsideParent(String target) {
        CreationView task = FixtureIds.extendScope(rest, target, Status.PartiallySucceeded, shared);
        assertTrue(task.failures().stream().anyMatch(failure ->
                shared.equals(failure.trackerId()) && "SourceTrackerNotFound".equals(failure.reason())));
    }
}
