package com.airedgesoft.sharekernel.workingset.application.command.dto;

import com.airedgesoft.sharekernel.workingset.domain.event.SnapshotRefEvent;

/**
 * 物化请求：目标变体身份、来源证据与业务版次；不携带 SK 聚合。
 */
public record MaterializeRequest(Long targetLocalId, Long targetTrackerId,
                                 Long commonId, Long sourceLocalId,
                                 Integer beforeRevision, Integer afterRevision,
                                 Long beforeLockVersion, Long afterLockVersion,
                                 Integer targetRevision, long eventId, SnapshotRefEvent snapshotRef) {
}
