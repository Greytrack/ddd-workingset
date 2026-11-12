package sk.workingset.application.port;

import sk.workingset.application.event.SnapshotRef;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;

public record MaterializeRequest(LocalId targetLocalId, TrackerId targetTrackerId,
                                 CommonId commonId, LocalId sourceLocalId,
                                 Revision beforeRevision, Revision afterRevision,
                                 LockVersion beforeLockVersion, LockVersion afterLockVersion,
                                 Revision targetRevision, long eventId, SnapshotRef snapshotRef) {}
