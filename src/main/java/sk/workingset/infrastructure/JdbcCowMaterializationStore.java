package sk.workingset.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import sk.workingset.application.CowMaterializationStore;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

/** COW 物化收据的 JDBC 实现：按事件与目标变体做幂等 upsert 与状态推进。 */
public final class JdbcCowMaterializationStore implements CowMaterializationStore {
    private static final String FIND = "SELECT * FROM cow_materializations WHERE event_id = ? AND target_local_id = ?";
    private static final RowMapper<Entry> ENTRY = (row, number) -> new Entry(row.getLong("event_id"),
            LocalId.of(row.getString("target_local_id")), LocalId.of(row.getString("source_local_id")),
            Revision.of(row.getInt("source_revision")), Status.valueOf(row.getString("status")),
            row.getInt("attempts"), row.getBoolean("business_saved"), row.getString("last_error"));

    private final JdbcTemplate jdbc;

    public JdbcCowMaterializationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Entry markPending(long eventId, LocalId target, LocalId source, Revision revision) {
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
                """, ENTRY, eventId, target.value(), source.value(), revision.value());
    }

    @Override
    public void markMaterialized(long eventId, LocalId target) {
        update("""
                UPDATE cow_materializations SET business_saved = true, status = 'MATERIALIZED',
                    last_error = NULL, updated_at = now()
                WHERE event_id = ? AND target_local_id = ? AND status <> 'SWITCHED'
                """, eventId, target);
    }

    @Override
    public void markSwitched(long eventId, LocalId target) {
        update("""
                UPDATE cow_materializations SET status = 'SWITCHED', last_error = NULL, updated_at = now()
                WHERE event_id = ? AND target_local_id = ? AND business_saved
                """, eventId, target);
    }

    @Override
    public void markFailed(long eventId, LocalId target, String error) {
        jdbc.update("""
                UPDATE cow_materializations SET status = 'FAILED', last_error = ?, updated_at = now()
                WHERE event_id = ? AND target_local_id = ? AND status <> 'SWITCHED'
                """, error, eventId, target.value());
    }

    @Override
    public Optional<Entry> findByEventAndTarget(long eventId, LocalId target) {
        return jdbc.query(FIND, ENTRY, eventId, target.value()).stream().findFirst();
    }

    @Override
    public Optional<Entry> lockByEventAndTarget(long eventId, LocalId target) {
        return jdbc.query(FIND + " FOR UPDATE", ENTRY, eventId, target.value()).stream().findFirst();
    }

    @Override
    public List<Entry> findByStatus(Status status) {
        return jdbc.query("SELECT * FROM cow_materializations WHERE status = ? ORDER BY event_id, target_local_id",
                ENTRY, status.name());
    }

    @Override
    public List<Entry> findBySource(LocalId source) {
        return jdbc.query("SELECT * FROM cow_materializations WHERE source_local_id = ? ORDER BY event_id, target_local_id",
                ENTRY, source.value());
    }

    private void update(String sql, long eventId, LocalId target) {
        if (jdbc.update(sql, eventId, target.value()) != 1) {
            throw new IllegalStateException("Unexpected COW receipt state for " + eventId + "/" + target.value());
        }
    }
}
