package bootstrap;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import sk.workingset.adapters.WorkingSetCreationController.CreationView;
import sk.workingset.domain.workingset.WorkingSetCreation.Status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public final class FixtureIds {
    private FixtureIds() {}

    public static String fixtureId(String label) {
        CRC32 digest = new CRC32();
        digest.update(label.getBytes(StandardCharsets.UTF_8));
        return Long.toString(((126_230_400_000L + digest.getValue()) << 11) | 1984);
    }

    static void insertWorkingSet(JdbcTemplate jdbc, String parent, String child) {
        jdbc.update("INSERT INTO working_sets (working_set_id, parent_working_set_id) VALUES (?, ?)",
                Long.valueOf(child), Long.valueOf(parent));
    }

    static void insertClassification(JdbcTemplate jdbc, String id, String sharingMode) {
        jdbc.update("""
                INSERT INTO classifications (classification_id, sharing_mode, name, base_class_id, class_id)
                VALUES (?, ?, '测试分类', 1401, 140101)
                ON CONFLICT (classification_id) DO NOTHING
                """, Long.valueOf(id), sharingMode);
    }

    static String ensureDefaultTracker(JdbcTemplate jdbc, String workingSet, String space) {
        List<Long> existing = jdbc.queryForList("""
                SELECT tracker_id FROM trackers
                WHERE working_set_id = ? AND product_space_id = ? AND classification_id = 10070101 AND is_default
                """, Long.class, Long.valueOf(workingSet), Long.valueOf(space));
        if (!existing.isEmpty()) {
            return existing.getFirst().toString();
        }
        String id = fixtureId("default-tracker-" + workingSet + "-" + space);
        Long parent = jdbc.queryForObject("SELECT parent_working_set_id FROM working_sets WHERE working_set_id = ?",
                Long.class, Long.valueOf(workingSet));
        String source = parent == null ? null : ensureDefaultTracker(jdbc, parent.toString(), space);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, source_id, working_set_id, product_space_id,
                                      name, classification_id, sharing_mode, is_default)
                VALUES (?, ?, ?, ?, '文件夹', 10070101, 'UNSHARED', TRUE)
                """, Long.valueOf(id), source == null ? null : Long.valueOf(source),
                Long.valueOf(workingSet), Long.valueOf(space));
        return id;
    }

    static void extendWithRootFolder(TestRestTemplate rest, JdbcTemplate jdbc, String target, String space) {
        Long folder = jdbc.queryForObject("""
                SELECT tracker_id FROM trackers
                WHERE working_set_id = 1 AND product_space_id = ? AND classification_id = 10070101
                """, Long.class, Long.valueOf(space));
        extendScope(rest, target, Status.Succeeded, folder.toString());
    }

    static CreationView extendScope(TestRestTemplate rest, String target, Status expected, String... trackerIds) {
        ResponseEntity<CreationView> response = rest.postForEntity("/api/worksets/" + target + "/extensions",
                extensionRequest(trackerIds), CreationView.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        String task = "/api/working-set-creations/" + response.getBody().creationId();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertEquals(expected, rest.getForObject(task, CreationView.class).status()));
        return rest.getForObject(task, CreationView.class);
    }

    static Map<String, Object> extensionRequest(String... trackerIds) {
        return Map.of("requestId", "fixture-" + UUID.randomUUID(), "trackerIds", List.of(trackerIds));
    }
}
