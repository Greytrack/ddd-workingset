package sk.workingset.domain.event;

import java.util.Objects;

import sk.tracker.domain.TrackerId;

/** Tracker 聚合请求删除；绑定清理与最终物理删除通过后续事件完成。 */
public record TrackerDeletionRequestedEvent(TrackerId trackerId) {
    public TrackerDeletionRequestedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
