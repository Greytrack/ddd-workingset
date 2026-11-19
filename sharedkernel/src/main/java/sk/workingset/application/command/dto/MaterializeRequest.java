package sk.workingset.application.command.dto;

import sk.workingset.domain.*;
import sk.workingset.domain.event.SnapshotRefEvent;

/**
 * 物化请求：目标变体身份、来源证据与业务版次；不携带 SK 聚合。
 */
public record MaterializeRequest(LocalId targetLocalId, TrackerId targetTrackerId,
                                 CommonId commonId, LocalId sourceLocalId,
                                 Revision beforeRevision, Revision afterRevision,
                                 LockVersion beforeLockVersion, LockVersion afterLockVersion,
                                 Revision targetRevision, long eventId, SnapshotRefEvent snapshotRef) {
}
