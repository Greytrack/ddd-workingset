package com.airedgesoft.sharekernel.workingset.domain.event;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.Revision;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;

import java.util.Objects;

/**
 * Reference 端点。source 端必须完整；target 的 local/workingSet/revision 可为空，
 * 表示目标尚未纳入或悬空引用。
 */
public record ReferenceEndpointEvent(WorkingSetId workingSetId, LocalId localId, CommonId commonId,
                                     TrackerId trackerId, Revision revision) {

    public ReferenceEndpointEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }

}
