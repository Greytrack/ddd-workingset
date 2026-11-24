package com.airedgesoft.lib.restful.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JdbcOutboxStore 真实 H2 往返单测（组件 10 存储侧；执行 {@code schema/outbox-h2.sql} 片段验证 DDL 与 SQL 同源）。
 */
class JdbcOutboxStoreTest {

    @Test
    void appendFetchMark_roundTripOnH2() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:outbox;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ScriptUtils.executeSqlScript(dataSource.getConnection(), new ClassPathResource("/schema/outbox-h2.sql"));
        JdbcOutboxStore store = new JdbcOutboxStore(jdbcTemplate);

        long createdAt = 1760000000000L;
        store.append(new OutboxRecord("o-2", "demo.task.created", "demoTasks", "101", "{\"k\":1}", createdAt, null));
        store.append(new OutboxRecord("o-1", "demo.task.updated", "demoTasks", "101", "{\"k\":2}", createdAt, null));

        List<OutboxRecord> pending = store.fetchUnpublished(10);
        assertThat(pending).extracting(OutboxRecord::id).containsExactly("o-1", "o-2");
        assertThat(pending).allSatisfy(record -> assertThat(record.published()).isFalse());
        assertThat(store.fetchUnpublished(1, pending.get(0)))
                .extracting(OutboxRecord::id).containsExactly("o-2");
        assertThat(store.findLastUnpublished()).contains(pending.get(1));
        assertThat(store.fetchUnpublished(10, null, pending.get(0)))
                .extracting(OutboxRecord::id).containsExactly("o-1");

        store.markPublished("o-1");
        List<OutboxRecord> afterMark = store.fetchUnpublished(10);
        assertThat(afterMark).extracting(OutboxRecord::id).containsExactly("o-2");

        // 与业务写同事务语义（append 参与 Spring 事务）由宿主事务管理器保证；此处验证 SQL 形态可执行即可
        String tableDdl = new String(
                new ClassPathResource("/schema/outbox-postgresql.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertThat(tableDdl).contains("create_time_ms").contains("published_time_ms");
    }

    @Test
    void recordPayload_isVerbatimJsonString() {
        String payload = "{\"title\":\"齿轮箱\"}";
        OutboxRecord record = OutboxRecord.pending(newId(), "demo.task.created", "demoTasks", "101", payload);
        assertThat(record.payload()).isEqualTo(payload); // 载荷原样承载（契约由 BC 定义）
        assertThat(record.published()).isFalse();
        assertThat(record.asPublished().publishedTimeMs()).isNotNull();
    }

    private static String newId() {
        return "outbox-" + java.util.UUID.randomUUID();
    }
}
