package sk.workingset.application;

import sk.workingset.error.WorkingsetResultCode;

/** 跨 BC 公共失败分类（T4 §5）。 */
public enum FailureReason {
    NOT_FOUND(WorkingsetResultCode.APPLICATION_NOT_FOUND),
    NOT_INCLUDED(WorkingsetResultCode.APPLICATION_NOT_INCLUDED),
    DENIED(WorkingsetResultCode.APPLICATION_DENIED),
    DELETED(WorkingsetResultCode.APPLICATION_DELETED),
    HISTORY_UNAVAILABLE(WorkingsetResultCode.APPLICATION_HISTORY_UNAVAILABLE),
    PREPARING(WorkingsetResultCode.APPLICATION_PREPARING),
    CAPABILITY_NOT_SUPPORTED(WorkingsetResultCode.APPLICATION_CAPABILITY_NOT_SUPPORTED),
    DEPENDENCY_UNAVAILABLE(WorkingsetResultCode.APPLICATION_DEPENDENCY_UNAVAILABLE),
    UNKNOWN(WorkingsetResultCode.APPLICATION_UNKNOWN);

    private final WorkingsetResultCode resultCode;

    FailureReason(WorkingsetResultCode resultCode) {
        this.resultCode = resultCode;
    }

    public WorkingsetResultCode resultCode() {
        return resultCode;
    }
}
