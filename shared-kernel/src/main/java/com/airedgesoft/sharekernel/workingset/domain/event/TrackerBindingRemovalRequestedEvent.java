package com.airedgesoft.sharekernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * 请求某个 Working Set 解除已删除 Tracker 的绑定。
 */
public record TrackerBindingRemovalRequestedEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId) {
    public TrackerBindingRemovalRequestedEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
