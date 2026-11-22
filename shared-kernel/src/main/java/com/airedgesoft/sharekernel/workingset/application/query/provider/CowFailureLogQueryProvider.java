package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.LockVersion;
import com.airedgesoft.sharekernel.workingset.domain.Revision;

import java.util.List;

/**
 * COW 失败记录端口：保留失败阶段、尝试次数与原因，供审计读取。
 */
public interface CowFailureLogQueryProvider {
    void record(Failure failure);

    List<Failure> findBySource(LocalId sourceLocalId);

    record Failure(long eventId, LocalId targetLocalId, LocalId sourceLocalId,
                   Revision beforeRevision, Revision afterRevision,
                   LockVersion beforeLockVersion, LockVersion afterLockVersion,
                   int attempts,
                   String stage, String errorType, String message, long occurredAt) {
    }
}
