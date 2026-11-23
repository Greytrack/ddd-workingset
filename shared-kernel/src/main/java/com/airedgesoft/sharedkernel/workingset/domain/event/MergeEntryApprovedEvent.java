package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Map;
import java.util.Objects;

/**
 * SK 已批准应用某个 Merge 条目；目标业务 BC 在自己的事务中执行并返回结果事件。
 *
 * <p>事件携带目标分类、冻结的应用值与目标期望版本。
 */
public record MergeEntryApprovedEvent(String roundId,
                                      String entryId,
                                      String token,
                                      Operation operation,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Long commonId,
                                      @JsonFormat(shape = JsonFormat.Shape.STRING) Long localId,
                                      Integer sourceRevision,
                                      Integer targetRevision,
                                      Long expectedLockVersion,
                                      String version,
                                      Map<String, Object> baseFields,
                                      Map<String, Object> acceptedFields,
                                      boolean inherited,
                                      long targetClassificationId) {

    public MergeEntryApprovedEvent {
        Objects.requireNonNull(roundId, "roundId");
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        Objects.requireNonNull(targetRevision, "targetRevision");
        if (targetClassificationId <= 0) {
            throw new IllegalArgumentException("targetClassificationId must be positive");
        }
        baseFields = baseFields == null ? Map.of() : Map.copyOf(baseFields);
        acceptedFields = acceptedFields == null ? Map.of() : Map.copyOf(acceptedFields);
    }

    public enum Operation {CREATE, UPDATE, DELETE, RESTORE}
}
