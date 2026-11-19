package sk.workingset.domain.event;

import java.util.Objects;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** 请求某个 Working Set 解除已删除 Tracker 的绑定。 */
public record TrackerBindingRemovalRequestedEvent(WorkingSetId workingSetId, TrackerId trackerId) {
    public TrackerBindingRemovalRequestedEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
