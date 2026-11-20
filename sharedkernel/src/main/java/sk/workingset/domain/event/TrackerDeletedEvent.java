package sk.workingset.domain.event;

import sk.tracker.domain.TrackerId;

import java.util.Objects;

/** Tracker 的所有 Working Set 绑定已清理，可安全执行物理删除。 */
public record TrackerDeletedEvent(TrackerId trackerId) {
    public TrackerDeletedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
