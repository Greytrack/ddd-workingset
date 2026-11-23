package com.airedgesoft.sharedkernel.tracker.domain.event;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.util.List;
import java.util.Objects;

/**
 * 产品空间的默认 Tracker 已批量创建，WorkingSet 可一次性建立绑定。
 */
public record DefaultTrackersProvisionedEvent(Long productSpaceId, @JsonSerialize(contentUsing = ToStringSerializer.class) List<Long> trackerIds) {
    public DefaultTrackersProvisionedEvent {
        Objects.requireNonNull(productSpaceId, "productSpaceId");
        trackerIds = List.copyOf(trackerIds);
    }
}
