package com.airedgesoft.sharekernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Tracker 的所有 Working Set 绑定已清理，可安全执行物理删除。
 */
public record TrackerDeletedEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId) {
    public TrackerDeletedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
    }
}
