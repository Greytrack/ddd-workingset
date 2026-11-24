package bootstrap;


import com.airedgesoft.alm.requirement.adapters.RequirementView;
import com.airedgesoft.alm.requirement.application.command.RequirementCommandService;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.CowFailureLogQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CowAcceptanceTest {
    private final List<String> targets = new ArrayList<>();
    private final List<String> worksets = new ArrayList<>();
    private final String tracker = fixtureId("T-" + UUID.randomUUID());
    private final String space = fixtureId("S-" + UUID.randomUUID());
    private final String classification = "14010101";
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CowFailureLogQueryProvider failures;
    @Autowired
    private RequirementCommandService requirements;
    @SpyBean
    private LightTrackerItemRepository items;
    private String source;
    private String common;

    @AfterEach
    void cleanUp() {
        if (source != null) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", eventPattern());

        }
        for (String target : targets) {
            jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%" + target + "%");

            jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", target);
            jdbc.update("DELETE FROM air_inst_requirement_rev WHERE instance_id = ?", target);
        }
        jdbc.update("DELETE FROM air_inst_requirement_rev WHERE instance_id = ?", source);
        for (int index = worksets.size() - 1; index >= 0; index--) {
            String ws = worksets.get(index);
            jdbc.update("DELETE FROM air_ws_items_index WHERE workingset_id = ?", ws);
            jdbc.update("DELETE FROM tracker_bindings WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM trackers WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM space_bindings WHERE working_set_id = ?", ws);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", ws);
        }
        jdbc.update("DELETE FROM air_ws_items_index WHERE branch_id = ?", tracker);
        jdbc.update("DELETE FROM tracker_bindings WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM trackers WHERE product_space_id = ?", space);
        jdbc.update("DELETE FROM air_inst_product_space WHERE id = ?", space);
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
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%.BusinessObjectUpdateEvent'
                  AND serialized_event::jsonb ->> 'sourceLocalId' = ?
                  AND serialized_event::jsonb -> 'snapshot' ->> 'title' = 'A'
                """, Integer.class, source));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(DISTINCT serialized_event::jsonb -> 'snapshotRef' ->> 'id')
                FROM event_publication
                WHERE event_type LIKE '%.TrackerItemMaterializationRequestedEvent'
                  AND serialized_event::jsonb ->> 'sourceLocalId' = ?
                """, Integer.class, source));
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%.TrackerItemMaterializationRequestedEvent'
                  AND serialized_event::jsonb ->> 'sourceLocalId' = ?
                """, Integer.class, source));
        assertEquals("C", rest.getForObject("/api/requirements/" + source, RequirementView.class).title());
        assertTrue(failures.findBySource(Long.valueOf(source)).isEmpty());
        System.out.printf("AT-03/04 HTTP B→C source=%s target=%s; DB=%s; completed=%d%n", source, first.id(),
                jdbc.queryForMap("""
                        SELECT r.name AS title, l.has_copied = 1 AS materialized, l.source_local_id, l.source_revision
                        FROM air_inst_requirement_rev r JOIN air_ws_items_index l ON l.local_id = r.instance_id
                        WHERE r.instance_id = ? ORDER BY r.revision DESC LIMIT 1
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

        ResponseEntity<RequirementView> changed = rest.exchange("/api/requirements/" + child.id(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Child edit", "description", "Child description",
                        "expectedLockVersion", 0)), RequirementView.class);

        assertEquals(HttpStatus.OK, changed.getStatusCode(), String.valueOf(changed.getBody()));
        await().atMost(Duration.ofSeconds(8)).until(() -> read(child).materialized());
        RequirementView[] requirements = rest.getForObject("/api/requirements?trackerId=" + child.trackerId()
                + "&workingSetId=" + child.ws(), RequirementView[].class);
        assertTrue(Arrays.stream(requirements)
                .anyMatch(requirement -> child.id().equals(requirement.localId())
                        && "Child edit".equals(requirement.title())));
        ReadContent content = read(child);
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
        ReadContent inherited = read(child);
        assertFalse(inherited.materialized());

        ResponseEntity<RequirementView> response = rest.exchange("/api/requirements/" + child.id() + "/title", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Child title", "expectedLockVersion", 0)), RequirementView.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        await().atMost(Duration.ofSeconds(8)).until(() -> read(child).materialized());
        ReadContent edited = read(child);
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
        jdbc.update("DELETE FROM air_ws_items_index WHERE local_id = ?", source);

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
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_inst_requirement_rev WHERE instance_id = ?",
                Integer.class, child.id()));
        assertFalse(read(child).materialized());
    }

    @Test
    void firstEditCommitsBusinessObjectBeforeAsynchronousSwitch() {
        createSource();
        Target child = derive();
        failNextSwitch(child);

        ResponseEntity<String> response = rest.exchange("/api/requirements/" + child.id(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", "Child edit", "description", "Child description",
                        "expectedLockVersion", 0)), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM air_inst_requirement_rev WHERE instance_id = ?",
                Integer.class, child.id()));
        ReadContent content = read(child);
        assertFalse(content.materialized());
        assertEquals(source, content.contentLocalId());
        assertEquals("Child edit", content.fields().get("title"));
        assertEquals("Child description", content.fields().get("description"));
    }

    @Test
    void descendantsUseActualContentOwnerAndCowFansOutAcrossLevels() {
        createSource();
        Target child = derive();
        String childTracker = jdbc.queryForObject(
                "SELECT branch_id FROM air_ws_items_index WHERE local_id = ?", String.class, child.id());
        Target grandchild = derive(child.ws(), childTracker, child.id());

        assertEquals(childTracker, jdbc.queryForObject("""
                SELECT t.source_id FROM trackers t JOIN air_ws_items_index l ON l.branch_id = t.tracker_id
                WHERE l.local_id = ?
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
        ItemIndexFixture.updateFamily(jdbc, invalid.id(), fixtureId("INVALID-" + UUID.randomUUID()));

        requirements.changeTitle(Long.valueOf(source), "B", 0L);

        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                assertEquals("MATERIALIZE", failures.findBySource(Long.valueOf(source)).get(0).stage()));
        assertFalse(read(invalid).materialized());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_inst_requirement_rev WHERE instance_id = ?",
                Integer.class, invalid.id()));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        jdbc.update("""
                INSERT INTO event_publication
                    (id, listener_id, event_type, serialized_event, publication_date, completion_date)
                SELECT gen_random_uuid(), listener_id, event_type, serialized_event, publication_date, now()
                FROM event_publication
                WHERE event_type LIKE '%.TrackerItemMaterializationCompletedEvent'
                  AND serialized_event::jsonb ->> 'targetLocalId' = ?
                  AND serialized_event::jsonb ->> 'outcome' = 'FAILED'
                """, invalid.id());
        assertEquals(1, failures.findBySource(Long.valueOf(source)).size());

        requirements.changeTitle(Long.valueOf(source), "C", 1L);
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 2);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM event_publication
                WHERE event_type LIKE '%.TrackerItemMaterializationRequestedEvent'
                  AND serialized_event::jsonb ->> 'targetLocalId' = ?
                """, Integer.class, invalid.id()));
    }

    @Test
    void revision_upgrade_materializes_from_before_revision_and_preserves_target_revision() {
        createSource();
        Target target = derive();
        ItemIndexFixture.updateRevision(jdbc, target.id(), 1);
        Requirement upgraded = requirements.upgradeRevision(Long.valueOf(source), Long.valueOf(0));
        assertEquals(4, upgraded.revision());
        assertEquals(Long.valueOf(1), upgraded.lockVersion());
        await().atMost(Duration.ofSeconds(8)).until(() -> completedEvents() == 1);
        assertLocalContent(target, "A");
        assertEquals(1, read(target).contentRevision());
        assertEquals(3, jdbc.queryForObject("SELECT (serialized_event::jsonb -> 'snapshot' ->> 'revision')::integer FROM event_publication WHERE event_type LIKE '%.BusinessObjectUpdateEvent' AND serialized_event::jsonb ->> 'sourceLocalId' = ?",
                Integer.class, source));
    }

    @Test
    void permanentDeletionWaitsForSoftDeleteMaterializationAndChildStaysEditable() throws InterruptedException {
        createSource();
        Target target = derive();
        ItemIndexFixture.updateRevision(jdbc, target.id(), 1);
        CountDownLatch reachedSwitch = new CountDownLatch(1);
        CountDownLatch releaseSwitch = new CountDownLatch(1);
        doAnswer(invocation -> {
            reachedSwitch.countDown();
            if (!releaseSwitch.await(8, TimeUnit.SECONDS)) {
                throw new IllegalStateException("materialization gate timed out");
            }
            return invocation.callRealMethod();
        }).when(items).save(argThat(item -> item != null && item.materialized()
                && Long.toString(item.localId()).equals(target.id())));

        String path = "/api/requirements/" + source;
        assertEquals(HttpStatus.CONFLICT, rest.postForEntity(path + "/delete2", null, Map.class).getStatusCode());
        try {
            assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete", null, Void.class).getStatusCode());
            assertTrue(reachedSwitch.await(5, TimeUnit.SECONDS));
            assertEquals(HttpStatus.CONFLICT, rest.postForEntity(path + "/delete2", null, Map.class).getStatusCode());
            assertEquals(HttpStatus.GONE, rest.getForEntity(path, Map.class).getStatusCode());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE event_type LIKE '%.BusinessObjectUpdateEvent' AND serialized_event::jsonb ->> 'sourceLocalId' = ? AND serialized_event::jsonb -> 'snapshot' IS NOT NULL",
                    Integer.class, source));
        } finally {
            releaseSwitch.countDown();
        }

        await().atMost(Duration.ofSeconds(8)).until(() -> read(target).materialized());
        assertLocalContent(target, "A");
        assertEquals(1, read(target).contentRevision());
        assertEquals(0, jdbc.queryForObject("SELECT flags FROM air_ws_items_index WHERE local_id = ?",
                Integer.class, Long.valueOf(target.id())));
        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete2", null, Void.class).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, rest.getForEntity(path, Map.class).getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE event_type LIKE '%.BusinessObjectUpdateEvent' AND serialized_event::jsonb ->> 'sourceLocalId' = ? AND serialized_event::jsonb -> 'snapshot' IS NOT NULL",
                Integer.class, source));
        Requirement edited = requirements.changeTitle(Long.valueOf(target.id()), "editable after purge", Long.valueOf(0));
        assertEquals("editable after purge", edited.title());
        assertEquals(1, edited.revision());
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
                && Long.toString(item.localId()).equals(target.id())));
    }

    private void createSource() {
        jdbc.update("INSERT INTO air_inst_product_space (id, lock_version, delete_flag, created_by, created_time, updated_by, updated_time) VALUES (?, 0, 0, 'test', 0, 'test', 0)", space);
        FixtureIds.insertClassification(jdbc, classification, "UNSHARED");
        mountSpaceWithLocalFolder("1");
        jdbc.update("""
                INSERT INTO trackers (tracker_id, working_set_id, product_space_id, classification_id, sharing_mode)
                VALUES (?, '1', ?, ?, 'UNSHARED')
                """, tracker, space, classification);
        ResponseEntity<RequirementView> result = rest.postForEntity("/api/requirements", Map.of("trackerId", tracker,
                "title", "A", "description", "COW", "revision", 3), RequirementView.class);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        source = result.getBody().localId();
        common = result.getBody().commonId();
        await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM air_ws_items_index WHERE local_id = ?)", Boolean.class, source)));
    }

    // COW starts with a valid mounted space; mounting itself is a separate use case.
    private void mountSpaceWithLocalFolder(String workingSetId) {
        String folderId = FixtureIds.ensureDefaultTracker(jdbc, workingSetId, space);
        jdbc.update("""
                INSERT INTO tracker_bindings (working_set_id, tracker_id, product_space_id)
                VALUES (?, ?, ?)
                """, workingSetId, folderId, space);
    }

    private Target derive() {
        return derive("1", tracker, source);
    }

    private Target derive(String parent, String sourceTracker, String sourceLocalId) {
        String ws = fixtureId("WS-" + UUID.randomUUID());
        FixtureIds.insertWorkingSet(jdbc, parent, ws);
        worksets.add(ws);
        mountSpaceWithLocalFolder(ws);
        DeriveFixture.Result result = DeriveFixture.derive(jdbc, ws, sourceTracker, sourceLocalId, 3, Long.valueOf(common));
        String id = Long.toString(result.item().localId());
        targets.add(id);
        return new Target(ws, id, result.trackerId());
    }

    private void change(String title, int expectedLock) {
        ResponseEntity<RequirementView> result = rest.exchange("/api/requirements/" + source + "/title", HttpMethod.PUT,
                new HttpEntity<>(Map.of("title", title, "expectedLockVersion", expectedLock)), RequirementView.class);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(title, result.getBody().title());
        assertEquals(3, result.getBody().revision());
        assertEquals(expectedLock + 1L, result.getBody().lockVersion());
    }

    private int completedEvents() {
        return jdbc.queryForObject("SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?"
                        + " AND event_type LIKE '%.BusinessObjectUpdateEvent' AND completion_date IS NOT NULL"
                        + " AND listener_id LIKE '%RequirementChangedListener%'",
                Integer.class, eventPattern());
    }

    private String eventPattern() {
        return "%" + source + "%";
    }

    private ReadContent read(Target target) {
        RequirementView requirement = rest.getForObject("/api/requirements/" + target.id(), RequirementView.class);
        LightTrackerItem item = items.findById(Long.valueOf(target.id())).orElseThrow();
        boolean materialized = item.materialized();
        return new ReadContent(
                Map.<String, Object>of("title", requirement.title(), "description", requirement.description()),
                materialized ? item.revision() : item.sourceRevision(),
                materialized ? target.id() : Long.toString(item.sourceLocalId()),
                materialized);
    }

    private void assertLocalContent(Target target, String title) {
        ReadContent content = read(target);
        assertTrue(content.materialized());
        assertEquals(target.id(), content.contentLocalId());
        assertEquals(title, content.fields().get("title"));
    }

    private record ReadContent(Map<String, Object> fields, int contentRevision, String contentLocalId,
                               boolean materialized) {
    }

    private record Target(String ws, String id, String trackerId) {
    }
}
