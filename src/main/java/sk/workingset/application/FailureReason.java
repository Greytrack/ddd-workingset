package sk.workingset.application;

/** 跨 BC 公共失败分类（T4 §5）。 */
public enum FailureReason {
    NOT_FOUND, NOT_INCLUDED, DENIED, DELETED, HISTORY_UNAVAILABLE, PREPARING,
    CAPABILITY_NOT_SUPPORTED, DEPENDENCY_UNAVAILABLE, UNKNOWN
}
