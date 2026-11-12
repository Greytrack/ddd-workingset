package bootstrap;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

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

    @Test
    void create_then_modify_emits_event_persisted_in_event_publication() {
        String commonId = "C-" + UUID.randomUUID();
        String trackerId = "T-" + UUID.randomUUID();
        Map<String, Object> createBody = Map.of(
                "commonId", commonId,
                "trackerId", trackerId,
                "title", "A",
                "description", "acceptance",
                "revision", 3);

        ResponseEntity<Map> created = rest.postForEntity("/api/requirements", createBody, Map.class);
        assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));
        String localId = String.valueOf(created.getBody().get("localId"));

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
                                + "WHERE serialized_event LIKE ? LIMIT 1",
                        Boolean.class,
                        "%" + localId + "%");
                return Boolean.TRUE.equals(completed);
            } catch (RuntimeException notYetAvailable) {
                return false;
            }
        });

        Map<String, Object> event = jdbc.queryForMap(
                "SELECT listener_id, event_type, publication_date, completion_date, serialized_event "
                        + "FROM event_publication WHERE serialized_event LIKE ? "
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
