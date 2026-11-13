package sk.workingset.domain.merge;

/** Merge 失败：冲突、拒绝、证据过期、轮次关闭等，区别于业务 BC 的 ApplicationFailure。 */
public final class MergeFailure extends RuntimeException {
    public enum Reason {
        MergeNotAllowed, ComparisonNotFound, ComparisonAlreadyUsed, RoundNotFound, RoundClosed, RoundInProgress, StaleEvidence,
        InvalidDecision, Rejected, Conflict
    }

    private final Reason reason;

    public MergeFailure(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
