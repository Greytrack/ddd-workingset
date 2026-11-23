package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;



/**
 * A business BC has created a native Tracker Item in its owning Working Set.
 */
public record TrackerItemCreatedEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long localId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long commonId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId, Integer revision, String effectiveFlags) {
    public TrackerItemCreatedEvent(Long localId, Long commonId, Long trackerId, Long workingSetId, Integer revision) {
        this(localId, commonId, trackerId, workingSetId, revision, null);
    }
}
