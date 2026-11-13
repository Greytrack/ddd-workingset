package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;

/**
 * Merge 持久化端口：冻结比较、轮次状态、Checkpoint 与决定版本。
 *
 * <p>当前实现按 JSONB 整份保存比较/轮次（D12-A 切片实现，规模与保留策略为后续专项）。
 */
public interface MergeStore {
    void insertComparison(Comparison comparison);
    Optional<Comparison> findComparison(String token);

    boolean insertRound(MergeRound round);
    Optional<MergeRound> findRound(String id, boolean lock);
    void updateRound(MergeRound round);

    long nextDecisionVersion();
    boolean recordCheckpoint(MergeCheckpoint checkpoint);
    Optional<MergeCheckpoint> findCheckpoint(MergeCheckpoint.Key key);
    boolean revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion, long newVersion);
}
