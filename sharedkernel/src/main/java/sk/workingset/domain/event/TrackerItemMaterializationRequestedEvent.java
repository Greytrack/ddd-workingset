package sk.workingset.domain.event;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.*;

import java.util.Objects;

/**
 * SK 请求 ALM 实例化一个目标变体；ALM 在自己的事务中保存业务对象。
 *
 * <p>事件只携带目标身份、来源切点和快照引用，不携带 SK 聚合。
 */
public record TrackerItemMaterializationRequestedEvent(long eventId,
                                                       LocalId targetLocalId,
                                                       TrackerId targetTrackerId,
                                                       CommonId commonId,
                                                       LocalId sourceLocalId,
                                                       Revision beforeRevision,
                                                       Revision afterRevision,
                                                       LockVersion beforeLockVersion,
                                                       LockVersion afterLockVersion,
                                                       Revision targetRevision,
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
