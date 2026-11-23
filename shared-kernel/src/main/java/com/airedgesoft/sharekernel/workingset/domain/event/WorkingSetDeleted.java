package com.airedgesoft.sharekernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * Working Set 聚合已删除。
 */
public record WorkingSetDeleted(@JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId) {
    public WorkingSetDeleted {
        Objects.requireNonNull(workingSetId, "workingSetId");
    }
}
