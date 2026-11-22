package com.airedgesoft.sharekernel.workingset.application.query.provider;


import java.util.List;

/**
 * COW 失败记录端口：保留失败阶段、尝试次数与原因，供审计读取。
 */
public interface CowFailureLogQueryProvider {
    void record(Failure failure);

    List<Failure> findBySource(Long sourceLocalId);

    record Failure(long eventId, Long targetLocalId, Long sourceLocalId,
                   Integer beforeRevision, Integer afterRevision,
                   Long beforeLockVersion, Long afterLockVersion,
                   int attempts,
                   String stage, String errorType, String message, long occurredAt) {
    }
}
