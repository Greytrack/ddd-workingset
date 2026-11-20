package sk.workingset.domain.event;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.Objects;

/** 请求某个 Working Set 解除已删除 Tracker 的绑定。 */
public record TrackerBindingRemovalRequestedEvent(WorkingSetId workingSetId, TrackerId trackerId) {
    public TrackerBindingRemovalRequestedEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
