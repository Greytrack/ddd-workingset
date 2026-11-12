package sk.workingset.application;

import java.util.List;
import java.util.Optional;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;

public interface CowMaterializationStore {
    enum Status { PENDING, MATERIALIZED, SWITCHED, FAILED }

    record Entry(long eventId, LocalId targetLocalId, LocalId sourceLocalId, Revision sourceRevision,
                 Status status, int attempts, boolean businessSaved, String lastError) {}

    Entry markPending(long eventId, LocalId target, LocalId source, Revision sourceRevision);
    void markMaterialized(long eventId, LocalId target);
    void markSwitched(long eventId, LocalId target);
    void markFailed(long eventId, LocalId target, String error);
    Optional<Entry> findByEventAndTarget(long eventId, LocalId target);
    Optional<Entry> lockByEventAndTarget(long eventId, LocalId target);
    List<Entry> findByStatus(Status status);
    List<Entry> findBySource(LocalId source);
}
