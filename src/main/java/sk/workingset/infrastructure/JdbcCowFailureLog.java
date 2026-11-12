package sk.workingset.infrastructure;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import sk.workingset.application.CowFailureLog;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

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
                                          after_revision, stage, error_type, message, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, failure.eventId(), failure.targetLocalId().value(), failure.sourceLocalId().value(),
                failure.beforeRevision().value(), failure.afterRevision().value(), failure.stage(),
                failure.errorType(), failure.message(), Timestamp.from(failure.occurredAt())));
    }

    @Override
    public List<Failure> findBySource(LocalId sourceLocalId) {
        return jdbc.query("""
                SELECT * FROM cow_failures WHERE source_local_id = ? ORDER BY id
                """, (row, number) -> new Failure(row.getLong("event_id"),
                new LocalId(row.getString("target_local_id")), sourceLocalId,
                new Revision(row.getInt("before_revision")), new Revision(row.getInt("after_revision")),
                row.getString("stage"), row.getString("error_type"), row.getString("message"),
                row.getTimestamp("occurred_at").toInstant()), sourceLocalId.value());
    }
}
