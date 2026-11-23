package com.airedgesoft.sharedkernel.workingset.domain.merge;

import com.airedgesoft.lib.exception.DomainException;
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
     * 原子认领 requestId：不存在则插入新轮次，已存在则不改变。
     *
     * @return 当前轮次是否成为该 requestId 的 winner
     */
    boolean claim(MergeRound round);

    /**
     * 更新已有轮次的状态；不插入新轮次，也不改变 requestId/token。
     * 调用方必须先通过 {@link #claim(MergeRound)} 认领或按 requestId 读到 winner。
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
