package sk.workingset.infrastructure;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.CowFailureLog;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;

/** COW 失败日志的 JDBC 实现；每条失败独立事务写入，避免随业务回滚丢失。 */
public final class JdbcCowFailureLog implements CowFailureLog {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransactionTemplate;

    public JdbcCowFailureLog(JdbcTemplate jdbc, TransactionTemplate newTransactionTemplate) {
        this.jdbc = jdbc;
        this.newTransactionTemplate = newTransactionTemplate;
    }

    @Override
    public void record(Failure failure) {
        newTransactionTemplate.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO cow_failures (event_id, target_local_id, source_local_id, before_revision,
                                          after_revision, stage, error_type, message, occurred_at,
                                          before_lock_version, after_lock_version, attempts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, failure.eventId(), failure.targetLocalId().value(), failure.sourceLocalId().value(),
                failure.beforeRevision().value(), failure.afterRevision().value(), failure.stage(),
                failure.errorType(), failure.message(), Timestamp.from(failure.occurredAt()),
                failure.beforeLockVersion().value(), failure.afterLockVersion().value(), failure.attempts()));
    }

    @Override
    public List<Failure> findBySource(LocalId sourceLocalId) {
        return jdbc.query("""
                SELECT * FROM cow_failures WHERE source_local_id = ? ORDER BY id
                """, (row, number) -> new Failure(row.getLong("event_id"),
                new LocalId(row.getString("target_local_id")), sourceLocalId,
                new Revision(row.getInt("before_revision")), new Revision(row.getInt("after_revision")),
                lockVersion(row.getObject("before_lock_version", Long.class)),
                lockVersion(row.getObject("after_lock_version", Long.class)),
                row.getInt("attempts"), row.getTimestamp("resolved_at") == null ? null
                        : row.getTimestamp("resolved_at").toInstant(),
                row.getString("stage"), row.getString("error_type"), row.getString("message"),
                row.getTimestamp("occurred_at").toInstant()), sourceLocalId.value());
    }

    @Override
    public void resolve(long eventId, LocalId target) {
        jdbc.update("""
                UPDATE cow_failures SET resolved_at = now()
                WHERE event_id = ? AND target_local_id = ? AND resolved_at IS NULL
                """, eventId, target.value());
    }

    private static LockVersion lockVersion(Long value) {
        return value == null ? null : LockVersion.of(value);
    }
}
