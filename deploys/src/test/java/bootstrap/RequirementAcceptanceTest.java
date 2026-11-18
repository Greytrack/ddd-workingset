package bootstrap;

import static bootstrap.FixtureIds.fixtureId;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.event.TrackerItemCreated;
import sk.workingset.domain.repository.LightTrackerItemRepository;

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

    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transaction;
    @SpyBean private LightTrackerItemRepository items;

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
        var indexing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            indexing.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Indexing gate timed out");
            return call.callRealMethod();
        }).when(items).save(argThat(item -> item != null && item.trackerId().equals(TrackerId.of(trackerId))));

        try {
            var created = rest.postForEntity("/api/requirements", Map.of("trackerId", trackerId,
                    "title", "A", "description", "async index", "revision", 3), Map.class);
            assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
            localId = String.valueOf(created.getBody().get("localId"));
            assertTrue(indexing.await(5, TimeUnit.SECONDS));
            assertEquals(HttpStatus.OK, rest.getForEntity("/api/requirements/" + localId, Map.class).getStatusCode());
            assertEquals(0, indexedRows());
        } finally {
            release.countDown();
        }

        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications("TrackerItemCreated") == 1);
        assertEquals(1, indexedRows());
        var event = new TrackerItemCreated(LocalId.of(localId), CommonId.of(localId), TrackerId.of(trackerId),
                WorkingSet.DEFAULT_ID, Revision.of(3));
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications("TrackerItemCreated") == 2);
        assertEquals(1, indexedRows());

        assertEquals(HttpStatus.NO_CONTENT,
                rest.postForEntity("/api/requirements/" + localId + "/delete2", null, Void.class).getStatusCode());
        await().atMost(Duration.ofSeconds(8)).until(() -> indexedRows() == 0);
        transaction.executeWithoutResult(status -> events.publishEvent(event));
        await().atMost(Duration.ofSeconds(8)).until(() -> completedPublications("TrackerItemCreated") == 3);
        assertEquals(0, indexedRows());
    }

    private int indexedRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM light_tracker_items WHERE local_id = ?",
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
                                + "WHERE serialized_event LIKE ? AND event_type LIKE '%.RequirementChanged' LIMIT 1",
                        Boolean.class,
                        "%" + localId + "%");
                return Boolean.TRUE.equals(completed);
            } catch (RuntimeException notYetAvailable) {
                return false;
            }
        });

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT listener_id, event_type, publication_date, completion_date, serialized_event "
                        + "FROM event_publication WHERE serialized_event LIKE ? AND event_type LIKE '%.RequirementChanged' "
                        + "ORDER BY publication_date DESC LIMIT 1",
                "%" + localId + "%");

        assertNotNull(event.get("listener_id"), "Modulith 必须记录监听器身份");
        assertTrue(String.valueOf(event.get("listener_id")).contains("RequirementChangedListener"));
        assertTrue(String.valueOf(event.get("event_type")).contains("RequirementChanged"));
        assertNotNull(event.get("publication_date"), "事件记录必须有发布时间");
        assertNotNull(event.get("completion_date"), "监听器成功后应标记完成");
        assertTrue(String.valueOf(event.get("serialized_event")).contains(localId),
                "事件载荷必须包含需求身份");
    }
}
