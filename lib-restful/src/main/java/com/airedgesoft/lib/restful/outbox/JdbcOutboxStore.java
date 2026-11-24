package com.airedgesoft.lib.restful.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link OutboxStore} JDBC 实现（{@code JdbcTemplate}；DDL 见 {@code classpath:schema/outbox-{h2,postgresql}.sql}）。
 *
 * <p>
 * 表名 / 列名固定（{@code airedge_outbox}），与业务表同库同事务入箱；{@code append} 在宿主事务内执行（{@code JdbcTemplate} 参与当前 Spring
 * 事务），派发（{@link OutboxDispatcher}）在事务外轮询——至少一次投递，消费端按 {@code id} 幂等去重。
 */
public class JdbcOutboxStore implements OutboxStore {

    /** 表名（DDL 脚本同源）。 */
    public static final String TABLE = "airedge_outbox";
    private static final String SELECT_PENDING = "SELECT id, event_type, aggregate_type, aggregate_id, payload,"
            + " create_time_ms, published_time_ms FROM " + TABLE + " WHERE published_time_ms IS NULL";
    private static final String ORDER_AND_LIMIT = " ORDER BY create_time_ms ASC, id ASC LIMIT ?";
    private static final String ORDER_LAST = " ORDER BY create_time_ms DESC, id DESC LIMIT 1";
    private static final RowMapper<OutboxRecord> RECORD_MAPPER = (rs, rowNum) -> new OutboxRecord(rs.getString("id"),
            rs.getString("event_type"), rs.getString("aggregate_type"), rs.getString("aggregate_id"),
            rs.getString("payload"), rs.getLong("create_time_ms"), rs.getObject("published_time_ms", Long.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(OutboxRecord record) {
        jdbcTemplate.update("INSERT INTO " + TABLE
                + " (id, event_type, aggregate_type, aggregate_id, payload, create_time_ms) VALUES (?, ?, ?, ?, ?, ?)",
                record.id(), record.eventType(), record.aggregateType(), record.aggregateId(), record.payload(),
                record.createTimeMs());
    }

    /** 在扫描边界内利用时间和主键定位下一批。 */
    @Override
    public List<OutboxRecord> fetchUnpublished(int limit, OutboxRecord after, OutboxRecord through) {
        String sql = SELECT_PENDING;
        List<Object> bindings = new ArrayList<>();
        if (after != null) {
            sql += " AND (create_time_ms > ? OR (create_time_ms = ? AND id > ?))";
            bindings.addAll(List.of(after.createTimeMs(), after.createTimeMs(), after.id()));
        }
        if (through != null) {
            sql += " AND (create_time_ms < ? OR (create_time_ms = ? AND id <= ?))";
            bindings.addAll(List.of(through.createTimeMs(), through.createTimeMs(), through.id()));
        }
        bindings.add(limit);
        return jdbcTemplate.query(sql + ORDER_AND_LIMIT, RECORD_MAPPER, bindings.toArray());
    }

    /** 用与批次查询相同的排序键反向定位未发布队尾。 */
    @Override
    public Optional<OutboxRecord> findLastUnpublished() {
        return jdbcTemplate.query(SELECT_PENDING + ORDER_LAST, RECORD_MAPPER).stream().findFirst();
    }

    @Override
    public void markPublished(String id) {
        jdbcTemplate.update("UPDATE " + TABLE + " SET published_time_ms = ? WHERE id = ? AND published_time_ms IS NULL",
                System.currentTimeMillis(), id);
    }
}
