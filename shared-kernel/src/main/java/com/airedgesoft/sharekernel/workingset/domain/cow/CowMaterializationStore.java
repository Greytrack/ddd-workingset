package com.airedgesoft.sharekernel.workingset.domain.cow;


import java.util.List;
import java.util.Optional;

/**
 * COW 物化收据存储端口：按 {@code eventId + targetLocalId} 记录 PENDING/MATERIALIZED/SWITCHED/FAILED、
 * 尝试次数与是否已保存业务对象，支撑幂等与“切换补偿”。
 */
public interface CowMaterializationStore {
    Entry markPending(long eventId, Long target, Long source, Integer sourceRevision);

    void markMaterialized(long eventId, Long target);

    void markSwitched(long eventId, Long target);

    void markFailed(long eventId, Long target, String error);

    Optional<Entry> findByEventAndTarget(long eventId, Long target);

    Optional<Entry> lockByEventAndTarget(long eventId, Long target);

    List<Entry> findByStatus(Status status);

    List<Entry> findBySource(Long source);

    enum Status {PENDING, MATERIALIZED, SWITCHED, FAILED}

    record Entry(long eventId, Long targetLocalId, Long sourceLocalId, Integer sourceRevision,
                 Status status, int attempts, boolean businessSaved, String lastError) {
    }
}
