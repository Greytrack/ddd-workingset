package sk.workingset.domain.merge;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.TrackerId;

import java.util.Optional;

/**
 * MergeRound 聚合仓储。
 *
 * <p>Compare 结果是瞬时展示数据，不写入本端口；MergeRound 保存用户决策及其所选差异证据。
 */
@Repository
public interface MergeRepository {
    /**
     * 原子受理指定 Tracker 对的轮次；相同请求返回已有轮次。
     */
    Optional<MergeRound> admit(MergeRound round, TrackerId trackerA, TrackerId trackerB);

    Optional<MergeRound> findRound(String id, boolean lock);

    Optional<MergeRound> findRoundByRequest(String requestId);

    void save(MergeRound round);
}
