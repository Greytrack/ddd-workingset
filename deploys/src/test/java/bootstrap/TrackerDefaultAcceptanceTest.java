package bootstrap;

import sk.common.error.DomainException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import sk.workingset.adapters.LightTrackerItemView;
import alm.requirement.adapters.RequirementView;
import sk.workingset.adapters.WorkingSetController.BindingsView;
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
    private final String child = "WS-CHILD-" + suffix;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM light_tracker_items WHERE tracker_id IN (SELECT tracker_id FROM trackers WHERE product_space_id LIKE ?)",
                "PS-" + suffix + "%");
        jdbc.update("DELETE FROM requirements WHERE tracker_id IN (SELECT tracker_id FROM trackers WHERE product_space_id LIKE ?)",
                "PS-" + suffix + "%");
        jdbc.update("DELETE FROM tracker_bindings WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id IN (?, ?)", "WS-MULTI-" + suffix, child);
        jdbc.update("DELETE FROM trackers WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
        jdbc.update("DELETE FROM space_bindings WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
        jdbc.update("DELETE FROM space_bindings WHERE working_set_id IN (?, ?)", "WS-MULTI-" + suffix, child);
        jdbc.update("DELETE FROM working_sets WHERE working_set_id IN (?, ?)", "WS-MULTI-" + suffix, child);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
    }

    @Test
    void creatingProductSpacePublishesDefaultTrackerBindingsAndItemReadAccess() {
        String spaceId = "PS-" + suffix;
        var response = rest.postForEntity("/api/worksets/1/product-spaces",
                Map.of("productSpaceId", spaceId, "name", "新空间-" + suffix), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), () -> String.valueOf(response.getBody()));
        int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications", Integer.class);
        assertEquals(classifications, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE product_space_id = ? AND is_default", Integer.class, spaceId));
        assertEquals(classifications, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'",
                Integer.class, spaceId));
        var trackers = Arrays.stream(rest.getForObject("/api/worksets/1/trackers", TrackerView[].class))
                .filter(tracker -> spaceId.equals(tracker.productSpaceId())).toList();
        assertEquals(classifications, trackers.size());
        for (var tracker : trackers) {
            var items = rest.getForEntity("/api/worksets/1/trackers/" + tracker.trackerId() + "/items",
                    String.class);
            assertEquals(HttpStatus.OK, items.getStatusCode(), items::getBody);
            assertEquals("[]", items.getBody());
        }

        var bindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        var projectedSpace = bindings.spaces().stream()
                .filter(binding -> spaceId.equals(binding.productSpaceId())).findFirst().orElseThrow();
        assertEquals("INITIAL_PROJECTION", projectedSpace.origin());
        assertEquals(trackers.stream().map(TrackerView::trackerId).sorted().toList(),
                bindings.trackers().stream().filter(binding -> spaceId.equals(binding.productSpaceId()))
                        .map(binding -> binding.trackerId()).sorted().toList());

        String trackerId = trackers.getFirst().trackerId();
        var item = rest.postForEntity("/api/requirements", Map.of("trackerId", trackerId, "workingSetId", "1",
                "title", "验证默认 Tracker", "description", "验证绑定范围", "revision", 1), RequirementView.class);
        assertEquals(HttpStatus.CREATED, item.getStatusCode());
        var items = rest.getForObject("/api/worksets/1/trackers/" + trackerId + "/items", LightTrackerItemView[].class);
        assertTrue(Arrays.stream(items).anyMatch(indexed -> item.getBody().localId().equals(indexed.localId())));
    }

    @Test
    void defaultTrackerIsUniquePerWorkingSetSpaceAndClassification() {
        String spaceId = "PS-DEF-" + suffix;
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)", spaceId, "空间-" + suffix);
        String classification = jdbc.queryForObject("SELECT classification_id FROM classifications LIMIT 1", String.class);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id,
                    sharing_mode, is_default)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED', TRUE)
                """, "T-DEF1-" + suffix, "默认1-" + suffix, spaceId, classification);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id,
                    sharing_mode, is_default)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED', TRUE)
                """, "T-DEF2-" + suffix, "默认2-" + suffix, spaceId, classification));
    }

    @Test
    void trackerNameIsUniqueWithinSpaceAndClassification() {
        String spaceId = "PS-NAME-" + suffix;
        jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)", spaceId, "空间-" + suffix);
        String classification = jdbc.queryForObject("SELECT classification_id FROM classifications LIMIT 1", String.class);
        String name = "同名-" + suffix;
        jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                """, "T-N1-" + suffix, name, spaceId, classification);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                """, "T-N2-" + suffix, name, spaceId, classification));
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
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/worksets/1/children",
                Map.of("workingSetId", child), Map.class).getStatusCode());
        String spaceId = "PS-CHILD-" + suffix;
        var response = rest.postForEntity("/api/worksets/" + child + "/product-spaces",
                Map.of("productSpaceId", spaceId, "name", "子工作集入口-" + suffix), Map.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), () -> String.valueOf(response.getBody()));

        int classifications = jdbc.queryForObject("SELECT count(*) FROM classifications", Integer.class);
        assertEquals(classifications, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = '1'
                """, Integer.class, spaceId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM trackers WHERE product_space_id = ? AND working_set_id = ?
                """, Integer.class, spaceId, child));

        var defaultBindings = rest.getForObject("/api/worksets/1/bindings", BindingsView.class);
        assertTrue(defaultBindings.spaces().stream().anyMatch(binding -> spaceId.equals(binding.productSpaceId())),
                "default working set should own the projected space binding");
        var childBindings = rest.getForObject("/api/worksets/" + child + "/bindings", BindingsView.class);
        assertTrue(childBindings.spaces().stream().noneMatch(binding -> spaceId.equals(binding.productSpaceId())),
                "product space must not be projected into the child working set");
    }

    @Test
    void originalTrackerCannotBeCreatedInChildWorkingSet() {
        var space = new ProductSpace("PS-DOM-" + suffix, "domain");
        var classification = new Classification("CL-REQ", Classification.SharingMode.UNSHARED);
        assertThrows(DomainException.class, () -> new Tracker(
                TrackerId.of("T-CHILD-" + suffix), "子工作集原生 Tracker", null,
                WorkingSetId.of(child), space.id(), classification.id(), classification.sharingMode(), null, false));
    }

    @Test
    void trackerNameUniquenessDoesNotCrossProductSpaces() {
        String firstSpace = "PS-NAME1-" + suffix;
        String secondSpace = "PS-NAME2-" + suffix;
        String classification = jdbc.queryForObject(
                "SELECT classification_id FROM classifications LIMIT 1", String.class);
        String name = "同名跨空间-" + suffix;
        for (String spaceId : Arrays.asList(firstSpace, secondSpace)) {
            jdbc.update("INSERT INTO product_spaces (product_space_id, name) VALUES (?, ?)",
                    spaceId, "空间-" + suffix);
            jdbc.update("""
                    INSERT INTO trackers (tracker_id, name, working_set_id, product_space_id, classification_id, sharing_mode)
                    VALUES (?, ?, '1', ?, ?, 'UNSHARED')
                    """, "T-NAME-" + spaceId, name, spaceId, classification);
        }
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM trackers WHERE name = ? AND working_set_id = '1'",
                Integer.class, name));
    }
}
