package com.airedgesoft.sharekernel.workingset.domain.event;

import java.util.List;

/**
 * Child trackers whose indexing scan reached its end.
 */
public record TrackerItemsIndexedEvent(String creationId, String workingSetId, List<String> trackerIds,
                                       long occurredAt) {
    public TrackerItemsIndexedEvent {
        trackerIds = List.copyOf(trackerIds);
    }
}
