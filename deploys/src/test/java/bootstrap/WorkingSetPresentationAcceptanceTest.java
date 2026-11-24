package bootstrap;

import com.airedgesoft.sharedkernel.workingset.application.command.WorkingSetCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 工作集名称与颜色是可编辑的展示属性，不改变谱系与绑定范围。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkingSetPresentationAcceptanceTest {

    private final List<String> created = new ArrayList<>();
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private WorkingSetCommandService commands;

    private static String path(String id) {
        return "/v1/workingsets/" + id;
    }

    @AfterEach
    void removeCreated() {
        for (int index = created.size() - 1; index >= 0; index--) {
            String id = created.get(index);
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id);
        }
    }

    @Test
    void readsTheDefaultWorksetScopeUsedByThePage() {
        ResponseEntity<List> children = rest.getForEntity(path("1") + "/children", List.class);
        ResponseEntity<Map> bindings = rest.getForEntity(path("1") + "/bindings", Map.class);
        ResponseEntity<List> trackers = rest.getForEntity(path("1") + "/trackers", List.class);

        assertEquals(HttpStatus.OK, children.getStatusCode());
        assertEquals(HttpStatus.OK, bindings.getStatusCode());
        assertEquals(HttpStatus.OK, trackers.getStatusCode());
        assertNotNull(children.getBody());
        assertNotNull(bindings.getBody().get("spaces"));
        assertNotNull(bindings.getBody().get("trackers"));
        assertNotNull(trackers.getBody());
    }

    @Test
    void editsDisplayNameAndColorWithoutChangingLineage() {
        String id = create("1", "原名称", "#2468AC");

        ResponseEntity<Map> response = patch(id, Map.of("displayName", "新名称", "color", "#0d9488"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("新名称", response.getBody().get("displayName"));
        assertEquals("#0D9488", response.getBody().get("color"));
        assertEquals("1", response.getBody().get("parentId"));

        Map<?, ?> reloaded = rest.getForObject(path(id), Map.class);
        assertEquals("新名称", reloaded.get("displayName"));
        assertEquals("#0D9488", reloaded.get("color"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_inst_workingset WHERE id = ? AND name = ? AND color = ?
                """, Integer.class, Long.valueOf(id), "新名称", "#0D9488"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM air_inst_common WHERE id = ? AND name = ?
                """, Integer.class, Long.valueOf(id), "新名称"));
    }

    @Test
    void keepsOmittedFieldAndRejectsInvalidEdits() {
        String id = create("1", "原名称", "#2468AC");

        ResponseEntity<Map> colorOnly = patch(id, Map.of("color", "#E88434"));
        assertEquals(HttpStatus.OK, colorOnly.getStatusCode());
        assertEquals("原名称", colorOnly.getBody().get("displayName"));
        assertEquals("#E88434", colorOnly.getBody().get("color"));

        assertEquals(HttpStatus.BAD_REQUEST, patch(id, Map.of("displayName", "   ")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, patch(id, Map.of("color", "red")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, patch(id, Map.of()).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, patch("258520000000099", Map.of("displayName", "不存在")).getStatusCode());
    }

    private String create(String parent, String name, String color) {
        String id = commands.createChildWorkingSet(Long.valueOf(parent), name, color).id().toString();
        created.add(id);
        return id;
    }

    private ResponseEntity<Map> patch(String id, Map<String, ?> body) {
        Map<String, Object> requestBody = new LinkedHashMap<>(body);
        ResponseEntity<Map> current = rest.getForEntity(path(id), Map.class);
        Object version = current.getStatusCode().is2xxSuccessful() ? current.getBody().get("lockedVersion") : 1L;
        assertNotNull(version);
        requestBody.put("lockedVersion", version);
        return restClient().patch().uri(path(id)).body(requestBody)
                .exchange((request, response) ->
                        ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(Map.class)));
    }

    private RestClient restClient() {
        return RestClient.builder().baseUrl(rest.getRootUri())
                .requestFactory(new JdkClientHttpRequestFactory()).build();
    }
}
