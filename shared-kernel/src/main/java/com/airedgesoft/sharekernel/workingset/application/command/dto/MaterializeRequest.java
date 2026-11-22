package com.airedgesoft.sharekernel.workingset.application.command.dto;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.LockVersion;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.event.SnapshotRefEvent;

/**
 * 物化请求：目标变体身份、来源证据与业务版次；不携带 SK 聚合。
 */
public record MaterializeRequest(LocalId targetLocalId, TrackerId targetTrackerId,
                                 CommonId commonId, LocalId sourceLocalId,
                                 Revision beforeRevision, Revision afterRevision,
                                 LockVersion beforeLockVersion, LockVersion afterLockVersion,
                                 Revision targetRevision, long eventId, SnapshotRefEvent snapshotRef) {
}
