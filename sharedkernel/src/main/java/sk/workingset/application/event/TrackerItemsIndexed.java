package sk.workingset.application.event;

import java.time.Instant;
import java.util.List;

/** Child trackers whose indexing scan reached its end. */
public record TrackerItemsIndexed(String creationId, String workingSetId, List<String> trackerIds, Instant occurredAt) {
    public TrackerItemsIndexed {
        trackerIds = List.copyOf(trackerIds);
    }
}
