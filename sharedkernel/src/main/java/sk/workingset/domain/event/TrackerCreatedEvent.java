package sk.workingset.domain.event;

import java.util.Objects;

import sk.tracker.domain.TrackerId;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSetId;

/** Tracker 聚合已创建；WorkingSet 绑定由 SK 监听事件后独立完成。 */
public record TrackerCreatedEvent(TrackerId trackerId,
                                  WorkingSetId workingSetId,
                                  String productSpaceId,
                                  String classificationId,
                                  SharingMode sharingMode) {

    public TrackerCreatedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(productSpaceId, "productSpaceId");
        Objects.requireNonNull(classificationId, "classificationId");
        Objects.requireNonNull(sharingMode, "sharingMode");
    }
}
