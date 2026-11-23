package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * SK 请求 ALM 实例化一个目标变体；ALM 在自己的事务中保存业务对象。
 *
 * <p>事件只携带目标身份、来源切点和快照引用，不携带 SK 聚合。
 */
public record TrackerItemMaterializationRequestedEvent(long eventId,
                                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long targetLocalId,
                                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long targetTrackerId,
                                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long commonId,
                                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long sourceLocalId,
                                                       Integer beforeRevision,
                                                       Integer afterRevision,
                                                       Long beforeLockVersion,
                                                       Long afterLockVersion,
                                                       Integer targetRevision,
                                                       SnapshotRefEvent snapshotRef) {

    public TrackerItemMaterializationRequestedEvent {
        Objects.requireNonNull(targetLocalId, "targetLocalId");
        Objects.requireNonNull(targetTrackerId, "targetTrackerId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(sourceLocalId, "sourceLocalId");
        Objects.requireNonNull(beforeRevision, "beforeRevision");
        Objects.requireNonNull(afterRevision, "afterRevision");
        Objects.requireNonNull(beforeLockVersion, "beforeLockVersion");
        Objects.requireNonNull(afterLockVersion, "afterLockVersion");
        Objects.requireNonNull(targetRevision, "targetRevision");
        Objects.requireNonNull(snapshotRef, "snapshotRef");
    }
}
