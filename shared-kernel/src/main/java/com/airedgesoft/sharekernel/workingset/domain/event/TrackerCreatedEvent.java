package com.airedgesoft.sharekernel.workingset.domain.event;

import com.airedgesoft.sharekernel.tracker.domain.TrackerId;
import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;

import java.util.Objects;

/**
 * Tracker 聚合已创建；WorkingSet 绑定由 SK 监听事件后独立完成。
 */
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
