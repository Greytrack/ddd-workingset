package com.airedgesoft.sharedkernel.workingset.application.query.provider;


import java.util.List;

/**
 * 按来源查询持久化的 COW 业务失败结果，供审计读取。
 */
public interface CowFailureLogQueryProvider {
    List<Failure> findBySource(Long sourceLocalId);

    record Failure(long eventId, Long targetLocalId, Long sourceLocalId,
                   Integer beforeRevision, Integer afterRevision,
                   Long beforeLockVersion, Long afterLockVersion,
                   int attempts,
                   String stage, String errorType, String message, long occurredAt) {
    }
}
