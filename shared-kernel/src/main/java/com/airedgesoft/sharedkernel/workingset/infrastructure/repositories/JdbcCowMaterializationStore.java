package com.airedgesoft.sharedkernel.workingset.infrastructure.repositories;


import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.workingset.domain.cow.CowMaterializationStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * COW 物化收据的 JDBC 实现：按事件与目标变体做幂等 upsert 与状态推进。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcCowMaterializationStore implements CowMaterializationStore {
    private static final String FIND = "SELECT * FROM cow_materializations WHERE event_id = ? AND target_local_id = ?";
    private static final RowMapper<Entry> ENTRY = (row, number) -> new Entry(row.getLong("event_id"),
            row.getLong("target_local_id"), row.getLong("source_local_id"),
            row.getInt("source_revision"), Status.valueOf(row.getString("status")),
            row.getInt("attempts"), row.getBoolean("business_saved"), row.getString("last_error"));

    private final JdbcTemplate jdbc;


    @Override
    public Entry markPending(long eventId, Long target, Long source, Integer revision) {
        return jdbc.queryForObject("""
                INSERT INTO cow_materializations AS m
                    (event_id, target_local_id, source_local_id, source_revision, status, attempts)
                VALUES (?, ?, ?, ?, 'PENDING', 1)
                ON CONFLICT (event_id, target_local_id) DO UPDATE
                    SET status = CASE WHEN m.status = 'SWITCHED' THEN m.status
                                      WHEN m.business_saved THEN 'MATERIALIZED' ELSE 'PENDING' END,
                        attempts = m.attempts + CASE WHEN m.status = 'SWITCHED' THEN 0 ELSE 1 END,
                        updated_at = now()
                WHERE m.source_local_id = EXCLUDED.source_local_id AND m.source_revision = EXCLUDED.source_revision
                RETURNING *
                """, ENTRY, eventId, target, source, revision);
    }

    @Override
    public void markMaterialized(long eventId, Long target) {
        update("""
                UPDATE cow_materializations SET business_saved = true, status = 'MATERIALIZED',
                    last_error = NULL, updated_at = now()
                WHERE event_id = ? AND target_local_id = ? AND status <> 'SWITCHED'
                """, eventId, target);
    }

    @Override
    public void markSwitched(long eventId, Long target) {
        update("""
                UPDATE cow_materializations SET status = 'SWITCHED', last_error = NULL, updated_at = now()
                WHERE event_id = ? AND target_local_id = ? AND business_saved
                """, eventId, target);
    }

    @Override
    public void markFailed(long eventId, Long target, String error) {
        jdbc.update("""
                UPDATE cow_materializations SET status = 'FAILED', last_error = ?, updated_at = now()
                WHERE event_id = ? AND target_local_id = ? AND status <> 'SWITCHED'
                """, error, eventId, target);
    }

    @Override
    public Optional<Entry> findByEventAndTarget(long eventId, Long target) {
        return jdbc.query(FIND, ENTRY, eventId, target).stream().findFirst();
    }

    @Override
    public Optional<Entry> lockByEventAndTarget(long eventId, Long target) {
        return jdbc.query(FIND + " FOR UPDATE", ENTRY, eventId, target).stream().findFirst();
    }

    @Override
    public List<Entry> findByStatus(Status status) {
        return jdbc.query("SELECT * FROM cow_materializations WHERE status = ? ORDER BY event_id, target_local_id",
                ENTRY, status.name());
    }

    @Override
    public List<Entry> findBySource(Long source) {
        return jdbc.query("SELECT * FROM cow_materializations WHERE source_local_id = ? ORDER BY event_id, target_local_id",
                ENTRY, source);
    }

    private void update(String sql, long eventId, Long target) {
        if (jdbc.update(sql, eventId, target) != 1) {
            throw DomainException.withMessage(SharedKernelResultCode.PERSISTENCE_STATE_INVALID, "Unexpected COW receipt state for " + eventId + "/" + target);
        }
    }
}
