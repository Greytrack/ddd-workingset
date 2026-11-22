package com.airedgesoft.sharekernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Tracker 聚合请求删除；绑定清理与最终物理删除通过后续事件完成。
 */
public record TrackerDeletionRequestedEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId) {
    public TrackerDeletionRequestedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
