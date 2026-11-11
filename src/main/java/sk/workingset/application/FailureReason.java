package sk.workingset.application;

/**
 * 首切片适用的公共失败分类子集（T4 §5.2）：NotFound / NotIncluded。
 * <p>其余分类（Denied/Deleted/HistoryUnavailable/Preparing/CapabilityNotSupported/DependencyUnavailable/Unknown）在后续切片实现。
 */
public enum FailureReason {
    NOT_FOUND,
    NOT_INCLUDED
}
