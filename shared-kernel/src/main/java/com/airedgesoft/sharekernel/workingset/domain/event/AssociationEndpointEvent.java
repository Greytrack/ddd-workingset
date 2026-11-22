package com.airedgesoft.sharekernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Association 端点：具体 Variant，不固定版次。
 */
public record AssociationEndpointEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long localId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long commonId,
                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId) {

    public AssociationEndpointEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
