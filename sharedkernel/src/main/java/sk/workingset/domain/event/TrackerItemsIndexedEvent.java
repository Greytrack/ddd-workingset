package sk.workingset.domain.event;

import java.time.Instant;
import java.util.List;

/**
 * Child trackers whose indexing scan reached its end.
 */
public record TrackerItemsIndexedEvent(String creationId, String workingSetId, List<String> trackerIds,
                                       Instant occurredAt) {
    public TrackerItemsIndexedEvent {
        trackerIds = List.copyOf(trackerIds);
    }
}
