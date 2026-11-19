package sk.workingset.domain.merge;

/** 分配 Merge 决策版本；由应用服务推进决定事实时调用。 */
public interface MergeDecisionVersionGenerator {
    long nextDecisionVersion();
}
