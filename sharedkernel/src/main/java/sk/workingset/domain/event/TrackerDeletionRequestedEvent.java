package sk.workingset.domain.event;

import sk.tracker.domain.TrackerId;

import java.util.Objects;

/**
 * Tracker 聚合请求删除；绑定清理与最终物理删除通过后续事件完成。
 */
public record TrackerDeletionRequestedEvent(TrackerId trackerId) {
    public TrackerDeletionRequestedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
