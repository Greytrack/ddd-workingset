package sk.workingset.application;

import java.time.Instant;
import java.util.List;

import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

public interface CowFailureLog {
    void record(Failure failure);
    List<Failure> findBySource(LocalId sourceLocalId);

    record Failure(long eventId, LocalId targetLocalId, LocalId sourceLocalId,
                   Revision beforeRevision, Revision afterRevision,
                   String stage, String errorType, String message, Instant occurredAt) {}
}
