package sk.workingset.application.port;

import sk.workingset.application.event.SnapshotRef;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

/** 物化请求：目标变体身份、来源证据与业务版次；不携带 SK 聚合。 */
public record MaterializeRequest(LocalId targetLocalId, TrackerId targetTrackerId,
                                 CommonId commonId, LocalId sourceLocalId,
                                 Revision beforeRevision, Revision afterRevision,
                                 LockVersion beforeLockVersion, LockVersion afterLockVersion,
                                 Revision targetRevision, long eventId, SnapshotRef snapshotRef) {}
