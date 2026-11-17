package bootstrap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import alm.requirement.adapters.RequirementView;
import sk.workingset.adapters.LightTrackerItemView;
import sk.workingset.adapters.WorkingSetController.ProductSpaceView;
import sk.workingset.adapters.WorkingSetController.TrackerView;
import sk.workingset.adapters.WorkingSetCreationController.CreationView;
import sk.workingset.domain.WorkingSetCreation.Status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SnowflakeStorageAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void storesNumericIdsAndPreservesOneReadOnlyNumberAcrossTheFamily() {
        var space = rest.postForEntity("/api/worksets/1/product-spaces",
                Map.of("name", "编号验收"), ProductSpaceView.class);
        assertEquals(HttpStatus.CREATED, space.getStatusCode());
        long spaceId = Long.parseLong(space.getBody().productSpaceId());
        Long targetId = null;
        try {
            var root = java.util.Arrays.stream(rest.getForObject("/api/worksets/1/trackers", TrackerView[].class))
                    .filter(tracker -> space.getBody().productSpaceId().equals(tracker.productSpaceId()))
                    .filter(tracker -> "UNSHARED".equals(tracker.sharingMode())).findFirst().orElseThrow();
            var created = rest.postForEntity("/api/requirements", Map.of("trackerId", root.trackerId(),
                    "workingSetId", "1", "title", "编号初稿", "description", "编号验收", "revision", 1),
                    RequirementView.class);
            assertEquals(HttpStatus.CREATED, created.getStatusCode());
            var requirement = created.getBody();
            long rootId = Long.parseLong(requirement.localId());
            assertEquals(requirement.localId(), requirement.commonId());
            assertTrue(requirement.number().startsWith("REQ-"));
            assertEquals(requirement.number(),
                    jdbc.queryForObject("SELECT number FROM requirements WHERE local_id = ?", String.class, rootId));

            var request = Map.of("parentWorkingSetId", "1", "requestId", UUID.randomUUID().toString(),
                    "name", "编号子工作集", "productSpaceIds", List.of(Long.toString(spaceId)),
                    "unsharedParentTrackerIds", List.of(root.trackerId()));
            var accepted = rest.postForEntity("/api/working-set-creations", request, CreationView.class);
            assertEquals(HttpStatus.ACCEPTED, accepted.getStatusCode());
            var task = accepted.getBody();
            targetId = Long.valueOf(task.targetWorkingSetId());
            assertEquals(task.creationId(),
                    rest.postForEntity("/api/working-set-creations", request, CreationView.class).getBody().creationId());
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                    assertEquals(Status.Succeeded, rest.getForObject("/api/working-set-creations/"
                            + task.creationId(), CreationView.class).status()));

            var tracker = rest.getForObject("/api/worksets/" + targetId + "/trackers", TrackerView[].class)[0];
            var item = rest.getForObject("/api/worksets/" + targetId + "/trackers/" + tracker.trackerId() + "/items",
                    LightTrackerItemView[].class)[0];
            assertNotEquals(requirement.localId(), item.localId());
            assertEquals(requirement.commonId(), item.commonId());
            assertEquals(requirement.number(), item.number());
            var edited = rest.exchange("/api/requirements/" + item.localId() + "/title", HttpMethod.PUT,
                    new HttpEntity<>(Map.of("title", "编号修订", "expectedLockVersion", 0)), RequirementView.class);
            assertEquals(HttpStatus.OK, edited.getStatusCode());
            assertEquals(requirement.number(), edited.getBody().number());
            assertEquals("bigint", jdbc.queryForObject("SELECT pg_typeof(local_id)::text FROM requirements WHERE local_id = ?",
                    String.class, Long.valueOf(item.localId())));
        } finally {
            var trackerFilter = "SELECT tracker_id FROM trackers WHERE product_space_id = ?";
            jdbc.update("DELETE FROM light_tracker_items WHERE tracker_id IN (" + trackerFilter + ")", spaceId);
            jdbc.update("DELETE FROM requirement_snapshots WHERE tracker_id IN (" + trackerFilter + ")", spaceId);
            jdbc.update("DELETE FROM requirements WHERE tracker_id IN (" + trackerFilter + ")", spaceId);
            jdbc.update("DELETE FROM tracker_bindings WHERE product_space_id = ?", spaceId);
            jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", spaceId);
            jdbc.update("DELETE FROM space_bindings WHERE product_space_id = ?", spaceId);
            if (targetId != null) {
                jdbc.update("DELETE FROM working_set_creations WHERE new_working_set_id = ?", targetId);
                jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", targetId);
            }
            jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", spaceId);
        }
    }
}
