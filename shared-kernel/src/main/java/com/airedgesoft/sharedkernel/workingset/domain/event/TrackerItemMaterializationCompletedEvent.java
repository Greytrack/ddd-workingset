package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Objects;

/**
 * ALM 对目标变体实例化请求的最终处理结果。
 */
public record TrackerItemMaterializationCompletedEvent(long eventId,
                                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long targetLocalId,
                                                       Outcome outcome,
                                                       @JsonFormat(shape = JsonFormat.Shape.STRING) Long businessRef,
                                                       Integer revision,
                                                       Long beforeLockVersion,
                                                       Long afterLockVersion,
                                                       String failure) {

    public TrackerItemMaterializationCompletedEvent {
        Objects.requireNonNull(targetLocalId, "targetLocalId");
        Objects.requireNonNull(outcome, "outcome");
    }

    public enum Outcome {MATERIALIZED, FAILED}
}
