package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Reference 端点。source 端必须完整；target 的 local/workingSet/revision 可为空，
 * 表示目标尚未纳入或悬空引用。
 */
public record ReferenceEndpointEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long localId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long commonId,
                                     @JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId, Integer revision) {

    public ReferenceEndpointEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
    }

}
