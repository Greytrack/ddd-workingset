package bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.TrackerItemLifecycleService;
import sk.workingset.application.query.provider.LightTrackerItemQueryProvider;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.event.BusinessObjectDeleteEvent;
import sk.workingset.domain.event.TrackerItemCreatedEvent;
import sk.workingset.domain.lighttrackeritem.ItemClassification;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import sk.workingset.domain.workingset.WorkingSet;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static bootstrap.FixtureIds.fixtureId;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

/**
 * 验收测试：真实启动内嵌 Tomcat + PostgreSQL + Redis，通过 HTTP 创建需求、修改标题，
 * 并验证 Spring Modulith Event Publication Registry 已将事件写入 {@code event_publication}。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequirementAcceptanceTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private TransactionTemplate transaction;
    @SpyBean
    private LightTrackerItemRepository items;
    @Autowired
    private LightTrackerItemQueryProvider indexQueries;
    @Autowired
    private TrackerItemLifecycleService indexLifecycle;

    private RootTrackerFixture fixture;
    private String localId;

    @AfterEach
    void cleanUp() {
        if (localId != null) {
            fixture.removeRequirement(localId);
        }
        if (fixture != null) {
            fixture.remove();
        }
    }

    @Test
    void creationCommitsBeforeIndexingAndLateReplayDoesNotRestorePurgedItem() throws InterruptedException {
        String trackerId = fixtureId("T-" + UUID.randomUUID());
        fixture = new RootTrackerFixture(jdbc, trackerId);
        fixture.provision();
        CountDownLatch indexing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            indexing.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Indexing gate timed out");
            return call.callRealMethod();
        }).when(items).save(argThat(item -> item != null && item.trackerId().equals(TrackerId.of(trackerId))));

        try {
            ResponseEntity<Map> created = rest.postForEntity("/api/requirements", Map.of("trackerId", trackerId,
                    "title", "A", "description", "async index", "revision", 3), Map.class);
            assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
            localId = String.valueOf(created.getBody().get("localId"));
            assertTrue(indexing.await(5, TimeUnit.SECONDS));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM air_inst_common
                    WHERE id = ? AND common_id = id AND workingset_id = 1 AND tracker = ?
                      AND base_class = 1401 AND class = 140101 AND type = 14010101
                      AND revision = 3 AND max_revision = 3 AND delete_flag = 0
                    """, Integer.class, Long.valueOf(localId), Long.valueOf(trackerId)));
            assertEquals(HttpStatus.OK, rest.getForEntity("/api/requirements/" + localId, Map.class).getStatusCode());
            assertEquals(0, indexedRows());
        } finally {
            release.countDown();
        }

        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications("TrackerItemCreatedEvent") == 1);
        assertEquals(1, indexedRows());
        assertEquals(List.of(3), indexedRevisions());
        TrackerItemCreatedEvent event = new TrackerItemCreatedEvent(LocalId.of(localId), CommonId.of(localId), TrackerId.of(trackerId),
                WorkingSet.DEFAULT_ID, Revision.of(3));
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications("TrackerItemCreatedEvent") == 2);
        assertEquals(1, indexedRows());

        assertEquals(HttpStatus.NO_CONTENT, rest.exchange("/api/requirements/" + localId,
                HttpMethod.DELETE, null, Void.class).getStatusCode());
        assertEquals(1, jdbc.queryForObject("SELECT delete_flag FROM air_inst_common WHERE id = ?",
                Integer.class, Long.valueOf(localId)));
        assertEquals(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + localId + "/delete2", null, Void.class).getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM air_inst_common WHERE id = ?",
                Integer.class, Long.valueOf(localId)));
        await().atMost(Duration.ofSeconds(8)).until(() -> indexedRows() == 0);
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications("TrackerItemCreatedEvent") == 3);
        assertEquals(0, indexedRows());
        assertTrue(indexedRevisions().isEmpty());
    }

    @Test
    void legacyIndexTracksSoftDeleteAndRestoreWithoutReplayingStaleState() {
        TrackerId trackerId = TrackerId.of(fixtureId("T-" + UUID.randomUUID()));
        fixture = new RootTrackerFixture(jdbc, trackerId.value());
        fixture.provision();
        ResponseEntity<Map> created = rest.postForEntity("/api/requirements", Map.of("trackerId", trackerId.value(),
                "title", "Index lifecycle", "description", "", "revision", 3), Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
        localId = String.valueOf(created.getBody().get("localId"));
        String path = "/api/requirements/" + localId;
        LocalId id = LocalId.of(localId);
        await().atMost(Duration.ofSeconds(8)).until(() -> indexedRows() == 1);
        assertEquals(0, indexFlags());

        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/delete", null, Void.class).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications(BusinessObjectDeleteEvent.class.getSimpleName()) == 1);
        assertEquals(1, indexFlags());
        assertTrue(items.findById(id).orElseThrow().deleted());
        assertTrue(indexQueries.findInTracker(trackerId).isEmpty());
        assertTrue(indexQueries.pageInTracker(trackerId, "", 10).isEmpty());
        assertTrue(indexQueries.hasFamily(WorkingSet.DEFAULT_ID, CommonId.of(localId)));
        assertEquals(List.of(3), indexedRevisions());

        assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(path + "/undelete", null, Void.class).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications(BusinessObjectDeleteEvent.class.getSimpleName()) == 2);
        assertEquals(0, indexFlags());
        BusinessObjectDeleteEvent staleDelete = new BusinessObjectDeleteEvent(id, true);
        transaction.executeWithoutResult(status -> events.publishEvent(staleDelete));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications(BusinessObjectDeleteEvent.class.getSimpleName()) == 3);
        assertFalse(items.findById(id).orElseThrow().deleted());
        assertEquals(0, indexFlags());
        assertEquals(List.of(id), indexQueries.findInTracker(trackerId).stream()
                .map(item -> item.localId()).toList());
        assertEquals(List.of(3), indexedRevisions());
    }

    private Integer indexFlags() {
        return jdbc.queryForObject("SELECT flags FROM air_ws_items_index WHERE local_id = ?",
                Integer.class, Long.valueOf(localId));
    }

    @Test
    @Transactional
    void committedUpgradeUpdatesBothLegacyIndexesAndPreservesPreviousRevision() {
        String trackerId = fixtureId("revision-tracker-" + UUID.randomUUID());
        new RootTrackerFixture(jdbc, trackerId).provision();
        LocalId id = LocalId.of(fixtureId("revision-item-" + UUID.randomUUID()));
        items.save(LightTrackerItem.local(id, CommonId.of(id.value()),
                TrackerId.of(trackerId), WorkingSet.DEFAULT_ID, Revision.of(3),
                new ItemClassification(Long.parseLong(fixtureId(trackerId + "-s")), 14010101L)));

        indexLifecycle.applyCommittedUpgrade(id, Revision.of(3), Revision.of(4));
        indexLifecycle.applyCommittedUpgrade(id, Revision.of(3), Revision.of(4));

        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_ws_items_index WHERE local_id = ? AND revision = 4
                    AND source_local_id = local_id AND source_revision = 4 AND has_copied = 1
                """, Integer.class, id.numericValue()));
        assertEquals(List.of(3, 4), jdbc.queryForList("""
                SELECT revision FROM air_ws_items_revision_index WHERE local_id = ? ORDER BY revision
                """, Integer.class, id.numericValue()));
    }

    private List<Integer> indexedRevisions() {
        return jdbc.queryForList("SELECT revision FROM air_ws_items_revision_index WHERE local_id = ? ORDER BY revision",
                Integer.class, Long.valueOf(localId));
    }

    private int indexedRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM air_ws_items_index WHERE local_id = ?",
                Integer.class, localId);
    }

    private int completedPublications(String type) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM event_publication
                WHERE event_type LIKE ? AND serialized_event LIKE ? AND completion_date IS NOT NULL
                """, Integer.class, "%." + type, "%" + localId + "%");
    }

    @Test
    void create_then_modify_emits_event_persisted_in_event_publication() {
        String trackerId = fixtureId("T-" + UUID.randomUUID());
        fixture = new RootTrackerFixture(jdbc, trackerId);
        fixture.provision();
        Map<String, Object> createBody = Map.of(
                "trackerId", trackerId,
                "title", "A",
                "description", "acceptance",
                "revision", 3);

        ResponseEntity<Map> created = rest.postForEntity("/api/requirements", createBody, Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
        localId = String.valueOf(created.getBody().get("localId"));
        assertEquals(localId, created.getBody().get("commonId"),
                "默认工作集创建时 CommonId 与 LocalId 应相等");

        Map<String, Object> changeBody = Map.of(
                "title", "B",
                "expectedLockVersion", 0);
        ResponseEntity<Map> changed = rest.exchange(
                "/api/requirements/" + localId + "/title",
                HttpMethod.PUT,
                new HttpEntity<>(changeBody),
                Map.class);

        assertEquals(HttpStatus.OK, changed.getStatusCode(), String.valueOf(changed.getBody()));
        assertEquals("B", changed.getBody().get("title"));
        assertEquals(3, ((Number) changed.getBody().get("revision")).intValue());
        assertEquals(1, ((Number) changed.getBody().get("lockVersion")).intValue());

        // @ApplicationModuleListener 默认异步执行；等待 Modulith 完成状态更新，避免竞态
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            try {
                Boolean completed = jdbc.queryForObject(
                        "SELECT completion_date IS NOT NULL FROM event_publication "
                                + "WHERE serialized_event LIKE ? AND event_type LIKE '%.BusinessObjectUpdateEvent' LIMIT 1",
                        Boolean.class,
                        "%" + localId + "%");
                return Boolean.TRUE.equals(completed);
            } catch (RuntimeException notYetAvailable) {
                return false;
            }
        });

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT listener_id, event_type, publication_date, completion_date, serialized_event "
                        + "FROM event_publication WHERE serialized_event LIKE ? AND event_type LIKE '%.BusinessObjectUpdateEvent' "
                        + "AND listener_id LIKE '%RequirementChangedListener%' "
                        + "ORDER BY publication_date DESC LIMIT 1",
                "%" + localId + "%");

        assertNotNull(event.get("listener_id"), "Modulith 必须记录监听器身份");
        assertTrue(String.valueOf(event.get("listener_id")).contains("RequirementChangedListener"));
        assertTrue(String.valueOf(event.get("event_type")).contains("BusinessObjectUpdateEvent"));
        assertNotNull(event.get("publication_date"), "事件记录必须有发布时间");
        assertNotNull(event.get("completion_date"), "监听器成功后应标记完成");
        assertTrue(String.valueOf(event.get("serialized_event")).contains(localId),
                "事件载荷必须包含需求身份");
    }
}
