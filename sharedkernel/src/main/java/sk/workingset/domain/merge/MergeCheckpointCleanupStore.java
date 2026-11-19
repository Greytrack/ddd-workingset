package sk.workingset.domain.merge;

/** 清理已被双侧更新版次超越的 Checkpoint；属于维护任务，不是聚合仓储职责。 */
public interface MergeCheckpointCleanupStore {
    int deleteSupersededCheckpoints();
}
