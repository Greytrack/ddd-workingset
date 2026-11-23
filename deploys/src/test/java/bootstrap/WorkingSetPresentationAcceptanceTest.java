package bootstrap;

import com.airedgesoft.sharedkernel.workingset.adapters.WorkingSetController.WorkingSetView;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static String path(String id) {
        return "/api/worksets/" + id;
    }

    @AfterEach
    void removeCreated() {
        for (String id : created.reversed()) {
            jdbc.update("DELETE FROM working_sets WHERE working_set_id = ?", id);
        }
    }

    @Test
    void editsDisplayNameAndColorWithoutChangingLineage() {
        String id = create("1", "原名称", "#2468AC");

        ResponseEntity<WorkingSetView> response = patch(id, Map.of("name", "新名称", "color", "#0d9488"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("新名称", response.getBody().name());
        assertEquals("#0D9488", response.getBody().color());
        assertEquals("1", response.getBody().parentWorkingSetId());

        WorkingSetView reloaded = rest.getForObject(path(id), WorkingSetView.class);
        assertEquals("新名称", reloaded.name());
        assertEquals("#0D9488", reloaded.color());
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

        ResponseEntity<WorkingSetView> colorOnly = patch(id, Map.of("color", "#E88434"));
        assertEquals(HttpStatus.OK, colorOnly.getStatusCode());
        assertEquals("原名称", colorOnly.getBody().name());
        assertEquals("#E88434", colorOnly.getBody().color());

        assertEquals(HttpStatus.BAD_REQUEST, patch(id, Map.of("name", "   ")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, patch(id, Map.of("color", "red")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, patch(id, Map.of()).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, patch("258520000000099", Map.of("name", "不存在")).getStatusCode());
    }

    private String create(String parent, String name, String color) {
        ResponseEntity<WorkingSetView> response = rest.postForEntity(path(parent) + "/children",
                Map.of("name", name, "color", color), WorkingSetView.class);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        String id = response.getBody().workingSetId();
        created.add(id);
        return id;
    }

    private ResponseEntity<WorkingSetView> patch(String id, Map<String, ?> body) {
        return restClient().patch().uri("/api/worksets/{id}", id).body(body)
                .exchange((request, response) ->
                        ResponseEntity.status(response.getStatusCode()).body(response.bodyTo(WorkingSetView.class)));
    }

    private RestClient restClient() {
        return RestClient.builder().baseUrl(rest.getRootUri())
                .requestFactory(new JdkClientHttpRequestFactory()).build();
    }
}
