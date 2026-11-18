package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

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

import sk.workingset.adapters.ReadView;
import sk.workingset.adapters.LightTrackerItemView;
import alm.requirement.adapters.RequirementView;
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
import alm.requirement.application.command.RequirementCommandService;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CowAcceptanceTest {
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private CowFailureLog failures;
    @Autowired private RequirementCommandService requirements;
    @SpyBean private LightTrackerItemRepository items;

    private final List<String> targets = new ArrayList<>();
    private final List<String> worksets = new ArrayList<>();
    private String source;
    private String common;
    private final String tracker = fixtureId("T-" + UUID.randomUUID());
    private final String space = fixtureId("S-" + UUID.randomUUID());
    private final String classification = fixtureId("CL-" + UUID.randomUUID());

    @AfterEach
    void cleanUp() {
        if (source != null) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", eventPattern());
            jdbc.update("DELETE FROM cow_failures WHERE source_local_id = ?", source);
            jdbc.update("DELETE FROM cow_materializations WHERE source_local_id = ?", source);
            jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", source);
        }
        for (String target : targets) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + target + "%");
            jdbc.update("DELETE FROM requirement_snapshots WHERE local_id = ?", target);
            redis.delete("workingset:lti:" + target);
            jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", target);
            jdbc.update("DELETE FROM requirements WHERE local_id = ?", target);
        }
        jdbc.update("DELETE FROM requirements WHERE local_id = ?", source);
        for (String ws : worksets.reversed()) {
            jdbc.update("DELETE FROM light_tracker_items WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", ws);
        }
        jdbc.update("DELETE FROM light_tracker_items WHERE tracker_id = ?", tracker);
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void editingChildRefreshesItsListNameAndContent(boolean materializeBeforeEdit) {
        createSource();
        Target child = derive();
        if (materializeBeforeEdit) {
            change("Parent edit", 0);
            await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        }
        assertEquals(materializeBeforeEdit, read(child).materialized());

        org.springframework.http.ResponseEntity<RequirementView> changed = rest.exchange("/api/requirements/" + child.id(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Child edit", "description", "Child description",
                        "expectedLockVersion", 0)), RequirementView.class);

        assertEquals(HttpStatus.OK, changed.getStatusCode(), String.valueOf(changed.getBody()));
        LightTrackerItemView[] listed = rest.getForObject("/api/worksets/" + child.ws() + "/trackers/" + child.trackerId() + "/items",
                LightTrackerItemView[].class);
        assertEquals(1, listed.length);
        assertEquals(child.id(), listed[0].localId());
        assertEquals("Child edit", listed[0].name());
        ReadView content = read(child);
        assertTrue(content.materialized());
        assertEquals(child.id(), content.contentLocalId());
        assertEquals(Map.of("title", "Child edit", "description", "Child description"), content.fields());
        assertEquals(materializeBeforeEdit ? "Parent edit" : "A",
                rest.getForObject("/api/requirements/" + source, RequirementView.class).title());
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NOT NULL)",
                Boolean.class, "%" + child.id() + "%")));
    }

    @Test
    void editingOnlyTheTitleOfAnInheritedChildPreservesItsDescription() {
        createSource();
        Target child = derive();
        ReadView inherited = read(child);
        assertFalse(inherited.materialized());

        org.springframework.http.ResponseEntity<RequirementView> response = rest.exchange("/api/requirements/" + child.id() + "/title", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Child title", "expectedLockVersion", 0)), RequirementView.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ReadView edited = read(child);
        assertTrue(edited.materialized());
        assertEquals(child.id(), edited.contentLocalId());
        assertEquals("Child title", edited.fields().get("title"));
        assertEquals(inherited.fields().get("description"), edited.fields().get("description"));
        assertEquals("A", rest.getForObject("/api/requirements/" + source, RequirementView.class).title());
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NOT NULL)",
                Boolean.class, "%" + child.id() + "%")));
    }

    @Test
    void readsUnindexedSourceAndRebuildsIndexedChildWithoutSavingIt() {
        createSource();
        Target child = derive();
        jdbc.update("DELETE FROM light_tracker_items WHERE local_id = ?", source);

        RequirementView sourceView = rest.getForObject("/api/requirements/" + source, RequirementView.class);
        assertEquals(source, sourceView.localId());
        assertEquals("A", sourceView.title());

        RequirementView childView = rest.getForObject("/api/requirements/" + child.id(), RequirementView.class);
        assertEquals(child.id(), childView.localId());
        assertEquals(common, childView.commonId());
        assertEquals(child.trackerId(), childView.trackerId());
        assertEquals(3, childView.revision());
        assertEquals(0, childView.lockVersion());
        assertEquals("A", childView.title());
        assertEquals("COW", childView.description());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM requirements WHERE local_id = ?",
                Integer.class, child.id()));
        assertFalse(read(child).materialized());
    }

    @Test
    void firstEditRollsBackIfSwitchToLocalContentFails() {
        createSource();
        Target child = derive();
        failNextSwitch(child);

        org.springframework.http.ResponseEntity<String> response = rest.exchange("/api/requirements/" + child.id(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Child edit", "description", "Child description",
                        "expectedLockVersion", 0)), String.class);

        assertTrue(response.getStatusCode().is5xxServerError());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM requirements WHERE local_id = ?",
                Integer.class, child.id()));
        ReadView content = read(child);
        assertFalse(content.materialized());
        assertEquals(source, content.contentLocalId());
        assertEquals("A", content.fields().get("title"));
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
                fixtureId("INVALID-" + UUID.randomUUID()), invalid.id());
        redis.delete("workingset:lti:" + invalid.id());

        change("B", 0);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertEquals("MATERIALIZE", failures.findBySource(LocalId.of(source)).getFirst().stage()));
        assertFalse(read(invalid).materialized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM requirements WHERE local_id = ?",
                Integer.class, invalid.id()));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
    }

    @Test
    void revision_upgrade_materializes_from_before_revision_and_preserves_target_revision() {
        createSource();
        Target target = derive();
        jdbc.update("UPDATE light_tracker_items SET revision = 1 WHERE local_id = ?", target.id());
        alm.requirement.domain.Requirement upgraded = requirements.upgradeRevision(LocalId.of(source), LockVersion.of(0));
        assertEquals(Revision.of(4), upgraded.revision());
        assertEquals(LockVersion.of(1), upgraded.lockVersion());
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        assertLocalContent(target, "A");
        assertEquals(1, read(target).contentRevision());
        assertEquals(3, jdbc.queryForObject("SELECT revision FROM requirement_snapshots WHERE local_id = ?",
                Integer.class, source));
    }

    private void failNextSwitch(Target target) {
        AtomicBoolean first = new AtomicBoolean(true);
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
        org.springframework.http.ResponseEntity<RequirementView> result = rest.postForEntity("/api/requirements", Map.of("trackerId", tracker,
                "title", "A", "description", "COW", "revision", 3), RequirementView.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        source = result.getBody().localId();
        common = result.getBody().commonId();
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM light_tracker_items WHERE local_id = ?)", Boolean.class, source)));
    }

    private Target derive() {
        return derive("1", tracker, source);
    }

    private Target derive(String parent, String sourceTracker, String sourceLocalId) {
        String ws = fixtureId("WS-" + UUID.randomUUID());
        FixtureIds.insertWorkingSet(jdbc, parent, ws);
        worksets.add(ws);
        assertEquals(HttpStatus.CREATED,
                rest.postForEntity("/api/worksets/" + ws + "/spaces/" + space, null, Void.class).getStatusCode());
        DeriveFixture.Result result = DeriveFixture.derive(jdbc, ws, sourceTracker, sourceLocalId, Revision.of(3), CommonId.of(common));
        String id = result.item().localId().value();
        targets.add(id);
        return new Target(ws, id, result.trackerId());
    }

    private void change(String title, int expectedLock) {
        org.springframework.http.ResponseEntity<RequirementView> result = rest.exchange("/api/requirements/" + source + "/title", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", title, "expectedLockVersion", expectedLock)), RequirementView.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(title, result.getBody().title());
        assertEquals(3, result.getBody().revision());
        assertEquals(expectedLock + 1L, result.getBody().lockVersion());
    }

    private int completedEvents() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                + " AND event_type LIKE '%.RequirementChanged' AND completion_date IS NOT NULL",
                Integer.class, eventPattern());
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

    private record Target(String ws, String id, String trackerId) {}
}
