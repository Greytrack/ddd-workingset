package sk.tracker.domain.event;

import java.util.List;
import java.util.Objects;

import sk.tracker.domain.TrackerId;

/** 产品空间的默认 Tracker 已批量创建，WorkingSet 可一次性建立绑定。 */
public record DefaultTrackersProvisionedEvent(Long productSpaceId, List<TrackerId> trackerIds) {
    public DefaultTrackersProvisionedEvent {
        Objects.requireNonNull(productSpaceId, "productSpaceId");
        trackerIds = List.copyOf(trackerIds);
    }
}
