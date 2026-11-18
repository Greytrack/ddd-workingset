package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import sk.workingset.adapters.WorkingSetController.BindingsView;
import sk.workingset.adapters.WorkingSetController.TrackerBindingView;
import sk.workingset.adapters.WorkingSetController.TrackerView;
import sk.workingset.adapters.WorkingSetController.WorkingSetView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BindingAcceptanceTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestRestTemplate rest;

    private final String suffix = UUID.randomUUID().toString();
    private final String ws = fixtureId("WS-" + suffix);
    private final String other = fixtureId("OTHER-" + suffix);
    private final String space = fixtureId("SPACE-" + suffix);
    private final String sharedClass = fixtureId("SC-" + suffix);
    private final String unsharedClass = fixtureId("UC-" + suffix);
    private final String shared = fixtureId("SH-" + suffix);
    private final String unshared = fixtureId("UN-" + suffix);
    private final String sourceItem = fixtureId("SOURCE-" + suffix);
    private final List<String> worksets = new ArrayList<>();

    @BeforeEach
    void provision() {
        for (String child : List.of(ws, other)) {
            createChild("1", child);
        }
        jdbc.update("INSERT INTO product_spaces VALUES (?)", space);
        jdbc.update("INSERT INTO classifications VALUES (?, 'SHARED'), (?, 'UNSHARED')", sharedClass, unsharedClass);
        original(shared, sharedClass, "SHARED", null);
        original(unshared, unsharedClass, "UNSHARED", null);
        businessItem(sourceItem, unshared);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM requirements WHERE tracker_id IN (?, ?)", shared, unshared);
        for (String id : worksets.reversed()) {
            jdbc.update("DELETE FROM light_tracker_items WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", id);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id);
        }
        jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM classifications WHERE classification_id IN (?, ?)", sharedClass, unsharedClass);
    }

    @Test
    void sharedBindingReusesRootTrackerAndRequiresUniqueSpaceAndSlot() {
        assertTrue(rest.getForObject(path("1"), WorkingSetView.class).defaultWorkingSet());
        assertFailure(rest.postForEntity(sharedPath(ws, shared), null, Map.class),
                HttpStatus.CONFLICT, "MissingSpaceBinding");
        assertFailure(rest.postForEntity(path(ws) + "/spaces/" + fixtureId(space + "-missing"), null, Map.class),
                HttpStatus.NOT_FOUND, "ProductSpaceNotFound");
        bindSpace(ws);
        assertFailure(rest.postForEntity(spacePath(ws), null, Map.class), HttpStatus.CONFLICT, "AlreadyBound");

        ResponseEntity<TrackerBindingView> result = rest.postForEntity(sharedPath(ws, shared), null, TrackerBindingView.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(shared, result.getBody().trackerId());
        assertFailure(rest.postForEntity(sharedPath(ws, shared), null, Map.class),
                HttpStatus.CONFLICT, "TrackerAlreadyBound");

        String anotherShared = fixtureId("ALT-" + suffix);
        original(anotherShared, sharedClass, "SHARED", null);
        assertEquals(HttpStatus.CREATED,
                rest.postForEntity(sharedPath(ws, anotherShared), null, TrackerBindingView.class).getStatusCode());
        assertFailure(rest.postForEntity(sharedPath(ws, unshared), null, Map.class),
                HttpStatus.UNPROCESSABLE_ENTITY, "SharingModeMismatch");

        BindingsView bindings = rest.getForObject(path(ws) + "/bindings", BindingsView.class);
        assertEquals(space, bindings.spaces().getFirst().productSpaceId());
        assertTrue(bindings.trackers().stream().anyMatch(binding -> binding.trackerId().equals(shared)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM trackers WHERE working_set_id = ?", Integer.class, ws));
    }

    @Test
    void trackerListIncludesOwnedAndBoundSharedTrackersExactlyOnce() {
        bindSpace(ws);
        String owned = derive(ws, unshared, sourceItem).trackerId();
        String listPath = path(ws) + "/trackers";
        assertEquals(List.of(owned), trackerIds(listPath));

        assertEquals(HttpStatus.CREATED,
                rest.postForEntity(sharedPath(ws, shared), null, Void.class).getStatusCode());
        assertEquals(List.of(owned, shared).stream().sorted().toList(), trackerIds(listPath));
        assertEquals(List.of(), trackerIds(path(other) + "/trackers"));

        assertEquals(HttpStatus.NO_CONTENT, delete(unbindPath(ws, shared)).getStatusCode());
        assertEquals(List.of(owned), trackerIds(listPath));
    }

    private List<String> trackerIds(String path) {
        return List.of(rest.getForObject(path, TrackerView[].class)).stream().map(TrackerView::trackerId).toList();
    }

    @Test
    void sharedDefaultComesFromRootAndIsNotReplacedAfterUnbindOrDelete() {
        String alternate = fixtureId("ALT-" + suffix);
        original(alternate, sharedClass, "SHARED", null);
        jdbc.update("UPDATE trackers SET is_default = true WHERE tracker_id = ?", shared);
        for (String child : List.of(ws, other)) {
            bindSpace(child);
            assertEquals(HttpStatus.CREATED,
                    rest.postForEntity(sharedPath(child, alternate), null, Void.class).getStatusCode());
        }
        assertEquals(Map.of(alternate, false), trackerDefaults(ws));
        for (String child : List.of(ws, other)) {
            assertEquals(HttpStatus.CREATED,
                    rest.postForEntity(sharedPath(child, shared), null, Void.class).getStatusCode());
        }
        assertEquals(Map.of(shared, true, alternate, false), trackerDefaults(ws));

        assertEquals(HttpStatus.NO_CONTENT, delete(unbindPath(ws, shared)).getStatusCode());
        assertEquals(Map.of(alternate, false), trackerDefaults(ws));
        assertEquals(Map.of(shared, true, alternate, false), trackerDefaults(other));

        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(shared)).getStatusCode());
        assertEquals(Map.of(alternate, false), trackerDefaults(other));
    }

    private Map<String, Boolean> trackerDefaults(String id) {
        return List.of(rest.getForObject(path(id) + "/trackers", TrackerView[].class)).stream()
                .collect(java.util.stream.Collectors.toMap(TrackerView::trackerId, TrackerView::isDefault));
    }

    @Test
    void doesNotCreateSecondDerivationForTheSameParentTracker() {
        bindSpace(ws);
        derive(ws, unshared, sourceItem);
        BindingFailure failure = assertThrows(BindingFailure.class, () -> derive(ws, unshared, sourceItem));
        assertEquals(BindingFailure.Reason.TrackerAlreadyBound, failure.reason());
    }

    @Test
    void grandchildRequiresItsDirectParentsRangeAndSharedReuseKeepsTheOriginalId() {
        String grandchild = fixtureId("GRAND-" + suffix);
        createChild(ws, grandchild);
        bindSpace(grandchild);

        assertFailure(rest.postForEntity(sharedPath(grandchild, shared), null, Map.class),
                HttpStatus.NOT_FOUND, "SourceTrackerNotFound");
        assertDirectSourceFailure(() -> derive(grandchild, unshared, sourceItem));

        bindSpace(other);
        DeriveFixture.Result sibling = derive(other, unshared, sourceItem);
        assertDirectSourceFailure(() -> derive(grandchild, sibling.trackerId(), sourceItem));

        bindSpace(ws);
        assertEquals(HttpStatus.CREATED, rest.postForEntity(sharedPath(ws, shared), null, Void.class).getStatusCode());
        ResponseEntity<TrackerBindingView> sharedAgain = rest.postForEntity(sharedPath(grandchild, shared), null, TrackerBindingView.class);
        assertEquals(HttpStatus.CREATED, sharedAgain.getStatusCode());
        assertEquals(shared, sharedAgain.getBody().trackerId());

        assertEquals(HttpStatus.NO_CONTENT, delete(unbindPath(ws, shared)).getStatusCode());
        assertEquals(shared, rest.getForObject(path(grandchild) + "/bindings", BindingsView.class)
                .trackers().getFirst().trackerId());
        String laterChild = fixtureId("LATER-" + suffix);
        createChild(ws, laterChild);
        bindSpace(laterChild);
        assertFailure(rest.postForEntity(sharedPath(laterChild, shared), null, Map.class),
                HttpStatus.NOT_FOUND, "SourceTrackerNotFound");
    }

    @Test
    void sharedUnbindPreservesTrackerItemAndSpaceBinding() {
        bindSpace(ws);
        assertEquals(HttpStatus.CREATED, rest.postForEntity(sharedPath(ws, shared), null, Void.class).getStatusCode());
        businessItem(fixtureId("ITEM-" + suffix), shared);
        assertFailure(delete(spacePath(ws)), HttpStatus.CONFLICT, "SpaceStillHasTrackers");

        assertEquals(HttpStatus.NO_CONTENT, delete(unbindPath(ws, shared)).getStatusCode());
        assertEquals(1, count("SELECT count(*) FROM requirements WHERE tracker_id = ?", shared));
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
        BindingsView bindings = rest.getForObject(path(ws) + "/bindings", BindingsView.class);
        assertTrue(bindings.trackers().isEmpty());
        assertEquals(space, bindings.spaces().getFirst().productSpaceId());
        assertFailure(delete(trackerPath(shared)), HttpStatus.CONFLICT, "TrackerHasItems");
    }

    @Test
    void nonsharedUnbindRejectsItemsAndEmptyTrackerCanBeRemoved() {
        bindSpace(ws);
        DeriveFixture.Result target = derive(ws, unshared, sourceItem);
        String tracker = target.trackerId();
        assertFailure(delete(unbindPath(ws, tracker)), HttpStatus.CONFLICT, "TrackerHasItems");
        assertEquals(1, count("SELECT count(*) FROM light_tracker_items WHERE tracker_id = ?", tracker));
        assertEquals(1, count("SELECT count(*) FROM tracker_bindings WHERE tracker_id = ?", tracker));
        assertFailure(delete(trackerPath(unshared)), HttpStatus.CONFLICT, "TrackerHasItems");

        jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", target.item().localId().value());
        assertFailure(delete(trackerPath(unshared)), HttpStatus.CONFLICT, "TrackerHasItems");
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", sourceItem);
        assertFailure(delete(trackerPath(unshared)), HttpStatus.CONFLICT, "TrackerHasDerivedChildren");

        assertEquals(HttpStatus.NO_CONTENT, delete(unbindPath(ws, tracker)).getStatusCode());
        assertEquals(0, count("SELECT count(*) FROM tracker_bindings WHERE tracker_id = ?", tracker));
        assertEquals(0, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", tracker));
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
        assertEquals(HttpStatus.CREATED, rest.postForEntity(sharedPath(ws, shared), null, Void.class).getStatusCode());
        assertEquals(HttpStatus.CREATED, rest.postForEntity(sharedPath(other, shared), null, Void.class).getStatusCode());

        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(shared)).getStatusCode());
        assertEquals(0, count("SELECT count(*) FROM tracker_bindings WHERE tracker_id = ?", shared));
        assertEquals(2, count("SELECT count(*) FROM space_bindings WHERE product_space_id = ?", space));
        assertFailure(rest.postForEntity("/api/requirements", Map.of("trackerId", shared,
                "title", "A", "description", "deleted tracker", "revision", 3), Map.class),
                HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT");
    }

    @Test
    void productSpaceDeletionReportsAllWorksetsIncludingEmptyBindings() {
        bindSpace(ws);
        bindSpace(other);
        String endpoint = path("1") + "/product-spaces/" + space;
        ResponseEntity<Map> blocked = delete(endpoint);
        assertFailure(blocked, HttpStatus.CONFLICT, "SpaceStillBoundInWorkingSet");
        assertEquals(java.util.stream.Stream.of(other, ws).sorted(java.util.Comparator.comparingLong(Long::parseLong))
                .toList(), blocked.getBody().get("workingSetIds"));

        assertEquals(HttpStatus.NO_CONTENT, delete(spacePath(ws)).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, delete(spacePath(other)).getStatusCode());
        assertFailure(delete(endpoint), HttpStatus.CONFLICT, "SpaceStillHasTrackers");
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", sourceItem);
        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(shared)).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, delete(trackerPath(unshared)).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, delete(endpoint).getStatusCode());
        assertEquals(0, count("SELECT count(*) FROM product_spaces WHERE product_space_id = ?", space));
    }

    @Test
    void worksetDeletionPreflightsAllMembersAndKeepsRootAndSharedTracker() {
        assertFailure(delete(path("1")), HttpStatus.CONFLICT, "DefaultWorkingSetUndeletable");
        bindSpace(ws);
        assertEquals(HttpStatus.CREATED, rest.postForEntity(sharedPath(ws, shared), null, Void.class).getStatusCode());
        DeriveFixture.Result target = derive(ws, unshared, sourceItem);
        businessItem(fixtureId("ITEM-" + suffix), shared);

        ResponseEntity<Map> blocked = delete(path(ws));
        assertFailure(blocked, HttpStatus.CONFLICT, "MemberCannotBeUnbound");
        assertEquals(Map.of(target.trackerId(), "TrackerHasItems"), blocked.getBody().get("members"));
        assertEquals(2, count("SELECT count(*) FROM tracker_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM space_bindings WHERE working_set_id = ?", ws));

        jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", target.item().localId().value());
        assertEquals(HttpStatus.NO_CONTENT, delete(path(ws)).getStatusCode());
        assertEquals(0, count("SELECT count(*) FROM working_sets WHERE working_set_id = ?", ws));
        assertEquals(0, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", target.trackerId()));
        assertEquals(0, count("SELECT count(*) FROM space_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM requirements WHERE tracker_id = ?", shared));
    }

    @Test
    void middleWorksetRejectsDeleteBeforeRemovingAnyBindings() {
        bindSpace(ws);
        assertEquals(HttpStatus.CREATED, rest.postForEntity(sharedPath(ws, shared), null, Void.class).getStatusCode());
        String grandchild = fixtureId("GRAND-" + suffix);
        createChild(ws, grandchild);

        ResponseEntity<Map> blocked = delete(path(ws));
        assertFailure(blocked, HttpStatus.CONFLICT, "WorkingSetHasChildren");
        assertEquals(List.of(grandchild), blocked.getBody().get("childWorkingSetIds"));

        assertEquals(ws, rest.getForObject(path(grandchild), WorkingSetView.class).parentWorkingSetId());
        BindingsView bindings = rest.getForObject(path(ws) + "/bindings", BindingsView.class);
        assertEquals(shared, bindings.trackers().getFirst().trackerId());
        assertEquals(space, bindings.spaces().getFirst().productSpaceId());

        assertEquals(HttpStatus.NO_CONTENT, delete(path(grandchild)).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, delete(path(ws)).getStatusCode());
        assertEquals(0, count("SELECT count(*) FROM space_bindings WHERE working_set_id = ?", ws));
        assertEquals(0, count("SELECT count(*) FROM tracker_bindings WHERE working_set_id = ?", ws));
        assertEquals(1, count("SELECT count(*) FROM trackers WHERE tracker_id = ?", shared));
        assertFailure(delete(path("1")), HttpStatus.CONFLICT, "DefaultWorkingSetUndeletable");
    }

    @Test
    void concurrentChildCreationAndParentDeletionCannotLeaveADanglingParent() throws Exception {
        String child = fixtureId("RACE-CHILD-" + suffix);
        CyclicBarrier start = new CyclicBarrier(2);
        try (java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Future<ResponseEntity<Map>> create = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return rest.postForEntity(path(ws) + "/children", Map.of("name", "并发创建"), Map.class);
            });
            java.util.concurrent.Future<ResponseEntity<Map>> remove = executor.submit(() -> {
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
        try (java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Future<org.springframework.http.HttpStatusCode> create = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return rest.postForEntity("/api/requirements", Map.of("trackerId", shared,
                        "title", "A", "description", "concurrent", "revision", 3), Map.class)
                        .getStatusCode();
            });
            java.util.concurrent.Future<org.springframework.http.HttpStatusCode> remove = executor.submit(() -> {
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
                INSERT INTO requirements (local_id, common_id, tracker_id, title, description, revision, lock_version)
                VALUES (?, ?, ?, 'A', 'test', 3, 0)
                """, id, fixtureId("family-" + id), tracker);
    }

    private int count(String sql, String parameter) {
        return jdbc.queryForObject(sql, Integer.class, parameter);
    }

    private ResponseEntity<Map> delete(String path) {
        return rest.exchange(path, HttpMethod.DELETE, HttpEntity.EMPTY, Map.class);
    }

    private static String unbindPath(String id, String tracker) {
        return path(id) + "/trackers/" + tracker;
    }

    private static String trackerPath(String tracker) {
        return "/api/trackers/" + tracker;
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
        assertEquals(HttpStatus.CREATED, rest.postForEntity(spacePath(id), null, Void.class).getStatusCode());
    }

    private DeriveFixture.Result derive(String target, String sourceTracker, String sourceLocalId) {
        return DeriveFixture.derive(jdbc, target, sourceTracker, sourceLocalId, Revision.of(3),
                CommonId.of(fixtureId("family-" + sourceLocalId)));
    }

    private static void assertDirectSourceFailure(org.junit.jupiter.api.function.Executable action) {
        BindingFailure failure = org.junit.jupiter.api.Assertions.assertThrows(BindingFailure.class, action);
        assertEquals(BindingFailure.Reason.SourceTrackerNotFound, failure.reason());
    }

    private String spacePath(String id) {
        return path(id) + "/spaces/" + space;
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
}
