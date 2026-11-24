package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Tracker 聚合已创建；WorkingSet 绑定由 SK 监听事件后独立完成。
 */
public record TrackerCreatedEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId,
                                  @JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId,
                                  String productSpaceId,
                                  String classificationId,
                                  boolean shared) {

    public TrackerCreatedEvent {
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(productSpaceId, "productSpaceId");
        Objects.requireNonNull(classificationId, "classificationId");
    }

    @JsonCreator
    public static TrackerCreatedEvent fromJson(@JsonProperty("trackerId") Long trackerId,
            @JsonProperty("workingSetId") Long workingSetId,
            @JsonProperty("productSpaceId") String productSpaceId,
            @JsonProperty("classificationId") String classificationId,
            @JsonProperty("shared") Boolean shared,
            @JsonProperty("sharingMode") String legacyMode) {
        // 未消费的历史发布记录可能仍包含原枚举值。
        boolean resolved = shared != null ? shared : switch (Objects.requireNonNull(legacyMode, "shared")) {
            case "SHARED" -> true;
            case "UNSHARED" -> false;
            default -> throw new IllegalArgumentException("Unknown tracker sharing mode: " + legacyMode);
        };
        return new TrackerCreatedEvent(trackerId, workingSetId, productSpaceId, classificationId, resolved);
    }
}
