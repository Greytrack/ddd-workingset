package com.airedgesoft.sharekernel.workingset.infrastructure.providers;

import com.airedgesoft.sharekernel.workingset.application.query.provider.CowFailureLogQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.LockVersion;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.List;

/**
 * COW 失败日志的 JDBC 实现；每条失败独立事务写入，避免随业务回滚丢失。
 */
@Component
@RequiredArgsConstructor
public class CowFailureLogQueryProviderImpl implements CowFailureLogQueryProvider {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransactionTemplate;
    private final IdentifierGenerator ids;

    private static LockVersion lockVersion(Long value) {
        return value == null ? null : LockVersion.of(value);
    }

    @Override
    public void record(Failure failure) {
        newTransactionTemplate.executeWithoutResult(status -> jdbc.update("""
                        INSERT INTO cow_failures (id, event_id, target_local_id, source_local_id, before_revision,
                                                  after_revision, stage, error_type, message, occurred_at,
                                                  before_lock_version, after_lock_version, attempts)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, ids.nextId(null).longValue(), failure.eventId(), failure.targetLocalId().numericValue(), failure.sourceLocalId().numericValue(),
                failure.beforeRevision().value(), failure.afterRevision().value(), failure.stage(),
                failure.errorType(), failure.message(), new Timestamp(failure.occurredAt()),
                failure.beforeLockVersion().value(), failure.afterLockVersion().value(), failure.attempts()));
    }

    @Override
    public List<Failure> findBySource(LocalId sourceLocalId) {
        return jdbc.query("""
                SELECT * FROM cow_failures WHERE source_local_id = ? ORDER BY id
                """, (row, number) -> new Failure(row.getLong("event_id"),
                LocalId.of(row.getLong("target_local_id")), sourceLocalId,
                new Revision(row.getInt("before_revision")), new Revision(row.getInt("after_revision")),
                lockVersion(row.getObject("before_lock_version", Long.class)),
                lockVersion(row.getObject("after_lock_version", Long.class)),
                row.getInt("attempts"),
                row.getString("stage"), row.getString("error_type"), row.getString("message"),
                row.getTimestamp("occurred_at").getTime()), sourceLocalId.numericValue());
    }
}
