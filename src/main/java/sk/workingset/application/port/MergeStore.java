package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.TrackerId;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;

/**
 * Merge 持久化端口：轮次状态、Checkpoint 与决定版本。
 *
 * <p>Compare 结果是瞬时展示数据，不写入本端口；MergeRound 保存用户决策及其所选差异证据。
 */
public interface MergeStore {
    /** 插入新轮次（带本轮 Tracker 分支对）；`requestId` 已存在时返回已有轮次，保持幂等。 */
    Optional<MergeRound> insertOrFindRound(MergeRound round, TrackerId trackerA, TrackerId trackerB);
    Optional<MergeRound> findRound(String id, boolean lock);
    Optional<MergeRound> findRoundByRequest(String requestId);
    void updateRound(MergeRound round);

    /** 同一 Tracker 分支对是否已有未终态轮次（并发合并闸门）。 */
    boolean hasActiveRoundForTrackerPair(TrackerId trackerA, TrackerId trackerB);

    /** 在当前事务内独占一个轮次的执行权，事务结束时释放。 */
    boolean tryLockExecution(String roundId);

    Optional<MergeCommitReceipt> findCommitReceipt(String roundId, String entryId);
    void insertCommitReceipt(String roundId, String entryId, MergeCommitReceipt receipt);

    long nextDecisionVersion();
    boolean recordCheckpoint(MergeCheckpoint checkpoint);
    Optional<MergeCheckpoint> findCheckpoint(MergeCheckpoint.Key key);
    boolean revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion, long newVersion);
}
