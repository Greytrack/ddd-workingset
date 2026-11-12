package sk.workingset.application;

import java.util.List;

public class CowMaterializationFailedException extends RuntimeException {
    private final long eventId;
    private final List<CowFailureLog.Failure> failures;

    public CowMaterializationFailedException(long eventId, List<CowFailureLog.Failure> failures) {
        super("COW event " + eventId + " failed for " + failures.size() + " target(s)");
        this.eventId = eventId;
        this.failures = List.copyOf(failures);
    }

    public long eventId() { return eventId; }
    public List<CowFailureLog.Failure> failures() { return failures; }
}
