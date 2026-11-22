package com.airedgesoft.sharekernel.workingset.domain.event;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.CommonId;
import com.airedgesoft.sharekernel.workingset.domain.LocalId;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;

import java.util.Objects;

/**
 * Association 端点：具体 Variant，不固定版次。
 */
public record AssociationEndpointEvent(WorkingSetId workingSetId, LocalId localId, CommonId commonId,
                                       TrackerId trackerId) {

    public AssociationEndpointEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
