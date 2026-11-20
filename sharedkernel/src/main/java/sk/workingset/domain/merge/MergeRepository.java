package sk.workingset.domain.merge;

import sk.common.error.DomainException;

import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

/**
 * MergeRound 聚合仓储。
 *
 * <p>Compare 结果是瞬时展示数据，不写入本端口；MergeRound 保存用户决策及其所选差异证据。
 * 并发闸门（同一 Tracker 对只允许一个活动轮次）与幂等键（requestId）由持久化机制兑现，
 * 冲突以 {@link DomainException} 表达，不向领域层暴露 lock 等调用参数。
 */
@Repository
public interface MergeRepository {
    /**
     * 保存轮次：新轮次插入，已有轮次更新；轮次条目随聚合根一并保存。
     */
    void save(MergeRound round);

    /**
     * 按轮次身份读取。
     */
    Optional<MergeRound> findById(String id);

    /**
     * 按提交幂等键读取。
     */
    Optional<MergeRound> findByRequestId(String requestId);
}
