package sk.workingset.application;

import java.time.Instant;
import java.util.List;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;

public interface CowFailureLog {
    void record(Failure failure);
    List<Failure> findBySource(LocalId sourceLocalId);
    void resolve(long eventId, LocalId target);

    record Failure(long eventId, LocalId targetLocalId, LocalId sourceLocalId,
                   Revision beforeRevision, Revision afterRevision,
                   LockVersion beforeLockVersion, LockVersion afterLockVersion,
                   int attempts, Instant resolvedAt,
                   String stage, String errorType, String message, Instant occurredAt) {}
}
