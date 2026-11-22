package com.airedgesoft.sharekernel.workingset.domain.cow;

import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;

import java.util.List;
import java.util.Optional;

/**
 * COW 物化收据存储端口：按 {@code eventId + targetLocalId} 记录 PENDING/MATERIALIZED/SWITCHED/FAILED、
 * 尝试次数与是否已保存业务对象，支撑幂等与“切换补偿”。
 */
public interface CowMaterializationStore {
    Entry markPending(long eventId, LocalId target, LocalId source, Revision sourceRevision);

    void markMaterialized(long eventId, LocalId target);

    void markSwitched(long eventId, LocalId target);

    void markFailed(long eventId, LocalId target, String error);

    Optional<Entry> findByEventAndTarget(long eventId, LocalId target);

    Optional<Entry> lockByEventAndTarget(long eventId, LocalId target);

    List<Entry> findByStatus(Status status);

    List<Entry> findBySource(LocalId source);

    enum Status {PENDING, MATERIALIZED, SWITCHED, FAILED}

    record Entry(long eventId, LocalId targetLocalId, LocalId sourceLocalId, Revision sourceRevision,
                 Status status, int attempts, boolean businessSaved, String lastError) {
    }
}
