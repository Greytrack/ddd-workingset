package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/** T6-F e 裁定：每个（工作集，空间，分类）一个默认 Tracker；同空间+分类允许出现多个 Tracker。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TrackerDefaultAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;

    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id LIKE ?", "WS-MULTI-" + suffix);
        jdbc.update("DELETE FROM trackers WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
        jdbc.update("DELETE FROM space_bindings WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
        jdbc.update("DELETE FROM working_sets WHERE working_set_id LIKE ?", "WS-MULTI-" + suffix);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id LIKE ?", "PS-" + suffix + "%");
    }

    @Test
    void creatingProductSpaceAlsoCreatesOneDefaultTrackerPerClassification() {
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
}
