package bootstrap;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;

import sk.common.error.DomainException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import sk.workingset.adapters.LightTrackerItemView;
import alm.requirement.adapters.RequirementView;
import sk.workingset.adapters.WorkingSetController.BindingsView;
import sk.workingset.adapters.WorkingSetController.ClassificationView;
import sk.workingset.adapters.WorkingSetController.TrackerView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.workingset.domain.Classification;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** T6-F e 裁定：每个（工作集，空间，分类）一个默认 Tracker；同空间+分类允许出现多个 Tracker。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackerDefaultAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String child = fixtureId("WS-CHILD-" + suffix);
    private final String sharedClassification = fixtureId("CL-SHARED-" + suffix);
    private final java.util.Set<String> testSpaces = new java.util.HashSet<>();

    @AfterEach
    void cleanup() {
        for (String space : testSpaces) {
            jdbc.update("DELETE FROM light_tracker_items WHERE tracker_id IN (SELECT tracker_id FROM trackers WHERE product_space_id = ?)", space);
            jdbc.update("DELETE FROM requirements WHERE tracker_id IN (SELECT tracker_id FROM trackers WHERE product_space_id = ?)", space);
            jdbc.update("DELETE FROM tracker_bindings WHERE product_space_id = ?", space);
            jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
            jdbc.update("DELETE FROM space_bindings WHERE product_space_id = ?", space);
            jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
        }
        jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", child);
        jdbc.update("DELETE FROM classifications WHERE classification_id = ?", sharedClassification);
    }

    @Test
    void creatingProductSpacePublishesDefaultTrackerBindingsAndItemReadAccess() {
        String spaceId = createSpace("1");
        int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications", Integer.class);
        assertEquals(classifications, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE product_space_id = ? AND is_default", Integer.class, spaceId));
        assertEquals(classifications, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'",
                Integer.class, spaceId));
        java.util.List<TrackerView> trackers = Arrays.stream(rest.getForObject("/api/worksets/1/trackers", TrackerView[].class))
                .filter(tracker -> spaceId.equals(tracker.productSpaceId())).toList();
        assertEquals(classifications, trackers.size());
        for (TrackerView tracker : trackers) {
            org.springframework.http.ResponseEntity<String> items = rest.getForEntity("/api/worksets/1/trackers/" + tracker.trackerId() + "/items",
                    String.class);
            assertEquals(HttpStatus.OK, items.getStatusCode(), items::getBody);
            assertEquals("[]", items.getBody());
        }

        BindingsView bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        sk.workingset.adapters.WorkingSetController.SpaceBindingView projectedSpace = bindings.spaces().stream()
                .filter(binding -> spaceId.equals(binding.productSpaceId())).findFirst().orElseThrow();
        assertEquals("INITIAL_PROJECTION", projectedSpace.origin());
        assertEquals(trackers.stream().map(TrackerView::trackerId).sorted().toList(),
                bindings.trackers().stream().filter(binding -> spaceId.equals(binding.productSpaceId()))
                        .map(binding -> binding.trackerId()).sorted().toList());

        String trackerId = trackers.getFirst().trackerId();
        org.springframework.http.ResponseEntity<RequirementView> item = rest.postForEntity("/api/requirements", Map.of("trackerId", trackerId, "workingSetId", "1",
                "title", "验证默认 Tracker", "description", "验证绑定范围", "revision", 1), RequirementView.class);
        assertEquals(HttpStatus.CREATED, item.getStatusCode());
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            LightTrackerItemView[] items = rest.getForObject("/api/worksets/1/trackers/" + trackerId + "/items", LightTrackerItemView[].class);
            assertTrue(Arrays.stream(items).anyMatch(indexed -> item.getBody().localId().equals(indexed.localId())));
        });
    }

    @Test
    void createsAndBindsNamedTrackersUsingClassificationSharingRules() {
        jdbc.update("INSERT INTO classifications VALUES (?, 'SHARED')", sharedClassification);
        String spaceId = createSpace("1");
        ClassificationView[] classifications = rest.getForObject("/api/worksets/metadata/classifications", ClassificationView[].class);
        assertTrue(Arrays.stream(classifications).anyMatch(classification ->
                sharedClassification.equals(classification.classificationId())
                        && classification.sharingMode() == Classification.SharingMode.SHARED));

        String url = "/api/worksets/1/trackers";
        for (Classification.SharingMode mode : Classification.SharingMode.values()) {
            Map<String,String> request = Map.of("productSpaceId", spaceId, "name", "项目-" + mode, "classificationId",
                    "258519859202016", "sharingMode", mode.name());
            org.springframework.http.ResponseEntity<TrackerView> response = rest.postForEntity(url, request, TrackerView.class);
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            TrackerView tracker = response.getBody();
            assertEquals(mode.name(), tracker.sharingMode());
            assertEquals("项目-" + mode, tracker.name());
            assertFalse(tracker.isDefault());
            assertEquals(HttpStatus.OK, rest.getForEntity(url + "/" + tracker.trackerId() + "/items",
                    String.class).getStatusCode());
            assertEquals(HttpStatus.CONFLICT, rest.postForEntity(url, request, Map.class).getStatusCode());

            if (mode == Classification.SharingMode.SHARED) {
                FixtureIds.insertWorkingSet(jdbc, "1", child);
                assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/" + child + "/spaces/" + spaceId,
                        null, Map.class).getStatusCode());
                assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/" + child + "/trackers/shared/"
                        + tracker.trackerId(), null, Map.class).getStatusCode());
                assertEquals(HttpStatus.CONFLICT, rest.postForEntity("/api/worksets/" + child + "/trackers",
                        request, Map.class).getStatusCode());
            }
        }
        Map<String,String> allowed = Map.of("productSpaceId", spaceId, "name", "仅共享", "classificationId",
                sharedClassification, "sharingMode", "SHARED");
        assertEquals(HttpStatus.CREATED, rest.postForEntity(url, allowed, TrackerView.class).getStatusCode());
        Map<String,String> rejected = Map.of("productSpaceId", spaceId, "name", "禁止非共享", "classificationId",
                sharedClassification, "sharingMode", "UNSHARED");
        org.springframework.http.ResponseEntity<Map> refusal = rest.postForEntity(url, rejected, Map.class);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, refusal.getStatusCode());
        assertEquals("SharingModeMismatch", refusal.getBody().get("reason"));
    }

    @Test
    void defaultTrackerIsUniquePerWorkingSetSpaceAndClassification() {
        String spaceId = fixtureId("PS-DEF-" + suffix);
        testSpaces.add(spaceId);
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)", spaceId, "空间-" + suffix);
        String classification = jdbc.queryForObject("SELECT classification_id FROM classifications LIMIT 1", String.class);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id,
                    sharing_mode, is_default)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED', TRUE)
                """, fixtureId("T-DEF1-" + suffix), "默认1-" + suffix, spaceId, classification);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id,
                    sharing_mode, is_default)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED', TRUE)
                """, fixtureId("T-DEF2-" + suffix), "默认2-" + suffix, spaceId, classification));
    }

    @Test
    void trackerNameIsUniqueWithinSpaceAndClassification() {
        String spaceId = fixtureId("PS-NAME-" + suffix);
        testSpaces.add(spaceId);
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)", spaceId, "空间-" + suffix);
        String classification = jdbc.queryForObject("SELECT classification_id FROM classifications LIMIT 1", String.class);
        String name = "同名-" + suffix;
        jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                """, fixtureId("T-N1-" + suffix), name, spaceId, classification);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                """, fixtureId("T-N2-" + suffix), name, spaceId, classification));
    }

    @Test
    void trackerBindingsNoLongerEnforceOneTrackerPerSpaceAndClassification() {
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM pg_indexes
                WHERE tablename = 'tracker_bindings'
                  AND indexdef LIKE '%(working_set_id, product_space_id, classification_id)%'
                """, Integer.class));
    }

    @Test
    void productSpaceCreationFromNonDefaultEntryStillTargetsDefaultWorkingSet() {
        FixtureIds.insertWorkingSet(jdbc, "1", child);
        String spaceId = createSpace(child);

        int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications", Integer.class);
        assertEquals(classifications, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'
                """, Integer.class, spaceId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = ?
                """, Integer.class, spaceId, child));

        BindingsView defaultBindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        assertTrue(defaultBindings.spaces().stream().anyMatch(binding -> spaceId.equals(binding.productSpaceId())),
                "default working set should own the projected space binding");
        BindingsView childBindings = rest.getForObject("/api/worksets/" + child + "/bindings", BindingsView.class);
        assertTrue(childBindings.spaces().stream().noneMatch(binding -> spaceId.equals(binding.productSpaceId())),
                "product space must not be projected into the child working set");
    }

    @Test
    void originalTrackerCannotBeCreatedInChildWorkingSet() {
        ProductSpace space = new ProductSpace(fixtureId("space-domain-" + suffix), "domain");
        Classification classification = new Classification("258831141156800", Classification.SharingMode.UNSHARED);
        assertThrows(DomainException.class, () -> new Tracker(
                TrackerId.of(fixtureId("tracker-domain-" + suffix)), "子工作集原生 Tracker", null,
                WorkingSetId.of(child), space.id(), classification.id(), classification.sharingMode(), null, false));
    }

    @Test
    void trackerNameUniquenessDoesNotCrossProductSpaces() {
        String firstSpace = fixtureId("PS-NAME1-" + suffix);
        String secondSpace = fixtureId("PS-NAME2-" + suffix);
        String classification = jdbc.queryForObject(
                "SELECT classification_id FROM classifications LIMIT 1", String.class);
        String name = "同名跨空间-" + suffix;
        for (String spaceId : Arrays.asList(firstSpace, secondSpace)) {
            testSpaces.add(spaceId);
            jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)",
                    spaceId, "空间-" + suffix);
            jdbc.update("""
                    INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                    VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                    """, fixtureId("T-NAME-" + spaceId), name, spaceId, classification);
        }
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE name = ? AND working_set_id = '1'",
                Integer.class, name));
    }

    private String createSpace(String workingSet) {
        org.springframework.http.ResponseEntity<sk.workingset.adapters.WorkingSetController.ProductSpaceView> response = rest.postForEntity("/api/worksets/" + workingSet + "/product-spaces",
                Map.of("name", "新空间-" + suffix),
                sk.workingset.adapters.WorkingSetController.ProductSpaceView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().productSpaceId();
        testSpaces.add(id);
        return id;
    }
}
