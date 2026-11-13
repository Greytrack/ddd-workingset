package bootstrap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import bootstrap.web.DeriveView;
import bootstrap.web.ReadView;
import bootstrap.web.RequirementView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import sk.workingset.application.CowFailureLog;
import alm.requirement.application.RequirementApplicationService;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CowAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private CowFailureLog failures;
    @Autowired private RequirementApplicationService requirements;
    @SpyBean private LightTrackerItemRepository items;

    private final List<String> targets = new ArrayList<>();
    private final List<String> worksets = new ArrayList<>();
    private String source;
    private String common;
    private final String tracker = "T-" + UUID.randomUUID();
    private final String space = "S-" + UUID.randomUUID();
    private final String classification = "CL-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        if (source != null) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", eventPattern());
            jdbc.update("DELETE FROM cow_failures WHERE source_local_id = ?", source);
            jdbc.update("DELETE FROM cow_materializations WHERE source_local_id = ?", source);
            jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", source);
        }
        for (String target : targets) {
            redis.delete("workingset:lti:" + target);
            jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", target);
            jdbc.update("DELETE FROM requirements WHERE local_id = ?", target);
        }
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", source);
        for (String ws : worksets.reversed()) {
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", ws);
        }
        jdbc.update("DELETE FROM trackers WHERE tracker_id = ?", tracker);
        jdbc.update("DELETE FROM product_spaces WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM classifications WHERE classification_id = ?", classification);
    }

    @Test
    void at03_and_at04_first_snapshot_survives_consecutive_source_edits() {
        createSource();
        Target first = derive();
        Target second = derive();

        change("B", 0);
        change("C", 1);

        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 2);
        assertLocalContent(first, "A");
        assertLocalContent(second, "A");
        assertEquals("C", rest.getForObject("/api/requirements/" + source, RequirementView.class).title());
        assertTrue(failures.findBySource(LocalId.of(source)).isEmpty());
        System.out.printf("AT-03/04 HTTP B→C source=%s target=%s; DB=%s; completed=%d%n", source, first.id(),
                jdbc.queryForMap("""
                        SELECT r.title, l.materialized, l.source_local_id, l.source_revision
                        FROM requirements r JOIN light_tracker_items l USING (local_id) WHERE r.local_id = ?
                        """, first.id()), completedEvents());
    }

    @Test
    void descendantsUseActualContentOwnerAndCowFansOutAcrossLevels() {
        createSource();
        Target child = derive();
        String childTracker = jdbc.queryForObject(
                "SELECT tracker_id FROM light_tracker_items WHERE local_id = ?", String.class, child.id());
        Target grandchild = derive(child.ws(), childTracker, child.id());

        assertEquals(childTracker, jdbc.queryForObject("""
                SELECT t.source_id FROM trackers t JOIN light_tracker_items l USING (tracker_id) WHERE l.local_id = ?
                """, String.class, grandchild.id()));
        assertEquals(source, read(grandchild).contentLocalId());

        change("B", 0);
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        assertLocalContent(child, "A");
        assertLocalContent(grandchild, "A");

        Target laterGrandchild = derive(child.ws(), childTracker, child.id());
        assertEquals(child.id(), read(laterGrandchild).contentLocalId());
        assertFalse(read(laterGrandchild).materialized());
    }

    @Test
    void invalid_snapshot_evidence_is_recorded_without_writing_or_switching_target() {
        createSource();
        Target invalid = derive();
        // 模拟早期数据的家族身份损坏，直接验证 COW 对持久化证据的防护。
        jdbc.update("UPDATE light_tracker_items SET common_id = ? WHERE local_id = ?",
                "INVALID-" + UUID.randomUUID(), invalid.id());
        redis.delete("workingset:lti:" + invalid.id());

        change("B", 0);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertEquals("MATERIALIZE", failures.findBySource(LocalId.of(source)).getFirst().stage()));
        assertFalse(read(invalid).materialized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM requirements WHERE local_id = ?",
                Integer.class, invalid.id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                + " AND completion_date IS NULL", Integer.class, eventPattern()));
    }

    @Test
    void at05_switch_only_preserves_edited_business_content_and_is_idempotent() {
        createSource();
        Target rejected = derive();
        Target successful = derive();
        failNextSwitch(rejected);

        change("B", 0);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertLocalContent(successful, "A");
            assertEquals("SWITCH", failures.findBySource(LocalId.of(source)).getFirst().stage());
        });
        ReadView stillSourceBacked = read(rejected);
        assertFalse(stillSourceBacked.materialized());
        assertEquals(source, stillSourceBacked.contentLocalId());
        assertEquals("B", stillSourceBacked.fields().get("title"));
        assertEquals("A", jdbc.queryForObject("SELECT title FROM requirements WHERE local_id = ?",
                String.class, rejected.id()));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
                "SELECT materialized FROM light_tracker_items WHERE local_id = ?", Boolean.class, rejected.id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                + " AND completion_date IS NULL", Integer.class, eventPattern()));
        var failure = failures.findBySource(LocalId.of(source)).getFirst();
        assertEquals(LockVersion.of(0), failure.beforeLockVersion());
        assertEquals(LockVersion.of(1), failure.afterLockVersion());
        assertEquals("FAILED", receiptStatus(rejected));
        jdbc.update("UPDATE requirements SET title = ?, lock_version = 1 WHERE local_id = ?",
                "edited locally", rejected.id());
        String recovery = "/api/cow/recover/switch/" + failure.eventId() + "/" + rejected.id();
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(recovery, null, Void.class).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(recovery, null, Void.class).getStatusCode());
        assertLocalContent(rejected, "edited locally");
        assertEquals("SWITCHED", receiptStatus(rejected));
        assertNotNull(failures.findBySource(LocalId.of(source)).getFirst().resolvedAt());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM requirements WHERE local_id = ?",
                Integer.class, rejected.id()));
        assertEquals(HttpStatus.ACCEPTED, rest.postForEntity("/api/cow/recover/incomplete",
                null, Void.class).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        assertLocalContent(rejected, "edited locally");
        assertEquals(1, attempts(rejected));
        assertEquals(1, attempts(successful));
        System.out.printf("AT-05 target=%s FAILED/SWITCH and incomplete → SWITCHED, edited locally, one target row, completed=%d%n",
                rejected.id(), completedEvents());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void republication_retries_only_unfinished_target(boolean restart) {
        createSource();
        Target failed = derive();
        Target successful = derive();
        failNextSwitch(failed);
        change("B", 0);
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> {
            assertEquals("FAILED", receiptStatus(failed));
            assertEquals("SWITCHED", receiptStatus(successful));
            assertFalse(failures.findBySource(LocalId.of(source)).isEmpty());
        });
        if (restart) {
            try (var restarted = new SpringApplicationBuilder(WorkingsetApplication.class)
                    .web(WebApplicationType.NONE).run()) {
                await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
            }
        } else {
            assertEquals(HttpStatus.ACCEPTED, rest.postForEntity("/api/cow/recover/incomplete",
                    null, Void.class).getStatusCode());
            await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        }
        assertEquals("SWITCHED", receiptStatus(failed));
        assertEquals(2, attempts(failed));
        assertEquals(1, attempts(successful));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM requirements WHERE local_id = ?",
                Integer.class, failed.id()));
        assertLocalContent(failed, "A");
    }

    @Test
    void revision_upgrade_materializes_from_before_revision_and_preserves_target_revision() {
        createSource();
        Target target = derive();
        jdbc.update("UPDATE light_tracker_items SET revision = 1 WHERE local_id = ?", target.id());
        var upgraded = requirements.upgradeRevision(LocalId.of(source), LockVersion.of(0));
        assertEquals(Revision.of(4), upgraded.revision());
        assertEquals(LockVersion.of(1), upgraded.lockVersion());
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        assertLocalContent(target, "A");
        assertEquals(1, read(target).contentRevision());
        assertEquals(3, jdbc.queryForObject("SELECT revision FROM requirement_snapshots WHERE local_id = ?",
                Integer.class, source));
    }

    private void failNextSwitch(Target target) {
        var first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            if (first.getAndSet(false)) {
                throw new IllegalStateException("injected failure after LTI write");
            }
            return null;
        }).when(items).save(argThat(item -> item != null && item.materialized()
                && item.localId().value().equals(target.id())));
    }

    private String receiptStatus(Target target) {
        return jdbc.queryForList("SELECT status FROM cow_materializations WHERE target_local_id = ?",
                String.class, target.id()).stream().findFirst().orElse("");
    }

    private int attempts(Target target) {
        return jdbc.queryForObject("SELECT attempts FROM cow_materializations WHERE target_local_id = ?",
                Integer.class, target.id());
    }

    private void createSource() {
        jdbc.update("INSERT INTO product_spaces VALUES (?)", space);
        jdbc.update("INSERT INTO classifications VALUES (?, 'UNSHARED')", classification);
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, tracker, space, classification);
        var result = rest.postForEntity("/api/requirements", Map.of("trackerId", tracker,
                "title", "A", "description", "COW", "revision", 3), RequirementView.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        source = result.getBody().localId();
        common = result.getBody().commonId();
    }

    private Target derive() {
        return derive("1", tracker, source);
    }

    private Target derive(String parent, String sourceTracker, String sourceLocalId) {
        String ws = "WS-" + UUID.randomUUID();
        assertEquals(HttpStatus.CREATED,
                rest.postForEntity("/api/worksets/" + parent + "/children",
                        Map.of("workingSetId", ws), Void.class).getStatusCode());
        worksets.add(ws);
        assertEquals(HttpStatus.CREATED,
                rest.postForEntity("/api/worksets/" + ws + "/spaces/" + space, null, Void.class).getStatusCode());
        var result = rest.postForEntity("/api/worksets/" + ws + "/derive", Map.of(
                "sourceLocalId", sourceLocalId, "sourceTrackerId", sourceTracker, "sourceRevision", 3, "commonId", common),
                DeriveView.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        String id = result.getBody().item().localId();
        targets.add(id);
        return new Target(ws, id);
    }

    private void change(String title, int expectedLock) {
        var result = rest.exchange("/api/requirements/" + source + "/title", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", title, "expectedLockVersion", expectedLock)), RequirementView.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(title, result.getBody().title());
        assertEquals(3, result.getBody().revision());
        assertEquals(expectedLock + 1L, result.getBody().lockVersion());
    }

    private int completedEvents() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                + " AND completion_date IS NOT NULL", Integer.class, eventPattern());
    }

    private String eventPattern() {
        return "%" + source + "%";
    }

    private ReadView read(Target target) {
        return rest.getForObject("/api/worksets/" + target.ws() + "/items/" + target.id() + "/content",
                ReadView.class);
    }

    private void assertLocalContent(Target target, String title) {
        ReadView content = read(target);
        assertTrue(content.materialized());
        assertEquals(target.id(), content.contentLocalId());
        assertEquals(title, content.fields().get("title"));
    }

    private record Target(String ws, String id) {}
}
