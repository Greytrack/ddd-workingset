package sk.workingset.application;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

import java.util.List;

public class CowMaterializationFailedException extends DomainException {
    private final long eventId;
    private final List<CowFailureLog.Failure> failures;

    public CowMaterializationFailedException(long eventId, List<CowFailureLog.Failure> failures) {
        super(WorkingsetResultCode.COW_MATERIALIZATION_FAILED, "COW event " + eventId + " failed for " + failures.size() + " target(s)", null);
        this.eventId = eventId;
        this.failures = List.copyOf(failures);
    }

    public long eventId() { return eventId; }
    public List<CowFailureLog.Failure> failures() { return failures; }
}
