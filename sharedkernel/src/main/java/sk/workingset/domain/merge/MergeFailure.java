package sk.workingset.domain.merge;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

/** Merge 失败：冲突、拒绝、证据过期、轮次关闭等，区别于应用层的 DomainException + FailureReason。 */
public final class MergeFailure extends DomainException {
    public enum Reason {
        MergeNotAllowed(WorkingsetResultCode.MERGE_MERGE_NOT_ALLOWED),
        RoundNotFound(WorkingsetResultCode.MERGE_ROUND_NOT_FOUND),
        RoundClosed(WorkingsetResultCode.MERGE_ROUND_CLOSED),
        RoundInProgress(WorkingsetResultCode.MERGE_ROUND_IN_PROGRESS),
        PairMergeInProgress(WorkingsetResultCode.MERGE_PAIR_MERGE_IN_PROGRESS),
        StaleEvidence(WorkingsetResultCode.MERGE_STALE_EVIDENCE),
        InvalidDecision(WorkingsetResultCode.MERGE_INVALID_DECISION),
        Rejected(WorkingsetResultCode.MERGE_REJECTED),
        Conflict(WorkingsetResultCode.MERGE_CONFLICT);

        private final WorkingsetResultCode resultCode;

        Reason(WorkingsetResultCode resultCode) {
            this.resultCode = resultCode;
        }

        public WorkingsetResultCode resultCode() {
            return resultCode;
        }
    }

    private final Reason reason;

    public MergeFailure(Reason reason, String message) {
        super(reason.resultCode(), message, null);
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
