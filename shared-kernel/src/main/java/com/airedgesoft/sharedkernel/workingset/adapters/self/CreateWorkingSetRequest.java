package com.airedgesoft.sharedkernel.workingset.adapters.self;

import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record CreateWorkingSetRequest(
        @Schema(type = "string") Long parentWorkingSetId,
        @Schema(description = "旧客户端的幂等键", deprecated = true) String requestId,
        @ArraySchema(schema = @Schema(type = "string")) List<Long> productSpaceIds,
        @ArraySchema(schema = @Schema(type = "string")) List<Long> sharedTrackerIds,
        @ArraySchema(schema = @Schema(type = "string")) List<Long> unsharedParentTrackerIds,
        @JsonAlias("name") String displayName,
        WorkingSetCreation.Mode mode, String color,
        Map<Long, Long> baselineAtByTracker) {
    public WorkingSetCreation.Request toDomainRequest(Long newWorkingSetId, String idempotencyKey) {
        WorkingSetCreation.Mode creationMode = mode == null ? WorkingSetCreation.Mode.CREATE : mode;
        return new WorkingSetCreation.Request(parentWorkingSetId, newWorkingSetId, idempotencyKey, productSpaceIds,
                sharedTrackerIds, unsharedParentTrackerIds, displayName, creationMode, color, baselineAtByTracker,
                List.of(), List.of());
    }
}
