package com.airedgesoft.sharedkernel.workingset.adapters.self;

import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;

import java.util.List;
import java.util.Map;

public record CreateWorkingSetRequest(Long parentWorkingSetId, String requestId,
                                      List<Long> productSpaceIds, List<Long> sharedTrackerIds,
                                      List<Long> unsharedParentTrackerIds, String name,
                                      WorkingSetCreation.Mode mode, String color,
                                      Map<Long, Long> baselineAtByTracker) {
    public WorkingSetCreation.Request toDomainRequest(Long newWorkingSetId) {
        WorkingSetCreation.Mode creationMode = mode == null ? WorkingSetCreation.Mode.CREATE : mode;
        return new WorkingSetCreation.Request(parentWorkingSetId, newWorkingSetId, requestId, productSpaceIds,
                sharedTrackerIds, unsharedParentTrackerIds, name, creationMode, color, baselineAtByTracker,
                List.of(), List.of());
    }
}
