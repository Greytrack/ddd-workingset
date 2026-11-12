package sk.workingset.domain.merge;

public final class MergeFailure extends RuntimeException {
    public enum Reason {
        MergeNotAllowed, ComparisonNotFound, ComparisonAlreadyUsed, RoundNotFound, RoundClosed, StaleEvidence,
        InvalidDecision, Rejected, Conflict
    }

    private final Reason reason;

    public MergeFailure(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
