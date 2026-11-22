package com.airedgesoft.sharekernel.workingset.domain.event;

import com.airedgesoft.sharekernel.workingset.domain.Classification.SharingMode;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Tracker 聚合已创建；WorkingSet 绑定由 SK 监听事件后独立完成。
 */
public record TrackerCreatedEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId,
                                  @JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId,
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
